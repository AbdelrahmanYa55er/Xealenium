package com.visual.engine;

import com.visual.baseline.BaselineStore;
import com.visual.embedding.EmbeddingFingerprintBuilder;
import com.visual.embedding.LocalEmbeddingService;
import com.visual.image.ImageUtils;
import com.visual.model.CandidateMetadata;
import com.visual.model.CandidateScore;
import com.visual.model.ElementSnapshot;
import com.visual.model.HealingDecision;
import com.visual.model.ScoreResult;
import com.visual.report.HeatmapRenderer;
import com.visual.report.ReportEntry;
import com.visual.semantic.SemanticSignalExtractor;
import com.visual.semantic.SemanticSignals;
import com.visual.semantic.SemanticSimilarity;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import java.awt.image.BufferedImage;
import java.util.List;

public class VisualHealingEngine {
    private static final double THR=0.56;
    private final BaselineStore store;
    private final SemanticSignalExtractor semanticExtractor;
    private final LocalEmbeddingService embeddingService;
    private final CandidateCollector candidateCollector;
    private final CandidateMetadataCollector candidateMetadataCollector;
    private final HealingReporter healingReporter;
    private final BaselineCaptureService baselineCaptureService;
    private final FieldAssignmentEngine fieldAssignmentEngine;
    private final CandidateScorer candidateScorer;
    private final HealingDecisionEngine healingDecisionEngine;
    private int healCount = 0;
    private boolean interactiveMode = false;

    public static final List<ReportEntry> REPORTS = HealingReporter.REPORTS;

    public VisualHealingEngine(){this(new BaselineStore());}
    public VisualHealingEngine(BaselineStore s){
        store=s;
        semanticExtractor = new SemanticSignalExtractor();
        embeddingService = LocalEmbeddingService.getInstance();
        candidateCollector = new CandidateCollector();
        candidateMetadataCollector = new CandidateMetadataCollector(candidateCollector, semanticExtractor, embeddingService);
        healingReporter = new HealingReporter();
        baselineCaptureService = new BaselineCaptureService(store, semanticExtractor, embeddingService, candidateCollector);
        fieldAssignmentEngine = new FieldAssignmentEngine(store, embeddingService);
        candidateScorer = new CandidateScorer(candidateCollector, embeddingService, fieldAssignmentEngine);
        healingDecisionEngine = new HealingDecisionEngine(THR);
    }

    public void setInteractiveMode(boolean b){ this.interactiveMode = b; }

    public void captureBaseline(WebDriver d, WebElement el, By loc){
        String pageUrl = d.getCurrentUrl();
        if (!shouldCaptureBaseline(pageUrl)) {
            return;
        }
        boolean replaceExisting = Boolean.parseBoolean(System.getProperty("visual.captureBaseline.refresh", "false"));
        baselineCaptureService.capture(d, el, loc, replaceExisting);
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

            List<CandidateMetadata> candidateMeta = candidateMetadataCollector.collect(d, key, embeddingsActive);

            String baseKind = FieldAssignmentEngine.resolveBaseKind(base, key);
            int baseSequence = fieldAssignmentEngine.baselineSequence(base, key, baseKind);
            int baseKindCount = fieldAssignmentEngine.baselineKindCount(base, baseKind);
            fieldAssignmentEngine.assignSequencePositions(candidateMeta);
            FieldAssignmentEngine.FieldCompetitionContext fieldCompetition = fieldAssignmentEngine.buildContext(
                base, baseFieldIdentity, baseFieldEmbedding, candidateMeta, baseKind
            );
            CandidateScoringResult scoringResult = candidateScorer.score(
                d, key, base, pageImg, tmpl, baseEmbedding, baseKind, baseSequence, baseKindCount,
                embeddingsActive, candidateMeta, fieldCompetition
            );
            HealingDecision decision = healingDecisionEngine.decide(
                key, pageImg, scoringResult.heatmapCandidates, scoringResult.rankedCandidates, interactiveMode
            );

            if (!decision.accepted) {
                return abortAndReport(key, decision.bestScore, scoringResult.totalCandidates, pageImg, scoringResult.heatmapCandidates, decision.displayIndex);
            }
            return healAndReport(key, decision.candidate, scoringResult.totalCandidates, pageImg, scoringResult.heatmapCandidates, decision.displayIndex);
        }catch(Exception e){
            System.err.println("[VISUAL-HEAL] "+key+": "+e.getMessage());
            return ScoreResult.aborted(0.0,0);
        }
    }

    private ScoreResult healAndReport(String key, CandidateScore cand, int totalCands, BufferedImage img, List<HeatmapRenderer.Candidate> heatCands, int displayIdx) {
        healCount++;
        String heatmapFile = healingReporter.writeHeatmap(key, healCount, img, heatCands, displayIdx);
        healingReporter.record(key, cand.getSelector(), cand.getScore(), heatmapFile);
        ScoreResult r=ScoreResult.healed(cand.getScore(), cand.getVis(), cand.getPos(), cand.getTxt(), totalCands, cand.getCx(), cand.getCy(), cand.getOriginalIndex());
        System.out.println("[VISUAL-HEAL] "+key+" | "+r+" kind="+cand.getKind()+" seq="+cand.getSequence()+" kindScore="+String.format("%.2f", cand.getKindScore())+" seqScore="+String.format("%.2f", cand.getSeqScore()));
        return r;
    }

    private ScoreResult abortAndReport(String key, double bestScore, int totalCands, BufferedImage img, List<HeatmapRenderer.Candidate> heatCands, int displayIdx) {
        healCount++;
        String heatmapFile = healingReporter.writeHeatmap(key, healCount, img, heatCands, displayIdx);
        healingReporter.record(key, "ABORTED (Score too low / User Refused)", bestScore, heatmapFile);
        ScoreResult r=ScoreResult.aborted(bestScore, totalCands);
        System.out.println("[VISUAL-HEAL] "+key+" | "+r);
        return r;
    }

    public BaselineStore getStore(){return store;}

    private boolean shouldCaptureBaseline(String pageUrl) {
        String explicit = System.getProperty("visual.captureBaseline");
        if (explicit != null && !explicit.isBlank()) {
            return Boolean.parseBoolean(explicit);
        }
        return pageUrl != null && pageUrl.toLowerCase().contains("baseline");
    }

    public static void generateHtmlReport(String outputPath) {
        HealingReporter.generateHtmlReport(outputPath);
    }
}






