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

package org.apache.commons.rng.examples.jmh.sampling.distribution;

import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.sampling.distribution.ContinuousSampler;
import org.apache.commons.rng.sampling.distribution.DiscreteSampler;
import org.apache.commons.rng.sampling.distribution.ZigguratSampler;
import org.apache.commons.rng.sampling.distribution.ZigguratNormalizedGaussianSampler;
import org.apache.commons.rng.simple.RandomSource;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import java.util.concurrent.TimeUnit;

/**
 * Executes a benchmark to compare the speed of generation of random numbers
 * using variations of the ziggurat method.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Fork(value = 1, jvmArgs = {"-server", "-Xms128M", "-Xmx128M"})
public class ZigguratSamplerPerformance {
    /** Mask to create an unsigned long from a signed long. */
    private static final long MAX_INT64 = 0x7fffffffffffffffL;
    /** 2^53. */
    private static final double TWO_POW_63 = 0x1.0p63;

    // Production versions

    /** The name for a copy of the {@link ZigguratNormalizedGaussianSampler} with a table of size 128.
     * This matches the version in Commons RNG release v1.1 to 1.3. */
    private static final String GAUSSIAN_128 = "Gaussian128";
    /** The name for the {@link ZigguratNormalizedGaussianSampler} (table of size 256).
     * This is the version in Commons RNG release v1.4+. */
    private static final String GAUSSIAN_256 = "Gaussian256";
    /** The name for the {@link ZigguratSampler.NormalizedGaussian}. */
    private static final String MOD_GAUSSIAN = "ModGaussian";
    /** The name for the {@link ZigguratSampler.Exponential}. */
    private static final String MOD_EXPONENTIAL = "ModExponential";

    // Testing versions

    /** The name for the {@link ZigguratExponentialSampler} with a table of size 256.
     * This is an exponential sampler using Marsaglia's ziggurat method. */
    private static final String EXPONENTIAL = "Exponential";

    /** The name for the {@link ModifiedZigguratNormalizedGaussianSampler}.
     * This is a base implementation of McFarland's ziggurat method. */
    private static final String MOD_GAUSSIAN2 = "ModGaussian2";
    /** The name for the {@link ModifiedZigguratNormalizedGaussianSamplerSimpleOverhangs}. */
    private static final String MOD_GAUSSIAN_SIMPLE_OVERHANGS = "ModGaussianSimpleOverhangs";
    /** The name for the {@link ModifiedZigguratNormalizedGaussianSamplerIntMap}. */
    private static final String MOD_GAUSSIAN_INT_MAP = "ModGaussianIntMap";

    /** The name for the {@link ModifiedZigguratExponetialSampler}.
     * This is a base implementation of McFarland's ziggurat method. */
    private static final String MOD_EXPONENTIAL2 = "ModExponential2";
    /** The name for the {@link ModifiedZigguratExponentialSamplerSimpleOverhangs}. */
    private static final String MOD_EXPONENTIAL_SIMPLE_OVERHANGS = "ModExponentialSimpleOverhangs";
    /** The name for the {@link ModifiedZigguratExponentialSamplerIntMap}. */
    private static final String MOD_EXPONENTIAL_INT_MAP = "ModExponentialIntMap";

    /**
     * The value.
     *
     * <p>This must NOT be final!</p>
     */
    private double value;

    /**
     * Defines method to use for creating {@code int} index values from a random long.
     */
    @State(Scope.Benchmark)
    public static class IndexSources {
        /** The method to obtain the index. */
        @Param({"CastMask", "MaskCast"})
        private String method;

        /** The sampler. */
        private DiscreteSampler sampler;

        /**
         * @return the sampler.
         */
        public DiscreteSampler getSampler() {
            return sampler;
        }

        /** Instantiates sampler. */
        @Setup
        public void setup() {
            // Use a fast generator
            final UniformRandomProvider rng = RandomSource.XO_RO_SHI_RO_128_PP.create();
            if ("CastMask".equals(method)) {
                sampler = new DiscreteSampler() {
                    @Override
                    public int sample() {
                        final long x = rng.nextLong();
                        return ((int) x) & 0xff;
                    }
                };
            } else if ("MaskCast".equals(method)) {
                sampler = new DiscreteSampler() {
                    @Override
                    public int sample() {
                        final long x = rng.nextLong();
                        return (int) (x & 0xff);
                    }
                };
            } else {
                throwIllegalStateException(method);
            }
        }
    }

    /**
     * Sampler that generates values of type {@code long}.
     */
    interface LongSampler {
        /**
         * @return a sample.
         */
        long sample();
    }

    /**
     * Defines method to use for creating unsigned {@code long} values.
     */
    @State(Scope.Benchmark)
    public static class LongSources {
        /** The method to obtain the long. */
        @Param({"Mask", "Shift"})
        private String method;

        /** The sampler. */
        private LongSampler sampler;

        /**
         * @return the sampler.
         */
        public LongSampler getSampler() {
            return sampler;
        }

        /** Instantiates sampler. */
        @Setup
        public void setup() {
            // Use a fast generator
            final UniformRandomProvider rng = RandomSource.XO_RO_SHI_RO_128_PP.create();
            if ("Mask".equals(method)) {
                sampler = new LongSampler() {
                    @Override
                    public long sample() {
                        return rng.nextLong() & Long.MAX_VALUE;
                    }
                };
            } else if ("Shift".equals(method)) {
                sampler = new LongSampler() {
                    @Override
                    public long sample() {
                        return rng.nextLong() >>> 1;
                    }
                };
            } else {
                throwIllegalStateException(method);
            }
        }
    }

    /**
     * The samplers to use for testing the ziggurat method.
     * Defines the RandomSource and the sampler type.
     */
    @State(Scope.Benchmark)
    public static class Sources {
        /**
         * RNG providers.
         *
         * <p>Use different speeds.</p>
         *
         * @see <a href="https://commons.apache.org/proper/commons-rng/userguide/rng.html">
         *      Commons RNG user guide</a>
         */
        @Param({"XO_RO_SHI_RO_128_PP",
                "MWC_256",
                "JDK"})
        private String randomSourceName;

        /**
         * The sampler type.
         */
        @Param({// Production versions
                GAUSSIAN_128, GAUSSIAN_256, MOD_GAUSSIAN, MOD_EXPONENTIAL,
                // Experimental Marsaglia exponential ziggurat sampler
                EXPONENTIAL,
                // Experimental McFarland Gaussian ziggurat samplers
                MOD_GAUSSIAN2, MOD_GAUSSIAN_SIMPLE_OVERHANGS, MOD_GAUSSIAN_INT_MAP,
                // Experimental McFarland Gaussian ziggurat samplers
                MOD_EXPONENTIAL2, MOD_EXPONENTIAL_SIMPLE_OVERHANGS, MOD_EXPONENTIAL_INT_MAP})
        private String type;

        /** The sampler. */
        private ContinuousSampler sampler;

        /**
         * @return the sampler.
         */
        public ContinuousSampler getSampler() {
            return sampler;
        }

        /** Instantiates sampler. */
        @Setup
        public void setup() {
            final RandomSource randomSource = RandomSource.valueOf(randomSourceName);
            final UniformRandomProvider rng = randomSource.create();
            sampler = createSampler(type, rng);
        }

        /**
         * Creates the sampler.
         *
         * @param type Type of sampler
         * @param rng RNG
         * @return the sampler
         */
        static ContinuousSampler createSampler(String type, UniformRandomProvider rng) {
            if (GAUSSIAN_128.equals(type)) {
                return new ZigguratNormalizedGaussianSampler128(rng);
            } else if (GAUSSIAN_256.equals(type)) {
                return ZigguratNormalizedGaussianSampler.of(rng);
            } else if (MOD_GAUSSIAN.equals(type)) {
                return ZigguratSampler.NormalizedGaussian.of(rng);
            } else if (MOD_EXPONENTIAL.equals(type)) {
                return ZigguratSampler.Exponential.of(rng);
            } else if (EXPONENTIAL.equals(type)) {
                return new ZigguratExponentialSampler(rng);
            } else if (MOD_GAUSSIAN2.equals(type)) {
                return new ModifiedZigguratNormalizedGaussianSampler(rng);
            } else if (MOD_GAUSSIAN_SIMPLE_OVERHANGS.equals(type)) {
                return new ModifiedZigguratNormalizedGaussianSamplerSimpleOverhangs(rng);
            } else if (MOD_GAUSSIAN_INT_MAP.equals(type)) {
                return new ModifiedZigguratNormalizedGaussianSamplerIntMap(rng);
            } else if (MOD_EXPONENTIAL2.equals(type)) {
                return new ModifiedZigguratExponentialSampler(rng);
            } else if (MOD_EXPONENTIAL_SIMPLE_OVERHANGS.equals(type)) {
                return new ModifiedZigguratExponentialSamplerSimpleOverhangs(rng);
            } else if (MOD_EXPONENTIAL_INT_MAP.equals(type)) {
                return new ModifiedZigguratExponentialSamplerIntMap(rng);
            } else {
                throw new IllegalStateException("Unknown type: " + type);
            }
        }
    }

    /**
     * The samplers to use for testing the ziggurat method with sequential sample generation.
     * Defines the RandomSource and the sampler type.
     *
     * <p>This specifically targets the Gaussian sampler. The modified ziggurat sampler
     * for the exponential distribution is always faster than the standard zigurat sampler.
     * The modified ziggurat sampler is faster on single samples than the standard sampler
     * but on repeat calls to generate multiple deviates the standard sampler can be faster
     * depending on the JDK (modern JDKs are faster with the 'old' sampler).
     */
    @State(Scope.Benchmark)
    public static class SequentialSources {
        /**
         * RNG providers.
         *
         * <p>Use different speeds.</p>
         *
         * @see <a href="https://commons.apache.org/proper/commons-rng/userguide/rng.html">
         *      Commons RNG user guide</a>
         */
        @Param({"XO_RO_SHI_RO_128_PP",
                //"MWC_256",
                //"JDK"
        })
        private String randomSourceName;

        /** The sampler type. */
        @Param({// Production versions
                GAUSSIAN_128, GAUSSIAN_256, MOD_GAUSSIAN,
                // Experimental McFarland Gaussian ziggurat samplers
                MOD_GAUSSIAN2, MOD_GAUSSIAN_SIMPLE_OVERHANGS, MOD_GAUSSIAN_INT_MAP})
        private String type;

        /** The size. */
        @Param({"1", "2", "3", "4", "5", "10", "20", "40"})
        private int size;

        /** The sampler. */
        private ContinuousSampler sampler;

        /**
         * @return the sampler.
         */
        public ContinuousSampler getSampler() {
            return sampler;
        }

        /** Instantiates sampler. */
        @Setup
        public void setup() {
            final RandomSource randomSource = RandomSource.valueOf(randomSourceName);
            final UniformRandomProvider rng = randomSource.create();
            final ContinuousSampler s = Sources.createSampler(type, rng);
            sampler = createSampler(size, s);
        }

        /**
         * Creates the sampler for the specified number of samples.
         *
         * @param size the size
         * @param s the sampler to create the samples
         * @return the sampler
         */
        private static ContinuousSampler createSampler(int size, ContinuousSampler s) {
            // Create size samples
            switch (size) {
            case 1:
                return new Size1Sampler(s);
            case 2:
                return new Size2Sampler(s);
            case 3:
                return new Size3Sampler(s);
            case 4:
                return new Size4Sampler(s);
            case 5:
                return new Size5Sampler(s);
            default:
                return new SizeNSampler(s, size);
            }
        }

        /**
         * Create a specified number of samples from an underlying sampler.
         */
        abstract static class SizeSampler implements ContinuousSampler {
            /** The sampler. */
            protected ContinuousSampler delegate;

            /**
             * @param delegate the sampler to create the samples
             */
            SizeSampler(ContinuousSampler delegate) {
                this.delegate = delegate;
            }
        }

        /**
         * Create 1 sample from the sampler.
         */
        static class Size1Sampler extends SizeSampler {
            /**
             * @param delegate the sampler to create the samples
             */
            Size1Sampler(ContinuousSampler delegate) {
                super(delegate);
            }

            @Override
            public double sample() {
                return delegate.sample();
            }
        }

        /**
         * Create 2 samples from the sampler.
         */
        static class Size2Sampler extends SizeSampler {
            /**
             * @param delegate the sampler to create the samples
             */
            Size2Sampler(ContinuousSampler delegate) {
                super(delegate);
            }

            @Override
            public double sample() {
                delegate.sample();
                return delegate.sample();
            }
        }

        /**
         * Create 3 samples from the sampler.
         */
        static class Size3Sampler extends SizeSampler {
            /**
             * @param delegate the sampler to create the samples
             */
            Size3Sampler(ContinuousSampler delegate) {
                super(delegate);
            }

            @Override
            public double sample() {
                delegate.sample();
                delegate.sample();
                return delegate.sample();
            }
        }

        /**
         * Create 4 samples from the sampler.
         */
        static class Size4Sampler extends SizeSampler {
            /**
             * @param delegate the sampler to create the samples
             */
            Size4Sampler(ContinuousSampler delegate) {
                super(delegate);
            }

            @Override
            public double sample() {
                delegate.sample();
                delegate.sample();
                delegate.sample();
                return delegate.sample();
            }
        }

        /**
         * Create 5 samples from the sampler.
         */
        static class Size5Sampler extends SizeSampler {
            /**
             * @param delegate the sampler to create the samples
             */
            Size5Sampler(ContinuousSampler delegate) {
                super(delegate);
            }

            @Override
            public double sample() {
                delegate.sample();
                delegate.sample();
                delegate.sample();
                delegate.sample();
                return delegate.sample();
            }
        }

        /**
         * Create N samples from the sampler.
         */
        static class SizeNSampler extends SizeSampler {
            /** The number of samples minus 1. */
            private final int sizeM1;

            /**
             * @param delegate the sampler to create the samples
             * @param size the size
             */
            SizeNSampler(ContinuousSampler delegate, int size) {
                super(delegate);
                if (size < 1) {
                    throw new IllegalArgumentException("Size must be above zero: " + size);
                }
                this.sizeM1 = size - 1;
            }

            @Override
            public double sample() {
                for (int i = sizeM1; i != 0; i--) {
                    delegate.sample();
                }
                return delegate.sample();
            }
        }
    }

    /**
     * <a href="https://en.wikipedia.org/wiki/Ziggurat_algorithm">
     * Marsaglia and Tsang "Ziggurat" method</a> for sampling from a NormalizedGaussian
     * distribution with mean 0 and standard deviation 1.
     *
     * <p>This is a copy of {@link ZigguratNormalizedGaussianSampler} using a table size of 256.
     */
    static class ZigguratNormalizedGaussianSampler128 implements ContinuousSampler {
        /** Start of tail. */
        private static final double R = 3.442619855899;
        /** Inverse of R. */
        private static final double ONE_OVER_R = 1 / R;
        /** Index of last entry in the tables (which have a size that is a power of 2). */
        private static final int LAST = 127;
        /** Auxiliary table. */
        private static final long[] K;
        /** Auxiliary table. */
        private static final double[] W;
        /** Auxiliary table. */
        private static final double[] F;
        /**
         * The multiplier to convert the least significant 53-bits of a {@code long} to a {@code double}.
         * Taken from org.apache.commons.rng.core.util.NumberFactory.
         */
        private static final double DOUBLE_MULTIPLIER = 0x1.0p-53d;

        /** Underlying source of randomness. */
        private final UniformRandomProvider rng;

        static {
            // Filling the tables.
            // Rectangle area.
            final double v = 9.91256303526217e-3;
            // Direction support uses the sign bit so the maximum magnitude from the long is 2^63
            final double max = Math.pow(2, 63);
            final double oneOverMax = 1d / max;

            K = new long[LAST + 1];
            W = new double[LAST + 1];
            F = new double[LAST + 1];

            double d = R;
            double t = d;
            double fd = pdf(d);
            final double q = v / fd;

            K[0] = (long) ((d / q) * max);
            K[1] = 0;

            W[0] = q * oneOverMax;
            W[LAST] = d * oneOverMax;

            F[0] = 1;
            F[LAST] = fd;

            for (int i = LAST - 1; i >= 1; i--) {
                d = Math.sqrt(-2 * Math.log(v / d + fd));
                fd = pdf(d);

                K[i + 1] = (long) ((d / t) * max);
                t = d;

                F[i] = fd;

                W[i] = d * oneOverMax;
            }
        }

        /**
         * @param rng Generator of uniformly distributed random numbers.
         */
        ZigguratNormalizedGaussianSampler128(UniformRandomProvider rng) {
            this.rng = rng;
        }

        /** {@inheritDoc} */
        @Override
        public double sample() {
            final long j = rng.nextLong();
            final int i = ((int) j) & LAST;
            if (Math.abs(j) < K[i]) {
                // This branch is called about 0.972101 times per sample.
                return j * W[i];
            }
            return fix(j, i);
        }

        /**
         * Gets the value from the tail of the distribution.
         *
         * @param hz Start random integer.
         * @param iz Index of cell corresponding to {@code hz}.
         * @return the requested random value.
         */
        private double fix(long hz,
                           int iz) {
            if (iz == 0) {
                // Base strip.
                // This branch is called about 5.7624515E-4 times per sample.
                double y;
                double x;
                do {
                    // Avoid infinity by creating a non-zero double.
                    y = -Math.log(makeNonZeroDouble(rng.nextLong()));
                    x = -Math.log(makeNonZeroDouble(rng.nextLong())) * ONE_OVER_R;
                } while (y + y < x * x);

                final double out = R + x;
                return hz > 0 ? out : -out;
            }
            // Wedge of other strips.
            // This branch is called about 0.027323 times per sample.
            final double x = hz * W[iz];
            if (F[iz] + rng.nextDouble() * (F[iz - 1] - F[iz]) < pdf(x)) {
                // This branch is called about 0.014961 times per sample.
                return x;
            }
            // Try again.
            // This branch is called about 0.012362 times per sample.
            return sample();
        }

        /**
         * Creates a {@code double} in the interval {@code (0, 1]} from a {@code long} value.
         *
         * @param v Number.
         * @return a {@code double} value in the interval {@code (0, 1]}.
         */
        private static double makeNonZeroDouble(long v) {
            // This matches the method in o.a.c.rng.core.util.NumberFactory.makeDouble(long)
            // but shifts the range from [0, 1) to (0, 1].
            return ((v >>> 11) + 1L) * DOUBLE_MULTIPLIER;
        }

        /**
         * Compute the Gaussian probability density function {@code f(x) = e^-0.5x^2}.
         *
         * @param x Argument.
         * @return \( e^{-\frac{x^2}{2}} \)
         */
        private static double pdf(double x) {
            return Math.exp(-0.5 * x * x);
        }
    }

    /**
     * <a href="https://en.wikipedia.org/wiki/Ziggurat_algorithm">
     * Marsaglia and Tsang "Ziggurat" method</a> for sampling from an exponential
     * distribution.
     *
     * <p>The algorithm is explained in this
     * <a href="http://www.jstatsoft.org/article/view/v005i08/ziggurat.pdf">paper</a>
     * and this implementation has been adapted from the C code provided therein.</p>
     */
    static class ZigguratExponentialSampler implements ContinuousSampler {
        /** Start of tail. */
        private static final double R = 7.69711747013104972;
        /** Index of last entry in the tables (which have a size that is a power of 2). */
        private static final int LAST = 255;
        /** Auxiliary table. */
        private static final long[] K;
        /** Auxiliary table. */
        private static final double[] W;
        /** Auxiliary table. */
        private static final double[] F;

        /** Underlying source of randomness. */
        private final UniformRandomProvider rng;

        static {
            // Filling the tables.
            // Rectangle area.
            final double v = 0.0039496598225815571993;
            // No support for unsigned long so the upper bound is 2^63
            final double max = Math.pow(2, 63);
            final double oneOverMax = 1d / max;

            K = new long[LAST + 1];
            W = new double[LAST + 1];
            F = new double[LAST + 1];

            double d = R;
            double t = d;
            double fd = pdf(d);
            final double q = v / fd;

            K[0] = (long) ((d / q) * max);
            K[1] = 0;

            W[0] = q * oneOverMax;
            W[LAST] = d * oneOverMax;

            F[0] = 1;
            F[LAST] = fd;

            for (int i = LAST - 1; i >= 1; i--) {
                d = -Math.log(v / d + fd);
                fd = pdf(d);

                K[i + 1] = (long) ((d / t) * max);
                t = d;

                F[i] = fd;

                W[i] = d * oneOverMax;
            }
        }

        /**
         * @param rng Generator of uniformly distributed random numbers.
         */
        ZigguratExponentialSampler(UniformRandomProvider rng) {
            this.rng = rng;
        }

        /** {@inheritDoc} */
        @Override
        public double sample() {
            // An unsigned long in [0, 2^63)
            final long j = rng.nextLong() >>> 1;
            final int i = ((int) j) & LAST;
            if (j < K[i]) {
                // This branch is called about 0.977777 times per call into createSample.
                // Note: Frequencies have been empirically measured for the first call to
                // createSample; recursion due to retries have been ignored. Frequencies sum to 1.
                return j * W[i];
            }
            return fix(j, i);
        }

        /**
         * Gets the value from the tail of the distribution.
         *
         * @param jz Start random integer.
         * @param iz Index of cell corresponding to {@code jz}.
         * @return the requested random value.
         */
        private double fix(long jz,
                           int iz) {
            if (iz == 0) {
                // Base strip.
                // This branch is called about 0.000448867 times per call into createSample.
                return R - Math.log(rng.nextDouble());
            }
            // Wedge of other strips.
            final double x = jz * W[iz];
            if (F[iz] + rng.nextDouble() * (F[iz - 1] - F[iz]) < pdf(x)) {
                // This branch is called about 0.0107820 times per call into createSample.
                return x;
            }
            // Try again.
            // This branch is called about 0.0109920 times per call into createSample
            // i.e. this is the recursion frequency.
            return sample();
        }

        /**
         * Compute the exponential probability density function {@code f(x) = e^-x}.
         *
         * @param x Argument.
         * @return {@code e^-x}
         */
        private static double pdf(double x) {
            return Math.exp(-x);
        }
    }

    /**
     * Modified Ziggurat method for sampling from a Gaussian distribution with mean 0 and standard deviation 1.
     *
     * <p>Uses the algorithm from:
     *
     * <blockquote>
     * McFarland, C.D. (2016)<br>
     * "A modified ziggurat algorithm for generating exponentially and normally distributed pseudorandom numbers".<br>
     * <i>Journal of Statistical Computation and Simulation</i> <b>86</b>, 1281-1294.
     * </blockquote>
     *
     * <p>This class uses the same tables as the production version
     * {@link org.apache.commons.rng.sampling.distribution.ZigguratSampler.NormalizedGaussian}
     * with the overhang sampling matching the reference c implementation. Methods and members
     * are protected to allow the implementation to be modified in sub-classes.
     *
     * @see <a href="https://www.tandfonline.com/doi/abs/10.1080/00949655.2015.1060234">
     * McFarland (2016) JSCS 86, 1281-1294</a>
     */
    static class ModifiedZigguratNormalizedGaussianSampler implements ContinuousSampler {
        /** Maximum i value for early exit. */
        protected static final int I_MAX = 253;
        /** The point where the Gaussian switches from convex to concave. */
        protected static final int J_INFLECTION = 205;
        /** Used for largest deviations of f(x) from y_i. This is negated on purpose. */
        protected static final long MAX_IE = -2269182951627976004L;
        /** Used for largest deviations of f(x) from y_i. */
        protected static final long MIN_IE = 760463704284035184L;
        /** Beginning of tail. */
        protected static final double X_0 = 3.6360066255009455861;
        /** 1/X_0. */
        protected static final double ONE_OVER_X_0 = 1d / X_0;

        /** The alias map. An integer in [0, 255] stored as a byte to save space. */
        protected static final byte[] MAP = toBytes(
            new int[] {0, 0, 239, 2, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 251, 251, 251, 251, 251, 251, 251, 250, 250, 250,
                250, 250, 249, 249, 249, 248, 248, 248, 247, 247, 247, 246, 246, 245, 244, 244, 243, 242, 240, 2, 2, 3,
                3, 0, 0, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 1, 0, 0});
        /** The alias inverse PMF. */
        protected static final long[] IPMF = {9223372036854775408L, 1100243796534090797L, 7866600928998383022L,
            6788754710675124691L, 9022865200181688296L, 6522434035205502164L, 4723064097360024697L,
            3360495653216416088L, 2289663232373870755L, 1423968905551920455L, 708364817827797893L, 106102487305601162L,
            -408333464665794443L, -853239722779025206L, -1242095211825521362L, -1585059631105762155L,
            -1889943050287169086L, -2162852901990669843L, -2408637386594511193L, -2631196530262954448L,
            -2833704942520925732L, -3018774289025787439L, -3188573753472222231L, -3344920681707410914L,
            -3489349705062150768L, -3623166100042179544L, -3747487436868335278L, -3863276422712173808L,
            -3971367044063130866L, -4072485557029823980L, -4167267476830916554L, -4256271432240159761L,
            -4339990541927306746L, -4418861817133802326L, -4493273980372377053L, -4563574004462246636L,
            -4630072609770453867L, -4693048910430964891L, -4752754358862894738L, -4809416110052769505L,
            -4863239903586985866L, -4914412541515875772L, -4963104028439161008L, -5009469424769119174L,
            -5053650458856559461L, -5095776932695077750L, -5135967952544929007L, -5174333008451230625L,
            -5210972924952654441L, -5245980700100460247L, -5279442247516297345L, -5311437055462369316L,
            -5342038772315650555L, -5371315728843297107L, -5399331404632512666L, -5426144845448965104L,
            -5451811038519422589L, -5476381248265593008L, -5499903320558339045L, -5522421955752311307L,
            -5543978956085263616L, -5564613449659060480L, -5584362093436146354L, -5603259257517428627L,
            -5621337193070986365L, -5638626184974132325L, -5655154691220933854L, -5670949470294763073L,
            -5686035697601807766L, -5700437072199152453L, -5714175914219812272L, -5727273255295221103L,
            -5739748920271997489L, -5751621603810411941L, -5762908939773946223L, -5773627565915007849L,
            -5783793183152377622L, -5793420610475628449L, -5802523835894661300L, -5811116062947570228L,
            -5819209754516120768L, -5826816672854571802L, -5833947916825278195L, -5840613956570608249L,
            -5846824665591763395L, -5852589350491075357L, -5857916778480726477L, -5862815203334800430L,
            -5867292388935742441L, -5871355631762283997L, -5875011781262890819L, -5878267259039093710L,
            -5881128076579883546L, -5883599852028851382L, -5885687825288565257L, -5887396872144963778L,
            -5888731517955042223L, -5889695949247728499L, -5890294025706689822L, -5890529289910829504L,
            -5890404977675987449L, -5889924026487208528L, -5889089083913555992L, -5887902514965209240L,
            -5886366408898372127L, -5884482585690639756L, -5882252601321090366L, -5879677752995027766L,
            -5876759083794175305L, -5873497386318840733L, -5869893206505510263L, -5865946846617024340L,
            -5861658367354159190L, -5857027590486131555L, -5852054100063428398L, -5846737243971504641L,
            -5841076134082373571L, -5835069647234580384L, -5828716424754549310L, -5822014871949021959L,
            -5814963157357531601L, -5807559211080072146L, -5799800723447230025L, -5791685142338073347L,
            -5783209670985158971L, -5774371264582489329L, -5765166627072226519L, -5755592207057667866L,
            -5745644193442049188L, -5735318510777133844L, -5724610813433666496L, -5713516480340333005L,
            -5702030608556698118L, -5690148005851018661L, -5677863184109371808L, -5665170350903313433L,
            -5652063400924580562L, -5638535907000141432L, -5624581109999480439L, -5610191908627599865L,
            -5595360848093632709L, -5580080108034218849L, -5564341489875550045L, -5548136403221394654L,
            -5531455851545399204L, -5514290416593586870L, -5496630242226406575L, -5478465016761742826L,
            -5459783954986665201L, -5440575777891777017L, -5420828692432397919L, -5400530368638773571L,
            -5379667916699401670L, -5358227861294116825L, -5336196115274292307L, -5313557951078385986L,
            -5290297970633451489L, -5266400072915222391L, -5241847420214015772L, -5216622401043726607L,
            -5190706591719533973L, -5164080714589203240L, -5136724594099067134L, -5108617109269313037L,
            -5079736143458214973L, -5050058530461741430L, -5019559997031891994L, -4988215100963583032L,
            -4955997165645492083L, -4922878208652041825L, -4888828866780320026L, -4853818314258475851L,
            -4817814175855179990L, -4780782432601701861L, -4742687321746719241L, -4703491227581444720L,
            -4663154564978699244L, -4621635653358766431L, -4578890580370785840L, -4534873055659683516L,
            -4489534251700611902L, -4442822631898829564L, -4394683764809104088L, -4345060121983362610L,
            -4293890858708922851L, -4241111576153830158L, -4186654061692619033L, -4130446006804747670L,
            -4072410698657718678L, -4012466683838401105L, -3950527400305017938L, -3886500774061896578L,
            -3820288777467837180L, -3751786943594897634L, -3680883832433527802L, -3607460442623922090L,
            -3531389562483324266L, -3452535052891361699L, -3370751053395887939L, -3285881101633968096L,
            -3197757155301365465L, -3106198503156485339L, -3011010550911937371L, -2911983463883581047L,
            -2808890647470271789L, -2701487041141150061L, -2589507199690603472L, -2472663129329160218L,
            -2350641842139870417L, -2223102583770035263L, -2089673683684728595L, -1949948966090106873L,
            -1803483646855993757L, -1649789631480328207L, -1488330106139747683L, -1318513295725618200L,
            -1139685236927327128L, -951121376596854700L, -752016768184775899L, -541474585642866346L,
            -318492605725778472L, -81947227249193332L, 169425512612864612L, 437052607232193594L, 722551297568810077L,
            1027761939299714316L, 1354787941622770469L, 1706044619203941749L, 2084319374409574060L,
            2492846399593711370L, 2935400169348532576L, 3416413484613111455L, 3941127949860576155L,
            4515787798793437894L, 5147892401439714413L, 5846529325380405959L, 6622819682216655291L,
            7490522659874166085L, 8466869998277892108L, 8216968526387345482L, 4550693915488934669L,
            7628019504138977223L, 6605080500908005863L, 7121156327650272532L, 2484871780331574356L,
            7179104797032803433L, 7066086283830045340L, 1516500120817362978L, 216305945438803570L, 6295963418525324512L,
            2889316805630113239L, -2712587580533804137L, 6562498853538167124L, 7975754821147501243L,
            -9223372036854775808L, -9223372036854775808L};
        /**
         * The precomputed ziggurat lengths, denoted X_i in the main text. X_i = length of
         * ziggurat layer i.
         */
        protected static final double[] X = {3.9421662825398133E-19, 3.720494500411901E-19, 3.582702448062868E-19,
            3.480747623654025E-19, 3.3990177171882136E-19, 3.330377836034014E-19, 3.270943881761755E-19,
            3.21835771324951E-19, 3.171075854184043E-19, 3.1280307407034065E-19, 3.088452065580402E-19,
            3.051765062410735E-19, 3.01752902925846E-19, 2.985398344070532E-19, 2.9550967462801797E-19,
            2.9263997988491663E-19, 2.8991225869977476E-19, 2.873110878022629E-19, 2.8482346327101335E-19,
            2.824383153519439E-19, 2.801461396472703E-19, 2.7793871261807797E-19, 2.758088692141121E-19,
            2.737503269830876E-19, 2.7175754543391047E-19, 2.6982561247538484E-19, 2.6795015188771505E-19,
            2.6612724730440033E-19, 2.6435337927976633E-19, 2.626253728202844E-19, 2.609403533522414E-19,
            2.5929570954331E-19, 2.5768906173214726E-19, 2.561182349771961E-19, 2.545812359339336E-19,
            2.530762329237246E-19, 2.51601538677984E-19, 2.501555953364619E-19, 2.487369613540316E-19,
            2.4734430003079206E-19, 2.4597636942892726E-19, 2.446320134791245E-19, 2.4331015411139206E-19,
            2.4200978427132955E-19, 2.407299617044588E-19, 2.3946980340903347E-19, 2.3822848067252674E-19,
            2.37005214619318E-19, 2.357992722074133E-19, 2.346099626206997E-19, 2.3343663401054455E-19,
            2.322786705467384E-19, 2.3113548974303765E-19, 2.300065400270424E-19, 2.2889129852797606E-19,
            2.2778926905921897E-19, 2.266999802752732E-19, 2.2562298398527416E-19, 2.245578536072726E-19,
            2.235041827493391E-19, 2.2246158390513294E-19, 2.214296872529625E-19, 2.2040813954857555E-19,
            2.19396603102976E-19, 2.183947548374962E-19, 2.1740228540916853E-19, 2.164188984001652E-19,
            2.1544430956570613E-19, 2.1447824613540345E-19, 2.1352044616350571E-19, 2.1257065792395107E-19,
            2.1162863934653125E-19, 2.1069415749082026E-19, 2.0976698805483467E-19, 2.0884691491567363E-19,
            2.0793372969963634E-19, 2.0702723137954107E-19, 2.061272258971713E-19, 2.0523352580895635E-19,
            2.0434594995315797E-19, 2.0346432313698148E-19, 2.0258847584216418E-19, 2.0171824394771313E-19,
            2.008534684685753E-19, 1.9999399530912015E-19, 1.9913967503040585E-19, 1.9829036263028144E-19,
            1.9744591733545175E-19, 1.9660620240469857E-19, 1.9577108494251485E-19, 1.9494043572246307E-19,
            1.941141290196216E-19, 1.9329204245152935E-19, 1.9247405682708168E-19, 1.9166005600287074E-19,
            1.9084992674649826E-19, 1.900435586064234E-19, 1.8924084378793725E-19, 1.8844167703488436E-19,
            1.8764595551677749E-19, 1.868535787209745E-19, 1.8606444834960934E-19, 1.8527846822098793E-19,
            1.8449554417517928E-19, 1.8371558398354868E-19, 1.8293849726199566E-19, 1.8216419538767393E-19,
            1.8139259141898448E-19, 1.8062360001864453E-19, 1.7985713737964743E-19, 1.7909312115393845E-19,
            1.78331470383642E-19, 1.7757210543468428E-19, 1.7681494793266395E-19, 1.760599207008314E-19,
            1.753069477000441E-19, 1.7455595397057217E-19, 1.7380686557563475E-19, 1.7305960954655264E-19,
            1.7231411382940904E-19, 1.7157030723311378E-19, 1.7082811937877138E-19, 1.7008748065025788E-19,
            1.6934832214591352E-19, 1.686105756312635E-19, 1.6787417349268046E-19, 1.6713904869190636E-19,
            1.6640513472135291E-19, 1.6567236556010242E-19, 1.6494067563053266E-19, 1.6420999975549115E-19,
            1.6348027311594532E-19, 1.627514312090366E-19, 1.6202340980646725E-19, 1.6129614491314931E-19,
            1.605695727260459E-19, 1.598436295931348E-19, 1.591182519724249E-19, 1.5839337639095554E-19,
            1.57668939403708E-19, 1.569448775523589E-19, 1.562211273238026E-19, 1.554976251083707E-19,
            1.547743071576727E-19, 1.540511095419833E-19, 1.5332796810709688E-19, 1.5260481843056974E-19,
            1.5188159577726683E-19, 1.5115823505412761E-19, 1.5043467076406199E-19, 1.4971083695888395E-19,
            1.4898666719118714E-19, 1.4826209446506113E-19, 1.4753705118554365E-19, 1.468114691066983E-19,
            1.4608527927820112E-19, 1.453584119903145E-19, 1.4463079671711862E-19, 1.4390236205786415E-19,
            1.4317303567630177E-19, 1.4244274423783481E-19, 1.4171141334433217E-19, 1.4097896746642792E-19,
            1.4024532987312287E-19, 1.3951042255849034E-19, 1.3877416616527576E-19, 1.3803647990516385E-19,
            1.3729728147547174E-19, 1.3655648697200824E-19, 1.3581401079782068E-19, 1.35069765567529E-19,
            1.3432366200692418E-19, 1.3357560884748263E-19, 1.3282551271542047E-19, 1.3207327801488087E-19,
            1.3131880680481524E-19, 1.3056199866908076E-19, 1.2980275057923788E-19, 1.2904095674948608E-19,
            1.2827650848312727E-19, 1.2750929400989213E-19, 1.2673919831340482E-19, 1.2596610294799512E-19,
            1.2518988584399374E-19, 1.2441042110056523E-19, 1.2362757876504165E-19, 1.2284122459762072E-19,
            1.2205121982017852E-19, 1.2125742084782245E-19, 1.2045967900166973E-19, 1.196578402011802E-19,
            1.1885174463419555E-19, 1.180412264026409E-19, 1.1722611314162064E-19, 1.164062256093911E-19,
            1.1558137724540874E-19, 1.1475137369333185E-19, 1.1391601228549047E-19, 1.1307508148492592E-19,
            1.1222836028063025E-19, 1.1137561753107903E-19, 1.1051661125053526E-19, 1.0965108783189755E-19,
            1.0877878119905372E-19, 1.0789941188076655E-19, 1.070126859970364E-19, 1.0611829414763286E-19,
            1.0521591019102928E-19, 1.0430518990027552E-19, 1.0338576948035472E-19, 1.0245726392923699E-19,
            1.015192652220931E-19, 1.0057134029488235E-19, 9.961302879967281E-20, 9.864384059945991E-20,
            9.766325296475582E-20, 9.667070742762345E-20, 9.566560624086667E-20, 9.464730838043321E-20,
            9.361512501732351E-20, 9.256831437088728E-20, 9.150607583763877E-20, 9.042754326772572E-20,
            8.933177723376368E-20, 8.821775610232788E-20, 8.708436567489232E-20, 8.593038710961216E-20,
            8.475448276424435E-20, 8.355517950846234E-20, 8.233084893358536E-20, 8.107968372912985E-20,
            7.979966928413386E-20, 7.848854928607274E-20, 7.714378370093469E-20, 7.576249697946757E-20,
            7.434141357848533E-20, 7.287677680737843E-20, 7.136424544352537E-20, 6.979876024076107E-20,
            6.817436894479905E-20, 6.648399298619854E-20, 6.471911034516277E-20, 6.28693148131037E-20,
            6.092168754828126E-20, 5.885987357557682E-20, 5.666267511609098E-20, 5.430181363089457E-20,
            5.173817174449422E-20, 4.8915031722398545E-20, 4.57447418907553E-20, 4.2078802568583416E-20,
            3.762598672240476E-20, 3.162858980588188E-20, 0.0};
        /** Overhang table. Y_i = f(X_i). */
        protected static final double[] Y = {1.4598410796619063E-22, 3.0066613427942797E-22, 4.612972881510347E-22,
            6.266335004923436E-22, 7.959452476188154E-22, 9.687465502170504E-22, 1.144687700237944E-21,
            1.3235036304379167E-21, 1.504985769205313E-21, 1.6889653000719298E-21, 1.8753025382711626E-21,
            2.063879842369519E-21, 2.2545966913644708E-21, 2.44736615188018E-21, 2.6421122727763533E-21,
            2.8387681187879908E-21, 3.0372742567457284E-21, 3.237577569998659E-21, 3.439630315794878E-21,
            3.64338936579978E-21, 3.848815586891231E-21, 4.0558733309492775E-21, 4.264530010428359E-21,
            4.474755742230507E-21, 4.686523046535558E-21, 4.899806590277526E-21, 5.114582967210549E-21,
            5.330830508204617E-21, 5.548529116703176E-21, 5.767660125269048E-21, 5.988206169917846E-21,
            6.210151079544222E-21, 6.433479778225721E-21, 6.65817819857139E-21, 6.884233204589318E-21,
            7.11163252279571E-21, 7.340364680490309E-21, 7.570418950288642E-21, 7.801785300137974E-21,
            8.034454348157002E-21, 8.268417321733312E-21, 8.503666020391502E-21, 8.740192782010952E-21,
            8.97799045202819E-21, 9.217052355306144E-21, 9.457372270392882E-21, 9.698944405926943E-21,
            9.941763378975842E-21, 1.0185824195119818E-20, 1.043112223011477E-20, 1.0677653212987396E-20,
            1.0925413210432004E-20, 1.1174398612392891E-20, 1.1424606118728715E-20, 1.1676032726866302E-20,
            1.1928675720361027E-20, 1.2182532658289373E-20, 1.2437601365406785E-20, 1.2693879923010674E-20,
            1.2951366660454145E-20, 1.321006014726146E-20, 1.3469959185800733E-20, 1.3731062804473644E-20,
            1.3993370251385596E-20, 1.4256880988463136E-20, 1.452159468598837E-20, 1.4787511217522902E-20,
            1.505463065519617E-20, 1.5322953265335218E-20, 1.5592479504415048E-20, 1.5863210015310328E-20,
            1.6135145623830982E-20, 1.6408287335525592E-20, 1.6682636332737932E-20, 1.6958193971903124E-20,
            1.7234961781071113E-20, 1.7512941457646084E-20, 1.7792134866331487E-20, 1.807254403727107E-20,
            1.8354171164377277E-20, 1.8637018603838945E-20, 1.8921088872801004E-20, 1.9206384648209468E-20,
            1.9492908765815636E-20, 1.9780664219333857E-20, 2.006965415974784E-20, 2.035988189476086E-20,
            2.0651350888385696E-20, 2.094406476067054E-20, 2.1238027287557466E-20, 2.1533242400870487E-20,
            2.1829714188430474E-20, 2.2127446894294597E-20, 2.242644491911827E-20, 2.2726712820637798E-20,
            2.3028255314272276E-20, 2.3331077273843558E-20, 2.3635183732413286E-20, 2.3940579883236352E-20,
            2.4247271080830277E-20, 2.455526284216033E-20, 2.4864560847940368E-20, 2.5175170944049622E-20,
            2.548709914306593E-20, 2.5800351625915997E-20, 2.6114934743643687E-20, 2.6430855019297323E-20,
            2.674811914993741E-20, 2.7066734008766247E-20, 2.7386706647381193E-20, 2.770804429815356E-20,
            2.803075437673527E-20, 2.835484448469575E-20, 2.868032241229163E-20, 2.9007196141372126E-20,
            2.933547384842322E-20, 2.966516390775399E-20, 2.9996274894828624E-20, 3.0328815589748056E-20,
            3.066279498088529E-20, 3.099822226867876E-20, 3.133510686958861E-20, 3.167345842022056E-20,
            3.201328678162299E-20, 3.235460204376261E-20, 3.2697414530184806E-20, 3.304173480286495E-20,
            3.338757366725735E-20, 3.373494217754894E-20, 3.408385164212521E-20, 3.443431362925624E-20,
            3.4786339973011376E-20, 3.5139942779411164E-20, 3.549513443282617E-20, 3.585192760263246E-20,
            3.621033525013417E-20, 3.6570370635764384E-20, 3.693204732657588E-20, 3.729537920403425E-20,
            3.76603804721264E-20, 3.8027065665798284E-20, 3.839544965973665E-20, 3.876554767751017E-20,
            3.9137375301086406E-20, 3.951094848074217E-20, 3.988628354538543E-20, 4.0263397213308566E-20,
            4.064230660339354E-20, 4.1023029246790967E-20, 4.140558309909644E-20, 4.178998655304882E-20,
            4.217625845177682E-20, 4.256441810262176E-20, 4.29544852915662E-20, 4.334648029830012E-20,
            4.3740423911958146E-20, 4.4136337447563716E-20, 4.4534242763218286E-20, 4.4934162278076256E-20,
            4.5336118991149025E-20, 4.5740136500984466E-20, 4.614623902627128E-20, 4.655445142742113E-20,
            4.696479922918509E-20, 4.737730864436494E-20, 4.779200659868417E-20, 4.820892075688811E-20,
            4.8628079550147814E-20, 4.9049512204847653E-20, 4.9473248772842596E-20, 4.9899320163277674E-20,
            5.032775817606897E-20, 5.0758595537153414E-20, 5.1191865935622696E-20, 5.162760406286606E-20,
            5.2065845653856416E-20, 5.2506627530725194E-20, 5.294998764878345E-20, 5.3395965145159426E-20,
            5.3844600390237576E-20, 5.429593504209936E-20, 5.475001210418387E-20, 5.520687598640507E-20,
            5.566657256998382E-20, 5.612914927627579E-20, 5.659465513990248E-20, 5.706314088652056E-20,
            5.753465901559692E-20, 5.800926388859122E-20, 5.848701182298758E-20, 5.89679611926598E-20,
            5.945217253510347E-20, 5.99397086661226E-20, 6.043063480261893E-20, 6.092501869420053E-20,
            6.142293076440286E-20, 6.192444426240153E-20, 6.242963542619394E-20, 6.293858365833621E-20,
            6.345137171544756E-20, 6.396808591283496E-20, 6.448881634575274E-20, 6.501365712899535E-20,
            6.554270665673171E-20, 6.607606788473072E-20, 6.66138486374042E-20, 6.715616194241298E-20,
            6.770312639595058E-20, 6.825486656224641E-20, 6.881151341132782E-20, 6.937320479965968E-20,
            6.994008599895911E-20, 7.05123102792795E-20, 7.109003955339717E-20, 7.16734450906448E-20,
            7.226270830965578E-20, 7.285802166105734E-20, 7.34595896130358E-20, 7.406762975496755E-20,
            7.468237403705282E-20, 7.530407016722667E-20, 7.593298319069855E-20, 7.656939728248375E-20,
            7.721361778948768E-20, 7.786597356641702E-20, 7.852681965945675E-20, 7.919654040385056E-20,
            7.987555301703797E-20, 8.056431178890163E-20, 8.126331299642618E-20, 8.19731007037063E-20,
            8.269427365263403E-20, 8.342749350883679E-20, 8.417349480745342E-20, 8.493309705283207E-20,
            8.57072195782309E-20, 8.64968999859307E-20, 8.730331729565533E-20, 8.81278213788595E-20,
            8.897197092819667E-20, 8.983758323931406E-20, 9.072680069786954E-20, 9.164218148406354E-20,
            9.258682640670276E-20, 9.356456148027886E-20, 9.458021001263618E-20, 9.564001555085036E-20,
            9.675233477050313E-20, 9.792885169780883E-20, 9.918690585753133E-20, 1.0055456271343397E-19,
            1.0208407377305566E-19, 1.0390360993240711E-19, 1.0842021724855044E-19};

        /** Underlying source of randomness. */
        protected final UniformRandomProvider rng;
        /** Exponential sampler used for the long tail. */
        protected final ContinuousSampler exponential;

        /**
         * @param rng Generator of uniformly distributed random numbers.
         */
        ModifiedZigguratNormalizedGaussianSampler(UniformRandomProvider rng) {
            this.rng = rng;
            exponential = new ModifiedZigguratExponentialSampler(rng);
        }

        /** {@inheritDoc} */
        @Override
        public double sample() {
            final long xx = rng.nextLong();
            // Float multiplication squashes these last 8 bits, so they can be used to sample i
            final int i = ((int) xx) & 0xff;

            if (i < I_MAX) {
                // Early exit.
                // Branch frequency: 0.988280
                return X[i] * xx;
            }

            // Recycle bits then advance RNG:
            // u1 = RANDOM_INT63();
            long u1 = xx & MAX_INT64;
            // Another squashed, recyclable bit
            // double sign_bit = u1 & 0x100 ? 1. : -1.
            // Use 2 - 1 or 0 - 1
            final double signBit = ((u1 >>> 7) & 0x2) - 1.0;
            final int j = normSampleA();
            // Four kinds of overhangs:
            //  j = 0                :  Sample from tail
            //  0 < j < J_INFLECTION :  Overhang is concave; only sample from Lower-Left triangle
            //  j = J_INFLECTION     :  Must sample from entire overhang rectangle
            //  j > J_INFLECTION     :  Overhangs are convex; implicitly accept point in Lower-Left triangle
            //
            // Conditional statements are arranged such that the more likely outcomes are first.
            double x;
            if (j > J_INFLECTION) {
                // Convex overhang
                // Branch frequency: 0.00891413
                // Loop repeat frequency: 0.389804
                for (;;) {
                    x = fastPrngSampleX(j, u1);
                    final long uDiff = randomInt63() - u1;
                    if (uDiff >= 0) {
                        // Lower-left triangle
                        break;
                    }
                    if (uDiff >= MAX_IE &&
                        // Within maximum distance of f(x) from the triangle hypotenuse.
                        // Frequency (per upper-right triangle): 0.431497
                        // Reject frequency: 0.489630
                        // Long.MIN_VALUE is used as an unsigned int with value 2^63:
                        // uy = Long.MIN_VALUE - (ux + uDiff)
                        fastPrngSampleY(j, Long.MIN_VALUE - (u1 + uDiff)) < Math.exp(-0.5 * x * x)) {
                        break;
                    }
                    // uDiff < MAX_IE (upper-right triangle) or rejected as above the curve
                    u1 = randomInt63();
                }
            } else if (j == 0) {
                // Tail
                // Branch frequency: 0.000277067
                // Note: Although less frequent than the next branch, j == 0 is a subset of
                // j < J_INFLECTION and must be first.
                // Loop repeat frequency: 0.0634786
                do {
                    x = ONE_OVER_X_0 * exponential.sample();
                } while (exponential.sample() < 0.5 * x * x);
                x += X_0;
            } else if (j < J_INFLECTION) {
                // Concave overhang
                // Branch frequency: 0.00251223
                // Loop repeat frequency: 0.0123784
                for (;;) {
                    // U_x <- min(U_1, U_2)
                    // distance <- | U_1 - U_2 |
                    // U_y <- 1 - (U_x + distance)
                    long uDiff = randomInt63() - u1;
                    if (uDiff < 0) {
                        uDiff = -uDiff;
                        u1 -= uDiff;
                    }
                    x = fastPrngSampleX(j, u1);
                    if (uDiff > MIN_IE ||
                        fastPrngSampleY(j, Long.MIN_VALUE - (u1 + uDiff)) < Math.exp(-0.5 * x * x)) {
                        break;
                    }
                    u1 = randomInt63();
                }
            } else {
                // Inflection point
                // Branch frequency: 0.0000161147
                // Loop repeat frequency: 0.500213
                for (;;) {
                    x = fastPrngSampleX(j, u1);
                    if (fastPrngSampleY(j, randomInt63()) < Math.exp(-0.5 * x * x)) {
                        break;
                    }
                    u1 = randomInt63();
                }
            }
            return signBit * x;
        }

        /**
         * Alias sampling.
         * See http://scorevoting.net/WarrenSmithPages/homepage/sampling.abs
         *
         * @return the alias
         */
        protected int normSampleA() {
            final long x = rng.nextLong();
            // j <- I(0, 256)
            final int j = ((int) x) & 0xff;
            return x >= IPMF[j] ? MAP[j] & 0xff : j;
        }

        /**
         * Return a positive long in {@code [0, 2^63)}.
         *
         * @return the long
         */
        protected long randomInt63() {
            return rng.nextLong() & MAX_INT64;
        }

        /**
         * Auxilary function to see if rejection sampling is required in the overhang.
         * See Fig. 2 in the main text.
         *
         * @param j j
         * @param ux ux
         * @return the sample
         */
        protected static double fastPrngSampleX(int j, long ux) {
            return X[j] * TWO_POW_63 + (X[j - 1] - X[j]) * ux;
        }

        /**
         * Auxilary function to see if rejection sampling is required in the overhang.
         * See Fig. 2 in the main text.
         *
         * @param i i
         * @param uy uy
         * @return the sample
         */
        protected static double fastPrngSampleY(int i, long uy) {
            return Y[i - 1] * TWO_POW_63 + (Y[i] - Y[i - 1]) * uy;
        }

        /**
         * Helper function to convert {@code int} values to bytes using a narrowing primitive conversion.
         *
         * @param values Integer values.
         * @return the bytes
         */
        private static byte[] toBytes(int[] values) {
            final byte[] bytes = new byte[values.length];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) values[i];
            }
            return bytes;
        }
    }

    /**
     * Modified Ziggurat method for sampling from a Gaussian distribution with mean 0 and standard deviation 1.
     *
     * <p>Uses the algorithm from:
     *
     * <blockquote>
     * McFarland, C.D. (2016)<br>
     * "A modified ziggurat algorithm for generating exponentially and normally distributed pseudorandom numbers".<br>
     * <i>Journal of Statistical Computation and Simulation</i> <b>86</b>, 1281-1294.
     * </blockquote>
     *
     * <p>This implementation uses simple overhangs and does not exploit the precomputed
     * distances of the concave and convex overhangs. The implementation matches the c-reference
     * compiled using -DSIMPLE_OVERHANGS.
     *
     * @see <a href="https://www.tandfonline.com/doi/abs/10.1080/00949655.2015.1060234">
     * McFarland (2016) JSCS 86, 1281-1294</a>
     */
    static class ModifiedZigguratNormalizedGaussianSamplerSimpleOverhangs
        extends ModifiedZigguratNormalizedGaussianSampler {

        /**
         * @param rng Generator of uniformly distributed random numbers.
         */
        ModifiedZigguratNormalizedGaussianSamplerSimpleOverhangs(UniformRandomProvider rng) {
            super(rng);
        }

        /** {@inheritDoc} */
        @Override
        public double sample() {
            final long xx = rng.nextLong();
            // Float multiplication squashes these last 8 bits, so they can be used to sample i
            final int i = ((int) xx) & 0xff;

            if (i < I_MAX) {
                // Early exit.
                // Branch frequency: 0.988280
                return X[i] * xx;
            }

            // Another squashed, recyclable bit
            // double sign_bit = u1 & 0x100 ? 1. : -1.
            // Use 2 - 1 or 0 - 1
            final double signBit = ((xx >>> 7) & 0x2) - 1.0;
            final int j = normSampleA();

            // Simple overhangs
            double x;
            if (j == 0) {
                // Tail
                // Branch frequency: 0.000276321
                // Loop repeat frequency: 0.0634091
                do {
                    x = ONE_OVER_X_0 * exponential.sample();
                } while (exponential.sample() < 0.5 * x * x);
                x += X_0;
            } else {
                // Rejection sampling
                // Branch frequency: 0.0114405
                // Loop repeat frequency: 0.419985

                // Recycle bits then advance RNG:
                // u1 = RANDOM_INT63();
                long u1 = xx & MAX_INT64;
                for (;;) {
                    x = fastPrngSampleX(X, j, u1);
                    if (fastPrngSampleY(Y, j, randomInt63()) < Math.exp(-0.5 * x * x)) {
                        break;
                    }
                    u1 = randomInt63();
                }
            }
            return signBit * x;
        }
    }

    /**
     * Modified Ziggurat method for sampling from a Gaussian distribution with mean 0 and standard deviation 1.
     *
     * <p>Uses the algorithm from:
     *
     * <blockquote>
     * McFarland, C.D. (2016)<br>
     * "A modified ziggurat algorithm for generating exponentially and normally distributed pseudorandom numbers".<br>
     * <i>Journal of Statistical Computation and Simulation</i> <b>86</b>, 1281-1294.
     * </blockquote>
     *
     * <p>This is a copy of {@link ModifiedZigguratNormalizedGaussianSampler} using
     * an integer map in-place of a byte map look-up table. The underlying exponential
     * sampler also uses an integer map.
     *
     * @see <a href="https://www.tandfonline.com/doi/abs/10.1080/00949655.2015.1060234">
     * McFarland (2016) JSCS 86, 1281-1294</a>
     */
    static class ModifiedZigguratNormalizedGaussianSamplerIntMap implements ContinuousSampler {
        /** Maximum i value for early exit. */
        protected static final int I_MAX = 253;
        /** The point where the Gaussian switches from convex to concave. */
        protected static final int J_INFLECTION = 205;
        /** Used for largest deviations of f(x) from y_i. This is negated on purpose. */
        protected static final long MAX_IE = -2269182951627976004L;
        /** Used for largest deviations of f(x) from y_i. */
        protected static final long MIN_IE = 760463704284035184L;
        /** Beginning of tail. */
        protected static final double X_0 = 3.6360066255009455861;
        /** 1/X_0. */
        protected static final double ONE_OVER_X_0 = 1d / X_0;

        /** The alias map. An integer in [0, 255] stored as a byte to save space. */
        protected static final int[] MAP =
            {0, 0, 239, 2, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253, 253,
                253, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 251, 251, 251, 251, 251, 251, 251, 250, 250, 250,
                250, 250, 249, 249, 249, 248, 248, 248, 247, 247, 247, 246, 246, 245, 244, 244, 243, 242, 240, 2, 2, 3,
                3, 0, 0, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 253, 1, 0, 0};
        /** The alias inverse PMF. */
        protected static final long[] IPMF = {9223372036854775408L, 1100243796534090797L, 7866600928998383022L,
            6788754710675124691L, 9022865200181688296L, 6522434035205502164L, 4723064097360024697L,
            3360495653216416088L, 2289663232373870755L, 1423968905551920455L, 708364817827797893L, 106102487305601162L,
            -408333464665794443L, -853239722779025206L, -1242095211825521362L, -1585059631105762155L,
            -1889943050287169086L, -2162852901990669843L, -2408637386594511193L, -2631196530262954448L,
            -2833704942520925732L, -3018774289025787439L, -3188573753472222231L, -3344920681707410914L,
            -3489349705062150768L, -3623166100042179544L, -3747487436868335278L, -3863276422712173808L,
            -3971367044063130866L, -4072485557029823980L, -4167267476830916554L, -4256271432240159761L,
            -4339990541927306746L, -4418861817133802326L, -4493273980372377053L, -4563574004462246636L,
            -4630072609770453867L, -4693048910430964891L, -4752754358862894738L, -4809416110052769505L,
            -4863239903586985866L, -4914412541515875772L, -4963104028439161008L, -5009469424769119174L,
            -5053650458856559461L, -5095776932695077750L, -5135967952544929007L, -5174333008451230625L,
            -5210972924952654441L, -5245980700100460247L, -5279442247516297345L, -5311437055462369316L,
            -5342038772315650555L, -5371315728843297107L, -5399331404632512666L, -5426144845448965104L,
            -5451811038519422589L, -5476381248265593008L, -5499903320558339045L, -5522421955752311307L,
            -5543978956085263616L, -5564613449659060480L, -5584362093436146354L, -5603259257517428627L,
            -5621337193070986365L, -5638626184974132325L, -5655154691220933854L, -5670949470294763073L,
            -5686035697601807766L, -5700437072199152453L, -5714175914219812272L, -5727273255295221103L,
            -5739748920271997489L, -5751621603810411941L, -5762908939773946223L, -5773627565915007849L,
            -5783793183152377622L, -5793420610475628449L, -5802523835894661300L, -5811116062947570228L,
            -5819209754516120768L, -5826816672854571802L, -5833947916825278195L, -5840613956570608249L,
            -5846824665591763395L, -5852589350491075357L, -5857916778480726477L, -5862815203334800430L,
            -5867292388935742441L, -5871355631762283997L, -5875011781262890819L, -5878267259039093710L,
            -5881128076579883546L, -5883599852028851382L, -5885687825288565257L, -5887396872144963778L,
            -5888731517955042223L, -5889695949247728499L, -5890294025706689822L, -5890529289910829504L,
            -5890404977675987449L, -5889924026487208528L, -5889089083913555992L, -5887902514965209240L,
            -5886366408898372127L, -5884482585690639756L, -5882252601321090366L, -5879677752995027766L,
            -5876759083794175305L, -5873497386318840733L, -5869893206505510263L, -5865946846617024340L,
            -5861658367354159190L, -5857027590486131555L, -5852054100063428398L, -5846737243971504641L,
            -5841076134082373571L, -5835069647234580384L, -5828716424754549310L, -5822014871949021959L,
            -5814963157357531601L, -5807559211080072146L, -5799800723447230025L, -5791685142338073347L,
            -5783209670985158971L, -5774371264582489329L, -5765166627072226519L, -5755592207057667866L,
            -5745644193442049188L, -5735318510777133844L, -5724610813433666496L, -5713516480340333005L,
            -5702030608556698118L, -5690148005851018661L, -5677863184109371808L, -5665170350903313433L,
            -5652063400924580562L, -5638535907000141432L, -5624581109999480439L, -5610191908627599865L,
            -5595360848093632709L, -5580080108034218849L, -5564341489875550045L, -5548136403221394654L,
            -5531455851545399204L, -5514290416593586870L, -5496630242226406575L, -5478465016761742826L,
            -5459783954986665201L, -5440575777891777017L, -5420828692432397919L, -5400530368638773571L,
            -5379667916699401670L, -5358227861294116825L, -5336196115274292307L, -5313557951078385986L,
            -5290297970633451489L, -5266400072915222391L, -5241847420214015772L, -5216622401043726607L,
            -5190706591719533973L, -5164080714589203240L, -5136724594099067134L, -5108617109269313037L,
            -5079736143458214973L, -5050058530461741430L, -5019559997031891994L, -4988215100963583032L,
            -4955997165645492083L, -4922878208652041825L, -4888828866780320026L, -4853818314258475851L,
            -4817814175855179990L, -4780782432601701861L, -4742687321746719241L, -4703491227581444720L,
            -4663154564978699244L, -4621635653358766431L, -4578890580370785840L, -4534873055659683516L,
            -4489534251700611902L, -4442822631898829564L, -4394683764809104088L, -4345060121983362610L,
            -4293890858708922851L, -4241111576153830158L, -4186654061692619033L, -4130446006804747670L,
            -4072410698657718678L, -4012466683838401105L, -3950527400305017938L, -3886500774061896578L,
            -3820288777467837180L, -3751786943594897634L, -3680883832433527802L, -3607460442623922090L,
            -3531389562483324266L, -3452535052891361699L, -3370751053395887939L, -3285881101633968096L,
            -3197757155301365465L, -3106198503156485339L, -3011010550911937371L, -2911983463883581047L,
            -2808890647470271789L, -2701487041141150061L, -2589507199690603472L, -2472663129329160218L,
            -2350641842139870417L, -2223102583770035263L, -2089673683684728595L, -1949948966090106873L,
            -1803483646855993757L, -1649789631480328207L, -1488330106139747683L, -1318513295725618200L,
            -1139685236927327128L, -951121376596854700L, -752016768184775899L, -541474585642866346L,
            -318492605725778472L, -81947227249193332L, 169425512612864612L, 437052607232193594L, 722551297568810077L,
            1027761939299714316L, 1354787941622770469L, 1706044619203941749L, 2084319374409574060L,
            2492846399593711370L, 2935400169348532576L, 3416413484613111455L, 3941127949860576155L,
            4515787798793437894L, 5147892401439714413L, 5846529325380405959L, 6622819682216655291L,
            7490522659874166085L, 8466869998277892108L, 8216968526387345482L, 4550693915488934669L,
            7628019504138977223L, 6605080500908005863L, 7121156327650272532L, 2484871780331574356L,
            7179104797032803433L, 7066086283830045340L, 1516500120817362978L, 216305945438803570L, 6295963418525324512L,
            2889316805630113239L, -2712587580533804137L, 6562498853538167124L, 7975754821147501243L,
            -9223372036854775808L, -9223372036854775808L};
        /**
         * The precomputed ziggurat lengths, denoted X_i in the main text. X_i = length of
         * ziggurat layer i.
         */
        protected static final double[] X = {3.9421662825398133E-19, 3.720494500411901E-19, 3.582702448062868E-19,
            3.480747623654025E-19, 3.3990177171882136E-19, 3.330377836034014E-19, 3.270943881761755E-19,
            3.21835771324951E-19, 3.171075854184043E-19, 3.1280307407034065E-19, 3.088452065580402E-19,
            3.051765062410735E-19, 3.01752902925846E-19, 2.985398344070532E-19, 2.9550967462801797E-19,
            2.9263997988491663E-19, 2.8991225869977476E-19, 2.873110878022629E-19, 2.8482346327101335E-19,
            2.824383153519439E-19, 2.801461396472703E-19, 2.7793871261807797E-19, 2.758088692141121E-19,
            2.737503269830876E-19, 2.7175754543391047E-19, 2.6982561247538484E-19, 2.6795015188771505E-19,
            2.6612724730440033E-19, 2.6435337927976633E-19, 2.626253728202844E-19, 2.609403533522414E-19,
            2.5929570954331E-19, 2.5768906173214726E-19, 2.561182349771961E-19, 2.545812359339336E-19,
            2.530762329237246E-19, 2.51601538677984E-19, 2.501555953364619E-19, 2.487369613540316E-19,
            2.4734430003079206E-19, 2.4597636942892726E-19, 2.446320134791245E-19, 2.4331015411139206E-19,
            2.4200978427132955E-19, 2.407299617044588E-19, 2.3946980340903347E-19, 2.3822848067252674E-19,
            2.37005214619318E-19, 2.357992722074133E-19, 2.346099626206997E-19, 2.3343663401054455E-19,
            2.322786705467384E-19, 2.3113548974303765E-19, 2.300065400270424E-19, 2.2889129852797606E-19,
            2.2778926905921897E-19, 2.266999802752732E-19, 2.2562298398527416E-19, 2.245578536072726E-19,
            2.235041827493391E-19, 2.2246158390513294E-19, 2.214296872529625E-19, 2.2040813954857555E-19,
            2.19396603102976E-19, 2.183947548374962E-19, 2.1740228540916853E-19, 2.164188984001652E-19,
            2.1544430956570613E-19, 2.1447824613540345E-19, 2.1352044616350571E-19, 2.1257065792395107E-19,
            2.1162863934653125E-19, 2.1069415749082026E-19, 2.0976698805483467E-19, 2.0884691491567363E-19,
            2.0793372969963634E-19, 2.0702723137954107E-19, 2.061272258971713E-19, 2.0523352580895635E-19,
            2.0434594995315797E-19, 2.0346432313698148E-19, 2.0258847584216418E-19, 2.0171824394771313E-19,
            2.008534684685753E-19, 1.9999399530912015E-19, 1.9913967503040585E-19, 1.9829036263028144E-19,
            1.9744591733545175E-19, 1.9660620240469857E-19, 1.9577108494251485E-19, 1.9494043572246307E-19,
            1.941141290196216E-19, 1.9329204245152935E-19, 1.9247405682708168E-19, 1.9166005600287074E-19,
            1.9084992674649826E-19, 1.900435586064234E-19, 1.8924084378793725E-19, 1.8844167703488436E-19,
            1.8764595551677749E-19, 1.868535787209745E-19, 1.8606444834960934E-19, 1.8527846822098793E-19,
            1.8449554417517928E-19, 1.8371558398354868E-19, 1.8293849726199566E-19, 1.8216419538767393E-19,
            1.8139259141898448E-19, 1.8062360001864453E-19, 1.7985713737964743E-19, 1.7909312115393845E-19,
            1.78331470383642E-19, 1.7757210543468428E-19, 1.7681494793266395E-19, 1.760599207008314E-19,
            1.753069477000441E-19, 1.7455595397057217E-19, 1.7380686557563475E-19, 1.7305960954655264E-19,
            1.7231411382940904E-19, 1.7157030723311378E-19, 1.7082811937877138E-19, 1.7008748065025788E-19,
            1.6934832214591352E-19, 1.686105756312635E-19, 1.6787417349268046E-19, 1.6713904869190636E-19,
            1.6640513472135291E-19, 1.6567236556010242E-19, 1.6494067563053266E-19, 1.6420999975549115E-19,
            1.6348027311594532E-19, 1.627514312090366E-19, 1.6202340980646725E-19, 1.6129614491314931E-19,
            1.605695727260459E-19, 1.598436295931348E-19, 1.591182519724249E-19, 1.5839337639095554E-19,
            1.57668939403708E-19, 1.569448775523589E-19, 1.562211273238026E-19, 1.554976251083707E-19,
            1.547743071576727E-19, 1.540511095419833E-19, 1.5332796810709688E-19, 1.5260481843056974E-19,
            1.5188159577726683E-19, 1.5115823505412761E-19, 1.5043467076406199E-19, 1.4971083695888395E-19,
            1.4898666719118714E-19, 1.4826209446506113E-19, 1.4753705118554365E-19, 1.468114691066983E-19,
            1.4608527927820112E-19, 1.453584119903145E-19, 1.4463079671711862E-19, 1.4390236205786415E-19,
            1.4317303567630177E-19, 1.4244274423783481E-19, 1.4171141334433217E-19, 1.4097896746642792E-19,
            1.4024532987312287E-19, 1.3951042255849034E-19, 1.3877416616527576E-19, 1.3803647990516385E-19,
            1.3729728147547174E-19, 1.3655648697200824E-19, 1.3581401079782068E-19, 1.35069765567529E-19,
            1.3432366200692418E-19, 1.3357560884748263E-19, 1.3282551271542047E-19, 1.3207327801488087E-19,
            1.3131880680481524E-19, 1.3056199866908076E-19, 1.2980275057923788E-19, 1.2904095674948608E-19,
            1.2827650848312727E-19, 1.2750929400989213E-19, 1.2673919831340482E-19, 1.2596610294799512E-19,
            1.2518988584399374E-19, 1.2441042110056523E-19, 1.2362757876504165E-19, 1.2284122459762072E-19,
            1.2205121982017852E-19, 1.2125742084782245E-19, 1.2045967900166973E-19, 1.196578402011802E-19,
            1.1885174463419555E-19, 1.180412264026409E-19, 1.1722611314162064E-19, 1.164062256093911E-19,
            1.1558137724540874E-19, 1.1475137369333185E-19, 1.1391601228549047E-19, 1.1307508148492592E-19,
            1.1222836028063025E-19, 1.1137561753107903E-19, 1.1051661125053526E-19, 1.0965108783189755E-19,
            1.0877878119905372E-19, 1.0789941188076655E-19, 1.070126859970364E-19, 1.0611829414763286E-19,
            1.0521591019102928E-19, 1.0430518990027552E-19, 1.0338576948035472E-19, 1.0245726392923699E-19,
            1.015192652220931E-19, 1.0057134029488235E-19, 9.961302879967281E-20, 9.864384059945991E-20,
            9.766325296475582E-20, 9.667070742762345E-20, 9.566560624086667E-20, 9.464730838043321E-20,
            9.361512501732351E-20, 9.256831437088728E-20, 9.150607583763877E-20, 9.042754326772572E-20,
            8.933177723376368E-20, 8.821775610232788E-20, 8.708436567489232E-20, 8.593038710961216E-20,
            8.475448276424435E-20, 8.355517950846234E-20, 8.233084893358536E-20, 8.107968372912985E-20,
            7.979966928413386E-20, 7.848854928607274E-20, 7.714378370093469E-20, 7.576249697946757E-20,
            7.434141357848533E-20, 7.287677680737843E-20, 7.136424544352537E-20, 6.979876024076107E-20,
            6.817436894479905E-20, 6.648399298619854E-20, 6.471911034516277E-20, 6.28693148131037E-20,
            6.092168754828126E-20, 5.885987357557682E-20, 5.666267511609098E-20, 5.430181363089457E-20,
            5.173817174449422E-20, 4.8915031722398545E-20, 4.57447418907553E-20, 4.2078802568583416E-20,
            3.762598672240476E-20, 3.162858980588188E-20, 0.0};
        /** Overhang table. Y_i = f(X_i). */
        protected static final double[] Y = {1.4598410796619063E-22, 3.0066613427942797E-22, 4.612972881510347E-22,
            6.266335004923436E-22, 7.959452476188154E-22, 9.687465502170504E-22, 1.144687700237944E-21,
            1.3235036304379167E-21, 1.504985769205313E-21, 1.6889653000719298E-21, 1.8753025382711626E-21,
            2.063879842369519E-21, 2.2545966913644708E-21, 2.44736615188018E-21, 2.6421122727763533E-21,
            2.8387681187879908E-21, 3.0372742567457284E-21, 3.237577569998659E-21, 3.439630315794878E-21,
            3.64338936579978E-21, 3.848815586891231E-21, 4.0558733309492775E-21, 4.264530010428359E-21,
            4.474755742230507E-21, 4.686523046535558E-21, 4.899806590277526E-21, 5.114582967210549E-21,
            5.330830508204617E-21, 5.548529116703176E-21, 5.767660125269048E-21, 5.988206169917846E-21,
            6.210151079544222E-21, 6.433479778225721E-21, 6.65817819857139E-21, 6.884233204589318E-21,
            7.11163252279571E-21, 7.340364680490309E-21, 7.570418950288642E-21, 7.801785300137974E-21,
            8.034454348157002E-21, 8.268417321733312E-21, 8.503666020391502E-21, 8.740192782010952E-21,
            8.97799045202819E-21, 9.217052355306144E-21, 9.457372270392882E-21, 9.698944405926943E-21,
            9.941763378975842E-21, 1.0185824195119818E-20, 1.043112223011477E-20, 1.0677653212987396E-20,
            1.0925413210432004E-20, 1.1174398612392891E-20, 1.1424606118728715E-20, 1.1676032726866302E-20,
            1.1928675720361027E-20, 1.2182532658289373E-20, 1.2437601365406785E-20, 1.2693879923010674E-20,
            1.2951366660454145E-20, 1.321006014726146E-20, 1.3469959185800733E-20, 1.3731062804473644E-20,
            1.3993370251385596E-20, 1.4256880988463136E-20, 1.452159468598837E-20, 1.4787511217522902E-20,
            1.505463065519617E-20, 1.5322953265335218E-20, 1.5592479504415048E-20, 1.5863210015310328E-20,
            1.6135145623830982E-20, 1.6408287335525592E-20, 1.6682636332737932E-20, 1.6958193971903124E-20,
            1.7234961781071113E-20, 1.7512941457646084E-20, 1.7792134866331487E-20, 1.807254403727107E-20,
            1.8354171164377277E-20, 1.8637018603838945E-20, 1.8921088872801004E-20, 1.9206384648209468E-20,
            1.9492908765815636E-20, 1.9780664219333857E-20, 2.006965415974784E-20, 2.035988189476086E-20,
            2.0651350888385696E-20, 2.094406476067054E-20, 2.1238027287557466E-20, 2.1533242400870487E-20,
            2.1829714188430474E-20, 2.2127446894294597E-20, 2.242644491911827E-20, 2.2726712820637798E-20,
            2.3028255314272276E-20, 2.3331077273843558E-20, 2.3635183732413286E-20, 2.3940579883236352E-20,
            2.4247271080830277E-20, 2.455526284216033E-20, 2.4864560847940368E-20, 2.5175170944049622E-20,
            2.548709914306593E-20, 2.5800351625915997E-20, 2.6114934743643687E-20, 2.6430855019297323E-20,
            2.674811914993741E-20, 2.7066734008766247E-20, 2.7386706647381193E-20, 2.770804429815356E-20,
            2.803075437673527E-20, 2.835484448469575E-20, 2.868032241229163E-20, 2.9007196141372126E-20,
            2.933547384842322E-20, 2.966516390775399E-20, 2.9996274894828624E-20, 3.0328815589748056E-20,
            3.066279498088529E-20, 3.099822226867876E-20, 3.133510686958861E-20, 3.167345842022056E-20,
            3.201328678162299E-20, 3.235460204376261E-20, 3.2697414530184806E-20, 3.304173480286495E-20,
            3.338757366725735E-20, 3.373494217754894E-20, 3.408385164212521E-20, 3.443431362925624E-20,
            3.4786339973011376E-20, 3.5139942779411164E-20, 3.549513443282617E-20, 3.585192760263246E-20,
            3.621033525013417E-20, 3.6570370635764384E-20, 3.693204732657588E-20, 3.729537920403425E-20,
            3.76603804721264E-20, 3.8027065665798284E-20, 3.839544965973665E-20, 3.876554767751017E-20,
            3.9137375301086406E-20, 3.951094848074217E-20, 3.988628354538543E-20, 4.0263397213308566E-20,
            4.064230660339354E-20, 4.1023029246790967E-20, 4.140558309909644E-20, 4.178998655304882E-20,
            4.217625845177682E-20, 4.256441810262176E-20, 4.29544852915662E-20, 4.334648029830012E-20,
            4.3740423911958146E-20, 4.4136337447563716E-20, 4.4534242763218286E-20, 4.4934162278076256E-20,
            4.5336118991149025E-20, 4.5740136500984466E-20, 4.614623902627128E-20, 4.655445142742113E-20,
            4.696479922918509E-20, 4.737730864436494E-20, 4.779200659868417E-20, 4.820892075688811E-20,
            4.8628079550147814E-20, 4.9049512204847653E-20, 4.9473248772842596E-20, 4.9899320163277674E-20,
            5.032775817606897E-20, 5.0758595537153414E-20, 5.1191865935622696E-20, 5.162760406286606E-20,
            5.2065845653856416E-20, 5.2506627530725194E-20, 5.294998764878345E-20, 5.3395965145159426E-20,
            5.3844600390237576E-20, 5.429593504209936E-20, 5.475001210418387E-20, 5.520687598640507E-20,
            5.566657256998382E-20, 5.612914927627579E-20, 5.659465513990248E-20, 5.706314088652056E-20,
            5.753465901559692E-20, 5.800926388859122E-20, 5.848701182298758E-20, 5.89679611926598E-20,
            5.945217253510347E-20, 5.99397086661226E-20, 6.043063480261893E-20, 6.092501869420053E-20,
            6.142293076440286E-20, 6.192444426240153E-20, 6.242963542619394E-20, 6.293858365833621E-20,
            6.345137171544756E-20, 6.396808591283496E-20, 6.448881634575274E-20, 6.501365712899535E-20,
            6.554270665673171E-20, 6.607606788473072E-20, 6.66138486374042E-20, 6.715616194241298E-20,
            6.770312639595058E-20, 6.825486656224641E-20, 6.881151341132782E-20, 6.937320479965968E-20,
            6.994008599895911E-20, 7.05123102792795E-20, 7.109003955339717E-20, 7.16734450906448E-20,
            7.226270830965578E-20, 7.285802166105734E-20, 7.34595896130358E-20, 7.406762975496755E-20,
            7.468237403705282E-20, 7.530407016722667E-20, 7.593298319069855E-20, 7.656939728248375E-20,
            7.721361778948768E-20, 7.786597356641702E-20, 7.852681965945675E-20, 7.919654040385056E-20,
            7.987555301703797E-20, 8.056431178890163E-20, 8.126331299642618E-20, 8.19731007037063E-20,
            8.269427365263403E-20, 8.342749350883679E-20, 8.417349480745342E-20, 8.493309705283207E-20,
            8.57072195782309E-20, 8.64968999859307E-20, 8.730331729565533E-20, 8.81278213788595E-20,
            8.897197092819667E-20, 8.983758323931406E-20, 9.072680069786954E-20, 9.164218148406354E-20,
            9.258682640670276E-20, 9.356456148027886E-20, 9.458021001263618E-20, 9.564001555085036E-20,
            9.675233477050313E-20, 9.792885169780883E-20, 9.918690585753133E-20, 1.0055456271343397E-19,
            1.0208407377305566E-19, 1.0390360993240711E-19, 1.0842021724855044E-19};

        /** Underlying source of randomness. */
        protected final UniformRandomProvider rng;
        /** Exponential sampler used for the long tail. */
        protected final ContinuousSampler exponential;

        /**
         * @param rng Generator of uniformly distributed random numbers.
         */
        ModifiedZigguratNormalizedGaussianSamplerIntMap(UniformRandomProvider rng) {
            this.rng = rng;
            exponential = new ModifiedZigguratExponentialSamplerIntMap(rng);
        }

        /** {@inheritDoc} */
        @Override
        public double sample() {
            final long xx = rng.nextLong();
            // Float multiplication squashes these last 8 bits, so they can be used to sample i
            final int i = ((int) xx) & 0xff;

            if (i < I_MAX) {
                // Early exit.
                // Branch frequency: 0.988280
                return X[i] * xx;
            }

            // Recycle bits then advance RNG:
            // u1 = RANDOM_INT63();
            long u1 = xx & MAX_INT64;
            // Another squashed, recyclable bit
            // double sign_bit = u1 & 0x100 ? 1. : -1.
            // Use 2 - 1 or 0 - 1
            final double signBit = ((u1 >>> 7) & 0x2) - 1.0;
            final int j = normSampleA();
            // Four kinds of overhangs:
            //  j = 0                :  Sample from tail
            //  0 < j < J_INFLECTION :  Overhang is concave; only sample from Lower-Left triangle
            //  j = J_INFLECTION     :  Must sample from entire overhang rectangle
            //  j > J_INFLECTION     :  Overhangs are convex; implicitly accept point in Lower-Left triangle
            //
            // Conditional statements are arranged such that the more likely outcomes are first.
            double x;
            if (j > J_INFLECTION) {
                // Convex overhang
                // Branch frequency: 0.00891413
                // Loop repeat frequency: 0.389804
                for (;;) {
                    x = fastPrngSampleX(j, u1);
                    final long uDiff = randomInt63() - u1;
                    if (uDiff >= 0) {
                        // Lower-left triangle
                        break;
                    }
                    if (uDiff >= MAX_IE &&
                        // Within maximum distance of f(x) from the triangle hypotenuse.
                        // Frequency (per upper-right triangle): 0.431497
                        // Reject frequency: 0.489630
                        // Long.MIN_VALUE is used as an unsigned int with value 2^63:
                        // uy = Long.MIN_VALUE - (ux + uDiff)
                        fastPrngSampleY(j, Long.MIN_VALUE - (u1 + uDiff)) < Math.exp(-0.5 * x * x)) {
                        break;
                    }
                    // uDiff < MAX_IE (upper-right triangle) or rejected as above the curve
                    u1 = randomInt63();
                }
            } else if (j == 0) {
                // Tail
                // Branch frequency: 0.000277067
                // Note: Although less frequent than the next branch, j == 0 is a subset of
                // j < J_INFLECTION and must be first.
                // Loop repeat frequency: 0.0634786
                do {
                    x = ONE_OVER_X_0 * exponential.sample();
                } while (exponential.sample() < 0.5 * x * x);
                x += X_0;
            } else if (j < J_INFLECTION) {
                // Concave overhang
                // Branch frequency: 0.00251223
                // Loop repeat frequency: 0.0123784
                for (;;) {
                    // U_x <- min(U_1, U_2)
                    // distance <- | U_1 - U_2 |
                    // U_y <- 1 - (U_x + distance)
                    long uDiff = randomInt63() - u1;
                    if (uDiff < 0) {
                        uDiff = -uDiff;
                        u1 -= uDiff;
                    }
                    x = fastPrngSampleX(j, u1);
                    if (uDiff > MIN_IE ||
                        fastPrngSampleY(j, Long.MIN_VALUE - (u1 + uDiff)) < Math.exp(-0.5 * x * x)) {
                        break;
                    }
                    u1 = randomInt63();
                }
            } else {
                // Inflection point
                // Branch frequency: 0.0000161147
                // Loop repeat frequency: 0.500213
                for (;;) {
                    x = fastPrngSampleX(j, u1);
                    if (fastPrngSampleY(j, randomInt63()) < Math.exp(-0.5 * x * x)) {
                        break;
                    }
                    u1 = randomInt63();
                }
            }
            return signBit * x;
        }

        /**
         * Alias sampling.
         * See http://scorevoting.net/WarrenSmithPages/homepage/sampling.abs
         *
         * @return the alias
         */
        protected int normSampleA() {
            final long x = rng.nextLong();
            // j <- I(0, 256)
            final int j = ((int) x) & 0xff;
            return x >= IPMF[j] ? MAP[j] : j;
        }

        /**
         * Return a positive long in {@code [0, 2^63)}.
         *
         * @return the long
         */
        protected long randomInt63() {
            return rng.nextLong() & MAX_INT64;
        }

        /**
         * Auxilary function to see if rejection sampling is required in the overhang.
         * See Fig. 2 in the main text.
         *
         * @param j j
         * @param ux ux
         * @return the sample
         */
        protected static double fastPrngSampleX(int j, long ux) {
            return X[j] * TWO_POW_63 + (X[j - 1] - X[j]) * ux;
        }

        /**
         * Auxilary function to see if rejection sampling is required in the overhang.
         * See Fig. 2 in the main text.
         *
         * @param i i
         * @param uy uy
         * @return the sample
         */
        protected static double fastPrngSampleY(int i, long uy) {
            return Y[i - 1] * TWO_POW_63 + (Y[i] - Y[i - 1]) * uy;
        }
    }

    /**
     * Modified Ziggurat method for sampling from an exponential distribution.
     *
     * <p>Uses the algorithm from:
     *
     * <blockquote>
     * McFarland, C.D. (2016)<br>
     * "A modified ziggurat algorithm for generating exponentially and normally distributed pseudorandom numbers".<br>
     * <i>Journal of Statistical Computation and Simulation</i> <b>86</b>, 1281-1294.
     * </blockquote>
     *
     * <p>This class uses the same tables as the production version
     * {@link org.apache.commons.rng.sampling.distribution.ZigguratSampler.Exponential}
     * with the overhang sampling matching the reference c implementation. Methods and members
     * are protected to allow the implementation to be modified in sub-classes.
     *
     * @see <a href="https://www.tandfonline.com/doi/abs/10.1080/00949655.2015.1060234">
     * McFarland (2016) JSCS 86, 1281-1294</a>
     */
    static class ModifiedZigguratExponentialSampler implements ContinuousSampler {
        /** Maximum i value for early exit. */
        private static final int I_MAX = 252;
        /** Maximum distance value for early exit. */
        private static final long IE_MAX = 513303011048449570L;
        /** Beginning of tail. */
        private static final double X_0 = 7.569274694148063;

        /** The alias map. An integer in [0, 255] stored as a byte to save space. */
        private static final byte[] MAP = toBytes(new int[] {0, 0, 1, 235, 3, 4, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
            1, 1, 1, 2, 2, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251,
            251, 250, 250, 250, 250, 250, 250, 250, 249, 249, 249, 249, 249, 249, 248, 248, 248, 248, 247, 247, 247,
            247, 246, 246, 246, 245, 245, 244, 244, 243, 243, 242, 241, 241, 240, 239, 237, 3, 3, 4, 4, 6, 0, 0, 0, 0,
            236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 2, 0, 0, 0});
        /** The alias inverse PMF. */
        private static final long[] IPMF = {9223372036854773904L, 1623796909450835018L, 2664290944894291308L,
            7387971354164061021L, 6515064486552723158L, 8840508362680718891L, 6099647593382936415L,
            7673130333659513775L, 6220332867583438096L, 5045979640552813698L, 4075305837223955523L,
            3258413672162525427L, 2560664887087762532L, 1957224924672899637L, 1429800935350577509L, 964606309710808246L,
            551043923599587126L, 180827629096890295L, -152619738120023625L, -454588624410291545L, -729385126147774968L,
            -980551509819446936L, -1211029700667463960L, -1423284293868548440L, -1619396356369050407L,
            -1801135830956211623L, -1970018048575618087L, -2127348289059705319L, -2274257249303686407L,
            -2411729520096655303L, -2540626634159180934L, -2661705860113406470L, -2775635634532450566L,
            -2883008316030465190L, -2984350790383654790L, -3080133339198116454L, -3170777096303091110L,
            -3256660348483819078L, -3338123885075136262L, -3415475560473299110L, -3488994201966428229L,
            -3558932970354473157L, -3625522261068041093L, -3688972217741989381L, -3749474917563782629L,
            -3807206277531056133L, -3862327722496843557L, -3914987649156779685L, -3965322714631865221L,
            -4013458973776895589L, -4059512885612783333L, -4103592206186241029L, -4145796782586128069L,
            -4186219260694346949L, -4224945717447275173L, -4262056226866285509L, -4297625367836519557L,
            -4331722680528537317L, -4364413077437472517L, -4395757214229401700L, -4425811824915135780L,
            -4454630025296932548L, -4482261588141290436L, -4508753193105288068L, -4534148654077808964L,
            -4558489126279958148L, -4581813295192216580L, -4604157549138257636L, -4625556137145255269L,
            -4646041313519104421L, -4665643470413305925L, -4684391259530326597L, -4702311703971761733L,
            -4719430301145103269L, -4735771117539946308L, -4751356876102087236L, -4766209036859134052L,
            -4780347871386013380L, -4793792531638892068L, -4806561113635132708L, -4818670716409306532L,
            -4830137496634465604L, -4840976719260837892L, -4851202804490348868L, -4860829371376459908L,
            -4869869278311657508L, -4878334660640770948L, -4886236965617427236L, -4893586984900802596L,
            -4900394884772702724L, -4906670234238885316L, -4912422031164496804L, -4917658726580119812L,
            -4922388247283532292L, -4926618016851066692L, -4930354975163335236L, -4933605596540651332L,
            -4936375906575303844L, -4938671497741365892L, -4940497543854575684L, -4941858813449629540L,
            -4942759682136115044L, -4943204143989086820L, -4943195822025527940L, -4942737977813206404L,
            -4941833520255033284L, -4940485013586738820L, -4938694684624359428L, -4936464429291795972L,
            -4933795818458825604L, -4930690103114057988L, -4927148218896864068L, -4923170790008275908L,
            -4918758132519213508L, -4913910257091645764L, -4908626871126539204L, -4902907380349534020L,
            -4896750889844272900L, -4890156204540531076L, -4883121829162554372L, -4875645967641781188L,
            -4867726521994927044L, -4859361090668103364L, -4850546966345113668L, -4841281133215539076L,
            -4831560263698491972L, -4821380714613447492L, -4810738522790066116L, -4799629400105481988L,
            -4788048727936307268L, -4775991551010515012L, -4763452570642114308L, -4750426137329494532L,
            -4736906242696389124L, -4722886510751377669L, -4708360188440088965L, -4693320135461420933L,
            -4677758813316108101L, -4661668273553489093L, -4645040145179241541L, -4627865621182772101L,
            -4610135444140930052L, -4591839890849345476L, -4572968755929961540L, -4553511334358205764L,
            -4533456402849101572L, -4512792200036278980L, -4491506405372580932L, -4469586116675402436L,
            -4447017826233108036L, -4423787395382284804L, -4399880027458416324L, -4375280239014115077L,
            -4349971829190472197L, -4323937847117721861L, -4297160557210933573L, -4269621402214949829L,
            -4241300963840749253L, -4212178920821861701L, -4182234004204451589L, -4151443949668877253L,
            -4119785446662287621L, -4087234084103201605L, -4053764292396156933L, -4019349281473081925L,
            -3983960974549692677L, -3947569937258423301L, -3910145301787345669L, -3871654685619032069L,
            -3832064104425388805L, -3791337878631544901L, -3749438533114327493L, -3706326689447984389L,
            -3661960950051848261L, -3616297773528534789L, -3569291340409189253L, -3520893408440946053L,
            -3471053156460654341L, -3419717015797782598L, -3366828488034805510L, -3312327947826460358L,
            -3256152429334010374L, -3198235394669719110L, -3138506482563172742L, -3076891235255162822L,
            -3013310801389730758L, -2947681612411374854L, -2879915029671670790L, -2809916959107513734L,
            -2737587429961866118L, -2662820133571325574L, -2585501917733379974L, -2505512231579385223L,
            -2422722515205211655L, -2336995527534088455L, -2248184604988727559L, -2156132842510764935L,
            -2060672187261025415L, -1961622433929371911L, -1858790108950105479L, -1751967229002895623L,
            -1640929916937142791L, -1525436855617582472L, -1405227557075253256L, -1280020420662649992L,
            -1149510549536596104L, -1013367289578704904L, -871231448632104200L, -722712146453667848L,
            -567383236774435977L, -404779231966938249L, -234390647591545737L, -55658667960119305L, 132030985907841399L,
            329355128892811767L, 537061298001085174L, 755977262693564150L, 987022116608033270L, 1231219266829431286L,
            1489711711346518517L, 1763780090187553909L, 2054864117341795061L, 2364588157623768948L,
            2694791916990503284L, 3047567482883476212L, 3425304305830816371L, 3830744187097297907L,
            4267048975685830386L, 4737884547990017266L, 5247525842198998257L, 5800989391535355377L,
            6404202162993295344L, 7064218894258540527L, 7789505049452331503L, 8590309807749444846L,
            7643763810684490059L, 8891950541491446071L, 5457384281016205975L, 9083704440929283969L,
            7976211653914433372L, 8178631350487117494L, 2821287825726744835L, 6322989683301709657L,
            4309503753387611426L, 4685170734960170474L, 8404845967535199663L, 7330522972447554153L,
            1960945799076992020L, 4742910674644898996L, -751799822533509968L, 7023456603741959948L,
            3843116882594676172L, 3927231442413902976L, -9223372036854775808L, -9223372036854775808L,
            -9223372036854775808L};
        /**
         * The precomputed ziggurat lengths, denoted X_i in the main text. X_i = length of
         * ziggurat layer i.
         */
        private static final double[] X = {8.206624067534882E-19, 7.397373235160728E-19, 6.913331337791529E-19,
            6.564735882096453E-19, 6.291253995981851E-19, 6.065722412960496E-19, 5.873527610373727E-19,
            5.705885052853694E-19, 5.557094569162239E-19, 5.423243890374395E-19, 5.301529769650878E-19,
            5.189873925770806E-19, 5.086692261799833E-19, 4.990749293879647E-19, 4.901062589444954E-19,
            4.816837901064919E-19, 4.737423865364471E-19, 4.662279580719682E-19, 4.590950901778405E-19,
            4.523052779065815E-19, 4.458255881635396E-19, 4.396276312636838E-19, 4.336867596710647E-19,
            4.2798143618469714E-19, 4.224927302706489E-19, 4.172039125346411E-19, 4.1210012522465616E-19,
            4.0716811225869233E-19, 4.0239599631006903E-19, 3.9777309342877357E-19, 3.93289757853345E-19,
            3.8893725129310323E-19, 3.8470763218720385E-19, 3.8059366138180143E-19, 3.765887213854473E-19,
            3.7268674692030177E-19, 3.688821649224816E-19, 3.651698424880007E-19, 3.6154504153287473E-19,
            3.5800337915318032E-19, 3.545407928453343E-19, 3.5115350988784242E-19, 3.478380203003096E-19,
            3.4459105288907336E-19, 3.4140955396563316E-19, 3.3829066838741162E-19, 3.3523172262289E-19,
            3.3223020958685874E-19, 3.292837750280447E-19, 3.263902052820205E-19, 3.2354741622810815E-19,
            3.207534433108079E-19, 3.180064325047861E-19, 3.1530463211820845E-19, 3.1264638534265134E-19,
            3.100301234693421E-19, 3.07454359701373E-19, 3.049176835000556E-19, 3.0241875541094565E-19,
            2.999563023214455E-19, 2.975291131074259E-19, 2.9513603463113224E-19, 2.9277596805684267E-19,
            2.9044786545442563E-19, 2.8815072666416712E-19, 2.858835963990693E-19, 2.8364556156331615E-19,
            2.81435748767798E-19, 2.7925332202553125E-19, 2.770974806115288E-19, 2.7496745707320232E-19,
            2.7286251537873397E-19, 2.7078194919206054E-19, 2.687250802641905E-19, 2.666912569315344E-19,
            2.646798527127889E-19, 2.6269026499668434E-19, 2.6072191381359757E-19, 2.5877424068465143E-19,
            2.568467075424817E-19, 2.549387957183548E-19, 2.530500049907748E-19, 2.511798526911271E-19,
            2.4932787286227806E-19, 2.474936154663866E-19, 2.456766456384867E-19, 2.438765429826784E-19,
            2.4209290090801527E-19, 2.403253260014054E-19, 2.3857343743505147E-19, 2.368368664061465E-19,
            2.3511525560671253E-19, 2.3340825872163284E-19, 2.3171553995306794E-19, 2.3003677356958333E-19,
            2.283716434784348E-19, 2.2671984281957174E-19, 2.250810735800194E-19, 2.234550462273959E-19,
            2.2184147936140775E-19, 2.2024009938224424E-19, 2.186506401748684E-19, 2.1707284280826716E-19,
            2.1550645524878675E-19, 2.1395123208673778E-19, 2.124069342755064E-19, 2.1087332888245875E-19,
            2.0935018885097035E-19, 2.0783729277295508E-19, 2.0633442467130712E-19, 2.0484137379170616E-19,
            2.0335793440326865E-19, 2.018839056075609E-19, 2.0041909115551697E-19, 1.9896329927183254E-19,
            1.975163424864309E-19, 1.9607803747261946E-19, 1.9464820489157862E-19, 1.9322666924284314E-19,
            1.9181325872045647E-19, 1.904078050744948E-19, 1.8901014347767504E-19, 1.8762011239677479E-19,
            1.8623755346860768E-19, 1.8486231138030984E-19, 1.8349423375370566E-19, 1.8213317103353295E-19,
            1.8077897637931708E-19, 1.7943150556069476E-19, 1.7809061685599652E-19, 1.7675617095390567E-19,
            1.7542803085801941E-19, 1.741060617941453E-19, 1.727901311201724E-19, 1.7148010823836362E-19,
            1.7017586450992059E-19, 1.6887727317167824E-19, 1.6758420925479093E-19, 1.6629654950527621E-19,
            1.650141723062866E-19, 1.6373695760198277E-19, 1.624647868228856E-19, 1.6119754281258616E-19,
            1.5993510975569615E-19, 1.586773731069231E-19, 1.5742421952115544E-19, 1.5617553678444595E-19,
            1.5493121374578016E-19, 1.5369114024951992E-19, 1.524552070684102E-19, 1.5122330583703858E-19,
            1.499953289856356E-19, 1.4877116967410352E-19, 1.4755072172615974E-19, 1.4633387956347966E-19,
            1.4512053813972103E-19, 1.439105928743099E-19, 1.4270393958586506E-19, 1.415004744251338E-19,
            1.4030009380730888E-19, 1.3910269434359025E-19, 1.3790817277185197E-19, 1.3671642588626657E-19,
            1.3552735046573446E-19, 1.3434084320095729E-19, 1.3315680061998685E-19, 1.3197511901207148E-19,
            1.3079569434961214E-19, 1.2961842220802957E-19, 1.28443197683331E-19, 1.2726991530715219E-19,
            1.2609846895903523E-19, 1.2492875177568625E-19, 1.237606560569394E-19, 1.225940731681333E-19,
            1.2142889343858445E-19, 1.2026500605581765E-19, 1.1910229895518744E-19, 1.1794065870449425E-19,
            1.1677997038316715E-19, 1.1562011745554883E-19, 1.144609816377787E-19, 1.1330244275772562E-19,
            1.1214437860737343E-19, 1.109866647870073E-19, 1.0982917454048923E-19, 1.086717785808435E-19,
            1.0751434490529747E-19, 1.0635673859884002E-19, 1.0519882162526621E-19, 1.0404045260457141E-19,
            1.0288148657544097E-19, 1.0172177474144965E-19, 1.0056116419943559E-19, 9.939949764834668E-20,
            9.823661307666745E-20, 9.70723434263201E-20, 9.590651623069063E-20, 9.47389532241542E-20,
            9.356946992015904E-20, 9.239787515456947E-20, 9.122397059055647E-20, 9.004755018085287E-20,
            8.886839958264763E-20, 8.768629551976745E-20, 8.650100508607102E-20, 8.531228498314119E-20,
            8.411988068438521E-20, 8.292352551651342E-20, 8.17229396480345E-20, 8.051782897283921E-20,
            7.930788387509923E-20, 7.809277785952443E-20, 7.687216602842904E-20, 7.564568338396512E-20,
            7.441294293017913E-20, 7.317353354509333E-20, 7.192701758763107E-20, 7.067292819766679E-20,
            6.941076623950036E-20, 6.813999682925642E-20, 6.686004537461023E-20, 6.557029304021008E-20,
            6.427007153336853E-20, 6.295865708092356E-20, 6.163526343814314E-20, 6.02990337321517E-20,
            5.894903089285018E-20, 5.758422635988593E-20, 5.62034866695974E-20, 5.480555741349931E-20,
            5.3389043909003295E-20, 5.1952387717989917E-20, 5.0493837866338355E-20, 4.901141522262949E-20,
            4.7502867933366117E-20, 4.5965615001265455E-20, 4.4396673897997565E-20, 4.279256630214859E-20,
            4.1149193273430015E-20, 3.9461666762606287E-20, 3.7724077131401685E-20, 3.592916408620436E-20,
            3.4067836691100565E-20, 3.2128447641564046E-20, 3.0095646916399994E-20, 2.794846945559833E-20,
            2.5656913048718645E-20, 2.317520975680391E-20, 2.042669522825129E-20, 1.7261770330213488E-20,
            1.3281889259442579E-20, 0.0};
        /** Overhang table. Y_i = f(X_i). */
        private static final double[] Y = {5.595205495112736E-23, 1.1802509982703313E-22, 1.844442338673583E-22,
            2.543903046669831E-22, 3.2737694311509334E-22, 4.0307732132706715E-22, 4.812547831949511E-22,
            5.617291489658331E-22, 6.443582054044353E-22, 7.290266234346368E-22, 8.156388845632194E-22,
            9.041145368348222E-22, 9.94384884863992E-22, 1.0863906045969114E-21, 1.1800799775461269E-21,
            1.2754075534831208E-21, 1.372333117637729E-21, 1.4708208794375214E-21, 1.5708388257440445E-21,
            1.6723581984374566E-21, 1.7753530675030514E-21, 1.8797999785104595E-21, 1.9856776587832504E-21,
            2.0929667704053244E-21, 2.201649700995824E-21, 2.311710385230618E-21, 2.4231341516125464E-21,
            2.535907590142089E-21, 2.650018437417054E-21, 2.765455476366039E-21, 2.8822084483468604E-21,
            3.000267975754771E-21, 3.1196254936130377E-21, 3.240273188880175E-21, 3.3622039464187092E-21,
            3.485411300740904E-21, 3.6098893927859475E-21, 3.735632931097177E-21, 3.862637156862005E-21,
            3.990897812355284E-21, 4.120411112391895E-21, 4.251173718448891E-21, 4.383182715163374E-21,
            4.5164355889510656E-21, 4.6509302085234806E-21, 4.7866648071096E-21, 4.923637966211997E-21,
            5.061848600747899E-21, 5.201295945443473E-21, 5.341979542364895E-21, 5.483899229483096E-21,
            5.627055130180635E-21, 5.7714476436191935E-21, 5.917077435895068E-21, 6.063945431917703E-21,
            6.212052807953168E-21, 6.3614009847804375E-21, 6.511991621413643E-21, 6.6638266093481696E-21,
            6.816908067292628E-21, 6.971238336352438E-21, 7.126819975634082E-21, 7.283655758242034E-21,
            7.441748667643017E-21, 7.601101894374635E-21, 7.761718833077541E-21, 7.923603079832257E-21,
            8.086758429783484E-21, 8.251188875036333E-21, 8.416898602810326E-21, 8.58389199383831E-21,
            8.752173620998646E-21, 8.921748248170071E-21, 9.09262082929965E-21, 9.264796507675128E-21,
            9.438280615393829E-21, 9.613078673021033E-21, 9.789196389431416E-21, 9.966639661827884E-21,
            1.0145414575932636E-20, 1.0325527406345955E-20, 1.0506984617068672E-20, 1.0689792862184811E-20,
            1.0873958986701341E-20, 1.10594900275424E-20, 1.1246393214695825E-20, 1.1434675972510121E-20,
            1.1624345921140471E-20, 1.181541087814266E-20, 1.2007878860214202E-20, 1.2201758085082226E-20,
            1.239705697353804E-20, 1.2593784151618565E-20, 1.2791948452935152E-20, 1.29915589211506E-20,
            1.3192624812605428E-20, 1.3395155599094805E-20, 1.3599160970797774E-20, 1.3804650839360727E-20,
            1.4011635341137284E-20, 1.4220124840587164E-20, 1.4430129933836705E-20, 1.46416614524042E-20,
            1.485473046709328E-20, 1.5069348292058084E-20, 1.5285526489044053E-20, 1.5503276871808626E-20,
            1.5722611510726402E-20, 1.5943542737583543E-20, 1.6166083150566702E-20, 1.6390245619451956E-20,
            1.6616043290999594E-20, 1.684348959456108E-20, 1.7072598247904713E-20, 1.7303383263267072E-20,
            1.7535858953637607E-20, 1.777003993928424E-20, 1.8005941154528286E-20, 1.8243577854777398E-20,
            1.8482965623825808E-20, 1.8724120381431627E-20, 1.8967058391181452E-20, 1.9211796268653192E-20,
            1.9458350989888484E-20, 1.9706739900186868E-20, 1.9956980723234356E-20, 2.0209091570579904E-20,
            2.0463090951473895E-20, 2.0718997783083593E-20, 2.097683140110135E-20, 2.123661157076213E-20,
            2.1498358498287976E-20, 2.1762092842777868E-20, 2.2027835728562592E-20, 2.229560875804522E-20,
            2.256543402504904E-20, 2.2837334128696004E-20, 2.311133218784001E-20, 2.3387451856080863E-20,
            2.366571733738611E-20, 2.394615340234961E-20, 2.422878540511741E-20, 2.451363930101321E-20,
            2.4800741664897764E-20, 2.5090119710298442E-20, 2.5381801309347597E-20, 2.56758150135705E-20,
            2.5972190075566336E-20, 2.6270956471628253E-20, 2.6572144925351523E-20, 2.687578693228184E-20,
            2.718191478565915E-20, 2.7490561603315974E-20, 2.7801761355793055E-20, 2.811554889573917E-20,
            2.8431959988666534E-20, 2.8751031345137833E-20, 2.907280065446631E-20, 2.9397306620015486E-20,
            2.9724588996191657E-20, 3.005468862722811E-20, 3.038764748786764E-20, 3.072350872605708E-20,
            3.1062316707775905E-20, 3.140411706412999E-20, 3.174895674085097E-20, 3.2096884050352357E-20,
            3.2447948726504914E-20, 3.280220198230601E-20, 3.315969657063137E-20, 3.352048684827223E-20,
            3.388462884347689E-20, 3.4252180327233346E-20, 3.4623200888548644E-20, 3.4997752014001677E-20,
            3.537589717186906E-20, 3.5757701901149035E-20, 3.61432339058358E-20, 3.65325631548274E-20,
            3.692576198788357E-20, 3.732290522808698E-20, 3.7724070301302117E-20, 3.812933736317104E-20,
            3.8538789434235234E-20, 3.895251254382786E-20, 3.93705958834424E-20, 3.979313197035144E-20,
            4.022021682232577E-20, 4.0651950144388133E-20, 4.1088435528630944E-20, 4.152978066823271E-20,
            4.197609758692658E-20, 4.242750288530745E-20, 4.2884118005513604E-20, 4.334606951598745E-20,
            4.381348941821026E-20, 4.428651547752084E-20, 4.476529158037235E-20, 4.5249968120658306E-20,
            4.574070241805442E-20, 4.6237659171683015E-20, 4.674101095281837E-20, 4.7250938740823415E-20,
            4.776763250705122E-20, 4.8291291852069895E-20, 4.8822126702292804E-20, 4.936035807293385E-20,
            4.990621890518202E-20, 5.045995498662554E-20, 5.1021825965285324E-20, 5.159210646917826E-20,
            5.2171087345169234E-20, 5.2759077033045284E-20, 5.335640309332586E-20, 5.396341391039951E-20,
            5.458048059625925E-20, 5.520799912453558E-20, 5.584639272987383E-20, 5.649611461419377E-20,
            5.715765100929071E-20, 5.783152465495663E-20, 5.851829876379432E-20, 5.921858155879171E-20,
            5.99330314883387E-20, 6.066236324679689E-20, 6.1407354758435E-20, 6.216885532049976E-20,
            6.294779515010373E-20, 6.37451966432144E-20, 6.456218773753799E-20, 6.54000178818891E-20,
            6.626007726330934E-20, 6.714392014514662E-20, 6.80532934473017E-20, 6.8990172088133E-20,
            6.99568031585645E-20, 7.095576179487843E-20, 7.199002278894508E-20, 7.306305373910546E-20,
            7.417893826626688E-20, 7.534254213417312E-20, 7.655974217114297E-20, 7.783774986341285E-20,
            7.918558267402951E-20, 8.06147755373533E-20, 8.214050276981807E-20, 8.378344597828052E-20,
            8.557312924967816E-20, 8.75544596695901E-20, 8.980238805770688E-20, 9.246247142115109E-20,
            9.591964134495172E-20, 1.0842021724855044E-19};

        /** Underlying source of randomness. */
        protected final UniformRandomProvider rng;

        /**
         * @param rng Generator of uniformly distributed random numbers.
         */
        ModifiedZigguratExponentialSampler(UniformRandomProvider rng) {
            this.rng = rng;
        }

        /** {@inheritDoc} */
        @Override
        public double sample() {
            final long x = rng.nextLong();
            // Float multiplication squashes these last 8 bits, so they can be used to sample i
            final int i = ((int) x) & 0xff;

            if (i < I_MAX) {
                // Early exit.
                // This branch is called about 0.984374 times per call into createSample.
                // Note: Frequencies have been empirically measured for the first call to
                // createSample; recursion due to retries have been ignored. Frequencies sum to 1.
                return X[i] * (x & MAX_INT64);
            }
            // For the first call into createSample:
            // Recursion frequency = 0.000515560
            // Overhang frequency  = 0.0151109
            final int j = expSampleA();
            return j == 0 ? X_0 + sample() : expOverhang(j);
        }

        /**
         * Alias sampling.
         * See http://scorevoting.net/WarrenSmithPages/homepage/sampling.abs
         *
         * @return the alias
         */
        protected int expSampleA() {
            final long x = rng.nextLong();
            // j <- I(0, 256)
            final int j = ((int) x) & 0xff;
            return x >= IPMF[j] ? MAP[j] & 0xff : j;
        }

        /**
         * Draws a PRN from overhang.
         *
         * @param j Index j (must be {@code > 0})
         * @return the sample
         */
        protected double expOverhang(int j) {
            // To sample a unit right-triangle:
            // U_x <- min(U_1, U_2)
            // distance <- | U_1 - U_2 |
            // U_y <- 1 - (U_x + distance)
            long ux = randomInt63();
            long uDistance = randomInt63() - ux;
            if (uDistance < 0) {
                uDistance = -uDistance;
                ux -= uDistance;
            }
            // _FAST_PRNG_SAMPLE_X(xj, ux)
            final double x = fastPrngSampleX(j, ux);
            if (uDistance >= IE_MAX) {
                // Frequency (per call into createSample): 0.0136732
                // Frequency (per call into expOverhang):  0.904857
                // Early Exit: x < y - epsilon
                return x;
            }
            // Frequency per call into createSample:
            // Return    = 0.00143769
            // Recursion = 1e-8
            // Frequency per call into expOverhang:
            // Return    = 0.0951426
            // Recursion = 6.61774e-07

            // _FAST_PRNG_SAMPLE_Y(j, pow(2, 63) - (ux + uDistance))
            // Long.MIN_VALUE is used as an unsigned int with value 2^63:
            // uy = Long.MIN_VALUE - (ux + uDistance)
            return fastPrngSampleY(j, Long.MIN_VALUE - (ux + uDistance)) <= Math.exp(-x) ? x : expOverhang(j);
        }

        /**
         * Return a positive long in {@code [0, 2^63)}.
         *
         * @return the long
         */
        protected long randomInt63() {
            return rng.nextLong() & MAX_INT64;
        }

        /**
         * Auxilary function to see if rejection sampling is required in the overhang.
         * See Fig. 2 in the main text.
         *
         * @param j j
         * @param ux ux
         * @return the sample
         */
        protected static double fastPrngSampleX(int j, long ux) {
            return X[j] * TWO_POW_63 + (X[j - 1] - X[j]) * ux;
        }

        /**
         * Auxilary function to see if rejection sampling is required in the overhang.
         * See Fig. 2 in the main text.
         *
         * @param i i
         * @param uy uy
         * @return the sample
         */
        static double fastPrngSampleY(int i, long uy) {
            return Y[i - 1] * TWO_POW_63 + (Y[i] - Y[i - 1]) * uy;
        }

        /**
         * Helper function to convert {@code int} values to bytes using a narrowing primitive conversion.
         *
         * @param values Integer values.
         * @return the bytes
         */
        private static byte[] toBytes(int[] values) {
            final byte[] bytes = new byte[values.length];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) values[i];
            }
            return bytes;
        }
    }

    /**
     * Modified Ziggurat method for sampling from an exponential distribution.
     *
     * <p>Uses the algorithm from:
     *
     * <blockquote>
     * McFarland, C.D. (2016)<br>
     * "A modified ziggurat algorithm for generating exponentially and normally distributed pseudorandom numbers".<br>
     * <i>Journal of Statistical Computation and Simulation</i> <b>86</b>, 1281-1294.
     * </blockquote>
     *
     * <p>This implementation uses simple overhangs and does not exploit the precomputed
     * distances of the convex overhang.
     *
     * @see <a href="https://www.tandfonline.com/doi/abs/10.1080/00949655.2015.1060234">
     * McFarland (2016) JSCS 86, 1281-1294</a>
     */
    static class ModifiedZigguratExponentialSamplerSimpleOverhangs
        extends ModifiedZigguratExponentialSampler {

        /**
         * @param rng Generator of uniformly distributed random numbers.
         */
        ModifiedZigguratExponentialSamplerSimpleOverhangs(UniformRandomProvider rng) {
            super(rng);
        }

        /** {@inheritDoc} */
        @Override
        protected double expOverhang(int j) {
            final double x = fastPrngSampleX(j, randomInt63());
            return fastPrngSampleY(j, randomInt63()) <= Math.exp(-x) ? x : expOverhang(j);
        }
    }


    /**
     * Modified Ziggurat method for sampling from an exponential distribution.
     *
     * <p>Uses the algorithm from:
     *
     * <blockquote>
     * McFarland, C.D. (2016)<br>
     * "A modified ziggurat algorithm for generating exponentially and normally distributed pseudorandom numbers".<br>
     * <i>Journal of Statistical Computation and Simulation</i> <b>86</b>, 1281-1294.
     * </blockquote>
     *
     * <p>This is a copy of {@link ModifiedZigguratExponentialSampler} using
     * an integer map in-place of a byte map look-up table.
     *
     * @see <a href="https://www.tandfonline.com/doi/abs/10.1080/00949655.2015.1060234">
     * McFarland (2016) JSCS 86, 1281-1294</a>
     */
    static class ModifiedZigguratExponentialSamplerIntMap implements ContinuousSampler {
        /** Maximum i value for early exit. */
        private static final int I_MAX = 252;
        /** Maximum distance value for early exit. */
        private static final long IE_MAX = 513303011048449570L;
        /** Beginning of tail. */
        private static final double X_0 = 7.569274694148063;

        /** The alias map. */
        private static final int[] MAP = {0, 0, 1, 235, 3, 4, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1,
            1, 1, 1, 2, 2, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252, 252,
            252, 252, 252, 252, 252, 252, 252, 252, 252, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251, 251,
            251, 250, 250, 250, 250, 250, 250, 250, 249, 249, 249, 249, 249, 249, 248, 248, 248, 248, 247, 247, 247,
            247, 246, 246, 246, 245, 245, 244, 244, 243, 243, 242, 241, 241, 240, 239, 237, 3, 3, 4, 4, 6, 0, 0, 0, 0,
            236, 237, 238, 239, 240, 241, 242, 243, 244, 245, 246, 247, 248, 249, 250, 251, 252, 2, 0, 0, 0};
        /** The alias inverse PMF. */
        private static final long[] IPMF = {9223372036854773904L, 1623796909450835018L, 2664290944894291308L,
            7387971354164061021L, 6515064486552723158L, 8840508362680718891L, 6099647593382936415L,
            7673130333659513775L, 6220332867583438096L, 5045979640552813698L, 4075305837223955523L,
            3258413672162525427L, 2560664887087762532L, 1957224924672899637L, 1429800935350577509L, 964606309710808246L,
            551043923599587126L, 180827629096890295L, -152619738120023625L, -454588624410291545L, -729385126147774968L,
            -980551509819446936L, -1211029700667463960L, -1423284293868548440L, -1619396356369050407L,
            -1801135830956211623L, -1970018048575618087L, -2127348289059705319L, -2274257249303686407L,
            -2411729520096655303L, -2540626634159180934L, -2661705860113406470L, -2775635634532450566L,
            -2883008316030465190L, -2984350790383654790L, -3080133339198116454L, -3170777096303091110L,
            -3256660348483819078L, -3338123885075136262L, -3415475560473299110L, -3488994201966428229L,
            -3558932970354473157L, -3625522261068041093L, -3688972217741989381L, -3749474917563782629L,
            -3807206277531056133L, -3862327722496843557L, -3914987649156779685L, -3965322714631865221L,
            -4013458973776895589L, -4059512885612783333L, -4103592206186241029L, -4145796782586128069L,
            -4186219260694346949L, -4224945717447275173L, -4262056226866285509L, -4297625367836519557L,
            -4331722680528537317L, -4364413077437472517L, -4395757214229401700L, -4425811824915135780L,
            -4454630025296932548L, -4482261588141290436L, -4508753193105288068L, -4534148654077808964L,
            -4558489126279958148L, -4581813295192216580L, -4604157549138257636L, -4625556137145255269L,
            -4646041313519104421L, -4665643470413305925L, -4684391259530326597L, -4702311703971761733L,
            -4719430301145103269L, -4735771117539946308L, -4751356876102087236L, -4766209036859134052L,
            -4780347871386013380L, -4793792531638892068L, -4806561113635132708L, -4818670716409306532L,
            -4830137496634465604L, -4840976719260837892L, -4851202804490348868L, -4860829371376459908L,
            -4869869278311657508L, -4878334660640770948L, -4886236965617427236L, -4893586984900802596L,
            -4900394884772702724L, -4906670234238885316L, -4912422031164496804L, -4917658726580119812L,
            -4922388247283532292L, -4926618016851066692L, -4930354975163335236L, -4933605596540651332L,
            -4936375906575303844L, -4938671497741365892L, -4940497543854575684L, -4941858813449629540L,
            -4942759682136115044L, -4943204143989086820L, -4943195822025527940L, -4942737977813206404L,
            -4941833520255033284L, -4940485013586738820L, -4938694684624359428L, -4936464429291795972L,
            -4933795818458825604L, -4930690103114057988L, -4927148218896864068L, -4923170790008275908L,
            -4918758132519213508L, -4913910257091645764L, -4908626871126539204L, -4902907380349534020L,
            -4896750889844272900L, -4890156204540531076L, -4883121829162554372L, -4875645967641781188L,
            -4867726521994927044L, -4859361090668103364L, -4850546966345113668L, -4841281133215539076L,
            -4831560263698491972L, -4821380714613447492L, -4810738522790066116L, -4799629400105481988L,
            -4788048727936307268L, -4775991551010515012L, -4763452570642114308L, -4750426137329494532L,
            -4736906242696389124L, -4722886510751377669L, -4708360188440088965L, -4693320135461420933L,
            -4677758813316108101L, -4661668273553489093L, -4645040145179241541L, -4627865621182772101L,
            -4610135444140930052L, -4591839890849345476L, -4572968755929961540L, -4553511334358205764L,
            -4533456402849101572L, -4512792200036278980L, -4491506405372580932L, -4469586116675402436L,
            -4447017826233108036L, -4423787395382284804L, -4399880027458416324L, -4375280239014115077L,
            -4349971829190472197L, -4323937847117721861L, -4297160557210933573L, -4269621402214949829L,
            -4241300963840749253L, -4212178920821861701L, -4182234004204451589L, -4151443949668877253L,
            -4119785446662287621L, -4087234084103201605L, -4053764292396156933L, -4019349281473081925L,
            -3983960974549692677L, -3947569937258423301L, -3910145301787345669L, -3871654685619032069L,
            -3832064104425388805L, -3791337878631544901L, -3749438533114327493L, -3706326689447984389L,
            -3661960950051848261L, -3616297773528534789L, -3569291340409189253L, -3520893408440946053L,
            -3471053156460654341L, -3419717015797782598L, -3366828488034805510L, -3312327947826460358L,
            -3256152429334010374L, -3198235394669719110L, -3138506482563172742L, -3076891235255162822L,
            -3013310801389730758L, -2947681612411374854L, -2879915029671670790L, -2809916959107513734L,
            -2737587429961866118L, -2662820133571325574L, -2585501917733379974L, -2505512231579385223L,
            -2422722515205211655L, -2336995527534088455L, -2248184604988727559L, -2156132842510764935L,
            -2060672187261025415L, -1961622433929371911L, -1858790108950105479L, -1751967229002895623L,
            -1640929916937142791L, -1525436855617582472L, -1405227557075253256L, -1280020420662649992L,
            -1149510549536596104L, -1013367289578704904L, -871231448632104200L, -722712146453667848L,
            -567383236774435977L, -404779231966938249L, -234390647591545737L, -55658667960119305L, 132030985907841399L,
            329355128892811767L, 537061298001085174L, 755977262693564150L, 987022116608033270L, 1231219266829431286L,
            1489711711346518517L, 1763780090187553909L, 2054864117341795061L, 2364588157623768948L,
            2694791916990503284L, 3047567482883476212L, 3425304305830816371L, 3830744187097297907L,
            4267048975685830386L, 4737884547990017266L, 5247525842198998257L, 5800989391535355377L,
            6404202162993295344L, 7064218894258540527L, 7789505049452331503L, 8590309807749444846L,
            7643763810684490059L, 8891950541491446071L, 5457384281016205975L, 9083704440929283969L,
            7976211653914433372L, 8178631350487117494L, 2821287825726744835L, 6322989683301709657L,
            4309503753387611426L, 4685170734960170474L, 8404845967535199663L, 7330522972447554153L,
            1960945799076992020L, 4742910674644898996L, -751799822533509968L, 7023456603741959948L,
            3843116882594676172L, 3927231442413902976L, -9223372036854775808L, -9223372036854775808L,
            -9223372036854775808L};
        /**
         * The precomputed ziggurat lengths, denoted X_i in the main text. X_i = length of
         * ziggurat layer i.
         */
        private static final double[] X = {8.206624067534882E-19, 7.397373235160728E-19, 6.913331337791529E-19,
            6.564735882096453E-19, 6.291253995981851E-19, 6.065722412960496E-19, 5.873527610373727E-19,
            5.705885052853694E-19, 5.557094569162239E-19, 5.423243890374395E-19, 5.301529769650878E-19,
            5.189873925770806E-19, 5.086692261799833E-19, 4.990749293879647E-19, 4.901062589444954E-19,
            4.816837901064919E-19, 4.737423865364471E-19, 4.662279580719682E-19, 4.590950901778405E-19,
            4.523052779065815E-19, 4.458255881635396E-19, 4.396276312636838E-19, 4.336867596710647E-19,
            4.2798143618469714E-19, 4.224927302706489E-19, 4.172039125346411E-19, 4.1210012522465616E-19,
            4.0716811225869233E-19, 4.0239599631006903E-19, 3.9777309342877357E-19, 3.93289757853345E-19,
            3.8893725129310323E-19, 3.8470763218720385E-19, 3.8059366138180143E-19, 3.765887213854473E-19,
            3.7268674692030177E-19, 3.688821649224816E-19, 3.651698424880007E-19, 3.6154504153287473E-19,
            3.5800337915318032E-19, 3.545407928453343E-19, 3.5115350988784242E-19, 3.478380203003096E-19,
            3.4459105288907336E-19, 3.4140955396563316E-19, 3.3829066838741162E-19, 3.3523172262289E-19,
            3.3223020958685874E-19, 3.292837750280447E-19, 3.263902052820205E-19, 3.2354741622810815E-19,
            3.207534433108079E-19, 3.180064325047861E-19, 3.1530463211820845E-19, 3.1264638534265134E-19,
            3.100301234693421E-19, 3.07454359701373E-19, 3.049176835000556E-19, 3.0241875541094565E-19,
            2.999563023214455E-19, 2.975291131074259E-19, 2.9513603463113224E-19, 2.9277596805684267E-19,
            2.9044786545442563E-19, 2.8815072666416712E-19, 2.858835963990693E-19, 2.8364556156331615E-19,
            2.81435748767798E-19, 2.7925332202553125E-19, 2.770974806115288E-19, 2.7496745707320232E-19,
            2.7286251537873397E-19, 2.7078194919206054E-19, 2.687250802641905E-19, 2.666912569315344E-19,
            2.646798527127889E-19, 2.6269026499668434E-19, 2.6072191381359757E-19, 2.5877424068465143E-19,
            2.568467075424817E-19, 2.549387957183548E-19, 2.530500049907748E-19, 2.511798526911271E-19,
            2.4932787286227806E-19, 2.474936154663866E-19, 2.456766456384867E-19, 2.438765429826784E-19,
            2.4209290090801527E-19, 2.403253260014054E-19, 2.3857343743505147E-19, 2.368368664061465E-19,
            2.3511525560671253E-19, 2.3340825872163284E-19, 2.3171553995306794E-19, 2.3003677356958333E-19,
            2.283716434784348E-19, 2.2671984281957174E-19, 2.250810735800194E-19, 2.234550462273959E-19,
            2.2184147936140775E-19, 2.2024009938224424E-19, 2.186506401748684E-19, 2.1707284280826716E-19,
            2.1550645524878675E-19, 2.1395123208673778E-19, 2.124069342755064E-19, 2.1087332888245875E-19,
            2.0935018885097035E-19, 2.0783729277295508E-19, 2.0633442467130712E-19, 2.0484137379170616E-19,
            2.0335793440326865E-19, 2.018839056075609E-19, 2.0041909115551697E-19, 1.9896329927183254E-19,
            1.975163424864309E-19, 1.9607803747261946E-19, 1.9464820489157862E-19, 1.9322666924284314E-19,
            1.9181325872045647E-19, 1.904078050744948E-19, 1.8901014347767504E-19, 1.8762011239677479E-19,
            1.8623755346860768E-19, 1.8486231138030984E-19, 1.8349423375370566E-19, 1.8213317103353295E-19,
            1.8077897637931708E-19, 1.7943150556069476E-19, 1.7809061685599652E-19, 1.7675617095390567E-19,
            1.7542803085801941E-19, 1.741060617941453E-19, 1.727901311201724E-19, 1.7148010823836362E-19,
            1.7017586450992059E-19, 1.6887727317167824E-19, 1.6758420925479093E-19, 1.6629654950527621E-19,
            1.650141723062866E-19, 1.6373695760198277E-19, 1.624647868228856E-19, 1.6119754281258616E-19,
            1.5993510975569615E-19, 1.586773731069231E-19, 1.5742421952115544E-19, 1.5617553678444595E-19,
            1.5493121374578016E-19, 1.5369114024951992E-19, 1.524552070684102E-19, 1.5122330583703858E-19,
            1.499953289856356E-19, 1.4877116967410352E-19, 1.4755072172615974E-19, 1.4633387956347966E-19,
            1.4512053813972103E-19, 1.439105928743099E-19, 1.4270393958586506E-19, 1.415004744251338E-19,
            1.4030009380730888E-19, 1.3910269434359025E-19, 1.3790817277185197E-19, 1.3671642588626657E-19,
            1.3552735046573446E-19, 1.3434084320095729E-19, 1.3315680061998685E-19, 1.3197511901207148E-19,
            1.3079569434961214E-19, 1.2961842220802957E-19, 1.28443197683331E-19, 1.2726991530715219E-19,
            1.2609846895903523E-19, 1.2492875177568625E-19, 1.237606560569394E-19, 1.225940731681333E-19,
            1.2142889343858445E-19, 1.2026500605581765E-19, 1.1910229895518744E-19, 1.1794065870449425E-19,
            1.1677997038316715E-19, 1.1562011745554883E-19, 1.144609816377787E-19, 1.1330244275772562E-19,
            1.1214437860737343E-19, 1.109866647870073E-19, 1.0982917454048923E-19, 1.086717785808435E-19,
            1.0751434490529747E-19, 1.0635673859884002E-19, 1.0519882162526621E-19, 1.0404045260457141E-19,
            1.0288148657544097E-19, 1.0172177474144965E-19, 1.0056116419943559E-19, 9.939949764834668E-20,
            9.823661307666745E-20, 9.70723434263201E-20, 9.590651623069063E-20, 9.47389532241542E-20,
            9.356946992015904E-20, 9.239787515456947E-20, 9.122397059055647E-20, 9.004755018085287E-20,
            8.886839958264763E-20, 8.768629551976745E-20, 8.650100508607102E-20, 8.531228498314119E-20,
            8.411988068438521E-20, 8.292352551651342E-20, 8.17229396480345E-20, 8.051782897283921E-20,
            7.930788387509923E-20, 7.809277785952443E-20, 7.687216602842904E-20, 7.564568338396512E-20,
            7.441294293017913E-20, 7.317353354509333E-20, 7.192701758763107E-20, 7.067292819766679E-20,
            6.941076623950036E-20, 6.813999682925642E-20, 6.686004537461023E-20, 6.557029304021008E-20,
            6.427007153336853E-20, 6.295865708092356E-20, 6.163526343814314E-20, 6.02990337321517E-20,
            5.894903089285018E-20, 5.758422635988593E-20, 5.62034866695974E-20, 5.480555741349931E-20,
            5.3389043909003295E-20, 5.1952387717989917E-20, 5.0493837866338355E-20, 4.901141522262949E-20,
            4.7502867933366117E-20, 4.5965615001265455E-20, 4.4396673897997565E-20, 4.279256630214859E-20,
            4.1149193273430015E-20, 3.9461666762606287E-20, 3.7724077131401685E-20, 3.592916408620436E-20,
            3.4067836691100565E-20, 3.2128447641564046E-20, 3.0095646916399994E-20, 2.794846945559833E-20,
            2.5656913048718645E-20, 2.317520975680391E-20, 2.042669522825129E-20, 1.7261770330213488E-20,
            1.3281889259442579E-20, 0.0};
        /** Overhang table. Y_i = f(X_i). */
        private static final double[] Y = {5.595205495112736E-23, 1.1802509982703313E-22, 1.844442338673583E-22,
            2.543903046669831E-22, 3.2737694311509334E-22, 4.0307732132706715E-22, 4.812547831949511E-22,
            5.617291489658331E-22, 6.443582054044353E-22, 7.290266234346368E-22, 8.156388845632194E-22,
            9.041145368348222E-22, 9.94384884863992E-22, 1.0863906045969114E-21, 1.1800799775461269E-21,
            1.2754075534831208E-21, 1.372333117637729E-21, 1.4708208794375214E-21, 1.5708388257440445E-21,
            1.6723581984374566E-21, 1.7753530675030514E-21, 1.8797999785104595E-21, 1.9856776587832504E-21,
            2.0929667704053244E-21, 2.201649700995824E-21, 2.311710385230618E-21, 2.4231341516125464E-21,
            2.535907590142089E-21, 2.650018437417054E-21, 2.765455476366039E-21, 2.8822084483468604E-21,
            3.000267975754771E-21, 3.1196254936130377E-21, 3.240273188880175E-21, 3.3622039464187092E-21,
            3.485411300740904E-21, 3.6098893927859475E-21, 3.735632931097177E-21, 3.862637156862005E-21,
            3.990897812355284E-21, 4.120411112391895E-21, 4.251173718448891E-21, 4.383182715163374E-21,
            4.5164355889510656E-21, 4.6509302085234806E-21, 4.7866648071096E-21, 4.923637966211997E-21,
            5.061848600747899E-21, 5.201295945443473E-21, 5.341979542364895E-21, 5.483899229483096E-21,
            5.627055130180635E-21, 5.7714476436191935E-21, 5.917077435895068E-21, 6.063945431917703E-21,
            6.212052807953168E-21, 6.3614009847804375E-21, 6.511991621413643E-21, 6.6638266093481696E-21,
            6.816908067292628E-21, 6.971238336352438E-21, 7.126819975634082E-21, 7.283655758242034E-21,
            7.441748667643017E-21, 7.601101894374635E-21, 7.761718833077541E-21, 7.923603079832257E-21,
            8.086758429783484E-21, 8.251188875036333E-21, 8.416898602810326E-21, 8.58389199383831E-21,
            8.752173620998646E-21, 8.921748248170071E-21, 9.09262082929965E-21, 9.264796507675128E-21,
            9.438280615393829E-21, 9.613078673021033E-21, 9.789196389431416E-21, 9.966639661827884E-21,
            1.0145414575932636E-20, 1.0325527406345955E-20, 1.0506984617068672E-20, 1.0689792862184811E-20,
            1.0873958986701341E-20, 1.10594900275424E-20, 1.1246393214695825E-20, 1.1434675972510121E-20,
            1.1624345921140471E-20, 1.181541087814266E-20, 1.2007878860214202E-20, 1.2201758085082226E-20,
            1.239705697353804E-20, 1.2593784151618565E-20, 1.2791948452935152E-20, 1.29915589211506E-20,
            1.3192624812605428E-20, 1.3395155599094805E-20, 1.3599160970797774E-20, 1.3804650839360727E-20,
            1.4011635341137284E-20, 1.4220124840587164E-20, 1.4430129933836705E-20, 1.46416614524042E-20,
            1.485473046709328E-20, 1.5069348292058084E-20, 1.5285526489044053E-20, 1.5503276871808626E-20,
            1.5722611510726402E-20, 1.5943542737583543E-20, 1.6166083150566702E-20, 1.6390245619451956E-20,
            1.6616043290999594E-20, 1.684348959456108E-20, 1.7072598247904713E-20, 1.7303383263267072E-20,
            1.7535858953637607E-20, 1.777003993928424E-20, 1.8005941154528286E-20, 1.8243577854777398E-20,
            1.8482965623825808E-20, 1.8724120381431627E-20, 1.8967058391181452E-20, 1.9211796268653192E-20,
            1.9458350989888484E-20, 1.9706739900186868E-20, 1.9956980723234356E-20, 2.0209091570579904E-20,
            2.0463090951473895E-20, 2.0718997783083593E-20, 2.097683140110135E-20, 2.123661157076213E-20,
            2.1498358498287976E-20, 2.1762092842777868E-20, 2.2027835728562592E-20, 2.229560875804522E-20,
            2.256543402504904E-20, 2.2837334128696004E-20, 2.311133218784001E-20, 2.3387451856080863E-20,
            2.366571733738611E-20, 2.394615340234961E-20, 2.422878540511741E-20, 2.451363930101321E-20,
            2.4800741664897764E-20, 2.5090119710298442E-20, 2.5381801309347597E-20, 2.56758150135705E-20,
            2.5972190075566336E-20, 2.6270956471628253E-20, 2.6572144925351523E-20, 2.687578693228184E-20,
            2.718191478565915E-20, 2.7490561603315974E-20, 2.7801761355793055E-20, 2.811554889573917E-20,
            2.8431959988666534E-20, 2.8751031345137833E-20, 2.907280065446631E-20, 2.9397306620015486E-20,
            2.9724588996191657E-20, 3.005468862722811E-20, 3.038764748786764E-20, 3.072350872605708E-20,
            3.1062316707775905E-20, 3.140411706412999E-20, 3.174895674085097E-20, 3.2096884050352357E-20,
            3.2447948726504914E-20, 3.280220198230601E-20, 3.315969657063137E-20, 3.352048684827223E-20,
            3.388462884347689E-20, 3.4252180327233346E-20, 3.4623200888548644E-20, 3.4997752014001677E-20,
            3.537589717186906E-20, 3.5757701901149035E-20, 3.61432339058358E-20, 3.65325631548274E-20,
            3.692576198788357E-20, 3.732290522808698E-20, 3.7724070301302117E-20, 3.812933736317104E-20,
            3.8538789434235234E-20, 3.895251254382786E-20, 3.93705958834424E-20, 3.979313197035144E-20,
            4.022021682232577E-20, 4.0651950144388133E-20, 4.1088435528630944E-20, 4.152978066823271E-20,
            4.197609758692658E-20, 4.242750288530745E-20, 4.2884118005513604E-20, 4.334606951598745E-20,
            4.381348941821026E-20, 4.428651547752084E-20, 4.476529158037235E-20, 4.5249968120658306E-20,
            4.574070241805442E-20, 4.6237659171683015E-20, 4.674101095281837E-20, 4.7250938740823415E-20,
            4.776763250705122E-20, 4.8291291852069895E-20, 4.8822126702292804E-20, 4.936035807293385E-20,
            4.990621890518202E-20, 5.045995498662554E-20, 5.1021825965285324E-20, 5.159210646917826E-20,
            5.2171087345169234E-20, 5.2759077033045284E-20, 5.335640309332586E-20, 5.396341391039951E-20,
            5.458048059625925E-20, 5.520799912453558E-20, 5.584639272987383E-20, 5.649611461419377E-20,
            5.715765100929071E-20, 5.783152465495663E-20, 5.851829876379432E-20, 5.921858155879171E-20,
            5.99330314883387E-20, 6.066236324679689E-20, 6.1407354758435E-20, 6.216885532049976E-20,
            6.294779515010373E-20, 6.37451966432144E-20, 6.456218773753799E-20, 6.54000178818891E-20,
            6.626007726330934E-20, 6.714392014514662E-20, 6.80532934473017E-20, 6.8990172088133E-20,
            6.99568031585645E-20, 7.095576179487843E-20, 7.199002278894508E-20, 7.306305373910546E-20,
            7.417893826626688E-20, 7.534254213417312E-20, 7.655974217114297E-20, 7.783774986341285E-20,
            7.918558267402951E-20, 8.06147755373533E-20, 8.214050276981807E-20, 8.378344597828052E-20,
            8.557312924967816E-20, 8.75544596695901E-20, 8.980238805770688E-20, 9.246247142115109E-20,
            9.591964134495172E-20, 1.0842021724855044E-19};

        /** Underlying source of randomness. */
        protected final UniformRandomProvider rng;

        /**
         * @param rng Generator of uniformly distributed random numbers.
         */
        ModifiedZigguratExponentialSamplerIntMap(UniformRandomProvider rng) {
            this.rng = rng;
        }

        /** {@inheritDoc} */
        @Override
        public double sample() {
            final long x = rng.nextLong();
            // Float multiplication squashes these last 8 bits, so they can be used to sample i
            final int i = ((int) x) & 0xff;

            if (i < I_MAX) {
                // Early exit.
                // This branch is called about 0.984374 times per call into createSample.
                // Note: Frequencies have been empirically measured for the first call to
                // createSample; recursion due to retries have been ignored. Frequencies sum to 1.
                return X[i] * (x & MAX_INT64);
            }
            // For the first call into createSample:
            // Recursion frequency = 0.000515560
            // Overhang frequency  = 0.0151109
            final int j = expSampleA();
            return j == 0 ? X_0 + sample() : expOverhang(j);
        }

        /**
         * Alias sampling.
         * See http://scorevoting.net/WarrenSmithPages/homepage/sampling.abs
         *
         * @return the alias
         */
        protected int expSampleA() {
            final long x = rng.nextLong();
            // j <- I(0, 256)
            final int j = ((int) x) & 0xff;
            return x >= IPMF[j] ? MAP[j] : j;
        }

        /**
         * Draws a PRN from overhang.
         *
         * @param j Index j (must be {@code > 0})
         * @return the sample
         */
        protected double expOverhang(int j) {
            // To sample a unit right-triangle:
            // U_x <- min(U_1, U_2)
            // distance <- | U_1 - U_2 |
            // U_y <- 1 - (U_x + distance)
            long ux = randomInt63();
            long uDistance = randomInt63() - ux;
            if (uDistance < 0) {
                uDistance = -uDistance;
                ux -= uDistance;
            }
            // _FAST_PRNG_SAMPLE_X(xj, ux)
            final double x = fastPrngSampleX(j, ux);
            if (uDistance >= IE_MAX) {
                // Frequency (per call into createSample): 0.0136732
                // Frequency (per call into expOverhang):  0.904857
                // Early Exit: x < y - epsilon
                return x;
            }
            // Frequency per call into createSample:
            // Return    = 0.00143769
            // Recursion = 1e-8
            // Frequency per call into expOverhang:
            // Return    = 0.0951426
            // Recursion = 6.61774e-07

            // _FAST_PRNG_SAMPLE_Y(j, pow(2, 63) - (ux + uDistance))
            // Long.MIN_VALUE is used as an unsigned int with value 2^63:
            // uy = Long.MIN_VALUE - (ux + uDistance)
            return fastPrngSampleY(j, Long.MIN_VALUE - (ux + uDistance)) <= Math.exp(-x) ? x : expOverhang(j);
        }

        /**
         * Return a positive long in {@code [0, 2^63)}.
         *
         * @return the long
         */
        protected long randomInt63() {
            return rng.nextLong() & MAX_INT64;
        }

        /**
         * Auxilary function to see if rejection sampling is required in the overhang.
         * See Fig. 2 in the main text.
         *
         * @param j j
         * @param ux ux
         * @return the sample
         */
        protected static double fastPrngSampleX(int j, long ux) {
            return X[j] * TWO_POW_63 + (X[j - 1] - X[j]) * ux;
        }

        /**
         * Auxilary function to see if rejection sampling is required in the overhang.
         * See Fig. 2 in the main text.
         *
         * @param i i
         * @param uy uy
         * @return the sample
         */
        static double fastPrngSampleY(int i, long uy) {
            return Y[i - 1] * TWO_POW_63 + (Y[i] - Y[i - 1]) * uy;
        }
    }

    /**
     * Throw an illegal state exception for the unknown parameter.
     *
     * @param parameter Parameter name
     */
    private static void throwIllegalStateException(String parameter) {
        throw new IllegalStateException("Unknown: " + parameter);
    }

    /**
     * Baseline for the JMH timing overhead for production of an {@code double} value.
     *
     * @return the {@code double} value
     */
    @Benchmark
    public double baseline() {
        return value;
    }

    /**
     * Benchmark methods for obtaining an index from the lower bits of a long.
     *
     * <p>Note: This is disabled as there is no measurable difference between methods.
     *
     * @param sources Source of randomness.
     * @return the sample value
     */
    //@Benchmark
    public int getIndex(IndexSources sources) {
        return sources.getSampler().sample();
    }

    /**
     * Benchmark methods for obtaining an unsigned long.
     *
     * <p>Note: This is disabled as there is no measurable difference between methods.
     *
     * @param sources Source of randomness.
     * @return the sample value
     */
    //@Benchmark
    public long getUnsignedLong(LongSources sources) {
        return sources.getSampler().sample();
    }

    /**
     * Run the sampler.
     *
     * @param sources Source of randomness.
     * @return the sample value
     */
    @Benchmark
    public double sample(Sources sources) {
        return sources.getSampler().sample();
    }

    /**
     * Run the sampler to generate a number of samples sequentially.
     *
     * @param sources Source of randomness.
     * @return the sample value
     */
    @Benchmark
    public double sequentialSample(SequentialSources sources) {
        return sources.getSampler().sample();
    }
}
