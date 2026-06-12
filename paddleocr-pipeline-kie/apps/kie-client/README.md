# KIE-Client (Spring Boot)

사용자 요청을 수신하고, 원격 KIE 분석 엔진(kie-module)과 통신하여 최종 결과를 반환하는 중계 서비스입니다.

## 기술 스택
- Framework: Spring Boot 3.x (Java 21)
- Build Tool: Gradle 8.x
- Communication: RestClient (Spring 6.1+)
- Library: Lombok, Jackson

## 서비스 구조
- Controller: /api/v1/kie/extract 엔드포인트를 통해 클라이언트 요청 수락
- Service (KieService): 외부 분석 엔진(kie-module)으로 멀티파트 요청을 위임하고 응답을 파싱
- DTO:
  - ExtractionResult: 분석 엔진의 응답을 담는 레코드 (Null-Safe)
  - ApiResponse: 표준 API 응답 템플릿 (성공 여부, 메시지 포함)

## 엔진 연동 설정 (application.properties)
분석 엔진 서버의 위치를 설정합니다.
```properties
kie.remote.api-url=http://localhost:8000/api/v1/kie/extract
```

## 에러 핸들링
- GlobalExceptionHandler를 통해 아래 예외를 공통 규격으로 응답합니다.
  - KIE_REMOTE_CONNECTION_FAILED: 분석 엔진 서버와 통신 불가 시
  - REMOTE_SERVER_ERROR: 엔진 서버에서 오류 응답(4xx, 5xx) 수신 시
  - MULTIPART_FILE_ERROR: 파일 업로드 중 문제 발생 시

## 데이터 흐름 (Workflow)
1. 사용자로부터 Multipart 이미지 수신
2. KieService에서 RestClient를 통해 분석 엔진으로 전송 (Multipart 파트별 Content-Type 명시)
3. 엔진의 분석 결과(JSON)를 ExtractionResult로 역직렬화
4. ApiResponse 래퍼에 담아 최종 사용자에게 반환
