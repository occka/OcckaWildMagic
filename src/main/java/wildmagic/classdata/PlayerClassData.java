package wildmagic.classdata;

import java.util.Locale;
import java.util.Optional;

public record PlayerClassData(WildMagicClass selectedClass, int level, int exp, int mana, int maxMana, String slotOneAbility, String slotTwoAbility, String slotThreeAbility) {
	public static final int ACTIVE_SLOT_COUNT = 3;
	public static final PlayerClassData EMPTY = new PlayerClassData(null, ClassProgression.MIN_LEVEL, 0, 0, 0, "", "", "");

	public boolean hasClass() {
		return selectedClass != null;
	}

	public PlayerClassData withClass(WildMagicClass clazz) {
		return createForClass(clazz, ClassProgression.MIN_LEVEL);
	}

	public static PlayerClassData createForClass(WildMagicClass clazz, int level) {
		int clampedLevel = ClassProgression.clampLevel(level);
		int maxMana = clazz.usesMana() ? ClassProgression.maxManaForLevel(clampedLevel) : 0;
		return new PlayerClassData(clazz, clampedLevel, 0, maxMana, maxMana, "", "", "");
	}

	public PlayerClassData withExp(int newExp) {
		return new PlayerClassData(selectedClass, level, Math.max(0, newExp), mana, maxMana, slotOneAbility, slotTwoAbility, slotThreeAbility);
	}

	public PlayerClassData withLevel(int newLevel) {
		int clamped = ClassProgression.clampLevel(newLevel);
		int newMaxMana = selectedClass != null && selectedClass.usesMana() ? ClassProgression.maxManaForLevel(clamped) : 0;
		int newMana = newMaxMana == 0 ? 0 : Math.min(newMaxMana, Math.max(mana, newMaxMana));
		return new PlayerClassData(selectedClass, clamped, exp, newMana, newMaxMana, slotOneAbility, slotTwoAbility, slotThreeAbility);
	}

	public PlayerClassData withActiveAbility(int slot, String abilityId) {
		String normalizedAbilityId = abilityId == null ? "" : abilityId;
		String slotOne = removeDuplicate(slot, 0, normalizedAbilityId, slotOneAbility);
		String slotTwo = removeDuplicate(slot, 1, normalizedAbilityId, slotTwoAbility);
		String slotThree = removeDuplicate(slot, 2, normalizedAbilityId, slotThreeAbility);
		return switch (slot) {
			case 0 -> new PlayerClassData(selectedClass, level, exp, mana, maxMana, normalizedAbilityId, slotTwo, slotThree);
			case 1 -> new PlayerClassData(selectedClass, level, exp, mana, maxMana, slotOne, normalizedAbilityId, slotThree);
			case 2 -> new PlayerClassData(selectedClass, level, exp, mana, maxMana, slotOne, slotTwo, normalizedAbilityId);
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
		return (selectedClass == null ? "none" : selectedClass.id()) + ";" + level + ";" + exp + ";" + mana + ";" + maxMana + ";" + slotOneAbility + ";" + slotTwoAbility + ";" + slotThreeAbility;
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
			return new PlayerClassData(
					clazz.get(),
					Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]),
					Integer.parseInt(parts[3]),
					Integer.parseInt(parts[4]),
					parts.length > 5 ? parts[5] : "",
					parts.length > 6 ? parts[6] : "",
					parts.length > 7 ? parts[7] : ""
			);
		} catch (NumberFormatException ignored) {
			return EMPTY;
		}
	}
}
