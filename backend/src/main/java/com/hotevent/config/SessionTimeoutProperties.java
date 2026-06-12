package com.hotevent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "session.timeout")
public class SessionTimeoutProperties {

    private int defaultMinutes = 30;
    private int warningMinutes = 5;
    private int minMinutes = 1;
    private int maxMinutes = 1440;

    public int getDefaultMinutes() {
        return defaultMinutes;
    }

    public void setDefaultMinutes(int defaultMinutes) {
        this.defaultMinutes = defaultMinutes;
    }

    public int getWarningMinutes() {
        return warningMinutes;
    }

    public void setWarningMinutes(int warningMinutes) {
        this.warningMinutes = warningMinutes;
    }

    public int getMinMinutes() {
        return minMinutes;
    }

    public void setMinMinutes(int minMinutes) {
        this.minMinutes = minMinutes;
    }

    public int getMaxMinutes() {
        return maxMinutes;
    }

    public void setMaxMinutes(int maxMinutes) {
        this.maxMinutes = maxMinutes;
    }

    public long getDefaultTimeoutMs() {
        return (long) defaultMinutes * 60 * 1000;
    }

    public long getWarningTimeoutMs() {
        return (long) warningMinutes * 60 * 1000;
    }
}
