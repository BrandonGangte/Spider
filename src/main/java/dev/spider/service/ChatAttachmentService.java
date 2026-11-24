package dev.spider.service;

import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.parser.apache.poi.ApachePoiDocumentParser;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatAttachmentService {

    private static final int MAX_ATTACHMENTS = 5;
    private static final int MAX_IMAGES = 3;
    private static final long MAX_FILE_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 20L * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 30_000_000L;
    private static final int MAX_IMAGE_EDGE = 1600;
    private static final int MAX_IMAGE_OUTPUT_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TEXT_PER_FILE = 40_000;
    private static final int MAX_TOTAL_TEXT = 100_000;
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg");
    private static final Set<String> OFFICE_EXTENSIONS = Set.of("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx");
    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "md", "csv", "json", "xml", "yaml", "yml", "html", "htm", "java", "js", "jsx",
            "ts", "tsx", "css", "sql", "py", "properties", "log", "toml", "ini", "sh", "ps1");

    private final ApachePdfBoxDocumentParser pdfParser = new ApachePdfBoxDocumentParser();
    private final ApachePoiDocumentParser officeParser = new ApachePoiDocumentParser();

    public List<Content> toContents(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("Attach at least one file.");
        }
        if (files.size() > MAX_ATTACHMENTS) {
            throw new IllegalArgumentException("Attach no more than five files at a time.");
        }

        long totalBytes = 0;
        int imageCount = 0;
        int totalTextCharacters = 0;
        List<Content> contents = new ArrayList<>();

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("One of the attached files is empty.");
            }
            if (file.getSize() > MAX_FILE_BYTES) {
                throw new IllegalArgumentException("Each attachment must be 8 MB or smaller.");
            }
            totalBytes += file.getSize();
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw new IllegalArgumentException("Attachments must total 20 MB or less.");
            }

            String filename = safeFilename(file.getOriginalFilename());
            String extension = extension(filename);
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (IOException exception) {
                throw new IllegalArgumentException("Could not read attachment " + filename + ".");
            }

            if (IMAGE_EXTENSIONS.contains(extension)) {
                imageCount++;
                if (imageCount > MAX_IMAGES) {
                    throw new IllegalArgumentException("Attach no more than three images at a time.");
                }
                contents.add(TextContent.from("Attached image: " + filename));
                contents.add(toImageContent(filename, extension, bytes));
            } else if (OFFICE_EXTENSIONS.contains(extension)) {
                String text = extractOfficeText(filename, bytes, extension);
                totalTextCharacters = addDocumentText(contents, filename, text, totalTextCharacters);
            } else if (TEXT_EXTENSIONS.contains(extension)) {
                String text = decodeText(filename, bytes);
                totalTextCharacters = addDocumentText(contents, filename, text, totalTextCharacters);
            } else {
                throw new IllegalArgumentException("Unsupported attachment type for " + filename + ". Use text, PDF, Office documents, PNG, or JPEG files.");
            }
        }
        return contents;
    }

    private int addDocumentText(List<Content> contents, String filename, String text, int totalCharacters) {
        String cleanText = text == null ? "" : text.strip();
        if (cleanText.isEmpty()) {
            cleanText = "[No extractable text was found in this document.]";
        }
        if (cleanText.length() > MAX_TEXT_PER_FILE) {
            cleanText = cleanText.substring(0, MAX_TEXT_PER_FILE) + "\n[Document text truncated at 40,000 characters.]";
        }
        totalCharacters += cleanText.length();
        if (totalCharacters > MAX_TOTAL_TEXT) {
            throw new IllegalArgumentException("Extracted document text exceeds the 100,000 character limit. Attach fewer or shorter documents.");
        }
        contents.add(TextContent.from("Attachment: " + filename + "\n" + cleanText));
        return totalCharacters;
    }

    private String extractOfficeText(String filename, byte[] bytes, String extension) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            if (extension.equals("pdf")) {
                return pdfParser.parse(inputStream).text();
            }
            return officeParser.parse(inputStream).text();
        } catch (RuntimeException | IOException exception) {
            throw new IllegalArgumentException("Could not extract text from " + filename + ". Check that the document is valid and readable.");
        }
    }

    private String decodeText(String filename, byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                throw new IllegalArgumentException(filename + " does not appear to be a text file.");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException(filename + " is not valid UTF-8 text.");
        }
    }

    private ImageContent toImageContent(String filename, String extension, byte[] bytes) {
        String expectedFormat = extension.equals("png") ? "png" : "jpeg";
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new IllegalArgumentException(filename + " is not a readable image.");
            }
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IllegalArgumentException(filename + " is not a supported PNG or JPEG image.");
            }
            ImageReader reader = readers.next();
            BufferedImage decoded;
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IllegalArgumentException(filename + " exceeds the 30 megapixel image limit.");
                }
                String actualFormat = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (expectedFormat.equals("jpeg") && !actualFormat.equals("jpeg") && !actualFormat.equals("jpg")) {
                    throw new IllegalArgumentException(filename + " extension does not match its image format.");
                }
                if (expectedFormat.equals("png") && !actualFormat.equals("png")) {
                    throw new IllegalArgumentException(filename + " extension does not match its image format.");
                }
                decoded = reader.read(0);
            } finally {
                reader.dispose();
            }

            double scale = Math.min(1d, (double) MAX_IMAGE_EDGE / Math.max(decoded.getWidth(), decoded.getHeight()));
            int width = Math.max(1, (int) Math.round(decoded.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(decoded.getHeight() * scale));
            BufferedImage normalized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = normalized.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, width, height);
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                graphics.drawImage(decoded, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
                decoded.flush();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(normalized, "jpeg", output)) {
                throw new IllegalArgumentException("Could not prepare " + filename + " for the vision model.");
            }
            normalized.flush();
            byte[] modelImage = output.toByteArray();
            if (modelImage.length > MAX_IMAGE_OUTPUT_BYTES) {
                throw new IllegalArgumentException(filename + " is too detailed to prepare within the 2 MB image limit.");
            }
            return ImageContent.from(Base64.getEncoder().encodeToString(modelImage), "image/jpeg");
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not decode image " + filename + ".");
        }
    }

    private static String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "attachment";
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[^A-Za-z0-9._ -]", "_");
        return filename.isBlank() ? "attachment" : filename;
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
