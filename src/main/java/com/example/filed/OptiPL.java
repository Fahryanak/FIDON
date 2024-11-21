package com.example.filed;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class OptiPL {
    private static final long MAX_MEMORY_USAGE = 50 * 1024 * 1024; // Batas penggunaan memori 50MiB dalam byte

    /**
     * Mengaktifkan optimisasi performa untuk plugin.
     * 
     * @param plugin Plugin Filed yang sedang berjalan.
     */
    public void enableOptimization(FiledPlugin plugin) {
        // Jalankan optimisasi setiap 10 detik
        new BukkitRunnable() {
            @Override
            public void run() {
                optimizePerformance(plugin);
            }
        }.runTaskTimer(plugin, 0L, 20L * 10); // 10 detik (20 tick * 10)
    }

    /**
     * Logika utama untuk optimisasi performa plugin.
     * 
     * @param plugin Plugin Filed yang sedang berjalan.
     */
    private void optimizePerformance(FiledPlugin plugin) {
        // Cek total penggunaan memori oleh JVM
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();

        if (usedMemory > MAX_MEMORY_USAGE) {
            // Jika memori melebihi batas, hentikan unduhan sementara
            Bukkit.getLogger().warning("Memori plugin mendekati batas 50MiB. Menghentikan tugas sementara.");
            plugin.pauseAllDownloads();
        } else {
            // Jika memori cukup, lanjutkan unduhan
            plugin.resumeAllDownloads();
        }

        // Sesuaikan kecepatan unduhan jika server sibuk
        int totalTasks = plugin.getServer().getScheduler().getPendingTasks().size();
        if (totalTasks > plugin.getMaxConcurrentDownloads()) {
            Bukkit.getLogger().warning("Server sedang sibuk. Menurunkan kecepatan unduh dan tugas simultan.");
            plugin.setDownloadSpeed(plugin.getDownloadSpeed() / 2); // Kurangi kecepatan unduh
            plugin.setMaxConcurrentDownloads(Math.max(1, plugin.getMaxConcurrentDownloads() / 2)); // Kurangi batas tugas
        } else {
            // Kembalikan pengaturan default jika server normal
            plugin.setDownloadSpeed(plugin.getConfig().getInt("download-speed"));
            plugin.setMaxConcurrentDownloads(plugin.getConfig().getInt("max-concurrent-downloads"));
        }
    }
}
