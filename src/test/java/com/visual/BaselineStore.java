package com.visual;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.File;
import java.util.*;
public class BaselineStore {
    private final File f;
    private final ObjectMapper m;
    public BaselineStore(){this("visual-baseline.json");}
    public BaselineStore(String p){f=new File(p);m=new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);}
    public synchronized boolean saveIfAbsent(ElementSnapshot s){
        return save(s, false);
    }
    public synchronized boolean save(ElementSnapshot s, boolean replaceExisting){
        try{
            List<ElementSnapshot> all=loadAll();
            int existingIndex = indexOf(all, s.pageUrl, s.locator);
            if (existingIndex >= 0) {
                ElementSnapshot existing = all.get(existingIndex);
                boolean missingMetadata = isBlank(existing.kind)
                    || isBlank(existing.tagName)
                    || isBlank(existing.accessibleName)
                    || isBlank(existing.semanticRole);
                if (!replaceExisting && !missingMetadata) return false;
                all.remove(existingIndex);
            }
            all.add(s);
            m.writeValue(f,all);
            return true;
        }
        catch(Exception e){System.err.println("[VISUAL-STORE] save:"+e.getMessage());}
        return false;
    }
    public synchronized List<ElementSnapshot> loadAll(){
        if(!f.exists())return new ArrayList<>();
        try{return m.readValue(f,new TypeReference<List<ElementSnapshot>>(){});}
        catch(Exception e){System.err.println("[VISUAL-STORE] load:"+e.getMessage());return new ArrayList<>();}
    }
    public synchronized boolean has(String pageUrl, String loc){
        return indexOf(loadAll(), pageUrl, loc) >= 0;
    }
    public synchronized ElementSnapshot find(String pageUrl, String loc){
        List<ElementSnapshot> matches = loadAll().stream()
            .filter(s -> Objects.equals(s.locator, loc))
            .toList();
        if (matches.isEmpty()) return null;

        String normalizedPageUrl = normalizeUrl(pageUrl);
        for (ElementSnapshot snapshot : matches) {
            if (Objects.equals(normalizeUrl(snapshot.pageUrl), normalizedPageUrl)) {
                return snapshot;
            }
        }

        return matches.stream()
            .max(Comparator.comparingInt(s -> urlSimilarity(normalizedPageUrl, normalizeUrl(s.pageUrl))))
            .orElse(null);
    }
    public int size(){return loadAll().size();}

    private int indexOf(List<ElementSnapshot> all, String pageUrl, String loc){
        String normalizedPageUrl = normalizeUrl(pageUrl);
        for (int i = 0; i < all.size(); i++) {
            ElementSnapshot snapshot = all.get(i);
            if (Objects.equals(snapshot.locator, loc) && Objects.equals(normalizeUrl(snapshot.pageUrl), normalizedPageUrl)) {
                return i;
            }
        }
        return -1;
    }

    private static String normalizeUrl(String url){
        if(url==null)return "";
        String normalized = url.trim().toLowerCase(Locale.ROOT);
        int hash = normalized.indexOf('#');
        if(hash >= 0) normalized = normalized.substring(0, hash);
        int query = normalized.indexOf('?');
        if(query >= 0) normalized = normalized.substring(0, query);
        return normalized;
    }

    private static int urlSimilarity(String a, String b){
        int max = Math.min(a.length(), b.length());
        int score = 0;
        for(int i = 0; i < max; i++){
            if(a.charAt(i) != b.charAt(i)) break;
            score++;
        }
        return score;
    }

    private static boolean isBlank(String value){
        return value == null || value.isBlank();
    }
}
