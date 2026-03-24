package com.visual.locator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SmartLocatorResult {
    private final String locatorType;
    private final String locator;
    private final double score;
    private final String strategy;
    private final String detectedTag;
    private final String accessibleName;
    private final String semanticRole;
    private final String autocomplete;
    private final List<LocatorCandidate> topCandidates;
    private final List<String> logs;

    public SmartLocatorResult(String locatorType, String locator, double score, String strategy,
                              String detectedTag, String accessibleName, String semanticRole, String autocomplete,
                              List<LocatorCandidate> topCandidates, List<String> logs) {
        this.locatorType = locatorType;
        this.locator = locator;
        this.score = score;
        this.strategy = strategy;
        this.detectedTag = detectedTag;
        this.accessibleName = accessibleName;
        this.semanticRole = semanticRole;
        this.autocomplete = autocomplete;
        this.topCandidates = Collections.unmodifiableList(new ArrayList<>(topCandidates));
        this.logs = Collections.unmodifiableList(new ArrayList<>(logs));
    }

    public String getLocatorType() {
        return locatorType;
    }

    public String getLocator() {
        return locator;
    }

    public double getScore() {
        return score;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getDetectedTag() {
        return detectedTag;
    }

    public String getAccessibleName() {
        return accessibleName;
    }

    public String getSemanticRole() {
        return semanticRole;
    }

    public String getAutocomplete() {
        return autocomplete;
    }

    public List<LocatorCandidate> getTopCandidates() {
        return topCandidates;
    }

    public List<String> getLogs() {
        return logs;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"locatorType\":\"").append(escape(locatorType)).append("\",");
        sb.append("\"locator\":\"").append(escape(locator)).append("\",");
        sb.append("\"score\":").append(String.format(java.util.Locale.US, "%.3f", score)).append(",");
        sb.append("\"strategy\":\"").append(escape(strategy)).append("\",");
        sb.append("\"accessibleName\":\"").append(escape(accessibleName)).append("\",");
        sb.append("\"semanticRole\":\"").append(escape(semanticRole)).append("\",");
        sb.append("\"autocomplete\":\"").append(escape(autocomplete)).append("\"");
        if (!topCandidates.isEmpty()) {
            sb.append(",\"topCandidates\":[");
            for (int i = 0; i < topCandidates.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(topCandidates.get(i).toJson());
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toJson();
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class LocatorCandidate {
        private final String locatorType;
        private final String locator;
        private final double score;
        private final String strategy;

        public LocatorCandidate(String locatorType, String locator, double score, String strategy) {
            this.locatorType = locatorType;
            this.locator = locator;
            this.score = score;
            this.strategy = strategy;
        }

        public String getLocatorType() {
            return locatorType;
        }

        public String getLocator() {
            return locator;
        }

        public double getScore() {
            return score;
        }

        public String getStrategy() {
            return strategy;
        }

        private String toJson() {
            return "{"
                + "\"locatorType\":\"" + escape(locatorType) + "\","
                + "\"locator\":\"" + escape(locator) + "\","
                + "\"score\":" + String.format(java.util.Locale.US, "%.3f", score) + ","
                + "\"strategy\":\"" + escape(strategy) + "\""
                + "}";
        }
    }
}


