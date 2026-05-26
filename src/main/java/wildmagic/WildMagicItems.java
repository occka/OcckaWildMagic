package wildmagic;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;

public final class WildMagicItems {

    public static final Item MANA_POTION = register("mana_potion", new ManaPotion(1, potionProps("mana_potion", 0x5B2FCC, 16)));
    public static final Item MANA_POTION_2 = register("mana_potion_2", new ManaPotion(2, potionProps("mana_potion_2", 0x2A5FFF, 16)));
    public static final Item DRAGON_POTION_FIRE = register("dragon_potion_fire", new SorcererElementPotion(wildmagic.classdata.SorcererElement.FIRE, potionProps("dragon_potion_fire", 0xFF4500, 1)));
    public static final Item DRAGON_POTION_ICE = register("dragon_potion_ice", new SorcererElementPotion(wildmagic.classdata.SorcererElement.ICE, potionProps("dragon_potion_ice", 0xAAEEFF, 1)));
    public static final Item DRAGON_POTION_LIGHTNING = register("dragon_potion_lightning", new SorcererElementPotion(wildmagic.classdata.SorcererElement.LIGHTNING, potionProps("dragon_potion_lightning", 0x4444FF, 1)));
    public static final Item DRAGON_POTION_POISON = register("dragon_potion_poison", new SorcererElementPotion(wildmagic.classdata.SorcererElement.POISON, potionProps("dragon_potion_poison", 0x1A7A1A, 1)));
    public static final Item DRAGON_POTION_THUNDER = register("dragon_potion_thunder", new SorcererElementPotion(wildmagic.classdata.SorcererElement.THUNDER, potionProps("dragon_potion_thunder", 0xCCCCCC, 1)));

    public static final Item WAND_WOODEN = register("wand_wooden", new MagicWandItem(itemProps("wand_wooden")));
    public static final Item WAND_COPPER = register("wand_copper", new MagicWandItem(itemProps("wand_copper")));
    public static final Item WAND_BONE = register("wand_bone", new MagicWandItem(itemProps("wand_bone")));
    public static final Item WAND_HELL = register("wand_hell", new MagicWandItem(itemProps("wand_hell").fireResistant()));
    public static final Item WAND_ENDER = register("wand_ender", new MagicWandItem(itemProps("wand_ender")));
    public static final Item STAFF_WOODEN = register("staff_wooden", new MagicStaffItem(itemProps("staff_wooden")));
    public static final Item STAFF_STONE = register("staff_stone", new MagicStaffItem(itemProps("staff_stone")));
    public static final Item STAFF_COPPER = register("staff_copper", new MagicStaffItem(itemProps("staff_copper")));
    public static final Item STAFF_FIRE = register("staff_fire", new MagicStaffItem(itemProps("staff_fire").fireResistant()));
    public static final Item STAFF_VOID = register("staff_void", new MagicStaffItem(itemProps("staff_void")));
    public static final Item COPPER_HAMMER = register("copper_hammer", new Item(itemProps("copper_hammer")));

    public static final CreativeModeTab MOD_ITEMS_TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, "mod_items"),
            CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.occkawildmagic.mod_items"))
                    .icon(() -> WAND_WOODEN.getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(MANA_POTION); output.accept(MANA_POTION_2);
                        output.accept(DRAGON_POTION_FIRE); output.accept(DRAGON_POTION_ICE); output.accept(DRAGON_POTION_LIGHTNING); output.accept(DRAGON_POTION_POISON); output.accept(DRAGON_POTION_THUNDER);
                        output.accept(WAND_WOODEN); output.accept(WAND_COPPER); output.accept(WAND_BONE); output.accept(WAND_HELL); output.accept(WAND_ENDER);
                        output.accept(STAFF_WOODEN); output.accept(STAFF_STONE); output.accept(STAFF_COPPER); output.accept(STAFF_FIRE); output.accept(STAFF_VOID);
                        output.accept(COPPER_HAMMER);
                    }).build());

    private static Item.Properties potionProps(String name, int color, int stack) { return new Item.Properties().stacksTo(stack).setId(itemKey(name)).component(DataComponents.POTION_CONTENTS, new net.minecraft.world.item.alchemy.PotionContents(java.util.Optional.empty(), java.util.Optional.of(color), java.util.List.of(), java.util.Optional.empty())); }
    private static Item.Properties itemProps(String name) { return new Item.Properties().stacksTo(1).setId(itemKey(name)); }
    private static ResourceKey<Item> itemKey(String name) { return ResourceKey.create(BuiltInRegistries.ITEM.key(), Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, name)); }
    private static Item register(String name, Item item) { return Registry.register(BuiltInRegistries.ITEM, Identifier.fromNamespaceAndPath(OcckaWildMagic.MOD_ID, name), item); }
    public static void register() { OcckaWildMagic.LOGGER.info("Registering items"); }
}
