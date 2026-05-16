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
import net.minecraft.world.entity.player.Player;
import wildmagic.classdata.ArmorTier;
import java.util.List;

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
	private static final Map<UUID, Map<UUID, Long>> CHARMED_TARGETS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> HEROISM_TARGETS = new ConcurrentHashMap<>();
    private static final Identifier BARD_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_light_step");
private static final Identifier BARD_SWORD_DAMAGE_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_damage");
private static final Identifier BARD_SWORD_SPEED_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_speed");
private static final Map<UUID, Long> HYPNOTIC_PATTERN_CASTERS = new ConcurrentHashMap<>();
private static final Identifier BARD_SWORD_REACH_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_reach");
private static final Map<UUID, List<net.minecraft.core.BlockPos>> FORCE_CAGE_BLOCKS = new ConcurrentHashMap<>();
private static final Map<UUID, Long> FORCE_CAGE_EXPIRE = new ConcurrentHashMap<>();
private static final Set<UUID> MORDENKAINEN_PENDING_HITS = ConcurrentHashMap.newKeySet();
// key = бард
private static final Map<UUID, Long> MORDENKAINEN_SWORD = new ConcurrentHashMap<>();
private static final Map<UUID, UUID> DOMINATED_ENTITIES = new ConcurrentHashMap<>();
// key = цель, value = бард
private static final Map<UUID, Long> DOMINATE_EXPIRE = new ConcurrentHashMap<>();
private static final Map<UUID, Long> HYPNOTIZED_PLAYERS = new ConcurrentHashMap<>();
private static final Map<UUID, double[]> SLOW_ZONES = new ConcurrentHashMap<>();
// double[]: x, y, z, expireGameTime
private static final Map<UUID, Long> INVISIBLE_PLAYERS = new ConcurrentHashMap<>();
private static final net.minecraft.resources.Identifier HEROISM_DAMAGE_MODIFIER_ID = 
    net.minecraft.resources.Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "heroism_damage");
    private static final Map<UUID, Long> GREATER_INVISIBLE_PLAYERS = new ConcurrentHashMap<>();
	private static final Map<UUID, double[]> SILENCE_ZONES = new ConcurrentHashMap<>();
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
		CHARMED_TARGETS.remove(player.getUUID());
        HYPNOTIZED_PLAYERS.remove(player.getUUID());
        GREATER_INVISIBLE_PLAYERS.remove(player.getUUID());
        MORDENKAINEN_SWORD.remove(player.getUUID());
        DOMINATED_ENTITIES.entrySet().removeIf(e -> e.getValue().equals(player.getUUID()));
DOMINATE_EXPIRE.entrySet().removeIf(e -> DOMINATED_ENTITIES.containsKey(e.getKey()) == false);
        HYPNOTIC_PATTERN_CASTERS.remove(player.getUUID());
		HEROISM_TARGETS.remove(player.getUUID());
INVISIBLE_PLAYERS.remove(player.getUUID());
		applyClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void clearClass(ServerPlayer player) {
		PLAYER_DATA.remove(player.getUUID());
		ABILITY_COOLDOWNS.remove(player.getUUID());
        HYPNOTIZED_PLAYERS.remove(player.getUUID());
		CHARMED_TARGETS.remove(player.getUUID());
        GREATER_INVISIBLE_PLAYERS.remove(player.getUUID());
        MORDENKAINEN_SWORD.remove(player.getUUID());
        DOMINATED_ENTITIES.entrySet().removeIf(e -> e.getValue().equals(player.getUUID()));
DOMINATE_EXPIRE.entrySet().removeIf(e -> DOMINATED_ENTITIES.containsKey(e.getKey()) == false);
        HYPNOTIC_PATTERN_CASTERS.remove(player.getUUID());
		HEROISM_TARGETS.remove(player.getUUID());
INVISIBLE_PLAYERS.remove(player.getUUID());
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

                if (current.selectedClass() == WildMagicClass.BARD && ability.manaCost() > 0) {
    if (getMaxArmorTier(player) == ArmorTier.HEAVY) {
        player.sendSystemMessage(Component.literal("Тяжёлая броня мешает использовать заклинания"));
        return;
    }
}

		// проверяем тишину
if (isInSilenceZone(player) && ability.manaCost() > 0) {
    player.sendSystemMessage(Component.literal("Тишина: заклинания недоступны"));
    return;
}

				// прерываем невидимость при касте любого заклинания кроме самой невидимости
if (!ability.id().equals("bard_invisibility")) {
    breakInvisibility(player);
}

		boolean used = switch (ability.id()) {
			case "bard_inspiration" -> useBardInspiration(player);
case "bard_healing_word" -> useBardHealingWord(player);
case "bard_sound_wave" -> useBardSoundWave(player);
case "bard_charm" -> useBardCharm(player);
case "bard_heroism" -> useBardHeroism(player);
case "bard_invisibility" -> useBardInvisibility(player);
case "bard_dispel" -> useBardDispel(player);
case "bard_slow_zone" -> useBardSlowZone(player);
case "bard_haste" -> useBardHaste(player);
case "bard_feather_fall" -> useBardFeatherFall(player);
case "bard_force_cage" -> useBardForceCage(player);
case "bard_mordenkainen_sword" -> useBardMordenkainenSword(player);
case "bard_heat_metal" -> useBardHeatMetal(player);
case "bard_shatter" -> useBardShatter(player);
case "bard_misty_step" -> useBardMistyStep(player);
case "bard_hypnotic_pattern" -> useBardHypnoticPattern(player);
case "bard_silence" -> useBardSilence(player);
case "bard_dimension_door" -> useBardDimensionDoor(player);
case "bard_greater_invisibility" -> useBardGreaterInvisibility(player);
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

	public static boolean isCharmed(LivingEntity attacker, Player target) {
    if (!(target instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return false;
    Map<UUID, Long> charmed = CHARMED_TARGETS.get(serverPlayer.getUUID());
    if (charmed == null) return false;

    Long expireTime = charmed.get(attacker.getUUID());
    if (expireTime == null) return false;

    if (serverPlayer.level().getGameTime() > expireTime) {
        charmed.remove(attacker.getUUID());
        return false;
    }

    return true;
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

    // только игроки, без партиклов
    for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, area)) {
        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1), player);
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 600, 5), player);
    }

		level.sendParticles(ParticleTypes.NOTE, player.getX(), player.getY() + 1.2D, player.getZ(), 32, 1.5D, 0.8D, 1.5D, 0.1D);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HARP.value(),
        net.minecraft.sounds.SoundSource.PLAYERS,
        1.0F, 1.2F);

		return true;
	}

    private static boolean useBardHaste(ServerPlayer player) {
    ServerLevel level = player.level();
    AABB area = player.getBoundingBox().inflate(5.0D);
    for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, area)) {
        target.addEffect(new MobEffectInstance(MobEffects.SPEED, 20 * 90, 2, false, false), player);
    }
    level.sendParticles(ParticleTypes.ENCHANT,
            player.getX(), player.getY() + 1.0D, player.getZ(),
            40, 1.5D, 0.5D, 1.5D, 0.1D);
    level.playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 1.4F);
    return true;
}

private static boolean useBardFeatherFall(ServerPlayer player) {
    ServerLevel level = player.level();
    AABB area = player.getBoundingBox().inflate(5.0D);
    for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, area)) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 20 * 60, 1, false, false), player);
    }
    level.sendParticles(ParticleTypes.CLOUD,
            player.getX(), player.getY() + 1.5D, player.getZ(),
            30, 1.2D, 0.3D, 1.2D, 0.02D);
    level.playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.ELYTRA_FLYING,
            net.minecraft.sounds.SoundSource.PLAYERS,
            0.7F, 1.6F);
    return true;
}

private static boolean useBardHeatMetal(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 12.0D);
    if (target == null) {
        player.sendSystemMessage(Component.literal("Цель не найдена"));
        return false;
    }

    // проверяем броню цели
    ArmorTier maxTier = ArmorTier.NONE;
    for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
            net.minecraft.world.entity.EquipmentSlot.HEAD,
            net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,
            net.minecraft.world.entity.EquipmentSlot.FEET}) {
        ArmorTier tier = ArmorTier.of(target.getItemBySlot(slot));
        if (tier.ordinal() > maxTier.ordinal()) maxTier = tier;
    }

    if (maxTier == ArmorTier.NONE || maxTier == ArmorTier.LIGHT) {
        player.sendSystemMessage(Component.literal("Цель не в металлической броне"));
        return false;
    }

    float damage = 2.0F + player.getRandom().nextFloat() * 10.0F;
    target.hurtServer(level, player.damageSources().playerAttack(player), damage);
   target.igniteForSeconds(5);

    level.sendParticles(ParticleTypes.FLAME,
            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
            20, 0.3D, 0.5D, 0.3D, 0.05D);
    level.sendParticles(ParticleTypes.LAVA,
            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
            5, 0.2D, 0.3D, 0.2D, 0.0D);
    level.playSound(null, target.getX(), target.getY(), target.getZ(),
            net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 0.8F);
    return true;
}

private static boolean useBardShatter(ServerPlayer player) {
    ServerLevel level = player.level();
    Vec3 target = raycastBlock(player, 20.0D);

    AABB area = new AABB(
            target.x - 5.0D, target.y - 5.0D, target.z - 5.0D,
            target.x + 5.0D, target.y + 5.0D, target.z + 5.0D);

    int hit = 0;
    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
            e -> e.distanceTo(player) > 0.1D)) {
        if (entity.position().distanceTo(target) <= 5.0D) {
            float damage = 4.0F + player.getRandom().nextFloat() * 10.0F;
            entity.hurtServer(level, player.damageSources().playerAttack(player), damage);
            hit++;
        }
    }

    // партиклы взрыва
    level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
            target.x, target.y + 1.0D, target.z,
            1, 0.0D, 0.0D, 0.0D, 0.0D);
    level.sendParticles(ParticleTypes.POOF,
            target.x, target.y + 1.0D, target.z,
            40, 2.0D, 2.0D, 2.0D, 0.1D);
    level.playSound(null, target.x, target.y, target.z,
            net.minecraft.sounds.SoundEvents.GLASS_BREAK,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.5F, 0.6F);
    return true;
}



	private static boolean useBardInvisibility(ServerPlayer player) {
    INVISIBLE_PLAYERS.put(player.getUUID(), player.level().getGameTime() + 30 * 20L);
    player.setInvisible(true);

    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT,
            net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
    return true;
}

private static boolean useBardWordOfPower(ServerPlayer player) {
    ServerLevel level = player.level();
    AABB area = player.getBoundingBox().inflate(5.0D);
    List<ServerPlayer> targets = level.getEntitiesOfClass(ServerPlayer.class, area);

    int count = targets.size();
    if (count == 0) return false;

    // 160 хп делим поровну, переводим в уровень эффекта (каждый уровень = 4hp)
    int hpEach = (int) Math.ceil(160.0 / count);
    int amplifier = Math.max(0, (hpEach / 4) - 1);

    for (ServerPlayer target : targets) {
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 20 * 600, amplifier, false, false), player);
    }

    level.sendParticles(ParticleTypes.HEART,
            player.getX(), player.getY() + 1.5D, player.getZ(),
            40, 2.0D, 1.0D, 2.0D, 0.1D);
    level.playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.RAID_HORN,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.5F, 1.0F);
    return true;
}

private static boolean useBardDominate(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 25.0D);
    if (target == null) {
        player.sendSystemMessage(Component.literal("Цель не найдена"));
        return false;
    }

    long expireTime = level.getGameTime() + 10 * 20L;
    DOMINATED_ENTITIES.put(target.getUUID(), player.getUUID());
    DOMINATE_EXPIRE.put(target.getUUID(), expireTime);

    // телепортируем цель справа от барда
    Vec3 right = new Vec3(
            Math.cos(Math.toRadians(player.getYRot() - 90)),
            0,
            Math.sin(Math.toRadians(player.getYRot() - 90))).normalize();
    Vec3 spawnPos = player.position().add(right.scale(1.5D));
    target.teleportTo(spawnPos.x, spawnPos.y, spawnPos.z);

    // если это моб — заставляем атаковать ближайшего врага барда
    if (target instanceof net.minecraft.world.entity.Mob mob) {
        mob.setTarget(null);
    }

    level.sendParticles(ParticleTypes.ENCHANT,
            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
            30, 0.5D, 0.8D, 0.5D, 0.1D);
    level.sendParticles(ParticleTypes.WITCH,
            target.getX(), target.getY() + target.getBbHeight(), target.getZ(),
            10, 0.3D, 0.3D, 0.3D, 0.05D);
    level.playSound(null, target.getX(), target.getY(), target.getZ(),
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 0.4F);
    return true;
}

	private static boolean useBardHealingWord(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 12.0D);

    LivingEntity healTarget = target != null ? target : player;
    float heal = 2.0F + player.getRandom().nextFloat() * 6.0F + get(player).level();
    healTarget.heal(heal);

    level.sendParticles(ParticleTypes.HEART,
            healTarget.getX(), healTarget.getY() + healTarget.getBbHeight() + 0.3D, healTarget.getZ(),
            6, 0.3D, 0.3D, 0.3D, 0.05D);
    level.playSound(null, healTarget.getX(), healTarget.getY(), healTarget.getZ(),
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HARP.value(),
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 1.8F);
    return true;
}

private static boolean useBardCharm(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 20.0D);
    if (target == null) {
        return false;
    }

    CHARMED_TARGETS.computeIfAbsent(player.getUUID(), k -> new ConcurrentHashMap<>())
            .put(target.getUUID(), level.getGameTime() + 15 * 20L);

    level.sendParticles(ParticleTypes.ENCHANT,
            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
            20, 0.5D, 0.8D, 0.5D, 0.1D);
    level.playSound(null, target.getX(), target.getY(), target.getZ(),
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 0.8F);
    return true;
}

private static boolean useBardHeroism(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 12.0D);
    long expireTime = level.getGameTime() + 20 * 20L;

    applyHeroism(player, level, expireTime);
    if (target instanceof ServerPlayer targetPlayer) {
        applyHeroism(targetPlayer, level, expireTime);
    }
    return true;
}

private static void applyHeroism(ServerPlayer player, ServerLevel level, long expireTime) {
    HEROISM_TARGETS.put(player.getUUID(), expireTime);

    net.minecraft.world.entity.ai.attributes.AttributeInstance dmg = 
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
    if (dmg != null && dmg.getModifier(HEROISM_DAMAGE_MODIFIER_ID) == null) {
        dmg.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
            HEROISM_DAMAGE_MODIFIER_ID, 3.0D,
            net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
    }

    level.sendParticles(ParticleTypes.WAX_ON,
            player.getX(), player.getY() + player.getBbHeight() + 0.5D, player.getZ(),
            12, 0.4D, 0.2D, 0.4D, 0.05D);
    level.playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
            net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
}

private static void removeHeroism(ServerPlayer player) {
    net.minecraft.world.entity.ai.attributes.AttributeInstance dmg =
        player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
    if (dmg != null) {
        dmg.removeModifier(HEROISM_DAMAGE_MODIFIER_ID);
    }
}


public static boolean isInSilenceZone(ServerPlayer player) {
    Vec3 pos = player.position();
    long now = player.level().getGameTime();
    return SILENCE_ZONES.values().stream().anyMatch(zone ->
            now <= zone[3] &&
            pos.distanceTo(new Vec3(zone[0], zone[1], zone[2])) <= 5.0D);
}

private static boolean useBardDispel(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 20.0D);
    if (target == null) {
        player.sendSystemMessage(Component.literal("Цель не найдена"));
        return false;
    }

    target.removeAllEffects();

    level.sendParticles(ParticleTypes.WITCH,
            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
            30, 0.4D, 0.6D, 0.4D, 0.1D);
    level.playSound(null, target.getX(), target.getY(), target.getZ(),
            net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 1.6F);
    return true;
}

private static boolean useBardSlowZone(ServerPlayer player) {
    ServerLevel level = player.level();
    Vec3 target = raycastBlock(player, 30.0D);

    UUID zoneId = UUID.randomUUID();
    SLOW_ZONES.put(zoneId, new double[]{
            target.x, target.y, target.z,
            level.getGameTime() + 15 * 20L
    });

    // границы зоны — рёбра куба партиклами
    drawZoneBorder(level, target, 4.0D);

    level.playSound(null, target.x, target.y, target.z,
            net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE,
            net.minecraft.sounds.SoundSource.PLAYERS,
            0.6F, 1.2F);
    return true;
}

private static void drawZoneBorder(ServerLevel level, Vec3 center, double size) {
    double half = size / 2.0D;
    double minX = center.x - half, maxX = center.x + half;
    double minY = center.y, maxY = center.y + size;
    double minZ = center.z - half, maxZ = center.z + half;

    // рёбра по X
    for (double x = minX; x <= maxX; x += 0.25D) {
        spawnBorderParticle(level, x, minY, minZ);
        spawnBorderParticle(level, x, minY, maxZ);
        spawnBorderParticle(level, x, maxY, minZ);
        spawnBorderParticle(level, x, maxY, maxZ);
    }
    // рёбра по Z
    for (double z = minZ; z <= maxZ; z += 0.25D) {
        spawnBorderParticle(level, minX, minY, z);
        spawnBorderParticle(level, maxX, minY, z);
        spawnBorderParticle(level, minX, maxY, z);
        spawnBorderParticle(level, maxX, maxY, z);
    }
    // рёбра по Y
    for (double y = minY; y <= maxY; y += 0.25D) {
        spawnBorderParticle(level, minX, y, minZ);
        spawnBorderParticle(level, maxX, y, minZ);
        spawnBorderParticle(level, minX, y, maxZ);
        spawnBorderParticle(level, maxX, y, maxZ);
    }
}

private static void spawnBorderParticle(ServerLevel level, double x, double y, double z) {
    level.sendParticles(ParticleTypes.WITCH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
}

private static void tickSlowZones(MinecraftServer server) {
    if (server.getTickCount() % 20 != 0) return;
    long now = server.overworld().getGameTime();

    SLOW_ZONES.entrySet().removeIf(entry -> now > entry.getValue()[3]);

    for (double[] zone : SLOW_ZONES.values()) {
        ServerLevel level = server.overworld();
        Vec3 center = new Vec3(zone[0], zone[1], zone[2]);
        double half = 2.0D;

        // перерисовываем границы каждую секунду
        drawZoneBorder(level, center, 4.0D);

        // применяем эффекты всем внутри
        AABB box = new AABB(
                center.x - half, zone[1], center.z - half,
                center.x + half, zone[1] + 4.0D, center.z + half);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box)) {
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 2, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 1, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 1, false, false));
        }
    }
}

private static void tickSilence(MinecraftServer server) {
    if (server.getTickCount() % 20 != 0) return;
    long now = server.overworld().getGameTime();

    SILENCE_ZONES.entrySet().removeIf(entry -> now > entry.getValue()[3]);

    // тиковые партиклы внутри активных зон
    for (double[] zone : SILENCE_ZONES.values()) {
        ServerLevel level = server.overworld();
        level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
                zone[0], zone[1] + 1.0D, zone[2],
                8, 2.0D, 2.0D, 2.0D, 0.02D);
    }
}

private static boolean useBardForceCage(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 25.0D);
    if (target == null) {
        player.sendSystemMessage(Component.literal("Цель не найдена"));
        return false;
    }

    net.minecraft.core.BlockPos center = target.blockPosition();
    List<net.minecraft.core.BlockPos> cageBlocks = new java.util.ArrayList<>();

    // строим клетку 5x5x5 из красного стекла вокруг цели
    for (int dx = -2; dx <= 2; dx++) {
        for (int dy = 0; dy <= 4; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                boolean isWall = dx == -2 || dx == 2 || dz == -2 || dz == 2 || dy == 0 || dy == 4;
                if (!isWall) continue;
                net.minecraft.core.BlockPos pos = center.offset(dx, dy, dz);
                // не трогаем твёрдые блоки
                if (!level.getBlockState(pos).isAir()) continue;
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
                cageBlocks.add(pos);
            }
        }
    }

    UUID cageId = UUID.randomUUID();
    FORCE_CAGE_BLOCKS.put(cageId, cageBlocks);
    FORCE_CAGE_EXPIRE.put(cageId, level.getGameTime() + 25 * 20L);

    // добавляем центр клетки в зону тишины
    SILENCE_ZONES.put(cageId, new double[]{
            center.getX(), center.getY(), center.getZ(),
            level.getGameTime() + 25 * 20L
    });

    // партиклы
    for (int i = 0; i < 32; i++) {
        double angle = i / 32.0D * 2 * Math.PI;
        level.sendParticles(ParticleTypes.WITCH,
        center.getX() + Math.cos(angle) * 2.5D,
        center.getY() + 2.0D,
        center.getZ() + Math.sin(angle) * 2.5D,
        1, 0.0D, 0.0D, 0.0D, 0.0D);
    }
    level.playSound(null, center.getX(), center.getY(), center.getZ(),
            net.minecraft.sounds.SoundEvents.GLASS_PLACE,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.5F, 0.5F);
    return true;
}

private static void tickForceCage(MinecraftServer server) {
    if (server.getTickCount() % 20 != 0) return;
    long now = server.overworld().getGameTime();

    FORCE_CAGE_EXPIRE.entrySet().removeIf(entry -> {
        if (now > entry.getValue()) {
            // убираем блоки
            List<net.minecraft.core.BlockPos> blocks = FORCE_CAGE_BLOCKS.remove(entry.getKey());
            if (blocks != null) {
                for (net.minecraft.core.BlockPos pos : blocks) {
                    ServerLevel level = server.overworld();
                    if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS)) {
                        level.removeBlock(pos, false);
                    }
                }
            }
            SILENCE_ZONES.remove(entry.getKey());
            return true;
        }
        return false;
    });
}


	private static boolean useBardSoundWave(ServerPlayer player) {
    ServerLevel level = player.level();
    Vec3 look = player.getLookAngle().normalize();
    Set<LivingEntity> hitEntities = new HashSet<>();

    for (int step = 1; step <= 6; step++) {
        Vec3 center = player.position().add(0.0D, 1.0D, 0.0D).add(look.scale(step));

        // партиклы похожие на звуковую волну
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                center.x, center.y, center.z,
                6, 0.4D, 0.4D, 0.4D, 0.08D);
        level.sendParticles(ParticleTypes.SONIC_BOOM,
                center.x, center.y, center.z,
                1, 0.0D, 0.0D, 0.0D, 0.0D);

        AABB waveBox = new AABB(
                center.x - 1.5D, center.y - 1.5D, center.z - 1.5D,
                center.x + 1.5D, center.y + 1.5D, center.z + 1.5D);

        for (LivingEntity target : level.getEntitiesOfClass(
                LivingEntity.class, waveBox, entity -> entity != player)) {
            if (hitEntities.add(target)) {
                float damage = 4.0F + (player.getRandom().nextFloat() * 6.0F);
                target.hurtServer(level, player.damageSources().playerAttack(player), damage);
                target.push(look.x * 0.8D, 0.45D, look.z * 0.8D);
            }
        }
    }

    // звук грома
    level.playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
            net.minecraft.sounds.SoundSource.PLAYERS,
            0.8F, 1.4F);

    return true;
}

public static boolean isMordenkainenActive(ServerPlayer player) {
    Long expire = MORDENKAINEN_SWORD.get(player.getUUID());
    if (expire == null) return false;
    if (player.level().getGameTime() > expire) {
        MORDENKAINEN_SWORD.remove(player.getUUID());
        return false;
    }
    return true;
}

private static boolean useBardMordenkainenSword(ServerPlayer player) {
    ServerLevel level = player.level();
    MORDENKAINEN_SWORD.put(player.getUUID(), level.getGameTime() + 60 * 20L);

    // призрачный меч — светящийся END_ROD над головой
    level.sendParticles(ParticleTypes.END_ROD,
            player.getX(), player.getY() + 2.2D, player.getZ(),
            20, 0.3D, 0.1D, 0.3D, 0.05D);
    level.sendParticles(ParticleTypes.ENCHANT,
            player.getX(), player.getY() + 2.0D, player.getZ(),
            15, 0.4D, 0.2D, 0.4D, 0.1D);
    level.playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 0.7F);
    return true;
}

private static void tickMordenkainenSword(MinecraftServer server) {
    if (server.getTickCount() % 3 != 0) return;
    long now = server.overworld().getGameTime();

    MORDENKAINEN_SWORD.entrySet().removeIf(entry -> now > entry.getValue());

    for (Map.Entry<UUID, Long> entry : MORDENKAINEN_SWORD.entrySet()) {
        ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
        if (player == null) continue;

        ServerLevel level = player.level();
        double time = server.getTickCount() / 10.0D;

        // направление меча — вращается вокруг игрока
        double orbitX = Math.cos(time) * 1.0D;
        double orbitZ = Math.sin(time) * 1.0D;
        double baseX = player.getX() + orbitX;
        double baseY = player.getY() + 1.2D;
        double baseZ = player.getZ() + orbitZ;

        // направление лезвия — перпендикулярно орбите, вертикально
        for (int i = 0; i < 8; i++) {
            double t = i / 7.0D;
            level.sendParticles(ParticleTypes.END_ROD,
                    baseX, baseY + t * 1.2D, baseZ,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        // рукоять
        level.sendParticles(ParticleTypes.ENCHANT,
                baseX + Math.cos(time + Math.PI / 2) * 0.25D,
                baseY,
                baseZ + Math.sin(time + Math.PI / 2) * 0.25D,
                2, 0.05D, 0.0D, 0.05D, 0.0D);
    }
}

private static boolean useBardDimensionDoor(ServerPlayer player) {
    ServerLevel level = player.level();
    Vec3 start = player.getEyePosition();
    Vec3 look = player.getLookAngle().normalize();

    // собираем союзника в радиусе 1 блока
    AABB nearBox = player.getBoundingBox().inflate(2.0D);
    ServerPlayer companion = level.getEntitiesOfClass(ServerPlayer.class, nearBox,
            p -> p != player).stream().findFirst().orElse(null);

    // ищем точку телепорта — сначала до 150 без стен
    Vec3 target = null;
    for (int i = 1; i <= 150; i++) {
        Vec3 step = start.add(look.scale(i));
        net.minecraft.world.level.ClipContext clip = new net.minecraft.world.level.ClipContext(
                start, step,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player);
        net.minecraft.world.phys.BlockHitResult hit = level.clip(clip);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            // упёрлись в стену — ищем свободное место за ней до 100 блоков дальше
            target = findSafeSpotThrough(level, player, start, look, i, 100);
            break;
        }
        target = step;
    }

    if (target == null) {
        player.sendSystemMessage(Component.literal("Нет безопасного места для телепорта"));
        return false;
    }

    Vec3 finalTarget = target;

    // партиклы на старте
    drawTeleportCircle(level, player.position().add(0, 0.1D, 0));

    // телепортируем
    double tx = finalTarget.x;
    double ty = finalTarget.y - 1.0D;
    double tz = finalTarget.z;
    player.teleportTo(tx, ty, tz);
    if (companion != null) companion.teleportTo(tx + 0.8D, ty, tz);

    // партиклы на финише
    drawTeleportCircle(level, new Vec3(tx, ty + 0.1D, tz));

    level.playSound(null, tx, ty, tz,
            net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 0.8F);
    return true;
}

private static Vec3 findSafeSpotThrough(ServerLevel level, ServerPlayer player,
        Vec3 start, Vec3 look, int wallStart, int maxExtra) {
    for (int i = wallStart + 1; i <= wallStart + maxExtra; i++) {
        Vec3 step = start.add(look.scale(i));
        net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(
                step.x, step.y - 1.0D, step.z);

        // проверяем что блок под ногами твёрдый, а на уровне тела и головы свободно
        net.minecraft.core.BlockPos bodyPos = pos.above();
        net.minecraft.core.BlockPos headPos = pos.above(2);

        boolean floorSolid = level.getBlockState(pos).isSolid();
        boolean bodyFree = level.getBlockState(bodyPos).isAir();
        boolean headFree = level.getBlockState(headPos).isAir();

        // проверка на лаву/огонь под ногами
        net.minecraft.world.level.block.state.BlockState floor = level.getBlockState(pos);
        boolean floorDangerous = floor.liquid() || floor.is(net.minecraft.tags.BlockTags.FIRE);

        if (floorSolid && bodyFree && headFree && !floorDangerous) {
            return new Vec3(step.x, step.y, step.z);
        }
    }
    return null;
}

private static void drawTeleportCircle(ServerLevel level, Vec3 center) {
    int points = 48;
    double radius = 2.5D;
    for (int i = 0; i < points; i++) {
        double angle = i / (double) points * 2 * Math.PI;
        level.sendParticles(ParticleTypes.PORTAL,
                center.x + Math.cos(angle) * radius,
                center.y + 0.1D,
                center.z + Math.sin(angle) * radius,
                2, 0.05D, 0.05D, 0.05D, 0.02D);
    }
    // второй внутренний круг
    double innerRadius = 1.3D;
    for (int i = 0; i < 24; i++) {
        double angle = i / 24.0D * 2 * Math.PI;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                center.x + Math.cos(angle) * innerRadius,
                center.y + 0.1D,
                center.z + Math.sin(angle) * innerRadius,
                1, 0.0D, 0.1D, 0.0D, 0.01D);
    }
    // столб вверх
    for (double y = 0.0D; y <= 2.5D; y += 0.2D) {
        level.sendParticles(ParticleTypes.PORTAL,
                center.x, center.y + y, center.z,
                1, 0.15D, 0.0D, 0.15D, 0.01D);
    }
}

private static boolean useBardGreaterInvisibility(ServerPlayer player) {
    ServerLevel level = player.level();
    long expireTime = level.getGameTime() + 25 * 20L;

    // бард
    GREATER_INVISIBLE_PLAYERS.put(player.getUUID(), expireTime);
    player.setInvisible(true);

    // союзники в радиусе 1 блока
    AABB nearBox = player.getBoundingBox().inflate(2.0D);
    for (ServerPlayer ally : level.getEntitiesOfClass(ServerPlayer.class, nearBox, p -> p != player)) {
        GREATER_INVISIBLE_PLAYERS.put(ally.getUUID(), expireTime);
        ally.setInvisible(true);
    }

    level.sendParticles(ParticleTypes.WITCH,
            player.getX(), player.getY() + 1.0D, player.getZ(),
            20, 0.5D, 0.5D, 0.5D, 0.05D);
    level.playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT,
            net.minecraft.sounds.SoundSource.PLAYERS,
            0.6F, 0.6F);
    return true;
}

private static void tickGreaterInvisibility(MinecraftServer server) {
    if (server.getTickCount() % 20 != 0) return;
    GREATER_INVISIBLE_PLAYERS.entrySet().removeIf(entry -> {
        ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
        if (player == null) return true;
        if (player.level().getGameTime() > entry.getValue()) {
            player.setInvisible(false);
            return true;
        }
        return false;
    });
}

private static boolean useBardHypnoticPattern(ServerPlayer player) {
    ServerLevel level = player.level();
    long expireTime = level.getGameTime() + 12 * 20L;
    HYPNOTIC_PATTERN_CASTERS.put(player.getUUID(), expireTime);
    Vec3 center = player.position().add(0, 3.0D, 0);

    // применяем к игрокам в радиусе 25 блоков включая моб
    AABB area = player.getBoundingBox().inflate(25.0D);
    for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
        if (entity == player) continue;

        // замедление 255 = полная остановка
        entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12 * 20, 255, false, false));

        if (entity instanceof ServerPlayer target) {
            HYPNOTIZED_PLAYERS.put(target.getUUID(), expireTime);
            // разворачиваем голову в сторону спирали
            Vec3 dir = center.subtract(target.getEyePosition()).normalize();
            float yaw = (float)(Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            float pitch = (float)(Math.toDegrees(-Math.asin(dir.y)));
            target.teleportTo(target.getX(), target.getY(), target.getZ());
            target.setYRot(yaw);
            target.setXRot(pitch);
        } else if (entity instanceof net.minecraft.world.entity.Mob mob) {
    mob.getLookControl().setLookAt(center.x, center.y, center.z);
}
    }

    level.playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.5F, 0.5F);

    return true;
}

private static void tickHypnoticPattern(MinecraftServer server) {
    if (server.getTickCount() % 2 != 0) return;

    long now = server.overworld().getGameTime();

    // чистим протухших
    HYPNOTIZED_PLAYERS.entrySet().removeIf(entry -> {
        ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
        if (p == null) return true;
        return p.level().getGameTime() > entry.getValue();
    });
    HYPNOTIC_PATTERN_CASTERS.entrySet().removeIf(entry -> now > entry.getValue());

    // рисуем спираль над каждым активным кастующим
    for (Map.Entry<UUID, Long> entry : HYPNOTIC_PATTERN_CASTERS.entrySet()) {
        ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
        if (player == null) continue;

        ServerLevel level = player.level();
        AABB area = player.getBoundingBox().inflate(25.0D);
        Vec3 spiralCenter = new Vec3(player.getX(), player.getY() + 3.0D, player.getZ());

        // разворачиваем загипнотизированных игроков
        for (ServerPlayer hypnotized : level.getEntitiesOfClass(ServerPlayer.class, area)) {
            if (!HYPNOTIZED_PLAYERS.containsKey(hypnotized.getUUID())) continue;
            Vec3 dir = spiralCenter.subtract(hypnotized.getEyePosition()).normalize();
            float yaw = (float)(Math.toDegrees(Math.atan2(-dir.x, dir.z)));
            float pitch = (float)(Math.toDegrees(-Math.asin(dir.y)));
            hypnotized.setYRot(yaw);
            hypnotized.setXRot(pitch);
        }

        // спираль
        double baseY = player.getY() + 3.0D;
        double time = server.getTickCount() / 5.0D;
        for (int arm = 0; arm < 3; arm++) {
            double armOffset = arm * (2 * Math.PI / 3);
            for (int i = 0; i < 12; i++) {
                double t = i / 12.0D;
                double radius = t * 3.0D;
                double angle = time + t * 4 * Math.PI + armOffset;
                double px = player.getX() + Math.cos(angle) * radius;
                double py = baseY + t * 1.5D;
                double pz = player.getZ() + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.ENCHANT, px, py, pz, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
        }
        level.sendParticles(ParticleTypes.END_ROD,
                player.getX(), baseY + 1.5D, player.getZ(),
                3, 0.1D, 0.1D, 0.1D, 0.02D);
    }
}

private static void tickDominate(MinecraftServer server) {
    if (server.getTickCount() % 10 != 0) return; // каждые 10 тиков

    long now = server.overworld().getGameTime();

    DOMINATE_EXPIRE.entrySet().removeIf(entry -> {
        if (now > entry.getValue()) {
            DOMINATED_ENTITIES.remove(entry.getKey());
            return true;
        }
        return false;
    });

    for (Map.Entry<UUID, UUID> entry : DOMINATED_ENTITIES.entrySet()) {
        ServerPlayer bard = server.getPlayerList().getPlayer(entry.getValue());
        if (bard == null) continue;

        ServerLevel level = bard.level();

        // ищем цель по UUID среди живых существ рядом
        LivingEntity dominated = null;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                bard.getBoundingBox().inflate(30.0D))) {
            if (e.getUUID().equals(entry.getKey())) {
                dominated = e;
                break;
            }
        }
        if (dominated == null || !dominated.isAlive()) {
            DOMINATED_ENTITIES.remove(entry.getKey());
            DOMINATE_EXPIRE.remove(entry.getKey());
            continue;
        }

        // двигаем цель за бардом — телепортируем если далеко
        double dist = dominated.distanceTo(bard);
        if (dist > 4.0D) {
            Vec3 right = new Vec3(
                    Math.cos(Math.toRadians(bard.getYRot() - 90)),
                    0,
                    Math.sin(Math.toRadians(bard.getYRot() - 90))).normalize();
            Vec3 followPos = bard.position().add(right.scale(1.5D));
            dominated.teleportTo(followPos.x, followPos.y, followPos.z);
        }

        if (dominated instanceof net.minecraft.world.entity.Mob mob) {
    LivingEntity bardTarget = bard.getLastHurtMob();
    if (bardTarget == null) {
        // атакуем ближайшего не-барда
        bardTarget = level.getEntitiesOfClass(LivingEntity.class,
                mob.getBoundingBox().inflate(10.0D),
                e -> e != mob && e != bard && e.isAlive())
                .stream().min(java.util.Comparator.comparingDouble(e -> e.distanceTo(mob)))
                .orElse(null);
    }
    if (bardTarget != null && bardTarget.isAlive()) {
        mob.setTarget(bardTarget);
        if (mob instanceof net.minecraft.world.entity.monster.Monster monster) {
            monster.setAggressive(true);
        }
    }
    // телепортируем если далеко
    double mobDist = mob.distanceTo(bard);
if (mobDist > 6.0D) {
        Vec3 right = new Vec3(
                Math.cos(Math.toRadians(bard.getYRot() - 90)),
                0,
                Math.sin(Math.toRadians(bard.getYRot() - 90))).normalize();
        Vec3 followPos = bard.position().add(right.scale(1.5D));
        mob.teleportTo(followPos.x, followPos.y, followPos.z);
    }
}

        // если это игрок — наносим доп урон ближайшему врагу барда
        if (dominated instanceof ServerPlayer dominatedPlayer) {
            LivingEntity bardTarget = bard.getLastHurtMob();
            if (bardTarget != null && bardTarget.isAlive()
                    && bardTarget.distanceTo(dominatedPlayer) < 4.0D) {
                bardTarget.hurtServer(level,
                        dominatedPlayer.damageSources().playerAttack(dominatedPlayer), 4.0F);
            }
        }

        // партиклы над подчинённым
        level.sendParticles(ParticleTypes.WITCH,
                dominated.getX(), dominated.getY() + dominated.getBbHeight() + 0.3D, dominated.getZ(),
                2, 0.2D, 0.1D, 0.2D, 0.02D);
    }
}

private static boolean useBardMistyStep(ServerPlayer player) {
    ServerLevel level = player.level();
    Vec3 start = player.getEyePosition();
    Vec3 look = player.getLookAngle().normalize();

    // ищем точку телепорта — идём по лучу пока не упрёмся в блок
    Vec3 target = start;
    for (int i = 1; i <= 25; i++) {
        Vec3 step = start.add(look.scale(i));
        net.minecraft.world.level.ClipContext clip = new net.minecraft.world.level.ClipContext(
                start, step,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player);
        net.minecraft.world.phys.BlockHitResult hit = level.clip(clip);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            // упёрлись в стену — телепортируемся на шаг назад
            target = start.add(look.scale(Math.max(1, i - 1)));
            break;
        }
        target = step;
    }

    // партиклы на старте
    level.sendParticles(ParticleTypes.POOF,
            player.getX(), player.getY() + 1.0D, player.getZ(),
            12, 0.3D, 0.5D, 0.3D, 0.05D);

    player.teleportTo(target.x, target.y - 1.0D, target.z);
    player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false));

    // партиклы на финише
    level.sendParticles(ParticleTypes.POOF,
            target.x, target.y, target.z,
            12, 0.3D, 0.5D, 0.3D, 0.05D);
    level.playSound(null, target.x, target.y, target.z,
            net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT,
            net.minecraft.sounds.SoundSource.PLAYERS,
            0.6F, 1.8F);

    return true;
}

private static boolean useBardSilence(ServerPlayer player) {
    ServerLevel level = player.level();
    Vec3 target = raycastBlock(player, 20.0D);

    SILENCE_ZONES.put(java.util.UUID.randomUUID(), new double[]{
            target.x, target.y, target.z,
            level.getGameTime() + 15 * 20L
    });

    // кольцо партиклов на земле
    for (int i = 0; i < 32; i++) {
        double angle = i / 32.0D * 2 * Math.PI;
        double radius = 5.0D;
        level.sendParticles(ParticleTypes.SCULK_SOUL,
                target.x + Math.cos(angle) * radius,
                target.y + 0.1D,
                target.z + Math.sin(angle) * radius,
                1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    // партиклы внутри сферы
    level.sendParticles(ParticleTypes.SCULK_CHARGE_POP,
            target.x, target.y + 2.5D, target.z,
            20, 2.0D, 2.0D, 2.0D, 0.05D);

    level.playSound(null, target.x, target.y, target.z,
            net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK,
            net.minecraft.sounds.SoundSource.PLAYERS,
            0.5F, 1.8F);

    return true;
}

private static Vec3 raycastBlock(ServerPlayer player, double range) {
    net.minecraft.world.level.ClipContext clip = new net.minecraft.world.level.ClipContext(
            player.getEyePosition(),
            player.getEyePosition().add(player.getLookAngle().normalize().scale(range)),
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,
            player);
    net.minecraft.world.phys.BlockHitResult hit = player.level().clip(clip);
    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
        return Vec3.atCenterOf(hit.getBlockPos());
    }
    return player.getEyePosition().add(player.getLookAngle().normalize().scale(range));
}

	private static boolean usePlaceholderAbility(ServerPlayer player, AbilityDefinition ability) {
		player.sendSystemMessage(Component.literal("Использована способность: " + ability.title()));
		return true;
	}

	private static LivingEntity raycastLivingEntity(ServerPlayer player, double range) {
    Vec3 start = player.getEyePosition();
    Vec3 end = start.add(player.getLookAngle().normalize().scale(range));
    AABB searchBox = player.getBoundingBox().expandTowards(player.getLookAngle().scale(range)).inflate(1.5D);

    LivingEntity closest = null;
    double closestDist = Double.MAX_VALUE;

    for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, searchBox, e -> e != player && e.isAlive())) {
        AABB hitbox = entity.getBoundingBox().inflate(0.3D);
        var hit = hitbox.clip(start, end);
        if (hit.isPresent()) {
            double dist = start.distanceTo(hit.get());
            if (dist < closestDist) {
                closestDist = dist;
                closest = entity;
            }
        }
    }

    return closest;
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
		WildMagicNetworking.sendClassData(player, get(player));
	}

	private static void tickPlayers(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		tickHeroism(server);
        tickGreaterInvisibility(server);
        tickSlowZones(server);
        tickDominate(server);
        tickForceCage(server);
tickMordenkainenSword(server);
        tickHypnoticPattern(server);
		tickInvisibility(server);
		tickSilence(server);

		CHARMED_TARGETS.forEach((playerUuid, targets) ->
    targets.entrySet().removeIf(entry -> {
        MinecraftServer srv = currentServer;
        if (srv == null) return true;
        ServerPlayer p = srv.getPlayerList().getPlayer(playerUuid);
        if (p == null) return true;
        return p.level().getGameTime() > entry.getValue();
    })
);

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

	private static void tickHeroism(MinecraftServer server) {
    // раз в секунду
    if (server.getTickCount() % 20 != 0) return;

    HEROISM_TARGETS.entrySet().removeIf(entry -> {
        ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
        if (player == null) return true;

if (player.level().getGameTime() > entry.getValue()) {
    removeHeroism(player);
    return true;
}

        // снимаем яд и иссушение если наложились
        player.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
        player.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);

        // нимб — несколько золотых партиклов по кругу над головой
        double r = 0.4D;
        double baseY = player.getY() + player.getBbHeight() + 0.3D;
        ServerLevel level = player.level();
        for (int i = 0; i < 4; i++) {
            double angle = (server.getTickCount() % 40) / 40.0D * 2 * Math.PI + (i * Math.PI / 2);
            level.sendParticles(ParticleTypes.WAX_ON,
                    player.getX() + Math.cos(angle) * r,
                    baseY,
                    player.getZ() + Math.sin(angle) * r,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);
        }

        return false;
    });
}

private static void tickInvisibility(MinecraftServer server) {
    if (server.getTickCount() % 20 != 0) return;
    INVISIBLE_PLAYERS.entrySet().removeIf(entry -> {
        ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
        if (player == null) return true;
        if (player.level().getGameTime() > entry.getValue()) {
            player.setInvisible(false);
            return true;
        }
        return false;
    });
}

public static void breakInvisibility(ServerPlayer player) {
    if (GREATER_INVISIBLE_PLAYERS.containsKey(player.getUUID())) return;
    if (INVISIBLE_PLAYERS.remove(player.getUUID()) != null) {
        player.setInvisible(false);
    }
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


public static boolean isHeroismActive(ServerPlayer player) {
    Long expire = HEROISM_TARGETS.get(player.getUUID());
    if (expire == null) return false;
    if (player.level().getGameTime() > expire) {
        HEROISM_TARGETS.remove(player.getUUID());
        return false;
    }
    return true;
}

	private static void applyClassPassives(ServerPlayer player) {
    PlayerClassData data = get(player);
    if (data.hasClass() && data.selectedClass() == WildMagicClass.BARD) {
        applyBardHealthPenalty(player);
        if (data.level() >= 5) applyBardLightStep(player);
        else removeBardLightStep(player);
        if (data.level() >= 10) applyBardCollegeOfSwords(player, data);
        else removeBardCollegeOfSwords(player);
    } else {
        removeBardHealthPenalty(player);
        removeBardLightStep(player);
        removeBardCollegeOfSwords(player);
    }
}

private static void removeClassPassives(ServerPlayer player) {
    removeBardHealthPenalty(player);
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
