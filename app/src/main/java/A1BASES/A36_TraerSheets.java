package A1BASES;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ⭐ NUEVO (22-nov, Jorge, etapa S2 de Sheets): "Traer de Sheets".
 *
 * Descarga una hoja creada por "Enviar a Sheets" (A33), la compara con la base y prepara los
 * cambios SIN aplicarlos (vista previa). Si hay un solo error, no se aplica nada.
 *
 * Reglas (acordadas con Jorge):
 *  - transaccion_id y el cuenta_id de la tabla cuentas son la identidad de cada fila: una fila con
 *    un id que no existe es error; las filas nuevas van con el id vacío.
 *  - Solo se toman las columnas editables (las de texto lila en la hoja); las demás (referencias,
 *    tipo_cuenta_id de la transacción, etc.) se ignoran.
 *  - El cuenta_id de una transacción se puede cambiar por otra cuenta existente; su tipo de cuenta
 *    se toma de la cuenta nueva.
 *  - Transacciones: modificar, agregar y borrar (no se borra una con inventario).
 *  - Cuentas: se actualizan por cuenta_id (sin borrar todo, así no se pierde la configuración);
 *    agregar; borrar solo si nada la usa.
 *  - Saldos iniciales: solo agrega las filas con valor distinto de 0.
 */
public class A36_TraerSheets {

    private static final String TAG = "A36_TraerSheets";
    private static final String MIME_HOJA = "application/vnd.google-apps.spreadsheet";
    private static final int MAX_LINEAS = 40;

    public static final String TIPO_TRANSACCIONES = A33_Sheets.TIPO_TRANSACCIONES;
    public static final String TIPO_CUENTAS = A33_Sheets.TIPO_CUENTAS;
    public static final String TIPO_SALDOS = A33_Sheets.TIPO_SALDOS;

    /** Hoja de Drive que se puede traer. */
    public static class HojaDrive {
        public String id;
        public String nombre;
        public String tipo;
        public long modificada;

        public String titulo() {
            if (TIPO_TRANSACCIONES.equals(tipo)) return "Transacciones";
            if (TIPO_CUENTAS.equals(tipo)) return "Cuentas";
            return "Saldos iniciales";
        }
    }

    /** Resultado del análisis: lo que se haría y los errores. */
    public static class Analisis {
        public HojaDrive hoja;
        public List<String> errores = new ArrayList<>();
        public List<String> cambios = new ArrayList<>();
        public int nuevas;
        public int modificadas;
        public int borradas;
        public int sinCambio;
        // Operaciones preparadas
        final List<ContentValues> inserciones = new ArrayList<>();
        final Map<Long, ContentValues> actualizaciones = new LinkedHashMap<>();
        final List<Long> borrados = new ArrayList<>();
        boolean cambiaDocumentoItem;
        boolean cambiaTipoCuenta;

        public boolean hayCambios() {
            return nuevas + modificadas + borradas > 0;
        }

        public String operacion() {
            if (TIPO_TRANSACCIONES.equals(hoja.tipo)) return "sheets-corregir-transacciones";
            if (TIPO_CUENTAS.equals(hoja.tipo)) return "sheets-corregir-cuentas";
            return "sheets-saldos-iniciales";
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Drive
    // ─────────────────────────────────────────────────────────────

    /** Hojas de Correcciones y Saldos iniciales (sin catálogos), la más reciente primero. */
    public static List<HojaDrive> listar(Context context) throws Exception {
        Drive drive = A32_Respaldos.servicioDrive(context);
        String raiz = A33_Sheets.carpetaSheets(context, drive);
        List<HojaDrive> lista = new ArrayList<>();
        for (String sub : new String[]{A33_Sheets.SUBCARPETA_CORRECCIONES, A33_Sheets.SUBCARPETA_SALDOS}) {
            String idCarpeta = A33_Sheets.subcarpeta(context, drive, raiz, sub);
            FileList l = drive.files().list()
                    .setQ("'" + idCarpeta + "' in parents and mimeType='" + MIME_HOJA + "' and trashed=false")
                    .setSpaces("drive").setFields("files(id, name, modifiedTime)")
                    .setOrderBy("modifiedTime desc").setPageSize(50).execute();
            if (l.getFiles() == null) continue;
            for (com.google.api.services.drive.model.File f : l.getFiles()) {
                String n = f.getName();
                String tipo = n.contains("_sheets-" + TIPO_TRANSACCIONES + "_") ? TIPO_TRANSACCIONES
                        : n.contains("_sheets-" + TIPO_CUENTAS + "_") ? TIPO_CUENTAS
                        : n.contains("_sheets-" + TIPO_SALDOS + "_") ? TIPO_SALDOS : null;
                if (tipo == null) continue; // catálogos y otros: no se importan
                HojaDrive h = new HojaDrive();
                h.id = f.getId();
                h.nombre = n;
                h.tipo = tipo;
                h.modificada = f.getModifiedTime() != null ? f.getModifiedTime().getValue() : 0;
                lista.add(h);
            }
        }
        java.util.Collections.sort(lista, (a, b) -> Long.compare(b.modificada, a.modificada));
        return lista;
    }

    /** Descarga la primera pestaña de la hoja como CSV y la convierte en filas. */
    public static List<String[]> descargar(Context context, String idHoja) throws Exception {
        Drive drive = A32_Respaldos.servicioDrive(context);
        try (InputStream in = drive.files().export(idHoja, "text/csv").executeMediaAsInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return parsearCsv(new String(out.toByteArray(), StandardCharsets.UTF_8));
        }
    }

    /** CSV con comillas (RFC 4180), como lo exporta Google. */
    static List<String[]> parsearCsv(String texto) {
        List<String[]> filas = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean comillas = false;
        if (texto.startsWith("﻿")) texto = texto.substring(1);
        for (int i = 0; i < texto.length(); i++) {
            char ch = texto.charAt(i);
            if (comillas) {
                if (ch == '"') {
                    if (i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                        campo.append('"');
                        i++;
                    } else {
                        comillas = false;
                    }
                } else {
                    campo.append(ch);
                }
            } else if (ch == '"') {
                comillas = true;
            } else if (ch == ',') {
                campos.add(campo.toString());
                campo.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < texto.length() && texto.charAt(i + 1) == '\n') i++;
                campos.add(campo.toString());
                campo.setLength(0);
                filas.add(campos.toArray(new String[0]));
                campos.clear();
            } else {
                campo.append(ch);
            }
        }
        if (campo.length() > 0 || !campos.isEmpty()) {
            campos.add(campo.toString());
            filas.add(campos.toArray(new String[0]));
        }
        return filas;
    }

    // ─────────────────────────────────────────────────────────────
    // Análisis (vista previa)
    // ─────────────────────────────────────────────────────────────

    public static Analisis analizar(Context context, HojaDrive hoja, List<String[]> filas) {
        Analisis a = new Analisis();
        a.hoja = hoja;
        if (filas.isEmpty()) {
            a.errores.add("La hoja está vacía.");
            return a;
        }
        Map<String, Integer> col = indiceColumnas(filas.get(0));
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context, A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null, A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getReadableDatabase();
        try {
            if (TIPO_TRANSACCIONES.equals(hoja.tipo)) analizarTransacciones(db, filas, col, a);
            else if (TIPO_CUENTAS.equals(hoja.tipo)) analizarCuentas(db, filas, col, a);
            else analizarSaldos(db, filas, col, a);
        } catch (Exception e) {
            Log.e(TAG, "Error al analizar la hoja", e);
            a.errores.add("Error al revisar la hoja: " + e.getMessage());
        } finally {
            db.close();
        }
        return a;
    }

    /** Encabezado legible → columna real (las de referencia se ignoran). */
    private static Map<String, Integer> indiceColumnas(String[] encabezado) {
        Map<String, String> inverso = new HashMap<>();
        for (Map.Entry<String, String> e : A33_Sheets.NOMBRE_LEGIBLE.entrySet()) inverso.put(e.getValue(), e.getKey());
        Map<String, Integer> col = new HashMap<>();
        for (int i = 0; i < encabezado.length; i++) {
            String h = encabezado[i] == null ? "" : encabezado[i].trim();
            if (h.isEmpty() || h.endsWith("(referencia)")) continue;
            String real = inverso.containsKey(h) ? inverso.get(h) : h;
            if (!col.containsKey(real)) col.put(real, i);
        }
        return col;
    }

    private static String v(String[] fila, Map<String, Integer> col, String nombre) {
        Integer i = col.get(nombre);
        if (i == null || i >= fila.length || fila[i] == null) return "";
        return fila[i].trim();
    }

    private static boolean vacia(String[] fila) {
        for (String s : fila) if (s != null && !s.trim().isEmpty()) return false;
        return true;
    }

    private static boolean faltan(Map<String, Integer> col, Analisis a, String... requeridas) {
        boolean falta = false;
        for (String r : requeridas) {
            if (!col.containsKey(r)) {
                String legible = A33_Sheets.NOMBRE_LEGIBLE.containsKey(r) ? A33_Sheets.NOMBRE_LEGIBLE.get(r) : r;
                a.errores.add("Falta la columna \"" + legible + "\".");
                falta = true;
            }
        }
        return falta;
    }

    private static Long entero(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String texto(Cursor c, int i) {
        return c.isNull(i) ? "" : c.getString(i);
    }

    private static void error(Analisis a, int fila, String mensaje) {
        a.errores.add("Fila " + fila + ": " + mensaje);
    }

    private static void cambio(Analisis a, String texto) {
        if (a.cambios.size() < MAX_LINEAS) a.cambios.add(texto);
    }

    // ---------- Transacciones ----------

    private static final String[] COLS_TRANS = {"c1_Documento", "c2_ItemDoc", "cuenta_id", "c5_Valor",
            "c6_Descripcion", "c7_FechaYHora", "c8_FechaInicial", "c9_FechaModificacion"};

    private static void analizarTransacciones(SQLiteDatabase db, List<String[]> filas, Map<String, Integer> col, Analisis a) {
        if (faltan(col, a, "transaccion_id", "c1_Documento", "c2_ItemDoc", "cuenta_id", "c5_Valor",
                "c6_Descripcion", "c7_FechaYHora", "c8_FechaInicial", "c9_FechaModificacion")) return;
        Map<Long, String> cuentas = new HashMap<>();
        Map<Long, Long> tipoDeCuenta = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT cuenta_id, Cuenta, tipo_cuenta_id FROM cuentas", null)) {
            while (c.moveToNext()) {
                cuentas.put(c.getLong(0), texto(c, 1));
                if (!c.isNull(2)) tipoDeCuenta.put(c.getLong(0), c.getLong(2));
            }
        }
        Map<Long, String[]> actual = new LinkedHashMap<>();
        try (Cursor c = db.rawQuery("SELECT transaccion_id, c1_Documento, c2_ItemDoc, cuenta_id, c5_Valor, "
                + "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion FROM transacciones", null)) {
            while (c.moveToNext()) {
                String[] f = new String[8];
                for (int i = 0; i < 8; i++) f[i] = texto(c, i + 1);
                actual.put(c.getLong(0), f);
            }
        }
        Set<Long> conInventario = new HashSet<>();
        try (Cursor c = db.rawQuery("SELECT transaccion_id FROM transacciones_inventario", null)) {
            while (c.moveToNext()) conInventario.add(c.getLong(0));
        } catch (Exception ignorado) { }

        Set<Long> vistos = new HashSet<>();
        Set<String> docItems = new HashSet<>();
        // ⭐ (22-nov, Jorge): partida doble. Suma por documento antes (base) y después (hoja).
        Map<String, Long> sumaAntes = new HashMap<>();
        for (String[] f : actual.values()) {
            Long val = entero(f[3]);
            sumaAntes.put(f[0], (sumaAntes.containsKey(f[0]) ? sumaAntes.get(f[0]) : 0L) + (val == null ? 0 : val));
        }
        Map<String, Long> sumaDespues = new HashMap<>();
        documentosTocados = new HashSet<>();
        for (int i = 1; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            if (vacia(fila)) continue;
            int n = i + 1;
            String[] nuevo = new String[8];
            for (int k = 0; k < 8; k++) nuevo[k] = v(fila, col, COLS_TRANS[k]);
            String idTxt = v(fila, col, "transaccion_id");
            int erroresAntes = a.errores.size();

            Long id = null;
            if (!idTxt.isEmpty()) {
                id = entero(idTxt);
                if (id == null || !actual.containsKey(id)) {
                    error(a, n, "transaccion_id " + idTxt + " no existe (las filas nuevas van con transaccion_id vacío).");
                } else if (!vistos.add(id)) {
                    error(a, n, "transaccion_id " + idTxt + " está repetido (para agregar una fila, deja transaccion_id vacío).");
                }
            }
            if (nuevo[0].isEmpty()) error(a, n, "falta el Documento.");
            if (nuevo[1].isEmpty()) error(a, n, "falta el Ítem documento.");
            Long cuenta = entero(nuevo[2]);
            if (cuenta == null || !cuentas.containsKey(cuenta)) {
                error(a, n, "cuenta_id \"" + nuevo[2] + "\" no existe (búscalo en Catálogos).");
            }
            if (entero(nuevo[3]) == null) error(a, n, "el Valor \"" + nuevo[3] + "\" debe ser un número entero, sin puntos ni comas.");
            if (nuevo[4].isEmpty()) error(a, n, "falta la Descripción.");
            if (nuevo[5].isEmpty()) error(a, n, "falta la Fecha y hora.");
            // ⭐ (22-nov, Jorge): la Fecha inicial solo se revisa si es nueva o cambió (hay datos
            // antiguos con 0, que se respetan).
            boolean fechaIgual = id != null && actual.containsKey(id) && actual.get(id)[6].trim().equals(nuevo[6]);
            if (!fechaIgual && !nuevo[6].isEmpty() && !"0".equals(nuevo[6])
                    && (entero(nuevo[6]) == null || nuevo[6].length() != 8)) {
                error(a, n, "la Fecha inicial \"" + nuevo[6] + "\" debe ser AAAAMMDD.");
            }
            if (nuevo[7].isEmpty()) error(a, n, "falta la Fecha modificación.");
            String clave = nuevo[0] + "|" + nuevo[1];
            if (!nuevo[0].isEmpty() && !nuevo[1].isEmpty() && !docItems.add(clave)) {
                error(a, n, "Documento " + nuevo[0] + " ítem " + nuevo[1] + " está repetido.");
            }
            Long valorFila = entero(nuevo[3]);
            if (valorFila != null && !nuevo[0].isEmpty()) {
                sumaDespues.put(nuevo[0], (sumaDespues.containsKey(nuevo[0]) ? sumaDespues.get(nuevo[0]) : 0L) + valorFila);
            }
            if (a.errores.size() > erroresAntes) continue;

            if (id == null) {
                documentosTocados.add(nuevo[0]);
                ContentValues cv = valoresTransaccion(nuevo, tipoDeCuenta.get(cuenta));
                a.inserciones.add(cv);
                a.nuevas++;
                cambio(a, "Nueva: doc " + nuevo[0] + "-" + nuevo[1] + " · " + cuentas.get(cuenta) + " · " + nuevo[3]);
            } else {
                String[] ant = actual.get(id);
                ContentValues cv = new ContentValues();
                StringBuilder det = new StringBuilder();
                for (int k = 0; k < 8; k++) {
                    // ⭐ (22-nov): sin contar espacios al inicio o al final (Sheets los quita).
                    if (!ant[k].trim().equals(nuevo[k])) {
                        ponerTransaccion(cv, COLS_TRANS[k], nuevo[k]);
                        String antTxt = ant[k], nuevoTxt = nuevo[k];
                        if (k == 2) {
                            antTxt = nombre(cuentas, ant[k]);
                            nuevoTxt = cuentas.get(cuenta);
                            Long tipo = tipoDeCuenta.get(cuenta);
                            if (tipo != null) cv.put("tipo_cuenta_id", tipo); else cv.putNull("tipo_cuenta_id");
                        }
                        if (k < 2) a.cambiaDocumentoItem = true;
                        det.append(det.length() > 0 ? "; " : "").append(legible(COLS_TRANS[k]))
                                .append(" ").append(antTxt).append(" → ").append(nuevoTxt);
                    }
                }
                if (cv.size() > 0) {
                    documentosTocados.add(ant[0]);
                    documentosTocados.add(nuevo[0]);
                }
                if (cv.containsKey("c1_Documento") || cv.containsKey("c2_ItemDoc")) {
                    // Al aplicar se aparta el documento: van siempre los dos valores finales.
                    cv.put("c1_Documento", nuevo[0]);
                    cv.put("c2_ItemDoc", nuevo[1]);
                }
                if (cv.size() == 0) {
                    a.sinCambio++;
                } else {
                    a.actualizaciones.put(id, cv);
                    a.modificadas++;
                    cambio(a, "Modificada id " + id + " (doc " + ant[0] + "-" + ant[1] + "): " + det);
                }
            }
        }
        for (Map.Entry<Long, String[]> e : actual.entrySet()) {
            if (vistos.contains(e.getKey())) continue;
            String[] f = e.getValue();
            documentosTocados.add(f[0]);
            if (conInventario.contains(e.getKey())) {
                a.errores.add("La transacción id " + e.getKey() + " (doc " + f[0] + "-" + f[1]
                        + ") no está en la hoja, pero tiene inventario: no se puede borrar.");
                continue;
            }
            a.borrados.add(e.getKey());
            a.borradas++;
            cambio(a, "Borrada id " + e.getKey() + ": doc " + f[0] + "-" + f[1] + " · " + nombre(cuentas, f[2]) + " · " + f[3]);
        }
        // ⭐ CAMBIO (22-nov, Jorge): la regla principal es el cuadre TOTAL: la suma de todas las
        // transacciones debe quedar en cero (positivos = negativos). El documento 0 (saldos
        // iniciales y saldos de los cierres) no cuadra solo: cuadra con el resto del libro.
        long totalAntes = 0, totalDespues = 0;
        for (long x : sumaAntes.values()) totalAntes += x;
        for (long x : sumaDespues.values()) totalDespues += x;
        if (totalDespues != 0) {
            a.errores.add("La hoja no queda cuadrada: todas las transacciones suman " + totalDespues
                    + " (los positivos deben igualar a los negativos"
                    + (totalAntes != 0 ? "; en la app suman " + totalAntes : "") + ").");
        }
        // Cada documento tocado (menos el 0) debe quedar en cero. Si ya estaba descuadrado desde
        // antes (datos antiguos), se acepta solo si su suma no cambia.
        for (String doc : documentosTocados) {
            Long numero = entero(doc);
            if (numero != null && numero == 0) continue;
            long antes = sumaAntes.containsKey(doc) ? sumaAntes.get(doc) : 0L;
            long despues = sumaDespues.containsKey(doc) ? sumaDespues.get(doc) : 0L;
            if (despues != 0 && despues != antes) {
                a.errores.add("El documento " + doc + " no queda en cero: sus valores suman " + despues
                        + " (los positivos deben igualar a los negativos"
                        + (antes != 0 ? "; antes sumaba " + antes : "") + ").");
            }
        }
    }

    private static Set<String> documentosTocados = new HashSet<>();

    private static ContentValues valoresTransaccion(String[] f, Long tipoCuenta) {
        ContentValues cv = new ContentValues();
        for (int k = 0; k < 8; k++) ponerTransaccion(cv, COLS_TRANS[k], f[k]);
        if (tipoCuenta != null) cv.put("tipo_cuenta_id", tipoCuenta);
        return cv;
    }

    private static void ponerTransaccion(ContentValues cv, String columna, String valor) {
        switch (columna) {
            case "cuenta_id":
            case "c5_Valor":
                cv.put(columna, Long.parseLong(valor));
                break;
            case "c8_FechaInicial":
                if (valor.isEmpty()) cv.putNull(columna); else cv.put(columna, Long.parseLong(valor));
                break;
            default:
                cv.put(columna, valor);
        }
    }

    private static String nombre(Map<Long, String> cuentas, String id) {
        Long n = entero(id);
        String nom = n == null ? null : cuentas.get(n);
        return nom != null ? nom : "(cuenta " + id + ")";
    }

    private static String legible(String columna) {
        if ("cuenta_id".equals(columna)) return "Cuenta";
        String l = A33_Sheets.NOMBRE_LEGIBLE.get(columna);
        return l != null ? l : columna;
    }

    // ---------- Cuentas ----------

    private static final String[] COLS_CUENTA = {"Item", "Cuenta", "Fecha", "codigo_cuenta", "Cerrable",
            "tipo_cuenta_id", "cuenta_seguimiento", "conciliable", "con_inventario", "renglon_renta_id",
            "entidad", "tipo_soporte"};
    private static final Set<String> COLS_0_1 = new HashSet<>(java.util.Arrays.asList("Cerrable", "conciliable", "con_inventario"));
    private static final Set<String> COLS_ENTERAS = new HashSet<>(java.util.Arrays.asList(
            "Cerrable", "conciliable", "con_inventario", "tipo_cuenta_id", "cuenta_seguimiento", "renglon_renta_id"));

    private static void analizarCuentas(SQLiteDatabase db, List<String[]> filas, Map<String, Integer> col, Analisis a) {
        if (faltan(col, a, "cuenta_id", "Item", "Cuenta", "Fecha")) return;
        // Solo las columnas editables que estén en la base y en la hoja.
        List<String> columnas = new ArrayList<>();
        Set<String> enBase = new HashSet<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(cuentas)", null)) {
            while (c.moveToNext()) enBase.add(c.getString(1));
        }
        for (String c : COLS_CUENTA) if (enBase.contains(c) && col.containsKey(c)) columnas.add(c);

        Set<Long> tipos = ids(db, "SELECT tipo_cuenta_id FROM " + A1_1_AyudanteBD.TABLE_TIPO_CUENTA);
        Set<Long> renglones = ids(db, "SELECT renglon_id FROM " + A1_1_AyudanteBD.TABLE_RENGLON_RENTA);
        Map<Long, Map<String, String>> actual = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT cuenta_id");
        for (String c : columnas) sql.append(", \"").append(c).append('"');
        try (Cursor c = db.rawQuery(sql.append(" FROM cuentas").toString(), null)) {
            while (c.moveToNext()) {
                Map<String, String> m = new HashMap<>();
                for (int k = 0; k < columnas.size(); k++) m.put(columnas.get(k), texto(c, k + 1));
                actual.put(c.getLong(0), m);
            }
        }

        Set<Long> vistos = new HashSet<>();
        Set<String> nombres = new HashSet<>();
        for (int i = 1; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            if (vacia(fila)) continue;
            int n = i + 1;
            int erroresAntes = a.errores.size();
            String idTxt = v(fila, col, "cuenta_id");
            Long id = null;
            if (!idTxt.isEmpty()) {
                id = entero(idTxt);
                if (id == null || !actual.containsKey(id)) {
                    error(a, n, "cuenta_id " + idTxt + " no existe (las cuentas nuevas van con cuenta_id vacío).");
                } else if (!vistos.add(id)) {
                    error(a, n, "cuenta_id " + idTxt + " está repetido.");
                }
            }
            Map<String, String> nuevo = new HashMap<>();
            for (String c : columnas) nuevo.put(c, v(fila, col, c));
            if (nuevo.get("Cuenta").isEmpty()) error(a, n, "falta el nombre de la Cuenta.");
            else if (!nombres.add(nuevo.get("Cuenta"))) error(a, n, "la cuenta \"" + nuevo.get("Cuenta") + "\" está repetida.");
            if (nuevo.get("Item").isEmpty()) error(a, n, "falta el Ítem.");
            if (nuevo.get("Fecha").isEmpty()) error(a, n, "falta la Fecha.");
            for (String c : columnas) {
                String val = nuevo.get(c);
                if (COLS_0_1.contains(c) && !"0".equals(val) && !"1".equals(val)) {
                    error(a, n, legibleCuenta(c) + " debe ser 0 o 1.");
                } else if (COLS_ENTERAS.contains(c) && !val.isEmpty() && entero(val) == null) {
                    error(a, n, legibleCuenta(c) + " \"" + val + "\" debe ser un número.");
                }
            }
            if (nuevo.containsKey("tipo_cuenta_id") && !nuevo.get("tipo_cuenta_id").isEmpty()
                    && !tipos.contains(entero(nuevo.get("tipo_cuenta_id")))) {
                error(a, n, "tipo_cuenta_id " + nuevo.get("tipo_cuenta_id") + " no existe (búscalo en Catálogos).");
            }
            if (nuevo.containsKey("renglon_renta_id") && !nuevo.get("renglon_renta_id").isEmpty()
                    && !renglones.contains(entero(nuevo.get("renglon_renta_id")))) {
                error(a, n, "renglon_renta_id " + nuevo.get("renglon_renta_id") + " no existe (búscalo en Catálogos).");
            }
            if (a.errores.size() > erroresAntes) continue;

            if (id == null) {
                ContentValues cv = new ContentValues();
                for (String c : columnas) ponerCuenta(cv, c, nuevo.get(c));
                a.inserciones.add(cv);
                a.nuevas++;
                cambio(a, "Nueva cuenta: " + nuevo.get("Cuenta"));
            } else {
                Map<String, String> ant = actual.get(id);
                ContentValues cv = new ContentValues();
                StringBuilder det = new StringBuilder();
                for (String c : columnas) {
                    if (!ant.get(c).trim().equals(nuevo.get(c))) {
                        ponerCuenta(cv, c, nuevo.get(c));
                        if ("tipo_cuenta_id".equals(c)) a.cambiaTipoCuenta = true;
                        det.append(det.length() > 0 ? "; " : "").append(legibleCuenta(c)).append(" ")
                                .append(ant.get(c)).append(" → ").append(nuevo.get(c));
                    }
                }
                if (cv.size() == 0) {
                    a.sinCambio++;
                } else {
                    a.actualizaciones.put(id, cv);
                    a.modificadas++;
                    cambio(a, "Modificada " + ant.get("Cuenta") + " (id " + id + "): " + det);
                }
            }
        }
        // Cuentas que no están en la hoja: se borran solo si nada las usa.
        List<String> tablasConCuenta = tablasQueUsanCuenta(db);
        for (Map.Entry<Long, Map<String, String>> e : actual.entrySet()) {
            if (vistos.contains(e.getKey())) continue;
            String uso = usoDeCuenta(db, tablasConCuenta, e.getKey());
            if (uso != null) {
                a.errores.add("La cuenta \"" + e.getValue().get("Cuenta") + "\" (id " + e.getKey()
                        + ") no está en la hoja, pero se usa en " + uso + ": no se puede borrar.");
                continue;
            }
            a.borrados.add(e.getKey());
            a.borradas++;
            cambio(a, "Borrada cuenta: " + e.getValue().get("Cuenta") + " (id " + e.getKey() + ")");
        }
    }

    private static void ponerCuenta(ContentValues cv, String columna, String valor) {
        if (COLS_ENTERAS.contains(columna)) {
            if (valor.isEmpty()) cv.putNull(columna); else cv.put(columna, Long.parseLong(valor));
        } else if (valor.isEmpty() && !"Item".equals(columna) && !"Cuenta".equals(columna) && !"Fecha".equals(columna)) {
            cv.putNull(columna);
        } else {
            cv.put(columna, valor);
        }
    }

    private static String legibleCuenta(String columna) {
        String l = A33_Sheets.NOMBRE_LEGIBLE.get(columna);
        return l != null ? l : columna;
    }

    private static Set<Long> ids(SQLiteDatabase db, String sql) {
        Set<Long> s = new HashSet<>();
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) if (!c.isNull(0)) s.add(c.getLong(0));
        }
        return s;
    }

    /** Tablas (fuera de cuentas) con una columna cuenta_id. */
    private static List<String> tablasQueUsanCuenta(SQLiteDatabase db) {
        List<String> tablas = new ArrayList<>();
        List<String> todas = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' "
                + "AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_%' AND name <> 'cuentas'", null)) {
            while (c.moveToNext()) todas.add(c.getString(0));
        }
        for (String t : todas) {
            try (Cursor c = db.rawQuery("PRAGMA table_info(\"" + t + "\")", null)) {
                while (c.moveToNext()) {
                    if ("cuenta_id".equalsIgnoreCase(c.getString(1))) {
                        tablas.add(t);
                        break;
                    }
                }
            }
        }
        return tablas;
    }

    private static String usoDeCuenta(SQLiteDatabase db, List<String> tablas, long cuentaId) {
        for (String t : tablas) {
            try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM \"" + t + "\" WHERE cuenta_id = ?",
                    new String[]{String.valueOf(cuentaId)})) {
                if (c.moveToFirst() && c.getLong(0) > 0) return t + " (" + c.getLong(0) + ")";
            }
        }
        return null;
    }

    // ---------- Saldos iniciales ----------

    private static void analizarSaldos(SQLiteDatabase db, List<String[]> filas, Map<String, Integer> col, Analisis a) {
        if (faltan(col, a, "c1_Documento", "c2_ItemDoc", "cuenta_id", "c5_Valor", "c6_Descripcion",
                "c7_FechaYHora", "c8_FechaInicial", "c9_FechaModificacion")) return;
        Map<Long, String> cuentas = new HashMap<>();
        Map<Long, Long> tipoDeCuenta = new HashMap<>();
        try (Cursor c = db.rawQuery("SELECT cuenta_id, Cuenta, tipo_cuenta_id FROM cuentas", null)) {
            while (c.moveToNext()) {
                cuentas.put(c.getLong(0), texto(c, 1));
                if (!c.isNull(2)) tipoDeCuenta.put(c.getLong(0), c.getLong(2));
            }
        }
        Set<String> existentes = new HashSet<>();
        try (Cursor c = db.rawQuery("SELECT c1_Documento, c2_ItemDoc FROM transacciones", null)) {
            while (c.moveToNext()) existentes.add(texto(c, 0) + "|" + texto(c, 1));
        }
        Set<String> enHoja = new HashSet<>();
        long sumaSaldos = 0;
        for (int i = 1; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            if (vacia(fila)) continue;
            String valor = v(fila, col, "c5_Valor");
            if (valor.isEmpty() || "0".equals(valor)) {
                a.sinCambio++;
                continue;
            }
            int n = i + 1;
            int erroresAntes = a.errores.size();
            String[] nuevo = new String[8];
            for (int k = 0; k < 8; k++) nuevo[k] = v(fila, col, COLS_TRANS[k]);
            Long cuenta = entero(nuevo[2]);
            if (cuenta == null || !cuentas.containsKey(cuenta)) error(a, n, "cuenta_id \"" + nuevo[2] + "\" no existe.");
            if (entero(valor) == null) error(a, n, "el Valor \"" + valor + "\" debe ser un número entero, sin puntos ni comas.");
            if (nuevo[0].isEmpty() || nuevo[1].isEmpty()) error(a, n, "faltan el Documento o el Ítem documento.");
            if (nuevo[4].isEmpty()) error(a, n, "falta la Descripción.");
            if (nuevo[5].isEmpty()) error(a, n, "falta la Fecha y hora.");
            if (!nuevo[6].isEmpty() && (entero(nuevo[6]) == null || nuevo[6].length() != 8)) {
                error(a, n, "la Fecha inicial \"" + nuevo[6] + "\" debe ser AAAAMMDD.");
            }
            if (nuevo[7].isEmpty()) nuevo[7] = "n a";
            String clave = nuevo[0] + "|" + nuevo[1];
            if (existentes.contains(clave)) error(a, n, "Documento " + nuevo[0] + " ítem " + nuevo[1]
                    + " ya existe en la app (¿ya se trajo esta hoja? Envía una plantilla nueva).");
            else if (!enHoja.add(clave)) error(a, n, "Documento " + nuevo[0] + " ítem " + nuevo[1] + " está repetido.");
            if (a.errores.size() > erroresAntes) continue;
            a.inserciones.add(valoresTransaccion(nuevo, tipoDeCuenta.get(cuenta)));
            a.nuevas++;
            sumaSaldos += entero(valor);
            cambio(a, "Saldo inicial: " + cuentas.get(cuenta) + " · " + valor);
        }
        // ⭐ (22-nov, Jorge): partida doble, los saldos nuevos deben sumar cero.
        if (a.nuevas > 0 && sumaSaldos != 0) {
            a.errores.add("Los saldos nuevos suman " + sumaSaldos
                    + ": deben sumar 0 (los positivos deben igualar a los negativos).");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Aplicar (una sola transacción de la base: todo o nada)
    // ─────────────────────────────────────────────────────────────

    public static void aplicar(Context context, Analisis a) {
        boolean cuentas = TIPO_CUENTAS.equals(a.hoja.tipo);
        String tabla = cuentas ? "cuentas" : "transacciones";
        String idCol = cuentas ? "cuenta_id" : "transaccion_id";
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context, A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null, A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getWritableDatabase();
        db.beginTransaction();
        try {
            for (Long id : a.borrados) {
                db.delete(tabla, idCol + " = ?", new String[]{String.valueOf(id)});
            }
            // Documento/ítem es único: primero se apartan los que cambian (valor provisional) para
            // que un intercambio entre dos filas no choque a mitad de camino.
            if (!cuentas && a.cambiaDocumentoItem) {
                for (Map.Entry<Long, ContentValues> e : a.actualizaciones.entrySet()) {
                    if (e.getValue().containsKey("c1_Documento") || e.getValue().containsKey("c2_ItemDoc")) {
                        ContentValues tmp = new ContentValues();
                        tmp.put("c1_Documento", "~tmp" + e.getKey());
                        db.update(tabla, tmp, idCol + " = ?", new String[]{String.valueOf(e.getKey())});
                    }
                }
            }
            if (cuentas) {
                // Igual con el nombre de cuenta, que también es único.
                for (Map.Entry<Long, ContentValues> e : a.actualizaciones.entrySet()) {
                    if (e.getValue().containsKey("Cuenta")) {
                        ContentValues tmp = new ContentValues();
                        tmp.put("Cuenta", "~tmp" + e.getKey());
                        db.update(tabla, tmp, idCol + " = ?", new String[]{String.valueOf(e.getKey())});
                    }
                }
            }
            for (Map.Entry<Long, ContentValues> e : a.actualizaciones.entrySet()) {
                db.update(tabla, e.getValue(), idCol + " = ?", new String[]{String.valueOf(e.getKey())});
            }
            for (ContentValues cv : a.inserciones) {
                if (db.insert(tabla, null, cv) == -1) {
                    throw new IllegalStateException("No se pudo insertar una fila nueva en " + tabla);
                }
            }
            if (cuentas && a.cambiaTipoCuenta) A1_1_AyudanteBD.alinearTipoCuentaDeTransacciones(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    // ⭐ NUEVO (22-nov, Jorge): al aplicar, la hoja queda marcada en Drive para no traerla dos veces.
    public static final String MARCA_APLICADA = " ✓ aplicada ";

    public static void marcarAplicada(Context context, HojaDrive hoja) {
        try {
            Drive drive = A32_Respaldos.servicioDrive(context);
            com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
            String base = hoja.nombre.contains(MARCA_APLICADA)
                    ? hoja.nombre.substring(0, hoja.nombre.indexOf(MARCA_APLICADA)) : hoja.nombre;
            meta.setName(base + MARCA_APLICADA
                    + new java.text.SimpleDateFormat("d MMM HH:mm", new java.util.Locale("es", "CO")).format(new java.util.Date()));
            drive.files().update(hoja.id, meta).execute();
        } catch (Exception e) {
            Log.w(TAG, "No se pudo marcar la hoja como aplicada: " + e.getMessage());
        }
    }

    /** Texto corto para el resumen final. */
    public static String detalle(Analisis a) {
        return "Hoja: " + a.hoja.nombre + "\nNuevas: " + a.nuevas + " · Modificadas: " + a.modificadas
                + " · Borradas: " + a.borradas + " · Sin cambio: " + a.sinCambio;
    }
}
