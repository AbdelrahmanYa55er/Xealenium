package com.visual.semantic;

import net.sf.extjwnl.JWNLException;
import net.sf.extjwnl.data.IndexWord;
import net.sf.extjwnl.data.POS;
import net.sf.extjwnl.data.PointerTarget;
import net.sf.extjwnl.data.PointerType;
import net.sf.extjwnl.data.Synset;
import net.sf.extjwnl.data.Word;
import net.sf.extjwnl.dictionary.Dictionary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class WordNetSemanticService {
    private static final Set<String> STOPWORDS = Set.of(
        "a", "an", "and", "are", "as", "at", "be", "button", "by", "click", "code", "control",
        "div", "enter", "field", "for", "form", "group", "in", "input", "label",
        "of", "on", "or", "please", "section", "select", "text", "textbox", "the", "to", "type",
        "value", "with", "your"
    );

    private static final WordNetSemanticService INSTANCE = new WordNetSemanticService();

    private final boolean available;
    private final Dictionary dictionary;
    private final Map<String, Set<String>> relatedTermsCache = new ConcurrentHashMap<>();
    private final Map<String, Double> phraseScoreCache = new ConcurrentHashMap<>();

    static WordNetSemanticService getInstance() {
        return INSTANCE;
    }

    private WordNetSemanticService() {
        Dictionary resolvedDictionary;
        boolean resolvedAvailable;
        try {
            resolvedDictionary = Dictionary.getDefaultResourceInstance();
            resolvedAvailable = resolvedDictionary != null;
        } catch (Exception e) {
            resolvedDictionary = null;
            resolvedAvailable = false;
        }
        dictionary = resolvedDictionary;
        available = resolvedAvailable;
    }

    boolean isAvailable() {
        return available;
    }

    double phraseSimilarity(String left, String right) {
        String normalizedLeft = normalize(left);
        String normalizedRight = normalize(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return 0.0;
        }
        if (normalizedLeft.equals(normalizedRight)) {
            return 1.0;
        }
        String cacheKey = normalizedLeft.compareTo(normalizedRight) <= 0
            ? normalizedLeft + "||" + normalizedRight
            : normalizedRight + "||" + normalizedLeft;
        return phraseScoreCache.computeIfAbsent(cacheKey, ignored -> computePhraseSimilarity(normalizedLeft, normalizedRight));
    }

    private double computePhraseSimilarity(String left, String right) {
        List<String> leftTokens = contentTokens(left);
        List<String> rightTokens = contentTokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0.0;
        }

        double forward = directionalSimilarity(leftTokens, rightTokens);
        double backward = directionalSimilarity(rightTokens, leftTokens);
        double overlap = overlapScore(leftTokens, rightTokens);
        return Math.max(overlap, (forward + backward) / 2.0);
    }

    private double directionalSimilarity(List<String> source, List<String> target) {
        double sum = 0.0;
        int count = 0;
        for (String leftToken : source) {
            double best = 0.0;
            for (String rightToken : target) {
                best = Math.max(best, tokenSimilarity(leftToken, rightToken));
            }
            sum += best;
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private double overlapScore(List<String> left, List<String> right) {
        Set<String> leftTerms = new HashSet<>();
        for (String token : left) {
            leftTerms.addAll(relatedTerms(token));
        }
        Set<String> rightTerms = new HashSet<>();
        for (String token : right) {
            rightTerms.addAll(relatedTerms(token));
        }
        if (leftTerms.isEmpty() || rightTerms.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(leftTerms);
        intersection.retainAll(rightTerms);
        if (intersection.isEmpty()) {
            return 0.0;
        }
        double denominator = Math.max(1.0, Math.min(leftTerms.size(), rightTerms.size()));
        return Math.min(1.0, intersection.size() / denominator);
    }

    private double tokenSimilarity(String leftToken, String rightToken) {
        if (leftToken.equals(rightToken)) {
            return 1.0;
        }
        Set<String> leftTerms = relatedTerms(leftToken);
        Set<String> rightTerms = relatedTerms(rightToken);
        if (!Collections.disjoint(leftTerms, rightTerms)) {
            return 1.0;
        }
        double best = TextSimilarity.score(leftToken, rightToken);
        for (String leftTerm : leftTerms) {
            for (String rightTerm : rightTerms) {
                best = Math.max(best, TextSimilarity.score(leftTerm, rightTerm));
            }
        }
        return best;
    }

    private Set<String> relatedTerms(String token) {
        String normalized = normalizeToken(token);
        if (normalized.isBlank()) {
            return Set.of();
        }
        return relatedTermsCache.computeIfAbsent(normalized, this::loadRelatedTerms);
    }

    private Set<String> loadRelatedTerms(String token) {
        Set<String> terms = new HashSet<>();
        terms.add(token);
        String singular = singularize(token);
        if (!singular.equals(token)) {
            terms.add(singular);
        }
        if (!available) {
            return terms;
        }
        for (POS pos : List.of(POS.NOUN)) {
            collectTermsForPos(token, pos, terms);
            if (!singular.equals(token)) {
                collectTermsForPos(singular, pos, terms);
            }
        }
        return terms;
    }

    private void collectTermsForPos(String token, POS pos, Set<String> terms) {
        try {
            IndexWord indexWord = dictionary.lookupIndexWord(pos, token);
            if (indexWord == null) {
                return;
            }
            for (Synset synset : indexWord.getSenses()) {
                collectTermsFromSynset(synset, terms);
            }
        } catch (JWNLException ignored) {
        }
    }

    private void collectTermsFromSynset(Synset synset, Set<String> terms) {
        for (Word word : synset.getWords()) {
            addNormalizedTerm(terms, word.getLemma());
        }
        addPointerTerms(synset, PointerType.HYPERNYM, terms);
        addPointerTerms(synset, PointerType.INSTANCE_HYPERNYM, terms);
    }

    private void addPointerTerms(Synset synset, PointerType pointerType, Set<String> terms) {
        try {
            for (PointerTarget target : synset.getTargets(pointerType)) {
                Synset related = target.getSynset();
                if (related == null) {
                    continue;
                }
                for (Word relatedWord : related.getWords()) {
                    addNormalizedTerm(terms, relatedWord.getLemma());
                }
            }
        } catch (JWNLException ignored) {
        }
    }

    private void addNormalizedTerm(Set<String> terms, String value) {
        String normalized = normalizeToken(value);
        if (normalized.isBlank()) {
            return;
        }
        terms.add(normalized);
        String singular = singularize(normalized);
        if (!singular.equals(normalized)) {
            terms.add(singular);
        }
        if (normalized.contains(" ")) {
            for (String part : normalized.split("\\s+")) {
                if (part.isBlank() || STOPWORDS.contains(part)) {
                    continue;
                }
                terms.add(part);
                String singularPart = singularize(part);
                if (!singularPart.equals(part)) {
                    terms.add(singularPart);
                }
            }
        }
    }

    private List<String> contentTokens(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            String normalizedToken = normalizeToken(token);
            if (normalizedToken.isBlank() || STOPWORDS.contains(normalizedToken)) {
                continue;
            }
            tokens.add(normalizedToken);
        }
        return tokens;
    }

    private String singularize(String token) {
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("ses") && token.length() > 4) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("s") && token.length() > 3 && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replace('_', ' ')
            .replaceAll("[^a-z0-9]+", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String normalizeToken(String value) {
        return normalize(value);
    }
}


