package com.fisa.van.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "van_transactions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VanTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long vanTxId;

    @Column(unique = true, nullable = false, length = 20)
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

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}