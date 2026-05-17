package wildmagic.classdata;

import java.util.List;

public final class MartialAbilities {
	private MartialAbilities() {
	}

	public static List<AbilityDefinition> create(WildMagicClass clazz) {
		return switch (clazz) {
			case BARBARIAN -> List.of(
					new AbilityDefinition("barbarian_rage", "Ярость", "Боевой режим без маны: временно усиливает ближний бой. Настроить механику позже.", 1, 20, 0),
					new AbilityDefinition("barbarian_leap", "Дикий прыжок", "Рывок к цели/вперёд с КД. Настроить механику позже.", 3, 16, 0),
					new AbilityDefinition("barbarian_ground_slam", "Удар о землю", "AOE-удар вокруг варвара. Настроить механику позже.", 6, 30, 0),
					new AbilityDefinition("barbarian_unbreakable", "Неукротимость", "Пассивка варвара: живучесть в ближнем бою.", 1, 0, 0, true)
			);
			case FIGHTER -> List.of(
					new AbilityDefinition("fighter_second_wind", "Второе дыхание", "Самовосстановление по КД. Настроить механику позже.", 1, 25, 0),
					new AbilityDefinition("fighter_action_surge", "Всплеск действий", "Короткое ускорение атак. Настроить механику позже.", 4, 35, 0),
					new AbilityDefinition("fighter_guard_break", "Пробитие защиты", "Сильный прием против защищенной цели. Настроить механику позже.", 7, 28, 0),
					new AbilityDefinition("fighter_weapon_mastery", "Мастер оружия", "Пассивка бойца: стабильный урон оружием.", 1, 0, 0, true)
			);
			case DRUID -> List.of(
					new AbilityDefinition("druid_barkskin", "Дубовая кожа", "Защитный природный прием по КД. Настроить механику позже.", 1, 24, 0),
					new AbilityDefinition("druid_entangle", "Оплетающие корни", "Контроль зоны без маны, только КД. Настроить механику позже.", 4, 30, 0),
					new AbilityDefinition("druid_wild_shape", "Дикая форма", "Превращение/стойка друида. Настроить механику позже.", 6, 45, 0),
					new AbilityDefinition("druid_nature_bond", "Связь с природой", "Пассивка друида.", 1, 0, 0, true)
			);
			case CLERIC -> List.of(
					new AbilityDefinition("cleric_prayer", "Молитва", "Поддержка союзников по КД. Настроить механику позже.", 1, 18, 0),
					new AbilityDefinition("cleric_turn_undead", "Изгнание нежити", "Контроль нежити без маны. Настроить механику позже.", 3, 30, 0),
					new AbilityDefinition("cleric_sanctuary", "Святилище", "Защитная зона по КД. Настроить механику позже.", 7, 45, 0),
					new AbilityDefinition("cleric_devotion", "Преданность", "Пассивка клирика.", 1, 0, 0, true)
			);
			case ARTIFICER -> List.of(
					new AbilityDefinition("artificer_gadget", "Магический гаджет", "Устройство по КД. Настроить механику позже.", 1, 20, 0),
					new AbilityDefinition("artificer_turret", "Турель", "Ставит механизм поддержки. Настроить механику позже.", 5, 40, 0),
					new AbilityDefinition("artificer_overcharge", "Перегрузка", "Усиление предмета/механизма. Настроить механику позже.", 8, 35, 0),
					new AbilityDefinition("artificer_infusions", "Инфузии", "Пассивка изобретателя.", 1, 0, 0, true)
			);
			case MONK -> List.of(
					new AbilityDefinition("monk_flurry", "Шквал ударов", "Серия быстрых ударов по КД. Настроить механику позже.", 1, 12, 0),
					new AbilityDefinition("monk_dash", "Шаг ветра", "Мобильный рывок без маны. Настроить механику позже.", 3, 14, 0),
					new AbilityDefinition("monk_stunning_strike", "Ошеломляющий удар", "Контроль цели по КД. Настроить механику позже.", 6, 28, 0),
					new AbilityDefinition("monk_unarmored", "Без брони", "Пассивка монаха: стиль боя без тяжелой брони.", 1, 0, 0, true)
			);
			case PALADIN -> List.of(
					new AbilityDefinition("paladin_smite", "Кара", "Усиленный удар по КД. Настроить механику позже.", 1, 16, 0),
					new AbilityDefinition("paladin_aura", "Аура защиты", "Короткая защитная аура. Настроить механику позже.", 4, 30, 0),
					new AbilityDefinition("paladin_lay_on_hands", "Возложение рук", "Поддержка союзника по КД. Настроить механику позже.", 7, 45, 0),
					new AbilityDefinition("paladin_oath", "Клятва", "Пассивка паладина.", 1, 0, 0, true)
			);
			case ROGUE -> List.of(
					new AbilityDefinition("rogue_dash", "Рывок", "Быстрый маневр по КД. Настроить механику позже.", 1, 10, 0),
					new AbilityDefinition("rogue_smoke_bomb", "Дымовая бомба", "Сбивает преследование. Настроить механику позже.", 4, 25, 0),
					new AbilityDefinition("rogue_backstab", "Удар в спину", "Сильный прием по уязвимой цели. Настроить механику позже.", 6, 22, 0),
					new AbilityDefinition("rogue_sneak_attack", "Скрытая атака", "Пассивка плута.", 1, 0, 0, true)
			);
			case RANGER -> List.of(
					new AbilityDefinition("ranger_mark", "Метка охотника", "Помечает цель по КД. Настроить механику позже.", 1, 18, 0),
					new AbilityDefinition("ranger_volley", "Залп", "Дальний прием по области. Настроить механику позже.", 4, 26, 0),
					new AbilityDefinition("ranger_trap", "Ловушка", "Ставит ловушку без маны. Настроить механику позже.", 6, 32, 0),
					new AbilityDefinition("ranger_favored_enemy", "Избранный враг", "Пассивка следопыта.", 1, 0, 0, true)
			);
			default -> List.of();
		};
	}
}
