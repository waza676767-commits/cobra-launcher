package app.velo.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The pieces a chat reply is broken into before drawing. */
sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Code(val code: String) : MdBlock
    data class ListItem(val indent: Int, val marker: String, val text: String) : MdBlock
    data class Quote(val text: String) : MdBlock
    data object Rule : MdBlock
}

private val headingLine = Regex("^(#{1,6})\\s+(.*)$")
private val bulletLine = Regex("^(\\s*)([-*+])\\s+(.*)$")
private val numberLine = Regex("^(\\s*)(\\d{1,3})[.)]\\s+(.*)$")
private val ruleLine = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

/** Splits Markdown into blocks. Tolerates half-finished input, since replies arrive in pieces. */
fun parseMarkdown(source: String): List<MdBlock> {
    val out = ArrayList<MdBlock>()
    val paragraph = StringBuilder()
    fun flush() {
        if (paragraph.isNotBlank()) out += MdBlock.Paragraph(paragraph.toString().trim())
        paragraph.setLength(0)
    }
    val lines = source.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val trimmed = line.trim()
        when {
            trimmed.startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[i])
                    i++
                }
                out += MdBlock.Code(code.toString())
            }
            trimmed.isEmpty() -> flush()
            ruleLine.matches(line) -> {
                flush()
                out += MdBlock.Rule
            }
            headingLine.matches(trimmed) -> {
                flush()
                val m = headingLine.find(trimmed)!!
                out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim().trimEnd('#').trim())
            }
            bulletLine.matches(line) -> {
                flush()
                val m = bulletLine.find(line)!!
                out += MdBlock.ListItem(m.groupValues[1].length / 2, "•", m.groupValues[3])
            }
            numberLine.matches(line) -> {
                flush()
                val m = numberLine.find(line)!!
                out += MdBlock.ListItem(m.groupValues[1].length / 2, m.groupValues[2] + ".", m.groupValues[3])
            }
            trimmed.startsWith(">") -> {
                flush()
                out += MdBlock.Quote(trimmed.removePrefix(">").trim())
            }
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(trimmed)
            }
        }
        i++
    }
    flush()
    return out
}

/** Turns inline Markdown (bold, italics, code, links) into styled text. */
fun inlineMarkdown(text: String, linkColor: Color, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val n = text.length
    fun closing(token: String, from: Int): Int {
        val at = text.indexOf(token, from)
        return if (at > from) at else -1
    }
    while (i < n) {
        val ch = text[i]
        when {
            ch == '`' -> {
                val end = closing("`", i + 1)
                if (end < 0) {
                    append(ch)
                    i++
                } else {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground, fontSize = 13.5.sp)) {
                        append(text, i + 1, end)
                    }
                    i = end + 1
                }
            }
            text.startsWith("**", i) -> {
                val end = closing("**", i + 2)
                if (end < 0) {
                    append("**")
                    i += 2
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append(inlineMarkdown(text.substring(i + 2, end), linkColor, codeBackground))
                    }
                    i = end + 2
                }
            }
            text.startsWith("~~", i) -> {
                val end = closing("~~", i + 2)
                if (end < 0) {
                    append("~~")
                    i += 2
                } else {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { append(text, i + 2, end) }
                    i = end + 2
                }
            }
            (ch == '*' || ch == '_') && i + 1 < n && !text[i + 1].isWhitespace() &&
                (ch == '*' || i == 0 || !text[i - 1].isLetterOrDigit()) -> {
                val end = closing(ch.toString(), i + 1)
                val valid = end > 0 && !text[end - 1].isWhitespace() &&
                    (ch == '*' || end + 1 >= n || !text[end + 1].isLetterOrDigit())
                if (!valid) {
                    append(ch)
                    i++
                } else {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inlineMarkdown(text.substring(i + 1, end), linkColor, codeBackground))
                    }
                    i = end + 1
                }
            }
            ch == '[' -> {
                val close = text.indexOf("](", i + 1)
                val end = if (close > 0) text.indexOf(')', close + 2) else -1
                val url = if (end > 0) text.substring(close + 2, end).trim() else ""
                if (end < 0 || !(url.startsWith("http://") || url.startsWith("https://"))) {
                    append(ch)
                    i++
                } else {
                    withLink(
                        LinkAnnotation.Url(
                            url,
                            TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
                        )
                    ) { append(text, i + 1, close) }
                    i = end + 1
                }
            }
            else -> {
                append(ch)
                i++
            }
        }
    }
}

@Composable
fun MarkdownText(source: String, modifier: Modifier = Modifier, color: Color = Velo.colors.text) {
    val c = Velo.colors
    val blocks = remember(source) { parseMarkdown(source) }
    val linkColor = if (c.accent == c.text) Color(0xFF3D7BFF) else c.accent
    val codeBg = c.surfaceHi
    val body = TextStyle(color = color, fontSize = 15.5.sp, lineHeight = 23.sp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.Paragraph -> BasicText(
                    remember(block.text, linkColor, codeBg) { inlineMarkdown(block.text, linkColor, codeBg) }, style = body,
                )
                is MdBlock.Heading -> BasicText(
                    remember(block.text, linkColor, codeBg) { inlineMarkdown(block.text, linkColor, codeBg) },
                    modifier = Modifier.padding(top = 4.dp),
                    style = body.copy(
                        fontSize = when (block.level) {
                            1 -> 21.sp
                            2 -> 18.5.sp
                            else -> 16.5.sp
                        },
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                is MdBlock.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(Velo.shapes.field)
                        .background(c.surface)
                        .horizontalScroll(rememberScrollState())
                        .padding(14.dp)
                ) {
                    BasicText(
                        block.code,
                        style = TextStyle(color = color, fontSize = 13.sp, lineHeight = 19.sp, fontFamily = FontFamily.Monospace),
                        softWrap = false,
                    )
                }
                is MdBlock.ListItem -> Row(Modifier.padding(start = (block.indent * 16).dp)) {
                    BasicText(block.marker, modifier = Modifier.width(if (block.marker == "•") 18.dp else 26.dp), style = body.copy(color = c.textDim))
                    BasicText(
                        remember(block.text, linkColor, codeBg) { inlineMarkdown(block.text, linkColor, codeBg) },
                        modifier = Modifier.weight(1f), style = body,
                    )
                }
                is MdBlock.Quote -> Row {
                    Box(Modifier.width(3.dp).height(22.dp).background(c.outline))
                    BasicText(
                        remember(block.text, linkColor, codeBg) { inlineMarkdown(block.text, linkColor, codeBg) },
                        modifier = Modifier.padding(start = 12.dp), style = body.copy(color = c.textDim),
                    )
                }
                MdBlock.Rule -> Box(Modifier.fillMaxWidth().height(1.dp).background(c.outline))
            }
        }
    }
}
