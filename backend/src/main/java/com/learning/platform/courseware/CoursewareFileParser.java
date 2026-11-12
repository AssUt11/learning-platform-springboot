package com.learning.platform.courseware;

import com.learning.platform.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipFile;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFGroupShape;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTable;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bounded local parsing, without network access, embedded attachments or OCR. */
@Component
public class CoursewareFileParser {
    public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;
    public static final int MAX_LOCATIONS = 200;
    public static final int MAX_CHARACTERS = 500_000;
    public static final int MAX_CHUNKS = 800;
    public static final int CHUNK_CHARACTERS = 900;
    private static final int MAX_PACKAGE_ENTRIES = 2000;
    private static final long MAX_EXPANDED_BYTES = 80L * 1024 * 1024;
    private static final Set<String> SUPPORTED = Set.of("pdf", "pptx", "docx", "txt");
    private final Path uploadRoot;
    private final Tika detector = new Tika();

    public CoursewareFileParser(@Value("${app.upload-dir}") String uploadRoot) {
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
    }

    public boolean supports(String filename) {
        return SUPPORTED.contains(extension(filename));
    }

    public Path resolve(String fileUrl) throws IOException {
        if (fileUrl == null || !fileUrl.matches("/uploads/[A-Za-z0-9._-]+") || fileUrl.contains("..")) {
            throw new BusinessException(400, "课件不是平台上传的本地文件。");
        }
        Path root = uploadRoot.toRealPath();
        Path file = root.resolve(fileUrl.substring("/uploads/".length())).toRealPath();
        if (!file.getParent().equals(root) || !Files.isRegularFile(file)) {
            throw new BusinessException(400, "课件文件路径无效。");
        }
        long size = Files.size(file);
        if (size <= 0 || size > MAX_FILE_BYTES) {
            throw new BusinessException(400, "课件须非空且不超过 20 MB。");
        }
        return file;
    }

    public String hash(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                long count = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    count += read;
                    if (count > MAX_FILE_BYTES) throw new BusinessException(400, "课件不能超过 20 MB。");
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public ParsedDocument parse(String fileUrl, String filename) throws Exception {
        if (!supports(filename)) throw unsupported();
        Path path = resolve(fileUrl);
        String beforeHash = hash(path);
        String detected = detector.detect(path);
        String extension = extension(filename);
        List<Segment> segments = new ArrayList<>();
        switch (extension) {
            case "pdf" -> {
                if (!"application/pdf".equals(detected)) throw invalidFormat();
                pdf(path, segments);
            }
            case "pptx" -> {
                officeType(detected);
                validatePackage(path);
                presentation(path, segments);
            }
            case "docx" -> {
                officeType(detected);
                validatePackage(path);
                document(path, segments);
            }
            case "txt" -> {
                if (!detected.startsWith("text/")) throw invalidFormat();
                text(path, segments);
            }
            default -> throw unsupported();
        }
        if (!beforeHash.equals(hash(path))) throw new BusinessException(409, "解析期间课件发生变化，请重新解析。");
        if (segments.isEmpty()) {
            throw new BusinessException(422, "未提取到可读文字。扫描件或纯图片课件暂不支持 OCR，请上传含文字的课件。");
        }
        return new ParsedDocument(beforeHash, List.copyOf(segments));
    }

    private void pdf(Path path, List<Segment> segments) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            if (pdf.isEncrypted() || !pdf.getCurrentAccessPermission().canExtractContent()) {
                throw new BusinessException(422, "加密或禁止复制的 PDF 无法解析，请提供可提取文字的版本。");
            }
            if (pdf.getNumberOfPages() > MAX_LOCATIONS) throw tooManyLocations();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int index = 1; index <= pdf.getNumberOfPages(); index++) {
                stripper.setStartPage(index);
                stripper.setEndPage(index);
                BoundedWriter text = new BoundedWriter();
                stripper.writeText(pdf, text);
                append(segments, "PAGE", index, text.toString());
            }
        }
    }

    private void presentation(Path path, List<Segment> segments) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(path.toFile(), PackageAccess.READ);
             XMLSlideShow presentation = new XMLSlideShow(pkg)) {
            if (presentation.getSlides().size() > MAX_LOCATIONS) throw tooManyLocations();
            int index = 0;
            for (var slide : presentation.getSlides()) {
                StringBuilder text = new StringBuilder();
                slideText(slide.getShapes(), text);
                append(segments, "SLIDE", ++index, text.toString());
            }
        }
    }

    private void slideText(List<XSLFShape> shapes, StringBuilder text) {
        for (XSLFShape shape : shapes) {
            if (shape instanceof XSLFTextShape) text.append(((XSLFTextShape) shape).getText()).append('\n');
            else if (shape instanceof XSLFGroupShape) slideText(((XSLFGroupShape) shape).getShapes(), text);
            else if (shape instanceof XSLFTable) {
                for (var row : ((XSLFTable) shape).getRows()) {
                    for (var cell : row.getCells()) text.append(cell.getText()).append('\t');
                    text.append('\n');
                }
            }
        }
    }

    private void document(Path path, List<Segment> segments) throws Exception {
        try (OPCPackage pkg = OPCPackage.open(path.toFile(), PackageAccess.READ);
             XWPFDocument document = new XWPFDocument(pkg)) {
            int index = 0;
            for (var body : document.getBodyElements()) {
                String text = body instanceof XWPFParagraph ? ((XWPFParagraph) body).getText()
                    : body instanceof XWPFTable ? ((XWPFTable) body).getText() : "";
                if (!text.trim().isEmpty()) append(segments, "SECTION", ++index, text);
            }
        }
    }

    private void text(Path path, List<Segment> segments) throws IOException {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString();
            if (text.length() > MAX_CHARACTERS) throw tooMuchText();
            int section = 0;
            for (String paragraph : text.split("\\R\\s*\\R")) {
                if (!paragraph.trim().isEmpty()) append(segments, "SECTION", ++section, paragraph);
            }
        } catch (CharacterCodingException exception) {
            throw new BusinessException(422, "TXT 课件需使用 UTF-8 编码。");
        }
    }

    private void append(List<Segment> segments, String type, int location, String content) {
        String text = content.replace('\u0000', ' ').replace("\r\n", "\n").trim();
        if (text.isEmpty()) return;
        int total = segments.stream().mapToInt(segment -> segment.text().length()).sum();
        if ((long) total + text.length() > MAX_CHARACTERS) {
            throw tooMuchText();
        }
        for (int start = 0; start < text.length();) {
            if (segments.size() >= MAX_CHUNKS) throw new BusinessException(422, "课件片段过多，请拆分文件。");
            int end = Math.min(start + CHUNK_CHARACTERS, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            segments.add(new Segment(type, location, text.substring(start, end)));
            start = end;
        }
    }

    private String extension(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        return name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
    }

    private void officeType(String mime) {
        if (!(mime.contains("openxmlformats") || "application/zip".equals(mime) || "application/x-tika-ooxml".equals(mime))) {
            throw invalidFormat();
        }
    }
    private void validatePackage(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            var entries = zip.entries();
            long total = 0;
            int count = 0;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (++count > MAX_PACKAGE_ENTRIES || entry.getSize() < 0 ||
                    entry.getSize() > 32L * 1024 * 1024 || (total += entry.getSize()) > MAX_EXPANDED_BYTES) {
                    throw new BusinessException(422, "Office 课件解压规模过大，请拆分文件。");
                }
            }
        }
    }
    private BusinessException unsupported() { return new BusinessException(400, "仅支持 PDF、PPTX、DOCX 和 UTF-8 TXT；旧版 DOC/PPT 请先转换。"); }
    private BusinessException invalidFormat() { return new BusinessException(422, "课件实际格式与文件扩展名不符。"); }
    private BusinessException tooManyLocations() { return new BusinessException(422, "课件超过 200 页或幻灯片，请拆分文件。"); }
    private BusinessException tooMuchText() { return new BusinessException(422, "课件文字超过 50 万字符，请拆分成较小文件。"); }

    private final class BoundedWriter extends Writer {
        private final StringBuilder text = new StringBuilder();
        @Override public void write(char[] chars, int offset, int length) {
            if ((long) text.length() + length > MAX_CHARACTERS) throw tooMuchText();
            text.append(chars, offset, length);
        }
        @Override public void flush() {}
        @Override public void close() {}
        @Override public String toString() { return text.toString(); }
    }

    public record Segment(String locationType, int locationIndex, String text) {}
    public record ParsedDocument(String versionHash, List<Segment> segments) {}
}
