// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import io.github._5thlayer.fieldworks.harvester.HarvestRecipe;
import io.github._5thlayer.fieldworks.harvester.Requirement;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.ore.OreResource;
import io.github._5thlayer.libworks.testkit.GalleryCheck;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * A resource whose Default recipe a Harvest Recipe replaces cannot be drawn by hand, with any tool, and the player is told
 * what it needs (ADR 0010). A pack that drops the recipe gives the hand draw back. All of these share the
 * {@code hand_gate_} prefix, so {@code --tests 'fieldworks:hand_gate_*'} selects them.
 */
final class HandGateTests {

    private static final Identifier HARVESTER_2 = TierBlock.HARVESTER_2.id();

    private HandGateTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        tests.test("hand_gate_a_netherite_pickaxe_takes_nothing_from_uranium", 20,
                helper -> HandMiningTests.mine(helper, OreResource.URANIUM, new ChunkPos(164, 180), Items.NETHERITE_PICKAXE, 0));
        tests.test("hand_gate_a_netherite_pickaxe_takes_nothing_from_diamond", 20,
                helper -> HandMiningTests.mine(helper, OreResource.DIAMOND, new ChunkPos(140, 180), Items.NETHERITE_PICKAXE, 0));
        tests.test("hand_gate_a_netherite_pickaxe_takes_nothing_from_netherite", 20,
                helper -> HandMiningTests.mine(helper, OreResource.NETHERITE, new ChunkPos(148, 180), Items.NETHERITE_PICKAXE, 0));
        tests.test("hand_gate_a_stone_pickaxe_still_takes_one_unit_of_copper", 20,
                helper -> HandMiningTests.mine(helper, OreResource.COPPER, new ChunkPos(156, 180), Items.STONE_PICKAXE, 1));
        tests.test("hand_gate_a_gated_block_outside_a_patch_is_refused", 20, HandGateTests::refusedOutsideAPatch);
        tests.test("hand_gate_a_creative_break_removes_a_gated_block", 20, HandGateTests::creativeRemoves);
        tests.test("hand_gate_the_requirement_names_the_electric_harvester_and_the_overworld_fluid_for_uranium_and_diamond", 20,
                helper -> requirementIs(helper, OreResource.URANIUM, OreResource.DIAMOND, overworld()));
        tests.test("hand_gate_the_requirement_names_the_electric_harvester_and_the_nether_fluid_for_netherite", 20,
                helper -> requirementIs(helper, OreResource.NETHERITE, OreResource.NETHERITE, nether()));
        tests.test("hand_gate_iron_has_no_requirement", 20, HandGateTests::ironNeedsNothing);
        tests.test("hand_gate_a_recipe_with_a_tier_and_no_fluid_reads_needs_the_tier", 20, HandGateTests::tierOnly);
        tests.test("hand_gate_a_recipe_with_no_tier_reads_needs_a_harvester", 20, HandGateTests::noTier);
        tests.test("hand_gate_a_single_fluid_is_named_by_its_own_name", 20, HandGateTests::singleFluid);
        tests.testOnItsOwn("hand_gate_a_pack_that_drops_the_diamond_recipe_gives_the_hand_draw_back", 100, HandGateTests::liftedByAPack);
    }

    // -- the refusal ------------------------------------------------------------------------------

    /** A gated block no patch placed holds its resource's default amount, and a diamond pickaxe takes none of it. */
    private static void refusedOutsideAPatch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        OreBlock ore = FieldworksRegistries.ore(OreResource.DIAMOND).get();
        level.setBlockAndUpdate(pos, ore.defaultBlockState());

        breakWith(level, pos, GameType.SURVIVAL, Items.NETHERITE_PICKAXE);

        helper.assertTrue(level.getBlockState(pos).getBlock() instanceof OreBlock, "a hand break removed the diamond block: " + level.getBlockState(pos));
        helper.assertValueEqual(OreMining.remaining(level, pos, ore), OreResource.DIAMOND.corpus().defaultAmount(), "the block's amount");
        helper.assertValueEqual(dropped(level, pos, OreResource.DIAMOND), 0, "diamonds dropped");
        helper.succeed();
    }

    private static void creativeRemoves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlockAndUpdate(pos, FieldworksRegistries.ore(OreResource.URANIUM).get().defaultBlockState());

        breakWith(level, pos, GameType.CREATIVE, Items.AIR);

        helper.assertTrue(level.getBlockState(pos).isAir(), "a creative break left " + level.getBlockState(pos));
        helper.succeed();
    }

    // -- the requirement text --------------------------------------------------------------------

    private static void requirementIs(GameTestHelper helper, OreResource first, OreResource second, TagKey<Fluid> fluid) {
        Component expected = Component.translatable("fieldworks.requirement.with_fluid", electric(),
                Component.translatable(Tags.getTagTranslationKey(fluid)));
        for (OreResource resource : List.of(first, second)) {
            helper.assertValueEqual(requirement(helper, resource), Optional.of(expected), resource.key() + "'s requirement");
        }
        helper.succeed();
    }

    private static void ironNeedsNothing(GameTestHelper helper) {
        helper.assertValueEqual(requirement(helper, OreResource.IRON), Optional.<Component>empty(), "iron's requirement");
        helper.succeed();
    }

    private static void tierOnly(GameTestHelper helper) {
        helper.assertValueEqual(Requirement.of(recipe(Optional.of(HARVESTER_2), Optional.empty())),
                Component.translatable("fieldworks.requirement", electric()), "the requirement");
        helper.succeed();
    }

    private static void noTier(GameTestHelper helper) {
        helper.assertValueEqual(Requirement.of(recipe(Optional.empty(), Optional.empty())),
                Component.translatable("fieldworks.requirement", Component.translatable("fieldworks.requirement.a_harvester")), "the requirement");
        helper.succeed();
    }

    private static void singleFluid(GameTestHelper helper) {
        Fluid mud = DrillingMud.SOURCE.get();
        HarvestRecipe.FluidInput input = new HarvestRecipe.FluidInput(HolderSet.direct(mud.builtInRegistryHolder()), 10);
        helper.assertValueEqual(Requirement.of(recipe(Optional.of(HARVESTER_2), Optional.of(input))),
                Component.translatable("fieldworks.requirement.with_fluid", electric(), mud.getFluidType().getDescription()), "the requirement");
        helper.succeed();
    }

    // -- a pack that drops the recipe ------------------------------------------------------------

    private static void liftedByAPack(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        LiftedGatePack.offer(server, true);
        CompletableFuture<Void> on = GalleryCheck.select(server, LiftedGatePack.ID, true);
        var off = new AtomicReference<CompletableFuture<Void>>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(on.isDone(), LiftedGatePack.ID + " is not on yet"))
                .thenExecute(() -> {
                    try {
                        diamondIsHandMinable(helper);
                    } finally {
                        off.set(GalleryCheck.select(server, LiftedGatePack.ID, false));
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(off.get() != null && off.get().isDone(), LiftedGatePack.ID + " is not off yet"))
                .thenExecute(() -> LiftedGatePack.offer(server, false))
                .thenSucceed();
    }

    private static void diamondIsHandMinable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        OreBlock ore = FieldworksRegistries.ore(OreResource.DIAMOND).get();
        level.setBlockAndUpdate(pos, ore.defaultBlockState());

        helper.assertValueEqual(requirement(helper, OreResource.DIAMOND), Optional.<Component>empty(), "diamond's requirement without its recipe");
        breakWith(level, pos, GameType.SURVIVAL, Items.IRON_PICKAXE);

        helper.assertValueEqual(OreMining.remaining(level, pos, ore), OreResource.DIAMOND.corpus().defaultAmount() - 1, "the block's amount");
        helper.assertValueEqual(dropped(level, pos, OreResource.DIAMOND), 1, "diamonds dropped");
    }

    // -- fixtures --------------------------------------------------------------------------------

    private static TagKey<Fluid> overworld() {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "drilling_fluids/overworld"));
    }

    private static TagKey<Fluid> nether() {
        return TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "drilling_fluids/nether"));
    }

    private static Component electric() {
        return Component.translatable("fieldworks.requirement.the", Component.translatable("block.fieldworks.harvester_2"));
    }

    private static Optional<Component> requirement(GameTestHelper helper, OreResource resource) {
        return Requirement.of(helper.getLevel(), FieldworksRegistries.ore(resource).get().defaultBlockState());
    }

    private static HarvestRecipe recipe(Optional<Identifier> tier, Optional<HarvestRecipe.FluidInput> fluid) {
        return new HarvestRecipe(HolderSet.direct(Blocks.IRON_BLOCK.builtInRegistryHolder()),
                new HarvestRecipe.Result.Item(new ItemStackTemplate(Items.IRON_INGOT)), 20, tier, true, fluid);
    }

    private static void breakWith(ServerLevel level, BlockPos pos, GameType mode, Item tool) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(mode);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        player.gameMode.destroyBlock(pos);
    }

    private static int dropped(ServerLevel level, BlockPos pos, OreResource resource) {
        Item drop = BuiltInRegistries.ITEM.getValue(Identifier.parse(resource.drop()));
        return level.getEntities(EntityType.ITEM, new AABB(pos).inflate(2), e -> true).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(drop))
                .mapToInt(ItemStack::getCount)
                .sum();
    }
}
