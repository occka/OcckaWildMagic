package wildmagic.server.state;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import wildmagic.OcckaWildMagic;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import wildmagic.server.ability.BardAbilities;

public final class WildMagicZones {
private static final Map<UUID, Map<UUID, Long>> CHARMED_TARGETS = new ConcurrentHashMap<>();
private static final Map<UUID, Long> HEROISM_TARGETS = new ConcurrentHashMap<>();
private static final Map<UUID, double[]> SLOW_ZONES = new ConcurrentHashMap<>();
// double[]: x, y, z, expireGameTime
private static final Map<UUID, Long> INVISIBLE_PLAYERS = new ConcurrentHashMap<>();
private static final Identifier HEROISM_DAMAGE_MODIFIER_ID =
    Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "heroism_damage");
private static final Map<UUID, double[]> SILENCE_ZONES = new ConcurrentHashMap<>();

private WildMagicZones() {
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


public static void applyHeroism(ServerPlayer player, ServerLevel level, long expireTime) {
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


public static void tickSlowZones(MinecraftServer server) {
    if (server.getTickCount() % 20 != 0) return;
    long now = server.overworld().getGameTime();

    SLOW_ZONES.entrySet().removeIf(entry -> now > entry.getValue()[3]);

    for (double[] zone : SLOW_ZONES.values()) {
        ServerLevel level = server.overworld();
        Vec3 center = new Vec3(zone[0], zone[1], zone[2]);
        double half = 2.0D;

        // перерисовываем границы каждую секунду
        BardAbilities.drawZoneBorder(level, center, 4.0D);

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


public static void tickSilence(MinecraftServer server) {
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


	public static void tickHeroism(MinecraftServer server) {
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


public static void tickInvisibility(MinecraftServer server) {
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
    if (BardAbilities.isGreaterInvisible(player)) return;
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

public static void clearPlayer(ServerPlayer player) {
    CHARMED_TARGETS.remove(player.getUUID());
    HEROISM_TARGETS.remove(player.getUUID());
    INVISIBLE_PLAYERS.remove(player.getUUID());
}

public static void addInvisibility(UUID playerId, long expireTime) {
    INVISIBLE_PLAYERS.put(playerId, expireTime);
}

public static void addCharmed(UUID bardId, UUID targetId, long expireTime) {
    CHARMED_TARGETS.computeIfAbsent(bardId, k -> new ConcurrentHashMap<>()).put(targetId, expireTime);
}

public static void addSlowZone(UUID zoneId, double[] zone) {
    SLOW_ZONES.put(zoneId, zone);
}

public static void addSilenceZone(UUID zoneId, double[] zone) {
    SILENCE_ZONES.put(zoneId, zone);
}

public static void removeSilenceZone(UUID zoneId) {
    SILENCE_ZONES.remove(zoneId);
}

public static void tickCharmed(MinecraftServer currentServer) {
    CHARMED_TARGETS.forEach((playerUuid, targets) ->
    targets.entrySet().removeIf(entry -> {
        MinecraftServer srv = currentServer;
        if (srv == null) return true;
        ServerPlayer p = srv.getPlayerList().getPlayer(playerUuid);
        if (p == null) return true;
        return p.level().getGameTime() > entry.getValue();
    })
);
}

}
