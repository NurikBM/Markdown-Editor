package com.markdown.editor.domain.syntax

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RegexCodeSyntaxTokenizerTest {

    private lateinit var tokenizer: RegexCodeSyntaxTokenizer

    @BeforeEach
    fun setUp() {
        tokenizer = RegexCodeSyntaxTokenizer()
    }

    @Test
    fun `tokenize empty code returns empty list`() {
        val tokens = tokenizer.tokenize("", "kotlin")
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `tokenize kotlin code identifies keywords strings numbers and comments`() {
        val code = """
            fun calculate(x: Int): String {
                // Return result
                val count = 42
                return "Result: "
            }
        """.trimIndent()

        val tokens = tokenizer.tokenize(code, "kotlin")
        assertTrue(tokens.isNotEmpty())

        val keywords = tokens.filter { it.type == SyntaxTokenType.KEYWORD }
        val comments = tokens.filter { it.type == SyntaxTokenType.COMMENT }
        val strings = tokens.filter { it.type == SyntaxTokenType.STRING_LITERAL }
        val numbers = tokens.filter { it.type == SyntaxTokenType.NUMBER_LITERAL }
        val types = tokens.filter { it.type == SyntaxTokenType.TYPE }

        assertTrue(keywords.any { code.substring(it.start, it.end) == "fun" })
        assertTrue(keywords.any { code.substring(it.start, it.end) == "val" })
        assertTrue(keywords.any { code.substring(it.start, it.end) == "return" })
        assertTrue(comments.any { code.substring(it.start, it.end) == "// Return result" })
        assertTrue(strings.any { code.substring(it.start, it.end) == "\"Result: \"" })
        assertTrue(numbers.any { code.substring(it.start, it.end) == "42" })
        assertTrue(types.any { code.substring(it.start, it.end) == "Int" })
        assertTrue(types.any { code.substring(it.start, it.end) == "String" })
    }

    @Test
    fun `keyword inside comment is not parsed as keyword`() {
        val code = "// val x = fun() return"
        val tokens = tokenizer.tokenize(code, "kotlin")

        assertEquals(1, tokens.size)
        assertEquals(SyntaxTokenType.COMMENT, tokens[0].type)
        assertEquals(code, code.substring(tokens[0].start, tokens[0].end))
    }

    @Test
    fun `keyword inside string is not parsed as keyword`() {
        val code = "val str = \"val fun return\""
        val tokens = tokenizer.tokenize(code, "kotlin")

        val keywords = tokens.filter { it.type == SyntaxTokenType.KEYWORD }
        assertEquals(1, keywords.size)
        assertEquals("val", code.substring(keywords[0].start, keywords[0].end))

        val strings = tokens.filter { it.type == SyntaxTokenType.STRING_LITERAL }
        assertEquals(1, strings.size)
        assertEquals("\"val fun return\"", code.substring(strings[0].start, strings[0].end))
    }

    @Test
    fun `tokenize python code identifies python keywords and hash comments`() {
        val code = """
            def greet(name):
                # Say hello to user
                return "Hello " + name
        """.trimIndent()

        val tokens = tokenizer.tokenize(code, "python")
        val keywords = tokens.filter { it.type == SyntaxTokenType.KEYWORD }
        val comments = tokens.filter { it.type == SyntaxTokenType.COMMENT }

        assertTrue(keywords.any { code.substring(it.start, it.end) == "def" })
        assertTrue(keywords.any { code.substring(it.start, it.end) == "return" })
        assertTrue(comments.any { code.substring(it.start, it.end) == "# Say hello to user" })
    }

    @Test
    fun `tokenize json identifies keys constants and numbers`() {
        val code = """
            {
                "id": 101,
                "active": true,
                "name": "Markdown"
            }
        """.trimIndent()

        val tokens = tokenizer.tokenize(code, "json")
        val keywords = tokens.filter { it.type == SyntaxTokenType.KEYWORD }
        val numbers = tokens.filter { it.type == SyntaxTokenType.NUMBER_LITERAL }
        val strings = tokens.filter { it.type == SyntaxTokenType.STRING_LITERAL }

        assertTrue(keywords.any { code.substring(it.start, it.end).contains("\"id\"") })
        assertTrue(keywords.any { code.substring(it.start, it.end) == "true" })
        assertTrue(numbers.any { code.substring(it.start, it.end) == "101" })
        assertTrue(strings.any { code.substring(it.start, it.end) == "\"Markdown\"" })
    }

    @Test
    fun `unknown language gracefully falls back to generic rules`() {
        val code = "function test() { return 100; }"
        val tokens = tokenizer.tokenize(code, "unknown-custom-lang")

        assertTrue(tokens.isNotEmpty())
        val keywords = tokens.filter { it.type == SyntaxTokenType.KEYWORD }
        assertTrue(keywords.any { code.substring(it.start, it.end) == "function" })
        assertTrue(keywords.any { code.substring(it.start, it.end) == "return" })
    }
}

