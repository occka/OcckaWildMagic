package wildmagic;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public final class WildMagicItems {
    public static final Item MANA_POTION   = new ManaPotion(1);
    public static final Item MANA_POTION_2 = new ManaPotion(2);

    private WildMagicItems() {}

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "mana_potion"),
                MANA_POTION);
        Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "mana_potion_2"),
                MANA_POTION_2);
    }
}