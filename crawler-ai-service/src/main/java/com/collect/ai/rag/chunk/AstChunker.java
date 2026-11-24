package com.collect.ai.rag.chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码文件的结构分块器（AST 风格的启发式实现，无外部解析器依赖）。
 *
 * <p>流程：
 * <ol>
 *   <li>词法扫描：识别字符串/字符字面量、模板字符串、行/块注释、Java 文本块，
 *       其中的花括号与分号不影响结构判断（{@link #splitDeclarations}）；</li>
 *   <li>在花括号深度 0（顶层）按声明边界切分：顶层 {@code {…}} 块
 *       （类/接口/函数体/命名空间等）或顶层 {@code ;} 语句（package/import/字段等）构成一个声明单元；
 *       无顶层花括号/分号结构的文件（Python 等缩进语言）退化为按空行切段；</li>
 *   <li>package/import/#include 等前言单元提取为上下文头（长度受限），前置到每个 chunk，
 *       使每个 chunk 自包含（{@link #buildHeader}）；</li>
 *   <li>声明单元贪心合并到 chunkSize；单个声明超长时按空行段/行边界再切；
 *       overlap 从上一块末尾回带完整行（{@link #mergeChunks}）；</li>
 *   <li>chunk 若由超长声明切行而成，父文本 = 该声明完整文本（small-to-big），
 *       否则父文本 = chunk 本身（{@link Result#parents()}）。</li>
 * </ol>
 *
 * <p>已知启发式边界：JS 模板字符串 ${…} 内的花括号用嵌套计数跟踪，
 * 表达式内仅识别引号字符串与嵌套模板；Java 文本块结束符按
 * "连续两个及以上引号，或单引号后紧跟换行"识别；字符串未闭合时按行自愈。
 */
public final class AstChunker {

    private AstChunker() {
    }

    /** 分块结果：chunks 与 parents 平行（拆分声明的父文本 = 完整声明文本）。 */
    public record Result(List<String> chunks, List<String> parents) {
    }

    /** 上下文头（前言）长度上限基准（字符）。 */
    private static final int HEADER_MAX_BASE = 500;

    /**
     * 结构分块入口。
     *
     * @param code     源代码文本
     * @param chunkSize 单块最大长度（含上下文头）
     * @param overlap   块间重叠（字符），从上一块末尾回带完整行
     */
    public static Result chunk(String code, int chunkSize, int overlap) {
        if (code == null || code.isBlank()) return new Result(List.of(), List.of());
        int size = Math.max(1, chunkSize);
        int ov = Math.max(0, overlap);
        List<String> units = splitDeclarations(code);
        String header = buildHeader(units, Math.min(HEADER_MAX_BASE, size / 4));
        List<String> pool = new ArrayList<>();
        for (String unit : units) {
            if (!isPreamble(unit)) pool.add(unit);
        }
        if (pool.isEmpty()) {
            // 文件只有前言（package/import），整体作为单块
            String text = String.join("\n", units);
            return new Result(List.of(text), List.of(text));
        }
        return mergeChunks(pool, size, ov, header);
    }

    // ------------------------------------------------------------------
    // 顶层声明切分（词法感知扫描）
    // ------------------------------------------------------------------

    /**
     * 按顶层声明边界切分：深度 0 的 {@code ;} 语句或顶层 {@code {…}} 块构成一个单元。
     * 字符串/注释/模板字符串/文本块内的符号不参与判断。
     * 无顶层结构的输入退化为空行切段。
     */
    static List<String> splitDeclarations(String code) {
        List<String> units = new ArrayList<>();
        int n = code.length();
        int depth = 0;
        int start = 0;
        int i = 0;
        boolean inBlockComment = false;
        boolean inTextBlock = false;
        boolean topLevelBlock = false; // 当前深度 0 花括号是否为"声明块"（而非对象/字典字面量）
        while (i < n) {
            char c = code.charAt(i);
            if (inBlockComment) {
                if (c == '*' && i + 1 < n && code.charAt(i + 1) == '/') {
                    inBlockComment = false;
                    i += 2;
                } else {
                    i++;
                }
                continue;
            }
            if (inTextBlock) {
                if (c == '"') {
                    int quotes = 1;
                    while (i + quotes < n && code.charAt(i + quotes) == '"') quotes++;
                    // Java 文本块结束符：连续 >=2 个引号，或单引号后紧跟换行
                    if (quotes >= 2 || (quotes == 1 && i + 1 < n && code.charAt(i + 1) == '\n')) {
                        inTextBlock = false;
                        i += quotes;
                    } else {
                        i++;
                    }
                } else {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n) {
                char next = code.charAt(i + 1);
                if (next == '/') {
                    int eol = code.indexOf('\n', i);
                    i = (eol < 0) ? n : eol;
                    continue;
                }
                if (next == '*') {
                    inBlockComment = true;
                    i += 2;
                    continue;
                }
            }
            if (c == '"' || c == '\'') {
                if (c == '"' && code.startsWith("\"\"\"", i)) {
                    inTextBlock = true;
                    i += 3;
                    continue;
                }
                i = skipQuote(code, i, n);
                continue;
            }
            if (c == '`') {
                i = skipTemplate(code, i, n);
                continue;
            }
            if (c == '{') {
                if (depth == 0) topLevelBlock = isBlockOpener(code, i);
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                if (depth > 0) {
                    depth--;
                    if (depth == 0) {
                        if (topLevelBlock) {
                            addUnit(units, code.substring(start, i + 1));
                            start = i + 1;
                        }
                        topLevelBlock = false;
                    }
                }
                i++;
                continue;
            }
            if (c == ';' && depth == 0) {
                addUnit(units, code.substring(start, i + 1));
                start = i + 1;
            }
            i++;
        }
        addUnit(units, code.substring(start));
        // 无顶层花括号/分号结构（Python 等缩进语言、SQL 片段等）：退化为空行切段
        if (units.size() == 1) {
            List<String> blocks = new ArrayList<>();
            for (String block : units.get(0).split("\\R\\s*\\R")) {
                if (!block.trim().isEmpty()) blocks.add(block.trim());
            }
            if (blocks.size() > 1) return blocks;
        }
        return units;
    }

    private static void addUnit(List<String> units, String text) {
        String value = text.trim();
        // 丢弃纯标点单元（如 C 结构体 "struct X {...};" 切出的孤立 ";"）
        if (value.isEmpty() || value.chars().noneMatch(Character::isLetterOrDigit)) {
            return;
        }
        units.add(value);
    }

    /**
     * 判断深度 0 的花括号是否为"声明块"起点（类/函数体/switch/块语句等），
     * 而非对象/字典字面量。启发式：向前找最近的有效字符，
     * 为 {@code = ( [ , :} 时视为字面量（JS 对象、Python 字典、数组元素等）。
     */
    private static boolean isBlockOpener(String code, int braceIndex) {
        int j = braceIndex - 1;
        while (j >= 0 && Character.isWhitespace(code.charAt(j))) j--;
        if (j < 0) return true;
        char ch = code.charAt(j);
        return ch != '=' && ch != '(' && ch != '[' && ch != ',' && ch != ':';
    }

    /**
     * 从开引号位置跳过整个引号字符串（支持反斜杠转义，未闭合时按行自愈），
     * 返回字符串之后的位置。
     */
    private static int skipQuote(String code, int i, int n) {
        char quote = code.charAt(i);
        i++;
        while (i < n) {
            char sc = code.charAt(i);
            if (sc == '\\') {
                i += 2;
                continue;
            }
            if (sc == quote || sc == '\n') {
                return i + 1;
            }
            i++;
        }
        return n;
    }

    /**
     * 跳过从反引号开始的模板字符串（含 ${…} 插值，插值内用花括号计数跟踪），
     * 返回模板字符串之后的位置。
     */
    private static int skipTemplate(String code, int i, int n) {
        i++;
        while (i < n) {
            char c = code.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == '`') {
                return i + 1;
            }
            if (c == '$' && i + 1 < n && code.charAt(i + 1) == '{') {
                int depth = 0;
                i += 2;
                while (i < n) {
                    char ic = code.charAt(i);
                    if (ic == '"' || ic == '\'') {
                        i = skipQuote(code, i, n);
                        continue;
                    }
                    if (ic == '`') {
                        i = skipTemplate(code, i, n);
                        continue;
                    }
                    if (ic == '{') {
                        depth++;
                    } else if (ic == '}') {
                        depth--;
                        if (depth == 0) {
                            i++;
                            break;
                        }
                    }
                    i++;
                }
                continue;
            }
            i++;
        }
        return n;
    }

    // ------------------------------------------------------------------
    // 上下文头（前言）
    // ------------------------------------------------------------------

    /** 前言语句（package/import/include 等）：提取为上下文头，从分块池移除。 */
    static boolean isPreamble(String unit) {
        String t = unit.trim();
        return t.startsWith("package ") || t.startsWith("package\t")
                || t.startsWith("import ") || t.startsWith("import\t")
                || t.startsWith("using ") || t.startsWith("namespace ")
                || t.startsWith("#include") || t.startsWith("#define ")
                || t.startsWith("use ")
                || (t.startsWith("from ") && t.contains(" import "))
                || t.startsWith("require(");
    }

    /** 把前言单元拼成上下文头，按行截断到 maxChars，超出时以 "…" 收尾。 */
    private static String buildHeader(List<String> units, int maxChars) {
        if (maxChars <= 0) return "";
        List<String> lines = new ArrayList<>();
        for (String unit : units) {
            if (isPreamble(unit)) lines.add(unit.trim());
        }
        if (lines.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int add = (sb.length() == 0 ? 0 : 1) + line.length();
            if (sb.length() + add > maxChars) {
                if (sb.length() > 0) {
                    sb.append('\n').append('…');
                } else {
                    sb.append(line, 0, Math.min(line.length(), maxChars));
                }
                break;
            }
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // 合并、重叠与超长声明处理
    // ------------------------------------------------------------------

    /**
     * 把声明单元贪心合并成 chunk（含上下文头后总长 ≤ chunkSize）。
     * 单个声明超预算时按行切分，其父文本记为完整声明文本。
     */
    private static Result mergeChunks(List<String> units, int chunkSize, int overlap, String header) {
        int headerLen = header.isEmpty() ? 0 : header.length() + 1;
        int budget = Math.max(1, chunkSize - headerLen);
        List<String> chunks = new ArrayList<>();
        List<String> parents = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int curLen = 0;
        String parentSource = null; // 当前 chunk 包含被切行的超长声明时，记其完整文本
        for (String unit : units) {
            boolean unitSplit = unit.length() > budget;
            List<String> pieces = unitSplit ? splitByLines(unit, budget) : List.of(unit);
            for (String piece : pieces) {
                if (curLen > 0 && curLen + 1 + piece.length() > budget) {
                    // 收口当前 chunk：从最后一个单元末尾回带完整行作为重叠
                    String last = current.get(current.size() - 1);
                    int maxCarry = Math.min(overlap, Math.max(0, budget - piece.length() - 1));
                    String carry = tailLines(last, maxCarry);
                    emit(chunks, parents, header, current, parentSource);
                    current = new ArrayList<>();
                    curLen = 0;
                    parentSource = null;
                    if (!carry.isEmpty()) {
                        current.add(carry);
                        curLen = carry.length();
                    }
                }
                curLen = current.isEmpty() ? piece.length() : curLen + 1 + piece.length();
                current.add(piece);
                if (unitSplit) parentSource = unit;
            }
        }
        emit(chunks, parents, header, current, parentSource);
        return new Result(chunks, parents);
    }

    private static void emit(List<String> chunks, List<String> parents, String header,
                             List<String> current, String parentSource) {
        if (current.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        if (!header.isEmpty()) sb.append(header).append('\n');
        String chunk = sb.append(String.join("\n", current)).toString().trim();
        chunks.add(chunk);
        String parent = parentSource == null ? chunk
                : (header.isEmpty() ? parentSource : header + "\n" + parentSource);
        parents.add(parent);
    }

    /**
     * 把超长声明切成不超 size 的片段：优先空行段，其次单行（保留缩进）；
     * 单行仍超长时在空格处（后半窗口）断开，否则硬切。
     */
    private static List<String> splitByLines(String text, int size) {
        List<String> out = new ArrayList<>();
        for (String block : text.split("\\R\\s*\\R")) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;
            if (block.length() <= size) {
                out.add(block);
                continue;
            }
            for (String line : block.split("\\R")) {
                if (line.isBlank()) continue;
                if (line.length() <= size) {
                    out.add(line);
                } else {
                    out.addAll(hardSplitLine(line, size));
                }
            }
        }
        return out;
    }

    /** 单行硬切：窗口后半段内优先从最后一个空格断开，否则硬切。 */
    private static List<String> hardSplitLine(String line, int size) {
        List<String> out = new ArrayList<>();
        int start = 0;
        while (start < line.length()) {
            int end = Math.min(start + size, line.length());
            if (end < line.length()) {
                int minCut = start + Math.max(1, (end - start) / 2);
                for (int i = end - 1; i >= minCut; i--) {
                    if (line.charAt(i) == ' ') {
                        end = i;
                        break;
                    }
                }
            }
            out.add(line.substring(start, end));
            if (end == line.length()) break;
            // 切点落在空格上时（end 指向空格），下一段跳过该空格，避免丢失字符
            start = line.charAt(end) == ' ' ? end + 1 : end;
        }
        return out;
    }

    /** 从文本末尾取完整行，总长（含换行）不超过 maxChars；不足一行时返回空串。 */
    private static String tailLines(String text, int maxChars) {
        if (maxChars <= 0) return "";
        String[] lines = text.split("\\R", -1);
        List<String> tail = new ArrayList<>();
        int len = 0;
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i];
            int add = line.length() + (tail.isEmpty() ? 0 : 1);
            if (len + add > maxChars) break;
            len += add;
            tail.add(0, line);
        }
        return String.join("\n", tail);
    }
}
