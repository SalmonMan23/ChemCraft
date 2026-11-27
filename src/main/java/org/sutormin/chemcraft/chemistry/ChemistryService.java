package org.sutormin.chemcraft.chemistry;

import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmiFlavor;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChemistryService {
  private final ThreadLocal<SmilesParser> parsers = ThreadLocal.withInitial(() -> new SmilesParser(SilentChemObjectBuilder.getInstance()));
  private final ThreadLocal<SmilesGenerator> generators = ThreadLocal.withInitial(() -> new SmilesGenerator(SmiFlavor.Canonical));
  private final Map<String, MoleculeInfo> cache = new ConcurrentHashMap<>();

  public MoleculeInfo analyze(String smiles) throws InvalidSmilesException {
    MoleculeInfo cached = cache.get(smiles);
    if (cached != null) return cached;
    IAtomContainer mol = parsers.get().parseSmiles(smiles);
    Map<String, Integer> atoms = new LinkedHashMap<>();
    int charge = 0;
    double mass = 0.0;
    for (IAtom atom : mol.atoms()) {
      String sym = atom.getSymbol();
      atoms.merge(sym, 1, Integer::sum);
      mass += atomicMass(sym);
      Integer impH = atom.getImplicitHydrogenCount();
      if (impH != null && impH > 0) {
        atoms.merge("H", impH, Integer::sum);
        mass += impH * atomicMass("H");
      }
      Integer formalCharge = atom.getFormalCharge();
      if (formalCharge != null) charge += formalCharge;
    }
    String formula = toHillFormula(atoms);
    int heavy = atoms.entrySet().stream().filter(e -> !"H".equals(e.getKey())).mapToInt(Map.Entry::getValue).sum();
    boolean hasH = atoms.getOrDefault("H", 0) > 0;
    boolean hasO = atoms.getOrDefault("O", 0) > 0;
    boolean hasHalogen = atoms.containsKey("F") || atoms.containsKey("Cl") || atoms.containsKey("Br") || atoms.containsKey("I");
    boolean hasMetal = atoms.keySet().stream().anyMatch(ChemistryService::isMetal);
    boolean elementalActiveMetal = charge == 0 && heavy == 1 && atoms.keySet().stream().anyMatch(ChemistryService::isActiveMetal) && atoms.getOrDefault("H", 0) == 0;
    boolean isWater = isWaterSmiles(smiles, atoms, charge);
    boolean isCarbonateLike = hasCarbonatePattern(atoms, charge);
    boolean isHydroxideLike = isHydroxide(atoms, charge, hasMetal);
    boolean isAcidLike = isAcid(atoms, charge, hasMetal, hasHalogen, hasO, hasH, smiles);
    boolean isBaseLike = isBase(atoms, charge, hasMetal, hasO, hasH, isHydroxideLike);
    List<String> traits = new ArrayList<>();
    if (elementalActiveMetal) traits.add("elemental_active_metal");
    else if (hasMetal) traits.add("metal_bearing");
    if (isAcidLike) traits.add("acid_like");
    if (isBaseLike) traits.add("base_like");
    if (isCarbonateLike) traits.add("carbonate_like");
    if (traits.isEmpty()) traits.add("neutral_species");
    MoleculeInfo info = new MoleculeInfo(smiles, formula, Map.copyOf(atoms), heavy, mass, charge, hasH, hasO, hasHalogen, hasMetal, elementalActiveMetal, isWater, isCarbonateLike, isHydroxideLike, isAcidLike, isBaseLike, List.copyOf(traits));
    cache.put(smiles, info);
    return info;
  }

  public IAtomContainer parse(String smiles) throws InvalidSmilesException {
    return parsers.get().parseSmiles(smiles);
  }

  public String canonicalSmiles(IAtomContainer mol) {
    try {
      return generators.get().create(mol);
    } catch (Exception ex) {
      return mol.getAtomCount() + "-atoms";
    }
  }

  public String elementalSmiles(String symbol) {
    String smiles = "[" + symbol + "]";
    try {
      analyze(smiles);
      return smiles;
    } catch (Exception ex) {
      return smiles;
    }
  }

  public Map<String, Integer> elementalCounts(String smiles) throws InvalidSmilesException {
    return analyze(smiles).atoms();
  }

  public void clearCache() {
    cache.clear();
  }

  public double estimateDensity(MoleculeInfo info) {
    if (info.isWater()) return 1.0;
    Map<String, Integer> atoms = info.atoms();
    int heavy = info.heavyAtoms();
    if (heavy == 0) return 0.00009;
    if (heavy == 1 && atoms.containsKey("H")) return 0.00009;
    if (heavy <= 2 && (atoms.containsKey("N") || atoms.containsKey("O") || atoms.containsKey("F") || atoms.containsKey("Cl") || atoms.containsKey("Xe")) && !info.hasMetal() && atoms.getOrDefault("C", 0) == 0) {
      if (atoms.containsKey("Xe")) return 0.0059;
      if (atoms.containsKey("Cl")) return 0.0032;
      if (atoms.containsKey("O")) return 0.0014;
      if (atoms.containsKey("N")) return 0.0013;
      if (atoms.containsKey("F")) return 0.0017;
      return 0.001;
    }
    if (heavy == 1 && info.hasMetal()) {
      String metal = atoms.keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse("Fe");
      return elementalDensity(metal);
    }
    double massSum = 0.0;
    double volumeSum = 0.0;
    for (Map.Entry<String, Integer> entry : atoms.entrySet()) {
      String sym = entry.getKey();
      int count = entry.getValue();
      double atomMass = atomicMass(sym) * count;
      double dens = elementalDensity(sym);
      massSum += atomMass;
      volumeSum += atomMass / dens;
    }
    if (volumeSum <= 1e-12) return 1.0;
    double estimated = massSum / volumeSum;
    if (info.hasMetal() && !info.hasO() && !info.hasHalogen()) return Math.max(estimated, 2.0);
    if (info.atoms().getOrDefault("C", 0) > 0 && !info.hasMetal()) return clamp(estimated * 0.85, 0.7, 2.2);
    return clamp(estimated, 0.5, 22.0);
  }

  private static double elementalDensity(String sym) {
    return switch (sym) {
      case "H" -> 0.00009;
      case "Li" -> 0.53;
      case "Be" -> 1.85;
      case "B" -> 2.34;
      case "C" -> 2.26;
      case "N" -> 0.0013;
      case "O" -> 0.0014;
      case "F" -> 0.0017;
      case "Na" -> 0.97;
      case "Mg" -> 1.74;
      case "Al" -> 2.70;
      case "Si" -> 2.33;
      case "P" -> 1.82;
      case "S" -> 2.07;
      case "Cl" -> 0.0032;
      case "K" -> 0.86;
      case "Ca" -> 1.55;
      case "Ti" -> 4.51;
      case "V" -> 6.11;
      case "Cr" -> 7.19;
      case "Mn" -> 7.47;
      case "Fe" -> 7.87;
      case "Co" -> 8.90;
      case "Ni" -> 8.91;
      case "Cu" -> 8.96;
      case "Zn" -> 7.14;
      case "Br" -> 3.12;
      case "Rb" -> 1.53;
      case "Sr" -> 2.64;
      case "Ag" -> 10.49;
      case "Cd" -> 8.65;
      case "Sn" -> 7.31;
      case "I" -> 4.93;
      case "Xe" -> 0.0059;
      case "Cs" -> 1.93;
      case "Ba" -> 3.62;
      case "Pt" -> 21.45;
      case "Au" -> 19.32;
      case "Pb" -> 11.34;
      case "U" -> 19.1;
      default -> 1.5;
    };
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  public static String toSubscript(String formula) {
    StringBuilder sb = new StringBuilder(formula.length());
    for (int i = 0; i < formula.length(); i++) {
      char c = formula.charAt(i);
      if (c >= '0' && c <= '9') sb.append((char) ('₀' + (c - '0')));
      else sb.append(c);
    }
    return sb.toString();
  }

  public static String fmt(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }

  public static boolean isActiveMetal(String sym) {
    return switch (sym) {
      case "Li", "Na", "K", "Rb", "Cs", "Ca", "Sr", "Ba" -> true;
      default -> false;
    };
  }

  public static boolean isMetal(String sym) {
    if (isActiveMetal(sym)) return true;
    return switch (sym) {
      case "Be", "Mg", "Al", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ag", "Cd", "Sn", "Au", "Pb", "Pt", "U" -> true;
      default -> false;
    };
  }

  private static String toHillFormula(Map<String, Integer> atoms) {
    StringBuilder sb = new StringBuilder();
    List<String> keys = new ArrayList<>(atoms.keySet());
    keys.sort(Comparator.comparing((String s) -> {
      if ("C".equals(s)) return "0";
      if ("H".equals(s)) return "1";
      return "2" + s;
    }));
    for (String key : keys) {
      int count = atoms.get(key);
      sb.append(key);
      if (count > 1) sb.append(count);
    }
    return sb.toString();
  }

  private static double atomicMass(String sym) {
    return switch (sym) {
      case "H" -> 1.008;
      case "B" -> 10.81;
      case "C" -> 12.011;
      case "N" -> 14.007;
      case "O" -> 15.999;
      case "F" -> 18.998;
      case "Na" -> 22.990;
      case "Mg" -> 24.305;
      case "Al" -> 26.982;
      case "Si" -> 28.085;
      case "P" -> 30.974;
      case "S" -> 32.06;
      case "Cl" -> 35.45;
      case "K" -> 39.098;
      case "Ca" -> 40.078;
      case "Ti" -> 47.867;
      case "V" -> 50.942;
      case "Cr" -> 51.996;
      case "Mn" -> 54.938;
      case "Fe" -> 55.845;
      case "Co" -> 58.933;
      case "Ni" -> 58.693;
      case "Cu" -> 63.546;
      case "Zn" -> 65.38;
      case "Br" -> 79.904;
      case "Rb" -> 85.468;
      case "Sr" -> 87.62;
      case "Ag" -> 107.868;
      case "Cd" -> 112.414;
      case "Sn" -> 118.710;
      case "I" -> 126.90;
      case "Xe" -> 131.293;
      case "Cs" -> 132.905;
      case "Ba" -> 137.327;
      case "Pt" -> 195.084;
      case "Au" -> 196.967;
      case "Pb" -> 207.2;
      case "U" -> 238.029;
      case "Be" -> 9.012;
      case "Li" -> 6.94;
      default -> 12.0;
    };
  }

  private static boolean isWaterSmiles(String smiles, Map<String, Integer> atoms, int charge) {
    if ("O".equals(smiles)) return true;
    return charge == 0 && atoms.getOrDefault("O", 0) == 1 && atoms.getOrDefault("H", 0) == 2 && atoms.size() == 2;
  }

  private static boolean hasCarbonatePattern(Map<String, Integer> atoms, int charge) {
    return atoms.getOrDefault("C", 0) >= 1 && atoms.getOrDefault("O", 0) >= 3 && (charge <= -1 || atoms.containsKey("Ca") || atoms.containsKey("Na") || atoms.containsKey("K") || atoms.containsKey("Mg"));
  }

  private static boolean isHydroxide(Map<String, Integer> atoms, int charge, boolean hasMetal) {
    return atoms.getOrDefault("O", 0) >= 1 && atoms.getOrDefault("H", 0) >= 1 && (charge < 0 || hasMetal) && atoms.getOrDefault("C", 0) == 0;
  }

  private static boolean isAcid(Map<String, Integer> atoms, int charge, boolean hasMetal, boolean hasHalogen, boolean hasO, boolean hasH, String smiles) {
    if (hasMetal) return false;
    if (charge > 0 && hasH) return true;
    if (hasH && hasHalogen && atoms.size() <= 3) return true;
    if (hasH && hasO && (atoms.containsKey("S") || atoms.containsKey("N") || atoms.containsKey("P") || atoms.getOrDefault("C", 0) > 0)) return true;
    return smiles.contains("(=O)") && hasH && hasO && !hasMetal;
  }

  private static boolean isBase(Map<String, Integer> atoms, int charge, boolean hasMetal, boolean hasO, boolean hasH, boolean hydroxideLike) {
    if (hydroxideLike) return true;
    if (charge < 0 && hasO) return true;
    return hasMetal && hasO && hasH;
  }
}
