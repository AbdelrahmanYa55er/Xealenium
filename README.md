# Xealenium

Xealenium is an experimental Selenium self-healing framework that layers:

1. Native Selenium lookup
2. Healenium recovery
3. Visual recovery with semantic reranking and smart locator generation

The project is no longer just a visual demo. It now includes:

- protected page-scoped baselines
- full-page heatmaps
- human-in-the-loop confirmation
- semantic signal extraction from DOM and Chromium accessibility data
- smart locator generation from points or healed elements
- local ONNX embeddings for open-world semantic matching, enabled automatically when the default model folder exists
- multiple page sets and recovery-mode tests

## Requirements

- Java 17+
- Chrome installed
- Windows PowerShell or `cmd` for the helper scripts

## Page Sets

Current demo assets in [`pages`](pages):

- `baseline.html` / `updated.html`
  - the original baseline vs changed DOM pair
- `baseline_hybrid.html` / `updated_hybrid.html`
  - mixed recovery-mode pair where some fields still work directly, some need Healenium, and some need visual healing
- `updated_variant.html`
  - reordered and structurally changed page for tougher semantic testing
- `semantic_signals.html`
  - fixture page for semantic extraction and smart-locator tests

## Quick Start

### One-command runner

```powershell
.\run.bat
```

This does two steps:

1. rebuilds the baseline from `pages/baseline.html`
2. runs healing on `pages/updated.html`

If a local model exists at `models\gte-small-onnx`, the runner enables embeddings automatically. Otherwise it runs without embeddings.

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

It also auto-detects the local embedding model if present.

### Model bootstrap

The repo does not commit the ONNX model itself. Instead, download it locally into `models\gte-small-onnx`:

```powershell
.\scripts\download-model.ps1
```

Model setup details are documented in [`models/README.md`](models/README.md).

## Useful Test Commands

### Core demo flow

```powershell
.\gradlew.bat --no-daemon test --tests "com.demo.VisualDemoTests"
```

### Multi-page semantic matrix

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.VisualPageMatrixTests"
```

### Hybrid recovery-mode suite

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.HybridRecoveryModeTests"
```

### Interactive run

```powershell
.\gradlew.bat --no-daemon "-Dinteractive=true" "-Dreport=true" test --tests "com.demo.VisualDemoTests"
```

### Embedding-enabled run

```powershell
.\gradlew.bat --no-daemon `
  "-Dvisual.embedding.enabled=true" `
  "-Dvisual.embedding.modelDir=C:/PATH_TO_MODEL_DIR" `
  "-Dvisual.embedding.modelName=gte-small" `
  --rerun-tasks test --tests "com.demo.VisualPageMatrixTests"
```

## Recovery Pipeline

### 1. Baseline capture

During the learning run, Xealenium stores page-scoped snapshots in `visual-baseline.json`.

Each snapshot includes:

- locator
- page URL
- full-page coordinates
- element image
- visible text
- element kind
- accessible name
- semantic role
- autocomplete
- semantic fingerprint
- optional embedding vector

Existing baseline entries are protected from accidental overwrite unless refresh is explicitly enabled.

### 2. Recovery order

When a locator fails during a test:

1. Selenium tries first
2. Healenium tries second
3. `VisualHealingEngine` runs third

That third phase is the “hard drift” path, where the DOM contract is assumed to have changed enough that the old selector shape is no longer worth preserving.

### 3. Visual healing

The visual engine:

1. collects candidate controls from the current page
2. compares them against the baseline snapshot
3. scores them with visual, positional, textual, structural, and semantic features
4. chooses the best candidate above threshold
5. generates a new robust locator with `SmartLocatorBuilder`
6. records the heal in the report and heatmap artifacts

## Semantic Stack

Xealenium now uses shared semantic extraction instead of separate ad-hoc heuristics in different classes.

### Semantic providers

- `DomSemanticProvider`
  - extracts accessible-name-like signals, role heuristics, labels, placeholder, description text, section context, and parent context from the DOM
- `AccessibilityTreeSemanticProvider`
  - attempts to read Chromium accessibility semantics via CDP
- `SemanticSignalExtractor`
  - merges both and prefers stronger AX values when available

### Current semantic signals

- accessible name
- semantic role
- `autocomplete`
- label text
- placeholder / `data-placeholder`
- description text
- section context
- parent context
- input type

### Lexical and semantic matching

Current non-embedding matching uses:

- normalized text similarity
- field-level semantic comparison
- WordNet-backed lexical similarity in `WordNetSemanticService`

This reduces dependence on a handwritten synonym dictionary while still keeping the system deterministic when embeddings are off.

## Smart Locator Builder

`SmartLocatorBuilder` turns a screen point or healed `WebElement` into a stable Selenium locator.

### Input

- `buildLocatorFromPoint(int x, int y)`
- direct element-based generation for already-healed candidates

### Flow

1. detect the element
2. normalize wrappers, icons, and decorative children to a meaningful control
3. extract semantic attributes and nearby context
4. generate locator candidates
5. validate uniqueness and exact identity
6. rank accepted candidates
7. return the best locator plus fallbacks and debug logs

### Candidate strategies

- `data-testid` / `data-test`
- stable `id`
- `name`
- `aria-label`
- placeholder and contenteditable placeholder
- label-based XPath
- class + attribute combinations
- class + text XPath for meaningful clickable elements
- limited ancestor + attribute combinations

### Rejection rules

- no absolute XPath
- no non-unique selectors
- no wrong-element selectors
- no obviously dynamic ids
- no layout-heavy DOM chains unless there is no better option

## Local Embeddings

Embeddings are local-only and now default to on whenever the default model folder is available.

Current verified setup:

- model: `gte-small`
- runtime: ONNX Runtime Java
- tokenizer: DJL Hugging Face tokenizers

### Fingerprints

`EmbeddingFingerprintBuilder` creates a multi-line semantic fingerprint from:

- locator tokens
- element kind
- tag
- role
- input type
- accessible name
- label
- placeholder
- description
- autocomplete
- section context
- parent context
- text
- aggregated semantic summary lines

### Default behavior

- if `models\gte-small-onnx` exists, embeddings are enabled automatically
- if the model folder is absent, the framework falls back to the non-embedding scorer
- if you want to disable embeddings explicitly, use `-Dvisual.embedding.enabled=false`

### Launcher behavior

The helper scripts now auto-enable embeddings when both of these files exist:

- `models\gte-small-onnx\model.onnx`
- `models\gte-small-onnx\tokenizer.json`

The `models` folder is intentionally ignored by git so local model artifacts are never pushed by mistake.

## Human In The Loop

With `-Dinteractive=true`, each visual heal opens a confirmation dialog that shows:

- the failed locator
- candidate score
- the generated smart locator
- the full-page heatmap

Available actions:

- `Confirm`
- `Try Next Best`
- `Refuse (Abort)`

The dialog now requires explicit confirmation and no longer auto-accepts by default.

## Heatmaps

Heatmaps are built from stitched full-page screenshots.

Important implementation detail:

- candidate boxes are stored in page coordinates, not viewport coordinates

That keeps lower-page overlays aligned after scrolling and fixed the earlier drift that appeared once screenshots became taller.

## Output Files

- `visual-baseline.json`
- `visual-heatmap-*.png`
- `visual-healing-report.html`

These artifacts are kept out of git and regenerated locally.

## Key Tests

- `com.demo.VisualDemoTests`
  - baseline and updated demo flow
- `com.demo.VisualPageMatrixTests`
  - baseline vs multiple updated pages
- `com.demo.HybridRecoveryModeTests`
  - direct vs Healenium vs visual-healing coverage on one page pair
- `com.visual.SmartLocatorBuilderTest`
  - locator generation and selector quality checks
- `com.visual.SemanticSimilarityTest`
  - semantic and lexical scoring checks
- `com.visual.EmbeddingFingerprintBuilderTest`
  - fingerprint construction checks
- `com.visual.LocalEmbeddingServiceTest`
  - local embedding service checks

## Key Classes

- `com.visual.VisualDriver`
  - wrapper that routes Selenium failures into the visual engine
- `com.visual.VisualHealingEngine`
  - scoring, candidate ranking, heatmaps, interactive confirmation, and report generation
- `com.visual.SmartLocatorBuilder`
  - semantic locator generation from points or elements
- `com.visual.SemanticSignalExtractor`
  - shared semantic extraction orchestrator
- `com.visual.LocalEmbeddingService`
  - optional ONNX embedding runtime
- `com.visual.BaselineStore`
  - page-scoped baseline persistence

## More Documentation

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- [`docs/TEST_MATRIX.md`](docs/TEST_MATRIX.md)
