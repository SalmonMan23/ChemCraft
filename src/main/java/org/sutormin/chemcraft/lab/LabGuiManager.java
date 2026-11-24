package org.sutormin.chemcraft.lab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ChemistryEngine;
import org.sutormin.chemcraft.item.ChemItemManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LabGuiManager implements Listener {

    public static final int GUI_SIZE = 27;
    public static final int RUN_BUTTON_SLOT = 26;
    public static final long COOLDOWN_MS = 3_000L;

    private final Plugin plugin;
    private final ChemItemManager itemManager;
    private final ChemistryEngine chemistryEngine;
    private final Map<UUID, Long> lastRunMillis = new ConcurrentHashMap<>();

    public LabGuiManager(Plugin plugin, ChemItemManager itemManager, ChemistryEngine chemistryEngine) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.chemistryEngine = chemistryEngine;
    }

    public void openLab(Player player) {
        Inventory inv = Bukkit.createInventory(player, GUI_SIZE, Component.text("ChemCraft Lab", NamedTextColor.DARK_GREEN));
        ItemStack button = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(Component.text("Run Reaction (CDK)", NamedTextColor.GREEN));
        meta.lore(List.of(
                Component.text("Place ChemCraft items in slots 0-25", NamedTextColor.GRAY),
                Component.text("Click this green block to finish & receive results (3s cooldown)", NamedTextColor.YELLOW)
        ));
        button.setItemMeta(meta);
        inv.setItem(RUN_BUTTON_SLOT, button);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!event.getView().title().equals(Component.text("ChemCraft Lab", NamedTextColor.DARK_GREEN))) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= GUI_SIZE) {
            return;
        }
        if (rawSlot == RUN_BUTTON_SLOT) {
            event.setCancelled(true);
            triggerSimulation(player, event.getInventory());
        }
    }

    private void triggerSimulation(Player player, Inventory inv) {
        long now = System.currentTimeMillis();
        Long prev = lastRunMillis.get(player.getUniqueId());
        if (prev != null && (now - prev) < COOLDOWN_MS) {
            long remainingSec = (COOLDOWN_MS - (now - prev) + 999L) / 1000L;
            player.sendMessage(Component.text("Lab cooldown: wait " + remainingSec + "s.", NamedTextColor.RED));
            return;
        }
        lastRunMillis.put(player.getUniqueId(), now);

        Map<String, Double> pool = new HashMap<>();
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i == RUN_BUTTON_SLOT) continue;
            ItemStack item = inv.getItem(i);
            if (itemManager.isChemItem(item)) {
                String smi = itemManager.getSmiles(item);
                double moles = itemManager.getMoles(item) * item.getAmount();
                pool.merge(smi, moles, Double::sum);
            }
        }

        if (pool.isEmpty()) {
            player.sendMessage(Component.text("No ChemCraft items in the Lab.", NamedTextColor.YELLOW));
            return;
        }

        // Clear input slots immediately and schedule completion after the 3-second run finishes
        for (int i = 0; i < GUI_SIZE; i++) {
            if (i != RUN_BUTTON_SLOT) {
                inv.setItem(i, null);
            }
        }
        player.sendMessage(Component.text("Lab running reaction (3s)...", NamedTextColor.AQUA));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ChemistryEngine.SimulationResult result;
            try {
                result = chemistryEngine.simulate(pool);
            } catch (InvalidSmilesException ex) {
                player.sendMessage(Component.text("Invalid SMILES: " + ex.getMessage(), NamedTextColor.RED));
                return;
            }

            List<ItemStack> outputs = new ArrayList<>();
            for (Map.Entry<String, Double> entry : result.resultingPool().entrySet()) {
                if (entry.getValue() > 1e-9) {
                    double rounded = Math.round(entry.getValue() * 1_000_000.0) / 1_000_000.0;
                    ItemStack outStack = itemManager.createChemItem(entry.getKey(), rounded, 1);
                    outputs.add(outStack);
                    if (inv.getViewers().contains(player)) {
                        int emptySlot = findFirstInputSlot(inv);
                        if (emptySlot != -1) {
                            inv.setItem(emptySlot, outStack.clone());
                            continue;
                        }
                    }
                    itemManager.giveToPlayer(player, outStack);
                }
            }

            if (result.logs().isEmpty()) {
                player.sendMessage(Component.text("Lab finished: no reaction occurred (" + outputs.size() + " output(s)).", NamedTextColor.GRAY));
            } else {
                for (String log : result.logs()) {
                    player.sendMessage(Component.text("Reaction: " + log, NamedTextColor.GREEN));
                }
                player.sendMessage(Component.text("Lab finished! Delivered " + outputs.size() + " product stack(s).", NamedTextColor.GOLD));
            }
        }, 60L);
    }

    private int findFirstInputSlot(Inventory inv) {
        for (int i = 0; i < RUN_BUTTON_SLOT; i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType().isAir()) {
                return i;
            }
        }
        return -1;
    }
}
