package com.example.filed;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class FiledPlugin extends JavaPlugin {
    private FileConfiguration config;
    private int downloadSpeed; // Kecepatan download dalam Mbps
    private int delay; // Delay dalam detik untuk membaca file link.txt
    private String downloadFolder; // Folder tempat file akan disimpan

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = getConfig();
        downloadSpeed = config.getInt("download-speed", 5); // Default 5 Mbps
        delay = config.getInt("read-delay-seconds", 10); // Default 10 detik untuk membaca link.txt
        downloadFolder = config.getString("download-folder", "downloads"); // Folder default 'downloads'

        getLogger().info("Plugin Filed diaktifkan!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("Usage: /filed download <URL> | /filed reload | /filed setfolder <folder-name> | /filed setspeed <speed>");
            return false;
        }

        if (args[0].equalsIgnoreCase("download") && args.length > 1) {
            String fileUrl = args[1];
            sender.sendMessage("Mengunduh file dari: " + fileUrl);
            startDownload(fileUrl, sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            config = getConfig();
            downloadSpeed = config.getInt("download-speed", 5);
            delay = config.getInt("read-delay-seconds", 10);
            downloadFolder = config.getString("download-folder", "downloads");
            sender.sendMessage("Konfigurasi telah dimuat ulang!");
            return true;
        }

        if (args[0].equalsIgnoreCase("setfolder") && args.length > 1) {
            downloadFolder = args[1];
            config.set("download-folder", downloadFolder);
            saveConfig();
            sender.sendMessage("Folder download diubah menjadi: " + downloadFolder);
            return true;
        }

        if (args[0].equalsIgnoreCase("setspeed") && args.length > 1) {
            try {
                downloadSpeed = Integer.parseInt(args[1]);
                if (downloadSpeed > 5) downloadSpeed = 5; // Maksimal 5 Mbps
                config.set("download-speed", downloadSpeed);
                saveConfig();
                sender.sendMessage("Kecepatan download diubah menjadi: " + downloadSpeed + " Mbps");
            } catch (NumberFormatException e) {
                sender.sendMessage("Kecepatan download harus berupa angka.");
            }
            return true;
        }

        return false;
    }

    private void startDownload(String fileUrl, CommandSender sender) {
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                URL url = new URL(fileUrl);
                String fileName = new File(url.getFile()).getName();
                File destinationFile = new File(getServer().getPluginManager().getPlugin("Filed").getDataFolder(), downloadFolder + "/" + fileName);

                // Membaca ukuran file
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("HEAD");
                connection.connect();
                int fileSize = connection.getContentLength();
                connection.disconnect();

                // Membuat folder jika tidak ada
                if (!destinationFile.getParentFile().exists()) {
                    destinationFile.getParentFile().mkdirs();
                }

                downloadFile(url, destinationFile, fileSize, sender);
            } catch (IOException e) {
                sender.sendMessage("Gagal mengunduh file: " + e.getMessage());
            }
        });
    }

    private void downloadFile(URL url, File destination, int fileSize, CommandSender sender) {
        Bukkit.getScheduler().runTask(this, () -> {
            try (InputStream inputStream = url.openStream();
                 FileOutputStream fileOutputStream = new FileOutputStream(destination)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                int totalBytesRead = 0;
                int lastDisplayedPercentage = -1;
                long lastDisplayedTime = System.currentTimeMillis();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    totalBytesRead += bytesRead;
                    fileOutputStream.write(buffer, 0, bytesRead);

                    // Menghitung persentase
                    int percentage = (int) ((double) totalBytesRead / fileSize * 100);
                    int mbDownloaded = totalBytesRead / (1024 * 1024); // Mengonversi ke MB

                    // Menampilkan progres download setiap 0.5 detik (2 kali per detik)
                    if (System.currentTimeMillis() - lastDisplayedTime >= 500) { // 500ms = 0.5 detik
                        lastDisplayedTime = System.currentTimeMillis();
                        if (percentage != lastDisplayedPercentage) {
                            sender.sendMessage("Downloading: " + destination.getName() + " " + percentage + "% (" + mbDownloaded + "MB downloaded)");
                            lastDisplayedPercentage = percentage;
                        }
                    }

                    // Menunggu sesuai dengan kecepatan download yang disetting
                    limitDownloadSpeed(bytesRead);
                }

                sender.sendMessage("Download selesai: " + destination.getName());
            } catch (IOException e) {
                sender.sendMessage("Gagal mengunduh file: " + e.getMessage());
            }
        });
    }

    private void limitDownloadSpeed(int bytesRead) {
        try {
            int delayInMillis = (int) (bytesRead * 8.0 / (downloadSpeed * 1024 * 1024) * 1000); // Menghitung delay untuk mencapai kecepatan download yang diinginkan
            Thread.sleep(delayInMillis);
        } catch (InterruptedException e) {
            getLogger().warning("Thread download terinterupsi: " + e.getMessage());
        }
    }
}
