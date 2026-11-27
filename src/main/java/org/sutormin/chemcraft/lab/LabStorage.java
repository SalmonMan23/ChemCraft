package org.sutormin.chemcraft.lab;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.sutormin.chemcraft.config.ChemConfig;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class LabStorage {
  private final JavaPlugin plugin;
  private final ChemConfig config;
  private final File file;
  private FileConfiguration data;

  public LabStorage(JavaPlugin plugin, ChemConfig config) {
    this.plugin = plugin;
    this.config = config;
    this.file = new File(plugin.getDataFolder(), "labs.yml");
  }

  public void load() {
    if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
    if (!file.exists()) {
      try { file.createNewFile(); } catch (IOException ignored) {}
    }
    data = YamlConfiguration.loadConfiguration(file);
  }

  public ItemStack[] loadContents(Location location) {
    ItemStack[] contents = new ItemStack[LabManager.INPUT_SLOTS];
    List<?> list = null;
    for (String path : paths(location)) {
      if (data.isConfigurationSection(path)) list = data.getList(path + ".contents");
      else if (data.contains(path)) list = data.getList(path);
      if (list != null) break;
    }
    if (list == null) return contents;
    for (int i = 0; i < Math.min(list.size(), contents.length); i++) {
      Object value = list.get(i);
      if (value instanceof ItemStack stack && !stack.getType().isAir()) contents[i] = stack.clone();
    }
    return contents;
  }

  public LabSettings loadSettings(Location location, LabType type) {
    LabDefaults defaults = config.labDefaults(type);
    LabSettings settings = LabSettings.defaults(type, defaults);
    ConfigurationSection section = null;
    for (String path : paths(location)) {
      section = data.getConfigurationSection(path);
      if (section != null) break;
    }
    if (section == null) return settings;
    if (section.contains("temperature")) settings.setTemperatureK(section.getDouble("temperature"));
    if (section.contains("voltage")) settings.setVoltageV(section.getDouble("voltage"));
    if (section.contains("pressure")) settings.setPressureAtm(section.getDouble("pressure"));
    settings.adjustTemperature(0, defaults);
    settings.adjustVoltage(0, defaults);
    return settings;
  }

  public void save(Location location, LabSettings settings, ItemStack[] contents) {
    String path = key(location);
    for (String legacy : paths(location)) {
      if (!legacy.equals(path)) data.set(legacy, null);
    }
    List<ItemStack> list = new ArrayList<>(contents.length);
    for (ItemStack stack : contents) {
      list.add(stack == null || stack.getType().isAir() ? null : stack.clone());
    }
    data.set(path + ".type", settings.type().name());
    data.set(path + ".temperature", settings.temperatureK());
    data.set(path + ".voltage", settings.voltageV());
    data.set(path + ".pressure", settings.pressureAtm());
    data.set(path + ".contents", list);
    save();
  }

  public void remove(Location location) {
    for (String path : paths(location)) data.set(path, null);
    save();
  }

  public void save() {
    try {
      data.save(file);
    } catch (IOException ex) {
      Bukkit.getLogger().warning("[ChemCraft] Failed to save labs.yml: " + ex.getMessage());
    }
  }

  private static String key(Location location) {
    return location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
  }

  private static String legacyKey(Location location) {
    return location.getWorld().getName() + ";" + location.getBlockX() + ";" + location.getBlockY() + ";" + location.getBlockZ();
  }

  private static String[] paths(Location location) {
    return new String[] { key(location), legacyKey(location) };
  }
}
