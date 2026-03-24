package com.visual.engine;

import com.visual.embedding.LocalEmbeddingService;
import com.visual.image.ImageUtils;
import com.visual.locator.SmartLocatorBuilder;
import com.visual.locator.SmartLocatorResult;
import com.visual.model.CandidateMetadata;
import com.visual.model.CandidateScore;
import com.visual.model.ElementSnapshot;
import com.visual.report.HeatmapRenderer;
import com.visual.semantic.SemanticSimilarity;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

final class CandidateScorer {
    private static final double W_VIS = 0.09;
    private static final double W_POS = 0.07;
    private static final double W_TXT = 0.06;
    private static final double W_KIND = 0.10;
    private static final double W_SEQ = 0.04;
    private static final double W_ROLE = 0.14;
    private static final double W_AUTO = 0.08;
    private static final double W_SEM = 0.20;
    private static final double W_FIELD = 0.22;
    private static final double W_EMB = 0.12;
    private static final double MAX_D = 600.0;

    private final CandidateCollector candidateCollector;
    private final LocalEmbeddingService embeddingService;
    private final FieldAssignmentEngine fieldAssignmentEngine;

    CandidateScorer(CandidateCollector candidateCollector, LocalEmbeddingService embeddingService,
                    FieldAssignmentEngine fieldAssignmentEngine) {
        this.candidateCollector = candidateCollector;
        this.embeddingService = embeddingService;
        this.fieldAssignmentEngine = fieldAssignmentEngine;
    }

    CandidateScoringResult score(WebDriver driver, String key, ElementSnapshot base, BufferedImage pageImage,
                                 BufferedImage template, float[] baseEmbedding, String baseKind, int baseSequence,
                                 int baseKindCount, boolean embeddingsActive,
                                 List<CandidateMetadata> candidates,
                                 FieldAssignmentEngine.FieldCompetitionContext fieldCompetition) {
        SmartLocatorBuilder locatorBuilder = new SmartLocatorBuilder(driver);
        double embeddingWeight = embeddingsActive ? W_EMB : 0.0;
        List<CandidateScore> ranked = new ArrayList<>();
        List<HeatmapRenderer.Candidate> heatmapCandidates = new ArrayList<>();

        for (CandidateMetadata candidate : candidates) {
            double vis = ImageUtils.templateMatch(ImageUtils.crop(pageImage, candidate.x, candidate.y, candidate.w, candidate.h), template);
            double pos = ImageUtils.positionScore(base.x, base.y, base.w, base.h, candidate.x, candidate.y, candidate.w, candidate.h, MAX_D);
            double txt = SemanticSimilarity.simpleScore(base.text, candidate.text);
            double kind = kindScore(baseKind, candidate.kind);
            double seq = FieldAssignmentEngine.sequenceScore(baseSequence, baseKindCount, candidate.sequence, candidate.kindCount, baseKind, candidate.kind);
            double role = semanticRoleScore(base.semanticRole, candidate.semanticRole, baseKind, candidate.kind);
            double autocomplete = autocompleteScore(base.autocomplete, candidate.autocomplete);
            double semantic = semanticTextScore(base, candidate);
            double fieldSemantic = fieldAssignmentEngine.fieldSemanticScore(candidate, fieldCompetition);
            double embedding = embeddingsActive ? embeddingScore(baseEmbedding, candidate.embeddingVector, baseKind, candidate.kind) : 0.0;
            double score = W_VIS * vis + W_POS * pos + W_TXT * txt + W_KIND * kind + W_SEQ * seq
                + W_ROLE * role + W_AUTO * autocomplete + W_SEM * semantic + W_FIELD * fieldSemantic + embeddingWeight * embedding;

            SmartLocatorResult smartLocator = buildSmartLocator(driver, locatorBuilder, candidate);
            String selector = smartLocator != null
                ? smartLocator.getLocatorType() + ": " + smartLocator.getLocator()
                : candidate.selector;
            String selectorStrategy = smartLocator != null ? smartLocator.getStrategy() : "visual-raw";

            heatmapCandidates.add(new HeatmapRenderer.Candidate(
                candidate.x, candidate.y, candidate.w, candidate.h, score,
                candidate.text + " [" + candidate.kind + " #" + candidate.sequence + "]"
            ));
            ranked.add(new CandidateScore(score, candidate.originalIndex, selector, selectorStrategy, candidate.kind,
                candidate.accessibleName, candidate.sequence, vis, pos, txt, kind, seq, role, autocomplete, semantic,
                fieldSemantic, embedding, candidate.x + candidate.w / 2, candidate.y + candidate.h / 2));
        }

        ranked.sort(Comparator.comparingDouble(CandidateScore::getScore).reversed());
        logTopCandidates(key, ranked);
        return new CandidateScoringResult(ranked, heatmapCandidates, candidates.size());
    }

    private SmartLocatorResult buildSmartLocator(WebDriver driver, SmartLocatorBuilder builder, CandidateMetadata candidate) {
        try {
            WebElement candidateElement = candidateCollector.resolveCandidateElement(driver, candidate.originalIndex);
            if (candidateElement != null) {
                return builder.buildLocatorForElement(candidateElement);
            }
            int[] scroll = candidateCollector.currentScrollOffset(driver);
            int px = candidate.x - scroll[0] + Math.max(1, Math.min(candidate.w - 2, candidate.w / 2));
            int py = candidate.y - scroll[1] + Math.max(1, Math.min(candidate.h - 2, candidate.h / 2));
            return builder.buildLocatorFromPoint(px, py);
        } catch (Exception e) {
            System.out.println("[SMART-LOCATOR] Fallback to raw visual selector for candidate " + candidate.originalIndex + " reason=" + e.getMessage());
            return null;
        }
    }

    private static double kindScore(String baseKind, String candidateKind) {
        if (baseKind == null || baseKind.isBlank()) return 0.5;
        if (candidateKind == null || candidateKind.isBlank()) return 0.3;
        if (baseKind.equals(candidateKind)) return 1.0;
        if ((baseKind.equals("text") && candidateKind.equals("select")) || (baseKind.equals("select") && candidateKind.equals("text"))) return 0.35;
        if (candidateKind.equals("generic") || baseKind.equals("generic")) return 0.40;
        return 0.05;
    }

    private static double semanticRoleScore(String baseRole, String candidateRole, String baseKind, String candidateKind) {
        String normalizedBase = normalizeRole(baseRole, baseKind);
        String normalizedCandidate = normalizeRole(candidateRole, candidateKind);
        if (normalizedBase.isBlank() && normalizedCandidate.isBlank()) return 0.50;
        if (normalizedBase.equals(normalizedCandidate)) return 1.0;
        if ((normalizedBase.equals("textbox") && normalizedCandidate.equals("combobox")) ||
            (normalizedBase.equals("combobox") && normalizedCandidate.equals("textbox"))) return 0.35;
        if (Objects.equals(baseKind, candidateKind)) return 0.55;
        return 0.10;
    }

    private static double autocompleteScore(String baseAutocomplete, String candidateAutocomplete) {
        String baseToken = FieldAssignmentEngine.normalizeAutocomplete(baseAutocomplete);
        String candidateToken = FieldAssignmentEngine.normalizeAutocomplete(candidateAutocomplete);
        if (baseToken.isBlank() && candidateToken.isBlank()) return 0.50;
        if (baseToken.isBlank() || candidateToken.isBlank()) return 0.35;
        return baseToken.equals(candidateToken) ? 1.0 : 0.0;
    }

    private static double semanticTextScore(ElementSnapshot base, CandidateMetadata candidate) {
        String baseAccessible = nv(base.accessibleName);
        String candidateAccessible = nv(candidate.accessibleName);
        double accessible = scoreIfPresent(baseAccessible, candidateAccessible);
        double label = Math.max(scoreIfPresent(base.labelText, candidate.labelText),
            Math.max(scoreIfPresent(baseAccessible, candidate.labelText), scoreIfPresent(base.labelText, candidateAccessible)));
        double crossAccessible = Math.max(
            scoreIfPresent(baseAccessible, candidate.text),
            scoreIfPresent(base.text, candidateAccessible)
        );
        double placeholder = Math.max(
            scoreIfPresent(base.placeholder, candidate.placeholder),
            Math.max(scoreIfPresent(base.placeholder, candidateAccessible), scoreIfPresent(baseAccessible, candidate.placeholder))
        );
        double context = Math.max(
            scoreIfPresent(base.sectionContext, candidate.sectionContext),
            scoreIfPresent(base.parentContext, candidate.parentContext)
        );
        double text = scoreIfPresent(base.text, candidate.text);
        if (!baseAccessible.isBlank() && !candidateAccessible.isBlank()) {
            return (0.38 * Math.max(accessible, label)) + (0.18 * placeholder) + (0.14 * context)
                + (0.15 * crossAccessible) + (0.15 * text);
        }
        return Math.max(Math.max(accessible, label),
            Math.max(placeholder, Math.max(crossAccessible, Math.max(context, text))));
    }

    private static double embeddingScore(float[] baselineVector, float[] candidateVector, String baseKind, String candidateKind) {
        if (!Objects.equals(baseKind, candidateKind)
            && !((Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select"))
            || (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text")))) {
            return 0.0;
        }
        return LocalEmbeddingService.cosine(baselineVector, candidateVector);
    }

    private static String normalizeRole(String role, String kind) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if (!normalized.isBlank()) return normalized;
        return switch (kind == null ? "" : kind) {
            case "text" -> "textbox";
            case "select" -> "combobox";
            case "toggle" -> "checkbox";
            case "action" -> "button";
            default -> "";
        };
    }

    private static String nv(String value) {
        return value == null ? "" : value;
    }

    private static double scoreIfPresent(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return 0.0;
        }
        return SemanticSimilarity.semanticScore(left, right);
    }

    private static void logTopCandidates(String key, List<CandidateScore> ranked) {
        int limit = Math.min(3, ranked.size());
        for (int i = 0; i < limit; i++) {
            CandidateScore candidate = ranked.get(i);
            System.out.println("[VISUAL-RANK] " + key + " rank=" + (i + 1)
                + " idx=" + candidate.getOriginalIndex()
                + " score=" + String.format("%.3f", candidate.getScore())
                + " kind=" + candidate.getKind()
                + " seq=" + candidate.getSequence()
                + " sem=" + String.format("%.2f", candidate.getSemanticScore())
                + " field=" + String.format("%.2f", candidate.getFieldSemanticScore())
                + " emb=" + String.format("%.2f", candidate.getEmbeddingScore())
                + " accessible='" + candidate.getAccessibleName() + "'"
                + " selector=" + candidate.getSelector());
        }
    }
}
