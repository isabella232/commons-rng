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

package org.apache.commons.rng.sampling;

import org.junit.jupiter.api.Assertions;

import org.apache.commons.rng.sampling.distribution.ContinuousSampler;
import org.apache.commons.rng.sampling.distribution.DiscreteSampler;
import org.apache.commons.rng.sampling.distribution.LongSampler;

/**
 * Utility class for testing random samplers.
 */
public final class RandomAssert {
    /** Number of samples to generate to test for equal sequences. */
    private static final int SAMPLES = 10;

    /**
     * Class contains only static methods.
     */
    private RandomAssert() {}

    /**
     * Exercise the {@link ContinuousSampler} interface, and
     * ensure that the two samplers produce the same sequence.
     *
     * @param sampler1 First sampler.
     * @param sampler2 Second sampler.
     */
    public static void assertProduceSameSequence(ContinuousSampler sampler1,
                                                 ContinuousSampler sampler2) {
        for (int i = 0; i < SAMPLES; i++) {
            Assertions.assertEquals(sampler1.sample(), sampler2.sample());
        }
    }

    /**
     * Exercise the {@link DiscreteSampler} interface, and
     * ensure that the two samplers produce the same sequence.
     *
     * @param sampler1 First sampler.
     * @param sampler2 Second sampler.
     */
    public static void assertProduceSameSequence(DiscreteSampler sampler1,
                                                 DiscreteSampler sampler2) {
        for (int i = 0; i < SAMPLES; i++) {
            Assertions.assertEquals(sampler1.sample(), sampler2.sample());
        }
    }

    /**
     * Exercise the {@link LongSampler} interface, and
     * ensure that the two samplers produce the same sequence.
     *
     * @param sampler1 First sampler.
     * @param sampler2 Second sampler.
     */
    public static void assertProduceSameSequence(LongSampler sampler1,
                                                 LongSampler sampler2) {
        for (int i = 0; i < SAMPLES; i++) {
            Assertions.assertEquals(sampler1.sample(), sampler2.sample());
        }
    }

    /**
     * Exercise the {@link ObjectSampler} interface, and
     * ensure that the two samplers produce the same sequence.
     *
     * <p>Arrays are tested using {@link Assertions#assertArrayEquals(Object[], Object[])}
     * which handles primitive arrays using exact equality and objects using
     * {@link Object#equals(Object)}. Otherwise {@link Assertions#assertEquals(Object, Object)}
     * is used which makes use of {@link Object#equals(Object)}.</p>
     *
     * <p>This should be used to test samplers of any type by wrapping the sample method
     * to an anonymous {@link ObjectSampler} class.</p>
     *
     * @param sampler1 First sampler.
     * @param sampler2 Second sampler.
     */
    public static <T> void assertProduceSameSequence(ObjectSampler<T> sampler1,
                                                     ObjectSampler<T> sampler2) {
        for (int i = 0; i < SAMPLES; i++) {
            final T value1 = sampler1.sample();
            final T value2 = sampler2.sample();
            if (isArray(value1) && isArray(value2)) {
                // JUnit assertArrayEquals will handle nested primitive arrays
                Assertions.assertArrayEquals(new Object[] {value1}, new Object[] {value2});
            } else {
                Assertions.assertEquals(value1, value2);
            }
        }
    }

    /**
     * Checks if the object is an array.
     *
     * @param object Object.
     * @return true if an array
     */
    private static boolean isArray(Object object) {
        return object != null && object.getClass().isArray();
    }
}
