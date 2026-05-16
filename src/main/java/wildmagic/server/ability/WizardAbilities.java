package wildmagic.server.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WizardAbilities {
	private WizardAbilities() {
	}

	public static boolean useFireball(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 target = BardAbilities.raycastBlock(player, 30.0D);
		AABB area = new AABB(target.x - 4.0D, target.y - 4.0D, target.z - 4.0D, target.x + 4.0D, target.y + 4.0D, target.z + 4.0D);
		boolean hitSomething = false;

		for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, entity -> entity != player && entity.isAlive())) {
			double distance = entity.position().distanceTo(target);
			if (distance > 4.0D) {
				continue;
			}

			float damage = 4.0F + player.getRandom().nextFloat() * 18.0F;
			entity.hurtServer(level, player.damageSources().onFire(), damage);
			entity.igniteForSeconds(6);
			hitSomething = true;
		}

		igniteArea(level, target);
		level.sendParticles(ParticleTypes.FLAME, target.x, target.y + 0.5D, target.z, 80, 3.0D, 1.2D, 3.0D, 0.08D);
		level.sendParticles(ParticleTypes.LAVA, target.x, target.y + 0.5D, target.z, 20, 2.5D, 0.8D, 2.5D, 0.0D);
		level.playSound(null, target.x, target.y, target.z,
				net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(),
				net.minecraft.sounds.SoundSource.PLAYERS,
				1.0F, 0.8F);

		if (!hitSomething) {
			player.sendSystemMessage(Component.literal("Фаербол поджёг область"));
		}
		return true;
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
}
