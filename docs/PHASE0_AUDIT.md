# Phase 0 Audit

## Source Layout

Current repository state before refactor:

- there is no `src/main/java`
- all Java source lives under `src/test/java`
- runtime/framework classes and tests are mixed in the same source set

## Class Map

### Framework and runtime classes

- `src/test/java/com/visual/AccessibilityTreeSemanticProvider.java`
- `src/test/java/com/visual/BaselineStore.java`
- `src/test/java/com/visual/DomSemanticProvider.java`
- `src/test/java/com/visual/ElementSnapshot.java`
- `src/test/java/com/visual/EmbeddingFingerprintBuilder.java`
- `src/test/java/com/visual/HeatmapRenderer.java`
- `src/test/java/com/visual/ImageUtils.java`
- `src/test/java/com/visual/LocalEmbeddingService.java`
- `src/test/java/com/visual/ReportEntry.java`
- `src/test/java/com/visual/ScoreResult.java`
- `src/test/java/com/visual/SemanticProvider.java`
- `src/test/java/com/visual/SemanticSignalExtractor.java`
- `src/test/java/com/visual/SemanticSignals.java`
- `src/test/java/com/visual/SemanticSimilarity.java`
- `src/test/java/com/visual/SmartLocatorBuilder.java`
- `src/test/java/com/visual/SmartLocatorResult.java`
- `src/test/java/com/visual/TextSimilarity.java`
- `src/test/java/com/visual/VisualDriver.java`
- `src/test/java/com/visual/VisualHealingEngine.java`
- `src/test/java/com/visual/WordNetSemanticService.java`

### Test and demo classes

- `src/test/java/com/demo/DemoTests.java`
- `src/test/java/com/demo/HybridRecoveryModeTests.java`
- `src/test/java/com/demo/VisualDemoTests.java`
- `src/test/java/com/demo/VisualPageMatrixTests.java`
- `src/test/java/com/visual/EmbeddingFingerprintBuilderTest.java`
- `src/test/java/com/visual/LocalEmbeddingServiceTest.java`
- `src/test/java/com/visual/SemanticSimilarityTest.java`
- `src/test/java/com/visual/SmartLocatorBuilderTest.java`

### Utility-heavy framework classes

- `src/test/java/com/visual/ImageUtils.java`
- `src/test/java/com/visual/TextSimilarity.java`
- `src/test/java/com/visual/EmbeddingFingerprintBuilder.java`
- `src/test/java/com/visual/WordNetSemanticService.java`

### Report and output ownership

- `src/test/java/com/visual/VisualHealingEngine.java`
  - owns report list, report HTML generation, and report entry creation
- `src/test/java/com/visual/HeatmapRenderer.java`
  - owns heatmap rendering
- `src/test/java/com/visual/ReportEntry.java`
  - report model

## Dependency Map

### Direct references to key framework classes

#### `VisualHealingEngine`

- `src/test/java/com/demo/HybridRecoveryModeTests.java`
- `src/test/java/com/demo/VisualDemoTests.java`
- `src/test/java/com/visual/VisualDriver.java`

#### `SmartLocatorBuilder`

- `src/test/java/com/visual/SmartLocatorBuilderTest.java`
- `src/test/java/com/visual/VisualDriver.java`
- `src/test/java/com/visual/VisualHealingEngine.java`

#### `SemanticSignalExtractor`

- `src/test/java/com/visual/SmartLocatorBuilder.java`
- `src/test/java/com/visual/VisualHealingEngine.java`

#### `BaselineStore`

- `src/test/java/com/visual/VisualHealingEngine.java`

#### `LocalEmbeddingService`

- `src/test/java/com/visual/LocalEmbeddingServiceTest.java`
- `src/test/java/com/visual/VisualHealingEngine.java`

## Selenium Dependency Surface

Classes that depend directly on Selenium APIs:

- all demo test classes under `src/test/java/com/demo`
- `AccessibilityTreeSemanticProvider`
- `DomSemanticProvider`
- `ImageUtils`
- `SemanticProvider`
- `SemanticSignalExtractor`
- `SmartLocatorBuilder`
- `SmartLocatorBuilderTest`
- `VisualDriver`
- `VisualHealingEngine`

## Embedding and Lexical Semantics

### ONNX / tokenizer dependencies

- `src/test/java/com/visual/LocalEmbeddingService.java`
  - imports DJL tokenizer APIs
  - imports ONNX Runtime APIs

### WordNet lexical semantics

- `src/test/java/com/visual/WordNetSemanticService.java`
- `src/test/java/com/visual/SemanticSimilarity.java`

## Embedded JavaScript Inventory

### Long embedded JavaScript blocks

- `src/test/java/com/demo/HybridRecoveryModeTests.java`
  - semantic inspection script used by assertions
- `src/test/java/com/demo/VisualPageMatrixTests.java`
  - semantic inspection script used by assertions
- `src/test/java/com/visual/DomSemanticProvider.java`
  - main browser-side semantic metadata extraction
- `src/test/java/com/visual/SmartLocatorBuilder.java`
  - locator metadata extraction / normalization script
- `src/test/java/com/visual/VisualHealingEngine.java`
  - candidate collection script
  - point-based candidate rehydration support

### Short browser-side script calls

- `src/test/java/com/visual/AccessibilityTreeSemanticProvider.java`
- `src/test/java/com/visual/ImageUtils.java`
- `src/test/java/com/visual/SmartLocatorBuilder.java`
- `src/test/java/com/visual/VisualDriver.java`
- `src/test/java/com/demo/HybridRecoveryModeTests.java`
- `src/test/java/com/demo/VisualPageMatrixTests.java`

## Current Runnable Entry Points

### Demo and benchmark tests

- `com.demo.DemoTests`
- `com.demo.VisualDemoTests`
- `com.demo.VisualPageMatrixTests`
- `com.demo.HybridRecoveryModeTests`

### Focused unit-style tests

- `com.visual.SmartLocatorBuilderTest`
- `com.visual.SemanticSimilarityTest`
- `com.visual.EmbeddingFingerprintBuilderTest`
- `com.visual.LocalEmbeddingServiceTest`

### Helper launchers

- `run.bat`
- `run_wizard.bat`
- `run_wizard.ps1`

## HTML Assets

- `pages/baseline.html`
- `pages/baseline_hybrid.html`
- `pages/semantic_signals.html`
- `pages/updated.html`
- `pages/updated_hybrid.html`
- `pages/updated_variant.html`

## Questions Answered For Phase Exit

### Which classes are framework code versus tests?

Framework/runtime code is currently everything under `src/test/java/com/visual` except:

- `EmbeddingFingerprintBuilderTest`
- `LocalEmbeddingServiceTest`
- `SemanticSimilarityTest`
- `SmartLocatorBuilderTest`

Test/demo code is everything under `src/test/java/com/demo` plus the four test classes above.

### Which classes depend directly on Selenium?

Direct Selenium dependency exists in:

- all demo test classes
- `AccessibilityTreeSemanticProvider`
- `DomSemanticProvider`
- `ImageUtils`
- `SemanticProvider`
- `SemanticSignalExtractor`
- `SmartLocatorBuilder`
- `VisualDriver`
- `VisualHealingEngine`

### Which classes depend on ONNX or embeddings?

- `LocalEmbeddingService`
- `EmbeddingFingerprintBuilder`
- `VisualHealingEngine`
- `LocalEmbeddingServiceTest`
- `EmbeddingFingerprintBuilderTest`

### Which classes own report generation?

- `VisualHealingEngine`
  - HTML report generation
  - report event creation
  - report list ownership
- `HeatmapRenderer`
  - heatmap image generation
- `ReportEntry`
  - report record model
