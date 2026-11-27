package org.sutormin.chemcraft.chemistry;

import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.lab.LabType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ReactionEngine {
  private static final double EPS = 1e-9;
  private static final String WATER = "O";
  private static final String HYDROGEN = "[H][H]";
  private static final String OXYGEN = "O=O";
  private static final String CO2 = "O=C=O";
  private static final String CHLORINE = "ClCl";
  private static final String NITROGEN = "N#N";

  private final ChemistryService chemistry;

  public record SimulationResult(Map<String, Double> pool, List<String> logs, boolean reacted) {}

  public ReactionEngine(ChemistryService chemistry) {
    this.chemistry = chemistry;
  }

  public SimulationResult simulate(Map<String, Double> initialPool, ReactionConditions conditions) throws InvalidSmilesException {
    Map<String, Double> pool = new HashMap<>();
    for (Map.Entry<String, Double> e : initialPool.entrySet()) {
      if (e.getValue() != null && e.getValue() > EPS) pool.put(e.getKey(), e.getValue());
    }
    List<String> logs = new ArrayList<>();
    Map<String, MoleculeInfo> infos = analyzeAll(pool);
    boolean any = false;
    boolean progressed;
    int guard = 0;
    do {
      progressed = false;
      guard++;
      if (applyElectrolysisWater(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyElectrolysisBrine(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyCombustion(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyCarbonateCalcination(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyOxideReduction(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyMetalSteam(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyPeroxideDecomposition(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyHaber(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyAcidBase(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyAcidCarbonate(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyMetalAcid(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyMetalWater(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applySingleDisplacement(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyHalogenDisplacement(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyEsterification(pool, infos, logs, conditions)) { progressed = true; any = true; }
      else if (applyIonicMetathesis(pool, infos, logs, conditions)) { progressed = true; any = true; }
      infos = analyzeAll(pool);
    } while (progressed && guard < 96);
    cleanup(pool);
    return new SimulationResult(pool, logs, any);
  }

  private Map<String, MoleculeInfo> analyzeAll(Map<String, Double> pool) throws InvalidSmilesException {
    Map<String, MoleculeInfo> infos = new HashMap<>();
    for (String smiles : pool.keySet()) infos.put(smiles, chemistry.analyze(smiles));
    return infos;
  }

  private boolean applyElectrolysisWater(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    if (c.labType() != LabType.ELECTRICAL || c.voltageV() + EPS < 1.23) return false;
    String water = find(pool, infos, MoleculeInfo::isWater);
    if (water == null) return false;
    double amount = Math.min(pool.getOrDefault(water, 0.0), 1.0);
    if (amount <= EPS) return false;
    consume(pool, water, amount);
    add(pool, HYDROGEN, amount);
    add(pool, OXYGEN, 0.5 * amount);
    logs.add(fmt("Electrolysis of water at %.2f V: %.4f mol H2O -> H2 + 0.5 O2", c.voltageV(), amount));
    return true;
  }

  private boolean applyElectrolysisBrine(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    if (c.labType() != LabType.ELECTRICAL || c.voltageV() + EPS < 2.0) return false;
    String salt = null;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info != null && info.smiles().contains("[Na+") && info.smiles().contains("Cl-") && pool.getOrDefault(s, 0.0) > EPS) { salt = s; break; }
    }
    String water = find(pool, infos, MoleculeInfo::isWater);
    if (salt == null || water == null) return false;
    double amount = Math.min(pool.getOrDefault(salt, 0.0), pool.getOrDefault(water, 0.0));
    if (amount <= EPS) return false;
    consume(pool, salt, amount);
    consume(pool, water, amount);
    add(pool, "[Na+].[OH-]", amount);
    add(pool, CHLORINE, 0.5 * amount);
    add(pool, HYDROGEN, 0.5 * amount);
    logs.add(fmt("Brine electrolysis at %.2f V: %.4f mol NaCl + H2O -> NaOH + Cl2 + H2", c.voltageV(), amount));
    return true;
  }

  private boolean applyCombustion(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    if (c.temperatureK() + EPS < 500.0) return false;
    String fuel = null;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info == null) continue;
      boolean carbonaceous = info.atoms().getOrDefault("C", 0) > 0 && !info.hasMetal() && !info.isAcidLike();
      boolean elementalCarbon = info.atoms().size() == 1 && info.atoms().containsKey("C");
      if ((carbonaceous || elementalCarbon) && pool.getOrDefault(s, 0.0) > EPS) { fuel = s; break; }
    }
    String oxidizer = null;
    for (String s : pool.keySet()) {
      if (s.equals(OXYGEN) || "O=O".equals(s) || "[O]".equals(s)) { oxidizer = s; break; }
    }
    if (fuel == null || oxidizer == null) return false;
    MoleculeInfo fuelInfo = infos.get(fuel);
    int cAtoms = fuelInfo.atoms().getOrDefault("C", 0);
    int hAtoms = fuelInfo.atoms().getOrDefault("H", 0);
    double oNeed = cAtoms + 0.25 * hAtoms;
    if (oNeed <= EPS) oNeed = 1.0;
    double maxFuel = pool.getOrDefault(fuel, 0.0);
    double maxOx = pool.getOrDefault(oxidizer, 0.0) / oNeed;
    double amount = Math.min(maxFuel, maxOx);
    if (amount <= EPS) return false;
    consume(pool, fuel, amount);
    consume(pool, oxidizer, amount * oNeed);
    if (cAtoms > 0) add(pool, CO2, amount * cAtoms);
    if (hAtoms > 0) add(pool, WATER, amount * 0.5 * hAtoms);
    logs.add(fmt("Combustion at %.0f K: %.4f mol %s + O2 -> CO2/H2O", c.temperatureK(), amount, fuel));
    return true;
  }

  private boolean applyCarbonateCalcination(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    if (c.temperatureK() + EPS < 1100.0) return false;
    String carbonate = find(pool, infos, MoleculeInfo::isCarbonateLike);
    if (carbonate == null) return false;
    double amount = pool.getOrDefault(carbonate, 0.0);
    if (amount <= EPS) return false;
    MoleculeInfo info = infos.get(carbonate);
    String metal = info.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse("Ca");
    consume(pool, carbonate, amount);
    add(pool, "[" + metal + "]=O", amount);
    add(pool, CO2, amount);
    logs.add(fmt("Calcination at %.0f K: %.4f mol carbonate -> oxide + CO2", c.temperatureK(), amount));
    return true;
  }

  private boolean applyOxideReduction(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    if (c.temperatureK() + EPS < 900.0) return false;
    String oxide = null;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info != null && info.hasMetal() && info.hasO() && !info.hasH() && info.atoms().getOrDefault("C", 0) == 0 && pool.getOrDefault(s, 0.0) > EPS) { oxide = s; break; }
    }
    String carbon = null;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info != null && info.atoms().containsKey("C") && info.heavyAtoms() == 1 && pool.getOrDefault(s, 0.0) > EPS) { carbon = s; break; }
    }
    if (oxide == null || carbon == null) return false;
    double amount = Math.min(pool.getOrDefault(oxide, 0.0), pool.getOrDefault(carbon, 0.0));
    if (amount <= EPS) return false;
    MoleculeInfo oxideInfo = infos.get(oxide);
    String metal = oxideInfo.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse("Fe");
    consume(pool, oxide, amount);
    consume(pool, carbon, amount);
    add(pool, "[" + metal + "]", amount);
    add(pool, CO2, amount);
    logs.add(fmt("Carbothermic reduction at %.0f K: %.4f mol %s + C -> [%s] + CO2", c.temperatureK(), amount, oxide, metal));
    return true;
  }

  private boolean applyMetalSteam(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    if (c.temperatureK() + EPS < 800.0) return false;
    String metal = null;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info != null && info.hasMetal() && info.heavyAtoms() == 1 && info.charge() == 0 && !info.isElementalActiveMetal() && pool.getOrDefault(s, 0.0) > EPS) {
        String sym = info.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse("");
        if (sym.equals("Fe") || sym.equals("Zn") || sym.equals("Mg") || sym.equals("Al")) { metal = s; break; }
      }
    }
    String water = find(pool, infos, MoleculeInfo::isWater);
    if (metal == null || water == null) return false;
    double amount = Math.min(pool.getOrDefault(metal, 0.0), pool.getOrDefault(water, 0.0));
    if (amount <= EPS) return false;
    MoleculeInfo metalInfo = infos.get(metal);
    String symbol = metalInfo.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse("Fe");
    consume(pool, metal, amount);
    consume(pool, water, amount);
    add(pool, "O=[" + symbol + "]O[" + symbol + "]=O", amount * 0.5);
    add(pool, HYDROGEN, amount);
    logs.add(fmt("Metal + steam at %.0f K: %.4f mol %s + H2O -> oxide + H2", c.temperatureK(), amount, symbol));
    return true;
  }

  private boolean applyPeroxideDecomposition(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    String peroxide = null;
    for (String s : pool.keySet()) {
      if (s.equals("OO") || s.equals("HOOH") || s.equals("[H]OO[H]")) { peroxide = s; break; }
    }
    if (peroxide == null) return false;
    boolean catalyzed = false;
    for (MoleculeInfo info : infos.values()) {
      if (info != null && (info.hasMetal() || info.atoms().containsKey("I"))) { catalyzed = true; break; }
    }
    if (c.temperatureK() + EPS < 350.0 && !catalyzed) return false;
    double amount = pool.getOrDefault(peroxide, 0.0);
    if (amount <= EPS) return false;
    consume(pool, peroxide, amount);
    add(pool, WATER, amount);
    add(pool, OXYGEN, 0.5 * amount);
    logs.add(fmt("H2O2 decomposition: %.4f mol -> H2O + 0.5 O2", amount));
    return true;
  }

  private boolean applyHaber(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    if (c.labType() != LabType.FURNACE || c.temperatureK() + EPS < 650.0) return false;
    String n2 = pool.containsKey(NITROGEN) ? NITROGEN : (pool.containsKey("N#N") ? "N#N" : null);
    String h2 = pool.containsKey(HYDROGEN) ? HYDROGEN : null;
    if (n2 == null || h2 == null) return false;
    double amount = Math.min(pool.getOrDefault(n2, 0.0), pool.getOrDefault(h2, 0.0) / 3.0);
    if (amount <= EPS) return false;
    consume(pool, n2, amount);
    consume(pool, h2, 3.0 * amount);
    add(pool, "N", 2.0 * amount);
    logs.add(fmt("Haber-Bosch at %.0f K / %.0f atm: %.4f mol N2 + 3 H2 -> 2 NH3", c.temperatureK(), c.pressureAtm(), amount));
    return true;
  }

  private boolean applyAcidBase(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) throws InvalidSmilesException {
    String acid = find(pool, infos, i -> i.isAcidLike() && !i.isBaseLike());
    String base = find(pool, infos, i -> i.isBaseLike() && !i.isAcidLike());
    if (acid == null || base == null || acid.equals(base)) return false;
    double amount = Math.min(pool.getOrDefault(acid, 0.0), pool.getOrDefault(base, 0.0));
    if (amount <= EPS) return false;
    consume(pool, acid, amount);
    consume(pool, base, amount);
    add(pool, WATER, amount);
    String salt = buildSaltSmiles(infos.get(acid), infos.get(base), acid, base);
    add(pool, salt, amount);
    logs.add(fmt("Acid-base neutralization at %.0f K: %.4f mol -> H2O + %s", c.temperatureK(), amount, salt));
    return true;
  }

  private boolean applyAcidCarbonate(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    String acid = find(pool, infos, MoleculeInfo::isAcidLike);
    String carbonate = find(pool, infos, MoleculeInfo::isCarbonateLike);
    if (acid == null || carbonate == null || acid.equals(carbonate)) return false;
    double amount = Math.min(pool.getOrDefault(acid, 0.0), pool.getOrDefault(carbonate, 0.0));
    if (amount <= EPS) return false;
    consume(pool, acid, amount);
    consume(pool, carbonate, amount);
    add(pool, WATER, amount);
    add(pool, CO2, amount);
    String salt = buildResidualSalt(infos.get(acid), infos.get(carbonate));
    add(pool, salt, amount);
    logs.add(fmt("Acid-carbonate at %.0f K: %.4f mol -> H2O + CO2 + salt", c.temperatureK(), amount));
    return true;
  }

  private boolean applyMetalAcid(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    String metal = find(pool, infos, MoleculeInfo::isElementalActiveMetal);
    String acid = find(pool, infos, MoleculeInfo::isAcidLike);
    if (metal == null || acid == null) return false;
    double amount = Math.min(pool.getOrDefault(metal, 0.0), pool.getOrDefault(acid, 0.0));
    if (amount <= EPS) return false;
    consume(pool, metal, amount);
    consume(pool, acid, amount);
    add(pool, HYDROGEN, 0.5 * amount);
    String salt = buildMetalSalt(infos.get(metal), infos.get(acid), metal, acid);
    add(pool, salt, amount);
    logs.add(fmt("Active metal + acid at %.0f K: %.4f mol -> salt + H2", c.temperatureK(), amount));
    return true;
  }

  private boolean applyMetalWater(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    String metal = find(pool, infos, MoleculeInfo::isElementalActiveMetal);
    String water = find(pool, infos, MoleculeInfo::isWater);
    if (metal == null || water == null) return false;
    double amount = Math.min(pool.getOrDefault(metal, 0.0), pool.getOrDefault(water, 0.0));
    if (amount <= EPS) return false;
    MoleculeInfo metalInfo = infos.get(metal);
    String symbol = metalInfo.atoms().keySet().stream().filter(ChemistryService::isActiveMetal).findFirst().orElse("Na");
    boolean alkalineEarth = symbol.equals("Ca") || symbol.equals("Sr") || symbol.equals("Ba");
    double waterNeed = alkalineEarth ? 2.0 * amount : amount;
    if (pool.getOrDefault(water, 0.0) + EPS < waterNeed) {
      amount = pool.getOrDefault(water, 0.0) / (alkalineEarth ? 2.0 : 1.0);
      waterNeed = alkalineEarth ? 2.0 * amount : amount;
    }
    if (amount <= EPS) return false;
    consume(pool, metal, amount);
    consume(pool, water, waterNeed);
    add(pool, HYDROGEN, alkalineEarth ? amount : 0.5 * amount);
    String hydroxide = alkalineEarth ? ("[" + symbol + "+2].[OH-].[OH-]") : ("[" + symbol + "+].[OH-]");
    add(pool, hydroxide, amount);
    logs.add(fmt("Active metal + water at %.0f K: %.4f mol %s -> hydroxide + H2", c.temperatureK(), amount, symbol));
    return true;
  }

  private boolean applySingleDisplacement(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    String metal = null;
    int metalRank = Integer.MAX_VALUE;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info == null || info.heavyAtoms() != 1 || info.charge() != 0) continue;
      String sym = info.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse(null);
      if (sym == null) continue;
      int rank = activityRank(sym);
      if (rank < metalRank) { metalRank = rank; metal = s; }
    }
    if (metal == null) return false;
    String salt = null;
    String saltMetal = null;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info == null || !info.smiles().contains(".") || !info.hasMetal()) continue;
      String sym = info.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse(null);
      if (sym == null) continue;
      if (activityRank(sym) > metalRank) { salt = s; saltMetal = sym; break; }
    }
    if (salt == null) return false;
    double amount = Math.min(pool.getOrDefault(metal, 0.0), pool.getOrDefault(salt, 0.0));
    if (amount <= EPS) return false;
    MoleculeInfo metalInfo = infos.get(metal);
    String incoming = metalInfo.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse("Zn");
    String[] parts = salt.split("\\.", 2);
    String anion = parts.length > 1 ? parts[1] : "[Cl-]";
    consume(pool, metal, amount);
    consume(pool, salt, amount);
    add(pool, "[" + incoming + "+]." + anion, amount);
    add(pool, "[" + saltMetal + "]", amount);
    logs.add(fmt("Single displacement at %.0f K: %.4f mol %s displaces %s", c.temperatureK(), amount, incoming, saltMetal));
    return true;
  }

  private boolean applyHalogenDisplacement(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    String halogen = null;
    int halogenRank = Integer.MAX_VALUE;
    for (String s : List.of("F", "F2", "FF", "Cl", "ClCl", "Br", "BrBr", "I", "II")) {
      if (pool.getOrDefault(s, 0.0) > EPS) {
        int rank = halogenRank(s);
        if (rank < halogenRank) { halogenRank = rank; halogen = s; }
      }
    }
    if (halogen == null) return false;
    String salt = null;
    String displaced = null;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info == null || !info.smiles().contains(".")) continue;
      for (String h : List.of("F", "Cl", "Br", "I")) {
        if (info.atoms().containsKey(h) && halogenRank(h) > halogenRank) { salt = s; displaced = h; break; }
      }
      if (salt != null) break;
    }
    if (salt == null) return false;
    double amount = Math.min(pool.getOrDefault(halogen, 0.0), pool.getOrDefault(salt, 0.0));
    if (amount <= EPS) return false;
    String[] parts = salt.split("\\.", 2);
    String cation = parts[0];
    String newHal = halogen.contains("F") ? "F" : halogen.contains("Cl") ? "Cl" : halogen.contains("Br") ? "Br" : "I";
    consume(pool, halogen, amount);
    consume(pool, salt, amount);
    add(pool, cation + ".[" + newHal + "-]", amount);
    add(pool, displaced + displaced, 0.5 * amount);
    logs.add(fmt("Halogen displacement at %.0f K: %.4f mol exchange", c.temperatureK(), amount));
    return true;
  }

  private boolean applyEsterification(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    if (c.temperatureK() + EPS < 330.0) return false;
    String acid = null;
    String alcohol = null;
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info == null) continue;
      if (info.isAcidLike() && info.atoms().getOrDefault("C", 0) > 0 && !info.hasMetal()) acid = s;
      if (!info.isAcidLike() && info.hasO() && info.hasH() && info.atoms().getOrDefault("C", 0) > 0 && !info.hasMetal() && s.contains("O")) alcohol = s;
    }
    if (acid == null || alcohol == null || acid.equals(alcohol)) return false;
    double amount = Math.min(pool.getOrDefault(acid, 0.0), pool.getOrDefault(alcohol, 0.0)) * 0.5;
    if (amount <= EPS) return false;
    consume(pool, acid, amount);
    consume(pool, alcohol, amount);
    add(pool, WATER, amount);
    add(pool, "CCOC(=O)C", amount);
    logs.add(fmt("Esterification at %.0f K: %.4f mol acid + alcohol -> ester + H2O", c.temperatureK(), amount));
    return true;
  }

  private boolean applyIonicMetathesis(Map<String, Double> pool, Map<String, MoleculeInfo> infos, List<String> logs, ReactionConditions c) {
    List<String> ionics = new ArrayList<>();
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info != null && info.smiles().contains(".") && info.hasMetal() && pool.getOrDefault(s, 0.0) > EPS) ionics.add(s);
    }
    if (ionics.size() < 2) return false;
    String a = ionics.get(0);
    String b = ionics.get(1);
    double amount = Math.min(pool.getOrDefault(a, 0.0), pool.getOrDefault(b, 0.0));
    if (amount <= EPS) return false;
    String[] partsA = a.split("\\.");
    String[] partsB = b.split("\\.");
    if (partsA.length < 2 || partsB.length < 2) return false;
    String product1 = partsA[0] + "." + partsB[partsB.length - 1];
    String product2 = partsB[0] + "." + partsA[partsA.length - 1];
    if (product1.equals(a) || product1.equals(b)) return false;
    consume(pool, a, amount);
    consume(pool, b, amount);
    add(pool, product1, amount);
    add(pool, product2, amount);
    logs.add(fmt("Ionic metathesis at %.0f K: %.4f mol -> %s + %s", c.temperatureK(), amount, product1, product2));
    return true;
  }

  private static String find(Map<String, Double> pool, Map<String, MoleculeInfo> infos, java.util.function.Predicate<MoleculeInfo> pred) {
    for (String s : pool.keySet()) {
      MoleculeInfo info = infos.get(s);
      if (info != null && pred.test(info) && pool.getOrDefault(s, 0.0) > EPS) return s;
    }
    return null;
  }

  private static int activityRank(String sym) {
    return switch (sym) {
      case "Li" -> 1;
      case "K" -> 2;
      case "Ba" -> 3;
      case "Sr" -> 4;
      case "Ca" -> 5;
      case "Na" -> 6;
      case "Mg" -> 7;
      case "Al" -> 8;
      case "Zn" -> 9;
      case "Fe" -> 10;
      case "Ni" -> 11;
      case "Sn" -> 12;
      case "Pb" -> 13;
      case "Cu" -> 15;
      case "Ag" -> 16;
      case "Au" -> 17;
      case "Pt" -> 18;
      default -> 14;
    };
  }

  private static int halogenRank(String token) {
    String t = token.replace("2", "");
    if (t.contains("F")) return 1;
    if (t.contains("Cl")) return 2;
    if (t.contains("Br")) return 3;
    if (t.contains("I")) return 4;
    return 99;
  }

  private String buildSaltSmiles(MoleculeInfo acid, MoleculeInfo base, String acidSmiles, String baseSmiles) throws InvalidSmilesException {
    String metal = base.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse(null);
    String halogen = acid.atoms().keySet().stream().filter(s -> s.equals("F") || s.equals("Cl") || s.equals("Br") || s.equals("I")).findFirst().orElse(null);
    if (metal != null && halogen != null) {
      String candidate = "[" + metal + "+].[" + halogen + "-]";
      chemistry.analyze(candidate);
      return candidate;
    }
    if (metal != null && acid.isAcidLike() && acid.hasO()) {
      String candidate = "[" + metal + "+]." + stripProtons(acidSmiles);
      try { chemistry.analyze(candidate); return candidate; } catch (InvalidSmilesException ex) { return "[" + metal + "+].[" + acidSmiles + "-]"; }
    }
    return "[" + baseSmiles + "." + acidSmiles + "]";
  }

  private String buildResidualSalt(MoleculeInfo acid, MoleculeInfo carbonate) {
    String metal = carbonate.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse("Na");
    String halogen = acid.atoms().keySet().stream().filter(s -> s.equals("F") || s.equals("Cl") || s.equals("Br") || s.equals("I")).findFirst().orElse(null);
    if (halogen != null) return "[" + metal + "+].[" + halogen + "-]";
    return "[" + metal + "+2].[O-]C([O-])=O";
  }

  private String buildMetalSalt(MoleculeInfo metal, MoleculeInfo acid, String metalSmiles, String acidSmiles) {
    String symbol = metal.atoms().keySet().stream().filter(ChemistryService::isMetal).findFirst().orElse("Na");
    String halogen = acid.atoms().keySet().stream().filter(s -> s.equals("F") || s.equals("Cl") || s.equals("Br") || s.equals("I")).findFirst().orElse(null);
    if (halogen != null) return "[" + symbol + "+].[" + halogen + "-]";
    return metalSmiles + "." + acidSmiles;
  }

  private String stripProtons(String smiles) {
    if (smiles.startsWith("[H+]")) return smiles.substring(4);
    return smiles;
  }

  private static void consume(Map<String, Double> pool, String smiles, double amount) {
    add(pool, smiles, -amount);
  }

  private static void add(Map<String, Double> pool, String smiles, double delta) {
    double next = pool.getOrDefault(smiles, 0.0) + delta;
    if (next <= EPS) pool.remove(smiles);
    else pool.put(smiles, next);
  }

  private static void cleanup(Map<String, Double> pool) {
    pool.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= EPS);
  }

  private static String fmt(String pattern, Object... args) {
    return String.format(Locale.ROOT, pattern, args);
  }
}
