package com.retailpos.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MarketCrashSnapshotRepository extends JpaRepository<MarketCrashSnapshot, Long> {
    List<MarketCrashSnapshot> findByCrashCode(String crashCode);
    void deleteByCrashCode(String crashCode);
}
