package com.visual;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

public class HeatmapRenderer {
    public static class Candidate {
        public final int x, y, w, h;
        public final double score;
        public final String text;
        public Candidate(int x, int y, int w, int h, double score, String text) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.score = score; this.text = text;
        }
    }

    public static BufferedImage renderImage(BufferedImage bg, List<Candidate> candidates, int bestIdx, String locator) {
        BufferedImage out = new BufferedImage(bg.getWidth(), bg.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(bg, 0, 0, null);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // draw overlay
        g.setColor(new Color(0, 0, 0, 80));
        g.fillRect(0, 0, out.getWidth(), out.getHeight());

        for (int i = 0; i < candidates.size(); i++) {
            Candidate c = candidates.get(i);
            if (c.score < 0.2) continue; // ignore terrible matches
            
            float hue = (float) ((c.score - 0.2) / 0.8 * 0.33f); // red to green
            hue = Math.max(0, Math.min(hue, 0.33f));
            Color bgCol = Color.getHSBColor(hue, 0.8f, 0.9f);
            
            g.setColor(new Color(bgCol.getRed(), bgCol.getGreen(), bgCol.getBlue(), 120));
            g.fillRect(c.x, c.y, c.w, c.h);
            
            g.setColor(bgCol);
            g.setStroke(new BasicStroke(i == bestIdx ? 4f : 2f));
            g.drawRect(c.x, c.y, c.w, c.h);
            
            g.setFont(new Font("SansSerif", Font.BOLD, 14));
            String label = String.format("%.2f %s", c.score, c.text);
            g.drawString(label, c.x + 5, c.y + c.h - 5);
        }

        if (bestIdx >= 0 && bestIdx < candidates.size()) {
            Candidate b = candidates.get(bestIdx);
            g.setColor(Color.CYAN);
            g.setStroke(new BasicStroke(5f));
            g.drawRect(b.x, b.y, b.w, b.h);
        }
        
        g.setColor(new Color(50, 50, 50, 200));
        g.fillRect(0, 0, out.getWidth(), 30);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("Visual Healing Heatmap: " + locator, 10, 20);
        
        g.dispose();
        return out;
    }

    public static void render(BufferedImage bg, List<Candidate> candidates, int bestIdx, String locator, String outputPath) {
        BufferedImage img = renderImage(bg, candidates, bestIdx, locator);
        try { ImageIO.write(img, "png", new File(outputPath)); System.out.println("[HEATMAP] Saved: " + outputPath); }
        catch (Exception e) { System.err.println("[HEATMAP] Failed to save overlay: " + e.getMessage()); }
    }
}
