package wildmagic.classdata;

public record AbilityDefinition(String id, String title, String description, int unlockLevel, int cooldownSeconds, int manaCost, boolean passive) {
	public AbilityDefinition(String id, String title, String description, int unlockLevel, int cooldownSeconds, int manaCost) {
		this(id, title, description, unlockLevel, cooldownSeconds, manaCost, false);
	}

	public boolean isUnlocked(PlayerClassData data) {
		return data.hasClass() && data.level() >= unlockLevel;
	}

	public boolean canBeEquipped() {
		return !passive;
	}
}
