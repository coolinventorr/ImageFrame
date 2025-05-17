package com.loohp.imageframe.sound;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.loohp.imageframe.ImageFrame;
import org.bukkit.entity.Player;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ResourcePackSoundManager implements AutoCloseable {

    private static final Gson GSON = new Gson();

    private final File packDir;
    private final File soundsDir;
    private final File soundsJson;
    private final File packFile;
    private final Map<String, String> soundMap;

    public ResourcePackSoundManager(File webRoot) {
        this.packDir = new File(webRoot, "soundpack");
        this.soundsDir = new File(packDir, "assets/imageframe/sounds");
        this.soundsJson = new File(packDir, "assets/imageframe/sounds.json");
        this.packFile = new File(webRoot, "soundpack.zip");
        this.soundMap = new HashMap<>();
        init();
    }

    private void init() {
        if (!soundsDir.exists()) {
            soundsDir.mkdirs();
        }
        File mcmeta = new File(packDir, "pack.mcmeta");
        if (!mcmeta.exists()) {
            JsonObject meta = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 6);
            pack.addProperty("description", "ImageFrame");
            meta.add("pack", pack);
            try (Writer writer = new FileWriter(mcmeta)) {
                GSON.toJson(meta, writer);
            } catch (IOException ignore) {
            }
        }
        saveSoundsJson();
        try {
            rebuildPack();
        } catch (IOException ignore) {
        }
    }

    private void saveSoundsJson() {
        JsonObject root = new JsonObject();
        for (Map.Entry<String, String> entry : soundMap.entrySet()) {
            JsonObject sound = new JsonObject();
            sound.add("sounds", GSON.toJsonTree(Collections.singletonList(entry.getValue())));
            root.add(entry.getKey(), sound);
        }
        if (!soundsJson.getParentFile().exists()) {
            soundsJson.getParentFile().mkdirs();
        }
        try (Writer writer = new FileWriter(soundsJson)) {
            GSON.toJson(root, writer);
        } catch (IOException ignore) {
        }
    }

    public synchronized String addSound(byte[] data) throws IOException {
        String id = UUID.randomUUID().toString().replace("-", "");
        File file = new File(soundsDir, id + ".ogg");
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
        String key = "imageframe." + id;
        soundMap.put(key, "imageframe/sounds/" + id);
        saveSoundsJson();
        rebuildPack();
        return key;
    }

    private void rebuildPack() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(packFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            Path base = packDir.toPath();
            Files.walk(base).forEach(p -> {
                if (Files.isRegularFile(p)) {
                    String entry = base.relativize(p).toString().replace("\\", "/");
                    try {
                        zos.putNextEntry(new ZipEntry(entry));
                        Files.copy(p, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            });
        }
    }

    private static String sha1(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (InputStream is = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public void applyResourcePack(Player player) {
        try {
            player.setResourcePack(packFile.toURI().toString(), sha1(packFile));
        } catch (Exception ignore) {
        }
    }

    public void playSound(Player player, String key) {
        if (key != null) {
            player.playSound(player.getLocation(), key, 1.0f, 1.0f);
        }
    }

    @Override
    public void close() {
    }
}
