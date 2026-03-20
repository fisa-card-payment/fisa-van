package com.fisa.van.repository;

import com.fisa.van.domain.CardBin;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CardBinRepository extends JpaRepository<CardBin, String> {
    Optional<CardBin> findByBinPrefix(String binPrefix);
}