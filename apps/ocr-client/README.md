# OCR Analysis Service

Java-based service for extracting structured legal data and handling document analysis logic.

## Features

- Document Structuring: Extracts court names, case numbers, and legal entities from raw text.
- Advanced Parsing: Leverages fuzzy matching and heuristic algorithms for high accuracy.
- Real-time Streaming: Provides analysis results page-by-page via Server-Sent Events (SSE).
- Fault Tolerance: Built-in retry mechanisms for external engine integration.

## Tech Stack

- Framework: Spring Boot 3.2
- Language: Java 17
- Communication: Spring WebFlux (Streaming), RestClient (Sync)
- Documentation: SpringDoc OpenAPI (Swagger UI)
- Utilities: Apache Commons Text, TwelveMonkeys ImageIO

## Getting Started

### Prerequisites

- Java 17 or higher
- OCR Engine Service running (http://localhost:8000)

### Execution

1. **Development**:
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=dev'
   ```

2. **Production Build**:
   ```bash
   ./gradlew build -x test
   java -jar build/libs/ocr-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
   ```

## Project Structure

- `api/`: REST Controllers and API documentation.
- `core/`: Core business logic and parsing algorithms.
- `integration/`: External API clients and response models.
- `infra/`: Global configurations and error handlers.
- `util/`: Reusable text and logging utilities.
