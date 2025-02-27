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

package org.apache.commons.rng.examples.sampling;

import java.io.PrintWriter;
import java.util.EnumSet;
import java.util.concurrent.Callable;
import java.io.IOException;
import org.apache.commons.rng.UniformRandomProvider;
import org.apache.commons.rng.simple.RandomSource;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;

import org.apache.commons.rng.sampling.distribution.ZigguratNormalizedGaussianSampler;
import org.apache.commons.rng.sampling.distribution.ZigguratSampler;
import org.apache.commons.rng.sampling.distribution.MarsagliaNormalizedGaussianSampler;
import org.apache.commons.rng.sampling.distribution.StableSampler;
import org.apache.commons.rng.sampling.distribution.BoxMullerNormalizedGaussianSampler;
import org.apache.commons.rng.sampling.distribution.ChengBetaSampler;
import org.apache.commons.rng.sampling.distribution.AhrensDieterExponentialSampler;
import org.apache.commons.rng.sampling.distribution.AhrensDieterMarsagliaTsangGammaSampler;
import org.apache.commons.rng.sampling.distribution.InverseTransformParetoSampler;
import org.apache.commons.rng.sampling.distribution.LevySampler;
import org.apache.commons.rng.sampling.distribution.LogNormalSampler;
import org.apache.commons.rng.sampling.distribution.ContinuousUniformSampler;
import org.apache.commons.rng.sampling.distribution.GaussianSampler;
import org.apache.commons.rng.sampling.distribution.ContinuousSampler;

/**
 * Approximation of the probability density by the histogram of the sampler output.
 */
@Command(name = "density",
         description = {"Approximate the probability density of samplers."})
class ProbabilityDensityApproximationCommand  implements Callable<Void> {
    /** The standard options. */
    @Mixin
    private StandardOptions reusableOptions;

    /** Number of (equal-width) bins in the histogram. */
    @Option(names = {"-b", "--bins"},
            description = "The number of bins in the histogram (default: ${DEFAULT-VALUE}).")
    private int numBins = 25_000;

    /** Number of samples to be generated. */
    @Option(names = {"-n", "--samples"},
            description = "The number of samples in the histogram (default: ${DEFAULT-VALUE}).")
    private long numSamples = 1_000_000_000;

    /** The samplers. */
    @Option(names = {"-s", "--samplers"},
            split = ",",
            description = {"The samplers (comma-delimited for multiple options).",
                           "Valid values: ${COMPLETION-CANDIDATES}."})
    private EnumSet<Sampler> samplers = EnumSet.noneOf(Sampler.class);

    /** The samplers. */
    @Option(names = {"-r", "--rng"},
            description = {"The source of randomness (default: ${DEFAULT-VALUE})."})
    private RandomSource randomSource = RandomSource.XOR_SHIFT_1024_S_PHI;

    /** Flag to output all samplers. */
    @Option(names = {"-a", "--all"},
            description = "Output all samplers")
    private boolean allSamplers;

    /**
     * The sampler. This enum uses lower case for clarity when matching the distribution name.
     */
    enum Sampler {
        /** The ziggurat gaussian sampler. */
        ZigguratGaussianSampler,
        /** The Marsaglia gaussian sampler. */
        MarsagliaGaussianSampler,
        /** The Box Muller gaussian sampler. */
        BoxMullerGaussianSampler,
        /** The modified ziggurat gaussian sampler. */
        ModifiedZigguratGaussianSampler,
        /** The Cheng beta sampler case 1. */
        ChengBetaSamplerCase1,
        /** The Cheng beta sampler case 2. */
        ChengBetaSamplerCase2,
        /** The Ahrens Dieter exponential sampler. */
        AhrensDieterExponentialSampler,
        /** The modified ziggurat exponential sampler. */
        ModifiedZigguratExponentialSampler,
        /** The Ahrens Dieter Marsaglia Tsang gamma sampler small gamma. */
        AhrensDieterMarsagliaTsangGammaSamplerCase1,
        /** The Ahrens Dieter Marsaglia Tsang gamma sampler large gamma. */
        AhrensDieterMarsagliaTsangGammaSamplerCase2,
        /** The inverse transform pareto sampler. */
        InverseTransformParetoSampler,
        /** The continuous uniform sampler. */
        ContinuousUniformSampler,
        /** The log normal ziggurat gaussian sampler. */
        LogNormalZigguratGaussianSampler,
        /** The log normal Marsaglia gaussian sampler. */
        LogNormalMarsagliaGaussianSampler,
        /** The log normal Box Muller gaussian sampler. */
        LogNormalBoxMullerGaussianSampler,
        /** The log normal modified ziggurat gaussian sampler. */
        LogNormalModifiedZigguratGaussianSampler,
        /** The Levy sampler. */
        LevySampler,
        /** The stable sampler. */
        StableSampler,
    }

    /**
     * @param sampler Sampler.
     * @param min Right abscissa of the first bin: every sample smaller
     * than that value will increment an additional bin (of infinite width)
     * placed before the first "equal-width" bin.
     * @param max abscissa of the last bin: every sample larger than or
     * equal to that value will increment an additional bin (of infinite
     * width) placed after the last "equal-width" bin.
     * @param outputFile Filename (final name is "pdf.[filename].txt").
     * @throws IOException Signals that an I/O exception has occurred.
     */
    private void createDensity(ContinuousSampler sampler,
                               double min,
                               double max,
                               String outputFile)
        throws IOException {
        final double binSize = (max - min) / numBins;
        final long[] histogram = new long[numBins];

        long belowMin = 0;
        long aboveMax = 0;
        for (long n = 0; n < numSamples; n++) {
            final double r = sampler.sample();

            if (r < min) {
                ++belowMin;
                continue;
            }

            if (r >= max) {
                ++aboveMax;
                continue;
            }

            final int binIndex = (int) ((r - min) / binSize);
            ++histogram[binIndex];
        }

        final double binHalfSize = 0.5 * binSize;
        final double norm = 1 / (binSize * numSamples);

        try (PrintWriter out = new PrintWriter("pdf." + outputFile + ".txt", "UTF-8")) {
            // CHECKSTYLE: stop MultipleStringLiteralsCheck
            out.println("# Sampler: " + sampler);
            out.println("# Number of bins: " + numBins);
            out.println("# Min: " + min + " (fraction of samples below: " + (belowMin / (double) numSamples) + ")");
            out.println("# Max: " + max + " (fraction of samples above: " + (aboveMax / (double) numSamples) + ")");
            out.println("# Bin width: " + binSize);
            out.println("# Histogram normalization factor: " + norm);
            out.println("#");
            out.println("# " + (min - binHalfSize) + " " + (belowMin * norm));
            for (int i = 0; i < numBins; i++) {
                out.println((min + (i + 1) * binSize - binHalfSize) + " " + (histogram[i] * norm));
            }
            out.println("# " + (max + binHalfSize) + " " + (aboveMax * norm));
            // CHECKSTYLE: resume MultipleStringLiteralsCheck
        }
    }

    /**
     * Program entry point.
     *
     * @throws IOException if failure occurred while writing to files.
     */
    @Override
    public Void call() throws IOException {
        if (allSamplers) {
            samplers = EnumSet.allOf(Sampler.class);
        } else if (samplers.isEmpty()) {
            // CHECKSTYLE: stop regexp
            System.err.println("ERROR: No samplers specified");
            // CHECKSTYLE: resume regexp
            System.exit(1);
        }

        final UniformRandomProvider rng = randomSource.create();

        final double gaussMean = 1;
        final double gaussSigma = 2;
        final double gaussMin = -9;
        final double gaussMax = 11;
        if (samplers.contains(Sampler.ZigguratGaussianSampler)) {
            createDensity(GaussianSampler.of(ZigguratNormalizedGaussianSampler.of(rng),
                                             gaussMean, gaussSigma),
                          gaussMin, gaussMax, "gauss.ziggurat");
        }
        if (samplers.contains(Sampler.MarsagliaGaussianSampler)) {
            createDensity(GaussianSampler.of(MarsagliaNormalizedGaussianSampler.of(rng),
                                             gaussMean, gaussSigma),
                          gaussMin, gaussMax, "gauss.marsaglia");
        }
        if (samplers.contains(Sampler.BoxMullerGaussianSampler)) {
            createDensity(GaussianSampler.of(BoxMullerNormalizedGaussianSampler.of(rng),
                                             gaussMean, gaussSigma),
                          gaussMin, gaussMax, "gauss.boxmuller");
        }
        if (samplers.contains(Sampler.ModifiedZigguratGaussianSampler)) {
            createDensity(GaussianSampler.of(ZigguratSampler.NormalizedGaussian.of(rng),
                                             gaussMean, gaussSigma),
                          gaussMin, gaussMax, "gauss.modified.ziggurat");
        }

        final double betaMin = 0;
        final double betaMax = 1;
        if (samplers.contains(Sampler.ChengBetaSamplerCase1)) {
            final double alphaBeta = 4.3;
            final double betaBeta = 2.1;
            createDensity(ChengBetaSampler.of(rng, alphaBeta, betaBeta),
                          betaMin, betaMax, "beta.case1");
        }
        if (samplers.contains(Sampler.ChengBetaSamplerCase2)) {
            final double alphaBetaAlt = 0.5678;
            final double betaBetaAlt = 0.1234;
            createDensity(ChengBetaSampler.of(rng, alphaBetaAlt, betaBetaAlt),
                          betaMin, betaMax, "beta.case2");
        }

        final double meanExp = 3.45;
        final double expMin = 0;
        final double expMax = 60;
        if (samplers.contains(Sampler.AhrensDieterExponentialSampler)) {
            createDensity(AhrensDieterExponentialSampler.of(rng, meanExp),
                          expMin, expMax, "exp");
        }
        if (samplers.contains(Sampler.ModifiedZigguratExponentialSampler)) {
            createDensity(ZigguratSampler.Exponential.of(rng, meanExp),
                          expMin, expMax, "exp.modified.ziggurat");
        }

        final double gammaMin = 0;
        final double gammaMax1 = 40;
        final double thetaGamma = 3.456;
        if (samplers.contains(Sampler.AhrensDieterMarsagliaTsangGammaSamplerCase1)) {
            final double alphaGammaSmallerThanOne = 0.1234;
            createDensity(AhrensDieterMarsagliaTsangGammaSampler.of(rng, alphaGammaSmallerThanOne, thetaGamma),
                          gammaMin, gammaMax1, "gamma.case1");
        }
        if (samplers.contains(Sampler.AhrensDieterMarsagliaTsangGammaSamplerCase2)) {
            final double alphaGammaLargerThanOne = 2.345;
            final double gammaMax2 = 70;
            createDensity(AhrensDieterMarsagliaTsangGammaSampler.of(rng, alphaGammaLargerThanOne, thetaGamma),
                          gammaMin, gammaMax2, "gamma.case2");
        }

        final double scalePareto = 23.45;
        final double shapePareto = 0.789;
        final double paretoMin = 23;
        final double paretoMax = 400;
        if (samplers.contains(Sampler.InverseTransformParetoSampler)) {
            createDensity(InverseTransformParetoSampler.of(rng, scalePareto, shapePareto),
                          paretoMin, paretoMax, "pareto");
        }

        final double loUniform = -9.876;
        final double hiUniform = 5.432;
        if (samplers.contains(Sampler.ContinuousUniformSampler)) {
            createDensity(ContinuousUniformSampler.of(rng, loUniform, hiUniform),
                          loUniform, hiUniform, "uniform");
        }

        final double scaleLogNormal = 2.345;
        final double shapeLogNormal = 0.1234;
        final double logNormalMin = 5;
        final double logNormalMax = 25;
        if (samplers.contains(Sampler.LogNormalZigguratGaussianSampler)) {
            createDensity(LogNormalSampler.of(ZigguratNormalizedGaussianSampler.of(rng),
                                              scaleLogNormal, shapeLogNormal),
                          logNormalMin, logNormalMax, "lognormal.ziggurat");
        }
        if (samplers.contains(Sampler.LogNormalMarsagliaGaussianSampler)) {
            createDensity(LogNormalSampler.of(MarsagliaNormalizedGaussianSampler.of(rng),
                                              scaleLogNormal, shapeLogNormal),
                          logNormalMin, logNormalMax, "lognormal.marsaglia");
        }
        if (samplers.contains(Sampler.LogNormalBoxMullerGaussianSampler)) {
            createDensity(LogNormalSampler.of(BoxMullerNormalizedGaussianSampler.of(rng),
                                              scaleLogNormal, shapeLogNormal),
                          logNormalMin, logNormalMax, "lognormal.boxmuller");
        }
        if (samplers.contains(Sampler.LogNormalModifiedZigguratGaussianSampler)) {
            createDensity(LogNormalSampler.of(ZigguratSampler.NormalizedGaussian.of(rng),
                                              scaleLogNormal, shapeLogNormal),
                          logNormalMin, logNormalMax, "lognormal.modified.ziggurat");
        }

        if (samplers.contains(Sampler.LevySampler)) {
            final double levyLocation = 1.23;
            final double levyscale = 0.75;
            final double levyMin = levyLocation;
            // Quantile 0 to 0.7 (avoid long tail to infinity)
            final double levyMax = 6.2815;
            createDensity(LevySampler.of(rng, levyLocation, levyscale),
                          levyMin, levyMax, "levy");
        }

        if (samplers.contains(Sampler.StableSampler)) {
            final double stableAlpha = 1.23;
            final double stableBeta = 0.75;
            // Quantiles 0.05 to 0.9 (avoid long tail to infinity)
            final double stableMin = -1.7862;
            final double stableMax = 4.0364;
            createDensity(StableSampler.of(rng, stableAlpha, stableBeta),
                          stableMin, stableMax, "stable");
        }

        return null;
    }
}
