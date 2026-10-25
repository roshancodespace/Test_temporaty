package com.example.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.SyntaxAnnotation
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxFunction
import com.example.ui.theme.SyntaxIdentifier
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxPunctuation
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.SyntaxType
import com.example.ui.theme.TextPrimary
import java.util.regex.Pattern

object SyntaxHighlighter {

    private val KOTLIN_KEYWORDS = setOf(
        "package", "import", "class", "interface", "fun", "val", "var", "data", "sealed",
        "object", "companion", "private", "protected", "public", "internal", "open", "override",
        "abstract", "final", "enum", "if", "else", "when", "for", "while", "do", "return",
        "break", "continue", "try", "catch", "finally", "throw", "this", "super", "null",
        "true", "false", "is", "as", "in", "by", "lazy", "lateinit", "suspend", "inline",
        "reified", "crossinline", "noinline", "typealias", "constructor", "init"
    )

    private val PYTHON_KEYWORDS = setOf(
        "def", "class", "import", "from", "as", "if", "elif", "else", "for", "while",
        "return", "yield", "break", "continue", "pass", "try", "except", "finally",
        "raise", "with", "in", "is", "not", "and", "or", "True", "False", "None",
        "lambda", "global", "nonlocal", "async", "await", "assert"
    )

    private val COMMON_TYPES = setOf(
        "String", "Int", "Boolean", "Float", "Double", "Long", "Short", "Byte", "Char",
        "Unit", "Any", "Nothing", "List", "Map", "Set", "Array", "StateFlow", "MutableStateFlow",
        "Flow", "CoroutineScope", "ViewModel", "Modifier", "Composable", "Column", "Row",
        "Box", "Text", "Button", "OutlinedTextField", "Icon", "Card", "Scaffold", "Surface",
        "File", "InputStream", "OutputStream", "Exception", "Result", "Pair"
    )

    fun highlight(code: String, fileExtension: String): AnnotatedString {
        if (code.isEmpty()) return AnnotatedString("")

        return when (fileExtension.lowercase()) {
            "kt", "kts", "java" -> highlightKotlin(code)
            "py" -> highlightPython(code)
            "json" -> highlightJson(code)
            "xml", "html" -> highlightXml(code)
            "md", "markdown" -> highlightMarkdown(code)
            else -> highlightGeneric(code)
        }
    }

    private fun highlightKotlin(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = TextPrimary), 0, code.length)

            // Multi-line and Single-line comments
            highlightPattern(code, "/\\*[\\s\\S]*?\\*/", SpanStyle(color = SyntaxComment))
            highlightPattern(code, "//[^\n]*", SpanStyle(color = SyntaxComment))

            // Annotations (e.g., @Composable)
            highlightPattern(code, "@[a-zA-Z_0-9]+", SpanStyle(color = SyntaxAnnotation))

            // Multi-line Strings ("""...""") and single-line Strings ("...")
            highlightPattern(code, "\"\"\"[\\s\\S]*?\"\"\"", SpanStyle(color = SyntaxString))
            highlightPattern(code, "\"[^\"\n\\\\]*(?:\\\\.[^\"\n\\\\]*)*\"", SpanStyle(color = SyntaxString))
            highlightPattern(code, "'[^'\n\\\\]*(?:\\\\.[^'\n\\\\]*)*'", SpanStyle(color = SyntaxString))

            // Numbers
            highlightPattern(code, "\\b\\d+[LfdDF]?\\b", SpanStyle(color = SyntaxNumber))

            // Keywords
            val wordPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b")
            val matcher = wordPattern.matcher(code)
            while (matcher.find()) {
                val start = matcher.start(1)
                val end = matcher.end(1)
                val word = matcher.group(1)

                // Check if inside comment or string already
                if (KOTLIN_KEYWORDS.contains(word)) {
                    addStyle(SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold), start, end)
                } else if (COMMON_TYPES.contains(word)) {
                    addStyle(SpanStyle(color = SyntaxType), start, end)
                }
            }

            // Function calls: foo(...)
            highlightPattern(code, "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*(?=\\()", SpanStyle(color = SyntaxFunction))
        }
    }

    private fun highlightPython(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = TextPrimary), 0, code.length)

            // Comments
            highlightPattern(code, "#[^\n]*", SpanStyle(color = SyntaxComment))

            // Strings
            highlightPattern(code, "\"\"\"[\\s\\S]*?\"\"\"", SpanStyle(color = SyntaxString))
            highlightPattern(code, "'''[\\s\\S]*?'''", SpanStyle(color = SyntaxString))
            highlightPattern(code, "\"[^\"\n\\\\]*(?:\\\\.[^\"\n\\\\]*)*\"", SpanStyle(color = SyntaxString))
            highlightPattern(code, "'[^'\n\\\\]*(?:\\\\.[^'\n\\\\]*)*'", SpanStyle(color = SyntaxString))

            // Numbers
            highlightPattern(code, "\\b\\d+(\\.\\d+)?\\b", SpanStyle(color = SyntaxNumber))

            // Decorators
            highlightPattern(code, "@[a-zA-Z_0-9]+", SpanStyle(color = SyntaxAnnotation))

            // Keywords
            val wordPattern = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b")
            val matcher = wordPattern.matcher(code)
            while (matcher.find()) {
                val word = matcher.group(1)
                if (PYTHON_KEYWORDS.contains(word)) {
                    addStyle(
                        SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.SemiBold),
                        matcher.start(1),
                        matcher.end(1)
                    )
                }
            }
        }
    }

    private fun highlightJson(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = TextPrimary), 0, code.length)

            // Keys
            highlightPattern(code, "\"[^\"]+\"\\s*(?=:)", SpanStyle(color = SyntaxIdentifier))
            // Values: Strings
            highlightPattern(code, "(?<=:)\\s*\"[^\"]*\"", SpanStyle(color = SyntaxString))
            // Numbers
            highlightPattern(code, "\\b-?\\d+(\\.\\d+)?([eE][+-]?\\d+)?\\b", SpanStyle(color = SyntaxNumber))
            // Literals
            highlightPattern(code, "\\b(true|false|null)\\b", SpanStyle(color = SyntaxKeyword))
        }
    }

    private fun highlightXml(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = TextPrimary), 0, code.length)

            // XML comments
            highlightPattern(code, "<!--[\\s\\S]*?-->", SpanStyle(color = SyntaxComment))

            // Tags: <tag or </tag>
            highlightPattern(code, "</?[a-zA-Z0-9_:\\-]+", SpanStyle(color = SyntaxKeyword))
            highlightPattern(code, "/?>", SpanStyle(color = SyntaxKeyword))

            // Attributes: android:name=
            highlightPattern(code, "[a-zA-Z0-9_:\\-]+(?=\\=)", SpanStyle(color = SyntaxIdentifier))

            // Attribute values: "..."
            highlightPattern(code, "\"[^\"\n]*\"", SpanStyle(color = SyntaxString))
        }
    }

    private fun highlightMarkdown(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = TextPrimary), 0, code.length)

            // Headings
            highlightPattern(code, "^#{1,6}\\s+[^\n]*", SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold))

            // Code spans
            highlightPattern(code, "`[^`\n]+`", SpanStyle(color = SyntaxString))

            // Bold
            highlightPattern(code, "\\*\\*[^*\\n]+\\*\\*", SpanStyle(fontWeight = FontWeight.Bold, color = SyntaxType))

            // List bullets
            highlightPattern(code, "^\\s*[-*+]\\s+", SpanStyle(color = SyntaxIdentifier))
        }
    }

    private fun highlightGeneric(code: String): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = TextPrimary), 0, code.length)
            highlightPattern(code, "//[^\n]*", SpanStyle(color = SyntaxComment))
            highlightPattern(code, "\"[^\"\n]*\"", SpanStyle(color = SyntaxString))
            highlightPattern(code, "\\b\\d+\\b", SpanStyle(color = SyntaxNumber))
        }
    }

    private fun AnnotatedString.Builder.highlightPattern(text: String, regex: String, style: SpanStyle) {
        try {
            val pattern = Pattern.compile(regex, Pattern.MULTILINE)
            val matcher = pattern.matcher(text)
            while (matcher.find()) {
                addStyle(style, matcher.start(), matcher.end())
            }
        } catch (_: Exception) {}
    }
}
