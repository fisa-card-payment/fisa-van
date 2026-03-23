package com.fisa.van.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VAN이 CSV 전송 ->
 * GET /api/van/sse/subscribe/2026-03-20 으로 기다림 ->
 * 카드사가 대조 완료 후 POST /api/van/sse/batch-result?batchDate=2026-03-20&result=OK ->
 * VAN SSE로 결과 수신
 *
 * 배치 결과 전송 엔드포인트 나중에 카드사 쪽 받아서 수정하기
 */
@Slf4j
@RestController
@RequestMapping("/api/van/sse")
public class SseController {

    // batchDate별로 SSE 연결 관리
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public record BatchResultRequest(
            String batchDate,
            String statusCode,
            String message
    ) {}

    // 카드사가 결과 보낼 때 받는 엔드포인트 -> json 수정
    @PostMapping("/batch-result")
    public void receiveBatchResult(@RequestBody BatchResultRequest resultRequest) {

        log.info("[SSE] 배치 결과 수신 - 날짜: {}, 상태: {}, 메시지: {}",
                resultRequest.batchDate(), resultRequest.statusCode(), resultRequest.message());

        SseEmitter emitter = emitters.get(resultRequest.batchDate());

        if (emitter != null) {
            try {
                // 1. 구독 중인 VAN 관리자에게 JSON 데이터 전송
                emitter.send(SseEmitter.event()
                        .name("batch-result")
                        .data(resultRequest));

                // 2. 최종 상태(SUCCESS, FAILED 시리즈)라면 연결 종료 및 맵에서 제거
                if (!"PROCESSING".equals(resultRequest.statusCode())) {
                    emitter.complete();
                    emitters.remove(resultRequest.batchDate());
                }

            } catch (IOException e) {
                log.error("[SSE] 전송 중 오류 발생: {}", e.getMessage());
                emitters.remove(resultRequest.batchDate());
            }
        } else {
            log.warn("[SSE] 해당 날짜({})에 대한 활성화된 구독이 없습니다.", resultRequest.batchDate());
        }
    }

    // VAN 관리자가 결과 기다릴 때 구독하는 엔드포인트
    @GetMapping(value = "/subscribe/{batchDate}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String batchDate) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30분 타임아웃
        emitters.put(batchDate, emitter);

        emitter.onCompletion(() -> emitters.remove(batchDate));
        emitter.onTimeout(() -> emitters.remove(batchDate));

        log.info("[SSE] 구독 시작 - 날짜: {}", batchDate);
        return emitter;
    }
}