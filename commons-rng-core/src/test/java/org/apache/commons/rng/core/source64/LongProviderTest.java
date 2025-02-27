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
package org.apache.commons.rng.core.source64;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The tests the caching of calls to {@link LongProvider#nextLong()} are used as
 * the source for {@link LongProvider#nextInt()} and
 * {@link LongProvider#nextBoolean()}.
 */
class LongProviderTest {
    /**
     * A simple class to return a fixed value as the source for
     * {@link LongProvider#next()}.
     */
    static final class FixedLongProvider extends LongProvider {
        /** The value. */
        private long value;

        /**
         * @param value the value
         */
        FixedLongProvider(long value) {
            this.value = value;
        }

        @Override
        public long next() {
            return value;
        }
    }

    /**
     * A simple class to flip the bits in a number as the source for
     * {@link LongProvider#next()}.
     */
    static final class FlipLongProvider extends LongProvider {
        /** The value. */
        private long value;

        /**
         * @param value the value
         */
        FlipLongProvider(long value) {
            // Flip the bits so the first call to next() returns to the same state
            this.value = ~value;
        }

        @Override
        public long next() {
            // Flip the bits
            value = ~value;
            return value;
        }
    }

    /**
     * This test ensures that the call to {@link LongProvider#nextInt()} returns the
     * upper and then lower 32-bits from {@link LongProvider#nextLong()}.
     */
    @Test
    void testNextInt() {
        final int max = 5;
        for (int i = 0; i < max; i++) {
            for (int j = 0; j < max; j++) {
                // Pack into upper then lower bits
                final long value = (((long) i) << 32) | (j & 0xffffffffL);
                final LongProvider provider = new FixedLongProvider(value);
                Assertions.assertEquals(i, provider.nextInt(), "1st call not the upper 32-bits");
                Assertions.assertEquals(j, provider.nextInt(), "2nd call not the lower 32-bits");
                Assertions.assertEquals(i, provider.nextInt(), "3rd call not the upper 32-bits");
                Assertions.assertEquals(j, provider.nextInt(), "4th call not the lower 32-bits");
            }
        }
    }

    /**
     * This test ensures that the call to {@link LongProvider#nextBoolean()} returns
     * each of the bits from a call to {@link LongProvider#nextLong()}.
     *
     * <p>The order should be from the least-significant bit.
     */
    @Test
    void testNextBoolean() {
        for (int i = 0; i < Long.SIZE; i++) {
            // Set only a single bit in the source
            final long value = 1L << i;
            final LongProvider provider = new FlipLongProvider(value);
            // Test the result for a single pass over the long
            for (int j = 0; j < Long.SIZE; j++) {
                final boolean expected = i == j;
                final int index = j;
                Assertions.assertEquals(expected, provider.nextBoolean(), () -> "Pass 1, bit " + index);
            }
            // The second pass should use the opposite bits
            for (int j = 0; j < Long.SIZE; j++) {
                final boolean expected = i != j;
                final int index = j;
                Assertions.assertEquals(expected, provider.nextBoolean(), () -> "Pass 2, bit " + index);
            }
        }
    }
}
