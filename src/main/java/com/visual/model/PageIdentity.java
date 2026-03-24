package com.visual.model;

public final class PageIdentity {
    public final String pageTitle;
    public final String pageFingerprint;

    public PageIdentity(String pageTitle, String pageFingerprint) {
        this.pageTitle = pageTitle == null ? "" : pageTitle;
        this.pageFingerprint = pageFingerprint == null ? "" : pageFingerprint;
    }
}
