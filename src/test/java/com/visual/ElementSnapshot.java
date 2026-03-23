package com.visual;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElementSnapshot {
    public String locator, screenshotBase64, text, pageUrl, kind, tagName, accessibleName, semanticRole, autocomplete,
        labelText, placeholder, descriptionText, sectionContext, parentContext, inputType,
        semanticFingerprint, embeddingModel;
    public float[] embeddingVector;
    public int x, y, w, h;
    public ElementSnapshot() {}
    public ElementSnapshot(String loc, String b64, int x, int y, int w, int h, String txt, String url,
                           String kind, String tagName, String accessibleName, String semanticRole, String autocomplete) {
        this.locator=loc; this.screenshotBase64=b64;
        this.x=x; this.y=y; this.w=w; this.h=h; this.text=txt; this.pageUrl=url;
        this.kind=kind; this.tagName=tagName;
        this.accessibleName = accessibleName;
        this.semanticRole = semanticRole;
        this.autocomplete = autocomplete;
    }

    public ElementSnapshot withEmbedding(String semanticFingerprint, String embeddingModel, float[] embeddingVector) {
        this.semanticFingerprint = semanticFingerprint;
        this.embeddingModel = embeddingModel;
        this.embeddingVector = embeddingVector;
        return this;
    }

    public ElementSnapshot withSemanticContext(String labelText, String placeholder, String descriptionText,
                                               String sectionContext, String parentContext, String inputType) {
        this.labelText = labelText;
        this.placeholder = placeholder;
        this.descriptionText = descriptionText;
        this.sectionContext = sectionContext;
        this.parentContext = parentContext;
        this.inputType = inputType;
        return this;
    }
}
