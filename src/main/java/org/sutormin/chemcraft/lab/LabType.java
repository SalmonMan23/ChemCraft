package org.sutormin.chemcraft.lab;

import org.bukkit.Material;

import java.util.Locale;

public enum LabType {
  STANDARD("lab", "Standard Lab", Material.CAULDRON),
  FURNACE("lab_furnace", "Furnace Lab", Material.BLAST_FURNACE),
  COOLER("lab_cooler", "Cooler Lab", Material.ENCHANTING_TABLE),
  ELECTRICAL("lab_electrical", "Electrical Lab", Material.BREWING_STAND);

  private final String toolId;
  private final String displayName;
  private final Material material;

  LabType(String toolId, String displayName, Material material) {
    this.toolId = toolId;
    this.displayName = displayName;
    this.material = material;
  }

  public String toolId() {
    return toolId;
  }

  public String displayName() {
    return displayName;
  }

  public Material material() {
    return material;
  }

  public boolean isLabTool(String tool) {
    return toolId.equals(tool);
  }

  public static boolean isAnyLab(String tool) {
    if (tool == null) return false;
    for (LabType type : values()) if (type.toolId.equals(tool)) return true;
    return false;
  }

  public static LabType fromToolId(String tool) {
    if (tool == null) return null;
    for (LabType type : values()) if (type.toolId.equals(tool)) return type;
    return null;
  }

  public static LabType fromArg(String raw) {
    if (raw == null || raw.isBlank()) return STANDARD;
    String key = raw.trim().toLowerCase(Locale.ROOT);
    return switch (key) {
      case "standard", "basic", "lab" -> STANDARD;
      case "furnace", "heat", "hot" -> FURNACE;
      case "cooler", "cold", "cryo" -> COOLER;
      case "electrical", "electric", "electro" -> ELECTRICAL;
      default -> null;
    };
  }
}
