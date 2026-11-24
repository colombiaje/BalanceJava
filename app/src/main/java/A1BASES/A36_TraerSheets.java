package A1BASES;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

import java.io.ByteArrayOutputStream;
import java.io.File;
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
        public long creada;
        // ⭐ (22-nov, Jorge): cuántas veces se aplicó esta hoja y cuántos cambios lleva en total
        // (propiedades del archivo en Drive).
        public int aplicaciones;
        public int cambiosTotales;

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
        // ⭐ (22-nov, fusión de tres versiones): cambios hechos en la app desde que se preparó la
        // hoja, que se conservan; y la edad de la hoja en horas.
        public int conservadosApp;
        public long horasHoja = -1;
        public long minutosRestantes = Long.MIN_VALUE;
        // Operaciones preparadas
        final List<ContentValues> inserciones = new ArrayList<>();
        final Map<Long, ContentValues> actualizaciones = new LinkedHashMap<>();
        final List<Long> borrados = new ArrayList<>();
        boolean cambiaDocumentoItem;
        boolean cambiaTipoCuenta;
        // ⭐ (24-nov, Jorge): "Historial" desde la hoja de cuentas. Activar (0 → 1) hace el saldo
        // de arranque igual que F2; en cuentas nuevas, por su posición en "inserciones".
        final List<Long> activarHistorial = new ArrayList<>();
        final java.util.Set<Integer> historialEnNuevas = new HashSet<>();

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
        List<HojaDrive> lista = new ArrayList<>();
        limpiarHojasSueltas(context, drive);
        // ⭐ (22-nov, Jorge): solo las hojas de este equipo.
        for (String sub : new String[]{A33_Sheets.SUBCARPETA_CORRECCIONES, A33_Sheets.SUBCARPETA_SALDOS}) {
            String idCarpeta = A33_Sheets.carpetaDeEquipo(context, drive, sub);
            FileList l = drive.files().list()
                    .setQ("'" + idCarpeta + "' in parents and mimeType='" + MIME_HOJA + "' and trashed=false")
                    .setSpaces("drive").setFields("files(id, name, modifiedTime, createdTime, appProperties)")
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
                h.creada = f.getCreatedTime() != null ? f.getCreatedTime().getValue() : h.modificada;
                if (f.getAppProperties() != null) {
                    Long ap = entero(f.getAppProperties().get(PROP_APLICACIONES));
                    Long ct = entero(f.getAppProperties().get(PROP_CAMBIOS));
                    h.aplicaciones = ap == null ? 0 : ap.intValue();
                    h.cambiosTotales = ct == null ? 0 : ct.intValue();
                }
                lista.add(h);
            }
        }
        java.util.Collections.sort(lista, (a, b) -> Long.compare(b.modificada, a.modificada));
        return lista;
    }

    /**
     * ⭐ NUEVO (22-nov, Jorge): las hojas de este equipo que quedaron sueltas de versiones
     * anteriores (en Sheets, Sheets › Correcciones o Sheets › Saldos iniciales, fuera de la carpeta
     * del equipo) van a la papelera de Drive: así solo queda una hoja por tabla.
     */
    private static void limpiarHojasSueltas(Context context, Drive drive) {
        try {
            String prefijo = A32_Respaldos.equipo(context) + "_sheets-";
            String raiz = A33_Sheets.carpetaSheets(context, drive);
            List<String> carpetas = new ArrayList<>();
            carpetas.add(raiz);
            carpetas.add(A33_Sheets.subcarpeta(context, drive, raiz, A33_Sheets.SUBCARPETA_CORRECCIONES));
            carpetas.add(A33_Sheets.subcarpeta(context, drive, raiz, A33_Sheets.SUBCARPETA_SALDOS));
            for (String idCarpeta : carpetas) {
                FileList l = drive.files().list()
                        .setQ("'" + idCarpeta + "' in parents and mimeType='" + MIME_HOJA + "'"
                                + " and name contains '" + prefijo + "' and trashed=false")
                        .setSpaces("drive").setFields("files(id, name)").setPageSize(100).execute();
                if (l.getFiles() == null) continue;
                for (com.google.api.services.drive.model.File f : l.getFiles()) {
                    if (!f.getName().startsWith(prefijo)) continue;
                    com.google.api.services.drive.model.File papelera = new com.google.api.services.drive.model.File();
                    papelera.setTrashed(true);
                    drive.files().update(f.getId(), papelera).execute();
                    borrarBase(context, f.getId());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudieron limpiar las hojas sueltas: " + e.getMessage());
        }
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

    // ═════════════════════════════════════════════════════════════
    // ⭐ NUEVO (22-nov, Jorge): ventana de tiempo y fusión de tres versiones
    //  - La hoja vale VENTANA_HORAS desde que se preparó o se puso al día por última vez. Pasada
    //    la ventana se bloquea: enviar una hoja nueva es sencillo y da certeza de lo que hay hoy.
    //  - Al preparar la hoja se guarda en el equipo su "base" (cómo estaba cada fila). Al traerla
    //    se comparan tres versiones de cada fila: base, app de hoy y hoja. Solo se aplica lo que
    //    cambió el usuario en la hoja; lo que cambió en la app después se conserva; si la misma
    //    fila cambió en los dos lados, es conflicto y no se aplica nada.
    // ═════════════════════════════════════════════════════════════
    // ⭐ (22-nov, Jorge): 6 horas; las correcciones en Sheets son cortas y volver a enviar es sencillo.
    public static final int VENTANA_HORAS = 6;

    /**
     * Minutos que le quedan a la hoja dentro de la ventana (negativo = vencida). Para hojas sin base
     * en este equipo devuelve Long.MIN_VALUE. Usa la fecha del archivo de la base (se escribe al
     * enviar o poner al día la hoja) o, en saldos iniciales, la creación de la hoja en Drive.
     */
    public static long minutosRestantes(Context context, HojaDrive hoja) {
        long desde;
        if (TIPO_SALDOS.equals(hoja.tipo)) {
            desde = hoja.creada;
        } else {
            File f = new File(carpetaBases(context), hoja.id + ".base");
            if (!f.exists()) return Long.MIN_VALUE;
            desde = f.lastModified();
        }
        return (desde + VENTANA_HORAS * 3600000L - System.currentTimeMillis()) / 60000L;
    }

    /** "5 h 20 min", "40 min". */
    public static String textoMinutos(long minutos) {
        long m = Math.max(0, minutos);
        return (m >= 60 ? (m / 60) + " h " : "") + (m % 60) + " min";
    }

    /** Copia base de una hoja: columnas comparables y filas por id. */
    static class Base implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        long fecha;
        ArrayList<String> columnas = new ArrayList<>();
        LinkedHashMap<Long, String[]> filas = new LinkedHashMap<>();
    }

    private static File carpetaBases(Context context) {
        File c = new File(context.getFilesDir(), "sheets_base");
        if (!c.exists()) c.mkdirs();
        return c;
    }

    /** Lee de la base las filas comparables de la tabla de la hoja. */
    static Base cargar(SQLiteDatabase db, String tipo) {
        Base b = new Base();
        b.fecha = System.currentTimeMillis();
        if (TIPO_CUENTAS.equals(tipo)) {
            Set<String> enBase = new HashSet<>();
            try (Cursor c = db.rawQuery("PRAGMA table_info(cuentas)", null)) {
                while (c.moveToNext()) enBase.add(c.getString(1));
            }
            for (String col : COLS_CUENTA) if (enBase.contains(col)) b.columnas.add(col);
            StringBuilder sql = new StringBuilder("SELECT cuenta_id");
            for (String col : b.columnas) sql.append(", \"").append(col).append('"');
            sql.append(" FROM cuentas");
            leerFilas(db, sql.toString(), b);
        } else {
            b.columnas.addAll(java.util.Arrays.asList(COLS_TRANS));
            leerFilas(db, "SELECT transaccion_id, c1_Documento, c2_ItemDoc, cuenta_id, c5_Valor, "
                    + "c6_Descripcion, c7_FechaYHora, c8_FechaInicial, c9_FechaModificacion FROM transacciones", b);
        }
        return b;
    }

    private static void leerFilas(SQLiteDatabase db, String sql, Base b) {
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) {
                String[] f = new String[b.columnas.size()];
                for (int k = 0; k < f.length; k++) f[k] = c.isNull(k + 1) ? "" : c.getString(k + 1).trim();
                b.filas.put(c.getLong(0), f);
            }
        }
    }

    /** Guarda la base de la hoja (al enviarla o ponerla al día). Limpia bases de más de 7 días. */
    public static void guardarBase(Context context, String idHoja, String tipo) {
        if (!TIPO_TRANSACCIONES.equals(tipo) && !TIPO_CUENTAS.equals(tipo)) return;
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context, A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null, A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getReadableDatabase();
        Base b;
        try {
            b = cargar(db, tipo);
        } finally {
            db.close();
        }
        File carpeta = carpetaBases(context);
        try (java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(
                new java.io.FileOutputStream(new File(carpeta, idHoja + ".base")))) {
            out.writeObject(b);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo guardar la base de la hoja", e);
        }
        File[] viejas = carpeta.listFiles();
        long limite = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        if (viejas != null) for (File f : viejas) if (f.lastModified() < limite) f.delete();
    }

    // ─────────────────────────────────────────────────────────────
    // ⭐ NUEVO (22-nov, Jorge): una sola sesión activa por equipo (correcciones O saldos).
    // Activa = hoja dentro de su ventana; la plantilla de saldos, además, sin aplicar.
    // ─────────────────────────────────────────────────────────────
    public static List<HojaDrive> sesionActiva(Context context) throws Exception {
        List<HojaDrive> activas = new ArrayList<>();
        for (HojaDrive h : listar(context)) {
            long min = minutosRestantes(context, h);
            if (min == Long.MIN_VALUE || min <= 0) continue;
            if (TIPO_SALDOS.equals(h.tipo) && h.aplicaciones > 0) continue;
            activas.add(h);
        }
        return activas;
    }

    /** Envía las hojas a la papelera de Drive (recuperables 30 días) y borra sus bases. */
    public static void descartar(Context context, List<HojaDrive> hojas) throws Exception {
        Drive drive = A32_Respaldos.servicioDrive(context);
        for (HojaDrive h : hojas) {
            com.google.api.services.drive.model.File papelera = new com.google.api.services.drive.model.File();
            papelera.setTrashed(true);
            drive.files().update(h.id, papelera).execute();
            borrarBase(context, h.id);
        }
    }

    public static void borrarBase(Context context, String idHoja) {
        new File(carpetaBases(context), idHoja + ".base").delete();
    }

    private static Base leerBase(Context context, String idHoja) {
        File f = new File(carpetaBases(context), idHoja + ".base");
        if (!f.exists()) return null;
        try (java.io.ObjectInputStream in = new java.io.ObjectInputStream(new java.io.FileInputStream(f))) {
            return (Base) in.readObject();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo leer la base de la hoja", e);
            return null;
        }
    }

    public static Analisis analizar(Context context, HojaDrive hoja, List<String[]> filas) {
        Analisis a = new Analisis();
        a.hoja = hoja;
        // Ventana de tiempo
        Base base = TIPO_SALDOS.equals(hoja.tipo) ? null : leerBase(context, hoja.id);
        long desde = TIPO_SALDOS.equals(hoja.tipo) ? hoja.creada : (base != null ? base.fecha : 0);
        if (!TIPO_SALDOS.equals(hoja.tipo) && base == null) {
            a.errores.add("Esta hoja no tiene su copia base en este equipo (se preparó en otro equipo o con una"
                    + " versión anterior de la app). Envía una hoja nueva desde la app.");
            return a;
        }
        a.horasHoja = Math.max(0, (System.currentTimeMillis() - desde) / 3600000L);
        a.minutosRestantes = (desde + VENTANA_HORAS * 3600000L - System.currentTimeMillis()) / 60000L;
        if (a.minutosRestantes <= 0) {
            a.errores.add("Esta hoja se preparó hace " + a.horasHoja + " horas y vale " + VENTANA_HORAS
                    + " horas. Envía una hoja nueva desde la app: tendrás la certeza de lo que hay hoy.");
            return a;
        }
        if (filas.isEmpty()) {
            a.errores.add("La hoja está vacía.");
            return a;
        }
        Map<String, Integer> col = indiceColumnas(filas.get(0));
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context, A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null, A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getReadableDatabase();
        try {
            if (TIPO_TRANSACCIONES.equals(hoja.tipo)) analizarTransacciones(db, filas, col, base, a);
            else if (TIPO_CUENTAS.equals(hoja.tipo)) analizarCuentas(db, filas, col, base, a);
            else analizarSaldos(db, filas, col, a);
        } catch (Exception e) {
            Log.e(TAG, "Error al analizar la hoja", e);
            a.errores.add("Error al revisar la hoja: " + e.getMessage());
        } finally {
            db.close();
        }
        return a;
    }

    /**
     * Fusión de tres versiones por fila. Devuelve el estado final de las filas existentes
     * (id → valores) y deja en 'cambiadas' las que hay que escribir y en 'borrar' las que se borran.
     */
    private static Map<Long, String[]> fusionar(Base base, Map<Long, String[]> app, Map<Long, String[]> hoja,
                                                Map<Long, Integer> filaDeId, Analisis a,
                                                Set<Long> cambiadas, List<Long> borrar,
                                                java.util.function.Function<Long, String> nombreFila) {
        List<String> cols = base.columnas;
        Map<Long, String[]> fin = new LinkedHashMap<>();
        for (Map.Entry<Long, String[]> e : app.entrySet()) fin.put(e.getKey(), e.getValue().clone());
        // Cambios hechos en la app desde la base (se conservan)
        for (Map.Entry<Long, String[]> e : app.entrySet()) {
            String[] b = base.filas.get(e.getKey());
            if (b == null || !java.util.Arrays.equals(b, e.getValue())) a.conservadosApp++;
        }
        for (Long id : base.filas.keySet()) if (!app.containsKey(id)) a.conservadosApp++;

        for (Map.Entry<Long, String[]> e : hoja.entrySet()) {
            Long id = e.getKey();
            String[] s = e.getValue();
            String[] b = base.filas.get(id);
            String[] ap = app.get(id);
            int n = filaDeId.get(id);
            if (b == null) {
                error(a, n, "el id " + id + " no estaba en la hoja preparada por la app (las filas nuevas van con el id vacío).");
                continue;
            }
            boolean hojaCambio = !java.util.Arrays.equals(b, s);
            if (ap == null) {
                if (hojaCambio) {
                    error(a, n, "conflicto: " + nombreFila.apply(id) + " se borró en la app después de preparar la hoja,"
                            + " pero la cambiaste en la hoja.");
                }
                continue;
            }
            if (!hojaCambio) continue;
            String[] f = fin.get(id);
            for (int k = 0; k < cols.size(); k++) {
                if (s[k].equals(b[k])) continue;          // la hoja no tocó este campo
                if (ap[k].equals(b[k]) || ap[k].equals(s[k])) {
                    f[k] = s[k];                          // solo cambió la hoja
                } else {
                    error(a, n, "conflicto en " + nombreFila.apply(id) + ", " + legibleCampo(cols.get(k))
                            + ": en la app ahora es \"" + ap[k] + "\" y en la hoja \"" + s[k]
                            + "\" (antes \"" + b[k] + "\").");
                }
            }
            if (!java.util.Arrays.equals(f, ap)) cambiadas.add(id);
        }
        // Filas de la base que el usuario quitó de la hoja
        for (Long id : base.filas.keySet()) {
            if (hoja.containsKey(id) || !app.containsKey(id)) continue;
            if (!java.util.Arrays.equals(app.get(id), base.filas.get(id))) {
                a.errores.add("Conflicto: quitaste de la hoja " + nombreFila.apply(id)
                        + ", pero cambió en la app después de preparar la hoja.");
                continue;
            }
            borrar.add(id);
            fin.remove(id);
        }
        return fin;
    }

    private static String legibleCampo(String col) {
        if ("cuenta_id".equals(col)) return "Cuenta";
        String l = A33_Sheets.NOMBRE_LEGIBLE.get(col);
        return l != null ? l : col;
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

    private static void analizarTransacciones(SQLiteDatabase db, List<String[]> filas, Map<String, Integer> col,
                                              Base base, Analisis a) {
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
        Map<Long, String[]> app = cargar(db, TIPO_TRANSACCIONES).filas;
        Set<Long> conInventario = new HashSet<>();
        try (Cursor c = db.rawQuery("SELECT transaccion_id FROM transacciones_inventario", null)) {
            while (c.moveToNext()) conInventario.add(c.getLong(0));
        } catch (Exception ignorado) { }

        // 1. Leer la hoja: filas con id (para fusionar) y nuevas (id vacío)
        Map<Long, String[]> hoja = new LinkedHashMap<>();
        Map<Long, Integer> filaDeId = new HashMap<>();
        List<String[]> nuevas = new ArrayList<>();
        List<Integer> filaNueva = new ArrayList<>();
        for (int i = 1; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            if (vacia(fila)) continue;
            int n = i + 1;
            String[] s = new String[8];
            for (int k = 0; k < 8; k++) s[k] = v(fila, col, COLS_TRANS[k]);
            String idTxt = v(fila, col, "transaccion_id");
            if (idTxt.isEmpty()) {
                nuevas.add(s);
                filaNueva.add(n);
                continue;
            }
            Long id = entero(idTxt);
            if (id == null) {
                error(a, n, "transaccion_id \"" + idTxt + "\" no es válido.");
            } else if (hoja.containsKey(id)) {
                error(a, n, "transaccion_id " + idTxt + " está repetido (para agregar una fila, deja transaccion_id vacío).");
            } else {
                hoja.put(id, s);
                filaDeId.put(id, n);
            }
        }
        // 2. Fusión de tres versiones
        Set<Long> cambiadas = new java.util.LinkedHashSet<>();
        List<Long> borrar = new ArrayList<>();
        Map<Long, String[]> fin = fusionar(base, app, hoja, filaDeId, a, cambiadas, borrar,
                id -> "la transacción id " + id + (app.containsKey(id) ? " (doc " + app.get(id)[0] + "-" + app.get(id)[1] + ")" : ""));

        // 3. Validar lo que cambia o entra
        for (Long id : cambiadas) validarTransaccion(fin.get(id), app.get(id), filaDeId.get(id), cuentas, a);
        for (int i = 0; i < nuevas.size(); i++) validarTransaccion(nuevas.get(i), null, filaNueva.get(i), cuentas, a);
        for (Long id : borrar) {
            if (conInventario.contains(id)) {
                String[] f = app.get(id);
                a.errores.add("La transacción id " + id + " (doc " + f[0] + "-" + f[1]
                        + ") no está en la hoja, pero tiene inventario: no se puede borrar.");
            }
        }
        // Documento + ítem únicos en el resultado final
        Map<String, String> docItems = new HashMap<>();
        for (Map.Entry<Long, String[]> e : fin.entrySet()) docItems.put(e.getValue()[0] + "|" + e.getValue()[1], "id " + e.getKey());
        if (docItems.size() < fin.size()) {
            Set<String> vistos = new HashSet<>();
            for (Map.Entry<Long, String[]> e : fin.entrySet()) {
                String k = e.getValue()[0] + "|" + e.getValue()[1];
                if (!vistos.add(k)) a.errores.add("Documento " + e.getValue()[0] + " ítem " + e.getValue()[1]
                        + " quedaría repetido (id " + e.getKey() + ").");
            }
        }
        for (int i = 0; i < nuevas.size(); i++) {
            String k = nuevas.get(i)[0] + "|" + nuevas.get(i)[1];
            if (docItems.containsKey(k)) error(a, filaNueva.get(i), "Documento " + nuevas.get(i)[0] + " ítem "
                    + nuevas.get(i)[1] + " ya existe (" + docItems.get(k) + ").");
            else docItems.put(k, "fila " + filaNueva.get(i));
        }
        // 4. Partida doble sobre el resultado final
        Map<String, Long> sumaAntes = sumasPorDocumento(app.values());
        List<String[]> resultado = new ArrayList<>(fin.values());
        resultado.addAll(nuevas);
        Map<String, Long> sumaDespues = sumasPorDocumento(resultado);
        long totalAntes = 0, totalDespues = 0;
        for (long x : sumaAntes.values()) totalAntes += x;
        for (long x : sumaDespues.values()) totalDespues += x;
        if (totalDespues != 0) {
            a.errores.add("La hoja no queda cuadrada: todas las transacciones sumarían " + totalDespues
                    + " (los positivos deben igualar a los negativos"
                    + (totalAntes != 0 ? "; en la app suman " + totalAntes : "") + ").");
        }
        Set<String> tocados = new HashSet<>();
        for (Long id : cambiadas) { tocados.add(app.get(id)[0]); tocados.add(fin.get(id)[0]); }
        for (Long id : borrar) tocados.add(app.get(id)[0]);
        for (String[] f : nuevas) tocados.add(f[0]);
        for (String doc : tocados) {
            Long numero = entero(doc);
            if (numero != null && numero == 0) continue;
            long antes = sumaAntes.containsKey(doc) ? sumaAntes.get(doc) : 0L;
            long despues = sumaDespues.containsKey(doc) ? sumaDespues.get(doc) : 0L;
            if (despues != 0 && despues != antes) {
                a.errores.add("El documento " + doc + " no queda en cero: sus valores sumarían " + despues
                        + " (los positivos deben igualar a los negativos" + (antes != 0 ? "; antes sumaba " + antes : "") + ").");
            }
        }
        if (!a.errores.isEmpty()) return;

        // 5. Preparar operaciones y la vista previa
        for (Long id : cambiadas) {
            String[] ant = app.get(id), f = fin.get(id);
            ContentValues cv = new ContentValues();
            StringBuilder det = new StringBuilder();
            for (int k = 0; k < 8; k++) {
                if (ant[k].equals(f[k])) continue;
                ponerTransaccion(cv, COLS_TRANS[k], f[k]);
                String antTxt = ant[k], nuevoTxt = f[k];
                if (k == 2) {
                    Long cuenta = entero(f[2]);
                    antTxt = nombre(cuentas, ant[2]);
                    nuevoTxt = cuentas.get(cuenta);
                    Long tipo = tipoDeCuenta.get(cuenta);
                    if (tipo != null) cv.put("tipo_cuenta_id", tipo); else cv.putNull("tipo_cuenta_id");
                }
                det.append(det.length() > 0 ? "; " : "").append(legible(COLS_TRANS[k])).append(" ")
                        .append(antTxt).append(" → ").append(nuevoTxt);
            }
            if (cv.containsKey("c1_Documento") || cv.containsKey("c2_ItemDoc")) {
                cv.put("c1_Documento", f[0]);
                cv.put("c2_ItemDoc", f[1]);
                a.cambiaDocumentoItem = true;
            }
            a.actualizaciones.put(id, cv);
            a.modificadas++;
            cambio(a, "Modificada id " + id + " (doc " + ant[0] + "-" + ant[1] + "): " + det);
        }
        for (String[] f : nuevas) {
            a.inserciones.add(valoresTransaccion(f, tipoDeCuenta.get(entero(f[2]))));
            a.nuevas++;
            cambio(a, "Nueva: doc " + f[0] + "-" + f[1] + " · " + cuentas.get(entero(f[2])) + " · " + f[3]);
        }
        for (Long id : borrar) {
            String[] f = app.get(id);
            a.borrados.add(id);
            a.borradas++;
            cambio(a, "Borrada id " + id + ": doc " + f[0] + "-" + f[1] + " · " + nombre(cuentas, f[2]) + " · " + f[3]);
        }
        a.sinCambio = Math.max(0, fin.size() - a.modificadas);
    }

    /** Valida una fila de transacción; si 'ant' no es null, solo los campos que cambian. */
    private static void validarTransaccion(String[] f, String[] ant, int n, Map<Long, String> cuentas, Analisis a) {
        boolean[] cambia = new boolean[8];
        for (int k = 0; k < 8; k++) cambia[k] = ant == null || !ant[k].equals(f[k]);
        if (cambia[0] && f[0].isEmpty()) error(a, n, "falta el Documento.");
        if (cambia[1] && f[1].isEmpty()) error(a, n, "falta el Ítem documento.");
        Long cuenta = entero(f[2]);
        if (cambia[2] && (cuenta == null || !cuentas.containsKey(cuenta))) {
            error(a, n, "cuenta_id \"" + f[2] + "\" no existe (búscalo en Catálogos).");
        }
        if (entero(f[3]) == null) error(a, n, "el Valor \"" + f[3] + "\" debe ser un número entero, sin puntos ni comas.");
        if (cambia[4] && f[4].isEmpty()) error(a, n, "falta la Descripción.");
        if (cambia[5] && f[5].isEmpty()) error(a, n, "falta la Fecha y hora.");
        if (cambia[6] && !f[6].isEmpty() && !"0".equals(f[6]) && (entero(f[6]) == null || f[6].length() != 8)) {
            error(a, n, "la Fecha inicial \"" + f[6] + "\" debe ser AAAAMMDD.");
        }
        if (cambia[7] && f[7].isEmpty()) error(a, n, "falta la Fecha modificación.");
    }

    private static Map<String, Long> sumasPorDocumento(java.util.Collection<String[]> filas) {
        Map<String, Long> m = new HashMap<>();
        for (String[] f : filas) {
            Long val = entero(f[3]);
            m.put(f[0], (m.containsKey(f[0]) ? m.get(f[0]) : 0L) + (val == null ? 0 : val));
        }
        return m;
    }

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

    private static void analizarCuentas(SQLiteDatabase db, List<String[]> filas, Map<String, Integer> col,
                                        Base base, Analisis a) {
        if (faltan(col, a, "cuenta_id", "Item", "Cuenta", "Fecha")) return;
        List<String> cols = base.columnas;
        for (String c : cols) {
            if (!col.containsKey(c)) {
                a.errores.add("Falta la columna \"" + legibleCuenta(c) + "\".");
            }
        }
        if (!a.errores.isEmpty()) return;
        Set<Long> tipos = ids(db, "SELECT tipo_cuenta_id FROM " + A1_1_AyudanteBD.TABLE_TIPO_CUENTA);
        Set<Long> renglones = ids(db, "SELECT renglon_id FROM " + A1_1_AyudanteBD.TABLE_RENGLON_RENTA);
        Map<Long, String[]> app = cargar(db, TIPO_CUENTAS).filas;
        int iCuenta = cols.indexOf("Cuenta");
        Map<String, String> historialHoja = new HashMap<>();          // ⭐ (24-nov)
        java.util.Set<Integer> historialNuevas = new HashSet<>();

        Map<Long, String[]> hoja = new LinkedHashMap<>();
        Map<Long, Integer> filaDeId = new HashMap<>();
        List<String[]> nuevas = new ArrayList<>();
        List<Integer> filaNueva = new ArrayList<>();
        for (int i = 1; i < filas.size(); i++) {
            String[] fila = filas.get(i);
            if (vacia(fila)) continue;
            int n = i + 1;
            String[] s = new String[cols.size()];
            for (int k = 0; k < s.length; k++) s[k] = v(fila, col, cols.get(k));
            String idTxt = v(fila, col, "cuenta_id");
            String hist = v(fila, col, "con_historial"); // ⭐ (24-nov) "Historial"
            if (!hist.isEmpty() && !"0".equals(hist) && !"1".equals(hist)) error(a, n, "Historial debe ser 0 o 1.");
            if (idTxt.isEmpty()) {
                if ("1".equals(hist)) historialNuevas.add(nuevas.size());
                nuevas.add(s);
                filaNueva.add(n);
                continue;
            }
            if (!hist.isEmpty()) historialHoja.put(idTxt, hist);
            Long id = entero(idTxt);
            if (id == null) error(a, n, "cuenta_id \"" + idTxt + "\" no es válido.");
            else if (hoja.containsKey(id)) error(a, n, "cuenta_id " + idTxt + " está repetido (para agregar una cuenta, deja cuenta_id vacío).");
            else {
                hoja.put(id, s);
                filaDeId.put(id, n);
            }
        }
        Set<Long> cambiadas = new java.util.LinkedHashSet<>();
        List<Long> borrar = new ArrayList<>();
        Map<Long, String[]> fin = fusionar(base, app, hoja, filaDeId, a, cambiadas, borrar,
                id -> "la cuenta \"" + (app.containsKey(id) ? app.get(id)[iCuenta] : String.valueOf(id)) + "\" (id " + id + ")");

        for (Long id : cambiadas) validarCuenta(cols, fin.get(id), app.get(id), filaDeId.get(id), tipos, renglones, a);
        for (int i = 0; i < nuevas.size(); i++) validarCuenta(cols, nuevas.get(i), null, filaNueva.get(i), tipos, renglones, a);
        // Nombres únicos en el resultado final
        Map<String, String> nombres = new HashMap<>();
        for (Map.Entry<Long, String[]> e : fin.entrySet()) {
            String nom = e.getValue()[iCuenta];
            if (nombres.containsKey(nom)) a.errores.add("La cuenta \"" + nom + "\" quedaría repetida.");
            nombres.put(nom, "id " + e.getKey());
        }
        for (int i = 0; i < nuevas.size(); i++) {
            String nom = nuevas.get(i)[iCuenta];
            if (nombres.containsKey(nom)) error(a, filaNueva.get(i), "la cuenta \"" + nom + "\" ya existe (" + nombres.get(nom) + ").");
            else nombres.put(nom, "fila " + filaNueva.get(i));
        }
        List<String> tablasConCuenta = tablasQueUsanCuenta(db);
        for (Long id : borrar) {
            String uso = usoDeCuenta(db, tablasConCuenta, id);
            if (uso != null) a.errores.add("La cuenta \"" + app.get(id)[iCuenta] + "\" (id " + id
                    + ") no está en la hoja, pero se usa en " + uso + ": no se puede borrar.");
        }
        if (!a.errores.isEmpty()) return;

        for (Long id : cambiadas) {
            String[] ant = app.get(id), f = fin.get(id);
            ContentValues cv = new ContentValues();
            StringBuilder det = new StringBuilder();
            for (int k = 0; k < cols.size(); k++) {
                if (ant[k].equals(f[k])) continue;
                ponerCuenta(cv, cols.get(k), f[k]);
                if ("tipo_cuenta_id".equals(cols.get(k))) a.cambiaTipoCuenta = true;
                det.append(det.length() > 0 ? "; " : "").append(legibleCuenta(cols.get(k))).append(" ")
                        .append(ant[k]).append(" → ").append(f[k]);
            }
            a.actualizaciones.put(id, cv);
            a.modificadas++;
            cambio(a, "Modificada " + ant[iCuenta] + " (id " + id + "): " + det);
        }
        for (int i = 0; i < nuevas.size(); i++) {
            String[] f = nuevas.get(i);
            ContentValues cv = new ContentValues();
            for (int k = 0; k < cols.size(); k++) ponerCuenta(cv, cols.get(k), f[k]);
            if (historialNuevas.contains(i)) a.historialEnNuevas.add(a.inserciones.size());
            a.inserciones.add(cv);
            a.nuevas++;
            cambio(a, "Nueva cuenta: " + f[iCuenta] + (historialNuevas.contains(i) ? " (con historial)" : ""));
        }
        // ⭐ (24-nov, Jorge): cambios de "Historial" (comparado con la app).
        try (Cursor k = db.rawQuery("SELECT cuenta_id, IFNULL(con_historial, 0) FROM cuentas", null)) {
            while (k.moveToNext()) {
                String hist = historialHoja.get(k.getString(0));
                if (hist == null || hist.equals(k.getString(1))) continue;
                long id = k.getLong(0);
                String nombre = app.containsKey(id) ? app.get(id)[iCuenta] : String.valueOf(id);
                if ("1".equals(hist)) {
                    a.activarHistorial.add(id);
                    cambio(a, "Historial activado: " + nombre + " (arranca con su saldo inicial)");
                } else {
                    ContentValues cv = a.actualizaciones.containsKey(id) ? a.actualizaciones.get(id) : new ContentValues();
                    cv.put("con_historial", 0);
                    if (!a.actualizaciones.containsKey(id)) a.modificadas++;
                    a.actualizaciones.put(id, cv);
                    cambio(a, "Historial desactivado: " + nombre + " (lo guardado se conserva)");
                }
            }
        }
        for (Long id : a.activarHistorial) if (!a.actualizaciones.containsKey(id)) a.modificadas++;
        for (Long id : borrar) {
            a.borrados.add(id);
            a.borradas++;
            cambio(a, "Borrada cuenta: " + app.get(id)[iCuenta] + " (id " + id + ")");
        }
        a.sinCambio = Math.max(0, fin.size() - a.modificadas);
    }

    private static void validarCuenta(List<String> cols, String[] f, String[] ant, int n, Set<Long> tipos,
                                      Set<Long> renglones, Analisis a) {
        for (int k = 0; k < cols.size(); k++) {
            if (ant != null && ant[k].equals(f[k])) continue;
            String c = cols.get(k), val = f[k];
            if (("Cuenta".equals(c) || "Item".equals(c) || "Fecha".equals(c)) && val.isEmpty()) {
                error(a, n, "falta " + legibleCuenta(c) + ".");
            } else if (COLS_0_1.contains(c) && !"0".equals(val) && !"1".equals(val)) {
                error(a, n, legibleCuenta(c) + " debe ser 0 o 1.");
            } else if (COLS_ENTERAS.contains(c) && !val.isEmpty() && entero(val) == null) {
                error(a, n, legibleCuenta(c) + " \"" + val + "\" debe ser un número.");
            } else if ("tipo_cuenta_id".equals(c) && !val.isEmpty() && !tipos.contains(entero(val))) {
                error(a, n, "tipo_cuenta_id " + val + " no existe (búscalo en Catálogos).");
            } else if ("renglon_renta_id".equals(c) && !val.isEmpty() && !renglones.contains(entero(val))) {
                error(a, n, "renglon_renta_id " + val + " no existe (búscalo en Catálogos).");
            }
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
            for (int i = 0; i < a.inserciones.size(); i++) {
                long nuevoId = db.insert(tabla, null, a.inserciones.get(i));
                if (nuevoId == -1) {
                    throw new IllegalStateException("No se pudo insertar una fila nueva en " + tabla);
                }
                if (cuentas && a.historialEnNuevas.contains(i)) A1_1_AyudanteBD.activarHistorial(db, nuevoId);
            }
            for (Long id : a.activarHistorial) A1_1_AyudanteBD.activarHistorial(db, id); // ⭐ (24-nov)
            if (cuentas && a.cambiaTipoCuenta) A1_1_AyudanteBD.alinearTipoCuentaDeTransacciones(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    // ⭐ CAMBIO (22-nov, Jorge): al aplicar, la hoja de transacciones o cuentas se pone al día con
    // la app (misma hoja) y se puede seguir corrigiendo; el nombre y las propiedades llevan la
    // cuenta de aplicaciones y de cambios. La de saldos iniciales solo se marca.
    public static final String MARCA_APLICADA = " ✓ aplicada ";
    private static final String PROP_APLICACIONES = "aplicaciones";
    private static final String PROP_CAMBIOS = "cambios";

    /** Devuelve una línea para el resumen final. */
    public static String actualizarTrasAplicar(Context context, Analisis a) {
        HojaDrive hoja = a.hoja;
        int aplicaciones = hoja.aplicaciones + 1;
        int cambios = hoja.cambiosTotales + a.nuevas + a.modificadas + a.borradas;
        String base = hoja.nombre.contains(MARCA_APLICADA)
                ? hoja.nombre.substring(0, hoja.nombre.indexOf(MARCA_APLICADA)) : hoja.nombre;
        String nombre = base + MARCA_APLICADA + aplicaciones + (aplicaciones == 1 ? " vez" : " veces") + " · "
                + new java.text.SimpleDateFormat("d MMM HH:mm", new java.util.Locale("es", "CO")).format(new java.util.Date());
        Map<String, String> props = new HashMap<>();
        props.put(PROP_APLICACIONES, String.valueOf(aplicaciones));
        props.put(PROP_CAMBIOS, String.valueOf(cambios));
        try {
            if (TIPO_SALDOS.equals(hoja.tipo)) {
                Drive drive = A32_Respaldos.servicioDrive(context);
                com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
                meta.setName(nombre);
                meta.setAppProperties(props);
                drive.files().update(hoja.id, meta).execute();
                return "La plantilla quedó marcada como aplicada en Drive. Para más saldos, envía una plantilla nueva.";
            }
            boolean[] nueva = new boolean[1];
            A33_Sheets.Hoja h = A33_Sheets.reescribirHoja(context, hoja.id, hoja.tipo, nombre, props, nueva);
            return nueva[0]
                    ? "Drive no dejó poner al día la misma hoja: se creó una nueva con los datos de la app"
                        + " (la anterior fue a la papelera). Ábrela desde Drive › Backups › Sheets."
                    : "La hoja se puso al día con la app (mismo enlace): puedes seguir corrigiéndola"
                        + " durante " + VENTANA_HORAS + " horas más."
                        + " Lleva " + aplicaciones + (aplicaciones == 1 ? " aplicación" : " aplicaciones")
                        + " y " + cambios + " cambios. Las pestañas o formatos que agregaste se reemplazaron.";
        } catch (Exception e) {
            Log.w(TAG, "No se pudo poner al día la hoja: " + e.getMessage());
            return "⚠ No se pudo poner al día la hoja en Drive (" + e.getMessage()
                    + "). Antes de seguir corrigiendo, envía una hoja nueva desde la app.";
        }
    }

    /** Texto corto para el resumen final. */
    public static String detalle(Analisis a) {
        return "Hoja: " + a.hoja.nombre + "\nNuevas: " + a.nuevas + " · Modificadas: " + a.modificadas
                + " · Borradas: " + a.borradas + " · Sin cambio: " + a.sinCambio;
    }
}
