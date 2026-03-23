package com.visual;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class VisualHealingEngine {
    private static final double W_VIS=0.40, W_POS=0.30, W_TXT=0.30;
    private static final double THR=0.45, MAX_D=600.0;
    private final BaselineStore store;
    private int healCount = 0;
    private boolean interactiveMode = false;

    public static final List<ReportEntry> REPORTS = new ArrayList<>();

    public VisualHealingEngine(){store=new BaselineStore();}
    public VisualHealingEngine(BaselineStore s){store=s;}

    public void setInteractiveMode(boolean b){ this.interactiveMode = b; }

    public void captureBaseline(WebDriver d, WebElement el, By loc){
        try{
            BufferedImage page=ImageUtils.screenshotPage(d);
            Map<String,Object> r=rect(d,el);
            int x=iv(r,"x"),y=iv(r,"y"),w=iv(r,"w"),h=iv(r,"h");
            if(w<=0||h<=0)return;
            String b64=ImageUtils.toBase64(ImageUtils.crop(page,x,y,w,h));
            String txt=richText(d,el);
            store.save(new ElementSnapshot(loc.toString(),b64,x,y,w,h,txt,d.getCurrentUrl()));
            System.out.println("[VISUAL-CAPTURE] "+loc+" box=["+x+","+y+","+w+"x"+h+"] text='"+txt+"'");
        }catch(Exception e){System.err.println("[VISUAL-CAPTURE] "+loc+": "+e.getMessage());}
    }

    public ScoreResult heal(WebDriver d, By loc){
        String key=loc.toString();
        ElementSnapshot base=store.find(key);
        if(base==null){System.out.println("[VISUAL-HEAL] No baseline: "+key);return ScoreResult.aborted(0.0,0);}
        try{
            BufferedImage pageImg=ImageUtils.screenshotPage(d);
            BufferedImage tmpl=ImageUtils.fromBase64(base.screenshotBase64);

            List<Map<String,Object>> rawCands=storeCandidatesAndGetMeta(d);
            List<CandidateWrapper> sortedCands = new ArrayList<>();
            List<HeatmapRenderer.Candidate> heatCands = new ArrayList<>();

            for(int idx=0; idx<rawCands.size(); idx++){
                Map<String,Object> c=rawCands.get(idx);
                int cx=iv(c,"x"),cy=iv(c,"y"),cw=iv(c,"w"),ch=iv(c,"h");
                if(cw<=0||ch<=0)continue;
                String cText=sv(c,"text"), cSel=sv(c,"selector");

                double vis=ImageUtils.templateMatch(ImageUtils.crop(pageImg,cx,cy,cw,ch),tmpl);
                double pos=ImageUtils.positionScore(base.x,base.y,base.w,base.h,cx,cy,cw,ch,MAX_D);
                double txt=SemanticSimilarity.score(base.text, cText);
                double score=W_VIS*vis+W_POS*pos+W_TXT*txt;

                heatCands.add(new HeatmapRenderer.Candidate(cx,cy,cw,ch,score,cText));
                sortedCands.add(new CandidateWrapper(score, idx, cSel, cx+cw/2, cy+ch/2, vis, pos, txt));
            }

            sortedCands.sort(Comparator.comparingDouble((CandidateWrapper cw) -> cw.score).reversed());

            if (sortedCands.isEmpty() || sortedCands.get(0).score < THR) {
                return abortAndReport(key, sortedCands.isEmpty() ? 0.0 : sortedCands.get(0).score, rawCands.size(), pageImg, heatCands, -1);
            }

            if (interactiveMode) {
                return promptUserLoop(key, pageImg, heatCands, sortedCands, rawCands.size());
            } else {
                CandidateWrapper best = sortedCands.get(0);
                return healAndReport(key, best, rawCands.size(), pageImg, heatCands, best.originalIndex);
            }
        }catch(Exception e){
            System.err.println("[VISUAL-HEAL] "+key+": "+e.getMessage());
            return ScoreResult.aborted(0.0,0);
        }
    }

    /**
     * Human-in-the-loop: shows a JOptionPane dialog with the heatmap image
     * and 3 buttons: Confirm / Try Next Best / Refuse (Abort)
     */
    private ScoreResult promptUserLoop(String key, BufferedImage pageImg,
            List<HeatmapRenderer.Candidate> heatCands, List<CandidateWrapper> sorted, int totalCands) {

        for (int i = 0; i < sorted.size(); i++) {
            CandidateWrapper cand = sorted.get(i);
            if (cand.score < THR) break;

            // Render heatmap highlighting THIS candidate
            BufferedImage heatmap = HeatmapRenderer.renderImage(pageImg, heatCands, cand.originalIndex,
                String.format("%s  [Rank %d | Score %.3f]", key, i+1, cand.score));

            // Scale for screen
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = heatmap.getWidth(), h = heatmap.getHeight();
            if (h > screen.height - 250 || w > screen.width - 200) {
                double scale = Math.min((screen.width - 200.0) / w, (screen.height - 250.0) / h);
                heatmap = ImageUtils.scale(heatmap, (int)(w * scale), (int)(h * scale));
            }

            // Build info panel
            JPanel panel = new JPanel(new BorderLayout(0, 10));

            String info = String.format(
                "<html><b>Locator:</b> %s<br>" +
                "<b>Candidate Rank:</b> %d of %d above threshold<br>" +
                "<b>New CSS Selector:</b> <code>%s</code><br>" +
                "<b>Score:</b> %.3f &nbsp;(visual=%.2f &nbsp;position=%.2f &nbsp;text=%.2f)</html>",
                key, i+1, sorted.size(), cand.selector, cand.score, cand.vis, cand.pos, cand.txt);
            JLabel infoLabel = new JLabel(info);
            infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            panel.add(infoLabel, BorderLayout.NORTH);

            JLabel imgLabel = new JLabel(new ImageIcon(heatmap));
            JScrollPane scroll = new JScrollPane(imgLabel);
            scroll.setPreferredSize(new Dimension(
                Math.min(heatmap.getWidth() + 30, screen.width - 100),
                Math.min(heatmap.getHeight() + 30, screen.height - 300)));
            panel.add(scroll, BorderLayout.CENTER);

            // 3-button dialog
            Object[] options = {"\u2705 Confirm", "\u27A1 Try Next Best", "\u274C Refuse (Abort)"};
            JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                JOptionPane.YES_NO_CANCEL_OPTION, null, options, options[0]);
            JDialog dialog = pane.createDialog("Visual Healing Confirmation - " + key);
            dialog.setAlwaysOnTop(true);
            dialog.setModal(true);
            dialog.setResizable(true);
            dialog.setVisible(true);

            Object selectedValue = pane.getValue();
            int choice = -1;
            for (int j = 0; j < options.length; j++) {
                if (options[j].equals(selectedValue)) { choice = j; break; }
            }

            if (choice == 0) {
                System.out.println("[INTERACTIVE] User CONFIRMED " + key + " -> " + cand.selector);
                return healAndReport(key, cand, totalCands, pageImg, heatCands, cand.originalIndex);
            } else if (choice == 1) {
                System.out.println("[INTERACTIVE] User requested NEXT BEST for " + key);
                continue;
            } else {
                System.out.println("[INTERACTIVE] User REFUSED " + key);
                break;
            }
        }
        return abortAndReport(key, sorted.get(0).score, totalCands, pageImg, heatCands, -1);
    }

    private ScoreResult healAndReport(String key, CandidateWrapper cand, int totalCands, BufferedImage img, List<HeatmapRenderer.Candidate> heatCands, int displayIdx) {
        healCount++;
        String safe = key.replaceAll("[^a-zA-Z0-9]","_");
        String heatmapFile = "visual-heatmap-" + safe + "-" + healCount + ".png";
        HeatmapRenderer.render(img, heatCands, displayIdx, key, heatmapFile);
        REPORTS.add(new ReportEntry(key, cand.selector, cand.score, heatmapFile));
        ScoreResult r=ScoreResult.healed(cand.score, cand.vis, cand.pos, cand.txt, totalCands, cand.cx, cand.cy, cand.originalIndex);
        System.out.println("[VISUAL-HEAL] "+key+" | "+r); return r;
    }

    private ScoreResult abortAndReport(String key, double bestScore, int totalCands, BufferedImage img, List<HeatmapRenderer.Candidate> heatCands, int displayIdx) {
        healCount++;
        String safe = key.replaceAll("[^a-zA-Z0-9]","_");
        String heatmapFile = "visual-heatmap-" + safe + "-" + healCount + ".png";
        HeatmapRenderer.render(img, heatCands, displayIdx, key, heatmapFile);
        REPORTS.add(new ReportEntry(key, "ABORTED (Score too low / User Refused)", bestScore, heatmapFile));
        ScoreResult r=ScoreResult.aborted(bestScore, totalCands);
        System.out.println("[VISUAL-HEAL] "+key+" | "+r); return r;
    }

    private static class CandidateWrapper {
        double score, vis, pos, txt; int originalIndex, cx, cy; String selector;
        CandidateWrapper(double s, int oi, String sel, int x, int y, double v, double p, double t) {
            score=s; originalIndex=oi; selector=sel; cx=x; cy=y; vis=v; pos=p; txt=t;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> storeCandidatesAndGetMeta(WebDriver d){
        String js =
            "function getCssPath(el) {"
            +"  if (!(el instanceof Element)) return '';"
            +"  var path = [];"
            +"  while (el.nodeType === Node.ELEMENT_NODE) {"
            +"    var selector = el.nodeName.toLowerCase();"
            +"    if (el.id) {"
            +"      selector += '#' + el.id;"
            +"      path.unshift(selector);"
            +"      break;"
            +"    } else {"
            +"      var sib = el, nth = 1;"
            +"      while (sib = sib.previousElementSibling) { if (sib.nodeName.toLowerCase() == selector) nth++; }"
            +"      if (nth != 1) selector += ':nth-of-type('+nth+')';"
            +"    }"
            +"    var classes=el.className.trim().split(/\\s+/);"
            +"    if(classes.length>0&&classes[0]!='') selector += '.' + classes.join('.');"
            +"    path.unshift(selector);"
            +"    el = el.parentNode;"
            +"  }"
            +"  return path.join(' > ');"
            +"}"
            +"var sel='input,select,button,textarea,a,[role],[tabindex],[contenteditable=true]';"
            +"var els=document.querySelectorAll(sel);"
            +"window.__visualCandidates=[];"
            +"var out=[];"
            +"for(var i=0;i<els.length;i++){"
            +"  var e=els[i], r=e.getBoundingClientRect();"
            +"  if(r.width<=0||r.height<=0) continue;"
            +"  var parts=[];"
            +"  if(e.value) parts.push(e.value);"
            +"  if(e.getAttribute('placeholder')) parts.push(e.getAttribute('placeholder'));"
            +"  if(e.getAttribute('aria-label')) parts.push(e.getAttribute('aria-label'));"
            +"  if(e.innerText && e.innerText.trim()) parts.push(e.innerText.trim());"
            +"  if(e.id){var lb=document.querySelector('label[for=\"'+e.id+'\"]');if(lb)parts.push(lb.innerText.trim());}"
            +"  var par=e.parentElement;"
            +"  if(par){"
            +"    var sibs=par.querySelectorAll('label,span.e-title,legend,.label,span');"
            +"    for(var s=0;s<sibs.length;s++){if(sibs[s]!==e&&sibs[s].innerText)parts.push(sibs[s].innerText.trim());}"
            +"  }"
            +"  if(par&&par.parentElement){"
            +"    var gp=par.parentElement.querySelectorAll('label,span.e-title,legend,.label');"
            +"    for(var s=0;s<gp.length;s++){if(gp[s].innerText)parts.push(gp[s].innerText.trim());}"
            +"  }"
            +"  var txt=[...new Set(parts)].join(' | ').substring(0,120);"
            +"  var idx=window.__visualCandidates.length;"
            +"  window.__visualCandidates.push(e);"
            +"  var selPath=getCssPath(e);"
            +"  out.push({x:Math.round(r.left),y:Math.round(r.top),w:Math.round(r.width),h:Math.round(r.height),text:txt,idx:idx,selector:selPath});"
            +"}"
            +"return out;";
        return(List<Map<String,Object>>)((JavascriptExecutor)d).executeScript(js);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> rect(WebDriver d,WebElement el){
        return(Map<String,Object>)((JavascriptExecutor)d).executeScript(
            "var r=arguments[0].getBoundingClientRect();return{x:Math.round(r.left),y:Math.round(r.top),w:Math.round(r.width),h:Math.round(r.height)};",el);
    }

    private String richText(WebDriver d,WebElement el){
        Object v=((JavascriptExecutor)d).executeScript(
            "var e=arguments[0],p=[];"
            +"if(e.value)p.push(e.value);"
            +"if(e.getAttribute('placeholder'))p.push(e.getAttribute('placeholder'));"
            +"if(e.getAttribute('aria-label'))p.push(e.getAttribute('aria-label'));"
            +"if(e.innerText&&e.innerText.trim())p.push(e.innerText.trim());"
            +"if(e.id){var lb=document.querySelector('label[for=\"'+e.id+'\"]');if(lb)p.push(lb.innerText.trim());}"
            +"var par=e.parentElement;"
            +"if(par){var s=par.querySelectorAll('label,span,legend');for(var i=0;i<s.length;i++){if(s[i]!==e&&s[i].innerText)p.push(s[i].innerText.trim());}}"
            +"return[...new Set(p)].join(' | ').substring(0,120);",el);
        return v==null?"":v.toString();
    }

    private static int iv(Map<String,Object> m,String k){Object v=m.get(k);if(v instanceof Number)return((Number)v).intValue();if(v instanceof String)try{return Integer.parseInt((String)v);}catch(Exception e){}return 0;}
    private static String sv(Map<String,Object> m,String k){Object v=m.get(k);return v==null?"":v.toString();}
    public BaselineStore getStore(){return store;}

    public static void generateHtmlReport(String outputPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>Visual Healing Report</title>");
        sb.append("<style>body{font-family:-apple-system,sans-serif;background:#f4f4f5;padding:2rem;} ");
        sb.append(".card{background:#fff;border-radius:8px;padding:1.5rem;box-shadow:0 1px 3px rgba(0,0,0,0.1);margin-bottom:2rem;} ");
        sb.append("img{max-width:100%;border:1px solid #e4e4e7;border-radius:4px;margin-top:1rem;} ");
        sb.append(".score{display:inline-block;padding:0.25rem 0.5rem;border-radius:4px;font-weight:bold;color:#fff;} ");
        sb.append(".high{background:#10b981;} .mid{background:#f59e0b;} .low{background:#ef4444;} ");
        sb.append("h3{margin-top:0;color:#18181b;} .loc{font-family:monospace;background:#f4f4f5;padding:0.2rem 0.4rem;border-radius:3px;}</style>");
        sb.append("</head><body>");
        sb.append("<h1>Xealenium - Visual Healing Report</h1>");
        if (REPORTS.isEmpty()) { sb.append("<p>No healing events occurred during this run.</p>"); }
        for (ReportEntry r : REPORTS) {
            String cClass = r.score > 0.8 ? "high" : (r.score >= 0.55 ? "mid" : "low");
            String statusHtml = "<span class='score " + cClass + "'>" + String.format("%.3f", r.score) + "</span>";
            sb.append("<div class='card'>");
            sb.append("<h3>Failed Locator: <span class='loc'>" + r.originalLocator + "</span></h3>");
            sb.append("<p><strong>New Healed Locator:</strong> <span class='loc'>" + r.newLocator + "</span></p>");
            sb.append("<p><strong>Score:</strong> " + statusHtml + "</p>");
            sb.append("<img src='" + r.heatmapFilename + "' alt='Heatmap' />");
            sb.append("</div>");
        }
        sb.append("</body></html>");
        try {
            Files.write(Paths.get(outputPath), sb.toString().getBytes());
            System.out.println("[REPORT] Generated visual healing report -> " + outputPath);
        } catch (Exception e) {
            System.err.println("[REPORT] Failed to generate: " + e.getMessage());
        }
    }
}
