package com.hotevent.crawler.http;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CookieManager implements CookieJar {

    private final Map<String, List<Cookie>> cookieStore = new ConcurrentHashMap<>();
    private final Map<String, Long> domainLastClearTime = new ConcurrentHashMap<>();

    private static final long COOKIE_CLEAR_INTERVAL_MS = 30 * 60 * 1000L;

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        String host = url.host();
        List<Cookie> existingCookies = cookieStore.computeIfAbsent(host, k -> Collections.synchronizedList(new ArrayList<>()));

        for (Cookie newCookie : cookies) {
            boolean replaced = false;
            for (int i = 0; i < existingCookies.size(); i++) {
                Cookie existing = existingCookies.get(i);
                if (existing.name().equals(newCookie.name()) && existing.domain().equals(newCookie.domain())) {
                    existingCookies.set(i, newCookie);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                existingCookies.add(newCookie);
            }
        }

        removeExpiredCookies(existingCookies);
        log.debug("保存Cookies到域名[{}]，当前数量: {}", host, existingCookies.size());
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        String host = url.host();
        periodicClearIfNeeded(host);

        List<Cookie> allCookies = cookieStore.getOrDefault(host, Collections.emptyList());
        List<Cookie> validCookies = new ArrayList<>();

        long now = System.currentTimeMillis();
        for (Cookie cookie : allCookies) {
            if (cookie.expiresAt() > now && cookie.matches(url)) {
                validCookies.add(cookie);
            }
        }

        removeExpiredCookies(allCookies);
        return validCookies;
    }

    public void addCookie(String domain, String name, String value, String path, long maxAgeSeconds) {
        Cookie.Builder builder = new Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path(path != null ? path : "/");
        if (maxAgeSeconds > 0) {
            builder.expiresAt(System.currentTimeMillis() + maxAgeSeconds * 1000);
        }
        Cookie cookie = builder.build();

        List<Cookie> cookies = cookieStore.computeIfAbsent(domain, k -> Collections.synchronizedList(new ArrayList<>()));
        cookies.add(cookie);
        log.debug("手动添加Cookie: {} -> {}={}", domain, name, value);
    }

    public void setRawCookieHeader(String domain, String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) return;
        String[] pairs = cookieHeader.split(";");
        for (String pair : pairs) {
            pair = pair.trim();
            int idx = pair.indexOf('=');
            if (idx > 0) {
                String name = pair.substring(0, idx).trim();
                String value = pair.substring(idx + 1).trim();
                addCookie(domain, name, value, "/", -1);
            }
        }
    }

    public String getCookieHeader(String domain) {
        List<Cookie> cookies = cookieStore.getOrDefault(domain, Collections.emptyList());
        if (cookies.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        long now = System.currentTimeMillis();
        for (Cookie c : cookies) {
            if (c.expiresAt() > now) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(c.name()).append('=').append(c.value());
            }
        }
        return sb.toString();
    }

    public void clearCookies(String domain) {
        cookieStore.remove(domain);
        log.info("已清除域名[{}]的所有Cookies", domain);
    }

    public void clearAllCookies() {
        cookieStore.clear();
        domainLastClearTime.clear();
        log.info("已清除所有Cookies");
    }

    private void removeExpiredCookies(List<Cookie> cookies) {
        long now = System.currentTimeMillis();
        cookies.removeIf(c -> c.expiresAt() <= now);
    }

    private void periodicClearIfNeeded(String host) {
        long now = System.currentTimeMillis();
        Long lastClear = domainLastClearTime.get(host);
        if (lastClear == null || (now - lastClear) > COOKIE_CLEAR_INTERVAL_MS) {
            List<Cookie> cookies = cookieStore.get(host);
            if (cookies != null) {
                removeExpiredCookies(cookies);
            }
            domainLastClearTime.put(host, now);
        }
    }

    public int getCookieCount(String domain) {
        return cookieStore.getOrDefault(domain, Collections.emptyList()).size();
    }
}
