package wildmagic.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelResource;
import wildmagic.server.ability.BardAbilities;
import wildmagic.server.state.WildMagicZones;
import wildmagic.OcckaWildMagic;
import wildmagic.classdata.AbilityDefinition;
import wildmagic.classdata.ClassProgression;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.network.WildMagicNetworking;
import net.minecraft.world.entity.player.Player;
import wildmagic.classdata.ArmorTier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public final class WildMagicServerState {
	private static final Identifier BARD_HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_fragile_performer");
	private static final Identifier WIZARD_HEALTH_MODIFIER_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "wizard_fragile_scholar");
	private static final double BARD_HEALTH_PENALTY = -4.0D;
	private static final double WIZARD_HEALTH_PENALTY = -12.0D;
	private static final Map<UUID, PlayerClassData> PLAYER_DATA = new ConcurrentHashMap<>();
	private static final Map<UUID, long[]> ABILITY_COOLDOWNS = new ConcurrentHashMap<>();
	private static final Identifier BARD_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_light_step");
	private static final Identifier BARD_SWORD_DAMAGE_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_damage");
	private static final Identifier BARD_SWORD_SPEED_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_speed");
	private static final Identifier BARD_SWORD_REACH_ID = Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "bard_sword_reach");
	private static final Map<UUID, long[]> MANA_EFFECTS = new ConcurrentHashMap<>();
	private static MinecraftServer currentServer;
	private static Path saveFile;

	private WildMagicServerState() {
	}

	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(WildMagicServerState::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(WildMagicServerState::save);
		ServerTickEvents.END_SERVER_TICK.register(WildMagicServerState::tickPlayers);
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (!world.isClientSide() && world.getBlockState(hitResult.getBlockPos()).is(net.minecraft.world.level.block.Blocks.ENCHANTING_TABLE)) {
				PlayerClassData data = player instanceof ServerPlayer serverPlayer ? get(serverPlayer) : PlayerClassData.EMPTY;
				if (!data.hasClass() || data.selectedClass() != WildMagicClass.WIZARD) {
					player.sendSystemMessage(Component.literal("Только волшебник может использовать стол зачарований"));
					return InteractionResult.FAIL;
				}
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			applyClassPassives(handler.player);
			sync(handler.player);
		});
	}

	public static PlayerClassData get(ServerPlayer player) {
		return PLAYER_DATA.getOrDefault(player.getUUID(), PlayerClassData.EMPTY);
	}

	public static void selectClass(ServerPlayer player, WildMagicClass clazz) {
		PlayerClassData current = get(player);
		if (current.hasClass()) {
			sync(player);
			return;
		}
		setClass(player, clazz, ClassProgression.MIN_LEVEL);
	}

	public static void setClass(ServerPlayer player, WildMagicClass clazz, int level) {
		removeClassPassives(player);
		PlayerClassData updated = PlayerClassData.createForClass(clazz, level);
		PLAYER_DATA.put(player.getUUID(), updated);
		ABILITY_COOLDOWNS.remove(player.getUUID());
		WildMagicZones.clearPlayer(player);
		wildmagic.server.ability.SorcererAbilities.clearPlayer(player);
		BardAbilities.clearPlayer(player);
		wildmagic.server.ability.WarlockAbilities.clearPlayer(player);
		applyClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void clearClass(ServerPlayer player) {
		PLAYER_DATA.remove(player.getUUID());
		ABILITY_COOLDOWNS.remove(player.getUUID());
		WildMagicZones.clearPlayer(player);
		wildmagic.server.ability.SorcererAbilities.clearPlayer(player);
		BardAbilities.clearPlayer(player);
		wildmagic.server.ability.WarlockAbilities.clearPlayer(player);
		removeClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void applyManaEffect(ServerPlayer player, int manaBonus, long durationTicks) {
		long expireTime = player.level().getGameTime() + durationTicks;
		MANA_EFFECTS.put(player.getUUID(), new long[]{(long) manaBonus, expireTime});
		PlayerClassData current = get(player);
		if (current.hasClass() && current.selectedClass().usesMana()) {
			PLAYER_DATA.put(player.getUUID(), current.withMaxManaBonus(manaBonus));
		}
		sync(player);
	}

	private static int getManaBonus(UUID playerId) {
		long[] effect = MANA_EFFECTS.get(playerId);
		if (effect == null) return 0;
		return (int) effect[0];
	}

	public static void setActiveAbility(ServerPlayer player, int slot, String abilityId) {
		PlayerClassData current = get(player);
		if (!current.hasClass() || slot < 0 || slot >= PlayerClassData.ACTIVE_SLOT_COUNT) {
			sync(player);
			return;
		}
		if (wouldChangeCooldownSlot(player, current, slot, abilityId)) {
			player.sendSystemMessage(Component.literal("Способность на перезарядке: слот нельзя изменить"));
			sync(player);
			return;
		}
		if (abilityId == null || abilityId.isBlank()) {
			updateActiveAbility(player, current, slot, "");
			return;
		}
		AbilityDefinition ability = ClassProgression.abilityFor(current.selectedClass(), abilityId).orElse(null);
		if (ability == null || ability.passive() || !ability.isUnlocked(current)) {
			sync(player);
			return;
		}
		updateActiveAbility(player, current, slot, ability.id());
	}

	public static void useActiveAbility(ServerPlayer player, int slot) {
		PlayerClassData current = get(player);
		if (!current.hasClass() || slot < 0 || slot >= PlayerClassData.ACTIVE_SLOT_COUNT) {
			return;
		}
		String abilityId = current.activeAbility(slot);
		if (abilityId.isBlank()) {
			return;
		}
		AbilityDefinition ability = ClassProgression.abilityFor(current.selectedClass(), abilityId)
				.filter(definition -> !definition.passive() && definition.isUnlocked(current))
				.orElse(null);
		if (ability == null || !hasEnoughMana(player, current, ability) || isOnCooldown(player, slot)) {
			return;
		}
		if (!canCastInCurrentArmor(player, current, ability)) {
			return;
		}
		if (wildmagic.server.ability.WarlockAbilities.isDeathRitualSilenced(player)) {
			player.sendSystemMessage(Component.literal("Ритуал смерти: магия недоступна"));
			return;
		}
		if (WildMagicZones.isInSilenceZone(player) && ability.manaCost() > 0) {
    if (wildmagic.server.ability.SorcererAbilities.isSilentSpellActive(player)) {
        wildmagic.server.ability.SorcererAbilities.consumeSilentSpell(player);
    } else {
        player.sendSystemMessage(Component.literal("Тишина: заклинания недоступны"));
        return;
    }
}
		if (!ability.id().equals("bard_invisibility")) {
			WildMagicZones.breakInvisibility(player);
		}
		boolean used = switch (ability.id()) {
			case "bard_inspiration" -> BardAbilities.useBardInspiration(player);
			case "bard_healing_word" -> BardAbilities.useBardHealingWord(player);
			case "bard_sound_wave" -> BardAbilities.useBardSoundWave(player);
			case "bard_charm" -> BardAbilities.useBardCharm(player);
			case "bard_heroism" -> BardAbilities.useBardHeroism(player);
			case "bard_invisibility" -> BardAbilities.useBardInvisibility(player);
			case "sorcerer_dragon_breath" -> wildmagic.server.ability.SorcererAbilities.useDragonBreath(player);
case "sorcerer_elemental_dash" -> wildmagic.server.ability.SorcererAbilities.useElementalDash(player);
case "sorcerer_storm_jump" -> wildmagic.server.ability.SorcererAbilities.useStormJump(player);
case "sorcerer_elemental_burst" -> wildmagic.server.ability.SorcererAbilities.useElementalBurst(player);
case "sorcerer_draconic_wings" -> wildmagic.server.ability.SorcererAbilities.useDraconicWings(player);
case "sorcerer_metamagic" -> wildmagic.server.ability.SorcererAbilities.useMetamagic(player);
case "sorcerer_silent_spell" -> wildmagic.server.ability.SorcererAbilities.useSilentSpell(player);
case "sorcerer_wild_surge" -> wildmagic.server.ability.SorcererAbilities.useWildSurge(player);
			case "bard_dispel" -> BardAbilities.useBardDispel(player);
			case "bard_slow_zone" -> BardAbilities.useBardSlowZone(player);
			case "bard_haste" -> BardAbilities.useBardHaste(player);
			case "bard_feather_fall" -> BardAbilities.useBardFeatherFall(player);
			case "bard_force_cage" -> BardAbilities.useBardForceCage(player);
			case "bard_mordenkainen_sword" -> BardAbilities.useBardMordenkainenSword(player);
			case "bard_heat_metal" -> BardAbilities.useBardHeatMetal(player);
			case "bard_shatter" -> BardAbilities.useBardShatter(player);
			case "bard_misty_step" -> BardAbilities.useBardMistyStep(player);
			case "bard_hypnotic_pattern" -> BardAbilities.useBardHypnoticPattern(player);
			case "sorcerer_leap"        -> wildmagic.server.ability.SorcererAbilities.useLeap(player);
    case "sorcerer_ice_dagger"  -> wildmagic.server.ability.SorcererAbilities.useIceDagger(player);
    case "sorcerer_pseudo_life" -> wildmagic.server.ability.SorcererAbilities.usePseudoLife(player);
    case "sorcerer_repair"      -> wildmagic.server.ability.SorcererAbilities.useRepair(player);
    case "sorcerer_mage_armor"  -> wildmagic.server.ability.SorcererAbilities.useMageArmor(player);
			case "bard_word_of_power" -> BardAbilities.useBardWordOfPower(player);
			case "bard_dominate" -> BardAbilities.useBardDominate(player);
			case "bard_silence" -> BardAbilities.useBardSilence(player);
			case "bard_dimension_door" -> BardAbilities.useBardDimensionDoor(player);
			case "bard_greater_invisibility" -> BardAbilities.useBardGreaterInvisibility(player);
			case "warlock_mystic_charge" -> wildmagic.server.ability.WarlockAbilities.useMysticCharge(player);
			case "warlock_frostbite" -> wildmagic.server.ability.WarlockAbilities.useFrostbite(player);
			case "warlock_hex" -> wildmagic.server.ability.WarlockAbilities.useHex(player);
			case "warlock_spider_climb" -> wildmagic.server.ability.WarlockAbilities.useSpiderClimb(player);
			case "warlock_ray_of_weakness" -> wildmagic.server.ability.WarlockAbilities.useRayOfWeakness(player);
			case "warlock_armor_of_agathys" -> wildmagic.server.ability.WarlockAbilities.useArmorOfAgathys(player);
			case "warlock_poison_spray" -> wildmagic.server.ability.WarlockAbilities.usePoisonSpray(player);
			case "warlock_pact_blade" -> wildmagic.server.ability.WarlockAbilities.usePactBlade(player);
			case "warlock_darkness" -> wildmagic.server.ability.WarlockAbilities.useDarkness(player);
			case "warlock_vampiric_touch" -> wildmagic.server.ability.WarlockAbilities.useVampiricTouch(player);
			case "warlock_counterspell" -> wildmagic.server.ability.WarlockAbilities.useCounterspell(player);
			case "warlock_circle_of_death" -> wildmagic.server.ability.WarlockAbilities.useCircleOfDeath(player);
			case "warlock_create_undead" -> wildmagic.server.ability.WarlockAbilities.useCreateUndead(player);
			case "warlock_finger_of_death" -> wildmagic.server.ability.WarlockAbilities.useFingerOfDeath(player);
			case "warlock_breakthrough" -> wildmagic.server.ability.WarlockAbilities.useBreakthrough(player);
			case "warlock_power_word_death" -> wildmagic.server.ability.WarlockAbilities.usePowerWordDeath(player);
			case "wizard_fire_bolt" -> wildmagic.server.ability.WizardAbilities.useFireBolt(player);
			case "wizard_magic_missile" -> wildmagic.server.ability.WizardAbilities.useMagicMissile(player);
			case "wizard_fireball" -> wildmagic.server.ability.WizardAbilities.useFireball(player);
			case "wizard_gravity_well" -> wildmagic.server.ability.WizardAbilities.useGravityWell(player);
			case "wizard_chain_lightning" -> wildmagic.server.ability.WizardAbilities.useChainLightning(player);
			case "wizard_meteor_shower" -> wildmagic.server.ability.WizardAbilities.useMeteorShower(player);
			default -> usePlaceholderAbility(player, ability);
		};
		if (!used) {
			return;
		}
		 if (!used) return;
 
    int actualManaCost = ClassProgression.effectiveManaCost(ability, current);
    // Метамагия делает следующее заклинание бесплатным (не саму метамагию)
    if (actualManaCost > 0
            && !ability.id().equals("sorcerer_metamagic")
            && wildmagic.server.ability.SorcererAbilities.isMetamagicActive(player)) {
        wildmagic.server.ability.SorcererAbilities.consumeMetamagic(player);
        actualManaCost = 0;
    }
 
    PlayerClassData updated = get(player).consumeMana(actualManaCost);
    PLAYER_DATA.put(player.getUUID(), updated);
    setCooldown(player, slot, ClassProgression.effectiveCooldownSeconds(ability, updated));
    sync(player);
	}

	public static boolean isCharmed(LivingEntity attacker, Player target) {
		return WildMagicZones.isCharmed(attacker, target);
	}

	public static boolean isMordenkainenActive(ServerPlayer player) {
		return BardAbilities.isMordenkainenActive(player);
	}

	public static boolean isHeroismActive(ServerPlayer player) {
		return WildMagicZones.isHeroismActive(player);
	}

	public static void breakInvisibility(ServerPlayer player) {
		WildMagicZones.breakInvisibility(player);
	}

	public static boolean isInSilenceZone(ServerPlayer player) {
		return WildMagicZones.isInSilenceZone(player);
	}

	public static void onPlayerDamaged(ServerPlayer player, LivingEntity attacker, ServerLevel level) {
		wildmagic.server.ability.WarlockAbilities.onPlayerDamaged(player, attacker, level);
	}

	public static void applyWarlockUndeadTouch(ServerPlayer attacker, LivingEntity target) {
		wildmagic.server.ability.WarlockAbilities.applyUndeadTouch(attacker, target);
	}

	public static boolean isFriendlySummonedUndead(LivingEntity attacker, LivingEntity target) {
		return wildmagic.server.ability.WarlockAbilities.isSummonedUndeadFriendly(attacker, target);
	}

	public static boolean areTeammates(Entity first, Entity second) {
		if (first == null || second == null) {
			return false;
		}
		return first.isAlliedTo(second);
	}

	public static boolean isDeadOne(ServerPlayer player) {
		return wildmagic.server.ability.WarlockAbilities.isDeadOne(player);
	}

	public static boolean isWarlock(ServerPlayer player) {
		PlayerClassData data = get(player);
		return data.hasClass() && data.selectedClass() == WildMagicClass.WARLOCK;
	}

	public static void drainManaAndClearSpellEffects(ServerPlayer player) {
		PlayerClassData current = get(player);
		if (current.hasClass() && current.maxMana() > 0) {
			PLAYER_DATA.put(player.getUUID(), current.withMana(0));
		}
		WildMagicZones.clearPlayer(player);
		BardAbilities.clearPlayer(player);
		wildmagic.server.ability.WarlockAbilities.clearPlayer(player);
		player.removeEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
		player.removeEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
		player.removeEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
		player.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
		sync(player);
	}

	private static boolean canCastInCurrentArmor(ServerPlayer player, PlayerClassData data, AbilityDefinition ability) {
		if (ability.passive()) {
			return true;
		}
		ArmorTier maxArmor = getMaxArmorTier(player);
		ArmorTier allowed = switch (data.selectedClass()) {
			case WIZARD -> ArmorTier.LIGHT;
			case WARLOCK, SORCERER, BARD -> ArmorTier.MEDIUM;
			default -> ArmorTier.HEAVY;
		};
		if (maxArmor.ordinal() <= allowed.ordinal()) {
			return true;
		}
		player.sendSystemMessage(Component.literal("Броня мешает использовать способность: максимум " + allowed.name().toLowerCase(java.util.Locale.ROOT)));
		return false;
	}

	private static boolean hasEnoughMana(ServerPlayer player, PlayerClassData data, AbilityDefinition ability) {
		int manaCost = ClassProgression.effectiveManaCost(ability, data);
		if (manaCost <= 0 || !data.selectedClass().usesMana()) {
			return true;
		}
		if (data.mana() >= manaCost) {
			return true;
		}
		player.sendSystemMessage(Component.literal("Недостаточно маны: нужно " + manaCost));
		return false;
	}

	private static boolean isOnCooldown(ServerPlayer player, int slot) {
		long remainingTicks = remainingCooldownTicks(player, slot);
		if (remainingTicks <= 0) {
			return false;
		}
		player.sendSystemMessage(Component.literal("Способность на перезарядке: " + Math.ceil(remainingTicks / 20.0D) + "с"));
		return true;
	}

	private static long remainingCooldownTicks(ServerPlayer player, int slot) {
		long[] cooldowns = ABILITY_COOLDOWNS.get(player.getUUID());
		if (cooldowns == null || slot >= cooldowns.length) {
			return 0L;
		}
		long remainingTicks = cooldowns[slot] - player.level().getGameTime();
		return Math.max(0L, remainingTicks);
	}

	private static boolean wouldChangeCooldownSlot(ServerPlayer player, PlayerClassData current, int targetSlot, String abilityId) {
		String normalizedAbilityId = abilityId == null ? "" : abilityId;
		String currentAbilityId = current.activeAbility(targetSlot);
		if (!normalizedAbilityId.equals(currentAbilityId) && remainingCooldownTicks(player, targetSlot) > 0) {
			return true;
		}
		if (normalizedAbilityId.isBlank()) {
			return false;
		}
		for (int otherSlot = 0; otherSlot < PlayerClassData.ACTIVE_SLOT_COUNT; otherSlot++) {
			if (otherSlot == targetSlot) {
				continue;
			}
			if (normalizedAbilityId.equals(current.activeAbility(otherSlot)) && remainingCooldownTicks(player, otherSlot) > 0) {
				return true;
			}
		}
		return false;
	}

	private static void setCooldown(ServerPlayer player, int slot, int cooldownSeconds) {
		if (cooldownSeconds <= 0) {
			return;
		}
		long[] cooldowns = ABILITY_COOLDOWNS.computeIfAbsent(player.getUUID(), uuid -> new long[PlayerClassData.ACTIVE_SLOT_COUNT]);
		cooldowns[slot] = player.level().getGameTime() + (cooldownSeconds * 20L);
	}

	private static boolean usePlaceholderAbility(ServerPlayer player, AbilityDefinition ability) {
		player.sendSystemMessage(Component.literal("Использована способность: " + ability.title()));
		return true;
	}

	private static void updateActiveAbility(ServerPlayer player, PlayerClassData current, int slot, String abilityId) {
		PlayerClassData updated = current.withActiveAbility(slot, abilityId);
		PLAYER_DATA.put(player.getUUID(), updated);
		sync(player);
		save(currentServer);
	}

	public static void addAdvancementExp(ServerPlayer player, int amount) {
		PlayerClassData current = get(player);
		if (!current.hasClass()) {
			return;
		}
		PlayerClassData updated = current.withExp(current.exp() + amount);
		while (updated.level() < ClassProgression.MAX_LEVEL && updated.exp() >= updated.expRequiredForNextLevel()) {
			updated = updated.withExp(updated.exp() - updated.expRequiredForNextLevel()).withLevel(updated.level() + 1);
		}
		PLAYER_DATA.put(player.getUUID(), updated);
		applyClassPassives(player);
		sync(player);
		save(currentServer);
	}

	public static void sync(ServerPlayer player) {
		long[] cooldowns = new long[PlayerClassData.ACTIVE_SLOT_COUNT];
		for (int slot = 0; slot < PlayerClassData.ACTIVE_SLOT_COUNT; slot++) {
			cooldowns[slot] = remainingCooldownTicks(player, slot);
		}
		WildMagicNetworking.sendClassData(player, get(player), cooldowns);
	}

	private static void tickPlayers(MinecraftServer server) {
		wildmagic.server.ability.WizardAbilities.tickProjectiles(server);
		wildmagic.server.ability.WarlockAbilities.tick(server);
		wildmagic.server.ability.SorcererAbilities.tickWings(server);
		wildmagic.server.ability.SorcererAbilities.tickStormJumps(server);
		wildmagic.server.ability.SorcererAbilities.tickIceDaggers(server);
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		MANA_EFFECTS.entrySet().removeIf(entry -> {
			ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
			if (p == null) return true;
			if (p.level().getGameTime() > entry.getValue()[1]) {
				PlayerClassData current = get(p);
				if (current.hasClass()) {
					PLAYER_DATA.put(p.getUUID(), current.withMaxManaBonus(0));
				}
				sync(p);
				return true;
			}
			PlayerClassData current = get(p);
			if (current.hasClass() && current.maxManaBonus() != (int) entry.getValue()[0]) {
				PLAYER_DATA.put(p.getUUID(), current.withMaxManaBonus((int) entry.getValue()[0]));
			}
			return false;
		});
		WildMagicZones.tickHeroism(server);
		BardAbilities.tickGreaterInvisibility(server);
		WildMagicZones.tickSlowZones(server);
		BardAbilities.tickDominate(server);
		BardAbilities.tickForceCage(server);
		BardAbilities.tickMordenkainenSword(server);
		BardAbilities.tickHypnoticPattern(server);
		WildMagicZones.tickInvisibility(server);
		WildMagicZones.tickSilence(server);
		WildMagicZones.tickCharmed(currentServer);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
    applyClassPassives(player);
    regenerateMana(player);
    wildmagic.server.ability.SorcererAbilities.tickPassives(player);
}

	
	}

	private static void regenerateMana(ServerPlayer player) {
		PlayerClassData data = get(player);
		if (!data.hasClass() || !data.selectedClass().usesMana() || data.mana() >= data.effectiveMaxMana()) {
			return;
		}
		PlayerClassData updated = data.withMana(data.mana() + ClassProgression.manaRegenPerSecond(data.selectedClass()));
		PLAYER_DATA.put(player.getUUID(), updated);
		sync(player);
	}
	

	public static void fillMana(ServerPlayer player) {
		PlayerClassData current = get(player);
		if (!current.hasClass() || !current.selectedClass().usesMana()) return;
		PLAYER_DATA.put(player.getUUID(), current.withMana(current.effectiveMaxMana()));
		sync(player);
	}

	private static void applyBardLightStep(ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null && speed.getModifier(BARD_SPEED_MODIFIER_ID) == null) {
			speed.addPermanentModifier(new AttributeModifier(BARD_SPEED_MODIFIER_ID, 0.02D, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	private static void removeBardLightStep(ServerPlayer player) {
		var speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) speed.removeModifier(BARD_SPEED_MODIFIER_ID);
	}

	private static void applyBardCollegeOfSwords(ServerPlayer player, PlayerClassData data) {
		boolean hasSword = player.getMainHandItem().getItem().getDescriptionId().contains("sword");
		var dmg = player.getAttribute(Attributes.ATTACK_DAMAGE);
		var speed = player.getAttribute(Attributes.ATTACK_SPEED);
		var reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (hasSword) {
			if (dmg != null && dmg.getModifier(BARD_SWORD_DAMAGE_ID) == null)
				dmg.addPermanentModifier(new AttributeModifier(BARD_SWORD_DAMAGE_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE));
			if (speed != null && speed.getModifier(BARD_SWORD_SPEED_ID) == null)
				speed.addPermanentModifier(new AttributeModifier(BARD_SWORD_SPEED_ID, 0.5D, AttributeModifier.Operation.ADD_VALUE));
			if (reach != null && reach.getModifier(BARD_SWORD_REACH_ID) == null)
				reach.addPermanentModifier(new AttributeModifier(BARD_SWORD_REACH_ID, 1.0D, AttributeModifier.Operation.ADD_VALUE));
		} else {
			removeBardCollegeOfSwords(player);
		}
	}

	private static void removeBardCollegeOfSwords(ServerPlayer player) {
		var dmg = player.getAttribute(Attributes.ATTACK_DAMAGE);
		var speed = player.getAttribute(Attributes.ATTACK_SPEED);
		var reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
		if (dmg != null) dmg.removeModifier(BARD_SWORD_DAMAGE_ID);
		if (speed != null) speed.removeModifier(BARD_SWORD_SPEED_ID);
		if (reach != null) reach.removeModifier(BARD_SWORD_REACH_ID);
	}

	public static ArmorTier getMaxArmorTier(ServerPlayer player) {
		ArmorTier max = ArmorTier.NONE;
		for (net.minecraft.world.entity.EquipmentSlot slot : new net.minecraft.world.entity.EquipmentSlot[]{
				net.minecraft.world.entity.EquipmentSlot.HEAD,
				net.minecraft.world.entity.EquipmentSlot.CHEST,
				net.minecraft.world.entity.EquipmentSlot.LEGS,
				net.minecraft.world.entity.EquipmentSlot.FEET}) {
			ArmorTier tier = ArmorTier.of(player.getItemBySlot(slot));
			if (tier.ordinal() > max.ordinal()) max = tier;
		}
		return max;
	}

	private static void applyClassPassives(ServerPlayer player) {
		PlayerClassData data = get(player);
		if (data.hasClass() && data.selectedClass() == WildMagicClass.BARD) {
			applyBardHealthPenalty(player);
			removeWizardHealthPenalty(player);
			if (data.level() >= 5) applyBardLightStep(player);
			else removeBardLightStep(player);
			if (data.level() >= 10) applyBardCollegeOfSwords(player, data);
			else removeBardCollegeOfSwords(player);
		} else if (data.hasClass() && data.selectedClass() == WildMagicClass.WIZARD) {
			removeBardHealthPenalty(player);
			removeBardLightStep(player);
			removeBardCollegeOfSwords(player);
			applyWizardHealthPenalty(player);
		} else {
			removeBardHealthPenalty(player);
			removeWizardHealthPenalty(player);
			removeBardLightStep(player);
			removeBardCollegeOfSwords(player);
		}
	}

	private static void removeClassPassives(ServerPlayer player) {
		removeBardHealthPenalty(player);
		removeWizardHealthPenalty(player);
		removeBardLightStep(player);
		removeBardCollegeOfSwords(player);
	}

	private static void applyBardHealthPenalty(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getModifier(BARD_HEALTH_MODIFIER_ID) != null) {
			return;
		}
		maxHealth.addPermanentModifier(new AttributeModifier(BARD_HEALTH_MODIFIER_ID, BARD_HEALTH_PENALTY, AttributeModifier.Operation.ADD_VALUE));
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
	}

	private static void removeBardHealthPenalty(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getModifier(BARD_HEALTH_MODIFIER_ID) == null) {
			return;
		}
		maxHealth.removeModifier(BARD_HEALTH_MODIFIER_ID);
	}

	private static void applyWizardHealthPenalty(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getModifier(WIZARD_HEALTH_MODIFIER_ID) != null) {
			return;
		}
		maxHealth.addPermanentModifier(new AttributeModifier(WIZARD_HEALTH_MODIFIER_ID, WIZARD_HEALTH_PENALTY, AttributeModifier.Operation.ADD_VALUE));
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
	}

	private static void removeWizardHealthPenalty(ServerPlayer player) {
		AttributeInstance maxHealth = player.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null || maxHealth.getModifier(WIZARD_HEALTH_MODIFIER_ID) == null) {
			return;
		}
		maxHealth.removeModifier(WIZARD_HEALTH_MODIFIER_ID);
	}

	public static void setSorcererElement(ServerPlayer player, wildmagic.classdata.SorcererElement element) {
    PlayerClassData current = get(player);
    if (!current.hasClass() || current.selectedClass() != WildMagicClass.SORCERER) return;
    PLAYER_DATA.put(player.getUUID(), current.withSorcererElement(element));
    sync(player);
    save(currentServer);
}

public static long[] getCooldowns(ServerPlayer player) {
    return ABILITY_COOLDOWNS.get(player.getUUID());
}

	private static void load(MinecraftServer server) {
		currentServer = server;
		saveFile = server.getWorldPath(LevelResource.ROOT).resolve("occkawildmagic-player-classes.txt");
		PLAYER_DATA.clear();
		ABILITY_COOLDOWNS.clear();
		if (!Files.exists(saveFile)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(saveFile, StandardCharsets.UTF_8)) {
				String[] parts = line.split("=", 2);
				if (parts.length == 2) {
					PLAYER_DATA.put(UUID.fromString(parts[0]), PlayerClassData.deserialize(parts[1]));
				}
			}
		} catch (IllegalArgumentException | IOException exception) {
			OcckaWildMagic.LOGGER.warn("Could not load Wild Magic class data", exception);
		}
	}

	private static void save(MinecraftServer server) {
		if (server == null || saveFile == null) {
			return;
		}
		StringBuilder builder = new StringBuilder();
		PLAYER_DATA.forEach((uuid, data) -> builder.append(uuid).append('=').append(data.serialize()).append('\n'));
		try {
			Files.writeString(saveFile, builder.toString(), StandardCharsets.UTF_8);
		} catch (IOException exception) {
			OcckaWildMagic.LOGGER.warn("Could not save Wild Magic class data", exception);
		}
	}
}
