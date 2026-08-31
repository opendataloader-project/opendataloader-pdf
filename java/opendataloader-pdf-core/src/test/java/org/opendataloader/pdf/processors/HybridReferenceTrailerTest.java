package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.AutoTagger;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.TaggingResult;
import org.verapdf.pd.PDDocument;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /XRefStm} is a byte offset into the layout of the source file, and the tagged document is
 * written out whole rather than appended to it. Inherited, it addresses arbitrary bytes: three Word
 * exports produced a tagged PDF that no reader honouring it could open, reported as "can not locate
 * xref table" with no output file.
 */
class HybridReferenceTrailerTest {

    private static Path writeHybridReferencePdf(Path dir) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] off = new int[7];

        append(out, "%PDF-1.7\n%âãÏÓ\n");
        off[1] = out.size();
        append(out, "1 0 obj\n<</Type /Catalog /Pages 2 0 R>>\nendobj\n");
        off[2] = out.size();
        append(out, "2 0 obj\n<</Type /Pages /Kids [3 0 R] /Count 1>>\nendobj\n");
        off[3] = out.size();
        append(out, "3 0 obj\n<</Type /Page /Parent 2 0 R /MediaBox [0 0 200 200]"
            + " /Contents 4 0 R /Resources <</Font <</F1 5 0 R>>>>>>\nendobj\n");
        String content = "BT /F1 14 Tf 20 150 Td (Hybrid reference fixture) Tj ET\n";
        off[4] = out.size();
        append(out, "4 0 obj\n<</Length " + content.length() + ">>\nstream\n" + content
            + "endstream\nendobj\n");
        off[5] = out.size();
        append(out, "5 0 obj\n<</Type /Font /Subtype /Type1 /BaseFont /Helvetica>>\nendobj\n");

        int classicXref = out.size();
        StringBuilder table = new StringBuilder("xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            table.append(String.format("%010d 00000 n \n", off[i]));
        }
        append(out, table.toString());
        append(out, "trailer\n<</Size 6 /Root 1 0 R>>\nstartxref\n" + classicXref + "\n%%EOF\n");

        // Uncompressed, so /Length needs no filter round-trip.
        off[6] = out.size();
        ByteArrayOutputStream entries = new ByteArrayOutputStream();
        int[] entryOffset = {0, off[1], off[2], off[3], off[4], off[5], off[6]};
        for (int i = 0; i <= 6; i++) {
            entries.write(i == 0 ? 0 : 1);
            int offset = entryOffset[i];
            entries.write((offset >>> 24) & 0xFF);
            entries.write((offset >>> 16) & 0xFF);
            entries.write((offset >>> 8) & 0xFF);
            entries.write(offset & 0xFF);
            int generation = i == 0 ? 65535 : 0;
            entries.write((generation >>> 8) & 0xFF);
            entries.write(generation & 0xFF);
        }
        byte[] streamData = entries.toByteArray();
        append(out, "6 0 obj\n<</Type /XRef /Size 7 /W [1 4 2] /Index [0 7] /Root 1 0 R /Length "
            + streamData.length + ">>\nstream\n");
        out.writeBytes(streamData);
        append(out, "\nendstream\nendobj\n");

        // What Word leaves behind: the last startxref points at an empty classic section whose
        // trailer delegates to the stream above (ISO 32000-1 7.5.8.4).
        int emptyXref = out.size();
        append(out, "xref\n0 0\ntrailer\n<</Size 7 /Root 1 0 R /Prev " + classicXref
            + " /XRefStm " + off[6] + ">>\nstartxref\n" + emptyXref + "\n%%EOF\n");

        Path pdf = dir.resolve("hybrid-reference.pdf");
        Files.write(pdf, out.toByteArray());
        return pdf;
    }

    private static void append(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String lastTrailer(Path pdf) throws Exception {
        String raw = new String(Files.readAllBytes(pdf), StandardCharsets.ISO_8859_1);
        int at = raw.lastIndexOf("trailer");
        assertThat(at).isNotNegative();
        return raw.substring(at);
    }

    private static Path tagAndSave(Path source, Path dir) throws Exception {
        Path output = dir.resolve("tagged.pdf");
        try (TaggingResult result = AutoTagger.tag(source.toString(), new Config(), null)) {
            result.saveTo(output.toString());
        }
        return output;
    }

    /** Without this, a fixture that stopped being hybrid would let the other two pass on nothing. */
    @Test
    void theFixtureIsAReadableHybridReferenceFile(@TempDir Path tempDir) throws Exception {
        Path pdf = writeHybridReferencePdf(tempDir);

        assertThat(lastTrailer(pdf)).contains("/XRefStm").contains("/Prev");
        assertThat(new PDDocument(pdf.toString()).getNumberOfPages()).isEqualTo(1);
    }

    @Test
    void taggedOutputDoesNotInheritTheSourceCrossReferenceOffset(@TempDir Path tempDir)
            throws Exception {
        Path output = tagAndSave(writeHybridReferencePdf(tempDir), tempDir);

        assertThat(lastTrailer(output)).doesNotContain("/XRefStm");
    }

    @Test
    void taggedOutputCanBeReopened(@TempDir Path tempDir) throws Exception {
        Path output = tagAndSave(writeHybridReferencePdf(tempDir), tempDir);

        assertThat(new PDDocument(output.toString()).getNumberOfPages()).isEqualTo(1);
    }
}
