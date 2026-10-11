// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github._5thlayer.fieldworks.harvester.HarvesterTier.Payment;
import io.github._5thlayer.fieldworks.harvester.HarvesterTier.Power;
import org.junit.jupiter.api.Test;

/**
 * What pays for a tick of work (#7): FE first, fuel as backup, and a started burn always finishes; and the burn a tick
 * spends, its share of an operation's.
 */
class PowerTest {

    private static final Power BOTH = new Power(true, 40, 200);
    private static final Power FE_ONLY = new Power(false, 40, 200);
    private static final Power BURN_ONLY = new Power(true, 0, 200);

    @Test
    void aTierThatTakesBothPaysFromFeWhileItHasATicksWorth() {
        assertEquals(Payment.FE, BOTH.payment(0, 40));
        assertEquals(Payment.FE, BOTH.payment(0, 5_000));
    }

    @Test
    void aTierThatTakesBothLightsFuelWhenFeRunsShortOfATick() {
        assertEquals(Payment.LIGHT, BOTH.payment(0, 39));
        assertEquals(Payment.LIGHT, BOTH.payment(0, 0));
    }

    @Test
    void aStartedBurnFinishesBeforeFeIsSpentAgain() {
        assertEquals(Payment.BURN, BOTH.payment(1, 5_000));
        assertEquals(Payment.BURN, BOTH.payment(1_600, 5_000));
    }

    @Test
    void anFeOnlyTierNeverLightsFuel() {
        assertEquals(Payment.FE, FE_ONLY.payment(0, 40));
        assertEquals(Payment.NONE, FE_ONLY.payment(0, 39));
    }

    @Test
    void aBurnOnlyTierLightsFuelAndBurnsIt() {
        assertEquals(Payment.LIGHT, BURN_ONLY.payment(0, 0));
        assertEquals(Payment.BURN, BURN_ONLY.payment(3, 0));
    }

    @Test
    void aBurnStartedBeforeAReloadMadeTheTierFeOnlyStillFinishes() {
        assertEquals(Payment.BURN, FE_ONLY.payment(10, 5_000));
    }

    @Test
    void anOperationsTicksSpendItsBurnPerOperationWhateverItsLength() {
        for (int ticks : new int[] {1, 7, 20, 40, 199, 200, 201, 1_000}) {
            int spent = 0;
            for (int progress = 0; progress < ticks; progress++) {
                spent += BURN_ONLY.burnShare(progress, ticks);
            }
            assertEquals(200, spent, "the burn an operation of " + ticks + " ticks spent");
        }
    }

    @Test
    void aTwentyTickDrawSpendsTenATickAndAFortyTickBreakFive() {
        assertEquals(10, BURN_ONLY.burnShare(0, 20));
        assertEquals(5, BURN_ONLY.burnShare(39, 40));
    }
}
