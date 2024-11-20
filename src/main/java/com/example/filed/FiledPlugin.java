package com.example.filed;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class FiledPlugin extends JavaPlugin {

    private File linkFile;
    private List<String> cachedLinks;
    private int intervalSeconds;
    private File downloadFolder;
    private int downloadSpeedMbps;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();

        linkFile = new File(getDataFolder(), "link.txt");

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        if (!linkFile.exists()) {
            try {
                linkFile.createNewFile();
                getLogger().info("File link.txt telah dibuat.");
            } catch (IOException e) {
                getLogger().severe("Gagal membuat file link.txt: " + e.getMessage());
            }
        }

        startLinkFileWatcher();
    }

    private void loadConfig() {
        reloadConfig();
        intervalSeconds = getConfig().getInt("read-interval", 60);
        String folderPath = getConfig().getString("download-folder", "plugins/filed/downloads");
        downloadSpeedMbps = getConfig().getInt("download-speed", 1);

        if (downloadSpeedMbps > 5) {
            downloadSpeedMbps = 5; // Batasi kecepatan download maksimal 5 Mbps
            getLogger().warning("Kecepatan download melebihi batas maksimal, diatur ke 5 Mbps.");
        }

        downloadFolder = new File(folderPath);
        if (!downloadFolder.exists()) {
            if (downloadFolder.mkdirs()) {
                getLogger().info("Folder tujuan download berhasil dibuat: " + downloadFolder.getAbsolutePath());
            } else {
                getLogger().warning("Gagal membuat folder tujuan download: " + downloadFolder.getAbsolutePath());
            }
        }

        getLogger().info("Konfigurasi dimuat ulang. Interval pembacaan: " + intervalSeconds + " detik.");
        getLogger().info("Kecepatan download: " + downloadSpeedMbps + " Mbps.");
    }

    private void startLinkFileWatcher() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cachedLinks = readLinksFromFile();
                getLogger().info("File link.txt diperbarui. Total link: " + cachedLinks.size());
            }
        }.runTaskTimer(this, 0, intervalSeconds * 20L);
    }

    private List<String> readLinksFromFile() {
        try {
            return java.nio.file.Files.readAllLines(linkFile.toPath());
        } catch (IOException e) {
            getLogger().severe("Gagal membaca file link.txt: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("filed")) {
            if (args.length == 0) {
                sender.sendMessage("Gunakan: /filed <reload|download>");
                return true;
            }

            if (args[0].equalsIgnoreCase("reload")) {
                loadConfig();
                sender.sendMessage("Konfigurasi berhasil dimuat ulang.");
                return true;
            }

            if (args[0].equalsIgnoreCase("download")) {
                if (args.length == 2) {
                    String link = args[1];
                    sender.sendMessage("Memulai download untuk: " + link);
                    new Thread(() -> downloadFile(link, new File(downloadFolder, getFileNameFromUrl(link)), sender)).start();
                } else {
                    sender.sendMessage("Gunakan: /filed download <link>");
                }
                return true;
            }
        }
        return false;
    }

    private void downloadFile(String fileUrl, File destination, CommandSender sender) {
        try {
            URL url = new URL(fileUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.connect();

            int fileSize = connection.getContentLength();
            InputStream inputStream = connection.getInputStream();
            FileOutputStream outputStream = new FileOutputStream(destination);

            byte[] buffer = new byte[1024];
            int bytesRead;
            int totalBytesRead = 0;
            int bufferSizePerSecond = downloadSpeedMbps * 1024 * 1024 / 8;

            long startTime = System.currentTimeMillis();

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                // Hitung progres dan tampilkan ke pengguna
                int progress = (int) ((double) totalBytesRead / fileSize * 100);
                double downloadedMB = totalBytesRead / (1024.0 * 1024.0);
                sender.sendMessage(String.format("Downloading %s: %d%% (%.2f MB)", destination.getName(), progress, downloadedMB));

                // Batasi kecepatan download
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime < 1000) {
                    Thread.sleep(1000 - elapsedTime);
                }
                startTime = System.currentTimeMillis();
            }

            sender.sendMessage("Download selesai: " + destination.getAbsolutePath());
            inputStream.close();
            outputStream.close();
        } catch (Exception e) {
            sender.sendMessage("Gagal mendownload file: " + e.getMessage());
            getLogger().severe("Gagal mendownload file dari " + fileUrl + ": " + e.getMessage());
        }
    }

    private String getFileNameFromUrl(String url) {
        return new File(url).getName();
    }
}