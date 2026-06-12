# PaddleOCR DJL & Document Analysis Engine

![Java](https://img.shields.io/badge/Java-17%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.3.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)
![OpenCV](https://img.shields.io/badge/OpenCV-Native-5C3EE8?style=for-the-badge&logo=opencv&logoColor=white)
![DJL](https://img.shields.io/badge/DJL-0.27.0-blue?style=for-the-badge)

A high-performance Optical Character Recognition (OCR) and document parsing engine built with Spring Boot and Deep Java Library (DJL). This service specializes in extracting structured data from Korean documents using the PaddleOCR model, analyzing spatial coordinates for table reconstruction, and identifying embedded table structures.

## Key Features

* **Advanced Document & Table Parsing**
    * **Table Structure Analysis**: Identifies physical contours and grid structures using OpenCV-based image processing and ROI detection.
    * **Domain-Specific Parsing**: Specialized logic for document type identification and structured data extraction from recognized text.
* **PaddleOCR & Vision Engine**
    * **DJL Integration**: High-accuracy text extraction using PaddleOCR models served via Deep Java Library (DJL).
    * **Image Pre-processing**: Scanned document enhancement (Binarization, Morphological operations) and layout analysis using OpenCV.
* **Document Recognition**
    * Automated detection and recognition of text within multi-page PDF and various image formats (PNG, JPG, TIFF, BMP).
* **Asynchronous Processing**
    * **Non-blocking Execution**: Parallel text recognition using multi-threaded execution for high-throughput processing.
    * **Progress Tracking**: Comprehensive logging and status management for ongoing OCR tasks.
* **Architectural Standards**
    * **Modular Design**: Clear separation between core engine components (`TableDetector`, `TextRecognizer`) and application services.
    * **Type-Safe Domain Models**: Robust Data Transfer Objects (DTOs) for managing extraction payloads and responses.

## API Overview

### 1. OCR Job Management
Processing for document analysis and text extraction.
* **Endpoint:**
    * `POST /api/ocr/extract`: Register a text and table extraction job.
* **Consumes:** `multipart/form-data` (Supports images and multi-page documents)
* **Produces:** `application/json`

### 2. Barcode Recognition
*Not implemented in this version*

## Project Structure

The project follows a standard Spring Boot architecture to ensure modularity and scalability:

    paddleocr-djl/
     ├── src/main/java/com/example/ocr/
     │    ├── config/      # Model (DET, CLS, REC) beans and properties
     │    ├── controller/  # REST API endpoints
     │    ├── core/        # Core OCR, table extraction, and vision logic
     │    │    ├── engine/ # TableDetector, TableParser, TextRecognizer
     │    │    ├── matcher/# FuzzyMatcher for data validation
     │    │    └── translator/ # Custom DJL Translators
     │    ├── dto/         # Request/Response Data Transfer Objects
     │    └── service/     # Business logic orchestration
     └── src/main/resources/ # Configuration and application.yml

## Getting Started

### Prerequisites
* Java 17 or higher
* PaddleOCR Inference Models (DET, CLS, REC)
* OpenCV Native Libraries (configured via DJL)

### Installation & Execution
1. Clone the repository:
   ```bash
   git clone <repository-url>
   ```
2. Build the project:
   ```bash
   ./gradlew clean build
   ```
3. Run the application:
   ```bash
   ./gradlew bootRun
   ```

## Testing

The project includes unit tests covering OCR context loading and engine logic.

```bash
# Run all tests
./gradlew test
```

## License
This project is distributed under the Apache License 2.0. 
For details, see the [LICENSE](LICENSE) file.
