package com.demo.benchmark;

record BenchmarkScenario(
    String id,
    String baselinePage,
    String updatedPage,
    String control,
    String changeType,
    String directResult,
    String healeniumResult,
    String xealeniumResult,
    String notes
) {
}
