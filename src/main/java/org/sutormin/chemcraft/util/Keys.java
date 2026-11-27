package org.sutormin.chemcraft.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

public final class Keys {
  public final NamespacedKey toolType;
  public final NamespacedKey smiles;
  public final NamespacedKey moles;
  public final NamespacedKey density;
  public final NamespacedKey labSetting;

  public Keys(Plugin plugin) {
    this.toolType = new NamespacedKey(plugin, "tool_type");
    this.smiles = new NamespacedKey(plugin, "smiles");
    this.moles = new NamespacedKey(plugin, "moles");
    this.density = new NamespacedKey(plugin, "density");
    this.labSetting = new NamespacedKey(plugin, "lab_setting");
  }
}
