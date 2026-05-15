package wildmagic.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
		PayloadTypeRegistry.playC2S().register(SelectClassC2SPayload.TYPE, SelectClassC2SPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SelectClassC2SPayload.TYPE, (payload, context) -> context.server().execute(() -> {
			ServerPlayer player = context.player();
			Optional<WildMagicClass> selected = WildMagicClass.VALUES.stream()
					.filter(clazz -> clazz.id().equals(payload.classId().toLowerCase(Locale.ROOT)))
					.findFirst();
			selected.ifPresent(clazz -> WildMagicServerState.selectClass(player, clazz));
		}));
	}

	public static void registerPayloadTypes() {
		PayloadTypeRegistry.playS2C().register(SyncClassDataS2CPayload.TYPE, SyncClassDataS2CPayload.CODEC);
	}

	public static void sendClassData(ServerPlayer player, PlayerClassData data) {
		ServerPlayNetworking.send(player, new SyncClassDataS2CPayload(data.serialize()));
	}

	private static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, path);
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
}
