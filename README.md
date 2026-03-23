# Xealenium - Visual Self-Healing Engine for Selenium

Xealenium is a Selenium demo project that combines three recovery layers:

1. Native Selenium locator lookup
2. Healenium self-healing
3. A custom visual healing engine with smart locator generation

When a locator fails, the visual engine compares the failed element's saved baseline snapshot against the current page using visual similarity, position, text, element kind, and sequence. Once it picks the best candidate, it generates a human-quality Selenium locator with `SmartLocatorBuilder` so the healed result is semantic instead of a fragile raw DOM path.

## Requirements

- Java 17+
- Chrome browser installed
- Windows PowerShell or `cmd` for the helper scripts

## Demo Pages

- `pages/baseline.html` - original page used to capture baseline snapshots
- `pages/updated.html` - changed page used to demonstrate healing

## Quick Start

### One-command flow

Run:

```powershell
.\run.bat
```

This script:

1. Rebuilds the visual baseline from `pages/baseline.html`
2. Runs the healing flow against `pages/updated.html`
3. Generates heatmaps and the HTML report

By default, `run.bat` is intended for the guided demo flow. If interactive mode is enabled in the script, the run will pause on each heal and wait for explicit human confirmation.

### Wizard flow

If you want prompts for rebuild/report/interactive options, run:

```powershell
.\run_wizard.bat
```

or:

```powershell
.\run_wizard.ps1
```

## Manual Commands

### Baseline capture

```powershell
.\gradlew.bat --no-daemon test --tests "com.demo.VisualDemoTests" -DtestUrl="file:///C:/PATH_TO_PROJECT/pages/baseline.html" -Dinteractive=false -Dreport=false -Dvisual.captureBaseline=true -Dvisual.captureBaseline.refresh=true
```

### Healing run

```powershell
.\gradlew.bat --no-daemon test --tests "com.demo.VisualDemoTests" -DtestUrl="file:///C:/PATH_TO_PROJECT/pages/updated.html" -Dinteractive=true -Dreport=true
```

## How It Works

### Baseline learning

During the baseline run, the engine stores element snapshots in `visual-baseline.json`. Each snapshot includes:

- locator key
- full-page coordinates
- element size
- cropped element image
- text context
- page URL
- inferred element kind

Baselines are page-scoped and protected from accidental overwrite during healing runs unless refresh is explicitly enabled.

### Healing

When Selenium and Healenium both fail:

1. The visual engine enumerates candidate elements on the page
2. It scores them using visual, positional, textual, kind, and sequence similarity
3. It chooses the best candidate above threshold
4. It generates a stable locator using `SmartLocatorBuilder`
5. It records the healed locator in the report and heatmap artifacts

### Smart locator generation

`SmartLocatorBuilder` supports:

- `data-testid` / `data-test`
- stable `id`
- `name`
- `aria-label`
- placeholder-based selectors
- label-based XPath
- class + attribute combinations
- text-based selectors for meaningful clickable elements

It rejects non-unique candidates and avoids absolute XPath or deep DOM chains unless it has no better option.

## Human In The Loop

With `-Dinteractive=true`, the visual engine opens a confirmation dialog for each heal. The dialog shows:

- the failed locator
- candidate rank and score
- the generated smart locator
- a full-page heatmap overlay

The dialog requires explicit confirmation and supports:

- `Confirm`
- `Try Next Best`
- `Refuse (Abort)`

## Full-Page Heatmaps

Heatmaps are generated against stitched full-page screenshots. Candidate boxes are stored in page coordinates, so overlays stay aligned even for elements near the bottom of long pages after scrolling.

## Flags

| Flag | Default | Description |
|------|---------|-------------|
| `-DtestUrl` | demo page | Target page URL (`file:///` or `http(s)://`) |
| `-Dinteractive` | `false` | Pause for human confirmation on each heal |
| `-Dreport` | `true` | Generate `visual-healing-report.html` |
| `-Dvisual.captureBaseline` | auto | Explicitly enable or disable baseline capture |
| `-Dvisual.captureBaseline.refresh` | `false` | Replace existing baseline entries during capture |

## Output Files

- `visual-baseline.json` - stored baseline snapshots
- `visual-heatmap-*.png` - per-heal heatmap overlays
- `visual-healing-report.html` - HTML report of healed locators and scores

## Main Classes

- `com.visual.VisualDriver` - wraps Selenium and routes failures into visual healing
- `com.visual.VisualHealingEngine` - scoring, heatmaps, reports, and interactive confirmation
- `com.visual.SmartLocatorBuilder` - converts a candidate element or point into a robust Selenium locator
- `com.visual.BaselineStore` - persists and loads visual baselines
- `com.demo.VisualDemoTests` - demo test flow against the baseline and updated pages
