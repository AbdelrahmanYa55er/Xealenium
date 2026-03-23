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
import java.util.Objects;

public class VisualHealingEngine {
    private static final double W_VIS=0.18, W_POS=0.18, W_TXT=0.14, W_KIND=0.20, W_SEQ=0.30;
    private static final double THR=0.56, MAX_D=600.0;
    private final BaselineStore store;
    private int healCount = 0;
    private boolean interactiveMode = false;

    public static final List<ReportEntry> REPORTS = new ArrayList<>();

    public VisualHealingEngine(){store=new BaselineStore();}
    public VisualHealingEngine(BaselineStore s){store=s;}

    public void setInteractiveMode(boolean b){ this.interactiveMode = b; }

    public void captureBaseline(WebDriver d, WebElement el, By loc){
        try{
            String pageUrl = d.getCurrentUrl();
            if (!shouldCaptureBaseline(pageUrl)) {
                return;
            }
            Map<String,Object> meta = metadata(d, el);
            int x=iv(meta,"x"),y=iv(meta,"y"),w=iv(meta,"w"),h=iv(meta,"h");
            if(w<=0||h<=0)return;
            BufferedImage page=ImageUtils.screenshotPage(d);
            String b64=ImageUtils.toBase64(ImageUtils.crop(page,x,y,w,h));
            String txt=sv(meta,"text");
            String kind=sv(meta,"kind");
            String tagName=sv(meta,"tag");
            boolean replaceExisting = Boolean.parseBoolean(System.getProperty("visual.captureBaseline.refresh", "false"));
            boolean saved = store.save(new ElementSnapshot(loc.toString(),b64,x,y,w,h,txt,pageUrl,kind,tagName), replaceExisting);
            if(saved){
                System.out.println("[VISUAL-CAPTURE] "+loc+" page="+pageUrl+" kind="+kind+" box=["+x+","+y+","+w+"x"+h+"] text='"+txt+"'");
            } else {
                System.out.println("[VISUAL-CAPTURE] Skipped existing baseline for "+loc+" page="+pageUrl);
            }
        }catch(Exception e){System.err.println("[VISUAL-CAPTURE] "+loc+": "+e.getMessage());}
    }

    public ScoreResult heal(WebDriver d, By loc){
        String key=loc.toString();
        ElementSnapshot base=store.find(d.getCurrentUrl(), key);
        if(base==null){System.out.println("[VISUAL-HEAL] No baseline: "+key);return ScoreResult.aborted(0.0,0);}
        try{
            BufferedImage pageImg=ImageUtils.screenshotPage(d);
            BufferedImage tmpl=ImageUtils.fromBase64(base.screenshotBase64);

            List<Map<String,Object>> rawCands=storeCandidatesAndGetMeta(d);
            List<CandidateMeta> candidateMeta = new ArrayList<>();
            for(int idx=0; idx<rawCands.size(); idx++){
                Map<String,Object> c=rawCands.get(idx);
                int cx=iv(c,"x"),cy=iv(c,"y"),cw=iv(c,"w"),ch=iv(c,"h");
                if(cw<=0||ch<=0)continue;
                candidateMeta.add(new CandidateMeta(idx, cx, cy, cw, ch, sv(c,"text"), sv(c,"selector"), sv(c,"kind")));
            }

            String baseKind = resolveBaseKind(base, key);
            int baseSequence = baselineSequence(base, key, baseKind);
            int baseKindCount = baselineKindCount(base, baseKind);
            assignSequencePositions(candidateMeta);

            List<CandidateWrapper> sortedCands = new ArrayList<>();
            List<HeatmapRenderer.Candidate> heatCands = new ArrayList<>();

            for(CandidateMeta c : candidateMeta){
                double vis=ImageUtils.templateMatch(ImageUtils.crop(pageImg,c.x,c.y,c.w,c.h),tmpl);
                double pos=ImageUtils.positionScore(base.x,base.y,base.w,base.h,c.x,c.y,c.w,c.h,MAX_D);
                double txt=SemanticSimilarity.score(base.text, c.text);
                double kind=kindScore(baseKind, c.kind);
                double seq=sequenceScore(baseSequence, baseKindCount, c.sequence, c.kindCount, baseKind, c.kind);
                double score=W_VIS*vis+W_POS*pos+W_TXT*txt+W_KIND*kind+W_SEQ*seq;

                heatCands.add(new HeatmapRenderer.Candidate(c.x,c.y,c.w,c.h,score,c.text + " [" + c.kind + " #" + c.sequence + "]"));
                sortedCands.add(new CandidateWrapper(score, c.originalIndex, c.selector, c.kind, c.sequence, vis, pos, txt, kind, seq, c.x + c.w/2, c.y + c.h/2));
            }

            sortedCands.sort(Comparator.comparingDouble((CandidateWrapper cw) -> cw.score).reversed());

            if (sortedCands.isEmpty() || sortedCands.get(0).score < THR) {
                return abortAndReport(key, sortedCands.isEmpty() ? 0.0 : sortedCands.get(0).score, candidateMeta.size(), pageImg, heatCands, -1);
            }

            if (interactiveMode) {
                return promptUserLoop(key, pageImg, heatCands, sortedCands, candidateMeta.size());
            }
            CandidateWrapper best = sortedCands.get(0);
            return healAndReport(key, best, candidateMeta.size(), pageImg, heatCands, best.originalIndex);
        }catch(Exception e){
            System.err.println("[VISUAL-HEAL] "+key+": "+e.getMessage());
            return ScoreResult.aborted(0.0,0);
        }
    }

    private ScoreResult promptUserLoop(String key, BufferedImage pageImg,
            List<HeatmapRenderer.Candidate> heatCands, List<CandidateWrapper> sorted, int totalCands) {

        for (int i = 0; i < sorted.size(); i++) {
            CandidateWrapper cand = sorted.get(i);
            if (cand.score < THR) break;

            BufferedImage heatmap = HeatmapRenderer.renderImage(pageImg, heatCands, cand.originalIndex,
                String.format("%s  [Rank %d | Score %.3f]", key, i+1, cand.score));

            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            int w = heatmap.getWidth(), h = heatmap.getHeight();
            if (h > screen.height - 250 || w > screen.width - 200) {
                double scale = Math.min((screen.width - 200.0) / w, (screen.height - 250.0) / h);
                heatmap = ImageUtils.scale(heatmap, (int)(w * scale), (int)(h * scale));
            }

            JPanel panel = new JPanel(new BorderLayout(0, 10));
            String info = String.format(
                "<html><b>Locator:</b> %s<br>" +
                "<b>Candidate Rank:</b> %d of %d above threshold<br>" +
                "<b>Candidate Kind:</b> %s<br>" +
                "<b>Sequence:</b> #%d<br>" +
                "<b>New CSS Selector:</b> <code>%s</code><br>" +
                "<b>Score:</b> %.3f &nbsp;(visual=%.2f &nbsp;position=%.2f &nbsp;text=%.2f &nbsp;kind=%.2f &nbsp;seq=%.2f)</html>",
                key, i+1, sorted.size(), cand.kind, cand.sequence, cand.selector, cand.score, cand.vis, cand.pos, cand.txt, cand.kindScore, cand.seqScore);
            JLabel infoLabel = new JLabel(info);
            infoLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
            panel.add(infoLabel, BorderLayout.NORTH);

            JLabel imgLabel = new JLabel(new ImageIcon(heatmap));
            JScrollPane scroll = new JScrollPane(imgLabel);
            scroll.setPreferredSize(new Dimension(
                Math.min(heatmap.getWidth() + 30, screen.width - 100),
                Math.min(heatmap.getHeight() + 30, screen.height - 300)));
            panel.add(scroll, BorderLayout.CENTER);

            Object[] options = {"Confirm", "Try Next Best", "Refuse (Abort)"};
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
        System.out.println("[VISUAL-HEAL] "+key+" | "+r+" kind="+cand.kind+" seq="+cand.sequence+" kindScore="+String.format("%.2f", cand.kindScore)+" seqScore="+String.format("%.2f", cand.seqScore));
        return r;
    }

    private ScoreResult abortAndReport(String key, double bestScore, int totalCands, BufferedImage img, List<HeatmapRenderer.Candidate> heatCands, int displayIdx) {
        healCount++;
        String safe = key.replaceAll("[^a-zA-Z0-9]","_");
        String heatmapFile = "visual-heatmap-" + safe + "-" + healCount + ".png";
        HeatmapRenderer.render(img, heatCands, displayIdx, key, heatmapFile);
        REPORTS.add(new ReportEntry(key, "ABORTED (Score too low / User Refused)", bestScore, heatmapFile));
        ScoreResult r=ScoreResult.aborted(bestScore, totalCands);
        System.out.println("[VISUAL-HEAL] "+key+" | "+r);
        return r;
    }

    private static class CandidateMeta {
        final int originalIndex;
        final int x, y, w, h;
        final String text, selector, kind;
        int sequence = 1;
        int kindCount = 1;
        CandidateMeta(int originalIndex, int x, int y, int w, int h, String text, String selector, String kind) {
            this.originalIndex = originalIndex;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.text = text; this.selector = selector; this.kind = kind;
        }
    }

    private static class CandidateWrapper {
        final double score, vis, pos, txt, kindScore, seqScore;
        final int originalIndex, sequence, cx, cy;
        final String selector, kind;
        CandidateWrapper(double score, int originalIndex, String selector, String kind, int sequence,
                         double vis, double pos, double txt, double kindScore, double seqScore, int cx, int cy) {
            this.score = score; this.originalIndex = originalIndex; this.selector = selector; this.kind = kind; this.sequence = sequence;
            this.vis = vis; this.pos = pos; this.txt = txt; this.kindScore = kindScore; this.seqScore = seqScore; this.cx = cx; this.cy = cy;
        }
    }

    private void assignSequencePositions(List<CandidateMeta> candidates) {
        List<String> kinds = List.of("text", "select", "toggle", "action", "generic");
        for (String kind : kinds) {
            List<CandidateMeta> sameKind = candidates.stream()
                .filter(c -> Objects.equals(kind, c.kind))
                .sorted(Comparator.comparingInt((CandidateMeta c) -> c.y).thenComparingInt(c -> c.x))
                .toList();
            for (int i = 0; i < sameKind.size(); i++) {
                CandidateMeta candidate = sameKind.get(i);
                candidate.sequence = i + 1;
                candidate.kindCount = sameKind.size();
            }
        }
    }

    private int baselineSequence(ElementSnapshot base, String locator, String baseKind) {
        List<ElementSnapshot> sameKind = baselineSnapshots(base, baseKind);
        for (int i = 0; i < sameKind.size(); i++) {
            if (Objects.equals(sameKind.get(i).locator, locator)) {
                return i + 1;
            }
        }
        int inferred = inferSequenceFromLocator(locator);
        if (inferred > 0) return inferred;
        return 1;
    }

    private int baselineKindCount(ElementSnapshot base, String baseKind) {
        int count = baselineSnapshots(base, baseKind).size();
        return Math.max(count, 1);
    }

    private List<ElementSnapshot> baselineSnapshots(ElementSnapshot base, String baseKind) {
        return store.loadAll().stream()
            .filter(s -> samePage(base.pageUrl, s.pageUrl))
            .filter(s -> Objects.equals(resolveBaseKind(s, s.locator), baseKind))
            .sorted(Comparator.comparingInt((ElementSnapshot s) -> s.y).thenComparingInt(s -> s.x))
            .toList();
    }

    private static boolean samePage(String a, String b) {
        return normalizeUrl(a).equals(normalizeUrl(b));
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String normalized = url.trim().toLowerCase();
        int hash = normalized.indexOf('#');
        if (hash >= 0) normalized = normalized.substring(0, hash);
        int query = normalized.indexOf('?');
        if (query >= 0) normalized = normalized.substring(0, query);
        return normalized;
    }

    private static double sequenceScore(int baseSequence, int baseCount, int candidateSequence, int candidateCount, String baseKind, String candidateKind) {
        if (!Objects.equals(baseKind, candidateKind)) {
            if ((Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select")) ||
                (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text"))) {
                return 0.85;
            }
            return 0.05;
        }
        int span = Math.max(Math.max(baseCount, candidateCount) - 1, 1);
        int delta = Math.abs(baseSequence - candidateSequence);
        return Math.max(0.0, 1.0 - ((double) delta / span));
    }

    private static int inferSequenceFromLocator(String locator) {
        String key = locator == null ? "" : locator.toLowerCase();
        if (key.contains("fname") || key.contains("first")) return 1;
        if (key.contains("lname") || key.contains("last") || key.contains("surname")) return 2;
        if (key.contains("email") || key.contains("mail")) return 3;
        if (key.contains("phone") || key.contains("tel")) return 4;
        if (key.contains("city") || key.contains("town")) return 5;
        if (key.contains("zip") || key.contains("postal")) return 6;
        if (key.contains("country") || key.contains("location")) return 1;
        if (key.contains("terms") || key.contains("agreement")) return 1;
        if (key.contains("newsletter") || key.contains("feed")) return 2;
        if (key.contains("submit") || key.contains("register") || key.contains("finish")) return 1;
        return -1;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> storeCandidatesAndGetMeta(WebDriver d){
        String js =
            "function push(parts, value) {"
            +"  if (value === null || value === undefined) return;"
            +"  value = String(value).trim();"
            +"  if (value) parts.push(value);"
            +"}"
            +"function nodeText(el) {"
            +"  return el && el.innerText ? el.innerText.trim() : '';"
            +"}"
            +"function nearestLabelText(el) {"
            +"  var parts = [];"
            +"  if (el.id) {"
            +"    var byFor = document.querySelector('label[for=\\\"'+el.id+'\\\"]');"
            +"    push(parts, nodeText(byFor));"
            +"  }"
            +"  var prev = el.previousElementSibling;"
            +"  while (prev && parts.length < 2) {"
            +"    push(parts, nodeText(prev));"
            +"    prev = prev.previousElementSibling;"
            +"  }"
            +"  return [...new Set(parts)].join(' | ');"
            +"}"
            +"function classify(el) {"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var type = (el.getAttribute('type') || '').toLowerCase();"
            +"  var role = (el.getAttribute('role') || '').toLowerCase();"
            +"  var editable = (el.getAttribute('contenteditable') || '').toLowerCase() === 'true';"
            +"  if (tag === 'select') return 'select';"
            +"  if (tag === 'textarea' || editable) return 'text';"
            +"  if (tag === 'input') {"
            +"    if (type === 'checkbox' || type === 'radio') return 'toggle';"
            +"    if (type === 'button' || type === 'submit' || type === 'reset') return 'action';"
            +"    return 'text';"
            +"  }"
            +"  if (role === 'checkbox' || role === 'switch' || role === 'radio') return 'toggle';"
            +"  if (el.classList.contains('fake-toggle') || el.querySelector('.toggle-box')) return 'toggle';"
            +"  if (tag === 'button' || tag === 'a' || role === 'button' || role === 'link' || !!el.onclick) return 'action';"
            +"  return 'generic';"
            +"}"
            +"function getCssPath(el) {"
            +"  if (!(el instanceof Element)) return '';"
            +"  var path = [];"
            +"  while (el && el.nodeType === Node.ELEMENT_NODE) {"
            +"    var selector = el.nodeName.toLowerCase();"
            +"    if (el.id) {"
            +"      selector += '#' + el.id;"
            +"      path.unshift(selector);"
            +"      break;"
            +"    }"
            +"    var sib = el, nth = 1;"
            +"    while ((sib = sib.previousElementSibling)) { if (sib.nodeName.toLowerCase() === selector) nth++; }"
            +"    if (nth !== 1) selector += ':nth-of-type(' + nth + ')';"
            +"    var className = (el.className || '').trim();"
            +"    if (className) selector += '.' + className.split(/\\s+/).join('.');"
            +"    path.unshift(selector);"
            +"    el = el.parentElement;"
            +"  }"
            +"  return path.join(' > ');"
            +"}"
            +"function elementText(el) {"
            +"  var parts = [];"
            +"  push(parts, el.value);"
            +"  push(parts, el.getAttribute('placeholder'));"
            +"  push(parts, el.getAttribute('aria-label'));"
            +"  push(parts, nodeText(el));"
            +"  push(parts, nearestLabelText(el));"
            +"  return [...new Set(parts)].join(' | ').substring(0, 120);"
            +"}"
            +"var sel='input,select,button,textarea,a,[role],[tabindex],[contenteditable=true]';"
            +"var els=document.querySelectorAll(sel);"
            +"window.__visualCandidates=[];"
            +"var out=[];"
            +"for(var i=0;i<els.length;i++){"
            +"  var e=els[i], r=e.getBoundingClientRect();"
            +"  if(r.width<=0||r.height<=0) continue;"
            +"  var idx=window.__visualCandidates.length;"
            +"  window.__visualCandidates.push(e);"
            +"  out.push({"
            +"    x:Math.round(r.left),"
            +"    y:Math.round(r.top),"
            +"    w:Math.round(r.width),"
            +"    h:Math.round(r.height),"
            +"    text:elementText(e),"
            +"    idx:idx,"
            +"    selector:getCssPath(e),"
            +"    kind:classify(e),"
            +"    tag:e.tagName.toLowerCase()"
            +"  });"
            +"}"
            +"return out;";
        return(List<Map<String,Object>>)((JavascriptExecutor)d).executeScript(js);
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> metadata(WebDriver d,WebElement el){
        return(Map<String,Object>)((JavascriptExecutor)d).executeScript(
            "function push(parts, value) {"
            +"  if (value === null || value === undefined) return;"
            +"  value = String(value).trim();"
            +"  if (value) parts.push(value);"
            +"}"
            +"function nodeText(el) { return el && el.innerText ? el.innerText.trim() : ''; }"
            +"function nearestLabelText(el) {"
            +"  var parts = [];"
            +"  if (el.id) {"
            +"    var byFor = document.querySelector('label[for=\\\"'+el.id+'\\\"]');"
            +"    push(parts, nodeText(byFor));"
            +"  }"
            +"  var prev = el.previousElementSibling;"
            +"  while (prev && parts.length < 2) { push(parts, nodeText(prev)); prev = prev.previousElementSibling; }"
            +"  return [...new Set(parts)].join(' | ');"
            +"}"
            +"function classify(el) {"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var type = (el.getAttribute('type') || '').toLowerCase();"
            +"  var role = (el.getAttribute('role') || '').toLowerCase();"
            +"  var editable = (el.getAttribute('contenteditable') || '').toLowerCase() === 'true';"
            +"  if (tag === 'select') return 'select';"
            +"  if (tag === 'textarea' || editable) return 'text';"
            +"  if (tag === 'input') {"
            +"    if (type === 'checkbox' || type === 'radio') return 'toggle';"
            +"    if (type === 'button' || type === 'submit' || type === 'reset') return 'action';"
            +"    return 'text';"
            +"  }"
            +"  if (role === 'checkbox' || role === 'switch' || role === 'radio') return 'toggle';"
            +"  if (el.classList.contains('fake-toggle') || el.querySelector('.toggle-box')) return 'toggle';"
            +"  if (tag === 'button' || tag === 'a' || role === 'button' || role === 'link' || !!el.onclick) return 'action';"
            +"  return 'generic';"
            +"}"
            +"var e=arguments[0], r=e.getBoundingClientRect(), parts=[];"
            +"push(parts, e.value);"
            +"push(parts, e.getAttribute('placeholder'));"
            +"push(parts, e.getAttribute('aria-label'));"
            +"push(parts, nodeText(e));"
            +"push(parts, nearestLabelText(e));"
            +"return {"
            +"  x:Math.round(r.left), y:Math.round(r.top), w:Math.round(r.width), h:Math.round(r.height),"
            +"  text:[...new Set(parts)].join(' | ').substring(0,120),"
            +"  kind:classify(e), tag:e.tagName.toLowerCase()"
            +"};",el);
    }

    private static String resolveBaseKind(ElementSnapshot base, String locator){
        String key = locator == null ? "" : locator.toLowerCase();
        if(key.contains("country")) return "select";
        if(key.contains("terms") || key.contains("newsletter")) return "toggle";
        if(key.contains("submit") || key.contains("register") || key.contains("finish") || key.contains("button")) return "action";
        if(key.contains("fname") || key.contains("lname") || key.contains("email") || key.contains("phone") || key.contains("city") || key.contains("zip") || key.contains("postal")) return "text";
        if(base != null && base.kind != null && !base.kind.isBlank()) return base.kind;
        String text = base == null || base.text == null ? "" : base.text.toLowerCase();
        if(text.contains("country") || text.contains("location")) return "select";
        if(text.contains("terms") || text.contains("agreement") || text.contains("newsletter") || (base != null && base.w <= 30 && base.h <= 30)) return "toggle";
        if(text.contains("register") || text.contains("finish") || text.contains("submit")) return "action";
        return "text";
    }

    private static double kindScore(String baseKind, String candidateKind){
        if(baseKind == null || baseKind.isBlank()) return 0.5;
        if(candidateKind == null || candidateKind.isBlank()) return 0.3;
        if(baseKind.equals(candidateKind)) return 1.0;
        if((baseKind.equals("text") && candidateKind.equals("select")) || (baseKind.equals("select") && candidateKind.equals("text"))) return 0.80;
        if(candidateKind.equals("generic") || baseKind.equals("generic")) return 0.40;
        return 0.05;
    }

    private static int iv(Map<String,Object> m,String k){Object v=m.get(k);if(v instanceof Number)return((Number)v).intValue();if(v instanceof String)try{return Integer.parseInt((String)v);}catch(Exception e){}return 0;}
    private static String sv(Map<String,Object> m,String k){Object v=m.get(k);return v==null?"":v.toString();}
    public BaselineStore getStore(){return store;}

    private boolean shouldCaptureBaseline(String pageUrl) {
        String explicit = System.getProperty("visual.captureBaseline");
        if (explicit != null && !explicit.isBlank()) {
            return Boolean.parseBoolean(explicit);
        }
        return pageUrl != null && pageUrl.toLowerCase().contains("baseline");
    }

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


