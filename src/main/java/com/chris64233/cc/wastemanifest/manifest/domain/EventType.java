package com.chris64233.cc.wastemanifest.manifest.domain;

/**
 * 交接与争议确认事件类型，与状态机严格对应。
 */
public enum EventType {

    GENERATOR_HANDOVER("产生方交运"),
    CARRIER_PICKUP("承运方接货"),
    DISPOSER_RECEIPT("处置方收货"),
    GENERATOR_CONFIRM("产生方确认修正重量"),
    DISPOSER_CONFIRM("处置方确认修正重量");

    private final String description;

    EventType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
