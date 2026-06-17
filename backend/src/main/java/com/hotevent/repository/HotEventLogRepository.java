package com.hotevent.repository;

import com.hotevent.entity.HotEventLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HotEventLogRepository extends JpaRepository<HotEventLog, Long>, JpaSpecificationExecutor<HotEventLog> {

    Page<HotEventLog> findByEventId(Long eventId, Pageable pageable);

    List<HotEventLog> findByEventIdOrderByOperationTimeDesc(Long eventId);

    @Query("SELECT DISTINCT h.source FROM HotEventLog h WHERE h.source IS NOT NULL ORDER BY h.source")
    List<String> findDistinctSources();

    @Query("SELECT DISTINCT h.operatorName FROM HotEventLog h WHERE h.operatorName IS NOT NULL ORDER BY h.operatorName")
    List<String> findDistinctOperators();
}
