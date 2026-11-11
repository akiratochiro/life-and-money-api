package com.akiratochiro.life_and_money_api.taxparam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

interface IrrfBracketRepository extends JpaRepository<IrrfBracketRow, Long> {
    List<IrrfBracketRow> findAllByValidFromLessThanEqual(LocalDate date);
}
