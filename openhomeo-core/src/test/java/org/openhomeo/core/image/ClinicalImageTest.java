package org.openhomeo.core.image;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ClinicalImageTest {

    @Test
    public void testImageVaultStoreAndRetrieve(@TempDir Path tempDir) throws Exception {
        ImageVaultService vault = new ImageVaultService(tempDir.toString());

        // Create a synthetic 100x100 test clinical image
        BufferedImage testImg = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = testImg.createGraphics();
        g.setColor(new Color(220, 60, 60)); // Erythematous rash color
        g.fillRect(0, 0, 100, 100);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(testImg, "PNG", baos);
        byte[] bytes = baos.toByteArray();

        ImageVaultService.StoreResult res = vault.storeImage(bytes, "PNG");
        assertNotNull(res.contentHash());
        assertEquals(100, res.width());
        assertEquals(100, res.height());
        assertEquals("PNG", res.fileFormat());

        // Retrieve by hash
        byte[] retrievedBytes = vault.retrieveImage(res.contentHash());
        assertArrayEquals(bytes, retrievedBytes);

        // Check thumbnail was generated
        assertTrue(vault.getThumbnailFile(res.contentHash()).exists());
    }

    @Test
    public void testClinicalColorAnalysisAndErythemaDelta() {
        // Baseline visit: Severe red inflamed rash (high a*)
        BufferedImage baseline = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g1 = baseline.createGraphics();
        g1.setColor(new Color(230, 50, 50));
        g1.fillRect(0, 0, 50, 50);
        g1.dispose();

        // Follow-up visit: After Sulphur / Arsenicum administration -> healing skin tone
        BufferedImage healed = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = healed.createGraphics();
        g2.setColor(new Color(210, 160, 140));
        g2.fillRect(0, 0, 50, 50);
        g2.dispose();

        ClinicalColorAnalyzer.LabStats baseStats = ClinicalColorAnalyzer.analyzeRegion(baseline, null);
        ClinicalColorAnalyzer.LabStats followUpStats = ClinicalColorAnalyzer.analyzeRegion(healed, null);

        assertTrue(baseStats.meanA() > 40.0, "Baseline should have high positive a* (erythema)");
        assertTrue(followUpStats.meanA() < baseStats.meanA(), "Follow-up should show lower a* redness");

        ClinicalColorAnalyzer.ComparisonResult comp = ClinicalColorAnalyzer.compareProgression(baseStats, followUpStats);
        assertTrue(comp.deltaE00() > 10.0, "Delta-E should indicate marked visual color change");
        assertTrue(comp.erythemaDeltaA() < -15.0, "Erythema delta a* should be substantially negative");
        assertTrue(comp.clinicalInterpretation().contains("reduction in erythema"),
                "Interpretation should diagnose reduction in inflammation: " + comp.clinicalInterpretation());
    }
}
