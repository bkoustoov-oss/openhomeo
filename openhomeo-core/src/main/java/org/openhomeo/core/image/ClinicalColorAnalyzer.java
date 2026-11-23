package org.openhomeo.core.image;

import org.openhomeo.core.native_bridge.NativeBridge;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Clinical Color Analyzer.
 * Measures erythema (redness shift on CIE-a* axis), jaundice/coating (CIE-b*),
 * and computes CIEDE2000 color delta for quantitative lesion progression tracking.
 */
public class ClinicalColorAnalyzer {

    public record LabStats(
            double meanL,   // Lightness (0 = black, 100 = white)
            double meanA,   // Green to Red axis (positive = erythema/redness)
            double meanB,   // Blue to Yellow axis (positive = jaundice/yellow coat)
            int pixelCount
    ) {}

    public record ComparisonResult(
            double deltaE00,            // CIEDE2000 total color difference
            double erythemaDeltaA,      // Change in redness (a2 - a1)
            double lightnessDeltaL,     // Change in lightness (l2 - l1)
            double yellownessDeltaB,    // Change in yellowness (b2 - b1)
            String clinicalInterpretation
    ) {}

    /**
     * Computes the mean CIE-L*a*b* coordinates for an entire image or a specific ROI bounding box.
     */
    public static LabStats analyzeRegion(BufferedImage img, Rectangle roi) {
        int xStart = roi != null ? Math.max(0, roi.x) : 0;
        int yStart = roi != null ? Math.max(0, roi.y) : 0;
        int width = roi != null ? Math.min(roi.width, img.getWidth() - xStart) : img.getWidth();
        int height = roi != null ? Math.min(roi.height, img.getHeight() - yStart) : img.getHeight();

        double sumL = 0.0;
        double sumA = 0.0;
        double sumB = 0.0;
        int count = 0;

        for (int y = yStart; y < yStart + height; y++) {
            for (int x = xStart; x < xStart + width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                double[] lab = NativeBridge.rgbToLab(r, g, b);
                sumL += lab[0];
                sumA += lab[1];
                sumB += lab[2];
                count++;
            }
        }

        if (count == 0) return new LabStats(0.0, 0.0, 0.0, 0);

        return new LabStats(sumL / count, sumA / count, sumB / count, count);
    }

    /**
     * Compares a baseline visit photo with a follow-up visit photo.
     */
    public static ComparisonResult compareProgression(LabStats baseline, LabStats followUp) {
        double deltaE = NativeBridge.ciede2000(
                baseline.meanL(), baseline.meanA(), baseline.meanB(),
                followUp.meanL(), followUp.meanA(), followUp.meanB()
        );

        double deltaA = followUp.meanA() - baseline.meanA();
        double deltaL = followUp.meanL() - baseline.meanL();
        double deltaB = followUp.meanB() - baseline.meanB();

        String interpretation;
        if (deltaE < 1.0) {
            interpretation = "No perceptible clinical color shift (Delta-E < 1.0)";
        } else if (deltaA < -3.0) {
            interpretation = String.format("Significant reduction in erythema/inflammation (Delta a* = %.1f, Delta-E = %.1f)", deltaA, deltaE);
        } else if (deltaA > 3.0) {
            interpretation = String.format("Increased erythema / inflammatory flare-up (Delta a* = +%.1f, Delta-E = %.1f)", deltaA, deltaE);
        } else {
            interpretation = String.format("Perceptible color change observed (Delta-E = %.1f)", deltaE);
        }

        return new ComparisonResult(deltaE, deltaA, deltaL, deltaB, interpretation);
    }
}
