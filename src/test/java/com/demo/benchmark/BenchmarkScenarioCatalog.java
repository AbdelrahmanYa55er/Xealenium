package com.demo.benchmark;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class BenchmarkScenarioCatalog {
    private static final AtomicBoolean PRINTED = new AtomicBoolean(false);

    static final List<BenchmarkScenario> SCENARIOS = List.of(
        new BenchmarkScenario(
            "matrix-updated-main",
            "baseline.html",
            "updated.html",
            "full form",
            "labels, wrappers, custom widgets",
            "fail",
            "partial",
            "pass",
            "Original demo benchmark where Xealenium heals the hard DOM drift end to end."
        ),
        new BenchmarkScenario(
            "matrix-updated-variant",
            "baseline.html",
            "updated_variant.html",
            "full form",
            "reordered fields, label changes, structure changes",
            "fail",
            "partial",
            "pass",
            "Stress scenario for reordered text fields and semantic relabeling."
        ),
        new BenchmarkScenario(
            "hybrid-direct",
            "baseline_hybrid.html",
            "updated_hybrid.html",
            "fname,email,lname,newsletter,zip,terms,submit-btn",
            "unchanged or intentionally stable",
            "pass",
            "not-needed",
            "not-needed",
            "Controls that still work through direct Selenium lookup."
        ),
        new BenchmarkScenario(
            "hybrid-healenium-phone",
            "baseline_hybrid.html",
            "updated_hybrid.html",
            "phone",
            "soft locator drift",
            "fail",
            "pass",
            "pass",
            "Soft drift intended to be recovered by Healenium before visual healing is needed."
        ),
        new BenchmarkScenario(
            "hybrid-healenium-country",
            "baseline_hybrid.html",
            "updated_hybrid.html",
            "country",
            "soft locator drift",
            "fail",
            "pass",
            "pass",
            "Select control with moderate DOM drift."
        ),
        new BenchmarkScenario(
            "hybrid-visual-city",
            "baseline_hybrid.html",
            "updated_hybrid.html",
            "city",
            "hard semantic and structural drift",
            "fail",
            "fail",
            "pass",
            "Hard case that requires Xealenium visual + semantic recovery."
        ),
        new BenchmarkScenario(
            "refusal-no-comparable-control",
            "baseline_refusal.html",
            "updated_refusal.html",
            "project-code",
            "target removed, no comparable text control remains",
            "fail",
            "fail",
            "refuse",
            "Refusal benchmark proving Xealenium does not guess when the baseline control has no reasonable match."
        )
    );

    private BenchmarkScenarioCatalog() {
    }

    static void printSummaryOnce() {
        if (!PRINTED.compareAndSet(false, true)) {
            return;
        }
        long directPass = SCENARIOS.stream().filter(s -> "pass".equalsIgnoreCase(s.directResult())).count();
        long healeniumPass = SCENARIOS.stream()
            .filter(s -> "fail".equalsIgnoreCase(s.directResult()) && "pass".equalsIgnoreCase(s.healeniumResult()))
            .count();
        long xealeniumPass = SCENARIOS.stream()
            .filter(s -> "fail".equalsIgnoreCase(s.healeniumResult()) && "pass".equalsIgnoreCase(s.xealeniumResult()))
            .count();
        long refusals = SCENARIOS.stream().filter(s -> "refuse".equalsIgnoreCase(s.xealeniumResult())).count();
        System.out.println("[BENCHMARK] scenarios=" + SCENARIOS.size()
            + " directOnly=" + directPass
            + " healeniumLayer=" + healeniumPass
            + " xealeniumOnly=" + xealeniumPass
            + " xealeniumRefuse=" + refusals);
        for (BenchmarkScenario scenario : SCENARIOS) {
            System.out.println("[BENCHMARK] " + scenario.id()
                + " baseline=" + scenario.baselinePage()
                + " updated=" + scenario.updatedPage()
                + " control=" + scenario.control()
                + " change=" + scenario.changeType()
                + " direct=" + scenario.directResult()
                + " healenium=" + scenario.healeniumResult()
                + " xealenium=" + scenario.xealeniumResult());
        }
    }
}
