package com.hotevent.service;

import com.hotevent.common.PageResult;
import com.hotevent.entity.FrontendLog;
import com.hotevent.repository.FrontendLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class FrontendLogService {

    @Autowired
    private FrontendLogRepository frontendLogRepository;

    @Transactional
    public FrontendLog saveLog(FrontendLog frontendLog) {
        return frontendLogRepository.save(frontendLog);
    }

    @Transactional
    public List<FrontendLog> saveLogs(List<FrontendLog> logs) {
        return frontendLogRepository.saveAll(logs);
    }

    public PageResult<FrontendLog> getLogs(String logLevel, Long userId, String username,
                                            LocalDateTime startTime, LocalDateTime endTime,
                                            int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "logTime"));
        Specification<FrontendLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (logLevel != null && !logLevel.isEmpty()) {
                predicates.add(cb.equal(root.get("logLevel"), logLevel));
            }

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }

            if (username != null && !username.isEmpty()) {
                predicates.add(cb.like(root.get("username"), "%" + username + "%"));
            }

            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("logTime"), startTime));
            }

            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("logTime"), endTime));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<FrontendLog> logPage = frontendLogRepository.findAll(spec, pageable);
        return PageResult.of(logPage.getContent(), logPage.getTotalElements(), page, size);
    }

    public Optional<FrontendLog> getLogById(Long id) {
        return frontendLogRepository.findById(id);
    }

    public List<String> getAvailableLevels() {
        return frontendLogRepository.findDistinctLogLevels();
    }

    @Transactional
    public void deleteLogsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        frontendLogRepository.deleteByLogTimeRange(startTime, endTime);
    }

    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> stats = new LinkedHashMap<>();

        Specification<FrontendLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("logTime"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("logTime"), endTime));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<FrontendLog> allLogs = frontendLogRepository.findAll(spec);
        long totalCount = allLogs.size();
        long errorCount = allLogs.stream().filter(l -> FrontendLog.LEVEL_ERROR.equals(l.getLogLevel())).count();
        long warnCount = allLogs.stream().filter(l -> FrontendLog.LEVEL_WARN.equals(l.getLogLevel())).count();
        long infoCount = allLogs.stream().filter(l -> FrontendLog.LEVEL_INFO.equals(l.getLogLevel())).count();

        Map<String, Long> levelStats = new LinkedHashMap<>();
        levelStats.put(FrontendLog.LEVEL_ERROR, errorCount);
        levelStats.put(FrontendLog.LEVEL_WARN, warnCount);
        levelStats.put(FrontendLog.LEVEL_INFO, infoCount);
        levelStats.put(FrontendLog.LEVEL_DEBUG, totalCount - errorCount - warnCount - infoCount);

        Map<String, Long> userStats = new LinkedHashMap<>();
        for (FrontendLog logEntry : allLogs) {
            String user = logEntry.getUsername() != null ? logEntry.getUsername() : "anonymous";
            userStats.put(user, userStats.getOrDefault(user, 0L) + 1);
        }

        Map<String, Long> browserStats = new LinkedHashMap<>();
        for (FrontendLog logEntry : allLogs) {
            String browser = logEntry.getBrowserInfo() != null ? logEntry.getBrowserInfo() : "unknown";
            browserStats.put(browser, browserStats.getOrDefault(browser, 0L) + 1);
        }

        stats.put("totalCount", totalCount);
        stats.put("errorCount", errorCount);
        stats.put("warnCount", warnCount);
        stats.put("infoCount", infoCount);
        stats.put("levelStats", levelStats);
        stats.put("userStats", userStats);
        stats.put("browserStats", browserStats);

        return stats;
    }
}
