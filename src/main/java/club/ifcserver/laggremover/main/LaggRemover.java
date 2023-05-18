package club.ifcserver.laggremover.main;

import club.ifcserver.laggremover.api.Module;
import club.ifcserver.laggremover.api.proto.DelayedLRProtocolResult;
import club.ifcserver.laggremover.api.proto.LRProtocol;
import club.ifcserver.laggremover.api.proto.LRProtocolResult;
import club.ifcserver.laggremover.api.proto.Protocol;
import club.ifcserver.laggremover.inf.Help;
import club.ifcserver.laggremover.util.DoubleVar;
import club.ifcserver.laggremover.util.LRConfig;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.HashMap;
import java.util.zip.ZipFile;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/* loaded from: LaggRemover-2.0.6.jar:drew6017/lr/main/LaggRemover.class */
public class LaggRemover extends JavaPlugin implements Listener {
    public static final String config_version = "0.1.7";
    public static final int wipeConfigLowerThan = 16;
    public static final long MEMORY_MBYTE_SIZE = 1024;
    public static final int MAX_PING_SIZE = 10000;
    public static LaggRemover lr;
    public static String prefix = "§6§lLaggRemover §7§l>>§r ";
    public static File modDir;
    private static HashMap<Module, String[]> loaded;

    public void onEnable() {
        File[] listFiles;
        lr = this;
        Bukkit.getServer().getPluginManager().registerEvents(new Events(), this);
        Bukkit.getServer().getScheduler().scheduleSyncRepeatingTask(this, new TPS(), 100L, 1L);
        Help.init();
        Protocol.init();
        LRConfig.init();
        if (getConfig().getBoolean("splashScreen")) {

            try {

                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                InputStream in = LaggRemover.class.getResourceAsStream("/club/ifcserver/laggremover/assets/splash.txt");
                byte[] buff = new byte[512];
                while (true) {
                    try {
                        int spi = in.read(buff);
                        if (spi == -1) {
                            break;
                        }
                        bo.write(buff, 0, spi);
                    } catch (IOException e) {
                        getLogger().warning("Could not load splash screen.");
                    }
                }

                in.close();
                bo.flush();

                String splashText = bo.toString();
                System.out.println(splashText);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        loaded = new HashMap<>();
        prefix = getConfig().getString("prefix").replaceAll("&", "§");
        if (LRConfig.autoChunk) {
            // from class: drew6017.lr.main.LaggRemover.1
            Bukkit.getScheduler().scheduleSyncRepeatingTask(lr, () -> {
                Chunk[] loadedChunks;
                for (World world : LaggRemover.this.getServer().getWorlds()) {
                    if (world.getPlayers().size() == 0) {
                        for (Chunk chunk : world.getLoadedChunks()) {
                            world.unloadChunk(chunk);
                        }
                    }
                }
            }, 200L, 200L);
        }
        if (LRConfig.autoLagRemoval) {
            autoLagRemovalLoop();
        }
        modDir = new File(getDataFolder(), "Modules");
        if (!modDir.exists()) {
            modDir.mkdirs();
        }
        for (File f : modDir.listFiles()) {
            if (!f.isDirectory() && f.getName().endsWith(".jar")) {
                try {
                    URL[] classes = {f.toURI().toURL()};
                    URLClassLoader loader = new URLClassLoader(classes, LaggRemover.class.getClassLoader());
                    ZipFile i = new ZipFile(f);
                    YamlConfiguration c = new YamlConfiguration();
                    c.load(new InputStreamReader(i.getInputStream(i.getEntry("module.yml"))));
                    String name = c.getString("name");
                    String version = c.getString("version");
                    String author = c.getString("author");
                    getLogger().info("Loading module \"" + name + "-" + version + "\" created by \"" + author + "\"...");
                    Class<?> plugin = Class.forName(c.getString("main"), true, loader);
                    Module m = (Module) plugin.newInstance();
                    loaded.put(m, new String[]{name, version, author});
                    m.onEnable();
                } catch (IOException | ClassNotFoundException | IllegalAccessException | InstantiationException | InvalidConfigurationException e2) {
                    getLogger().info("LaggRemover located an invalid module named \"" + f.getName() + "\"");
                }
            }
        }
        getLogger().info("Loaded " + Integer.toString(loaded.size()) + " module(s)");
        if (LRConfig.isAIActive) {
            getLogger().info("The LaggRemover AI is now active!");
        }
        getLogger().info("LaggRemover has been enabled!");
//        if (getConfig().getBoolean("auto-update")) {
//            Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new Runnable() { // from class: drew6017.lr.main.LaggRemover.2
//                @Override // java.lang.Runnable
//                public void run() {
//                    new Updater((Plugin) LaggRemover.lr, 91200, LaggRemover.this.getFile(), Updater.UpdateType.DEFAULT, true);
//                }
//            }, 0L, 144000L);
//        }
    }

    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(lr);
        for (Module m : loaded.keySet()) {
            m.onDisable();
        }
        lr = null;
        getLogger().info("LaggRemover has been disabled!");
    }

    public static String[] getModulesList() {
        StringBuilder sb = new StringBuilder();
        for (String[] s : loaded.values()) {
            sb.append(s[0]);
            sb.append(", ");
        }
        String sbs = sb.toString();
        if (!sbs.equals("")) {
            sbs = sbs.substring(0, sbs.length() - 2);
        }
        return new String[]{sbs, Integer.toString(loaded.size())};
    }

    public static String[] getProtocolList() {
        StringBuilder sb = new StringBuilder();
        Collection<LRProtocol> protocols = Protocol.getProtocols();
        for (LRProtocol p : protocols) {
            sb.append(p.id());
            sb.append(", ");
        }
        String sbs = sb.toString();
        if (!sbs.equals("")) {
            sbs = sbs.substring(0, sbs.length() - 2);
        }
        return new String[]{sbs, Integer.toString(protocols.size())};
    }

    @EventHandler
    public void onSpawn(EntitySpawnEvent e) {
        Entity ent = e.getEntity();
        if (!ent.hasMetadata("NPC") && LRConfig.thinMobs && ent.getLocation().getChunk().getEntities().length > LRConfig.thinAt) {
            e.setCancelled(true);
        }
    }

    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        Player p = sender instanceof Player ? (Player) sender : null;
        if (cmd.getName().equalsIgnoreCase("lr")) {
            if (args.length == 0) {
                Help.send(p, 1);
                return true;
            } else if (!LRCommand.onCommand(p, args)) {
                for (Module m : loaded.keySet()) {
                    if (m.onCommand(sender, label, args)) {
                        return true;
                    }
                }
                Help.sendMsg(p, "§cCommand not found! Use /lr help for a list of commands.", true);
                return true;
            } else {
                return true;
            }
        }
        return true;
    }

    public static void broadcast(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

    public static String[] getData(Module m) {
        return loaded.get(m);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void autoLagRemovalLoop() {
        // from class: drew6017.lr.main.LaggRemover.3
        Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> {
            for (LRProtocol p : LRConfig.periodic_protocols.keySet()) {
                DoubleVar<Object[], Boolean> dat = LRConfig.periodic_protocols.get(p);
                if (dat.getVar2()) {
                    Protocol.rund(p, dat.getVar1(), new DelayedLRProtocolResult() { // from class: drew6017.lr.main.LaggRemover.3.1
                        @Override // drew6017.lr.api.proto.DelayedLRProtocolResult
                        public void receive(LRProtocolResult result) {
                        }
                    });
                } else {
                    p.run(dat.getVar1());
                }
            }
            LaggRemover.this.autoLagRemovalLoop();
        }, 1200L * LRConfig.autoLagRemovalTime);
    }
}
