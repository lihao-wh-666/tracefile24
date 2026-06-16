package com.hotevent.crawler.filter;

public enum SensitiveType {
    POLITICS("politics", "涉政"),
    PORN("porn", "色情"),
    ABUSE("abuse", "辱骂"),
    AD("ad", "广告"),
    VIOLENCE("violence", "暴力"),
    GAMBLING("gambling", "赌博"),
    DRUG("drug", "毒品"),
    OTHER("other", "其他");

    private final String code;
    private final String displayName;

    SensitiveType(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SensitiveType fromCode(String code) {
        if (code == null) return OTHER;
        for (SensitiveType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return OTHER;
    }
}
