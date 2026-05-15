package wildmagic.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import wildmagic.classdata.AbilityDefinition;
import wildmagic.classdata.ClassProgression;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.client.state.ClientClassState;
import wildmagic.network.WildMagicNetworking;

import java.util.List;

public class ClassMenuScreen extends Screen {
	private WildMagicClass focusedClass = WildMagicClass.BARD;

	public ClassMenuScreen() {
		super(Component.literal("Occka Wild Magic"));
	}

	@Override
	protected void init() {
		clearWidgets();
		PlayerClassData data = ClientClassState.data();
		if (!data.hasClass()) {
			initClassPicker();
		} else {
			focusedClass = data.selectedClass();
			initClassDetails(data);
		}
	}

	private void initClassPicker() {
		int cardWidth = 92;
		int cardHeight = 22;
		int startX = Math.max(10, (width - (cardWidth * 3 + 12)) / 2);
		int startY = 42;
		for (int i = 0; i < WildMagicClass.VALUES.size(); i++) {
			WildMagicClass clazz = WildMagicClass.VALUES.get(i);
			int x = startX + (i % 3) * (cardWidth + 6);
			int y = startY + (i / 3) * (cardHeight + 6);
			addRenderableWidget(Button.builder(Component.literal(clazz.displayName()), button -> focusedClass = clazz)
					.bounds(x, y, cardWidth, cardHeight)
					.build());
		}

		addRenderableWidget(Button.builder(Component.literal("Выбрать навсегда"), button -> {
			ClientPlayNetworking.send(new WildMagicNetworking.SelectClassC2SPayload(focusedClass.id()));
			onClose();
		}).bounds(width / 2 - 70, height - 34, 140, 20).build());
	}

	private void initClassDetails(PlayerClassData data) {
		List<AbilityDefinition> abilities = ClassProgression.abilitiesFor(data.selectedClass());
		int startX = width / 2 - 135;
		int startY = 88;
		for (int i = 0; i < abilities.size(); i++) {
			AbilityDefinition ability = abilities.get(i);
			Component label = Component.literal((ability.isUnlocked(data) ? "✓ " : "✗ ") + ability.title() + " (ур. " + ability.unlockLevel() + ")");
			addRenderableWidget(Button.builder(label, button -> {})
					.bounds(startX, startY + i * 24, 270, 20)
					.build());
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderBackground(graphics, mouseX, mouseY, delta);
		PlayerClassData data = ClientClassState.data();
		if (!data.hasClass()) {
			renderClassPicker(graphics);
		} else {
			renderClassDetails(graphics, data);
		}
		super.render(graphics, mouseX, mouseY, delta);
	}

	private void renderClassPicker(GuiGraphics graphics) {
		graphics.drawCenteredString(font, Component.literal("Выбор класса").withStyle(ChatFormatting.GOLD), width / 2, 16, 0xFFFFFF);
		graphics.drawCenteredString(font, Component.literal("Класс закрепляется за игроком навсегда. Картинки классов можно положить в textures/gui/classes/."), width / 2, 28, 0xC8C8C8);
		graphics.fill(width / 2 - 150, height - 82, width / 2 + 150, height - 44, 0xAA101018);
		graphics.drawString(font, Component.literal(focusedClass.displayName()).withStyle(ChatFormatting.AQUA), width / 2 - 142, height - 76, 0xFFFFFF);
		graphics.drawString(font, Component.literal(focusedClass.shortDescription()), width / 2 - 142, height - 64, 0xDCDCDC);
		graphics.drawString(font, Component.literal(focusedClass.usesMana() ? "Ресурс: мана" : "Ресурс: перезарядки"), width / 2 - 142, height - 52, 0xDCDCDC);
	}

	private void renderClassDetails(GuiGraphics graphics, PlayerClassData data) {
		WildMagicClass clazz = data.selectedClass();
		graphics.drawCenteredString(font, Component.literal(clazz.displayName() + " — уровень " + data.level()).withStyle(ChatFormatting.GOLD), width / 2, 18, 0xFFFFFF);
		graphics.drawCenteredString(font, Component.literal(clazz.shortDescription()), width / 2, 34, 0xC8C8C8);
		int barX = width / 2 - 100;
		int barY = 58;
		graphics.fill(barX, barY, barX + 200, barY + 10, 0xFF232333);
		graphics.fill(barX, barY, barX + Math.round(200 * data.expProgress()), barY + 10, 0xFF7D4CDB);
		graphics.drawCenteredString(font, Component.literal("Опыт класса: " + data.exp() + " / " + data.expRequiredForNextLevel()), width / 2, barY + 14, 0xFFFFFF);
		if (clazz.usesMana()) {
			graphics.drawCenteredString(font, Component.literal("Мана: " + data.mana() + " / " + data.maxMana()).withStyle(ChatFormatting.AQUA), width / 2, barY + 28, 0xFFFFFF);
		}
	}
}
