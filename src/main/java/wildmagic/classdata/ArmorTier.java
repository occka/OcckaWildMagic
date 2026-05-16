package wildmagic.classdata;

public enum ArmorTier {
    NONE,
    LIGHT,   // кожа, цепь
    MEDIUM,  // железо, медь, золото
    HEAVY;   // алмаз, незерит

    public static ArmorTier of(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) return NONE;
        String id = stack.getItem().toString();
        if (id.contains("netherite") || id.contains("diamond")) return HEAVY;
        if (id.contains("iron") || id.contains("copper") || id.contains("gold")) return MEDIUM;
        if (id.contains("leather") || id.contains("chain")) return LIGHT;
        return NONE;
    }
}