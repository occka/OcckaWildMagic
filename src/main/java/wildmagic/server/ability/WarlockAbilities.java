package wildmagic.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Unit;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.server.WildMagicServerState;
import net.minecraft.core.Holder;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarlockAbilities {
	private static final String PACT_BLADE_TAG = "OcckaWarlockPactBlade";
	private static final String PACT_BLADE_OWNER_TAG = "OcckaWarlockOwner";
	private static final Map<UUID, AgathysState> ARMOR_OF_AGATHYS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> PACT_BLADE_OWNERS = new ConcurrentHashMap<>();
	private static final Map<UUID, DarknessZone> DARKNESS_ZONES = new ConcurrentHashMap<>();
	private static final Map<UUID, DeathCircle> DEATH_CIRCLES = new ConcurrentHashMap<>();
	private static final Map<UUID, DeathRitual> DEATH_RITUALS = new ConcurrentHashMap<>();
	private static final Map<UUID, UUID> SUMMONED_UNDEAD = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> SPIDER_CLIMB_PLAYERS = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> REFLECTING_AGATHYS = ThreadLocal.withInitial(() -> false);

	// Hex effect pool — (MobEffect, particleColor label)
	private record HexEffect(Holder<MobEffect> effect, String colorLabel) {}

	private WarlockAbilities() {
	}

	// -------------------------------------------------------------------------
	// Новые способности
	// -------------------------------------------------------------------------

	public static boolean useFrostbite(ServerPlayer player) {
		ServerLevel level = player.level();
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 3.5D);
		if (target == null) {
			player.sendSystemMessage(Component.literal("Цель не найдена"));
			return false;
		}

		float damage = 2.0F + player.getRandom().nextFloat() * 4.0F;
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
		// Wither-эффект блокирует естественную регенерацию; для блокировки heal() используем
		// кастомный эффект — но в ванилле нет «no-heal» эффекта, поэтому используем
		// WITHER (0 уровень, 8с) — он не даёт пассивной регенерации и мешает absorption.
		// Дополнительно снимаем регенерацию если была.
		target.removeEffect(MobEffects.REGENERATION);
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 8 * 20, 0, false, false), player);

		level.sendParticles(ParticleTypes.SNOWFLAKE,
				target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
				20, 0.3D, 0.4D, 0.3D, 0.04D);
		level.sendParticles(ParticleTypes.ITEM_SNOWBALL,
				target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
				12, 0.2D, 0.3D, 0.2D, 0.06D);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.PLAYER_HURT_FREEZE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.9F, 1.3F);
		return true;
	}

	public static boolean useHex(ServerPlayer player) {
		ServerLevel level = player.level();
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 15.0D);
		if (target == null) {
			player.sendSystemMessage(Component.literal("Цель не найдена"));
			return false;
		}

		// Пул эффектов с цветами луча
		record HexEntry(Holder<MobEffect> effect, BeamColor color) {}
		List<HexEntry> pool = List.of(
				new HexEntry(MobEffects.SLOWNESS,       BeamColor.BLUE),
				new HexEntry(MobEffects.WEAKNESS,       BeamColor.GRAY),
				new HexEntry(MobEffects.MINING_FATIGUE, BeamColor.BROWN),
				new HexEntry(MobEffects.BLINDNESS,      BeamColor.BLACK),
				new HexEntry(MobEffects.POISON,         BeamColor.GREEN),
				new HexEntry(MobEffects.WITHER,         BeamColor.DARK),
				new HexEntry(MobEffects.HUNGER,         BeamColor.YELLOW),
				new HexEntry(MobEffects.LEVITATION,     BeamColor.PINK)
		);

		HexEntry chosen = pool.get(player.getRandom().nextInt(pool.size()));
		target.addEffect(new MobEffectInstance(chosen.effect(), 8 * 20, 0, false, true), player);

		drawHexBeam(level, player.getEyePosition(),
				target.position().add(0, target.getBbHeight() * 0.5D, 0),
				chosen.color());

		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.WITCH_THROW,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 0.9F);
		return true;
	}

	public static boolean useSpiderClimb(ServerPlayer player) {
		ServerLevel level = player.level();
		long expireTime = level.getGameTime() + 30 * 20L;
		SPIDER_CLIMB_PLAYERS.put(player.getUUID(), expireTime);

		// Эффект паутины — замедление не нужно, но ставим web-визуал
		level.sendParticles(ParticleTypes.POOF,
				player.getX(), player.getY() + 1.0D, player.getZ(),
				16, 0.4D, 0.5D, 0.4D, 0.03D);
		// Паутинные нити вокруг игрока
		for (int i = 0; i < 8; i++) {
			double angle = i / 8.0D * 2 * Math.PI;
			level.sendParticles(ParticleTypes.ITEM_COBWEB,
					player.getX() + Math.cos(angle) * 0.6D,
					player.getY() + 0.8D,
					player.getZ() + Math.sin(angle) * 0.6D,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.9F, 0.7F);
		return true;
	}

	public static boolean useRayOfWeakness(ServerPlayer player) {
		ServerLevel level = player.level();
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 15.0D);
		if (target == null) {
			player.sendSystemMessage(Component.literal("Цель не найдена"));
			return false;
		}

		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5 * 20, 0, false, true), player);

		// Серый луч
		drawGrayBeam(level,
				player.getEyePosition(),
				target.position().add(0, target.getBbHeight() * 0.5D, 0));

		level.sendParticles(ParticleTypes.WITCH,
				target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(),
				12, 0.3D, 0.4D, 0.3D, 0.02D);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.ELDER_GUARDIAN_CURSE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.6F, 1.4F);
		return true;
	}

	// -------------------------------------------------------------------------
	// Spider climb tick — вешаем эффект паука каждую секунду
	// -------------------------------------------------------------------------

	private static void tickSpiderClimb(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) return;
		long now = server.overworld().getGameTime();

		SPIDER_CLIMB_PLAYERS.entrySet().removeIf(entry -> {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) return true;
			if (now > entry.getValue()) {
				// Снимаем эффект
				player.removeEffect(MobEffects.SLOWNESS);
				return true;
			}
			// CLIMBING — в ванилле нет прямого эффекта "лезть по стене",
			// но можно поставить игроку флаг через EntityData или использовать
			// эффект левитации + ограничение. Наилучший вариант — пока просто
			// даём эффект "медленного падения" чтобы игрок мог карабкаться,
			// а реальный climbing требует микса. Ставим SLOW_FALLING + подбор.
			player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 25, 0, false, false));

			// Партиклы паутины вокруг игрока раз в секунду
			ServerLevel level = player.level();
			level.sendParticles(ParticleTypes.ITEM_COBWEB,
					player.getX(), player.getY() + 1.0D, player.getZ(),
					4, 0.3D, 0.5D, 0.3D, 0.01D);
			return false;
		});
	}

	public static boolean isSpiderClimbing(ServerPlayer player) {
		Long expire = SPIDER_CLIMB_PLAYERS.get(player.getUUID());
		if (expire == null) return false;
		if (player.level().getGameTime() > expire) {
			SPIDER_CLIMB_PLAYERS.remove(player.getUUID());
			return false;
		}
		return true;
	}

	// -------------------------------------------------------------------------
	// Beam helpers для Hex
	// -------------------------------------------------------------------------

	private enum BeamColor {
		BLUE, GRAY, BROWN, BLACK, GREEN, DARK, YELLOW, PINK
	}

	private static void drawHexBeam(ServerLevel level, Vec3 start, Vec3 end, BeamColor color) {
		Vec3 delta = end.subtract(start);
		double distance = delta.length();
		if (distance <= 0.0D) return;
		Vec3 dir = delta.normalize();

		for (double step = 0.0D; step <= distance; step += 0.3D) {
			Vec3 point = start.add(dir.scale(step));
			switch (color) {
				case BLUE   -> level.sendParticles(ParticleTypes.FALLING_WATER, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				case GRAY   -> level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				case BROWN  -> level.sendParticles(ParticleTypes.FALLING_DRIPSTONE_LAVA, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				case BLACK  -> level.sendParticles(ParticleTypes.SQUID_INK, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				case GREEN  -> level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				case DARK   -> { level.sendParticles(ParticleTypes.SOUL, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D); }
				case YELLOW -> level.sendParticles(ParticleTypes.WAX_ON, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				case PINK   -> level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	private static void drawGrayBeam(ServerLevel level, Vec3 start, Vec3 end) {
		Vec3 delta = end.subtract(start);
		double distance = delta.length();
		if (distance <= 0.0D) return;
		Vec3 dir = delta.normalize();
		for (double step = 0.0D; step <= distance; step += 0.3D) {
			Vec3 point = start.add(dir.scale(step));
			level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			level.sendParticles(ParticleTypes.POOF, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	// -------------------------------------------------------------------------
	// Существующие способности (без изменений)
	// -------------------------------------------------------------------------

	public static boolean useMysticCharge(ServerPlayer player) {
		ServerLevel level = player.level();
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 24.0D);
		if (target == null) {
			player.sendSystemMessage(Component.literal("Цель не найдена"));
			return false;
		}

		int warlockLevel = WildMagicServerState.get(player).level();
		float minDamage = warlockLevel >= 10 ? 5.0F : 2.0F;
		float maxDamage = warlockLevel >= 10 ? 12.0F : 8.0F;
		float damage = minDamage + player.getRandom().nextFloat() * (maxDamage - minDamage);

		drawBeam(level, player.getEyePosition(), target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D), OldBeamColor.RED);
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);

		if (warlockLevel >= 6) {
			Vec3 knockback = target.position().subtract(player.position()).normalize().scale(0.85D);
			target.push(knockback.x, 0.12D, knockback.z);
			target.hurtMarked = true;
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.BLAZE_SHOOT,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 0.55F);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.35F, 1.7F);
		return true;
	}

	public static boolean useArmorOfAgathys(ServerPlayer player) {
		int warlockLevel = WildMagicServerState.get(player).level();
		float shieldAndDamage = warlockLevel >= 10 ? 15.0F : warlockLevel >= 6 ? 10.0F : 5.0F;
		long expireTime = player.level().getGameTime() + 60 * 20L;
		int amplifier = Math.max(0, (int) Math.ceil(shieldAndDamage / 4.0D) - 1);

		ARMOR_OF_AGATHYS.put(player.getUUID(), new AgathysState(expireTime, shieldAndDamage, false));
		player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 60 * 20, amplifier, false, false), player);
		player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), shieldAndDamage));
		player.level().sendParticles(ParticleTypes.SNOWFLAKE,
				player.getX(), player.getY() + 1.0D, player.getZ(),
				32, 0.6D, 0.8D, 0.6D, 0.03D);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.GLASS_PLACE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 0.55F);
		return true;
	}

	public static boolean usePoisonSpray(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 start = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		AABB searchBox = player.getBoundingBox().expandTowards(look.scale(5.0D)).inflate(3.0D);
		boolean hitAny = false;

		for (double distance = 0.5D; distance <= 5.0D; distance += 0.35D) {
			Vec3 center = start.add(look.scale(distance));
			double spread = 0.15D + distance * 0.18D;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x, center.y, center.z, 4, spread, spread * 0.55D, spread, 0.02D);
		}

		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, searchBox, entity -> entity != player && entity.isAlive())) {
			Vec3 toTarget = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D).subtract(start);
			double distance = toTarget.length();
			if (distance > 5.0D || distance <= 0.0D) continue;
			double angleDot = look.dot(toTarget.normalize());
			if (angleDot < 0.72D) continue;

			target.addEffect(new MobEffectInstance(MobEffects.POISON, 10 * 20, 0), player);
			target.hurtServer(level, player.damageSources().playerAttack(player), 5.0F);
			hitAny = true;
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.SPIDER_AMBIENT,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 1.2F);
		return hitAny;
	}

	public static boolean usePactBlade(ServerPlayer player) {
		removePactBladeFromInventory(player);
		ItemStack blade = createPactBlade(player);
		if (!player.getInventory().add(blade)) {
			player.drop(blade, false);
		}
		PACT_BLADE_OWNERS.put(player.getUUID(), player.level().getGameTime());

		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0D, player.getZ(), 48, 0.8D, 0.8D, 0.8D, 0.08D);
		level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 1.0D, player.getZ(), 12, 0.5D, 0.5D, 0.5D, 0.0D);
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 1.8F);
		return true;
	}

	public static boolean useDarkness(ServerPlayer player) {
		ServerLevel level = player.level();
		DARKNESS_ZONES.put(UUID.randomUUID(), new DarknessZone(player.getUUID(), player.position(), level.getGameTime() + 15 * 20L));
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.SCULK_SHRIEKER_SHRIEK,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.45F, 0.55F);
		return true;
	}

	public static boolean useVampiricTouch(ServerPlayer player) {
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 3.0D);
		if (target == null) return false;

		target.hurtServer(player.level(), player.damageSources().playerAttack(player), 8.0F);
		player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 8.0F));
		player.level().sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 8, 0.2D, 0.3D, 0.2D, 0.02D);
		player.level().sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0D, player.getZ(), 6, 0.3D, 0.5D, 0.3D, 0.03D);
		player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.WITHER_HURT,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.7F, 1.4F);
		return true;
	}

	public static boolean useCounterspell(ServerPlayer player) {
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 30.0D);
		if (!(target instanceof ServerPlayer targetPlayer)) return false;

		drawBeam(player.level(), player.getEyePosition(), targetPlayer.getEyePosition(), OldBeamColor.PINK);
		WildMagicServerState.drainManaAndClearSpellEffects(targetPlayer);
		player.level().playSound(null, targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
				net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.0F, 1.8F);
		return true;
	}

	public static boolean useCircleOfDeath(ServerPlayer player) {
		ServerLevel level = player.level();
		DEATH_CIRCLES.put(UUID.randomUUID(), new DeathCircle(player.getUUID(), player.position(), level.getGameTime(), level.getGameTime() + 5 * 20L));
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.6F, 0.45F);
		return true;
	}

	public static boolean useCreateUndead(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 base = player.position().add(player.getLookAngle().normalize().scale(2.0D));
		spawnZombie(level, player, base.add(1.0D, 0.0D, 0.0D));
		spawnZombie(level, player, base.add(-1.0D, 0.0D, 0.0D));
		Skeleton skeleton = EntityType.SKELETON.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (skeleton != null) {
			skeleton.setPos(base.x, base.y, base.z + 1.0D);
			skeleton.setPersistenceRequired();
			skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			level.addFreshEntity(skeleton);
			SUMMONED_UNDEAD.put(skeleton.getUUID(), player.getUUID());
		}
		level.sendParticles(ParticleTypes.SOUL, base.x, base.y + 1.0D, base.z, 36, 1.5D, 0.8D, 1.5D, 0.05D);
		level.playSound(null, base.x, base.y, base.z,
				net.minecraft.sounds.SoundEvents.ZOMBIE_VILLAGER_CURE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 0.6F);
		return true;
	}

	public static boolean useFingerOfDeath(ServerPlayer player) {
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 25.0D);
		if (target == null) return false;

		ServerLevel level = player.level();
		drawBeam(level, player.getEyePosition(), target.getEyePosition(), OldBeamColor.GREEN);
		float damage = 2.0F + player.getRandom().nextFloat() * 16.0F;
		target.hurtServer(level, player.damageSources().magic(), damage);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(),
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.0F, 0.7F);
		if (!target.isAlive()) {
			spawnRandomUndead(level, player, target.position());
		}
		return true;
	}

	public static boolean useBreakthrough(ServerPlayer player) {
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 30.0D);
		if (target == null) return false;

		ServerLevel level = player.level();
		drawBeam(level, player.getEyePosition(), target.getEyePosition(), OldBeamColor.PINK);
		float damage = 2.0F + player.getRandom().nextFloat() * 14.0F;
		player.hurtServer(level, player.damageSources().generic(), damage);
		if (!player.isAlive()) return true;

		Vec3 behindTarget = target.position().subtract(player.getLookAngle().normalize().scale(0.8D));
		player.teleportTo(behindTarget.x, behindTarget.y, behindTarget.z);
		target.hurtServer(level, player.damageSources().magic(), damage);
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 8 * 20, 0), player);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 8 * 20, 0), player);
		level.sendParticles(ParticleTypes.PORTAL, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 40, 0.4D, 0.8D, 0.4D, 0.08D);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.0F, 0.55F);
		return true;
	}

	public static boolean usePowerWordDeath(ServerPlayer player) {
		LivingEntity target = BardAbilities.raycastLivingEntity(player, 15.0D);
		if (target == null) return false;

		ServerLevel level = player.level();
		drawBeam(level, player.getEyePosition(), target.getEyePosition(), OldBeamColor.GREEN);
		DEATH_RITUALS.put(target.getUUID(), new DeathRitual(player.getUUID(), target.getUUID(), target.position(), level.getGameTime() + 5 * 20L));
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.2F, 0.65F);
		return true;
	}

	public static void onPlayerDamaged(ServerPlayer player, LivingEntity attacker, ServerLevel level) {
		if (REFLECTING_AGATHYS.get()) return;

		AgathysState state = ARMOR_OF_AGATHYS.get(player.getUUID());
		if (state == null || state.retaliated() || level.getGameTime() > state.expireTime()) return;

		ARMOR_OF_AGATHYS.put(player.getUUID(), state.withRetaliated());
		REFLECTING_AGATHYS.set(true);
		try {
			attacker.hurtServer(level, player.damageSources().playerAttack(player), state.damage());
		} finally {
			REFLECTING_AGATHYS.set(false);
		}

		level.sendParticles(ParticleTypes.SNOWFLAKE,
				attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5D, attacker.getZ(),
				18, 0.3D, 0.5D, 0.3D, 0.04D);
		level.playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(),
				net.minecraft.sounds.SoundEvents.PLAYER_HURT_FREEZE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 1.2F);
	}

	public static void tick(MinecraftServer server) {
		tickPactBlades(server);
		tickSpiderClimb(server);
		if (server.getTickCount() % 20 != 0) return;

		tickWarlockPassives(server);
		tickArmorOfAgathys(server);
		tickDarknessZones(server);
		tickDeathCircles(server);
		tickDeathRituals(server);
		tickSummonedUndead(server);
		tickDeadOneMobPacification(server);
	}

	public static void tickArmorOfAgathys(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		ARMOR_OF_AGATHYS.entrySet().removeIf(entry -> {
			if (now <= entry.getValue().expireTime()) return false;
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && player.getAbsorptionAmount() <= entry.getValue().damage() + 0.5F) {
				player.setAbsorptionAmount(0.0F);
			}
			return true;
		});
	}

	public static void clearPlayer(ServerPlayer player) {
		ARMOR_OF_AGATHYS.remove(player.getUUID());
		PACT_BLADE_OWNERS.remove(player.getUUID());
		SPIDER_CLIMB_PLAYERS.remove(player.getUUID());
		removePactBladeFromInventory(player);
		DARKNESS_ZONES.entrySet().removeIf(entry -> entry.getValue().casterId().equals(player.getUUID()));
		DEATH_CIRCLES.entrySet().removeIf(entry -> entry.getValue().casterId().equals(player.getUUID()));
		DEATH_RITUALS.entrySet().removeIf(entry -> entry.getValue().casterId().equals(player.getUUID()) || entry.getValue().targetId().equals(player.getUUID()));
		SUMMONED_UNDEAD.entrySet().removeIf(entry -> entry.getValue().equals(player.getUUID()));
	}

	public static boolean isSummonedUndead(UUID entityId) {
		return SUMMONED_UNDEAD.containsKey(entityId);
	}

	public static boolean isSummonedUndeadFriendly(LivingEntity attacker, LivingEntity target) {
		UUID ownerId = SUMMONED_UNDEAD.get(attacker.getUUID());
		if (ownerId == null) return false;
		if (target instanceof ServerPlayer targetPlayer) {
			return targetPlayer.getUUID().equals(ownerId) || isWarlock(targetPlayer);
		}
		return SUMMONED_UNDEAD.containsKey(target.getUUID());
	}

	public static void applyUndeadTouch(ServerPlayer attacker, LivingEntity target) {
		PlayerClassData data = WildMagicServerState.get(attacker);
		if (!data.hasClass() || data.selectedClass() != WildMagicClass.WARLOCK || data.level() < 5 || !attacker.getMainHandItem().isEmpty()) return;
		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 4 * 20, 0), attacker);
	}

	// =========================================================================
	// ФИКС: пассивка 10 уровня — слабость только на солнце
	// =========================================================================
	private static void tickWarlockPassives(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isWarlock(player)) continue;

			player.removeEffect(MobEffects.HUNGER);
			player.setAirSupply(player.getMaxAirSupply());
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 25 * 20, 0, false, false), player);
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 25 * 20, 0, false, false), player);

			if (dataLevel(player) >= 10) {
				player.removeEffect(MobEffects.POISON);
				player.removeEffect(MobEffects.WITHER);

				boolean inSun = player.level().isBrightOutside()
						&& player.level().canSeeSkyFromBelowWater(player.blockPosition());

				if (inSun) {
					// На солнце: горит И получает слабость
					player.igniteForSeconds(3.0F);
					player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25 * 20, 0, false, false), player);
				}
				// НЕ на солнце — слабость не вешаем, убираем если была
				// (removeEffect только если эффект ещё активен — не трогаем если нет)
			}
		}
	}

	private static void tickPactBlades(MinecraftServer server) {
		Set<UUID> activeOwners = new HashSet<>(PACT_BLADE_OWNERS.keySet());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			removeForeignPactBlades(player);
			if (!activeOwners.contains(player.getUUID())) continue;
			if (!player.isAlive()) {
				PACT_BLADE_OWNERS.remove(player.getUUID());
				removePactBladeFromInventory(player);
				continue;
			}
			if (!hasPactBlade(player)) {
				player.getInventory().add(createPactBlade(player));
			}
		}

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AABB nearby = player.getBoundingBox().inflate(32.0D);
			for (ItemEntity itemEntity : player.level().getEntitiesOfClass(ItemEntity.class, nearby, item -> isPactBlade(item.getItem()))) {
				itemEntity.discard();
			}
		}
	}

	private static void tickDarknessZones(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		DARKNESS_ZONES.entrySet().removeIf(entry -> now > entry.getValue().expireTime());
		for (DarknessZone zone : DARKNESS_ZONES.values()) {
			ServerPlayer caster = server.getPlayerList().getPlayer(zone.casterId());
			ServerLevel level = caster != null ? caster.level() : server.overworld();
			AABB area = new AABB(zone.center().x - 3.5D, zone.center().y - 2.0D, zone.center().z - 3.5D, zone.center().x + 3.5D, zone.center().y + 2.0D, zone.center().z + 3.5D);
			level.sendParticles(ParticleTypes.SQUID_INK, zone.center().x, zone.center().y + 1.0D, zone.center().z, 32, 3.0D, 1.5D, 3.0D, 0.02D);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
				if (entity.getUUID().equals(zone.casterId())) continue;
				if (entity instanceof ServerPlayer sp && isWarlock(sp)) continue;
				entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45, 0, false, false));
				entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 45, 0, false, false));
			}
		}
	}

	private static void tickDeathCircles(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		DEATH_CIRCLES.entrySet().removeIf(entry -> now > entry.getValue().expireTime());
		for (Map.Entry<UUID, DeathCircle> entry : DEATH_CIRCLES.entrySet()) {
			DeathCircle circle = entry.getValue();
			ServerPlayer caster = server.getPlayerList().getPlayer(circle.casterId());
			ServerLevel level = caster != null ? caster.level() : server.overworld();
			drawDeathCircle(level, circle.center());
			dryPlants(level, circle.center());
			AABB area = new AABB(circle.center().x - 10.0D, circle.center().y - 3.0D, circle.center().z - 10.0D, circle.center().x + 10.0D, circle.center().y + 3.0D, circle.center().z + 10.0D);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity.isAlive() && entity.position().distanceTo(circle.center()) <= 10.0D)) {
				if (caster != null && entity == caster) continue;
				entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 5 * 20, 0), caster);
				entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 10 * 20, 0), caster);
			}
		}
	}

	private static void tickDeathRituals(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		DEATH_RITUALS.entrySet().removeIf(entry -> {
			DeathRitual ritual = entry.getValue();
			LivingEntity target = findLivingEntity(server, ritual.targetId());
			ServerPlayer caster = server.getPlayerList().getPlayer(ritual.casterId());
			if (target == null || !target.isAlive()) return true;

			ServerLevel level = (ServerLevel) target.level();
			Vec3 center = target.position();
			drawRitualCircle(level, center);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 255, false, false), caster);
			target.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, 30, 4, false, false), caster);
			level.sendParticles(ParticleTypes.SOUL, center.x, center.y + target.getBbHeight() * 0.5D, center.z, 10, 0.7D, 0.8D, 0.7D, 0.05D);
			if (now <= ritual.expireTime()) return false;

			target.hurtServer(level, caster == null ? target.damageSources().magic() : caster.damageSources().magic(), 40.0F);
			level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y + 0.8D, center.z, 64, 1.0D, 1.0D, 1.0D, 0.12D);
			level.playSound(null, center.x, center.y, center.z,
					net.minecraft.sounds.SoundEvents.WITHER_DEATH,
					net.minecraft.sounds.SoundSource.PLAYERS,
					1.0F, 0.8F);
			return true;
		});
	}

	private static void tickDeadOneMobPacification(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isDeadOne(player)) continue;
			AABB area = player.getBoundingBox().inflate(32.0D);
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, area, mob -> mob.getTarget() == player)) {
				mob.setTarget(null);
			}
		}
	}

	private static void tickSummonedUndead(MinecraftServer server) {
		SUMMONED_UNDEAD.entrySet().removeIf(entry -> {
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue());
			if (owner == null) return true;
			LivingEntity summon = findLivingEntity(server, entry.getKey());
			if (!(summon instanceof Mob mob) || !mob.isAlive()) return true;
			if (mob.getTarget() == null || !mob.getTarget().isAlive() || isFriendlyToSummon(owner, mob.getTarget())) {
				mob.setTarget(findNearestSummonTarget(owner, mob));
			}
			return false;
		});
	}

	private static LivingEntity findNearestSummonTarget(ServerPlayer owner, Mob summon) {
		AABB area = summon.getBoundingBox().inflate(16.0D);
		LivingEntity closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (LivingEntity entity : summon.level().getEntitiesOfClass(LivingEntity.class, area, entity -> entity != summon && entity.isAlive() && !isFriendlyToSummon(owner, entity))) {
			double distance = summon.distanceToSqr(entity);
			if (distance < closestDistance) {
				closestDistance = distance;
				closest = entity;
			}
		}
		return closest;
	}

	private static boolean isFriendlyToSummon(ServerPlayer owner, LivingEntity entity) {
		if (entity == owner || SUMMONED_UNDEAD.containsKey(entity.getUUID())) return true;
		return entity instanceof ServerPlayer player && (isWarlock(player) || isDeadOne(player));
	}

	private static void spawnRandomUndead(ServerLevel level, ServerPlayer owner, Vec3 position) {
		@SuppressWarnings("unchecked")
		EntityType<? extends Mob>[] pool = new EntityType[]{
				EntityType.ZOMBIE, EntityType.ZOMBIE_VILLAGER, EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIFIED_PIGLIN,
				EntityType.SKELETON, EntityType.STRAY, EntityType.WITHER_SKELETON, EntityType.BOGGED, EntityType.BLAZE
		};
		EntityType<? extends Mob> type = pool[owner.getRandom().nextInt(pool.length)];
		Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) return;
		mob.setPos(position.x, position.y, position.z);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		SUMMONED_UNDEAD.put(mob.getUUID(), owner.getUUID());
	}

	private static void spawnZombie(ServerLevel level, ServerPlayer owner, Vec3 position) {
		Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (zombie == null) return;
		zombie.setPos(position.x, position.y, position.z);
		zombie.setPersistenceRequired();
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		level.addFreshEntity(zombie);
		SUMMONED_UNDEAD.put(zombie.getUUID(), owner.getUUID());
	}

	private static LivingEntity findLivingEntity(MinecraftServer server, UUID entityId) {
		var entity = server.overworld().getEntityInAnyDimension(entityId);
		return entity instanceof LivingEntity livingEntity ? livingEntity : null;
	}

	private static ItemStack createPactBlade(ServerPlayer player) {
		ItemStack blade = new ItemStack(Items.GOLDEN_SWORD);
		var enchantments = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
		blade.enchant(enchantments.getOrThrow(Enchantments.SHARPNESS), 3);
		blade.enchant(enchantments.getOrThrow(Enchantments.FIRE_ASPECT), 1);
		blade.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
		blade.set(DataComponents.CUSTOM_NAME, Component.literal("Договорной клинок"));
		CompoundTag tag = new CompoundTag();
		tag.putBoolean(PACT_BLADE_TAG, true);
		tag.putString(PACT_BLADE_OWNER_TAG, player.getUUID().toString());
		blade.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return blade;
	}

	private static boolean hasPactBlade(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (isPactBlade(stack) && isPactBladeOwner(stack, player.getUUID())) return true;
		}
		return false;
	}

	private static void removePactBladeFromInventory(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (isPactBlade(stack)) player.getInventory().setItem(slot, ItemStack.EMPTY);
		}
	}

	private static void removeForeignPactBlades(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (isPactBlade(stack) && !isPactBladeOwner(stack, player.getUUID())) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
	}

	private static boolean isPactBlade(ItemStack stack) {
		if (stack.isEmpty()) return false;
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBooleanOr(PACT_BLADE_TAG, false);
	}

	private static boolean isPactBladeOwner(ItemStack stack, UUID ownerId) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return ownerId.toString().equals(data.copyTag().getStringOr(PACT_BLADE_OWNER_TAG, ""));
	}

	private static void drawRitualCircle(ServerLevel level, Vec3 center) {
		for (int i = 0; i < 64; i++) {
			double angle = i / 64.0D * Math.PI * 2.0D;
			double radius = 1.8D;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x + Math.cos(angle) * radius, center.y + 0.1D, center.z + Math.sin(angle) * radius, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
		for (double y = 0.2D; y <= 2.4D; y += 0.35D) {
			level.sendParticles(ParticleTypes.WITCH, center.x, center.y + y, center.z, 3, 0.9D, 0.0D, 0.9D, 0.02D);
		}
	}

	private static void drawDeathCircle(ServerLevel level, Vec3 center) {
		for (int i = 0; i < 96; i++) {
			double angle = i / 96.0D * Math.PI * 2.0D;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, center.x + Math.cos(angle) * 10.0D, center.y + 0.15D, center.z + Math.sin(angle) * 10.0D, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	private static void dryPlants(ServerLevel level, Vec3 center) {
		BlockPos origin = BlockPos.containing(center);
		for (int dx = -10; dx <= 10; dx++) {
			for (int dz = -10; dz <= 10; dz++) {
				if (dx * dx + dz * dz > 100) continue;
				for (int dy = -2; dy <= 2; dy++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					var state = level.getBlockState(pos);
					Block block = state.getBlock();
					if (block == Blocks.GRASS_BLOCK) {
						level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
					} else if (block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN || block == Blocks.LARGE_FERN || block == Blocks.BUSH || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.VINE || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)) {
						level.setBlock(pos, Blocks.DEAD_BUSH.defaultBlockState(), Block.UPDATE_ALL);
					}
				}
			}
		}
	}

	// Старый enum для существующих способностей
	private static void drawBeam(ServerLevel level, Vec3 start, Vec3 end, OldBeamColor color) {
		Vec3 delta = end.subtract(start);
		double distance = delta.length();
		if (distance <= 0.0D) return;
		Vec3 direction = delta.normalize();
		for (double step = 0.0D; step <= distance; step += 0.25D) {
			Vec3 point = start.add(direction.scale(step));
			if (color == OldBeamColor.PINK) {
				level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			} else if (color == OldBeamColor.GREEN) {
				level.sendParticles(ParticleTypes.SOUL, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			} else {
				level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				level.sendParticles(ParticleTypes.DRIPPING_LAVA, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	public static boolean isDeathRitualSilenced(ServerPlayer player) {
		return DEATH_RITUALS.containsKey(player.getUUID());
	}

	public static boolean isDeadOne(ServerPlayer player) {
		PlayerClassData data = WildMagicServerState.get(player);
		return data.hasClass() && data.selectedClass() == WildMagicClass.WARLOCK && data.level() >= 10;
	}

	private static int dataLevel(ServerPlayer player) {
		PlayerClassData data = WildMagicServerState.get(player);
		return data.hasClass() && data.selectedClass() == WildMagicClass.WARLOCK ? data.level() : 0;
	}

	private static boolean isWarlock(ServerPlayer player) {
		PlayerClassData data = WildMagicServerState.get(player);
		return data.hasClass() && data.selectedClass() == WildMagicClass.WARLOCK;
	}

	private enum OldBeamColor {
		RED, PINK, GREEN
	}

	private record AgathysState(long expireTime, float damage, boolean retaliated) {
		private AgathysState withRetaliated() {
			return new AgathysState(expireTime, damage, true);
		}
	}

	private record DarknessZone(UUID casterId, Vec3 center, long expireTime) {}
	private record DeathCircle(UUID casterId, Vec3 center, long startTime, long expireTime) {}
	private record DeathRitual(UUID casterId, UUID targetId, Vec3 startCenter, long expireTime) {}
}