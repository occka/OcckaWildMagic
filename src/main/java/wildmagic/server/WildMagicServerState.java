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
private static final Map<UUID, Long> INVISIBLE_PLAYERS = new ConcurrentHashMap<>();
private static final net.minecraft.resources.Identifier HEROISM_DAMAGE_MODIFIER_ID = 
    net.minecraft.resources.Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "heroism_damage");
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
		HEROISM_TARGETS.remove(player.getUUID());
INVISIBLE_PLAYERS.remove(player.getUUID());
		applyClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void clearClass(ServerPlayer player) {
		PLAYER_DATA.remove(player.getUUID());
		ABILITY_COOLDOWNS.remove(player.getUUID());
		CHARMED_TARGETS.remove(player.getUUID());
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
case "bard_misty_step" -> useBardMistyStep(player);
case "bard_silence" -> useBardSilence(player);
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

	private static boolean useBardInvisibility(ServerPlayer player) {
    INVISIBLE_PLAYERS.put(player.getUUID(), player.level().getGameTime() + 30 * 20L);
    player.setInvisible(true);

    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT,
            net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
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
    if (INVISIBLE_PLAYERS.remove(player.getUUID()) != null) {
        player.setInvisible(false);
    }
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
