// SPDX-FileCopyrightText: 2026 5thlayer
// SPDX-License-Identifier: MIT

package io.github._5thlayer.fieldworks.harvester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

/** A Harvester Tier's file (ADR 0005): the one Fieldworks ships, and the ones a load refuses, saying why. */
class HarvesterTierTest {

    @Test
    void theBurnerHarvesterShipsRankOneBurnOnlyAtSpeedOne() throws Exception {
        HarvesterTier tier = parse(shipped("harvester_1")).getOrThrow();

        assertEquals(1, tier.rank());
        assertEquals(1.0, tier.speed(), 1e-9);
        assertTrue(tier.power().fuel());
        assertEquals(0, tier.power().fePerTick());
        assertEquals(0, tier.feBuffer());
        assertEquals(1, tier.reach());
    }

    @Test
    void aTierThatNamesNoReachHasNone() {
        HarvesterTier tier = parse(json("""
                {"rank": 1, "speed": 1.0, "power": {"fuel": true}, "fe_buffer": 0}""")).getOrThrow();

        assertEquals(0, tier.reach());
    }

    @Test
    void aReachHasNoCapButCannotBeNegative() {
        assertEquals(500, parse(json("""
                {"rank": 1, "speed": 1.0, "power": {"fuel": true}, "reach": 500}""")).getOrThrow().reach());
        assertRefused(parse(json("""
                {"rank": 1, "speed": 1.0, "power": {"fuel": true}, "reach": -1}""")), "reach");
    }

    @Test
    void oneCoalBuysTheBurnerHarvesterEightOperationsAsItBuysAFurnaceEightSmelts() throws Exception {
        HarvesterTier tier = parse(shipped("harvester_1")).getOrThrow();

        int coal = 1600;
        assertEquals(8f, tier.power().operations(coal));
    }

    @Test
    void aTierThatNamesNoBurnPerOperationSpendsAFurnacesSmelt() {
        HarvesterTier tier = parse(json("""
                {"rank": 1, "speed": 1.0, "power": {"fuel": true}, "fe_buffer": 0}""")).getOrThrow();

        assertEquals(200, tier.power().burnPerOperation());
    }

    @Test
    void aBurnPerOperationBelowOneIsRefused() {
        DataResult<HarvesterTier> result = parse(json("""
                {"rank": 1, "speed": 1.0, "power": {"fuel": true, "burn_per_operation": 0}, "fe_buffer": 0}"""));

        assertRefused(result, "burn_per_operation");
    }

    @Test
    void theElectricHarvesterShipsRankTwoFeOnlyAtSpeedTwo() throws Exception {
        HarvesterTier tier = parse(shipped("harvester_2")).getOrThrow();

        assertEquals(2, tier.rank());
        assertEquals(2.0, tier.speed(), 1e-9);
        assertFalse(tier.power().fuel());
        assertEquals(40, tier.power().fePerTick());
        assertEquals(50_000, tier.feBuffer());
        assertEquals(2, tier.reach());
    }

    @Test
    void aTierThatTakesNoPowerIsRefused() {
        DataResult<HarvesterTier> result = parse(json("""
                {"rank": 1, "speed": 1.0, "power": {}, "fe_buffer": 0}"""));

        assertRefused(result, "no power");
    }

    @Test
    void aTierWithNoSpeedIsRefused() {
        DataResult<HarvesterTier> result = parse(json("""
                {"rank": 1, "speed": 0, "power": {"fuel": true}, "fe_buffer": 0}"""));

        assertRefused(result, "speed");
    }

    @Test
    void anFeTierWhoseBufferCannotHoldOneTicksCostIsRefused() {
        DataResult<HarvesterTier> result = parse(json("""
                {"rank": 2, "speed": 2.0, "power": {"fe_per_tick": 40}, "fe_buffer": 10}"""));

        assertRefused(result, "buffer");
    }

    @Test
    void anOperationTakesItsDurationOverTheSpeedAndNeverLessThanATick() {
        HarvesterTier doubled = new HarvesterTier(2, 2.0, new HarvesterTier.Power(false, 40, 1), 4000);
        HarvesterTier fast = new HarvesterTier(3, 100.0, new HarvesterTier.Power(true, 0, 1), 0);

        assertEquals(10, doubled.ticks(20));
        assertEquals(1, fast.ticks(20));
    }

    private static void assertRefused(DataResult<HarvesterTier> result, String why) {
        assertFalse(result.isSuccess(), "the file loaded: " + result);
        String message = result.error().orElseThrow().message();
        assertTrue(message.contains(why), "the refusal says \"" + message + "\", not why (" + why + ")");
    }

    private static DataResult<HarvesterTier> parse(JsonElement json) {
        return HarvesterTier.CODEC.parse(JsonOps.INSTANCE, json);
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static JsonElement shipped(String tier) throws Exception {
        String path = "/data/fieldworks/harvester_tier/" + tier + ".json";
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(HarvesterTierTest.class.getResourceAsStream(path), path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        }
    }
}
