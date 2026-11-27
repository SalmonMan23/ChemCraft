package org.sutormin.chemcraft.chemistry;

import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.layout.StructureDiagramGenerator;
import org.openscience.cdk.silent.AtomContainer;

import javax.vecmath.Point2d;
import java.util.ArrayList;
import java.util.List;

public final class MoleculeLayout {
  public record AtomPos(String symbol, double x, double y, int charge) {}
  public record BondSeg(int a, int b, IBond.Order order) {}
  public record Layout(List<AtomPos> atoms, List<BondSeg> bonds, double minX, double minY, double maxX, double maxY) {}

  private MoleculeLayout() {}

  public static Layout layout(IAtomContainer input) throws CDKException {
    IAtomContainer mol = new AtomContainer(input);
    StructureDiagramGenerator sdg = new StructureDiagramGenerator();
    sdg.setMolecule(mol);
    sdg.generateCoordinates();
    IAtomContainer laid = sdg.getMolecule();
    List<AtomPos> atoms = new ArrayList<>();
    double minX = Double.POSITIVE_INFINITY;
    double minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY;
    double maxY = Double.NEGATIVE_INFINITY;
    for (IAtom atom : laid.atoms()) {
      Point2d p = atom.getPoint2d();
      if (p == null) p = new Point2d(0, 0);
      int charge = atom.getFormalCharge() == null ? 0 : atom.getFormalCharge();
      atoms.add(new AtomPos(atom.getSymbol(), p.x, p.y, charge));
      minX = Math.min(minX, p.x);
      minY = Math.min(minY, p.y);
      maxX = Math.max(maxX, p.x);
      maxY = Math.max(maxY, p.y);
    }
    List<BondSeg> bonds = new ArrayList<>();
    for (IBond bond : laid.bonds()) {
      int a = laid.indexOf(bond.getBegin());
      int b = laid.indexOf(bond.getEnd());
      if (a < 0 || b < 0) continue;
      bonds.add(new BondSeg(a, b, bond.getOrder() == null ? IBond.Order.SINGLE : bond.getOrder()));
    }
    if (!Double.isFinite(minX)) {
      minX = 0; minY = 0; maxX = 1; maxY = 1;
    }
    if (Math.abs(maxX - minX) < 1e-6) { minX -= 0.5; maxX += 0.5; }
    if (Math.abs(maxY - minY) < 1e-6) { minY -= 0.5; maxY += 0.5; }
    return new Layout(atoms, bonds, minX, minY, maxX, maxY);
  }
}
