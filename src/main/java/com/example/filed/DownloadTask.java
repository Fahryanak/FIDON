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
    private final int maxFileSize; // Dalam MB
    private final CommandSender sender;
    private int progress = 0;

    public DownloadTask(FiledPlugin plugin, URL url, File destination, int downloadSpeed, int maxFileSize, CommandSender sender) {
        this.plugin = plugin;
        this.url = url;
        this.destination = destination;
        this.downloadSpeed = downloadSpeed;
        this.maxFileSize = maxFileSize;
        this.sender = sender;
    }

    @Override
    public void run() {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            int fileSize = connection.getContentLength(); // Dalam byte
            connection.disconnect();

            // Validasi ukuran file
            if (fileSize > maxFileSize * 1024 * 1024) {
                sender.sendMessage("Gagal: File terlalu besar (maks: " + maxFileSize + " MB).");
                return;
            }

            // Cek resume jika file sudah ada
            long downloadedBytes = destination.exists() ? destination.length() : 0;
            if (downloadedBytes > 0) {
                sender.sendMessage("Melanjutkan unduhan: " + destination.getName());
            }

            // Mulai unduhan
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("Range", "bytes=" + downloadedBytes + "-");

            try (InputStream inputStream = connection.getInputStream();
                 RandomAccessFile file = new RandomAccessFile(destination, "rw")) {

                file.seek(downloadedBytes); // Lanjutkan dari byte terakhir
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = downloadedBytes;
                long startTime = System.currentTimeMillis();

                sender.sendMessage("Memulai unduhan: " + destination.getName());
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    file.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;

                    // Update progress
                    int newProgress = (int) ((double) totalBytesRead / fileSize * 100);
                    if (newProgress != progress) {
                        progress = newProgress;
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        long speed = totalBytesRead / (elapsedTime / 1000 + 1); // Byte per detik
                        long eta = (fileSize - totalBytesRead) / speed; // Detik
                        sender.sendMessage("Downloading: " + progress + "%, ETA: " + eta + "s");
                    }

                    // Batasi kecepatan
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
        double delay = (bytesRead * 8.0 / (downloadSpeed * 1024 * 1024)) * 1000; // ms
        Thread.sleep((long) delay);
    }

    public int getProgress() {
        return progress;
    }
}
