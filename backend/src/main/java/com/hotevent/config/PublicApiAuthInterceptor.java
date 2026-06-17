package com.hotevent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotevent.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

@Slf4j
@Component
public class PublicApiAuthInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${hot-event.public-api.auth.enabled:false}")
    private boolean authEnabled;

    @Value("#{'${hot-event.public-api.auth.api-keys:}'.split(',')}")
    private List<String> validApiKeys;

    @Value("${hot-event.public-api.auth.header-name:X-API-Key}")
    private String headerName;

    @Value("${hot-event.public-api.auth.query-param-name:apiKey}")
    private String queryParamName;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!authEnabled) {
            return true;
        }

        String apiKey = extractApiKey(request);

        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("[Public API] 缺少 API Key，请求: {} {}", request.getMethod(), request.getRequestURI());
            sendAuthError(response, 401, "缺少 API Key，请在请求头 " + headerName + " 或查询参数 " + queryParamName + " 中提供");
            return false;
        }

        if (!isValidApiKey(apiKey)) {
            log.warn("[Public API] 无效的 API Key，请求: {} {}", request.getMethod(), request.getRequestURI());
            sendAuthError(response, 403, "无效的 API Key");
            return false;
        }

        return true;
    }

    private String extractApiKey(HttpServletRequest request) {
        String apiKey = request.getHeader(headerName);
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey.trim();
        }
        apiKey = request.getParameter(queryParamName);
        if (apiKey != null && !apiKey.isEmpty()) {
            return apiKey.trim();
        }
        return null;
    }

    private boolean isValidApiKey(String apiKey) {
        if (validApiKeys == null || validApiKeys.isEmpty()) {
            return false;
        }
        for (String validKey : validApiKeys) {
            if (validKey != null && validKey.trim().equals(apiKey)) {
                return true;
            }
        }
        return false;
    }

    private void sendAuthError(HttpServletResponse response, int code, String message) {
        try {
            response.setStatus(code);
            response.setContentType("application/json;charset=UTF-8");
            Result<Void> result = Result.error(code, message);
            response.getWriter().write(objectMapper.writeValueAsString(result));
        } catch (Exception e) {
            log.error("[Public API] 写入认证错误响应失败: {}", e.getMessage());
        }
    }
}
