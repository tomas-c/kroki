package io.github.kerubistan.kroki.xml

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.xml.stream.XMLEventReader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.events.StartElement
import kotlin.reflect.KFunction0

fun XmlBuilder.nothing() {
	// intentionally blank, used as default
}

interface XmlBuilder {
	fun tag(name: String, vararg atts: Pair<String, Any>, builder: XmlBuilder.() -> Unit)
	fun tag(name: String, vararg atts: Pair<String, Any>)
	fun cdata(data: String)
	fun text(builder: StringBuilder.() -> Unit)
	fun text(value: String)
	fun text(value: Any?) = text(value?.toString() ?: "null")
	fun comment(value: String)
	operator fun Any?.unaryMinus() = text(this?.toString() ?: "null")
	operator fun String.not() = comment(this)
	operator fun String.invoke(vararg atts: Pair<String, Any>): String {
		tag(this, *atts)
		return this
	}

	operator fun String.invoke(vararg atts: Pair<String, Any>, builder: XmlBuilder.() -> Unit): String {
		tag(this, *atts, builder = builder)
		return this
	}
}

fun xml(
	formatMode: FormatMode = FormatMode.COMPACT,
	root: String,
	vararg atts: Pair<String, Any>,
	builder: XmlBuilder.() -> Unit = XmlBuilder::nothing
): InputStream = ByteArrayOutputStream().use {
	StaxXmlBuilder(it, formatMode).use { xmlBuilder ->
		xmlBuilder.tag(root, *atts) { builder() }
	}
	ByteArrayInputStream(it.toByteArray())
}

fun xml(
	formatMode: FormatMode = FormatMode.COMPACT,
	root: String,
	vararg atts: Pair<String, Any>,
	out: OutputStream,
	builder: XmlBuilder.() -> Unit = XmlBuilder::nothing
) {
	StaxXmlBuilder(out, formatMode).use { xmlBuilder ->
		xmlBuilder.tag(root, *atts) { builder() }
	}
}

interface XmlParserBuilder<T> {
	/**
	 * Registers a tag
	 * @param name the name of the tag
	 * @param selector
	 */
	fun tag(
		name: String,
		selector: (name: String, atts: List<Pair<String, String>>) -> Boolean = { _, _ -> true },
		processor: XmlParserBuilder<T>.() -> Unit
	)

	fun text(): String
	fun int(): Int = text().toInt()
	fun <T> yield(result: T): T
	operator fun String.div(s: String): Any {
		TODO("not implemented")
	}

	operator fun Any.div(ref: KFunction0<T>): String {
		TODO("not implemented")
	}

	operator fun String.invoke(function: () -> T): T {
		tag(this@invoke) {
			function()
		}
		TODO("not implemented")
	}

	operator fun String.invoke(vararg atts: Pair<String, Any>): String {
		TODO("not implemented")
	}

	operator fun String.div(kFunction0: KFunction0<Int>): Int {
		TODO()
	}

	operator fun div(s: String): String {
		TODO("not implemented")
	}

	operator fun String.div(function: () -> T): String {
		TODO("not implemented")
	}

	operator fun <E, C : Collection<E>> String.times(function: () -> E): C {
		TODO("not implemented")
	}

	operator fun String.unaryMinus(): String {
		TODO("not implemented")
	}
}

inline fun <T> String.parseAsXml(crossinline builder: XmlParserBuilder<T>.() -> Unit): T =
	ByteArrayInputStream(this.toByteArray()).parseAsXml(builder)

/**
 * Parses the stream as XML and returns the data extracted.
 * @param builder builds the data extractor
 */
inline fun <T> InputStream.parseAsXml(builder: XmlParserBuilder<T>.() -> Unit): T =
	StaxXmlParserBuilder<T>(this).let {
		it.builder()
		it.read()
	}

interface XmlEventStreamParserBuilder {
	fun build() : XmlEventStreamParser
}

interface XmlEventStreamTagParserBuilder: XmlEventStreamParserBuilder {
	fun tag(name: String, builder: XmlEventStreamTagParserBuilder.() -> Unit)
	operator fun String.invoke(builder: XmlEventStreamTagParserBuilder.() -> Unit) {
		tag(name = this, builder = builder)
	}
	fun use(name : String, eventStream: XMLEventReader.() -> Unit)
	operator fun String.minus(fn : (XMLEventReader.() -> Unit)) {
		use(name = this, eventStream = fn)
	}
}

interface XmlEventStreamParser {
	fun parse(events: XMLEventReader)
}

class UseTagXmlEventStreamParser(private val fn : XMLEventReader.() -> Unit) : XmlEventStreamParser {
	override fun parse(events: XMLEventReader) {
		events.fn()
	}
}

/**
 * Only skips through the events until the end.
 */
object NoOperationStreamParser : XmlEventStreamParser {
	override fun parse(events: XMLEventReader) {
		while (events.hasNext()) {
			events.nextEvent()
		}
	}
}

/**
 * Delegates control to a single configured event parser, ignores all other.
 * Separate from MultipleTagsEventStreamParser for performance reason.
 */
class SingleTagEventStreamParser(private val tag : String, private val parser : XmlEventStreamParser) : XmlEventStreamParser {
	override fun parse(events: XMLEventReader) {
		while (events.hasNext()) {
			val event = events.nextEvent()
			if(event is StartElement && event.name.localPart == tag) {
				parser.parse(SubXMLEventReader(events, event.name.localPart))
			}
		}
	}
}

/**
 * Delegates control to XmlEventStreamParser objects based on the tag name.
 * If there is only one, use SingleTagEventStreamParser.
 */
class MultipleTagsEventStreamParser(private val tags : Map<String, XmlEventStreamParser>) : XmlEventStreamParser {
	override fun parse(events: XMLEventReader) {
		TODO("not implemented")
	}
}

class XmlEventStreamUseTagBuilderImpl(private val eventStream: XMLEventReader.() -> Unit) : XmlEventStreamParserBuilder {
	override fun build(): XmlEventStreamParser = UseTagXmlEventStreamParser(eventStream)
}

class XmlEventStreamTagParserBuilderImpl : XmlEventStreamTagParserBuilder {

	private val tagMap = mutableMapOf<String, XmlEventStreamParserBuilder>()

	override fun tag(name: String, builder: XmlEventStreamTagParserBuilder.() -> Unit) {
		require(!tagMap.containsKey(name)) {
			"tag $name already defined"
		}
		val sub = XmlEventStreamTagParserBuilderImpl()
		sub.builder()
		tagMap[name] = sub
	}

	override fun use(name: String, eventStream: XMLEventReader.() -> Unit) {
		require(!tagMap.containsKey(name)) {
			"tag $name already defined"
		}
		tagMap[name] = XmlEventStreamUseTagBuilderImpl(eventStream)
	}

	override fun build(): XmlEventStreamParser =
		when(tagMap.size) {
			0 -> NoOperationStreamParser
			1 -> {
				val tagName = tagMap.keys.single()
				SingleTagEventStreamParser(tagName, tagMap.getValue(tagName).build())
			}
			else -> MultipleTagsEventStreamParser( tagMap.mapValues { it.value.build() } )
		}

}

val xmlInputFactory: XMLInputFactory = XMLInputFactory.newInstance()

inline fun InputStream.readAsXmlEventStream(crossinline builder: XmlEventStreamTagParserBuilder.() -> Unit) {
	XmlEventStreamTagParserBuilderImpl().apply(builder).build().parse(xmlInputFactory.createXMLEventReader(this))
}

fun InputStream.readAsXmlEventStream(parser : XmlEventStreamParser) {
	parser.parse(xmlInputFactory.createXMLEventReader(this))
}
