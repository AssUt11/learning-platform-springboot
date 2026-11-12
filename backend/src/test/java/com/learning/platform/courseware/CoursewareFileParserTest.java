package com.learning.platform.courseware;

import static org.junit.jupiter.api.Assertions.*;

import com.learning.platform.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoursewareFileParserTest {
    @TempDir Path uploads;

    @Test
    void pdfKeepsRealPageNumbersAndContentHash() throws Exception {
        Path file = uploads.resolve("course.pdf");
        try (PDDocument document = new PDDocument()) {
            for (String line : List.of("Index narrows query scans.", "Transactions commit together.")) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                    content.newLineAtOffset(40, 700);
                    content.showText(line);
                    content.endText();
                }
            }
            document.save(file.toFile());
        }
        var result = parser().parse("/uploads/course.pdf", "course.pdf");
        assertEquals(List.of("PAGE", "PAGE"), result.segments().stream().map(CoursewareFileParser.Segment::locationType).toList());
        assertEquals(List.of(1, 2), result.segments().stream().map(CoursewareFileParser.Segment::locationIndex).toList());
        assertTrue(result.segments().get(0).text().contains("Index narrows"));
        assertTrue(result.segments().get(1).text().contains("Transactions commit"));
        assertEquals(CoursewareAssetVersion.sha256(Files.readAllBytes(file)), result.versionHash());
    }

    @Test
    void pptxKeepsSlideNumbersAndVisibleTableText() throws Exception {
        Path file = uploads.resolve("slides.pptx");
        try (XMLSlideShow slides = new XMLSlideShow()) {
            var first = slides.createSlide();
            first.createTextBox().setText("索引服务于具体查询条件。");
            var table = slides.createSlide().createTable(1, 1);
            table.getCell(0, 0).setText("事务与隔离级别");
            try (var output = Files.newOutputStream(file)) { slides.write(output); }
        }
        var result = parser().parse("/uploads/slides.pptx", "slides.pptx");
        assertEquals(2, result.segments().size());
        assertEquals("SLIDE", result.segments().get(1).locationType());
        assertEquals(2, result.segments().get(1).locationIndex());
        assertTrue(result.segments().get(1).text().contains("事务与隔离级别"));
    }

    @Test
    void docxUsesSectionReferencesRatherThanInventedPhysicalPages() throws Exception {
        Path file = uploads.resolve("notes.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            document.createParagraph().createRun().setText("概率描述事件发生的可能性。");
            document.createTable(1, 1).getRow(0).getCell(0).setText("期望是理论平均值。");
            try (var output = Files.newOutputStream(file)) { document.write(output); }
        }
        var result = parser().parse("/uploads/notes.docx", "notes.docx");
        assertEquals(2, result.segments().size());
        assertEquals("SECTION", result.segments().get(0).locationType());
        assertTrue(result.segments().get(1).text().contains("期望是理论平均值"));
    }

    @Test
    void utf8TextIsChunkedWithoutBreakingSurrogatePairs() throws Exception {
        String content = "中".repeat(899) + "𠀀" + "文".repeat(20);
        Files.writeString(uploads.resolve("notes.txt"), content, StandardCharsets.UTF_8);
        var result = parser().parse("/uploads/notes.txt", "notes.txt");
        assertEquals(2, result.segments().size());
        assertEquals(content, String.join("", result.segments().stream().map(CoursewareFileParser.Segment::text).toList()));
        assertFalse(Character.isHighSurrogate(result.segments().get(0).text().charAt(898)));
    }

    @Test
    void rejectsRemoteAndTraversalPaths() throws Exception {
        Files.writeString(uploads.resolve("safe.txt"), "safe");
        for (String value : List.of("https://example.com/course.pdf", "/uploads/../private.txt", "/uploads/avatars/photo.png", "file:///private.txt")) {
            assertThrows(BusinessException.class, () -> parser().resolve(value));
        }
    }

    @Test
    void rejectsDisguisedPdfUnsupportedLegacyAndInvalidUtf8() throws Exception {
        Files.writeString(uploads.resolve("forged.pdf"), "This is not a PDF.");
        assertThrows(BusinessException.class, () -> parser().parse("/uploads/forged.pdf", "forged.pdf"));
        assertFalse(parser().supports("legacy.doc"));
        assertFalse(parser().supports("legacy.ppt"));
        assertThrows(BusinessException.class, () -> parser().parse("/uploads/legacy.doc", "legacy.doc"));
        Files.write(uploads.resolve("invalid.txt"), new byte[] { (byte) 0xc3, (byte) 0x28 });
        assertThrows(BusinessException.class, () -> parser().parse("/uploads/invalid.txt", "invalid.txt"));
    }

    @Test
    void imageOnlyPdfDoesNotClaimOcrSuccess() throws Exception {
        Path file = uploads.resolve("scan.pdf");
        try (PDDocument document = new PDDocument()) { document.addPage(new PDPage()); document.save(file.toFile()); }
        BusinessException error = assertThrows(BusinessException.class, () -> parser().parse("/uploads/scan.pdf", "scan.pdf"));
        assertTrue(error.getMessage().contains("OCR"));
    }

    @Test
    void rejectsOversizedFileBeforeParsing() throws Exception {
        try (var file = new java.io.RandomAccessFile(uploads.resolve("large.pdf").toFile(), "rw")) {
            file.setLength(CoursewareFileParser.MAX_FILE_BYTES + 1);
        }
        assertThrows(BusinessException.class, () -> parser().resolve("/uploads/large.pdf"));
    }

    @Test
    void rejectsOversizedOfficeZipExpansionBeforeOpeningPoi() throws Exception {
        Path file = uploads.resolve("large.docx");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
            byte[] block = new byte[1024 * 1024];
            for (int index = 0; index < 33; index++) output.write(block);
            output.closeEntry();
        }
        BusinessException error = assertThrows(BusinessException.class,
            () -> parser().parse("/uploads/large.docx", "large.docx"));
        assertTrue(error.getMessage().contains("解压规模"));
    }

    @Test
    void refusesOversizedTextAndMoreThanTwoHundredPdfPages() throws Exception {
        Files.writeString(uploads.resolve("long.txt"), "中".repeat(CoursewareFileParser.MAX_CHARACTERS + 1));
        assertThrows(BusinessException.class, () -> parser().parse("/uploads/long.txt", "long.txt"));
        try (PDDocument pdf = new PDDocument()) {
            for (int index = 0; index <= CoursewareFileParser.MAX_LOCATIONS; index++) pdf.addPage(new PDPage());
            pdf.save(uploads.resolve("long.pdf").toFile());
        }
        BusinessException error = assertThrows(BusinessException.class,
            () -> parser().parse("/uploads/long.pdf", "long.pdf"));
        assertTrue(error.getMessage().contains("200"));
    }

    private CoursewareFileParser parser() { return new CoursewareFileParser(uploads.toString()); }
}
