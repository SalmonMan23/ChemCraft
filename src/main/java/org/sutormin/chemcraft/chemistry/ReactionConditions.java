package org.sutormin.chemcraft.chemistry;

import org.sutormin.chemcraft.lab.LabType;

public record ReactionConditions(LabType labType, double temperatureK, double pressureAtm, double voltageV) {
  public static ReactionConditions stp(LabType type) {
    return new ReactionConditions(type, 298.15, 1.0, 0.0);
  }
}
