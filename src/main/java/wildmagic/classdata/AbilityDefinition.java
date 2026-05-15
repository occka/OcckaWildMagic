package wildmagic.classdata;

public record AbilityDefinition(String id, String title, String description, int unlockLevel, int cooldownSeconds, int manaCost) {
	public boolean isUnlocked(PlayerClassData data) {
		return data.hasClass() && data.level() >= unlockLevel;
	}
}
