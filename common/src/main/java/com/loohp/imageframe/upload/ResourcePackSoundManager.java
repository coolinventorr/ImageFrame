package com.loohp.imageframe.upload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Manages a generated resource pack that stores sounds extracted from mp4 files.
 */
public class ResourcePackSoundManager implements AutoCloseable {

    private final File packDir;
    private final File packFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, String> sounds;

    public ResourcePackSoundManager(File webRootDir) {
        this.packDir = new File(ImageFrame.plugin.getDataFolder(), "resourcepack/work");
        this.packFile = new File(webRootDir, "imageframe_resourcepack.zip");
        this.sounds = new HashMap<>();
        if (!packDir.exists()) {
            packDir.mkdirs();
        }
    }

    /**
     * Adds a new OGG sound to the resource pack.
     *
     * @param data sound data in OGG format
     * @return sound key usable with Bukkit's playSound
     */
    public synchronized String addSound(byte[] data) throws IOException {
        String key = "sound_" + UUID.randomUUID().toString().replace('-', '_');
        File soundFile = new File(packDir, "assets/imageframe/sounds/" + key + ".ogg");
        soundFile.getParentFile().mkdirs();
        Files.write(soundFile.toPath(), data);
        sounds.put(key, "sounds/" + key + ".ogg");
        rebuild();
        return "imageframe:" + key;
    }

    private void rebuild() throws IOException {
        // Write sounds.json
        File soundsJsonFile = new File(packDir, "assets/imageframe/sounds.json");
        JsonObject root = new JsonObject();
        for (String key : sounds.keySet()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("category", "master");
            JsonArray arr = new JsonArray();
            arr.add(key);
            obj.add("sounds", arr);
            root.add(key, obj);
        }
        soundsJsonFile.getParentFile().mkdirs();
        try (Writer writer = new FileWriter(soundsJsonFile)) {
            gson.toJson(root, writer);
        }
        // Write pack.mcmeta
        File mcmeta = new File(packDir, "pack.mcmeta");
        JsonObject meta = new JsonObject();
        meta.addProperty("pack_format", 15);
        meta.addProperty("description", "ImageFrame Audio");
        try (Writer writer = new FileWriter(mcmeta)) {
            gson.toJson(meta, writer);
        }
        // Zip the pack
        if (packFile.exists()) {
            packFile.delete();
        }
        try (FileOutputStream fos = new FileOutputStream(packFile); ZipOutputStream zos = new ZipOutputStream(fos)) {
            Path base = packDir.toPath();
            Files.walk(base).filter(Files::isRegularFile).forEach(p -> {
                try {
                    String name = base.relativize(p).toString().replace(File.separatorChar, '/');
                    zos.putNextEntry(new ZipEntry(name));
                    Files.copy(p, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }

    public String getPackURL() {
        return ImageFrame.uploadServiceDisplayURL + "/" + packFile.getName();
    }

    public void sendPack(Player player) {
        player.setResourcePack(getPackURL());
    }

    @Override
    public void close() {
        // nothing to close
    }
}

