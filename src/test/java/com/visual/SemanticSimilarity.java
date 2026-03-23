package com.visual;
import java.util.*;
/**
 * Lightweight lexical similarity used as a neutral fallback below the
 * accessibility and embedding layers.
 */
public final class SemanticSimilarity {
    private static final WordNetSemanticService WORDNET = WordNetSemanticService.getInstance();
    private static final Set<String> GENERIC_TOKENS = Set.of(
        "field", "form", "input", "label", "value", "entry", "control", "choice",
        "name", "line", "contact", "mark", "address", "code", "first", "last",
        "given", "family"
    );

    private SemanticSimilarity(){}

    /**
     * Compatibility wrapper for older callers.
     * The project no longer uses synonym dictionaries, so this delegates to
     * the neutral lexical scorer.
     */
    public static double score(String a, String b) {
        return semanticScore(a, b);
    }

    /**
     * Lightweight semantic comparison without synonym dictionaries.
     * Useful as a neutral, framework-friendly baseline before embeddings.
     */
    public static double simpleScore(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        a = normalize(a);
        b = normalize(b);
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;

        double levSim = TextSimilarity.score(a, b);
        double wordSim = wordOverlap(a, b);
        double containsSim = containmentScore(a, b);
        return Math.max(levSim, Math.max(wordSim, containsSim));
    }

    /**
     * Broader semantic comparison that layers a general lexical resource on top
     * of the lightweight lexical fallback.
     */
    public static double semanticScore(String a, String b) {
        double lexical = simpleScore(a, b);
        if (!WORDNET.isAvailable()) {
            return lexical;
        }
        List<String> leftTokens = semanticTokens(a);
        List<String> rightTokens = semanticTokens(b);
        double lexicalKnowledge = 0.0;
        if (leftTokens.size() == 1 && rightTokens.size() == 1) {
            lexicalKnowledge = WORDNET.phraseSimilarity(leftTokens.get(0), rightTokens.get(0));
        } else if (leftTokens.size() == 1 && rightTokens.size() <= 3) {
            lexicalKnowledge = bestSingleTokenMatch(leftTokens.get(0), rightTokens);
        } else if (rightTokens.size() == 1 && leftTokens.size() <= 3) {
            lexicalKnowledge = bestSingleTokenMatch(rightTokens.get(0), leftTokens);
        }
        return Math.max(lexical, lexicalKnowledge);
    }

    /** Jaccard coefficient on word tokens. */
    private static double wordOverlap(String a, String b) {
        Set<String> setA = new HashSet<>(Arrays.asList(a.split("\\s+")));
        Set<String> setB = new HashSet<>(Arrays.asList(b.split("\\s+")));
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    private static double containmentScore(String a, String b) {
        String longer = a.length() >= b.length() ? a : b;
        String shorter = longer.equals(a) ? b : a;
        if (shorter.length() < 4) return 0.0;
        return longer.contains(shorter) ? 0.85 : 0.0;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase();
    }

    private static int tokenCount(String value) {
        String normalized = normalize(value).replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isBlank()) {
            return 0;
        }
        return normalized.split("\\s+").length;
    }

    private static List<String> semanticTokens(String value) {
        String normalized = normalize(value).replaceAll("[^a-z0-9]+", " ").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.isBlank() || GENERIC_TOKENS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private static double bestSingleTokenMatch(String token, List<String> phraseTokens) {
        double best = 0.0;
        for (String candidate : phraseTokens) {
            best = Math.max(best, WORDNET.phraseSimilarity(token, candidate));
        }
        return best;
    }
}
