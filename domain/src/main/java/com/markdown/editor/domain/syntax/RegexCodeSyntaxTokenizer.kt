package com.markdown.editor.domain.syntax

/**
 * High-performance, pure Kotlin regex-based tokenization engine for fenced code blocks.
 *
 * Implements lexical scanning using prioritized pattern matching with conflict avoidance
 * (e.g. comments and strings take precedence over keywords and numbers).
 */
class RegexCodeSyntaxTokenizer : CodeSyntaxTokenizer {

    override fun tokenize(code: String, language: String?): List<SyntaxToken> {
        if (code.isEmpty()) return emptyList()

        val normalizedLang = language?.trim()?.lowercase() ?: ""
        val rules = getRulesForLanguage(normalizedLang)

        val occupied = BooleanArray(code.length)
        val tokens = mutableListOf<SyntaxToken>()

        for (rule in rules) {
            applyRule(rule, code, occupied, tokens)
        }

        tokens.sortBy { it.start }
        return tokens
    }

    private fun isRangeAvailable(occupied: BooleanArray, start: Int, end: Int): Boolean {
        for (i in start until end) {
            if (occupied[i]) return false
        }
        return true
    }

    private fun markOccupied(occupied: BooleanArray, start: Int, end: Int) {
        for (i in start until end) {
            occupied[i] = true
        }
    }

    private fun applyRule(
        rule: SyntaxRule,
        code: String,
        occupied: BooleanArray,
        tokens: MutableList<SyntaxToken>
    ) {
        for (match in rule.regex.findAll(code)) {
            val start = match.range.first
            val end = match.range.last + 1
            if (start in 0 until code.length && end <= code.length && start < end && isRangeAvailable(occupied, start, end)) {
                markOccupied(occupied, start, end)
                tokens.add(SyntaxToken(type = rule.tokenType, start = start, end = end))
            }
        }
    }

    private fun getRulesForLanguage(language: String): List<SyntaxRule> {
        return when (language) {
            "kotlin", "kt", "kts" -> kotlinRules
            "java" -> javaRules
            "python", "py" -> pythonRules
            "javascript", "js", "typescript", "ts", "jsx", "tsx" -> jsTsRules
            "json" -> jsonRules
            "sql" -> sqlRules
            "html", "xml" -> xmlRules
            "markdown", "md" -> markdownRules
            else -> genericRules
        }
    }

    private data class SyntaxRule(
        val tokenType: SyntaxTokenType,
        val regex: Regex
    )

    companion object {
        // Common reusable patterns
        private val LINE_COMMENT = Regex("""//.*""")
        private val BLOCK_COMMENT = Regex("""/\*[\s\S]*?\*/""")
        private val HASH_COMMENT = Regex("""#.*""")
        private val XML_COMMENT = Regex("""<!--[\s\S]*?-->""")

        private val TRIPLE_QUOTE_STRING = Regex("\"\"\"[\\s\\S]*?\"\"\"")
        private val DOUBLE_QUOTE_STRING = Regex("\"(?:[^\"\\\\]|\\\\.)*\"")
        private val SINGLE_QUOTE_STRING = Regex("'(?:[^'\\\\]|\\\\.)*'")
        private val BACKTICK_STRING = Regex("`(?:[^`\\\\]|\\\\.)*`")

        private val NUMBER_LITERAL = Regex("""\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\d+[\d_]*(?:\.[\d_]+)?(?:[eE][+-]?\d+)?(?:[fFLluU]|ms|s)?)\b""")
        private val FUNCTION_CALL = Regex("""\b([a-zA-Z_][a-zA-Z0-9_]*)\s*(?=\()""")
        private val PASCAL_CASE_TYPE = Regex("""\b[A-Z][a-zA-Z0-9_]*\b""")

        // Kotlin rules
        private val KOTLIN_KEYWORDS = setOf(
            "package", "import", "class", "interface", "object", "val", "var", "fun",
            "return", "if", "else", "when", "for", "while", "do", "try", "catch", "finally",
            "throw", "is", "as", "in", "sealed", "data", "enum", "override", "private",
            "protected", "public", "internal", "abstract", "final", "open", "companion",
            "suspend", "inline", "tailrec", "operator", "infix", "typealias", "this", "super",
            "null", "true", "false", "it", "by", "lazy", "init", "constructor"
        ).joinToString("|")

        private val kotlinRules = listOf(
            SyntaxRule(SyntaxTokenType.COMMENT, TRIPLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.COMMENT, BLOCK_COMMENT),
            SyntaxRule(SyntaxTokenType.COMMENT, LINE_COMMENT),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, DOUBLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, SINGLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.KEYWORD, Regex("""\b($KOTLIN_KEYWORDS)\b""")),
            SyntaxRule(SyntaxTokenType.FUNCTION, FUNCTION_CALL),
            SyntaxRule(SyntaxTokenType.TYPE, PASCAL_CASE_TYPE),
            SyntaxRule(SyntaxTokenType.NUMBER_LITERAL, NUMBER_LITERAL)
        )

        // Java rules
        private val JAVA_KEYWORDS = setOf(
            "package", "import", "public", "private", "protected", "class", "interface",
            "enum", "record", "extends", "implements", "void", "return", "if", "else",
            "for", "while", "do", "switch", "case", "break", "continue", "default", "new",
            "this", "super", "try", "catch", "finally", "throw", "throws", "static", "final",
            "abstract", "synchronized", "volatile", "transient", "native", "instanceof",
            "assert", "null", "true", "false", "int", "boolean", "long", "float", "double",
            "char", "byte", "short"
        ).joinToString("|")

        private val javaRules = listOf(
            SyntaxRule(SyntaxTokenType.COMMENT, BLOCK_COMMENT),
            SyntaxRule(SyntaxTokenType.COMMENT, LINE_COMMENT),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, DOUBLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, SINGLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.KEYWORD, Regex("""\b($JAVA_KEYWORDS)\b""")),
            SyntaxRule(SyntaxTokenType.FUNCTION, FUNCTION_CALL),
            SyntaxRule(SyntaxTokenType.TYPE, PASCAL_CASE_TYPE),
            SyntaxRule(SyntaxTokenType.NUMBER_LITERAL, NUMBER_LITERAL)
        )

        // Python rules
        private val PYTHON_KEYWORDS = setOf(
            "def", "class", "import", "from", "as", "return", "if", "elif", "else",
            "for", "while", "break", "continue", "try", "except", "finally", "raise",
            "with", "yield", "lambda", "pass", "global", "nonlocal", "assert", "async",
            "await", "and", "or", "not", "in", "is", "True", "False", "None", "self"
        ).joinToString("|")

        private val pythonRules = listOf(
            SyntaxRule(SyntaxTokenType.COMMENT, HASH_COMMENT),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, TRIPLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, DOUBLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, SINGLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.KEYWORD, Regex("""\b($PYTHON_KEYWORDS)\b""")),
            SyntaxRule(SyntaxTokenType.FUNCTION, FUNCTION_CALL),
            SyntaxRule(SyntaxTokenType.TYPE, PASCAL_CASE_TYPE),
            SyntaxRule(SyntaxTokenType.NUMBER_LITERAL, NUMBER_LITERAL)
        )

        // JS/TS rules
        private val JS_TS_KEYWORDS = setOf(
            "function", "const", "let", "var", "class", "interface", "type", "enum",
            "return", "if", "else", "for", "while", "do", "switch", "case", "break",
            "continue", "default", "new", "this", "super", "try", "catch", "finally",
            "throw", "import", "export", "from", "as", "async", "await", "yield", "typeof",
            "instanceof", "null", "undefined", "true", "false", "void", "any", "unknown",
            "never", "keyof", "readonly"
        ).joinToString("|")

        private val jsTsRules = listOf(
            SyntaxRule(SyntaxTokenType.COMMENT, BLOCK_COMMENT),
            SyntaxRule(SyntaxTokenType.COMMENT, LINE_COMMENT),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, BACKTICK_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, DOUBLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, SINGLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.KEYWORD, Regex("""\b($JS_TS_KEYWORDS)\b""")),
            SyntaxRule(SyntaxTokenType.FUNCTION, FUNCTION_CALL),
            SyntaxRule(SyntaxTokenType.TYPE, PASCAL_CASE_TYPE),
            SyntaxRule(SyntaxTokenType.NUMBER_LITERAL, NUMBER_LITERAL)
        )

        // JSON rules
        private val JSON_KEY = Regex(""""(?:[^"\\]|\\.)*"\s*(?=:)""")
        private val JSON_CONSTANTS = Regex("""\b(true|false|null)\b""")

        private val jsonRules = listOf(
            SyntaxRule(SyntaxTokenType.KEYWORD, JSON_KEY),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, DOUBLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.KEYWORD, JSON_CONSTANTS),
            SyntaxRule(SyntaxTokenType.NUMBER_LITERAL, NUMBER_LITERAL)
        )

        // SQL rules
        private val SQL_KEYWORDS = setOf(
            "select", "from", "where", "insert", "into", "values", "update", "set",
            "delete", "create", "table", "alter", "drop", "index", "join", "inner",
            "left", "right", "outer", "full", "on", "group", "by", "order", "having",
            "limit", "offset", "and", "or", "not", "in", "is", "null", "like", "as",
            "primary", "key", "foreign", "references", "default", "constraint", "case",
            "when", "then", "else", "end", "distinct", "union", "all"
        ).joinToString("|")

        private val sqlRules = listOf(
            SyntaxRule(SyntaxTokenType.COMMENT, Regex("""--.*""")),
            SyntaxRule(SyntaxTokenType.COMMENT, BLOCK_COMMENT),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, SINGLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, DOUBLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.KEYWORD, Regex("""(?i)\b($SQL_KEYWORDS)\b""")),
            SyntaxRule(SyntaxTokenType.FUNCTION, FUNCTION_CALL),
            SyntaxRule(SyntaxTokenType.NUMBER_LITERAL, NUMBER_LITERAL)
        )

        // XML / HTML rules
        private val xmlRules = listOf(
            SyntaxRule(SyntaxTokenType.COMMENT, XML_COMMENT),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, DOUBLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, SINGLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.KEYWORD, Regex("""</?[a-zA-Z0-9\-:]+""")),
            SyntaxRule(SyntaxTokenType.TYPE, Regex("""[a-zA-Z0-9\-:]+(?=\=)"""))
        )

        // Markdown rules
        private val markdownRules = listOf(
            SyntaxRule(SyntaxTokenType.KEYWORD, Regex("""^#{1,6}\s+.*$""", RegexOption.MULTILINE)),
            SyntaxRule(SyntaxTokenType.COMMENT, XML_COMMENT),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, Regex("""`[^`\n]+`""")),
            SyntaxRule(SyntaxTokenType.FUNCTION, Regex("""\[.+?\]\(.+?\)""")),
            SyntaxRule(SyntaxTokenType.TYPE, Regex("""^>\s+.*$""", RegexOption.MULTILINE))
        )

        // Generic fallback rules
        private val GENERIC_KEYWORDS = setOf(
            "function", "def", "fn", "var", "val", "let", "const", "class", "struct",
            "interface", "return", "if", "else", "for", "while", "do", "switch", "case",
            "break", "continue", "try", "catch", "throw", "import", "export", "true",
            "false", "null", "nil"
        ).joinToString("|")

        private val genericRules = listOf(
            SyntaxRule(SyntaxTokenType.COMMENT, BLOCK_COMMENT),
            SyntaxRule(SyntaxTokenType.COMMENT, LINE_COMMENT),
            SyntaxRule(SyntaxTokenType.COMMENT, HASH_COMMENT),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, DOUBLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.STRING_LITERAL, SINGLE_QUOTE_STRING),
            SyntaxRule(SyntaxTokenType.KEYWORD, Regex("""\b($GENERIC_KEYWORDS)\b""")),
            SyntaxRule(SyntaxTokenType.FUNCTION, FUNCTION_CALL),
            SyntaxRule(SyntaxTokenType.TYPE, PASCAL_CASE_TYPE),
            SyntaxRule(SyntaxTokenType.NUMBER_LITERAL, NUMBER_LITERAL)
        )
    }
}
