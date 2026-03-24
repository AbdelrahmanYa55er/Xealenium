package com.visual.engine;

import com.visual.baseline.BaselineStore;
import com.visual.embedding.EmbeddingFingerprintBuilder;
import com.visual.embedding.LocalEmbeddingService;
import com.visual.model.CandidateMetadata;
import com.visual.model.ElementSnapshot;
import com.visual.model.FieldProfile;
import com.visual.semantic.SemanticSimilarity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class FieldAssignmentEngine {
    private final BaselineStore store;
    private final LocalEmbeddingService embeddingService;

    FieldAssignmentEngine(BaselineStore store, LocalEmbeddingService embeddingService) {
        this.store = store;
        this.embeddingService = embeddingService;
    }

    void assignSequencePositions(List<CandidateMetadata> candidates) {
        List<String> kinds = List.of("text", "select", "toggle", "action", "generic");
        for (String kind : kinds) {
            List<CandidateMetadata> sameKind = candidates.stream()
                .filter(c -> Objects.equals(kind, c.kind))
                .sorted(Comparator.comparingInt((CandidateMetadata c) -> c.y).thenComparingInt(c -> c.x))
                .toList();
            for (int i = 0; i < sameKind.size(); i++) {
                CandidateMetadata candidate = sameKind.get(i);
                candidate.sequence = i + 1;
                candidate.kindCount = sameKind.size();
            }
        }
    }

    int baselineSequence(ElementSnapshot base, String locator, String baseKind) {
        List<ElementSnapshot> sameKind = baselineSnapshots(base, baseKind);
        for (int i = 0; i < sameKind.size(); i++) {
            if (Objects.equals(sameKind.get(i).locator, locator)) {
                return i + 1;
            }
        }
        int inferred = inferSequenceFromLocator(locator);
        if (inferred > 0) {
            return inferred;
        }
        return 1;
    }

    int baselineKindCount(ElementSnapshot base, String baseKind) {
        return Math.max(baselineSnapshots(base, baseKind).size(), 1);
    }

    FieldCompetitionContext buildContext(ElementSnapshot base, String baseFieldIdentity, float[] baseFieldEmbedding,
                                         List<CandidateMetadata> allCandidates, String baseKind) {
        List<ElementSnapshot> sameKindBaselines = baselineSnapshots(base, baseKind);
        List<FieldProfile> baselineProfiles = new ArrayList<>();
        int currentBaselineIndex = -1;
        for (ElementSnapshot snapshot : sameKindBaselines) {
            if (snapshot == null) {
                continue;
            }
            boolean isCurrent = sameSnapshot(snapshot, base) || Objects.equals(nv(snapshot.locator), nv(base.locator));
            String fieldIdentity = isCurrent
                ? baseFieldIdentity
                : EmbeddingFingerprintBuilder.buildFieldIdentity(
                    snapshot.accessibleName, snapshot.labelText, snapshot.placeholder, snapshot.autocomplete,
                    snapshot.inputType, snapshot.text, snapshot.locator);
            float[] fieldEmbedding = isCurrent
                ? baseFieldEmbedding
                : (embeddingService.isEnabled() ? embeddingService.embed(fieldIdentity) : null);
            baselineProfiles.add(new FieldProfile(snapshot, fieldIdentity, fieldEmbedding));
            if (isCurrent) {
                currentBaselineIndex = baselineProfiles.size() - 1;
            }
        }

        List<CandidateMetadata> compatibleCandidates = allCandidates.stream()
            .filter(candidate -> compatibleFieldKind(baseKind, candidate.kind))
            .toList();
        if (baselineProfiles.isEmpty() || compatibleCandidates.isEmpty()) {
            return new FieldCompetitionContext(currentBaselineIndex, baselineProfiles, compatibleCandidates,
                new double[0][0], new int[0], new int[0], new double[0], new double[0]);
        }

        double[][] pairScores = new double[baselineProfiles.size()][compatibleCandidates.size()];
        double[][] assignmentScores = new double[baselineProfiles.size()][compatibleCandidates.size()];
        double[] bestCandidateScoreByBaseline = new double[baselineProfiles.size()];
        double[] bestBaselineScoreByCandidate = new double[compatibleCandidates.size()];
        for (int i = 0; i < baselineProfiles.size(); i++) {
            FieldProfile profile = baselineProfiles.get(i);
            String profileKind = resolveBaseKind(profile.getSnapshot(), profile.getSnapshot().locator);
            int baselineSequence = i + 1;
            for (int j = 0; j < compatibleCandidates.size(); j++) {
                CandidateMetadata candidate = compatibleCandidates.get(j);
                double pairScore = fieldPairScore(profile.getSnapshot(), candidate, profile.getFieldIdentity(), profile.getFieldEmbedding());
                double sequenceTieBreaker = 0.15 * sequenceScore(
                    baselineSequence, baselineProfiles.size(), candidate.sequence, candidate.kindCount, profileKind, candidate.kind
                );
                double neighborhoodTieBreaker = 0.12 * neighborhoodScore(i, candidate, baselineProfiles, compatibleCandidates);
                pairScores[i][j] = pairScore;
                assignmentScores[i][j] = pairScore + sequenceTieBreaker + neighborhoodTieBreaker;
                bestCandidateScoreByBaseline[i] = Math.max(bestCandidateScoreByBaseline[i], pairScore);
                bestBaselineScoreByCandidate[j] = Math.max(bestBaselineScoreByCandidate[j], pairScore);
            }
        }

        int[] assignedCandidateByBaseline = assignFieldCandidates(assignmentScores);
        int[] assignedBaselineByCandidate = new int[compatibleCandidates.size()];
        Arrays.fill(assignedBaselineByCandidate, -1);
        for (int i = 0; i < assignedCandidateByBaseline.length; i++) {
            int candidateIndex = assignedCandidateByBaseline[i];
            if (candidateIndex >= 0 && candidateIndex < assignedBaselineByCandidate.length) {
                assignedBaselineByCandidate[candidateIndex] = i;
            }
        }
        return new FieldCompetitionContext(currentBaselineIndex, baselineProfiles, compatibleCandidates, pairScores,
            assignedCandidateByBaseline, assignedBaselineByCandidate, bestCandidateScoreByBaseline, bestBaselineScoreByCandidate);
    }

    double fieldSemanticScore(CandidateMetadata candidate, FieldCompetitionContext context) {
        if (context == null) {
            return 0.0;
        }
        int baselineIndex = context.currentBaselineIndex;
        int candidateIndex = context.findCandidateIndex(candidate.originalIndex);
        if (baselineIndex < 0 || candidateIndex < 0 || baselineIndex >= context.pairScores.length) {
            return 0.0;
        }
        double currentMatch = context.pairScores[baselineIndex][candidateIndex];
        if (context.baselines.size() <= 1) {
            return currentMatch;
        }
        double baselineBest = context.bestCandidateScoreByBaseline[baselineIndex];
        double candidateBest = context.bestBaselineScoreByCandidate[candidateIndex];
        double baselineAffinity = normalizeAgainstBest(currentMatch, baselineBest);
        double candidateAffinity = normalizeAgainstBest(currentMatch, candidateBest);
        double mutualAffinity = Math.sqrt(baselineAffinity * candidateAffinity);
        double assignmentAffinity = assignmentAffinity(baselineIndex, candidateIndex, context);
        double anchoredAssignment = assignmentAffinity * currentMatch;
        double score = (0.50 * currentMatch) + (0.20 * mutualAffinity) + (0.30 * anchoredAssignment);
        if (candidateOwnedByDifferentBaseline(baselineIndex, candidateIndex, context)) {
            score *= 0.70;
        }
        return score;
    }

    static double sequenceScore(int baseSequence, int baseCount, int candidateSequence, int candidateCount, String baseKind, String candidateKind) {
        if (!Objects.equals(baseKind, candidateKind)) {
            if ((Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select")) ||
                (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text"))) {
                return 0.40;
            }
            return 0.05;
        }
        int span = Math.max(Math.max(baseCount, candidateCount) - 1, 1);
        int delta = Math.abs(baseSequence - candidateSequence);
        return Math.max(0.0, 1.0 - ((double) delta / span));
    }

    static String resolveBaseKind(ElementSnapshot base, String locator) {
        String key = locator == null ? "" : locator.toLowerCase();
        if (key.contains("country")) return "select";
        if (key.contains("terms") || key.contains("newsletter")) return "toggle";
        if (key.contains("submit") || key.contains("register") || key.contains("finish") || key.contains("button")) return "action";
        if (key.contains("fname") || key.contains("lname") || key.contains("email") || key.contains("phone") || key.contains("city") || key.contains("zip") || key.contains("postal")) return "text";
        if (base != null && base.kind != null && !base.kind.isBlank()) return base.kind;
        String text = base == null || base.text == null ? "" : base.text.toLowerCase();
        if (text.contains("country") || text.contains("location")) return "select";
        if (text.contains("terms") || text.contains("agreement") || text.contains("newsletter") || (base != null && base.w <= 30 && base.h <= 30)) return "toggle";
        if (text.contains("register") || text.contains("finish") || text.contains("submit")) return "action";
        return "text";
    }

    private List<ElementSnapshot> baselineSnapshots(ElementSnapshot base, String baseKind) {
        return store.loadAll().stream()
            .filter(snapshot -> samePage(base.pageUrl, snapshot.pageUrl))
            .filter(snapshot -> Objects.equals(resolveBaseKind(snapshot, snapshot.locator), baseKind))
            .sorted(Comparator.comparingInt((ElementSnapshot snapshot) -> snapshot.y).thenComparingInt(snapshot -> snapshot.x))
            .toList();
    }

    private static boolean compatibleFieldKind(String baseKind, String candidateKind) {
        if (Objects.equals(baseKind, candidateKind)) {
            return true;
        }
        return (Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select"))
            || (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text"));
    }

    private static int[] assignFieldCandidates(double[][] pairScores) {
        int baselineCount = pairScores.length;
        if (baselineCount == 0) {
            return new int[0];
        }
        int candidateCount = pairScores[0].length;
        int[] assignments = new int[baselineCount];
        Arrays.fill(assignments, -1);
        if (candidateCount == 0) {
            return assignments;
        }
        if (candidateCount <= 18) {
            return optimalFieldAssignment(pairScores);
        }
        return greedyFieldAssignment(pairScores);
    }

    private static int[] optimalFieldAssignment(double[][] pairScores) {
        Map<String, Double> memo = new HashMap<>();
        Map<String, Integer> choice = new HashMap<>();
        solveFieldAssignment(0, 0L, pairScores, memo, choice);
        int[] assignments = new int[pairScores.length];
        Arrays.fill(assignments, -1);
        long usedMask = 0L;
        for (int baselineIndex = 0; baselineIndex < assignments.length; baselineIndex++) {
            String key = baselineIndex + ":" + usedMask;
            int chosenCandidate = choice.getOrDefault(key, -1);
            if (chosenCandidate >= 0) {
                assignments[baselineIndex] = chosenCandidate;
                usedMask |= (1L << chosenCandidate);
            }
        }
        return assignments;
    }

    private static double solveFieldAssignment(int baselineIndex, long usedMask, double[][] pairScores,
                                               Map<String, Double> memo, Map<String, Integer> choice) {
        if (baselineIndex >= pairScores.length) {
            return 0.0;
        }
        String key = baselineIndex + ":" + usedMask;
        Double cached = memo.get(key);
        if (cached != null) {
            return cached;
        }

        double bestScore = solveFieldAssignment(baselineIndex + 1, usedMask, pairScores, memo, choice);
        int bestChoice = -1;
        for (int candidateIndex = 0; candidateIndex < pairScores[baselineIndex].length; candidateIndex++) {
            if ((usedMask & (1L << candidateIndex)) != 0L) {
                continue;
            }
            double pairScore = pairScores[baselineIndex][candidateIndex];
            if (pairScore <= 0.0) {
                continue;
            }
            double totalScore = pairScore + solveFieldAssignment(baselineIndex + 1, usedMask | (1L << candidateIndex), pairScores, memo, choice);
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestChoice = candidateIndex;
            }
        }
        memo.put(key, bestScore);
        choice.put(key, bestChoice);
        return bestScore;
    }

    private static int[] greedyFieldAssignment(double[][] pairScores) {
        int baselineCount = pairScores.length;
        int candidateCount = pairScores[0].length;
        int[] assignments = new int[baselineCount];
        Arrays.fill(assignments, -1);
        boolean[] usedCandidates = new boolean[candidateCount];
        List<int[]> rankedPairs = new ArrayList<>();
        for (int i = 0; i < baselineCount; i++) {
            for (int j = 0; j < candidateCount; j++) {
                if (pairScores[i][j] > 0.0) {
                    rankedPairs.add(new int[]{i, j});
                }
            }
        }
        rankedPairs.sort((left, right) -> Double.compare(pairScores[right[0]][right[1]], pairScores[left[0]][left[1]]));
        for (int[] pair : rankedPairs) {
            int baselineIndex = pair[0];
            int candidateIndex = pair[1];
            if (assignments[baselineIndex] >= 0 || usedCandidates[candidateIndex]) {
                continue;
            }
            assignments[baselineIndex] = candidateIndex;
            usedCandidates[candidateIndex] = true;
        }
        return assignments;
    }

    private static double normalizeAgainstBest(double current, double best) {
        if (best <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, current / best));
    }

    private static double assignmentAffinity(int baselineIndex, int candidateIndex, FieldCompetitionContext context) {
        int assignedCandidate = baselineIndex >= 0 && baselineIndex < context.assignedCandidateByBaseline.length
            ? context.assignedCandidateByBaseline[baselineIndex]
            : -1;
        if (assignedCandidate == candidateIndex) {
            return 1.0;
        }
        int ownerBaseline = candidateIndex >= 0 && candidateIndex < context.assignedBaselineByCandidate.length
            ? context.assignedBaselineByCandidate[candidateIndex]
            : -1;
        if (ownerBaseline >= 0 && ownerBaseline != baselineIndex) {
            return 0.0;
        }
        return 0.18;
    }

    private static boolean candidateOwnedByDifferentBaseline(int baselineIndex, int candidateIndex,
                                                             FieldCompetitionContext context) {
        if (candidateIndex < 0 || candidateIndex >= context.assignedBaselineByCandidate.length) {
            return false;
        }
        int ownerBaseline = context.assignedBaselineByCandidate[candidateIndex];
        return ownerBaseline >= 0 && ownerBaseline != baselineIndex;
    }

    private double neighborhoodScore(int baselineIndex, CandidateMetadata candidate,
                                     List<FieldProfile> baselines, List<CandidateMetadata> candidates) {
        double sum = 0.0;
        int count = 0;

        CandidateMetadata previousCandidate = neighborCandidate(candidates, candidate, -1);
        if (baselineIndex > 0 && previousCandidate != null) {
            FieldProfile previousBaseline = baselines.get(baselineIndex - 1);
            sum += fieldPairScore(previousBaseline.getSnapshot(), previousCandidate, previousBaseline.getFieldIdentity(), previousBaseline.getFieldEmbedding());
            count++;
        }

        CandidateMetadata nextCandidate = neighborCandidate(candidates, candidate, 1);
        if (baselineIndex + 1 < baselines.size() && nextCandidate != null) {
            FieldProfile nextBaseline = baselines.get(baselineIndex + 1);
            sum += fieldPairScore(nextBaseline.getSnapshot(), nextCandidate, nextBaseline.getFieldIdentity(), nextBaseline.getFieldEmbedding());
            count++;
        }

        if (count == 0) {
            return 0.50;
        }
        return sum / count;
    }

    private static CandidateMetadata neighborCandidate(List<CandidateMetadata> candidates, CandidateMetadata current, int direction) {
        CandidateMetadata best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (CandidateMetadata candidate : candidates) {
            if (candidate.originalIndex == current.originalIndex) {
                continue;
            }
            int delta = candidate.sequence - current.sequence;
            if (direction < 0 && delta >= 0) {
                continue;
            }
            if (direction > 0 && delta <= 0) {
                continue;
            }
            int distance = Math.abs(delta);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean sameSnapshot(ElementSnapshot left, ElementSnapshot right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(nv(left.locator), nv(right.locator))
            && Objects.equals(nv(left.pageUrl), nv(right.pageUrl))
            && left.x == right.x && left.y == right.y && left.w == right.w && left.h == right.h;
    }

    private double fieldPairScore(ElementSnapshot base, CandidateMetadata candidate,
                                  String baseFieldIdentity, float[] baseFieldEmbedding) {
        String baseKind = resolveBaseKind(base, base.locator);
        double identityText = scoreIfPresent(baseFieldIdentity, candidate.fieldIdentity);
        double directName = Math.max(
            scoreIfPresent(base.accessibleName, candidate.accessibleName),
            Math.max(
                scoreIfPresent(base.labelText, candidate.labelText),
                Math.max(
                    scoreIfPresent(base.accessibleName, candidate.labelText),
                    compoundLabelScore(firstNonBlank(base.labelText, base.accessibleName),
                        firstNonBlank(candidate.labelText, candidate.accessibleName))
                )
            )
        );
        double placeholder = Math.max(
            scoreIfPresent(base.placeholder, candidate.placeholder),
            Math.max(scoreIfPresent(base.placeholder, candidate.accessibleName), scoreIfPresent(base.accessibleName, candidate.placeholder))
        );
        double autocomplete = strictAutocompleteScore(base.autocomplete, candidate.autocomplete);
        double inputType = scoreIfPresent(base.inputType, candidate.inputType);
        double control = fieldControlScore(baseKind, candidate.kind, base.semanticRole, candidate.semanticRole);
        double embedding = baseFieldEmbedding == null || candidate.fieldEmbeddingVector == null
            ? 0.0
            : LocalEmbeddingService.cosine(baseFieldEmbedding, candidate.fieldEmbeddingVector);
        double nameEmbedding = semanticNameEmbedding(base, candidate);
        if (embedding > 0.0 || nameEmbedding > 0.0) {
            return (0.18 * directName) + (0.14 * identityText) + (0.10 * placeholder)
                + (0.24 * nameEmbedding) + (0.06 * embedding)
                + (0.09 * autocomplete) + (0.05 * inputType) + (0.14 * control);
        }
        return (0.31 * directName) + (0.24 * identityText) + (0.18 * placeholder)
            + (0.10 * autocomplete) + (0.05 * inputType) + (0.12 * control);
    }

    private double semanticNameEmbedding(ElementSnapshot base, CandidateMetadata candidate) {
        if (!embeddingService.isEnabled()) {
            return 0.0;
        }
        String baseName = semanticPrimaryName(base.kind, base.accessibleName, base.labelText, base.placeholder, base.text, base.inputType);
        String candidateName = semanticPrimaryName(candidate.kind, candidate.accessibleName, candidate.labelText,
            candidate.placeholder, candidate.text, candidate.inputType);
        return LocalEmbeddingService.cosine(
            embeddingService.embed(baseName),
            embeddingService.embed(candidateName)
        );
    }

    private double compoundLabelScore(String left, String right) {
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
        String leftModifier = String.join(" ", Arrays.copyOf(leftTokens, leftTokens.length - 1)).trim();
        String rightModifier = String.join(" ", Arrays.copyOf(rightTokens, rightTokens.length - 1)).trim();
        if (leftModifier.isBlank() || rightModifier.isBlank()) {
            return 0.0;
        }
        double lexical = SemanticSimilarity.semanticScore(leftModifier, rightModifier);
        double embedding = embeddingService.isEnabled()
            ? LocalEmbeddingService.cosine(embeddingService.embed(leftModifier), embeddingService.embed(rightModifier))
            : 0.0;
        return (0.35 * 1.0) + (0.65 * Math.max(lexical, embedding));
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

    private static String[] normalizedTokens(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase().replaceAll("[^a-z0-9]+", " ").trim();
        return normalized.isBlank() ? new String[0] : normalized.split("\\s+");
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static double fieldControlScore(String baseKind, String candidateKind, String baseRole, String candidateRole) {
        double kind = strictFieldKindScore(baseKind, candidateKind);
        double role = semanticRoleScore(baseRole, candidateRole, baseKind, candidateKind);
        return (0.60 * kind) + (0.40 * role);
    }

    private static double strictFieldKindScore(String baseKind, String candidateKind) {
        if (Objects.equals(baseKind, candidateKind)) {
            return 1.0;
        }
        if ((Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select")) ||
            (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text"))) {
            return 0.20;
        }
        return 0.0;
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

    static String normalizeAutocomplete(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase();
        if (normalized.isBlank()) return "";
        String[] tokens = normalized.split("\\s+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            String token = tokens[i];
            if (token.isBlank()) continue;
            if (token.startsWith("section-")) continue;
            if (List.of("shipping", "billing", "home", "work", "mobile", "fax", "pager").contains(token)) continue;
            return token;
        }
        return tokens[tokens.length - 1];
    }

    private static double strictAutocompleteScore(String baseAutocomplete, String candidateAutocomplete) {
        String baseToken = normalizeAutocomplete(baseAutocomplete);
        String candidateToken = normalizeAutocomplete(candidateAutocomplete);
        if (baseToken.isBlank() || candidateToken.isBlank()) return 0.0;
        return baseToken.equals(candidateToken) ? 1.0 : 0.0;
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

    private static boolean samePage(String left, String right) {
        return normalizeUrl(left).equals(normalizeUrl(right));
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String normalized = url.trim().toLowerCase();
        int hash = normalized.indexOf('#');
        if (hash >= 0) normalized = normalized.substring(0, hash);
        int query = normalized.indexOf('?');
        if (query >= 0) normalized = normalized.substring(0, query);
        return normalized;
    }

    private static int inferSequenceFromLocator(String locator) {
        String key = locator == null ? "" : locator.toLowerCase();
        if (key.contains("fname") || key.contains("first")) return 1;
        if (key.contains("lname") || key.contains("last") || key.contains("surname")) return 2;
        if (key.contains("email") || key.contains("mail")) return 3;
        if (key.contains("phone") || key.contains("tel")) return 4;
        if (key.contains("city") || key.contains("town")) return 5;
        if (key.contains("zip") || key.contains("postal")) return 6;
        if (key.contains("country") || key.contains("location")) return 1;
        if (key.contains("terms") || key.contains("agreement")) return 1;
        if (key.contains("newsletter") || key.contains("feed")) return 2;
        if (key.contains("submit") || key.contains("register") || key.contains("finish")) return 1;
        return -1;
    }

    private static double scoreIfPresent(String left, String right) {
        if (left == null || left.isBlank() || right == null || right.isBlank()) {
            return 0.0;
        }
        return SemanticSimilarity.semanticScore(left, right);
    }

    private static String nv(String value) {
        return value == null ? "" : value;
    }

    static final class FieldCompetitionContext {
        final int currentBaselineIndex;
        final List<FieldProfile> baselines;
        final List<CandidateMetadata> candidates;
        final double[][] pairScores;
        final int[] assignedCandidateByBaseline;
        final int[] assignedBaselineByCandidate;
        final double[] bestCandidateScoreByBaseline;
        final double[] bestBaselineScoreByCandidate;

        FieldCompetitionContext(int currentBaselineIndex, List<FieldProfile> baselines, List<CandidateMetadata> candidates,
                                double[][] pairScores, int[] assignedCandidateByBaseline, int[] assignedBaselineByCandidate,
                                double[] bestCandidateScoreByBaseline, double[] bestBaselineScoreByCandidate) {
            this.currentBaselineIndex = currentBaselineIndex;
            this.baselines = baselines;
            this.candidates = candidates;
            this.pairScores = pairScores;
            this.assignedCandidateByBaseline = assignedCandidateByBaseline;
            this.assignedBaselineByCandidate = assignedBaselineByCandidate;
            this.bestCandidateScoreByBaseline = bestCandidateScoreByBaseline;
            this.bestBaselineScoreByCandidate = bestBaselineScoreByCandidate;
        }

        int findCandidateIndex(int originalIndex) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).originalIndex == originalIndex) {
                    return i;
                }
            }
            return -1;
        }
    }
}
