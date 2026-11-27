package org.sutormin.chemcraft.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ChemistryService;
import org.sutormin.chemcraft.chemistry.MoleculeInfo;
import org.sutormin.chemcraft.config.ChemConfig;
import org.sutormin.chemcraft.item.ChemItems;
import org.sutormin.chemcraft.util.Chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PurifierService {
  private final ChemItems items;
  private final ChemConfig config;
  private final ChemistryService chemistry;

  public PurifierService(ChemItems items, ChemConfig config, ChemistryService chemistry) {
    this.items = items;
    this.config = config;
    this.chemistry = chemistry;
  }

  public void handle(Player player, ItemStack held) {
    if (held == null || held.getType().isAir() || items.getToolType(held) != null) return;
    if (items.isChemItem(held)) {
      purifyToElements(player, held);
      return;
    }
    purifyVanilla(player, held);
  }

  private void purifyVanilla(Player player, ItemStack held) {
    Material material = held.getType();
    Material base = config.isMappedBlock(material) ? config.baseMaterial(material) : material;
    List<ChemConfig.Component> components = config.componentsOf(base);
    int multiplier = config.isMappedBlock(material) ? config.multiplierOf(material) : (components.isEmpty() ? 0 : 1);
    if (components.isEmpty() || multiplier <= 0) {
      Chat.err(player, "Purifier failed: no composition mapping for " + material.name() + ".");
      return;
    }
    List<ItemStack> outputs = new ArrayList<>();
    try {
      for (ChemConfig.Component component : components) {
        MoleculeInfo info = chemistry.analyze(component.smiles());
        double moles = (component.grams() * multiplier) / info.molarMass();
        outputs.add(items.createChemItem(component.smiles(), moles, component.density()));
      }
    } catch (InvalidSmilesException ex) {
      Chat.err(player, "Purifier failed: invalid config SMILES.");
      return;
    }
    held.setAmount(held.getAmount() - 1);
    for (ItemStack out : outputs) items.give(player, out);
    Chat.ok(player, "Purified " + material.name() + " into " + outputs.size() + " component(s).");
  }

  private void purifyToElements(Player player, ItemStack held) {
    String smiles = items.getSmiles(held);
    double moles = items.getMoles(held);
    Map<String, Integer> atoms;
    try {
      atoms = chemistry.elementalCounts(smiles);
    } catch (InvalidSmilesException ex) {
      Chat.err(player, "Purifier failed: invalid chemical SMILES.");
      return;
    }
    if (atoms.isEmpty()) {
      Chat.err(player, "Purifier failed: no elements found.");
      return;
    }
    List<ItemStack> outputs = new ArrayList<>();
    try {
      for (Map.Entry<String, Integer> entry : atoms.entrySet()) {
        String elementSmiles = chemistry.elementalSmiles(entry.getKey());
        outputs.add(items.createChemItem(elementSmiles, moles * entry.getValue(), config.densityOf(elementSmiles)));
      }
    } catch (InvalidSmilesException ex) {
      Chat.err(player, "Purifier failed: could not create elemental items.");
      return;
    }
    held.setAmount(held.getAmount() - 1);
    for (ItemStack out : outputs) items.give(player, out);
    Chat.ok(player, "Purified " + smiles + " into " + outputs.size() + " elemental item(s).");
  }
}
