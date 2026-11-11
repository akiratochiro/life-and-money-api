package com.akiratochiro.life_and_money_api.taxparam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

interface SimplesBracketRepository extends JpaRepository<SimplesBracketRow, Long> {
    List<SimplesBracketRow> findAllByValidFromLessThanEqual(LocalDate date);
}
