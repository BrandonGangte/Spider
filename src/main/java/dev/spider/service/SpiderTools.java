package dev.spider.service;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SpiderTools {

    private static final int MAX_FILE_BYTES = 64 * 1024;
    private static final int MAX_SEARCH_FILE_BYTES = 256 * 1024;
    private static final int MAX_SEARCH_FILES = 1000;
    private static final int MAX_SEARCH_RESULTS = 10;
    private static final int MAX_LIST_RESULTS = 100;
    private final Path workspaceRoot;

    public SpiderTools(@Value("${spider.tools.workspace-root:./workspace}") String workspaceRoot) {
        try {
            Path configuredRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
            Files.createDirectories(configuredRoot);
            this.workspaceRoot = configuredRoot.toRealPath();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not initialize the Spider workspace directory.", exception);
        }
    }

    @Tool("Evaluate a basic arithmetic expression. Supports addition, subtraction, multiplication, division, remainder, unary signs, decimals, and parentheses. Use this instead of mental arithmetic.")
    public String calculate(@P("Arithmetic expression, for example (18 * 7) / 3") String expression) {
        if (expression == null || expression.length() > 128) {
            return "Calculation error: expression must contain between 1 and 128 characters.";
        }

        try {
            double result = new ArithmeticParser(expression).parse();
            if (!Double.isFinite(result)) {
                return "Calculation error: result is outside the supported numeric range.";
            }
            return BigDecimal.valueOf(result).stripTrailingZeros().toPlainString();
        } catch (IllegalArgumentException exception) {
            return "Calculation error: " + exception.getMessage();
        }
    }

    @Tool("Return the current date and time on the Spider server, including its time zone. Use this when the user asks for the current date or time.")
    public String getCurrentDateTime() {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }

    @Tool("Use only when the user explicitly asks to inspect files in Spider's workspace. List files and directories directly inside a relative directory in the read-only workspace. Do not use for general questions or casual conversation. Use '.' for the workspace root. Hidden and sensitive files are omitted.")
    public String listWorkspaceFiles(@P("Relative directory inside the workspace, or . for the root") String relativeDirectory) {
        try {
            Path directory = resolveWorkspacePath(relativeDirectory);
            if (!Files.isDirectory(directory)) {
                return "Workspace error: the requested path is not a directory.";
            }

            List<String> entries = new ArrayList<>();
            try (Stream<Path> children = Files.list(directory)) {
                children.limit(MAX_LIST_RESULTS + 1L).forEach(child -> {
                    try {
                        Path realChild = child.toRealPath();
                        if (!isVisibleWorkspacePath(realChild)) {
                            return;
                        }
                        String relative = workspaceRoot.relativize(realChild).toString().replace('\\', '/');
                        if (Files.isDirectory(realChild)) {
                            entries.add("[DIR] " + relative + "/");
                        } else if (Files.isRegularFile(realChild)) {
                            entries.add("[FILE] " + relative);
                        }
                    } catch (IOException ignored) {
                        // Entries that cannot be resolved safely are omitted.
                    }
                });
            }

            if (entries.isEmpty()) {
                return "The requested workspace directory is empty.";
            }
            boolean truncated = entries.size() > MAX_LIST_RESULTS;
            List<String> visibleEntries = entries.stream().limit(MAX_LIST_RESULTS).toList();
            return String.join("\n", visibleEntries) + (truncated ? "\n[Results limited to 100 entries.]" : "");
        } catch (IOException | IllegalArgumentException exception) {
            return "Workspace error: " + exception.getMessage();
        }
    }

    @Tool("Use only when the user explicitly asks you to read a local workspace file or identifies a specific local file. Read a UTF-8 text file from Spider's read-only workspace. Do not use for general questions or casual conversation. Only relative paths inside the workspace are allowed. Hidden, sensitive, binary, and files larger than 64 KB cannot be read.")
    public String readWorkspaceFile(@P("Relative path of the text file inside the workspace") String relativePath) {
        try {
            Path file = resolveWorkspacePath(relativePath);
            if (!Files.isRegularFile(file)) {
                return "Workspace error: the requested path is not a file.";
            }
            if (!isVisibleWorkspacePath(file)) {
                return "Workspace error: this file is hidden or restricted.";
            }
            long size = Files.size(file);
            if (size > MAX_FILE_BYTES) {
                return "Workspace error: the file exceeds the 64 KB read limit.";
            }
            byte[] content = Files.readAllBytes(file);
            for (byte value : content) {
                if (value == 0) {
                    return "Workspace error: binary files cannot be read by this tool.";
                }
            }
            return new String(content, StandardCharsets.UTF_8);
        } catch (IOException | IllegalArgumentException exception) {
            return "Workspace error: " + exception.getMessage();
        }
    }

    @Tool("Use only when the user explicitly asks to search files in Spider's workspace. Search for a literal, case-insensitive text query in the read-only workspace. Do not use for general questions or casual conversation. Search is limited to 1000 text files, 256 KB per file, and 10 matching results. Hidden and sensitive files are skipped.")
    public String searchWorkspaceFiles(@P("Literal text to search for; 1 to 128 characters") String query) {
        if (query == null || query.isBlank() || query.length() > 128) {
            return "Workspace search error: query must contain between 1 and 128 characters.";
        }

        String needle = query.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> paths = Files.walk(workspaceRoot, 5)) {
            var iterator = paths.iterator();
            while (iterator.hasNext() && scanned < MAX_SEARCH_FILES && results.size() < MAX_SEARCH_RESULTS) {
                Path path = iterator.next();
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    continue;
                }
                Path realPath;
                try {
                    realPath = path.toRealPath();
                } catch (IOException ignored) {
                    continue;
                }
                if (!isVisibleWorkspacePath(realPath)) {
                    continue;
                }

                scanned++;
                try {
                    if (Files.size(realPath) > MAX_SEARCH_FILE_BYTES) {
                        continue;
                    }
                    String content = Files.readString(realPath, StandardCharsets.UTF_8);
                    if (content.indexOf('\0') >= 0) {
                        continue;
                    }
                    int matchAt = content.toLowerCase(Locale.ROOT).indexOf(needle);
                    if (matchAt >= 0) {
                        int lineNumber = 1;
                        for (int index = 0; index < matchAt; index++) {
                            if (content.charAt(index) == '\n') {
                                lineNumber++;
                            }
                        }
                        int start = Math.max(0, matchAt - 100);
                        int end = Math.min(content.length(), matchAt + query.length() + 140);
                        String excerpt = content.substring(start, end).replaceAll("\\s+", " ").trim();
                        String relative = workspaceRoot.relativize(realPath).toString().replace('\\', '/');
                        results.add(relative + ":" + lineNumber + ": " + excerpt);
                    }
                } catch (IOException ignored) {
                    // Files that cannot be read as UTF-8 text are skipped.
                }
            }
        } catch (IOException exception) {
            return "Workspace search error: " + exception.getMessage();
        }

        if (results.isEmpty()) {
            return "No matching text found in the workspace.";
        }
        boolean resultLimitReached = results.size() == MAX_SEARCH_RESULTS;
        return String.join("\n", results) + (resultLimitReached ? "\n[Results limited to 10 matches.]" : "");
    }

    private Path resolveWorkspacePath(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("a relative workspace path is required.");
        }
        Path requested = Path.of(relativePath);
        if (requested.isAbsolute()) {
            throw new IllegalArgumentException("absolute paths are not allowed.");
        }
        Path normalized = workspaceRoot.resolve(requested).normalize();
        if (!normalized.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("path must stay inside the workspace.");
        }
        Path realPath = normalized.toRealPath();
        if (!realPath.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("path must stay inside the workspace.");
        }
        if (!isVisibleWorkspacePath(realPath)) {
            throw new IllegalArgumentException("hidden or sensitive paths are not accessible.");
        }
        return realPath;
    }

    private boolean isVisibleWorkspacePath(Path path) {
        if (!path.startsWith(workspaceRoot)) {
            return false;
        }
        Path relative = workspaceRoot.relativize(path);
        for (Path segment : relative) {
            String name = segment.toString();
            String lowerName = name.toLowerCase(Locale.ROOT);
            if (name.startsWith(".") || lowerName.contains("secret") || lowerName.contains("credential")
                    || lowerName.equals("id_rsa") || lowerName.equals("id_ed25519")
                    || lowerName.endsWith(".pem") || lowerName.endsWith(".key")
                    || lowerName.endsWith(".p12") || lowerName.endsWith(".pfx")
                    || lowerName.endsWith(".jks") || lowerName.endsWith(".kdbx")) {
                return false;
            }
        }
        return true;
    }

    private static final class ArithmeticParser {

        private final String input;
        private int position;

        private ArithmeticParser(String input) {
            this.input = input;
        }

        private double parse() {
            double result = expression();
            skipWhitespace();
            if (position != input.length()) {
                throw error("unexpected character '" + input.charAt(position) + "'.");
            }
            return result;
        }

        private double expression() {
            double result = term();
            while (true) {
                skipWhitespace();
                if (take('+')) {
                    result += term();
                } else if (take('-')) {
                    result -= term();
                } else {
                    return result;
                }
            }
        }

        private double term() {
            double result = factor();
            while (true) {
                skipWhitespace();
                if (take('*')) {
                    result *= factor();
                } else if (take('/')) {
                    double divisor = factor();
                    if (divisor == 0) {
                        throw error("division by zero is not allowed.");
                    }
                    result /= divisor;
                } else if (take('%')) {
                    double divisor = factor();
                    if (divisor == 0) {
                        throw error("remainder by zero is not allowed.");
                    }
                    result %= divisor;
                } else {
                    return result;
                }
            }
        }

        private double factor() {
            skipWhitespace();
            if (take('+')) {
                return factor();
            }
            if (take('-')) {
                return -factor();
            }
            if (take('(')) {
                double result = expression();
                skipWhitespace();
                if (!take(')')) {
                    throw error("missing closing parenthesis.");
                }
                return result;
            }
            return number();
        }

        private double number() {
            skipWhitespace();
            int start = position;
            boolean decimalPoint = false;
            while (position < input.length()) {
                char current = input.charAt(position);
                if (Character.isDigit(current)) {
                    position++;
                } else if (current == '.' && !decimalPoint) {
                    decimalPoint = true;
                    position++;
                } else {
                    break;
                }
            }
            if (start == position || input.substring(start, position).equals(".")) {
                throw error("a number was expected.");
            }
            try {
                return Double.parseDouble(input.substring(start, position));
            } catch (NumberFormatException exception) {
                throw error("invalid number.");
            }
        }

        private boolean take(char expected) {
            if (position < input.length() && input.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private void skipWhitespace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " At position " + (position + 1) + ".");
        }
    }
}
