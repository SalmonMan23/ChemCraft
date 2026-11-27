package org.sutormin.chemcraft.listener;

import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ChemistryService;
import org.sutormin.chemcraft.chemistry.MoleculeInfo;
import org.sutormin.chemcraft.item.ChemItems;
import org.sutormin.chemcraft.map.StructureMapService;
import org.sutormin.chemcraft.util.Chat;

public final class AnalyzerService {
  private final ChemItems items;
  private final ChemistryService chemistry;
  private final StructureMapService maps;

  public AnalyzerService(ChemItems items, ChemistryService chemistry, StructureMapService maps) {
    this.items = items;
    this.chemistry = chemistry;
    this.maps = maps;
  }

  public void handle(Player player, ItemStack held, Block analyzerBlock) {
    if (held == null || held.getType().isAir() || !items.isChemItem(held)) return;
    String smiles = items.getSmiles(held);
    double moles = items.getMoles(held);
    double density = items.getDensity(held);
    try {
      MoleculeInfo info = chemistry.analyze(smiles);
      double mass = moles * info.molarMass();
      double volume = density <= 0.0 ? Double.NaN : mass / density;
      Chat.ok(player, "Analyzer: " + smiles + " | " + info.formulaSubscript() + " | " + ChemistryService.fmt(moles) + " mol | " + ChemistryService.fmt(mass) + " g | " + ChemistryService.fmt(density) + " g/cm³ | " + (Double.isNaN(volume) ? "?" : ChemistryService.fmt(volume) + " cm³") + " | charge " + info.charge() + " | " + String.join(", ", info.traits()) + " | " + info.phase(density));
      ItemFrame frame = maps.findMapFrameOnTop(analyzerBlock);
      if (frame != null) maps.renderOntoFrameAsync(frame, smiles);
    } catch (InvalidSmilesException ex) {
      Chat.err(player, "Analyzer failed: invalid SMILES.");
    }
  }
}
