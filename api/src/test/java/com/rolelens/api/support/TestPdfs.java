package com.rolelens.api.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Builds small real PDFs for tests, so nothing depends on fixture files. */
public final class TestPdfs {

    private TestPdfs() {
    }

    /** A one-page PDF with each given string on its own line. */
    public static byte[] withLines(String... lines) {
        return build(1, lines, null);
    }

    /** A PDF whose pages have no text at all, like a scanned CV. */
    public static byte[] blankPages(int pageCount) {
        return build(pageCount, new String[0], null);
    }

    /** A PDF with several pages where only the first page has text. */
    public static byte[] withPages(int pageCount, String firstPageText) {
        return build(pageCount, new String[] {firstPageText}, null);
    }

    /**
     * A PDF that needs a password to open. It sets a user password, because a
     * file with only an owner password can still be opened and read by anyone.
     */
    public static byte[] passwordProtected(String userPassword, String text) {
        StandardProtectionPolicy policy =
                new StandardProtectionPolicy("owner-" + userPassword, userPassword, new AccessPermission());
        policy.setEncryptionKeyLength(128);
        return build(1, new String[] {text}, policy);
    }

    private static byte[] build(int pageCount, String[] lines, StandardProtectionPolicy policy) {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {

            for (int i = 0; i < pageCount; i++) {
                PDPage page = new PDPage();
                document.addPage(page);

                if (i == 0 && lines.length > 0) {
                    try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                        content.beginText();
                        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        content.setLeading(16);
                        content.newLineAtOffset(72, 720);
                        for (String line : lines) {
                            content.showText(line);
                            content.newLine();
                        }
                        content.endText();
                    }
                }
            }

            if (policy != null) {
                document.protect(policy);
            }

            document.save(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
