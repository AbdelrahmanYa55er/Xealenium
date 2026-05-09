package com.visual.engine;

import com.visual.config.CandidateScoringWeights;
import com.visual.config.RegionSafetyConfig;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class CandidateScorer {
    private final CandidateCollector candidateCollector;
    private final LocalEmbeddingService embeddingService;
    private final FieldAssignmentEngine fieldAssignmentEngine;
    private final CandidateScoringWeights weights;
    private final RegionSafetyConfig regionSafety;

    CandidateScorer(CandidateCollector candidateCollector, LocalEmbeddingService embeddingService,
                    FieldAssignmentEngine fieldAssignmentEngine, CandidateScoringWeights weights,
                    RegionSafetyConfig regionSafety) {
        this.candidateCollector = candidateCollector;
        this.embeddingService = embeddingService;
        this.fieldAssignmentEngine = fieldAssignmentEngine;
        this.weights = weights == null ? CandidateScoringWeights.builder().build() : weights;
        this.regionSafety = regionSafety == null ? RegionSafetyConfig.builder().build() : regionSafety;
    }

    CandidateScoringResult score(WebDriver driver, String key, ElementSnapshot base, BufferedImage pageImage,
                                 BufferedImage template, float[] baseEmbedding, String baseKind, int baseSequence,
                                 int baseKindCount, boolean embeddingsActive,
                                 List<CandidateMetadata> candidates,
                                 FieldAssignmentEngine.FieldCompetitionContext fieldCompetition) {
        SmartLocatorBuilder locatorBuilder = new SmartLocatorBuilder(driver);
        double embeddingWeight = embeddingsActive ? weights.getEmbedding() : 0.0;
        String baseSemanticName = semanticPrimaryName(base.kind, base.accessibleName, base.labelText, base.placeholder, base.text, base.inputType);
        float[] baseSemanticNameEmbedding = embeddingsActive ? embeddingService.embed(baseSemanticName) : null;
        List<CandidateScore> ranked = new ArrayList<>();
        List<HeatmapRenderer.Candidate> heatmapCandidates = new ArrayList<>();

        for (CandidateMetadata candidate : candidates) {
            double vis = ImageUtils.templateMatch(ImageUtils.crop(pageImage, candidate.x, candidate.y, candidate.w, candidate.h), template);
            double pos = ImageUtils.positionScore(base.x, base.y, base.w, base.h, candidate.x, candidate.y, candidate.w, candidate.h, weights.getMaxDistance());
            double txt = SemanticSimilarity.simpleScore(base.text, candidate.text);
            double kind = kindScore(baseKind, candidate.kind);
            double seq = FieldAssignmentEngine.sequenceScore(baseSequence, baseKindCount, candidate.sequence, candidate.kindCount, baseKind, candidate.kind);
            double role = semanticRoleScore(base.semanticRole, candidate.semanticRole, baseKind, candidate.kind);
            double autocomplete = autocompleteScore(base.autocomplete, candidate.autocomplete);
            String candidateSemanticName = semanticPrimaryName(candidate.kind, candidate.accessibleName, candidate.labelText,
                candidate.placeholder, candidate.text, candidate.inputType);
            float[] candidateSemanticNameEmbedding = embeddingsActive ? embeddingService.embed(candidateSemanticName) : null;
            double semantic = semanticTextScore(base, candidate, baseSemanticName, candidateSemanticName,
                baseSemanticNameEmbedding, candidateSemanticNameEmbedding, embeddingsActive);
            double fieldSemantic = fieldAssignmentEngine.fieldSemanticScore(candidate, fieldCompetition);
            double embedding = embeddingsActive ? embeddingScore(baseEmbedding, candidate.embeddingVector, baseKind, candidate.kind) : 0.0;
            double score = weights.getVisual() * vis + weights.getPosition() * pos + weights.getText() * txt
                + weights.getKind() * kind + weights.getSequence() * seq + weights.getRole() * role
                + weights.getAutocomplete() * autocomplete + weights.getSemantic() * semantic
                + weights.getField() * fieldSemantic + embeddingWeight * embedding;
            score *= regionSafetyMultiplier(base, candidate, pos);

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
                candidate.accessibleName, candidate.pageRegion, candidate.sequence, vis, pos, txt, kind, seq, role, autocomplete, semantic,
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

    private double regionSafetyMultiplier(ElementSnapshot base, CandidateMetadata candidate, double positionScore) {
        if (!regionSafety.isEnabled() || positionScore > regionSafety.getMaxFarPositionScore()) {
            return 1.0;
        }
        String baselineRegion = normalizeRegion(base.pageRegion);
        String candidateRegion = normalizeRegion(candidate.pageRegion);
        if (baselineRegion.isBlank() || candidateRegion.isBlank() || baselineRegion.equals(candidateRegion)) {
            return 1.0;
        }
        if (!isBusinessRegion(baselineRegion) || !isPageChromeRegion(candidateRegion)) {
            return 1.0;
        }
        double overlap = contextOverlap(base, candidate);
        if (overlap >= regionSafety.getMinContextOverlap()) {
            return 1.0;
        }
        return regionSafety.getCrossRegionPenalty();
    }

    private static boolean isBusinessRegion(String region) {
        return switch (region) {
            case "product-card", "cart", "checkout", "payment", "form", "main" -> true;
            default -> false;
        };
    }

    private static boolean isPageChromeRegion(String region) {
        return Objects.equals(region, "global-nav") || Objects.equals(region, "footer") || Objects.equals(region, "sidebar");
    }

    private static double contextOverlap(ElementSnapshot base, CandidateMetadata candidate) {
        Set<String> baseTokens = normalizedTokenSet(base.parentContext + " " + base.sectionContext + " " + base.text);
        Set<String> candidateTokens = normalizedTokenSet(candidate.parentContext + " " + candidate.sectionContext + " " + candidate.text);
        if (baseTokens.isEmpty() || candidateTokens.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new LinkedHashSet<>(baseTokens);
        intersection.retainAll(candidateTokens);
        Set<String> union = new LinkedHashSet<>(baseTokens);
        union.addAll(candidateTokens);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    private double semanticTextScore(ElementSnapshot base, CandidateMetadata candidate,
                                     String baseAccessible, String candidateAccessible,
                                     float[] baseSemanticNameEmbedding, float[] candidateSemanticNameEmbedding,
                                     boolean embeddingsActive) {
        double accessible = Math.max(
            scoreIfPresent(baseAccessible, candidateAccessible),
            compoundLabelScore(baseAccessible, candidateAccessible, embeddingsActive)
        );
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
        double nameEmbedding = embeddingsActive
            ? LocalEmbeddingService.cosine(baseSemanticNameEmbedding, candidateSemanticNameEmbedding)
            : 0.0;
        if (!baseAccessible.isBlank() && !candidateAccessible.isBlank()) {
            if (embeddingsActive && nameEmbedding > 0.0) {
                return (0.22 * Math.max(accessible, label)) + (0.12 * placeholder) + (0.10 * context)
                    + (0.08 * crossAccessible) + (0.08 * text) + (0.40 * nameEmbedding);
            }
            return (0.38 * Math.max(accessible, label)) + (0.18 * placeholder) + (0.14 * context)
                + (0.15 * crossAccessible) + (0.15 * text);
        }
        double fallback = Math.max(Math.max(accessible, label),
            Math.max(placeholder, Math.max(crossAccessible, Math.max(context, text))));
        return embeddingsActive ? Math.max(fallback, nameEmbedding) : fallback;
    }

    private static double embeddingScore(float[] baselineVector, float[] candidateVector, String baseKind, String candidateKind) {
        if (!Objects.equals(baseKind, candidateKind)
            && !((Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select"))
            || (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text")))) {
            return 0.0;
        }
        return LocalEmbeddingService.cosine(baselineVector, candidateVector);
    }

    private static String semanticPrimaryName(String kind, String accessibleName, String labelText, String placeholder,
                                              String text, String inputType) {
        boolean fieldLike = Objects.equals(kind, "text") || Objects.equals(kind, "select")
            || Objects.equals(normalizeInputType(inputType), "contenteditable")
            || Objects.equals(normalizeInputType(inputType), "text")
            || Objects.equals(normalizeInputType(inputType), "email")
            || Objects.equals(normalizeInputType(inputType), "tel")
            || Objects.equals(normalizeInputType(inputType), "search")
            || Objects.equals(normalizeInputType(inputType), "url");
        if (fieldLike) {
            String preferred = firstNonBlank(labelText, accessibleName, placeholder);
            if (!preferred.isBlank()) {
                return preferred;
            }
        }
        return firstNonBlank(accessibleName, labelText, placeholder, text);
    }

    private static String normalizeInputType(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private double compoundLabelScore(String left, String right, boolean embeddingsActive) {
        String[] leftTokens = normalizedTokens(left);
        String[] rightTokens = normalizedTokens(right);
        if (leftTokens.length < 2 || rightTokens.length < 2) {
            return 0.0;
        }
        String leftHead = leftTokens[leftTokens.length - 1];
        String rightHead = rightTokens[rightTokens.length - 1];
        if (!leftHead.equals(rightHead)) {
            return 0.0;
        }
        String leftModifier = String.join(" ", java.util.Arrays.copyOf(leftTokens, leftTokens.length - 1)).trim();
        String rightModifier = String.join(" ", java.util.Arrays.copyOf(rightTokens, rightTokens.length - 1)).trim();
        if (leftModifier.isBlank() || rightModifier.isBlank()) {
            return 0.0;
        }
        double lexical = SemanticSimilarity.semanticScore(leftModifier, rightModifier);
        double embedding = embeddingsActive
            ? LocalEmbeddingService.cosine(embeddingService.embed(leftModifier), embeddingService.embed(rightModifier))
            : 0.0;
        return (0.35 * 1.0) + (0.65 * Math.max(lexical, embedding));
    }

    private static String[] normalizedTokens(String value) {
        String normalized = normalizeForContext(value);
        return normalized.isBlank() ? new String[0] : normalized.split("\\s+");
    }

    private static Set<String> normalizedTokenSet(String value) {
        String[] tokens = normalizedTokens(value);
        Set<String> result = new LinkedHashSet<>();
        for (String token : tokens) {
            if (!token.isBlank()) {
                result.add(token);
            }
        }
        return result;
    }

    private static String normalizeRegion(String value) {
        return normalizeForContext(value).replace(' ', '-');
    }

    private static String normalizeForContext(String value) {
        return value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
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
                + " region=" + candidate.getPageRegion()
                + " seq=" + candidate.getSequence()
                + " sem=" + String.format("%.2f", candidate.getSemanticScore())
                + " field=" + String.format("%.2f", candidate.getFieldSemanticScore())
                + " emb=" + String.format("%.2f", candidate.getEmbeddingScore())
                + " accessible='" + candidate.getAccessibleName() + "'"
                + " selector=" + candidate.getSelector());
        }
    }
}
