package com.invest.management.moex;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface MoexStockRepository extends JpaRepository<MoexStock, Long> {

    Optional<MoexStock> findBySecid(String secid);
    
    Optional<MoexStock> findByIsin(String isin);
    
    @Query("SELECT MIN(s.updatedAt) FROM MoexStock s")
    Optional<OffsetDateTime> findMinUpdatedAt();
    
    @Query("SELECT COUNT(s) FROM MoexStock s")
    long countAll();
    
    @Query("SELECT COUNT(s) FROM MoexStock s WHERE s.listlevel = 2")
    long countByListLevel2();
}

