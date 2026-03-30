package com.example.safesphere;

import java.util.ArrayList;
import java.util.List;

/**
 * NP-complete formulation module for emergency action selection.
 *
 * We model optional emergency actions as 0/1 knapsack items and select
 * the best subset under a battery-derived budget when battery is low.
 */
public final class EmergencyActionOptimizer {

    public static final int LOW_BATTERY_THRESHOLD_PERCENT = 20;

    private EmergencyActionOptimizer() {
    }

    private enum ActionType {
        CALL_CONTACT_1,
        CALL_CONTACT_2,
        CALL_CONTACT_3,
        LIVE_LOCATION
    }

    private static final class ActionItem {
        final ActionType type;
        final int cost;
        final int value;

        ActionItem(ActionType type, int cost, int value) {
            this.type = type;
            this.cost = cost;
            this.value = value;
        }
    }

    public static final class OptimizationResult {
        public final boolean optimizationApplied;
        public final int batteryPercent;
        public final String[] callNumbers;
        public final boolean liveLocationEnabled;
        public final String debugSummary;

        OptimizationResult(boolean optimizationApplied,
                           int batteryPercent,
                           String[] callNumbers,
                           boolean liveLocationEnabled,
                           String debugSummary) {
            this.optimizationApplied = optimizationApplied;
            this.batteryPercent = batteryPercent;
            this.callNumbers = callNumbers;
            this.liveLocationEnabled = liveLocationEnabled;
            this.debugSummary = debugSummary;
        }
    }

    public static OptimizationResult planForEmergency(String[] allNumbers,
                                                      int batteryPercent,
                                                      boolean liveModeRequested) {
        String[] safeNumbers = allNumbers != null ? allNumbers : new String[0];

        if (batteryPercent > LOW_BATTERY_THRESHOLD_PERCENT) {
            return new OptimizationResult(
                    false,
                    batteryPercent,
                    safeNumbers,
                    liveModeRequested,
                    "Battery high, optimizer bypassed");
        }

        int budget = batteryBudgetForPercent(batteryPercent);
        List<ActionItem> items = new ArrayList<>();
        List<Integer> contactIndexForItem = new ArrayList<>();

        addCallItemIfPresent(items, contactIndexForItem, safeNumbers, 0, ActionType.CALL_CONTACT_1, 2, 10);
        addCallItemIfPresent(items, contactIndexForItem, safeNumbers, 1, ActionType.CALL_CONTACT_2, 2, 8);
        addCallItemIfPresent(items, contactIndexForItem, safeNumbers, 2, ActionType.CALL_CONTACT_3, 2, 6);

        int liveLocationItemIndex = -1;
        if (liveModeRequested) {
            liveLocationItemIndex = items.size();
            items.add(new ActionItem(ActionType.LIVE_LOCATION, 1, 3));
            contactIndexForItem.add(-1);
        }

        boolean[] picked = solveKnapsack(items, budget);

        List<String> selectedCalls = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            if (!picked[i]) {
                continue;
            }
            int contactIndex = contactIndexForItem.get(i);
            if (contactIndex >= 0 && contactIndex < safeNumbers.length) {
                selectedCalls.add(safeNumbers[contactIndex]);
            }
        }

        if (selectedCalls.isEmpty()) {
            String first = firstValidNumber(safeNumbers);
            if (first != null) {
                selectedCalls.add(first);
            }
        }

        boolean liveEnabled = liveLocationItemIndex >= 0
                && liveLocationItemIndex < picked.length
                && picked[liveLocationItemIndex];

        return new OptimizationResult(
                true,
                batteryPercent,
                selectedCalls.toArray(new String[0]),
                liveEnabled,
                "Battery low, optimizer applied with budget=" + budget + ", callsSelected=" + selectedCalls.size());
    }

    private static void addCallItemIfPresent(List<ActionItem> items,
                                             List<Integer> contactIndexForItem,
                                             String[] numbers,
                                             int index,
                                             ActionType type,
                                             int cost,
                                             int value) {
        if (index < 0 || index >= numbers.length) {
            return;
        }
        String n = numbers[index];
        if (n == null || n.trim().isEmpty()) {
            return;
        }
        items.add(new ActionItem(type, cost, value));
        contactIndexForItem.add(index);
    }

    private static int batteryBudgetForPercent(int batteryPercent) {
        if (batteryPercent <= 5) {
            return 2;
        }
        if (batteryPercent <= 10) {
            return 3;
        }
        if (batteryPercent <= 15) {
            return 4;
        }
        return 5;
    }

    private static boolean[] solveKnapsack(List<ActionItem> items, int budget) {
        int n = items.size();
        int[][] dp = new int[n + 1][budget + 1];

        for (int i = 1; i <= n; i++) {
            ActionItem item = items.get(i - 1);
            for (int w = 0; w <= budget; w++) {
                dp[i][w] = dp[i - 1][w];
                if (item.cost <= w) {
                    int withItem = dp[i - 1][w - item.cost] + item.value;
                    if (withItem > dp[i][w]) {
                        dp[i][w] = withItem;
                    }
                }
            }
        }

        boolean[] picked = new boolean[n];
        int w = budget;
        for (int i = n; i >= 1; i--) {
            if (dp[i][w] != dp[i - 1][w]) {
                picked[i - 1] = true;
                w -= items.get(i - 1).cost;
                if (w <= 0) {
                    break;
                }
            }
        }

        return picked;
    }

    private static String firstValidNumber(String[] numbers) {
        for (String number : numbers) {
            if (number != null && !number.trim().isEmpty()) {
                return number.trim();
            }
        }
        return null;
    }
}
