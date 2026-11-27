package org.sutormin.chemcraft.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ChemistryService;
import org.sutormin.chemcraft.chemistry.MoleculeInfo;
import org.sutormin.chemcraft.config.ChemConfig;
import org.sutormin.chemcraft.lab.LabType;
import org.sutormin.chemcraft.util.Keys;

import java.util.HashMap;
import java.util.List;

public final class ChemItems {
  public static final String PURIFIER = "purifier";
  public static final String ANALYZER = "analyzer";
  public static final String STORAGE = "storage";

  private final Keys keys;
  private final ChemistryService chemistry;
  private final ChemConfig config;

  public ChemItems(Keys keys, ChemistryService chemistry, ChemConfig config) {
    this.keys = keys;
    this.chemistry = chemistry;
    this.config = config;
  }

  public Keys keys() {
    return keys;
  }

  public ItemStack createChemItem(String smiles, double moles, double density) throws InvalidSmilesException {
    MoleculeInfo info = chemistry.analyze(smiles);
    double resolvedDensity = resolveDensity(smiles, density, info);
    String formula = info.formula();
    String title = config.displayName(smiles, formula);
    ItemStack stack = new ItemStack(Material.PAPER, 1);
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(Component.text(title, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
    double grams = moles * info.molarMass();
    meta.lore(List.of(Component.text(info.formulaSubscript(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), Component.text(smiles, NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), Component.text(String.format(java.util.Locale.ROOT, "%.4f mol  |  %.4f g  |  %.3f g/cm³", moles, grams, resolvedDensity), NamedTextColor.DARK_GREEN).decoration(TextDecoration.ITALIC, false), Component.text("ChemCraft", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, true)));
    meta.getPersistentDataContainer().set(keys.smiles, PersistentDataType.STRING, smiles);
    meta.getPersistentDataContainer().set(keys.moles, PersistentDataType.DOUBLE, moles);
    meta.getPersistentDataContainer().set(keys.density, PersistentDataType.DOUBLE, resolvedDensity);
    stack.setItemMeta(meta);
    return stack;
  }

  public ItemStack createChemItem(String smiles, double moles) throws InvalidSmilesException {
    return createChemItem(smiles, moles, 0.0);
  }

  public double resolveDensity(String smiles, double density, MoleculeInfo info) {
    if (density > 0.0) return density;
    double mapped = config.densityOf(smiles);
    if (mapped > 0.0 && config.hasDensity(smiles)) return mapped;
    return chemistry.estimateDensity(info);
  }

  public ItemStack createPurifier() {
    return createTool(Material.END_PORTAL_FRAME, PURIFIER, "ChemCraft Purifier", "Hold a vanilla or ChemCraft item and right-click this block.");
  }

  public ItemStack createLab(LabType type) {
    return createTool(type.material(), type.toolId(), "ChemCraft " + type.displayName(), "Right-click to open. Top two rows store chemicals; bottom row is lab settings.");
  }

  public ItemStack createAnalyzer() {
    return createTool(Material.JUKEBOX, ANALYZER, "ChemCraft Analyzer", "Hold a ChemCraft item and right-click this block.");
  }

  public ItemStack createStorage() {
    return createTool(Material.BARREL, STORAGE, "ChemCraft Storage", "Chemicals only. Same chemicals merge. Click a chemical, then type grams in chat to withdraw.");
  }

  private ItemStack createTool(Material material, String type, String title, String usage) {
    ItemStack stack = new ItemStack(material, 1);
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(Component.text(title, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
    meta.lore(List.of(Component.text(usage, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), Component.text("ChemCraft", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, true)));
    meta.getPersistentDataContainer().set(keys.toolType, PersistentDataType.STRING, type);
    stack.setItemMeta(meta);
    return stack;
  }

  public String getToolType(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return null;
    return stack.getItemMeta().getPersistentDataContainer().get(keys.toolType, PersistentDataType.STRING);
  }

  public boolean isChemItem(ItemStack stack) {
    if (stack == null || !stack.hasItemMeta()) return false;
    return stack.getItemMeta().getPersistentDataContainer().has(keys.smiles, PersistentDataType.STRING) && stack.getItemMeta().getPersistentDataContainer().has(keys.moles, PersistentDataType.DOUBLE);
  }

  public String getSmiles(ItemStack stack) {
    return stack.getItemMeta().getPersistentDataContainer().get(keys.smiles, PersistentDataType.STRING);
  }

  public double getMoles(ItemStack stack) {
    Double value = stack.getItemMeta().getPersistentDataContainer().get(keys.moles, PersistentDataType.DOUBLE);
    return value == null ? 0.0 : value;
  }

  public double getDensity(ItemStack stack) {
    Double value = stack.getItemMeta().getPersistentDataContainer().get(keys.density, PersistentDataType.DOUBLE);
    if (value != null && value > 0.0) return value;
    try {
      String smiles = getSmiles(stack);
      return resolveDensity(smiles, 0.0, chemistry.analyze(smiles));
    } catch (InvalidSmilesException ex) {
      return 1.0;
    }
  }

  public void give(Player player, ItemStack stack) {
    HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
    overflow.values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
  }
}
