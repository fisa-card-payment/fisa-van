package com.fisa.van.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "acquisition_batches")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcquisitionBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long batchId;

    @Column(nullable = false)
    private LocalDate batchDate;

    @Column(length = 10)
    private String cardCompany;

    @Builder.Default
    private Integer totalCount = 0;

    @Builder.Default
    private Long totalAmount = 0L;

    @Column(length = 100)
    private String fileName;

    @Column(length = 10)
    private String status;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}