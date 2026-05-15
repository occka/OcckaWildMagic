package wildmagic.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import wildmagic.OcckaWildMagic;
import org.lwjgl.glfw.GLFW;
import wildmagic.classdata.PlayerClassData;
import wildmagic.client.gui.ClassMenuScreen;
import wildmagic.client.state.ClientClassState;
import wildmagic.network.WildMagicNetworking;

public class OcckaWildMagicClient implements ClientModInitializer {
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "wild_magic")
	);
	private static KeyMapping openClassMenu;
	private static KeyMapping abilitySlotOne;
	private static KeyMapping abilitySlotTwo;
	private static KeyMapping abilitySlotThree;

	@Override
	public void onInitializeClient() {
		registerNetworking();
		registerKeybind();
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "class_exp"),
				(graphics, tickCounter) -> renderClassExpHud(graphics)
		);
	}

	private static void registerNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(WildMagicNetworking.SyncClassDataS2CPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientClassState.update(PlayerClassData.deserialize(payload.serializedData()))));
	}

	private static void registerKeybind() {
		openClassMenu = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.occkawildmagic.open_class_menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				CATEGORY
		));

		abilitySlotOne = registerAbilityKey("key.occkawildmagic.ability_slot_1", GLFW.GLFW_KEY_Z);
		abilitySlotTwo = registerAbilityKey("key.occkawildmagic.ability_slot_2", GLFW.GLFW_KEY_X);
		abilitySlotThree = registerAbilityKey("key.occkawildmagic.ability_slot_3", GLFW.GLFW_KEY_C);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openClassMenu.consumeClick()) {
				client.setScreen(new ClassMenuScreen());
			}

			consumeAbilityKey(abilitySlotOne, 0);
			consumeAbilityKey(abilitySlotTwo, 1);
			consumeAbilityKey(abilitySlotThree, 2);
		});
	}

	private static KeyMapping registerAbilityKey(String translationKey, int defaultKey) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(
				translationKey,
				InputConstants.Type.KEYSYM,
				defaultKey,
				CATEGORY
		));
	}

	private static void consumeAbilityKey(KeyMapping keyMapping, int slot) {
		while (keyMapping.consumeClick()) {
			ClientPlayNetworking.send(new WildMagicNetworking.UseAbilitySlotC2SPayload(slot));
		}
	}

	private static void renderClassExpHud(GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}

		PlayerClassData data = ClientClassState.data();
		if (!data.hasClass()) {
			drawCentered(graphics, client, Component.literal("Нажми R, чтобы выбрать класс"), screenWidth(client) / 2, screenHeight(client) - 62, 0xFFE6C15A);
			return;
		}

		int width = 182;
		int x = (screenWidth(client) - width) / 2;
		int y = screenHeight(client) - 40;
		graphics.fill(x, y, x + width, y + 5, 0xAA11111A);
		graphics.fill(x, y, x + Math.round(width * data.expProgress()), y + 5, 0xFF8A55FF);
		drawCentered(graphics, client, Component.literal(data.selectedClass().displayName() + " " + data.level() + " ур.  " + data.exp() + "/" + data.expRequiredForNextLevel()), screenWidth(client) / 2, y - 10, 0xFFFFFFFF);
		if (data.selectedClass().usesMana()) {
			drawCentered(graphics, client, Component.literal("Мана " + data.mana() + "/" + data.maxMana()), screenWidth(client) / 2, y + 8, 0xFF55D8FF);
		}
		drawCentered(graphics, client, Component.literal("Z: " + slotLabel(data, 0) + "  X: " + slotLabel(data, 1) + "  C: " + slotLabel(data, 2)), screenWidth(client) / 2, y + 20, 0xFFE6E6E6);
	}

	private static String slotLabel(PlayerClassData data, int slot) {
		String abilityId = data.activeAbility(slot);
		if (abilityId.isBlank()) {
			return "-";
		}

		return wildmagic.classdata.ClassProgression.abilityFor(data.selectedClass(), abilityId)
				.map(wildmagic.classdata.AbilityDefinition::title)
				.orElse(abilityId);
	}

	private static int screenWidth(Minecraft client) {
		return client.getWindow().getGuiScaledWidth();
	}

	private static int screenHeight(Minecraft client) {
		return client.getWindow().getGuiScaledHeight();
	}

	private static void drawCentered(GuiGraphicsExtractor graphics, Minecraft client, Component text, int centerX, int y, int color) {
		graphics.text(client.font, text, centerX - client.font.width(text) / 2, y, color, true);
	}
}
