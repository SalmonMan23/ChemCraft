package org.sutormin.chemcraft.chemistry;

import java.util.List;
import java.util.Map;

public record MoleculeInfo(
  String smiles,
  Map<String, Integer> atoms,
  int heavyAtoms,
  double molarMass,
  int charge,
  boolean hasH,
  boolean hasO,
  boolean hasHalogen,
  boolean hasMetal,
  boolean isElementalActiveMetal,
  List<String> traits
) {}