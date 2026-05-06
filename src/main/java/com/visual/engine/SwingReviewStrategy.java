package com.visual.engine;

import com.visual.model.CandidateScore;
import com.visual.model.HealingDecision;
import com.visual.report.HeatmapRenderer;

import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.List;

final class SwingReviewStrategy implements HealingReviewStrategy {
    @Override
    public HealingDecision review(String key, BufferedImage pageImg, List<HeatmapRenderer.Candidate> heatCandidates,
                                  List<CandidateScore> rankedCandidates, double threshold) {
        if (rankedCandidates.isEmpty() || rankedCandidates.get(0).getScore() < threshold) {
            return HealingDecision.abort(rankedCandidates.isEmpty() ? 0.0 : rankedCandidates.get(0).getScore());
        }
        for (int i = 0; i < rankedCandidates.size(); i++) {
            CandidateScore candidate = rankedCandidates.get(i);
            if (candidate.getScore() < threshold) {
                break;
            }

            BufferedImage heatmap = HeatmapRenderer.renderImage(pageImg, heatCandidates, candidate.getOriginalIndex(),
                String.format("%s  [Rank %d | Score %.3f]", key, i + 1, candidate.getScore()));

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
                key, i + 1, rankedCandidates.size(), candidate.getKind(), candidate.getSequence(), candidate.getStrategy(), candidate.getSelector(), candidate.getScore(),
                candidate.getVis(), candidate.getPos(), candidate.getTxt(), candidate.getKindScore(), candidate.getSeqScore(), candidate.getRoleScore(),
                candidate.getAutocompleteScore(), candidate.getSemanticScore(), candidate.getFieldSemanticScore(), candidate.getEmbeddingScore()
            );
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
            System.out.println("[INTERACTIVE] Opening Swing review dialog for " + key + " candidate=" + candidate.getSelector());
            dialog.setVisible(true);

            Object selectedValue = pane.getValue();
            if (selectedValue == null || selectedValue == JOptionPane.UNINITIALIZED_VALUE) {
                System.out.println("[INTERACTIVE] User closed dialog for " + key + ", treating as REFUSE");
                break;
            }

            int choice = -1;
            for (int j = 0; j < options.length; j++) {
                if (options[j].equals(selectedValue)) {
                    choice = j;
                    break;
                }
            }

            if (choice == 0) {
                System.out.println("[INTERACTIVE] User CONFIRMED " + key + " -> " + candidate.getSelector() + " strategy=" + candidate.getStrategy());
                return HealingDecision.accept(candidate);
            }
            if (choice == 1) {
                System.out.println("[INTERACTIVE] User requested NEXT BEST for " + key);
            } else {
                System.out.println("[INTERACTIVE] User REFUSED " + key);
                break;
            }
        }
        return HealingDecision.abort(rankedCandidates.isEmpty() ? 0.0 : rankedCandidates.get(0).getScore());
    }
}
