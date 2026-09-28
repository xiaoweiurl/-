package com.imagemanager.imagesearch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recall@K：每个查询取 |命中 ∩ 相关| / |相关|，再对查询取平均。
 * Hit@K：相关结果至少出现一个的查询比例。
 */
public final class ImageSearchRecall {

    private ImageSearchRecall() {
    }

    public static double meanRecall(List<List<String>> relevant, List<List<String>> retrieved, int k) {
        if (relevant == null || relevant.isEmpty()) {
            return 0d;
        }
        double sum = 0d;
        int counted = 0;
        for (int i = 0; i < relevant.size(); i++) {
            List<String> truth = relevant.get(i);
            if (truth == null || truth.isEmpty()) {
                continue;
            }
            List<String> top = top(retrieved, i, k);
            Set<String> hit = new HashSet<>(top);
            int matched = 0;
            for (String id : truth) {
                if (hit.contains(id)) {
                    matched++;
                }
            }
            sum += (double) matched / truth.size();
            counted++;
        }
        return counted == 0 ? 0d : sum / counted;
    }

    public static double hitRate(List<List<String>> relevant, List<List<String>> retrieved, int k) {
        if (relevant == null || relevant.isEmpty()) {
            return 0d;
        }
        int hits = 0;
        int counted = 0;
        for (int i = 0; i < relevant.size(); i++) {
            List<String> truth = relevant.get(i);
            if (truth == null || truth.isEmpty()) {
                continue;
            }
            Set<String> top = new HashSet<>(top(retrieved, i, k));
            counted++;
            for (String id : truth) {
                if (top.contains(id)) {
                    hits++;
                    break;
                }
            }
        }
        return counted == 0 ? 0d : (double) hits / counted;
    }

    public static long percentile(List<Long> samples, int percent) {
        if (samples == null || samples.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = new ArrayList<>(samples);
        sorted.sort(Long::compareTo);
        int index = (int) Math.ceil(percent / 100d * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private static List<String> top(List<List<String>> retrieved, int index, int k) {
        if (retrieved == null || index >= retrieved.size() || retrieved.get(index) == null) {
            return List.of();
        }
        List<String> row = retrieved.get(index);
        if (row.size() <= k) {
            return row;
        }
        return row.subList(0, k);
    }
}
