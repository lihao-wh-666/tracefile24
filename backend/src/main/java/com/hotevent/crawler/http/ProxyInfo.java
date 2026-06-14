package com.hotevent.crawler.http;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.net.InetSocketAddress;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProxyInfo {

    private String host;
    private int port;
    private String username;
    private String password;
    private ProxyType type;
    private long lastUsedTime;
    private int successCount;
    private int failCount;
    private double successRate;

    public enum ProxyType {
        HTTP, HTTPS, SOCKS4, SOCKS5
    }

    public boolean isValid() {
        return host != null && !host.isEmpty() && port > 0 && port < 65536;
    }

    public InetSocketAddress toSocketAddress() {
        return new InetSocketAddress(host, port);
    }

    public boolean hasAuth() {
        return username != null && !username.isEmpty() && password != null;
    }

    public void recordSuccess() {
        successCount++;
        lastUsedTime = System.currentTimeMillis();
        updateSuccessRate();
    }

    public void recordFailure() {
        failCount++;
        lastUsedTime = System.currentTimeMillis();
        updateSuccessRate();
    }

    private void updateSuccessRate() {
        int total = successCount + failCount;
        if (total > 0) {
            this.successRate = (double) successCount / total;
        }
    }

    public boolean isUsable(double minSuccessRate, int maxFailures) {
        if (failCount >= maxFailures) return false;
        int total = successCount + failCount;
        if (total == 0) return true;
        return this.successRate >= minSuccessRate;
    }
}
