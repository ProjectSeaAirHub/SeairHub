package net.dima.project.repository;

import net.dima.project.entity.ScfiData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScfiDataRepository extends JpaRepository<ScfiData, Long> {
    // 가장 최신 데이터부터 52개(약 1년치)를 날짜 오름차순으로 조회
    List<ScfiData> findTop52ByOrderByRecordDateAsc();
}