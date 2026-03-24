package com.visual.model;
public final class ScoreResult {
    public enum Decision { HEALED, ABORTED }
    public final Decision decision;
    public final double totalScore, visualScore, positionScore, textScore;
    public final int candidatesEvaluated, centerX, centerY;
    public final int candidateIndex; // index into window.__visualCandidates

    private ScoreResult(Decision d,double t,double v,double p,double tx,
                        int count,int cx,int cy,int idx){
        decision=d;totalScore=t;visualScore=v;positionScore=p;textScore=tx;
        candidatesEvaluated=count;centerX=cx;centerY=cy;candidateIndex=idx;
    }
    public static ScoreResult healed(double t,double v,double p,double tx,
                                     int count,int cx,int cy,int idx){
        return new ScoreResult(Decision.HEALED,t,v,p,tx,count,cx,cy,idx);
    }
    public static ScoreResult aborted(double best,int count){
        return new ScoreResult(Decision.ABORTED,best,0,0,0,count,-1,-1,-1);
    }
    public String toString(){
        if(decision==Decision.HEALED)
            return String.format("HEALED score=%.3f (vis=%.2f pos=%.2f txt=%.2f) cands=%d pt=(%d,%d) idx=%d",
                totalScore,visualScore,positionScore,textScore,candidatesEvaluated,centerX,centerY,candidateIndex);
        return String.format("ABORTED bestScore=%.3f cands=%d",totalScore,candidatesEvaluated);
    }
}
