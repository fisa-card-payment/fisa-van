package com.fisa.van.controller;

import com.fisa.van.batch.AcquisitionItemReader;
import com.fisa.van.dto.PaymentRequestDto;
import com.fisa.van.dto.PaymentResponseDto;
import com.fisa.van.service.VanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/van")
@RequiredArgsConstructor
public class VanController {

    private final VanService vanService;

    @Autowired
    private JobLauncher jobLauncher;

    @Autowired
    private Job acquisitionJob;

    @Autowired
    private AcquisitionItemReader acquisitionItemReader;

    @PostMapping("/approve")
    public ResponseEntity<PaymentResponseDto> approve(@RequestBody PaymentRequestDto request) {
        log.info("[VAN] 승인 요청 - 가맹점: {}, 금액: {}", request.getMerchantId(), request.getAmount());
        PaymentResponseDto response = vanService.processPayment(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/batch/run")
    public ResponseEntity<String> runBatch() throws Exception {
        acquisitionItemReader.reset();
        JobParameters params = new JobParametersBuilder()
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();
        jobLauncher.run(acquisitionJob, params);
        return ResponseEntity.ok("배치 실행 완료");
    }
}

//    // 취소/환불 요청
//    @PostMapping("/cancel")
//    public ResponseEntity<PaymentResponseDto> cancel(@RequestBody CancelRequestDto request) {
//        log.info("[VAN] 취소 요청 - RRN: {}", request.getRrn());
//        PaymentResponseDto response = vanService.processCancel(request);
//        return ResponseEntity.ok(response);
//    }