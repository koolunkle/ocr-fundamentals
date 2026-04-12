# OCR Document Analysis Microservice

A high-performance document recognition and analysis microservice built with **Java 17** and **Spring Boot 3.2**. It processes OCR raw text from external engines (RapidOCR) and extracts structured legal document data using fuzzy matching and heuristic parsing algorithms.

## Key Features

- **Document Structuring**: Extracts court names, case numbers, creditors, debtors, and orders from raw OCR lines.
- **Modern Java 17+ Architecture**: Leverages `Sealed Interfaces`, `Records`, and `Pattern Matching` for type-safe and declarative data handling.
- **Hybrid API Endpoints**:
  - **Standard REST**: Synchronous full-document analysis results.
  - **Real-time Streaming**: Page-by-page analysis results via **Server-Sent Events (SSE)** using `Flux`.
- **Fault-Tolerant Engine Integration**: Built-in retry mechanisms and localized error handling to ensure service stability.

## Tech Stack

- **Framework**: Spring Boot 3.2.2 (Spring WebFlux for streaming)
- **Language**: Java 17
- **Communication**: RestClient (Sync), WebClient (Async/Stream)
- **Documentation**: SpringDoc OpenAPI 3 (Swagger UI)
- **Utilities**: Apache Commons Text (Fuzzy matching), TwelveMonkeys ImageIO (TIFF support)

## Getting Started

### Prerequisites

- Java 17 or higher
- External OCR Engine (RapidOCR API compatible)

### Execution

1. **Development Environment**:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

2. **Production Build & Run**:
   ```bash
   ./gradlew build -x test
   java -jar build/libs/ocr-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
   ```

3. **API Documentation**:
   Access Swagger UI at: `http://localhost:8080/swagger-ui.html`

## Project Structure

- `api/`: REST Controllers and API documentation annotations.
- `core/`: Core business logic including document parsing and fuzzy matching.
- `integration/`: External API clients and response models.
- `infra/`: Global configurations, custom exceptions, and error handlers.
- `util/`: Reusable text sanitization and analysis logging tools.
