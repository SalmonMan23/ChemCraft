package org.sutormin.chemcraft.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.chemistry.ChemistryEngine;
import org.sutormin.chemcraft.item.ChemItemManager;
import org.sutormin.chemcraft.lab.LabGuiManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class ChemCommand {
  private final Plugin plugin;
  private final ChemItemManager itemManager;
  private final ChemistryEngine chemistryEngine;
  private final LabGuiManager labGuiManager;

  public ChemCommand(
    Plugin plugin,
    ChemItemManager itemManager,
    ChemistryEngine chemistryEngine,
    LabGuiManager labGuiManager
  ) {
    this.plugin = plugin;
    this.itemManager = itemManager;
    this.chemistryEngine = chemistryEngine;
    this.labGuiManager = labGuiManager;
  }

  public void register(Commands commands) {
    LiteralCommandNode<CommandSourceStack> root = Commands.literal("chem")
      .requires(src -> src.getSender().hasPermission("chemcraft.use"))
      .then(Commands.literal("purifier").executes(ctx -> giveTool(ctx, "purifier")))
      .then(Commands.literal("lab").executes(ctx -> giveTool(ctx, "lab")))
      .then(Commands.literal("analyzer").executes(ctx -> giveTool(ctx, "analyzer")))
      .then(Commands.literal("give")
        .then(Commands.argument("smiles", StringArgumentType.string())
        .suggests((ctx, builder) -> {
          for (String example : List.of("O", "Cl", "[Na]", "CCO", "O=O")) {
            builder.suggest(example);
          }
          return builder.buildFuture();
        })
          .then(Commands.argument("moles", DoubleArgumentType.doubleArg(0.0))
            .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
              .executes(this::executeGive))))
      .build();

    commands.register(root, "ChemCraft command", List.of());
  }

  private CompletableFuture<Suggestions> suggestSpecs(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
    for (String example : List.of("O:1.0", "Cl:1.0", "[Na]:1.0", "CCO:1.0", "O=O:1.0")) {
      builder.suggest(example);
    }
    return builder.buildFuture();
  }

  private int giveTool(CommandContext<CommandSourceStack> ctx, String toolType) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can receive ChemCraft tools.");
      return 0;
    }
    ItemStack tool = switch (toolType) {
      case "purifier" -> itemManager.createPurifier();
      case "lab" -> itemManager.createLab();
      case "analyzer" -> itemManager.createAnalyzer();
      default -> null;
    };
    if (tool == null) return 0;

    itemManager.giveToPlayer(player, tool);
    player.sendMessage(Component.text("Gave " + toolType + ".", NamedTextColor.GREEN));
    return 1;
  }

  private int executeGive(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) {
      sender.sendMessage("Only players can receive ChemCraft items.");
      return 0;
    }

    String smiles = StringArgumentType.getString(ctx, "smiles");
    Double moles = DoubleArgumentType.getDouble(ctx, "moles");
    int count = IntegerArgumentType.getInteger(ctx, "count");

    if (moles <= 0.0) {
      player.sendMessage(Component.text("Moles must be greater than 0.", NamedTextColor.RED));
      return 0;
    }

    try {
      chemistryEngine.analyze(smiles);
    } catch (InvalidSmilesException ex) {
      player.sendMessage(Component.text("Invalid SMILES: " + smiles, NamedTextColor.RED));
      return 0;
    }

    ItemStack stack = itemManager.createChemItem(smiles, moles, count);
    itemManager.giveToPlayer(player, stack);
    player.sendMessage(Component.text("Gave " + count + "x " + smiles + " (" + moles + " mol).", NamedTextColor.AQUA));
    return 1;
  }
}
