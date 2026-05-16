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
import wildmagic.server.WildMagicServerState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarlockAbilities {
	private static final Map<UUID, AgathysState> ARMOR_OF_AGATHYS = new ConcurrentHashMap<>();
	private static final ThreadLocal<Boolean> REFLECTING_AGATHYS = ThreadLocal.withInitial(() -> false);

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

		ARMOR_OF_AGATHYS.put(player.getUUID(), new AgathysState(expireTime, shieldAndDamage, false));
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
			if (distance > 5.0D || distance <= 0.0D) {
				continue;
			}

			double angleDot = look.dot(toTarget.normalize());
			if (angleDot < 0.72D) {
				continue;
			}

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

	public static void onPlayerDamaged(ServerPlayer player, LivingEntity attacker, ServerLevel level) {
		if (REFLECTING_AGATHYS.get()) {
			return;
		}

		AgathysState state = ARMOR_OF_AGATHYS.get(player.getUUID());
		if (state == null || state.retaliated() || level.getGameTime() > state.expireTime()) {
			return;
		}

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

	public static void tickArmorOfAgathys(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		ARMOR_OF_AGATHYS.entrySet().removeIf(entry -> {
			if (now <= entry.getValue().expireTime()) {
				return false;
			}

			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player != null && player.getAbsorptionAmount() <= entry.getValue().damage() + 0.5F) {
				player.setAbsorptionAmount(0.0F);
			}
			return true;
		});
	}

	public static void clearPlayer(ServerPlayer player) {
		ARMOR_OF_AGATHYS.remove(player.getUUID());
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

	private record AgathysState(long expireTime, float damage, boolean retaliated) {
		private AgathysState withRetaliated() {
			return new AgathysState(expireTime, damage, true);
		}
	}
}
