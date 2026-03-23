# Models

This folder is reserved for local embedding models.

The framework expects the verified default layout:

```text
models/
  gte-small-onnx/
    model.onnx
    tokenizer.json
```

The actual model files are intentionally not committed to git.

## Why

- keeps the source repository small
- avoids large binary blobs in git history
- lets contributors choose whether they want embedding-assisted healing
- keeps model provenance explicit instead of hiding it inside the repo

## Recommended Model

Current verified local model:

- `Qdrant/gte-small-onnx`

## Download

Use the helper script from the project root:

```powershell
.\scripts\download-model.ps1
```

That downloads:

- `model.onnx`
- `tokenizer.json`

into `models\gte-small-onnx`.

## Launcher Behavior

The helper launchers auto-enable embeddings when both files exist:

- [`run.bat`](../run.bat)
- [`run_wizard.bat`](../run_wizard.bat)
- [`run_wizard.ps1`](../run_wizard.ps1)
