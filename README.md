# OCR Fundamentals

A consolidated collection of high-performance Optical Character Recognition (OCR) and Document AI engines for advanced document structure analysis and information extraction.

## Project Portfolio

| Project | Description | Tech Stack |
| :--- | :--- | :--- |
| **[paddleocr-djl](./paddleocr-djl)** | High-performance document analysis engine using Deep Java Library (DJL) for advanced table parsing and text extraction. | Java, Spring Boot, DJL, OpenCV |
| **[paddleocr-onnx](./paddleocr-onnx)** | High-performance OCR and document parsing engine with SLANet-based table reconstruction. | Java, Spring Boot, ONNX (RapidOCR), OpenCV |
| **[paddleocr-pipeline](./paddleocr-pipeline)** | Document analysis pipeline specialized for judicial documents and legal data reconstruction. | Java, Python, FastAPI, RapidOCR, Project Reactor |
| **[paddleocr-pipeline-kie](./paddleocr-pipeline-kie)** | End-to-end Key Information Extraction (KIE) pipeline using Semantic Entity Recognition (SER). | Java, Python, FastAPI, LayoutXLM, ONNX |
| **[tesseract-tess4j](./tesseract-tess4j)** | Hexagonal architecture-based OCR system for complex document parsing and barcode recognition. | Java, OpenCV, Tesseract (Tess4J), Tabula |

## Structure

This repository contains multiple independent modules, each maintaining its own build configuration (Gradle/uv). For specific implementation details, setup guides, and API documentation, refer to the README within each project directory.
