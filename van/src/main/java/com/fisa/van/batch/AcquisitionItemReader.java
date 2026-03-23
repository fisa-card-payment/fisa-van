package com.fisa.van.batch;

import com.fisa.van.domain.VanTransaction;
import com.fisa.van.repository.VanTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemReader;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AcquisitionItemReader implements ItemReader<VanTransaction> {

    private final VanTransactionRepository vanTransactionRepository;
    private Iterator<VanTransaction> iterator;

    @Override
    public VanTransaction read() {
        if (iterator == null) {
            // 전날 00:00 ~ 23:59 APPROVED 거래 조회
            // LocalDateTime start = LocalDateTime.now().minusDays(1).toLocalDate().atStartOfDay();
            // LocalDateTime end = start.plusDays(1).minusSeconds(1);

            LocalDateTime start = LocalDateTime.now().toLocalDate().atStartOfDay();
            LocalDateTime end = LocalDateTime.now();

            List<VanTransaction> transactions =
                    vanTransactionRepository.findByCreatedAtBetweenAndStatus(start, end, "APPROVED");

            log.info("[BATCH] 대상 거래 수: {}", transactions.size());
            iterator = transactions.iterator();
        }

        return iterator.hasNext() ? iterator.next() : null;
    }

    // 배치 실행할 때마다 iterator 초기화
    public void reset() {
        this.iterator = null;
    }
}