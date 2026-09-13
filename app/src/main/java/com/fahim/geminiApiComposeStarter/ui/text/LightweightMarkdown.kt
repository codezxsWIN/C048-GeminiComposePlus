package com.fahim.geminiApiComposeStarter.ui.text

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Bullets(val items: List<String>, val numbered: Boolean) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val language: String, val text: String) : MarkdownBlock
    data object Rule : MarkdownBlock
}

/** A dependency-free, memoized Markdown subset tuned for model responses. */
@Composable
fun LightweightMarkdown(markdown: String, modifier: Modifier = Modifier) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    SelectionContainer {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            blocks.forEach { block ->
                when (block) {
                    is MarkdownBlock.Heading -> InlineMarkdownText(
                        block.text,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            else -> MaterialTheme.typography.titleMedium
                        },
                        fontWeight = FontWeight.Bold,
                    )
                    is MarkdownBlock.Paragraph -> InlineMarkdownText(block.text)
                    is MarkdownBlock.Quote -> Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        InlineMarkdownText(
                            block.text,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    is MarkdownBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        block.items.forEachIndexed { index, item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text(if (block.numbered) "${index + 1}." else "•", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(8.dp))
                                InlineMarkdownText(item, Modifier.weight(1f))
                            }
                        }
                    }
                    is MarkdownBlock.Code -> CodeBlock(block.language, block.text)
                    MarkdownBlock.Rule -> HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(language.ifBlank { "code" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                    Icon(Icons.Default.ContentCopy, "Copy code")
                }
            }
            Text(
                code,
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InlineMarkdownText(
    value: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(value, linkColor) { inlineMarkdown(value, linkColor) }
    val uriHandler = LocalUriHandler.current
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = fontWeight, fontStyle = fontStyle),
        onClick = { offset ->
            annotated.getStringAnnotations("URL", offset, offset).firstOrNull()?.let { link ->
                runCatching { uriHandler.openUri(link.item) }
            }
        },
    )
}

private fun inlineMarkdown(value: String, linkColor: androidx.compose.ui.graphics.Color): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < value.length) {
        when {
            value.startsWith("**", index) -> {
                val end = value.indexOf("**", index + 2)
                if (end > index) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append(value.substring(index + 2, end)); pop()
                    index = end + 2
                } else { append(value[index]); index++ }
            }
            value[index] == '`' -> {
                val end = value.indexOf('`', index + 1)
                if (end > index) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = linkColor.copy(alpha = .10f)))
                    append(value.substring(index + 1, end)); pop(); index = end + 1
                } else { append(value[index]); index++ }
            }
            value[index] == '[' -> {
                val labelEnd = value.indexOf(']', index + 1)
                val urlStart = if (labelEnd >= 0 && labelEnd + 1 < value.length && value[labelEnd + 1] == '(') labelEnd + 2 else -1
                val urlEnd = if (urlStart >= 0) value.indexOf(')', urlStart) else -1
                if (urlEnd > urlStart) {
                    val url = value.substring(urlStart, urlEnd)
                    pushStringAnnotation("URL", url)
                    pushStyle(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
                    append(value.substring(index + 1, labelEnd)); pop(); pop()
                    index = urlEnd + 1
                } else { append(value[index]); index++ }
            }
            value[index] == '*' -> {
                val end = value.indexOf('*', index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(value.substring(index + 1, end)); pop()
                    index = end + 1
                } else { append(value[index]); index++ }
            }
            else -> { append(value[index]); index++ }
        }
    }
}

private fun parseBlocks(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").lines()
    val result = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        when {
            line.isBlank() -> index++
            line.trimStart().startsWith("```") -> {
                val language = line.trim().removePrefix("```").trim()
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) code += lines[index++]
                if (index < lines.size) index++
                result += MarkdownBlock.Code(language, code.joinToString("\n"))
            }
            Regex("^#{1,3}\\s+").containsMatchIn(line) -> {
                val marks = line.takeWhile { it == '#' }.length
                result += MarkdownBlock.Heading(marks, line.drop(marks).trim())
                index++
            }
            line.trim() in setOf("---", "***", "___") -> { result += MarkdownBlock.Rule; index++ }
            line.trimStart().startsWith("> ") -> {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quote += lines[index++].trimStart().removePrefix(">").trimStart()
                }
                result += MarkdownBlock.Quote(quote.joinToString("\n"))
            }
            line.trimStart().let { it.startsWith("- ") || it.startsWith("* ") } -> {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val item = lines[index].trimStart()
                    if (!item.startsWith("- ") && !item.startsWith("* ")) break
                    items += item.drop(2); index++
                }
                result += MarkdownBlock.Bullets(items, false)
            }
            Regex("^\\s*\\d+[.)]\\s+").containsMatchIn(line) -> {
                val items = mutableListOf<String>()
                while (index < lines.size) {
                    val match = Regex("^\\s*\\d+[.)]\\s+(.+)$").matchEntire(lines[index]) ?: break
                    items += match.groupValues[1]; index++
                }
                result += MarkdownBlock.Bullets(items, true)
            }
            else -> {
                val paragraph = mutableListOf<String>()
                while (index < lines.size && lines[index].isNotBlank() && !isBlockStart(lines[index], paragraph.isNotEmpty())) {
                    paragraph += lines[index++].trim()
                }
                if (paragraph.isEmpty()) paragraph += lines[index++].trim()
                result += MarkdownBlock.Paragraph(paragraph.joinToString("\n"))
            }
        }
    }
    return result
}

private fun isBlockStart(line: String, paragraphStarted: Boolean): Boolean {
    if (!paragraphStarted) return false
    val trimmed = line.trimStart()
    return trimmed.startsWith("```") || trimmed.startsWith("# ") || trimmed.startsWith("## ") ||
        trimmed.startsWith("### ") || trimmed.startsWith("> ") || trimmed.startsWith("- ") ||
        trimmed.startsWith("* ") || Regex("^\\d+[.)]\\s+").containsMatchIn(trimmed) || trimmed in setOf("---", "***", "___")
}
