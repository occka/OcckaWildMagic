package wildmagic.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wildmagic.server.WildMagicServerState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WizardAbilities {
	private static final List<WizardProjectile> PROJECTILES = new CopyOnWriteArrayList<>();
	private static final List<MagicMissile> MAGIC_MISSILES = new CopyOnWriteArrayList<>();
	private static final List<GravityWell> GRAVITY_WELLS = new CopyOnWriteArrayList<>();
	private static final List<MeteorShower> METEOR_SHOWERS = new CopyOnWriteArrayList<>();
	private static final List<Meteor> METEORS = new CopyOnWriteArrayList<>();

	private WizardAbilities() {
	}

	public static boolean useFireBolt(ServerPlayer player) {
		int wizardLevel = WildMagicServerState.get(player).level();
		float minDamage = wizardLevel >= 10 ? 5.0F : wizardLevel >= 6 ? 2.0F : 1.0F;
		float maxDamage = wizardLevel >= 10 ? 20.0F : wizardLevel >= 6 ? 12.0F : 10.0F;
		spawnProjectile(player, WizardProjectile.Kind.FIRE_BOLT, 1.5D, 32, minDamage, maxDamage);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.6F, 1.6F);
		return true;
	}

	public static boolean useMagicMissile(ServerPlayer player) {
		List<LivingEntity> targets = findConeTargets(player, 25.0D, 0.72D);
		if (targets.isEmpty()) {
			player.sendSystemMessage(Component.literal("Цель не найдена"));
			return false;
		}

		int wizardLevel = WildMagicServerState.get(player).level();
		int count = wizardLevel >= 12 ? 6 : wizardLevel >= 10 ? 5 : wizardLevel >= 5 ? 4 : 3;
		Vec3 look = player.getLookAngle().normalize();
		Vec3 right = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
		if (right.lengthSqr() < 0.001D) {
			right = new Vec3(1.0D, 0.0D, 0.0D);
		}
		right = right.normalize();
		Vec3 up = right.cross(look).normalize();

		for (int i = 0; i < count; i++) {
			LivingEntity target = targets.get(i % targets.size());
			double offset = (i - ((count - 1) / 2.0D)) * 0.52D;
			Vec3 start = player.getEyePosition().add(look.scale(0.9D)).add(right.scale(offset)).add(up.scale(0.12D * (i % 2)));
			double arcHeight = 1.6D + (i % 3) * 0.5D;
			Vec3 control = start
					.add(look.scale(3.6D + (i % 3) * 0.7D))
					.add(up.scale(arcHeight))
					.add(right.scale(offset * 2.6D));
			MAGIC_MISSILES.add(new MagicMissile(player.getUUID(), target.getUUID(), start, control, start, 0, 22 + (i % 3) * 2));
		}

		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.9F, 1.6F);
		return true;
	}

	public static boolean useFireball(ServerPlayer player) {
		spawnProjectile(player, WizardProjectile.Kind.FIREBALL, 0.9D, 60, 4.0F, 22.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.0F, 0.8F);
		return true;
	}

	public static boolean useGravityWell(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = raycastBlock(player, 35.0D);
		long now = level.getGameTime();
		GRAVITY_WELLS.add(new GravityWell(player.getUUID(), center, now + 5 * 20L, now));
		level.playSound(null, center.x, center.y, center.z,
				net.minecraft.sounds.SoundEvents.END_PORTAL_SPAWN,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 0.55F);
		return true;
	}

	public static boolean useChainLightning(ServerPlayer player) {
		ServerLevel level = player.level();
		LivingEntity first = findConeTarget(player, 30.0D, 0.88D, Set.of());
		if (first == null) {
			player.sendSystemMessage(Component.literal("Цель не найдена"));
			return false;
		}

		List<LivingEntity> chain = new ArrayList<>();
		Set<UUID> used = new HashSet<>();
		chain.add(first);
		used.add(first.getUUID());
		LivingEntity current = first;
		for (int i = 0; i < 4; i++) {
			LivingEntity next = findNearestEnemy(level, player, current.position(), 14.0D, used);
			if (next == null) {
				break;
			}
			chain.add(next);
			used.add(next.getUUID());
			current = next;
		}

		Vec3 from = player.getEyePosition();
		for (int i = 0; i < chain.size(); i++) {
			LivingEntity target = chain.get(i);
			float damage = i == 0 ? 14.0F : 8.0F;
			target.hurtServer(level, player.damageSources().magic(), damage);
			spawnLightning(level, target.position());
			drawLightningArc(level, from, target.getEyePosition());
			from = target.getEyePosition();
		}
		level.playSound(null, first.getX(), first.getY(), first.getZ(),
				net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER,
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.2F, 1.2F);
		return true;
	}

	public static boolean useMeteorShower(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = raycastBlock(player, 60.0D);
		long now = level.getGameTime();
		int count = 3 + player.getRandom().nextInt(3);
		METEOR_SHOWERS.add(new MeteorShower(player.getUUID(), center, now + 15 * 20L, count, now + 20L));
		drawCircle(level, center, 25.0D, ParticleTypes.FLAME);
		level.playSound(null, center.x, center.y, center.z,
				net.minecraft.sounds.SoundEvents.WITHER_SPAWN,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.8F, 0.7F);
		return true;
	}

	public static void tickProjectiles(MinecraftServer server) {
		tickFireProjectiles(server);
		tickMagicMissiles(server);
		tickGravityWells(server);
		tickMeteorShowers(server);
		tickMeteors(server);
	}

	private static void tickFireProjectiles(MinecraftServer server) {
		Iterator<WizardProjectile> iterator = PROJECTILES.iterator();
		while (iterator.hasNext()) {
			WizardProjectile projectile = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(projectile.ownerId());
			if (owner == null || !owner.isAlive() || projectile.remainingTicks() <= 0) {
				PROJECTILES.remove(projectile);
				continue;
			}

			ServerLevel level = owner.level();
			Vec3 nextPosition = projectile.position().add(projectile.direction().scale(projectile.speed()));
			Vec3 hitPosition = blockHitPosition(level, owner, projectile.position(), nextPosition);
			LivingEntity target = hitEntity(level, owner, projectile.position(), hitPosition == null ? nextPosition : hitPosition, projectile.kind());

			spawnProjectileParticles(level, projectile.kind(), projectile.position());
			if (target != null) {
				impactEntity(level, owner, projectile, target);
				PROJECTILES.remove(projectile);
				continue;
			}

			if (hitPosition != null) {
				impactBlock(level, owner, projectile, hitPosition);
				PROJECTILES.remove(projectile);
				continue;
			}

			PROJECTILES.remove(projectile);
			PROJECTILES.add(projectile.next(nextPosition));
		}
	}

	private static void tickMagicMissiles(MinecraftServer server) {
		Iterator<MagicMissile> iterator = MAGIC_MISSILES.iterator();
		while (iterator.hasNext()) {
			MagicMissile missile = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(missile.ownerId());
			if (owner == null || !owner.isAlive() || missile.age() >= missile.totalTicks()) {
				MAGIC_MISSILES.remove(missile);
				continue;
			}

			ServerLevel level = owner.level();
			LivingEntity target = findEntity(level, missile.targetId());
			if (target == null || !target.isAlive() || isFriendly(owner, target)) {
				MAGIC_MISSILES.remove(missile);
				continue;
			}

			Vec3 aim = target.getEyePosition();
			double progress = Math.min(1.0D, (missile.age() + 1) / (double) missile.totalTicks());
			Vec3 next = bezier(missile.start(), missile.control(), aim, progress);
			Vec3 direction = next.subtract(missile.position()).normalize();
			level.sendParticles(ParticleTypes.ENCHANT, missile.position().x, missile.position().y, missile.position().z, 5, 0.06D, 0.06D, 0.06D, 0.02D);
			level.sendParticles(ParticleTypes.END_ROD, missile.position().x, missile.position().y, missile.position().z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			level.sendParticles(ParticleTypes.WITCH, missile.position().x - direction.x * 0.25D, missile.position().y - direction.y * 0.25D, missile.position().z - direction.z * 0.25D, 2, 0.03D, 0.03D, 0.03D, 0.01D);

			if (progress >= 1.0D || next.distanceTo(aim) <= 1.2D || target.getBoundingBox().inflate(0.5D).contains(next)) {
				target.hurtServer(level, owner.damageSources().magic(), 2.0F);
				target.igniteForSeconds(3);
				level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 14, 0.25D, 0.35D, 0.25D, 0.04D);
				level.playSound(null, target.getX(), target.getY(), target.getZ(),
						net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME,
						net.minecraft.sounds.SoundSource.PLAYERS,
						0.7F, 1.8F);
				MAGIC_MISSILES.remove(missile);
				continue;
			}

			MAGIC_MISSILES.remove(missile);
			MAGIC_MISSILES.add(new MagicMissile(missile.ownerId(), missile.targetId(), missile.start(), missile.control(), next, missile.age() + 1, missile.totalTicks()));
		}
	}

	private static void tickGravityWells(MinecraftServer server) {
		Iterator<GravityWell> iterator = GRAVITY_WELLS.iterator();
		while (iterator.hasNext()) {
			GravityWell well = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(well.ownerId());
			if (owner == null || !owner.isAlive()) {
				GRAVITY_WELLS.remove(well);
				continue;
			}

			ServerLevel level = owner.level();
			long now = level.getGameTime();
			if (now > well.expireTick()) {
				GRAVITY_WELLS.remove(well);
				continue;
			}

			drawBlackHole(level, well.center(), now);
			AABB area = new AABB(well.center().x - 6.0D, well.center().y - 6.0D, well.center().z - 6.0D,
					well.center().x + 6.0D, well.center().y + 6.0D, well.center().z + 6.0D);
			for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area,
					e -> e != owner && e.isAlive() && e.position().distanceTo(well.center()) <= 6.0D && !isFriendly(owner, e))) {
				Vec3 pull = well.center().subtract(entity.position()).normalize().scale(0.08D);
				entity.push(pull.x, Math.max(-0.02D, pull.y), pull.z);
				entity.hurtMarked = true;
				if (now >= well.nextDamageTick()) {
					entity.hurtServer(level, owner.damageSources().magic(), 2.0F);
					entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 0, false, false), owner);
				}
			}

			if (now >= well.nextDamageTick()) {
				GRAVITY_WELLS.remove(well);
				GRAVITY_WELLS.add(new GravityWell(well.ownerId(), well.center(), well.expireTick(), now + 20L));
			}
		}
	}

	private static void tickMeteorShowers(MinecraftServer server) {
		Iterator<MeteorShower> iterator = METEOR_SHOWERS.iterator();
		while (iterator.hasNext()) {
			MeteorShower shower = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(shower.ownerId());
			if (owner == null || !owner.isAlive()) {
				METEOR_SHOWERS.remove(shower);
				continue;
			}

			ServerLevel level = owner.level();
			long now = level.getGameTime();
			if (now > shower.expireTick() || shower.remainingMeteors() <= 0) {
				METEOR_SHOWERS.remove(shower);
				continue;
			}

			if (now >= shower.nextMeteorTick()) {
				spawnMeteor(level, owner, shower.center());
				int remaining = shower.remainingMeteors() - 1;
				long next = now + Math.max(15L, (shower.expireTick() - now) / Math.max(1, remaining + 1));
				METEOR_SHOWERS.remove(shower);
				METEOR_SHOWERS.add(new MeteorShower(shower.ownerId(), shower.center(), shower.expireTick(), remaining, next));
			} else if (now % 10L == 0L) {
				drawCircle(level, shower.center(), 25.0D, ParticleTypes.SMOKE);
			}
		}
	}

	private static void tickMeteors(MinecraftServer server) {
		Iterator<Meteor> iterator = METEORS.iterator();
		while (iterator.hasNext()) {
			Meteor meteor = iterator.next();
			ServerPlayer owner = server.getPlayerList().getPlayer(meteor.ownerId());
			if (owner == null || !owner.isAlive()) {
				METEORS.remove(meteor);
				continue;
			}

			ServerLevel level = owner.level();
			List<FallingBlockEntity> entities = findFallingBlocks(level, meteor.entityIds());
			if (entities.isEmpty() || meteor.remainingTicks() <= 0) {
				explodeMeteor(level, owner, meteor.lastPosition());
				METEORS.remove(meteor);
				continue;
			}

			Vec3 pos = averagePosition(entities);
			boolean hit = false;
			for (FallingBlockEntity entity : entities) {
				entity.setDeltaMovement(0.0D, -1.35D, 0.0D);
				hit = hit || entity.onGround() || blockHitPosition(level, owner, entity.position(), entity.position().add(0.0D, -1.6D, 0.0D)) != null;
			}
			spawnMeteorTrail(level, pos);
			if (hit) {
				for (FallingBlockEntity entity : entities) {
					entity.discard();
				}
				explodeMeteor(level, owner, pos);
				METEORS.remove(meteor);
				continue;
			}

			METEORS.remove(meteor);
			METEORS.add(new Meteor(meteor.ownerId(), meteor.entityIds(), pos, meteor.remainingTicks() - 1));
		}
	}

	private static void spawnProjectile(ServerPlayer player, WizardProjectile.Kind kind, double speed, int remainingTicks, float minDamage, float maxDamage) {
		Vec3 direction = player.getLookAngle().normalize();
		Vec3 start = player.getEyePosition().add(direction.scale(0.8D));
		PROJECTILES.add(new WizardProjectile(player.getUUID(), kind, start, direction, speed, remainingTicks, minDamage, maxDamage));
	}

	private static void spawnMeteor(ServerLevel level, ServerPlayer owner, Vec3 center) {
		double angle = owner.getRandom().nextDouble() * Math.PI * 2.0D;
		double distance = Math.sqrt(owner.getRandom().nextDouble()) * 25.0D;
		double x = center.x + Math.cos(angle) * distance;
		double z = center.z + Math.sin(angle) * distance;
		double y = Math.min(level.getMaxY() - 4.0D, Math.max(center.y + 28.0D, level.getMaxY() - 12.0D));
		List<UUID> ids = new ArrayList<>();
		for (int dx = 0; dx <= 1; dx++) {
			for (int dz = 0; dz <= 1; dz++) {
				for (int dy = 0; dy <= 1; dy++) {
					BlockPos pos = BlockPos.containing(x + dx - 0.5D, y + dy, z + dz - 0.5D);
					FallingBlockEntity entity = FallingBlockEntity.fall(level, pos, Blocks.MAGMA_BLOCK.defaultBlockState());
					entity.disableDrop();
					entity.setHurtsEntities(0.0F, 0);
					entity.setDeltaMovement(0.0D, -1.35D, 0.0D);
					ids.add(entity.getUUID());
				}
			}
		}
		METEORS.add(new Meteor(owner.getUUID(), List.copyOf(ids), new Vec3(x, y, z), 80));
		level.playSound(null, x, y, z,
				net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.2F, 0.45F);
	}

	private static Vec3 blockHitPosition(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 end) {
		ClipContext clip = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner);
		var hit = level.clip(clip);
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
	}

	private static LivingEntity hitEntity(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 end, WizardProjectile.Kind kind) {
		double inflate = kind == WizardProjectile.Kind.FIREBALL ? 0.75D : 0.35D;
		AABB path = new AABB(start, end).inflate(inflate);
		LivingEntity closest = null;
		double closestDistance = Double.MAX_VALUE;
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, path, entity -> entity != owner && entity.isAlive() && !isFriendly(owner, entity))) {
			var hit = entity.getBoundingBox().inflate(inflate).clip(start, end);
			if (hit.isPresent()) {
				double distance = start.distanceTo(hit.get());
				if (distance < closestDistance) {
					closestDistance = distance;
					closest = entity;
				}
			}
		}
		return closest;
	}

	private static LivingEntity findConeTarget(ServerPlayer player, double range, double minDot, Set<UUID> excluded) {
		return findConeTargets(player, range, minDot).stream()
				.filter(entity -> !excluded.contains(entity.getUUID()))
				.findFirst()
				.orElse(null);
	}

	private static List<LivingEntity> findConeTargets(ServerPlayer player, double range, double minDot) {
		ServerLevel level = player.level();
		Vec3 start = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		AABB area = player.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.35D);
		List<LivingEntity> targets = new ArrayList<>();
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != player && e.isAlive() && !isFriendly(player, e))) {
			Vec3 toTarget = entity.getEyePosition().subtract(start);
			double distance = toTarget.length();
			if (distance <= 0.1D || distance > range) {
				continue;
			}
			double dot = look.dot(toTarget.normalize());
			if (dot < minDot || !hasLineOfSight(level, player, start, entity.getEyePosition())) {
				continue;
			}
			targets.add(entity);
		}
		targets.sort(Comparator
				.comparingDouble((LivingEntity entity) -> -look.dot(entity.getEyePosition().subtract(start).normalize()))
				.thenComparingDouble(entity -> entity.getEyePosition().distanceTo(start)));
		return targets;
	}

	private static LivingEntity findNearestEnemy(ServerLevel level, ServerPlayer owner, Vec3 center, double range, Set<UUID> excluded) {
		AABB area = new AABB(center.x - range, center.y - range, center.z - range, center.x + range, center.y + range, center.z + range);
		return level.getEntitiesOfClass(LivingEntity.class, area,
						e -> e != owner && e.isAlive() && !excluded.contains(e.getUUID()) && !isFriendly(owner, e) && e.position().distanceTo(center) <= range)
				.stream()
				.min(Comparator.comparingDouble(e -> e.position().distanceTo(center)))
				.orElse(null);
	}

	private static LivingEntity findEntity(ServerLevel level, UUID id) {
		return level.getEntityInAnyDimension(id) instanceof LivingEntity entity ? entity : null;
	}

	private static List<FallingBlockEntity> findFallingBlocks(ServerLevel level, List<UUID> ids) {
		List<FallingBlockEntity> result = new ArrayList<>();
		for (UUID id : ids) {
			if (level.getEntityInAnyDimension(id) instanceof FallingBlockEntity entity && entity.isAlive()) {
				result.add(entity);
			}
		}
		return result;
	}

	private static boolean hasLineOfSight(ServerLevel level, ServerPlayer owner, Vec3 start, Vec3 end) {
		ClipContext clip = new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner);
		return level.clip(clip).getType() == HitResult.Type.MISS;
	}

	private static boolean isFriendly(ServerPlayer owner, LivingEntity entity) {
		return entity == owner || WildMagicServerState.areTeammates(owner, entity);
	}

	private static Vec3 raycastBlock(ServerPlayer player, double range) {
		ClipContext clip = new ClipContext(
				player.getEyePosition(),
				player.getEyePosition().add(player.getLookAngle().normalize().scale(range)),
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				player);
		var hit = player.level().clip(clip);
		if (hit.getType() == HitResult.Type.BLOCK) {
			return hit.getLocation();
		}
		return player.getEyePosition().add(player.getLookAngle().normalize().scale(range));
	}

	private static void impactEntity(ServerLevel level, ServerPlayer owner, WizardProjectile projectile, LivingEntity target) {
		if (projectile.kind() == WizardProjectile.Kind.FIREBALL) {
			explodeFireball(level, owner, target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D), projectile.minDamage(), projectile.maxDamage());
			return;
		}

		float damage = projectile.minDamage() + owner.getRandom().nextFloat() * (projectile.maxDamage() - projectile.minDamage());
		target.hurtServer(level, owner.damageSources().onFire(), damage);
		target.igniteForSeconds(3);
		level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ(), 12, 0.2D, 0.3D, 0.2D, 0.04D);
		level.playSound(null, target.getX(), target.getY(), target.getZ(),
				net.minecraft.sounds.SoundEvents.BLAZE_HURT,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.5F, 1.8F);
	}

	private static void impactBlock(ServerLevel level, ServerPlayer owner, WizardProjectile projectile, Vec3 hitPosition) {
		if (projectile.kind() == WizardProjectile.Kind.FIREBALL) {
			explodeFireball(level, owner, hitPosition, projectile.minDamage(), projectile.maxDamage());
			return;
		}

		level.sendParticles(ParticleTypes.SMALL_FLAME, hitPosition.x, hitPosition.y, hitPosition.z, 8, 0.15D, 0.15D, 0.15D, 0.02D);
		level.playSound(null, hitPosition.x, hitPosition.y, hitPosition.z,
				net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
				net.minecraft.sounds.SoundSource.PLAYERS,
				0.35F, 1.4F);
	}

	private static void explodeFireball(ServerLevel level, ServerPlayer owner, Vec3 center, float minDamage, float maxDamage) {
		AABB area = new AABB(center.x - 4.0D, center.y - 4.0D, center.z - 4.0D, center.x + 4.0D, center.y + 4.0D, center.z + 4.0D);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != owner && entity.isAlive() && !isFriendly(owner, entity))) {
			double distance = entity.position().distanceTo(center);
			if (distance > 4.0D) {
				continue;
			}

			float damage = minDamage + owner.getRandom().nextFloat() * (maxDamage - minDamage);
			entity.hurtServer(level, owner.damageSources().onFire(), damage);
			entity.igniteForSeconds(6);
		}

		igniteArea(level, center, 4);
		level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.5D, center.z, 80, 3.0D, 1.2D, 3.0D, 0.08D);
		level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.5D, center.z, 20, 2.5D, 0.8D, 2.5D, 0.0D);
		level.playSound(null, center.x, center.y, center.z,
				net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.0F, 0.8F);
	}

	private static void explodeMeteor(ServerLevel level, ServerPlayer owner, Vec3 center) {
		AABB area = new AABB(center.x - 7.0D, center.y - 7.0D, center.z - 7.0D, center.x + 7.0D, center.y + 7.0D, center.z + 7.0D);
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, e -> e != owner && e.isAlive() && !isFriendly(owner, e))) {
			if (entity.position().distanceTo(center) <= 7.0D) {
				float damage = 10.0F;
				entity.hurtServer(level, owner.damageSources().onFire(), damage);
				entity.igniteForSeconds(8);
			}
		}
		level.explode(owner, center.x, center.y, center.z, 7.0F, true, Level.ExplosionInteraction.TNT);
		igniteArea(level, center, 7);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y, center.z, 5, 1.2D, 0.8D, 1.2D, 0.0D);
		level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 1.0D, center.z, 160, 4.5D, 2.2D, 4.5D, 0.14D);
		level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 1.0D, center.z, 45, 3.2D, 1.7D, 3.2D, 0.0D);
		level.playSound(null, center.x, center.y, center.z,
				net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.5F, 0.6F);
	}

	private static Vec3 bezier(Vec3 start, Vec3 control, Vec3 end, double t) {
		double inv = 1.0D - t;
		return start.scale(inv * inv).add(control.scale(2.0D * inv * t)).add(end.scale(t * t));
	}

	private static Vec3 averagePosition(List<FallingBlockEntity> entities) {
		Vec3 sum = Vec3.ZERO;
		for (FallingBlockEntity entity : entities) {
			sum = sum.add(entity.position());
		}
		return sum.scale(1.0D / entities.size());
	}

	private static void spawnMeteorTrail(ServerLevel level, Vec3 pos) {
		level.sendParticles(ParticleTypes.FLAME, pos.x, pos.y, pos.z, 90, 1.35D, 1.1D, 1.35D, 0.14D);
		level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y, pos.z, 22, 1.1D, 1.0D, 1.1D, 0.0D);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 0.7D, pos.z, 36, 1.6D, 1.3D, 1.6D, 0.06D);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.x, pos.y + 1.0D, pos.z, 8, 1.5D, 1.0D, 1.5D, 0.02D);
	}

	private static void spawnProjectileParticles(ServerLevel level, WizardProjectile.Kind kind, Vec3 position) {
		if (kind == WizardProjectile.Kind.FIREBALL) {
			level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 8, 0.18D, 0.18D, 0.18D, 0.02D);
			level.sendParticles(ParticleTypes.SMOKE, position.x, position.y, position.z, 2, 0.08D, 0.08D, 0.08D, 0.01D);
		} else {
			level.sendParticles(ParticleTypes.SMALL_FLAME, position.x, position.y, position.z, 3, 0.04D, 0.04D, 0.04D, 0.0D);
		}
	}

	private static void spawnLightning(ServerLevel level, Vec3 pos) {
		var bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.snapTo(pos.x, pos.y, pos.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	private static void drawLightningArc(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 delta = to.subtract(from);
		int steps = Math.max(4, (int) (delta.length() * 2.0D));
		for (int i = 0; i <= steps; i++) {
			Vec3 point = from.add(delta.scale(i / (double) steps));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 2, 0.08D, 0.08D, 0.08D, 0.02D);
		}
	}

	private static void drawBlackHole(ServerLevel level, Vec3 center, long tick) {
		double time = tick * 0.18D;
		for (int ring = 0; ring < 3; ring++) {
			double radius = 1.2D + ring * 0.85D;
			for (int i = 0; i < 18; i++) {
				double angle = time + ring * 1.7D + i * Math.PI * 2.0D / 18.0D;
				double y = center.y + 1.0D + Math.sin(angle * 1.4D) * 0.45D;
				level.sendParticles(ParticleTypes.REVERSE_PORTAL,
						center.x + Math.cos(angle) * radius,
						y,
						center.z + Math.sin(angle) * radius,
						1, 0.0D, 0.0D, 0.0D, 0.02D);
			}
		}
		level.sendParticles(ParticleTypes.SMOKE, center.x, center.y + 1.0D, center.z, 12, 0.35D, 0.35D, 0.35D, 0.01D);
		level.sendParticles(ParticleTypes.PORTAL, center.x, center.y + 1.0D, center.z, 8, 1.0D, 0.6D, 1.0D, -0.02D);
	}

	private static void drawCircle(ServerLevel level, Vec3 center, double radius, net.minecraft.core.particles.SimpleParticleType particle) {
		for (int i = 0; i < 72; i++) {
			double angle = i * Math.PI * 2.0D / 72.0D;
			level.sendParticles(particle,
					center.x + Math.cos(angle) * radius,
					center.y + 0.2D,
					center.z + Math.sin(angle) * radius,
					1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}

	private static void igniteArea(ServerLevel level, Vec3 center, int radius) {
		BlockPos origin = BlockPos.containing(center);
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if ((dx * dx) + (dz * dz) > radius * radius) {
					continue;
				}

				for (int dy = -1; dy <= 1; dy++) {
					BlockPos pos = origin.offset(dx, dy, dz);
					BlockPos firePos = pos.above();
					if (level.getBlockState(firePos).isAir()
							&& level.getBlockState(pos).isSolid()
							&& !level.getBlockState(pos).is(BlockTags.FIRE)) {
						level.setBlock(firePos, Blocks.FIRE.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_ALL);
						break;
					}
				}
			}
		}
	}

	private record WizardProjectile(UUID ownerId, Kind kind, Vec3 position, Vec3 direction, double speed, int remainingTicks, float minDamage, float maxDamage) {
		private WizardProjectile next(Vec3 nextPosition) {
			return new WizardProjectile(ownerId, kind, nextPosition, direction, speed, remainingTicks - 1, minDamage, maxDamage);
		}

		private enum Kind {
			FIRE_BOLT,
			FIREBALL
		}
	}

	private record MagicMissile(UUID ownerId, UUID targetId, Vec3 start, Vec3 control, Vec3 position, int age, int totalTicks) {
	}

	private record GravityWell(UUID ownerId, Vec3 center, long expireTick, long nextDamageTick) {
	}

	private record MeteorShower(UUID ownerId, Vec3 center, long expireTick, int remainingMeteors, long nextMeteorTick) {
	}

	private record Meteor(UUID ownerId, List<UUID> entityIds, Vec3 lastPosition, int remainingTicks) {
	}
}
