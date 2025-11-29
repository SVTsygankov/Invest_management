package com.invest.management.moex.bond;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BondRepository extends JpaRepository<Bond, Long> {

    Optional<Bond> findBySecid(String secid);
    
    Optional<Bond> findByIsin(String isin);
}

