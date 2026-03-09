package org.ethereumphone.andyclaw.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Column(modifier = modifier) {
        for (block in blocks) {
            when (block) {
                is MdBlock.CodeBlock -> {
                    Text(
                        text = block.code,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(codeBackground)
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState()),
                        color = color,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                        ),
                    )
                }
                is MdBlock.RichText -> {
                    Text(
                        text = block.annotated,
                        color = color,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

private sealed class MdBlock {
    data class CodeBlock(val language: String, val code: String) : MdBlock()
    data class RichText(val annotated: AnnotatedString) : MdBlock()
}

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var i = 0

    val richLines = mutableListOf<String>()

    fun flushRich() {
        if (richLines.isNotEmpty()) {
            blocks.add(MdBlock.RichText(parseRichLines(richLines)))
            richLines.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        if (line.startsWith("```")) {
            flushRich()
            val language = line.removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            if (i < lines.size) i++ // skip closing ```
            blocks.add(MdBlock.CodeBlock(language, codeLines.joinToString("\n")))
        } else {
            richLines.add(line)
            i++
        }
    }
    flushRich()
    return blocks
}

private fun parseRichLines(lines: List<String>): AnnotatedString {
    return buildAnnotatedString {
        for ((index, line) in lines.withIndex()) {
            when {
                line.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        appendInlineMarkdown(line.removePrefix("### "))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp)) {
                        appendInlineMarkdown(line.removePrefix("## "))
                    }
                }
                line.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
                        appendInlineMarkdown(line.removePrefix("# "))
                    }
                }
                line.startsWith("> ") -> {
                    append("  \u2502 ")
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(line.removePrefix("> "))
                    }
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    append("  \u2022 ")
                    appendInlineMarkdown(line.drop(2))
                }
                line.matches(Regex("^\\d+\\.\\s.*")) -> {
                    val num = line.substringBefore(".")
                    val content = line.substringAfter(". ")
                    append("  $num. ")
                    appendInlineMarkdown(content)
                }
                else -> appendInlineMarkdown(line)
            }
            if (index < lines.lastIndex) append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            // Bold italic ***text***
            text.startsWith("***", i) -> {
                val end = text.indexOf("***", i + 3)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 3
                } else {
                    append(text[i])
                    i++
                }
            }
            // Bold **text**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineMarkdown(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Bold __text__
            text.startsWith("__", i) -> {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineMarkdown(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Strikethrough ~~text~~
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    // No native strikethrough in SpanStyle, just render the text
                    append(text.substring(i + 2, end))
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            // Inline code `text`
            text.startsWith("`", i) -> {
                val end = text.indexOf("`", i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Link [text](url) — display just the text
            text.startsWith("[", i) -> {
                val closeBracket = text.indexOf("]", i + 1)
                if (closeBracket != -1 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                    val closeParen = text.indexOf(")", closeBracket + 2)
                    if (closeParen != -1) {
                        val linkText = text.substring(i + 1, closeBracket)
                        appendInlineMarkdown(linkText)
                        i = closeParen + 1
                    } else {
                        append(text[i])
                        i++
                    }
                } else {
                    append(text[i])
                    i++
                }
            }
            // Italic *text* (but not **)
            text.startsWith("*", i) && !text.startsWith("**", i) -> {
                val end = text.indexOf("*", i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            // Italic _text_ (but not __)
            text.startsWith("_", i) && !text.startsWith("__", i) -> {
                val end = findSingleUnderscoreEnd(text, i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}

/** Find the closing single `_` that is not part of `__`. */
private fun findSingleUnderscoreEnd(text: String, start: Int): Int {
    var j = start
    while (j < text.length) {
        if (text[j] == '_') {
            // Make sure this is a single underscore, not a double
            val isDouble = (j + 1 < text.length && text[j + 1] == '_')
            if (!isDouble) return j
            j += 2 // skip the double underscore
        } else {
            j++
        }
    }
    return -1
}
