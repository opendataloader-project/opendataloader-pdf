package org.opendataloader.pdf.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModeWeightStatistics {
    private final Map<Double, Long> countMap = new HashMap<>();
    private List<Map.Entry<Double, Long>> sorted = new ArrayList<>();

    public void addScore(double score) {
        countMap.merge(score, 1L, Long::sum);
    }

    public void sortByFrequency() {
        sorted = new ArrayList<>(countMap.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
    }

    public double getMode(double modeMin, double modeMax) {
        if (sorted.isEmpty()) {
            sortByFrequency();
        }
        for (Map.Entry<Double, Long> entry : sorted) {
            double value = entry.getKey();
            if (value >= modeMin && value <= modeMax) {
                return value;
            }
        }
        return 0.0;
    }

    public double getBoost(double score, double scoreMax, double modeMin, double modeMax) {
        if (sorted.isEmpty()) {
            sortByFrequency();
        }
        double mode = getMode(modeMin, modeMax);
        if (score <= mode || score > scoreMax) {
            return 0.0;
        }
        List<Double> higherScores = sorted.stream()
            .map(Map.Entry::getKey)
            .filter(s -> s > mode && s <= scoreMax)
            .sorted()
            .collect(Collectors.toList());
        int n = higherScores.size();
        if (n == 0) {
            return 0.0;
        }
        for (int i = 0; i < n; i++) {
            if (Double.compare(higherScores.get(i), score) == 0) {
                return (double) (i + 1) / n;
            }
        }
        return 0.0;
    }
}
