package com.visual.locator;

import org.openqa.selenium.By;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SmartLocatorResult {
    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    private final String locatorType;
    private final String locator;
    private final double score;
    private final String strategy;
    private final String detectedTag;
    private final String accessibleName;
    private final String semanticRole;
    private final String autocomplete;
    private final By selectedBy;
    private final String selectedLocator;
    private final String selectedStrategy;
    private final RiskLevel selectedRiskLevel;
    private final String selectedReason;
    private final List<LocatorCandidate> candidates;
    private final List<String> logs;

    public SmartLocatorResult(String detectedTag, String accessibleName, String semanticRole, String autocomplete,
                              LocatorCandidate selectedCandidate, List<LocatorCandidate> candidates, List<String> logs) {
        this.detectedTag = detectedTag;
        this.accessibleName = accessibleName;
        this.semanticRole = semanticRole;
        this.autocomplete = autocomplete;
        this.candidates = Collections.unmodifiableList(new ArrayList<>(candidates));
        this.logs = Collections.unmodifiableList(new ArrayList<>(logs));

        LocatorCandidate effective = selectedCandidate != null
            ? selectedCandidate
            : this.candidates.isEmpty() ? null : this.candidates.get(0);

        this.selectedBy = effective == null ? null : effective.by;
        this.locatorType = effective == null ? "" : effective.type;
        this.locator = effective == null ? "" : effective.value;
        this.score = effective == null ? 0.0 : effective.score;
        this.strategy = effective == null ? "" : effective.strategy;
        this.selectedLocator = locator;
        this.selectedStrategy = strategy;
        this.selectedRiskLevel = effective == null ? RiskLevel.HIGH : effective.riskLevel;
        this.selectedReason = effective == null ? "" : effective.reason;
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

    public By getSelectedBy() {
        return selectedBy;
    }

    public String getSelectedLocator() {
        return selectedLocator;
    }

    public String getSelectedStrategy() {
        return selectedStrategy;
    }

    public RiskLevel getSelectedRiskLevel() {
        return selectedRiskLevel;
    }

    public String getSelectedReason() {
        return selectedReason;
    }

    public List<LocatorCandidate> getCandidates() {
        return candidates;
    }

    public List<LocatorCandidate> getTopCandidates() {
        return candidates;
    }

    public List<String> getLogs() {
        return logs;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"locatorType\":\"").append(escape(locatorType)).append("\",");
        sb.append("\"locator\":\"").append(escape(locator)).append("\",");
        sb.append("\"score\":").append(String.format(Locale.US, "%.3f", score)).append(",");
        sb.append("\"strategy\":\"").append(escape(strategy)).append("\",");
        sb.append("\"selectedLocator\":\"").append(escape(selectedLocator)).append("\",");
        sb.append("\"selectedStrategy\":\"").append(escape(selectedStrategy)).append("\",");
        sb.append("\"selectedRiskLevel\":\"").append(selectedRiskLevel).append("\",");
        sb.append("\"selectedReason\":\"").append(escape(selectedReason)).append("\",");
        sb.append("\"accessibleName\":\"").append(escape(accessibleName)).append("\",");
        sb.append("\"semanticRole\":\"").append(escape(semanticRole)).append("\",");
        sb.append("\"autocomplete\":\"").append(escape(autocomplete)).append("\"");
        if (!candidates.isEmpty()) {
            sb.append(",\"candidates\":[");
            for (int i = 0; i < candidates.size(); i++) {
                if (i > 0) {
                    sb.append(",");
                }
                sb.append(candidates.get(i).toJson());
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
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public static class LocatorCandidate {
        private final By by;
        private final String type;
        private final String value;
        private final int rank;
        private final boolean unique;
        private final String reason;
        private final RiskLevel riskLevel;
        private final double score;
        private final String strategy;

        public LocatorCandidate(By by, String type, String value, int rank, boolean unique,
                                String reason, RiskLevel riskLevel, double score, String strategy) {
            this.by = by;
            this.type = type;
            this.value = value;
            this.rank = rank;
            this.unique = unique;
            this.reason = reason;
            this.riskLevel = riskLevel;
            this.score = score;
            this.strategy = strategy;
        }

        public By getBy() {
            return by;
        }

        public String getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        public int getRank() {
            return rank;
        }

        public boolean isUnique() {
            return unique;
        }

        public String getReason() {
            return reason;
        }

        public RiskLevel getRiskLevel() {
            return riskLevel;
        }

        public double getScore() {
            return score;
        }

        public String getStrategy() {
            return strategy;
        }

        public String getLocatorType() {
            return type;
        }

        public String getLocator() {
            return value;
        }

        private String toJson() {
            return "{"
                + "\"type\":\"" + escape(type) + "\","
                + "\"value\":\"" + escape(value) + "\","
                + "\"rank\":" + rank + ","
                + "\"unique\":" + unique + ","
                + "\"reason\":\"" + escape(reason) + "\","
                + "\"riskLevel\":\"" + riskLevel + "\","
                + "\"score\":" + String.format(Locale.US, "%.3f", score) + ","
                + "\"strategy\":\"" + escape(strategy) + "\""
                + "}";
        }
    }
}
