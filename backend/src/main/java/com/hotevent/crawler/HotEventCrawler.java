package com.hotevent.crawler;

import com.hotevent.entity.HotEvent;
import java.util.List;

public interface HotEventCrawler {

    String getSourceName();

    List<HotEvent> crawl() throws Exception;

    boolean isEnabled();
}
