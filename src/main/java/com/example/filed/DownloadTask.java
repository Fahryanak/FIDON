package com.example.filed;

import org.bukkit.command.CommandSender;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class DownloadTask implements Runnable {
    private final FiledPlugin plugin;
    private final URL url;
    private final File destination;
    private final int downloadSpeed;
    private final CommandSender sender;
    private int progress = 0;

    public DownloadTask(FiledPlugin plugin, URL url, File destination, int downloadSpeed, CommandSender sender) {
        this.plugin = plugin;
        this.url = url;
        this.destination = destination;
        this.downloadSpeed = downloadSpeed;
        this.sender = sender;
    }

    @Override
    public void run() {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            int fileSize = connection.getContentLength();
            connection.disconnect();

            long downloadedBytes = destination.exists() ? destination.length() : 0;
            if (downloadedBytes > 0) {
                sender.sendMessage("Melanjutkan unduhan: " + destination.getName());
            }

            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Range", "bytes=" + downloadedBytes + "-");

            try (InputStream inputStream = connection.getInputStream();
                 RandomAccessFile file = new RandomAccessFile(destination, "rw")) {

                file.seek(downloadedBytes);
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = downloadedBytes;
                long startTime = System.currentTimeMillis();
                long lastUpdateTime = System.currentTimeMillis();

                sender.sendMessage("Memulai unduhan: " + destination.getName());
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    file.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastUpdateTime >= 500) { // Update setiap 500ms atau 2 kali per detik
                        int newProgress = (int) ((double) totalBytesRead / fileSize * 100);
                        progress = newProgress;
                        long elapsedTime = (currentTime - startTime) / 1000; // Dalam detik
                        long speed = totalBytesRead / (elapsedTime + 1); // Byte per detik
                        long eta = (fileSize - totalBytesRead) / (speed > 0 ? speed : 1); // Detik
                        sender.sendMessage("Downloading: " + progress + "%, ETA: " + eta + "s");
                        lastUpdateTime = currentTime;
                    }

                    limitDownloadSpeed(bytesRead);
                }

                sender.sendMessage("Unduhan selesai: " + destination.getName());
                plugin.addDownloadToHistory(destination.getName());
                plugin.onDownloadComplete(url.toString());
            }
        } catch (Exception e) {
            sender.sendMessage("Gagal mengunduh file: " + e.getMessage());
        } finally {
            plugin.onDownloadComplete(url.toString());
        }
    }

    private void limitDownloadSpeed(int bytesRead) throws InterruptedException {
        double delay = (bytesRead * 8.0 / (downloadSpeed * 1024 * 1024)) * 1000;
        Thread.sleep((long) delay);
    }

    public int getProgress() {
        return progress;
    }
}
