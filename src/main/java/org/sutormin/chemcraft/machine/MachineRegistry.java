package org.sutormin.chemcraft.machine;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.sutormin.chemcraft.util.Keys;

import java.io.File;
import java.io.IOException;

public final class MachineRegistry {
  private final JavaPlugin plugin;
  private final Keys keys;
  private final File file;
  private FileConfiguration data;

  public MachineRegistry(JavaPlugin plugin, Keys keys) {
    this.plugin = plugin;
    this.keys = keys;
    this.file = new File(plugin.getDataFolder(), "machines.yml");
  }

  public void load() {
    if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
    if (!file.exists()) {
      try { file.createNewFile(); } catch (IOException ignored) {}
    }
    data = YamlConfiguration.loadConfiguration(file);
  }

  public void set(Block block, String toolType) {
    if (block.getState() instanceof TileState state) {
      state.getPersistentDataContainer().set(keys.toolType, PersistentDataType.STRING, toolType);
      state.update();
    }
    data.set(key(block.getLocation()), toolType);
    save();
  }

  public String get(Block block) {
    if (block.getState() instanceof TileState state) {
      String fromTile = state.getPersistentDataContainer().get(keys.toolType, PersistentDataType.STRING);
      if (fromTile != null) return fromTile;
    }
    return data.getString(key(block.getLocation()));
  }

  public void remove(Block block) {
    if (block.getState() instanceof TileState state) {
      state.getPersistentDataContainer().remove(keys.toolType);
      state.update();
    }
    data.set(key(block.getLocation()), null);
    save();
  }

  public void save() {
    try {
      data.save(file);
    } catch (IOException ex) {
      Bukkit.getLogger().warning("[ChemCraft] Failed to save machines.yml: " + ex.getMessage());
    }
  }

  private static String key(Location location) {
    return location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
  }
}
