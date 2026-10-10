// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** That no bonus Harvest Recipe Fieldworks ships yields more than the cap times its source's Default recipe. */
class BonusCapTest {

    private static Path fixture(String name) {
        return Path.of("src/test/resources/bonus_cap", name);
    }

    @Test
    void aBonusAboveTheCapIsReported() {
        List<String> violations = BonusCap.violations(fixture("above_cap"));

        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("raw_iron_thrice"), violations.get(0));
    }

    @Test
    void aBonusWithinTheCapPasses() {
        assertEquals(List.of(), BonusCap.violations(fixture("within_cap")));
    }

    @Test
    void aBonusFromABlockTagIsHeldToTheCapOfEachOreInIt() {
        List<String> violations = BonusCap.violations(fixture("tag_above_cap"));

        assertEquals(1, violations.size(), violations.toString());
        assertTrue(violations.get(0).contains("raw_iron_by_tag"), violations.get(0));
    }

    @Test
    void recipesThatAreNotBonusesAreLeftAlone() {
        // Replaces the Default, yields another item, has a source with no known Default, yields one,
        // or is no Harvest Recipe at all.
        assertEquals(List.of(), BonusCap.violations(fixture("not_a_bonus")));
    }

    @Test
    void theHarvestRecipesFieldworksShipsStayWithinTheCap() {
        Path shipped = Path.of("src/main/resources");
        assertTrue(Files.isDirectory(shipped.resolve("data/fieldworks/recipe")), "the shipped recipes were not found");

        assertEquals(List.of(), BonusCap.violations(shipped));
    }
}
