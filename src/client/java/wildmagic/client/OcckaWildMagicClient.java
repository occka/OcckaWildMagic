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
import wildmagic.WildMagicItems;
import org.lwjgl.glfw.GLFW;
import wildmagic.classdata.PlayerClassData;
import wildmagic.client.gui.ClassMenuScreen;
import wildmagic.client.state.ClientClassState;
import wildmagic.network.WildMagicNetworking;
import wildmagic.client.state.ClientClimbState;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;

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
		registerItemColors();
		registerKeybind();
		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "class_exp"),
				(graphics, tickCounter) -> renderClassExpHud(graphics)
		);
	}

	private static void registerItemColors() {
    var colors = Minecraft.getInstance().getItemColors();
    colors.register((stack, tintIndex) -> tintIndex == 0 ? 0x5B2FCC : -1, WildMagicItems.MANA_POTION);
    colors.register((stack, tintIndex) -> tintIndex == 0 ? 0x2A5FFF : -1, WildMagicItems.MANA_POTION_2);
    colors.register((stack, tintIndex) -> tintIndex == 0 ? 0xFF4500 : -1, WildMagicItems.DRAGON_POTION_FIRE);
    colors.register((stack, tintIndex) -> tintIndex == 0 ? 0xAAEEFF : -1, WildMagicItems.DRAGON_POTION_ICE);
    colors.register((stack, tintIndex) -> tintIndex == 0 ? 0x4444FF : -1, WildMagicItems.DRAGON_POTION_LIGHTNING);
    colors.register((stack, tintIndex) -> tintIndex == 0 ? 0x1A7A1A : -1, WildMagicItems.DRAGON_POTION_POISON);
    colors.register((stack, tintIndex) -> tintIndex == 0 ? 0xCCCCCC : -1, WildMagicItems.DRAGON_POTION_THUNDER);
}


	private static void registerNetworking() {
		ClientPlayNetworking.registerGlobalReceiver(WildMagicNetworking.SyncClassDataS2CPayload.TYPE, (payload, context) -> context.client().execute(() -> ClientClassState.update(payload.serializedData())));
		ClientPlayNetworking.registerGlobalReceiver(WildMagicNetworking.SpiderClimbS2CPayload.TYPE, 
    (payload, context) -> context.client().execute(() -> ClientClimbState.setClimbing(payload.active())));
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
			drawCentered(graphics, client, Component.literal("Нажми R, чтобы выбрать класс"), screenWidth(client) / 2, screenHeight(client) - 72, 0xFFE6C15A);
			return;
		}

		int classBarWidth = 182;
		int classBarX = (screenWidth(client) - classBarWidth) / 2;
		int classBarY = screenHeight(client) - 62;
		graphics.fill(classBarX, classBarY, classBarX + classBarWidth, classBarY + 5, 0xAA11111A);
		graphics.fill(classBarX, classBarY, classBarX + Math.round(classBarWidth * data.expProgress()), classBarY + 5, 0xFF8A55FF);
		drawCentered(graphics, client, Component.literal(data.selectedClass().displayName() + " " + data.level() + " ур.  " + data.exp() + "/" + data.expRequiredForNextLevel()), screenWidth(client) / 2, classBarY - 10, 0xFFFFFFFF);

		int abilityX = (screenWidth(client) / 2) + 96;
		int abilityY = screenHeight(client) - 24;
		for (int slot = 0; slot < PlayerClassData.ACTIVE_SLOT_COUNT; slot++) {
			int x = abilityX + (slot * 24);
			graphics.fill(x, abilityY, x + 22, abilityY + 22, 0xAA11111A);
			graphics.fill(x + 1, abilityY + 1, x + 21, abilityY + 21, 0xAA25253A);
			drawCentered(graphics, client, Component.literal(slotHudText(data, slot)), x + 11, abilityY + 7, 0xFFFFFFFF);
		}

		if (data.selectedClass().usesMana()) {
			int manaX = abilityX;
			int manaY = abilityY - 10;
			int manaWidth = 70;
			float manaProgress = data.effectiveMaxMana() <= 0 ? 0.0F : (float) data.mana() / (float) data.effectiveMaxMana();
			graphics.fill(manaX, manaY, manaX + manaWidth, manaY + 5, 0xAA071225);
			graphics.fill(manaX, manaY, manaX + Math.round(manaWidth * manaProgress), manaY + 5, 0xFF2AA7FF);
			drawCentered(graphics, client, Component.literal(data.mana() + "/" + data.effectiveMaxMana()), manaX + (manaWidth / 2), manaY - 9, 0xFF55D8FF);
		}
	}

	private static String slotHudText(PlayerClassData data, int slot) {
		String abilityId = data.activeAbility(slot);
		if (abilityId.isBlank()) {
			return "-";
		}

		int remainingCooldown = ClientClassState.remainingCooldownSeconds(slot);
		if (remainingCooldown > 0) {
			return remainingCooldown + "s";
		}

		return wildmagic.classdata.ClassProgression.abilityFor(data.selectedClass(), abilityId)
				.map(ability -> {
					int cooldown = wildmagic.classdata.ClassProgression.effectiveCooldownSeconds(ability, data);
					int manaCost = wildmagic.classdata.ClassProgression.effectiveManaCost(ability, data);
					if (!data.selectedClass().usesMana() || manaCost <= 0) {
						return cooldown > 0 ? cooldown + "s" : "OK";
					}
					return manaCost + "M";
				})
				.orElse("?");
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
