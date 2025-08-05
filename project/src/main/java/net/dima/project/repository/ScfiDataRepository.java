package net.dima.project.repository;

import net.dima.project.entity.ScfiData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScfiDataRepository extends JpaRepository<ScfiData, Long> {
    // 가장 최신 데이터부터 52개(약 1년치)를 날짜 오름차순으로 조회
    List<ScfiData> findByOrderByRecordDateAsc();
    
    // [추가] 가장 최신 데이터 2개를 날짜 내림차순으로 조회
    List<ScfiData> findTop2ByOrderByRecordDateDesc();
    // [추가] 날짜로 데이터를 조회하는 메서드
    Optional<ScfiData> findByRecordDate(LocalDate recordDate);
}