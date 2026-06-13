package com.hotevent.repository;

import com.hotevent.entity.CrawlRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CrawlRecordRepository extends JpaRepository<CrawlRecord, Long> {

    Page<CrawlRecord> findBySource(String source, Pageable pageable);

    List<CrawlRecord> findBySourceAndCrawlTimeAfterOrderByCrawlTimeDesc(String source, LocalDateTime crawlTime);

    @Query("SELECT c.source, COUNT(c), SUM(c.successCount), SUM(c.failCount) " +
           "FROM CrawlRecord c WHERE c.crawlTime >= :startTime GROUP BY c.source")
    List<Object[]> getCrawlStatistics(@Param("startTime") LocalDateTime startTime);

    List<CrawlRecord> findByCrawlTimeAfterOrderByCrawlTimeDesc(LocalDateTime crawlTime);

    java.util.Optional<CrawlRecord> findTopBySourceOrderByCrawlTimeDesc(String source);
}
