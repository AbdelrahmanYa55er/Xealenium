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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class VisualHealingEngine {
    private static final double W_VIS=0.09, W_POS=0.07, W_TXT=0.06, W_KIND=0.10, W_SEQ=0.04,
        W_ROLE=0.14, W_AUTO=0.08, W_SEM=0.20, W_FIELD=0.22, W_EMB=0.12;
    private static final double THR=0.56, MAX_D=600.0;
    private final BaselineStore store;
    private final SemanticSignalExtractor semanticExtractor;
    private final LocalEmbeddingService embeddingService;
    private int healCount = 0;
    private boolean interactiveMode = false;

    public static final List<ReportEntry> REPORTS = new ArrayList<>();

    public VisualHealingEngine(){this(new BaselineStore());}
    public VisualHealingEngine(BaselineStore s){store=s; semanticExtractor = new SemanticSignalExtractor(); embeddingService = LocalEmbeddingService.getInstance();}

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
            SemanticSignals signals = semanticExtractor.extract(d, el);
            String accessibleName=firstNonBlank(signals.getAccessibleName(), sv(meta,"accessibleName"));
            String semanticRole=firstNonBlank(signals.getSemanticRole(), sv(meta,"semanticRole"));
            String autocomplete=firstNonBlank(signals.getAutocomplete(), sv(meta,"autocomplete"));
            String labelText=signals.getLabelText();
            String placeholder=signals.getPlaceholder();
            String descriptionText=signals.getDescriptionText();
            String sectionContext=signals.getSectionContext();
            String parentContext=signals.getParentContext();
            String inputType=signals.getInputType();
            ElementSnapshot snapshot = new ElementSnapshot(loc.toString(),b64,x,y,w,h,txt,pageUrl,kind,tagName,
                accessibleName, semanticRole, autocomplete)
                .withSemanticContext(labelText, placeholder, descriptionText, sectionContext, parentContext, inputType);
            String fingerprint = EmbeddingFingerprintBuilder.forSnapshot(loc.toString(), snapshot);
            float[] embeddingVector = embeddingService.embed(fingerprint);
            snapshot.withEmbedding(fingerprint, embeddingService.getModelName(), embeddingVector);
            boolean replaceExisting = Boolean.parseBoolean(System.getProperty("visual.captureBaseline.refresh", "false"));
            boolean saved = store.save(snapshot, replaceExisting);
            if(saved){
                System.out.println("[VISUAL-CAPTURE] "+loc+" page="+pageUrl+" kind="+kind+" role="+semanticRole+" autocomplete="+autocomplete
                    +" emb="+(embeddingVector == null ? "off" : embeddingService.getModelName())
                    +" box=["+x+","+y+","+w+"x"+h+"] text='"+txt+"' accessible='"+accessibleName+"'");
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
            float[] baseEmbedding = embeddingService.embeddingForSnapshot(key, base);
            boolean embeddingsActive = baseEmbedding != null && embeddingService.isEnabled();
            String baseFieldIdentity = EmbeddingFingerprintBuilder.buildFieldIdentity(
                base.accessibleName, base.labelText, base.placeholder, base.autocomplete, base.inputType, base.text, key);
            float[] baseFieldEmbedding = embeddingsActive ? embeddingService.embed(baseFieldIdentity) : null;

            List<Map<String,Object>> rawCands=storeCandidatesAndGetMeta(d);
            List<CandidateMeta> candidateMeta = new ArrayList<>();
            for(int idx=0; idx<rawCands.size(); idx++){
                Map<String,Object> c=rawCands.get(idx);
                int cx=iv(c,"x"),cy=iv(c,"y"),cw=iv(c,"w"),ch=iv(c,"h");
                if(cw<=0||ch<=0)continue;
                WebElement candidateElement = resolveCandidateElement(d, idx);
                SemanticSignals signals = candidateElement == null
                    ? SemanticSignals.empty("unavailable")
                    : semanticExtractor.extract(d, candidateElement);
                String kind = sv(c,"kind");
                String tagName = sv(c,"tag");
                String accessibleName = firstNonBlank(signals.getAccessibleName(), sv(c,"accessibleName"));
                String semanticRole = firstNonBlank(signals.getSemanticRole(), sv(c,"semanticRole"));
                String autocomplete = firstNonBlank(signals.getAutocomplete(), sv(c,"autocomplete"));
                String labelText = signals.getLabelText();
                String placeholder = signals.getPlaceholder();
                String descriptionText = signals.getDescriptionText();
                String sectionContext = signals.getSectionContext();
                String parentContext = signals.getParentContext();
                String inputType = signals.getInputType();
                String text = sv(c,"text");
                String fingerprint = EmbeddingFingerprintBuilder.build(key, kind, tagName, accessibleName, semanticRole, autocomplete,
                    labelText, placeholder, descriptionText, sectionContext, parentContext, inputType, text);
                String fieldIdentity = EmbeddingFingerprintBuilder.buildFieldIdentity(
                    accessibleName, labelText, placeholder, autocomplete, inputType, text);
                float[] embeddingVector = embeddingsActive ? embeddingService.embed(fingerprint) : null;
                float[] fieldEmbeddingVector = embeddingsActive ? embeddingService.embed(fieldIdentity) : null;
                candidateMeta.add(new CandidateMeta(idx, cx, cy, cw, ch, text, sv(c,"selector"), kind, tagName,
                    accessibleName, semanticRole, autocomplete, labelText, placeholder, descriptionText,
                    sectionContext, parentContext, inputType, fingerprint, fieldIdentity, embeddingVector, fieldEmbeddingVector));
            }

            String baseKind = resolveBaseKind(base, key);
            List<ElementSnapshot> sameKindBaselines = baselineSnapshots(base, baseKind);
            int baseSequence = baselineSequence(base, key, baseKind);
            int baseKindCount = sameKindBaselines.size();
            assignSequencePositions(candidateMeta);
            FieldCompetitionContext fieldCompetition = buildFieldCompetitionContext(
                base, baseFieldIdentity, baseFieldEmbedding, sameKindBaselines, candidateMeta, baseKind
            );
            SmartLocatorBuilder locatorBuilder = new SmartLocatorBuilder(d);

            List<CandidateWrapper> sortedCands = new ArrayList<>();
            List<HeatmapRenderer.Candidate> heatCands = new ArrayList<>();

            for(CandidateMeta c : candidateMeta){
                double vis=ImageUtils.templateMatch(ImageUtils.crop(pageImg,c.x,c.y,c.w,c.h),tmpl);
                double pos=ImageUtils.positionScore(base.x,base.y,base.w,base.h,c.x,c.y,c.w,c.h,MAX_D);
                double txt=SemanticSimilarity.simpleScore(base.text, c.text);
                double kind=kindScore(baseKind, c.kind);
                double seq=sequenceScore(baseSequence, baseKindCount, c.sequence, c.kindCount, baseKind, c.kind);
                double role=semanticRoleScore(base.semanticRole, c.semanticRole, baseKind, c.kind);
                double autocomplete=autocompleteScore(base.autocomplete, c.autocomplete);
                double semantic=semanticTextScore(base, c);
                double fieldSemantic=fieldSemanticScore(c, fieldCompetition);
                double embedding = embeddingsActive ? embeddingScore(baseEmbedding, c.embeddingVector, baseKind, c.kind) : 0.0;
                double embeddingWeight = embeddingsActive ? W_EMB : 0.0;
                double score=W_VIS*vis+W_POS*pos+W_TXT*txt+W_KIND*kind+W_SEQ*seq
                    +W_ROLE*role+W_AUTO*autocomplete+W_SEM*semantic+W_FIELD*fieldSemantic+embeddingWeight*embedding;
                SmartLocatorResult smartLocator = buildSmartLocator(d, locatorBuilder, c);
                String selector = smartLocator != null
                    ? smartLocator.getLocatorType() + ": " + smartLocator.getLocator()
                    : c.selector;
                String selectorStrategy = smartLocator != null ? smartLocator.getStrategy() : "visual-raw";

                heatCands.add(new HeatmapRenderer.Candidate(c.x,c.y,c.w,c.h,score,c.text + " [" + c.kind + " #" + c.sequence + "]"));
                sortedCands.add(new CandidateWrapper(score, c.originalIndex, selector, selectorStrategy, c.kind, c.sequence,
                    vis, pos, txt, kind, seq, role, autocomplete, semantic, fieldSemantic, embedding, c.x + c.w/2, c.y + c.h/2));
            }

            sortedCands.sort(Comparator.comparingDouble((CandidateWrapper cw) -> cw.score).reversed());
            logTopCandidates(key, candidateMeta, sortedCands);

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

            JPanel panel = new JPanel(new BorderLayout(0, 10));
            String info = String.format(
                "<html><b>Locator:</b> %s<br>" +
                "<b>Candidate Rank:</b> %d of %d above threshold<br>" +
                "<b>Candidate Kind:</b> %s<br>" +
                "<b>Sequence:</b> #%d<br>" +
                "<b>Locator Strategy:</b> %s<br>" +
                "<b>New Locator:</b> <code>%s</code><br>" +
                "<b>Score:</b> %.3f &nbsp;(visual=%.2f &nbsp;position=%.2f &nbsp;text=%.2f &nbsp;kind=%.2f &nbsp;seq=%.2f &nbsp;role=%.2f &nbsp;auto=%.2f &nbsp;sem=%.2f &nbsp;field=%.2f &nbsp;emb=%.2f)</html>",
                key, i+1, sorted.size(), cand.kind, cand.sequence, cand.strategy, cand.selector, cand.score, cand.vis, cand.pos,
                cand.txt, cand.kindScore, cand.seqScore, cand.roleScore, cand.autocompleteScore, cand.semanticScore, cand.fieldSemanticScore, cand.embeddingScore);
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
                JOptionPane.YES_NO_CANCEL_OPTION, null, options, null);
            pane.setInitialValue(null);
            pane.setValue(JOptionPane.UNINITIALIZED_VALUE);
            JDialog dialog = pane.createDialog("Visual Healing Confirmation - " + key);
            dialog.setAlwaysOnTop(true);
            dialog.setModal(true);
            dialog.setResizable(true);
            dialog.setVisible(true);

            Object selectedValue = pane.getValue();
            if (selectedValue == null || selectedValue == JOptionPane.UNINITIALIZED_VALUE) {
                System.out.println("[INTERACTIVE] User closed dialog for " + key + ", treating as REFUSE");
                break;
            }
            int choice = -1;
            for (int j = 0; j < options.length; j++) {
                if (options[j].equals(selectedValue)) { choice = j; break; }
            }

            if (choice == 0) {
                System.out.println("[INTERACTIVE] User CONFIRMED " + key + " -> " + cand.selector + " strategy=" + cand.strategy);
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
        final String text, selector, kind, tagName, accessibleName, semanticRole, autocomplete,
            labelText, placeholder, descriptionText, sectionContext, parentContext, inputType, fingerprint, fieldIdentity;
        final float[] embeddingVector, fieldEmbeddingVector;
        int sequence = 1;
        int kindCount = 1;
        CandidateMeta(int originalIndex, int x, int y, int w, int h, String text, String selector, String kind, String tagName,
                      String accessibleName, String semanticRole, String autocomplete, String labelText, String placeholder,
                      String descriptionText, String sectionContext, String parentContext, String inputType,
                      String fingerprint, String fieldIdentity, float[] embeddingVector, float[] fieldEmbeddingVector) {
            this.originalIndex = originalIndex;
            this.x = x; this.y = y; this.w = w; this.h = h;
            this.text = text; this.selector = selector; this.kind = kind;
            this.tagName = tagName;
            this.accessibleName = accessibleName; this.semanticRole = semanticRole; this.autocomplete = autocomplete;
            this.labelText = labelText; this.placeholder = placeholder; this.descriptionText = descriptionText;
            this.sectionContext = sectionContext; this.parentContext = parentContext; this.inputType = inputType;
            this.fingerprint = fingerprint; this.fieldIdentity = fieldIdentity;
            this.embeddingVector = embeddingVector; this.fieldEmbeddingVector = fieldEmbeddingVector;
        }
    }

    private static class CandidateWrapper {
        final double score, vis, pos, txt, kindScore, seqScore, roleScore, autocompleteScore, semanticScore, fieldSemanticScore, embeddingScore;
        final int originalIndex, sequence, cx, cy;
        final String selector, strategy, kind;
        CandidateWrapper(double score, int originalIndex, String selector, String strategy, String kind, int sequence,
                         double vis, double pos, double txt, double kindScore, double seqScore,
                         double roleScore, double autocompleteScore, double semanticScore, double fieldSemanticScore, double embeddingScore, int cx, int cy) {
            this.score = score; this.originalIndex = originalIndex; this.selector = selector; this.strategy = strategy; this.kind = kind; this.sequence = sequence;
            this.vis = vis; this.pos = pos; this.txt = txt; this.kindScore = kindScore; this.seqScore = seqScore;
            this.roleScore = roleScore; this.autocompleteScore = autocompleteScore; this.semanticScore = semanticScore; this.fieldSemanticScore = fieldSemanticScore; this.embeddingScore = embeddingScore;
            this.cx = cx; this.cy = cy;
        }
    }

    private static class BaselineFieldProfile {
        final ElementSnapshot snapshot;
        final String fieldIdentity;
        final float[] fieldEmbedding;

        BaselineFieldProfile(ElementSnapshot snapshot, String fieldIdentity, float[] fieldEmbedding) {
            this.snapshot = snapshot;
            this.fieldIdentity = fieldIdentity;
            this.fieldEmbedding = fieldEmbedding;
        }
    }

    private static class FieldCompetitionContext {
        final int currentBaselineIndex;
        final List<BaselineFieldProfile> baselines;
        final List<CandidateMeta> candidates;
        final double[][] pairScores;
        final int[] assignedCandidateByBaseline;
        final int[] assignedBaselineByCandidate;
        final double[] bestCandidateScoreByBaseline;
        final double[] bestBaselineScoreByCandidate;

        FieldCompetitionContext(int currentBaselineIndex, List<BaselineFieldProfile> baselines, List<CandidateMeta> candidates,
                                double[][] pairScores, int[] assignedCandidateByBaseline, int[] assignedBaselineByCandidate,
                                double[] bestCandidateScoreByBaseline, double[] bestBaselineScoreByCandidate) {
            this.currentBaselineIndex = currentBaselineIndex;
            this.baselines = baselines;
            this.candidates = candidates;
            this.pairScores = pairScores;
            this.assignedCandidateByBaseline = assignedCandidateByBaseline;
            this.assignedBaselineByCandidate = assignedBaselineByCandidate;
            this.bestCandidateScoreByBaseline = bestCandidateScoreByBaseline;
            this.bestBaselineScoreByCandidate = bestBaselineScoreByCandidate;
        }

        int findCandidateIndex(int originalIndex) {
            for (int i = 0; i < candidates.size(); i++) {
                if (candidates.get(i).originalIndex == originalIndex) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static double semanticRoleScore(String baseRole, String candidateRole, String baseKind, String candidateKind) {
        String normalizedBase = normalizeRole(baseRole, baseKind);
        String normalizedCandidate = normalizeRole(candidateRole, candidateKind);
        if (normalizedBase.isBlank() && normalizedCandidate.isBlank()) return 0.50;
        if (normalizedBase.equals(normalizedCandidate)) return 1.0;
        if ((normalizedBase.equals("textbox") && normalizedCandidate.equals("combobox")) ||
            (normalizedBase.equals("combobox") && normalizedCandidate.equals("textbox"))) return 0.35;
        if (Objects.equals(baseKind, candidateKind)) return 0.55;
        return 0.10;
    }

    private static double autocompleteScore(String baseAutocomplete, String candidateAutocomplete) {
        String baseToken = normalizeAutocomplete(baseAutocomplete);
        String candidateToken = normalizeAutocomplete(candidateAutocomplete);
        if (baseToken.isBlank() && candidateToken.isBlank()) return 0.50;
        if (baseToken.isBlank() || candidateToken.isBlank()) return 0.35;
        return baseToken.equals(candidateToken) ? 1.0 : 0.0;
    }

    private static double semanticTextScore(ElementSnapshot base, CandidateMeta candidate) {
        String baseAccessible = nv(base.accessibleName);
        String candidateAccessible = nv(candidate.accessibleName);
        double accessible = scoreIfPresent(baseAccessible, candidateAccessible);
        double label = Math.max(scoreIfPresent(base.labelText, candidate.labelText),
            Math.max(scoreIfPresent(baseAccessible, candidate.labelText), scoreIfPresent(base.labelText, candidateAccessible)));
        double crossAccessible = Math.max(
            scoreIfPresent(baseAccessible, candidate.text),
            scoreIfPresent(base.text, candidateAccessible)
        );
        double placeholder = Math.max(
            scoreIfPresent(base.placeholder, candidate.placeholder),
            Math.max(scoreIfPresent(base.placeholder, candidateAccessible), scoreIfPresent(baseAccessible, candidate.placeholder))
        );
        double context = Math.max(
            scoreIfPresent(base.sectionContext, candidate.sectionContext),
            scoreIfPresent(base.parentContext, candidate.parentContext)
        );
        double text = scoreIfPresent(base.text, candidate.text);
        if (!baseAccessible.isBlank() && !candidateAccessible.isBlank()) {
            return (0.38 * Math.max(accessible, label)) + (0.18 * placeholder) + (0.14 * context)
                + (0.15 * crossAccessible) + (0.15 * text);
        }
        return Math.max(Math.max(accessible, label),
            Math.max(placeholder, Math.max(crossAccessible, Math.max(context, text))));
    }

    private static double embeddingScore(float[] baselineVector, float[] candidateVector, String baseKind, String candidateKind) {
        if (!Objects.equals(baseKind, candidateKind)
            && !((Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select"))
            || (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text")))) {
            return 0.0;
        }
        return LocalEmbeddingService.cosine(baselineVector, candidateVector);
    }

    private FieldCompetitionContext buildFieldCompetitionContext(ElementSnapshot base, String baseFieldIdentity, float[] baseFieldEmbedding,
                                                                List<ElementSnapshot> sameKindBaselines, List<CandidateMeta> allCandidates,
                                                                String baseKind) {
        List<BaselineFieldProfile> baselineProfiles = new ArrayList<>();
        int currentBaselineIndex = -1;
        for (ElementSnapshot snapshot : sameKindBaselines) {
            if (snapshot == null) {
                continue;
            }
            boolean isCurrent = sameSnapshot(snapshot, base) || Objects.equals(nv(snapshot.locator), nv(base.locator));
            String fieldIdentity = isCurrent
                ? baseFieldIdentity
                : EmbeddingFingerprintBuilder.buildFieldIdentity(
                    snapshot.accessibleName, snapshot.labelText, snapshot.placeholder, snapshot.autocomplete,
                    snapshot.inputType, snapshot.text, snapshot.locator);
            float[] fieldEmbedding = isCurrent
                ? baseFieldEmbedding
                : (embeddingService.isEnabled() ? embeddingService.embed(fieldIdentity) : null);
            baselineProfiles.add(new BaselineFieldProfile(snapshot, fieldIdentity, fieldEmbedding));
            if (isCurrent) {
                currentBaselineIndex = baselineProfiles.size() - 1;
            }
        }

        List<CandidateMeta> compatibleCandidates = allCandidates.stream()
            .filter(candidate -> compatibleFieldKind(baseKind, candidate.kind))
            .toList();
        if (baselineProfiles.isEmpty() || compatibleCandidates.isEmpty()) {
            return new FieldCompetitionContext(currentBaselineIndex, baselineProfiles, compatibleCandidates, new double[0][0], new int[0], new int[0], new double[0], new double[0]);
        }

        double[][] pairScores = new double[baselineProfiles.size()][compatibleCandidates.size()];
        double[][] assignmentScores = new double[baselineProfiles.size()][compatibleCandidates.size()];
        double[] bestCandidateScoreByBaseline = new double[baselineProfiles.size()];
        double[] bestBaselineScoreByCandidate = new double[compatibleCandidates.size()];
        for (int i = 0; i < baselineProfiles.size(); i++) {
            BaselineFieldProfile profile = baselineProfiles.get(i);
            String profileKind = resolveBaseKind(profile.snapshot, profile.snapshot.locator);
            int baselineSequence = i + 1;
            for (int j = 0; j < compatibleCandidates.size(); j++) {
                CandidateMeta candidate = compatibleCandidates.get(j);
                double pairScore = fieldPairScore(profile.snapshot, candidate, profile.fieldIdentity, profile.fieldEmbedding);
                double sequenceTieBreaker = 0.15 * sequenceScore(
                    baselineSequence, baselineProfiles.size(), candidate.sequence, candidate.kindCount, profileKind, candidate.kind
                );
                double neighborhoodTieBreaker = 0.12 * neighborhoodScore(i, candidate, baselineProfiles, compatibleCandidates);
                pairScores[i][j] = pairScore;
                assignmentScores[i][j] = pairScore + sequenceTieBreaker + neighborhoodTieBreaker;
                bestCandidateScoreByBaseline[i] = Math.max(bestCandidateScoreByBaseline[i], pairScore);
                bestBaselineScoreByCandidate[j] = Math.max(bestBaselineScoreByCandidate[j], pairScore);
            }
        }

        int[] assignedCandidateByBaseline = assignFieldCandidates(assignmentScores);
        int[] assignedBaselineByCandidate = new int[compatibleCandidates.size()];
        Arrays.fill(assignedBaselineByCandidate, -1);
        for (int i = 0; i < assignedCandidateByBaseline.length; i++) {
            int candidateIndex = assignedCandidateByBaseline[i];
            if (candidateIndex >= 0 && candidateIndex < assignedBaselineByCandidate.length) {
                assignedBaselineByCandidate[candidateIndex] = i;
            }
        }
        return new FieldCompetitionContext(currentBaselineIndex, baselineProfiles, compatibleCandidates, pairScores,
            assignedCandidateByBaseline, assignedBaselineByCandidate, bestCandidateScoreByBaseline, bestBaselineScoreByCandidate);
    }

    private static boolean compatibleFieldKind(String baseKind, String candidateKind) {
        if (Objects.equals(baseKind, candidateKind)) {
            return true;
        }
        return (Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select"))
            || (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text"));
    }

    private static int[] assignFieldCandidates(double[][] pairScores) {
        int baselineCount = pairScores.length;
        if (baselineCount == 0) {
            return new int[0];
        }
        int candidateCount = pairScores[0].length;
        int[] assignments = new int[baselineCount];
        Arrays.fill(assignments, -1);
        if (candidateCount == 0) {
            return assignments;
        }
        if (candidateCount <= 18) {
            return optimalFieldAssignment(pairScores);
        }
        return greedyFieldAssignment(pairScores);
    }

    private static int[] optimalFieldAssignment(double[][] pairScores) {
        Map<String, Double> memo = new HashMap<>();
        Map<String, Integer> choice = new HashMap<>();
        solveFieldAssignment(0, 0L, pairScores, memo, choice);
        int[] assignments = new int[pairScores.length];
        Arrays.fill(assignments, -1);
        long usedMask = 0L;
        for (int baselineIndex = 0; baselineIndex < assignments.length; baselineIndex++) {
            String key = baselineIndex + ":" + usedMask;
            int chosenCandidate = choice.getOrDefault(key, -1);
            if (chosenCandidate >= 0) {
                assignments[baselineIndex] = chosenCandidate;
                usedMask |= (1L << chosenCandidate);
            }
        }
        return assignments;
    }

    private static double solveFieldAssignment(int baselineIndex, long usedMask, double[][] pairScores,
                                               Map<String, Double> memo, Map<String, Integer> choice) {
        if (baselineIndex >= pairScores.length) {
            return 0.0;
        }
        String key = baselineIndex + ":" + usedMask;
        Double cached = memo.get(key);
        if (cached != null) {
            return cached;
        }

        double bestScore = solveFieldAssignment(baselineIndex + 1, usedMask, pairScores, memo, choice);
        int bestChoice = -1;
        for (int candidateIndex = 0; candidateIndex < pairScores[baselineIndex].length; candidateIndex++) {
            if ((usedMask & (1L << candidateIndex)) != 0L) {
                continue;
            }
            double pairScore = pairScores[baselineIndex][candidateIndex];
            if (pairScore <= 0.0) {
                continue;
            }
            double totalScore = pairScore + solveFieldAssignment(baselineIndex + 1, usedMask | (1L << candidateIndex), pairScores, memo, choice);
            if (totalScore > bestScore) {
                bestScore = totalScore;
                bestChoice = candidateIndex;
            }
        }
        memo.put(key, bestScore);
        choice.put(key, bestChoice);
        return bestScore;
    }

    private static int[] greedyFieldAssignment(double[][] pairScores) {
        int baselineCount = pairScores.length;
        int candidateCount = pairScores[0].length;
        int[] assignments = new int[baselineCount];
        Arrays.fill(assignments, -1);
        boolean[] usedCandidates = new boolean[candidateCount];
        List<int[]> rankedPairs = new ArrayList<>();
        for (int i = 0; i < baselineCount; i++) {
            for (int j = 0; j < candidateCount; j++) {
                if (pairScores[i][j] > 0.0) {
                    rankedPairs.add(new int[]{i, j});
                }
            }
        }
        rankedPairs.sort((left, right) -> Double.compare(pairScores[right[0]][right[1]], pairScores[left[0]][left[1]]));
        for (int[] pair : rankedPairs) {
            int baselineIndex = pair[0];
            int candidateIndex = pair[1];
            if (assignments[baselineIndex] >= 0 || usedCandidates[candidateIndex]) {
                continue;
            }
            assignments[baselineIndex] = candidateIndex;
            usedCandidates[candidateIndex] = true;
        }
        return assignments;
    }

    private static double fieldSemanticScore(CandidateMeta candidate, FieldCompetitionContext context) {
        if (context == null) {
            return 0.0;
        }
        int baselineIndex = context.currentBaselineIndex;
        int candidateIndex = context.findCandidateIndex(candidate.originalIndex);
        if (baselineIndex < 0 || candidateIndex < 0 || baselineIndex >= context.pairScores.length) {
            return 0.0;
        }
        double currentMatch = context.pairScores[baselineIndex][candidateIndex];
        if (context.baselines.size() <= 1) {
            return currentMatch;
        }
        double baselineBest = context.bestCandidateScoreByBaseline[baselineIndex];
        double candidateBest = context.bestBaselineScoreByCandidate[candidateIndex];
        double baselineAffinity = normalizeAgainstBest(currentMatch, baselineBest);
        double candidateAffinity = normalizeAgainstBest(currentMatch, candidateBest);
        double mutualAffinity = Math.sqrt(baselineAffinity * candidateAffinity);
        double assignmentAffinity = assignmentAffinity(baselineIndex, candidateIndex, context);
        double anchoredAssignment = assignmentAffinity * currentMatch;
        return (0.50 * currentMatch) + (0.20 * mutualAffinity) + (0.30 * anchoredAssignment);
    }

    private static double normalizeAgainstBest(double current, double best) {
        if (best <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, current / best));
    }

    private static double assignmentAffinity(int baselineIndex, int candidateIndex, FieldCompetitionContext context) {
        int assignedCandidate = baselineIndex >= 0 && baselineIndex < context.assignedCandidateByBaseline.length
            ? context.assignedCandidateByBaseline[baselineIndex]
            : -1;
        if (assignedCandidate == candidateIndex) {
            return 1.0;
        }
        int ownerBaseline = candidateIndex >= 0 && candidateIndex < context.assignedBaselineByCandidate.length
            ? context.assignedBaselineByCandidate[candidateIndex]
            : -1;
        if (ownerBaseline >= 0 && ownerBaseline != baselineIndex) {
            return 0.0;
        }
        return 0.18;
    }

    private static double neighborhoodScore(int baselineIndex, CandidateMeta candidate,
                                            List<BaselineFieldProfile> baselines, List<CandidateMeta> candidates) {
        double sum = 0.0;
        int count = 0;

        CandidateMeta previousCandidate = neighborCandidate(candidates, candidate, -1);
        if (baselineIndex > 0 && previousCandidate != null) {
            BaselineFieldProfile previousBaseline = baselines.get(baselineIndex - 1);
            sum += fieldPairScore(previousBaseline.snapshot, previousCandidate, previousBaseline.fieldIdentity, previousBaseline.fieldEmbedding);
            count++;
        }

        CandidateMeta nextCandidate = neighborCandidate(candidates, candidate, 1);
        if (baselineIndex + 1 < baselines.size() && nextCandidate != null) {
            BaselineFieldProfile nextBaseline = baselines.get(baselineIndex + 1);
            sum += fieldPairScore(nextBaseline.snapshot, nextCandidate, nextBaseline.fieldIdentity, nextBaseline.fieldEmbedding);
            count++;
        }

        if (count == 0) {
            return 0.50;
        }
        return sum / count;
    }

    private static CandidateMeta neighborCandidate(List<CandidateMeta> candidates, CandidateMeta current, int direction) {
        CandidateMeta best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (CandidateMeta candidate : candidates) {
            if (candidate.originalIndex == current.originalIndex) {
                continue;
            }
            int delta = candidate.sequence - current.sequence;
            if (direction < 0 && delta >= 0) {
                continue;
            }
            if (direction > 0 && delta <= 0) {
                continue;
            }
            int distance = Math.abs(delta);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean sameSnapshot(ElementSnapshot left, ElementSnapshot right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(nv(left.locator), nv(right.locator))
            && Objects.equals(nv(left.pageUrl), nv(right.pageUrl))
            && left.x == right.x && left.y == right.y && left.w == right.w && left.h == right.h;
    }

    private static double fieldPairScore(ElementSnapshot base, CandidateMeta candidate,
                                         String baseFieldIdentity, float[] baseFieldEmbedding) {
        String baseKind = resolveBaseKind(base, base.locator);
        double identityText = scoreIfPresent(baseFieldIdentity, candidate.fieldIdentity);
        double directName = Math.max(
            scoreIfPresent(base.accessibleName, candidate.accessibleName),
            Math.max(scoreIfPresent(base.labelText, candidate.labelText), scoreIfPresent(base.accessibleName, candidate.labelText))
        );
        double placeholder = Math.max(
            scoreIfPresent(base.placeholder, candidate.placeholder),
            Math.max(scoreIfPresent(base.placeholder, candidate.accessibleName), scoreIfPresent(base.accessibleName, candidate.placeholder))
        );
        double autocomplete = strictAutocompleteScore(base.autocomplete, candidate.autocomplete);
        double inputType = scoreIfPresent(base.inputType, candidate.inputType);
        double control = fieldControlScore(baseKind, candidate.kind, base.semanticRole, candidate.semanticRole);
        double embedding = baseFieldEmbedding == null || candidate.fieldEmbeddingVector == null
            ? 0.0
            : LocalEmbeddingService.cosine(baseFieldEmbedding, candidate.fieldEmbeddingVector);
        if (embedding > 0.0) {
            return (0.24 * directName) + (0.18 * identityText) + (0.14 * placeholder) + (0.18 * embedding)
                + (0.09 * autocomplete) + (0.05 * inputType) + (0.12 * control);
        }
        return (0.31 * directName) + (0.24 * identityText) + (0.18 * placeholder)
            + (0.10 * autocomplete) + (0.05 * inputType) + (0.12 * control);
    }

    private static double fieldControlScore(String baseKind, String candidateKind, String baseRole, String candidateRole) {
        double kind = strictFieldKindScore(baseKind, candidateKind);
        double role = semanticRoleScore(baseRole, candidateRole, baseKind, candidateKind);
        return (0.60 * kind) + (0.40 * role);
    }

    private static double strictFieldKindScore(String baseKind, String candidateKind) {
        if (Objects.equals(baseKind, candidateKind)) {
            return 1.0;
        }
        if ((Objects.equals(baseKind, "text") && Objects.equals(candidateKind, "select")) ||
            (Objects.equals(baseKind, "select") && Objects.equals(candidateKind, "text"))) {
            return 0.20;
        }
        return 0.0;
    }

    private static String normalizeAutocomplete(String value) {
        if (value == null) return "";
        String normalized = value.trim().toLowerCase();
        if (normalized.isBlank()) return "";
        String[] tokens = normalized.split("\\s+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            String token = tokens[i];
            if (token.isBlank()) continue;
            if (token.startsWith("section-")) continue;
            if (List.of("shipping", "billing", "home", "work", "mobile", "fax", "pager").contains(token)) continue;
            return token;
        }
        return tokens[tokens.length - 1];
    }

    private static double strictAutocompleteScore(String baseAutocomplete, String candidateAutocomplete) {
        String baseToken = normalizeAutocomplete(baseAutocomplete);
        String candidateToken = normalizeAutocomplete(candidateAutocomplete);
        if (baseToken.isBlank() || candidateToken.isBlank()) return 0.0;
        return baseToken.equals(candidateToken) ? 1.0 : 0.0;
    }

    private static String normalizeRole(String role, String kind) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if (!normalized.isBlank()) return normalized;
        return switch (kind == null ? "" : kind) {
            case "text" -> "textbox";
            case "select" -> "combobox";
            case "toggle" -> "checkbox";
            case "action" -> "button";
            default -> "";
        };
    }

    private static String nv(String value) {
        return value == null ? "" : value;
    }

    private static String firstNonBlank(String primary, String fallback) {
        String left = nv(primary).trim();
        return left.isBlank() ? nv(fallback).trim() : left;
    }

    private static double scoreIfPresent(String a, String b) {
        if (a == null || a.isBlank() || b == null || b.isBlank()) return 0.0;
        return SemanticSimilarity.semanticScore(a, b);
    }

    private static void logTopCandidates(String key, List<CandidateMeta> meta, List<CandidateWrapper> sorted) {
        int limit = Math.min(3, sorted.size());
        for (int i = 0; i < limit; i++) {
            CandidateWrapper cand = sorted.get(i);
            CandidateMeta source = meta.stream()
                .filter(m -> m.originalIndex == cand.originalIndex)
                .findFirst()
                .orElse(null);
            String accessible = source == null ? "" : source.accessibleName;
            System.out.println("[VISUAL-RANK] " + key + " rank=" + (i + 1)
                + " idx=" + cand.originalIndex
                + " score=" + String.format("%.3f", cand.score)
                + " kind=" + cand.kind
                + " seq=" + cand.sequence
                + " sem=" + String.format("%.2f", cand.semanticScore)
                + " field=" + String.format("%.2f", cand.fieldSemanticScore)
                + " emb=" + String.format("%.2f", cand.embeddingScore)
                + " accessible='" + accessible + "'"
                + " selector=" + cand.selector);
        }
    }

    private SmartLocatorResult buildSmartLocator(WebDriver driver, SmartLocatorBuilder builder, CandidateMeta candidate) {
        try {
            WebElement candidateElement = resolveCandidateElement(driver, candidate.originalIndex);
            if (candidateElement != null) {
                return builder.buildLocatorForElement(candidateElement);
            }
            int[] scroll = currentScrollOffset(driver);
            int px = candidate.x - scroll[0] + Math.max(1, Math.min(candidate.w - 2, candidate.w / 2));
            int py = candidate.y - scroll[1] + Math.max(1, Math.min(candidate.h - 2, candidate.h / 2));
            return builder.buildLocatorFromPoint(px, py);
        } catch (Exception e) {
            System.out.println("[SMART-LOCATOR] Fallback to raw visual selector for candidate " + candidate.originalIndex + " reason=" + e.getMessage());
            return null;
        }
    }

    private WebElement resolveCandidateElement(WebDriver driver, int candidateIndex) {
        try {
            Object raw = ((JavascriptExecutor) driver).executeScript(
                "return window.__visualCandidates && window.__visualCandidates.length > arguments[0] ? window.__visualCandidates[arguments[0]] : null;",
                candidateIndex);
            return raw instanceof WebElement element ? element : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int[] currentScrollOffset(WebDriver driver) {
        try {
            Object raw = ((JavascriptExecutor) driver).executeScript(
                "return [" +
                "Math.round(window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0)," +
                "Math.round(window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0)" +
                "];");
            if (raw instanceof List<?> values && values.size() >= 2) {
                return new int[]{toInt(values.get(0)), toInt(values.get(1))};
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
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
                return 0.40;
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
            +"function attr(el, name) {"
            +"  return el ? String(el.getAttribute(name) || '').trim() : '';"
            +"}"
            +"function referencedText(ids) {"
            +"  if (!ids) return '';"
            +"  var parts = [];"
            +"  ids.split(/\\s+/).forEach(function(id) {"
            +"    if (!id) return;"
            +"    var ref = document.getElementById(id);"
            +"    push(parts, nodeText(ref));"
            +"  });"
            +"  return parts.join(' ');"
            +"}"
            +"function labelLikeText(el) {"
            +"  if (!el) return '';"
            +"  var aria = referencedText(attr(el, 'aria-labelledby'));"
            +"  if (aria) return aria;"
            +"  var direct = attr(el, 'aria-label') || attr(el, 'data-label');"
            +"  if (direct) return direct;"
            +"  var txt = nodeText(el);"
            +"  if (!txt) return '';"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var cls = attr(el, 'class').toLowerCase();"
            +"  if (tag === 'label' || tag === 'legend') return txt;"
            +"  if (cls.indexOf('label') >= 0 || cls.indexOf('title') >= 0) return txt;"
            +"  if (tag === 'span' || tag === 'div' || tag === 'p' || tag === 'strong') return txt;"
            +"  return '';"
            +"}"
            +"function isFieldLike(el) {"
            +"  if (!el) return false;"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var type = attr(el, 'type').toLowerCase();"
            +"  var editable = attr(el, 'contenteditable').toLowerCase() === 'true';"
            +"  if (editable || tag === 'textarea' || tag === 'select') return true;"
            +"  if (tag === 'input') return type !== 'hidden';"
            +"  return false;"
            +"}"
            +"function nearestLabelText(el) {"
            +"  var parts = [];"
            +"  push(parts, referencedText(attr(el, 'aria-labelledby')));"
            +"  var wrappedLabel = el.closest('label');"
            +"  push(parts, nodeText(wrappedLabel));"
            +"  if (el.id) {"
            +"    var byFor = document.querySelector('label[for=\\\"'+el.id+'\\\"]');"
            +"    push(parts, nodeText(byFor));"
            +"  }"
            +"  if (parts.length) return [...new Set(parts)].join(' | ');"
            +"  var prev = el.previousElementSibling;"
            +"  while (prev) {"
            +"    var prevLabel = labelLikeText(prev);"
            +"    if (prevLabel) {"
            +"      push(parts, prevLabel);"
            +"      break;"
            +"    }"
            +"    if (isFieldLike(prev)) break;"
            +"    prev = prev.previousElementSibling;"
            +"  }"
            +"  return [...new Set(parts)].join(' | ');"
            +"}"
            +"function computedRole(el) {"
            +"  var role = attr(el, 'role').toLowerCase();"
            +"  if (role) return role;"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var type = attr(el, 'type').toLowerCase();"
            +"  var editable = attr(el, 'contenteditable').toLowerCase() === 'true';"
            +"  if (tag === 'button') return 'button';"
            +"  if (tag === 'a' && attr(el, 'href')) return 'link';"
            +"  if (tag === 'select') return 'combobox';"
            +"  if (tag === 'textarea' || editable) return 'textbox';"
            +"  if (tag === 'input') {"
            +"    if (type === 'checkbox') return 'checkbox';"
            +"    if (type === 'radio') return 'radio';"
            +"    if (type === 'button' || type === 'submit' || type === 'reset') return 'button';"
            +"    return 'textbox';"
            +"  }"
            +"  if (!!el.onclick) return 'button';"
            +"  return tag;"
            +"}"
            +"function accessibleName(el) {"
            +"  var labelledBy = referencedText(attr(el, 'aria-labelledby'));"
            +"  if (labelledBy) return labelledBy;"
            +"  var ariaLabel = attr(el, 'aria-label');"
            +"  if (ariaLabel) return ariaLabel;"
            +"  var label = nearestLabelText(el);"
            +"  if (label) return label;"
            +"  var title = attr(el, 'title');"
            +"  if (title) return title;"
            +"  var placeholder = attr(el, 'placeholder') || attr(el, 'data-placeholder');"
            +"  if (placeholder) return placeholder;"
            +"  var value = attr(el, 'value');"
            +"  if (value) return value;"
            +"  return nodeText(el);"
            +"}"
            +"function selectHint(el) {"
            +"  var role = attr(el, 'role').toLowerCase();"
            +"  if (role === 'combobox' || role === 'listbox') return true;"
            +"  if (attr(el, 'aria-haspopup').toLowerCase() === 'listbox') return true;"
            +"  var text = [attr(el, 'aria-label'), attr(el, 'placeholder'), attr(el, 'data-placeholder'), nearestLabelText(el), nodeText(el)].join(' ').toLowerCase();"
            +"  if (!text) return false;"
            +"  return /(\\bchoose\\b|\\bselect\\b|\\bpick\\b)/.test(text) || /(\\bcountry\\b|\\bnation\\b|\\bregion\\b|\\blocation\\b)/.test(text);"
            +"}"
            +"function classify(el) {"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var type = attr(el, 'type').toLowerCase();"
            +"  var role = computedRole(el);"
            +"  var editable = attr(el, 'contenteditable').toLowerCase() === 'true';"
            +"  if (tag === 'select') return 'select';"
            +"  if (role === 'checkbox' || role === 'switch' || role === 'radio') return 'toggle';"
            +"  if (el.classList.contains('fake-toggle') || el.querySelector('.toggle-box')) return 'toggle';"
            +"  if (tag === 'button' || tag === 'a' || role === 'button' || role === 'link') return 'action';"
            +"  if (selectHint(el)) return 'select';"
            +"  if (tag === 'textarea' || editable) return 'text';"
            +"  if (tag === 'input') {"
            +"    if (type === 'checkbox' || type === 'radio') return 'toggle';"
            +"    if (type === 'button' || type === 'submit' || type === 'reset') return 'action';"
            +"    return 'text';"
            +"  }"
            +"  if (!!el.onclick) return 'action';"
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
            +"  push(parts, attr(el, 'placeholder'));"
            +"  push(parts, attr(el, 'data-placeholder'));"
            +"  push(parts, attr(el, 'aria-label'));"
            +"  push(parts, accessibleName(el));"
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
            +"  var sx=Math.round(window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0);"
            +"  var sy=Math.round(window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0);"
            +"  if(r.width<=0||r.height<=0) continue;"
            +"  var idx=window.__visualCandidates.length;"
            +"  window.__visualCandidates.push(e);"
            +"  out.push({"
            +"    x:Math.round(r.left)+sx,"
            +"    y:Math.round(r.top)+sy,"
            +"    w:Math.round(r.width),"
            +"    h:Math.round(r.height),"
            +"    text:elementText(e),"
            +"    idx:idx,"
            +"    selector:getCssPath(e),"
            +"    kind:classify(e),"
            +"    accessibleName:accessibleName(e),"
            +"    semanticRole:computedRole(e),"
            +"    autocomplete:attr(e, 'autocomplete'),"
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
            +"function attr(el, name) { return el ? String(el.getAttribute(name) || '').trim() : ''; }"
            +"function referencedText(ids) {"
            +"  if (!ids) return '';"
            +"  var parts = [];"
            +"  ids.split(/\\s+/).forEach(function(id) {"
            +"    if (!id) return;"
            +"    var ref = document.getElementById(id);"
            +"    push(parts, nodeText(ref));"
            +"  });"
            +"  return parts.join(' ');"
            +"}"
            +"function labelLikeText(el) {"
            +"  if (!el) return '';"
            +"  var aria = referencedText(attr(el, 'aria-labelledby'));"
            +"  if (aria) return aria;"
            +"  var direct = attr(el, 'aria-label') || attr(el, 'data-label');"
            +"  if (direct) return direct;"
            +"  var txt = nodeText(el);"
            +"  if (!txt) return '';"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var cls = attr(el, 'class').toLowerCase();"
            +"  if (tag === 'label' || tag === 'legend') return txt;"
            +"  if (cls.indexOf('label') >= 0 || cls.indexOf('title') >= 0) return txt;"
            +"  if (tag === 'span' || tag === 'div' || tag === 'p' || tag === 'strong') return txt;"
            +"  return '';"
            +"}"
            +"function isFieldLike(el) {"
            +"  if (!el) return false;"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var type = attr(el, 'type').toLowerCase();"
            +"  var editable = attr(el, 'contenteditable').toLowerCase() === 'true';"
            +"  if (editable || tag === 'textarea' || tag === 'select') return true;"
            +"  if (tag === 'input') return type !== 'hidden';"
            +"  return false;"
            +"}"
            +"function nearestLabelText(el) {"
            +"  var parts = [];"
            +"  push(parts, referencedText(attr(el, 'aria-labelledby')));"
            +"  var wrappedLabel = el.closest('label');"
            +"  push(parts, nodeText(wrappedLabel));"
            +"  if (el.id) {"
            +"    var byFor = document.querySelector('label[for=\\\"'+el.id+'\\\"]');"
            +"    push(parts, nodeText(byFor));"
            +"  }"
            +"  if (parts.length) return [...new Set(parts)].join(' | ');"
            +"  var prev = el.previousElementSibling;"
            +"  while (prev) {"
            +"    var prevLabel = labelLikeText(prev);"
            +"    if (prevLabel) { push(parts, prevLabel); break; }"
            +"    if (isFieldLike(prev)) break;"
            +"    prev = prev.previousElementSibling;"
            +"  }"
            +"  return [...new Set(parts)].join(' | ');"
            +"}"
            +"function computedRole(el) {"
            +"  var role = attr(el, 'role').toLowerCase();"
            +"  if (role) return role;"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var type = attr(el, 'type').toLowerCase();"
            +"  var editable = attr(el, 'contenteditable').toLowerCase() === 'true';"
            +"  if (tag === 'button') return 'button';"
            +"  if (tag === 'a' && attr(el, 'href')) return 'link';"
            +"  if (tag === 'select') return 'combobox';"
            +"  if (selectHint(el)) return 'combobox';"
            +"  if (tag === 'textarea' || editable) return 'textbox';"
            +"  if (tag === 'input') {"
            +"    if (type === 'checkbox') return 'checkbox';"
            +"    if (type === 'radio') return 'radio';"
            +"    if (type === 'button' || type === 'submit' || type === 'reset') return 'button';"
            +"    return 'textbox';"
            +"  }"
            +"  if (!!el.onclick) return 'button';"
            +"  return tag;"
            +"}"
            +"function accessibleName(el) {"
            +"  var labelledBy = referencedText(attr(el, 'aria-labelledby'));"
            +"  if (labelledBy) return labelledBy;"
            +"  var ariaLabel = attr(el, 'aria-label');"
            +"  if (ariaLabel) return ariaLabel;"
            +"  var label = nearestLabelText(el);"
            +"  if (label) return label;"
            +"  var title = attr(el, 'title');"
            +"  if (title) return title;"
            +"  var placeholder = attr(el, 'placeholder') || attr(el, 'data-placeholder');"
            +"  if (placeholder) return placeholder;"
            +"  var value = attr(el, 'value');"
            +"  if (value) return value;"
            +"  return nodeText(el);"
            +"}"
            +"function selectHint(el) {"
            +"  var role = attr(el, 'role').toLowerCase();"
            +"  if (role === 'combobox' || role === 'listbox') return true;"
            +"  if (attr(el, 'aria-haspopup').toLowerCase() === 'listbox') return true;"
            +"  var text = [attr(el, 'aria-label'), attr(el, 'placeholder'), attr(el, 'data-placeholder'), nearestLabelText(el), nodeText(el)].join(' ').toLowerCase();"
            +"  if (!text) return false;"
            +"  return /(\\bchoose\\b|\\bselect\\b|\\bpick\\b)/.test(text) || /(\\bcountry\\b|\\bnation\\b|\\bregion\\b|\\blocation\\b)/.test(text);"
            +"}"
            +"function classify(el) {"
            +"  var tag = el.tagName.toLowerCase();"
            +"  var type = attr(el, 'type').toLowerCase();"
            +"  var role = computedRole(el);"
            +"  var editable = attr(el, 'contenteditable').toLowerCase() === 'true';"
            +"  if (tag === 'select') return 'select';"
            +"  if (role === 'checkbox' || role === 'switch' || role === 'radio') return 'toggle';"
            +"  if (el.classList.contains('fake-toggle') || el.querySelector('.toggle-box')) return 'toggle';"
            +"  if (tag === 'button' || tag === 'a' || role === 'button' || role === 'link') return 'action';"
            +"  if (selectHint(el)) return 'select';"
            +"  if (tag === 'textarea' || editable) return 'text';"
            +"  if (tag === 'input') {"
            +"    if (type === 'checkbox' || type === 'radio') return 'toggle';"
            +"    if (type === 'button' || type === 'submit' || type === 'reset') return 'action';"
            +"    return 'text';"
            +"  }"
            +"  if (!!el.onclick) return 'action';"
            +"  return 'generic';"
            +"}"
            +"var e=arguments[0], r=e.getBoundingClientRect(), parts=[];"
            +"var sx=Math.round(window.pageXOffset || document.documentElement.scrollLeft || document.body.scrollLeft || 0);"
            +"var sy=Math.round(window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0);"
            +"push(parts, e.value);"
            +"push(parts, attr(e, 'placeholder'));"
            +"push(parts, attr(e, 'data-placeholder'));"
            +"push(parts, attr(e, 'aria-label'));"
            +"push(parts, accessibleName(e));"
            +"push(parts, nodeText(e));"
            +"push(parts, nearestLabelText(e));"
            +"return {"
            +"  x:Math.round(r.left)+sx, y:Math.round(r.top)+sy, w:Math.round(r.width), h:Math.round(r.height),"
            +"  text:[...new Set(parts)].join(' | ').substring(0,120),"
            +"  kind:classify(e), tag:e.tagName.toLowerCase(),"
            +"  accessibleName:accessibleName(e), semanticRole:computedRole(e), autocomplete:attr(e, 'autocomplete')"
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
        if((baseKind.equals("text") && candidateKind.equals("select")) || (baseKind.equals("select") && candidateKind.equals("text"))) return 0.35;
        if(candidateKind.equals("generic") || baseKind.equals("generic")) return 0.40;
        return 0.05;
    }

    private static int iv(Map<String,Object> m,String k){Object v=m.get(k);if(v instanceof Number)return((Number)v).intValue();if(v instanceof String)try{return Integer.parseInt((String)v);}catch(Exception e){}return 0;}
    private static int toInt(Object v){if(v instanceof Number)return((Number)v).intValue();if(v instanceof String)try{return Integer.parseInt((String)v);}catch(Exception e){}return 0;}
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






