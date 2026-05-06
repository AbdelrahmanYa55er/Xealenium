# Xealenium Run Commands Cheat Sheet

Run these commands from the project root folder, where `build.gradle` and `gradlew.bat` exist.

## 1. Capture baseline without embeddings

Use this to learn the original stable page and create/update `visual-baseline.json`.

```powershell
.\gradlew.bat --no-daemon captureBaseline
```

## 2. Run healing without embeddings

Use this to run against `pages/updated.html` using the saved baseline.

```powershell
.\gradlew.bat --no-daemon runHealing
```

## 3. Capture baseline with embeddings

Use this when you want baseline snapshots to include `gte-small` embedding vectors.

```powershell
.\gradlew.bat --no-daemon captureBaselineWithEmbeddings
```

## 4. Run healing with embeddings

This is the preferred stable demo run because embeddings improve confidence.

```powershell
.\gradlew.bat --no-daemon runHealingWithEmbeddings
```

## 5. Run only VisualDemoTests manually

Use this if you want to run the demo test class directly.

```powershell
.\gradlew.bat --no-daemon test --tests "com.demo.VisualDemoTests"
```

## 6. Run a specific test class

Replace the class name with your test class.

```powershell
.\gradlew.bat --no-daemon test --tests "your.package.YourTestClass"
```

## 7. Override threshold from command line

Use this to experiment with stricter or looser healing acceptance.

```powershell
.\gradlew.bat --no-daemon runHealingWithEmbeddings "-Dvisual.threshold=0.75"
```

## 8. Override page from command line

Use this if you want to force baseline or updated page without editing `xealenium.properties`.

```powershell
.\gradlew.bat --no-daemon test --tests "com.demo.VisualDemoTests" "-Dtest.page=baseline"
```

```powershell
.\gradlew.bat --no-daemon test --tests "com.demo.VisualDemoTests" "-Dtest.page=updated"
```

## 9. Force baseline capture manually

Use this if you are running a test directly and want learning mode.

```powershell
.\gradlew.bat --no-daemon test --tests "com.demo.VisualDemoTests" "-Dtest.page=baseline" "-Dvisual.captureBaseline=true"
```

## 10. Force healing mode manually

Use this if you are running a test directly and want healing mode.

```powershell
.\gradlew.bat --no-daemon test --tests "com.demo.VisualDemoTests" "-Dtest.page=updated" "-Dvisual.captureBaseline=false"
```

## 11. Clean build outputs

Use this if Gradle has stale files or locked test result files.

```powershell
.\gradlew.bat --no-daemon clean
```

Then rerun your needed task.

## 12. Recommended daily flow

For normal embedding-based validation:

```powershell
.\gradlew.bat --no-daemon captureBaselineWithEmbeddings
.\gradlew.bat --no-daemon runHealingWithEmbeddings
```

## Important notes

- `captureBaseline` means learning mode.
- `runHealing` means healing mode.
- `--no-daemon` avoids Gradle daemon issues on machines using newer Java versions.
- `visual-baseline.json` is the saved baseline file.
- `visual-healing-report.html` is the generated healing report.
- `xealenium.properties` contains default runtime behavior.
- Command-line `-D...` values override `xealenium.properties`.
