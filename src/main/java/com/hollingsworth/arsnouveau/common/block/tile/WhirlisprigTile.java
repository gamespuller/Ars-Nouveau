package com.hollingsworth.arsnouveau.common.block.tile;

import com.hollingsworth.arsnouveau.ArsNouveau;
import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import com.hollingsworth.arsnouveau.api.util.DropDistribution;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.client.particle.GlowParticleData;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import com.hollingsworth.arsnouveau.client.particle.ParticleUtil;
import com.hollingsworth.arsnouveau.common.entity.EntityFollowProjectile;
import com.hollingsworth.arsnouveau.common.entity.Whirlisprig;
import com.hollingsworth.arsnouveau.setup.BlockRegistry;
import com.hollingsworth.arsnouveau.setup.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class WhirlisprigTile extends SummoningTile implements IAnimatable {

    public static TagKey<Block> KINDA_LIKES =  BlockTags.create(new ResourceLocation(ArsNouveau.MODID, "whirlisprig/kinda_likes"));
    public static TagKey<Block> GREATLY_LIKES =  BlockTags.create(new ResourceLocation(ArsNouveau.MODID, "whirlisprig/greatly_likes"));
    public static TagKey<Item> DENIED_DROP =  ItemTags.create(new ResourceLocation(ArsNouveau.MODID, "whirlisprig/denied_drop"));
    public List<ItemStack> ignoreItems = new ArrayList<>();
    public int ticksToNextEval;
    public int moodScore;
    public int diversityScore;
    public int progress;
    public Map<BlockState, Integer> genTable = new HashMap<>();
    public Map<BlockState, Integer> scoreMap = new HashMap<>();

    public WhirlisprigTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public WhirlisprigTile(BlockPos pPos, BlockState pState) {
        super(BlockRegistry.WHIRLISPRIG_TILE, pPos, pState);
    }

    @Override
    public void tick() {
        super.tick();
        if(level.isClientSide){
            for(int i = 0; i < progress/20; i++){
                level.addParticle(
                        GlowParticleData.createData(new ParticleColor(
                                50,
                                255,
                                20
                        )),
                        getBlockPos().getX() +0.5 + ParticleUtil.inRange(-0.1, 0.1)  , getBlockPos().getY() + 1.3  + ParticleUtil.inRange(-0.1, 0.1) , getBlockPos().getZ() +0.5 + ParticleUtil.inRange(-0.1, 0.1),
                        0,0,0);
            }

        }
        if(!level.isClientSide){
            if(ticksToNextEval > 0)
                ticksToNextEval--;
            if(ticksToNextEval <= 0)
                evaluateGrove();

            if(level.getGameTime() % 60 == 0 && progress >= Config.WHIRLISPRIG_MAX_PROGRESS.get() && SourceUtil.takeSourceNearbyWithParticles(worldPosition, level, 5, Config.SYLPH_MANA_COST.get()) != null){
                this.progress = 0;
                DropDistribution<BlockState> blockDropDistribution = new DropDistribution<>(genTable);
                int numDrops = getDropsByDiversity() + 3;
                for(int i = 0; i < numDrops; i++){
                    BlockState block = blockDropDistribution.nextDrop();
                    if(block == null)
                        return;

                    for(ItemStack s : getDrops(blockDropDistribution)){
                        BlockUtil.insertItemAdjacent(level, worldPosition, s);
                    }
                }
                updateBlock();
            }
        }
    }

    public boolean isValidReward(ItemStack stack){
        if (stack.is(DENIED_DROP))
            return false;
        if (ignoreItems == null || ignoreItems.isEmpty())
            return true;
        return ignoreItems.stream().noneMatch(i -> i.sameItem(stack));
    }

    public int getDropsByDiversity(){
        return diversityScore / 2;
    }

    public List<ItemStack> getDrops(DropDistribution<BlockState> blockDropDistribution){
        Supplier<List<ItemStack>> getDrops = () -> Block.getDrops(blockDropDistribution.nextDrop(), (ServerLevel) level, worldPosition, null);

        List<ItemStack> successfulDrops;
        boolean bonusReroll = false;
        for(int numRerolls = 0; numRerolls < (bonusReroll ? 16 : 8); numRerolls++) {
            List<ItemStack> drops = getDrops.get();
            if (drops.isEmpty()) continue;
            successfulDrops = drops.stream().filter(s -> isValidReward(s)).collect(Collectors.toCollection(ArrayList::new));
            bonusReroll = true;
            if (successfulDrops.isEmpty()) continue;
            return successfulDrops;
        }
        return new ArrayList<>();
    }

    public void addProgress(){
        this.progress += this.moodScore / 30;
        if(this.progress > Config.WHIRLISPRIG_MAX_PROGRESS.get())
            this.progress = Config.WHIRLISPRIG_MAX_PROGRESS.get();
        updateBlock();
    }

    public void evaluateGrove(){
        Level world = getLevel();
        Map<BlockState, Integer> defaultMap = new HashMap<>();
        Map<BlockState, Integer> dropMap = new HashMap<>();
        int score = 0;
        for(BlockPos b : BlockPos.betweenClosed(getBlockPos().north(10).west(10).below(1),getBlockPos().south(10).east(10).above(30))){
            BlockState state = world.getBlockState(b);
            BlockState defaultState = state.getBlock().defaultBlockState();
            int points = getScore(defaultState);
            if(world.isOutsideBuildHeight(b) || points == 0)
                continue;
            if(!defaultMap.containsKey(defaultState)) {
                defaultMap.put(defaultState, 0);
            }
            if(!dropMap.containsKey(state)){
                dropMap.put(state, 0);
            }
            if(!state.hasBlockEntity())
                dropMap.put(state, dropMap.get(state) + 1);
            defaultMap.put(defaultState, defaultMap.get(defaultState) + 1);
            score += defaultMap.get(defaultState) <= 50 ? points : 0;
        }
        ticksToNextEval = 20 * 120;
        genTable = dropMap;
        scoreMap = defaultMap;
        diversityScore = defaultMap.keySet().size();
        moodScore = score;
    }

    public static int getScore(BlockState state){

        if(state.getMaterial() == Material.AIR)
            return 0;

        if(state == Blocks.WATER.defaultBlockState() || state == Blocks.GRASS_BLOCK.defaultBlockState() || state == Blocks.PODZOL.defaultBlockState() || state == Blocks.DIRT_PATH.defaultBlockState())
            return 1;

        if(state.getBlock() instanceof BushBlock)
            return 2;


        if(state.getBlock() instanceof StemGrownBlock)
            return 2;

        if(state.is(BlockTags.LOGS))
            return 2;

        if(state.is(BlockTags.LEAVES) || state.getBlock() instanceof LeavesBlock)
            return 1;

        if(state.getMaterial() == Material.PLANT || state.getMaterial() == Material.REPLACEABLE_PLANT)
            return 1;

        if(state.getBlock() instanceof BonemealableBlock)
            return 1;

        if(state.is(KINDA_LIKES))
            return 1;
        if(state.is(GREATLY_LIKES))
            return 2;
        return 0;
    }

    public void convertedEffect() {
        super.convertedEffect();
        if (tickCounter >= 120 && !level.isClientSide) {
            converted = true;
            level.setBlockAndUpdate(worldPosition, level.getBlockState(worldPosition).setValue(SummoningTile.CONVERTED, true));
            Whirlisprig entityWhirlisprig = new Whirlisprig(level, true, new BlockPos(getBlockPos()));
            entityWhirlisprig.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5);
            level.addFreshEntity(entityWhirlisprig);
            ParticleUtil.spawnPoof((ServerLevel) level, worldPosition.above());
            tickCounter = 0;
            return;
        }
        if (tickCounter % 10 == 0 && !level.isClientSide) {
            Random r = level.random;
            int min = -2;
            int max = 2;
            EntityFollowProjectile proj1 = new EntityFollowProjectile(level, worldPosition.offset(r.nextInt(max - min) + min, 3, r.nextInt(max - min) + min), worldPosition, r.nextInt(255), r.nextInt(255), r.nextInt(255));
            level.addFreshEntity(proj1);
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("moodScore", moodScore);
        tag.putInt("diversityScore", diversityScore);
        tag.putInt("progress", progress);
        tag.putInt("evalTicks", ticksToNextEval);
    }

    @Override
    public void load(CompoundTag compound) {
        super.load(compound);
        moodScore = compound.getInt("moodScore");
        diversityScore = compound.getInt("diversityScore");
        progress = compound.getInt("progress");
        ticksToNextEval = compound.getInt("evalTicks");
    }

    @Override
    public void registerControllers(AnimationData data) {
        AnimationController controller = new AnimationController<>(this, "rotateController", 1, this::walkPredicate);
        data.addAnimationController(controller);
    }

    private <T extends IAnimatable> PlayState walkPredicate(AnimationEvent<T> tAnimationEvent) {
        tAnimationEvent.getController().setAnimation(new AnimationBuilder().addAnimation("spin"));
        return PlayState.CONTINUE;
    }

    AnimationFactory factory = new AnimationFactory(this);
    @Override
    public AnimationFactory getFactory() {
        return factory;
    }
}
