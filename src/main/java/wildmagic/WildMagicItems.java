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
                            .component(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
    new net.minecraft.world.item.alchemy.PotionContents(
        java.util.Optional.empty(),
        java.util.Optional.of(0x5B2FCC),
        java.util.List.of(),
        java.util.Optional.empty()
    ))
            )
    );

    public static final Item MANA_POTION_2 = register(
            "mana_potion_2",
            new ManaPotion(
                    2,
                    new Item.Properties()
                            .stacksTo(16)
                            .setId(itemKey("mana_potion_2"))
                            .component(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
    new net.minecraft.world.item.alchemy.PotionContents(
        java.util.Optional.empty(),
        java.util.Optional.of(0x2A5FFF),
        java.util.List.of(),
        java.util.Optional.empty()
    ))
            )
    );

    public static final Item DRAGON_POTION_FIRE = register("dragon_potion_fire",
    new SorcererElementPotion(wildmagic.classdata.SorcererElement.FIRE,
        new Item.Properties().stacksTo(1).setId(itemKey("dragon_potion_fire"))
            .component(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(
                    java.util.Optional.empty(), java.util.Optional.of(0xFF4500),
                    java.util.List.of(), java.util.Optional.empty()))));

public static final Item DRAGON_POTION_ICE = register("dragon_potion_ice",
    new SorcererElementPotion(wildmagic.classdata.SorcererElement.ICE,
        new Item.Properties().stacksTo(1).setId(itemKey("dragon_potion_ice"))
            .component(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(
                    java.util.Optional.empty(), java.util.Optional.of(0xAAEEFF),
                    java.util.List.of(), java.util.Optional.empty()))));

public static final Item DRAGON_POTION_LIGHTNING = register("dragon_potion_lightning",
    new SorcererElementPotion(wildmagic.classdata.SorcererElement.LIGHTNING,
        new Item.Properties().stacksTo(1).setId(itemKey("dragon_potion_lightning"))
            .component(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(
                    java.util.Optional.empty(), java.util.Optional.of(0x4444FF),
                    java.util.List.of(), java.util.Optional.empty()))));

public static final Item DRAGON_POTION_POISON = register("dragon_potion_poison",
    new SorcererElementPotion(wildmagic.classdata.SorcererElement.POISON,
        new Item.Properties().stacksTo(1).setId(itemKey("dragon_potion_poison"))
            .component(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(
                    java.util.Optional.empty(), java.util.Optional.of(0x1A7A1A),
                    java.util.List.of(), java.util.Optional.empty()))));

public static final Item DRAGON_POTION_THUNDER = register("dragon_potion_thunder",
    new SorcererElementPotion(wildmagic.classdata.SorcererElement.THUNDER,
        new Item.Properties().stacksTo(1).setId(itemKey("dragon_potion_thunder"))
            .component(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                new net.minecraft.world.item.alchemy.PotionContents(
                    java.util.Optional.empty(), java.util.Optional.of(0xCCCCCC),
                    java.util.List.of(), java.util.Optional.empty()))));

    private WildMagicItems() {}

    private static ResourceKey<Item> itemKey(String name) {
        return ResourceKey.create(
                BuiltInRegistries.ITEM.key(),
                Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, name)
        );
    }

    private static Item register(String name, Item item) {
        return Registry.register(
                BuiltInRegistries.ITEM,
                Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, name),
                item
        );
    }

    public static void register() {
        OcckaWildMagic.LOGGER.info("Registering items");
    }
}