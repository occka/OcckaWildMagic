package wildmagic.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import wildmagic.OcckaWildMagic;
import wildmagic.classdata.AbilityDefinition;
import wildmagic.classdata.ClassProgression;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.network.WildMagicNetworking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WildMagicServerState {
	private static final Identifier BARD_HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_fragile_performer");
	private static final double BARD_HEALTH_PENALTY = -4.0D;
	private static final Map<UUID, PlayerClassData> PLAYER_DATA = new ConcurrentHashMap<>();
	private static final Map<UUID, long[]> ABILITY_COOLDOWNS = new ConcurrentHashMap<>();
	private static MinecraftServer currentServer;
	private static Path saveFile;

	private WildMagicServerState() {
	}

	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(WildMagicServerState::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(WildMagicServerState::save);
		ServerTickEvents.END_SERVER_TICK.register(WildMagicServerState::tickPlayers);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			applyClassPassives(handler.player);
			sync(handler.player);
		});
	}

	public static PlayerClassData get(ServerPlayer player) {
		return PLAYER_DATA.getOrDefault(player.getUUID(), PlayerClassData.EMPTY);
	}

	public static void selectClass(ServerPlayer player, WildMagicClass clazz) {
		PlayerClassData current = get(player);
		if (current.hasClass()) {
			sync(player);
			return;
		}

		setClass(player, clazz, ClassProgression.MIN_LEVEL);
	}

	public static void setClass(ServerPlayer player, WildMagicClass clazz, int level) {
		removeClassPassives(player);
		PlayerClassData updated = PlayerClassData.createForClass(clazz, level);
		PLAYER_DATA.put(player.getUUID(), updated);
		ABILITY_COOLDOWNS.remove(player.getUUID());
		applyClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void clearClass(ServerPlayer player) {
		PLAYER_DATA.remove(player.getUUID());
		ABILITY_COOLDOWNS.remove(player.getUUID());
		removeClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void setActiveAbility(ServerPlayer player, int slot, String abilityId) {
		PlayerClassData current = get(player);
		if (!current.hasClass() || slot < 0 || slot >= PlayerClassData.ACTIVE_SLOT_COUNT) {
			sync(player);
			return;
		}

		if (abilityId == null || abilityId.isBlank()) {
			updateActiveAbility(player, current, slot, "");
			return;
		}

		AbilityDefinition ability = ClassProgression.abilityFor(current.selectedClass(), abilityId).orElse(null);
		if (ability == null || ability.passive() || !ability.isUnlocked(current)) {
			sync(player);
			return;
		}

		updateActiveAbility(player, current, slot, ability.id());
	}

	public static void useActiveAbility(ServerPlayer player, int slot) {
		PlayerClassData current = get(player);
		if (!current.hasClass() || slot < 0 || slot >= PlayerClassData.ACTIVE_SLOT_COUNT) {
			return;
		}

		String abilityId = current.activeAbility(slot);
		if (abilityId.isBlank()) {
			return;
		}

		AbilityDefinition ability = ClassProgression.abilityFor(current.selectedClass(), abilityId)
				.filter(definition -> !definition.passive() && definition.isUnlocked(current))
				.orElse(null);
		if (ability == null || !hasEnoughMana(player, current, ability) || isOnCooldown(player, slot)) {
			return;
		}

		boolean used = switch (ability.id()) {
			case "bard_inspiration" -> useBardInspiration(player);
			case "bard_sound_wave" -> useBardSoundWave(player);
			default -> usePlaceholderAbility(player, ability);
		};
		if (!used) {
			return;
		}

		PlayerClassData updated = get(player).consumeMana(ability.manaCost());
		PLAYER_DATA.put(player.getUUID(), updated);
		setCooldown(player, slot, ability.cooldownSeconds());
		sync(player);
	}

	private static boolean hasEnoughMana(ServerPlayer player, PlayerClassData data, AbilityDefinition ability) {
		if (ability.manaCost() <= 0 || !data.selectedClass().usesMana()) {
			return true;
		}

		if (data.mana() >= ability.manaCost()) {
			return true;
		}

		player.sendSystemMessage(Component.literal("Недостаточно маны: нужно " + ability.manaCost()));
		return false;
	}

	private static boolean isOnCooldown(ServerPlayer player, int slot) {
		long[] cooldowns = ABILITY_COOLDOWNS.get(player.getUUID());
		if (cooldowns == null || slot >= cooldowns.length) {
			return false;
		}

		long remainingTicks = cooldowns[slot] - player.level().getGameTime();
		if (remainingTicks <= 0) {
			return false;
		}

		player.sendSystemMessage(Component.literal("Способность на перезарядке: " + Math.ceil(remainingTicks / 20.0D) + "с"));
		return true;
	}

	private static void setCooldown(ServerPlayer player, int slot, int cooldownSeconds) {
		if (cooldownSeconds <= 0) {
			return;
		}

		long[] cooldowns = ABILITY_COOLDOWNS.computeIfAbsent(player.getUUID(), uuid -> new long[PlayerClassData.ACTIVE_SLOT_COUNT]);
		cooldowns[slot] = player.level().getGameTime() + (cooldownSeconds * 20L);
	}

	private static boolean useBardInspiration(ServerPlayer player) {
		ServerLevel level = player.level();
		AABB area = player.getBoundingBox().inflate(5.0D);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
			entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1), player);
			entity.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 5), player);
		}

		level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1.2D, player.getZ(), 32, 1.5D, 0.8D, 1.5D, 0.1D);
		return true;
	}

	private static boolean useBardSoundWave(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().normalize();
		Set<LivingEntity> hitEntities = new HashSet<>();
		for (int step = 1; step <= 6; step++) {
			Vec3 center = player.position().add(0.0D, 1.0D, 0.0D).add(look.scale(step));
			level.sendParticles(ParticleTypes.NOTE, center.x, center.y, center.z, 8, 1.0D, 1.0D, 1.0D, 0.0D);
			AABB waveBox = new AABB(center.x - 1.5D, center.y - 1.5D, center.z - 1.5D, center.x + 1.5D, center.y + 1.5D, center.z + 1.5D);
			for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, waveBox, entity -> entity != player)) {
				if (hitEntities.add(target)) {
					float damage = 4.0F + (player.getRandom().nextFloat() * 6.0F);
					target.hurtServer(level, player.damageSources().playerAttack(player), damage);
					target.push(look.x * 0.8D, 0.45D, look.z * 0.8D);
				}
			}
		}

		return true;
	}

	private static boolean usePlaceholderAbility(ServerPlayer player, AbilityDefinition ability) {
		player.sendSystemMessage(Component.literal("Использована способность: " + ability.title()));
		return true;
	}

	private static void updateActiveAbility(ServerPlayer player, PlayerClassData current, int slot, String abilityId) {
		PlayerClassData updated = current.withActiveAbility(slot, abilityId);
		PLAYER_DATA.put(player.getUUID(), updated);
		sync(player);
		save(currentServer);
	}

	public static void addAdvancementExp(ServerPlayer player) {
		PlayerClassData current = get(player);
		if (!current.hasClass()) {
			return;
		}

		PlayerClassData updated = current.withExp(current.exp() + 1);
		while (updated.level() < ClassProgression.MAX_LEVEL && updated.exp() >= updated.expRequiredForNextLevel()) {
			updated = updated.withExp(updated.exp() - updated.expRequiredForNextLevel()).withLevel(updated.level() + 1);
		}
		PLAYER_DATA.put(player.getUUID(), updated);
		applyClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void sync(ServerPlayer player) {
		WildMagicNetworking.sendClassData(player, get(player));
	}

	private static void tickPlayers(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyClassPassives(player);
			regenerateMana(player);
		}
	}

	private static void regenerateMana(ServerPlayer player) {
		PlayerClassData data = get(player);
		if (!data.hasClass() || !data.selectedClass().usesMana() || data.mana() >= data.maxMana()) {
			return;
		}

		PlayerClassData updated = data.withMana(data.mana() + ClassProgression.manaRegenPerSecond(data.selectedClass()));
		PLAYER_DATA.put(player.getUUID(), updated);
		sync(player);
	}

	private static void applyClassPassives(ServerPlayer player) {
		PlayerClassData data = get(player);
		if (data.hasClass() && data.selectedClass() == WildMagicClass.BARD) {
			applyBardHealthPenalty(player);
		} else {
			removeBardHealthPenalty(player);
		}
	}

	private static void removeClassPassives(ServerPlayer player) {
		removeBardHealthPenalty(player);
	}

	private static void applyBardHealthPenalty(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getModifier(BARD_HEALTH_MODIFIER_ID) != null) {
			return;
		}

		maxHealth.addPermanentModifier(new AttributeModifier(BARD_HEALTH_MODIFIER_ID, BARD_HEALTH_PENALTY, AttributeModifier.Operation.ADD_VALUE));
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
	}

	private static void removeBardHealthPenalty(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getModifier(BARD_HEALTH_MODIFIER_ID) == null) {
			return;
		}

		maxHealth.removeModifier(BARD_HEALTH_MODIFIER_ID);
	}

	private static void load(MinecraftServer server) {
		currentServer = server;
		saveFile = server.getWorldPath(LevelResource.ROOT).resolve("occkawildmagic-player-classes.txt");
		PLAYER_DATA.clear();
		ABILITY_COOLDOWNS.clear();
		if (!Files.exists(saveFile)) {
			return;
		}

		try {
			for (String line : Files.readAllLines(saveFile, StandardCharsets.UTF_8)) {
				String[] parts = line.split("=", 2);
				if (parts.length == 2) {
					PLAYER_DATA.put(UUID.fromString(parts[0]), PlayerClassData.deserialize(parts[1]));
				}
			}
		} catch (IllegalArgumentException | IOException exception) {
			OcckaWildMagic.LOGGER.warn("Could not load Wild Magic class data", exception);
		}
	}

	private static void save(MinecraftServer server) {
		if (server == null || saveFile == null) {
			return;
		}

		StringBuilder builder = new StringBuilder();
		PLAYER_DATA.forEach((uuid, data) -> builder.append(uuid).append('=').append(data.serialize()).append('\n'));
		try {
			Files.writeString(saveFile, builder.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			OcckaWildMagic.LOGGER.warn("Could not save Wild Magic class data", exception);
		}
	}
}
