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

import org.apache.commons.math3.special.Gamma;
import org.apache.commons.rng.sampling.distribution.InternalUtils.FactorialLog;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Test for the {@link InternalUtils}.
 */
class InternalUtilsTest {
    /** The maximum value for n! that is representable as a long. */
    private static final int MAX_REPRESENTABLE = 20;

    @Test
    void testFactorial() {
        Assertions.assertEquals(1L, InternalUtils.factorial(0));
        long result = 1;
        for (int n = 1; n <= MAX_REPRESENTABLE; n++) {
            result *= n;
            Assertions.assertEquals(result, InternalUtils.factorial(n));
        }
    }

    @Test
    void testFactorialThrowsWhenNegative() {
        Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> InternalUtils.factorial(-1));
    }

    @Test
    void testFactorialThrowsWhenNotRepresentableAsLong() {
        Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> InternalUtils.factorial(MAX_REPRESENTABLE + 1));
    }

    @Test
    void testFactorialLog() {
        // Cache size allows some of the factorials to be cached and some
        // to be under the precomputed factorials.
        FactorialLog factorialLog = FactorialLog.create().withCache(MAX_REPRESENTABLE / 2);
        Assertions.assertEquals(0, factorialLog.value(0), 1e-10);
        for (int n = 1; n <= MAX_REPRESENTABLE + 5; n++) {
            // Use Commons math to compute logGamma(1 + n);
            double expected = Gamma.logGamma(1 + n);
            Assertions.assertEquals(expected, factorialLog.value(n), 1e-10);
        }
    }

    @Test
    void testFactorialLogCacheSizeAboveRepresentableFactorials() {
        final int limit = MAX_REPRESENTABLE + 5;
        FactorialLog factorialLog = FactorialLog.create().withCache(limit);
        for (int n = MAX_REPRESENTABLE; n <= limit; n++) {
            // Use Commons math to compute logGamma(1 + n);
            double expected = Gamma.logGamma(1 + n);
            Assertions.assertEquals(expected, factorialLog.value(n), 1e-10);
        }
    }

    @Test
    void testFactorialLogCacheExpansion() {
        // There is no way to determine if the cache values were reused but this test
        // exercises the method to ensure it does not error.
        final FactorialLog factorialLog = FactorialLog.create()
                                                      // Edge case where cache should not be copied (<2)
                                                      .withCache(1)
                                                      // Expand
                                                      .withCache(5)
                                                      // Expand more
                                                      .withCache(10)
                                                      // Contract
                                                      .withCache(5);
        for (int n = 1; n <= 5; n++) {
            // Use Commons math to compute logGamma(1 + n);
            double expected = Gamma.logGamma(1 + n);
            Assertions.assertEquals(expected, factorialLog.value(n), 1e-10);
        }
    }

    @Test
    void testLogFactorialThrowsWhenNegative() {
        Assertions.assertThrows(IndexOutOfBoundsException.class,
            () -> FactorialLog.create().value(-1));
    }

    @Test
    void testLogFactorialWithCacheThrowsWhenNegative() {
        Assertions.assertThrows(NegativeArraySizeException.class,
            () -> FactorialLog.create().withCache(-1));
    }
}
