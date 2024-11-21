package com.example.filed;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

public class DownloadTask implements Runnable {
    private final FiledPlugin plugin;
    private final URL fileUrl;
    private final File destinationFile;
    private final int downloadSpeed; // Kecepatan unduhan dalam byte per detik
    private final CommandSender sender;
    private final AtomicBoolean paused = new AtomicBoolean(false); // Mengontrol status pause/resume
    private long bytesDownloaded = 0; // Byte yang telah diunduh
    private long totalFileSize = -1; // Ukuran total file (diambil dari header)

    public DownloadTask(FiledPlugin plugin, URL fileUrl, File destinationFile, int downloadSpeed, CommandSender sender) {
        this.plugin = plugin;
        this.fileUrl = fileUrl;
        this.destinationFile = destinationFile;
        this.downloadSpeed = downloadSpeed;
        this.sender = sender;
    }

    @Override
    public void run() {
        try (HttpURLConnection connection = (HttpURLConnection) fileUrl.openConnection()) {
            connection.setRequestMethod("GET");
            connection.setDoInput(true);
            connection.connect();

            totalFileSize = connection.getContentLengthLong();
            InputStream inputStream = connection.getInputStream();
            BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
            FileOutputStream fileOutputStream = new FileOutputStream(destinationFile);

            byte[] buffer = new byte[8192];
            int bytesRead;
            long startTime = System.currentTimeMillis();

            while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                if (paused.get()) {
                    // Jika unduhan dijeda, tunggu hingga dilanjutkan
                    synchronized (paused) {
                        while (paused.get()) {
                            paused.wait();
                        }
                    }
                }

                fileOutputStream.write(buffer, 0, bytesRead);
                bytesDownloaded += bytesRead;

                // Menghitung dan mengirimkan pembaruan progres unduhan
                if (System.currentTimeMillis() - startTime > 1000) {
                    startTime = System.currentTimeMillis();
                    sendProgressUpdate();
                }

                // Kontrol kecepatan unduhan
                if (bytesDownloaded >= downloadSpeed) {
                    try {
                        long elapsedTime = System.currentTimeMillis() - startTime;
                        if (elapsedTime < 1000) {
                            Thread.sleep(1000 - elapsedTime); // Tidur jika terlalu cepat
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    bytesDownloaded = 0; // Reset hitungan setelah kontrol kecepatan
                }
            }

            bufferedInputStream.close();
            fileOutputStream.close();

            plugin.onDownloadComplete(fileUrl.toString());
            plugin.addDownloadToHistory(destinationFile.getName());
            sendCompletionMessage();

        } catch (IOException | InterruptedException e) {
            plugin.getLogger().warning("Gagal mengunduh file: " + e.getMessage());
        }
    }

    private void sendProgressUpdate() {
        double progress = (double) bytesDownloaded / totalFileSize * 100;
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("Unduhan " + fileUrl + " progress: " + (int) progress + "%"));
    }

    private void sendCompletionMessage() {
        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("Unduhan selesai: " + fileUrl));
    }

    public void pauseDownload() {
        paused.set(true);
    }

    public void resumeDownload() {
        synchronized (paused) {
            paused.set(false);
            paused.notify(); // Melanjutkan proses unduhan
        }
    }

    public String getProgress() {
        if (totalFileSize == -1) return "0%";
        return String.format("%.2f%%", (double) bytesDownloaded / totalFileSize * 100);
    }

    public File getDestinationFile() {
        return destinationFile;
    }
}
