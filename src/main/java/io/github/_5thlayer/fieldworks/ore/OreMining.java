// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The draw: one break takes one unit and the block stands until the count reaches zero (ADR-0002).
 * A hand break is cancelled rather than allowed to destroy a block holding hundreds of units, and
 * a machine takes from the same number through {@link #draw}. A depleted block becomes stone, so
 * a patch lies flush with the ground it was dealt on.
 */
public final class OreMining {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The block {@link #draw} is replacing with stone, whose removal is not a loss of its units. */
    private static @Nullable BlockPos depleting;

    private OreMining() {
    }

    /**
     * A player breaking an ore block. A creative break is let through: it is a build gesture, and
     * cancelling it would leave a creative player unable to remove ore at all. A break with too weak
     * a tool draws nothing; the block survives with its amount intact.
     */
    public static void onBreak(BreakBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        if (!(state.getBlock() instanceof OreBlock ore)) {
            return;
        }
        Player player = event.getPlayer();
        if (player != null && player.getAbilities().instabuild) {
            return;
        }
        event.setCanceled(true);
        if (player != null && !player.hasCorrectToolForDrops(state)) {
            return;
        }
        BlockPos pos = event.getPos();
        if (draw(level, ore, pos).paid() > 0) {
            drop(level, pos, ore.resource());
        }
    }

    /**
     * Takes one unit out of an ore block and leaves the position showing what is left. The one
     * extraction mechanism there is: it pays nothing out itself, and hands back the draw so the
     * caller decides where the unit goes.
     */
    public static OreDelta.Draw draw(ServerLevel level, OreBlock ore, BlockPos pos) {
        @Nullable PatchDisc disc = discOf(level, ore, pos);
        int initial = disc != null ? disc.amountPerBlock() : 0;
        OreDelta delta = deltaOf(level, pos);
        OreDelta.Draw draw = delta.draw(pos.asLong(), initial);
        level.getChunk(pos).markUnsaved();

        LOGGER.debug("ore draw: {} at {}, initial={}, paid={}, remaining={}, exhausted={}",
                ore.resource().key(), pos, initial, draw.paid(), draw.remaining(), draw.exhausted());
        if (disc != null && draw.paid() > 0) {
            PatchLedgerData.get(level.getServer()).drew(PatchId.of(dimension(level), disc));
        }
        if (draw.exhausted()) {
            depleting = pos;
            try {
                level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            } finally {
                depleting = null;
            }
            if (disc != null) {
                blockGone(level, disc, 0);
            }
        } else {
            level.setBlockAndUpdate(pos, ore.stateFor(draw.remaining(), initial));
        }
        return draw;
    }

    /** What is left in the block at {@code pos}, for a HUD. */
    public static int remaining(Level level, BlockPos pos, OreBlock ore) {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        return deltaOf(server, pos).remaining(pos.asLong(), initialAmount(server, ore, pos));
    }

    /** The block's initial amount: its patch's quotient, or {@code 0} for a block no patch placed. */
    public static int initialAmount(ServerLevel level, OreBlock ore, BlockPos pos) {
        PatchDisc disc = discOf(level, ore, pos);
        return disc != null ? disc.amountPerBlock() : 0;
    }

    /** The disc a block belongs to, or null for a block no patch placed. */
    private static @Nullable PatchDisc discOf(ServerLevel level, OreBlock ore, BlockPos pos) {
        for (StructureStart start : level.structureManager().startsForStructure(ChunkPos.containing(pos), structure -> true)) {
            for (StructurePiece piece : start.getPieces()) {
                if (piece instanceof PatchDisc.Source source
                        && source.disc().resource() == ore.resource()
                        && piece.getBoundingBox().isInside(pos)
                        && source.covers(pos.getX(), pos.getZ())) {
                    return source.disc();
                }
            }
        }
        return null;
    }

    private static void blockGone(ServerLevel level, PatchDisc disc, int unitsLost) {
        PatchLedgerData.get(level.getServer()).blockGone(PatchId.of(dimension(level), disc), unitsLost, disc.blockCount());
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /**
     * Any route out of being an ore block but depletion, so a delta never outlives the block it
     * counted. A block removed any other way takes its undrawn units out of its patch's ledger.
     */
    public static void onRemoved(Level level, BlockPos pos, OreBlock ore) {
        if (level instanceof ServerLevel server) {
            OreDelta delta = deltaOf(server, pos);
            if (!pos.equals(depleting)) {
                PatchDisc disc = discOf(server, ore, pos);
                if (disc != null) {
                    blockGone(server, disc, delta.remaining(pos.asLong(), disc.amountPerBlock()));
                }
            }
            delta.retire(pos.asLong());
            server.getChunkAt(pos).markUnsaved();
        }
    }

    private static OreDelta deltaOf(ServerLevel level, BlockPos pos) {
        return level.getChunkAt(pos).getData(FieldworksRegistries.ORE_DELTA);
    }

    private static void drop(ServerLevel level, BlockPos pos, OreResource resource) {
        Identifier id = Identifier.parse(resource.drop());
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(id).map(Holder::value).orElse(Items.AIR));
        if (stack.isEmpty()) {
            LOGGER.error("{} pays out {}, which nothing registers: the draw is lost", resource.key(), resource.drop());
            return;
        }
        level.addFreshEntity(new ItemEntity(
                level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
    }
}
