package org.sutormin.chemcraft.chemistry;

import java.util.List;
import java.util.Map;

public record MoleculeInfo(String smiles, String formula, Map<String, Integer> atoms, int heavyAtoms, double molarMass, int charge, boolean hasH, boolean hasO, boolean hasHalogen, boolean hasMetal, boolean isElementalActiveMetal, boolean isWater, boolean isCarbonateLike, boolean isHydroxideLike, boolean isAcidLike, boolean isBaseLike, List<String> traits) {
  public String formulaSubscript() {
    return ChemistryService.toSubscript(formula);
  }

  public String phase(double density) {
    if (density < 0.1) return "gas";
    if (density < 1.5) return "liquid";
    return "solid";
  }
}
