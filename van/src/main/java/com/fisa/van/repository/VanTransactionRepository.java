package com.fisa.van.repository;

import com.fisa.van.domain.VanTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface VanTransactionRepository extends JpaRepository<VanTransaction, Long> {
    Optional<VanTransaction> findByRrn(String rrn);
    List<VanTransaction> findByCreatedAtBetweenAndStatus(
            LocalDateTime start, LocalDateTime end, String status);
}