package com.visual;
import java.util.*;
/**
 * Lightweight semantic text similarity.
 * Combines Levenshtein (edit distance) with a built-in synonym dictionary
 * for common form-field terms.
 *
 * score = max(levenshtein_sim, synonym_sim, word_overlap_sim)
 */
public final class SemanticSimilarity {
    private SemanticSimilarity(){}

    // Synonym clusters: words within a cluster are semantically equivalent
    private static final String[][] SYNONYMS = {
        {"first name","given name","fname","forename","first","name"},
        {"last name","surname","lname","family name","last"},
        {"email","mail","e-mail","email address","mail contact"},
        {"phone","telephone","tel","mobile","cell","phone number"},
        {"city","town","municipality","home city"},
        {"zip","zip code","postal","postal code","postcode"},
        {"country","nation","location","region"},
        {"terms","accept","agree","agreement","policy","terms and conditions","agreement signed"},
        {"newsletter","subscribe","mailing list","email feed","news"},
        {"register","submit","sign up","create account","finish","finish registration","register now"},
        {"password","pass","secret","pwd"},
        {"username","user","login","user name"},
        {"address","street","addr"}
    };

    /**
     * Multi-strategy similarity: returns the best of three comparisons.
     *   1. Levenshtein (edit distance)
     *   2. Synonym cluster match
     *   3. Word-overlap (Jaccard on tokens)
     */
    public static double score(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        a = a.trim().toLowerCase();
        b = b.trim().toLowerCase();
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.equals(b)) return 1.0;

        double levSim  = TextSimilarity.score(a, b);         // edit distance
        double synSim  = synonymScore(a, b);                  // domain synonyms
        double wordSim = wordOverlap(a, b);                   // token Jaccard
        return Math.max(levSim, Math.max(synSim, wordSim));
    }

    /** If both strings belong to the same synonym cluster, return 1.0. */
    private static double synonymScore(String a, String b) {
        for (String[] cluster : SYNONYMS) {
            boolean hasA = false, hasB = false;
            for (String syn : cluster) {
                if (a.contains(syn) || syn.contains(a)) hasA = true;
                if (b.contains(syn) || syn.contains(b)) hasB = true;
            }
            if (hasA && hasB) return 1.0;
        }
        return 0.0;
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
}
