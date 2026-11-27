package org.sutormin.chemcraft.lab;

public final class LabSettings {
  private final LabType type;
  private double temperatureK;
  private double voltageV;
  private double pressureAtm;

  public LabSettings(LabType type, double temperatureK, double voltageV, double pressureAtm) {
    this.type = type;
    this.temperatureK = temperatureK;
    this.voltageV = voltageV;
    this.pressureAtm = pressureAtm;
  }

  public static LabSettings defaults(LabType type, LabDefaults defaults) {
    return new LabSettings(type, defaults.defaultTemperatureK(), defaults.defaultVoltageV(), defaults.defaultPressureAtm());
  }

  public LabType type() {
    return type;
  }

  public double temperatureK() {
    return temperatureK;
  }

  public double voltageV() {
    return voltageV;
  }

  public double pressureAtm() {
    return pressureAtm;
  }

  public void setTemperatureK(double temperatureK) {
    this.temperatureK = temperatureK;
  }

  public void setVoltageV(double voltageV) {
    this.voltageV = voltageV;
  }

  public void setPressureAtm(double pressureAtm) {
    this.pressureAtm = pressureAtm;
  }

  public void adjustTemperature(double delta, LabDefaults defaults) {
    temperatureK = clamp(temperatureK + delta, defaults.minTemperatureK(), defaults.maxTemperatureK());
  }

  public void adjustVoltage(double delta, LabDefaults defaults) {
    voltageV = clamp(voltageV + delta, defaults.minVoltageV(), defaults.maxVoltageV());
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }
}
