package org.sutormin.chemcraft.lab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ReactionConditions;
import org.sutormin.chemcraft.chemistry.ReactionEngine;
import org.sutormin.chemcraft.config.ChemConfig;
import org.sutormin.chemcraft.item.ChemItems;
import org.sutormin.chemcraft.util.Chat;
import org.sutormin.chemcraft.util.Keys;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LabManager implements Listener {
  public static final int GUI_SIZE = 27;
  public static final int INPUT_SLOTS = 18;
  public static final int SETTINGS_START = 18;
  public static final int RUN_SLOT = 26;
  public static final int STATUS_SLOT = 22;
  private static final long COOLDOWN_MS = 3000L;

  private final JavaPlugin plugin;
  private final ChemItems items;
  private final ChemConfig config;
  private final ReactionEngine reactions;
  private final LabStorage storage;
  private final Keys keys;
  private final Map<UUID, Long> cooldown = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> busy = new ConcurrentHashMap<>();

  public LabManager(JavaPlugin plugin, ChemItems items, ChemConfig config, ReactionEngine reactions, LabStorage storage, Keys keys) {
    this.plugin = plugin;
    this.items = items;
    this.config = config;
    this.reactions = reactions;
    this.storage = storage;
    this.keys = keys;
  }

  public void open(Player player, Block block, LabType type) {
    LabSettings settings = storage.loadSettings(block.getLocation(), type);
    LabHolder holder = new LabHolder(block.getLocation(), type, settings);
    Inventory inv = Bukkit.createInventory(holder, GUI_SIZE, Component.text(type.displayName(), NamedTextColor.DARK_GREEN));
    holder.inventory(inv);
    ItemStack[] saved = storage.loadContents(block.getLocation());
    for (int i = 0; i < INPUT_SLOTS; i++) {
      if (saved[i] != null && items.isChemItem(saved[i])) inv.setItem(i, saved[i]);
    }
    paintSettings(inv, holder);
    player.openInventory(inv);
  }

  public void dropContents(Location location) {
    ItemStack[] contents = storage.loadContents(location);
    for (ItemStack stack : contents) {
      if (stack != null && !stack.getType().isAir()) location.getWorld().dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), stack);
    }
    storage.remove(location);
  }

  @EventHandler
  public void onClick(InventoryClickEvent event) {
    if (!(event.getInventory().getHolder() instanceof LabHolder holder)) return;
    if (!(event.getWhoClicked() instanceof Player player)) return;
    if (Boolean.TRUE.equals(busy.get(player.getUniqueId()))) {
      event.setCancelled(true);
      return;
    }
    int raw = event.getRawSlot();
    if (raw >= SETTINGS_START && raw < GUI_SIZE) {
      event.setCancelled(true);
      if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof LabHolder)) return;
      handleSettingClick(player, event.getInventory(), holder, raw);
      return;
    }
    if (raw >= 0 && raw < INPUT_SLOTS) {
      ItemStack cursor = event.getCursor();
      if (cursor != null && !cursor.getType().isAir() && !items.isChemItem(cursor)) event.setCancelled(true);
    }
    if (event.isShiftClick() && raw >= GUI_SIZE) {
      ItemStack moving = event.getCurrentItem();
      if (moving != null && !moving.getType().isAir() && !items.isChemItem(moving)) event.setCancelled(true);
    }
  }

  @EventHandler
  public void onDrag(InventoryDragEvent event) {
    if (!(event.getInventory().getHolder() instanceof LabHolder)) return;
    for (int slot : event.getRawSlots()) {
      if (slot >= SETTINGS_START && slot < GUI_SIZE) {
        event.setCancelled(true);
        return;
      }
    }
    ItemStack old = event.getOldCursor();
    if (old != null && !old.getType().isAir() && !items.isChemItem(old)) {
      for (int slot : event.getRawSlots()) {
        if (slot < INPUT_SLOTS) {
          event.setCancelled(true);
          return;
        }
      }
    }
  }

  @EventHandler
  public void onClose(InventoryCloseEvent event) {
    if (!(event.getInventory().getHolder() instanceof LabHolder holder)) return;
    persist(holder, event.getInventory());
  }

  private void handleSettingClick(Player player, Inventory inv, LabHolder holder, int slot) {
    if (Boolean.TRUE.equals(busy.get(player.getUniqueId()))) return;
    if (slot == RUN_SLOT) {
      runReaction(player, inv, holder);
      return;
    }
    LabDefaults defaults = config.labDefaults(holder.type());
    LabSettings settings = holder.settings();
    if (defaults.voltageControl()) {
      double[] steps = toArray(defaults.voltageSteps());
      if (slot >= 18 && slot <= 21) {
        int idx = 21 - slot;
        if (idx >= 0 && idx < steps.length) settings.adjustVoltage(-steps[idx], defaults);
      } else if (slot >= 23 && slot <= 25) {
        int idx = slot - 23;
        if (idx >= 0 && idx < steps.length) settings.adjustVoltage(steps[idx], defaults);
      }
    } else if (defaults.temperatureControl()) {
      double[] steps = toArray(defaults.temperatureSteps());
      if (slot >= 18 && slot <= 21) {
        int idx = 21 - slot;
        if (idx >= 0 && idx < steps.length) settings.adjustTemperature(-steps[idx], defaults);
      } else if (slot >= 23 && slot <= 25) {
        int idx = slot - 23;
        if (idx >= 0 && idx < Math.min(3, steps.length)) settings.adjustTemperature(steps[idx], defaults);
      }
    }
    paintSettings(inv, holder);
    persist(holder, inv);
  }

  private void runReaction(Player player, Inventory inv, LabHolder holder) {
    UUID id = player.getUniqueId();
    if (Boolean.TRUE.equals(busy.get(id))) return;
    long now = System.currentTimeMillis();
    Long prev = cooldown.get(id);
    if (prev != null && now - prev < COOLDOWN_MS) return;
    Map<String, Double> pool = new HashMap<>();
    Map<String, Double> densities = new HashMap<>();
    ItemStack[] snapshot = new ItemStack[INPUT_SLOTS];
    for (int i = 0; i < INPUT_SLOTS; i++) {
      ItemStack stack = inv.getItem(i);
      if (stack != null && items.isChemItem(stack)) snapshot[i] = stack.clone();
      if (!items.isChemItem(stack)) continue;
      String smiles = items.getSmiles(stack);
      double moles = items.getMoles(stack) * Math.max(1, stack.getAmount());
      pool.merge(smiles, moles, Double::sum);
      densities.putIfAbsent(smiles, items.getDensity(stack));
    }
    if (pool.isEmpty()) {
      Chat.err(player, "Reaction failed: no ChemCraft chemicals in the lab.");
      return;
    }
    cooldown.put(id, now);
    busy.put(id, true);
    LabSettings settings = holder.settings();
    ReactionConditions conditions = new ReactionConditions(holder.type(), settings.temperatureK(), settings.pressureAtm(), settings.voltageV());
    for (int i = 0; i < INPUT_SLOTS; i++) inv.setItem(i, null);
    persist(holder, inv);
    Map<String, Double> poolCopy = Map.copyOf(pool);
    Map<String, Double> densityCopy = Map.copyOf(densities);
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      ReactionEngine.SimulationResult result;
      try {
        result = reactions.simulate(poolCopy, conditions);
      } catch (InvalidSmilesException ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          restoreInputs(inv, snapshot);
          paintSettings(inv, holder);
          persist(holder, inv);
          busy.remove(id);
          Chat.err(player, "Reaction failed: invalid SMILES during simulation.");
        });
        return;
      } catch (Exception ex) {
        Bukkit.getScheduler().runTask(plugin, () -> {
          restoreInputs(inv, snapshot);
          paintSettings(inv, holder);
          persist(holder, inv);
          busy.remove(id);
          Chat.err(player, "Reaction failed: simulation error.");
        });
        return;
      }
      ReactionEngine.SimulationResult finalResult = result;
      Bukkit.getScheduler().runTask(plugin, () -> {
        try {
          if (!finalResult.reacted()) {
            restoreInputs(inv, snapshot);
            paintSettings(inv, holder);
            persist(holder, inv);
            Chat.err(player, "Reaction failed at " + String.format(Locale.ROOT, "%.0f K, %.2f V", settings.temperatureK(), settings.voltageV()) + ".");
            return;
          }
          List<ItemStack> outputs = new ArrayList<>();
          for (Map.Entry<String, Double> entry : finalResult.pool().entrySet()) {
            double moles = Math.round(entry.getValue() * 1_000_000.0) / 1_000_000.0;
            if (moles <= 0) continue;
            double density = densityCopy.getOrDefault(entry.getKey(), config.densityOf(entry.getKey()));
            try {
              outputs.add(items.createChemItem(entry.getKey(), moles, density));
            } catch (InvalidSmilesException ignored) {}
          }
          for (int i = 0; i < INPUT_SLOTS; i++) inv.setItem(i, null);
          int slot = 0;
          for (ItemStack out : outputs) {
            if (slot >= INPUT_SLOTS) items.give(player, out);
            else inv.setItem(slot++, out);
          }
          paintSettings(inv, holder);
          persist(holder, inv);
          String detail = finalResult.logs().isEmpty() ? (outputs.size() + " product(s)") : finalResult.logs().get(0);
          Chat.ok(player, "Reaction succeeded: " + detail);
        } finally {
          busy.remove(id);
        }
      });
    });
  }

  private void restoreInputs(Inventory inv, ItemStack[] snapshot) {
    for (int i = 0; i < INPUT_SLOTS; i++) inv.setItem(i, snapshot[i] == null ? null : snapshot[i].clone());
  }

  private void paintSettings(Inventory inv, LabHolder holder) {
    LabDefaults defaults = config.labDefaults(holder.type());
    LabSettings settings = holder.settings();
    for (int i = SETTINGS_START; i < GUI_SIZE; i++) inv.setItem(i, null);
    if (defaults.voltageControl()) {
      double[] steps = toArray(defaults.voltageSteps());
      for (int i = 0; i < Math.min(4, steps.length); i++) inv.setItem(21 - i, settingButton(Material.REDSTONE, NamedTextColor.RED, "Voltage -" + trim(steps[i]) + " V", "voltage_down_" + trim(steps[i])));
      for (int i = 0; i < Math.min(3, steps.length); i++) inv.setItem(23 + i, settingButton(Material.GLOWSTONE_DUST, NamedTextColor.YELLOW, "Voltage +" + trim(steps[i]) + " V", "voltage_up_" + trim(steps[i])));
      inv.setItem(STATUS_SLOT, settingButton(Material.REDSTONE_BLOCK, NamedTextColor.GOLD, String.format(Locale.ROOT, "Voltage: %.2f V", settings.voltageV()), "status"));
    } else {
      double[] steps = toArray(defaults.temperatureSteps());
      for (int i = 0; i < Math.min(4, steps.length); i++) inv.setItem(21 - i, settingButton(Material.SNOWBALL, NamedTextColor.AQUA, "Temp -" + trim(steps[i]) + " K", "temp_down_" + trim(steps[i])));
      for (int i = 0; i < Math.min(3, steps.length); i++) inv.setItem(23 + i, settingButton(Material.BLAZE_POWDER, NamedTextColor.GOLD, "Temp +" + trim(steps[i]) + " K", "temp_up_" + trim(steps[i])));
      inv.setItem(STATUS_SLOT, settingButton(Material.CLOCK, NamedTextColor.YELLOW, String.format(Locale.ROOT, "Temp: %.0f K  |  P: %.1f atm", settings.temperatureK(), settings.pressureAtm()), "status"));
    }
    inv.setItem(RUN_SLOT, settingButton(Material.EMERALD_BLOCK, NamedTextColor.GREEN, "Run Reaction", "run"));
  }

  private ItemStack settingButton(Material material, NamedTextColor color, String title, String id) {
    ItemStack stack = new ItemStack(material);
    ItemMeta meta = stack.getItemMeta();
    meta.displayName(Component.text(title, color).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
    meta.lore(List.of(Component.text("Lab setting", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false), Component.text("ChemCraft", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, true)));
    meta.getPersistentDataContainer().set(keys.labSetting, PersistentDataType.STRING, id);
    stack.setItemMeta(meta);
    return stack;
  }

  private void persist(LabHolder holder, Inventory inv) {
    ItemStack[] contents = new ItemStack[INPUT_SLOTS];
    for (int i = 0; i < INPUT_SLOTS; i++) {
      ItemStack stack = inv.getItem(i);
      contents[i] = (stack != null && items.isChemItem(stack)) ? stack.clone() : null;
    }
    storage.save(holder.location(), holder.settings(), contents);
  }

  private static double[] toArray(List<Double> values) {
    double[] out = new double[values.size()];
    for (int i = 0; i < values.size(); i++) out[i] = values.get(i);
    return out;
  }

  private static String trim(double value) {
    if (Math.abs(value - Math.rint(value)) < 1e-9) return String.valueOf((long) Math.rint(value));
    return String.format(Locale.ROOT, "%.1f", value);
  }
}
