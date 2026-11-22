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
 * Solo usa la API de Drive (sin la API de Sheets, decisión de Jorge): cada tabla es un archivo;
 * el CSV se sube con conversión a hoja de Google. Se conservan las 3 más recientes por tipo.
 * La importación de vuelta ("Traer de Sheets", con validación y vista previa) es la etapa S2.
 */
public class A33_Sheets {

    private static final String TAG = "A33_Sheets";
    public static final String CARPETA_SHEETS = "Sheets";
    private static final String MIME_HOJA = "application/vnd.google-apps.spreadsheet";
    private static final String MIME_CARPETA = "application/vnd.google-apps.folder";
    private static final int MAX_POR_TIPO = 3;
    private static final String PREFS = "a33_sheets";
    private static final String CLAVE_CARPETA = "carpeta_sheets_id";

    public static final String TIPO_TRANSACCIONES = "transacciones";
    public static final String TIPO_CUENTAS = "cuentas";
    public static final String TIPO_CATALOGOS = "catalogos";
    public static final String TIPO_SALDOS = "saldos-iniciales";

    public static class Hoja {
        public String titulo = "";
        public String nombre = "";
        public String enlace;
        public long filas;
    }

    public static class Envio {
        public boolean ok;
        public String mensaje = "";
        public List<Hoja> hojas = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────
    // Envíos (fuera del hilo principal)
    // ─────────────────────────────────────────────────────────────

    /** Transacciones, cuentas y catálogos: para corregir registros. */
    public static Envio enviarTablas(Context context) {
        Envio r = new Envio();
        File carpeta = carpetaTemporal(context);
        File fTrans = new File(carpeta, "transacciones.csv");
        File fCuentas = new File(carpeta, "cuentas.csv");
        File fCatalogos = new File(carpeta, "catalogos.csv");
        long[] filas = new long[3];
        SQLiteDatabase db = abrirBase(context);
        try {
            filas[0] = escribirConsulta(db, sqlTransacciones(db), fTrans, null);
            filas[1] = escribirConsulta(db, sqlCuentas(db), fCuentas, null);
            filas[2] = escribirCatalogos(db, fCatalogos);
        } catch (Exception e) {
            Log.e(TAG, "No se pudieron preparar las hojas", e);
            r.mensaje = "No se pudieron preparar los datos: " + e.getMessage();
            return r;
        } finally {
            db.close();
        }
        try {
            Drive drive = A32_Respaldos.servicioDrive(context);
            String idCarpeta = carpetaSheets(context, drive);
            r.hojas.add(subir(context, drive, idCarpeta, fTrans, TIPO_TRANSACCIONES, "Transacciones", filas[0]));
            r.hojas.add(subir(context, drive, idCarpeta, fCuentas, TIPO_CUENTAS, "Cuentas", filas[1]));
            r.hojas.add(subir(context, drive, idCarpeta, fCatalogos, TIPO_CATALOGOS, "Catálogos (solo consulta)", filas[2]));
            r.ok = true;
            r.mensaje = "Hojas creadas en Drive › Backups › " + CARPETA_SHEETS;
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

    /** Una fila por cuenta, con el valor en blanco: para cargar saldos iniciales. */
    public static Envio enviarPlantillaSaldos(Context context) {
        Envio r = new Envio();
        File carpeta = carpetaTemporal(context);
        File fSaldos = new File(carpeta, "saldos.csv");
        File fCatalogos = new File(carpeta, "catalogos.csv");
        long[] filas = new long[2];
        SQLiteDatabase db = abrirBase(context);
        try {
            filas[0] = escribirPlantillaSaldos(db, fSaldos);
            filas[1] = escribirCatalogos(db, fCatalogos);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo preparar la plantilla", e);
            r.mensaje = "No se pudo preparar la plantilla: " + e.getMessage();
            return r;
        } finally {
            db.close();
        }
        try {
            Drive drive = A32_Respaldos.servicioDrive(context);
            String idCarpeta = carpetaSheets(context, drive);
            r.hojas.add(subir(context, drive, idCarpeta, fSaldos, TIPO_SALDOS, "Plantilla de saldos iniciales", filas[0]));
            r.hojas.add(subir(context, drive, idCarpeta, fCatalogos, TIPO_CATALOGOS, "Catálogos (solo consulta)", filas[1]));
            r.ok = true;
            r.mensaje = "Hojas creadas en Drive › Backups › " + CARPETA_SHEETS;
        } catch (Exception e) {
            Log.e(TAG, "No se pudo subir la plantilla", e);
            r.mensaje = "No se pudo crear la plantilla en Drive: " + e.getMessage();
        } finally {
            fSaldos.delete();
            fCatalogos.delete();
        }
        return r;
    }

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
            sb.append(", t.\"").append(col).append('"');
            if (col.equals("cuenta_id")) sb.append(", c.Cuenta AS \"Cuenta (referencia)\"");
            if (col.equals("tipo_cuenta_id")) sb.append(", tc.nombre AS \"Tipo de cuenta (referencia)\"");
        }
        sb.append(" FROM transacciones t")
                .append(" LEFT JOIN cuentas c ON c.cuenta_id = t.cuenta_id")
                .append(" LEFT JOIN ").append(A1_1_AyudanteBD.TABLE_TIPO_CUENTA)
                .append(" tc ON tc.tipo_cuenta_id = t.tipo_cuenta_id")
                .append(" ORDER BY CAST(t.c1_Documento AS INTEGER), CAST(t.c2_ItemDoc AS INTEGER)");
        return sb.toString();
    }

    /** Todas las columnas de cuentas (cuenta_id primero), con tipo y renglón como referencia. */
    private static String sqlCuentas(SQLiteDatabase db) {
        StringBuilder sb = new StringBuilder("SELECT c.cuenta_id");
        for (String col : columnas(db, "cuentas")) {
            if (col.equals("cuenta_id")) continue;
            sb.append(", c.\"").append(col).append('"');
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

    /** Catálogos para consulta: tres bloques, uno debajo del otro. Devuelve las filas de datos. */
    private static long escribirCatalogos(SQLiteDatabase db, File destino) throws Exception {
        long filas = 0;
        try (Writer w = escritor(destino)) {
            w.write("CATÁLOGOS (solo consulta: la app no importa este archivo)\n\n");
            w.write("CUENTAS\n");
            filas += volcar(db, "SELECT c.cuenta_id, c.Cuenta, tc.nombre AS \"Tipo de cuenta\" FROM cuentas c"
                    + " LEFT JOIN " + A1_1_AyudanteBD.TABLE_TIPO_CUENTA + " tc ON tc.tipo_cuenta_id = c.tipo_cuenta_id"
                    + " ORDER BY c.Cuenta", w);
            w.write("\nTIPOS DE CUENTA\n");
            filas += volcar(db, "SELECT tipo_cuenta_id, nombre, naturaleza_normal FROM "
                    + A1_1_AyudanteBD.TABLE_TIPO_CUENTA + " ORDER BY tipo_cuenta_id", w);
            w.write("\nRENGLONES DE RENTA (vigentes)\n");
            filas += volcar(db, "SELECT renglon_id, pais, codigo, nombre, naturaleza FROM "
                    + A1_1_AyudanteBD.TABLE_RENGLON_RENTA + " WHERE vigente_hasta IS NULL"
                    + " ORDER BY pais, orden, codigo", w);
        }
        return filas;
    }

    /**
     * Plantilla de saldos iniciales: documento 0, ítem consecutivo después del último ítem del
     * documento 0 que ya exista, cuenta, valor en blanco, descripción y fechas de hoy.
     */
    private static long escribirPlantillaSaldos(SQLiteDatabase db, File destino) throws Exception {
        long siguiente = 1;
        try (Cursor c = db.rawQuery("SELECT MAX(CAST(c2_ItemDoc AS INTEGER)) FROM transacciones "
                + "WHERE CAST(c1_Documento AS INTEGER) = 0", null)) {
            if (c.moveToFirst() && !c.isNull(0)) siguiente = c.getLong(0) + 1;
        }
        Date hoy = new Date();
        String fechaYHora = new SimpleDateFormat("yyyy/M/d H:m", Locale.US).format(hoy);
        String fechaInicial = new SimpleDateFormat("yyyyMMdd", Locale.US).format(hoy);
        long filas = 0;
        try (Writer w = escritor(destino);
             Cursor c = db.rawQuery("SELECT cuenta_id, Cuenta FROM cuentas ORDER BY CAST(Item AS INTEGER), Cuenta", null)) {
            w.write("c1_Documento,c2_ItemDoc,cuenta_id,Cuenta (referencia),c5_Valor,c6_Descripcion,"
                    + "c7_FechaYHora,c8_FechaInicial,c9_FechaModificacion\n");
            while (c.moveToNext()) {
                w.write("0," + (siguiente++) + "," + c.getLong(0) + "," + A32_Respaldos.celdaCsv(c.getString(1))
                        + ",,Saldos iniciales," + fechaYHora + "," + fechaInicial + ",n a\n");
                filas++;
            }
        }
        return filas;
    }

    // ─────────────────────────────────────────────────────────────
    // CSV
    // ─────────────────────────────────────────────────────────────

    private static Writer escritor(File destino) throws Exception {
        return new OutputStreamWriter(new FileOutputStream(destino), StandardCharsets.UTF_8);
    }

    private static long escribirConsulta(SQLiteDatabase db, String sql, File destino, String titulo) throws Exception {
        try (Writer w = escritor(destino)) {
            if (titulo != null) w.write(titulo + "\n");
            return volcar(db, sql, w);
        }
    }

    /** Escribe encabezado y filas de la consulta; devuelve las filas de datos. */
    private static long volcar(SQLiteDatabase db, String sql, Writer w) throws Exception {
        long filas = 0;
        try (Cursor c = db.rawQuery(sql, null)) {
            String[] cols = c.getColumnNames();
            for (int i = 0; i < cols.length; i++) {
                if (i > 0) w.write(',');
                w.write(A32_Respaldos.celdaCsv(cols[i]));
            }
            w.write('\n');
            while (c.moveToNext()) {
                for (int i = 0; i < cols.length; i++) {
                    if (i > 0) w.write(',');
                    w.write(c.isNull(i) ? "" : A32_Respaldos.celdaCsv(c.getString(i)));
                }
                w.write('\n');
                filas++;
            }
        }
        return filas;
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
    private static String carpetaSheets(Context context, Drive drive) throws Exception {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = sp.getString(CLAVE_CARPETA, null);
        if (id != null) {
            try {
                com.google.api.services.drive.model.File f = drive.files().get(id).setFields("id, trashed").execute();
                if (f.getTrashed() == null || !f.getTrashed()) return id;
            } catch (Exception e) {
                Log.w(TAG, "La carpeta Sheets recordada ya no está: " + e.getMessage());
            }
        }
        FileList lista = drive.files().list()
                .setQ("'" + A32_Respaldos.FOLDER_ID_BACKUPS + "' in parents and mimeType='" + MIME_CARPETA + "'"
                        + " and name='" + CARPETA_SHEETS + "' and trashed=false")
                .setSpaces("drive").setFields("files(id)").execute();
        if (lista.getFiles() != null && !lista.getFiles().isEmpty()) {
            id = lista.getFiles().get(0).getId();
        } else {
            com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
            meta.setName(CARPETA_SHEETS);
            meta.setMimeType(MIME_CARPETA);
            meta.setParents(Collections.singletonList(A32_Respaldos.FOLDER_ID_BACKUPS));
            id = drive.files().create(meta).setFields("id").execute().getId();
        }
        sp.edit().putString(CLAVE_CARPETA, id).apply();
        return id;
    }

    /** Sube el CSV convertido en hoja de Google y rota a las 3 más recientes de ese tipo y equipo. */
    private static Hoja subir(Context context, Drive drive, String idCarpeta, File csv, String tipo,
                              String titulo, long filas) throws Exception {
        String prefijo = A32_Respaldos.equipo(context) + "_sheets-" + tipo + "_";
        String nombre = prefijo + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
        meta.setName(nombre);
        meta.setMimeType(MIME_HOJA);
        meta.setParents(Collections.singletonList(idCarpeta));
        com.google.api.services.drive.model.File creado = drive.files()
                .create(meta, new FileContent("text/csv", csv))
                .setFields("id, name, webViewLink").execute();
        FileList previas = drive.files().list()
                .setQ("'" + idCarpeta + "' in parents and mimeType='" + MIME_HOJA + "'"
                        + " and name contains '" + prefijo + "' and trashed=false")
                .setSpaces("drive").setFields("files(id, name)").setOrderBy("name desc").setPageSize(100).execute();
        if (previas.getFiles() != null) {
            for (int i = MAX_POR_TIPO; i < previas.getFiles().size(); i++) {
                try {
                    drive.files().delete(previas.getFiles().get(i).getId()).execute();
                } catch (Exception e) {
                    Log.e(TAG, "No se pudo borrar en Drive: " + previas.getFiles().get(i).getName(), e);
                }
            }
        }
        Hoja h = new Hoja();
        h.titulo = titulo;
        h.nombre = creado.getName();
        h.enlace = creado.getWebViewLink();
        h.filas = filas;
        return h;
    }
}
