package com.jonaswanke.unicorn.utils


// region Builder methods
fun buildMarkup(content: CharSequence): Markup = buildMarkup {
    +content
}

fun buildMarkup(builder: MarkupBuilder): Markup {
    return Markup().apply(builder)
}

fun <T> MarkupTag.joined(
    list: Iterable<T>,
    separator: CharSequence = ", ",
    prefix: CharSequence = "",
    postfix: CharSequence = "",
    limit: Int = -1,
    truncated: CharSequence = "...",
    builder: MarkupTag.(T) -> Unit = { +it.toString() }
) {
    +prefix
    var count = 0
    for (element in list) {
        if (++count > 1) +separator
        if (limit < 0 || count <= limit) {
            builder(element)
        } else break
    }
    if (limit in 0 until count) +truncated
    +postfix
}

typealias MarkupBuilder = Markup.() -> Unit
// endregion

@DslMarker
annotation class MarkupTagMarker

@MarkupTagMarker
abstract class MarkupTag(val parent: MarkupTag?) {
    val parents: Sequence<MarkupTag>
        get() = generateSequence(parent) { it.parent }
    val children: MutableList<MarkupTag> = mutableListOf()

    operator fun CharSequence.unaryPlus() {
        string(this)
    }

    operator fun MarkupTag.unaryPlus() {
        this@MarkupTag.children += this@unaryPlus
    }


    // region String Building
    // region Console
    fun toConsoleString() = buildString { appendToConsole(this) }

    abstract fun appendToConsole(builder: StringBuilder)

    fun appendChildrenToConsole(builder: StringBuilder) {
        children.forEach { it.appendToConsole(builder) }
    }

    fun consoleAppendSimple(builder: StringBuilder, prefix: String = "", postfix: String = ""): Unit = with(builder) {
        append(prefix)
        appendChildrenToConsole(this)
        append(postfix)
    }

    protected fun consoleAppendRestore(builder: StringBuilder) {
        val parents = this.parents.toList()
        val formattingCodes = listOfNotNull(
            "0",
            parents.any { it is BoldTag }.thenTake { BoldTag.CONSOLE_FORMATTING_VALUE },
            parents.any { it is CodeTag }.thenTake { CodeTag.CONSOLE_FORMATTING_VALUE }
        )
        builder.consoleAppendFormattingSequence(formattingCodes.joinToString(";"))
    }
    // endregion

    // region Markdown
    fun toMarkdownString() = buildString { appendToMarkdown(this) }

    abstract fun appendToMarkdown(builder: StringBuilder)

    fun appendChildrenToMarkdown(builder: StringBuilder) {
        children.forEach { it.appendToMarkdown(builder) }
    }

    fun markdownAppendSimpleHtml(builder: StringBuilder, tag: String): Unit = with(builder) {
        append("<$tag>")
        appendChildrenToMarkdown(this)
        append("</$tag>")
    }
    // endregion
    // endregion
}

@MarkupTagMarker
abstract class BlockTag(parent: MarkupTag?) : MarkupTag(parent)

@MarkupTagMarker
class Markup : BlockTag(null) {
    override fun appendToConsole(builder: StringBuilder) = appendChildrenToConsole(builder)
    override fun appendToMarkdown(builder: StringBuilder) = appendChildrenToMarkdown(builder)
}


private const val ANSI_ESCAPE = "\u001B["
private const val ANSI_ESCAPE_FORMATTING_END = "m"
private fun StringBuilder.consoleAppendFormattingSequence(sequence: String) {
    append(ANSI_ESCAPE)
    append(sequence)
    append(ANSI_ESCAPE_FORMATTING_END)
}


// region Inline Tags
@MarkupTagMarker
class LineTag(parent: MarkupTag?) : MarkupTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        appendChildrenToConsole(this)
    }

    override fun appendToMarkdown(builder: StringBuilder): Unit = with(builder) {
        appendChildrenToMarkdown(this)
    }
}

fun MarkupTag.line(builder: LineTag.() -> Unit = {}) = +LineTag(this).apply(builder)


@MarkupTagMarker
class StringTag(parent: MarkupTag?, val content: CharSequence) : MarkupTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        append(content)
        appendChildrenToConsole(this)
    }

    override fun appendToMarkdown(builder: StringBuilder): Unit = with(builder) {
        append(content)
        appendChildrenToMarkdown(this)
    }
}

fun MarkupTag.string(content: CharSequence) = +StringTag(this, content)


@MarkupTagMarker
class BoldTag(parent: MarkupTag?) : MarkupTag(parent) {
    companion object {
        const val CONSOLE_FORMATTING_VALUE = "1"
    }

    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        consoleAppendFormattingSequence(CONSOLE_FORMATTING_VALUE)
        appendChildrenToConsole(this)
        consoleAppendRestore(this)
    }

    override fun appendToMarkdown(builder: StringBuilder) = markdownAppendSimpleHtml(builder, "b")
}

fun MarkupTag.bold(content: String? = null, builder: BoldTag.() -> Unit = {}) {
    +BoldTag(this).apply {
        content?.let { +content }
        builder()
    }
}


@MarkupTagMarker
class ItalicTag(parent: MarkupTag?) : MarkupTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        // TODO: italics isn't widely supported on consoles. Maybe use a different color instead?
        appendChildrenToConsole(this)
    }

    override fun appendToMarkdown(builder: StringBuilder) = markdownAppendSimpleHtml(builder, "i")
}

fun MarkupTag.italic(content: String? = null, builder: ItalicTag.() -> Unit = {}) {
    +ItalicTag(this).apply {
        content?.let { +content }
        builder()
    }
}


@MarkupTagMarker
class LinkTag(parent: MarkupTag?, val href: String) : MarkupTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        appendChildrenToConsole(this)
        append(" ($href)")
    }

    override fun appendToMarkdown(builder: StringBuilder): Unit = with(builder) {
        append("<a href=\"$href\">")
        appendChildrenToMarkdown(this)
        append("</a>")
    }
}

fun MarkupTag.link(href: String, content: String? = null, builder: LinkTag.() -> Unit = {}) {
    +LinkTag(parent, href).apply {
        content?.let { +content }
        builder()
    }
}


@MarkupTagMarker
class CodeTag(parent: MarkupTag?) : MarkupTag(parent) {
    companion object {
        const val CONSOLE_FORMATTING_VALUE = "36"
    }

    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        consoleAppendFormattingSequence(CONSOLE_FORMATTING_VALUE)
        appendChildrenToConsole(this)
        consoleAppendRestore(this)
    }

    override fun appendToMarkdown(builder: StringBuilder) = markdownAppendSimpleHtml(builder, "code")
}

fun MarkupTag.code(content: String? = null, builder: CodeTag.() -> Unit = {}) {
    +CodeTag(this).apply {
        content?.let { +content }
        builder()
    }
}


@MarkupTagMarker
class KbdTag(parent: MarkupTag?) : MarkupTag(parent) {
    override fun appendToConsole(builder: StringBuilder) = consoleAppendSimple(builder, "[", "]")
    override fun appendToMarkdown(builder: StringBuilder) = markdownAppendSimpleHtml(builder, "kbd")
}

fun MarkupTag.kbd(content: String? = null, builder: KbdTag.() -> Unit = {}) {
    +KbdTag(this).apply {
        content?.let { +content }
        builder()
    }
}
// endregion

// region Block Tags
@MarkupTagMarker
class ParagraphTag(parent: MarkupTag?) : BlockTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        appendln()
        appendChildrenToConsole(this)
        appendln()
    }

    override fun appendToMarkdown(builder: StringBuilder) = markdownAppendSimpleHtml(builder, "p")
}

fun BlockTag.p(builder: ParagraphTag.() -> Unit) {
    +ParagraphTag(this).apply(builder)
}


@MarkupTagMarker
class ListTag(parent: MarkupTag?) : BlockTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        children.forEach {
            append("- ")
            it.appendToConsole(this)
            appendln()
        }
    }

    override fun appendToMarkdown(builder: StringBuilder): Unit = with(builder) {
        appendln("<ul>")
        children.forEach {
            appendln("<li>")
            it.appendToMarkdown(builder)
            appendln("</li>")
        }
        appendln("</ul>")
    }
}

fun BlockTag.list(builder: ListTag.() -> Unit) {
    +ListTag(this).apply(builder)
}


@MarkupTagMarker
class NewlineTag(parent: MarkupTag?) : BlockTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        appendChildrenToConsole(this)
        appendln()
    }

    override fun appendToMarkdown(builder: StringBuilder): Unit = with(builder) {
        appendChildrenToConsole(this)
        appendln("<br>")
    }
}

fun BlockTag.newLine() {
    +NewlineTag(this)
}


@MarkupTagMarker
class H1Tag(parent: MarkupTag?) : BlockTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        appendChildrenToConsole(this)
        appendln()
        appendln("======")
    }

    override fun appendToMarkdown(builder: StringBuilder) = markdownAppendSimpleHtml(builder, "h1")
}

fun BlockTag.h1(heading: String? = null, builder: H1Tag.() -> Unit = {}) {
    +H1Tag(this).apply {
        heading?.let { +heading }
        builder()
    }
}

@MarkupTagMarker
class H2Tag(parent: MarkupTag?) : BlockTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        appendChildrenToConsole(this)
        appendln()
        appendln("------")
    }

    override fun appendToMarkdown(builder: StringBuilder) = markdownAppendSimpleHtml(builder, "h2")
}

fun BlockTag.h2(heading: String? = null, builder: H2Tag.() -> Unit = {}) {
    +H2Tag(this).apply {
        heading?.let { +heading }
        builder()
    }
}

@MarkupTagMarker
class H3Tag(parent: MarkupTag?) : BlockTag(parent) {
    override fun appendToConsole(builder: StringBuilder): Unit = with(builder) {
        appendChildrenToConsole(this)
        appendln()
    }

    override fun appendToMarkdown(builder: StringBuilder) = markdownAppendSimpleHtml(builder, "h3")
}

fun BlockTag.h3(heading: String? = null, builder: H3Tag.() -> Unit = {}) {
    +H3Tag(this).apply {
        heading?.let { +heading }
        builder()
    }
}
// endregion
