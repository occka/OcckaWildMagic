package wildmagic.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import wildmagic.OcckaWildMagic;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.server.WildMagicServerState;

import java.util.Locale;
import java.util.Optional;

public final class WildMagicNetworking {
	private WildMagicNetworking() {
	}

	public static void registerServerReceivers() {
		PayloadTypeRegistry.serverboundPlay().register(SelectClassC2SPayload.TYPE, SelectClassC2SPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetAbilitySlotC2SPayload.TYPE, SetAbilitySlotC2SPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(UseAbilitySlotC2SPayload.TYPE, UseAbilitySlotC2SPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectClassC2SPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			Optional<WildMagicClass> selected = WildMagicClass.VALUES.stream()
					.filter(clazz -> clazz.id().equals(payload.classId().toLowerCase(Locale.ROOT)))
					.findFirst();
			selected.ifPresent(clazz -> WildMagicServerState.selectClass(player, clazz));
		}));
		ServerPlayNetworking.registerGlobalReceiver(SetAbilitySlotC2SPayload.TYPE, (payload, context) -> context.server().execute(() -> WildMagicServerState.setActiveAbility(context.player(), payload.slot(), payload.abilityId())));
		ServerPlayNetworking.registerGlobalReceiver(UseAbilitySlotC2SPayload.TYPE, (payload, context) -> context.server().execute(() -> WildMagicServerState.useActiveAbility(context.player(), payload.slot())));
	}

	public static void registerPayloadTypes() {
		PayloadTypeRegistry.clientboundPlay().register(SyncClassDataS2CPayload.TYPE, SyncClassDataS2CPayload.CODEC);
	}

	public static void sendClassData(ServerPlayer player, PlayerClassData data) {
		sendClassData(player, data, new long[PlayerClassData.ACTIVE_SLOT_COUNT]);
	}

	public static void sendClassData(ServerPlayer player, PlayerClassData data, long[] cooldownTicks) {
		StringBuilder serialized = new StringBuilder(data.serialize());
		for (int slot = 0; slot < PlayerClassData.ACTIVE_SLOT_COUNT; slot++) {
			long ticks = cooldownTicks != null && slot < cooldownTicks.length ? Math.max(0L, cooldownTicks[slot]) : 0L;
			serialized.append(';').append(ticks);
		}
		ServerPlayNetworking.send(player, new SyncClassDataS2CPayload(serialized.toString()));
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, path);
	}

	public record SyncClassDataS2CPayload(String serializedData) implements CustomPacketPayload {
		public static final Type<SyncClassDataS2CPayload> TYPE = new Type<>(id("sync_class_data"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SyncClassDataS2CPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8,
				SyncClassDataS2CPayload::serializedData,
				SyncClassDataS2CPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SelectClassC2SPayload(String classId) implements CustomPacketPayload {
		public static final Type<SelectClassC2SPayload> TYPE = new Type<>(id("select_class"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SelectClassC2SPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8,
				SelectClassC2SPayload::classId,
				SelectClassC2SPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record SetAbilitySlotC2SPayload(int slot, String abilityId) implements CustomPacketPayload {
		public static final Type<SetAbilitySlotC2SPayload> TYPE = new Type<>(id("set_ability_slot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, SetAbilitySlotC2SPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT,
				SetAbilitySlotC2SPayload::slot,
				ByteBufCodecs.STRING_UTF8,
				SetAbilitySlotC2SPayload::abilityId,
				SetAbilitySlotC2SPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record UseAbilitySlotC2SPayload(int slot) implements CustomPacketPayload {
		public static final Type<UseAbilitySlotC2SPayload> TYPE = new Type<>(id("use_ability_slot"));
		public static final StreamCodec<RegistryFriendlyByteBuf, UseAbilitySlotC2SPayload> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT,
				UseAbilitySlotC2SPayload::slot,
				UseAbilitySlotC2SPayload::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
