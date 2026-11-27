package org.sutormin.chemcraft.storage;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class ChemStorageHolder implements InventoryHolder {
  private final Location location;
  private Inventory inventory;

  public ChemStorageHolder(Location location) {
    this.location = location.clone();
  }

  public Location location() {
    return location;
  }

  public void inventory(Inventory inventory) {
    this.inventory = inventory;
  }

  @Override
  public Inventory getInventory() {
    return inventory;
  }
}
