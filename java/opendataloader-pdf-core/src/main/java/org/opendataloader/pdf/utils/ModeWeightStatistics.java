package org.opendataloader.pdf.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ModeWeightStatistics {
    private final double scoreMax;
    private final double scoreMin;
    private final double modeMin;
    private final double modeMax;
    private final Map<Double, Long> countMap = new HashMap<>();
    private List<Map.Entry<Double, Long>> sorted = new ArrayList<>();
    private List<Double> higherScores = new ArrayList<>();
    private boolean isInitHigherScores = false;

    public ModeWeightStatistics(double scoreMin, double scoreMax, double modeMin, double modeMax) {
        this.scoreMin = scoreMin;
        this.scoreMax = scoreMax;
        this.modeMin = modeMin;
        this.modeMax = modeMax;
    }

    public void addScore(double score) {
        countMap.merge(score, 1L, Long::sum);
    }

    public double getBoost(double score) {
        initHigherScores();
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

    public void sortByFrequency() {
        sorted = new ArrayList<>(countMap.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
    }

    public double getMode() {
        for (Map.Entry<Double, Long> entry : sorted) {
            double value = entry.getKey();
            if (value >= modeMin && value <= modeMax) {
                return value;
            }
        }
        return 0.0;
    }

    private void initHigherScores() {
        if (isInitHigherScores) {
            return;
        }

        sortByFrequency();
        double mode = getMode();

        higherScores = sorted.stream()
            .map(Map.Entry::getKey)
            .filter(s -> s > mode && s >= scoreMin && s <= scoreMax)
            .sorted()
            .collect(Collectors.toList());

        isInitHigherScores = true;
    }
}
