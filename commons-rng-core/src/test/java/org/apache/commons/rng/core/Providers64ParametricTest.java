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
package org.apache.commons.rng.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.apache.commons.rng.RestorableUniformRandomProvider;

/**
 * Tests which all 64-bits based generators must pass.
 */
class Providers64ParametricTest {
    private static Iterable<RestorableUniformRandomProvider> getList() {
        return ProvidersList.list64();
    }

    @ParameterizedTest
    @MethodSource("getList")
    void testNextBytesChunks(RestorableUniformRandomProvider generator) {
        final int[] chunkSizes = {8, 16, 24};
        final int[] chunks = {1, 2, 3, 4, 5};
        for (int chunkSize : chunkSizes) {
            for (int numChunks : chunks) {
                ProvidersCommonParametricTest.checkNextBytesChunks(generator,
                                                                   chunkSize,
                                                                   numChunks);
            }
        }
    }
}
