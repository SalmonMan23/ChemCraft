package org.sutormin.chemcraft.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

public final class Chat {
  private Chat() {}

  public static Component prefix() {
    return Component.text("[ChemCraft] ", NamedTextColor.DARK_AQUA).decoration(TextDecoration.BOLD, false);
  }

  public static void info(CommandSender sender, String message) {
    sender.sendMessage(prefix().append(Component.text(message, NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
  }

  public static void ok(CommandSender sender, String message) {
    sender.sendMessage(prefix().append(Component.text(message, NamedTextColor.GREEN).decoration(TextDecoration.BOLD, false)));
  }

  public static void warn(CommandSender sender, String message) {
    sender.sendMessage(prefix().append(Component.text(message, NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
  }

  public static void err(CommandSender sender, String message) {
    sender.sendMessage(prefix().append(Component.text(message, NamedTextColor.RED).decoration(TextDecoration.BOLD, false)));
  }

  public static void tip(CommandSender sender, String message) {
    sender.sendMessage(prefix().append(Component.text(message, NamedTextColor.AQUA).decoration(TextDecoration.BOLD, false)));
  }
}
