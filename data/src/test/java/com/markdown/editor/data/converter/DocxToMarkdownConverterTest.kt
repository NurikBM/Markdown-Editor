package com.markdown.editor.data.converter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocxToMarkdownConverterTest {

    private val converter = DocxToMarkdownConverter()

    @Test
    fun `convert parses headings paragraphs bold italic and tables from docx xml`() {
        val documentXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                <w:body>
                    <w:p>
                        <w:pPr>
                            <w:pStyle w:val="Heading1"/>
                        </w:pPr>
                        <w:r><w:t>Document Title</w:t></w:r>
                    </w:p>
                    <w:p>
                        <w:r>
                            <w:rPr><w:b/></w:rPr>
                            <w:t>Bold Text</w:t>
                        </w:r>
                        <w:r><w:t> and </w:t></w:r>
                        <w:r>
                            <w:rPr><w:i/></w:rPr>
                            <w:t>Italic Text</w:t>
                        </w:r>
                    </w:p>
                    <w:tbl>
                        <w:tr>
                            <w:tc><w:p><w:r><w:t>Header 1</w:t></w:r></w:p></w:tc>
                            <w:tc><w:p><w:r><w:t>Header 2</w:t></w:r></w:p></w:tc>
                        </w:tr>
                        <w:tr>
                            <w:tc><w:p><w:r><w:t>Value 1</w:t></w:r></w:p></w:tc>
                            <w:tc><w:p><w:r><w:t>Value 2</w:t></w:r></w:p></w:tc>
                        </w:tr>
                    </w:tbl>
                </w:body>
            </w:document>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("word/document.xml"))
            zos.write(documentXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = converter.convert("MyReport.docx", baos.toByteArray().inputStream())

        assertFalse(result.isBestEffort)
        assertTrue(result.title == "MyReport")
        assertTrue(result.markdownContent.contains("# Document Title"))
        assertTrue(result.markdownContent.contains("**Bold Text** and *Italic Text*"))
        assertTrue(result.markdownContent.contains("| Header 1 | Header 2 |"))
        assertTrue(result.markdownContent.contains("| --- | --- |"))
        assertTrue(result.markdownContent.contains("| Value 1 | Value 2 |"))
    }
}

