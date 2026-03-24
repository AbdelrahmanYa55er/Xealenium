package com.visual.model;

public final class PageIdentity {
    public final String pageTitle;
    public final String pageFingerprint;
    public final String normalizedPath;
    public final String headingFingerprint;
    public final String formFingerprint;

    public PageIdentity(String pageTitle, String pageFingerprint) {
        this(pageTitle, pageFingerprint, "", "", "");
    }

    public PageIdentity(String pageTitle, String pageFingerprint, String normalizedPath,
                        String headingFingerprint, String formFingerprint) {
        this.pageTitle = pageTitle == null ? "" : pageTitle;
        this.pageFingerprint = pageFingerprint == null ? "" : pageFingerprint;
        this.normalizedPath = normalizedPath == null ? "" : normalizedPath;
        this.headingFingerprint = headingFingerprint == null ? "" : headingFingerprint;
        this.formFingerprint = formFingerprint == null ? "" : formFingerprint;
    }
}
