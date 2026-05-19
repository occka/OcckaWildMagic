package wildmagic.classdata;

import java.util.Locale;
import java.util.Optional;

public record PlayerClassData(WildMagicClass selectedClass, int level, int exp, int mana, int maxMana, int maxManaBonus, String slotOneAbility, String slotTwoAbility, String slotThreeAbility) {
	public static final int ACTIVE_SLOT_COUNT = 3;
	public static final PlayerClassData EMPTY = new PlayerClassData(null, ClassProgression.MIN_LEVEL, 0, 0, 0, 0, "", "", "");

	public boolean hasClass() {
		return selectedClass != null;
	}

	public PlayerClassData withClass(WildMagicClass clazz) {
    return createForClass(clazz, ClassProgression.MIN_LEVEL);
}
	public static PlayerClassData createForClass(WildMagicClass clazz, int level) {
		int clampedLevel = ClassProgression.clampLevel(level);
		int maxMana = clazz.usesMana() ? ClassProgression.maxManaForLevel(clampedLevel) : 0;
		return new PlayerClassData(clazz, clampedLevel, 0, maxMana, maxMana, 0, "", "", "");
	}

	public PlayerClassData withExp(int newExp) {
		return new PlayerClassData(selectedClass, level, Math.max(0, newExp), mana, maxMana, maxManaBonus, slotOneAbility, slotTwoAbility, slotThreeAbility);
	}

	public int effectiveMaxMana() {
    return maxMana + maxManaBonus;
}

public PlayerClassData withMaxManaBonus(int bonus) {
    return new PlayerClassData(selectedClass, level, exp, Math.min(mana, maxMana + bonus), maxMana, bonus, slotOneAbility, slotTwoAbility, slotThreeAbility);
}

	public PlayerClassData withLevel(int newLevel) {
		int clamped = ClassProgression.clampLevel(newLevel);
		int newMaxMana = selectedClass != null && selectedClass.usesMana() ? ClassProgression.maxManaForLevel(clamped) : 0;
		int newMana = newMaxMana == 0 ? 0 : Math.min(newMaxMana, Math.max(mana, newMaxMana));
		return new PlayerClassData(selectedClass, clamped, exp, newMana, newMaxMana, maxManaBonus, slotOneAbility, slotTwoAbility, slotThreeAbility);
	}

	public PlayerClassData withMana(int newMana) {
    return new PlayerClassData(selectedClass, level, exp, Math.clamp(newMana, 0, effectiveMaxMana()), maxMana, maxManaBonus, slotOneAbility, slotTwoAbility, slotThreeAbility);
}

	public PlayerClassData consumeMana(int manaCost) {
		return withMana(mana - Math.max(0, manaCost));
	}

	public PlayerClassData withActiveAbility(int slot, String abilityId) {
		String normalizedAbilityId = abilityId == null ? "" : abilityId;
		String slotOne = removeDuplicate(slot, 0, normalizedAbilityId, slotOneAbility);
		String slotTwo = removeDuplicate(slot, 1, normalizedAbilityId, slotTwoAbility);
		String slotThree = removeDuplicate(slot, 2, normalizedAbilityId, slotThreeAbility);
		return switch (slot) {
			case 0 -> new PlayerClassData(selectedClass, level, exp, mana, maxMana, maxManaBonus, normalizedAbilityId, slotTwo, slotThree);
case 1 -> new PlayerClassData(selectedClass, level, exp, mana, maxMana, maxManaBonus, slotOne, normalizedAbilityId, slotThree);
case 2 -> new PlayerClassData(selectedClass, level, exp, mana, maxMana, maxManaBonus, slotOne, slotTwo, normalizedAbilityId);
			default -> this;
		};
	}

	private String removeDuplicate(int targetSlot, int currentSlot, String abilityId, String currentAbilityId) {
		if (targetSlot != currentSlot && !abilityId.isBlank() && abilityId.equals(currentAbilityId)) {
			return "";
		}

		return currentAbilityId;
	}

	public String activeAbility(int slot) {
		return switch (slot) {
			case 0 -> slotOneAbility;
			case 1 -> slotTwoAbility;
			case 2 -> slotThreeAbility;
			default -> "";
		};
	}

	public int expRequiredForNextLevel() {
		return ClassProgression.expRequiredForNextLevel(level);
	}

	public float expProgress() {
		int required = expRequiredForNextLevel();
		return required <= 0 ? 1.0F : Math.clamp(exp / (float) required, 0.0F, 1.0F);
	}

	public String serialize() {
    return (selectedClass == null ? "none" : selectedClass.id()) + ";" + level + ";" + exp + ";" + mana + ";" + maxMana + ";" + maxManaBonus + ";" + slotOneAbility + ";" + slotTwoAbility + ";" + slotThreeAbility;
}

	public static PlayerClassData deserialize(String raw) {
		if (raw == null || raw.isBlank()) {
			return EMPTY;
		}

		String[] parts = raw.split(";", -1);
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
			int level = ClassProgression.clampLevel(Integer.parseInt(parts[1]));
int maxMana = clazz.get().usesMana() ? ClassProgression.maxManaForLevel(level) : 0;
int maxManaBonus = parts.length > 5 ? Integer.parseInt(parts[5]) : 0;
int mana = Math.clamp(Integer.parseInt(parts[3]), 0, maxMana + maxManaBonus);
return new PlayerClassData(
    clazz.get(), level,
    Integer.parseInt(parts[2]),
    mana, maxMana, maxManaBonus,
    parts.length > 6 ? parts[6] : "",
    parts.length > 7 ? parts[7] : "",
    parts.length > 8 ? parts[8] : ""
);
		} catch (NumberFormatException ignored) {
			return EMPTY;
		}
	}
}
