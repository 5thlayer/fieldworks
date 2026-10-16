// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import com.mojang.serialization.MapCodec;
import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import org.jspecify.annotations.Nullable;

/**
 * A plain fluid-handler block for the game tests: two 8,000 mB tanks, each of any fluid, open to insert and
 * extract on every face through NeoForge's fluid capability, as another mod's tank or pipe would be.
 * Registered only in the Module's own runs, from the {@code gametest} source set, so no player's world
 * has the block.
 */
final class TestTank {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Fieldworks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Fieldworks.MOD_ID);

    static final DeferredBlock<Block> BLOCK = BLOCKS.registerBlock("gametest_tank", Block::new);

    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<Entity>> ENTITY =
            BLOCK_ENTITIES.register("gametest_tank", () -> new BlockEntityType<>(Entity::new, BLOCK.get()));

    private TestTank() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(TestTank::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Fluid.BLOCK, ENTITY.get(), (tank, side) -> tank.tanks);
    }

    static final class Block extends BaseEntityBlock {

        private static final MapCodec<Block> CODEC = simpleCodec(Block::new);

        Block(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<Block> codec() {
            return CODEC;
        }

        @Override
        public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Entity(pos, state);
        }
    }

    static final class Entity extends BlockEntity {

        final FluidStacksResourceHandler tanks = new FluidStacksResourceHandler(2, 8000);

        Entity(BlockPos pos, BlockState state) {
            super(ENTITY.get(), pos, state);
        }
    }
}
