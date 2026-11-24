package com.collect.ai.rag.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AstChunkerTest {

    private static final String Q3 = "\"\"\""; // 三个双引号（Java 文本块定界符）

    // ------------------------------------------------------------------
    // splitDeclarations：顶层声明切分（词法感知）
    // ------------------------------------------------------------------

    @Test
    void splitDeclarations_shouldSplitJavaTopLevelDeclarations() {
        String code = "package com.example;\n\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n\n"
                + "class A {\n    int x = 1;\n}\n\n"
                + "class B {\n    int y = 2;\n}\n";
        List<String> units = AstChunker.splitDeclarations(code);
        assertEquals(5, units.size());
        assertEquals("package com.example;", units.get(0));
        assertEquals("import java.util.List;", units.get(1));
        assertEquals("class A {\n    int x = 1;\n}", units.get(3));
        assertEquals("class B {\n    int y = 2;\n}", units.get(4));
    }

    @Test
    void splitDeclarations_shouldIgnoreBracesInStringsCommentsAndTextBlocks() {
        String code = "package com.example;\n\n"
                + "class A {\n"
                + "    String s = \"}{ ;\";\n"
                + "    String tb = " + Q3 + "\n        { } ;\n" + Q3 + ";\n"
                + "    // comment } { ;\n"
                + "    /* block } { ; */\n"
                + "    int x = 1;\n"
                + "}\n";
        List<String> units = AstChunker.splitDeclarations(code);
        assertEquals(2, units.size());
        String unit = units.get(1);
        assertTrue(unit.startsWith("class A {"));
        assertTrue(unit.contains("}{ ;"), "字符串内容应保留在单元内");
        assertTrue(unit.contains("{ } ;"), "文本块内容应保留在单元内");
        assertTrue(unit.endsWith("}"));
    }

    @Test
    void splitDeclarations_shouldHandleJsTemplateLiteralWithObjectInInterpolation() {
        String code = "const s = `x ${ {a: 1} } y`;\nfunction f() { return s; }";
        List<String> units = AstChunker.splitDeclarations(code);
        assertEquals(2, units.size());
        assertEquals("const s = `x ${ {a: 1} } y`;", units.get(0));
        assertEquals("function f() { return s; }", units.get(1));
    }

    @Test
    void splitDeclarations_shouldTreatObjectAndDictLiteralsNotBlocks() {
        // JS 对象字面量：顶层 "}" 不应成为声明边界
        List<String> js = AstChunker.splitDeclarations(
                "const o = { a: 1, b: { c: 2 } };\nfunction f() { return o; }");
        assertEquals(2, js.size());
        assertEquals("const o = { a: 1, b: { c: 2 } };", js.get(0));

        // Python 字典字面量同理
        List<String> py = AstChunker.splitDeclarations("x = {1: 2}\ny = 3\n");
        assertEquals(1, py.size());
        assertTrue(py.get(0).contains("x = {1: 2}"));
    }

    @Test
    void splitDeclarations_shouldDropPunctuationOnlyUnits() {
        // C 结构体 "struct X {...};" 切出的孤立 ";" 应被丢弃
        String code = "struct Point { int x; int y; };\nint main() { return 0; }";
        List<String> units = AstChunker.splitDeclarations(code);
        assertEquals(2, units.size());
        assertEquals("struct Point { int x; int y; }", units.get(0));
        assertEquals("int main() { return 0; }", units.get(1));
    }

    @Test
    void splitDeclarations_shouldFallbackToBlankLinesForPython() {
        String code = "def a():\n    x = {1: 2}\n    return x\n\n\ndef b():\n    return 2\n\nprint(a())\n";
        List<String> units = AstChunker.splitDeclarations(code);
        assertEquals(3, units.size());
        assertTrue(units.get(0).startsWith("def a():"));
        assertTrue(units.get(0).contains("x = {1: 2}"), "字典行应保留在函数块内");
        assertEquals("def b():\n    return 2", units.get(1));
        assertEquals("print(a())", units.get(2));
    }

    // ------------------------------------------------------------------
    // isPreamble：前言识别
    // ------------------------------------------------------------------

    @Test
    void isPreamble_shouldDetectCommonPreambleForms() {
        assertTrue(AstChunker.isPreamble("package com.example;"));
        assertTrue(AstChunker.isPreamble("import java.util.List;"));
        assertTrue(AstChunker.isPreamble("using System;"));
        assertTrue(AstChunker.isPreamble("namespace Foo {"));
        assertTrue(AstChunker.isPreamble("#include <stdio.h>"));
        assertTrue(AstChunker.isPreamble("from os import path"));
        assertTrue(AstChunker.isPreamble("require('fs')"));
        assertTrue(AstChunker.isPreamble("use std::io;"));

        assertFalse(AstChunker.isPreamble("class A {"));
        assertFalse(AstChunker.isPreamble("const x = 1;"));
        assertFalse(AstChunker.isPreamble("print('hi')"));
    }

    // ------------------------------------------------------------------
    // chunk：合并、上下文头、尺寸约束
    // ------------------------------------------------------------------

    private static final String JAVA_SOURCE = "package com.example;\n\n"
            + "import java.util.List;\n"
            + "import java.util.Map;\n\n"
            + "class A {\n"
            + "    void m1() {\n        String s = \"}{ ;\";\n    }\n"
            + "}\n\n"
            + "class B {\n    int x = 1;\n}\n";

    @Test
    void chunk_shouldMergeIntoSingleSelfContainedChunkWhenSizeAllows() {
        AstChunker.Result result = AstChunker.chunk(JAVA_SOURCE, 1200, 0);
        assertEquals(1, result.chunks().size());
        String chunk = result.chunks().get(0);
        assertTrue(chunk.startsWith("package com.example;"), "上下文头应前置");
        assertTrue(chunk.contains("import java.util.List;"));
        assertTrue(chunk.contains("class A {"));
        assertTrue(chunk.contains("class B {"));
        assertEquals(1, result.parents().size());
        assertEquals(chunk, result.parents().get(0), "无拆分声明时父文本 = chunk 本身");
    }

    @Test
    void chunk_shouldKeepEveryChunkWithinSizeAndRepeatHeader() {
        // chunkSize 90：上下文头按行截断（min(500, 90/4)=22 字符，仅 package 行 + "…"）
        AstChunker.Result result = AstChunker.chunk(JAVA_SOURCE, 90, 0);
        assertTrue(result.chunks().size() >= 2, "应切出多个 chunk: " + result.chunks().size());
        for (String chunk : result.chunks()) {
            assertTrue(chunk.length() <= 90, "chunk 超长: " + chunk.length());
            assertTrue(chunk.startsWith("package com.example;"), "每个 chunk 都应携带上下文头");
        }
        assertEquals(result.chunks().size(), result.parents().size());
    }

    // ------------------------------------------------------------------
    // chunk：超长声明切行 + 父块（small-to-big）
    // ------------------------------------------------------------------

    private static String bigFunction() {
        StringBuilder sb = new StringBuilder("function big() {\n    let s = \"\";\n");
        for (int i = 1; i <= 60; i++) {
            sb.append(String.format("    s += \"line%02d\";\n", i));
        }
        sb.append("    return s;\n}\n");
        return sb.toString();
    }

    @Test
    void chunk_shouldSplitOversizedDeclarationAndUseFullDeclarationAsParent() {
        AstChunker.Result result = AstChunker.chunk(bigFunction(), 400, 0);
        String full = bigFunction().trim();
        assertTrue(result.chunks().size() >= 2, "超长声明应切成多个 chunk");
        for (String chunk : result.chunks()) {
            assertTrue(chunk.length() <= 400, "chunk 超长: " + chunk.length());
        }
        // 每个 chunk 都来自被切行的声明 → 父文本 = 完整声明
        for (String parent : result.parents()) {
            assertEquals(full, parent, "拆分声明的父文本应为完整声明");
        }
        assertTrue(result.parents().get(0).length() > result.chunks().get(0).length());
    }

    // ------------------------------------------------------------------
    // chunk：overlap 回带
    // ------------------------------------------------------------------

    @Test
    void chunk_shouldCarryTailLinesAsOverlap() {
        String code = "function u1() {\n    const a = 1;\n    const b = 2;\n    return a + b;\n}\n"
                + "function u2() {\n    const c = 3;\n    return c;\n}\n";
        AstChunker.Result result = AstChunker.chunk(code, 100, 50);
        assertEquals(2, result.chunks().size());
        String first = result.chunks().get(0);
        String second = result.chunks().get(1);
        assertEquals("function u1() {\n    const a = 1;\n    const b = 2;\n    return a + b;\n}", first);
        assertTrue(second.contains("function u2() {"));
        assertTrue(second.contains("return a + b;"), "第二块应回带第一块末尾行");
        assertTrue(second.indexOf("return a + b;") < second.indexOf("function u2() {"),
                "回带内容应位于新声明之前");
    }

    @Test
    void chunk_shouldReturnEmptyForBlankInput() {
        assertTrue(AstChunker.chunk("", 1000, 0).chunks().isEmpty());
        assertTrue(AstChunker.chunk("   \n  ", 1000, 0).chunks().isEmpty());
    }
}
