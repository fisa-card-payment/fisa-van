# FISA VAN 서비스 로직 문서

> 카드 결제 시스템 VAN 서비스 상세 기술 문서

---

## 1. 프로젝트 개요

VAN(Value Added Network) 서비스는 POS 단말기와 카드사 사이에서 결제 요청을 중계하는 서비스입니다.  
실시간 결제 승인, 배치 처리, SSE 기반 정산 결과 수신 기능을 담당합니다.

### 기술 스택

| 기술 | 버전 | 사용 목적 |
|------|------|-----------|
| Java | 17 | 메인 개발 언어 |
| Spring Boot | 3.5.12 | 애플리케이션 프레임워크 |
| Spring Batch | 5.2.5 | 배치 처리 |
| Spring Data JPA | 3.5.10 | ORM / DB 연동 |
| MySQL | 8.0 | 데이터 저장 |
| Docker | latest | 컨테이너화 |
| Eureka Client | 4.3.1 | 서비스 디스커버리 |

### 서비스 포트

| 서비스 | 포트 |
|--------|------|
| VAN | 8081 |
| VAN DB (Docker) | 3307 |

### 전체 시스템 흐름

```
POS(Postman) → VAN(8081) → API Gateway(8080) → payment-service(8082)
                                               → settlement-service(8084)
카드사 정산 완료 → SSE → VAN
```

---

## 2. 패키지 구조

```
com.fisa.van/
├── domain/          # JPA Entity (DB 테이블 매핑)
├── repository/      # Spring Data JPA Repository
├── service/         # 비즈니스 로직
├── controller/      # REST API 엔드포인트
├── dto/             # 요청/응답 데이터 객체
└── batch/           # Spring Batch 배치 처리
    ├── AcquisitionJobConfig.java      # Job/Step 설정 + 스케줄러
    ├── AcquisitionItemReader.java     # DB에서 데이터 읽기
    ├── AcquisitionItemProcessor.java  # 데이터 가공 (CSV 한 줄 변환)
    └── AcquisitionItemWriter.java     # CSV 파일 생성 + 카드사 전송
```

---

## 3. Domain (Entity)

DB 테이블과 1:1로 매핑되는 클래스들입니다. JPA를 통해 객체와 DB 테이블을 자동으로 연결합니다.

### 3.1 VanTransaction

실시간 결제 승인/거절 내역을 저장하는 테이블입니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| vanTxId | BIGINT AUTO_INCREMENT | Primary Key |
| rrn | VARCHAR(20) | 거래 고유번호 - 카드사가 생성하여 응답 |
| stan | VARCHAR(6) | 시스템 추적번호 - POS가 발급 |
| cardNumber | VARCHAR(19) | 카드번호 |
| amount | BIGINT | 결제 금액 |
| merchantId | VARCHAR(15) | 가맹점 번호 |
| cardCompany | VARCHAR(10) | 카드사 이름 (BIN 조회 결과) |
| responseCode | VARCHAR(2) | 응답코드 (00: 승인, 나머지: 거절) |
| approvalCode | VARCHAR(6) | 카드사 승인번호 |
| status | VARCHAR(10) | APPROVED / REJECTED / CANCELLED |
| createdAt | TIMESTAMP | @PrePersist로 자동 저장 |

### 3.2 AcquisitionBatch

매일 자정 배치 실행 기록을 저장합니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| batchId | BIGINT AUTO_INCREMENT | Primary Key |
| batchDate | DATE | 정산 대상 날짜 (전날) |
| totalCount | INT | 총 거래 건수 |
| totalAmount | BIGINT | 총 거래 금액 |
| fileName | VARCHAR(100) | 생성된 CSV 파일명 |
| status | VARCHAR(10) | SENT / FAILED |
| createdAt | TIMESTAMP | 배치 실행 시각 |

### 3.3 CardBin

카드번호 앞 6자리(BIN)를 통해 카드사를 식별하는 테이블입니다.

| 필드 | 타입 | 설명 |
|------|------|------|
| binPrefix | VARCHAR(6) PK | 카드번호 앞 6자리 |
| companyName | VARCHAR(20) | 카드사 이름 |
| apiEndpoint | VARCHAR(100) | 카드사 API 엔드포인트 |

---

## 4. VanService - 핵심 비즈니스 로직

### 4.1 결제 승인 처리 (processPayment)

POS로부터 결제 요청을 받아 카드사 API Gateway로 전달하고 결과를 반환합니다.

**처리 순서**
1. BIN 조회: 카드번호 앞 6자리로 card_bins 테이블에서 카드사 이름 조회
2. 카드사 요청: `http://api-gateway:8080/api/payment/approve`로 HTTP POST 전송
3. RRN 수신: 카드사가 응답으로 보내준 RRN 추출
4. 응답코드 확인: responseCode == '00'이면 APPROVED, 아니면 REJECTED
5. DB 저장: 결과를 van_transactions 테이블에 저장
6. 응답 반환: POS에 결과 반환

**왜 이렇게 설계했나요?**
- RRN은 카드사가 생성: 실제 카드 결제 표준(ISO 8583)에서 RRN은 카드사가 발급하는 고유번호
- BIN 조회를 통한 카드사 식별: 카드번호 앞 6자리로 카드사를 판별하는 것이 실제 VAN 시스템의 동작 방식
- Gateway 경유: 카드사 내부 서비스에 직접 접근하지 않고 Gateway를 통해 라우팅

**대안 비교**

| 방법 | 장점 | 단점 |
|------|------|------|
| RestTemplate (현재) | 간단하고 익숙한 동기 방식 | 블로킹 I/O, 타임아웃 시 스레드 점유 |
| WebClient (비동기) | 논블로킹, 높은 처리량 | Reactor 학습 필요, 복잡도 증가 |
| Feign Client | 선언적 HTTP 클라이언트, 코드 간결 | 의존성 추가 필요 |

**주의사항**
- 카드번호에 `-` 포함 시 카드사 validation 실패 (`카드번호는 16자리 숫자여야 합니다`)
- stan(시스템추적번호)은 UNIQUE 제약조건이 있어 동일한 stan으로 중복 요청 불가
- 카드사 요청 실패 시 REJECTED 처리 후 DB에 저장 (서비스 중단 없이 계속 동작)

---

## 5. Spring Batch - 배치 처리 로직

Spring Batch는 대량의 데이터를 안정적으로 처리하기 위한 프레임워크입니다.  
Reader → Processor → Writer 구조로 동작합니다.

### 5.1 배치 전체 흐름

```
매일 자정 @Scheduled 트리거 (또는 수동 실행 API)
  → AcquisitionItemReader: van_transactions에서 당일 APPROVED 거래 조회
  → AcquisitionItemProcessor: 각 거래를 CSV 한 줄로 변환 + 카드번호 마스킹
  → AcquisitionItemWriter: CSV 파일 생성 → Gateway 통해 카드사 전송
  → acquisition_batches 테이블에 배치 기록 저장
```

### 5.2 AcquisitionJobConfig - Job/Step 설정

**핵심 설정**
- `chunk(100)`: 100건씩 묶어서 처리. 메모리 효율적이고 중간 실패 시 해당 chunk부터 재시작 가능
- `@EnableScheduling`: 스케줄러 기능 활성화
- `@Scheduled(cron = "0 0 0 * * *")`: 매일 자정 자동 실행
- `JobParameters`에 time 추가: 같은 Job을 날마다 실행하기 위해 고유 파라미터 필요

**cron 표현식 설명**

| 자리 | 값 | 의미 |
|------|-----|------|
| 초 | 0 | 0초 |
| 분 | 0 | 0분 |
| 시 | 0 | 0시 (자정) |
| 일 | * | 매일 |
| 월 | * | 매월 |
| 요일 | * | 모든 요일 |

**왜 Spring Batch를 사용했나요?**
- 재시작 가능: 중간에 오류가 발생해도 실패한 지점부터 재시작 가능
- chunk 처리: 대량 데이터를 일정 단위로 나눠 메모리 효율적으로 처리
- 메타 테이블: 배치 실행 이력이 자동으로 DB에 기록
- 트랜잭션 관리: chunk 단위로 트랜잭션 처리

**단일 서버 기준 동작**
- `@Scheduled`는 각 서버 독립적으로 실행되어 다중 서버 환경에서 중복 실행 가능
- 현재 프로젝트는 단일 서버 환경을 가정하므로 문제 없음
- 다중 서버 환경에서는 ShedLock 도입으로 해결 가능

**대안 비교**

| 방법 | 특징 | 적합한 경우 |
|------|------|-------------|
| @Scheduled + 단순 반복문 | 구현 간단 | 소량 데이터, 실패 허용 |
| Spring Batch (현재) | 안정적, 재시작 가능 | 중량 데이터, 안정성 중요 |
| Quartz Scheduler | 클러스터 지원 | 다중 서버 환경 |
| Kubernetes CronJob | 컨테이너 기반 | K8s 환경 |

### 5.3 AcquisitionItemReader - 데이터 읽기

van_transactions 테이블에서 당일 승인된 거래를 조회합니다.

**동작 방식**
- 처음 `read()` 호출 시: 오늘 00:00 ~ 현재 시각 사이 APPROVED 거래 전체 조회
- Iterator로 하나씩 반환
- 모두 반환하면 null 반환 → Step 종료
- `reset()`: 배치 재실행 시 Iterator 초기화

> **주의**: 원래 전날 데이터를 읽도록 설계되었으나, 시연을 위해 당일 데이터를 읽도록 수정

**장단점**
- 장점: 전체 데이터를 한 번에 메모리에 올려서 처리 속도 빠름
- 단점: 데이터가 매우 많으면 OutOfMemoryError 가능
- 개선안: JpaPagingItemReader 사용하면 페이지 단위로 조회 가능

### 5.4 AcquisitionItemProcessor - 데이터 가공

VanTransaction 객체를 CSV 한 줄 문자열로 변환합니다.

**카드번호 마스킹 처리**
```
원본:   1234567890120003
마스킹: 123456******0003  (앞 6자리 + ****** + 뒤 4자리)
```

개인정보 보호를 위해 카드번호 중간 6자리를 마스킹합니다.

**CSV 컬럼 구조**

| 컬럼 | 설명 | 예시 |
|------|------|------|
| RRN | 거래 고유번호 | 26F890BC443D |
| STAN | 시스템 추적번호 | 123456 |
| CARD_NUMBER | 마스킹된 카드번호 | 123456******0003 |
| AMOUNT | 결제 금액 | 50000 |
| MERCHANT_ID | 가맹점 번호 | MERCHANT_001 |
| CARD_COMPANY | 카드사 이름 | UNKNOWN |
| APPROVAL_CODE | 승인번호 | 521216 |
| CREATED_AT | 거래 시각 | 2026-03-23T07:38:08 |

### 5.5 AcquisitionItemWriter - 파일 생성 및 전송

CSV 파일을 생성하고 카드사 settlement-service로 전송합니다.

**처리 순서**
1. CSV 파일 생성: `./csv/acquisition_yyyyMMdd.csv` 파일에 헤더 + 데이터 기록
2. acquisition_batches 테이블에 배치 기록 저장
3. multipart/form-data로 `http://api-gateway:8080/api/settlement/upload?batchDate=yyyy-MM-dd`로 전송

**파일 전송 방식**
- 전송 방식: HTTP multipart/form-data
- 파라미터: file (CSV 파일) + batchDate (QueryParam)
- URL: `http://api-gateway:8080/api/settlement/upload?batchDate=yyyy-MM-dd`

---

## 6. SSE (Server-Sent Events) - 실시간 정산 결과 수신

카드사가 CSV 대조 및 정산 처리를 완료한 후 VAN에 결과를 실시간으로 통보하는 기능입니다.

### 6.1 SSE 흐름

```
VAN → CSV 전송 (즉시 응답: SUCCESS)
  → GET /api/van/sse/subscribe/{batchDate}로 구독 (기다림)
  → 카드사 대조 완료
  → POST /api/van/sse/batch-result로 결과 전송
  → VAN SSE 구독자에게 실시간 전달
```

### 6.2 statusCode 종류

| statusCode | 의미 |
|------------|------|
| SUCCESS | 원장 대사 및 정산 입금까지 정상 완료 |
| COMPARE_FAILED | CSV와 원장 DB 대조 불일치 |
| SETTLEMENT_FAILED | 대사 성공 후 은행 이체 실패 |
| PROCESSING_FAILED | 처리 중 예외 발생 |

### 6.3 왜 SSE를 사용했나요?

카드사 정산 처리에 시간이 걸리기 때문에 즉각 응답이 불가능합니다.

| 방법 | 특징 | 단점 |
|------|------|------|
| SSE (현재) | 단방향 서버→클라이언트, 구현 간단 | 클라이언트→서버 불가 |
| Polling | 클라이언트가 주기적으로 조회 | 불필요한 요청 많음 |
| WebSocket | 양방향 통신 | 구현 복잡, 오버스펙 |
| Kafka/RabbitMQ | 메시지 큐, 안정적 | 별도 인프라 필요 |

**장단점**
- 장점: 구현이 간단하고 Spring MVC에 내장
- 장점: HTTP 기반이라 방화벽 문제 없음
- 장점: 카드사 → VAN 단방향 통보에 적합
- 단점: 서버 재시작 시 emitter 맵이 초기화됨
- 단점: 다중 서버 환경에서 공유 안 됨 (Redis Pub/Sub으로 해결 가능)

---

## 7. API 명세

### 결제 승인
```
POST /api/van/approve
Content-Type: application/json

Request:
{
    "cardNumber": "1234567890120003",  // 하이픈(-) 없이 16자리
    "amount": 50000,
    "merchantId": "MERCHANT_001",
    "stan": "123456"                   // 매 요청마다 다른 값 (UNIQUE)
}

Response (성공):
{
    "rrn": "26F890BC443D",
    "approvalCode": "521216",
    "responseCode": "00",
    "status": "APPROVED",
    "message": "승인 완료"
}

Response (실패):
{
    "rrn": "ERR88534",
    "approvalCode": null,
    "responseCode": "99",
    "status": "REJECTED",
    "message": "승인 거절"
}
```

### 배치 수동 실행
```
POST /api/van/batch/run

Response: "배치 실행 완료"
```

### SSE 구독
```
GET /api/van/sse/subscribe/{batchDate}
예: GET /api/van/sse/subscribe/2026-03-23
```

### 배치 결과 수신 (카드사 → VAN)
```
POST /api/van/sse/batch-result
Content-Type: application/json

{
    "batchDate": "2026-03-23",
    "statusCode": "SUCCESS",
    "message": "정산 완료"
}
```

---

## 8. 테스트 시나리오 및 실행 순서

### 8.1 전체 실행 순서

**1단계 - 카드사 시스템 실행**
```bash
cd ~/Desktop/fisa-card/fisa-card-company
docker-compose up -d
```

**2단계 - VAN 시스템 실행**
```bash
cd ~/Desktop/fisa-card/fisa-van
docker-compose up -d --build
```

**3단계 - Eureka 대시보드 확인**
```
http://localhost:8761
→ GATEWAY, VAN, PAYMENT-SERVICE, BANKING-SERVICE, SETTLEMENT-SERVICE 등록 확인
```

---

### 8.2 결제 테스트

**체크카드 결제**
```json
POST http://localhost:8081/api/van/approve

{
    "cardNumber": "1234567890120003",
    "amount": 50000,
    "merchantId": "MERCHANT_001",
    "stan": "100001"
}
```

**신용카드 결제**
```json
POST http://localhost:8081/api/van/approve

{
    "cardNumber": "1234567890120001",
    "amount": 300000,
    "merchantId": "MERCHANT_001",
    "stan": "100002"
}
```

> 테스트 카드 데이터 (shared_master DB card_master 테이블)
> - `1234567890120001`: 신용카드, 한도 100만원
> - `1234567890120002`: 신용카드, 한도 50만원 (45만원 사용 중)
> - `1234567890120003`: 체크카드

---

### 8.3 배치 테스트

**1. SSE 구독 먼저 열기**

브라우저에서:
```
http://localhost:8081/api/van/sse/subscribe/2026-03-23
```
이 탭을 열어두면 카드사 정산 결과가 실시간으로 표시됩니다.

**2. 결제 데이터 여러 건 생성**

위 결제 테스트를 stan 값 바꿔가며 3~5건 실행

**3. van_transactions 데이터 확인**
```bash
docker exec -it van-db mysql -u root -p1234 van_db -e "SELECT * FROM van_transactions;"
```

**4. 배치 수동 실행**
```
POST http://localhost:8081/api/van/batch/run
```

**5. CSV 파일 생성 확인**
```bash
docker exec -it van ls ./csv/
docker exec -it van cat ./csv/acquisition_20260323.csv
```

**6. SSE 결과 확인**

브라우저에서 열어둔 SSE 탭에 카드사 정산 결과가 오면 성공!

---

## 9. DB 스키마

```sql
CREATE TABLE van_transactions (
    van_tx_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    rrn           VARCHAR(20) UNIQUE NOT NULL,
    stan          VARCHAR(6) NOT NULL,
    card_number   VARCHAR(19) NOT NULL,
    amount        BIGINT NOT NULL,
    merchant_id   VARCHAR(15) NOT NULL,
    card_company  VARCHAR(10),
    response_code VARCHAR(2),
    approval_code VARCHAR(6),
    status        VARCHAR(10),
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE acquisition_batches (
    batch_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_date   DATE NOT NULL,
    card_company VARCHAR(10),
    total_count  INT DEFAULT 0,
    total_amount BIGINT DEFAULT 0,
    file_name    VARCHAR(100),
    status       VARCHAR(10),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE card_bins (
    bin_prefix   VARCHAR(6) PRIMARY KEY,
    company_name VARCHAR(20),
    api_endpoint VARCHAR(100)
);
```

---

## 10. Docker 구성

### docker-compose.yml 구조

| 서비스 | 포트 | 설명 |
|--------|------|------|
| van-db | 3307:3306 | VAN MySQL DB |
| van | 8081:8081 | VAN 애플리케이션 |

### 네트워크 설정

VAN은 두 개의 네트워크에 연결:
- `van-net`: VAN과 van-db 간 내부 통신
- `fisa-card-company_card-net`: 카드사 시스템과 통신 (Eureka, Gateway, payment-service 등)

### Dockerfile (멀티스테이지 빌드)

```dockerfile
# 1단계: 빌드
FROM amazoncorretto:17-alpine AS build
WORKDIR /app
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
COPY src ./src
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test --no-daemon

# 2단계: 실행
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

멀티스테이지 빌드를 사용하여 최종 이미지 크기를 줄이고 빌드 도구가 포함되지 않도록 합니다.

---

## 11. 개선 가능한 점

- **타임아웃/재시도 정책**: RestTemplate에 타임아웃 설정 추가 필요
- **SSE 다중 서버**: Redis Pub/Sub으로 교체하면 다중 서버 환경 지원
- **배치 페이징**: JpaPagingItemReader로 교체하면 대용량 데이터 안전 처리
- **배치 중복 실행 방지**: ShedLock 도입으로 다중 서버 환경에서 중복 실행 방지
- **카드번호 암호화**: DB 저장 시 카드번호 암호화 처리
- **로그 추적**: TraceId를 MDC에 저장하여 로그에 자동 포함
