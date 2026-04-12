# OCR Engine Service

Python-based engine for high-performance text recognition and document layout analysis.

## Features

- Text Recognition: Powered by RapidOCR (ONNX) for fast and accurate OCR results.
- Layout Analysis: Automatically detects document structures and logical text blocks.
- Parallel Processing: Multi-threaded handling for multi-page PDF and TIFF documents.
- Modern Tooling: Managed with uv for predictable and fast environment setup.

## Tech Stack

- Framework: FastAPI
- OCR Engine: RapidOCR (ONNX Runtime)
- Environment: Python 3.13+
- Package Manager: uv

## Project Structure

- `app/api/`: API endpoints and dependencies.
- `app/engine/`: Core OCR, layout analysis, and processing logic.
- `models/`: ONNX models and dictionary files.
- `tests/`: Test suite for engine validation.

## Getting Started

### 1. Installation
```bash
# Recommended
uv sync

# Standard pip
pip install -r requirements.txt
```

### 2. Run Service
```bash
# Using uv
uv run python -m app.main

# Using python
python -m app.main
```

## API Endpoints

- `POST /api/v1/ocr/`: Standard batch processing.
- `POST /api/v1/ocr/stream`: Real-time page-by-page streaming results.

---
*Note: This service is optimized for Korean legal documents but can be extended for other domains.*
