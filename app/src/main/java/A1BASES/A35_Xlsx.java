package A1BASES;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ⭐ NUEVO (22-nov, Jorge): escritor mínimo de Excel (.xlsx) sin librerías, para darle a las
 * hojas de "Enviar a Sheets" la identidad de la app. Drive convierte el .xlsx en hoja de Google
 * conservando el formato (sin la API de Sheets):
 *  - fila 1 (encabezados) con fondo morado (el de los títulos de la app) y texto blanco;
 *  - filas de datos con el fondo rosado de las pantallas de la app;
 *  - texto azul rey en las columnas que NO se deben cambiar y fucsia (el de "donde se llenan
 *    datos" de la app) en las que sí;
 *  - fila 1 y columna A (el id) inmovilizadas, y un filtro listo en los encabezados.
 * Una hoja (pestaña) por tabla.
 */
public final class A35_Xlsx {

    private A35_Xlsx() { }

    /** Una pestaña: encabezados, qué columnas se pueden cambiar y las filas. */
    public static class Hoja {
        public String nombre;
        public List<String> columnas = new ArrayList<>();
        public List<Boolean> editable = new ArrayList<>();
        public List<String[]> filas = new ArrayList<>();
        public boolean inmovilizarColumnaA = true;
    }

    // Estilos (cellXfs): 0 normal · 1 encabezado · 2 dato fijo (azul rey) · 3 dato editable (fucsia)
    private static final int ESTILO_ENCABEZADO = 1;
    private static final int ESTILO_FIJO = 2;
    private static final int ESTILO_EDITABLE = 3;

    public static void escribir(File destino, List<Hoja> hojas) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(destino))) {
            poner(zip, "[Content_Types].xml", tiposDeContenido(hojas.size()));
            poner(zip, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                            + "</Relationships>");
            poner(zip, "xl/workbook.xml", libro(hojas));
            poner(zip, "xl/_rels/workbook.xml.rels", relacionesLibro(hojas.size()));
            poner(zip, "xl/styles.xml", estilos());
            for (int i = 0; i < hojas.size(); i++) {
                poner(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", hoja(hojas.get(i)));
            }
        }
    }

    private static void poner(ZipOutputStream zip, String nombre, String contenido) throws IOException {
        zip.putNextEntry(new ZipEntry(nombre));
        zip.write(contenido.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String tiposDeContenido(int n) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 1; i <= n; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private static String libro(List<Hoja> hojas) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < hojas.size(); i++) {
            sb.append("<sheet name=\"").append(xml(nombreHoja(hojas.get(i).nombre))).append("\" sheetId=\"")
                    .append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        sb.append("</sheets><definedNames>");
        for (int i = 0; i < hojas.size(); i++) {
            Hoja h = hojas.get(i);
            sb.append("<definedName name=\"_xlnm._FilterDatabase\" localSheetId=\"").append(i)
                    .append("\" hidden=\"1\">'").append(xml(nombreHoja(h.nombre))).append("'!$A$1:$")
                    .append(letra(Math.max(1, h.columnas.size()) - 1)).append('$')
                    .append(h.filas.size() + 1).append("</definedName>");
        }
        return sb.append("</definedNames></workbook>").toString();
    }

    private static String relacionesLibro(int n) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= n; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        sb.append("<Relationship Id=\"rId").append(n + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        return sb.append("</Relationships>").toString();
    }

    /** Colores de la app: morado #AA66CC (títulos), rosado #F4D7F5 (fondo), azul rey #1F3FA6, fucsia #D81B60. */
    private static String estilos() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<fonts count=\"4\">"
                + "<font><sz val=\"10\"/><name val=\"Arial\"/></font>"
                + "<font><b/><sz val=\"10\"/><color rgb=\"FFFFFFFF\"/><name val=\"Arial\"/></font>"
                + "<font><sz val=\"10\"/><color rgb=\"FF1F3FA6\"/><name val=\"Arial\"/></font>"
                + "<font><b/><sz val=\"10\"/><color rgb=\"FFD81B60\"/><name val=\"Arial\"/></font>"
                + "</fonts>"
                + "<fills count=\"4\">"
                + "<fill><patternFill patternType=\"none\"/></fill>"
                + "<fill><patternFill patternType=\"gray125\"/></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFAA66CC\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFF4D7F5\"/><bgColor indexed=\"64\"/></patternFill></fill>"
                + "</fills>"
                + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
                + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
                + "<cellXfs count=\"4\">"
                + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
                + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"2\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "<xf numFmtId=\"0\" fontId=\"3\" fillId=\"3\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
                + "</cellXfs>"
                + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
                + "</styleSheet>";
    }

    private static String hoja(Hoja h) {
        int nCols = Math.max(1, h.columnas.size());
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">");
        // Inmovilizar la fila 1 (y la columna A, el id).
        sb.append("<sheetViews><sheetView workbookViewId=\"0\">");
        if (h.inmovilizarColumnaA) {
            sb.append("<pane xSplit=\"1\" ySplit=\"1\" topLeftCell=\"B2\" activePane=\"bottomRight\" state=\"frozen\"/>");
        } else {
            sb.append("<pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/>");
        }
        sb.append("</sheetView></sheetViews>");
        // Anchos de columna según el contenido (tope 45).
        sb.append("<cols>");
        for (int c = 0; c < nCols; c++) {
            int ancho = c < h.columnas.size() ? h.columnas.get(c).length() : 8;
            for (int f = 0; f < Math.min(h.filas.size(), 300); f++) {
                String[] fila = h.filas.get(f);
                if (c < fila.length && fila[c] != null) ancho = Math.max(ancho, fila[c].length());
            }
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1).append("\" width=\"")
                    .append(Math.min(45, ancho + 3)).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");
        sb.append("<row r=\"1\">");
        for (int c = 0; c < h.columnas.size(); c++) {
            celda(sb, c, 1, h.columnas.get(c), ESTILO_ENCABEZADO, false);
        }
        sb.append("</row>");
        for (int f = 0; f < h.filas.size(); f++) {
            int r = f + 2;
            String[] fila = h.filas.get(f);
            sb.append("<row r=\"").append(r).append("\">");
            for (int c = 0; c < h.columnas.size(); c++) {
                String v = c < fila.length ? fila[c] : null;
                boolean editable = c < h.editable.size() && Boolean.TRUE.equals(h.editable.get(c));
                celda(sb, c, r, v == null ? "" : v, editable ? ESTILO_EDITABLE : ESTILO_FIJO, true);
            }
            sb.append("</row>");
        }
        sb.append("</sheetData>");
        // Filtro listo en los encabezados.
        sb.append("<autoFilter ref=\"A1:").append(letra(nCols - 1)).append(h.filas.size() + 1).append("\"/>");
        return sb.append("</worksheet>").toString();
    }

    /** Número si es un entero simple (sin ceros a la izquierda); si no, texto. */
    private static void celda(StringBuilder sb, int c, int r, String v, int estilo, boolean permitirNumero) {
        String ref = letra(c) + r;
        if (permitirNumero && esEntero(v)) {
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(estilo).append("\"><v>")
                    .append(v).append("</v></c>");
        } else {
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(estilo).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xml(v)).append("</t></is></c>");
        }
    }

    private static boolean esEntero(String v) {
        if (v == null || v.isEmpty() || v.length() > 15) return false;
        int i = v.charAt(0) == '-' ? 1 : 0;
        if (i >= v.length()) return false;
        if (v.charAt(i) == '0' && v.length() > i + 1) return false; // conserva "0000", "007"
        for (; i < v.length(); i++) {
            if (!Character.isDigit(v.charAt(i))) return false;
        }
        return true;
    }

    /** 0 → A, 25 → Z, 26 → AA. */
    private static String letra(int indice) {
        StringBuilder sb = new StringBuilder();
        int n = indice + 1;
        while (n > 0) {
            int resto = (n - 1) % 26;
            sb.insert(0, (char) ('A' + resto));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static String nombreHoja(String nombre) {
        String limpio = nombre == null ? "Hoja" : nombre.replaceAll("[\\\\/?*\\[\\]:']", " ").trim();
        if (limpio.isEmpty()) limpio = "Hoja";
        return limpio.length() > 31 ? limpio.substring(0, 31) : limpio;
    }

    private static String xml(String v) {
        StringBuilder sb = new StringBuilder(v.length() + 8);
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            switch (ch) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:
                    if (ch >= 0x20 || ch == '\t' || ch == '\n' || ch == '\r') sb.append(ch);
            }
        }
        return sb.toString();
    }
}
