# Document Structure Analysis & OCR Engine

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.10%2B-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.100%2B-009688?style=for-the-badge&logo=fastapi&logoColor=white)

High-performance document analysis system specialized for legal and judicial documents. This system combines a Python-based OCR engine for high-accuracy text extraction with a Java-based analysis service for structured data reconstruction and domain-specific parsing.

## Key Features

* **Hybrid Service Architecture**
    * **Engine Service (Python)**: High-performance OCR inference using RapidOCR and layout analysis.
    * **Analysis Service (Java)**: Robust business logic, fuzzy string matching, and structured data extraction.
* **Advanced Document Parsing**
    * **Fuzzy Matcher**: Utilizes Jaro-Winkler distance for resilient text matching against legal terminology and entities.
    * **Document Parser**: Reconstructs logical document structures (Court, Case Info, Creditors, etc.) from raw OCR fragments and spatial coordinates.
    * **Line Merger**: Intelligent merging of fragmented text lines based on spatial proximity and alignment.
* **Real-Time SSE Streaming**
    * Full support for Server-Sent Events (SSE) in both services, providing page-by-page analysis results in real-time for multi-page documents (TIFF/Images).
* **Fault-Tolerant Processing**
    * Per-page error isolation ensures that a failure in one page doesn't interrupt the entire document analysis process.
* **Architectural Standards**
    * Immutable data structures using Java records and Python Pydantic models.
    * Reactive programming (Project Reactor) for efficient streaming data handling in the analysis layer.

## API Overview

### 1. Synchronous Analysis
Processes the entire document and returns a complete structured JSON response.
* **Endpoint:** `POST /api/v1/ocr`
* **Consumes:** `multipart/form-data` (Supports images and multi-page TIFF)
* **Produces:** `application/json`

### 2. Streaming Analysis (SSE)
Provides real-time updates as each page is processed via Server-Sent Events.
* **Endpoint:** `POST /api/v1/ocr/stream`
* **Consumes:** `multipart/form-data`
* **Produces:** `text/event-stream`

## Project Structure

The system is divided into two specialized applications to ensure scalability and separation of concerns:

```text
ocr-system/
 ├── apps/
 │    ├── ocr-client/    # Analysis Service (Java/Spring Boot)
 │    │    ├── api/      # REST Controllers and API Documentation
 │    │    ├── core/     # Analysis Logic (Fuzzy matching, Document parsing)
 │    │    ├── integration/ # OCR Engine communication clients
 │    │    └── util/     # Text and logging utilities
 │    └── ocr-module/    # Engine Service (Python/FastAPI)
 │         ├── app/      # FastAPI application and OCR processor
 │         └── models/   # Dictionary and layout configuration files
 └── libs/               # Shared components (Planned)
```

## Getting Started

### Prerequisites
* Java 17 or higher
* Python 3.10 or higher with `uv` package manager
* Gradle

### Installation & Execution

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/ocr-system.git
   cd ocr-system
   ```

2. **Run Engine Service (Python):**
   ```bash
   cd apps/ocr-module
   uv sync
   uv run python -m app.main
   ```

3. **Run Analysis Service (Java):**
   ```bash
   cd apps/ocr-client
   ./gradlew bootRun
   ```

## Testing

Comprehensive test suites are provided for both services covering core algorithms and API integrity.

```bash
# Test Java Analysis Service
cd apps/ocr-client
./gradlew test

# Test Python Engine Service
cd apps/ocr-module
uv run pytest
```

## License
This project is distributed under the Apache License 2.0. 
For details, see the [LICENSE](LICENSE) file.
