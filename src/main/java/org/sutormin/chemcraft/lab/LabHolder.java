package org.sutormin.chemcraft.lab;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class LabHolder implements InventoryHolder {
  private final Location location;
  private final LabType type;
  private final LabSettings settings;
  private Inventory inventory;

  public LabHolder(Location location, LabType type, LabSettings settings) {
    this.location = location.clone();
    this.type = type;
    this.settings = settings;
  }

  public Location location() {
    return location;
  }

  public LabType type() {
    return type;
  }

  public LabSettings settings() {
    return settings;
  }

  public void inventory(Inventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
