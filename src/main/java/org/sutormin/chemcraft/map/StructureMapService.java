package org.sutormin.chemcraft.map;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.plugin.java.JavaPlugin;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.sutormin.chemcraft.chemistry.ChemistryService;
import org.sutormin.chemcraft.chemistry.MoleculeLayout;

public final class StructureMapService {
  private final JavaPlugin plugin;
  private final ChemistryService chemistry;

  public StructureMapService(JavaPlugin plugin, ChemistryService chemistry) {
    this.plugin = plugin;
    this.chemistry = chemistry;
  }

  public ItemFrame findMapFrameOnTop(Block analyzer) {
    Block above = analyzer.getRelative(BlockFace.UP);
    for (Entity entity : analyzer.getWorld().getNearbyEntities(above.getLocation().add(0.5, 0.5, 0.5), 0.75, 0.75, 0.75)) {
      if (!(entity instanceof ItemFrame frame)) continue;
      if (!isFrameOnTopOf(frame, analyzer)) continue;
      ItemStack item = frame.getItem();
      if (item == null || item.getType().isAir()) continue;
      if (item.getType() == Material.MAP || item.getType() == Material.FILLED_MAP) return frame;
    }
    return null;
  }

  public static boolean isFrameOnTopOf(ItemFrame frame, Block support) {
    Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
    if (attached.equals(support) && frame.getAttachedFace() == BlockFace.UP) return true;
    Block frameBlock = frame.getLocation().getBlock();
    return frameBlock.equals(support.getRelative(BlockFace.UP)) || (frameBlock.getRelative(BlockFace.DOWN).equals(support));
  }

  public void renderOntoFrameAsync(ItemFrame frame, String smiles) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
      try {
        IAtomContainer mol = chemistry.parse(smiles);
        MoleculeLayout.Layout layout = MoleculeLayout.layout(mol);
        Bukkit.getScheduler().runTask(plugin, () -> applyMap(frame, layout));
      } catch (CDKException ex) {
        // ignore render failures; analyzer chat still works
      }
    });
  }

  private void applyMap(ItemFrame frame, MoleculeLayout.Layout layout) {
    if (!frame.isValid()) return;
    World world = frame.getWorld();
    MapView view = Bukkit.createMap(world);
    view.getRenderers().forEach(view::removeRenderer);
    view.addRenderer(new MoleculeMapRenderer(layout));
    view.setTrackingPosition(false);
    view.setUnlimitedTracking(false);
    ItemStack map = new ItemStack(Material.FILLED_MAP);
    MapMeta meta = (MapMeta) map.getItemMeta();
    meta.setMapView(view);
    meta.displayName(net.kyori.adventure.text.Component.text("Skeletal Formula"));
    map.setItemMeta(meta);
    frame.setItem(map);
  }
}
