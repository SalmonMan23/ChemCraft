package org.sutormin.chemcraft.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ChemistryService;
import org.sutormin.chemcraft.lab.LabDefaults;
import org.sutormin.chemcraft.lab.LabType;
import org.sutormin.chemcraft.util.Chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ChemConfig {
  public record Component(String smiles, double density, double grams) {}

  private final JavaPlugin plugin;
  private final ChemistryService chemistry;
  private final Map<Material, List<Component>> composition = new EnumMap<>(Material.class);
  private final Map<Material, Integer> blockMultipliers = new EnumMap<>(Material.class);
  private final Map<String, String> names = new HashMap<>();
  private final Map<String, Double> densities = new HashMap<>();
  private final Map<LabType, LabDefaults> labDefaults = new EnumMap<>(LabType.class);

  public ChemConfig(JavaPlugin plugin, ChemistryService chemistry) {
    this.plugin = plugin;
    this.chemistry = chemistry;
  }

  public void load() {
    composition.clear();
    blockMultipliers.clear();
    names.clear();
    densities.clear();
    labDefaults.clear();
    plugin.saveDefaultConfig();
    plugin.reloadConfig();
    FileConfiguration cfg = plugin.getConfig();
    loadComposition(cfg);
    loadNames(cfg);
    loadBlockMultipliers();
    loadLabs(cfg);
    Chat.info(plugin.getServer().getConsoleSender(), "Loaded " + composition.size() + " compositions and " + names.size() + " names.");
  }

  private void loadComposition(FileConfiguration cfg) {
    ConfigurationSection section = cfg.getConfigurationSection("composition");
    if (section == null) return;
    for (String key : section.getKeys(false)) {
      Material material;
      try {
        material = Material.valueOf(key.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        plugin.getLogger().warning("Unknown material in composition: " + key);
        continue;
      }
      List<Map<?, ?>> rawList = section.getMapList(key);
      List<Component> components = new ArrayList<>();
      for (Map<?, ?> raw : rawList) {
        Object smilesObj = raw.get("smiles");
        Object densityObj = raw.get("density");
        Object gramsObj = raw.get("grams");
        if (smilesObj == null || densityObj == null || gramsObj == null) {
          plugin.getLogger().warning("Incomplete composition entry for " + key);
          continue;
        }
        String smiles = String.valueOf(smilesObj);
        double density = Double.parseDouble(String.valueOf(densityObj));
        double grams = Double.parseDouble(String.valueOf(gramsObj));
        try {
          chemistry.analyze(smiles);
        } catch (InvalidSmilesException ex) {
          plugin.getLogger().warning("Invalid SMILES for " + key + ": " + smiles);
          continue;
        }
        components.add(new Component(smiles, density, grams));
        densities.putIfAbsent(smiles, density);
      }
      if (!components.isEmpty()) composition.put(material, List.copyOf(components));
    }
  }

  private void loadNames(FileConfiguration cfg) {
    ConfigurationSection section = cfg.getConfigurationSection("names");
    if (section == null) return;
    for (String smiles : section.getKeys(false)) {
      String name = section.getString(smiles);
      if (name != null && !name.isBlank()) names.put(smiles, name);
    }
  }

  private void loadBlockMultipliers() {
    putBlocks(9, "COAL_BLOCK", "COPPER_BLOCK", "DIAMOND_BLOCK", "EMERALD_BLOCK", "GOLD_BLOCK", "IRON_BLOCK", "LAPIS_BLOCK", "NETHERITE_BLOCK", "RAW_COPPER_BLOCK", "RAW_GOLD_BLOCK", "RAW_IRON_BLOCK", "REDSTONE_BLOCK", "RESIN_BLOCK", "BAMBOO_BLOCK", "BONE_BLOCK", "DRIED_KELP_BLOCK", "HAY_BLOCK", "MELON", "NETHER_WART_BLOCK", "PRISMARINE_BRICKS", "SLIME_BLOCK");
    putBlocks(4, "QUARTZ_BLOCK", "BRICKS", "CLAY", "DRIPSTONE_BLOCK", "GLOWSTONE", "MAGMA_BLOCK", "SANDSTONE", "SNOW_BLOCK", "WHITE_WOOL");
    putBlocks(1, "PURPUR_BLOCK");
  }

  private void putBlocks(int multiplier, String... names) {
    for (String name : names) {
      try {
        blockMultipliers.put(Material.valueOf(name), multiplier);
      } catch (IllegalArgumentException ignored) {}
    }
  }

  public List<Component> componentsOf(Material material) {
    return composition.getOrDefault(material, List.of());
  }

  public Material baseMaterial(Material material) {
    return switch (material) {
      case COAL_BLOCK -> Material.COAL;
      case COPPER_BLOCK -> Material.COPPER_INGOT;
      case DIAMOND_BLOCK -> Material.DIAMOND;
      case EMERALD_BLOCK -> Material.EMERALD;
      case GOLD_BLOCK -> Material.GOLD_INGOT;
      case IRON_BLOCK -> Material.IRON_INGOT;
      case LAPIS_BLOCK -> Material.LAPIS_LAZULI;
      case NETHERITE_BLOCK -> Material.NETHERITE_INGOT;
      case RAW_COPPER_BLOCK -> Material.RAW_COPPER;
      case RAW_GOLD_BLOCK -> Material.RAW_GOLD;
      case RAW_IRON_BLOCK -> Material.RAW_IRON;
      case REDSTONE_BLOCK -> Material.REDSTONE;
      case RESIN_BLOCK -> Material.RESIN_CLUMP;
      case BAMBOO_BLOCK -> Material.BAMBOO;
      case BONE_BLOCK -> Material.BONE_MEAL;
      case DRIED_KELP_BLOCK -> Material.DRIED_KELP;
      case HAY_BLOCK -> Material.WHEAT;
      case MELON -> Material.MELON_SLICE;
      case NETHER_WART_BLOCK -> Material.NETHER_WART;
      case PRISMARINE_BRICKS -> Material.PRISMARINE_SHARD;
      case SLIME_BLOCK -> Material.SLIME_BALL;
      case QUARTZ_BLOCK -> Material.QUARTZ;
      case BRICKS -> Material.BRICK;
      case CLAY -> Material.CLAY_BALL;
      case DRIPSTONE_BLOCK -> Material.POINTED_DRIPSTONE;
      case GLOWSTONE -> Material.GLOWSTONE_DUST;
      case MAGMA_BLOCK -> Material.MAGMA_CREAM;
      case SANDSTONE -> Material.SAND;
      case SNOW_BLOCK -> Material.SNOWBALL;
      case WHITE_WOOL -> Material.STRING;
      case PURPUR_BLOCK -> Material.POPPED_CHORUS_FRUIT;
      default -> material;
    };
  }

  public int multiplierOf(Material material) {
    if (blockMultipliers.containsKey(material)) return blockMultipliers.get(material);
    return composition.containsKey(material) ? 1 : 0;
  }

  public boolean isMappedBlock(Material material) {
    return blockMultipliers.containsKey(material);
  }

  public String displayName(String smiles, String formula) {
    return names.getOrDefault(smiles, formula);
  }

  public double densityOf(String smiles) {
    return densities.getOrDefault(smiles, 0.0);
  }

  public boolean hasDensity(String smiles) {
    return densities.containsKey(smiles);
  }

  public Map<String, String> namesView() {
    return Collections.unmodifiableMap(names);
  }

  public Map<Material, List<Component>> compositionView() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(composition));
  }

  public LabDefaults labDefaults(LabType type) {
    return labDefaults.getOrDefault(type, LabDefaults.forType(type));
  }

  private void loadLabs(FileConfiguration cfg) {
    for (LabType type : LabType.values()) labDefaults.put(type, LabDefaults.forType(type));
    ConfigurationSection root = cfg.getConfigurationSection("labs");
    if (root == null) return;
    for (LabType type : LabType.values()) {
      ConfigurationSection section = root.getConfigurationSection(type.name().toLowerCase(Locale.ROOT));
      if (section == null) section = root.getConfigurationSection(type.toolId());
      if (section == null) continue;
      LabDefaults base = LabDefaults.forType(type);
      double defTemp = section.getDouble("default-temperature", base.defaultTemperatureK());
      double minTemp = section.getDouble("min-temperature", base.minTemperatureK());
      double maxTemp = section.getDouble("max-temperature", base.maxTemperatureK());
      List<Double> tempSteps = readDoubleList(section, "temperature-steps", base.temperatureSteps());
      double defVolt = section.getDouble("default-voltage", base.defaultVoltageV());
      double minVolt = section.getDouble("min-voltage", base.minVoltageV());
      double maxVolt = section.getDouble("max-voltage", base.maxVoltageV());
      List<Double> voltSteps = readDoubleList(section, "voltage-steps", base.voltageSteps());
      double pressure = section.getDouble("default-pressure", base.defaultPressureAtm());
      boolean tempControl = section.getBoolean("temperature-control", base.temperatureControl());
      boolean voltControl = section.getBoolean("voltage-control", base.voltageControl());
      labDefaults.put(type, new LabDefaults(defTemp, minTemp, maxTemp, tempSteps, defVolt, minVolt, maxVolt, voltSteps, pressure, tempControl, voltControl));
    }
  }

  private static List<Double> readDoubleList(ConfigurationSection section, String path, List<Double> fallback) {
    List<?> raw = section.getList(path);
    if (raw == null || raw.isEmpty()) return fallback;
    List<Double> out = new ArrayList<>();
    for (Object value : raw) {
      try { out.add(Double.parseDouble(String.valueOf(value))); } catch (NumberFormatException ignored) {}
    }
    return out.isEmpty() ? fallback : List.copyOf(out);
  }
}
