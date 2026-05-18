package wildmagic;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class WildMagicItems {

    public static final Item MANA_POTION = register(
            "mana_potion",
            new ManaPotion(
                    1,
                    new Item.Properties()
                            .stacksTo(16)
                            .setId(itemKey("mana_potion"))
            )
    );

    public static final Item MANA_POTION_2 = register(
            "mana_potion_2",
            new ManaPotion(
                    2,
                    new Item.Properties()
                            .stacksTo(16)
                            .setId(itemKey("mana_potion_2"))
            )
    );

    private WildMagicItems() {}

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(
                BuiltInRegistries.ITEM.key(),
                Identifier.fromNamespaceAndPath(
                        OcckaWildMagic.MOD_ID,
                        name
                )
        );
    }

    private static Item register(String name, Item item) {
        return Registry.register(
                BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath(
                        OcckaWildMagic.MOD_ID,
                        name
                ),
                item
        );
    }

    public static void register() {
        OcckaWildMagic.LOGGER.info("Registering items");
    }
}