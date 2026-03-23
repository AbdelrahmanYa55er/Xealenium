# Architecture

## Overview

Xealenium is organized around three recovery layers:

1. Selenium
2. Healenium
3. Visual healing

Visual healing is responsible for the “hard DOM drift” case where the original locator contract is no longer reliable.

## Main Flow

```text
failed locator
  -> Selenium miss
  -> Healenium miss or unsuitable recovery
  -> VisualHealingEngine candidate search
  -> semantic + visual scoring
  -> best candidate
  -> SmartLocatorBuilder
  -> healed locator + report + heatmap
```

## Core Components

### `VisualDriver`

- wraps the active driver
- captures baselines on successful baseline runs
- routes unresolved locator failures into `VisualHealingEngine`
- exposes smart-locator generation from screen coordinates

### `VisualHealingEngine`

- stores and loads baseline snapshots
- enumerates candidate controls
- compares baseline snapshots against live candidates
- computes ranking scores
- renders heatmaps
- supports human-in-the-loop confirmation
- records healed locator reports

### `BaselineStore`

- persists snapshots into `visual-baseline.json`
- scopes entries by page URL and locator
- protects baseline entries from accidental overwrite unless refresh is enabled

### `SmartLocatorBuilder`

- can start from `(x, y)` or a concrete `WebElement`
- normalizes noisy targets like inner spans, icons, and wrappers
- generates multiple locator candidates
- validates uniqueness and exact identity
- ranks candidates for stability and readability

## Semantic Layer

### Providers

- `DomSemanticProvider`
- `AccessibilityTreeSemanticProvider`
- `SemanticSignalExtractor`

### Signals

- accessible name
- semantic role
- autocomplete
- label text
- placeholder
- description text
- section context
- parent context
- input type

The goal is to feed the same semantic truth into both the visual scorer and the smart locator builder.

## Embedding Layer

Embeddings are optional and layered on top of the deterministic scorer.

### `EmbeddingFingerprintBuilder`

Builds a semantic fingerprint from:

- locator tokens
- kind
- tag
- role
- input type
- accessible name
- label
- placeholder
- description
- autocomplete
- context
- text

### `LocalEmbeddingService`

- loads ONNX model and tokenizer locally
- computes sentence embeddings
- adds an `emb` score to candidate ranking when enabled

## Hybrid Testing Design

The project now includes a hybrid page pair that exercises all three recovery modes on one updated page:

- direct lookup still works for some controls
- Healenium is needed for soft DOM drift
- visual healing is needed for hard DOM drift

That makes it easier to reason about recovery behavior without switching between multiple suites.
