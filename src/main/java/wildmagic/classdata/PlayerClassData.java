package wildmagic.classdata;

import java.util.Locale;
import java.util.Optional;

public record PlayerClassData(WildMagicClass selectedClass, int level, int exp, int mana, int maxMana) {
	public static final PlayerClassData EMPTY = new PlayerClassData(null, ClassProgression.MIN_LEVEL, 0, 0, 0);

	public boolean hasClass() {
		return selectedClass != null;
	}

	public PlayerClassData withClass(WildMagicClass clazz) {
		int maxMana = clazz.usesMana() ? ClassProgression.maxManaForLevel(ClassProgression.MIN_LEVEL) : 0;
		return new PlayerClassData(clazz, ClassProgression.MIN_LEVEL, 0, maxMana, maxMana);
	}

	public PlayerClassData withExp(int newExp) {
		return new PlayerClassData(selectedClass, level, Math.max(0, newExp), mana, maxMana);
	}

	public PlayerClassData withLevel(int newLevel) {
		int clamped = Math.clamp(newLevel, ClassProgression.MIN_LEVEL, ClassProgression.MAX_LEVEL);
		int newMaxMana = selectedClass != null && selectedClass.usesMana() ? ClassProgression.maxManaForLevel(clamped) : 0;
		int newMana = newMaxMana == 0 ? 0 : Math.min(newMaxMana, Math.max(mana, newMaxMana));
		return new PlayerClassData(selectedClass, clamped, exp, newMana, newMaxMana);
	}

	public int expRequiredForNextLevel() {
		return ClassProgression.expRequiredForNextLevel(level);
	}

	public float expProgress() {
		int required = expRequiredForNextLevel();
		return required <= 0 ? 1.0F : Math.clamp(exp / (float) required, 0.0F, 1.0F);
	}

	public String serialize() {
		return (selectedClass == null ? "none" : selectedClass.id()) + ";" + level + ";" + exp + ";" + mana + ";" + maxMana;
	}

	public static PlayerClassData deserialize(String raw) {
		if (raw == null || raw.isBlank()) {
			return EMPTY;
		}

		String[] parts = raw.split(";");
		if (parts.length < 5 || "none".equals(parts[0])) {
			return EMPTY;
		}

		Optional<WildMagicClass> clazz = WildMagicClass.VALUES.stream()
				.filter(value -> value.id().equals(parts[0].toLowerCase(Locale.ROOT)))
				.findFirst();
		if (clazz.isEmpty()) {
			return EMPTY;
		}

		try {
			return new PlayerClassData(clazz.get(), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]));
		} catch (NumberFormatException ignored) {
			return EMPTY;
		}
	}
}
