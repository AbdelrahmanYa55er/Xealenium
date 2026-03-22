package com.visual;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
@JsonIgnoreProperties(ignoreUnknown = true)
public class ElementSnapshot {
    public String locator, screenshotBase64, text, pageUrl;
    public int x, y, w, h;
    public ElementSnapshot() {}
    public ElementSnapshot(String loc, String b64, int x, int y, int w, int h, String txt, String url) {
        this.locator=loc; this.screenshotBase64=b64;
        this.x=x; this.y=y; this.w=w; this.h=h; this.text=txt; this.pageUrl=url;
    }
}
