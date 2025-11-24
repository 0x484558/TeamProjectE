package dev.hex.teamprojecte;

import moze_intel.projecte.api.ItemInfo;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;

/**
 * Global knowledge and EMC data shared by all players on the server.
 */
public class GlobalKnowledgeData {
    private final Set<ItemInfo> knowledge = new HashSet<>();
    private BigInteger emc = BigInteger.ZERO;
    private boolean fullKnowledge = false;

    public GlobalKnowledgeData() {
    }

    public GlobalKnowledgeData(CompoundTag tag) {
        deserialize(tag);
    }

    public boolean addKnowledge(ItemInfo info) {
        if (fullKnowledge) {
            return false;
        }
        return knowledge.add(info);
    }

    public boolean removeKnowledge(ItemInfo info) {
        return knowledge.remove(info);
    }

    public void clearKnowledge() {
        knowledge.clear();
        fullKnowledge = false;
    }

    public Set<ItemInfo> getKnowledge() {
        return knowledge;
    }

    public void setEmc(BigInteger emc) {
        this.emc = emc;
    }

    public BigInteger getEmc() {
        return emc;
    }

    public void setFullKnowledge(boolean fullKnowledge) {
        this.fullKnowledge = fullKnowledge;
    }

    public boolean hasFullKnowledge() {
        return fullKnowledge;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("emc", emc.toString());
        tag.putBoolean("fullKnowledge", fullKnowledge);

        ListTag knowledgeList = new ListTag();
        for (ItemInfo info : knowledge) {
            ItemInfo.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, info)
                    .resultOrPartial()
                    .ifPresent(knowledgeList::add);
        }
        tag.put("knowledge", knowledgeList);

        return tag;
    }

    public void deserialize(CompoundTag tag) {
        emc = new BigInteger(tag.getString("emc"));
        fullKnowledge = tag.getBoolean("fullKnowledge");

        knowledge.clear();
        ListTag knowledgeList = tag.getList("knowledge", Tag.TAG_COMPOUND);
        for (Tag t : knowledgeList) {
            ItemInfo.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, t)
                    .resultOrPartial()
                    .ifPresent(knowledge::add);
        }
    }
}
