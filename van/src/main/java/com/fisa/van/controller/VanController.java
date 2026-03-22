package com.fisa.van.controller;

import com.fisa.van.dto.CancelRequestDto;
import com.fisa.van.dto.PaymentRequestDto;
import com.fisa.van.dto.PaymentResponseDto;
import com.fisa.van.service.VanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/van")
@RequiredArgsConstructor
public class VanController {

    private final VanService vanService;

    // 결제 승인 요청
    @PostMapping("/approve")
    public ResponseEntity<PaymentResponseDto> approve(@RequestBody PaymentRequestDto request) {
        log.info("[VAN] 승인 요청 - 가맹점: {}, 금액: {}", request.getMerchantId(), request.getAmount());
        PaymentResponseDto response = vanService.processPayment(request);
        return ResponseEntity.ok(response);
    }

//    // 취소/환불 요청
//    @PostMapping("/cancel")
//    public ResponseEntity<PaymentResponseDto> cancel(@RequestBody CancelRequestDto request) {
//        log.info("[VAN] 취소 요청 - RRN: {}", request.getRrn());
//        PaymentResponseDto response = vanService.processCancel(request);
//        return ResponseEntity.ok(response);
//    }
}