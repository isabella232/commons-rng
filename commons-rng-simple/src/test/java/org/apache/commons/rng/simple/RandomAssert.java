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

package org.apache.commons.rng.simple;

import org.junit.jupiter.api.Assertions;

import org.apache.commons.rng.UniformRandomProvider;

/**
 * Utility class for testing random generators.
 */
public final class RandomAssert {
    /**
     * Class contains only static methods.
     */
    private RandomAssert() {}

    /**
     * Exercise all methods from the UniformRandomProvider interface, and
     * ensure that the two generators produce the same sequence.
     *
     * @param rng1 RNG.
     * @param rng2 RNG.
     */
    public static void assertProduceSameSequence(UniformRandomProvider rng1,
                                                 UniformRandomProvider rng2) {
        for (int i = 0; i < 54; i++) {
            Assertions.assertEquals(rng1.nextBoolean(), rng2.nextBoolean());
        }
        for (int i = 0; i < 23; i++) {
            Assertions.assertEquals(rng1.nextInt(), rng2.nextInt());
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                final int max = 107 * i + 374 * j + 11;
                Assertions.assertEquals(rng1.nextInt(max), rng2.nextInt(max));
            }
        }
        for (int i = 0; i < 23; i++) {
            Assertions.assertEquals(rng1.nextLong(), rng2.nextLong());
        }
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 5; j++) {
                final long max = (Long.MAX_VALUE << 2) + 107 * i + 374 * j + 11;
                Assertions.assertEquals(rng1.nextLong(max), rng2.nextLong(max));
            }
        }
        for (int i = 0; i < 103; i++) {
            Assertions.assertEquals(rng1.nextFloat(), rng2.nextFloat());
        }
        for (int i = 0; i < 79; i++) {
            Assertions.assertEquals(rng1.nextDouble(), rng2.nextDouble());
        }

        final int size = 345;
        final byte[] a1 = new byte[size];
        final byte[] a2 = new byte[size];

        for (int i = 0; i < 3; i++) {
            rng1.nextBytes(a1);
            rng2.nextBytes(a2);
            Assertions.assertArrayEquals(a1, a2);
        }

        for (int i = 0; i < 5; i++) {
            final int offset = 200 + i;
            final int n = 23 + i;
            rng1.nextBytes(a1, offset, n);
            rng2.nextBytes(a2, offset, n);
            Assertions.assertArrayEquals(a1, a2);
        }
    }
}
