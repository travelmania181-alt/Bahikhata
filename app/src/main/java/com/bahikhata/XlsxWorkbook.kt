package com.bahikhata

import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object XlsxWorkbook {
    fun create(entries: List<LedgerEntry>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.writeText(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                      <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                      <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                    </Types>""",
            )
            zip.writeText(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                    </Relationships>""",
            )
            zip.writeText(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                      <sheets><sheet name="Transactions" sheetId="1" r:id="rId1"/></sheets>
                    </workbook>""",
            )
            zip.writeText(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                      <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                    </Relationships>""",
            )
            zip.writeText(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                    <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                      <numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts>
                      <fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts>
                      <fills count="1"><fill><patternFill patternType="none"/></fill></fills>
                      <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                      <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                      <cellXfs count="3">
                        <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                        <xf numFmtId="14" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                        <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                      </cellXfs>
                      <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                    </styleSheet>""",
            )
            zip.writeText("xl/worksheets/sheet1.xml", worksheet(entries))
        }
        return output.toByteArray()
    }

    private fun worksheet(entries: List<LedgerEntry>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols>""")
        append("<col min=\"1\" max=\"1\" width=\"16\" customWidth=\"1\"/>")
        append("<col min=\"2\" max=\"2\" width=\"12\" customWidth=\"1\"/>")
        append("<col min=\"3\" max=\"3\" width=\"22\" customWidth=\"1\"/>")
        append("<col min=\"4\" max=\"4\" width=\"16\" customWidth=\"1\"/>")
        append("<col min=\"5\" max=\"5\" width=\"32\" customWidth=\"1\"/>")
        append("</cols><sheetData>")
        append("<row r=\"1\">")
        listOf("Date", "Type", "Category", "Amount", "Note").forEachIndexed { index, value ->
            append(inlineCell("${columnName(index + 1)}1", value))
        }
        append("</row>")
        entries.sortedBy { it.date }.forEachIndexed { index, entry ->
            val row = index + 2
            append("<row r=\"$row\">")
            val excelDate = entry.date.toEpochDay() + 25569
            append("<c r=\"A$row\" s=\"1\"><v>$excelDate</v></c>")
            append(inlineCell("B$row", if (entry.type == EntryType.INCOME) "Income" else "Expense"))
            append(inlineCell("C$row", entry.category))
            val amount = BigDecimal.valueOf(entry.amountMinor, 2).toPlainString()
            append("<c r=\"D$row\" s=\"2\"><v>$amount</v></c>")
            append(inlineCell("E$row", entry.note))
            append("</row>")
        }
        append("</sheetData></worksheet>")
    }

    private fun inlineCell(reference: String, value: String): String =
        "<c r=\"$reference\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${value.escapeXml()}</t></is></c>"

    private fun String.escapeXml(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun columnName(index: Int): String = when (index) {
        1 -> "A"
        2 -> "B"
        3 -> "C"
        4 -> "D"
        else -> "E"
    }

    private fun ZipOutputStream.writeText(path: String, contents: String) {
        putNextEntry(ZipEntry(path))
        write(contents.trimIndent().toByteArray(Charsets.UTF_8))
        closeEntry()
    }
}
