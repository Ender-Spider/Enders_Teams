package io.github.loadingerror303.endersteams;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;

public enum TeamIcon {
    COAL("Coal", Material.COAL, NamedTextColor.DARK_GRAY),
    COPPER("Copper", Material.COPPER_INGOT, NamedTextColor.GOLD),
    IRON("Iron", Material.IRON_INGOT, NamedTextColor.WHITE),
    GOLD("Gold", Material.GOLD_INGOT, NamedTextColor.YELLOW),
    REDSTONE("Redstone", Material.REDSTONE, NamedTextColor.RED),
    LAPIS("Lapis", Material.LAPIS_LAZULI, NamedTextColor.BLUE),
    EMERALD("Emerald", Material.EMERALD, NamedTextColor.GREEN),
    DIAMOND("Diamond", Material.DIAMOND, NamedTextColor.AQUA),
    QUARTZ("Quartz", Material.QUARTZ, NamedTextColor.WHITE),
    ANCIENT_DEBRIS("Ancient Debris", Material.ANCIENT_DEBRIS, NamedTextColor.DARK_RED),
    AMETHYST("Amethyst", Material.AMETHYST_SHARD, NamedTextColor.LIGHT_PURPLE);

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
}
