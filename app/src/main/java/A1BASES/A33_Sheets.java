package A1BASES;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.FileList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ⭐ NUEVO (22-nov, Jorge, etapa S1 de Sheets): "Enviar a Sheets".
 *
 * Sheets es la herramienta de emergencia para correcciones masivas de transacciones y cuentas
 * (y para cargar saldos iniciales). Antes el usuario buscaba un CSV en Drive, lo abría, lo copiaba
 * a una hoja fija compartida con "cualquier persona con el enlace" y luego la importaba. Ahora la
 * app crea las hojas directamente, PRIVADAS, en Drive › Backups › Sheets, con el formato del
 * modelo actual:
 *  - transacciones y cuentas: todas sus columnas, con el id de cada fila primero y, junto a cada
 *    id que apunta a otra tabla, una columna "(referencia)" con su nombre (no se importa);
 *  - catálogos (solo consulta): cuentas, tipos de cuenta y renglones de renta con id y nombre,
 *    para buscar el id correcto al corregir;
 *  - plantilla de saldos iniciales: una fila por cuenta, con el valor en blanco para llenar.
 *
 * Solo usa la API de Drive (sin la API de Sheets, decisión de Jorge): cada tabla es un archivo.
 * ⭐ (22-nov) Se sube como .xlsx (A35_Xlsx) con conversión a hoja de Google, para llevar la
 * identidad de la app: encabezado morado, fondo rosado, azul rey en lo fijo, fucsia en lo
 * editable, fila 1 y columna A inmovilizadas y filtro listo. Se conservan 3 por tipo.
 * La importación de vuelta ("Traer de Sheets", con validación y vista previa) es la etapa S2.
 */
public class A33_Sheets {

    private static final String TAG = "A33_Sheets";
    public static final String CARPETA_SHEETS = "Sheets";
    // ⭐ (22-nov, Jorge): una subcarpeta por opción de "Enviar a Sheets", para no mezclar archivos.
    public static final String SUBCARPETA_CORRECCIONES = "Correcciones";
    public static final String SUBCARPETA_SALDOS = "Saldos iniciales";
    private static final String MIME_HOJA = "application/vnd.google-apps.spreadsheet";
    private static final String MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_CARPETA = "application/vnd.google-apps.folder";
    // ⭐ (22-nov, Jorge): una sola hoja por tabla y equipo; las anteriores van a la papelera.
    private static final int MAX_POR_TIPO = 1;
    private static final String PREFS = "a33_sheets";
    private static final String CLAVE_CARPETA = "carpeta_sheets_id";

    public static final String TIPO_TRANSACCIONES = "transacciones";
    public static final String TIPO_CUENTAS = "cuentas";
    public static final String TIPO_CATALOGOS = "catalogos";
    public static final String TIPO_SALDOS = "saldos-iniciales";

    // ⭐ NUEVO (22-nov, Jorge): encabezados legibles en las hojas, sin los prefijos técnicos de
    // la base (c1_, c5_…). La etapa S2 usa este mismo mapa para volver a las columnas reales.
    // (Pendiente aparte: quitar esos prefijos de los nombres de columna en el código y la base.)
    public static final java.util.Map<String, String> NOMBRE_LEGIBLE = new java.util.LinkedHashMap<>();
    static {
        NOMBRE_LEGIBLE.put("c1_Documento", "Documento");
        NOMBRE_LEGIBLE.put("c2_ItemDoc", "Ítem documento");
        NOMBRE_LEGIBLE.put("c5_Valor", "Valor");
        NOMBRE_LEGIBLE.put("c6_Descripcion", "Descripción");
        NOMBRE_LEGIBLE.put("c7_FechaYHora", "Fecha y hora");
        NOMBRE_LEGIBLE.put("c8_FechaInicial", "Fecha inicial");
        NOMBRE_LEGIBLE.put("c9_FechaModificacion", "Fecha modificación");
        NOMBRE_LEGIBLE.put("Item", "Ítem");
        NOMBRE_LEGIBLE.put("codigo_cuenta", "Código cuenta");
        NOMBRE_LEGIBLE.put("cuenta_seguimiento", "Cuenta seguimiento");
        NOMBRE_LEGIBLE.put("conciliable", "Conciliable");
        NOMBRE_LEGIBLE.put("con_inventario", "Con inventario");
        NOMBRE_LEGIBLE.put("entidad", "Entidad");
        NOMBRE_LEGIBLE.put("tipo_soporte", "Tipo de soporte");
        NOMBRE_LEGIBLE.put("con_historial", "Historial");          // ⭐ (24-nov) editable: 0/1
        NOMBRE_LEGIBLE.put("historial_desde", "Historial asignado en"); // ⭐ (25-nov, Jorge): semántica
    }

    /** Columna real → expresión SQL con su encabezado legible. */
    private static String columnaLegible(String alias, String col) {
        String legible = NOMBRE_LEGIBLE.get(col);
        return alias + ".\"" + col + "\"" + (legible != null ? " AS \"" + legible + "\"" : "");
    }

    public static class Hoja {
        public String titulo = "";
        public String nombre = "";
        public String enlace;
        public String id;
        public long filas;
    }

    public static class Envio {
        public boolean ok;
        public String mensaje = "";
        public String aviso = "";
        public List<Hoja> hojas = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────
    // Envíos (fuera del hilo principal)
    // ─────────────────────────────────────────────────────────────

    /** Transacciones, cuentas y catálogos: para corregir registros. */
    public static Envio enviarTablas(Context context) {
        Envio r = new Envio();
        File carpeta = carpetaTemporal(context);
        File fTrans = new File(carpeta, "transacciones.xlsx");
        File fCuentas = new File(carpeta, "cuentas.xlsx");
        File fCatalogos = new File(carpeta, "catalogos.xlsx");
        long[] filas = new long[3];
        SQLiteDatabase db = abrirBase(context);
        try {
            List<A35_Xlsx.Hoja> libroTrans = libroTransacciones(db);
            List<A35_Xlsx.Hoja> libroCuentas = libroCuentas(db);
            A35_Xlsx.Hoja cuentas = libroCuentas.get(0);
            List<A35_Xlsx.Hoja> catalogos = hojasCatalogos(db);
            filas[0] = libroTrans.get(0).filas.size();
            filas[1] = cuentas.filas.size();
            for (A35_Xlsx.Hoja h : catalogos) filas[2] += h.filas.size();
            A35_Xlsx.escribir(fTrans, libroTrans);
            A35_Xlsx.escribir(fCuentas, libroCuentas);
            A35_Xlsx.escribir(fCatalogos, catalogos);
        } catch (Exception e) {
            Log.e(TAG, "No se pudieron preparar las hojas", e);
            r.mensaje = "No se pudieron preparar los datos: " + e.getMessage();
            return r;
        } finally {
            db.close();
        }
        try {
            Drive drive = A32_Respaldos.servicioDrive(context);
            String idCarpeta = carpetaDeEquipo(context, drive, SUBCARPETA_CORRECCIONES);
            r.hojas.add(subir(context, drive, idCarpeta, fTrans, TIPO_TRANSACCIONES, "Transacciones", filas[0]));
            r.hojas.add(subir(context, drive, idCarpeta, fCuentas, TIPO_CUENTAS, "Cuentas", filas[1]));
            r.hojas.add(subir(context, drive, idCarpeta, fCatalogos, TIPO_CATALOGOS, "Catálogos (solo consulta)", filas[2]));
            r.ok = true;
            r.mensaje = "Hojas creadas en Drive › Backups › " + CARPETA_SHEETS + " › " + SUBCARPETA_CORRECCIONES
                    + " › " + A32_Respaldos.equipo(context);
        } catch (Exception e) {
            Log.e(TAG, "No se pudieron subir las hojas", e);
            r.mensaje = "No se pudieron crear las hojas en Drive: " + e.getMessage();
        } finally {
            fTrans.delete();
            fCuentas.delete();
            fCatalogos.delete();
        }
        return r;
    }

    /**
     * ⭐ NUEVO (22-nov, Jorge): después de "Traer de Sheets", la MISMA hoja (mismo archivo y
     * enlace) se reescribe con lo que quedó en la app, para que el usuario siga corrigiendo sobre
     * datos al día (ids de filas nuevas, borradas fuera). Si Drive no deja reescribirla, se crea
     * una hoja nueva en la misma carpeta y la vieja va a la papelera.
     * Devuelve la hoja resultante (enlace) o null si no se pudo; 'nueva' = true si es otra hoja.
     */
    public static Hoja reescribirHoja(Context context, String idHoja, String tipo, String nombre,
                                      java.util.Map<String, String> propiedades, boolean[] nueva) throws Exception {
        File archivo = new File(carpetaTemporal(context), "refresco.xlsx");
        long filas;
        SQLiteDatabase db = abrirBase(context);
        try {
            List<A35_Xlsx.Hoja> libro = TIPO_CUENTAS.equals(tipo)
                    ? libroCuentas(db)
                    : libroTransacciones(db);
            filas = libro.get(0).filas.size();
            A35_Xlsx.escribir(archivo, libro);
        } finally {
            db.close();
        }
        Drive drive = A32_Respaldos.servicioDrive(context);
        try {
            com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
            meta.setName(nombre);
            meta.setAppProperties(propiedades);
            com.google.api.services.drive.model.File f;
            try {
                f = drive.files().update(idHoja, meta, new FileContent(MIME_XLSX, archivo))
                        .setFields("id, name, webViewLink").execute();
                nueva[0] = false;
            } catch (Exception e) {
                Log.w(TAG, "Drive no dejó reescribir la hoja; se crea una nueva: " + e.getMessage());
                com.google.api.services.drive.model.File vieja = drive.files().get(idHoja).setFields("parents").execute();
                meta.setMimeType(MIME_HOJA);
                meta.setParents(vieja.getParents());
                f = drive.files().create(meta, new FileContent(MIME_XLSX, archivo))
                        .setFields("id, name, webViewLink").execute();
                com.google.api.services.drive.model.File papelera = new com.google.api.services.drive.model.File();
                papelera.setTrashed(true);
                drive.files().update(idHoja, papelera).execute();
                nueva[0] = true;
            }
            // ⭐ (22-nov): nueva base de la hoja (empieza otra ventana de tiempo).
            A36_TraerSheets.guardarBase(context, f.getId(), tipo);
            if (nueva[0]) A36_TraerSheets.borrarBase(context, idHoja);
            Hoja r = new Hoja();
            r.id = f.getId();
            r.nombre = f.getName();
            r.enlace = f.getWebViewLink();
            r.filas = filas;
            return r;
        } finally {
            archivo.delete();
        }
    }

    /** Una fila por cuenta, con el valor en blanco: para cargar saldos iniciales. */
    public static Envio enviarPlantillaSaldos(Context context) {
        Envio r = new Envio();
        File carpeta = carpetaTemporal(context);
        File fSaldos = new File(carpeta, "saldos.xlsx");
        File fCatalogos = new File(carpeta, "catalogos.xlsx");
        long[] filas = new long[2];
        SQLiteDatabase db = abrirBase(context);
        try {
            // ⭐ (22-nov, Jorge): condiciones de los saldos iniciales.
            long cuentas = numero(db, "SELECT COUNT(*) FROM cuentas WHERE TRIM(Cuenta) <> '' AND LOWER(TRIM(Cuenta)) <> 'n a'");
            if (cuentas == 0) {
                r.mensaje = "Primero crea las cuentas (F2): la plantilla de saldos iniciales trae una fila por cuenta.";
                return r;
            }
            long movimientos = numero(db, "SELECT COUNT(*) FROM transacciones WHERE CAST(c1_Documento AS INTEGER) <> 0");
            long primera = numero(db, "SELECT MIN(c8_FechaInicial) FROM transacciones "
                    + "WHERE CAST(c1_Documento AS INTEGER) <> 0 AND c8_FechaInicial > 19000101");
            Date fecha = new Date();
            if (movimientos > 0 && primera > 0) {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.set((int) (primera / 10000), (int) ((primera / 100) % 100) - 1, (int) (primera % 100), 0, 0, 0);
                cal.add(java.util.Calendar.DAY_OF_MONTH, -1);
                fecha = cal.getTime();
                r.aviso = "Ya hay " + movimientos + " transacciones registradas desde el "
                        + new SimpleDateFormat("d MMM yyyy", new Locale("es", "CO")).format(new Date(fecha.getTime() + 86400000L))
                        + ". La plantilla trae como fecha el día anterior al primer movimiento ("
                        + new SimpleDateFormat("d MMM yyyy", new Locale("es", "CO")).format(fecha) + ").";
            }
            A35_Xlsx.Hoja saldos = hojaPlantillaSaldos(db, fecha, movimientos > 0);
            List<A35_Xlsx.Hoja> catalogos = hojasCatalogos(db);
            filas[0] = saldos.filas.size();
            for (A35_Xlsx.Hoja h : catalogos) filas[1] += h.filas.size();
            A35_Xlsx.escribir(fSaldos, Collections.singletonList(saldos));
            A35_Xlsx.escribir(fCatalogos, catalogos);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo preparar la plantilla", e);
            r.mensaje = "No se pudo preparar la plantilla: " + e.getMessage();
            return r;
        } finally {
            db.close();
        }
        try {
            Drive drive = A32_Respaldos.servicioDrive(context);
            String idCarpeta = carpetaDeEquipo(context, drive, SUBCARPETA_SALDOS);
            r.hojas.add(subir(context, drive, idCarpeta, fSaldos, TIPO_SALDOS, "Plantilla de saldos iniciales", filas[0]));
            r.hojas.add(subir(context, drive, idCarpeta, fCatalogos, TIPO_CATALOGOS, "Catálogos (solo consulta)", filas[1]));
            r.ok = true;
            r.mensaje = "Hojas creadas en Drive › Backups › " + CARPETA_SHEETS + " › " + SUBCARPETA_SALDOS
                    + " › " + A32_Respaldos.equipo(context);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo subir la plantilla", e);
            r.mensaje = "No se pudo crear la plantilla en Drive: " + e.getMessage();
        } finally {
            fSaldos.delete();
            fCatalogos.delete();
        }
        return r;
    }

    // ⭐ (22-nov, Jorge): columnas que el usuario SÍ puede cambiar (texto fucsia en la hoja); las
    // demás (ids propios y referencias) van en azul rey. Una columna nueva que no esté aquí queda
    // fija por seguridad. El cuenta_id de una transacción se puede cambiar por otro existente.
    static final java.util.Set<String> EDITABLES_TRANSACCIONES = new java.util.HashSet<>(java.util.Arrays.asList(
            "Documento", "Ítem documento", "Valor", "Descripción", "Fecha y hora", "Fecha inicial",
            "Fecha modificación", "cuenta_id"));
    static final java.util.Set<String> EDITABLES_CUENTAS = new java.util.HashSet<>(java.util.Arrays.asList(
            "Ítem", "Cuenta", "Fecha", "Código cuenta", "Cerrable", "tipo_cuenta_id", "Cuenta seguimiento",
            "Conciliable", "Con inventario", "renglon_renta_id", "Entidad", "Tipo de soporte", "Historial"));
    static final java.util.Set<String> EDITABLES_SALDOS = new java.util.HashSet<>(java.util.Arrays.asList(
            "Valor", "Descripción", "Fecha y hora", "Fecha inicial"));

    // ─────────────────────────────────────────────────────────────
    // Consultas
    // ─────────────────────────────────────────────────────────────

    private static SQLiteDatabase abrirBase(Context context) {
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context, A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null, A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        return ayudante.getReadableDatabase();
    }

    private static List<String> columnas(SQLiteDatabase db, String tabla) {
        List<String> lista = new ArrayList<>();
        try (Cursor c = db.rawQuery("PRAGMA table_info(" + tabla + ")", null)) {
            while (c.moveToNext()) lista.add(c.getString(1));
        }
        return lista;
    }

    /**
     * Todas las columnas de transacciones (transaccion_id primero), con el nombre de la cuenta y
     * del tipo de cuenta como referencia junto a sus ids.
     */
    private static String sqlTransacciones(SQLiteDatabase db) {
        StringBuilder sb = new StringBuilder("SELECT t.transaccion_id");
        for (String col : columnas(db, "transacciones")) {
            if (col.equals("transaccion_id")) continue;
            sb.append(", ").append(columnaLegible("t", col));
            if (col.equals("tipo_cuenta_id")) sb.append(", tc.nombre AS \"Tipo de cuenta (referencia)\"");
        }
        sb.append(" FROM transacciones t")
                .append(" LEFT JOIN cuentas c ON c.cuenta_id = t.cuenta_id")
                .append(" LEFT JOIN ").append(A1_1_AyudanteBD.TABLE_TIPO_CUENTA)
                .append(" tc ON tc.tipo_cuenta_id = t.tipo_cuenta_id")
                .append(" ORDER BY CAST(t.c1_Documento AS INTEGER), CAST(t.c2_ItemDoc AS INTEGER)");
        return sb.toString();
    }

    /**
     * ⭐ NUEVO (23-nov, Jorge): el archivo de transacciones con tres pestañas. "Transacciones" va
     * primero, porque "Traer de Sheets" lee solo la primera.
     *  - Transacciones: al final, "Cuenta (referencia)" con BUSCARV: el nombre cambia solo si el
     *    usuario cambia el cuenta_id (no se importa, termina en "(referencia)");
     *  - Conciliación: suma neta, positivos, negativos y comprobación, con fórmulas vivas;
     *  - Cuentas (consulta): id y nombre, la tabla que usa el BUSCARV.
     */
    private static List<A35_Xlsx.Hoja> libroTransacciones(SQLiteDatabase db) {
        A35_Xlsx.Hoja trans = leer(db, sqlTransacciones(db), "Transacciones", EDITABLES_TRANSACCIONES);
        // ⭐ (23-nov, Jorge): con el tipo de cuenta, para que tipo_cuenta_id y su referencia también
        // sigan al cuenta_id (la app recalcula el tipo al aplicar; esto es lo que ve el usuario).
        A35_Xlsx.Hoja nombres = leer(db, "SELECT c.cuenta_id, c.Cuenta, c.tipo_cuenta_id, tc.nombre AS \"Tipo de cuenta\""
                + " FROM cuentas c LEFT JOIN " + A1_1_AyudanteBD.TABLE_TIPO_CUENTA
                + " tc ON tc.tipo_cuenta_id = c.tipo_cuenta_id ORDER BY c.cuenta_id", "Cuentas (consulta)", null);
        String rangoNombres = "'" + nombres.nombre + "'!A:D";
        int colCuenta = trans.columnas.indexOf("cuenta_id");
        int colValor = trans.columnas.indexOf("Valor");
        if (colCuenta >= 0) {
            String letraCuenta = A35_Xlsx.letra(colCuenta);
            // tipo_cuenta_id (col. 3 de la consulta) y su nombre (col. 4), por fórmula.
            int[] colsTipo = {trans.columnas.indexOf("tipo_cuenta_id"), trans.columnas.indexOf("Tipo de cuenta (referencia)")};
            for (int k = 0; k < 2; k++) {
                if (colsTipo[k] < 0) continue;
                trans.formulas.add(colsTipo[k]);
                for (int f = 0; f < trans.filas.size(); f++) {
                    trans.filas.get(f)[colsTipo[k]] = "IFERROR(VLOOKUP(" + letraCuenta + (f + 2) + "," + rangoNombres
                            + "," + (k + 3) + ",FALSE)&\"\",\"\")";
                }
            }
            int nueva = trans.columnas.size();
            trans.columnas.add("Cuenta (referencia)");
            trans.editable.add(false);
            trans.formulas.add(nueva);
            for (int f = 0; f < trans.filas.size(); f++) {
                String[] vieja = trans.filas.get(f);
                String[] fila = java.util.Arrays.copyOf(vieja, nueva + 1);
                fila[nueva] = "IFERROR(VLOOKUP(" + letraCuenta + (f + 2) + "," + rangoNombres
                        + ",2,FALSE),\"⚠ cuenta no existe\")";
                trans.filas.set(f, fila);
            }
        }
        List<A35_Xlsx.Hoja> libro = new ArrayList<>();
        libro.add(trans);
        if (colValor >= 0) {
            String d = "Transacciones!" + A35_Xlsx.letra(colValor) + ":" + A35_Xlsx.letra(colValor);
            A35_Xlsx.Hoja con = new A35_Xlsx.Hoja();
            con.nombre = "Conciliación";
            con.inmovilizarColumnaA = false;
            con.columnas.add("Concepto");
            con.columnas.add("Valor");
            con.editable.add(false);
            con.editable.add(false);
            con.formulas.add(1);
            con.filas.add(new String[]{"Suma neta", "SUM(" + d + ")"});
            con.filas.add(new String[]{"Suma positivos", "SUMIF(" + d + ",\">0\")"});
            con.filas.add(new String[]{"Suma negativos", "SUMIF(" + d + ",\"<0\")"});
            con.filas.add(new String[]{"Comprobación (positivos + negativos, debe dar 0)", "B3+B4"});
            libro.add(con);
        }
        libro.add(nombres);
        return libro;
    }

    /**
     * ⭐ NUEVO (24-nov, Jorge): revisión precisa de cuentas por Sheets. Archivo con tres pestañas;
     * "Cuentas" va primero (Traer de Sheets lee solo la primera):
     *  - Cuentas: las columnas de siempre (editables en lila, ahora también "Historial") y al
     *    final referencias vivas por BUSCARV (tipo, clasificación, clase, naturaleza, liquidez,
     *    soporte sugerido, renglón), contexto fijo (saldo, transacciones, último movimiento,
     *    saldo inicial, líneas en historial) y "Revisión" con avisos por fórmula. Todo lo que
     *    termina en "(referencia)" la app no lo lee al traer.
     *  - Tipos de cuenta (consulta) y Renglones (consulta): las tablas de los BUSCARV.
     */
    private static List<A35_Xlsx.Hoja> libroCuentas(SQLiteDatabase db) {
        A35_Xlsx.Hoja c = leer(db, sqlCuentas(db), "Cuentas", EDITABLES_CUENTAS);
        A35_Xlsx.Hoja tipos = leer(db, "SELECT tc.tipo_cuenta_id, tc.nombre AS \"Tipo de cuenta\","
                + " cl.nombre AS \"Clasificación\", ca.nombre AS \"Clase\", tc.naturaleza_normal AS \"Naturaleza\","
                + " tc.liquidez AS \"Liquidez\", tc.soporte_sugerido AS \"Soporte sugerido\""
                + " FROM " + A1_1_AyudanteBD.TABLE_TIPO_CUENTA + " tc"
                + " LEFT JOIN " + A1_1_AyudanteBD.TABLE_CLASIFICACION_CONTABLE + " cl ON cl.clasificacion_id = tc.clasificacion_id"
                + " LEFT JOIN " + A1_1_AyudanteBD.TABLE_CLASE_CONTABLE + " ca ON ca.clase_id = cl.clase_id"
                + " ORDER BY tc.tipo_cuenta_id", "Tipos de cuenta (consulta)", null);
        A35_Xlsx.Hoja renglones = leer(db, "SELECT renglon_id AS renglon_renta_id, (codigo || ' ' || nombre) AS \"Renglón\","
                + " pais AS \"País\", naturaleza AS \"Naturaleza\" FROM " + A1_1_AyudanteBD.TABLE_RENGLON_RENTA
                + " ORDER BY pais, orden, codigo", "Renglones (consulta)", null);
        // Contexto por cuenta (fijo, del momento del envío).
        java.util.Map<String, String[]> ctx = new java.util.HashMap<>();
        try (Cursor k = db.rawQuery("SELECT cu.cuenta_id,"
                + " (SELECT IFNULL(SUM(c5_Valor), 0) FROM transacciones t WHERE t.cuenta_id = cu.cuenta_id),"
                + " (SELECT COUNT(*) FROM transacciones t WHERE t.cuenta_id = cu.cuenta_id),"
                + " (SELECT MAX(c8_FechaInicial) FROM transacciones t WHERE t.cuenta_id = cu.cuenta_id),"
                + " (SELECT IFNULL(SUM(c5_Valor), 0) FROM transacciones t WHERE t.cuenta_id = cu.cuenta_id"
                + "   AND CAST(t.c1_Documento AS INTEGER) = 0),"
                + " (SELECT COUNT(*) FROM " + A1_1_AyudanteBD.TABLE_HISTORIAL + " h WHERE h.cuenta_id = cu.cuenta_id)"
                + " FROM cuentas cu", null)) {
            while (k.moveToNext()) {
                ctx.put(k.getString(0), new String[]{k.getString(1), k.getString(2),
                        k.isNull(3) ? "" : k.getString(3), k.getString(4), k.getString(5)});
            }
        }
        // Las referencias fijas viejas salen: ahora van por fórmula al final.
        int quitarTipo = c.columnas.indexOf("Tipo de cuenta (referencia)");
        int quitarRenglon = c.columnas.indexOf("Renglón renta (referencia)");
        java.util.List<Integer> quedan = new ArrayList<>();
        for (int k = 0; k < c.columnas.size(); k++) if (k != quitarTipo && k != quitarRenglon) quedan.add(k);
        A35_Xlsx.Hoja h = new A35_Xlsx.Hoja();
        h.nombre = c.nombre;
        for (int k : quedan) {
            h.columnas.add(c.columnas.get(k));
            h.editable.add(c.editable.get(k));
        }
        int base = h.columnas.size();
        String[] nuevas = {"Tipo de cuenta (referencia)", "Clasificación (referencia)", "Clase (referencia)",
                "Naturaleza (referencia)", "Liquidez (referencia)", "Soporte sugerido (referencia)",
                "Renglón renta (referencia)", "Saldo (referencia)", "Transacciones (referencia)",
                "Último movimiento (referencia)", "Saldo inicial (referencia)", "Líneas historial (referencia)",
                "Revisión (referencia)"};
        for (String n : nuevas) {
            h.columnas.add(n);
            h.editable.add(false);
        }
        // Columnas que se calculan con fórmula (las de contexto son valores).
        for (int k = 0; k <= 6; k++) h.formulas.add(base + k);
        h.formulas.add(base + 12);
        String lTipo = A35_Xlsx.letra(h.columnas.indexOf("tipo_cuenta_id"));
        String lRenglon = A35_Xlsx.letra(h.columnas.indexOf("renglon_renta_id"));
        String lSoporte = A35_Xlsx.letra(h.columnas.indexOf("Tipo de soporte"));
        String lNat = A35_Xlsx.letra(base + 3), lSop = A35_Xlsx.letra(base + 5), lSaldo = A35_Xlsx.letra(base + 7);
        String rTipos = "'" + tipos.nombre + "'!A:G", rRenglones = "'" + renglones.nombre + "'!A:B";
        int iId = c.columnas.indexOf("cuenta_id");
        for (int f = 0; f < c.filas.size(); f++) {
            String[] vieja = c.filas.get(f);
            String[] fila = new String[h.columnas.size()];
            for (int k = 0; k < quedan.size(); k++) fila[k] = vieja[quedan.get(k)];
            int r = f + 2;
            for (int k = 0; k < 6; k++) {
                fila[base + k] = "IFERROR(VLOOKUP(" + lTipo + r + "," + rTipos + "," + (k + 2) + ",FALSE)&\"\",\"\")";
            }
            fila[base + 6] = "IFERROR(VLOOKUP(" + lRenglon + r + "," + rRenglones + ",2,FALSE)&\"\",\"\")";
            String[] x = iId >= 0 ? ctx.get(vieja[iId]) : null;
            for (int k = 0; k < 5; k++) fila[base + 7 + k] = x == null ? "" : x[k];
            fila[base + 12] = "IF(TRIM("
                    + "IF(" + lTipo + r + "=\"\",\"⚠ sin tipo \",\"\")"
                    + "&IF(" + lRenglon + r + "=\"\",\"⚠ sin renglón \",\"\")"
                    + "&IF(AND(" + lSoporte + r + "<>\"\"," + lSop + r + "<>\"\"," + lSoporte + r + "<>" + lSop + r
                    + "),\"⚠ soporte ≠ sugerido \",\"\")"
                    + "&IF(OR(AND(" + lNat + r + "=\"DEUDORA\"," + lSaldo + r + "<0),AND(" + lNat + r + "=\"ACREEDORA\","
                    + lSaldo + r + ">0)),\"⚠ saldo contrario a la naturaleza\",\"\")"
                    + ")=\"\",\"✓\",TRIM("
                    + "IF(" + lTipo + r + "=\"\",\"⚠ sin tipo \",\"\")"
                    + "&IF(" + lRenglon + r + "=\"\",\"⚠ sin renglón \",\"\")"
                    + "&IF(AND(" + lSoporte + r + "<>\"\"," + lSop + r + "<>\"\"," + lSoporte + r + "<>" + lSop + r
                    + "),\"⚠ soporte ≠ sugerido \",\"\")"
                    + "&IF(OR(AND(" + lNat + r + "=\"DEUDORA\"," + lSaldo + r + "<0),AND(" + lNat + r + "=\"ACREEDORA\","
                    + lSaldo + r + ">0)),\"⚠ saldo contrario a la naturaleza\",\"\")))";
            h.filas.add(fila);
        }
        List<A35_Xlsx.Hoja> libro = new ArrayList<>();
        libro.add(h);
        libro.add(tipos);
        libro.add(renglones);
        return libro;
    }

    /** Todas las columnas de cuentas (cuenta_id primero), con tipo y renglón como referencia. */
    private static String sqlCuentas(SQLiteDatabase db) {
        StringBuilder sb = new StringBuilder("SELECT c.cuenta_id");
        for (String col : columnas(db, "cuentas")) {
            if (col.equals("cuenta_id")) continue;
            sb.append(", ").append(columnaLegible("c", col));
            if (col.equals("tipo_cuenta_id")) sb.append(", tc.nombre AS \"Tipo de cuenta (referencia)\"");
            if (col.equals("renglon_renta_id")) {
                sb.append(", (r.codigo || ' ' || r.nombre) AS \"Renglón renta (referencia)\"");
            }
        }
        sb.append(" FROM cuentas c")
                .append(" LEFT JOIN ").append(A1_1_AyudanteBD.TABLE_TIPO_CUENTA)
                .append(" tc ON tc.tipo_cuenta_id = c.tipo_cuenta_id")
                .append(" LEFT JOIN ").append(A1_1_AyudanteBD.TABLE_RENGLON_RENTA)
                .append(" r ON r.renglon_id = c.renglon_renta_id")
                .append(" ORDER BY CAST(c.Item AS INTEGER), c.Cuenta");
        return sb.toString();
    }

    /** Lee una consulta como pestaña: encabezados, columnas editables y filas. */
    private static A35_Xlsx.Hoja leer(SQLiteDatabase db, String sql, String nombre, java.util.Set<String> editables) {
        A35_Xlsx.Hoja h = new A35_Xlsx.Hoja();
        h.nombre = nombre;
        try (Cursor c = db.rawQuery(sql, null)) {
            String[] cols = c.getColumnNames();
            for (String col : cols) {
                h.columnas.add(col);
                h.editable.add(editables != null && editables.contains(col));
            }
            while (c.moveToNext()) {
                String[] fila = new String[cols.length];
                for (int i = 0; i < cols.length; i++) fila[i] = c.isNull(i) ? "" : c.getString(i);
                h.filas.add(fila);
            }
        }
        return h;
    }

    /** Catálogos para consulta: una pestaña por catálogo, todo en azul rey (no se importa). */
    private static List<A35_Xlsx.Hoja> hojasCatalogos(SQLiteDatabase db) {
        List<A35_Xlsx.Hoja> hojas = new ArrayList<>();
        hojas.add(leer(db, "SELECT c.cuenta_id, c.Cuenta, tc.nombre AS \"Tipo de cuenta\" FROM cuentas c"
                + " LEFT JOIN " + A1_1_AyudanteBD.TABLE_TIPO_CUENTA + " tc ON tc.tipo_cuenta_id = c.tipo_cuenta_id"
                + " ORDER BY c.Cuenta", "Cuentas", null));
        hojas.add(leer(db, "SELECT tipo_cuenta_id, nombre AS \"Tipo de cuenta\", naturaleza_normal AS \"Naturaleza\" FROM "
                + A1_1_AyudanteBD.TABLE_TIPO_CUENTA + " ORDER BY tipo_cuenta_id", "Tipos de cuenta", null));
        hojas.add(leer(db, "SELECT renglon_id AS renglon_renta_id, pais AS \"País\", codigo AS \"Código\", "
                + "nombre AS \"Renglón\", naturaleza AS \"Naturaleza\" FROM "
                + A1_1_AyudanteBD.TABLE_RENGLON_RENTA + " WHERE vigente_hasta IS NULL"
                + " ORDER BY pais, orden, codigo", "Renglones de renta", null));
        return hojas;
    }

    /**
     * Plantilla de saldos iniciales: documento 0, ítem consecutivo después del último ítem del
     * documento 0 que ya exista, cuenta, valor en blanco (fucsia), descripción y fechas de hoy.
     */
    private static long numero(SQLiteDatabase db, String sql) {
        try (Cursor c = db.rawQuery(sql, null)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0;
        }
    }

    private static A35_Xlsx.Hoja hojaPlantillaSaldos(SQLiteDatabase db, Date fecha, boolean horaCero) {
        long siguiente = 1;
        try (Cursor c = db.rawQuery("SELECT MAX(CAST(c2_ItemDoc AS INTEGER)) FROM transacciones "
                + "WHERE CAST(c1_Documento AS INTEGER) = 0", null)) {
            if (c.moveToFirst() && !c.isNull(0)) siguiente = c.getLong(0) + 1;
        }
        String fechaYHora = new SimpleDateFormat(horaCero ? "yyyy/M/d 0:0" : "yyyy/M/d H:m", Locale.US).format(fecha);
        String fechaInicial = new SimpleDateFormat("yyyyMMdd", Locale.US).format(fecha);
        A35_Xlsx.Hoja h = new A35_Xlsx.Hoja();
        h.nombre = "Saldos iniciales";
        String[] cols = {"Documento", "Ítem documento", "cuenta_id", "Cuenta (referencia)", "Valor", "Descripción",
                "Fecha y hora", "Fecha inicial", "Fecha modificación"};
        for (String col : cols) {
            h.columnas.add(col);
            h.editable.add(EDITABLES_SALDOS.contains(col));
        }
        try (Cursor c = db.rawQuery("SELECT cuenta_id, Cuenta FROM cuentas ORDER BY CAST(Item AS INTEGER), Cuenta", null)) {
            while (c.moveToNext()) {
                h.filas.add(new String[]{"0", String.valueOf(siguiente++), String.valueOf(c.getLong(0)),
                        c.getString(1), "", "Saldos iniciales", fechaYHora, fechaInicial, "n a"});
            }
        }
        return h;
    }

    private static File carpetaTemporal(Context context) {
        File carpeta = new File(context.getCacheDir(), "sheets");
        if (!carpeta.exists()) carpeta.mkdirs();
        return carpeta;
    }

    // ─────────────────────────────────────────────────────────────
    // Drive
    // ─────────────────────────────────────────────────────────────

    /** Carpeta Backups › Sheets (creada por la app; su id se recuerda). */
    static String carpetaSheets(Context context, Drive drive) throws Exception {
        return carpeta(context, drive, A32_Respaldos.FOLDER_ID_BACKUPS, CARPETA_SHEETS);
    }

    /** ⭐ (22-nov, Jorge): Sheets › Correcciones|Saldos iniciales › <equipo>. */
    static String carpetaDeEquipo(Context context, Drive drive, String opcion) throws Exception {
        String idOpcion = subcarpeta(context, drive, carpetaSheets(context, drive), opcion);
        return subcarpeta(context, drive, idOpcion, A32_Respaldos.equipo(context));
    }

    /** Subcarpeta de Sheets por opción (Correcciones, Saldos iniciales). */
    static String subcarpeta(Context context, Drive drive, String idPadre, String nombre) throws Exception {
        return carpeta(context, drive, idPadre, nombre);
    }

    /** Busca (o crea) la carpeta 'nombre' dentro de 'idPadre'; recuerda su id. */
    private static String carpeta(Context context, Drive drive, String idPadre, String nombre) throws Exception {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String clave = CLAVE_CARPETA + "_" + idPadre + "_" + nombre;
        String id = sp.getString(clave, null);
        if (id != null) {
            try {
                com.google.api.services.drive.model.File f = drive.files().get(id).setFields("id, trashed").execute();
                if (f.getTrashed() == null || !f.getTrashed()) return id;
            } catch (Exception e) {
                Log.w(TAG, "La carpeta " + nombre + " recordada ya no está: " + e.getMessage());
            }
        }
        FileList lista = drive.files().list()
                .setQ("'" + idPadre + "' in parents and mimeType='" + MIME_CARPETA + "'"
                        + " and name='" + nombre + "' and trashed=false")
                .setSpaces("drive").setFields("files(id)").execute();
        if (lista.getFiles() != null && !lista.getFiles().isEmpty()) {
            id = lista.getFiles().get(0).getId();
        } else {
            com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
            meta.setName(nombre);
            meta.setMimeType(MIME_CARPETA);
            meta.setParents(Collections.singletonList(idPadre));
            id = drive.files().create(meta).setFields("id").execute().getId();
        }
        sp.edit().putString(clave, id).apply();
        return id;
    }

    /** Sube el .xlsx convertido en hoja de Google y rota a las 3 más recientes de ese tipo y equipo. */
    private static Hoja subir(Context context, Drive drive, String idCarpeta, File archivo, String tipo,
                              String titulo, long filas) throws Exception {
        String prefijo = A32_Respaldos.equipo(context) + "_sheets-" + tipo + "_";
        String nombre = prefijo + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
        meta.setName(nombre);
        meta.setMimeType(MIME_HOJA);
        meta.setParents(Collections.singletonList(idCarpeta));
        com.google.api.services.drive.model.File creado = drive.files()
                .create(meta, new FileContent(MIME_XLSX, archivo))
                .setFields("id, name, webViewLink").execute();
        FileList previas = drive.files().list()
                .setQ("'" + idCarpeta + "' in parents and mimeType='" + MIME_HOJA + "'"
                        + " and name contains '" + prefijo + "' and trashed=false")
                .setSpaces("drive").setFields("files(id, name)").setOrderBy("name desc").setPageSize(100).execute();
        if (previas.getFiles() != null) {
            for (int i = MAX_POR_TIPO; i < previas.getFiles().size(); i++) {
                try {
                    com.google.api.services.drive.model.File papelera = new com.google.api.services.drive.model.File();
                    papelera.setTrashed(true);
                    drive.files().update(previas.getFiles().get(i).getId(), papelera).execute();
                    A36_TraerSheets.borrarBase(context, previas.getFiles().get(i).getId());
                } catch (Exception e) {
                    Log.e(TAG, "No se pudo borrar en Drive: " + previas.getFiles().get(i).getName(), e);
                }
            }
        }
        Hoja h = new Hoja();
        h.titulo = titulo;
        h.nombre = creado.getName();
        h.enlace = creado.getWebViewLink();
        h.id = creado.getId();
        // ⭐ (22-nov, fusión de tres versiones): base de la hoja en el equipo.
        A36_TraerSheets.guardarBase(context, h.id, tipo);
        h.filas = filas;
        return h;
    }
}
