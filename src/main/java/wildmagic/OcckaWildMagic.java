package wildmagic;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import wildmagic.network.WildMagicNetworking;
import wildmagic.server.WildMagicCommands;
import wildmagic.server.WildMagicServerState;

public class OcckaWildMagic implements ModInitializer {
	public static final String MOD_ID = "occkawildmagic";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		WildMagicNetworking.registerPayloadTypes();
		WildMagicNetworking.registerServerReceivers();
		WildMagicServerState.registerEvents();
		WildMagicCommands.register();
		LOGGER.info("Occka Wild Magic class framework initialized");
	}
}
