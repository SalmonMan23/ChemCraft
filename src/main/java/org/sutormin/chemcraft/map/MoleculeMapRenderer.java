package org.sutormin.chemcraft.map;

import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.openscience.cdk.interfaces.IBond;
import org.sutormin.chemcraft.chemistry.MoleculeLayout;

import java.awt.Color;

public final class MoleculeMapRenderer extends MapRenderer {
  private final MoleculeLayout.Layout layout;
  private boolean drawn;

  public MoleculeMapRenderer(MoleculeLayout.Layout layout) {
    super(true);
    this.layout = layout;
  }

  @Override
  public void render(MapView map, MapCanvas canvas, Player player) {
    if (drawn) return;
    drawn = true;
    for (int x = 0; x < 128; x++) {
      for (int y = 0; y < 128; y++) canvas.setPixelColor(x, y, new Color(250, 250, 245));
    }
    double pad = 12.0;
    double spanX = layout.maxX() - layout.minX();
    double spanY = layout.maxY() - layout.minY();
    double scale = Math.min((128.0 - 2 * pad) / spanX, (128.0 - 2 * pad) / spanY);
    double offX = (128.0 - spanX * scale) * 0.5;
    double offY = (128.0 - spanY * scale) * 0.5;
    for (MoleculeLayout.BondSeg bond : layout.bonds()) {
      MoleculeLayout.AtomPos a = layout.atoms().get(bond.a());
      MoleculeLayout.AtomPos b = layout.atoms().get(bond.b());
      int x1 = sx(a.x(), scale, offX);
      int y1 = sy(a.y(), scale, offY);
      int x2 = sx(b.x(), scale, offX);
      int y2 = sy(b.y(), scale, offY);
      Color bondColor = new Color(40, 40, 40);
      int copies = bond.order() == IBond.Order.TRIPLE ? 3 : bond.order() == IBond.Order.DOUBLE ? 2 : 1;
      drawBond(canvas, x1, y1, x2, y2, copies, bondColor);
    }
    for (MoleculeLayout.AtomPos atom : layout.atoms()) {
      int x = sx(atom.x(), scale, offX);
      int y = sy(atom.y(), scale, offY);
      Color color = elementColor(atom.symbol());
      fillCircle(canvas, x, y, atom.symbol().equals("C") || atom.symbol().equals("H") ? 1 : 2, color);
      if (!atom.symbol().equals("C") && !atom.symbol().equals("H")) {
        canvas.setPixelColor(Math.min(127, x + 1), Math.min(127, y - 1), color);
      }
    }
  }

  private int sx(double x, double scale, double offX) {
    return (int) Math.round((x - layout.minX()) * scale + offX);
  }

  private int sy(double y, double scale, double offY) {
    return (int) Math.round((layout.maxY() - y) * scale + offY);
  }

  private static void drawBond(MapCanvas canvas, int x1, int y1, int x2, int y2, int copies, Color color) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double len = Math.hypot(dx, dy);
    if (len < 1e-6) return;
    double nx = -dy / len;
    double ny = dx / len;
    double spread = copies == 1 ? 0 : 1.5;
    for (int c = 0; c < copies; c++) {
      double shift = copies == 1 ? 0 : (c - (copies - 1) * 0.5) * spread;
      int ax = (int) Math.round(x1 + nx * shift);
      int ay = (int) Math.round(y1 + ny * shift);
      int bx = (int) Math.round(x2 + nx * shift);
      int by = (int) Math.round(y2 + ny * shift);
      drawLine(canvas, ax, ay, bx, by, color);
    }
  }

  private static void drawLine(MapCanvas canvas, int x1, int y1, int x2, int y2, Color color) {
    int dx = Math.abs(x2 - x1);
    int dy = Math.abs(y2 - y1);
    int sx = x1 < x2 ? 1 : -1;
    int sy = y1 < y2 ? 1 : -1;
    int err = dx - dy;
    int x = x1;
    int y = y1;
    while (true) {
      set(canvas, x, y, color);
      if (x == x2 && y == y2) break;
      int e2 = 2 * err;
      if (e2 > -dy) { err -= dy; x += sx; }
      if (e2 < dx) { err += dx; y += sy; }
    }
  }

  private static void fillCircle(MapCanvas canvas, int cx, int cy, int r, Color color) {
    for (int x = -r; x <= r; x++) {
      for (int y = -r; y <= r; y++) {
        if (x * x + y * y <= r * r) set(canvas, cx + x, cy + y, color);
      }
    }
  }

  private static void set(MapCanvas canvas, int x, int y, Color color) {
    if (x < 0 || y < 0 || x > 127 || y > 127) return;
    canvas.setPixelColor(x, y, color);
  }

  private static Color elementColor(String symbol) {
    return switch (symbol) {
      case "H" -> new Color(255, 255, 255);
      case "C" -> new Color(50, 50, 50);
      case "N" -> new Color(48, 80, 248);
      case "O" -> new Color(255, 13, 13);
      case "F" -> new Color(144, 224, 80);
      case "Cl" -> new Color(31, 240, 31);
      case "Br" -> new Color(166, 41, 41);
      case "I" -> new Color(148, 0, 148);
      case "S" -> new Color(255, 200, 50);
      case "P" -> new Color(255, 128, 0);
      case "Fe" -> new Color(224, 102, 51);
      case "Cu" -> new Color(200, 128, 51);
      case "Na" -> new Color(171, 92, 242);
      case "K" -> new Color(143, 64, 212);
      case "Ca" -> new Color(61, 255, 0);
      case "Mg" -> new Color(138, 255, 0);
      case "Al" -> new Color(191, 166, 166);
      case "Si" -> new Color(240, 200, 160);
      case "Au" -> new Color(255, 209, 35);
      case "Pt" -> new Color(208, 208, 224);
      case "U" -> new Color(0, 143, 255);
      default -> new Color(255, 20, 147);
    };
  }
}
