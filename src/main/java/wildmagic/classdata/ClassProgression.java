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

		return 4 + ((currentLevel - 1) * 2);
	}

	public static int clampLevel(int level) {
		return Math.clamp(level, MIN_LEVEL, MAX_LEVEL);
	}

	public static int maxManaForLevel(int level) {
		return 40 + (Math.max(MIN_LEVEL, level) * 10);
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
		String resource = clazz.usesMana() ? "мана" : "кд";
		return List.of(
				new AbilityDefinition(clazz.id() + "_starter", "Стартовая способность", "Базовая активная способность класса. Позже здесь будет точная механика и баланс.", 1, clazz.usesMana() ? 0 : 12, clazz.usesMana() ? 10 : 0),
				new AbilityDefinition(clazz.id() + "_advanced", "Усиленный прием", "Активная способность с прокачкой. Использует ресурс: " + resource + ".", 4, clazz.usesMana() ? 0 : 24, clazz.usesMana() ? 25 : 0),
				new AbilityDefinition(clazz.id() + "_mastery", "Мастерство класса", "Поздняя сильная активная способность для 8+ уровня.", 8, clazz.usesMana() ? 0 : 45, clazz.usesMana() ? 45 : 0),
				new AbilityDefinition(clazz.id() + "_passive", "Пассивка класса", "Пассивная способность: работает всегда и не ставится в слот хотбара.", 1, 0, 0, true)
		);
	}
}
