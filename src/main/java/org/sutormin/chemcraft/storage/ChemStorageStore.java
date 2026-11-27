package org.sutormin.chemcraft.storage;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class ChemStorageStore {
  private final JavaPlugin plugin;
  private final File file;
  private FileConfiguration data;

  public ChemStorageStore(JavaPlugin plugin) {
    this.plugin = plugin;
    this.file = new File(plugin.getDataFolder(), "storage.yml");
  }

  public void load() {
    if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
    if (!file.exists()) {
      try { file.createNewFile(); } catch (IOException ignored) {}
    }
    data = YamlConfiguration.loadConfiguration(file);
  }

  public ItemStack[] loadContents(Location location, int size) {
    ItemStack[] contents = new ItemStack[size];
    List<?> list = data.getList(key(location));
    if (list == null) return contents;
    for (int i = 0; i < Math.min(list.size(), size); i++) {
      Object value = list.get(i);
      if (value instanceof ItemStack stack && !stack.getType().isAir()) contents[i] = stack.clone();
    }
    return contents;
  }

  public void saveContents(Location location, ItemStack[] contents) {
    List<ItemStack> list = new ArrayList<>(contents.length);
    for (ItemStack stack : contents) {
      list.add(stack == null || stack.getType().isAir() ? null : stack.clone());
    }
    data.set(key(location), list);
    save();
  }

  public void remove(Location location) {
    data.set(key(location), null);
    save();
  }

  public void save() {
    try {
      data.save(file);
    } catch (IOException ex) {
      Bukkit.getLogger().warning("[ChemCraft] Failed to save storage.yml: " + ex.getMessage());
    }
  }

  private static String key(Location location) {
    return location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
  }
}
