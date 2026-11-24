package org.sutormin.chemcraft.chemistry;

import org.openscience.cdk.exception.InvalidSmilesException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ChemistryEngine {

  private final SmilesParser smilesParser =
      new SmilesParser(SilentChemObjectBuilder.getInstance());
  private final Map<String, MoleculeInfo> cache = new ConcurrentHashMap<>();

  public record SimulationResult(Map<String, Double> resultingPool, List<String> logs) {}

  public MoleculeInfo analyze(String smiles) throws InvalidSmilesException {
    MoleculeInfo cached = cache.get(smiles);
    if (cached != null) {
      return cached;
    }

    IAtomContainer mol = smilesParser.parseSmiles(smiles);
    Map<String, Integer> atoms = new HashMap<>();
    int charge = 0;
    double mass = 0.0;

    for (IAtom atom : mol.atoms()) {
      String sym = atom.getSymbol();
      atoms.merge(sym, 1, Integer::sum);

      Integer impH = atom.getImplicitHydrogenCount();
      if (impH != null && impH > 0) {
        atoms.merge("H", impH, Integer::sum);
      }

      Integer formalCharge = atom.getFormalCharge();
      if (formalCharge != null) {
        charge += formalCharge;
      }

      Double exactMass = atom.getExactMass();
      mass += (exactMass != null ? exactMass : fallbackMass(sym));
    }

    int hCount = atoms.getOrDefault("H", 0);
    mass += hCount * 1.008;

    int heavy = atoms.entrySet().stream()
        .filter(e -> !e.getKey().equals("H"))
        .mapToInt(Map.Entry::getValue)
        .sum();

    boolean hasH = hCount > 0;
    boolean hasO = atoms.getOrDefault("O", 0) > 0;
    boolean hasHalogen = atoms.containsKey("F") || atoms.containsKey("Cl") || atoms.containsKey("Br") || atoms.containsKey("I");
    boolean hasMetal = atoms.keySet().stream().anyMatch(ChemistryEngine::isMetal);
    boolean isElementalActiveMetal = charge == 0 && heavy == 1 && atoms.keySet().stream().anyMatch(ChemistryEngine::isActiveMetal);

    List<String> traits = new ArrayList<>();
    if (isElementalActiveMetal) {
      traits.add("elemental_active_metal");
    } else if (hasMetal) {
      traits.add("metal_bearing");
    }
    if (hasH && (hasHalogen || hasO) && !hasMetal) {
      traits.add("acid_like");
    }
    if (hasO && hasH && (hasMetal || charge < 0)) {
      traits.add("base_like");
    }
    if (traits.isEmpty()) {
      traits.add("neutral_species");
    }

    MoleculeInfo info = new MoleculeInfo(
      smiles,
      atoms,
      heavy,
      mass,
      charge,
      hasH,
      hasO,
      hasHalogen,
      hasMetal,
      isElementalActiveMetal,
      traits
    );
    cache.put(smiles, info);
    return info;
  }

  public SimulationResult simulate(Map<String, Double> initialPool) throws InvalidSmilesException {
    Map<String, Double> pool = new HashMap<>(initialPool);
    List<String> logs = new ArrayList<>();

    Map<String, MoleculeInfo> infos = new HashMap<>();
    for (String smi : pool.keySet()) {
      infos.put(smi, analyze(smi));
    }

    List<String> acids = pool.keySet().stream()
        .filter(s -> infos.get(s).hasH() && (infos.get(s).hasHalogen() || infos.get(s).hasO()) && !infos.get(s).hasMetal())
        .toList();
    List<String> bases = pool.keySet().stream()
        .filter(s -> infos.get(s).hasO() && infos.get(s).hasH() && (infos.get(s).hasMetal() || infos.get(s).charge() < 0))
        .toList();

    for (String a : acids) {
      for (String b : bases) {
        if (a.equals(b)) continue;
        double ext = Math.min(pool.getOrDefault(a, 0.0), pool.getOrDefault(b, 0.0));
        if (ext > 1e-9) {
          addMoles(pool, a, -ext);
          addMoles(pool, b, -ext);
          addMoles(pool, "O", ext);
          addMoles(pool, "[" + a + "." + b + "]", ext);
          logs.add("Neutralized " + fmt(ext) + " mol (" + a + " + " + b + ")");
        }
      }
    }

    for (String m : List.copyOf(pool.keySet())) {
      MoleculeInfo mInfo = infos.get(m);
      if (mInfo == null || !mInfo.isElementalActiveMetal()) continue;
      for (String h : List.copyOf(pool.keySet())) {
        if (m.equals(h)) continue;
        MoleculeInfo hInfo = infos.get(h);
        if (hInfo == null || !hInfo.hasH() || hInfo.hasMetal()) continue;
        double ext = Math.min(pool.getOrDefault(m, 0.0), pool.getOrDefault(h, 0.0));
        if (ext > 1e-9) {
          int hAtoms = hInfo.atoms().getOrDefault("H", 1);
          addMoles(pool, m, -ext);
          addMoles(pool, h, -ext);
          addMoles(pool, "[HH]", 0.5 * hAtoms * ext);
          addMoles(pool, "[" + m + "+" + h + "]", ext);
          logs.add("Metal+H: " + fmt(ext) + " mol " + m + " + " + h);
        }
      }
    }

    return new SimulationResult(pool, logs);
  }

  private static boolean isActiveMetal(String sym) {
    return switch (sym) {
      case "Li", "Na", "K", "Rb", "Cs", "Ca", "Sr", "Ba" -> true;
      default -> false;
    };
  }

  private static boolean isMetal(String sym) {
    if (isActiveMetal(sym)) return true;
    return switch (sym) {
      case "Be", "Mg", "Al", "Ti", "V", "Cr", "Mn", "Fe", "Co", "Ni", "Cu", "Zn", "Ag", "Cd", "Sn", "Au", "Pb" -> true;
      default -> false;
    };
  }

  private static double fallbackMass(String sym) {
    return switch (sym) {
      case "C" -> 12.011;
      case "N" -> 14.007;
      case "O" -> 15.999;
      case "F" -> 18.998;
      case "Na" -> 22.990;
      case "S" -> 32.060;
      case "Cl" -> 35.450;
      case "K" -> 39.098;
      case "Ca" -> 40.078;
      case "Fe" -> 55.845;
      case "Cu" -> 63.546;
      default -> 12.0;
    };
  }

  private static void addMoles(Map<String, Double> pool, String smi, double delta) {
    double next = pool.getOrDefault(smi, 0.0) + delta;
    if (next <= 1e-9) {
      pool.remove(smi);
    } else {
      pool.put(smi, next);
    }
  }

  private static String fmt(double val) {
    return String.format(Locale.ROOT, "%.4f", val);
  }
}
