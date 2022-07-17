package com.hollingsworth.arsnouveau.api.potion;

import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;

import java.util.*;

public class PotionData {
    public Potion potion;
    public List<MobEffectInstance> customEffects;
    public int amount;

    public PotionData(Potion potion, List<MobEffectInstance> customEffects, int amount) {
        this.potion = potion;
        this.customEffects = customEffects;
        this.amount = amount;
    }

    public PotionData(){
        this(Potions.EMPTY, new ArrayList<>(), 0);
    }

    public PotionData(ItemStack stack, int amount){
        this(PotionUtils.getPotion(stack), PotionUtils.getMobEffects(stack), amount);
    }

    public PotionData(Potion potion, int amount){
        this(potion, new ArrayList<>(), amount);
    }

    public ItemStack asPotionStack(){
        ItemStack potionStack = new ItemStack(Items.POTION);
        PotionUtils.setPotion(potionStack, this.potion);
        PotionUtils.setCustomEffects(potionStack, customEffects);
        return potionStack;
    }

    public static PotionData fromTag(CompoundTag tag){
        PotionData instance = new PotionData();
        instance.amount = tag.getInt("amount");
        instance.potion = PotionUtils.getPotion(tag);
        instance.customEffects.addAll(PotionUtils.getCustomEffects(tag));
        return instance;
    }

    public CompoundTag toTag(){
        CompoundTag tag = new CompoundTag();
        tag.putInt("amount", amount);
        tag.putString("Potion", Registry.POTION.getKey(potion).toString());
        if (!customEffects.isEmpty()) {
            ListTag listnbt = new ListTag();

            for (MobEffectInstance effectinstance : customEffects) {
                listnbt.add(effectinstance.save(new CompoundTag()));
            }

            tag.put("CustomPotionEffects", listnbt);
        }
        return tag;
    }

    public List<MobEffectInstance> fullEffects(){
        List<MobEffectInstance> thisEffects = new ArrayList<>(customEffects);
        thisEffects.addAll(potion.getEffects());
        return thisEffects;
    }

    public boolean areSameEffects(List<MobEffectInstance> effects){
        List<MobEffectInstance> thisEffects = fullEffects();
        if(thisEffects.size() != effects.size())
            return false;
        effects.sort(Comparator.comparing(MobEffectInstance::toString));
        thisEffects.sort(Comparator.comparing(MobEffectInstance::toString));
        return thisEffects.equals(effects);
    }

    public boolean areSameEffects(PotionData other){
        return areSameEffects(other.fullEffects());
    }

    public PotionData mergeEffects(PotionData other){
        if(areSameEffects(other))
            return new PotionData(potion, customEffects, amount);
        Set<MobEffectInstance> set = new HashSet<>();
        set.addAll(this.fullEffects());
        set.addAll(this.fullEffects());
        return new PotionData(potion, new ArrayList<>(set), amount);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof PotionData other && areSameEffects(other) && amount == other.amount;
    }
}