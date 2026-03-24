package com.visual.model;

public final class CollectedElementMetadata {
    private final int x;
    private final int y;
    private final int w;
    private final int h;
    private final String text;
    private final String selector;
    private final String kind;
    private final String tag;
    private final String accessibleName;
    private final String semanticRole;
    private final String autocomplete;

    public CollectedElementMetadata(int x, int y, int w, int h, String text, String selector, String kind,
                                    String tag, String accessibleName, String semanticRole, String autocomplete) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.text = text;
        this.selector = selector;
        this.kind = kind;
        this.tag = tag;
        this.accessibleName = accessibleName;
        this.semanticRole = semanticRole;
        this.autocomplete = autocomplete;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getW() {
        return w;
    }

    public int getH() {
        return h;
    }

    public String getText() {
        return text;
    }

    public String getSelector() {
        return selector;
    }

    public String getKind() {
        return kind;
    }

    public String getTag() {
        return tag;
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
}
