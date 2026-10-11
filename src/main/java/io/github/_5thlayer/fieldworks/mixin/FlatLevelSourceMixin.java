// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.mixin;

import io.github._5thlayer.fieldworks.FieldworksTags;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A superflat world places only the structure sets its preset lists, villages by default, so the patches and oil
 * fields join every preset's list through {@code #fieldworks:flat_worlds}. A world with no list already places them.
 */
@Mixin(FlatLevelSource.class)
public abstract class FlatLevelSourceMixin {

    @Inject(method = "createState", at = @At("HEAD"), cancellable = true)
    private void fieldworks$placeOnFlatWorlds(HolderLookup<StructureSet> structureSets, RandomState randomState,
            long levelSeed, CallbackInfoReturnable<ChunkGeneratorStructureState> cir) {
        FlatLevelSource self = (FlatLevelSource) (Object) this;
        self.settings().structureOverrides().ifPresent(overrides -> {
            Stream<Holder<StructureSet>> ours = structureSets.get(FieldworksTags.FLAT_WORLD_STRUCTURE_SETS)
                    .map(HolderSet::stream).orElseGet(Stream::empty);
            cir.setReturnValue(ChunkGeneratorStructureState.createForFlat(randomState, levelSeed,
                    ((ChunkGenerator) self).getBiomeSource(), Stream.concat(overrides.stream(), ours).distinct()));
        });
    }
}
