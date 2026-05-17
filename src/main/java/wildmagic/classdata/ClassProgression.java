package wildmagic.classdata;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ClassProgression {
	public static final int MIN_LEVEL = 1;
	public static final int MAX_LEVEL = 12;

	private static final Map<WildMagicClass, List<AbilityDefinition>> DEFAULT_ABILITIES = WildMagicClass.VALUES.stream()
			.collect(Collectors.toUnmodifiableMap(clazz -> clazz, ClassProgression::createStarterAbilities));

	private ClassProgression() {
	}

public static int expRequiredForNextLevel(int currentLevel) {
    if (currentLevel < MIN_LEVEL || currentLevel >= MAX_LEVEL) {
        return 0;
    }
    return Math.min(4 + ((currentLevel - 1) * 2), 10);
}

	public static int clampLevel(int level) {
		return Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
	}

	public static int maxManaForLevel(int level) {
    int clamped = Math.max(MIN_LEVEL, level);
    return 100 + (clamped / 3) * 20;
}

	public static int manaRegenPerSecond(WildMagicClass clazz) {
    if (clazz == null || !clazz.usesMana()) return 0;
    return switch (clazz) {
        case BARD -> 2;
        case WARLOCK -> 3;
        default -> 5;
    };
}

	public static List<AbilityDefinition> abilitiesFor(WildMagicClass clazz) {
		return DEFAULT_ABILITIES.getOrDefault(clazz, List.of());
	}

	public static Optional<AbilityDefinition> abilityFor(WildMagicClass clazz, String abilityId) {
		if (clazz == null || abilityId == null || abilityId.isBlank()) {
			return Optional.empty();
		}

		return abilitiesFor(clazz).stream()
				.filter(ability -> ability.id().equals(abilityId))
				.findFirst();
	}

	public static int effectiveCooldownSeconds(AbilityDefinition ability, PlayerClassData data) {
		if (ability == null || data == null) {
			return 0;
		}

		if ("warlock_mystic_charge".equals(ability.id()) && data.level() >= 10) {
			return 4;
		}

		return ability.cooldownSeconds();
	}

	public static int effectiveManaCost(AbilityDefinition ability, PlayerClassData data) {
		if (ability == null || data == null) {
			return 0;
		}

		if ("warlock_armor_of_agathys".equals(ability.id())) {
			if (data.level() >= 10) {
				return 110;
			}

			if (data.level() >= 6) {
				return 90;
			}
		}

		return ability.manaCost();
	}

	private static List<AbilityDefinition> createStarterAbilities(WildMagicClass clazz) {
		if (clazz == WildMagicClass.BARD) {
			return List.of(
					new AbilityDefinition("bard_inspiration", "Вдохновение", "AOE аура: реген II + поглощение VI союзникам в радиусе 5 блоков.", 1, 0, 100),
new AbilityDefinition("bard_healing_word", "Слово исцеления", "Лечит цель в прицеле (до 12 блоков) на 2-8 + уровень HP. Без цели — лечит себя.", 1, 0, 40),
new AbilityDefinition("bard_sound_wave", "Звуковая волна", "Волна 3x3 летит на 6 блоков, наносит 4-10 урона и отталкивает цели.", 2, 0, 50),
new AbilityDefinition("bard_charm", "Очарование", "Цель в прицеле (до 20 блоков) не может наносить урон игроку 15 секунд.", 2, 0, 90),
new AbilityDefinition("bard_heroism", "Героизм", "Цель в прицеле и бард получают +50% урона и иммунитет к яду и иссушению на 20с.", 3, 0, 110),
new AbilityDefinition("bard_invisibility", "Невидимость", "Бард становится полностью невидимым на 60с. Прерывается при атаке или касте.", 3, 0, 120),
new AbilityDefinition("bard_misty_step", "Туманный шаг", "Телепорт вперёд до 25 блоков, не сквозь стены. 40 маны.", 4, 0, 25),
new AbilityDefinition("bard_silence", "Тишина", "Сфера радиусом 5 блоков — никто внутри не может использовать заклинания. 120 маны.", 4, 0, 120),
new AbilityDefinition("bard_dispel", "Рассеивание магии", "Снимает все эффекты с цели в прицеле (до 20 блоков). 150 маны.", 6, 0, 120),
new AbilityDefinition("bard_slow_zone", "Замедление", "Снаряд создаёт зону 4x4x4 с замедлением, слабостью и усталостью на 15с.", 6, 0, 130),
new AbilityDefinition("bard_dimension_door", "Переносящая дверь", "Телепортирует барда и союзника рядом до 150 блоков вперёд или сквозь стены.", 7, 0, 140),
new AbilityDefinition("bard_greater_invisibility", "Высшая невидимость", "Бард и союзники в радиусе 1 блока невидимы 25с, не снимается атакой.", 7, 0, 140),
new AbilityDefinition("bard_force_cage", "Силовая клетка", "Создаёт клетку из стекла вокруг цели (25 блоков) на 25с. Внутри нельзя использовать магию.", 12, 300, 180),
new AbilityDefinition("bard_mordenkainen_sword", "Меч Морденкайнена", "Даёт Силу II на 1 минуту.", 12, 240, 170),
new AbilityDefinition("bard_haste", "Скороход", "Скорость III всем в радиусе 5 блоков на 1.5 минуты.", 8, 0, 30),
new AbilityDefinition("bard_feather_fall", "Падение перышком", "Плавное падение II всем в радиусе 5 блоков на минуту.", 8, 0, 30),
new AbilityDefinition("bard_hypnotic_pattern", "Завораживающий узор", "Спираль над бардом — все в радиусе 25 блоков останавливаются и смотрят на неё 8с.", 9, 0, 160),
new AbilityDefinition("bard_word_of_power", "Слово силы: укрепление", "Распределяет 80HP поглощения поровну игрокам в радиусе 5 блоков на 10 минут.", 11, 180, 160),
new AbilityDefinition("bard_dominate", "Подчинение личности", "Берёт цель под контроль на 10с — перемещает к барду и атакует вместе с ним.", 11, 240, 160),
new AbilityDefinition("bard_heat_metal", "Раскалённый металл", "2-12 урона огнём цели в броне medium/heavy, поджигает.", 8, 0, 40),
new AbilityDefinition("bard_shatter", "Дребезги", "Сфера радиусом 5 блоков — все внутри получают 4-14 физ. урона.", 8, 0, 50),
new AbilityDefinition("bard_light_step", "Лёгкая поступь", "Пассивка: бард немного быстрее.", 5, 0, 0, true),
new AbilityDefinition("bard_college_of_swords", "Коллегия мечей", "Пассивка: с мечом в руке — дальше, быстрее, +2 урона.", 10, 0, 0, true),
new AbilityDefinition("bard_fragile_performer", "Хрупкий исполнитель", "Пассивка: -2 сердца. Нельзя использовать заклинания в алмазной или незеритовой броне.", 1, 0, 0, true)
			);
		}
		if (clazz == WildMagicClass.WIZARD) {
			return WizardAbilities.create();
		}
		if (clazz == WildMagicClass.SORCERER) {
			return SorcererAbilities.create();
		}
		if (clazz == WildMagicClass.WARLOCK) {
			return WarlockAbilities.create();
		}

		return MartialAbilities.create(clazz);
	}
}
