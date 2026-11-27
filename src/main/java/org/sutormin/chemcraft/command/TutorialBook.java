package org.sutormin.chemcraft.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.List;

public final class TutorialBook {
  private TutorialBook() {}

  public static ItemStack create() {
    ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
    BookMeta meta = (BookMeta) book.getItemMeta();
    meta.setTitle("ChemCraft Tutorial");
    meta.setAuthor("ChemCraft");
    meta.displayName(Component.text("ChemCraft Tutorial", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
    meta.addPages(
      Component.text("ChemCraft\n\nMachines:\n- Purifier\n- Labs (standard/furnace/cooler/electrical)\n- Analyzer\n\nAdmin: /chem lab <type>"),
      Component.text("Purifier\n\nMain-hand item + right-click.\nVanilla -> one chem item per component.\nChem item -> elements."),
      Component.text("Labs\n\nTop 2 rows: chemical storage.\nBottom row: settings only (temp/voltage steps + Run).\n\n/chem lab standard|furnace|cooler|electrical"),
      Component.text("Reactions\n\nDepend on lab conditions:\n- acid/base, metals, salts\n- combustion / reduction (hot)\n- electrolysis (electrical)\nChat only reports success/fail."),
      Component.text("Analyzer\n\nHold a chem item and right-click.\nPrints SMILES, formula, moles, mass, density, volume, charge, traits, phase.")
    );
    meta.lore(List.of(Component.text("ChemCraft", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, true)));
    book.setItemMeta(meta);
    return book;
  }
}