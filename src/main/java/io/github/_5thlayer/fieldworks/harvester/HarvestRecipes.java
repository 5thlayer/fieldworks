// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import com.mojang.logging.LogUtils;
import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/**
 * The {@code fieldworks:harvesting} recipe type and what a Harvester reads of it. The type and its serializer
 * share a name, so a datapack file says {@code "type": "fieldworks:harvesting"}.
 *
 * <p>The client has no recipe manager and is sent only the recipe types asked for, so these are asked for at
 * every datapack sync: a recipe viewer lists them from what it is sent (#10).
 */
public final class HarvestRecipes {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Fieldworks.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Fieldworks.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<HarvestRecipe>> TYPE = TYPES.register("harvesting",
            () -> RecipeType.simple(Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "harvesting")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<HarvestRecipe>> SERIALIZER =
            SERIALIZERS.register("harvesting", HarvestRecipe::serializer);

    private static volatile Cache cache = new Cache(null, null, new Loaded(List.of(), Map.of()));

    private HarvestRecipes() {
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        NeoForge.EVENT_BUS.addListener(HarvestRecipes::sendToClients);
    }

    private static void sendToClients(OnDatapackSyncEvent event) {
        event.sendRecipes(TYPE.get());
    }

    /** The Harvest Recipe at this id, whatever it needs. */
    public static Optional<HarvestRecipe> find(ServerLevel level, Identifier id) {
        RecipeHolder<?> holder = level.getServer().getRecipeManager().recipeMap().byKey(ResourceKey.create(Registries.RECIPE, id));
        return holder != null && holder.value() instanceof HarvestRecipe recipe ? Optional.of(recipe) : Optional.empty();
    }

    /** Every Harvest Recipe on the server, as the choice reads them and by id, the id as the choice writes it. */
    record Loaded(List<OperationChoice.Recipe<HarvestRecipe.Result>> choices, Map<String, OperationChoice.Recipe<HarvestRecipe.Result>> byId) {
    }

    private record Cache(RecipeManager manager, Map<Identifier, HarvesterTier> tiers, Loaded loaded) {
    }

    /**
     * All Harvest Recipes, read from the server's recipe manager. A datapack reload builds a new manager and new
     * tier files, so what was derived from the old ones is read again; a Harvester asks every tick.
     */
    static Loaded loaded(ServerLevel level) {
        RecipeManager manager = level.getServer().getRecipeManager();
        Map<Identifier, HarvesterTier> tiers = HarvesterTiers.snapshot();
        Cache current = cache;
        if (current.manager == manager && current.tiers == tiers) {
            return current.loaded;
        }
        List<OperationChoice.Recipe<HarvestRecipe.Result>> choices = new ArrayList<>();
        Map<String, OperationChoice.Recipe<HarvestRecipe.Result>> byId = new HashMap<>();
        for (RecipeHolder<HarvestRecipe> holder : manager.recipeMap().byType(TYPE.get())) {
            Identifier id = holder.id().identifier();
            OperationChoice.Recipe<HarvestRecipe.Result> choice = holder.value().forChoice(id, tier ->
                    tiers.containsKey(tier) ? OptionalInt.of(tiers.get(tier).rank()) : OptionalInt.empty());
            if (choice.drillingFluid().filter(fluid -> fluid.fluids().isEmpty()).isPresent()) {
                // Loaded, but nothing can meet it: only fluids in #fieldworks:drilling_fluids serve (#1).
                LOGGER.warn("Harvest Recipe {} names no fluid in #fieldworks:drilling_fluids, so it can never run", id);
            }
            choices.add(choice);
            byId.put(choice.id(), choice);
        }
        Loaded loaded = new Loaded(List.copyOf(choices), Map.copyOf(byId));
        cache = new Cache(manager, tiers, loaded);
        return loaded;
    }
}
