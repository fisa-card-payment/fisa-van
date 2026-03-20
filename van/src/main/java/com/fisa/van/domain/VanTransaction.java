package com.fisa.van.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity // 이 클래스가 DB 테이블과 매핑된다는 선언
@Table(name = "van_transactions") // 매핑할 테이블 이름
@Getter
@NoArgsConstructor
public class VanTransaction {

    @Id // Primary Key
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vanTxId;

    @Column(unique = true, nullable = false, length = 12) // 컬럼 세부 설정
    private String rrn;

    @Column(nullable = false, length = 6)
    private String stan;

    @Column(nullable = false, length = 19)
    private String cardNumber;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 15)
    private String merchantId;

    @Column(length = 10)
    private String cardCompany;

    @Column(length = 2)
    private String responseCode;

    @Column(length = 6)
    private String approvalCode;

    @Column(length = 10)
    private String status;

    private LocalDateTime createdAt;

    @PrePersist // DB에 저장되기 직전에 실행 → createdAt 자동 세팅
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}