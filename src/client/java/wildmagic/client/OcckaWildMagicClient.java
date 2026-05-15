package wildmagic.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import wildmagic.classdata.PlayerClassData;
import wildmagic.client.gui.ClassMenuScreen;
import wildmagic.client.state.ClientClassState;
import wildmagic.network.WildMagicNetworking;

public class OcckaWildMagicClient implements ClientModInitializer {
	private static KeyMapping openClassMenu;

	@Override
	public void onInitializeClient() {
		registerNetworking();
		registerKeybind();
		HudRenderCallback.EVENT.register((graphics, tickCounter) -> renderClassExpHud(graphics));
	}

	private static void registerNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(WildMagicNetworking.SyncClassDataS2CPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientClassState.update(PlayerClassData.deserialize(payload.serializedData()))));
	}

	private static void registerKeybind() {
		openClassMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.occkawildmagic.open_class_menu",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				"category.occkawildmagic"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openClassMenu.consumeClick()) {
				client.setScreen(new ClassMenuScreen());
			}
		});
	}

	private static void renderClassExpHud(GuiGraphics graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}

		PlayerClassData data = ClientClassState.data();
		if (!data.hasClass()) {
			graphics.drawCenteredString(client.font, Component.literal("Нажми R, чтобы выбрать класс"), graphics.guiWidth() / 2, graphics.guiHeight() - 62, 0xFFE6C15A);
			return;
		}

		int width = 182;
		int x = (graphics.guiWidth() - width) / 2;
		int y = graphics.guiHeight() - 40;
		graphics.fill(x, y, x + width, y + 5, 0xAA11111A);
		graphics.fill(x, y, x + Math.round(width * data.expProgress()), y + 5, 0xFF8A55FF);
		graphics.drawCenteredString(client.font, Component.literal(data.selectedClass().displayName() + " " + data.level() + " ур.  " + data.exp() + "/" + data.expRequiredForNextLevel()), graphics.guiWidth() / 2, y - 10, 0xFFFFFFFF);
		if (data.selectedClass().usesMana()) {
			graphics.drawCenteredString(client.font, Component.literal("Мана " + data.mana() + "/" + data.maxMana()), graphics.guiWidth() / 2, y + 8, 0xFF55D8FF);
		}
	}
}
