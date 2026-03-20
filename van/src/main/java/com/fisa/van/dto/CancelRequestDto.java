package com.fisa.van.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CancelRequestDto {
    private String rrn;         // 원거래 고유번호
    private String merchantId;  // 가맹점번호
    private Long amount;        // 취소금액
}