# Test Matrix

## Page Assets

### `baseline.html`

Original demo page used to learn the baseline.

### `updated.html`

Primary changed page used for the original end-to-end healing scenario.

### `updated_variant.html`

Harder changed page that reorders controls and changes labels and structure more aggressively.

### `baseline_hybrid.html`

Baseline for the mixed recovery-mode scenario.

### `updated_hybrid.html`

Updated page intentionally designed so recovery splits across:

- direct Selenium success
- Healenium recovery
- visual recovery

### `semantic_signals.html`

Focused fixture for semantic extraction and locator-generation tests.

## Test Classes

### `com.demo.VisualDemoTests`

Purpose:

- standard baseline vs updated demo flow

Validates:

- baseline capture
- end-to-end healing
- report generation
- interactive mode compatibility

### `com.demo.VisualPageMatrixTests`

Purpose:

- run the same flow against multiple updated page variants

Validates:

- recovery across different labels, layouts, and ordering changes
- semantic ranking stability

### `com.demo.HybridRecoveryModeTests`

Purpose:

- prove the framework can distinguish recovery layers on one updated page

Breakdown on `updated_hybrid.html`:

- direct: `fname`, `email`, `lname`, `newsletter`, `zip`, `terms`, `submit-btn`
- Healenium: `phone`, `country`
- visual healing: `city`

### `com.visual.SmartLocatorBuilderTest`

Purpose:

- verify point-to-locator and element-to-locator generation

Validates:

- semantic selectors
- uniqueness checks
- rejection of low-quality selectors
- contenteditable handling

### `com.visual.SemanticSimilarityTest`

Purpose:

- verify lexical and semantic scoring utilities

### `com.visual.EmbeddingFingerprintBuilderTest`

Purpose:

- verify the richer fingerprint format used for local embeddings

### `com.visual.LocalEmbeddingServiceTest`

Purpose:

- verify local model loading and vector generation behavior

## Recommended Regression Runs

### Deterministic baseline

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.VisualPageMatrixTests"
```

### Hybrid mode coverage

```powershell
.\gradlew.bat --no-daemon --rerun-tasks test --tests "com.demo.HybridRecoveryModeTests"
```

### Embedding-enabled coverage

```powershell
.\gradlew.bat --no-daemon `
  "-Dvisual.embedding.enabled=true" `
  "-Dvisual.embedding.modelDir=C:/PATH_TO_MODEL_DIR" `
  "-Dvisual.embedding.modelName=gte-small" `
  --rerun-tasks test --tests "com.demo.VisualPageMatrixTests"
```
