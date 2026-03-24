# Xealenium

Xealenium is a Selenium recovery framework for cases where the DOM changed too much for a classic selector-healing approach to stay reliable.

It layers three recovery modes:

1. Native Selenium lookup
2. Healenium recovery
3. Visual recovery with semantic reranking and smart locator generation

## What It Does

Xealenium is no longer just a demo wrapper around screenshots. The current framework includes:

- page-scoped baseline storage
- stitched full-page heatmaps
- smart locator generation from healed elements or screen coordinates
- shared semantic extraction from DOM plus Chromium accessibility data
- optional local ONNX embeddings for open-world semantic matching
- human-in-the-loop review strategies
- benchmark suites that separate direct success, Healenium recovery, Xealenium-only recovery, and refusal behavior

## Current Scope

Xealenium is strongest today on form-like workflows:

- text inputs
- contenteditable fields
- checkboxes and toggle-like widgets
- selects and select-like wrappers
- action buttons and submit links

That is the most mature domain in the repo right now. It can work outside forms, but the benchmark and semantic assumptions are still most proven on forms and registration/profile-style pages.

## Why This Is Not Just Healenium

Healenium is best when the original locator shape is still mostly meaningful and the DOM drift is moderate.

Xealenium is the next layer for the harder cases where:

- ids disappeared
- tags changed
- wrappers changed heavily
- field order moved
- contenteditable widgets replaced inputs
- semantic meaning survived, but the old selector contract did not

In that phase, Xealenium stops trying to imitate the old selector and instead:

1. matches the baseline element against live candidates visually and semantically
2. chooses the best candidate above threshold
3. generates a new maintainable locator for that healed element

## Requirements

- Java 17+
- Chrome installed
- Windows PowerShell or `cmd` for the helper scripts

## Repository Layout

Framework code now lives in `src/main/java` and is split by responsibility:

- `com.visual.driver`
- `com.visual.engine`
- `com.visual.semantic`
- `com.visual.locator`
- `com.visual.baseline`
- `com.visual.embedding`
- `com.visual.report`
- `com.visual.image`
- `com.visual.model`
- `com.visual.config`

Benchmarks and demos live under `src/test/java`.

## Page Sets

Current HTML assets in [`pages`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/pages):

- `baseline.html` / `updated.html`
  - original hard-drift pair
- `baseline.html` / `updated_variant.html`
  - reordered and relabeled stress pair
- `baseline_hybrid.html` / `updated_hybrid.html`
  - mixed recovery-mode pair
- `baseline_refusal.html` / `updated_refusal.html`
  - refusal pair where no comparable control remains
- `semantic_signals.html`
  - focused semantic extraction and locator fixture

## Quick Start

### One-command runner

```powershell
.\run.bat
```

This:

1. rebuilds the baseline from `pages/baseline.html`
2. runs healing on `pages/updated.html`

If a local model exists at `models\gte-small-onnx`, the launcher enables embeddings automatically.

### Wizard runner

```powershell
.\run_wizard.bat
```

or:

```powershell
.\run_wizard.ps1
```

The wizard prompts for:

- interactive mode
- report generation
- baseline rebuild

### Local model bootstrap

The repo intentionally does not commit ONNX model binaries. Download the default local model into `models\gte-small-onnx` with:

```powershell
.\scripts\download-model.ps1
```

Details are in [`models/README.md`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/models/README.md).

## Public API

The intended adoption surface is small:

- [`VisualDriver.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/driver/VisualDriver.java)
- [`VisualHealingEngine.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/engine/VisualHealingEngine.java)
- [`VisualHealingConfig.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/config/VisualHealingConfig.java)
- [`EmbeddingConfig.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/config/EmbeddingConfig.java)

Example:

```java
ChromeDriver chrome = new ChromeDriver();
WebDriver healenium = SelfHealingDriver.create(chrome);

VisualHealingConfig config = VisualHealingConfig.builder()
    .interactiveReview(false)
    .refreshBaseline(false)
    .embeddingConfig(EmbeddingConfig.fromSystemProperties())
    .build();

VisualDriver driver = new VisualDriver(healenium, chrome, config);
```

Compatibility note:

- system properties still work
- config objects are now the preferred runtime API
- benchmark tests now use explicit engine setters for mid-run baseline transitions instead of mutating JVM properties

### Library-style adoption

```java
ChromeDriver chrome = new ChromeDriver();
WebDriver healenium = SelfHealingDriver.create(chrome);

EmbeddingConfig embedding = EmbeddingConfig.builder()
    .enabled(true)
    .modelDir(Path.of("models", "gte-small-onnx"))
    .modelName("gte-small")
    .build();

VisualHealingConfig healing = VisualHealingConfig.builder()
    .interactiveReview(false)
    .refreshBaseline(false)
    .embeddingConfig(embedding)
    .build();

VisualDriver driver = new VisualDriver(healenium, chrome, healing);
WebElement city = driver.findElement(By.id("city"));
```

### Benchmark-style baseline then healing

```java
VisualDriver driver = new VisualDriver(healenium, chrome, VisualHealingConfig.fromSystemProperties());

driver.getEngine().setCaptureBaseline(true);
driver.getEngine().setRefreshBaseline(true);
driver.get(pageUrl("baseline.html"));
driver.findElement(By.id("fname"));
driver.findElement(By.id("lname"));

driver.getEngine().setCaptureBaseline(false);
driver.getEngine().setRefreshBaseline(false);
driver.get(pageUrl("updated.html"));
WebElement healed = driver.findElement(By.id("fname"));
```

## Recovery Pipeline

### 1. Baseline capture

During the learning run, Xealenium stores page-scoped snapshots in `visual-baseline.json`.

Each snapshot includes:

- locator
- page URL
- page identity metadata
- full-page coordinates
- element image
- visible text
- accessible name
- semantic role
- autocomplete
- semantic fingerprint
- optional embedding vector

### 2. Recovery order

When a lookup fails:

1. Selenium tries first
2. Healenium tries second
3. Xealenium visual healing runs third

### 3. Visual healing

The engine:

1. loads the baseline snapshot
2. collects candidate controls from the current page
3. enriches them with semantic metadata
4. scores them with visual, positional, semantic, field, and embedding features
5. makes a healing decision through a review strategy
6. generates a robust locator for the selected candidate
7. records heatmap and report artifacts

## Semantic Stack

Xealenium now uses one shared semantic pipeline instead of multiple drifting heuristics.

Core classes:

- [`DomSemanticProvider.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/semantic/DomSemanticProvider.java)
- [`AccessibilityTreeSemanticProvider.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/semantic/AccessibilityTreeSemanticProvider.java)
- [`SemanticSignalExtractor.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/semantic/SemanticSignalExtractor.java)

Current signals:

- accessible name
- semantic role
- `autocomplete`
- label text
- placeholder
- description text
- section context
- parent context
- input type

Lexical matching is backed by [`SemanticSimilarity.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/semantic/SemanticSimilarity.java) and [`WordNetSemanticService.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/semantic/WordNetSemanticService.java).

## Smart Locator Builder

[`SmartLocatorBuilder.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/locator/SmartLocatorBuilder.java) converts either a healed `WebElement` or a screen point into a stable Selenium locator.

Main steps:

1. detect or normalize the target element
2. extract semantic and structural attributes
3. generate locator candidates
4. reject fragile or non-unique selectors
5. rank accepted candidates
6. return the best locator plus fallback candidates and logs

Supported strategies include:

- `data-testid` / `data-test`
- stable `id`
- `name`
- `aria-label`
- placeholder-based selectors
- label-based XPath
- class plus attribute combinations
- class plus text XPath for meaningful clickable controls

Strictly avoided unless no better option exists:

- absolute XPath
- layout-heavy chains
- non-unique selectors

## Local Embeddings

Embeddings are local-only and enabled by default when the local model folder is available.

Current verified setup:

- model: `gte-small`
- runtime: ONNX Runtime Java
- tokenizer: DJL Hugging Face tokenizers

Core classes:

- [`EmbeddingFingerprintBuilder.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/embedding/EmbeddingFingerprintBuilder.java)
- [`LocalEmbeddingService.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/embedding/LocalEmbeddingService.java)

If the model is missing, Xealenium falls back to the deterministic scorer.

## Human Review

Review is now strategy-based rather than hardwired into the engine.

Implementations:

- [`ThresholdOnlyReviewStrategy.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/engine/ThresholdOnlyReviewStrategy.java)
- [`AutoAcceptReviewStrategy.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/engine/AutoAcceptReviewStrategy.java)
- [`SwingReviewStrategy.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/main/java/com/visual/engine/SwingReviewStrategy.java)

With `interactive=true`, Swing review shows:

- failed locator
- candidate score
- smart locator
- full-page heatmap

## Output Files

Generated locally:

- `visual-baseline.json`
- `visual-heatmap-*.png`
- `visual-healing-report.html`

These are not part of the committed source.

## Benchmark Suites

Main benchmark entry points:

- `com.demo.benchmark.VisualPageMatrixTests`
- `com.demo.benchmark.HybridRecoveryModeTests`
- `com.demo.benchmark.RefusalBenchmarkTests`

These prove:

- direct Selenium success
- Healenium recovery on soft drift
- Xealenium-only recovery on hard drift
- Xealenium refusal when confidence is too low

If you want the benchmark-style setup pattern, see:

- [`VisualPageMatrixTests.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/test/java/com/demo/benchmark/VisualPageMatrixTests.java)
- [`HybridRecoveryModeTests.java`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/src/test/java/com/demo/benchmark/HybridRecoveryModeTests.java)

## Recommended Commands

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.benchmark.VisualPageMatrixTests"
```

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.benchmark.HybridRecoveryModeTests"
```

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.benchmark.RefusalBenchmarkTests"
```

## Limitations

- strongest current benchmark coverage is forms
- currently optimized around Chrome and Chromium-based AX extraction
- some custom widgets still rely on heuristics rather than widget-specific adapters
- the repo still carries non-blocking Healenium init-report warnings and Selenium CDP warnings on Chrome 146

## More Documentation

- [`docs/ARCHITECTURE.md`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/docs/ARCHITECTURE.md)
- [`docs/TEST_MATRIX.md`](/C:/Users/Hyper/.gemini/antigravity/scratch/healenium-tests/docs/TEST_MATRIX.md)
