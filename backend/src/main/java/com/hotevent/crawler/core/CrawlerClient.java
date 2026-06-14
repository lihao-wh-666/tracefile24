package com.hotevent.crawler.core;

import com.hotevent.crawler.http.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class CrawlerClient {

    @Autowired
    private HttpClientWrapper httpClient;

    @Autowired
    private RateLimitManager rateLimitManager;

    @Autowired
    private CookieManager cookieManager;

    private final ExecutorService requestExecutor;

    public CrawlerClient() {
        this.requestExecutor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    public CrawlResponse execute(CrawlRequest request) {
        if (request.getRequestId() == null || request.getRequestId().isEmpty()) {
            request.setRequestId(UUID.randomUUID().toString());
        }

        CrawlResponse.CrawlResponseBuilder responseBuilder = CrawlResponse.builder()
                .platform(request.getPlatform())
                .taskId(request.getTaskId())
                .requestId(request.getRequestId())
                .startTime(LocalDateTime.now());

        long startTime = System.currentTimeMillis();

        try {
            log.info("[CrawlerClient][{}] 开始执行请求: {} {} type={}",
                    request.getRequestId(), request.getMethod(), request.getUrl(), request.getRequestType());

            if (request.getRequestIntervalMs() > 0) {
                Thread.sleep(request.getRequestIntervalMs());
            }

            HttpRequestWrapper httpRequest = convertToHttpRequest(request);
            HttpResponseWrapper httpResponse = httpClient.execute(httpRequest);

            responseBuilder.rawResponse(httpResponse);

            if (!httpResponse.isOk()) {
                handleHttpError(responseBuilder, httpResponse);
                log.warn("[CrawlerClient][{}] HTTP请求异常: status={} url={}",
                        request.getRequestId(), httpResponse.getStatusCode(), request.getUrl());
            } else {
                responseBuilder.status(CrawlResponse.Status.SUCCESS);
                log.debug("[CrawlerClient][{}] HTTP请求成功: status={} size={}bytes",
                        request.getRequestId(), httpResponse.getStatusCode(),
                        httpResponse.getRawBody() != null ? httpResponse.getRawBody().length : 0);
            }

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            responseBuilder.status(CrawlResponse.Status.TIMEOUT)
                    .message("请求被中断: " + ie.getMessage());
            log.warn("[CrawlerClient][{}] 请求被中断", request.getRequestId());

        } catch (Exception e) {
            responseBuilder.status(CrawlResponse.Status.ERROR)
                    .message("请求异常: " + e.getMessage());
            log.error("[CrawlerClient][{}] 请求执行异常: {}",
                    request.getRequestId(), e.getMessage(), e);
        }

        long duration = System.currentTimeMillis() - startTime;
        CrawlResponse response = responseBuilder
                .endTime(LocalDateTime.now())
                .durationMs(duration)
                .build();

        log.info("[CrawlerClient][{}] 请求完成: status={} 耗时={}ms",
                request.getRequestId(), response.getStatus(), duration);

        return response;
    }

    private HttpRequestWrapper convertToHttpRequest(CrawlRequest request) {
        HttpRequestWrapper.HttpRequestWrapperBuilder builder = HttpRequestWrapper.builder()
                .method(request.getMethod() != null ? request.getMethod() : HttpMethod.GET)
                .url(request.getUrl())
                .headers(request.getHeaders())
                .queryParams(request.getQueryParams())
                .formParams(request.getFormParams())
                .body(request.getBody())
                .contentType(request.getContentType() != null ? request.getContentType() : "application/json")
                .useCookies(true)
                .useProxy(request.isUseProxy())
                .platform(request.getPlatform())
                .retryStrategy(request.getRetryStrategy());

        if (request.getHeaders() != null && request.getHeaders().containsKey("User-Agent")) {
            builder.userAgent(request.getHeaders().get("User-Agent"));
        }
        if (request.getHeaders() != null && request.getHeaders().containsKey("Referer")) {
            builder.referer(request.getHeaders().get("Referer"));
        }

        return builder.build();
    }

    private void handleHttpError(CrawlResponse.CrawlResponseBuilder builder, HttpResponseWrapper httpResponse) {
        int status = httpResponse.getStatusCode();
        if (status == 429) {
            builder.status(CrawlResponse.Status.RATE_LIMITED)
                    .message("请求被限流 (HTTP 429)");
        } else if (status == 403 || status == 401) {
            builder.status(CrawlResponse.Status.BLOCKED)
                    .message("访问被拒绝 (HTTP " + status + ")");
        } else if (status == 404) {
            builder.status(CrawlResponse.Status.FAILED)
                    .message("资源不存在 (HTTP 404)");
        } else if (status >= 500) {
            builder.status(CrawlResponse.Status.FAILED)
                    .message("服务器错误 (HTTP " + status + ")");
        } else {
            builder.status(CrawlResponse.Status.FAILED)
                    .message("HTTP请求失败: " + status);
        }
    }

    public void executeAsync(CrawlRequest request, java.util.function.Consumer<CrawlResponse> callback) {
        requestExecutor.submit(() -> {
            try {
                CrawlResponse response = execute(request);
                if (callback != null) {
                    callback.accept(response);
                }
            } catch (Exception e) {
                log.error("[CrawlerClient] 异步请求异常: {}", e.getMessage(), e);
                if (callback != null) {
                    CrawlResponse errorResponse = CrawlResponse.builder()
                            .status(CrawlResponse.Status.ERROR)
                            .platform(request.getPlatform())
                            .taskId(request.getTaskId())
                            .requestId(request.getRequestId())
                            .message("异步执行异常: " + e.getMessage())
                            .build();
                    callback.accept(errorResponse);
                }
            }
        });
    }

    public CrawlResponse executeTask(CrawlTask task) {
        CrawlResponse.CrawlResponseBuilder finalResponse = CrawlResponse.builder()
                .platform(task.getPlatform())
                .taskId(task.getTaskId())
                .startTime(LocalDateTime.now());

        task.setStatus(CrawlTask.TaskStatus.RUNNING);
        task.setStartTime(LocalDateTime.now());

        log.info("[CrawlerClient][Task:{}] 开始执行任务: {} (平台: {})",
                task.getTaskId(), task.getTaskName(), task.getPlatform());

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger totalItems = new AtomicInteger(0);

        List<CrawlRequest> requests = task.getRequests();
        if (requests == null || requests.isEmpty()) {
            finalResponse.status(CrawlResponse.Status.FAILED)
                    .message("任务没有请求可执行");
            task.setStatus(CrawlTask.TaskStatus.FAILED);
            return finalResponse.build();
        }

        for (CrawlRequest request : requests) {
            if (task.getStatus() == CrawlTask.TaskStatus.CANCELLED) {
                log.warn("[CrawlerClient][Task:{}] 任务已被取消", task.getTaskId());
                break;
            }

            request.setTaskId(task.getTaskId());
            request.setPlatform(task.getPlatform());

            try {
                CrawlResponse resp = execute(request);
                task.addResponse(resp);
                task.getProcessedCount().incrementAndGet();

                if (resp.isSuccess()) {
                    success.incrementAndGet();
                    if (resp.getDataItems() != null) {
                        task.collectItems(resp.getDataItems());
                        totalItems.addAndGet(resp.getDataItems().size());
                        finalResponse.dataItems(resp.getDataItems());
                    }
                    if (resp.getFollowUpRequests() != null) {
                        for (CrawlRequest followUp : resp.getFollowUpRequests()) {
                            if (followUp.getCurrentDepth() < task.getMaxDepth()) {
                                task.addRequest(followUp);
                            }
                        }
                    }
                } else {
                    failed.incrementAndGet();
                    finalResponse.addError("请求失败: " + resp.getMessage());
                }

            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("[CrawlerClient][Task:{}] 请求执行异常: {}", task.getTaskId(), e.getMessage());
                finalResponse.addError(e.getMessage());
            }
        }

        task.setEndTime(LocalDateTime.now());
        task.setTotalDurationMs(System.currentTimeMillis() - task.getStartTime().getNano() / 1_000_000);
        task.setSuccessCount(success);
        task.setFailCount(failed);

        if (task.getStatus() == CrawlTask.TaskStatus.RUNNING) {
            task.setStatus(failed.get() == 0
                    ? CrawlTask.TaskStatus.COMPLETED
                    : (success.get() > 0 ? CrawlTask.TaskStatus.COMPLETED : CrawlTask.TaskStatus.FAILED));
        }

        CrawlResponse.Status finalStatus = failed.get() == 0
                ? CrawlResponse.Status.SUCCESS
                : (success.get() > 0 ? CrawlResponse.Status.PARTIAL_SUCCESS : CrawlResponse.Status.FAILED);

        CrawlResponse result = finalResponse
                .status(finalStatus)
                .endTime(LocalDateTime.now())
                .durationMs(task.getTotalDurationMs())
                .totalItems(requests.size())
                .parsedItems(success.get())
                .failedItems(failed.get())
                .build();

        log.info("[CrawlerClient][Task:{}] 任务完成: status={} 请求数={} 成功={} 失败={} 数据项={}",
                task.getTaskId(), finalStatus, requests.size(), success.get(), failed.get(), totalItems.get());

        return result;
    }

    public void shutdown() {
        try {
            requestExecutor.shutdown();
            if (!requestExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                requestExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            requestExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
