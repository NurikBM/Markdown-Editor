package com.markdown.editor.data.converter

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CsvToMarkdownConverterTest {

    private val converter = CsvToMarkdownConverter()

    @Test
    fun `convert parses comma separated values with quotes into Markdown table`() {
        val csvData = """
            Name,Role,"Description, Notes"
            Alice,Developer,"Frontend, Compose"
            Bob,Architect,"System, Backend"
        """.trimIndent()

        val result = converter.convert("Team.csv", csvData.byteInputStream())

        assertFalse(result.isBestEffort)
        assertTrue(result.title == "Team")
        assertTrue(result.markdownContent.contains("| Name | Role | Description, Notes |"))
        assertTrue(result.markdownContent.contains("| --- | --- | --- |"))
        assertTrue(result.markdownContent.contains("| Alice | Developer | Frontend, Compose |"))
        assertTrue(result.markdownContent.contains("| Bob | Architect | System, Backend |"))
    }

    @Test
    fun `convert parses tab separated values into Markdown table`() {
        val tsvData = "ColA\tColB\nVal1\tVal2"
        val result = converter.convert("Data.tsv", tsvData.byteInputStream())

        assertTrue(result.markdownContent.contains("| ColA | ColB |"))
        assertTrue(result.markdownContent.contains("| Val1 | Val2 |"))
    }
}

