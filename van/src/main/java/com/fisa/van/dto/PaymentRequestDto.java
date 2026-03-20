package com.fisa.van.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentRequestDto {
    private String cardNumber;   // 카드번호
    private Long amount;         // 결제금액
    private String merchantId;   // 가맹점번호
    private String stan;         // 시스템추적번호
}