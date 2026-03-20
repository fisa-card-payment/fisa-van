package com.fisa.van.service;

import com.fisa.van.domain.CardBin;
import com.fisa.van.domain.VanTransaction;
import com.fisa.van.dto.CancelRequestDto;
import com.fisa.van.dto.PaymentRequestDto;
import com.fisa.van.dto.PaymentResponseDto;
import com.fisa.van.repository.CardBinRepository;
import com.fisa.van.repository.VanTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VanService {

    private final VanTransactionRepository vanTransactionRepository;
    private final CardBinRepository cardBinRepository;
    private final RestTemplate restTemplate;

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequestDto request) {
        // 1. RRN 생성 (거래 고유번호 12자리)
        String rrn = generateRrn();

        // 2. BIN 조회로 카드사 찾기
        String binPrefix = request.getCardNumber().replaceAll("-", "").substring(0, 6);
        CardBin cardBin = cardBinRepository.findByBinPrefix(binPrefix)
                .orElse(null);

        String cardCompany = cardBin != null ? cardBin.getCompanyName() : "UNKNOWN";
        String cardCompanyEndpoint = cardBin != null ? cardBin.getApiEndpoint() : null;

        // 3. 카드사로 승인 요청 (Gateway 경유)
        PaymentResponseDto cardResponse = null;
        if (cardCompanyEndpoint != null) {
            try {
                cardResponse = restTemplate.postForObject(
                        cardCompanyEndpoint,
                        request,
                        PaymentResponseDto.class
                );
            } catch (Exception e) {
                log.error("[VAN] 카드사 요청 실패: {}", e.getMessage());
            }
        }

        // 4. 응답 처리
        String status = (cardResponse != null && "00".equals(cardResponse.getResponseCode()))
                ? "APPROVED" : "REJECTED";
        String approvalCode = cardResponse != null ? cardResponse.getApprovalCode() : null;
        String responseCode = cardResponse != null ? cardResponse.getResponseCode() : "99";

        // 5. DB 저장
        VanTransaction tx = VanTransaction.builder()
                .rrn(rrn)
                .stan(request.getStan())
                .cardNumber(request.getCardNumber())
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .cardCompany(cardCompany)
                .responseCode(responseCode)
                .approvalCode(approvalCode)
                .status(status)
                .build();

        vanTransactionRepository.save(tx);
        log.info("[VAN] 거래 저장 완료 - RRN: {}, STATUS: {}", rrn, status);

        return PaymentResponseDto.builder()
                .rrn(rrn)
                .approvalCode(approvalCode)
                .responseCode(responseCode)
                .status(status)
                .message(status.equals("APPROVED") ? "승인 완료" : "승인 거절")
                .build();
    }

    @Transactional
    public PaymentResponseDto processCancel(CancelRequestDto request) {
        // 1. 원거래 조회
        VanTransaction original = vanTransactionRepository.findByRrn(request.getRrn())
                .orElseThrow(() -> new IllegalArgumentException("원거래를 찾을 수 없습니다: " + request.getRrn()));

        // 2. 카드사로 취소 요청 (TODO: 카드사 API 연동)
        log.info("[VAN] 취소 요청 - RRN: {}", request.getRrn());

        // 3. 취소 거래 저장
        String cancelRrn = generateRrn();
        VanTransaction cancelTx = VanTransaction.builder()
                .rrn(cancelRrn)
                .stan(original.getStan())
                .cardNumber(original.getCardNumber())
                .amount(request.getAmount())
                .merchantId(request.getMerchantId())
                .cardCompany(original.getCardCompany())
                .responseCode("00")
                .status("CANCELLED")
                .build();

        vanTransactionRepository.save(cancelTx);

        return PaymentResponseDto.builder()
                .rrn(cancelRrn)
                .responseCode("00")
                .status("CANCELLED")
                .message("취소 완료")
                .build();
    }

    private String generateRrn() {
        return UUID.randomUUID().toString().replaceAll("-", "").substring(0, 12).toUpperCase();
    }
}