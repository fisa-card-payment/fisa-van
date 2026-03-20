package com.fisa.van.repository;

import com.fisa.van.domain.AcquisitionBatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface AcquisitionBatchRepository extends JpaRepository<AcquisitionBatch, Long> {
    List<AcquisitionBatch> findByBatchDate(LocalDate batchDate);
}