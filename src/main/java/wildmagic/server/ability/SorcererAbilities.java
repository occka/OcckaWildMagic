package wildmagic.server.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import wildmagic.classdata.SorcererElement;
import wildmagic.server.WildMagicServerState;

import java.util.HashSet;
import java.util.Set;

public final class SorcererAbilities {
    private SorcererAbilities() {}

   public static boolean useDragonBreath(ServerPlayer player) {
    ServerLevel level = player.level();
    SorcererElement element = WildMagicServerState.get(player).sorcererElementEnum();
    Vec3 look = player.getLookAngle().normalize();
    Vec3 start = player.getEyePosition();
    Set<LivingEntity> hit = new HashSet<>();

    // находим перпендикуляры к вектору взгляда
    Vec3 up = new Vec3(0, 1, 0);
    Vec3 right = look.cross(up).normalize();
    Vec3 upPerp = look.cross(right).normalize();

    for (int step = 1; step <= 8; step++) {
        Vec3 center = start.add(look.scale(step));
        double radius = step * 0.35D; // конус расширяется
        int points = 8 + step * 2;

        // рисуем кольцо партиклов
        for (int i = 0; i < points; i++) {
            double angle = i / (double) points * Math.PI * 2;
            Vec3 offset = right.scale(Math.cos(angle) * radius).add(upPerp.scale(Math.sin(angle) * radius));
            Vec3 particlePos = center.add(offset);
            spawnElementParticles(level, element, particlePos, 0.05D);
        }
        // партиклы внутри конуса
        spawnElementParticles(level, element, center, radius * 0.5D);

        // хитбокс конуса
        AABB box = new AABB(
                center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive())) {
            if (!hit.add(target)) continue;
            float damage = 3.0F + player.getRandom().nextFloat() * 5.0F;
            target.hurtServer(level, player.damageSources().playerAttack(player), damage);
            applyElementEffect(player, target, element);
        }
    }

    playElementSound(level, player.getX(), player.getY(), player.getZ(), element);
    return true;
}

    public static boolean useElementalBurst(ServerPlayer player) {
        ServerLevel level = player.level();
        SorcererElement element = WildMagicServerState.get(player).sorcererElementEnum();
        Vec3 center = player.position().add(0, 1, 0);
        AABB area = player.getBoundingBox().inflate(5.0D);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
            float damage = 4.0F + player.getRandom().nextFloat() * 8.0F;
            target.hurtServer(level, player.damageSources().playerAttack(player), damage);
            applyElementEffect(player, target, element);
        }

        spawnBurstParticles(level, element, center);
        playElementSound(level, center.x, center.y, center.z, element);
        return true;
    }

    public static boolean useDraconicWings(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15 * 20, 0, false, false));
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();

player.level().sendParticles(ParticleTypes.POOF,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                30, 0.5D, 0.5D, 0.5D, 0.05D);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);

        // снимаем через 15 секунд через тик — регистрируем в WarlockAbilities нельзя, используем эффект
        // полёт снимается отдельно в tick
        WINGS_EXPIRE.put(player.getUUID(), player.level().getGameTime() + 15 * 20L);
        return true;
    }

    private static final java.util.Map<java.util.UUID, Long> WINGS_EXPIRE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Boolean> METAMAGIC_ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<java.util.UUID, Boolean> TWINNED_ACTIVE = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean useMetamagic(ServerPlayer player) {
        METAMAGIC_ACTIVE.put(player.getUUID(), true);
        player.sendSystemMessage(Component.literal("Метамагия: следующее заклинание бесплатно"));
        player.level().sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                30, 0.5D, 0.8D, 0.5D, 0.1D);
        return true;
    }

    public static boolean isMetamagicActive(ServerPlayer player) {
        return METAMAGIC_ACTIVE.getOrDefault(player.getUUID(), false);
    }

    public static void consumeMetamagic(ServerPlayer player) {
        METAMAGIC_ACTIVE.remove(player.getUUID());
    }

    public static boolean useTwinnedSpell(ServerPlayer player) {
        TWINNED_ACTIVE.put(player.getUUID(), true);
        player.sendSystemMessage(Component.literal("Сдвоенное заклинание: следующий луч бьёт двух целей"));
        player.level().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                20, 0.4D, 0.6D, 0.4D, 0.08D);
        return true;
    }

    public static boolean isTwinnedActive(ServerPlayer player) {
        return TWINNED_ACTIVE.getOrDefault(player.getUUID(), false);
    }

    public static void consumeTwinned(ServerPlayer player) {
        TWINNED_ACTIVE.remove(player.getUUID());
    }

    public static boolean useQuickenSpell(ServerPlayer player) {
        long[] cooldowns = wildmagic.server.WildMagicServerState.getCooldowns(player);
        if (cooldowns != null) {
            java.util.Arrays.fill(cooldowns, 0L);
        }
        player.sendSystemMessage(Component.literal("Ускоренное заклинание: КД сброшены"));
        player.level().sendParticles(ParticleTypes.END_ROD,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                5, 0.3D, 0.3D, 0.3D, 0.0D);
        return true;
    }

    public static boolean useWildSurge(ServerPlayer player) {
        ServerLevel level = player.level();
        int roll = player.getRandom().nextInt(8);
        return switch (roll) {
            case 0 -> { // взрыв маны
                AABB area = player.getBoundingBox().inflate(6.0D);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player))
                    e.hurtServer(level, player.damageSources().magic(), 10.0F);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 3, 0, 0, 0, 0);
                player.sendSystemMessage(Component.literal("Дикий выброс: взрыв маны!"));
                yield true;
            }
            case 1 -> { // телепорт в случайном направлении
                double angle = player.getRandom().nextDouble() * Math.PI * 2;
                player.teleportTo(player.getX() + Math.cos(angle) * 10, player.getY(), player.getZ() + Math.sin(angle) * 10);
                player.sendSystemMessage(Component.literal("Дикий выброс: случайный телепорт!"));
                yield true;
            }
            case 2 -> { // полное восстановление маны
                wildmagic.server.WildMagicServerState.fillMana(player);
                player.sendSystemMessage(Component.literal("Дикий выброс: мана восстановлена!"));
                yield true;
            }
            case 3 -> { // невидимость 10с
                player.setInvisible(true);
                wildmagic.server.state.WildMagicZones.addInvisibility(player.getUUID(), level.getGameTime() + 10 * 20L);
                player.sendSystemMessage(Component.literal("Дикий выброс: невидимость!"));
                yield true;
            }
            case 4 -> { // урон самому себе
                player.hurtServer(level, player.damageSources().magic(), 8.0F);
                player.sendSystemMessage(Component.literal("Дикий выброс: обратная волна!"));
                yield true;
            }
            case 5 -> { // левитация 5с
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 5 * 20, 0));
                player.sendSystemMessage(Component.literal("Дикий выброс: левитация!"));
                yield true;
            }
            case 6 -> { // исцеление
                player.heal(10.0F);
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 10, 0.5D, 0.5D, 0.5D, 0.05D);
                player.sendSystemMessage(Component.literal("Дикий выброс: исцеление!"));
                yield true;
            }
            default -> { // огненный дождь вокруг
                AABB area = player.getBoundingBox().inflate(8.0D);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player))
                    e.igniteForSeconds(5);
                level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 3, player.getZ(), 60, 4D, 1D, 4D, 0.1D);
                player.sendSystemMessage(Component.literal("Дикий выброс: огненный дождь!"));
                yield true;
            }
        };
    }

    public static void tickWings(net.minecraft.server.MinecraftServer server) {
        long now = server.overworld().getGameTime();
        WINGS_EXPIRE.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) return true;
            if (now > entry.getValue()) {
                if (!player.isCreative() && !player.isSpectator()) {
                    player.getAbilities().mayfly = false;
                    player.getAbilities().flying = false;
                    player.onUpdateAbilities();
                }
                return true;
            }
            return false;
        });
    }

    public static void clearPlayer(ServerPlayer player) {
        WINGS_EXPIRE.remove(player.getUUID());
        METAMAGIC_ACTIVE.remove(player.getUUID());
        TWINNED_ACTIVE.remove(player.getUUID());
        if (!player.isCreative() && !player.isSpectator()) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
    }

    private static void applyElementEffect(ServerPlayer caster, LivingEntity target, SorcererElement element) {
        switch (element) {
            case FIRE -> target.igniteForSeconds(4);
            case ICE -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4 * 20, 2), caster);
            case LIGHTNING -> target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 3 * 20, 1), caster);
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 5 * 20, 0), caster);
            case THUNDER -> {
                Vec3 kb = target.position().subtract(caster.position()).normalize().scale(1.2D);
                target.push(kb.x, 0.5D, kb.z);
                target.hurtMarked = true;
            }
        }
    }

    private static void spawnElementParticles(ServerLevel level, SorcererElement element, Vec3 center, double spread) {
        switch (element) {
            case FIRE -> level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 4, spread, spread, spread, 0.02D);
            case ICE -> level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, 4, spread, spread, spread, 0.02D);
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 4, spread, spread, spread, 0.02D);
            case POISON -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 4, spread, spread, spread, 0.02D);
            case THUNDER -> level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        }
    }

    private static void spawnBurstParticles(ServerLevel level, SorcererElement element, Vec3 center) {
        switch (element) {
            case FIRE -> level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 60, 3D, 1D, 3D, 0.08D);
            case ICE -> level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, 60, 3D, 1D, 3D, 0.04D);
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 60, 3D, 1D, 3D, 0.08D);
            case POISON -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 60, 3D, 1D, 3D, 0.05D);
            case THUNDER -> level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 3, 1D, 0.5D, 1D, 0D);
        }
    }

    private static void playElementSound(ServerLevel level, double x, double y, double z, SorcererElement element) {
        net.minecraft.sounds.SoundEvent sound = switch (element) {
            case FIRE -> net.minecraft.sounds.SoundEvents.FIRECHARGE_USE;
            case ICE -> net.minecraft.sounds.SoundEvents.PLAYER_HURT_FREEZE;
            case LIGHTNING -> net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER;
            case POISON -> net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT;
            case THUNDER -> net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT;
        };
        level.playSound(null, x, y, z, sound, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.0F);
    }
}