/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.rng.sampling.distribution;

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.apache.commons.math3.distribution.PoissonDistribution;
import org.apache.commons.math3.stat.inference.ChiSquareTest;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.RandomAssert;
import org.apache.commons.rng.simple.RandomSource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

/**
 * Test for the {@link AliasMethodDiscreteSampler}.
 */
class AliasMethodDiscreteSamplerTest {
    @Test
    void testConstructorThrowsWithNullProbabilites() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> createSampler(null));
    }

    @Test
    void testConstructorThrowsWithZeroLengthProbabilites() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> createSampler(new double[0]));
    }

    @Test
    void testConstructorThrowsWithNegativeProbabilites() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> createSampler(new double[] {-1, 0.1, 0.2}));
    }

    @Test
    void testConstructorThrowsWithNaNProbabilites() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> createSampler(new double[] {0.1, Double.NaN, 0.2}));
    }

    @Test
    void testConstructorThrowsWithInfiniteProbabilites() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> createSampler(new double[] {0.1, Double.POSITIVE_INFINITY, 0.2}));
    }

    @Test
    void testConstructorThrowsWithInfiniteSumProbabilites() {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> createSampler(new double[] {Double.MAX_VALUE, Double.MAX_VALUE}));
    }

    @Test
    void testConstructorThrowsWithZeroSumProbabilites() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> createSampler(new double[4]));
    }

    @Test
    void testToString() {
        final SharedStateDiscreteSampler sampler = createSampler(new double[] {0.5, 0.5});
        Assertions.assertTrue(sampler.toString().toLowerCase().contains("alias method"));
    }

    /**
     * Creates the sampler without zero-padding enabled.
     *
     * @param probabilities the probabilities
     * @return the alias method discrete sampler
     */
    private static SharedStateDiscreteSampler createSampler(double[] probabilities) {
        final UniformRandomProvider rng = RandomSource.SPLIT_MIX_64.create();
        return AliasMethodDiscreteSampler.of(rng, probabilities, -1);
    }

    /**
     * Test sampling from a binomial distribution.
     */
    @Test
    void testBinomialSamples() {
        final int trials = 67;
        final double probabilityOfSuccess = 0.345;
        final BinomialDistribution dist = new BinomialDistribution(trials, probabilityOfSuccess);
        final double[] expected = new double[trials + 1];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = dist.probability(i);
        }
        checkSamples(expected);
    }

    /**
     * Test sampling from a Poisson distribution.
     */
    @Test
    void testPoissonSamples() {
        final double mean = 3.14;
        final PoissonDistribution dist = new PoissonDistribution(null, mean,
            PoissonDistribution.DEFAULT_EPSILON, PoissonDistribution.DEFAULT_MAX_ITERATIONS);
        final int maxN = dist.inverseCumulativeProbability(1 - 1e-6);
        double[] expected = new double[maxN];
        for (int i = 0; i < expected.length; i++) {
            expected[i] = dist.probability(i);
        }
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of probabilities (these sum to 1).
     */
    @Test
    void testNonUniformSamplesWithProbabilities() {
        final double[] expected = {0.1, 0.2, 0.3, 0.1, 0.3 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution using the factory constructor to zero pad
     * the input probabilities.
     */
    @Test
    void testNonUniformSamplesWithProbabilitiesWithDefaultFactoryConstructor() {
        final double[] expected = {0.1, 0.2, 0.3, 0.1, 0.3 };
        checkSamples(AliasMethodDiscreteSampler.of(RandomSource.SPLIT_MIX_64.create(), expected), expected);
    }

    /**
     * Test sampling from a non-uniform distribution of observations (i.e. the sum is not 1 as per
     * probabilities).
     */
    @Test
    void testNonUniformSamplesWithObservations() {
        final double[] expected = {1, 2, 3, 1, 3 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of probabilities (these sum to 1).
     * Extra zero-values are added to make the table size a power of 2.
     */
    @Test
    void testNonUniformSamplesWithProbabilitiesPaddedToPowerOf2() {
        final double[] expected = {0.1, 0, 0.2, 0.3, 0.1, 0.3, 0, 0 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of observations (i.e. the sum is not 1 as per
     * probabilities). Extra zero-values are added to make the table size a power of 2.
     */
    @Test
    void testNonUniformSamplesWithObservationsPaddedToPowerOf2() {
        final double[] expected = {1, 2, 3, 0, 1, 3, 0, 0 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of probabilities (these sum to 1).
     * Extra zero-values are added.
     */
    @Test
    void testNonUniformSamplesWithZeroProbabilities() {
        final double[] expected = {0.1, 0, 0.2, 0.3, 0.1, 0.3, 0 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution of observations (i.e. the sum is not 1 as per
     * probabilities). Extra zero-values are added.
     */
    @Test
    void testNonUniformSamplesWithZeroObservations() {
        final double[] expected = {1, 2, 3, 0, 1, 3, 0 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a uniform distribution. This is an edge case where there
     * are no probabilities less than the mean.
     */
    @Test
    void testUniformSamplesWithNoObservationLessThanTheMean() {
        final double[] expected = {2, 2, 2, 2, 2, 2 };
        checkSamples(expected);
    }

    /**
     * Test sampling from a non-uniform distribution which is zero-padded to a large size.
     */
    @Test
    void testLargeTableSize() {
        double[] expected = {0.1, 0.2, 0.3, 0.1, 0.3 };
        // Pad to a large table size not supported for fast sampling (anything > 2^11)
        expected = Arrays.copyOf(expected, 1 << 12);
        checkSamples(expected);
    }

    /**
     * Check the distribution of samples match the expected probabilities.
     *
     * @param expected the expected probabilities
     */
    private static void checkSamples(double[] probabilies) {
        checkSamples(createSampler(probabilies), probabilies);
    }

    /**
     * Check the distribution of samples match the expected probabilities.
     *
     * @param expected the expected probabilities
     */
    private static void checkSamples(SharedStateDiscreteSampler sampler, double[] probabilies) {
        final int numberOfSamples = 10000;
        final long[] samples = new long[probabilies.length];
        for (int i = 0; i < numberOfSamples; i++) {
            samples[sampler.sample()]++;
        }

        // Handle a test with some zero-probability observations by mapping them out
        int mapSize = 0;
        for (int i = 0; i < probabilies.length; i++) {
            if (probabilies[i] != 0) {
                mapSize++;
            }
        }

        double[] expected = new double[mapSize];
        long[] observed = new long[mapSize];
        for (int i = 0; i < probabilies.length; i++) {
            if (probabilies[i] != 0) {
                --mapSize;
                expected[mapSize] = probabilies[i];
                observed[mapSize] = samples[i];
            } else {
                Assertions.assertEquals(0, samples[i], "No samples expected from zero probability");
            }
        }

        final ChiSquareTest chiSquareTest = new ChiSquareTest();
        // Pass if we cannot reject null hypothesis that the distributions are the same.
        Assertions.assertFalse(chiSquareTest.chiSquareTest(expected, observed, 0.001));
    }

    /**
     * Test the SharedStateSampler implementation for the specialised power-of-2 table size.
     */
    @Test
    void testSharedStateSamplerWithPowerOf2TableSize() {
        testSharedStateSampler(new double[] {0.1, 0.2, 0.3, 0.4});
    }

    /**
     * Test the SharedStateSampler implementation for the generic non power-of-2 table size.
     */
    @Test
    void testSharedStateSamplerWithNonPowerOf2TableSize() {
        testSharedStateSampler(new double[] {0.1, 0.2, 0.3});
    }

    /**
     * Test the SharedStateSampler implementation.
     *
     * @param probabilities The probabilities
     */
    private static void testSharedStateSampler(double[] probabilities) {
        final UniformRandomProvider rng1 = RandomSource.SPLIT_MIX_64.create(0L);
        final UniformRandomProvider rng2 = RandomSource.SPLIT_MIX_64.create(0L);
        // Use negative alpha to disable padding
        final SharedStateDiscreteSampler sampler1 =
            AliasMethodDiscreteSampler.of(rng1, probabilities, -1);
        final SharedStateDiscreteSampler sampler2 = sampler1.withUniformRandomProvider(rng2);
        RandomAssert.assertProduceSameSequence(sampler1, sampler2);
    }
}
