package com.visual.engine;

import com.visual.report.HeatmapRenderer;
import com.visual.report.ReportEntry;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class HealingReporter {
    public static final List<ReportEntry> REPORTS = new ArrayList<>();

    public String writeHeatmap(String key, int healCount, BufferedImage image,
                               List<HeatmapRenderer.Candidate> candidates, int displayIdx) {
        String safe = key.replaceAll("[^a-zA-Z0-9]", "_");
        String heatmapFile = "visual-heatmap-" + safe + "-" + healCount + ".png";
        HeatmapRenderer.render(image, candidates, displayIdx, key, heatmapFile);
        return heatmapFile;
    }

    public void record(String originalLocator, String newLocator, double score, String heatmapFilename) {
        REPORTS.add(new ReportEntry(originalLocator, newLocator, score, heatmapFilename));
    }

    public static void generateHtmlReport(String outputPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>Visual Healing Report</title>");
        sb.append("<style>body{font-family:-apple-system,sans-serif;background:#f4f4f5;padding:2rem;} ");
        sb.append(".card{background:#fff;border-radius:8px;padding:1.5rem;box-shadow:0 1px 3px rgba(0,0,0,0.1);margin-bottom:2rem;} ");
        sb.append("img{max-width:100%;border:1px solid #e4e4e7;border-radius:4px;margin-top:1rem;} ");
        sb.append(".score{display:inline-block;padding:0.25rem 0.5rem;border-radius:4px;font-weight:bold;color:#fff;} ");
        sb.append(".high{background:#10b981;} .mid{background:#f59e0b;} .low{background:#ef4444;} ");
        sb.append("h3{margin-top:0;color:#18181b;} .loc{font-family:monospace;background:#f4f4f5;padding:0.2rem 0.4rem;border-radius:3px;}</style>");
        sb.append("</head><body>");
        sb.append("<h1>Xealenium - Visual Healing Report</h1>");
        if (REPORTS.isEmpty()) {
            sb.append("<p>No healing events occurred during this run.</p>");
        }
        for (ReportEntry reportEntry : REPORTS) {
            String cClass = reportEntry.score > 0.8 ? "high" : (reportEntry.score >= 0.55 ? "mid" : "low");
            String statusHtml = "<span class='score " + cClass + "'>" + String.format("%.3f", reportEntry.score) + "</span>";
            sb.append("<div class='card'>");
            sb.append("<h3>Failed Locator: <span class='loc'>" + reportEntry.originalLocator + "</span></h3>");
            sb.append("<p><strong>New Healed Locator:</strong> <span class='loc'>" + reportEntry.newLocator + "</span></p>");
            sb.append("<p><strong>Score:</strong> " + statusHtml + "</p>");
            sb.append("<img src='" + reportEntry.heatmapFilename + "' alt='Heatmap' />");
            sb.append("</div>");
        }
        sb.append("</body></html>");
        try {
            Files.write(Paths.get(outputPath), sb.toString().getBytes());
            System.out.println("[REPORT] Generated visual healing report -> " + outputPath);
        } catch (Exception e) {
            System.err.println("[REPORT] Failed to generate: " + e.getMessage());
        }
    }
}
