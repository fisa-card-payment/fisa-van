package com.fisa.van.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentResponseDto {
    private String rrn;           // 거래고유번호
    private String approvalCode;  // 승인번호
    private String responseCode;  // 응답코드 (00: 승인, 기타: 거절)
    private String status;        // APPROVED / REJECTED
    private String message;       // 응답 메시지
}