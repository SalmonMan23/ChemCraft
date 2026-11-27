package org.sutormin.chemcraft.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.sutormin.chemcraft.item.ChemItems;
import org.sutormin.chemcraft.lab.LabManager;
import org.sutormin.chemcraft.lab.LabType;
import org.sutormin.chemcraft.machine.MachineRegistry;
import org.sutormin.chemcraft.map.StructureMapService;
import org.sutormin.chemcraft.storage.ChemStorageManager;

public final class MachineListener implements Listener {
  private final ChemItems items;
  private final PurifierService purifier;
  private final AnalyzerService analyzer;
  private final LabManager lab;
  private final ChemStorageManager storage;
  private final MachineRegistry machines;

  public MachineListener(ChemItems items, PurifierService purifier, AnalyzerService analyzer, LabManager lab, ChemStorageManager storage, MachineRegistry machines) {
    this.items = items;
    this.purifier = purifier;
    this.analyzer = analyzer;
    this.lab = lab;
    this.storage = storage;
    this.machines = machines;
  }

  @EventHandler
  public void onPlace(BlockPlaceEvent event) {
    String tool = items.getToolType(event.getItemInHand());
    if (tool == null) return;
    Block block = event.getBlockPlaced();
    if (block.getType() != expectedMaterial(tool)) {
      event.setCancelled(true);
      return;
    }
    machines.set(block, tool);
    if (ChemItems.PURIFIER.equals(tool) && block.getBlockData() instanceof EndPortalFrame frame) {
      frame.setEye(false);
      block.setBlockData(frame);
    }
  }

  @EventHandler
  public void onBreak(BlockBreakEvent event) {
    Block block = event.getBlock();
    String tool = machines.get(block);
    if (tool == null) return;
    event.setDropItems(false);
    LabType labType = LabType.fromToolId(tool);
    if (labType != null) lab.dropContents(block.getLocation());
    if (ChemItems.STORAGE.equals(tool)) storage.dropContents(block.getLocation());
    machines.remove(block);
    ItemStack drop;
    if (ChemItems.PURIFIER.equals(tool)) drop = items.createPurifier();
    else if (ChemItems.ANALYZER.equals(tool)) drop = items.createAnalyzer();
    else if (ChemItems.STORAGE.equals(tool)) drop = items.createStorage();
    else if (labType != null) drop = items.createLab(labType);
    else drop = null;
    if (drop != null) block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), drop);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onInteract(PlayerInteractEvent event) {
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    Block block = event.getClickedBlock();
    if (block == null) return;
    String tool = machines.get(block);
    Player player = event.getPlayer();
    ItemStack held = player.getInventory().getItemInMainHand();
    if (tool == null) return;
    if (ChemItems.PURIFIER.equals(tool) && held.getType() == Material.ENDER_EYE) {
      event.setCancelled(true);
      return;
    }
    if (ChemItems.ANALYZER.equals(tool) && isMusicDisc(held.getType())) {
      event.setCancelled(true);
      return;
    }
    if (player.isSneaking() && held.getType().isBlock()) return;
    event.setCancelled(true);
    LabType labType = LabType.fromToolId(tool);
    if (ChemItems.PURIFIER.equals(tool)) {
      clearEyeIfPresent(block);
      purifier.handle(player, held);
    } else if (ChemItems.ANALYZER.equals(tool)) analyzer.handle(player, held, block);
    else if (ChemItems.STORAGE.equals(tool)) storage.open(player, block);
    else if (labType != null) lab.open(player, block, labType);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void onFrameInteract(PlayerInteractEntityEvent event) {
    if (!(event.getRightClicked() instanceof ItemFrame frame)) return;
    Block below = frame.getLocation().getBlock().getRelative(BlockFace.DOWN);
    Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
    boolean onAnalyzer = ChemItems.ANALYZER.equals(machines.get(below)) || ChemItems.ANALYZER.equals(machines.get(attached)) || StructureMapService.isFrameOnTopOf(frame, below) && ChemItems.ANALYZER.equals(machines.get(below));
    if (!onAnalyzer) return;
    ItemStack held = event.getPlayer().getInventory().getItemInMainHand();
    ItemStack current = frame.getItem();
    boolean placing = held != null && !held.getType().isAir() && (current == null || current.getType().isAir());
    if (placing && held.getType() != Material.MAP && held.getType() != Material.FILLED_MAP) event.setCancelled(true);
  }

  @EventHandler
  public void onQuit(PlayerQuitEvent event) {
    storage.cleanupPlayer(event.getPlayer());
  }

  private void clearEyeIfPresent(Block block) {
    BlockData data = block.getBlockData();
    if (data instanceof EndPortalFrame frame && frame.hasEye()) {
      frame.setEye(false);
      block.setBlockData(frame);
    }
  }

  private static boolean isMusicDisc(Material material) {
    return material != null && material.name().contains("MUSIC_DISC");
  }

  private Material expectedMaterial(String tool) {
    if (ChemItems.PURIFIER.equals(tool)) return Material.END_PORTAL_FRAME;
    if (ChemItems.ANALYZER.equals(tool)) return Material.JUKEBOX;
    if (ChemItems.STORAGE.equals(tool)) return Material.BARREL;
    LabType type = LabType.fromToolId(tool);
    return type == null ? null : type.material();
  }
}
