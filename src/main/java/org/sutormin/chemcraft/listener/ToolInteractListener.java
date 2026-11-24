package org.sutormin.chemcraft.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ChemistryEngine;
import org.sutormin.chemcraft.chemistry.MoleculeInfo;
import org.sutormin.chemcraft.item.ChemItemManager;
import org.sutormin.chemcraft.lab.LabGuiManager;

import java.util.Locale;

public final class ToolInteractListener implements Listener {
  private final ChemItemManager itemManager;
  private final ChemistryEngine chemistryEngine;
  private final LabGuiManager labGuiManager;

  public ToolInteractListener(
    ChemItemManager itemManager,
    ChemistryEngine chemistryEngine,
    LabGuiManager labGuiManager
  ) {
    this.itemManager = itemManager;
    this.chemistryEngine = chemistryEngine;
    this.labGuiManager = labGuiManager;
  }

  @EventHandler
  public void onBlockPlace(BlockPlaceEvent event) {
    String tool = itemManager.getToolType(event.getItemInHand());
    if (tool == null) {
      return;
    }
    Block block = event.getBlockPlaced();
    if (block.getState() instanceof TileState tileState) {
      tileState.getPersistentDataContainer().set(itemManager.getToolTypeKey(), PersistentDataType.STRING, tool);
      tileState.update();
    }
    event.getPlayer().sendMessage(Component.text("Placed " + tool + " block.", NamedTextColor.GREEN));
  }

  @EventHandler
  public void onBlockBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    if (!(block.getState() instanceof TileState tileState)) {
      return;
    }
    String tool = tileState.getPersistentDataContainer().get(itemManager.getToolTypeKey(), PersistentDataType.STRING);
    if (tool == null) {
      return;
    }
    event.setDropItems(false);
    ItemStack drop = switch (tool) {
      case "purifier" -> itemManager.createPurifier();
      case "lab" -> itemManager.createLab();
      case "analyzer" -> itemManager.createAnalyzer();
      default -> null;
    };
    if (drop != null) {
      block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
    }
  }

  @EventHandler
  public void onPlayerInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    Action action = event.getAction();
    if (action != Action.RIGHT_CLICK_BLOCK) return;

    Player player = event.getPlayer();
    ItemStack held = event.getItem();
    if (event.getClickedBlock().getState() instanceof TileState tileState) {
      tool = tileState.getPersistentDataContainer().get(itemManager.getToolTypeKey(), PersistentDataType.STRING);
    }

    if (tool == null) return;
    if (player.isSneaking() && held != null && held.getType().isBlock()) return;
    event.setCancelled(true);

    switch (tool) {
      case "purifier" -> handlePurifier(player, held);
      case "lab" -> labGuiManager.openLab(player);
      case "analyzer" -> handleAnalyzer(player, held);
    }
  }

  private void handlePurifier(Player player, ItemStack held) {
    if (held.getType().isAir() || itemManager.getToolType(held) != null || itemManager.isChemItem(held)) {
      player.sendMessage(Component.text("Hold a vanilla item in your hand and right click.", NamedTextColor.YELLOW));
      return;
    }

    String smiles = itemManager.resolveVanillaSmiles(offHand.getType());
    double totalMoles = offHand.getAmount();
    player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
    itemManager.giveToPlayer(player, itemManager.createChemItem(smiles, totalMoles, 1));
    player.sendMessage(Component.text("Purified into " + smiles + " (" + totalMoles + " mol).", NamedTextColor.GREEN));
  }

  private void handleAnalyzer(Player player) {
    ItemStack offHand = player.getInventory().getItemInOffHand();
    if (!itemManager.isChemItem(offHand)) {
      player.sendMessage(Component.text("Hold a ChemCraft item in your off-hand to analyze.", NamedTextColor.YELLOW));
      return;
    }

    String smiles = itemManager.getSmiles(offHand);
    double moles = itemManager.getMoles(offHand);

    try {
      MoleculeInfo info = chemistryEngine.analyze(smiles);
      player.sendMessage(Component.text("=== ChemCraft Analyzer ===", NamedTextColor.GOLD));
      player.sendMessage(Component.text("SMILES: " + smiles + " | Moles: " + moles + " mol", NamedTextColor.WHITE));
      player.sendMessage(Component.text("Atoms: " + info.atoms() + " | Heavy Atoms: " + info.heavyAtoms(), NamedTextColor.GRAY));
      player.sendMessage(Component.text(
          "Molar Mass: " + String.format(Locale.ROOT, "%.3f", info.molarMass()) + " g/mol | Charge: " + info.charge(),
          NamedTextColor.GRAY
      ));
      player.sendMessage(Component.text("Traits: " + String.join(", ", info.traits()), NamedTextColor.AQUA));
    } catch (InvalidSmilesException ex) {
      player.sendMessage(Component.text("Invalid SMILES: " + smiles, NamedTextColor.RED));
    }
  }
}
