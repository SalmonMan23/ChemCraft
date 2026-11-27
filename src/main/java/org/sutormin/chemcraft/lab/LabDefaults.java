package org.sutormin.chemcraft.lab;

import java.util.List;

public record LabDefaults(double defaultTemperatureK, double minTemperatureK, double maxTemperatureK, List<Double> temperatureSteps, double defaultVoltageV, double minVoltageV, double maxVoltageV, List<Double> voltageSteps, double defaultPressureAtm, boolean temperatureControl, boolean voltageControl) {
  public static LabDefaults forType(LabType type) {
    return switch (type) {
      case STANDARD -> new LabDefaults(298.15, 273.15, 323.15, List.of(1.0, 5.0, 10.0, 25.0), 0.0, 0.0, 0.0, List.of(), 1.0, true, false);
      case FURNACE -> new LabDefaults(298.15, 298.15, 1500.0, List.of(1.0, 5.0, 10.0, 25.0), 0.0, 0.0, 0.0, List.of(), 1.0, true, false);
      case COOLER -> new LabDefaults(298.15, 77.0, 298.15, List.of(1.0, 5.0, 10.0, 25.0), 0.0, 0.0, 0.0, List.of(), 1.0, true, false);
      case ELECTRICAL -> new LabDefaults(298.15, 273.15, 373.15, List.of(1.0, 5.0, 10.0), 0.0, 0.0, 12.0, List.of(0.5, 1.0, 2.0, 5.0), 1.0, false, true);
    };
  }
}
