package com.hotevent.service;

import com.hotevent.common.PageResult;
import com.hotevent.entity.HotEvent;
import com.hotevent.entity.HotEventLog;
import com.hotevent.entity.User;
import com.hotevent.repository.HotEventLogRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class HotEventLogService {

    @Autowired
    private HotEventLogRepository hotEventLogRepository;

    public static final String OPERATION_INSERT = "INSERT";
    public static final String OPERATION_UPDATE = "UPDATE";
    public static final String OPERATION_DELETE = "DELETE";

    @Transactional
    public void logInsert(HotEvent event, String reason) {
        logEventChange(event, null, OPERATION_INSERT, null, reason);
    }

    @Transactional
    public void logUpdate(HotEvent oldEvent, HotEvent newEvent, String reason) {
        if (oldEvent == null || newEvent == null) {
            return;
        }

        List<FieldChange> changes = detectChanges(oldEvent, newEvent);
        for (FieldChange change : changes) {
            HotEventLog logEntry = buildLogEntry(newEvent, OPERATION_UPDATE, change.fieldName, change.oldValue, change.newValue, reason);
            hotEventLogRepository.save(logEntry);
        }
    }

    @Transactional
    public void logDelete(HotEvent event, String reason) {
        logEventChange(event, null, OPERATION_DELETE, null, reason);
    }

    @Transactional
    public void logFieldChange(HotEvent event, String fieldName, String oldValue, String newValue, String reason) {
        HotEventLog logEntry = buildLogEntry(event, OPERATION_UPDATE, fieldName, oldValue, newValue, reason);
        hotEventLogRepository.save(logEntry);
    }

    private void logEventChange(HotEvent event, String fieldName, String operationType, String oldValue, String reason) {
        String newValue = null;
        if (OPERATION_INSERT.equals(operationType)) {
            newValue = buildSnapshot(event);
        } else if (OPERATION_DELETE.equals(operationType)) {
            oldValue = buildSnapshot(event);
        }
        HotEventLog logEntry = buildLogEntry(event, operationType, fieldName, oldValue, newValue, reason);
        hotEventLogRepository.save(logEntry);
    }

    private HotEventLog buildLogEntry(HotEvent event, String operationType, String fieldName,
                                      String oldValue, String newValue, String reason) {
        HotEventLog logEntry = new HotEventLog();
        logEntry.setEventId(event.getId());
        logEntry.setEventTitle(event.getTitle());
        logEntry.setSource(event.getSource());
        logEntry.setOperationType(operationType);
        logEntry.setFieldName(fieldName);
        logEntry.setOldValue(oldValue);
        logEntry.setNewValue(newValue);
        logEntry.setReason(reason);
        logEntry.setOperationTime(LocalDateTime.now());

        User operator = getCurrentUser();
        if (operator != null) {
            logEntry.setOperatorId(operator.getId());
            logEntry.setOperatorName(operator.getNickname() != null ? operator.getNickname() : operator.getUsername());
        } else {
            logEntry.setOperatorName("system");
        }

        return logEntry;
    }

    private User getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                return (User) authentication.getPrincipal();
            }
        } catch (Exception e) {
            log.debug("获取当前用户失败: {}", e.getMessage());
        }
        return null;
    }

    private List<FieldChange> detectChanges(HotEvent oldEvent, HotEvent newEvent) {
        List<FieldChange> changes = new ArrayList<>();

        addChangeIfDifferent(changes, "title", oldEvent.getTitle(), newEvent.getTitle());
        addChangeIfDifferent(changes, "description", oldEvent.getDescription(), newEvent.getDescription());
        addChangeIfDifferent(changes, "source", oldEvent.getSource(), newEvent.getSource());
        addChangeIfDifferent(changes, "sourceUrl", oldEvent.getSourceUrl(), newEvent.getSourceUrl());
        addChangeIfDifferent(changes, "hotValue", oldEvent.getHotValue(), newEvent.getHotValue());
        addChangeIfDifferent(changes, "hotRank", oldEvent.getHotRank(), newEvent.getHotRank());
        addChangeIfDifferent(changes, "category", oldEvent.getCategory(), newEvent.getCategory());
        addChangeIfDifferent(changes, "imageUrl", oldEvent.getImageUrl(), newEvent.getImageUrl());
        addChangeIfDifferent(changes, "isHot", oldEvent.getIsHot(), newEvent.getIsHot());
        addChangeIfDifferent(changes, "isRising", oldEvent.getIsRising(), newEvent.getIsRising());
        addChangeIfDifferent(changes, "risingRate", oldEvent.getRisingRate(), newEvent.getRisingRate());
        addChangeIfDifferent(changes, "deleted", oldEvent.getDeleted(), newEvent.getDeleted());

        return changes;
    }

    private void addChangeIfDifferent(List<FieldChange> changes, String fieldName, Object oldValue, Object newValue) {
        if (!Objects.equals(oldValue, newValue)) {
            changes.add(new FieldChange(fieldName,
                    oldValue != null ? oldValue.toString() : null,
                    newValue != null ? newValue.toString() : null));
        }
    }

    private String buildSnapshot(HotEvent event) {
        if (event == null) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("title=").append(event.getTitle()).append("; ");
        sb.append("source=").append(event.getSource()).append("; ");
        sb.append("hotValue=").append(event.getHotValue()).append("; ");
        sb.append("hotRank=").append(event.getHotRank()).append("; ");
        sb.append("category=").append(event.getCategory()).append("; ");
        sb.append("isHot=").append(event.getIsHot()).append("; ");
        sb.append("isRising=").append(event.getIsRising()).append("; ");
        sb.append("risingRate=").append(event.getRisingRate());
        return sb.toString();
    }

    public PageResult<HotEventLog> getLogs(Long eventId, String source, String operator,
                                           LocalDateTime startTime, LocalDateTime endTime,
                                           String operationType, int page, int size) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "operationTime"));
        Specification<HotEventLog> spec = buildSearchSpecification(eventId, source, operator, startTime, endTime, operationType);
        Page<HotEventLog> logPage = hotEventLogRepository.findAll(spec, pageable);

        return PageResult.of(
                logPage.getContent(),
                logPage.getTotalElements(),
                page,
                size
        );
    }

    private Specification<HotEventLog> buildSearchSpecification(Long eventId, String source, String operator,
                                                                 LocalDateTime startTime, LocalDateTime endTime,
                                                                 String operationType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (eventId != null) {
                predicates.add(cb.equal(root.get("eventId"), eventId));
            }

            if (source != null && !source.isEmpty()) {
                predicates.add(cb.equal(root.get("source"), source));
            }

            if (operator != null && !operator.isEmpty()) {
                predicates.add(cb.like(root.get("operatorName"), "%" + operator + "%"));
            }

            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("operationTime"), startTime));
            }

            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("operationTime"), endTime));
            }

            if (operationType != null && !operationType.isEmpty()) {
                predicates.add(cb.equal(root.get("operationType"), operationType));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public List<HotEventLog> getEventLogs(Long eventId) {
        return hotEventLogRepository.findByEventIdOrderByOperationTimeDesc(eventId);
    }

    public List<String> getAvailableSources() {
        return hotEventLogRepository.findDistinctSources();
    }

    public List<String> getAvailableOperators() {
        return hotEventLogRepository.findDistinctOperators();
    }

    public Map<String, Object> getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        Map<String, Object> statistics = new LinkedHashMap<>();

        Specification<HotEventLog> spec = buildSearchSpecification(null, null, null, startTime, endTime, null);
        List<HotEventLog> allLogs = hotEventLogRepository.findAll(spec);

        long totalCount = allLogs.size();
        long insertCount = allLogs.stream().filter(l -> OPERATION_INSERT.equals(l.getOperationType())).count();
        long updateCount = allLogs.stream().filter(l -> OPERATION_UPDATE.equals(l.getOperationType())).count();
        long deleteCount = allLogs.stream().filter(l -> OPERATION_DELETE.equals(l.getOperationType())).count();

        Map<String, Long> sourceStats = new LinkedHashMap<>();
        Map<String, Long> operatorStats = new LinkedHashMap<>();

        for (HotEventLog logEntry : allLogs) {
            String src = logEntry.getSource() != null ? logEntry.getSource() : "unknown";
            sourceStats.put(src, sourceStats.getOrDefault(src, 0L) + 1);

            String op = logEntry.getOperatorName() != null ? logEntry.getOperatorName() : "unknown";
            operatorStats.put(op, operatorStats.getOrDefault(op, 0L) + 1);
        }

        statistics.put("totalCount", totalCount);
        statistics.put("insertCount", insertCount);
        statistics.put("updateCount", updateCount);
        statistics.put("deleteCount", deleteCount);
        statistics.put("sourceStats", sourceStats);
        statistics.put("operatorStats", operatorStats);

        return statistics;
    }

    private static class FieldChange {
        String fieldName;
        String oldValue;
        String newValue;

        FieldChange(String fieldName, String oldValue, String newValue) {
            this.fieldName = fieldName;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }
    }
}
