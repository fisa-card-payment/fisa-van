# FISA VAN Service

카드 결제 시스템의 VAN(Value Added Network) 서비스입니다.
POS 단말기의 결제 요청을 수신하여 카드사 API Gateway로 전달하고, 결과를 반환합니다.

## 기술 스택

- Java 17
- Spring Boot 3.5.12
- Spring Batch
- Spring Data JPA
- MySQL 8.0
- Docker
- Eureka Client

## 서비스 포트

| 서비스 | 포트 |
|--------|------|
| VAN | 8081 |
| VAN DB | 3307 |

## 주요 기능

### 1. 실시간 결제 승인
- POS로부터 결제 요청 수신
- BIN 테이블 기반 카드사 식별
- 카드사 API Gateway로 승인 요청 전달
- 승인/거절 결과 반환 및 DB 저장

### 2. 배치 처리 (매일 자정)
- 전일 승인 내역 조회
- CSV 파일 생성 (카드번호 마스킹 처리)
- 카드사 매입/정산 서비스로 전송

### 3. SSE (Server-Sent Events)
- 카드사 정산 결과 실시간 수신
- 배치 처리 결과 구독 기능

## API 명세

### 결제 승인 요청
```
POST /api/van/approve
Content-Type: application/json

{
    "cardNumber": "1234-5678-9012-0003",
    "amount": 50000,
    "merchantId": "MERCHANT_001",
    "stan": "123456"
}
```

### 결제 응답
```
{
    "rrn": "4CCCD3168CA8",
    "approvalCode": "025383",
    "responseCode": "00",
    "status": "APPROVED",
    "message": "승인 완료"
}
```

### 배치 결과 구독 (SSE)
```
GET /api/van/sse/subscribe/{batchDate}
```

### 배치 결과 수신
```
POST /api/van/sse/batch-result
Content-Type: application/json

{
    "batchDate": "2026-03-23",
    "statusCode": "SUCCESS",
    "message": "정산 완료"
}
```

## CSV 명세

### 전송 방식
`multipart/form-data`

### 컬럼 구조
```
RRN,STAN,CARD_NUMBER,AMOUNT,MERCHANT_ID,CARD_COMPANY,APPROVAL_CODE,CREATED_AT
```

### 예시
```
6C2B740A7E58,123456,412345******2345,50000,MERCHANT001,신한,AB1234,2026-03-20T10:00:00
```

> 카드번호는 앞 6자리 + ****** + 뒤 4자리로 마스킹 처리됩니다.

## 실행 방법

### 환경변수 설정
루트 디렉토리에 `.env` 파일 생성:
```
DB_PASSWORD=비밀번호
DB_USERNAME=root
DB_URL=jdbc:mysql://van-db:3306/van_db?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
EUREKA_URL=http://eureka-server:8761/eureka/
```

### Docker 실행
```bash
# 빌드
cd van
./gradlew build -x test

# 실행
cd ..
docker-compose up -d
```

### 실행 순서
1. Eureka Server 실행
2. 카드사 시스템 실행 (fisa-card-company)
3. VAN 실행

## DB 스키마
```sql
CREATE TABLE van_transactions (
    van_tx_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    rrn          VARCHAR(20) UNIQUE NOT NULL,
    stan         VARCHAR(6) NOT NULL,
    card_number  VARCHAR(19) NOT NULL,
    amount       BIGINT NOT NULL,
    merchant_id  VARCHAR(15) NOT NULL,
    card_company VARCHAR(10),
    response_code VARCHAR(2),
    approval_code VARCHAR(6),
    status       VARCHAR(10),
    created_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

## 전체 시스템 흐름
```
POS → VAN(8081) → API Gateway(8080) → Payment Service(8082)
                                     → Settlement Service(8084)
```
