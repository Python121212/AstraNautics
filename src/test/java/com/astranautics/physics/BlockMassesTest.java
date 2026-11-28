package com.astranautics.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every block has a weight, and the weight follows the material: gold sinks, wool barely
 * registers, and a block nobody wrote a rule for still has mass.
 */
class BlockMassesTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void goldIsHeavierThanIronWhichIsHeavierThanStone() {
        double gold = BlockMasses.densityOfName("gold_block", 3.0F);
        double netherite = BlockMasses.densityOfName("netherite_block", 50.0F);
        double iron = BlockMasses.densityOfName("iron_block", 5.0F);
        double stone = BlockMasses.densityOfName("stone", 1.5F);

        assertEquals(12.0D, gold, EPSILON);
        assertEquals(7.0D, iron, EPSILON);
        assertEquals(2.2D, stone, EPSILON);
        assertTrue(gold > netherite, "gold must out-sink netherite");
        assertTrue(netherite > iron, "netherite must out-sink iron");
        assertTrue(iron > stone, "iron must out-sink stone");
    }

    @Test
    void woolIsLighterThanWoodAndWoodLighterThanStone() {
        double wool = BlockMasses.densityOfName("white_wool", 0.8F);
        double planks = BlockMasses.densityOfName("oak_planks", 2.0F);
        double stone = BlockMasses.densityOfName("stone", 1.5F);

        assertEquals(0.25D, wool, EPSILON);
        assertEquals(0.6D, planks, EPSILON);
        assertTrue(wool < planks);
        assertTrue(planks < stone);
    }

    @Test
    void aBlockWithNoRuleStillWeighsSomething() {
        double unknown = BlockMasses.densityOfName("some_modded_contraption", 1.0F);

        assertTrue(unknown > 0.0D, "unknown blocks must not be weightless");
        assertTrue(unknown >= 0.2D && unknown <= 6.0D, "the fallback stays in a sane range");
    }

    @Test
    void harderUnknownBlocksAreHeavierUntilTheFallbackClamps() {
        double soft = BlockMasses.densityOfName("odd_soft_block", 0.2F);
        double hard = BlockMasses.densityOfName("odd_hard_block", 4.0F);
        double absurd = BlockMasses.densityOfName("odd_absurd_block", 100.0F);

        assertTrue(hard > soft);
        assertEquals(6.0D, absurd, EPSILON, "the fallback must not grow without bound");
    }

    @Test
    void unbreakableBlocksAreHeavy() {
        assertEquals(9.0D, BlockMasses.densityOfName("bedrock", -1.0F), EPSILON);
    }

    @Test
    void theScaleMultipliesEveryBlockTheSameWay() {
        assertEquals(2.2D * 0.5D, BlockMasses.massOfDensity(2.2D, 0.5D), EPSILON);
        assertEquals(2.2D * 3.0D, BlockMasses.massOfDensity(2.2D, 3.0D), EPSILON);
        assertTrue(BlockMasses.massOfDensity(0.0D, 1.0D) > 0.0D, "mass never reaches zero");
    }
}
