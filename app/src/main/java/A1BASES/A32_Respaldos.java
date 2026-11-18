package A1BASES;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ⭐ NUEVO — E1 del sistema de respaldos (17-nov, Jorge).
 *
 * Módulo único de respaldo COMPLETO de la base (balance.db):
 *   1. Copia consistente de la base (VACUUM INTO en Android 11+; si no, punto de control del
 *      registro WAL + copia del archivo).
 *   2. Verificación inmediata: integridad (PRAGMA integrity_check), versión de la base y filas
 *      por tabla comparadas con la base real. Si algo no cuadra, la copia se descarta.
 *   3. Nombre con prefijo de equipo: <equipo>_<tipo>_v<versión>_<aaaammdd-hhmmss>.db
 *   4. Rotación local por equipo y tipo (carpeta Balance/Respaldos completos).
 *   5. Subida a Drive a la carpeta "Respaldos completos" (por ID, nunca por nombre), con aviso
 *      real de éxito o fallo, y rotación en Drive por equipo y tipo.
 *
 * Todo corre en segundo plano; los avisos llegan al hilo principal por la interfaz Escucha.
 */
public class A32_Respaldos {

    private static final String TAG = "A32_Respaldos";

    // Carpetas de Drive por ID (decisión de Jorge, 17-nov: los nombres se pueden cambiar sin
    // querer; los IDs no).
    public static final String FOLDER_ID_BACKUPS = "1tUtI-92CwnalMn8nkeUOFWRclPFP4Aeg";
    public static final String FOLDER_ID_RESPALDOS_COMPLETOS = "1FmiaU_IbK5CtvYdiUc5t3q75-flefUpG";

    public static final String CARPETA_LOCAL = "Balance/Respaldos completos";
    public static final String CUENTA_GOOGLE = "colombiaje@gmail.com";

    // Tipos de respaldo (van en el nombre del archivo).
    public static final String TIPO_MANUAL = "manual";
    public static final String TIPO_AUTOMATICO = "auto";

    // Prefijos de equipo acordados con Jorge (17-nov).
    public static final String EQUIPO_TELEFONO = "13T";
    public static final String EQUIPO_TABLETA = "PAD5";

    private static final String PREFS = "respaldos";
    private static final String CLAVE_EQUIPO = "equipo";
    private static final String CLAVE_DRIVE_OK = "drive_ok";
    private static final String CLAVE_DRIVE_MSG = "drive_msg";
    private static final String CLAVE_DRIVE_FECHA = "drive_fecha";
    private static final String CLAVE_PENDIENTE_DESC = "restauracion_pendiente";
    private static final String CLAVE_AVISO_RESTAURADA = "aviso_restaurada";

    public static final String TIPO_ANTES_RESTAURAR = "antes-restaurar";

    private static final ExecutorService HILO = Executors.newSingleThreadExecutor();
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    // ─────────────────────────────────────────────────────────────
    // Resultado y avisos
    // ─────────────────────────────────────────────────────────────
    public static class Resultado {
        public boolean ok;
        public String mensaje = "";
        public File archivo;
        public int version;
        public int tablas;
        public long filas;
    }

    public interface Escucha {
        /** La copia local terminó (verificada o no). */
        void alTerminarLocal(Resultado r);

        /** Terminó la subida a Drive (solo si la copia local salió bien). */
        void alTerminarDrive(boolean ok, String mensaje);
    }

    // ─────────────────────────────────────────────────────────────
    // Equipo
    // ─────────────────────────────────────────────────────────────

    /** Prefijo del equipo. Se fija la primera vez (tableta o teléfono) y queda guardado. */
    public static String equipo(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String guardado = sp.getString(CLAVE_EQUIPO, null);
        if (guardado != null && !guardado.trim().isEmpty()) {
            return guardado;
        }
        boolean esTableta = context.getResources().getConfiguration().smallestScreenWidthDp >= 600;
        String equipo = esTableta ? EQUIPO_TABLETA : EQUIPO_TELEFONO;
        sp.edit().putString(CLAVE_EQUIPO, equipo).apply();
        return equipo;
    }

    public static void cambiarEquipo(Context context, String equipo) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(CLAVE_EQUIPO, equipo.trim()).apply();
    }

    public static File carpetaLocal() {
        return new File(Environment.getExternalStorageDirectory(), CARPETA_LOCAL);
    }

    // ─────────────────────────────────────────────────────────────
    // Entrada principal (segundo plano)
    // ─────────────────────────────────────────────────────────────

    /**
     * Hace el respaldo completo en segundo plano: copia, verifica, rota local y sube a Drive.
     * Los avisos llegan al hilo principal.
     */
    public static void respaldarEnSegundoPlano(Context context, String tipo, int maxCopias, Escucha escucha) {
        final Context app = context.getApplicationContext();
        HILO.execute(() -> {
            Resultado r = respaldarBaseCompleta(app, tipo, maxCopias);
            PRINCIPAL.post(() -> { if (escucha != null) escucha.alTerminarLocal(r); });
            if (!r.ok) {
                return;
            }
            String[] drive = subirADrive(app, r.archivo, tipo, maxCopias);
            boolean okDrive = "ok".equals(drive[0]);
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(CLAVE_DRIVE_OK, okDrive)
                    .putString(CLAVE_DRIVE_MSG, drive[1])
                    .putLong(CLAVE_DRIVE_FECHA, System.currentTimeMillis())
                    .apply();
            PRINCIPAL.post(() -> { if (escucha != null) escucha.alTerminarDrive(okDrive, drive[1]); });
        });
    }

    // ─────────────────────────────────────────────────────────────
    // Copia + verificación (llamar fuera del hilo principal)
    // ─────────────────────────────────────────────────────────────
    public static Resultado respaldarBaseCompleta(Context context, String tipo, int maxCopias) {
        Resultado r = new Resultado();
        File carpeta = carpetaLocal();
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            r.mensaje = "No se pudo crear la carpeta " + CARPETA_LOCAL;
            return r;
        }

        String equipo = equipo(context);
        int version = A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
        String sello = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        String nombre = equipo + "_" + tipo + "_v" + version + "_" + sello + ".db";
        File temporal = new File(carpeta, nombre + ".tmp");
        File definitivo = new File(carpeta, nombre);
        if (temporal.exists()) {
            temporal.delete();
        }

        Map<String, Long> filasOrigen;
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context,
                A1_1_AyudanteBD.balanceSqlite_String_PSF, null, version);
        try {
            SQLiteDatabase db = ayudante.getWritableDatabase();
            filasOrigen = contarFilas(db);
            copiarBase(context, db, temporal);
        } catch (Exception e) {
            Log.e(TAG, "Error al copiar la base", e);
            temporal.delete();
            r.mensaje = "Error al copiar la base: " + e.getMessage();
            return r;
        } finally {
            ayudante.close();
        }

        // Verificación de la copia
        try {
            SQLiteDatabase copia = SQLiteDatabase.openDatabase(
                    temporal.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try {
                String integridad = primerTexto(copia, "PRAGMA integrity_check");
                if (!"ok".equalsIgnoreCase(integridad)) {
                    throw new IllegalStateException("integridad: " + integridad);
                }
                int versionCopia = (int) primerNumero(copia, "PRAGMA user_version");
                if (versionCopia != version) {
                    throw new IllegalStateException("versión de la copia " + versionCopia + " ≠ " + version);
                }
                Map<String, Long> filasCopia = contarFilas(copia);
                for (Map.Entry<String, Long> e : filasOrigen.entrySet()) {
                    Long enCopia = filasCopia.get(e.getKey());
                    if (enCopia == null || !enCopia.equals(e.getValue())) {
                        throw new IllegalStateException("tabla " + e.getKey() + ": " + e.getValue()
                                + " filas en la base, " + enCopia + " en la copia");
                    }
                }
                r.version = versionCopia;
                r.tablas = filasCopia.size();
                long total = 0;
                for (Long n : filasCopia.values()) total += n;
                r.filas = total;
            } finally {
                copia.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "La copia no pasó la verificación", e);
            temporal.delete();
            r.mensaje = "La copia no pasó la verificación (" + e.getMessage() + "). Vuelve a intentarlo.";
            return r;
        }

        if (!temporal.renameTo(definitivo)) {
            temporal.delete();
            r.mensaje = "No se pudo guardar la copia " + nombre;
            return r;
        }

        rotarLocal(carpeta, equipo + "_" + tipo + "_", maxCopias);

        r.ok = true;
        r.archivo = definitivo;
        r.mensaje = "Respaldo verificado: " + nombre + " · " + r.tablas + " tablas · " + r.filas + " filas";
        return r;
    }

    /** Copia consistente de la base al archivo destino. */
    private static void copiarBase(Context context, SQLiteDatabase db, File destino) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // SQLite 3.27+: copia compacta y consistente aunque la base esté en uso.
            db.execSQL("VACUUM INTO '" + destino.getAbsolutePath().replace("'", "''") + "'");
            return;
        }
        // Android 10 o anterior: vaciar el registro WAL en la base y copiar el archivo.
        Cursor c = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null);
        try { c.moveToFirst(); } finally { c.close(); }
        File origen = context.getDatabasePath(A1_1_AyudanteBD.balanceSqlite_String_PSF);
        try (InputStream in = new FileInputStream(origen); OutputStream out = new FileOutputStream(destino)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    /** Filas por tabla (sin tablas internas de SQLite ni de Android). */
    private static Map<String, Long> contarFilas(SQLiteDatabase db) {
        Map<String, Long> filas = new LinkedHashMap<>();
        List<String> tablas = new ArrayList<>();
        Cursor c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' "
                + "AND name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' ORDER BY name", null);
        try {
            while (c.moveToNext()) tablas.add(c.getString(0));
        } finally {
            c.close();
        }
        for (String t : tablas) {
            filas.put(t, primerNumero(db, "SELECT COUNT(*) FROM \"" + t.replace("\"", "\"\"") + "\""));
        }
        return filas;
    }

    private static String primerTexto(SQLiteDatabase db, String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            c.close();
        }
    }

    private static long primerNumero(SQLiteDatabase db, String sql) {
        Cursor c = db.rawQuery(sql, null);
        try {
            return c.moveToFirst() ? c.getLong(0) : -1;
        } finally {
            c.close();
        }
    }

    /** Deja solo las 'max' copias más recientes de un equipo y tipo (el nombre ordena por fecha). */
    private static void rotarLocal(File carpeta, String prefijo, int max) {
        File[] archivos = carpeta.listFiles((dir, n) -> n.startsWith(prefijo) && n.endsWith(".db"));
        if (archivos == null || archivos.length <= max) {
            return;
        }
        Arrays.sort(archivos, (a, b) -> b.getName().compareTo(a.getName()));
        for (int i = max; i < archivos.length; i++) {
            if (archivos[i].delete()) {
                Log.d(TAG, "Rotación local: borrado " + archivos[i].getName());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Drive (llamar fuera del hilo principal)
    // ─────────────────────────────────────────────────────────────

    /** Sube la copia a "Respaldos completos" y rota por equipo y tipo. Devuelve {"ok"|"error", mensaje}. */
    private static String[] subirADrive(Context context, File archivo, String tipo, int maxCopias) {
        try {
            Drive drive = servicioDrive(context);

            com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
            meta.setName(archivo.getName());
            meta.setParents(Collections.singletonList(FOLDER_ID_RESPALDOS_COMPLETOS));
            com.google.api.services.drive.model.File subido = drive.files()
                    .create(meta, new FileContent("application/x-sqlite3", archivo))
                    .setFields("id, name")
                    .execute();
            if (subido == null || subido.getId() == null) {
                return new String[]{"error", "Drive no confirmó la subida"};
            }

            // Rotación en Drive por equipo y tipo (solo archivos que subió esta app)
            String prefijo = equipo(context) + "_" + tipo + "_";
            FileList lista = drive.files().list()
                    .setQ("'" + FOLDER_ID_RESPALDOS_COMPLETOS + "' in parents and name contains '"
                            + prefijo + "' and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .setOrderBy("name desc")
                    .execute();
            List<com.google.api.services.drive.model.File> archivos = lista.getFiles();
            int borrados = 0;
            if (archivos != null) {
                for (int i = maxCopias; i < archivos.size(); i++) {
                    try {
                        drive.files().delete(archivos.get(i).getId()).execute();
                        borrados++;
                    } catch (Exception e) {
                        Log.e(TAG, "No se pudo borrar en Drive: " + archivos.get(i).getName(), e);
                    }
                }
            }
            return new String[]{"ok", "Subido a Drive: " + subido.getName()
                    + (borrados > 0 ? " · " + borrados + " copia(s) antigua(s) retirada(s)" : "")};
        } catch (Exception e) {
            Log.e(TAG, "Error al subir a Drive", e);
            return new String[]{"error", "No se pudo subir a Drive: " + e.getMessage()};
        }
    }

    private static Drive servicioDrive(Context context) {
        GoogleAccountCredential credencial = GoogleAccountCredential.usingOAuth2(
                context, Collections.singleton(DriveScopes.DRIVE_FILE))
                .setSelectedAccountName(CUENTA_GOOGLE);
        return new Drive.Builder(AndroidHttp.newCompatibleTransport(),
                JacksonFactory.getDefaultInstance(), credencial)
                .setApplicationName("a4.balance")
                .build();
    }

    // ═════════════════════════════════════════════════════════════
    // ⭐ NUEVO E2 (17-nov, Jorge): estado, listas y restauración
    // ═════════════════════════════════════════════════════════════

    /** Texto del último resultado de Drive, o null si nunca se ha subido. */
    public static String estadoDrive(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long fecha = sp.getLong(CLAVE_DRIVE_FECHA, 0);
        if (fecha == 0) return null;
        String cuando = new SimpleDateFormat("d MMM HH:mm", new Locale("es", "CO")).format(new Date(fecha));
        return (sp.getBoolean(CLAVE_DRIVE_OK, false) ? "✓ " : "✗ ") + cuando + " · "
                + sp.getString(CLAVE_DRIVE_MSG, "");
    }

    /** Respaldos completos en la carpeta local, del más nuevo al más viejo (todos los equipos). */
    public static List<File> respaldosLocales() {
        List<File> lista = new ArrayList<>();
        File[] archivos = carpetaLocal().listFiles((dir, n) -> n.endsWith(".db"));
        if (archivos != null) {
            lista.addAll(Arrays.asList(archivos));
            Collections.sort(lista, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        }
        return lista;
    }

    /** Respaldos completos en Drive: cada elemento = {id, nombre, tamaño en bytes}. Fuera del hilo principal. */
    public static List<String[]> respaldosEnDrive(Context context) throws Exception {
        List<String[]> lista = new ArrayList<>();
        String token = null;
        do {
            FileList r = servicioDrive(context).files().list()
                    .setQ("'" + FOLDER_ID_RESPALDOS_COMPLETOS + "' in parents and trashed=false")
                    .setSpaces("drive")
                    .setFields("nextPageToken, files(id, name, size)")
                    .setOrderBy("createdTime desc")
                    .setPageToken(token)
                    .execute();
            if (r.getFiles() != null) {
                for (com.google.api.services.drive.model.File f : r.getFiles()) {
                    if (f.getName() != null && f.getName().endsWith(".db")) {
                        lista.add(new String[]{f.getId(), f.getName(),
                                String.valueOf(f.getSize() == null ? 0 : f.getSize())});
                    }
                }
            }
            token = r.getNextPageToken();
        } while (token != null);
        return lista;
    }

    /** Descarga un respaldo de Drive a la caché de la app. Fuera del hilo principal. */
    public static File descargarDeDrive(Context context, String id, String nombre) throws Exception {
        File destino = new File(context.getCacheDir(), nombre);
        try (OutputStream out = new FileOutputStream(destino)) {
            servicioDrive(context).files().get(id).executeMediaAndDownloadTo(out);
        }
        return destino;
    }

    /** Revisión de un respaldo candidato antes de restaurarlo. */
    public static class Revision {
        public boolean ok;
        public String mensaje = "";
        public int version;
        public int tablas;
        public long filas;
    }

    /** Abre el respaldo en solo lectura: integridad, versión (no más nueva que la app) y filas. */
    public static Revision revisarCandidato(File archivo) {
        Revision rv = new Revision();
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(archivo.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try {
                String integridad = primerTexto(db, "PRAGMA integrity_check");
                if (!"ok".equalsIgnoreCase(integridad)) {
                    rv.mensaje = "El respaldo está dañado (integridad: " + integridad + ").";
                    return rv;
                }
                rv.version = (int) primerNumero(db, "PRAGMA user_version");
                Map<String, Long> filas = contarFilas(db);
                rv.tablas = filas.size();
                for (Long n : filas.values()) rv.filas += n;
            } finally {
                db.close();
            }
        } catch (Exception e) {
            rv.mensaje = "No se pudo abrir el respaldo: " + e.getMessage();
            return rv;
        }
        int versionApp = A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
        if (rv.version > versionApp) {
            rv.mensaje = "El respaldo es de una versión de base más nueva (v" + rv.version
                    + ") que la de esta app (v" + versionApp + "). Instala primero la app más reciente.";
            return rv;
        }
        if (rv.version < 1) {
            rv.mensaje = "El archivo no parece un respaldo de la app (sin versión de base).";
            return rv;
        }
        rv.ok = true;
        return rv;
    }

    private static File archivoPendiente(Context context) {
        return new File(context.getFilesDir(), "restauracion_pendiente.db");
    }

    /**
     * Deja el respaldo listo para reemplazar la base en el próximo arranque de la app (con la
     * base cerrada). Fuera del hilo principal.
     */
    public static boolean prepararRestauracion(Context context, File candidato, String descripcion) {
        File pendiente = archivoPendiente(context);
        File tmp = new File(pendiente.getPath() + ".tmp");
        try {
            copiarArchivo(candidato, tmp);
            if (tmp.length() != candidato.length()) {
                tmp.delete();
                return false;
            }
            if (pendiente.exists()) pendiente.delete();
            if (!tmp.renameTo(pendiente)) {
                tmp.delete();
                return false;
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(CLAVE_PENDIENTE_DESC, descripcion).commit();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "No se pudo preparar la restauración", e);
            tmp.delete();
            return false;
        }
    }

    /**
     * Se llama al arrancar la app, ANTES de abrir la base. Si hay una restauración pendiente,
     * reemplaza balance.db por el respaldo. La copia nueva se escribe junto a la base y solo
     * entonces se cambia por la actual, para no quedar nunca sin base.
     */
    public static void aplicarRestauracionPendiente(Context context) {
        File pendiente = archivoPendiente(context);
        if (!pendiente.exists() || pendiente.length() == 0) {
            return;
        }
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String desc = sp.getString(CLAVE_PENDIENTE_DESC, pendiente.getName());
        File base = context.getDatabasePath(A1_1_AyudanteBD.balanceSqlite_String_PSF);
        File nueva = new File(base.getPath() + ".restaurando");
        try {
            copiarArchivo(pendiente, nueva);
            if (nueva.length() != pendiente.length()) {
                throw new IllegalStateException("copia incompleta");
            }
            for (String sufijo : new String[]{"-wal", "-shm", "-journal"}) {
                File f = new File(base.getPath() + sufijo);
                if (f.exists()) f.delete();
            }
            if (base.exists() && !base.delete()) {
                throw new IllegalStateException("no se pudo retirar la base actual");
            }
            if (!nueva.renameTo(base)) {
                throw new IllegalStateException("no se pudo poner la base restaurada");
            }
            pendiente.delete();
            sp.edit().remove(CLAVE_PENDIENTE_DESC)
                    .putString(CLAVE_AVISO_RESTAURADA, "✓ Base restaurada desde:\n" + desc).commit();
        } catch (Exception e) {
            Log.e(TAG, "Falló la restauración pendiente", e);
            nueva.delete();
            pendiente.delete();
            sp.edit().remove(CLAVE_PENDIENTE_DESC)
                    .putString(CLAVE_AVISO_RESTAURADA, "✗ No se pudo restaurar " + desc + ": "
                            + e.getMessage() + "\nLa base anterior sigue en uso.").commit();
        }
    }

    /** Aviso de la última restauración aplicada (se entrega una sola vez). */
    public static String tomarAvisoRestauracion(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String aviso = sp.getString(CLAVE_AVISO_RESTAURADA, null);
        if (aviso != null) sp.edit().remove(CLAVE_AVISO_RESTAURADA).apply();
        return aviso;
    }

    private static void copiarArchivo(File origen, File destino) throws Exception {
        try (InputStream in = new FileInputStream(origen); OutputStream out = new FileOutputStream(destino)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

}
