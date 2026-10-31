package com.mauyaapps.aircode.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorMessageParserTest {

    @Test
    fun разбираетПростыеСообщения() {
        assertEquals(EditorMessage.Ready, EditorMessageParser.parse("""{"type":"ready"}"""))
        assertEquals(EditorMessage.Changed, EditorMessageParser.parse("""{"type":"changed"}"""))
    }

    @Test
    fun разбираетПозициюКурсора() {
        val message = EditorMessageParser.parse("""{"type":"cursor","line":3,"column":7}""")
        assertEquals(EditorMessage.Cursor(3, 7), message)
    }

    @Test
    fun разбираетРезультатПоиска() {
        val message = EditorMessageParser.parse("""{"type":"search","current":2,"total":9}""")
        assertEquals(EditorMessage.Search(2, 9), message)
    }

    @Test
    fun разбираетПорциюТекста() {
        val message = EditorMessageParser.parse(
            """{"type":"text","requestId":4,"index":0,"total":2,"data":"a\nb"}""",
        )
        assertEquals(EditorMessage.TextChunk(4, 0, 2, "a\nb"), message)
    }

    @Test
    fun неизвестноеИПовреждённоеСообщениеДаётNull() {
        assertNull(EditorMessageParser.parse("""{"type":"что-то"}"""))
        assertNull(EditorMessageParser.parse("не json"))
        assertNull(EditorMessageParser.parse("""{"type":"cursor","line":1}"""))
        assertNull(EditorMessageParser.parse("""{"type":"search","current":1}"""))
    }
}
