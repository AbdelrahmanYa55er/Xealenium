# Xealenium - Visual Self-Healing Engine for Selenium

A lightweight visual fallback engine that triggers when native Selenium and Healenium fail to locate an element. Uses pixel-level template matching, spatial proximity, and semantic text similarity to re-locate elements on heavily restructured web pages.

## Requirements
- Java 17+
- Chrome Browser installed
- Python 3 (for the local web server in `run.bat`)

## Quick Start

### 1. Place your HTML pages
- **`pages/baseline.html`** — Your original, working page (the one your tests were written against)
- **`pages/updated.html`** — Your modified/broken page (the one with changed elements)

### 2. Run the wizard
Double-click `run.bat` — it will:
1. Start a local web server on port 8081
2. Run the baseline capture (learning element snapshots)
3. Run healing against your updated page **with human-in-the-loop confirmation**

### 3. Review the report
After the run, open `visual-healing-report.html` in any browser.

---

## Manual Commands

### Baseline capture
```bash
.\gradlew.bat test --tests "com.demo.VisualDemoTests" -DtestUrl="file:///.../pages/baseline.html" -Dinteractive=false -Dreport=false
```

### Healing with human-in-the-loop confirmation
```bash
.\gradlew.bat test --tests "com.demo.VisualDemoTests" -DtestUrl="file:///.../pages/updated.html" -Dinteractive=true
```

### Healing without confirmation (auto-accept best match)
```bash
.\gradlew.bat test --tests "com.demo.VisualDemoTests" -DtestUrl="file:///.../pages/updated.html" -Dinteractive=false
```

## Flags

| Flag | Default | Description |
|------|---------|-------------|
| `-DtestUrl` | `file:///.../pages/baseline.html` | Target page URL |
| `-Dinteractive` | `false` | Show heatmap dialog for each heal (Confirm / Try Next / Refuse) |
| `-Dreport` | `true` | Generate `visual-healing-report.html` after the run |

## Output Files
- `visual-healing-report.html` — Interactive report with new locators and embedded heatmaps
- `visual-heatmap-*.png` — Individual heatmap overlays per element
- `visual-baseline.json` — Stored element snapshots from the baseline run
