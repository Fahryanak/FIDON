package com.example.filed;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DownloadPlugin extends JavaPlugin {

    private static final String LINK_FILE = "link.txt";

    @Override
    public void onEnable() {
        getLogger().info("Filed Plugin Enabled!");

        File file = new File(getDataFolder(), LINK_FILE);
        if (!file.exists()) {
            try {
                getDataFolder().mkdirs();
                file.createNewFile();
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                    writer.write("# Add your direct links here, one per line.");
                }
                getLogger().info("link.txt created.");
            } catch (IOException e) {
                getLogger().severe("Failed to create link.txt: " + e.getMessage());
                return;
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, this::downloadFiles);
    }

    private void downloadFiles() {
        File file = new File(getDataFolder(), LINK_FILE);
        try {
            List<String> links = Files.readAllLines(file.toPath());
            for (String link : links) {
                if (link.isEmpty() || link.startsWith("#")) {
                    continue;
                }

                getLogger().info("Downloading: " + link);
                try (InputStream in = new URL(link).openStream()) {
                    String fileName = Paths.get(new URL(link).getPath()).getFileName().toString();
                    File outputFile = new File(getDataFolder(), fileName);
                    Files.copy(in, outputFile.toPath());
                    getLogger().info("Downloaded: " + fileName);
                } catch (IOException e) {
                    getLogger().severe("Failed to download " + link + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            getLogger().severe("Error reading link.txt: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Filed Plugin Disabled!");
    }
}
