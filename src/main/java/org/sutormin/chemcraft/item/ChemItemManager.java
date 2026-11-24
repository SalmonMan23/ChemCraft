package org.sutormin.chemcraft.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChemItemManager {
  private final NamespacedKey toolTypeKey;
  private final NamespacedKey smilesKey;
  private final NamespacedKey molesKey;

  private static final Map<Material, String> VANILLA_TO_SMILES = Map.ofEntries(
      Map.entry(Material.WATER_BUCKET, "O"),
      Map.entry(Material.ICE, "O"),
      Map.entry(Material.PACKED_ICE, "O"),
      Map.entry(Material.BLUE_ICE, "O"),
      Map.entry(Material.COAL, "C"),
      Map.entry(Material.CHARCOAL, "C"),
      Map.entry(Material.DIAMOND, "C"),
      Map.entry(Material.IRON_INGOT, "[Fe]"),
      Map.entry(Material.COPPER_INGOT, "[Cu]"),
      Map.entry(Material.GOLD_INGOT, "[Au]"),
      Map.entry(Material.SUGAR, "C(C(C(C(C(O)O)O)O)O)O"),
      Map.entry(Material.REDSTONE, "[As]"),
      Map.entry(Material.SULFUR, "S")
  );

  public ChemItemManager(Plugin plugin) {
    this.toolTypeKey = new NamespacedKey(plugin, "tool_type");
    this.smilesKey = new NamespacedKey(plugin, "smiles");
    this.molesKey = new NamespacedKey(plugin, "moles");
  }

  public NamespacedKey getToolTypeKey() {
    return toolTypeKey;
  }

  public ItemStack createChemItem(String smiles, double moles, int count) {
    ItemStack stack = new ItemStack(Material.PAPER, count);
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(Component.text(smiles, NamedTextColor.WHITE));
    meta.lore(List.of(Component.text("Moles: " + moles, NamedTextColor.GRAY)));
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    pdc.set(smilesKey, PersistentDataType.STRING, smiles);
    pdc.set(molesKey, PersistentDataType.DOUBLE, moles);
    stack.setItemMeta(meta);
    return stack;
  }

  public ItemStack createPurifier() {
    return createTool(
        Material.SHULKER_BOX,
        "purifier",
        "ChemCraft Purifier",
        NamedTextColor.AQUA,
        "Right click with vanilla item."
    );
  }

  public ItemStack createLab() {
    return createTool(
        Material.SMOKER,
        "lab",
        "ChemCraft Lab",
        NamedTextColor.AQUA,
        "Right click to open lab gui."
    );
  }

  public ItemStack createAnalyzer() {
    return createTool(
        Material.LECTERN,
        "analyzer",
        "ChemCraft Analyzer",
        NamedTextColor.AQUA,
        "Right click with ChemCraft item."
    );
  }

  private ItemStack createTool(Material mat, String type, String title, NamedTextColor color, String loreLine) {
    ItemStack stack = new ItemStack(mat, 1);
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(Component.text(title, color));
    meta.lore(List.of(Component.text(loreLine, NamedTextColor.GRAY)));
    meta.getPersistentDataContainer().set(toolTypeKey, PersistentDataType.STRING, type);
    stack.setItemMeta(meta);
    return stack;
  }

  public String getToolType(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) {
      return null;
    }
    return stack.getItemMeta().getPersistentDataContainer().get(toolTypeKey, PersistentDataType.STRING);
  }

  public boolean isChemItem(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) {
      return false;
    }
    PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
    return pdc.has(smilesKey, PersistentDataType.STRING) && pdc.has(molesKey, PersistentDataType.DOUBLE);
  }

  public String getSmiles(ItemStack stack) {
    return stack.getItemMeta().getPersistentDataContainer().get(smilesKey, PersistentDataType.STRING);
  }

  public double getMoles(ItemStack stack) {
    Double value = stack.getItemMeta().getPersistentDataContainer().get(molesKey, PersistentDataType.DOUBLE);
    return value != null ? value : 0.0;
  }

  public String resolveVanillaSmiles(Material material) {
    return VANILLA_TO_SMILES.get(material);
  }

  public String resolveVanillaMoles(Material material) {
    return VANILLA_TO_MOLES.getOrDefault(material, 1.0);
  }

  public void giveToPlayer(Player player, ItemStack stack) {
    HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
    overflow.values().forEach(rem -> player.getWorld().dropItemNaturally(player.getLocation(), rem));
  }
}
