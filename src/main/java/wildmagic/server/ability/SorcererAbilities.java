package wildmagic.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import wildmagic.OcckaWildMagic;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.SorcererElement;
import wildmagic.classdata.WildMagicClass;
import wildmagic.server.WildMagicServerState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SorcererAbilities {
    private SorcererAbilities() {
    }

    // -----------------------------------------------------------------------
    // Attribute modifier IDs
    // -----------------------------------------------------------------------

    private static final Identifier DRAGON_HIDE_ARMOR_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID,
            "sorcerer_dragon_hide");
    private static final Identifier MAGE_ARMOR_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID,
            "sorcerer_mage_armor");

    /** +2 full armor = +4 half-points */
    private static final double DRAGON_HIDE_ARMOR_VALUE = 4.0D;
    private static final double MAGE_ARMOR_VALUE = 4.0D;

    // -----------------------------------------------------------------------
    // Draconic Wings
    // -----------------------------------------------------------------------

    private record WingsState(long startTick, long expireTick) {
    }

    private record StormJumpState(long startTick, double startY, boolean diving) {
    }

    private record PoisonCloud(UUID ownerId, Vec3 center, long expireTick, long nextDamageTick) {
    }

    private static final double WINGS_SPEED = 0.9D;
    private static final int WINGS_GROUND_GRACE = 10;

    private static final Map<UUID, WingsState> WINGS_ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, StormJumpState> STORM_JUMPS = new ConcurrentHashMap<>();
    private static final List<PoisonCloud> POISON_CLOUDS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final Map<UUID, Boolean> METAMAGIC_ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> TWINNED_ACTIVE = new ConcurrentHashMap<>();
    private record WebZone(UUID ownerId, BlockPos center, long expireTick) {}
    private record Sunbeam(UUID ownerId, long expireTick, long nextTick) {}
    private static final Map<UUID, Long> FIRE_PALMS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> SHIELD_READY = new ConcurrentHashMap<>();
    private static final List<WebZone> WEB_ZONES = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<Sunbeam> SUNBEAMS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static boolean useGust(ServerPlayer player) {
        ServerLevel level = player.level();
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 p = player.getEyePosition().add(dir.scale(1.2D));
        level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 30, 0.8D, 0.6D, 0.8D, 0.03D);
        AABB area = new AABB(p.x-2.5D,p.y-2.0D,p.z-2.5D,p.x+2.5D,p.y+2.0D,p.z+2.5D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive())) {
            e.push(dir.x * 1.1D, 0.2D, dir.z * 1.1D);
            e.hurtMarked = true;
        }
        return true;
    }
    public static boolean useFirePalms(ServerPlayer player) { FIRE_PALMS.put(player.getUUID(), player.level().getGameTime() + 30*20L); return true; }
    public static boolean useWeb(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos c = BlockPos.containing(player.position().add(player.getLookAngle().normalize().scale(6.0D)));
        for(int dx=-2;dx<=2;dx++) for(int dy=-2;dy<=2;dy++) for(int dz=-2;dz<=2;dz++) {
            BlockPos pos = c.offset(dx,dy,dz);
            if (level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.COBWEB.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
        WEB_ZONES.add(new WebZone(player.getUUID(), c, level.getGameTime() + 20*20L));
        return true;
    }
    public static boolean useShield(ServerPlayer player) { SHIELD_READY.put(player.getUUID(), true); return true; }
    public static boolean consumeShield(ServerPlayer player) { return SHIELD_READY.remove(player.getUUID()) != null; }
    public static boolean useSunbeam(ServerPlayer player) { long now = player.level().getGameTime(); SUNBEAMS.add(new Sunbeam(player.getUUID(), now + 6*20L, now)); return true; }
    public static boolean useDisintegrate(ServerPlayer player) {
        LivingEntity t = findRayTarget(player, 25.0D);
        if (t == null) { player.sendSystemMessage(Component.literal("Цель не найдена")); return false; }
        float self = 5.0F + player.getRandom().nextFloat() * 7.0F;
        player.hurtServer(player.level(), player.damageSources().playerAttack(player), self);
        for (var slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            if (!slot.isArmor()) continue;
            var st = t.getItemBySlot(slot);
            if (st.isDamageableItem()) st.setDamageValue(Math.min(st.getMaxDamage()-1, st.getDamageValue() + (int)(st.getMaxDamage()*0.4D)));
        }
        drawGreenBeam(player.level(), player.getEyePosition(), t.getEyePosition());
        return true;
    }


    /** Mage Armor expire game-time per player */
    private static final Map<UUID, Long> MAGE_ARMOR_EXPIRE = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // tickWings — must be called EVERY tick (before the %20 gate)
    // -----------------------------------------------------------------------

    public static void tickWings(MinecraftServer server) {
        WINGS_ACTIVE.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null)
                return true;

            WingsState state = entry.getValue();
            long now = player.level().getGameTime();
            long elapsed = now - state.startTick();
            boolean expired = now >= state.expireTick();
            boolean landed = elapsed > WINGS_GROUND_GRACE && player.onGround();

            if (expired || landed) {
                endFlight(player, expired);
                return true;
            }

            player.startFallFlying();
            Vec3 dir = player.getLookAngle().normalize();
            player.setDeltaMovement(dir.scale(WINGS_SPEED));
            player.hurtMarked = true;
            player.fallDistance = 0;

            if (now % 2 == 0) {
                ServerLevel level = player.level();
                Vec3 trail = player.position()
                        .add(0, player.getEyeHeight() * 0.5D, 0)
                        .subtract(dir.scale(0.8D));
                SorcererElement el = WildMagicServerState.get(player).sorcererElementEnum();
                switch (el) {
                    case FIRE -> {
                        level.sendParticles(ParticleTypes.FLAME, trail.x, trail.y, trail.z, 6, 0.2D, 0.2D, 0.2D, 0.02D);
                        level.sendParticles(ParticleTypes.SMOKE, trail.x, trail.y, trail.z, 2, 0.15D, 0.15D, 0.15D,
                                0.01D);
                    }
                    case ICE -> level.sendParticles(ParticleTypes.SNOWFLAKE, trail.x, trail.y, trail.z, 6, 0.2D, 0.2D,
                            0.2D, 0.02D);
                    case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, trail.x, trail.y, trail.z, 6,
                            0.2D, 0.2D, 0.2D, 0.02D);
                    case POISON -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, trail.x, trail.y, trail.z, 6, 0.2D,
                            0.2D, 0.2D, 0.02D);
                    case THUNDER ->
                        level.sendParticles(ParticleTypes.POOF, trail.x, trail.y, trail.z, 4, 0.2D, 0.2D, 0.2D, 0.02D);
                }
            }
            return false;
        });
    }

    private static void endFlight(ServerPlayer player, boolean giveSlowFall) {
        player.fallDistance = 0;
        if (giveSlowFall && !player.onGround()) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 15 * 20, 0, false, false));
        }
    }

    public static void tickStormJumps(MinecraftServer server) {
        STORM_JUMPS.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                return true;
            }

            StormJumpState state = entry.getValue();
            ServerLevel level = player.level();
            long elapsed = level.getGameTime() - state.startTick();
            SorcererElement element = WildMagicServerState.get(player).sorcererElementEnum();

            if (!state.diving() && player.getY() < state.startY() + 25.0D && elapsed < 45L) {
                player.setDeltaMovement(0.0D, 1.15D, 0.0D);
                player.hurtMarked = true;
                player.fallDistance = 0.0F;
                spawnElementParticles(level, element, player.position().add(0.0D, 0.4D, 0.0D), 0.25D);
                return false;
            }

            if (!state.diving()) {
                STORM_JUMPS.put(player.getUUID(), new StormJumpState(state.startTick(), state.startY(), true));
            }

            if (player.onGround() && elapsed > 8L) {
                finishStormJump(player, element);
                return true;
            }

            player.startFallFlying();
            Vec3 look = player.getLookAngle().normalize();
            Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
            if (horizontal.lengthSqr() < 0.001D) {
                horizontal = new Vec3(0.0D, 0.0D, 1.0D);
            }
            horizontal = horizontal.normalize().scale(0.8D);
            player.setDeltaMovement(horizontal.x, -0.55D, horizontal.z);
            player.hurtMarked = true;
            player.fallDistance = 0.0F;
            spawnElementParticles(level, element, player.position().add(0.0D, 0.8D, 0.0D), 0.35D);
            return elapsed > 20 * 12L;
        });

        tickPoisonClouds(server);
        tickWebZones(server);
        tickSunbeams(server);
    }

    // -----------------------------------------------------------------------
    // tickPassives — once per second (%20 gate in tickPlayers)
    // -----------------------------------------------------------------------

    public static void tickPassives(ServerPlayer player) {
        PlayerClassData data = WildMagicServerState.get(player);
        if (!data.hasClass() || data.selectedClass() != WildMagicClass.SORCERER) {
            removeDragonHide(player);
            removeMageArmor(player);
            return;
        }

        // Lv1 — Dragon Hide
        applyDragonHide(player);

        // Lv5 — Elemental Affinity
        if (data.level() >= 5) {
            applyElementalAffinityTick(player, data.sorcererElementEnum());
        }

        // Lv10 — Resistance I always; Regeneration I while on fire (no fire damage)
        if (data.level() >= 10) {
            player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 25 * 20, 0, false, false));
            if (player.isOnFire()) {
                player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 25 * 20, 0, false, false));
                player.clearFire();
            }
        }

        FIRE_PALMS.entrySet().removeIf(e -> player.level().getGameTime() > e.getValue());

        // Mage Armor expire check
        Long mageExpire = MAGE_ARMOR_EXPIRE.get(player.getUUID());
        if (mageExpire != null && player.level().getGameTime() > mageExpire) {
            MAGE_ARMOR_EXPIRE.remove(player.getUUID());
        FIRE_PALMS.remove(player.getUUID());
        SHIELD_READY.remove(player.getUUID());
        WEB_ZONES.removeIf(z -> z.ownerId().equals(player.getUUID()));
        SUNBEAMS.removeIf(z -> z.ownerId().equals(player.getUUID()));
            removeMageArmor(player);
        }
    }

    // -----------------------------------------------------------------------
    // Dragon Hide
    // -----------------------------------------------------------------------

    public static void applyDragonHide(ServerPlayer player) {
        var armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null && armor.getModifier(DRAGON_HIDE_ARMOR_ID) == null) {
            armor.addPermanentModifier(new AttributeModifier(
                    DRAGON_HIDE_ARMOR_ID, DRAGON_HIDE_ARMOR_VALUE,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    public static void removeDragonHide(ServerPlayer player) {
        var armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null)
            armor.removeModifier(DRAGON_HIDE_ARMOR_ID);
    }

    // -----------------------------------------------------------------------
    // Mage Armor (active, Lv3)
    // -----------------------------------------------------------------------

    public static boolean useMageArmor(ServerPlayer player) {
        ServerLevel level = player.level();
        long expireTime = level.getGameTime() + 30 * 20L;
        MAGE_ARMOR_EXPIRE.put(player.getUUID(), expireTime);

        var armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null && armor.getModifier(MAGE_ARMOR_ID) == null) {
            armor.addPermanentModifier(new AttributeModifier(
                    MAGE_ARMOR_ID, MAGE_ARMOR_VALUE,
                    AttributeModifier.Operation.ADD_VALUE));
        }

        level.sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0D, player.getZ(),
                40, 0.6D, 0.8D, 0.6D, 0.1D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
        return true;
    }

    private static void removeMageArmor(ServerPlayer player) {
        var armor = player.getAttribute(Attributes.ARMOR);
        if (armor != null)
            armor.removeModifier(MAGE_ARMOR_ID);
    }

    // -----------------------------------------------------------------------
    // Lv5 elemental affinity per-second effects
    // -----------------------------------------------------------------------

    private static void applyElementalAffinityTick(ServerPlayer player, SorcererElement element) {
        switch (element) {
            case FIRE -> player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 25 * 20, 0, false, false));
            case LIGHTNING -> player.addEffect(new MobEffectInstance(MobEffects.HASTE, 25 * 20, 1, false, false));
            case POISON -> player.removeEffect(MobEffects.POISON);
            case ICE, THUNDER -> {
            }
        }
    }

    // -----------------------------------------------------------------------
    // Lv5 Ice — 1 damage retaliation on being hit
    // -----------------------------------------------------------------------

    public static void onSorcererHit(ServerPlayer victim, LivingEntity attacker) {
        PlayerClassData data = WildMagicServerState.get(victim);
        if (!data.hasClass() || data.selectedClass() != WildMagicClass.SORCERER)
            return;
        if (data.level() < 5 || data.sorcererElementEnum() != SorcererElement.ICE)
            return;
        if (attacker == null || !attacker.isAlive() || attacker == victim)
            return;

        ServerLevel level = victim.level();
        attacker.hurtServer(level, victim.damageSources().playerAttack(victim), 1.0F);
        level.sendParticles(ParticleTypes.SNOWFLAKE,
                attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5D, attacker.getZ(),
                8, 0.3D, 0.4D, 0.3D, 0.03D);
    }

    // -----------------------------------------------------------------------
    // Lv5 Poison — melee applies Poison 5s
    // -----------------------------------------------------------------------

public static void onSorcererAttack(ServerPlayer attacker, LivingEntity victim) {
    PlayerClassData data = WildMagicServerState.get(attacker);
    if (!data.hasClass() || data.selectedClass() != WildMagicClass.SORCERER) return;
    if (data.level() < 5) return;

    switch (data.sorcererElementEnum()) {
        case POISON -> victim.addEffect(
                new MobEffectInstance(MobEffects.POISON, 5 * 20, 0, false, false), attacker);

        case THUNDER -> {
            // 1/100 chance to strike with lightning
            if (attacker.getRandom().nextInt(100) == 0) {
                ServerLevel level = attacker.level();
                var bolt = EntityType.LIGHTNING_BOLT.create(level,
                        net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                if (bolt != null) {
                    bolt.snapTo(victim.getX(), victim.getY(), victim.getZ());
                    bolt.setVisualOnly(false);
                    level.addFreshEntity(bolt);
                }
            }
        }

        default -> {}
    }
    Long fire = FIRE_PALMS.get(attacker.getUUID());
    if (fire != null && attacker.level().getGameTime() <= fire) victim.igniteForSeconds(2);
}



private static final java.util.Set<UUID> SILENT_SPELL_ACTIVE = ConcurrentHashMap.newKeySet();

public static boolean useSilentSpell(ServerPlayer player) {
    SILENT_SPELL_ACTIVE.add(player.getUUID());
    player.sendSystemMessage(Component.literal("Молчаливое заклинание: следующее заклинание игнорирует тишину"));
    player.level().sendParticles(ParticleTypes.SCULK_SOUL,
            player.getX(), player.getY() + 1.0D, player.getZ(),
            16, 0.4D, 0.6D, 0.4D, 0.05D);
    return true;
}

public static boolean isSilentSpellActive(ServerPlayer player) {
    return SILENT_SPELL_ACTIVE.contains(player.getUUID());
}

public static void consumeSilentSpell(ServerPlayer player) {
    SILENT_SPELL_ACTIVE.remove(player.getUUID());
}



    // -----------------------------------------------------------------------
    // useDraconicWings
    // -----------------------------------------------------------------------

    public static boolean useDraconicWings(ServerPlayer player) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        WINGS_ACTIVE.put(player.getUUID(), new WingsState(now, now + 25 * 20L));
        player.fallDistance = 0;
        player.startFallFlying();
        level.sendParticles(ParticleTypes.POOF,
                player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.5D, 0.5D, 0.5D, 0.05D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ENDER_DRAGON_FLAP,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    public static boolean useElementalDash(ServerPlayer player) {
        ServerLevel level = player.level();
        SorcererElement element = WildMagicServerState.get(player).sorcererElementEnum();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 direction = new Vec3(look.x, 0.0D, look.z);
        if (direction.lengthSqr() < 0.001D) {
            direction = look;
        }
        direction = direction.normalize();

        Vec3 start = player.position();
        for (int i = 1; i <= 6; i++) {
            Vec3 point = start.add(direction.scale(i));
            applyDashTrail(player, level, element, point, i == 6);
        }

        Vec3 currentVelocity = player.getDeltaMovement();
        Vec3 dashVelocity = direction.scale(2.35D).add(0.0D, Math.max(0.1D, currentVelocity.y), 0.0D);
        player.setDeltaMovement(dashVelocity);
        player.hurtMarked = true;
        player.fallDistance = 0.0F;
        playElementSound(level, player.getX(), player.getY(), player.getZ(), element);
        return true;
    }

    public static boolean useStormJump(ServerPlayer player) {
        ServerLevel level = player.level();
        long now = level.getGameTime();
        STORM_JUMPS.put(player.getUUID(), new StormJumpState(now, player.getY(), false));
        player.setDeltaMovement(0.0D, 1.25D, 0.0D);
        player.fallDistance = 0.0F;
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.4D, player.getZ(), 30, 0.5D, 0.2D, 0.5D, 0.08D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                net.minecraft.sounds.SoundSource.PLAYERS,
                0.9F, 1.5F);
        return true;
    }

    // -----------------------------------------------------------------------
    // useDragonBreath (Lv1)
    // -----------------------------------------------------------------------

    public static boolean useDragonBreath(ServerPlayer player) {
        ServerLevel level = player.level();
        SorcererElement element = WildMagicServerState.get(player).sorcererElementEnum();
        Vec3 look = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition();
        Set<LivingEntity> hit = new HashSet<>();

        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up).normalize();
        Vec3 upPerp = look.cross(right).normalize();

        for (int step = 1; step <= 8; step++) {
            Vec3 center = start.add(look.scale(step));
            double radius = step * 0.35D;
            int points = 8 + step * 2;
            for (int i = 0; i < points; i++) {
                double angle = i / (double) points * Math.PI * 2;
                Vec3 offset = right.scale(Math.cos(angle) * radius).add(upPerp.scale(Math.sin(angle) * radius));
                spawnElementParticles(level, element, center.add(offset), 0.05D);
            }
            spawnElementParticles(level, element, center, radius * 0.5D);

            AABB box = new AABB(center.x - radius, center.y - radius, center.z - radius,
                    center.x + radius, center.y + radius, center.z + radius);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != player && e.isAlive())) {
                if (!hit.add(target))
                    continue;
                float damage = 3.0F + player.getRandom().nextFloat() * 5.0F;
                target.hurtServer(level, player.damageSources().playerAttack(player), damage);
                applyElementEffect(player, target, element);
            }
        }
        playElementSound(level, player.getX(), player.getY(), player.getZ(), element);
        return true;
    }

    // -----------------------------------------------------------------------
    // useIceDagger — projectile, Lv2
    // -----------------------------------------------------------------------

    public static boolean useIceDagger(ServerPlayer player) {
        ServerLevel level = player.level();
        // Reuse wizard projectile system — spawn via a simple custom projectile loop
        Vec3 direction = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition().add(direction.scale(0.8D));
        ICE_DAGGERS.add(new IceDagger(player.getUUID(), start, direction, 1.4D, 24));

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.SNOWBALL_THROW,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.4F);
        return true;
    }

    // -----------------------------------------------------------------------
    // useElementalBurst — reworked, Lv7
    // -----------------------------------------------------------------------

    public static boolean useElementalBurst(ServerPlayer player) {
        ServerLevel level = player.level();
        SorcererElement element = WildMagicServerState.get(player).sorcererElementEnum();
        Vec3 center = player.position();

        switch (element) {

            case FIRE -> {
                // Ignite all in radius 10, set ground on fire
                AABB area = player.getBoundingBox().inflate(10.0D);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != player && e.isAlive() && e.position().distanceTo(center) <= 10.0D)) {
                    target.igniteForSeconds(8);
                }
                // Ignite ground
                BlockPos origin = BlockPos.containing(center);
                for (int dx = -10; dx <= 10; dx++) {
                    for (int dz = -10; dz <= 10; dz++) {
                        if (dx * dx + dz * dz > 100)
                            continue;
                        BlockPos ground = origin.offset(dx, -1, dz);
                        BlockPos firePos = ground.above();
                        if (level.getBlockState(firePos).isAir() && level.getBlockState(ground).isSolid()) {
                            level.setBlock(firePos, Blocks.FIRE.defaultBlockState(),
                                    net.minecraft.world.level.block.Block.UPDATE_ALL);
                        }
                    }
                }
                level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 1D, center.z, 80, 5D, 1D, 5D, 0.1D);
                level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 1D, center.z, 20, 4D, 1D, 4D, 0D);
                level.playSound(null, center.x, center.y, center.z,
                        net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
            }

            case POISON -> {
                // Blindness + Slowness III radius 10 for 10s
                AABB area = player.getBoundingBox().inflate(10.0D);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != player && e.isAlive() && e.position().distanceTo(center) <= 10.0D)) {
                    target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 10 * 20, 0), player);
                    target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10 * 20, 2), player);
                }
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y + 1D, center.z, 80, 5D, 2D, 5D,
                        0.05D);
                level.playSound(null, center.x, center.y, center.z,
                        net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
            }

            case ICE -> {
                // Powder snow freeze effect radius 10 for 10s
                AABB area = player.getBoundingBox().inflate(10.0D);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != player && e.isAlive() && e.position().distanceTo(center) <= 10.0D)) {
                    target.setTicksFrozen(Math.max(target.getTicksFrozen(), 200)); // threshold is 200 to show overlay
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y + 1D, center.z, 100, 5D, 2D, 5D, 0.04D);
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, center.x, center.y + 1D, center.z, 40, 4D, 1D, 4D,
                        0.08D);
                level.playSound(null, center.x, center.y, center.z,
                        net.minecraft.sounds.SoundEvents.PLAYER_HURT_FREEZE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.7F);
            }

            case LIGHTNING -> {
                // Strike up to 3 nearest targets in radius 15 with lightning
                AABB area = player.getBoundingBox().inflate(15.0D);
                List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != player && e.isAlive() && e.position().distanceTo(center) <= 15.0D);
                candidates.sort(java.util.Comparator.comparingDouble(e -> e.position().distanceTo(center)));

                Set<UUID> struck = new HashSet<>();
                int count = 0;
                for (LivingEntity target : candidates) {
                    if (count >= 3)
                        break;
                    if (!struck.add(target.getUUID()))
                        continue;
                    // Spawn lightning bolt at target
                    var bolt = EntityType.LIGHTNING_BOLT.create(level,
                            net.minecraft.world.entity.EntitySpawnReason.TRIGGERED);
                    if (bolt != null) {
bolt.snapTo(target.getX(), target.getY(), target.getZ());
                        bolt.setVisualOnly(false);
                        level.addFreshEntity(bolt);
                    }
                    count++;
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y + 1D, center.z, 60, 5D, 2D, 5D,
                        0.1D);
                level.playSound(null, center.x, center.y, center.z,
                        net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.9F);
            }

            case THUNDER -> {
                // Original: knockback all in radius 5
                AABB area = player.getBoundingBox().inflate(5.0D);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != player && e.isAlive())) {
                    float damage = 4.0F + player.getRandom().nextFloat() * 8.0F;
                    target.hurtServer(level, player.damageSources().playerAttack(player), damage);
                    Vec3 kb = target.position().subtract(center).normalize().scale(1.8D);
                    target.push(kb.x, 0.6D, kb.z);
                    target.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y + 1D, center.z, 3, 1D, 0.5D, 1D, 0D);
                level.sendParticles(ParticleTypes.POOF, center.x, center.y + 1D, center.z, 60, 3D, 1D, 3D, 0.15D);
                level.playSound(null, center.x, center.y, center.z,
                        net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_IMPACT,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 0.8F);
            }
        }
        return true;
    }

    // -----------------------------------------------------------------------
    // useLeap — Lv1, Jump Boost III for 45s
    // -----------------------------------------------------------------------

    public static boolean useLeap(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 45 * 20, 2, false, false));
        player.level().sendParticles(ParticleTypes.POOF,
                player.getX(), player.getY() + 0.5D, player.getZ(),
                20, 0.4D, 0.3D, 0.4D, 0.08D);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.SLIME_JUMP,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 1.3F);
        return true;
    }

    // -----------------------------------------------------------------------
    // usePseudoLife — Lv2, 2 bars absorption for 2min
    // -----------------------------------------------------------------------

    public static boolean usePseudoLife(ServerPlayer player) {
        // 2 bars = 40 HP absorption; amplifier = ceil(40/4) - 1 = 9
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 2 * 60 * 20, 9, false, false));
        player.level().sendParticles(ParticleTypes.HEART,
                player.getX(), player.getY() + 1.2D, player.getZ(),
                20, 0.5D, 0.5D, 0.5D, 0.05D);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    // -----------------------------------------------------------------------
    // useRepair — Lv3, repair worn armor + items in hands by 20 durability
    // -----------------------------------------------------------------------

    public static boolean useRepair(ServerPlayer player) {
        // Armor slots
        for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[] {
                net.minecraft.world.entity.EquipmentSlot.HEAD,
                net.minecraft.world.entity.EquipmentSlot.CHEST,
                net.minecraft.world.entity.EquipmentSlot.LEGS,
                net.minecraft.world.entity.EquipmentSlot.FEET }) {
            repairStack(player.getItemBySlot(slot));
        }
        // Main hand and off hand (weapons/tools only — not armor)
        repairStack(player.getMainHandItem());
        repairStack(player.getOffhandItem());

        ServerLevel level = player.level();
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                player.getX(), player.getY() + 1.5D, player.getZ(),
                12, 0.4D, 0.3D, 0.4D, 0.05D);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.ANVIL_USE,
                net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private static void repairStack(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem())
            return;
        int newDamage = Math.max(0, stack.getDamageValue() - 20);
        stack.setDamageValue(newDamage);
    }

    // -----------------------------------------------------------------------
    // Ice Dagger projectile system
    // -----------------------------------------------------------------------

    private record IceDagger(UUID ownerId, Vec3 position, Vec3 direction, double speed, int remainingTicks) {
        IceDagger next(Vec3 nextPos) {
            return new IceDagger(ownerId, nextPos, direction, speed, remainingTicks - 1);
        }
    }

    private static final List<IceDagger> ICE_DAGGERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Must be called every tick alongside tickWings */
    public static void tickIceDaggers(MinecraftServer server) {
        java.util.Iterator<IceDagger> it = ICE_DAGGERS.iterator();
        while (it.hasNext()) {
            IceDagger dagger = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(dagger.ownerId());
            if (owner == null || !owner.isAlive() || dagger.remainingTicks() <= 0) {
                ICE_DAGGERS.remove(dagger);
                continue;
            }

            ServerLevel level = owner.level();
            Vec3 nextPos = dagger.position().add(dagger.direction().scale(dagger.speed()));

            // Block hit check
            net.minecraft.world.level.ClipContext clip = new net.minecraft.world.level.ClipContext(
                    dagger.position(), nextPos,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, owner);
            var blockHit = level.clip(clip);
            boolean hitBlock = blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK;

            // Entity hit check
            AABB path = new AABB(dagger.position(), nextPos).inflate(0.3D);
            LivingEntity target = null;
            double closestDist = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, path,
                    e -> e != owner && e.isAlive())) {
                var hit = e.getBoundingBox().inflate(0.3D).clip(dagger.position(), nextPos);
                if (hit.isPresent()) {
                    double d = dagger.position().distanceTo(hit.get());
                    if (d < closestDist) {
                        closestDist = d;
                        target = e;
                    }
                }
            }

            // Particles every step
            level.sendParticles(ParticleTypes.SNOWFLAKE,
                    dagger.position().x, dagger.position().y, dagger.position().z,
                    3, 0.05D, 0.05D, 0.05D, 0.0D);
            level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    dagger.position().x, dagger.position().y, dagger.position().z,
                    1, 0.0D, 0.0D, 0.0D, 0.0D);

            if (target != null) {
                float damage = 2.0F + owner.getRandom().nextFloat() * 4.0F;
                target.hurtServer(level, owner.damageSources().playerAttack(owner), damage);
                target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 4 * 20, 2, false, false), owner);
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
                        20, 0.3D, 0.4D, 0.3D, 0.04D);
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        net.minecraft.sounds.SoundEvents.SNOWBALL_THROW,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.6F);
                ICE_DAGGERS.remove(dagger);
                continue;
            }

            if (hitBlock) {
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                        blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z,
                        12, 0.1D, 0.1D, 0.1D, 0.05D);
                ICE_DAGGERS.remove(dagger);
                continue;
            }

            ICE_DAGGERS.remove(dagger);
            ICE_DAGGERS.add(dagger.next(nextPos));
        }
    }

    // -----------------------------------------------------------------------
    // Metamagic
    // -----------------------------------------------------------------------

    public static boolean useMetamagic(ServerPlayer player) {
        METAMAGIC_ACTIVE.put(player.getUUID(), true);
        player.sendSystemMessage(Component.literal("Метамагия: следующее заклинание бесплатно"));
        player.level().sendParticles(ParticleTypes.ENCHANT,
                player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.5D, 0.8D, 0.5D, 0.1D);
        return true;
    }

    public static boolean isMetamagicActive(ServerPlayer player) {
        return METAMAGIC_ACTIVE.getOrDefault(player.getUUID(), false);
    }

    public static void consumeMetamagic(ServerPlayer player) {
        METAMAGIC_ACTIVE.remove(player.getUUID());
    }

    // -----------------------------------------------------------------------
    // Wild Surge (300s CD set in AbilityDefinition)
    // -----------------------------------------------------------------------

    public static boolean useWildSurge(ServerPlayer player) {
        ServerLevel level = player.level();
        int roll = player.getRandom().nextInt(8);
        return switch (roll) {
            case 0 -> {
                AABB area = player.getBoundingBox().inflate(6.0D);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player))
                    e.hurtServer(level, player.damageSources().magic(), 10.0F);
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, player.getX(), player.getY(), player.getZ(), 3, 0,
                        0, 0, 0);
                player.sendSystemMessage(Component.literal("Дикий выброс: взрыв маны!"));
                yield true;
            }
            case 1 -> {
                double angle = player.getRandom().nextDouble() * Math.PI * 2;
                player.teleportTo(player.getX() + Math.cos(angle) * 10, player.getY(),
                        player.getZ() + Math.sin(angle) * 10);
                player.sendSystemMessage(Component.literal("Дикий выброс: случайный телепорт!"));
                yield true;
            }
            case 2 -> {
                WildMagicServerState.fillMana(player);
                player.sendSystemMessage(Component.literal("Дикий выброс: мана восстановлена!"));
                yield true;
            }
            case 3 -> {
                player.setInvisible(true);
                wildmagic.server.state.WildMagicZones.addInvisibility(player.getUUID(), level.getGameTime() + 10 * 20L);
                player.sendSystemMessage(Component.literal("Дикий выброс: невидимость!"));
                yield true;
            }
            case 4 -> {
                player.hurtServer(level, player.damageSources().magic(), 8.0F);
                player.sendSystemMessage(Component.literal("Дикий выброс: обратная волна!"));
                yield true;
            }
            case 5 -> {
                player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 5 * 20, 0));
                player.sendSystemMessage(Component.literal("Дикий выброс: левитация!"));
                yield true;
            }
            case 6 -> {
                player.heal(10.0F);
                level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1, player.getZ(), 10, 0.5D,
                        0.5D, 0.5D, 0.05D);
                player.sendSystemMessage(Component.literal("Дикий выброс: исцеление!"));
                yield true;
            }
            default -> {
                AABB area = player.getBoundingBox().inflate(8.0D);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player))
                    e.igniteForSeconds(5);
                level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 3, player.getZ(), 60, 4D, 1D,
                        4D, 0.1D);
                player.sendSystemMessage(Component.literal("Дикий выброс: огненный дождь!"));
                yield true;
            }
        };
    }

    private static void finishStormJump(ServerPlayer player, SorcererElement element) {
        ServerLevel level = player.level();
        Vec3 center = player.position();
        player.fallDistance = 0.0F;

        AABB area = player.getBoundingBox().inflate(5.0D);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> e != player && e.isAlive() && e.position().distanceTo(center) <= 5.0D)) {
            float damage = 8.0F + player.getRandom().nextFloat() * 7.0F;
            target.hurtServer(level, player.damageSources().magic(), damage);
            applyElementImpact(player, target, element, true);
        }

        applyDashTrail(player, level, element, center, true);
        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.2D, center.z, 4, 0.8D, 0.2D, 0.8D, 0.0D);
        playElementSound(level, center.x, center.y, center.z, element);
    }

    private static boolean isSafePlayerPosition(ServerLevel level, Vec3 position) {
        BlockPos feet = BlockPos.containing(position.x, position.y, position.z);
        BlockPos head = feet.above();
        return level.getBlockState(feet).isAir() && level.getBlockState(head).isAir();
    }

    private static void applyDashTrail(ServerPlayer player, ServerLevel level, SorcererElement element, Vec3 point, boolean endpoint) {
        BlockPos feet = BlockPos.containing(point.x, point.y, point.z);
        BlockPos ground = feet.below();
        switch (element) {
            case FIRE -> {
                if (level.getBlockState(feet).isAir() && level.getBlockState(ground).isSolid()) {
                    level.setBlock(feet, Blocks.FIRE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
                level.sendParticles(ParticleTypes.FLAME, point.x, point.y + 0.2D, point.z, endpoint ? 24 : 5, 0.35D, 0.15D, 0.35D, 0.04D);
            }
            case ICE -> {
                if (level.getBlockState(feet).isAir() && level.getBlockState(ground).isSolid()) {
                    level.setBlock(feet, Blocks.SNOW.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
                level.sendParticles(ParticleTypes.SNOWFLAKE, point.x, point.y + 0.2D, point.z, endpoint ? 24 : 5, 0.35D, 0.15D, 0.35D, 0.03D);
            }
            case LIGHTNING -> {
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, 15 * 20, 1, false, false));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y + 0.8D, point.z, endpoint ? 24 : 5, 0.35D, 0.35D, 0.35D, 0.05D);
            }
            case THUNDER -> {
                double radius = endpoint ? 4.0D : 1.5D;
                double strength = endpoint ? 2.4D : 1.2D;
                AABB area = new AABB(point.x - radius, point.y - radius, point.z - radius, point.x + radius, point.y + radius, point.z + radius);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != player && e.isAlive() && e.position().distanceTo(point) <= radius)) {
                    Vec3 kb = target.position().subtract(point).normalize().scale(strength);
                    target.push(kb.x, endpoint ? 1.0D : 0.35D, kb.z);
                    target.hurtMarked = true;
                }
                level.sendParticles(ParticleTypes.POOF, point.x, point.y + 0.5D, point.z, endpoint ? 36 : 6, 0.45D, 0.25D, 0.45D, 0.08D);
            }
            case POISON -> {
                POISON_CLOUDS.add(new PoisonCloud(player.getUUID(), point.add(0.0D, 0.5D, 0.0D), level.getGameTime() + 5 * 20L, level.getGameTime()));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y + 0.5D, point.z, endpoint ? 28 : 5, 0.45D, 0.2D, 0.45D, 0.03D);
            }
        }
    }

    private static void tickPoisonClouds(MinecraftServer server) {
        java.util.Iterator<PoisonCloud> iterator = POISON_CLOUDS.iterator();
        while (iterator.hasNext()) {
            PoisonCloud cloud = iterator.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(cloud.ownerId());
            if (owner == null || !owner.isAlive()) {
                POISON_CLOUDS.remove(cloud);
                continue;
            }
            ServerLevel level = owner.level();
            long now = level.getGameTime();
            if (now > cloud.expireTick()) {
                POISON_CLOUDS.remove(cloud);
                continue;
            }

            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, cloud.center().x, cloud.center().y, cloud.center().z, 3, 1.2D, 0.35D, 1.2D, 0.02D);
            if (now >= cloud.nextDamageTick()) {
                AABB area = new AABB(cloud.center().x - 2.0D, cloud.center().y - 1.0D, cloud.center().z - 2.0D,
                        cloud.center().x + 2.0D, cloud.center().y + 1.5D, cloud.center().z + 2.0D);
                for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                        e -> e != owner && e.isAlive() && e.position().distanceTo(cloud.center()) <= 2.5D)) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 3 * 20, 0, false, false), owner);
                }
                POISON_CLOUDS.remove(cloud);
                POISON_CLOUDS.add(new PoisonCloud(cloud.ownerId(), cloud.center(), cloud.expireTick(), now + 20L));
            }
        }
    }

    private static void applyElementImpact(ServerPlayer caster, LivingEntity target, SorcererElement element, boolean strongThunder) {
        switch (element) {
            case FIRE -> target.igniteForSeconds(5);
            case ICE -> target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5 * 20, 2, false, false), caster);
            case LIGHTNING -> target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5 * 20, 0, false, false), caster);
            case POISON -> target.addEffect(new MobEffectInstance(MobEffects.POISON, 6 * 20, 0, false, false), caster);
            case THUNDER -> {
                Vec3 kb = target.position().subtract(caster.position()).normalize().scale(strongThunder ? 2.2D : 1.2D);
                target.push(kb.x, strongThunder ? 1.0D : 0.5D, kb.z);
                target.hurtMarked = true;
            }
        }
    }


    private static void tickWebZones(MinecraftServer server) {
        WEB_ZONES.removeIf(zone -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(zone.ownerId());
            if (owner == null) return true;
            if (owner.level().getGameTime() <= zone.expireTick()) return false;
            ServerLevel level = owner.level();
            for(int dx=-2;dx<=2;dx++) for(int dy=-2;dy<=2;dy++) for(int dz=-2;dz<=2;dz++) {
                BlockPos pos = zone.center().offset(dx,dy,dz);
                if (level.getBlockState(pos).is(Blocks.COBWEB)) level.setBlock(pos, Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
            return true;
        });
    }
    private static void tickSunbeams(MinecraftServer server) {
        SUNBEAMS.removeIf(state -> {
            ServerPlayer owner = server.getPlayerList().getPlayer(state.ownerId());
            if (owner == null || !owner.isAlive()) return true;
            ServerLevel level = owner.level();
            long now = level.getGameTime();
            if (now > state.expireTick()) return true;
            if (now >= state.nextTick()) {
                Vec3 from = owner.getEyePosition();
                Vec3 to = from.add(owner.getLookAngle().normalize().scale(30.0D));
                AABB box = new AABB(from, to).inflate(0.8D);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive())) {
                    e.hurtServer(level, owner.damageSources().magic(), 2.0F);
                    e.igniteForSeconds(2);
                }
                drawSunBeam(level, from, to);
                SUNBEAMS.remove(state);
                SUNBEAMS.add(new Sunbeam(state.ownerId(), state.expireTick(), now + 20L));
            }
            return false;
        });
    }
    private static LivingEntity findRayTarget(ServerPlayer player, double range) {
        Vec3 start = player.getEyePosition(); Vec3 end = start.add(player.getLookAngle().normalize().scale(range));
        AABB path = new AABB(start, end).inflate(0.4D); LivingEntity best = null; double bestDist = Double.MAX_VALUE;
        for (LivingEntity entity : player.level().getEntitiesOfClass(LivingEntity.class, path, e -> e != player && e.isAlive())) {
            var hit = entity.getBoundingBox().inflate(0.4D).clip(start, end);
            if (hit.isPresent()) { double d = start.distanceTo(hit.get()); if (d < bestDist) { bestDist = d; best = entity; } }
        }
        return best;
    }
    private static void drawSunBeam(ServerLevel level, Vec3 from, Vec3 to) { Vec3 d = to.subtract(from); for(int i=0;i<=60;i++){ Vec3 p = from.add(d.scale(i/60.0D)); level.sendParticles(ParticleTypes.END_ROD,p.x,p.y,p.z,1,0.02,0.02,0.02,0); } }
    private static void drawGreenBeam(ServerLevel level, Vec3 from, Vec3 to) { Vec3 d = to.subtract(from); for(int i=0;i<=50;i++){ Vec3 p=from.add(d.scale(i/50.0D)); level.sendParticles(ParticleTypes.HAPPY_VILLAGER,p.x,p.y,p.z,1,0.01,0.01,0.01,0); } }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    public static void clearPlayer(ServerPlayer player) {
        WINGS_ACTIVE.remove(player.getUUID());
        STORM_JUMPS.remove(player.getUUID());
        POISON_CLOUDS.removeIf(cloud -> cloud.ownerId().equals(player.getUUID()));
        METAMAGIC_ACTIVE.remove(player.getUUID());
        SILENT_SPELL_ACTIVE.remove(player.getUUID());
        TWINNED_ACTIVE.remove(player.getUUID());
        MAGE_ARMOR_EXPIRE.remove(player.getUUID());
        FIRE_PALMS.remove(player.getUUID());
        SHIELD_READY.remove(player.getUUID());
        WEB_ZONES.removeIf(z -> z.ownerId().equals(player.getUUID()));
        SUNBEAMS.removeIf(z -> z.ownerId().equals(player.getUUID()));
        removeDragonHide(player);
        removeMageArmor(player);
    }

    // -----------------------------------------------------------------------
    // Shared helpers
    // -----------------------------------------------------------------------

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
            case FIRE -> level.sendParticles(ParticleTypes.FLAME, center.x, center.y, center.z, 4, spread, spread,
                    spread, 0.02D);
            case ICE -> level.sendParticles(ParticleTypes.SNOWFLAKE, center.x, center.y, center.z, 4, spread, spread,
                    spread, 0.02D);
            case LIGHTNING -> level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 4, spread,
                    spread, spread, 0.02D);
            case POISON -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 4, spread,
                    spread, spread, 0.02D);
            case THUNDER -> level.sendParticles(ParticleTypes.SONIC_BOOM, center.x, center.y, center.z, 1, 0, 0, 0, 0);
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
