package com.example.filed;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.*;

public class FiledPlugin extends JavaPlugin {
    private FileConfiguration config;
    private int downloadSpeed;
    private String downloadFolder;
    private int maxConcurrentDownloads;
    private final Map<String, DownloadTask> downloadTasks = new HashMap<>();
    private final Queue<String> downloadQueue = new LinkedList<>();
    private final List<String> downloadHistory = new ArrayList<>();
    private OptiPL optimizer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        downloadSpeed = config.getInt("download-speed", 5);
        downloadFolder = config.getString("download-folder", "downloads");
        maxConcurrentDownloads = config.getInt("max-concurrent-downloads", 3);

        optimizer = new OptiPL();
        optimizer.optimize(this);

        getLogger().info("Plugin Filed diaktifkan!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /filed download <URL> <location> | /filed batch <file> <location> | /filed list | /filed history | /filed reload");
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "download":
                if (args.length > 2) {
                    String fileUrl = args[1];
                    String location = args[2];
                    queueDownload(fileUrl, location, sender);
                } else {
                    sender.sendMessage("Usage: /filed download <URL> <location>");
                }
                return true;

            case "batch":
                if (args.length > 2) {
                    String filePath = args[1];
                    String location = args[2];
                    batchDownload(filePath, location, sender);
                } else {
                    sender.sendMessage("Usage: /filed batch <file> <location>");
                }
                return true;

            case "list":
                listDownloads(sender);
                return true;

            case "history":
                showHistory(sender);
                return true;

            case "reload":
                reloadConfig();
                config = getConfig();
                downloadSpeed = config.getInt("download-speed", 5);
                downloadFolder = config.getString("download-folder", "downloads");
                maxConcurrentDownloads = config.getInt("max-concurrent-downloads", 3);
                sender.sendMessage("Konfigurasi telah dimuat ulang!");
                return true;

            default:
                sender.sendMessage("Perintah tidak dikenal. Gunakan /filed untuk melihat bantuan.");
                return false;
        }
    }

    private void listDownloads(CommandSender sender) {
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
    }

    private void showHistory(CommandSender sender) {
        if (downloadHistory.isEmpty()) {
            sender.sendMessage("Tidak ada riwayat unduhan.");
        } else {
            sender.sendMessage("Riwayat unduhan:");
            downloadHistory.forEach(sender::sendMessage);
        }
    }

    private void queueDownload(String fileUrl, String location, CommandSender sender) {
        if (downloadTasks.size() >= maxConcurrentDownloads) {
            sender.sendMessage("Unduhan telah mencapai batas maksimal (" + maxConcurrentDownloads + "). Menambahkan ke antrean.");
            downloadQueue.add(fileUrl);
            return;
        }

        startDownload(fileUrl, location, sender);
    }

    private void startDownload(String fileUrl, String location, CommandSender sender) {
        try {
            URL url = new URL(fileUrl);
            String fileName = new File(url.getFile()).getName();
            File destinationFile;

            switch (location.toLowerCase()) {
                case "main":
                    destinationFile = new File(getDataFolder().getParentFile().getParentFile(), fileName);
                    break;
                case "plugins":
                    destinationFile = new File(getDataFolder().getParentFile(), fileName);
                    break;
                default:
                    destinationFile = new File(location, fileName);
                    break;
            }

            if (!destinationFile.getParentFile().exists()) {
                destinationFile.getParentFile().mkdirs();
            }

            DownloadTask task = new DownloadTask(this, url, destinationFile, downloadSpeed, sender);
            downloadTasks.put(fileUrl, task);
            Bukkit.getScheduler().runTaskAsynchronously(this, task);
        } catch (Exception e) {
            sender.sendMessage("Gagal memulai unduhan: " + e.getMessage());
        }
    }

    private void batchDownload(String filePath, String location, CommandSender sender) {
        File file = new File(getDataFolder(), filePath);
        if (!file.exists()) {
            sender.sendMessage("File tidak ditemukan: " + filePath);
            return;
        }

        try {
            List<String> urls = Files.readAllLines(file.toPath());
            for (String url : urls) {
                queueDownload(url, location, sender);
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
            Bukkit.getScheduler().runTask(this, () -> startDownload(nextUrl, downloadFolder, Bukkit.getConsoleSender()));
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

    public int getDownloadSpeed() {
        return downloadSpeed;
    }

    public void setDownloadSpeed(int downloadSpeed) {
        this.downloadSpeed = downloadSpeed;
    }

    public int getMaxConcurrentDownloads() {
        return maxConcurrentDownloads;
    }

    public void setMaxConcurrentDownloads(int maxConcurrentDownloads) {
        this.maxConcurrentDownloads = maxConcurrentDownloads;
    }
}
