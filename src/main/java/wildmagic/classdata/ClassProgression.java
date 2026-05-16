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

	private static List<AbilityDefinition> createStarterAbilities(WildMagicClass clazz) {
		if (clazz == WildMagicClass.BARD) {
			return List.of(
					new AbilityDefinition("bard_inspiration", "Вдохновение", "AOE аура: реген II + поглощение VI союзникам в радиусе 5 блоков.", 1, 0, 100),
new AbilityDefinition("bard_healing_word", "Слово исцеления", "Лечит цель в прицеле (до 12 блоков) на 2-8 + уровень HP. Без цели — лечит себя.", 1, 0, 50),
new AbilityDefinition("bard_sound_wave", "Звуковая волна", "Волна 3x3 летит на 6 блоков, наносит 4-10 урона и отталкивает цели.", 2, 0, 60),
new AbilityDefinition("bard_charm", "Очарование", "Цель в прицеле (до 20 блоков) не может наносить урон игроку 15 секунд.", 2, 0, 90),
new AbilityDefinition("bard_heroism", "Героизм", "Цель в прицеле и бард получают +50% урона и иммунитет к яду и иссушению на 20с.", 3, 0, 110),
new AbilityDefinition("bard_invisibility", "Невидимость", "Бард становится полностью невидимым на 60с. Прерывается при атаке или касте.", 3, 0, 120),
new AbilityDefinition("bard_mastery", "Великая баллада", "Поздняя активная способность барда для будущей настройки.", 8, 0, 45),
new AbilityDefinition("bard_fragile_performer", "Хрупкий исполнитель", "Пассивка: максимум здоровья барда меньше на 2 сердца.", 1, 0, 0, true)
			);
		}

		String resource = clazz.usesMana() ? "мана" : "кд";
		return List.of(
				new AbilityDefinition(clazz.id() + "_starter", "Стартовая способность", "Базовая активная способность класса. Позже здесь будет точная механика и баланс.", 1, clazz.usesMana() ? 0 : 12, clazz.usesMana() ? 10 : 0),
				new AbilityDefinition(clazz.id() + "_advanced", "Усиленный прием", "Активная способность с прокачкой. Использует ресурс: " + resource + ".", 4, clazz.usesMana() ? 0 : 24, clazz.usesMana() ? 25 : 0),
				new AbilityDefinition(clazz.id() + "_mastery", "Мастерство класса", "Поздняя сильная активная способность для 8+ уровня.", 8, clazz.usesMana() ? 0 : 45, clazz.usesMana() ? 45 : 0),
				new AbilityDefinition(clazz.id() + "_passive", "Пассивка класса", "Пассивная способность: работает всегда и не ставится в слот хотбара.", 1, 0, 0, true)
		);
	}
}
