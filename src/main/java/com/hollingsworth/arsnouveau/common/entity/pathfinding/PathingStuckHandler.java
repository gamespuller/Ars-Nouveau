package com.hollingsworth.arsnouveau.common.entity.pathfinding;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.damagesource.EntityDamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Stuck handler for pathing
 */
public class PathingStuckHandler implements IStuckHandler
{
    /**
     * The distance at which we consider a target to arrive
     */
    private static final double MIN_TARGET_DIST = 3;

    /**
     * All directions.
     */
    public static final List<Direction> HORIZONTAL_DIRS = Arrays.asList(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST);

    /**
     * Constants related to tp.
     */
    private static final int MAX_TP_DELAY    = 60 * 20 * 5;
    private static final int MIN_DIST_FOR_TP = 10;

    /**
     * Amount of path steps allowed to teleport on stuck, 0 = disabled
     */
    private int teleportRange = 0;

    /**
     * Max timeout per block to go, default = 10sec per block
     */
    private int timePerBlockDistance = 200;

    /**
     * The current stucklevel, determines actions taken
     */
    private int stuckLevel = 0;

    /**
     * Global timeout counter, used to determine when we're completly stuck
     */
    private int globalTimeout = 0;

    /**
     * The previously desired go to position of the entity
     */
    private BlockPos prevDestination = BlockPos.ZERO;

    /**
     * Whether breaking blocks is enabled
     */
    private boolean canBreakBlocks = false;

    /**
     * Whether placing ladders is enabled
     */
    private boolean canPlaceLadders = false;

    /**
     * Whether leaf bridges are enabled
     */
    private boolean canBuildLeafBridges = false;

    /**
     * Whether teleport to goal at full stuck is enabled
     */
    private boolean canTeleportGoal = false;

    /**
     * Whether take damage on stuck is enabled
     */
    private boolean takeDamageOnCompleteStuck = false;
    private float   damagePct                 = 0.2f;

    /**
     * BLock break range on complete stuck
     */
    private int completeStuckBlockBreakRange = 0;

    /**
     * Temporary comparison variables to compare with last update
     */
    private boolean hadPath         = false;
    private int     lastPathIndex   = -1;
    private int     progressedNodes = 0;

    /**
     * Delay before taking unstuck actions in ticks, default 60 seconds
     */
    private int delayBeforeActions       = 10 * 20;
    private int delayToNextUnstuckAction = delayBeforeActions;

    private Random rand = new Random();

    private PathingStuckHandler()
    {
    }

    /**
     * Creates a new stuck handler
     *
     * @return new stuck handler
     */
    public static PathingStuckHandler createStuckHandler()
    {
        return new PathingStuckHandler();
    }

    /**
     * Checks the entity for stuck
     *
     * @param navigator navigator to check
     */
    @Override
    public void checkStuck(final AbstractAdvancedPathNavigate navigator)
    {
        if (navigator.getDesiredPos() == null || navigator.getDesiredPos().equals(BlockPos.ZERO))
        {
            resetGlobalStuckTimers();
            return;
        }

        if (navigator.getOurEntity() instanceof IStuckHandlerEntity && !((IStuckHandlerEntity) navigator.getOurEntity()).canBeStuck())
        {
            return;
        }

        final double distanceToGoal =
          navigator.getOurEntity().position().distanceTo(new Vec3(navigator.getDesiredPos().getX(), navigator.getDesiredPos().getY(), navigator.getDesiredPos().getZ()));

        // Close enough to be considered at the goal
        if (distanceToGoal < MIN_TARGET_DIST)
        {
            resetGlobalStuckTimers();
            return;
        }

        // Global timeout check
        if (prevDestination.equals(navigator.getDesiredPos()))
        {
            globalTimeout++;

            // Try path first, if path fits target pos
            if (globalTimeout > Math.min(MAX_TP_DELAY, timePerBlockDistance * Math.max(MIN_DIST_FOR_TP, distanceToGoal)))
            {
                completeStuckAction(navigator);
            }
        }
        else
        {
            resetGlobalStuckTimers();
        }

        prevDestination = navigator.getDesiredPos();

        if (navigator.getPath() == null || navigator.getPath().isDone())
        {
            // With no path reset the last path index point to -1
            lastPathIndex = -1;
            progressedNodes = 0;

            // Stuck when we have no path and had no path last update before
            if (!hadPath)
            {
                tryUnstuck(navigator);
            }
        }
        else
        {
            if (navigator.getPath().getNextNodeIndex() == lastPathIndex)
            {
                // Stuck when we have a path, but are not progressing on it
                tryUnstuck(navigator);
            }
            else
            {
                if (lastPathIndex != -1 && navigator.getPath().getTarget().distSqr(prevDestination) < 25)
                {
                    progressedNodes = navigator.getPath().getNextNodeIndex() > lastPathIndex ? progressedNodes + 1 : progressedNodes - 1;

                    if (progressedNodes > 5)
                    {
                        // Not stuck when progressing
                        resetStuckTimers();
                    }
                }
            }
        }

        lastPathIndex = navigator.getPath() != null ? navigator.getPath().getNextNodeIndex() : -1;

        hadPath = navigator.getPath() != null && !navigator.getPath().isDone();
    }

    /**
     * Resets global stuck timers
     */
    private void resetGlobalStuckTimers()
    {
        globalTimeout = 0;
        prevDestination = BlockPos.ZERO;
        resetStuckTimers();
    }

    /**
     * Final action when completly stuck before resetting stuck handler and path
     */
    private void completeStuckAction(final AbstractAdvancedPathNavigate navigator)
    {
        final BlockPos desired = navigator.getDesiredPos();
        final Level world = navigator.getOurEntity().level;
        final Mob entity = navigator.getOurEntity();

        if (canTeleportGoal)
        {
            for (final Direction dir : HORIZONTAL_DIRS)
            {
                // need two air
                if (world.isEmptyBlock(desired.relative(dir)) && world.isEmptyBlock(desired.relative(dir).above()))
                {
                    // Teleport
                    entity.teleportTo(desired.relative(dir).getX() + 0.5d, desired.relative(dir).getY(), desired.relative(dir).getZ() + 0.5d);
                    break;
                }
            }
        }
        if (takeDamageOnCompleteStuck)
        {
            entity.hurt(new EntityDamageSource("Stuck-damage", entity), entity.getMaxHealth() * damagePct);
        }

        if (completeStuckBlockBreakRange > 0)
        {
            final Direction facing = getFacing(new BlockPos(entity.position()), navigator.getDesiredPos());

            for (int i = 1; i <= completeStuckBlockBreakRange; i++)
            {
                if (!world.isEmptyBlock(new BlockPos(entity.position()).relative(facing, i)) || !world.isEmptyBlock(new BlockPos(entity.position()).relative(facing, i).above()))
                {
                    breakBlocksAhead(world, new BlockPos(entity.position()).relative(facing, i - 1), facing);
                    break;
                }
            }
        }

        navigator.stop();
        resetGlobalStuckTimers();
    }

    /**
     * Calculate in which direction a pos is facing.
     *
     * @param pos      the pos.
     * @param neighbor the block its facing.
     * @return the directions its facing.
     */
    public static Direction getFacing(final BlockPos pos, final BlockPos neighbor)
    {
        final BlockPos vector = neighbor.subtract(pos);
        return Direction.getNearest(vector.getX(), vector.getY(), -vector.getZ());
    }

    /**
     * Tries unstuck options depending on the level
     */
    private void tryUnstuck(final AbstractAdvancedPathNavigate navigator)
    {
        if (delayToNextUnstuckAction-- > 0)
        {
            return;
        }

        // Clear path
        if (stuckLevel == 0)
        {
            stuckLevel++;
            delayToNextUnstuckAction = 600;
            navigator.stop();
            return;
        }

        // Skip ahead
        if (stuckLevel == 2 && teleportRange > 0 && hadPath)
        {
            int index = Math.min(navigator.getPath().getNextNodeIndex() + teleportRange, navigator.getPath().getNodeCount() - 1);
            final Node togo = navigator.getPath().getNode(index);
            navigator.getOurEntity().teleportTo(togo.x + 0.5d, togo.y, togo.z + 0.5d);
            delayToNextUnstuckAction = 300;
        }

        chanceStuckLevel();

        if (stuckLevel == 8)
        {
            resetStuckTimers();
        }
    }

    /**
     * Random chance to decrease to a previous level of stuck
     */
    private void chanceStuckLevel()
    {
        stuckLevel++;
        // 20 % to decrease to the previous level again
        if (stuckLevel > 1 && rand.nextInt(6) == 0)
        {
            stuckLevel -= 2;
        }
    }

    /**
     * Resets timers
     */
    private void resetStuckTimers()
    {
        delayToNextUnstuckAction = delayBeforeActions;
        lastPathIndex = -1;
        progressedNodes = 0;
        stuckLevel = 0;
    }

    /**
     * Attempt to break blocks that are blocking the entity to reach its destination.
     * @param world the world it is in.
     * @param start the position the entity is at.
     * @param facing the direction the goal is in.
     */
    private void breakBlocksAhead(final Level world, final BlockPos start, final Direction facing)
    {
        // Above entity
        if (!world.isEmptyBlock(start.above(3)))
        {
            setAirIfPossible(world, start.above(3));
            return;
        }

        // In goal direction
        if (!world.isEmptyBlock(start.relative(facing)))
        {
            setAirIfPossible(world, start.relative(facing));
            return;
        }

        // Goal direction up
        if (!world.isEmptyBlock(start.above().relative(facing)))
        {
            setAirIfPossible(world, start.above().relative(facing));
        }
    }

    /**
     * Check if the block at the position is indestructible, if not, attempt to break it.
     * @param world the world the block is in.
     * @param pos the pos the block is at.
     */
    private void setAirIfPossible(final Level world, final BlockPos pos)
    {
        final Block blockAtPos = world.getBlockState(pos).getBlock();
        world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
    }

    /**
     * Enables teleporting a certain amount of steps along a generated path
     *
     * @param steps steps to teleport
     * @return this
     */
    public PathingStuckHandler withTeleportSteps(int steps)
    {
        teleportRange = steps;
        return this;
    }

    public PathingStuckHandler withTeleportOnFullStuck()
    {
        canTeleportGoal = true;
        return this;
    }

    public PathingStuckHandler withTakeDamageOnStuck(float damagePct)
    {
        this.damagePct = damagePct;
        takeDamageOnCompleteStuck = true;
        return this;
    }

    /**
     * Sets the time per block distance to travel, before timing out
     *
     * @param time in ticks to set
     * @return this
     */
    public PathingStuckHandler withTimePerBlockDistance(int time)
    {
        timePerBlockDistance = time;
        return this;
    }

    /**
     * Sets the delay before taking stuck actions
     *
     * @param delay to set
     * @return this
     */
    public PathingStuckHandler withDelayBeforeStuckActions(int delay)
    {
        delayBeforeActions = delay;
        return this;
    }

    /**
     * Sets the block break range on complete stuck
     *
     * @param range to set
     * @return this
     */
    public PathingStuckHandler withCompleteStuckBlockBreak(int range)
    {
        completeStuckBlockBreakRange = range;
        return this;
    }
}
