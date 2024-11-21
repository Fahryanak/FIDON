package com.example.filed;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.HashMap;
import java.util.Map;

public class OptiPL {
    private final Map<String, Integer> pluginLoad = new HashMap<>();

    public void optimize(FiledPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                adjustPerformance(plugin);
            }
        }.runTaskTimer(plugin, 0L, 20L * 60); // Jalankan setiap menit
    }

    private void adjustPerformance(FiledPlugin plugin) {
        int totalTasks = plugin.getServer().getScheduler().getPendingTasks().size();
        pluginLoad.put(plugin.getName(), totalTasks);

        if (totalTasks > plugin.getMaxConcurrentDownloads()) {
            Bukkit.getLogger().warning("Server under heavy load. Adjusting download speed and task limits.");
            plugin.setDownloadSpeed(plugin.getDownloadSpeed() / 2);
            plugin.setMaxConcurrentDownloads(plugin.getMaxConcurrentDownloads() / 2);
        } else {
            plugin.setDownloadSpeed(plugin.getConfig().getInt("download-speed"));
            plugin.setMaxConcurrentDownloads(plugin.getConfig().getInt("max-concurrent-downloads"));
        }
    }
}
