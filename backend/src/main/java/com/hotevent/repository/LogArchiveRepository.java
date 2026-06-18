package com.hotevent.repository;

import com.hotevent.entity.LogArchive;
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
public interface LogArchiveRepository extends JpaRepository<LogArchive, Long>, JpaSpecificationExecutor<LogArchive> {

    Page<LogArchive> findByStatus(String status, Pageable pageable);

    List<LogArchive> findByStatusOrderByCreateTimeDesc(String status);

    @Query("SELECT la FROM LogArchive la WHERE la.startTime >= :start AND la.endTime <= :end")
    List<LogArchive> findByTimeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Modifying
    @Query("DELETE FROM LogArchive la WHERE la.id = :id")
    void deleteByIdCustom(@Param("id") Long id);

    boolean existsByStatus(String status);

    long countByStatus(String status);
}
