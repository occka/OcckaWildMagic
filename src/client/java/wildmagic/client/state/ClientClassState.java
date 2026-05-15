package wildmagic.client.state;

import wildmagic.classdata.PlayerClassData;

public final class ClientClassState {
	private static PlayerClassData data = PlayerClassData.EMPTY;

	private ClientClassState() {
	}

	public static PlayerClassData data() {
		return data;
	}

	public static void update(PlayerClassData newData) {
		data = newData;
	}

	public static void setActiveAbility(int slot, String abilityId) {
		data = data.withActiveAbility(slot, abilityId);
	}
}
