package io.github.loadingerror303.endersteams;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum TeamIcon {
    COAL("Coal Block", Material.COAL_BLOCK, NamedTextColor.DARK_GRAY),
    COPPER("Block of Copper", Material.COPPER_BLOCK, NamedTextColor.GOLD),
    IRON("Block of Iron", Material.IRON_BLOCK, NamedTextColor.WHITE),
    GOLD("Block of Gold", Material.GOLD_BLOCK, NamedTextColor.YELLOW),
    REDSTONE("Block of Redstone", Material.REDSTONE_BLOCK, NamedTextColor.RED),
    LAPIS("Block of Lapis Lazuli", Material.LAPIS_BLOCK, NamedTextColor.BLUE),
    EMERALD("Block of Emerald", Material.EMERALD_BLOCK, NamedTextColor.GREEN),
    DIAMOND("Block of Diamond", Material.DIAMOND_BLOCK, NamedTextColor.AQUA),
    QUARTZ("Block of Quartz", Material.QUARTZ_BLOCK, NamedTextColor.WHITE),
    ANCIENT_DEBRIS("Ancient Debris", Material.ANCIENT_DEBRIS, NamedTextColor.DARK_RED),
    AMETHYST("Block of Amethyst", Material.AMETHYST_BLOCK, NamedTextColor.LIGHT_PURPLE),
    PRISMARINE("Prismarine", Material.PRISMARINE, NamedTextColor.DARK_AQUA),
    OBSIDIAN("Obsidian", Material.OBSIDIAN, NamedTextColor.DARK_PURPLE),
    CRYING_OBSIDIAN("Crying Obsidian", Material.CRYING_OBSIDIAN, NamedTextColor.LIGHT_PURPLE),
    GLOWSTONE("Glowstone", Material.GLOWSTONE, NamedTextColor.YELLOW),
    DEEPSLATE("Deepslate", Material.DEEPSLATE, NamedTextColor.DARK_GRAY),
    SCULK("Sculk", Material.SCULK, NamedTextColor.DARK_AQUA),
    GILDED_BLACKSTONE("Gilded Blackstone", Material.GILDED_BLACKSTONE, NamedTextColor.GOLD),
    SEA_LANTERN("Sea Lantern", Material.SEA_LANTERN, NamedTextColor.AQUA),
    MAGMA("Magma Block", Material.MAGMA_BLOCK, NamedTextColor.GOLD);

    private final String label;
    private final Material material;
    private final NamedTextColor color;

    TeamIcon(String label, Material material, NamedTextColor color) {
        this.label = label;
        this.material = material;
        this.color = color;
    }

    public String label() { return label; }
    public Material material() { return material; }
    public NamedTextColor color() { return color; }

    // Keep retired enum names readable for teams saved by earlier versions.
    public static TeamIcon[] selectableValues() {
        return java.util.Arrays.stream(values())
                .filter(icon -> icon != OBSIDIAN && icon != MAGMA)
                .toArray(TeamIcon[]::new);
    }
}
