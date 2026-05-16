package wildmagic.client.state;

import wildmagic.classdata.PlayerClassData;

public final class ClientClassState {
	private static PlayerClassData data = PlayerClassData.EMPTY;
	private static final long[] cooldownEndsMillis = new long[PlayerClassData.ACTIVE_SLOT_COUNT];

	private ClientClassState() {
	}

	public static PlayerClassData data() {
		return data;
	}

	public static void update(PlayerClassData newData) {
		data = newData;
	}

	public static void update(String serializedData) {
		data = PlayerClassData.deserialize(serializedData);
		String[] parts = serializedData == null ? new String[0] : serializedData.split(";", -1);
		long now = System.currentTimeMillis();
		for (int slot = 0; slot < PlayerClassData.ACTIVE_SLOT_COUNT; slot++) {
			long ticks = 0L;
			int index = 8 + slot;
			if (index < parts.length) {
				try {
					ticks = Math.max(0L, Long.parseLong(parts[index]));
				} catch (NumberFormatException ignored) {
					ticks = 0L;
				}
			}
			cooldownEndsMillis[slot] = ticks <= 0L ? 0L : now + (ticks * 50L);
		}
	}

	public static int remainingCooldownSeconds(int slot) {
		if (slot < 0 || slot >= cooldownEndsMillis.length) {
			return 0;
		}

		long remainingMillis = cooldownEndsMillis[slot] - System.currentTimeMillis();
		return remainingMillis <= 0L ? 0 : (int) Math.ceil(remainingMillis / 1000.0D);
	}

	public static void setActiveAbility(int slot, String abilityId) {
		data = data.withActiveAbility(slot, abilityId);
	}
}
