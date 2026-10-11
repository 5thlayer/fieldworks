// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github._5thlayer.fieldworks.harvester.HarvesterTier.Payment;
import io.github._5thlayer.fieldworks.harvester.HarvesterTier.Power;
import org.junit.jupiter.api.Test;

/** What pays for a tick of work (#7): FE first, fuel as backup, and a started burn always finishes. */
class PowerTest {

    private static final Power BOTH = new Power(true, 40, 1);
    private static final Power FE_ONLY = new Power(false, 40, 1);
    private static final Power BURN_ONLY = new Power(true, 0, 1);

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
}
