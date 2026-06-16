package com.hotevent.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.poi.excel.ExcelUtil;
import cn.hutool.poi.excel.ExcelWriter;
import com.hotevent.entity.HotEvent;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

public class ExportUtil {

    public static void exportHotEventsToExcel(List<HotEvent> events, HttpServletResponse response) throws IOException {
        ExcelWriter writer = ExcelUtil.getWriter();

        writer.addHeaderAlias("hotRank", "排名");
        writer.addHeaderAlias("title", "标题");
        writer.addHeaderAlias("description", "描述");
        writer.addHeaderAlias("source", "来源");
        writer.addHeaderAlias("category", "分类");
        writer.addHeaderAlias("hotValue", "热度值");
        writer.addHeaderAlias("isHot", "是否热门");
        writer.addHeaderAlias("isRising", "是否飙升");
        writer.addHeaderAlias("risingRate", "飙升率(%)");
        writer.addHeaderAlias("firstSeenTime", "首次出现时间");
        writer.addHeaderAlias("lastSeenTime", "最后出现时间");
        writer.addHeaderAlias("crawlTime", "抓取时间");
        writer.addHeaderAlias("sourceUrl", "原文链接");

        writer.setOnlyAlias(true);
        writer.write(events, true);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("热点事件列表_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        try (OutputStream out = response.getOutputStream()) {
            writer.flush(out, true);
        } finally {
            writer.close();
        }
    }

    public static void exportHotEventsToCsv(List<HotEvent> events, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=utf-8");
        String fileName = URLEncoder.encode("热点事件列表_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".csv");

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write('\uFEFF');

            writer.println("排名,标题,描述,来源,分类,热度值,是否热门,是否飙升,飙升率(%),首次出现时间,最后出现时间,抓取时间,原文链接");

            for (HotEvent event : events) {
                StringBuilder row = new StringBuilder();
                row.append(escapeCsvField(String.valueOf(event.getHotRank() != null ? event.getHotRank() : ""))).append(",");
                row.append(escapeCsvField(event.getTitle())).append(",");
                row.append(escapeCsvField(event.getDescription())).append(",");
                row.append(escapeCsvField(event.getSource())).append(",");
                row.append(escapeCsvField(event.getCategory())).append(",");
                row.append(escapeCsvField(String.valueOf(event.getHotValue() != null ? event.getHotValue() : ""))).append(",");
                row.append(escapeCsvField(Boolean.TRUE.equals(event.getIsHot()) ? "是" : "否")).append(",");
                row.append(escapeCsvField(Boolean.TRUE.equals(event.getIsRising()) ? "是" : "否")).append(",");
                row.append(escapeCsvField(String.valueOf(event.getRisingRate() != null ? event.getRisingRate() : ""))).append(",");
                row.append(escapeCsvField(event.getFirstSeenTime() != null ? DateUtil.format(event.getFirstSeenTime(), "yyyy-MM-dd HH:mm:ss") : "")).append(",");
                row.append(escapeCsvField(event.getLastSeenTime() != null ? DateUtil.format(event.getLastSeenTime(), "yyyy-MM-dd HH:mm:ss") : "")).append(",");
                row.append(escapeCsvField(event.getCrawlTime() != null ? DateUtil.format(event.getCrawlTime(), "yyyy-MM-dd HH:mm:ss") : "")).append(",");
                row.append(escapeCsvField(event.getSourceUrl()));
                writer.println(row);
            }
            writer.flush();
        }
    }

    public static void exportStatisticsToExcel(Map<String, Object> statistics, HttpServletResponse response) throws IOException {
        ExcelWriter writer = ExcelUtil.getWriter(true);

        writer.setCurrentRow(0);
        writer.merge(2, "统计概览");
        writer.setCurrentRow(1);

        Long totalCount = (Long) statistics.get("totalCount");
        Map<String, Long> sourceStats = (Map<String, Long>) statistics.get("sourceStats");
        Map<String, Long> categoryStats = (Map<String, Long>) statistics.get("categoryStats");
        List<HotEvent> topEvents = (List<HotEvent>) statistics.get("topEvents");

        writer.setCurrentRow(3);
        writer.merge(1, "--- 数据概览 ---");
        writer.setCurrentRow(4);
        writer.writeCellValue(0, "事件总数");
        writer.writeCellValue(1, totalCount != null ? totalCount : 0);
        writer.writeCellValue(2, "活跃来源数");
        writer.writeCellValue(3, sourceStats != null ? sourceStats.size() : 0);

        int rowIndex = 7;
        writer.setCurrentRow(rowIndex);
        writer.merge(1, "--- 来源分布统计 ---");
        rowIndex++;
        writer.setCurrentRow(rowIndex);
        writer.writeCellValue(0, "来源");
        writer.writeCellValue(1, "数量");
        writer.writeCellValue(2, "占比(%)");
        rowIndex++;

        if (sourceStats != null && !sourceStats.isEmpty()) {
            long total = totalCount != null ? totalCount : 1;
            for (Map.Entry<String, Long> entry : sourceStats.entrySet()) {
                writer.setCurrentRow(rowIndex);
                writer.writeCellValue(0, entry.getKey());
                writer.writeCellValue(1, entry.getValue());
                double percent = (entry.getValue() * 100.0) / total;
                writer.writeCellValue(2, String.format("%.2f", percent));
                rowIndex++;
            }
        }

        rowIndex += 2;
        writer.setCurrentRow(rowIndex);
        writer.merge(1, "--- 分类分布统计 (Top 10) ---");
        rowIndex++;
        writer.setCurrentRow(rowIndex);
        writer.writeCellValue(0, "分类");
        writer.writeCellValue(1, "数量");
        writer.writeCellValue(2, "占比(%)");
        rowIndex++;

        if (categoryStats != null && !categoryStats.isEmpty()) {
            long total = totalCount != null ? totalCount : 1;
            int count = 0;
            for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
                if (count >= 10) break;
                writer.setCurrentRow(rowIndex);
                writer.writeCellValue(0, entry.getKey());
                writer.writeCellValue(1, entry.getValue());
                double percent = (entry.getValue() * 100.0) / total;
                writer.writeCellValue(2, String.format("%.2f", percent));
                rowIndex++;
                count++;
            }
        }

        rowIndex += 2;
        writer.setCurrentRow(rowIndex);
        writer.merge(3, "--- 热门事件 Top 10 (近24小时) ---");
        rowIndex++;
        writer.setCurrentRow(rowIndex);
        writer.writeCellValue(0, "排名");
        writer.writeCellValue(1, "标题");
        writer.writeCellValue(2, "来源");
        writer.writeCellValue(3, "分类");
        writer.writeCellValue(4, "热度值");
        writer.writeCellValue(5, "抓取时间");
        rowIndex++;

        if (topEvents != null && !topEvents.isEmpty()) {
            int rank = 1;
            for (HotEvent event : topEvents) {
                writer.setCurrentRow(rowIndex);
                writer.writeCellValue(0, rank++);
                writer.writeCellValue(1, event.getTitle());
                writer.writeCellValue(2, event.getSource());
                writer.writeCellValue(3, event.getCategory() != null ? event.getCategory() : "");
                writer.writeCellValue(4, event.getHotValue() != null ? event.getHotValue() : 0);
                writer.writeCellValue(5, event.getCrawlTime() != null ? DateUtil.format(event.getCrawlTime(), "yyyy-MM-dd HH:mm:ss") : "");
                rowIndex++;
            }
        }

        for (int i = 0; i < 6; i++) {
            writer.setColumnWidth(i, 20);
        }
        writer.setColumnWidth(1, 50);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setCharacterEncoding("utf-8");
        String fileName = URLEncoder.encode("统计报表_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        try (OutputStream out = response.getOutputStream()) {
            writer.flush(out, true);
        } finally {
            writer.close();
        }
    }

    public static void exportStatisticsToCsv(Map<String, Object> statistics, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=utf-8");
        String fileName = URLEncoder.encode("统计报表_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".csv");

        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write('\uFEFF');

            Long totalCount = (Long) statistics.get("totalCount");
            Map<String, Long> sourceStats = (Map<String, Long>) statistics.get("sourceStats");
            Map<String, Long> categoryStats = (Map<String, Long>) statistics.get("categoryStats");
            List<HotEvent> topEvents = (List<HotEvent>) statistics.get("topEvents");

            writer.println("=== 数据概览 ===");
            writer.println("事件总数,活跃来源数");
            writer.println((totalCount != null ? totalCount : 0) + "," + (sourceStats != null ? sourceStats.size() : 0));
            writer.println();

            writer.println("=== 来源分布统计 ===");
            writer.println("来源,数量,占比(%)");
            if (sourceStats != null && !sourceStats.isEmpty()) {
                long total = totalCount != null ? totalCount : 1;
                for (Map.Entry<String, Long> entry : sourceStats.entrySet()) {
                    double percent = (entry.getValue() * 100.0) / total;
                    writer.println(escapeCsvField(entry.getKey()) + "," + entry.getValue() + "," + String.format("%.2f", percent));
                }
            }
            writer.println();

            writer.println("=== 分类分布统计 (Top 10) ===");
            writer.println("分类,数量,占比(%)");
            if (categoryStats != null && !categoryStats.isEmpty()) {
                long total = totalCount != null ? totalCount : 1;
                int count = 0;
                for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
                    if (count >= 10) break;
                    double percent = (entry.getValue() * 100.0) / total;
                    writer.println(escapeCsvField(entry.getKey()) + "," + entry.getValue() + "," + String.format("%.2f", percent));
                    count++;
                }
            }
            writer.println();

            writer.println("=== 热门事件 Top 10 (近24小时) ===");
            writer.println("排名,标题,来源,分类,热度值,抓取时间");
            if (topEvents != null && !topEvents.isEmpty()) {
                int rank = 1;
                for (HotEvent event : topEvents) {
                    StringBuilder row = new StringBuilder();
                    row.append(rank++).append(",");
                    row.append(escapeCsvField(event.getTitle())).append(",");
                    row.append(escapeCsvField(event.getSource())).append(",");
                    row.append(escapeCsvField(event.getCategory() != null ? event.getCategory() : "")).append(",");
                    row.append(event.getHotValue() != null ? event.getHotValue() : 0).append(",");
                    row.append(escapeCsvField(event.getCrawlTime() != null ? DateUtil.format(event.getCrawlTime(), "yyyy-MM-dd HH:mm:ss") : ""));
                    writer.println(row);
                }
            }

            writer.flush();
        }
    }

    private static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }
}
