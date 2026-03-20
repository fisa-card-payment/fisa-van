package com.fisa.van.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity // 이 클래스가 DB 테이블과 매핑된다는 선언
@Table(name = "acquisition_batches") // 매핑할 테이블 이름
@Getter
@NoArgsConstructor
public class AcquisitionBatch {

    @Id // Primary Key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // AUTO_INCREMENT
    private Long batchId;

    @Column(nullable = false) // 컬럼 세부 설정
    private LocalDate batchDate;

    @Column(length = 10)
    private String cardCompany;

    private Integer totalCount = 0;
    private Long totalAmount = 0L;

    @Column(length = 100)
    private String fileName;

    @Column(length = 10)
    private String status;

    private LocalDateTime createdAt;

    @PrePersist // DB에 저장되기 직전에 실행 → createdAt 자동 세팅
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}