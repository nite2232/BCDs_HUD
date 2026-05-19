package io.github.nite2232.bcds_hud;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class BCDs_HUD extends JavaPlugin {

    private static BCDs_HUD main;

    private HudManager hudManager;

    @Override
    public void onEnable() {
        main = this;

        hudManager = new HudManager(this);

        new BukkitRunnable() {
            @Override
            public void run() {
                hudManager.updateAll();
            }
        }.runTaskTimer(this, 0L, 1L);
    }

    @Override
    public void onDisable() {
        hudManager.hideAll();
        main = null;
    }

    public static BCDs_HUD getPlugin() {
        return main;
    }

    public HudManager getHudManager() {
        return hudManager;
    }
}