package wildmagic.server.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import wildmagic.classdata.ArmorTier;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import wildmagic.server.WildMagicServerState;
import wildmagic.server.state.WildMagicZones;

public final class BardAbilities {
private static final Map<UUID, Long> HYPNOTIC_PATTERN_CASTERS = new ConcurrentHashMap<>();
private static final Map<UUID, List<net.minecraft.core.BlockPos>> FORCE_CAGE_BLOCKS = new ConcurrentHashMap<>();
private static final Map<UUID, Long> FORCE_CAGE_EXPIRE = new ConcurrentHashMap<>();
private static final Set<UUID> MORDENKAINEN_PENDING_HITS = ConcurrentHashMap.newKeySet();
// key = bard
private static final Map<UUID, Long> MORDENKAINEN_SWORD = new ConcurrentHashMap<>();
private static final Map<UUID, UUID> DOMINATED_ENTITIES = new ConcurrentHashMap<>();
// key = target, value = bard
private static final Map<UUID, Long> DOMINATE_EXPIRE = new ConcurrentHashMap<>();
private static final Map<UUID, Long> HYPNOTIZED_PLAYERS = new ConcurrentHashMap<>();
private static final Map<UUID, Long> GREATER_INVISIBLE_PLAYERS = new ConcurrentHashMap<>();

private BardAbilities() {
}


	public static boolean useBardInspiration(ServerPlayer player) {
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


    public static boolean useBardHaste(ServerPlayer player) {
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


public static boolean useBardFeatherFall(ServerPlayer player) {
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


public static boolean useBardHeatMetal(ServerPlayer player) {
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


public static boolean useBardShatter(ServerPlayer player) {
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




	public static boolean useBardInvisibility(ServerPlayer player) {
    WildMagicZones.addInvisibility(player.getUUID(), player.level().getGameTime() + 30 * 20L);
    player.setInvisible(true);

    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
            net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT,
            net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 1.4F);
    return true;
}


public static boolean useBardWordOfPower(ServerPlayer player) {
    ServerLevel level = player.level();
    AABB area = player.getBoundingBox().inflate(5.0D);
    List<ServerPlayer> targets = level.getEntitiesOfClass(ServerPlayer.class, area);

    int count = targets.size();
    if (count == 0) return false;

    // 80 HP pool split evenly, Absorption gives 4 HP per amplifier level.
    int hpEach = (int) Math.ceil(80.0D / count);
    int amplifier = Math.max(0, (int) Math.ceil(hpEach / 4.0D) - 1);

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


public static boolean useBardDominate(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 25.0D);
    if (target == null) {
        player.sendSystemMessage(Component.literal("Цель не найдена"));
        return false;
    }
    if (!(target instanceof ServerPlayer)) {
        player.sendSystemMessage(Component.literal("Подчинение личности работает только на игроков"));
        return false;
    }
    if (target == player) {
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


	public static boolean useBardHealingWord(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 12.0D);

    LivingEntity healTarget = target != null ? target : player;
    float heal = 2.0F + player.getRandom().nextFloat() * 6.0F + WildMagicServerState.get(player).level();
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


public static boolean useBardCharm(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 20.0D);
    if (target == null) {
        return false;
    }

    WildMagicZones.addCharmed(player.getUUID(), target.getUUID(), level.getGameTime() + 15 * 20L);

    level.sendParticles(ParticleTypes.ENCHANT,
            target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
            20, 0.5D, 0.8D, 0.5D, 0.1D);
    level.playSound(null, target.getX(), target.getY(), target.getZ(),
            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(),
            net.minecraft.sounds.SoundSource.PLAYERS,
            1.0F, 0.8F);
    return true;
}


public static boolean useBardHeroism(ServerPlayer player) {
    ServerLevel level = player.level();
    LivingEntity target = raycastLivingEntity(player, 12.0D);
    long expireTime = level.getGameTime() + 20 * 20L;

    WildMagicZones.applyHeroism(player, level, expireTime);
    if (target instanceof ServerPlayer targetPlayer) {
        WildMagicZones.applyHeroism(targetPlayer, level, expireTime);
    }
    return true;
}


public static boolean useBardDispel(ServerPlayer player) {
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


public static boolean useBardSlowZone(ServerPlayer player) {
    ServerLevel level = player.level();
    Vec3 target = raycastBlock(player, 30.0D);

    UUID zoneId = UUID.randomUUID();
    WildMagicZones.addSlowZone(zoneId, new double[]{
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


public static void drawZoneBorder(ServerLevel level, Vec3 center, double size) {
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


public static void spawnBorderParticle(ServerLevel level, double x, double y, double z) {
    level.sendParticles(ParticleTypes.WITCH, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
}


public static boolean useBardForceCage(ServerPlayer player) {
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
    WildMagicZones.addSilenceZone(cageId, new double[]{
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


public static void tickForceCage(MinecraftServer server) {
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
            WildMagicZones.removeSilenceZone(entry.getKey());
            return true;
        }
        return false;
    });

    for (List<net.minecraft.core.BlockPos> blocks : FORCE_CAGE_BLOCKS.values()) {
        ServerLevel level = server.overworld();
        if (blocks.isEmpty()) {
            continue;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (net.minecraft.core.BlockPos pos : blocks) {
            if (!level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS)) {
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.RED_STAINED_GLASS.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        AABB cageBox = new AABB(minX + 1.0D, minY + 1.0D, minZ + 1.0D,
                maxX, maxY, maxZ);
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, cageBox)) {
            player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 40, 2, false, false));
        }
    }
}



	public static boolean useBardSoundWave(ServerPlayer player) {
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


public static boolean useBardMordenkainenSword(ServerPlayer player) {
    ServerLevel level = player.level();
    player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60 * 20, 1, false, false), player);

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


public static void tickMordenkainenSword(MinecraftServer server) {
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


public static boolean useBardDimensionDoor(ServerPlayer player) {
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


public static boolean useBardGreaterInvisibility(ServerPlayer player) {
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


public static void tickGreaterInvisibility(MinecraftServer server) {
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


public static boolean useBardHypnoticPattern(ServerPlayer player) {
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


public static void tickHypnoticPattern(MinecraftServer server) {
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


public static void tickDominate(MinecraftServer server) {
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
        if (dist > 3.0D) {
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
        mob.getLookControl().setLookAt(bardTarget);
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
                    && bardTarget != dominatedPlayer) {
                Vec3 dir = bardTarget.getEyePosition().subtract(dominatedPlayer.getEyePosition()).normalize();
                float yaw = (float)(Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                float pitch = (float)(Math.toDegrees(-Math.asin(dir.y)));
                dominatedPlayer.setYRot(yaw);
                dominatedPlayer.setXRot(pitch);

                if (bardTarget.distanceTo(dominatedPlayer) < 4.0D) {
                    bardTarget.hurtServer(level,
                            dominatedPlayer.damageSources().playerAttack(dominatedPlayer), 4.0F);
                }
            }
        }

        // партиклы над подчинённым
        level.sendParticles(ParticleTypes.WITCH,
                dominated.getX(), dominated.getY() + dominated.getBbHeight() + 0.3D, dominated.getZ(),
                2, 0.2D, 0.1D, 0.2D, 0.02D);
    }
}


public static boolean useBardMistyStep(ServerPlayer player) {
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


public static boolean useBardSilence(ServerPlayer player) {
    ServerLevel level = player.level();
    Vec3 target = raycastBlock(player, 20.0D);

    WildMagicZones.addSilenceZone(java.util.UUID.randomUUID(), new double[]{
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


public static Vec3 raycastBlock(ServerPlayer player, double range) {
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


	public static LivingEntity raycastLivingEntity(ServerPlayer player, double range) {
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

public static void clearPlayer(ServerPlayer player) {
    HYPNOTIZED_PLAYERS.remove(player.getUUID());
    GREATER_INVISIBLE_PLAYERS.remove(player.getUUID());
    MORDENKAINEN_SWORD.remove(player.getUUID());
    DOMINATED_ENTITIES.entrySet().removeIf(e -> e.getValue().equals(player.getUUID()));
    DOMINATE_EXPIRE.entrySet().removeIf(e -> DOMINATED_ENTITIES.containsKey(e.getKey()) == false);
    HYPNOTIC_PATTERN_CASTERS.remove(player.getUUID());
}

public static boolean isGreaterInvisible(ServerPlayer player) {
    return GREATER_INVISIBLE_PLAYERS.containsKey(player.getUUID());
}

}
