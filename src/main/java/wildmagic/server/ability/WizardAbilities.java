package wildmagic.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import wildmagic.server.WildMagicServerState;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public final class WizardAbilities {
	private static final List<WizardProjectile> PROJECTILES = new CopyOnWriteArrayList<>();

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

	public static boolean useFireball(ServerPlayer player) {
		spawnProjectile(player, WizardProjectile.Kind.FIREBALL, 0.9D, 60, 4.0F, 22.0F);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				net.minecraft.sounds.SoundEvents.FIRECHARGE_USE,
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.0F, 0.8F);
		return true;
	}

	public static void tickProjectiles(MinecraftServer server) {
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

	private static void spawnProjectile(ServerPlayer player, WizardProjectile.Kind kind, double speed, int remainingTicks, float minDamage, float maxDamage) {
		Vec3 direction = player.getLookAngle().normalize();
		Vec3 start = player.getEyePosition().add(direction.scale(0.8D));
		PROJECTILES.add(new WizardProjectile(player.getUUID(), kind, start, direction, speed, remainingTicks, minDamage, maxDamage));
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
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, path, entity -> entity != owner && entity.isAlive())) {
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
		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != owner && entity.isAlive())) {
			double distance = entity.position().distanceTo(center);
			if (distance > 4.0D) {
				continue;
			}

			float damage = minDamage + owner.getRandom().nextFloat() * (maxDamage - minDamage);
			entity.hurtServer(level, owner.damageSources().onFire(), damage);
			entity.igniteForSeconds(6);
		}

		igniteArea(level, center);
		level.sendParticles(ParticleTypes.FLAME, center.x, center.y + 0.5D, center.z, 80, 3.0D, 1.2D, 3.0D, 0.08D);
		level.sendParticles(ParticleTypes.LAVA, center.x, center.y + 0.5D, center.z, 20, 2.5D, 0.8D, 2.5D, 0.0D);
		level.playSound(null, center.x, center.y, center.z,
				net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.0F, 0.8F);
	}

	private static void spawnProjectileParticles(ServerLevel level, WizardProjectile.Kind kind, Vec3 position) {
		if (kind == WizardProjectile.Kind.FIREBALL) {
			level.sendParticles(ParticleTypes.FLAME, position.x, position.y, position.z, 8, 0.18D, 0.18D, 0.18D, 0.02D);
			level.sendParticles(ParticleTypes.SMOKE, position.x, position.y, position.z, 2, 0.08D, 0.08D, 0.08D, 0.01D);
		} else {
			level.sendParticles(ParticleTypes.SMALL_FLAME, position.x, position.y, position.z, 3, 0.04D, 0.04D, 0.04D, 0.0D);
		}
	}

	private static void igniteArea(ServerLevel level, Vec3 center) {
		BlockPos origin = BlockPos.containing(center);
		int radius = 4;
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
}
