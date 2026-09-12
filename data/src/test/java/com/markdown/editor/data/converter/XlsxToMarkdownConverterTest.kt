package com.markdown.editor.data.converter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxToMarkdownConverterTest {

    private val converter = XlsxToMarkdownConverter()

    @Test
    fun `convert parses shared strings and sheet data into Markdown table`() {
        val sharedStringsXml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
                <si><t>Item</t></si>
                <si><t>Price</t></si>
                <si><t>Apple</t></si>
                <si><t>Banana</t></si>
            </sst>
        """.trimIndent()

        val sheet1Xml = """
            <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                    <row r="1">
                        <c r="A1" t="s"><v>0</v></c>
                        <c r="B1" t="s"><v>1</v></c>
                    </row>
                    <row r="2">
                        <c r="A2" t="s"><v>2</v></c>
                        <c r="B2"><v>1.50</v></c>
                    </row>
                    <row r="3">
                        <c r="A3" t="s"><v>3</v></c>
                        <c r="B3"><v>0.80</v></c>
                    </row>
                </sheetData>
            </worksheet>
        """.trimIndent()

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("xl/sharedStrings.xml"))
            zos.write(sharedStringsXml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(sheet1Xml.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = converter.convert("Groceries.xlsx", baos.toByteArray().inputStream())

        assertFalse(result.isBestEffort)
        assertTrue(result.title == "Groceries")
        assertTrue(result.markdownContent.contains("| Item | Price |"))
        assertTrue(result.markdownContent.contains("| --- | --- |"))
        assertTrue(result.markdownContent.contains("| Apple | 1.50 |"))
        assertTrue(result.markdownContent.contains("| Banana | 0.80 |"))
    }
}

