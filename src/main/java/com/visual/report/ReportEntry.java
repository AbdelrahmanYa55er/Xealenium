package com.visual.report;

public class ReportEntry {
    public final String originalLocator;
    public final String newLocator;
    public final double score;
    public final String heatmapFilename;

    public ReportEntry(String originalLocator, String newLocator, double score, String heatmapFilename) {
        this.originalLocator = originalLocator;
        this.newLocator = newLocator;
        this.score = score;
        this.heatmapFilename = heatmapFilename;
    }
}
