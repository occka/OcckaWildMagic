package wildmagic.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import wildmagic.server.ability.BardAbilities;
import wildmagic.server.state.WildMagicZones;
import wildmagic.OcckaWildMagic;
import wildmagic.classdata.AbilityDefinition;
import wildmagic.classdata.ClassProgression;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.network.WildMagicNetworking;
import net.minecraft.world.entity.player.Player;
import wildmagic.classdata.ArmorTier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class WildMagicServerState {
	private static final Identifier BARD_HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_fragile_performer");
	private static final Identifier WIZARD_HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "wizard_fragile_scholar");
	private static final double BARD_HEALTH_PENALTY = -4.0D;
	private static final double WIZARD_HEALTH_PENALTY = -12.0D;
	private static final Map<UUID, PlayerClassData> PLAYER_DATA = new ConcurrentHashMap<>();
	private static final Map<UUID, long[]> ABILITY_COOLDOWNS = new ConcurrentHashMap<>();
    private static final Identifier BARD_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_light_step");
private static final Identifier BARD_SWORD_DAMAGE_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_damage");
private static final Identifier BARD_SWORD_SPEED_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_speed");
private static final Identifier BARD_SWORD_REACH_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_reach");
	private static MinecraftServer currentServer;
	private static Path saveFile;

	private WildMagicServerState() {
	}

	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(WildMagicServerState::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(WildMagicServerState::save);
		ServerTickEvents.END_SERVER_TICK.register(WildMagicServerState::tickPlayers);
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClientSide() && world.getBlockState(hitResult.getBlockPos()).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)) {
				PlayerClassData data = player instanceof ServerPlayer serverPlayer ? get(serverPlayer) : PlayerClassData.EMPTY;
				if (!data.hasClass() || data.selectedClass() != WildMagicClass.WIZARD) {
					player.sendSystemMessage(Component.literal("Только волшебник может использовать стол зачарований"));
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
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
		WildMagicZones.clearPlayer(player);
		BardAbilities.clearPlayer(player);

		applyClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void clearClass(ServerPlayer player) {
		PLAYER_DATA.remove(player.getUUID());
		ABILITY_COOLDOWNS.remove(player.getUUID());
		WildMagicZones.clearPlayer(player);
		BardAbilities.clearPlayer(player);

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

		if (wouldChangeCooldownSlot(player, current, slot, abilityId)) {
			player.sendSystemMessage(Component.literal("Способность на перезарядке: слот нельзя изменить"));
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

		if (!canCastInCurrentArmor(player, current, ability)) {
			return;
		}

		// проверяем тишину
if (WildMagicZones.isInSilenceZone(player) && ability.manaCost() > 0) {
    player.sendSystemMessage(Component.literal("Тишина: заклинания недоступны"));
    return;
}

				// прерываем невидимость при касте любого заклинания кроме самой невидимости
if (!ability.id().equals("bard_invisibility")) {
    WildMagicZones.breakInvisibility(player);
}

		boolean used = switch (ability.id()) {
			case "bard_inspiration" -> BardAbilities.useBardInspiration(player);
case "bard_healing_word" -> BardAbilities.useBardHealingWord(player);
case "bard_sound_wave" -> BardAbilities.useBardSoundWave(player);
case "bard_charm" -> BardAbilities.useBardCharm(player);
case "bard_heroism" -> BardAbilities.useBardHeroism(player);
case "bard_invisibility" -> BardAbilities.useBardInvisibility(player);
case "bard_dispel" -> BardAbilities.useBardDispel(player);
case "bard_slow_zone" -> BardAbilities.useBardSlowZone(player);
case "bard_haste" -> BardAbilities.useBardHaste(player);
case "bard_feather_fall" -> BardAbilities.useBardFeatherFall(player);
case "bard_force_cage" -> BardAbilities.useBardForceCage(player);
case "bard_mordenkainen_sword" -> BardAbilities.useBardMordenkainenSword(player);
case "bard_heat_metal" -> BardAbilities.useBardHeatMetal(player);
case "bard_shatter" -> BardAbilities.useBardShatter(player);
case "bard_misty_step" -> BardAbilities.useBardMistyStep(player);
case "bard_hypnotic_pattern" -> BardAbilities.useBardHypnoticPattern(player);
case "bard_word_of_power" -> BardAbilities.useBardWordOfPower(player);
case "bard_dominate" -> BardAbilities.useBardDominate(player);
case "bard_silence" -> BardAbilities.useBardSilence(player);
case "bard_dimension_door" -> BardAbilities.useBardDimensionDoor(player);
case "bard_greater_invisibility" -> BardAbilities.useBardGreaterInvisibility(player);
case "warlock_mystic_charge" -> wildmagic.server.ability.WarlockAbilities.useMysticCharge(player);
case "wizard_fireball" -> wildmagic.server.ability.WizardAbilities.useFireball(player);
			default -> usePlaceholderAbility(player, ability);
		};
		if (!used) {
			return;
		}

		PlayerClassData updated = get(player).consumeMana(ability.manaCost());
		PLAYER_DATA.put(player.getUUID(), updated);
		setCooldown(player, slot, ClassProgression.effectiveCooldownSeconds(ability, updated));
		sync(player);
	}


	public static boolean isCharmed(LivingEntity attacker, Player target) {
		return WildMagicZones.isCharmed(attacker, target);
	}

	public static boolean isMordenkainenActive(ServerPlayer player) {
		return BardAbilities.isMordenkainenActive(player);
	}

	public static boolean isHeroismActive(ServerPlayer player) {
		return WildMagicZones.isHeroismActive(player);
	}

	public static void breakInvisibility(ServerPlayer player) {
		WildMagicZones.breakInvisibility(player);
	}

	public static boolean isInSilenceZone(ServerPlayer player) {
		return WildMagicZones.isInSilenceZone(player);
	}

	private static boolean canCastInCurrentArmor(ServerPlayer player, PlayerClassData data, AbilityDefinition ability) {
		if (ability.passive()) {
			return true;
		}

		ArmorTier maxArmor = getMaxArmorTier(player);
		ArmorTier allowed = switch (data.selectedClass()) {
			case WIZARD -> ArmorTier.LIGHT;
			case WARLOCK, SORCERER, BARD -> ArmorTier.MEDIUM;
			default -> ArmorTier.HEAVY;
		};

		if (maxArmor.ordinal() <= allowed.ordinal()) {
			return true;
		}

		player.sendSystemMessage(Component.literal("Броня мешает использовать способность: максимум " + allowed.name().toLowerCase(java.util.Locale.ROOT)));
		return false;
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
		long remainingTicks = remainingCooldownTicks(player, slot);
		if (remainingTicks <= 0) {
			return false;
		}

		player.sendSystemMessage(Component.literal("Способность на перезарядке: " + Math.ceil(remainingTicks / 20.0D) + "с"));
		return true;
	}

	private static long remainingCooldownTicks(ServerPlayer player, int slot) {
		long[] cooldowns = ABILITY_COOLDOWNS.get(player.getUUID());
		if (cooldowns == null || slot >= cooldowns.length) {
			return 0L;
		}

		long remainingTicks = cooldowns[slot] - player.level().getGameTime();
		return Math.max(0L, remainingTicks);
	}

	private static boolean wouldChangeCooldownSlot(ServerPlayer player, PlayerClassData current, int targetSlot, String abilityId) {
		String normalizedAbilityId = abilityId == null ? "" : abilityId;
		String currentAbilityId = current.activeAbility(targetSlot);
		if (!normalizedAbilityId.equals(currentAbilityId) && remainingCooldownTicks(player, targetSlot) > 0) {
			return true;
		}

		if (normalizedAbilityId.isBlank()) {
			return false;
		}

		for (int otherSlot = 0; otherSlot < PlayerClassData.ACTIVE_SLOT_COUNT; otherSlot++) {
			if (otherSlot == targetSlot) {
				continue;
			}

			if (normalizedAbilityId.equals(current.activeAbility(otherSlot)) && remainingCooldownTicks(player, otherSlot) > 0) {
				return true;
			}
		}

		return false;
	}

	private static void setCooldown(ServerPlayer player, int slot, int cooldownSeconds) {
		if (cooldownSeconds <= 0) {
			return;
		}

		long[] cooldowns = ABILITY_COOLDOWNS.computeIfAbsent(player.getUUID(), uuid -> new long[PlayerClassData.ACTIVE_SLOT_COUNT]);
		cooldowns[slot] = player.level().getGameTime() + (cooldownSeconds * 20L);
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

	public static void addAdvancementExp(ServerPlayer player, int amount) {
    PlayerClassData current = get(player);
    if (!current.hasClass()) {
        return;
    }

    PlayerClassData updated = current.withExp(current.exp() + amount);
    while (updated.level() < ClassProgression.MAX_LEVEL && updated.exp() >= updated.expRequiredForNextLevel()) {
        updated = updated.withExp(updated.exp() - updated.expRequiredForNextLevel()).withLevel(updated.level() + 1);
    }
    PLAYER_DATA.put(player.getUUID(), updated);
    applyClassPassives(player);
    sync(player);
    save(currentServer);
}

	public static void sync(ServerPlayer player) {
		long[] cooldowns = new long[PlayerClassData.ACTIVE_SLOT_COUNT];
		for (int slot = 0; slot < PlayerClassData.ACTIVE_SLOT_COUNT; slot++) {
			cooldowns[slot] = remainingCooldownTicks(player, slot);
		}
		WildMagicNetworking.sendClassData(player, get(player), cooldowns);
	}

	private static void tickPlayers(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		WildMagicZones.tickHeroism(server);
        BardAbilities.tickGreaterInvisibility(server);
        WildMagicZones.tickSlowZones(server);
        BardAbilities.tickDominate(server);
        BardAbilities.tickForceCage(server);
BardAbilities.tickMordenkainenSword(server);
        BardAbilities.tickHypnoticPattern(server);
		WildMagicZones.tickInvisibility(server);
		WildMagicZones.tickSilence(server);
		WildMagicZones.tickCharmed(currentServer);

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

	public static void fillMana(ServerPlayer player) {
    PlayerClassData current = get(player);
    if (!current.hasClass() || !current.selectedClass().usesMana()) return;
    PLAYER_DATA.put(player.getUUID(), current.withMana(current.maxMana()));
    sync(player);
}




private static void applyBardLightStep(ServerPlayer player) {
    var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
    if (speed != null && speed.getModifier(BARD_SPEED_MODIFIER_ID) == null) {
        speed.addPermanentModifier(new AttributeModifier(BARD_SPEED_MODIFIER_ID, 0.02D, AttributeModifier.Operation.ADD_VALUE));
    }
}

private static void removeBardLightStep(ServerPlayer player) {
    var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
    if (speed != null) speed.removeModifier(BARD_SPEED_MODIFIER_ID);
}

private static void applyBardCollegeOfSwords(ServerPlayer player, PlayerClassData data) {
boolean hasSword = player.getMainHandItem().getItem().getDescriptionId().contains("sword");

    var dmg = player.getAttribute(Attributes.ATTACK_DAMAGE);
    var speed = player.getAttribute(Attributes.ATTACK_SPEED);
    var reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);

    if (hasSword) {
        if (dmg != null && dmg.getModifier(BARD_SWORD_DAMAGE_ID) == null)
            dmg.addPermanentModifier(new AttributeModifier(BARD_SWORD_DAMAGE_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE));
        if (speed != null && speed.getModifier(BARD_SWORD_SPEED_ID) == null)
            speed.addPermanentModifier(new AttributeModifier(BARD_SWORD_SPEED_ID, 0.5D, AttributeModifier.Operation.ADD_VALUE));
        if (reach != null && reach.getModifier(BARD_SWORD_REACH_ID) == null)
            reach.addPermanentModifier(new AttributeModifier(BARD_SWORD_REACH_ID, 1.0D, AttributeModifier.Operation.ADD_VALUE));
    } else {
        removeBardCollegeOfSwords(player);
    }
}

private static void removeBardCollegeOfSwords(ServerPlayer player) {
    var dmg = player.getAttribute(Attributes.ATTACK_DAMAGE);
    var speed = player.getAttribute(Attributes.ATTACK_SPEED);
    var reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
    if (dmg != null) dmg.removeModifier(BARD_SWORD_DAMAGE_ID);
    if (speed != null) speed.removeModifier(BARD_SWORD_SPEED_ID);
    if (reach != null) reach.removeModifier(BARD_SWORD_REACH_ID);
}


public static ArmorTier getMaxArmorTier(ServerPlayer player) {
    ArmorTier max = ArmorTier.NONE;
    for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
            net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET}) {
        ArmorTier tier = ArmorTier.of(player.getItemBySlot(slot));
        if (tier.ordinal() > max.ordinal()) max = tier;
    }
    return max;
}


	private static void applyClassPassives(ServerPlayer player) {
    PlayerClassData data = get(player);
    if (data.hasClass() && data.selectedClass() == WildMagicClass.BARD) {
        applyBardHealthPenalty(player);
        removeWizardHealthPenalty(player);
        if (data.level() >= 5) applyBardLightStep(player);
        else removeBardLightStep(player);
        if (data.level() >= 10) applyBardCollegeOfSwords(player, data);
        else removeBardCollegeOfSwords(player);
    } else if (data.hasClass() && data.selectedClass() == WildMagicClass.WIZARD) {
        removeBardHealthPenalty(player);
        removeBardLightStep(player);
        removeBardCollegeOfSwords(player);
        applyWizardHealthPenalty(player);
    } else {
        removeBardHealthPenalty(player);
        removeWizardHealthPenalty(player);
        removeBardLightStep(player);
        removeBardCollegeOfSwords(player);
    }
}

private static void removeClassPassives(ServerPlayer player) {
    removeBardHealthPenalty(player);
    removeWizardHealthPenalty(player);
    removeBardLightStep(player);
    removeBardCollegeOfSwords(player);
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

	private static void applyWizardHealthPenalty(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getModifier(WIZARD_HEALTH_MODIFIER_ID) != null) {
			return;
		}

		maxHealth.addPermanentModifier(new AttributeModifier(WIZARD_HEALTH_MODIFIER_ID, WIZARD_HEALTH_PENALTY, AttributeModifier.Operation.ADD_VALUE));
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
	}

	private static void removeWizardHealthPenalty(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getModifier(WIZARD_HEALTH_MODIFIER_ID) == null) {
			return;
		}

		maxHealth.removeModifier(WIZARD_HEALTH_MODIFIER_ID);
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
