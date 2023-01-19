package dev.rosewood.rosestacker.nms.v1_16_R3.storage;

import dev.rosewood.rosestacker.nms.NMSAdapter;
import dev.rosewood.rosestacker.nms.NMSHandler;
import dev.rosewood.rosestacker.nms.storage.EntityDataEntry;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataIOException;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataStorage;
import dev.rosewood.rosestacker.nms.storage.StackedEntityDataStorageType;
import dev.rosewood.rosestacker.nms.v1_16_R3.NMSHandlerImpl;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.server.v1_16_R3.NBTBase;
import net.minecraft.server.v1_16_R3.NBTCompressedStreamTools;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.NBTTagList;
import org.bukkit.entity.LivingEntity;

public class NBTStackedEntityDataStorage extends StackedEntityDataStorage {

    private final NBTTagCompound base;
    private final List<NBTTagCompound> data;

    public NBTStackedEntityDataStorage(LivingEntity livingEntity) {
        super(StackedEntityDataStorageType.NBT, livingEntity);
        this.base = new NBTTagCompound();

        ((NMSHandlerImpl) NMSAdapter.getHandler()).saveEntityToTag(livingEntity, this.base);
        this.stripUnneeded(this.base);
        this.stripAttributeUuids(this.base);

        this.data = Collections.synchronizedList(new LinkedList<>());
    }

    public NBTStackedEntityDataStorage(LivingEntity livingEntity, byte[] data) {
        super(StackedEntityDataStorageType.NBT, livingEntity);
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
             ObjectInputStream dataInput = new ObjectInputStream(inputStream)) {

            this.base = NBTCompressedStreamTools.a((DataInput) dataInput);
            int length = dataInput.readInt();
            List<NBTTagCompound> tags = new LinkedList<>();
            for (int i = 0; i < length; i++)
                tags.add(NBTCompressedStreamTools.a((DataInput) dataInput));
            this.data = Collections.synchronizedList(tags);
        } catch (Exception e) {
            throw new StackedEntityDataIOException(e);
        }
    }

    @Override
    public void addFirst(LivingEntity entity) {
        this.addAt(0, entity);
    }

    @Override
    public void addLast(LivingEntity entity) {
        this.addAt(this.data.size(), entity);
    }

    @Override
    public void addAllFirst(List<EntityDataEntry> stackedEntityDataEntry) {
        stackedEntityDataEntry.forEach(x -> this.addAt(0, x));
    }

    @Override
    public void addAllLast(List<EntityDataEntry> stackedEntityDataEntry) {
        stackedEntityDataEntry.forEach(x -> this.addAt(this.data.size(), x));
    }

    @Override
    public void addClones(int amount) {
        for (int i = 0; i < amount; i++)
            this.data.add(this.base.clone());
    }

    @Override
    public NBTEntityDataEntry peek() {
        return new NBTEntityDataEntry(this.rebuild(this.data.get(0)));
    }

    @Override
    public NBTEntityDataEntry pop() {
        return new NBTEntityDataEntry(this.rebuild(this.data.remove(0)));
    }

    @Override
    public List<EntityDataEntry> pop(int amount) {
        amount = Math.min(amount, this.data.size());

        List<EntityDataEntry> popped = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++)
            popped.add(new NBTEntityDataEntry(this.rebuild(this.data.remove(0))));
        return popped;
    }

    @Override
    public int size() {
        return this.data.size();
    }

    @Override
    public boolean isEmpty() {
        return this.data.isEmpty();
    }

    @Override
    public List<EntityDataEntry> getAll() {
        List<EntityDataEntry> wrapped = new ArrayList<>(this.data.size());
        for (NBTTagCompound compoundTag : new ArrayList<>(this.data))
            wrapped.add(new NBTEntityDataEntry(this.rebuild(compoundTag)));
        return wrapped;
    }

    @Override
    public byte[] serialize(int maxAmount) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ObjectOutputStream dataOutput = new ObjectOutputStream(outputStream)) {

            int targetAmount = Math.min(maxAmount, this.data.size());
            List<NBTTagCompound> tagsToSave = new ArrayList<>(targetAmount);
            Iterator<NBTTagCompound> iterator = this.data.iterator();
            for (int i = 0; i < targetAmount; i++)
                tagsToSave.add(iterator.next());

            NBTCompressedStreamTools.a(this.base, (DataOutput) dataOutput);
            dataOutput.writeInt(tagsToSave.size());
            for (NBTTagCompound compoundTag : tagsToSave)
                NBTCompressedStreamTools.a(compoundTag, (DataOutput) dataOutput);

            dataOutput.close();
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new StackedEntityDataIOException(e);
        }
    }

    @Override
    public void forEach(Consumer<LivingEntity> consumer) {
        this.forEachCapped(Integer.MAX_VALUE, consumer);
    }

    @Override
    public void forEachCapped(int count, Consumer<LivingEntity> consumer) {
        if (count > this.data.size())
            count = this.data.size();

        NMSHandler nmsHandler = NMSAdapter.getHandler();
        LivingEntity thisEntity = this.entity.get();
        if (thisEntity == null)
            return;

        Iterator<NBTTagCompound> iterator = this.data.iterator();
        for (int i = 0; i < count; i++) {
            NBTTagCompound compoundTag = iterator.next();
            LivingEntity entity = new NBTEntityDataEntry(this.rebuild(compoundTag)).createEntity(thisEntity.getLocation(), false, thisEntity.getType());
            consumer.accept(entity);
        }
    }

    @Override
    public List<LivingEntity> removeIf(Function<LivingEntity, Boolean> function) {
        List<LivingEntity> removedEntries = new ArrayList<>(this.data.size());
        LivingEntity thisEntity = this.entity.get();
        if (thisEntity == null)
            return removedEntries;

        NMSHandler nmsHandler = NMSAdapter.getHandler();
        this.data.removeIf(x -> {
            LivingEntity entity = new NBTEntityDataEntry(this.rebuild(x)).createEntity(thisEntity.getLocation(), false, thisEntity.getType());
            boolean removed = function.apply(entity);
            if (removed) removedEntries.add(entity);
            return removed;
        });
        return removedEntries;
    }

    private void addAt(int index, LivingEntity livingEntity) {
        NBTTagCompound compoundTag = new NBTTagCompound();
        ((NMSHandlerImpl) NMSAdapter.getHandler()).saveEntityToTag(livingEntity, compoundTag);
        this.stripUnneeded(compoundTag);
        this.stripAttributeUuids(compoundTag);
        this.removeDuplicates(compoundTag);
        this.data.add(index, compoundTag);
    }

    private void addAt(int index, EntityDataEntry entityDataEntry) {
        NBTTagCompound compoundTag = ((NBTEntityDataEntry) entityDataEntry).get();
        this.stripUnneeded(compoundTag);
        this.stripAttributeUuids(compoundTag);
        this.removeDuplicates(compoundTag);
        this.data.add(index, compoundTag);
    }

    private void removeDuplicates(NBTTagCompound compoundTag) {
        for (String key : new ArrayList<>(compoundTag.getKeys())) {
            NBTBase baseValue = this.base.get(key);
            NBTBase thisValue = compoundTag.get(key);
            if (baseValue != null && baseValue.equals(thisValue))
                compoundTag.remove(key);
        }
    }

    private NBTTagCompound rebuild(NBTTagCompound compoundTag) {
        NBTTagCompound merged = new NBTTagCompound();
        merged.a(this.base);
        merged.a(compoundTag);
        this.fillAttributeUuids(merged);
        return merged;
    }

    private void stripUnneeded(NBTTagCompound compoundTag) {
        compoundTag.remove("UUID");
        compoundTag.remove("Pos");
        compoundTag.remove("Rotation");
        compoundTag.remove("WorldUUIDMost");
        compoundTag.remove("WorldUUIDLeast");
        compoundTag.remove("Motion");
        compoundTag.remove("OnGround");
        compoundTag.remove("FallDistance");
        compoundTag.remove("Leash");
        compoundTag.remove("AngryAt");
        compoundTag.remove("Spigot.ticksLived");
        compoundTag.remove("Paper.OriginWorld");
        compoundTag.remove("Paper.Origin");
        NBTTagCompound bukkitValues = compoundTag.getCompound("BukkitValues");
        bukkitValues.remove("rosestacker:stacked_entity_data");
    }

    private void stripAttributeUuids(NBTTagCompound compoundTag) {
        NBTTagList attributes = compoundTag.getList("Attributes", 10);
        for (int i = 0; i < attributes.size(); i++) {
            NBTTagCompound attribute = attributes.getCompound(i);
            attribute.remove("UUID");
            NBTTagList modifiers = attribute.getList("Modifiers", 10);
            for (int j = 0; j < modifiers.size(); j++) {
                NBTTagCompound modifier = modifiers.getCompound(j);
                if (modifier.getString("Name").equals("Random spawn bonus")) {
                    modifiers.remove(j);
                    j--;
                } else {
                    modifier.remove("UUID");
                }
            }
        }
    }

    private void fillAttributeUuids(NBTTagCompound compoundTag) {
        NBTTagList attributes = compoundTag.getList("Attributes", 10);
        for (int i = 0; i < attributes.size(); i++) {
            NBTTagCompound attribute = attributes.getCompound(i);
            attribute.a("UUID", UUID.randomUUID());
            NBTTagList modifiers = attribute.getList("Modifiers", 10);
            for (int j = 0; j < modifiers.size(); j++) {
                NBTTagCompound modifier = modifiers.getCompound(j);
                modifier.a("UUID", UUID.randomUUID());
            }
            if (modifiers.size() == 0)
                attribute.remove("Modifiers");
        }
    }

}
