// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** That no Harvest Recipe Fieldworks ships yields more than its cap allows over its source's Default recipe. */
class BonusCapTest {

    private static Path fixture(String name) {
        return Path.of("src/test/resources/bonus_cap", name);
    }

    @Test
    void aBonusSpendingADrillingFluidAboveTwiceItsDefaultIsReported() {
        List<String> violations = BonusCap.violations(fixture("fluid_above_cap"));

        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("raw_iron_thrice"), violations.get(0));
    }

    @Test
    void aBonusSpendingADrillingFluidAtTwiceItsDefaultPasses() {
        assertEquals(List.of(), BonusCap.violations(fixture("fluid_within_cap")));
    }

    @Test
    void aRecipeSpendingNoFluidThatYieldsMoreThanItsDefaultIsReported() {
        List<String> violations = BonusCap.violations(fixture("fluid_free_above_default"));

        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("raw_iron_twice_dry"), violations.get(0));
    }

    @Test
    void aBonusFromABlockTagIsHeldToTheCapOfEachOreInIt() {
        List<String> violations = BonusCap.violations(fixture("tag_above_cap"));

        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("raw_iron_by_tag"), violations.get(0));
    }

    @Test
    void aSourceWhoseDefaultAnotherRecipeReplacesIsNotJudged() {
        assertEquals(List.of(), BonusCap.violations(fixture("default_replaced_elsewhere")));
    }

    @Test
    void recipesThatAreNotBonusesAreLeftAlone() {
        // Replaces the Default, yields another item, has a source with no known Default, yields one with no fluid,
        // pumps crude in fixed mB or the well's own cycle, or is no Harvest Recipe at all.
        assertEquals(List.of(), BonusCap.violations(fixture("not_a_bonus")));
    }

    @Test
    void theHarvestRecipesFieldworksShipsStayWithinTheCap() {
        Path shipped = Path.of("src/main/resources");
        assertTrue(Files.isDirectory(shipped.resolve("data/fieldworks/recipe")), "the shipped recipes were not found");

        assertEquals(List.of(), BonusCap.violations(shipped));
    }
}
