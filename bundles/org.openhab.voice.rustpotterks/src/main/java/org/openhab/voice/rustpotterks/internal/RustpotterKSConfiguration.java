/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.voice.rustpotterks.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link RustpotterKSConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Miguel Álvarez - Initial contribution
 */
@NonNullByDefault
public class RustpotterKSConfiguration {
    /**
     * Configures the detector threshold, is the min score (in range 0. to 1.) that some wake word template should
     * obtain to trigger a detection. Defaults to 0.5.
     */
    public float threshold = 0.5f;
    /**
     * Configures the detector averaged threshold, is the min score (in range 0. to 1.) that the audio should obtain
     * against a
     * combination of the wake word templates, the detection will be aborted if this is not the case. This way it can
     * prevent to
     * run the comparison of the current frame against each of the wake word templates which saves cpu.
     * If set to 0 this functionality is disabled.
     */
    public float averagedThreshold = 0.2f;
    /**
     * Indicates how to calculate the final score.
     */
    public String scoreMode = "max";
    /**
     * Minimum number of positive scores to consider a partial detection as a detection.
     */
    public int minScores = 5;
    /**
     * Configures the reference for the comparator used to match the samples.
     */
    public float comparatorRef = 0.22f;
    /**
     * Configures the band-size for the comparator used to match the samples.
     */
    public int comparatorBandSize = 5;
    /**
     * Enables an audio filter that intent to approximate the volume of the stream to a reference level (RMS of the
     * samples is used as volume measure).
     */
    public boolean gainNormalizer = false;
    /**
     * Min gain applied by the gain normalizer filter.
     */
    public float minGain = 0.5f;
    /**
     * Max gain applied by the gain normalizer filter.
     */
    public float maxGain = 1f;
    /**
     * Set the RMS reference used by the gain-normalizer to calculate the gain applied. If unset an estimation of the
     * wakeword level is used.
     */
    public @Nullable Float gainRef = null;
    /**
     * Enables an audio filter that attenuates frequencies outside the low cutoff and high cutoff range.
     */
    public boolean bandPass = false;
    /**
     * Low cutoff for the band-pass filter.
     */
    public float lowCutoff = 80f;
    /**
     * High cutoff for the band-pass filter.
     */
    public float highCutoff = 400f;
}
