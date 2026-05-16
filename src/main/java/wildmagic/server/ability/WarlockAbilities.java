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

import java.util.HashSet;
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
	private static final Map<UUID, UUID> SUMMONED_UNDEAD = new ConcurrentHashMap<>();
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

		drawBeam(level, player.getEyePosition(), target.position().add(0.0D, target.getBbHeight() * 0.55D, 0.0D), BeamColor.RED);
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
		if (target == null) {
			return false;
		}

		target.hurtServer(player.level(), player.damageSources().playerAttack(player), 8.0F);
		player.heal(8.0F);
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
		if (!(target instanceof ServerPlayer targetPlayer)) {
			return false;
		}

		drawBeam(player.level(), player.getEyePosition(), targetPlayer.getEyePosition(), BeamColor.PINK);
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

	public static void tick(MinecraftServer server) {
		tickPactBlades(server);
		if (server.getTickCount() % 20 != 0) {
			return;
		}

		tickWarlockPassives(server);
		tickArmorOfAgathys(server);
		tickDarknessZones(server);
		tickDeathCircles(server);
		tickSummonedUndead(server);
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
		PACT_BLADE_OWNERS.remove(player.getUUID());
		removePactBladeFromInventory(player);
		DARKNESS_ZONES.entrySet().removeIf(entry -> entry.getValue().casterId().equals(player.getUUID()));
		DEATH_CIRCLES.entrySet().removeIf(entry -> entry.getValue().casterId().equals(player.getUUID()));
		SUMMONED_UNDEAD.entrySet().removeIf(entry -> entry.getValue().equals(player.getUUID()));
	}

	public static boolean isSummonedUndead(UUID entityId) {
		return SUMMONED_UNDEAD.containsKey(entityId);
	}

	public static boolean isSummonedUndeadFriendly(LivingEntity attacker, LivingEntity target) {
		UUID ownerId = SUMMONED_UNDEAD.get(attacker.getUUID());
		if (ownerId == null) {
			return false;
		}

		if (target instanceof ServerPlayer targetPlayer) {
			return targetPlayer.getUUID().equals(ownerId) || isWarlock(targetPlayer);
		}
		return SUMMONED_UNDEAD.containsKey(target.getUUID());
	}

	public static void applyUndeadTouch(ServerPlayer attacker, LivingEntity target) {
		PlayerClassData data = WildMagicServerState.get(attacker);
		if (!data.hasClass() || data.selectedClass() != WildMagicClass.WARLOCK || data.level() < 5 || !attacker.getMainHandItem().isEmpty()) {
			return;
		}

		target.addEffect(new MobEffectInstance(MobEffects.WITHER, 2 * 20, 0), attacker);
	}

	private static void tickWarlockPassives(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!isWarlock(player)) {
				continue;
			}

			player.getFoodData().setFoodLevel(20);
			player.getFoodData().setSaturation(Math.max(player.getFoodData().getSaturationLevel(), 5.0F));
			player.setAirSupply(player.getMaxAirSupply());
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 25 * 20, 0, false, false), player);
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 25 * 20, 0, false, false), player);
		}
	}

	private static void tickPactBlades(MinecraftServer server) {
		Set<UUID> activeOwners = new HashSet<>(PACT_BLADE_OWNERS.keySet());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			removeForeignPactBlades(player);
			if (!activeOwners.contains(player.getUUID())) {
				continue;
			}

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
				if (entity.getUUID().equals(zone.casterId())) {
					continue;
				}
				if (entity instanceof ServerPlayer player && isWarlock(player)) {
					continue;
				}

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
				if (caster != null && entity == caster) {
					continue;
				}
				entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 3 * 20, 0), caster);
				entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 10 * 20, 0), caster);
			}
		}
	}

	private static void tickSummonedUndead(MinecraftServer server) {
		SUMMONED_UNDEAD.entrySet().removeIf(entry -> {
			ServerPlayer owner = server.getPlayerList().getPlayer(entry.getValue());
			if (owner == null) {
				return true;
			}

			LivingEntity summon = findLivingEntity(server, entry.getKey());
			if (!(summon instanceof Mob mob) || !mob.isAlive()) {
				return true;
			}

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
		if (entity == owner || SUMMONED_UNDEAD.containsKey(entity.getUUID())) {
			return true;
		}
		return entity instanceof ServerPlayer player && isWarlock(player);
	}

	private static void spawnZombie(ServerLevel level, ServerPlayer owner, Vec3 position) {
		Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (zombie == null) {
			return;
		}

		zombie.setPos(position.x, position.y, position.z);
		zombie.setPersistenceRequired();
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		zombie.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		zombie.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		zombie.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
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
			if (isPactBlade(stack) && isPactBladeOwner(stack, player.getUUID())) {
				return true;
			}
		}
		return false;
	}

	private static void removePactBladeFromInventory(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (isPactBlade(stack)) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
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
		if (stack.isEmpty()) {
			return false;
		}

		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBooleanOr(PACT_BLADE_TAG, false);
	}

	private static boolean isPactBladeOwner(ItemStack stack, UUID ownerId) {
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return ownerId.toString().equals(data.copyTag().getStringOr(PACT_BLADE_OWNER_TAG, ""));
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
				if (dx * dx + dz * dz > 100) {
					continue;
				}

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

	private static void drawBeam(ServerLevel level, Vec3 start, Vec3 end, BeamColor color) {
		Vec3 delta = end.subtract(start);
		double distance = delta.length();
		if (distance <= 0.0D) {
			return;
		}

		Vec3 direction = delta.normalize();
		for (double step = 0.0D; step <= distance; step += 0.25D) {
			Vec3 point = start.add(direction.scale(step));
			if (color == BeamColor.PINK) {
				level.sendParticles(ParticleTypes.WITCH, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			} else {
				level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
				level.sendParticles(ParticleTypes.DRIPPING_LAVA, point.x, point.y, point.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	private static boolean isWarlock(ServerPlayer player) {
		PlayerClassData data = WildMagicServerState.get(player);
		return data.hasClass() && data.selectedClass() == WildMagicClass.WARLOCK;
	}

	private enum BeamColor {
		RED,
		PINK
	}

	private record AgathysState(long expireTime, float damage, boolean retaliated) {
		private AgathysState withRetaliated() {
			return new AgathysState(expireTime, damage, true);
		}
	}

	private record DarknessZone(UUID casterId, Vec3 center, long expireTime) {
	}

	private record DeathCircle(UUID casterId, Vec3 center, long startTime, long expireTime) {
	}
}
