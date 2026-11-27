package org.sutormin.chemcraft.storage;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ChemistryService;
import org.sutormin.chemcraft.chemistry.MoleculeInfo;
import org.sutormin.chemcraft.item.ChemItems;
import org.sutormin.chemcraft.util.Chat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChemStorageManager implements Listener {
  public static final int GUI_SIZE = 54;

  private final JavaPlugin plugin;
  private final ChemItems items;
  private final ChemistryService chemistry;
  private final ChemStorageStore store;
  private final Map<UUID, PendingWithdraw> pending = new ConcurrentHashMap<>();

  private record PendingWithdraw(Location storage, int slot, String smiles, double density, double availableMoles) {}

  public ChemStorageManager(JavaPlugin plugin, ChemItems items, ChemistryService chemistry, ChemStorageStore store) {
    this.plugin = plugin;
    this.items = items;
    this.chemistry = chemistry;
    this.store = store;
  }

  public void open(Player player, Block block) {
    ChemStorageHolder holder = new ChemStorageHolder(block.getLocation());
    Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, Component.text("ChemCraft Storage", NamedTextColor.DARK_AQUA));
    holder.inventory(inv);
    ItemStack[] saved = store.loadContents(block.getLocation(), GUI_SIZE);
    for (int i = 0; i < GUI_SIZE; i++) {
      if (saved[i] != null && items.isChemItem(saved[i])) inv.setItem(i, saved[i]);
    }
    player.openInventory(inv);
  }

  public void dropContents(Location location) {
    ItemStack[] contents = store.loadContents(location, GUI_SIZE);
    for (ItemStack stack : contents) {
      if (stack != null && !stack.getType().isAir()) location.getWorld().dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), stack);
    }
    store.remove(location);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof ChemStorageHolder holder)) return;
    if (!(event.getWhoClicked() instanceof Player player)) return;
    int raw = event.getRawSlot();
    if (raw < 0) return;
    if (raw >= GUI_SIZE) {
      if (event.isShiftClick()) {
        ItemStack moving = event.getCurrentItem();
        if (moving == null || moving.getType().isAir()) return;
        if (!items.isChemItem(moving)) {
          event.setCancelled(true);
          return;
        }
        event.setCancelled(true);
        if (deposit(event.getInventory(), moving)) {
          event.setCurrentItem(null);
          persist(holder, event.getInventory());
        }
      }
      return;
    }
    event.setCancelled(true);
    ItemStack cursor = event.getCursor();
    ItemStack current = event.getCurrentItem();
    if (cursor != null && !cursor.getType().isAir()) {
      if (!items.isChemItem(cursor)) return;
      ItemStack one = cursor.clone();
      one.setAmount(1);
      if (!deposit(event.getInventory(), one)) return;
      cursor.setAmount(cursor.getAmount() - 1);
      if (cursor.getAmount() <= 0) player.setItemOnCursor(null);
      else player.setItemOnCursor(cursor);
      persist(holder, event.getInventory());
      return;
    }
    if (current != null && items.isChemItem(current)) {
      beginWithdraw(player, holder.location(), raw, current);
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (!(event.getInventory().getHolder() instanceof ChemStorageHolder)) return;
    for (int slot : event.getRawSlots()) {
      if (slot < GUI_SIZE) {
        event.setCancelled(true);
        return;
      }
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof ChemStorageHolder holder)) return;
    persist(holder, event.getInventory());
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onChat(AsyncChatEvent event) {
    PendingWithdraw withdraw = pending.get(event.getPlayer().getUniqueId());
    if (withdraw == null) return;
    event.setCancelled(true);
    String typed = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
    Player player = event.getPlayer();
    pending.remove(player.getUniqueId());
    if (typed.equalsIgnoreCase("cancel")) {
      Bukkit.getScheduler().runTask(plugin, () -> Chat.ok(player, "Withdraw cancelled."));
      return;
    }
    double grams;
    try {
      grams = Double.parseDouble(typed.replace("g", "").replace("G", "").trim());
    } catch (NumberFormatException ex) {
      Bukkit.getScheduler().runTask(plugin, () -> Chat.err(player, "Storage withdraw failed: type a number of grams in chat (or cancel)."));
      return;
    }
    if (grams <= 0) {
      Bukkit.getScheduler().runTask(plugin, () -> Chat.err(player, "Storage withdraw failed: grams must be > 0."));
      return;
    }
    Bukkit.getScheduler().runTask(plugin, () -> completeWithdraw(player, withdraw, grams));
  }

  private void beginWithdraw(Player player, Location storage, int slot, ItemStack current) {
    String smiles = items.getSmiles(current);
    double density = items.getDensity(current);
    double moles = items.getMoles(current);
    pending.put(player.getUniqueId(), new PendingWithdraw(storage.clone(), slot, smiles, density, moles));
    player.closeInventory();
    try {
      MoleculeInfo info = chemistry.analyze(smiles);
      Chat.ok(player, "Type grams to withdraw in chat (available " + ChemistryService.fmt(moles * info.molarMass()) + " g), or cancel.");
    } catch (InvalidSmilesException ex) {
      Chat.ok(player, "Type grams to withdraw in chat, or cancel.");
    }
  }

  private void completeWithdraw(Player player, PendingWithdraw withdraw, double grams) {
    try {
      MoleculeInfo info = chemistry.analyze(withdraw.smiles());
      double molesWanted = grams / info.molarMass();
      if (molesWanted > withdraw.availableMoles() + 1e-9) {
        Chat.err(player, "Storage withdraw failed: only " + ChemistryService.fmt(withdraw.availableMoles() * info.molarMass()) + " g available.");
        return;
      }
      ItemStack[] contents = store.loadContents(withdraw.storage(), GUI_SIZE);
      ItemStack stored = contents[withdraw.slot()];
      if (stored == null || !items.isChemItem(stored) || !withdraw.smiles().equals(items.getSmiles(stored))) {
        Chat.err(player, "Storage withdraw failed: chemical changed.");
        return;
      }
      double remaining = items.getMoles(stored) - molesWanted;
      if (remaining <= 1e-9) contents[withdraw.slot()] = null;
      else contents[withdraw.slot()] = items.createChemItem(withdraw.smiles(), remaining, withdraw.density());
      store.saveContents(withdraw.storage(), contents);
      items.give(player, items.createChemItem(withdraw.smiles(), molesWanted, withdraw.density()));
      Chat.ok(player, "Withdrew " + ChemistryService.fmt(grams) + " g of " + withdraw.smiles() + ".");
    } catch (InvalidSmilesException ex) {
      Chat.err(player, "Storage withdraw failed: invalid SMILES.");
    }
  }

  private boolean deposit(Inventory inv, ItemStack incoming) {
    if (!items.isChemItem(incoming)) return false;
    String smiles = items.getSmiles(incoming);
    double density = items.getDensity(incoming);
    double moles = items.getMoles(incoming) * Math.max(1, incoming.getAmount());
    for (int i = 0; i < GUI_SIZE; i++) {
      ItemStack existing = inv.getItem(i);
      if (!items.isChemItem(existing)) continue;
      if (!smiles.equals(items.getSmiles(existing))) continue;
      double mergedDensity = items.getDensity(existing);
      if (Math.abs(mergedDensity - density) > 1e-6) continue;
      try {
        inv.setItem(i, items.createChemItem(smiles, items.getMoles(existing) + moles, density));
        return true;
      } catch (InvalidSmilesException ex) {
        return false;
      }
    }
    for (int i = 0; i < GUI_SIZE; i++) {
      ItemStack existing = inv.getItem(i);
      if (existing != null && !existing.getType().isAir()) continue;
      try {
        inv.setItem(i, items.createChemItem(smiles, moles, density));
        return true;
      } catch (InvalidSmilesException ex) {
        return false;
      }
    }
    return false;
  }

  private void persist(ChemStorageHolder holder, Inventory inv) {
    ItemStack[] contents = new ItemStack[GUI_SIZE];
    for (int i = 0; i < GUI_SIZE; i++) {
      ItemStack stack = inv.getItem(i);
      contents[i] = (stack != null && items.isChemItem(stack)) ? stack.clone() : null;
    }
    store.saveContents(holder.location(), contents);
  }

  public void cleanupPlayer(Player player) {
    pending.remove(player.getUniqueId());
  }
}
