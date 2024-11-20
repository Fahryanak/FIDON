package com.example.filed;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class FiledPlugin extends JavaPlugin {
    private FileConfiguration config;
    private int downloadSpeed; // Kecepatan download dalam Mbps
    private String downloadFolder; // Folder tempat file akan disimpan
    private int maxFileSize; // Maksimum ukuran file dalam MB
    private int maxConcurrentDownloads; // Jumlah maksimum unduhan simultan
    private final Map<String, DownloadTask> downloadTasks = new HashMap<>();
    private final Queue<String> downloadQueue = new LinkedList<>();
    private final List<String> downloadHistory = new ArrayList<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        downloadSpeed = config.getInt("download-speed", 5); // Default 5 Mbps
        downloadFolder = config.getString("download-folder", "downloads");
        maxFileSize = config.getInt("max-file-size", 2048); // Default 2 GB
        maxConcurrentDownloads = config.getInt("max-concurrent-downloads", 3); // Default 3

        getLogger().info("Plugin Filed diaktifkan!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /filed download <URL> | /filed batch <file> | /filed list | /filed history | /filed reload");
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "download":
                if (args.length > 1) {
                    String fileUrl = args[1];
                    queueDownload(fileUrl, sender);
                } else {
                    sender.sendMessage("Usage: /filed download <URL>");
                }
                return true;

            case "batch":
                if (args.length > 1) {
                    String filePath = args[1];
                    batchDownload(filePath, sender);
                } else {
                    sender.sendMessage("Usage: /filed batch <file>");
                }
                return true;

            case "list":
                if (downloadTasks.isEmpty() && downloadQueue.isEmpty()) {
                    sender.sendMessage("Tidak ada unduhan yang sedang berjalan atau dalam antrean.");
                } else {
                    sender.sendMessage("Unduhan aktif:");
                    downloadTasks.forEach((url, task) -> sender.sendMessage("- " + url + ": " + task.getProgress() + "%"));
                    sender.sendMessage("Antrean unduhan:");
                    for (String url : downloadQueue) {
                        sender.sendMessage("- " + url);
                    }
                }
                return true;

            case "history":
                if (downloadHistory.isEmpty()) {
                    sender.sendMessage("Tidak ada riwayat unduhan.");
                } else {
                    sender.sendMessage("Riwayat unduhan:");
                    downloadHistory.forEach(sender::sendMessage);
                }
                return true;

            case "reload":
                reloadConfig();
                config = getConfig();
                downloadSpeed = config.getInt("download-speed", 5);
                downloadFolder = config.getString("download-folder", "downloads");
                maxFileSize = config.getInt("max-file-size", 2048);
                maxConcurrentDownloads = config.getInt("max-concurrent-downloads", 3);
                sender.sendMessage("Konfigurasi telah dimuat ulang!");
                return true;

            default:
                sender.sendMessage("Perintah tidak dikenal. Gunakan /filed untuk melihat bantuan.");
                return false;
        }
    }

    private void queueDownload(String fileUrl, CommandSender sender) {
        if (downloadTasks.size() >= maxConcurrentDownloads) {
            sender.sendMessage("Unduhan telah mencapai batas maksimal (" + maxConcurrentDownloads + "). Menambahkan ke antrean.");
            downloadQueue.add(fileUrl);
            return;
        }

        startDownload(fileUrl, sender);
    }

    private void startDownload(String fileUrl, CommandSender sender) {
        try {
            URL url = new URL(fileUrl);
            String fileName = new File(url.getFile()).getName();
            File destinationFile = new File(getDataFolder(), downloadFolder + "/" + fileName);

            // Membuat folder jika tidak ada
            if (!destinationFile.getParentFile().exists()) {
                destinationFile.getParentFile().mkdirs();
            }

            DownloadTask task = new DownloadTask(this, url, destinationFile, downloadSpeed, maxFileSize, sender);
            downloadTasks.put(fileUrl, task);
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        } catch (Exception e) {
            sender.sendMessage("Gagal memulai unduhan: " + e.getMessage());
        }
    }

    private void batchDownload(String filePath, CommandSender sender) {
        File file = new File(getDataFolder(), filePath);
        if (!file.exists()) {
            sender.sendMessage("File tidak ditemukan: " + filePath);
            return;
        }

        try {
            List<String> urls = Files.readAllLines(file.toPath());
            for (String url : urls) {
                queueDownload(url, sender);
            }
            sender.sendMessage("Batch download dimulai untuk " + urls.size() + " file.");
        } catch (IOException e) {
            sender.sendMessage("Gagal membaca file batch: " + e.getMessage());
        }
    }

    public void onDownloadComplete(String fileUrl) {
        downloadTasks.remove(fileUrl);
        if (!downloadQueue.isEmpty()) {
            String nextUrl = downloadQueue.poll();
            Bukkit.getScheduler().runTask(this, () -> startDownload(nextUrl, Bukkit.getConsoleSender()));
        }
    }

    public void addDownloadToHistory(String fileName) {
        downloadHistory.add(fileName);
        File logFile = new File(getDataFolder(), "history.log");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(fileName);
            writer.newLine();
        } catch (IOException e) {
            getLogger().warning("Gagal menulis riwayat unduhan: " + e.getMessage());
        }
    }
}
