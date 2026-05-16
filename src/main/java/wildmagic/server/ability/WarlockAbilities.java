package wildmagic.server.ability;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import wildmagic.server.WildMagicServerState;

public final class WarlockAbilities {
	private WarlockAbilities() {
	}

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

		drawRedBeam(level, player.getEyePosition(), target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D));
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);

		if (warlockLevel >= 6) {
			Vec3 knockback = target.position().subtract(player.position()).normalize().scale(5.0D);
			target.push(knockback.x, 0.35D, knockback.z);
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

	private static void drawRedBeam(ServerLevel level, Vec3 start, Vec3 end) {
		Vec3 delta = end.subtract(start);
		double distance = delta.length();
		if (distance <= 0.0D) {
			return;
		}

		Vec3 direction = delta.normalize();
		for (double step = 0.0D; step <= distance; step += 0.25D) {
			Vec3 point = start.add(direction.scale(step));
			level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			level.sendParticles(ParticleTypes.DRIPPING_LAVA, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
		}
	}
}
