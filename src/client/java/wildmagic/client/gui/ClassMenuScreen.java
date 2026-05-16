package wildmagic.client.gui;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import wildmagic.classdata.AbilityDefinition;
import wildmagic.classdata.ClassProgression;
import wildmagic.classdata.PlayerClassData;
import wildmagic.classdata.WildMagicClass;
import wildmagic.client.state.ClientClassState;
import wildmagic.network.WildMagicNetworking;

import java.util.Comparator;
import java.util.List;

public class ClassMenuScreen extends Screen {
	private WildMagicClass focusedClass = WildMagicClass.BARD;
	private int selectedAbilitySlot;

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
    int slotWidth = 86;
    int slotStartX = width / 2 - 134;
    for (int slot = 0; slot < PlayerClassData.ACTIVE_SLOT_COUNT; slot++) {
        int currentSlot = slot;
        String abilityTitle = abilityTitle(data.selectedClass(), data.activeAbility(slot));
        Component slotLabel = Component.literal((selectedAbilitySlot == slot ? "> " : "") + "Слот " + (slot + 1) + ": " + abilityTitle);
        addRenderableWidget(Button.builder(slotLabel, button -> selectedAbilitySlot = currentSlot)
                .bounds(slotStartX + slot * (slotWidth + 6), 64, slotWidth, 20)
                .build());
    }

    addRenderableWidget(Button.builder(Component.literal("Очистить слот " + (selectedAbilitySlot + 1)), button -> equipAbility(""))
            .bounds(width / 2 - 70, 88, 140, 20)
            .build());

    List<AbilityDefinition> abilities = ClassProgression.abilitiesFor(data.selectedClass()).stream()
            .sorted(Comparator.comparing(AbilityDefinition::passive)
                    .thenComparingInt(AbilityDefinition::unlockLevel)
                    .thenComparing(AbilityDefinition::title))
            .toList();

    int colCount = 4;
    int btnWidth = (width - 20) / colCount - 4;
    int btnHeight = 20;
    int gridStartX = 10;
    int gridStartY = 116;
    int colIndex = 0;
    int rowIndex = 0;

    for (AbilityDefinition ability : abilities) {
        if (!ability.isUnlocked(data)) continue;

        int x = gridStartX + colIndex * (btnWidth + 4);
        int y = gridStartY + rowIndex * (btnHeight + 4);

        Component label = Component.literal(ability.passive() ? "[П] " + ability.title() : ability.title() + " (" + abilityResourceText(data, ability) + ")");
        addRenderableWidget(Button.builder(label, button -> {
            if (ability.canBeEquipped() && ability.isUnlocked(ClientClassState.data())) {
                equipAbility(ability.id());
            }
        }).bounds(x, y, btnWidth, btnHeight).build());

        colIndex++;
        if (colIndex >= colCount) {
            colIndex = 0;
            rowIndex++;
        }
    }
}

	private void equipAbility(String abilityId) {
		ClientClassState.setActiveAbility(selectedAbilitySlot, abilityId);
		ClientPlayNetworking.send(new WildMagicNetworking.SetAbilitySlotC2SPayload(selectedAbilitySlot, abilityId));
		clearWidgets();
		initClassDetails(ClientClassState.data());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		PlayerClassData data = ClientClassState.data();
		if (!data.hasClass()) {
			renderClassPicker(graphics);
		} else {
			renderClassDetails(graphics, data);
		}
	}

	private void renderClassPicker(GuiGraphicsExtractor graphics) {
		drawCentered(graphics, Component.literal("Выбор класса").withStyle(ChatFormatting.GOLD), width / 2, 16, 0xFFFFFFFF);
		drawCentered(graphics, Component.literal("Класс закрепляется за игроком навсегда. Картинки классов можно положить в textures/gui/classes/."), width / 2, 28, 0xFFC8C8C8);
		graphics.fill(width / 2 - 150, height - 82, width / 2 + 150, height - 44, 0xAA101018);
		graphics.text(font, Component.literal(focusedClass.displayName()).withStyle(ChatFormatting.AQUA), width / 2 - 142, height - 76, 0xFFFFFFFF, true);
		graphics.text(font, Component.literal(focusedClass.shortDescription()), width / 2 - 142, height - 64, 0xFFDCDCDC, true);
		graphics.text(font, Component.literal(focusedClass.usesMana() ? "Ресурс: мана" : "Ресурс: перезарядки"), width / 2 - 142, height - 52, 0xFFDCDCDC, true);
	}

	private void renderClassDetails(GuiGraphicsExtractor graphics, PlayerClassData data) {
    WildMagicClass clazz = data.selectedClass();
    drawCentered(graphics, Component.literal(clazz.displayName() + " — уровень " + data.level()).withStyle(ChatFormatting.GOLD), width / 2, 18, 0xFFFFFFFF);
    drawCentered(graphics, Component.literal(clazz.shortDescription()), width / 2, 34, 0xFFC8C8C8);

    int barX = width / 2 - 100;
    int barY = 48;
    graphics.fill(barX, barY, barX + 200, barY + 8, 0xFF232333);
    graphics.fill(barX, barY, barX + Math.round(200 * data.expProgress()), barY + 8, 0xFF7D4CDB);

    if (data.level() >= ClassProgression.MAX_LEVEL) {
        drawCentered(graphics, Component.literal("Максимальный уровень класса"), width / 2, barY + 10, 0xFFFFFFFF);
    } else {
        drawCentered(graphics, Component.literal("До уровня " + (data.level() + 1) + ": " + data.exp() + " / " + data.expRequiredForNextLevel() + " exp"), width / 2, barY + 10, 0xFFFFFFFF);
    }

    if (clazz.usesMana()) {
        graphics.text(font, Component.literal("Мана: " + data.mana() + " / " + data.maxMana() + " (+" + ClassProgression.manaRegenPerSecond(clazz) + "/с)").withStyle(ChatFormatting.AQUA), width / 2 + 108, barY, 0xFFFFFFFF, true);
    } else {
        graphics.text(font, Component.literal("Ресурс: КД способностей").withStyle(ChatFormatting.AQUA), width / 2 + 108, barY, 0xFFFFFFFF, true);
    }

    // описание выбранной способности
String selectedId = data.activeAbility(selectedAbilitySlot);
if (!selectedId.isBlank()) {
    ClassProgression.abilityFor(data.selectedClass(), selectedId).ifPresent(ability -> {
        drawCentered(graphics, Component.literal(ability.description()).withStyle(ChatFormatting.GRAY), width / 2, height - 24, 0xFFCCCCCC);
    });
}
drawCentered(graphics, Component.literal("[П] — пассивка, работает всегда. Выбери слот сверху, затем нажми способность."), width / 2, height - 14, 0xFF888888);
}

	private String abilityResourceText(PlayerClassData data, AbilityDefinition ability) {
		int cooldown = ClassProgression.effectiveCooldownSeconds(ability, data);
		int manaCost = ClassProgression.effectiveManaCost(ability, data);
		if (!data.selectedClass().usesMana() || manaCost <= 0) {
			return cooldown > 0 ? "КД " + cooldown + "с" : "без КД";
		}

		return manaCost + " маны" + (cooldown > 0 ? ", КД " + cooldown + "с" : "");
	}

	private String abilityTitle(WildMagicClass clazz, String abilityId) {
		if (abilityId == null || abilityId.isBlank()) {
			return "пусто";
		}

		return ClassProgression.abilityFor(clazz, abilityId)
				.map(AbilityDefinition::title)
				.orElse(abilityId);
	}

	private void drawCentered(GuiGraphicsExtractor graphics, Component text, int centerX, int y, int color) {
		graphics.text(font, text, centerX - font.width(text) / 2, y, color, true);
	}
}
