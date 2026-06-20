package com.hotevent.repository;

import com.hotevent.entity.FrontendLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FrontendLogRepository extends JpaRepository<FrontendLog, Long>, JpaSpecificationExecutor<FrontendLog> {

    Page<FrontendLog> findByLogLevel(String logLevel, Pageable pageable);

    Page<FrontendLog> findByUserId(Long userId, Pageable pageable);

    List<FrontendLog> findByLogTimeBetweenOrderByLogTimeAsc(LocalDateTime startTime, LocalDateTime endTime);

    long countByLogLevel(String logLevel);

    @Modifying
    @Query("DELETE FROM FrontendLog fl WHERE fl.logTime >= :start AND fl.logTime < :end")
    void deleteByLogTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT DISTINCT fl.logLevel FROM FrontendLog fl ORDER BY fl.logLevel")
    List<String> findDistinctLogLevels();
}
