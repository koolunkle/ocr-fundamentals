# Document Structure Analysis & OCR Engine

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![Python](https://img.shields.io/badge/Python-3.13%2B-3776AB?style=for-the-badge&logo=python&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.135%2B-009688?style=for-the-badge&logo=fastapi&logoColor=white)
![ONNX](https://img.shields.io/badge/ONNX-Inference-005CED?style=for-the-badge&logo=onnx&logoColor=white)

An integrated system for AI-powered document information extraction (OCR) and structured data parsing (Key Information Extraction). This service transforms unstructured images into structured JSON by combining high-performance text recognition with semantic entity analysis.

## Key Features

* **High-Performance OCR & KIE Pipeline**
    * **PaddleOCR Integration**: Robust text detection and recognition across various document formats and orientations.
    * **LayoutXLM (SER)**: Utilizes Semantic Entity Recognition to extract meaningful fields (e.g., dates, amounts, names) based on both textual content and spatial coordinates.
    * **ONNX Runtime**: Optimized AI inference engine ensuring low-latency processing and high throughput on CPU environments.
* **Intelligent Document Reconstruction**
    * **Spatial Sorting**: Automatically aligns detected text blocks using a heuristic coordinate-based algorithm (top-to-bottom, left-to-right) for accurate context mapping.
    * **BIO-Tag Parsing**: Sophisticated field reconstruction logic using Begin-Inside-Outside tagging to merge sub-word tokens and adjacent text regions into coherent entities.
* **Scalable Microservice Architecture**
    * **KIE Client (Java/Spring Boot)**: Production-ready gateway service handling request validation, multi-part file processing, and unified API response standard.
    * **KIE Module (Python/FastAPI)**: Specialized inference engine focused on AI model orchestration, image preprocessing (OpenCV), and heavy computational tasks.
* **Modern Engineering Standards**
    * **Type-Safe Data Contracts**: Immutable Java records and Python Pydantic models ensure strict schema validation across the entire pipeline.
    * **Advanced Dependency Management**: Leveraging `uv` for lightning-fast Python environment synchronization and `Gradle` for reliable Java builds.

## API Overview

### 1. Document Extraction (KIE Client)
The primary entry point for external systems to upload documents and receive structured data.
* **Endpoint:** `POST /api/v1/kie/extract`
* **Consumes:** `multipart/form-data` (Supports JPG, PNG)
* **Produces:** `application/json`

### 2. Inference Engine (KIE Module)
The internal API used for raw AI analysis, providing detailed extraction results.
* **Endpoint:** `POST /api/v1/kie/extract`
* **Consumes:** `multipart/form-data`
* **Produces:** `application/json`

## Project Structure

The repository follows a clean separation of concerns between the orchestration layer and the inference engine:

    /
    ├── apps/
    │   ├── kie-client/   # Spring Boot Gateway (Java 17)
    │   └── kie-module/   # AI Inference Engine (Python 3.13)
    ├── libs/             # Shared assets and common libraries
    └── models/           # Pre-trained ONNX models and label definitions

## Getting Started

### Prerequisites
* **Java 17** or higher
* **Python 3.13** or higher
* **uv** (Python package manager)
* **Gradle** (Java build tool)

### Installation & Execution

1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-username/kie-system.git
   cd kie-system
   ```

2. **Run the KIE Module (Inference Engine):**
   ```bash
   cd apps/kie-module
   uv sync
   uv run python -m uvicorn app.main:app --reload --port 8000
   ```

3. **Run the KIE Client (Gateway):**
   ```bash
   cd apps/kie-client
   ./gradlew bootRun
   ```

## Testing

The project includes unit and integration tests covering the core AI algorithms and API orchestration layers.

```bash
# Run Java Client Tests
cd apps/kie-client
./gradlew test

# Run Python Module Tests
cd apps/kie-module
# uv run pytest (if tests are configured)
```

## License
This project is distributed under the Apache License 2.0. 
For details, see the [LICENSE](LICENSE) file.
