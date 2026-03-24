# Test Matrix

## Benchmark Assets

- `baseline.html` -> `updated.html`
  - original hard-drift benchmark
- `baseline.html` -> `updated_variant.html`
  - reordered and relabeled stress benchmark
- `baseline_hybrid.html` -> `updated_hybrid.html`
  - mixed recovery-mode benchmark
- `baseline_refusal.html` -> `updated_refusal.html`
  - refusal benchmark where no comparable control remains
- `semantic_signals.html`
  - focused semantic extraction and locator fixture

The machine-readable matrix lives in [`benchmark-matrix.json`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/docs/benchmark-matrix.json).

## Scenario Matrix

| Scenario | Baseline | Updated | Control | Change Type | Direct | Healenium | Xealenium | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `matrix-updated-main` | `baseline.html` | `updated.html` | full form | labels, wrappers, custom widgets | fail | partial | pass | Original benchmark where Xealenium heals hard DOM drift end to end. |
| `matrix-updated-variant` | `baseline.html` | `updated_variant.html` | full form | reordered fields, label changes, structure changes | fail | partial | pass | Stress scenario for reordered text fields and semantic relabeling. |
| `hybrid-direct` | `baseline_hybrid.html` | `updated_hybrid.html` | `fname,email,lname,newsletter,zip,terms,submit-btn` | unchanged or intentionally stable | pass | not-needed | not-needed | Controls that still work directly. |
| `hybrid-healenium-phone` | `baseline_hybrid.html` | `updated_hybrid.html` | `phone` | soft locator drift | fail | pass | pass | Soft drift intended to be recovered by Healenium before Xealenium fallback is needed. |
| `hybrid-healenium-country` | `baseline_hybrid.html` | `updated_hybrid.html` | `country` | soft locator drift | fail | pass | pass | Moderate drift on a select-like control. |
| `hybrid-visual-city` | `baseline_hybrid.html` | `updated_hybrid.html` | `city` | hard semantic and structural drift | fail | fail | pass | Hard case that requires visual plus semantic recovery. |
| `refusal-no-comparable-control` | `baseline_refusal.html` | `updated_refusal.html` | `project-code` | target removed, no comparable text control remains | fail | fail | refuse | Proves Xealenium does not guess when no safe match exists. |

## Runnable Benchmark Suites

### `com.demo.benchmark.VisualPageMatrixTests`

Purpose:

- validates the main hard-change benchmark pages
- proves Xealenium heals both the primary updated page and the reordered variant

### `com.demo.benchmark.HybridRecoveryModeTests`

Purpose:

- proves the framework can distinguish recovery layers on one updated page
- shows direct Selenium success, Healenium recovery, and Xealenium-only recovery in one suite

### `com.demo.benchmark.RefusalBenchmarkTests`

Purpose:

- proves Xealenium refuses recovery when no comparable control remains

## Benchmark Summary Output

Benchmark runs emit:

1. a scenario count line
2. layer-focused summary counts
3. one line per scenario from the catalog

Example shape:

```text
[BENCHMARK] scenarios=7 directOnly=1 healeniumLayer=2 xealeniumOnly=1 xealeniumRefuse=1
```

Important note:

- those counts are intentionally layer-focused, not a full partition of all scenarios
- the full-form matrix scenarios are still listed explicitly right below the summary output
- the scenario table above is the authoritative product-claim matrix

## What The Matrix Proves

This benchmark suite is designed to make the product claim easy to inspect:

- direct Selenium still wins when the control is stable
- Healenium still adds value for moderate DOM drift
- Xealenium adds value when semantics survive but selector structure does not
- Xealenium should refuse when confidence is too low or the target meaning is gone

## Framework Unit Tests

### `com.visual.SmartLocatorBuilderTest`

- point-to-locator and element-to-locator generation
- uniqueness checks
- contenteditable handling

### `com.visual.SemanticSimilarityTest`

- lexical fallback scoring
- WordNet-backed similarity
- semantic scoring sanity checks for tricky labels

### `com.visual.EmbeddingFingerprintBuilderTest`

- richer embedding fingerprint construction

### `com.visual.embedding.LocalEmbeddingServiceTest`

- local ONNX loading
- enable and disable behavior
- config override behavior

### `com.visual.engine.HealingDecisionEngineTest`

- review-strategy behavior
- threshold versus auto-accept behavior

## Recommended Runs

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.benchmark.VisualPageMatrixTests"
```

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.benchmark.HybridRecoveryModeTests"
```

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.benchmark.RefusalBenchmarkTests"
```
