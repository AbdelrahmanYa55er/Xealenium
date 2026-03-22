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
    public synchronized void save(ElementSnapshot s){
        try{List<ElementSnapshot> all=loadAll();all.removeIf(x->x.locator.equals(s.locator));all.add(s);m.writeValue(f,all);}
        catch(Exception e){System.err.println("[VISUAL-STORE] save:"+e.getMessage());}
    }
    public synchronized List<ElementSnapshot> loadAll(){
        if(!f.exists())return new ArrayList<>();
        try{return m.readValue(f,new TypeReference<List<ElementSnapshot>>(){});}
        catch(Exception e){System.err.println("[VISUAL-STORE] load:"+e.getMessage());return new ArrayList<>();}
    }
    public ElementSnapshot find(String loc){
        return loadAll().stream().filter(s->s.locator.equals(loc)).findFirst().orElse(null);
    }
    public int size(){return loadAll().size();}
}
