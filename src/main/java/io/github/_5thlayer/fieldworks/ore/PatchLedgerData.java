// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.ore;

import io.github._5thlayer.fieldworks.Fieldworks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** The patches' ledger, saved with the world on the overworld's data storage. */
public final class PatchLedgerData extends SavedData {

    public static final SavedDataType<PatchLedgerData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Fieldworks.MOD_ID, "patch_ledger"),
            PatchLedgerData::new,
            PatchLedger.CODEC.xmap(PatchLedgerData::new, data -> data.ledger));

    private final PatchLedger ledger;

    public PatchLedgerData() {
        this(new PatchLedger());
    }

    private PatchLedgerData(PatchLedger ledger) {
        this.ledger = ledger;
    }

    public static PatchLedgerData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public void drew(PatchId patch) {
        ledger.drew(patch);
        setDirty();
    }

    public void blockGone(PatchId patch, int unitsLost, int blockCount) {
        ledger.blockGone(patch, unitsLost, blockCount);
        setDirty();
    }

    public long remaining(PatchId patch, long total) {
        return ledger.remaining(patch, total);
    }
}
