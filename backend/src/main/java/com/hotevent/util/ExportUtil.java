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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class ExportUtil {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
        String fileName = URLEncoder.encode("热点事件列表_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        try (OutputStream out = response.getOutputStream()) {
            writer.flush(out, true);
        } finally {
            writer.close();
        }
    }

    public static void exportHotEventsToCsv(List<HotEvent> events, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=utf-8");
        String fileName = URLEncoder.encode("热点事件列表_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".csv");

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            pw.write('\uFEFF');

            pw.println("排名,标题,描述,来源,分类,热度值,是否热门,是否飙升,飙升率(%),首次出现时间,最后出现时间,抓取时间,原文链接");

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
                row.append(escapeCsvField(formatLdt(event.getFirstSeenTime()))).append(",");
                row.append(escapeCsvField(formatLdt(event.getLastSeenTime()))).append(",");
                row.append(escapeCsvField(formatLdt(event.getCrawlTime()))).append(",");
                row.append(escapeCsvField(event.getSourceUrl()));
                pw.println(row);
            }
            pw.flush();
        }
    }

    public static void exportStatisticsToExcel(Map<String, Object> statistics, HttpServletResponse response) throws IOException {
        ExcelWriter writer = ExcelUtil.getWriter(true);

        Long totalCount = (Long) statistics.get("totalCount");
        Map<String, Long> sourceStats = (Map<String, Long>) statistics.get("sourceStats");
        Map<String, Long> categoryStats = (Map<String, Long>) statistics.get("categoryStats");
        List<HotEvent> topEvents = (List<HotEvent>) statistics.get("topEvents");

        int row = 0;
        writer.writeCellValue(0, row, "统计概览");
        writer.merge(row, "统计概览", false);

        row = 2;
        writer.writeCellValue(0, row, "--- 数据概览 ---");
        row = 3;
        writer.writeCellValue(0, row, "事件总数");
        writer.writeCellValue(1, row, String.valueOf(totalCount != null ? totalCount : 0));
        writer.writeCellValue(2, row, "活跃来源数");
        writer.writeCellValue(3, row, String.valueOf(sourceStats != null ? sourceStats.size() : 0));

        row = 5;
        writer.writeCellValue(0, row, "--- 来源分布统计 ---");
        row = 6;
        writer.writeCellValue(0, row, "来源");
        writer.writeCellValue(1, row, "数量");
        writer.writeCellValue(2, row, "占比(%)");
        row = 7;

        if (sourceStats != null && !sourceStats.isEmpty()) {
            long total = totalCount != null ? totalCount : 1;
            for (Map.Entry<String, Long> entry : sourceStats.entrySet()) {
                writer.writeCellValue(0, row, entry.getKey());
                writer.writeCellValue(1, row, String.valueOf(entry.getValue()));
                double percent = (entry.getValue() * 100.0) / total;
                writer.writeCellValue(2, row, String.format("%.2f", percent));
                row++;
            }
        }

        row++;
        writer.writeCellValue(0, row, "--- 分类分布统计 (Top 10) ---");
        row++;
        writer.writeCellValue(0, row, "分类");
        writer.writeCellValue(1, row, "数量");
        writer.writeCellValue(2, row, "占比(%)");
        row++;

        if (categoryStats != null && !categoryStats.isEmpty()) {
            long total = totalCount != null ? totalCount : 1;
            int count = 0;
            for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
                if (count >= 10) break;
                writer.writeCellValue(0, row, entry.getKey());
                writer.writeCellValue(1, row, String.valueOf(entry.getValue()));
                double percent = (entry.getValue() * 100.0) / total;
                writer.writeCellValue(2, row, String.format("%.2f", percent));
                row++;
                count++;
            }
        }

        row++;
        writer.writeCellValue(0, row, "--- 热门事件 Top 10 (近24小时) ---");
        row++;
        writer.writeCellValue(0, row, "排名");
        writer.writeCellValue(1, row, "标题");
        writer.writeCellValue(2, row, "来源");
        writer.writeCellValue(3, row, "分类");
        writer.writeCellValue(4, row, "热度值");
        writer.writeCellValue(5, row, "抓取时间");
        row++;

        if (topEvents != null && !topEvents.isEmpty()) {
            int rank = 1;
            for (HotEvent event : topEvents) {
                writer.writeCellValue(0, row, String.valueOf(rank++));
                writer.writeCellValue(1, row, event.getTitle());
                writer.writeCellValue(2, row, event.getSource());
                writer.writeCellValue(3, row, event.getCategory() != null ? event.getCategory() : "");
                writer.writeCellValue(4, row, String.valueOf(event.getHotValue() != null ? event.getHotValue() : 0));
                writer.writeCellValue(5, row, formatLdt(event.getCrawlTime()));
                row++;
            }
        }

        for (int i = 0; i < 6; i++) {
            writer.setColumnWidth(i, 20);
        }
        writer.setColumnWidth(1, 50);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = URLEncoder.encode("统计报表_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

        try (OutputStream out = response.getOutputStream()) {
            writer.flush(out, true);
        } finally {
            writer.close();
        }
    }

    public static void exportStatisticsToCsv(Map<String, Object> statistics, HttpServletResponse response) throws IOException {
        response.setContentType("text/csv;charset=utf-8");
        String fileName = URLEncoder.encode("统计报表_" + DateUtil.format(DateUtil.date(), "yyyyMMdd_HHmmss"), StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        response.setHeader("Content-Disposition", "attachment;filename*=utf-8''" + fileName + ".csv");

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8))) {
            pw.write('\uFEFF');

            Long totalCount = (Long) statistics.get("totalCount");
            Map<String, Long> sourceStats = (Map<String, Long>) statistics.get("sourceStats");
            Map<String, Long> categoryStats = (Map<String, Long>) statistics.get("categoryStats");
            List<HotEvent> topEvents = (List<HotEvent>) statistics.get("topEvents");

            pw.println("=== 数据概览 ===");
            pw.println("事件总数,活跃来源数");
            pw.println((totalCount != null ? totalCount : 0) + "," + (sourceStats != null ? sourceStats.size() : 0));
            pw.println();

            pw.println("=== 来源分布统计 ===");
            pw.println("来源,数量,占比(%)");
            if (sourceStats != null && !sourceStats.isEmpty()) {
                long total = totalCount != null ? totalCount : 1;
                for (Map.Entry<String, Long> entry : sourceStats.entrySet()) {
                    double percent = (entry.getValue() * 100.0) / total;
                    pw.println(escapeCsvField(entry.getKey()) + "," + entry.getValue() + "," + String.format("%.2f", percent));
                }
            }
            pw.println();

            pw.println("=== 分类分布统计 (Top 10) ===");
            pw.println("分类,数量,占比(%)");
            if (categoryStats != null && !categoryStats.isEmpty()) {
                long total = totalCount != null ? totalCount : 1;
                int count = 0;
                for (Map.Entry<String, Long> entry : categoryStats.entrySet()) {
                    if (count >= 10) break;
                    double percent = (entry.getValue() * 100.0) / total;
                    pw.println(escapeCsvField(entry.getKey()) + "," + entry.getValue() + "," + String.format("%.2f", percent));
                    count++;
                }
            }
            pw.println();

            pw.println("=== 热门事件 Top 10 (近24小时) ===");
            pw.println("排名,标题,来源,分类,热度值,抓取时间");
            if (topEvents != null && !topEvents.isEmpty()) {
                int rank = 1;
                for (HotEvent event : topEvents) {
                    StringBuilder row = new StringBuilder();
                    row.append(rank++).append(",");
                    row.append(escapeCsvField(event.getTitle())).append(",");
                    row.append(escapeCsvField(event.getSource())).append(",");
                    row.append(escapeCsvField(event.getCategory() != null ? event.getCategory() : "")).append(",");
                    row.append(event.getHotValue() != null ? event.getHotValue() : 0).append(",");
                    row.append(escapeCsvField(formatLdt(event.getCrawlTime())));
                    pw.println(row);
                }
            }

            pw.flush();
        }
    }

    private static String formatLdt(LocalDateTime ldt) {
        if (ldt == null) return "";
        return ldt.format(DTF);
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
