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

    // 카드사가 결과 보낼 때 받는 엔드포인트
    @PostMapping("/batch-result")
    public void receiveBatchResult(
            @RequestParam String batchDate,
            @RequestParam String result) {

        log.info("[SSE] 배치 결과 수신 - 날짜: {}, 결과: {}", batchDate, result);

        SseEmitter emitter = emitters.get(batchDate);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("batch-result")
                        .data(result));
                emitter.complete();
                emitters.remove(batchDate);
            } catch (IOException e) {
                log.error("[SSE] 전송 실패: {}", e.getMessage());
                emitters.remove(batchDate);
            }
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