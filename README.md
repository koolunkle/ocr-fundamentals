# OCR Analysis System

High-performance document analysis system for legal and judicial documents.

## System Overview

This system is composed of two specialized services working together:

1. [Analysis Service (Java)](apps/ocr-client/README.md): Handles business logic, fuzzy matching, and structured data extraction.
2. [Engine Service (Python)](apps/ocr-module/README.md): Provides core text recognition and layout analysis.

## Project Structure

```text
ocr-system/
├── apps/
│   ├── ocr-client/         # Document analysis and business logic
│   └── ocr-module/      # OCR engine and layout analysis
├── libs/                       # Shared components (Planned)
└── README.md          # System documentation
```

## Getting Started

Follow these steps to run the entire system locally.

### 1. Engine Service (Python)
```bash
cd apps/ocr-module
uv sync
uv run python -m app.main
```
*The engine runs at http://localhost:8000 by default.*

### 2. Analysis Service (Java)
```bash
cd apps/ocr-client
./gradlew bootRun
```
*The service runs at http://localhost:8080 by default.*

---

## Documentation
- [Analysis Service Guide](apps/ocr-client/README.md)
- [Engine Service Guide](apps/ocr-module/README.md)
