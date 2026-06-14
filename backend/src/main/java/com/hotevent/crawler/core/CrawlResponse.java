package com.hotevent.crawler.core;

import com.hotevent.crawler.http.HttpResponseWrapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CrawlResponse {

    public enum Status {
        SUCCESS, PARTIAL_SUCCESS, FAILED, RATE_LIMITED, BLOCKED, LOGIN_REQUIRED, TIMEOUT, ERROR
    }

    @Builder.Default
    private Status status = Status.SUCCESS;

    private String platform;
    private String taskId;
    private String requestId;
    private String message;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMs;
    private int totalItems;
    private int parsedItems;
    private int failedItems;
    private boolean hasMore;
    private String nextCursor;
    private int nextPage;

    @Builder.Default
    private List<DataItem> dataItems = new ArrayList<>();

    @Builder.Default
    private List<CrawlRequest> followUpRequests = new ArrayList<>();

    private HttpResponseWrapper rawResponse;

    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public void addDataItem(DataItem item) {
        if (dataItems == null) dataItems = new ArrayList<>();
        dataItems.add(item);
    }

    public void addDataItems(List<DataItem> items) {
        if (dataItems == null) dataItems = new ArrayList<>();
        if (items != null) dataItems.addAll(items);
    }

    public void addFollowUpRequest(CrawlRequest request) {
        if (followUpRequests == null) followUpRequests = new ArrayList<>();
        followUpRequests.add(request);
    }

    public void addError(String error) {
        if (errors == null) errors = new ArrayList<>();
        errors.add(error);
    }

    public void putMetadata(String key, Object value) {
        if (metadata == null) metadata = new HashMap<>();
        metadata.put(key, value);
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS || status == Status.PARTIAL_SUCCESS;
    }
}
