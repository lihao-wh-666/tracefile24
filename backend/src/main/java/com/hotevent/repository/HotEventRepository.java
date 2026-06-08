package com.hotevent.repository;

import com.hotevent.entity.HotEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface HotEventRepository extends JpaRepository<HotEvent, Long> {

    Page<HotEvent> findBySourceAndDeletedFalse(String source, Pageable pageable);

    Page<HotEvent> findByDeletedFalse(Pageable pageable);

    Optional<HotEvent> findBySourceAndTitleAndDeletedFalse(String source, String title);

    List<HotEvent> findBySourceAndCrawlTimeAfterAndDeletedFalse(String source, LocalDateTime crawlTime);

    @Query("SELECT DISTINCT h.source FROM HotEvent h WHERE h.deleted = false")
    List<String> findDistinctSources();

    @Query("SELECT h.source, COUNT(h) FROM HotEvent h WHERE h.deleted = false GROUP BY h.source")
    List<Object[]> countBySource();

    @Query("SELECT h.category, COUNT(h) FROM HotEvent h WHERE h.deleted = false AND h.category IS NOT NULL GROUP BY h.category")
    List<Object[]> countByCategory();

    @Query("SELECT h FROM HotEvent h WHERE h.deleted = false AND h.crawlTime >= :startTime ORDER BY h.hotValue DESC")
    List<HotEvent> findTopHotEvents(@Param("startTime") LocalDateTime startTime, Pageable pageable);

    Page<HotEvent> findByTitleContainingAndDeletedFalse(String keyword, Pageable pageable);

    @Query("SELECT h FROM HotEvent h WHERE h.deleted = false AND h.source = :source " +
           "AND h.crawlTime >= :startTime AND h.crawlTime <= :endTime ORDER BY h.hotRank ASC")
    List<HotEvent> findBySourceAndTimeRange(@Param("source") String source,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);
}
