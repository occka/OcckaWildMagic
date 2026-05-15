package wildmagic.classdata;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum WildMagicClass {
	BARD("Bard", true, "Музыкальная поддержка, вдохновение союзников и чарующие заклинания."),
	BARBARIAN("Barbarian", false, "Ярость, живучесть и мощные рывки в ближнем бою."),
	FIGHTER("Fighter", false, "Тактические финты, стойки и стабильный урон оружием."),
	WIZARD("Wizard", true, "Книга заклинаний, контроль поля боя и сильная магия."),
	DRUID("Druid", false, "Сила природы, формы зверей и защитные обряды."),
	CLERIC("Cleric", false, "Молитвы, исцеление и священная защита команды."),
	ARTIFICER("Artificer", false, "Магические устройства, турели и усиление снаряжения."),
	WARLOCK("Warlock", true, "Пакт с покровителем, проклятия и темная мана."),
	MONK("Monk", false, "Комбо, мобильность и быстрые удары без тяжелой брони."),
	PALADIN("Paladin", false, "Клятвы, ауры и карающие удары по врагам."),
	ROGUE("Rogue", false, "Скрытность, критические атаки и ловкие приемы."),
	RANGER("Ranger", false, "Охота, меткая стрельба и связь с дикой природой."),
	SORCERER("Sorcerer", true, "Врожденная магия, гибкие заклинания и всплески маны.");

	public static final List<WildMagicClass> VALUES = Arrays.asList(values());

	private final String displayName;
	private final boolean usesMana;
	private final String shortDescription;

	WildMagicClass(String displayName, boolean usesMana, String shortDescription) {
		this.displayName = displayName;
		this.usesMana = usesMana;
		this.shortDescription = shortDescription;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public String displayName() {
		return displayName;
	}

	public boolean usesMana() {
		return usesMana;
	}

	public String shortDescription() {
		return shortDescription;
	}

	public String texturePath() {
		return "textures/gui/classes/" + id() + ".png";
	}
}
