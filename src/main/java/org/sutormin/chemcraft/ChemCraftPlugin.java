package org.sutormin.chemcraft;

import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.sutormin.chemcraft.chemistry.ChemistryService;
import org.sutormin.chemcraft.chemistry.ReactionEngine;
import org.sutormin.chemcraft.command.ChemCommand;
import org.sutormin.chemcraft.config.ChemConfig;
import org.sutormin.chemcraft.item.ChemItems;
import org.sutormin.chemcraft.lab.LabManager;
import org.sutormin.chemcraft.lab.LabStorage;
import org.sutormin.chemcraft.listener.AnalyzerService;
import org.sutormin.chemcraft.listener.MachineListener;
import org.sutormin.chemcraft.listener.PurifierService;
import org.sutormin.chemcraft.machine.MachineRegistry;
import org.sutormin.chemcraft.map.StructureMapService;
import org.sutormin.chemcraft.storage.ChemStorageManager;
import org.sutormin.chemcraft.storage.ChemStorageStore;
import org.sutormin.chemcraft.util.Chat;
import org.sutormin.chemcraft.util.Keys;

public final class ChemCraftPlugin extends JavaPlugin {
  private ChemistryService chemistry;
  private ChemConfig chemConfig;
  private ChemItems items;
  private LabStorage labStorage;
  private ChemStorageStore chemStorageStore;
  private MachineRegistry machines;

  @Override
  public void onEnable() {
    this.chemistry = new ChemistryService();
    this.chemConfig = new ChemConfig(this, chemistry);
    this.chemConfig.load();
    Keys keys = new Keys(this);
    this.items = new ChemItems(keys, chemistry, chemConfig);
    ReactionEngine reactions = new ReactionEngine(chemistry);
    this.labStorage = new LabStorage(this, chemConfig);
    this.labStorage.load();
    this.chemStorageStore = new ChemStorageStore(this);
    this.chemStorageStore.load();
    this.machines = new MachineRegistry(this, keys);
    this.machines.load();
    LabManager labManager = new LabManager(this, items, chemConfig, reactions, labStorage, keys);
    ChemStorageManager storageManager = new ChemStorageManager(this, items, chemistry, chemStorageStore);
    StructureMapService maps = new StructureMapService(this, chemistry);
    PurifierService purifier = new PurifierService(items, chemConfig, chemistry);
    AnalyzerService analyzer = new AnalyzerService(items, chemistry, maps);
    getServer().getPluginManager().registerEvents(new MachineListener(items, purifier, analyzer, labManager, storageManager, machines), this);
    getServer().getPluginManager().registerEvents(labManager, this);
    getServer().getPluginManager().registerEvents(storageManager, this);
    LifecycleEventManager<Plugin> lifecycle = getLifecycleManager();
    lifecycle.registerEventHandler(LifecycleEvents.COMMANDS, event -> new ChemCommand(this, items, chemistry).register(event.registrar()));
    Chat.ok(getServer().getConsoleSender(), "ChemCraft enabled.");
  }

  @Override
  public void onDisable() {
    if (labStorage != null) labStorage.save();
    if (chemStorageStore != null) chemStorageStore.save();
    if (machines != null) machines.save();
  }

  public void reloadChemConfig() {
    chemistry.clearCache();
    chemConfig.load();
  }
}
