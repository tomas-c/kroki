package io.github.kerubistan.kroki.xml

import javax.xml.stream.XMLEventReader
import javax.xml.stream.events.EndElement
import javax.xml.stream.events.StartElement
import javax.xml.stream.events.XMLEvent

/**
 * Allows the client code to read XML events of XML stream only in a certain tag of the
 * whole XML stream. Once the closing tag is reached, the reader object will respond as
 * if no more events available.
 */
class SubXMLEventReader (private val original : XMLEventReader, private val closeTag: String) : XMLEventReader {

	var open = true
	var depth = 0

	private fun checkOpen() {
		check(open) { "Event reader closed already" }
	}

	override fun nextEvent(): XMLEvent {
		checkOpen()
		val event = original.nextEvent()
		when(event) {
			is EndElement -> {
				TODO("not implemented")
			}
			is StartElement -> {
				TODO("not implemented")
			}
		}
		return event
	}

	override fun remove() {
		checkOpen()
		original.remove()
	}

	override fun getElementText(): String {
		checkOpen()
		return original.elementText
	}

	override fun getProperty(prop: String?): Any {
		checkOpen()
		return original.getProperty(prop)
	}

	override fun next(): Any? {
		checkOpen()
		val next = original.next()
		when(next) {
			is StartElement -> depth++
			is EndElement -> {
				depth--
			}
		}
		return next
	}

	override fun peek(): XMLEvent {
		checkOpen()
		TODO("not implemented")
	}

	override fun hasNext(): Boolean = open && original.hasNext()

	override fun close() {
		open = false
	}

	override fun nextTag(): XMLEvent {
		TODO("not implemented")
	}
}