// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.gametest;

import static io.github._5thlayer.fieldworks.gametest.HarvesterTests.ORIGIN;

import java.util.List;
import java.util.Optional;

import io.github._5thlayer.fieldworks.Fieldworks;
import io.github._5thlayer.fieldworks.FieldworksRegistries;
import io.github._5thlayer.fieldworks.FieldworksTags;
import io.github._5thlayer.fieldworks.config.FieldworksConfig;
import io.github._5thlayer.fieldworks.fluid.CrudeOil;
import io.github._5thlayer.fieldworks.fluid.DrillingMud;
import io.github._5thlayer.fieldworks.fluid.MagmaSlurry;
import io.github._5thlayer.fieldworks.harvester.HarvesterBlockEntity;
import io.github._5thlayer.fieldworks.harvester.OperationChoice.IdleReason;
import io.github._5thlayer.fieldworks.harvester.TierBlock;
import io.github._5thlayer.fieldworks.oil.OilWellBlockEntity;
import io.github._5thlayer.fieldworks.ore.OreBlock;
import io.github._5thlayer.fieldworks.ore.OreMining;
import io.github._5thlayer.fieldworks.ore.OreResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandlerUtil;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * What a Patch resource asks of the Harvester and the world around it (ADR 0007, fieldworks#35): the Burner works every
 * common resource with no fluid and is refused on uranium, diamond, netherite and an oil well; the Electric works
 * them, the first three only with the Drilling Fluid of their dimension; a block takes the pickaxe tier of its vanilla
 * counterpart; and magma slurry is a fluid of its own that is not lava. The Patch discs themselves are in
 * {@link PatchDiscTests}. Every test here is named {@code patches_...}, so one glob runs them all.
 */
final class PatchGateTests {

    private static final BlockPos CENTRE = ORIGIN.below();
    private static final BlockPos CHEST = ORIGIN.relative(Direction.NORTH, 2);
    
    /** What a gated recipe takes: a source, the recipe's file, the mB of fluid it spends a draw and its ticks at speed x1. */
    private record Gate(String name, OreResource ore, String recipe, int amount, int ticks) {

        Identifier id() {
            return Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, recipe);
        }
    }

    private static final List<Gate> ORE_GATES = List.of(
            new Gate("uranium", OreResource.URANIUM, "uranium_from_ore", 10, 20),
            new Gate("diamond", OreResource.DIAMOND, "diamond_from_ore", 10, 60),
            new Gate("netherite", OreResource.NETHERITE, "netherite_scrap_from_ore", 25, 100));

    private PatchGateTests() {
    }

    static void register(FieldworksGameTests.Registrar tests) {
        for (Gate gate : ORE_GATES) {
            tests.test("patches_the_burner_harvester_is_refused_on_" + gate.name(), 20, helper -> burnerRefused(helper, gate, false));
            tests.test("patches_the_electric_harvester_draws_no_" + gate.name() + "_with_no_fluid_or_the_other_dimensions", 20,
                    helper -> electricNeedsItsFluid(helper, gate));
            tests.test("patches_the_electric_harvester_draws_" + gate.name() + "_and_spends_its_drilling_fluid_per_draw", 20,
                    helper -> electricSpendsItsFluid(helper, gate));
        }
        tests.test("patches_the_burner_harvester_is_refused_on_an_oil_well", 20,
                helper -> burnerRefused(helper, new Gate("oil_well", null, "crude_from_oil_well", 0, 20), true));
        tests.test("patches_the_electric_harvester_pumps_an_oil_well_with_no_drilling_fluid", 20, PatchGateTests::electricPumpsAWell);
        for (OreResource resource : OreResource.values()) {
            if (ORE_GATES.stream().noneMatch(gate -> gate.ore() == resource)) {
                tests.test("patches_" + resource.key() + "_works_on_the_burner_harvester_with_no_fluid", 20,
                        helper -> burnerWorksCommon(helper, resource));
            }
        }
        tests.test("patches_each_new_ore_block_is_in_its_mining_tool_tier_and_c_tags", 1, PatchGateTests::tags);
        tests.test("patches_every_resource_has_a_generation_switch", 1, PatchGateTests::switches);
        tests.test("patches_the_pickaxe_tiers_match_vanillas", 20, PatchGateTests::pickaxeTiers);
        tests.test("patches_the_drilling_fluid_tags_hold_the_right_fluids_per_dimension", 1, PatchGateTests::fluidTags);
        tests.test("patches_the_magma_slurry_recipe_crafts_a_bucket_from_a_lava_bucket_and_soul_soil", 1, PatchGateTests::slurryRecipe);
        tests.test("patches_magma_slurry_is_a_fluid_of_its_own_that_is_not_lava_and_is_no_fuel", 1, PatchGateTests::slurryIsNotLava);
        tests.test("patches_magma_slurry_beside_crude_sets_no_tar", 60, PatchGateTests::slurrySetsNoTar);
        tests.test("patches_the_magma_slurry_bucket_exposes_the_item_fluid_capability", 1, PatchGateTests::slurryCapability);
    }

    // -- the Harvester ---------------------------------------------------------------------------

    /**
     * A Burner Harvester over the source holds the gated recipe and idles for its tier, drawing nothing and pumping
     * nothing; with nothing held it has nothing to work either, since the recipe replaces the Default.
     */
    private static void burnerRefused(GameTestHelper helper, Gate gate, boolean well) {
        BlockPos chest = chest(helper);
        int initial = 0;
        if (well) {
            helper.setBlock(CENTRE, FieldworksRegistries.OIL_WELL.get());
            helper.getBlockEntity(CENTRE, OilWellBlockEntity.class).start(300_000);
        } else {
            initial = HarvesterTests.ore(helper, gate.ore(), CENTRE, 0);
        }
        HarvesterBlockEntity machine = burner(helper);

        machine.setHeldRecipe(gate.id());
        HarvesterTests.tick(helper, machine, 200);
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.TIER_TOO_LOW), "the idle reason with the recipe held");
        helper.assertValueEqual(amountOf(helper, FluidResource.of(CrudeOil.SOURCE.get())), 0, "crude pumped");
        machine.setHeldRecipe(null);
        HarvesterTests.tick(helper, machine, 200);
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.RECIPE_NEEDED), "the idle reason with nothing held");
        helper.assertValueEqual(amountOf(helper, FluidResource.of(CrudeOil.SOURCE.get())), 0, "crude pumped with nothing held");
        if (!well) {
            helper.assertValueEqual(HarvesterTests.remaining(helper, CENTRE), initial, gate.name() + " ore left after the Burner");
            helper.assertValueEqual(HarvesterTests.count(helper, chest, drop(gate.ore())), 0, "what the Burner took from " + gate.name());
        }
        helper.succeed();
    }

    /** An Electric Harvester holding the recipe draws nothing without its fluid, nor with the other dimension's. */
    private static void electricNeedsItsFluid(GameTestHelper helper, Gate gate) {
        BlockPos chest = chest(helper);
        HarvesterTests.ore(helper, gate.ore(), CENTRE, 0);
        HarvesterBlockEntity machine = electric(helper);
        Fluid other = PatchDiscTests.nether(gate.ore()) ? DrillingMud.SOURCE.get() : MagmaSlurry.SOURCE.get();
        machine.setHeldRecipe(gate.id());

        HarvesterTests.tick(helper, machine, 400);
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NO_FLUID), "the idle reason with no fluid");
        helper.assertValueEqual(HarvesterTests.count(helper, chest, drop(gate.ore())), 0, gate.name() + " drawn with no fluid");

        fill(helper, other, 1_000);
        HarvesterTests.tick(helper, machine, 400);
        helper.assertValueEqual(machine.idleReason(), Optional.of(IdleReason.NO_FLUID), "the idle reason with the other dimension's fluid");
        helper.assertValueEqual(HarvesterTests.count(helper, chest, drop(gate.ore())), 0, gate.name() + " drawn on the other dimension's fluid");
        helper.assertValueEqual(amountOf(helper, FluidResource.of(other)), 1_000, "the other dimension's fluid taken");
        helper.succeed();
    }

    /** With its own dimension's fluid in the tank, the recipe draws, and spends the recipe's amount for each draw. */
    private static void electricSpendsItsFluid(GameTestHelper helper, Gate gate) {
        BlockPos chest = chest(helper);
        HarvesterTests.ore(helper, gate.ore(), CENTRE, 0);
        HarvesterBlockEntity machine = electric(helper);
        Fluid own = PatchDiscTests.nether(gate.ore()) ? MagmaSlurry.SOURCE.get() : DrillingMud.SOURCE.get();
        machine.setHeldRecipe(gate.id());

        fill(helper, own, 1_000);
        int before = amountOf(helper, FluidResource.of(own));
        int initial = HarvesterTests.remaining(helper, CENTRE);
        HarvesterTests.tick(helper, machine, 2 * gate.ticks() + 10);
        int draws = initial - HarvesterTests.remaining(helper, CENTRE);
        helper.assertTrue(draws >= 1, gate.name() + " was not drawn with its own fluid");
        helper.assertTrue(HarvesterTests.count(helper, chest, drop(gate.ore())) >= 1, gate.name() + " reached no chest");
        helper.assertValueEqual(before - amountOf(helper, FluidResource.of(own)), draws * gate.amount(),
                "fluid spent for " + draws + " draws of " + gate.amount() + " mB");
        helper.succeed();
    }

    /** An Electric Harvester pumps a well that has not decayed, with no fluid and nothing held but the recipe. */
    private static void electricPumpsAWell(GameTestHelper helper) {
        helper.setBlock(CENTRE, FieldworksRegistries.OIL_WELL.get());
        helper.getBlockEntity(CENTRE, OilWellBlockEntity.class).start(300_000);
        HarvesterBlockEntity machine = electric(helper);
        machine.setHeldRecipe(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "crude_from_oil_well"));

        HarvesterTests.tick(helper, machine, 100);

        helper.assertTrue(amountOf(helper, FluidResource.of(CrudeOil.SOURCE.get())) > 0, "no crude pumped");
        helper.assertValueEqual(machine.idleReason(), Optional.empty(), "the idle reason");
        helper.succeed();
    }

    /** A common resource is drawn by the Burner Harvester fed coal, with no fluid and nothing held. */
    private static void burnerWorksCommon(GameTestHelper helper, OreResource resource) {
        BlockPos chest = chest(helper);
        int initial = HarvesterTests.ore(helper, resource, CENTRE, 0);
        HarvesterBlockEntity machine = burner(helper);

        HarvesterTests.tick(helper, machine, 100);

        int drawn = HarvesterTests.count(helper, chest, drop(resource));
        helper.assertTrue(drawn >= 1, resource.key() + " was not drawn: " + machine.idleReason());
        helper.assertTrue(initial - HarvesterTests.remaining(helper, CENTRE) >= drawn, resource.key() + " ore left, for " + drawn + " items drawn");
        helper.succeed();
    }

    // -- data --------------------------------------------------------------------------------------

    /**
     * Each new ore block is mineable with a pickaxe, in the tool tier its vanilla counterpart needs, and in
     * {@code c:ores} and its own {@code c:ores/<resource>}. Quartz needs no tier and glowstone no tool.
     */
    private static void tags(GameTestHelper helper) {
        TagKey<Block> ores = blockTag("c", "ores");
        for (OreResource resource : List.of(OreResource.REDSTONE, OreResource.GOLD, OreResource.DIAMOND,
                OreResource.QUARTZ, OreResource.GLOWSTONE, OreResource.NETHERITE)) {
            Block block = FieldworksRegistries.ore(resource).get();
            var state = block.defaultBlockState();
            helper.assertTrue(state.is(BlockTags.MINEABLE_WITH_PICKAXE), resource.blockName() + " is not mineable with a pickaxe");
            helper.assertTrue(state.is(ores), resource.blockName() + " is not in c:ores");
            helper.assertTrue(state.is(blockTag("c", "ores/" + resource.key())), resource.blockName() + " is not in c:ores/" + resource.key());
            helper.assertValueEqual(state.is(BlockTags.NEEDS_STONE_TOOL), false, resource.blockName() + " in needs_stone_tool");
            boolean iron = resource == OreResource.REDSTONE || resource == OreResource.GOLD || resource == OreResource.DIAMOND;
            helper.assertValueEqual(state.is(BlockTags.NEEDS_IRON_TOOL), iron, resource.blockName() + " in needs_iron_tool");
            helper.assertValueEqual(state.is(BlockTags.NEEDS_DIAMOND_TOOL), resource == OreResource.NETHERITE,
                    resource.blockName() + " in needs_diamond_tool");
            helper.assertValueEqual(state.requiresCorrectToolForDrops(), resource != OreResource.GLOWSTONE,
                    "whether " + resource.blockName() + " needs the right tool for drops");
        }
        helper.succeed();
    }

    private static void switches(GameTestHelper helper) {
        for (OreResource resource : OreResource.values()) {
            helper.assertTrue(FieldworksConfig.generateSwitch(resource) != null, resource.key() + " has no Generation switch");
            helper.assertTrue(FieldworksConfig.generates(resource), resource.key() + "'s Generation switch is off by default");
        }
        helper.assertValueEqual(OreResource.values().length, 12, "the resources");
        helper.succeed();
    }

    /**
     * Vanilla's tiers where a hand draw exists: iron for redstone and gold, any pickaxe for quartz, nothing for glowstone.
     * Diamond and netherite are Harvester-only, so no pickaxe tier applies ({@link HandGateTests}).
     */
    private static void pickaxeTiers(GameTestHelper helper) {
        int slot = 0;
        for (OreResource resource : List.of(OreResource.REDSTONE, OreResource.GOLD)) {
            helper.assertValueEqual(breakOnce(helper, resource, Items.STONE_PICKAXE, slot++), 0, "a stone pickaxe on " + resource.key());
            helper.assertValueEqual(breakOnce(helper, resource, Items.IRON_PICKAXE, slot++), 1, "an iron pickaxe on " + resource.key());
        }
        helper.assertValueEqual(breakOnce(helper, OreResource.QUARTZ, Items.WOODEN_PICKAXE, slot++), 1, "a wooden pickaxe on quartz");
        helper.assertValueEqual(breakOnce(helper, OreResource.QUARTZ, Items.AIR, slot++), 0, "a bare hand on quartz");
        helper.assertValueEqual(breakOnce(helper, OreResource.GLOWSTONE, Items.AIR, slot++), 1, "a bare hand on glowstone");
        helper.succeed();
    }

    /** Overworld mud in the Overworld tag, slurry in the Nether's, both in the combined tag and in neither's other. */
    private static void fluidTags(GameTestHelper helper) {
        TagKey<Fluid> overworld = FieldworksTags.DRILLING_FLUIDS_OVERWORLD;
        TagKey<Fluid> nether = FieldworksTags.DRILLING_FLUIDS_NETHER;
        for (Fluid mud : List.of(DrillingMud.SOURCE.get(), DrillingMud.FLOWING.get())) {
            helper.assertTrue(mud.defaultFluidState().is(overworld), mud + " is not in #fieldworks:drilling_fluids/overworld");
            helper.assertFalse(mud.defaultFluidState().is(nether), mud + " is in #fieldworks:drilling_fluids/nether");
            helper.assertTrue(mud.defaultFluidState().is(FieldworksTags.DRILLING_FLUIDS), mud + " is not in #fieldworks:drilling_fluids");
        }
        for (Fluid slurry : List.of(MagmaSlurry.SOURCE.get(), MagmaSlurry.FLOWING.get())) {
            helper.assertTrue(slurry.defaultFluidState().is(nether), slurry + " is not in #fieldworks:drilling_fluids/nether");
            helper.assertFalse(slurry.defaultFluidState().is(overworld), slurry + " is in #fieldworks:drilling_fluids/overworld");
            helper.assertTrue(slurry.defaultFluidState().is(FieldworksTags.DRILLING_FLUIDS), slurry + " is not in #fieldworks:drilling_fluids");
        }
        helper.assertFalse(CrudeOil.SOURCE.get().defaultFluidState().is(FieldworksTags.DRILLING_FLUIDS), "crude oil is a Drilling Fluid");
        helper.succeed();
    }

    private static void slurryRecipe(GameTestHelper helper) {
        for (List<ItemStack> grid : List.of(
                List.of(new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.SOUL_SOIL)),
                List.of(new ItemStack(Items.SOUL_SOIL), new ItemStack(Items.LAVA_BUCKET)))) {
            CraftingInput input = CraftingInput.of(2, 1, grid);
            Optional<RecipeHolder<CraftingRecipe>> recipe = helper.getLevel().getServer().getRecipeManager()
                    .getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
            helper.assertTrue(recipe.isPresent(), "no crafting recipe takes a lava bucket and soul soil");
            ItemStack result = recipe.get().value().assemble(input);
            helper.assertTrue(result.is(MagmaSlurry.BUCKET.get()) && result.getCount() == 1, "they craft " + result);
        }
        CraftingInput soulSand = CraftingInput.of(2, 1, List.of(new ItemStack(Items.LAVA_BUCKET), new ItemStack(Items.SOUL_SAND)));
        helper.assertTrue(helper.getLevel().getServer().getRecipeManager()
                        .getRecipeFor(RecipeType.CRAFTING, soulSand, helper.getLevel()).isEmpty(),
                "soul sand crafts something with a lava bucket");
        helper.succeed();
    }

    /** Not in {@code #minecraft:lava}, no fuel, a still and a flowing form and a liquid block of its own, and no bottles. */
    private static void slurryIsNotLava(GameTestHelper helper) {
        Fluid source = MagmaSlurry.SOURCE.get();
        helper.assertFalse(source.defaultFluidState().is(FluidTags.LAVA), "magma slurry is in #minecraft:lava");
        helper.assertFalse(MagmaSlurry.FLOWING.get().defaultFluidState().is(FluidTags.LAVA), "flowing magma slurry is in #minecraft:lava");
        helper.assertTrue(source.defaultFluidState().isSource() && !MagmaSlurry.FLOWING.get().defaultFluidState().isSource(),
                "the still and flowing forms are not told apart");
        helper.assertFalse(helper.getLevel().fuelValues().isFuel(new ItemStack(MagmaSlurry.BUCKET.get())), "the slurry bucket is furnace fuel");
        helper.assertTrue(helper.getLevel().fuelValues().isFuel(new ItemStack(Items.LAVA_BUCKET)), "a lava bucket is no fuel, so the check proves nothing");
        helper.assertTrue(MagmaSlurry.BLOCK.get().defaultBlockState().getFluidState().getType() == source, "the block holds another fluid");
        helper.assertTrue(BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "magma_slurry_bottle")).isEmpty(),
                "magma slurry has a bottle");
        helper.succeed();
    }

    /** A crude source touching a slurry source stays crude: only lava sets crude into tar. */
    private static void slurrySetsNoTar(GameTestHelper helper) {
        BlockPos crude = new BlockPos(2, 2, 2);
        helper.setBlock(crude, CrudeOil.BLOCK.get());
        helper.setBlock(crude.east(), MagmaSlurry.BLOCK.get());
        helper.setBlock(crude.below(), Blocks.STONE);
        helper.startSequence().thenIdle(40).thenExecute(() -> {
            helper.assertBlockPresent(CrudeOil.BLOCK.get(), crude);
            helper.assertBlockPresent(MagmaSlurry.BLOCK.get(), crude.east());
        }).thenSucceed();
    }

    private static void slurryCapability(GameTestHelper helper) {
        ItemStack bucket = new ItemStack(MagmaSlurry.BUCKET.get());
        ResourceHandler<FluidResource> fluid = bucket.getCapability(Capabilities.Fluid.ITEM, net.neoforged.neoforge.transfer.access.ItemAccess.forStack(bucket));
        helper.assertTrue(fluid != null, "the slurry bucket has no fluid capability");
        helper.assertValueEqual(fluid.getResource(0), FluidResource.of(MagmaSlurry.SOURCE.get()), "what the bucket holds");
        helper.assertValueEqual(fluid.getAmountAsInt(0), 1_000, "how much the bucket holds");
        helper.succeed();
    }

    // -- fixtures ----------------------------------------------------------------------------------

    /** Breaks a block of {@code resource} once with {@code tool}, away from any other, and counts the units it paid. */
    private static int breakOnce(GameTestHelper helper, OreResource resource, Item tool, int slot) {
        ServerLevel level = helper.getLevel();
        BlockPos spaced = helper.absolutePos(new BlockPos(1 + (slot % 6), 1, 1 + (slot / 6) * 4));
        OreBlock ore = FieldworksRegistries.ore(resource).get();
        level.setBlockAndUpdate(spaced, ore.defaultBlockState());
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(tool));
        int before = OreMining.remaining(level, spaced, ore);
        player.gameMode.destroyBlock(spaced);
        int units = before - OreMining.remaining(level, spaced, ore);
        Item drop = drop(resource);
        int paid = level.getEntities(EntityType.ITEM, new AABB(spaced).inflate(0.6), e -> true).stream()
                .map(ItemEntity::getItem).filter(stack -> stack.is(drop)).mapToInt(ItemStack::getCount).sum();
        level.getEntities(EntityType.ITEM, new AABB(spaced).inflate(3), e -> true).forEach(ItemEntity::discard);
        helper.assertValueEqual(paid, units, "units paid against units drawn from " + resource.key() + " by " + tool);
        return units;
    }

    static Item drop(OreResource resource) {
        return BuiltInRegistries.ITEM.getValue(Identifier.parse(resource.drop()));
    }

    private static TagKey<Block> blockTag(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(namespace, path));
    }

    /** A chest at the Harvester's output face. */
    private static BlockPos chest(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        return CHEST;
    }

    /** A Burner Harvester facing north over the layer, fed coal through its item capability. */
    private static HarvesterBlockEntity burner(GameTestHelper helper) {
        helper.setBlock(CENTRE, helper.getBlockState(CENTRE).isAir() ? Blocks.STONE.defaultBlockState() : helper.getBlockState(CENTRE));
        HarvesterTests.place(helper, Direction.NORTH);
        ResourceHandler<ItemResource> items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(items != null, "the Harvester has no item capability");
        try (Transaction feeding = Transaction.openRoot()) {
            items.insert(ItemResource.of(Items.COAL), 64, feeding);
            feeding.commit();
        }
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    /** An Electric Harvester facing north over the layer, charged full of FE through its energy capability. */
    static HarvesterBlockEntity electric(GameTestHelper helper) {
        HarvesterTests.place(helper, Direction.NORTH, TierBlock.HARVESTER_2);
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(ORIGIN.west()), Direction.WEST);
        helper.assertTrue(energy != null, "the Electric Harvester has no energy capability");
        EnergyHandlerUtil.move(new SimpleEnergyHandler(1_000_000, 1_000_000, 1_000_000, 1_000_000), energy, 50_000, null);
        return helper.getBlockEntity(ORIGIN, HarvesterBlockEntity.class);
    }

    private static void fill(GameTestHelper helper, Fluid fluid, int amount) {
        try (Transaction filling = Transaction.openRoot()) {
            helper.assertValueEqual(fluids(helper).insert(FluidResource.of(fluid), amount, filling), amount, "fluid put into the tank");
            filling.commit();
        }
    }

    private static ResourceHandler<FluidResource> fluids(GameTestHelper helper) {
        ResourceHandler<FluidResource> fluids = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(ORIGIN.east()), Direction.EAST);
        helper.assertTrue(fluids != null, "the Harvester has no fluid capability");
        return fluids;
    }

    private static int amountOf(GameTestHelper helper, FluidResource fluid) {
        ResourceHandler<FluidResource> fluids = fluids(helper);
        int total = 0;
        for (int index = 0; index < fluids.size(); index++) {
            if (fluids.getResource(index).equals(fluid)) {
                total += fluids.getAmountAsInt(index);
            }
        }
        return total;
    }
}
