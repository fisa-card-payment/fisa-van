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

@Slf4j
@Service
@RequiredArgsConstructor
public class VanService {

    private final VanTransactionRepository vanTransactionRepository;
    private final CardBinRepository cardBinRepository;
    private final RestTemplate restTemplate;

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequestDto request) {
        // 1. BIN 조회로 카드사 이름 찾기
        String binPrefix = request.getCardNumber().replaceAll("-", "").substring(0, 6);
        CardBin cardBin = cardBinRepository.findByBinPrefix(binPrefix).orElse(null);
        String cardCompany = cardBin != null ? cardBin.getCompanyName() : "UNKNOWN";

        // 2. 카드사로 승인 요청 (Gateway 경유) - 단일 엔드포인트
        String cardCompanyEndpoint = "http://localhost:8080/api/payment/approve";
        log.info("[VAN] 결제 요청 - 가맹점: {}, 금액: {}", request.getMerchantId(), request.getAmount());

        PaymentResponseDto cardResponse = null;
        try {
            cardResponse = restTemplate.postForObject(
                    cardCompanyEndpoint,
                    request,
                    PaymentResponseDto.class
            );
        } catch (Exception e) {
            log.error("[VAN] 카드사 요청 실패: {}", e.getMessage());
        }

        // 3. 카드사 응답에서 RRN 꺼내기
        String rrn = (cardResponse != null && cardResponse.getRrn() != null)
                ? cardResponse.getRrn()
                : "UNKNOWN_" + System.currentTimeMillis();

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

//    @Transactional
//    public PaymentResponseDto processCancel(CancelRequestDto request) {
//        // 1. 원거래 조회
//        VanTransaction original = vanTransactionRepository.findByRrn(request.getRrn())
//                .orElseThrow(() -> new IllegalArgumentException("원거래를 찾을 수 없습니다: " + request.getRrn()));
//
//        log.info("[VAN] 취소 요청 - RRN: {}", request.getRrn());
//
//        // 2. 카드사로 취소 요청 (Gateway 경유)
//        PaymentResponseDto cardResponse = null;
//        try {
//            cardResponse = restTemplate.postForObject(
//                    "http://localhost:8080/payment/cancel",
//                    request,
//                    PaymentResponseDto.class
//            );
//        } catch (Exception e) {
//            log.error("[VAN] 카드사 취소 요청 실패: {}", e.getMessage());
//        }
//
//        // 3. 카드사 응답에서 RRN 꺼내기
//        String cancelRrn = (cardResponse != null && cardResponse.getRrn() != null)
//                ? cardResponse.getRrn()
//                : "UNKNOWN_" + System.currentTimeMillis();
//
//        // 4. 취소 거래 저장
//        String cancelStatus = (cardResponse != null && "00".equals(cardResponse.getResponseCode()))
//                ? "CANCELLED" : "CANCEL_FAILED";
//
//        VanTransaction cancelTx = VanTransaction.builder()
//                .rrn(cancelRrn)
//                .stan(original.getStan())
//                .cardNumber(original.getCardNumber())
//                .amount(request.getAmount())
//                .merchantId(request.getMerchantId())
//                .cardCompany(original.getCardCompany())
//                .responseCode(cardResponse != null ? cardResponse.getResponseCode() : "99")
//                .status(cancelStatus)
//                .build();
//
//        vanTransactionRepository.save(cancelTx);
//
//        return PaymentResponseDto.builder()
//                .rrn(cancelRrn)
//                .responseCode(cardResponse != null ? cardResponse.getResponseCode() : "99")
//                .status(cancelStatus)
//                .message(cancelStatus.equals("CANCELLED") ? "취소 완료" : "취소 실패")
//                .build();
//    }
}