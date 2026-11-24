package org.sutormin.chemcraft;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.sutormin.chemcraft.chemistry.ChemistryEngine;
import org.sutormin.chemcraft.command.ChemCommand;
import org.sutormin.chemcraft.item.ChemItemManager;
import org.sutormin.chemcraft.lab.LabGuiManager;
import org.sutormin.chemcraft.listener.ToolInteractListener;

public final class ChemCraftPlugin extends JavaPlugin {
  private ChemItemManager itemManager;
  private ChemistryEngine chemistryEngine;
  private LabGuiManager labGuiManager;

  @Override
  public void onEnable() {
    this.itemManager = new ChemItemManager(this);
    this.chemistryEngine = new ChemistryEngine();
    this.labGuiManager = new LabGuiManager(this, itemManager, chemistryEngine);

    getServer().getPluginManager().registerEvents(
      new ToolInteractListener(itemManager, chemistryEngine, labGuiManager),
      this
    );
    getServer().getPluginManager().registerEvents(labGuiManager, this);

    LifecycleEventManager<Plugin> manager = this.getLifecycleManager();
    manager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
      Commands commands = event.registrar();
      new ChemCommand(this, itemManager, chemistryEngine, labGuiManager).register(commands);
    });
  }
}
