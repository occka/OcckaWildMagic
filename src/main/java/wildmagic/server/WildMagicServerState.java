package wildmagic.server;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import wildmagic.OcckaWildMagic;
import wildmagic.classdata.AbilityDefinition;
import wildmagic.classdata.ClassProgression;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.network.WildMagicNetworking;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WildMagicServerState {
	private static final Map<UUID, PlayerClassData> PLAYER_DATA = new ConcurrentHashMap<>();
	private static MinecraftServer currentServer;
	private static Path saveFile;

	private WildMagicServerState() {
	}

	public static void registerEvents() {
		ServerLifecycleEvents.SERVER_STARTED.register(WildMagicServerState::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(WildMagicServerState::save);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
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
		PlayerClassData updated = PlayerClassData.createForClass(clazz, level);
		PLAYER_DATA.put(player.getUUID(), updated);
		sync(player);
		save(currentServer);
	}

	public static void clearClass(ServerPlayer player) {
		PLAYER_DATA.remove(player.getUUID());
		sync(player);
		save(currentServer);
	}

	public static void setActiveAbility(ServerPlayer player, int slot, String abilityId) {
		PlayerClassData current = get(player);
		if (!current.hasClass() || slot < 0 || slot >= PlayerClassData.ACTIVE_SLOT_COUNT) {
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

		ClassProgression.abilityFor(current.selectedClass(), abilityId)
				.filter(ability -> !ability.passive() && ability.isUnlocked(current))
				.ifPresent(ability -> player.sendSystemMessage(Component.literal("Использована способность: " + ability.title())));
	}

	private static void updateActiveAbility(ServerPlayer player, PlayerClassData current, int slot, String abilityId) {
		PlayerClassData updated = current.withActiveAbility(slot, abilityId);
		PLAYER_DATA.put(player.getUUID(), updated);
		sync(player);
		save(currentServer);
	}

	public static void addAdvancementExp(ServerPlayer player) {
		PlayerClassData current = get(player);
		if (!current.hasClass()) {
			return;
		}

		PlayerClassData updated = current.withExp(current.exp() + 1);
		PLAYER_DATA.put(player.getUUID(), updated);
		sync(player);
	}

	public static void sync(ServerPlayer player) {
		WildMagicNetworking.sendClassData(player, get(player));
	}

	private static void load(MinecraftServer server) {
		currentServer = server;
		saveFile = server.getWorldPath(LevelResource.ROOT).resolve("occkawildmagic-player-classes.txt");
		PLAYER_DATA.clear();
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
