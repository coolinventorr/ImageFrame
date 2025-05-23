/*
 * This file is part of ImageFrame.
 *
 * Copyright (C) 2025. LoohpJames <jamesloohp@gmail.com>
 * Copyright (C) 2025. Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.loohp.imageframe.nms;

import com.google.common.collect.Collections2;
import com.loohp.imageframe.objectholders.CombinedMapItemInfo;
import com.loohp.imageframe.objectholders.MutablePair;
import com.loohp.imageframe.utils.UUIDUtils;
import com.loohp.imageframe.utils.UnsafeAccessor;
import net.kyori.adventure.key.Key;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.TranslatableComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import net.minecraft.server.v1_16_R1.ChunkProviderServer;
import net.minecraft.server.v1_16_R1.DataWatcher;
import net.minecraft.server.v1_16_R1.DataWatcherObject;
import net.minecraft.server.v1_16_R1.EntityHuman;
import net.minecraft.server.v1_16_R1.EntityItemFrame;
import net.minecraft.server.v1_16_R1.EntityPlayer;
import net.minecraft.server.v1_16_R1.IChatBaseComponent;
import net.minecraft.server.v1_16_R1.ItemWorldMap;
import net.minecraft.server.v1_16_R1.MapIcon;
import net.minecraft.server.v1_16_R1.MinecraftKey;
import net.minecraft.server.v1_16_R1.NBTTagCompound;
import net.minecraft.server.v1_16_R1.NBTTagList;
import net.minecraft.server.v1_16_R1.NBTTagString;
import net.minecraft.server.v1_16_R1.Packet;
import net.minecraft.server.v1_16_R1.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_16_R1.PacketPlayOutMap;
import net.minecraft.server.v1_16_R1.PersistentIdCounts;
import net.minecraft.server.v1_16_R1.PlayerChunkMap;
import net.minecraft.server.v1_16_R1.PlayerInventory;
import net.minecraft.server.v1_16_R1.ResourceKey;
import net.minecraft.server.v1_16_R1.WorldMap;
import net.minecraft.server.v1_16_R1.WorldServer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.bukkit.craftbukkit.libs.it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.bukkit.craftbukkit.v1_16_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R1.entity.CraftEntity;
import org.bukkit.craftbukkit.v1_16_R1.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R1.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_16_R1.map.CraftMapView;
import org.bukkit.craftbukkit.v1_16_R1.map.RenderData;
import org.bukkit.craftbukkit.v1_16_R1.util.CraftChatMessage;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class V1_16 extends NMSWrapper {

    private final Field nmsEntityByteDataWatcherField;
    private final Field craftMapViewWorldMapField;
    private final Field nmsMapIconTypeDisplayField;
    private final Field[] nmsPacketPlayOutEntityMetadataFields;
    private final Field nmsItemFrameItemStackDataWatcherField;
    private final Field persistentIdCountsUsedAuxIdsField;

    public V1_16() {
        try {
            nmsEntityByteDataWatcherField = net.minecraft.server.v1_16_R1.Entity.class.getDeclaredField("T");
            craftMapViewWorldMapField = CraftMapView.class.getDeclaredField("worldMap");
            nmsMapIconTypeDisplayField = MapIcon.Type.class.getDeclaredField("C");
            nmsPacketPlayOutEntityMetadataFields = PacketPlayOutEntityMetadata.class.getDeclaredFields();
            nmsItemFrameItemStackDataWatcherField = EntityItemFrame.class.getDeclaredField("ITEM");
            persistentIdCountsUsedAuxIdsField = PersistentIdCounts.class.getDeclaredField("a");
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public WorldMap getWorldMap(MapView mapView) {
        try {
            CraftMapView craftMapView = (CraftMapView) mapView;
            craftMapViewWorldMapField.setAccessible(true);
            return (WorldMap) craftMapViewWorldMapField.get(craftMapView);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setColors(MapView mapView, byte[] colors) {
        if (colors.length != COLOR_ARRAY_LENGTH) {
            throw new IllegalArgumentException("colors array length must be " + COLOR_ARRAY_LENGTH);
        }
        WorldMap nmsWorldMap = getWorldMap(mapView);
        nmsWorldMap.colors = colors;
    }

    @Override
    public Collection<Player> getViewers(MapView mapView) {
        WorldMap nmsWorldMap = getWorldMap(mapView);
        Map<EntityHuman, WorldMap.WorldMapHumanTracker> humansMap = nmsWorldMap.humans;
        return Collections2.transform(humansMap.keySet(), e -> (Player) e.getBukkitEntity());
    }

    @Override
    public boolean hasViewers(MapView mapView) {
        WorldMap nmsWorldMap = getWorldMap(mapView);
        Map<EntityHuman, WorldMap.WorldMapHumanTracker> humansMap = nmsWorldMap.humans;
        return !humansMap.isEmpty();
    }

    @Override
    public MapIcon toNMSMapIcon(MapCursor mapCursor) {
        MapIcon.Type mapIconType = toNMSMapIconType(mapCursor.getType());
        IChatBaseComponent iChat = CraftChatMessage.fromStringOrNull(mapCursor.getCaption());
        return new MapIcon(mapIconType, mapCursor.getX(), mapCursor.getY(), mapCursor.getDirection(), iChat);
    }

    @SuppressWarnings("deprecation")
    @Override
    public MapIcon.Type toNMSMapIconType(MapCursor.Type type) {
        return MapIcon.Type.a(type.getValue());
    }

    @Override
    public boolean isRenderOnFrame(MapCursor.Type type) {
        MapIcon.Type mapIconType = toNMSMapIconType(type);
        try {
            nmsMapIconTypeDisplayField.setAccessible(true);
            return nmsMapIconTypeDisplayField.getBoolean(mapIconType);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"unchecked", "resource"})
    @Override
    public int getNextAvailableMapId(World world) {
        try {
            persistentIdCountsUsedAuxIdsField.setAccessible(true);
            WorldServer worldServer = ((CraftWorld) world).getHandle();
            PersistentIdCounts persistentIdCounts = worldServer.getMinecraftServer().D().getWorldPersistentData().a(PersistentIdCounts::new, "idcounts");
            Object2IntMap<String> usedAuxIds = (Object2IntMap<String>) persistentIdCountsUsedAuxIdsField.get(persistentIdCounts);
            return usedAuxIds.getInt("map") + 1;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({"deprecation", "unchecked", "resource"})
    @Override
    public MapView getMapOrCreateMissing(World world, int id) {
        try {
            MapView mapView = Bukkit.getMap(id);
            if (mapView != null) {
                return mapView;
            }
            persistentIdCountsUsedAuxIdsField.setAccessible(true);
            Location spawnLocation = world.getSpawnLocation();
            WorldServer worldServer = ((CraftWorld) world).getHandle();
            ResourceKey<net.minecraft.server.v1_16_R1.World> worldTypeKey = worldServer.getDimensionKey();
            String mapId = ItemWorldMap.a(id);
            WorldMap worldMap = new WorldMap(mapId);
            worldMap.a(spawnLocation.getBlockX(), spawnLocation.getBlockZ(), 3, false, false, worldTypeKey);
            worldServer.a(worldMap);
            PersistentIdCounts persistentIdCounts = worldServer.getMinecraftServer().D().getWorldPersistentData().a(PersistentIdCounts::new, "idcounts");
            Object2IntMap<String> usedAuxIds = (Object2IntMap<String>) persistentIdCountsUsedAuxIdsField.get(persistentIdCounts);
            int freeAuxValue = usedAuxIds.getInt("map");
            if (freeAuxValue < id) {
                usedAuxIds.put("map", id);
                persistentIdCounts.b();
            }
            return Bukkit.getMap(id);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public MutablePair<byte[], List<MapCursor>> bukkitRenderMap(MapView mapView, Player player) {
        CraftMapView craftMapView = (CraftMapView) mapView;
        CraftPlayer craftPlayer = (CraftPlayer) player;
        RenderData renderData = craftMapView.render(craftPlayer);
        return new MutablePair<>(renderData.buffer, renderData.cursors);
    }

    @Override
    public Set<Player> getEntityTrackers(Entity entity) {
        WorldServer worldServer = ((CraftWorld) entity.getWorld()).getHandle();
        ChunkProviderServer chunkProviderServer = worldServer.getChunkProvider();
        PlayerChunkMap playerChunkMap = chunkProviderServer.playerChunkMap;
        Int2ObjectMap<PlayerChunkMap.EntityTracker> entityTrackers = playerChunkMap.trackedEntities;
        PlayerChunkMap.EntityTracker entityTracker = entityTrackers.get(entity.getEntityId());
        if (entityTracker == null) {
            return Collections.emptySet();
        } else {
            Set<Player> players = new HashSet<>();
            for (EntityPlayer player : entityTracker.trackedPlayers) {
                players.add(player.getBukkitEntity());
            }
            return players;
        }
    }

    @Override
    public PacketPlayOutMap createMapPacket(int mapId, byte[] colors, Collection<MapCursor> cursors) {
        List<MapIcon> mapIcons = cursors == null ? Collections.emptyList() : cursors.stream().map(this::toNMSMapIcon).collect(Collectors.toList());
        byte[] colors0;
        int size;
        if (colors == null) {
            colors0 = EMPTY_BYTE_ARRAY;
            size = 0;
        } else {
            colors0 = colors;
            size = 128;
        }
        return new PacketPlayOutMap(mapId, (byte) 0, false, false, mapIcons, colors0, 0, 0, size, size);
    }

    @SuppressWarnings("unchecked")
    @Override
    public PacketPlayOutEntityMetadata createItemFrameItemChangePacket(int entityId, ItemStack itemStack) {
        try {
            nmsItemFrameItemStackDataWatcherField.setAccessible(true);
            DataWatcherObject<net.minecraft.server.v1_16_R1.ItemStack> dataWatcherObject = (DataWatcherObject<net.minecraft.server.v1_16_R1.ItemStack>) nmsItemFrameItemStackDataWatcherField.get(null);
            List<DataWatcher.Item<?>> dataWatchers = Collections.singletonList(new DataWatcher.Item<>(dataWatcherObject, CraftItemStack.asNMSCopy(itemStack)));
            PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata();
            nmsPacketPlayOutEntityMetadataFields[0].setAccessible(true);
            nmsPacketPlayOutEntityMetadataFields[0].setInt(packet, entityId);
            nmsPacketPlayOutEntityMetadataFields[1].setAccessible(true);
            nmsPacketPlayOutEntityMetadataFields[1].set(packet, dataWatchers);
            return packet;
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object createEntityFlagsPacket(Entity entity, Boolean invisible, Boolean glowing) {
        try {
            int entityId = entity.getEntityId();
            net.minecraft.server.v1_16_R1.Entity nmsEntity = ((CraftEntity) entity).getHandle();

            nmsEntityByteDataWatcherField.setAccessible(true);

            DataWatcher watcher = nmsEntity.getDataWatcher();
            DataWatcherObject<Byte> byteField = (DataWatcherObject<Byte>) nmsEntityByteDataWatcherField.get(null);
            byte value = watcher.get(byteField);

            if (invisible != null) {
                if (invisible) {
                    value = (byte) (value | 0x20);
                } else {
                    value = (byte) (value & ~0x20);
                }
            }
            if (glowing != null) {
                if (glowing) {
                    value = (byte) (value | 0x40);
                } else {
                    value = (byte) (value & ~0x40);
                }
            }

            List<DataWatcher.Item<?>> dataWatchers = Collections.singletonList(new DataWatcher.Item<>(byteField, value));
            PacketPlayOutEntityMetadata packet = (PacketPlayOutEntityMetadata) UnsafeAccessor.getUnsafe().allocateInstance(PacketPlayOutEntityMetadata.class);
            nmsPacketPlayOutEntityMetadataFields[0].setAccessible(true);
            nmsPacketPlayOutEntityMetadataFields[0].setInt(packet, entityId);
            nmsPacketPlayOutEntityMetadataFields[1].setAccessible(true);
            nmsPacketPlayOutEntityMetadataFields[1].set(packet, dataWatchers);
            return packet;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sendPacket(Player player, Object packet) {
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket((Packet<?>) packet);
    }

    @Override
    public CombinedMapItemInfo getCombinedMapItemInfo(ItemStack itemStack) {
        net.minecraft.server.v1_16_R1.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = nmsItemStack.getTag();
        if (tag == null || !tag.hasKey(CombinedMapItemInfo.KEY)) {
            return null;
        }
        int imageMapIndex = tag.getInt(CombinedMapItemInfo.KEY);
        if (!tag.hasKey(CombinedMapItemInfo.PLACEMENT_UUID_KEY) || !tag.hasKey(CombinedMapItemInfo.PLACEMENT_YAW_KEY)) {
            return new CombinedMapItemInfo(imageMapIndex);
        }
        float yaw = tag.getFloat(CombinedMapItemInfo.PLACEMENT_YAW_KEY);
        UUID uuid = UUIDUtils.fromIntArray(tag.getIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY));
        return new CombinedMapItemInfo(imageMapIndex, new CombinedMapItemInfo.PlacementInfo(yaw, uuid));
    }

    @Override
    public ItemStack withCombinedMapItemInfo(ItemStack itemStack, CombinedMapItemInfo combinedMapItemInfo) {
        net.minecraft.server.v1_16_R1.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound tag = nmsItemStack.getOrCreateTag();
        tag.setInt(CombinedMapItemInfo.KEY, combinedMapItemInfo.getImageMapIndex());
        if (combinedMapItemInfo.hasPlacement()) {
            CombinedMapItemInfo.PlacementInfo placement = combinedMapItemInfo.getPlacement();
            tag.setFloat(CombinedMapItemInfo.PLACEMENT_YAW_KEY, placement.getYaw());
            tag.setIntArray(CombinedMapItemInfo.PLACEMENT_UUID_KEY, UUIDUtils.toIntArray(placement.getUniqueId()));
        }
        return CraftItemStack.asCraftMirror(nmsItemStack);
    }

    @Override
    public ItemStack withInvisibleItemFrameMeta(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().equals(Material.AIR)) {
            return itemStack;
        }
        net.minecraft.server.v1_16_R1.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
        NBTTagCompound displayTag = nmsItemStack.a("display");
        List<String> loreLines;
        if (displayTag.d("Lore") == 9) {
            NBTTagList loreLineTagList = displayTag.getList("Lore", 8);
            loreLines = new ArrayList<>(loreLineTagList.size());
            for (int i = 0; i < loreLineTagList.size(); i++) {
                loreLines.add(loreLineTagList.getString(i));
            }
        } else {
            loreLines = new ArrayList<>(1);
        }
        TranslatableComponent translatableComponent = new TranslatableComponent("effect.minecraft.invisibility");
        translatableComponent.setColor(ChatColor.GRAY);
        translatableComponent.setItalic(false);
        loreLines.add(0, ComponentSerializer.toString(translatableComponent));
        NBTTagList loreLineTagList = new NBTTagList();
        for (int i = 0; i < loreLines.size(); i++) {
            loreLineTagList.b(i, NBTTagString.a(loreLines.get(i)));
        }
        displayTag.set("Lore", loreLineTagList);
        ItemStack modified = CraftItemStack.asCraftMirror(nmsItemStack);
        ItemMeta itemMeta = modified.getItemMeta();
        if (itemMeta == null) {
            return itemStack;
        }
        itemMeta.addEnchant(Enchantment.LUCK, 1, true);
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        modified.setItemMeta(itemMeta);
        return modified;
    }

    @Override
    public List<ItemStack> giveItems(Player player, List<ItemStack> itemStacks) {
        List<ItemStack> leftovers = new ArrayList<>();
        EntityPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        PlayerInventory inventory = nmsPlayer.inventory;
        for (ItemStack itemStack : itemStacks) {
            net.minecraft.server.v1_16_R1.ItemStack nmsItemStack = CraftItemStack.asNMSCopy(itemStack);
            boolean added = inventory.pickup(nmsItemStack);
            if (!added || !nmsItemStack.isEmpty()) {
                leftovers.add(CraftItemStack.asBukkitCopy(nmsItemStack));
            }
        }
        nmsPlayer.activeContainer.c();
        return leftovers;
    }

    @SuppressWarnings("PatternValidation")
    public Key getWorldNamespacedKey(World world) {
        WorldServer nmsWorld = ((CraftWorld) world).getHandle();
        MinecraftKey key = nmsWorld.getTypeKey().a();
        return Key.key(key.getNamespace(), key.getKey());
    }
}
