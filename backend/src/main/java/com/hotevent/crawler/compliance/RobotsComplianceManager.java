package com.hotevent.crawler.compliance;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RobotsComplianceManager {

    @Autowired
    private com.hotevent.crawler.http.HttpClientWrapper httpClient;

    private final Map<String, RobotsConfig> robotsCache = new ConcurrentHashMap<>();

    public static class RobotsConfig {
        String domain;
        Set<String> disallowPaths = ConcurrentHashMap.newKeySet();
        Set<String> allowPaths = ConcurrentHashMap.newKeySet();
        Set<String> sitemaps = ConcurrentHashMap.newKeySet();
        String userAgent = "*";
        int crawlDelay = 0;
        LocalDateTime loadTime;
        boolean available = true;

        public boolean isAllowed(String path) {
            if (!available) return false;
            if (path == null || path.isEmpty()) path = "/";
            for (String allow : allowPaths) {
                if (matchPath(allow, path)) return true;
            }
            for (String disallow : disallowPaths) {
                if (matchPath(disallow, path)) return false;
            }
            return true;
        }

        private boolean matchPath(String pattern, String path) {
            if (pattern == null || pattern.isEmpty()) return false;
            pattern = pattern.trim();
            if (pattern.equals("*") || pattern.equals("/")) return true;
            pattern = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
            return path.matches(pattern) || path.startsWith(pattern);
        }
    }

    public boolean isAllowed(String platform, String url) {
        try {
            if (url == null || url.isEmpty()) return true;
            String domain = extractDomain(url);
            if (domain == null) return true;

            RobotsConfig config = getOrLoadRobots(domain);
            if (config == null) return true;
            String path = extractPath(url);
            boolean allowed = config.isAllowed(path);
            if (!allowed) {
                log.warn("[ROBOTS] 平台[{}]禁止访问URL: {} (path: {})", platform, url, path);
            }
            return allowed;
        } catch (Exception e) {
            log.warn("[ROBOTS] 合规检查异常: {}", e.getMessage());
            return true;
        }
    }

    public int getCrawlDelay(String url) {
        try {
            String domain = extractDomain(url);
            if (domain == null) return 0;
            RobotsConfig config = getOrLoadRobots(domain);
            return config != null ? config.crawlDelay : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public Set<String> getSitemaps(String domain) {
        RobotsConfig config = getOrLoadRobots(domain);
        return config != null ? config.sitemaps : Collections.emptySet();
    }

    private RobotsConfig getOrLoadRobots(String domain) {
        RobotsConfig cached = robotsCache.get(domain);
        if (cached != null) {
            long age = java.time.Duration.between(cached.loadTime, LocalDateTime.now()).toHours();
            if (age < 24) return cached;
        }
        RobotsConfig config = loadRobots(domain);
        if (config != null) {
            robotsCache.put(domain, config);
        }
        return config;
    }

    private RobotsConfig loadRobots(String domain) {
        RobotsConfig config = new RobotsConfig();
        config.domain = domain;
        config.loadTime = LocalDateTime.now();

        String robotsUrl = "https://" + domain + "/robots.txt";
        String content = null;
        try {
            com.hotevent.crawler.http.HttpResponseWrapper resp = httpClient.get(robotsUrl);
            if (resp.isOk()) {
                content = resp.getBody();
            } else {
                try {
                    resp = httpClient.get("http://" + domain + "/robots.txt");
                    if (resp.isOk()) content = resp.getBody();
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("[ROBOTS] 获取robots.txt失败[{}]: {}", domain, e.getMessage());
        }

        if (content == null || content.isEmpty()) {
            log.info("[ROBOTS] 域名[{}]未找到robots.txt，默认允许全部路径", domain);
            return config;
        }

        try {
            parseRobotsContent(config, content);
            log.info("[ROBOTS] 加载[{}]完成: 禁止{}条 允许{}条 Sitemap{}条 延迟{}s",
                    domain, config.disallowPaths.size(), config.allowPaths.size(),
                    config.sitemaps.size(), config.crawlDelay);
        } catch (Exception e) {
            log.warn("[ROBOTS] 解析robots.txt异常[{}]: {}", domain, e.getMessage());
        }
        return config;
    }

    private void parseRobotsContent(RobotsConfig config, String content) {
        String[] lines = content.split("\\r?\\n");
        boolean inTargetUserAgent = false;
        String currentUA = "*";

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;

            int idx = line.indexOf(':');
            if (idx <= 0) continue;
            String key = line.substring(0, idx).trim().toLowerCase();
            String value = line.length() > idx + 1 ? line.substring(idx + 1).trim() : "";
            if (value.contains("#")) {
                value = value.substring(0, value.indexOf('#')).trim();
            }

            switch (key) {
                case "user-agent":
                    currentUA = value;
                    inTargetUserAgent = "*".equals(value) || value.toLowerCase().contains("crawler")
                            || value.toLowerCase().contains("bot") || value.toLowerCase().contains("spider");
                    break;
                case "disallow":
                    if (inTargetUserAgent || "*".equals(currentUA)) {
                        if (!value.isEmpty()) config.disallowPaths.add(value);
                    }
                    break;
                case "allow":
                    if (inTargetUserAgent || "*".equals(currentUA)) {
                        if (!value.isEmpty()) config.allowPaths.add(value);
                    }
                    break;
                case "sitemap":
                    if (!value.isEmpty()) config.sitemaps.add(value);
                    break;
                case "crawl-delay":
                    try {
                        if (inTargetUserAgent || "*".equals(currentUA)) {
                            int delay = Integer.parseInt(value);
                            if (delay > config.crawlDelay) {
                                config.crawlDelay = delay;
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                    break;
                case "request-rate":
                case "visit-time":
                case "robot-version":
                default:
                    break;
            }
        }
    }

    public List<Map<String, Object>> parseSitemap(String sitemapUrl) {
        List<Map<String, Object>> urls = new ArrayList<>();
        try {
            com.hotevent.crawler.http.HttpResponseWrapper resp = httpClient.get(sitemapUrl);
            if (!resp.isOk() || resp.getBody() == null) return urls;
            Document doc = Jsoup.parse(resp.getBody());
            Elements urlEls = doc.select("url > loc");
            for (org.jsoup.nodes.Element el : urlEls) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("loc", el.text().trim());
                org.jsoup.nodes.Element parent = el.parent();
                if (parent != null) {
                    Elements lastmod = parent.select("lastmod");
                    if (!lastmod.isEmpty()) item.put("lastmod", lastmod.text().trim());
                    Elements changefreq = parent.select("changefreq");
                    if (!changefreq.isEmpty()) item.put("changefreq", changefreq.text().trim());
                    Elements priority = parent.select("priority");
                    if (!priority.isEmpty()) item.put("priority", priority.text().trim());
                }
                urls.add(item);
            }
            log.info("[ROBOTS] 解析Sitemap[{}]完成，获得{}条URL", sitemapUrl, urls.size());
        } catch (Exception e) {
            log.warn("[ROBOTS] 解析Sitemap异常: {}", e.getMessage());
        }
        return urls;
    }

    private String extractDomain(String url) {
        try {
            URL u = new URI(url).toURL();
            return u.getHost().toLowerCase();
        } catch (Exception e) {
            try {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    int start = url.indexOf("://") + 3;
                    int end = url.indexOf('/', start);
                    if (end < 0) end = url.length();
                    return url.substring(start, end).toLowerCase();
                }
                if (url.contains("/")) {
                    return url.substring(0, url.indexOf('/')).toLowerCase();
                }
                return url.toLowerCase();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private String extractPath(String url) {
        try {
            URL u = new URI(url).toURL();
            String path = u.getPath();
            String query = u.getQuery();
            return query != null ? path + "?" + query : path;
        } catch (Exception e) {
            if (url.startsWith("http")) {
                int start = url.indexOf("://") + 3;
                int end = url.indexOf('/', start);
                if (end < 0) return "/";
                return url.substring(end);
            }
            return url.startsWith("/") ? url : "/" + url;
        }
    }

    public void clearCache(String domain) {
        robotsCache.remove(domain);
        log.info("[ROBOTS] 清除[{}]的robots缓存", domain);
    }

    public void clearAllCache() {
        int count = robotsCache.size();
        robotsCache.clear();
        log.info("[ROBOTS] 清除全部{}个robots缓存", count);
    }

    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("cachedDomains", robotsCache.size());
        List<Map<String, Object>> domains = new ArrayList<>();
        for (Map.Entry<String, RobotsConfig> e : robotsCache.entrySet()) {
            RobotsConfig c = e.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("domain", e.getKey());
            info.put("disallowCount", c.disallowPaths.size());
            info.put("allowCount", c.allowPaths.size());
            info.put("sitemapCount", c.sitemaps.size());
            info.put("crawlDelay", c.crawlDelay + "s");
            info.put("loadTime", c.loadTime);
            info.put("available", c.available);
            domains.add(info);
        }
        stats.put("domains", domains);
        return stats;
    }
}
