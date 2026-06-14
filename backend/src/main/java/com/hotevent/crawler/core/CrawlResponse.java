package com.hotevent.crawler.core;

import com.hotevent.crawler.http.HttpResponseWrapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrawlResponse {

    public enum Status {
        SUCCESS, PARTIAL_SUCCESS, FAILED, RATE_LIMITED, BLOCKED, LOGIN_REQUIRED, TIMEOUT, ERROR
    }

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
    private List<DataItem> dataItems = new ArrayList<>();
    private List<CrawlRequest> followUpRequests = new ArrayList<>();
    private HttpResponseWrapper rawResponse;
    private Map<String, Object> metadata = new HashMap<>();
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

    public static CrawlResponseBuilder builder() {
        return new CrawlResponseBuilder();
    }

    @Setter
    @Accessors(fluent = true, chain = true)
    public static class CrawlResponseBuilder {
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
        private List<DataItem> dataItems = new ArrayList<>();
        private List<CrawlRequest> followUpRequests = new ArrayList<>();
        private HttpResponseWrapper rawResponse;
        private Map<String, Object> metadata = new HashMap<>();
        private List<String> errors = new ArrayList<>();

        public CrawlResponseBuilder addError(String error) {
            this.errors.add(error);
            return this;
        }

        public CrawlResponseBuilder dataItems(List<DataItem> items) {
            if (items != null) {
                this.dataItems.addAll(items);
            }
            return this;
        }

        public CrawlResponse build() {
            CrawlResponse resp = new CrawlResponse();
            resp.status = this.status;
            resp.platform = this.platform;
            resp.taskId = this.taskId;
            resp.requestId = this.requestId;
            resp.message = this.message;
            resp.startTime = this.startTime;
            resp.endTime = this.endTime;
            resp.durationMs = this.durationMs;
            resp.totalItems = this.totalItems;
            resp.parsedItems = this.parsedItems;
            resp.failedItems = this.failedItems;
            resp.hasMore = this.hasMore;
            resp.nextCursor = this.nextCursor;
            resp.nextPage = this.nextPage;
            resp.dataItems = this.dataItems;
            resp.followUpRequests = this.followUpRequests;
            resp.rawResponse = this.rawResponse;
            resp.metadata = this.metadata;
            resp.errors = this.errors;
            return resp;
        }
    }
}
