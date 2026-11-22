/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.utils;

/**
 * Configuration holder that exposes the scoring constants used by {@link TextNodeStatistics}.
 * The defaults mimic the legacy hard-coded probabilities but callers may override them
 * to tune heading detection without touching the algorithm code.
 */
public class TextNodeStatisticsConfig {
    public double fontSizeDominantMin = 10.0;
    public double fontSizeDominantMax = 13.0;
    public double fontSizeHeadingMin = 10.0;
    public double fontSizeHeadingMax = 32.0;
    public double fontSizeRarityBoost = 0.5;

    public double fontWeightDominantMin = 395.0;
    public double fontWeightDominantMax = 405.0;
    public double fontWeightHeadingMin = 400.0;
    public double fontWeightHeadingMax = 900.0;
    public double fontWeightRarityBoost = 0.3;
}
