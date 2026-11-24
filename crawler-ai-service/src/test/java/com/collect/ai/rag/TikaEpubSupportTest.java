package com.collect.ai.rag;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证上传文档链路（RagService.extractTextWithTika 同款 Tika 配置）可解析 EPUB：
 * 1. 裸流 + 空 Metadata 时，ZipContainerDetector 依据包内 mimetype 条目识别 application/epub+zip；
 * 2. 预置 .epub 扩展名对应的 Content-Type 时，EpubParser 同样生效（兜底非标准包）。
 */
class TikaEpubSupportTest {

    private static final String CH1_TEXT = "这是第一章的正文内容，用于验证 EPUB 解析。";
    private static final String CH2_TEXT = "这是第二章的正文内容，用于验证章节顺序。";

    @Test
    void tikaShouldExtractEpubTextFromRawStream() throws Exception {
        byte[] epub = buildMinimalEpub();
        Metadata metadata = new Metadata();
        String text = parseWithTika(epub, metadata);

        assertTrue(text.contains(CH1_TEXT), "应提取出第一章正文，实际: " + abbreviate(text));
        assertTrue(text.contains(CH2_TEXT), "应提取出第二章正文，实际: " + abbreviate(text));
        assertTrue(text.indexOf(CH1_TEXT) < text.indexOf(CH2_TEXT), "章节顺序应遵循 spine");
        assertEquals("application/epub+zip", metadata.get(Metadata.CONTENT_TYPE),
                "裸流检测应识别为 EPUB");
    }

    @Test
    void tikaShouldExtractEpubWhenContentTypeIsPreSet() throws Exception {
        byte[] epub = buildMinimalEpub();
        Metadata metadata = new Metadata();
        // RagService 对 .epub 扩展名预置该 Content-Type 的兜底路径
        metadata.set(Metadata.CONTENT_TYPE, "application/epub+zip");
        String text = parseWithTika(epub, metadata);

        assertTrue(text.contains(CH1_TEXT));
        assertTrue(text.contains(CH2_TEXT));
    }

    private static String parseWithTika(byte[] bytes, Metadata metadata) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        // 与 RagService.extractTextWithTika 保持同款的解析器构造方式
        AutoDetectParser parser = new AutoDetectParser();
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            parser.parse(input, handler, metadata, new ParseContext());
        }
        return handler.toString();
    }

    /** 最小 EPUB 2.0：mimetype + container.xml + OPF（两章，spine 顺序 ch1 → ch2） */
    private static byte[] buildMinimalEpub() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            // EPUB 规范：mimetype 必须是首个条目且内容为 application/epub+zip
            putEntry(zip, "mimetype", "application/epub+zip");
            putEntry(zip, "META-INF/container.xml",
                    """
                    <?xml version="1.0"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/book.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """);
            putEntry(zip, "OEBPS/book.opf",
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:title>测试书</dc:title>
                        <dc:language>zh</dc:language>
                        <meta name="dcterms:identifier" id="uid">test-epub-1</meta>
                      </metadata>
                      <manifest>
                        <item id="ch1" href="ch1.html" media-type="application/xhtml+xml"/>
                        <item id="ch2" href="ch2.html" media-type="application/xhtml+xml"/>
                      </manifest>
                      <spine>
                        <itemref idref="ch1"/>
                        <itemref idref="ch2"/>
                      </spine>
                    </package>
                    """);
            putEntry(zip, "OEBPS/ch1.html",
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>第一章</title></head>
                      <body><h1>第一章</h1><p>%s</p></body>
                    </html>
                    """.formatted(CH1_TEXT));
            putEntry(zip, "OEBPS/ch2.html",
                    """
                    <?xml version="1.0" encoding="utf-8"?>
                    <html xmlns="http://www.w3.org/1999/xhtml">
                      <head><title>第二章</title></head>
                      <body><h1>第二章</h1><p>%s</p></body>
                    </html>
                    """.formatted(CH2_TEXT));
        }
        return buffer.toByteArray();
    }

    private static void putEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String abbreviate(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }
}
