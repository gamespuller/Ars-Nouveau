package com.hollingsworth.arsnouveau.api.event;

import com.hollingsworth.arsnouveau.api.spell.Spell;
import com.hollingsworth.arsnouveau.api.spell.SpellContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.Cancelable;

@Cancelable
public class SpellCastEvent extends LivingEvent {

    public Spell spell;
    public SpellContext context;

    public SpellCastEvent(LivingEntity entity, Spell spell, SpellContext context){
        super(entity);
        this.spell = spell;
        this.context = context;
    }

    public Level getWorld(){
        return this.getEntityLiving().level;
    }
}
