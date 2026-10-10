// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.marker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** What each player's map is sent, and when it is sent again. */
class MarkerDeliveryTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    private static final PatchMarker IRON = new PatchMarker("iron", 200, 64, 300, 5000);
    private static final PatchMarker COAL = new PatchMarker("coal", -400, 70, 10, 900);
    private static final PatchMarker OIL = new PatchMarker(PatchMarker.CRUDE_OIL, 900, 66, 900, 640_000);

    private final UUID player = UUID.randomUUID();
    private final MarkerDelivery delivery = new MarkerDelivery();

    private static MarkerDelivery.Amounts amounts(long iron, long coal) {
        return (dimension, marker) -> switch (marker.resource()) {
            case "iron" -> iron;
            case "coal" -> coal;
            default -> marker.total();
        };
    }

    @Test
    void aPlayerIsSentEveryMarkerFoundInTheirDimension() {
        delivery.observe(player, OVERWORLD, List.of(IRON, COAL));

        assertEquals(List.of(new MarkerDelivery.Update(IRON, 5000), new MarkerDelivery.Update(COAL, 900)),
                delivery.take(player, amounts(5000, 900)));
    }

    @Test
    void aMarkerIsNotSentAgainWhileItsAmountIsUnchanged() {
        delivery.observe(player, OVERWORLD, List.of(IRON));
        delivery.take(player, amounts(5000, 900));

        delivery.refresh(OVERWORLD);

        assertTrue(delivery.take(player, amounts(5000, 900)).isEmpty());
    }

    @Test
    void aMinedPatchIsSentAgainWithWhatIsLeft() {
        delivery.observe(player, OVERWORLD, List.of(IRON));
        delivery.take(player, amounts(5000, 900));

        delivery.refresh(OVERWORLD);

        assertEquals(List.of(new MarkerDelivery.Update(IRON, 4200)), delivery.take(player, amounts(4200, 900)));
    }

    @Test
    void aPatchThatRanOutIsRemovedOnceAndNeverSentAfter() {
        delivery.observe(player, OVERWORLD, List.of(IRON));
        delivery.take(player, amounts(5000, 900));

        delivery.refresh(OVERWORLD);
        assertEquals(List.of(new MarkerDelivery.Update(IRON, 0)), delivery.take(player, amounts(0, 900)));

        delivery.refresh(OVERWORLD);
        assertTrue(delivery.take(player, amounts(0, 900)).isEmpty());
        delivery.found(OVERWORLD, IRON);
        assertTrue(delivery.take(player, amounts(0, 900)).isEmpty(), "an exhausted patch is never marked again");
    }

    @Test
    void aPatchThatRanOutBeforeThePlayerSawItIsNeverSent() {
        delivery.observe(player, OVERWORLD, List.of(IRON));

        assertTrue(delivery.take(player, amounts(0, 900)).isEmpty());
    }

    @Test
    void aNewlyFoundPatchReachesEveryPlayerInItsDimensionOnly() {
        UUID other = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        delivery.observe(player, OVERWORLD, List.of());
        delivery.observe(other, OVERWORLD, List.of());
        delivery.observe(away, NETHER, List.of());

        delivery.found(OVERWORLD, COAL);

        assertEquals(1, delivery.take(player, amounts(0, 900)).size());
        assertEquals(1, delivery.take(other, amounts(0, 900)).size());
        assertTrue(delivery.take(away, amounts(0, 900)).isEmpty());
    }

    @Test
    void anOilFieldIsNeverRemoved() {
        delivery.observe(player, OVERWORLD, List.of(OIL));
        delivery.take(player, amounts(0, 0));

        delivery.refresh(OVERWORLD);

        assertTrue(delivery.take(player, amounts(0, 0)).isEmpty());
    }

    @Test
    void enteringADimensionSendsItsMarkersAndLeavingForgetsTheOthers() {
        delivery.observe(player, OVERWORLD, List.of(IRON));
        delivery.take(player, amounts(5000, 900));

        delivery.observe(player, NETHER, List.of(COAL));

        assertEquals(List.of(new MarkerDelivery.Update(COAL, 900)), delivery.take(player, amounts(5000, 900)));
    }

    @Test
    void aLoginIsSentEverythingAgain() {
        delivery.observe(player, OVERWORLD, List.of(IRON));
        delivery.take(player, amounts(5000, 900));
        delivery.logout(player);

        delivery.observe(player, OVERWORLD, List.of(IRON));

        assertEquals(List.of(new MarkerDelivery.Update(IRON, 5000)), delivery.take(player, amounts(5000, 900)));
    }
}
