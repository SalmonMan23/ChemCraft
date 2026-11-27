package org.sutormin.chemcraft.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.openscience.cdk.exception.InvalidSmilesException;
import org.sutormin.chemcraft.ChemCraftPlugin;
import org.sutormin.chemcraft.chemistry.ChemistryService;
import org.sutormin.chemcraft.chemistry.MoleculeInfo;
import org.sutormin.chemcraft.item.ChemItems;
import org.sutormin.chemcraft.lab.LabType;
import org.sutormin.chemcraft.util.Chat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class ChemCommand {
  private final ChemCraftPlugin plugin;
  private final ChemItems items;
  private final ChemistryService chemistry;

  public ChemCommand(ChemCraftPlugin plugin, ChemItems items, ChemistryService chemistry) {
    this.plugin = plugin;
    this.items = items;
    this.chemistry = chemistry;
  }

  public void register(Commands commands) {
    LiteralCommandNode<CommandSourceStack> root = Commands.literal("chem")
      .then(Commands.literal("tutorial").requires(src -> src.getSender().hasPermission("chemcraft.tutorial")).executes(this::tutorial))
      .then(Commands.literal("reload").requires(src -> src.getSender().hasPermission("chemcraft.admin")).executes(this::reload))
      .then(Commands.literal("purifier").requires(src -> src.getSender().hasPermission("chemcraft.admin")).executes(ctx -> givePurifier(ctx)))
      .then(Commands.literal("analyzer").requires(src -> src.getSender().hasPermission("chemcraft.admin")).executes(ctx -> giveAnalyzer(ctx)))
      .then(Commands.literal("storage").requires(src -> src.getSender().hasPermission("chemcraft.admin")).executes(ctx -> giveStorage(ctx)))
      .then(Commands.literal("lab").requires(src -> src.getSender().hasPermission("chemcraft.admin"))
        .executes(ctx -> giveLab(ctx, LabType.STANDARD))
        .then(Commands.argument("type", StringArgumentType.word())
          .suggests((ctx, builder) -> {
            for (String type : List.of("standard", "furnace", "cooler", "electrical")) builder.suggest(type);
            return builder.buildFuture();
          })
          .executes(this::giveLabTyped)))
      .then(Commands.literal("give").requires(src -> src.getSender().hasPermission("chemcraft.admin"))
        .then(Commands.argument("smiles", StringArgumentType.string())
          .suggests((ctx, builder) -> {
            for (String example : List.of("O", "O=C=O", "[Fe]", "[Na+].[OH-]", "CCO", "O=O", "N#N", "[H][H]")) builder.suggest(example);
            return builder.buildFuture();
          })
          .then(Commands.argument("density", DoubleArgumentType.doubleArg())
            .then(Commands.argument("grams", DoubleArgumentType.doubleArg(0.000001))
              .then(Commands.argument("count", IntegerArgumentType.integer(1, 64))
                .executes(this::giveChem))))))
      .build();
    commands.register(root, "ChemCraft commands", List.of());
  }

  private int tutorial(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) return 0;
    items.give(player, TutorialBook.create());
    return 1;
  }

  private int reload(CommandContext<CommandSourceStack> ctx) {
    plugin.reloadChemConfig();
    return 1;
  }

  private int givePurifier(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) return 0;
    items.give(player, items.createPurifier());
    return 1;
  }

  private int giveAnalyzer(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) return 0;
    items.give(player, items.createAnalyzer());
    return 1;
  }

  private int giveStorage(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) return 0;
    items.give(player, items.createStorage());
    return 1;
  }

  private int giveLabTyped(CommandContext<CommandSourceStack> ctx) {
    LabType type = LabType.fromArg(StringArgumentType.getString(ctx, "type"));
    if (type == null) {
      Chat.err(ctx.getSource().getSender(), "Unknown lab type. Use: " + Arrays.toString(Arrays.stream(LabType.values()).map(t -> t.name().toLowerCase(Locale.ROOT)).toArray()));
      return 0;
    }
    return giveLab(ctx, type);
  }

  private int giveLab(CommandContext<CommandSourceStack> ctx, LabType type) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) return 0;
    items.give(player, items.createLab(type));
    return 1;
  }

  private int giveChem(CommandContext<CommandSourceStack> ctx) {
    CommandSender sender = ctx.getSource().getSender();
    if (!(sender instanceof Player player)) return 0;
    String smiles = StringArgumentType.getString(ctx, "smiles");
    double density = DoubleArgumentType.getDouble(ctx, "density");
    double grams = DoubleArgumentType.getDouble(ctx, "grams");
    int count = IntegerArgumentType.getInteger(ctx, "count");
    try {
      MoleculeInfo info = chemistry.analyze(smiles);
      double moles = grams / info.molarMass();
      for (int i = 0; i < count; i++) items.give(player, items.createChemItem(smiles, moles, density));
      return 1;
    } catch (InvalidSmilesException ex) {
      Chat.err(player, "Invalid SMILES: " + smiles);
      return 0;
    }
  }
}
