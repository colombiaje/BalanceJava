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
    // ⭐ NUEVO (21-nov, Jorge, build B): origen, estado anterior e historial de restauraciones.
    private static final String CLAVE_PENDIENTE_ORIGEN = "restauracion_pendiente_origen";
    private static final String CLAVE_PENDIENTE_ANTERIOR = "restauracion_pendiente_anterior";
    private static final String CLAVE_HISTORIAL_RESTAURACIONES = "historial_restauraciones";
    public static final int MAX_HISTORIAL_RESTAURACIONES = 10;

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
        public Map<String, Long> filasPorTabla = new LinkedHashMap<>();
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
        A9_2_BackupFile.prefijoEquipo = equipo.trim();
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
                r.filasPorTabla = filasCopia;
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

        rotarLocalPorTipo(carpeta, equipo + "_" + tipo + "_", tipo, maxCopias);

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
    // ═════════════════════════════════════════════════════════════
    // ⭐ NUEVO (21-nov, Jorge): retención por generaciones del respaldo AUTOMÁTICO
    //   - las últimas 'max' (10);
    //   - una por semana de las últimas 8 semanas (la MÁS RECIENTE de cada semana);
    //   - una por mes de los últimos 12 meses (la MÁS ANTIGUA de cada mes, para que no quede
    //     cerca de la semanal y amplíe las fechas disponibles).
    // Los demás tipos conservan solo las últimas 'max'.
    // ═════════════════════════════════════════════════════════════
    public static final int SEMANAS_AUTOMATICO = 8;
    public static final int MESES_AUTOMATICO = 12;

    /** Nombres a conservar. 'nombres' en cualquier orden; la fecha sale del nombre (aaaammdd-hhmmss). */
    static java.util.Set<String> aConservar(List<String> nombres, String tipo, int max) {
        List<String> orden = new ArrayList<>(nombres);
        Collections.sort(orden, Collections.reverseOrder()); // más nuevo primero
        java.util.Set<String> conservar = new java.util.HashSet<>();
        for (int i = 0; i < orden.size() && i < max; i++) conservar.add(orden.get(i));
        if (!TIPO_AUTOMATICO.equals(tipo)) {
            return conservar;
        }
        java.util.Calendar ahora = java.util.Calendar.getInstance();
        java.util.Calendar limiteSemanas = (java.util.Calendar) ahora.clone();
        limiteSemanas.add(java.util.Calendar.WEEK_OF_YEAR, -SEMANAS_AUTOMATICO);
        java.util.Calendar limiteMeses = (java.util.Calendar) ahora.clone();
        limiteMeses.add(java.util.Calendar.MONTH, -MESES_AUTOMATICO);

        Map<String, String> porSemana = new LinkedHashMap<>(); // semana → más reciente
        Map<String, String> porMes = new LinkedHashMap<>();    // mes → más antigua
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("_(\\d{8})-(\\d{6})\\.");
        for (String n : orden) {
            java.util.regex.Matcher m = p.matcher(n);
            if (!m.find()) continue;
            Date fecha;
            try {
                fecha = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(m.group(1) + m.group(2));
            } catch (Exception e) {
                continue;
            }
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTime(fecha);
            if (c.after(limiteSemanas)) {
                String semana = c.get(java.util.Calendar.YEAR) + "-" + c.get(java.util.Calendar.WEEK_OF_YEAR);
                if (!porSemana.containsKey(semana)) porSemana.put(semana, n); // 'orden' va de nuevo a viejo
            }
            if (c.after(limiteMeses)) {
                String mes = m.group(1).substring(0, 6);
                porMes.put(mes, n); // se queda con el último visto = el más antiguo del mes
            }
        }
        conservar.addAll(porSemana.values());
        conservar.addAll(porMes.values());
        return conservar;
    }

    private static void rotarLocalPorTipo(File carpeta, String prefijo, String tipo, int max) {
        File[] archivos = carpeta.listFiles((dir, n) -> n.startsWith(prefijo) && n.endsWith(".db"));
        if (archivos == null) return;
        List<String> nombres = new ArrayList<>();
        for (File f : archivos) nombres.add(f.getName());
        java.util.Set<String> conservar = aConservar(nombres, tipo, max);
        for (File f : archivos) {
            if (!conservar.contains(f.getName()) && f.delete()) {
                Log.d(TAG, "Rotación local: borrado " + f.getName());
            }
        }
    }

    private static void rotarLocal(File carpeta, String prefijo, int max) {
        rotarLocal(carpeta, prefijo, ".db", max);
    }

    private static void rotarLocal(File carpeta, String prefijo, String extension, int max) {
        File[] archivos = carpeta.listFiles((dir, n) -> n.startsWith(prefijo) && n.endsWith(extension));
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
                    .create(meta, new FileContent(archivo.getName().endsWith(".txt") ? "text/plain"
                            : archivo.getName().endsWith(".csv") ? "text/csv" : "application/x-sqlite3", archivo))
                    .setFields("id, name")
                    .execute();
            if (subido == null || subido.getId() == null) {
                return new String[]{"error", "Drive no confirmó la subida"};
            }

            // Rotación en Drive por equipo y tipo (solo archivos que subió esta app)
            String prefijo = equipo(context) + "_" + tipo + "_";
            FileList lista = drive.files().list()
                    .setQ("'" + FOLDER_ID_RESPALDOS_COMPLETOS + "' in parents and name contains '"
                            + prefijo + "' and name contains '" + (archivo.getName().endsWith(".txt") ? ".txt"
                            : archivo.getName().endsWith(".csv") ? ".csv" : ".db")
                            + "' and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .setPageSize(500)
                    .setOrderBy("name desc")
                    .execute();
            List<com.google.api.services.drive.model.File> archivos = lista.getFiles();
            int borrados = 0;
            if (archivos != null) {
                // ⭐ (21-nov, Jorge): el automático conserva además una por semana y una por mes.
                List<String> nombres = new ArrayList<>();
                for (com.google.api.services.drive.model.File f : archivos) nombres.add(f.getName());
                java.util.Set<String> conservar = aConservar(nombres, tipo, maxCopias);
                for (int i = 0; i < archivos.size(); i++) {
                    if (conservar.contains(archivos.get(i).getName())) continue;
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

    // (22-nov) visible en el paquete: también lo usa A33_Sheets.
    static Drive servicioDrive(Context context) {
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
        // ⭐ NUEVO (21-nov, Jorge): filas por tabla, para comparar con la base actual.
        public Map<String, Long> filasPorTabla = new LinkedHashMap<>();
    }

    // ⭐ NUEVO (21-nov, Jorge): {cuentas, transacciones} de un respaldo, para la lista de
    // restauración (una copia sin transacciones se ve antes de elegirla). -1 si no se pudo leer.
    public static long[] cuentasYTransacciones(File archivo) {
        long[] r = {-1, -1};
        try {
            SQLiteDatabase db = SQLiteDatabase.openDatabase(archivo.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            try {
                r[0] = primerNumero(db, "SELECT COUNT(*) FROM cuentas");
                r[1] = primerNumero(db, "SELECT COUNT(*) FROM transacciones");
            } finally {
                db.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "No se pudo contar " + archivo.getName(), e);
        }
        return r;
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
                rv.filasPorTabla = filas;
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
        return prepararRestauracion(context, candidato, descripcion, "", "");
    }

    // ⭐ NUEVO (21-nov, Jorge, build B): guarda también el origen (este equipo / Drive) y el
    // nombre del respaldo del estado anterior, para el aviso y la lista "Últimas restauraciones".
    public static boolean prepararRestauracion(Context context, File candidato, String descripcion,
                                               String origen, String estadoAnterior) {
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
                    .putString(CLAVE_PENDIENTE_DESC, descripcion)
                    .putString(CLAVE_PENDIENTE_ORIGEN, origen == null ? "" : origen)
                    .putString(CLAVE_PENDIENTE_ANTERIOR, estadoAnterior == null ? "" : estadoAnterior)
                    .commit();
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
        String origen = sp.getString(CLAVE_PENDIENTE_ORIGEN, "");
        String anterior = sp.getString(CLAVE_PENDIENTE_ANTERIOR, "");
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
            sp.edit().remove(CLAVE_PENDIENTE_DESC).remove(CLAVE_PENDIENTE_ORIGEN).remove(CLAVE_PENDIENTE_ANTERIOR)
                    .putString(CLAVE_AVISO_RESTAURADA, "✓ Base restaurada desde:\n" + etiquetaRespaldo(desc)
                            + (origen.isEmpty() ? "" : " (" + origen + ")") + "\n" + desc
                            + (anterior.isEmpty() ? "" : "\n\nEl estado anterior quedó respaldado en:\n"
                                    + etiquetaRespaldo(anterior) + "\n" + anterior))
                    .commit();
            anotarRestauracion(sp, true, desc, origen);
        } catch (Exception e) {
            Log.e(TAG, "Falló la restauración pendiente", e);
            nueva.delete();
            pendiente.delete();
            sp.edit().remove(CLAVE_PENDIENTE_DESC).remove(CLAVE_PENDIENTE_ORIGEN).remove(CLAVE_PENDIENTE_ANTERIOR)
                    .putString(CLAVE_AVISO_RESTAURADA, "✗ No se pudo restaurar " + desc + ": "
                            + e.getMessage() + "\nLa base anterior sigue en uso.").commit();
            anotarRestauracion(sp, false, desc, origen);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // ⭐ NUEVO (21-nov, Jorge, build B): historial de restauraciones, etiquetas legibles y
    // reinicio automático de la app
    // ═════════════════════════════════════════════════════════════

    /** Restauración aplicada o fallida: fecha en ms, ok, respaldo y origen. */
    public static class Restauracion {
        public long fecha;
        public boolean ok;
        public String nombre = "";
        public String origen = "";
    }

    // Se guarda en las preferencias de la app (fuera de la base), así sobrevive a la propia
    // restauración. Una línea por restauración: fecha|ok|nombre|origen, la más nueva primero.
    private static void anotarRestauracion(SharedPreferences sp, boolean ok, String nombre, String origen) {
        String linea = System.currentTimeMillis() + "|" + (ok ? "1" : "0") + "|"
                + nombre.replace("|", " ").replace("\n", " ") + "|" + origen.replace("|", " ");
        String previo = sp.getString(CLAVE_HISTORIAL_RESTAURACIONES, "");
        StringBuilder sb = new StringBuilder(linea);
        int n = 1;
        for (String l : previo.split("\n")) {
            if (l.trim().isEmpty() || n >= MAX_HISTORIAL_RESTAURACIONES) continue;
            sb.append('\n').append(l);
            n++;
        }
        sp.edit().putString(CLAVE_HISTORIAL_RESTAURACIONES, sb.toString()).commit();
    }

    /** Últimas restauraciones, de la más nueva a la más vieja. */
    public static List<Restauracion> ultimasRestauraciones(Context context) {
        List<Restauracion> lista = new ArrayList<>();
        String texto = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(CLAVE_HISTORIAL_RESTAURACIONES, "");
        for (String l : texto.split("\n")) {
            String[] p = l.split("\\|", -1);
            if (p.length < 4) continue;
            try {
                Restauracion r = new Restauracion();
                r.fecha = Long.parseLong(p[0]);
                r.ok = "1".equals(p[1]);
                r.nombre = p[2];
                r.origen = p[3];
                lista.add(r);
            } catch (Exception ignorado) { }
        }
        return lista;
    }

    /**
     * Nombre legible de un respaldo .db: "PAD5_antes-cierre-total_v31_20261121-101500.db" →
     * "Antes de cierre total – 21 nov 10:15 · PAD5". Si el nombre no sigue el formato, lo devuelve igual.
     */
    public static String etiquetaRespaldo(String nombre) {
        if (nombre == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^([^_]+)_(.+?)_v\\d+_(\\d{8})-(\\d{6})\\.db$").matcher(nombre);
        if (!m.matches()) return nombre;
        String texto = tipoLegible(m.group(2));
        try {
            Date d = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US).parse(m.group(3) + m.group(4));
            texto += " – " + new SimpleDateFormat("d MMM yyyy HH:mm", new Locale("es", "CO")).format(d);
        } catch (Exception ignorado) { }
        return texto + " · " + m.group(1);
    }

    /** "antes-cierre-total" → "Antes de cierre total"; "auto" → "Automático". */
    public static String tipoLegible(String tipo) {
        if (TIPO_MANUAL.equals(tipo)) return "Manual";
        if (TIPO_AUTOMATICO.equals(tipo)) return "Automático";
        String prefijo = "";
        String op = tipo;
        if (tipo.startsWith("antes-")) { prefijo = "Antes de "; op = tipo.substring(6); }
        else if (tipo.startsWith("despues-")) { prefijo = "Después de "; op = tipo.substring(8); }
        String nombreOp;
        switch (op) {
            case "restaurar": nombreOp = "restaurar"; break;
            case "cierre-total": nombreOp = "cierre total"; break;
            case "cierre-algunas": nombreOp = "cierre de algunas cuentas"; break;
            case "sheets-transacciones": nombreOp = "importar Balance de Sheets"; break;
            case "sheets-cuentas": nombreOp = "importar cuentas de Sheets"; break;
            // ⭐ (22-nov, S2): "Traer de Sheets".
            case "sheets-corregir-transacciones": nombreOp = "corregir transacciones desde Sheets"; break;
            case "sheets-corregir-cuentas": nombreOp = "corregir cuentas desde Sheets"; break;
            case "sheets-saldos-iniciales": nombreOp = "cargar saldos iniciales desde Sheets"; break;
            default: nombreOp = op.replace('-', ' ');
        }
        String texto = prefijo + nombreOp;
        return texto.isEmpty() ? tipo : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }

    /**
     * Cierra la app y la vuelve a abrir sola. Al arrancar, C2 aplica la restauración pendiente
     * antes de abrir la base y muestra el aviso. Se reinicia el proceso completo para que no
     * quede ninguna conexión abierta a la base anterior.
     */
    public static void reiniciarApp(android.app.Activity actividad) {
        Context app = actividad.getApplicationContext();
        android.content.Intent arranque = app.getPackageManager().getLaunchIntentForPackage(app.getPackageName());
        if (arranque != null && arranque.getComponent() != null) {
            android.content.Intent reinicio = android.content.Intent.makeRestartActivityTask(arranque.getComponent());
            app.startActivity(reinicio);
        }
        actividad.finishAffinity();
        Runtime.getRuntime().exit(0);
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


    // ═════════════════════════════════════════════════════════════
    // ⭐ NUEVO E3 (17-nov, Jorge): automático, puntos de restauración y resumen
    // ═════════════════════════════════════════════════════════════

    public static final String TIPO_RESUMEN = "resumen";

    /** ⭐ NUEVO (18-nov): filas por tabla de la base actual, sin hacer copia (para resúmenes de
     *  restauraciones, que ya no guardan un punto "después"). */
    public static Resultado estadoActual(Context context) {
        Resultado r = new Resultado();
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(context.getApplicationContext(),
                A1_1_AyudanteBD.balanceSqlite_String_PSF, null, A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        try {
            r.filasPorTabla = contarFilas(ayudante.getReadableDatabase());
            r.ok = true;
        } catch (Exception e) {
            r.mensaje = e.getMessage();
        } finally {
            ayudante.close();
        }
        return r;
    }

    /**
     * Respaldo automático tras un cambio (GUARDAR en F1, cambios de cuentas en F2): copia
     * completa en segundo plano, 10 copias. Solo avisa si algo falla.
     */
    public static void respaldoAutomatico(Context context) {
        final Context app = context.getApplicationContext();
        respaldarEnSegundoPlano(app, TIPO_AUTOMATICO, 10, new Escucha() {
            @Override
            public void alTerminarLocal(Resultado r) {
                if (!r.ok) {
                    android.widget.Toast.makeText(app, "✗ Respaldo automático: " + r.mensaje,
                            android.widget.Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void alTerminarDrive(boolean ok, String mensaje) {
                if (!ok) {
                    android.widget.Toast.makeText(app, "✗ Respaldo automático: " + mensaje,
                            android.widget.Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * Punto de restauración ANTES o DESPUÉS de una operación que borra datos. La copia local
     * se hace aquí mismo (quien llama decide si seguir según r.ok); la subida a Drive va en
     * segundo plano. 5 copias por equipo y tipo.
     */
    public static Resultado puntoDeRestauracion(Context context, String tipo) {
        final Context app = context.getApplicationContext();
        Resultado r = respaldarBaseCompleta(app, tipo, 5);
        if (r.ok) {
            final File archivo = r.archivo;
            HILO.execute(() -> {
                String[] drive = subirADrive(app, archivo, tipo, 5);
                guardarEstadoDrive(app, "ok".equals(drive[0]), drive[1]);
                if (!"ok".equals(drive[0])) {
                    PRINCIPAL.post(() -> android.widget.Toast.makeText(app,
                            "✗ Punto de restauración no subido a Drive: " + drive[1],
                            android.widget.Toast.LENGTH_LONG).show());
                }
            });
        }
        return r;
    }

    private static void guardarEstadoDrive(Context app, boolean ok, String msg) {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(CLAVE_DRIVE_OK, ok)
                .putString(CLAVE_DRIVE_MSG, msg)
                .putLong(CLAVE_DRIVE_FECHA, System.currentTimeMillis())
                .apply();
    }

    /**
     * Resumen de una operación: filas por tabla antes y después, más un detalle libre. Se
     * guarda como .txt junto a los respaldos (10 por operación), se sube a Drive y se devuelve
     * el texto para mostrarlo en la app.
     */
    public static String resumenOperacion(Context context, String operacion, String titulo,
                                          Resultado antes, Resultado despues, String detalle) {
        final Context app = context.getApplicationContext();
        String equipo = equipo(app);
        String sello = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        StringBuilder sb = new StringBuilder();
        sb.append(titulo).append('\n')
                .append("Fecha: ").append(new SimpleDateFormat("d MMM yyyy HH:mm:ss", new Locale("es", "CO")).format(new Date()))
                .append(" · Equipo: ").append(equipo).append('\n');
        if (antes != null && antes.ok) sb.append("Antes: ").append(antes.archivo.getName()).append('\n');
        if (despues != null && despues.ok && despues.archivo != null) sb.append("Después: ").append(despues.archivo.getName()).append('\n');
        if (despues != null && !despues.ok) sb.append("⚠ Punto \"después\" no realizado: ").append(despues.mensaje).append('\n');

        if (antes != null && antes.ok && despues != null && despues.ok) {
            sb.append("\nCambios por tabla (antes → después):\n");
            boolean hubo = false;
            for (Map.Entry<String, Long> e : despues.filasPorTabla.entrySet()) {
                Long previo = antes.filasPorTabla.get(e.getKey());
                long a = previo == null ? 0 : previo;
                if (a != e.getValue()) {
                    sb.append("  ").append(e.getKey()).append(": ").append(a).append(" → ").append(e.getValue()).append('\n');
                    hubo = true;
                }
            }
            if (!hubo) sb.append("  (sin cambios en el número de filas)\n");
        }
        if (detalle != null && !detalle.isEmpty()) {
            sb.append('\n').append(detalle).append('\n');
        }
        String texto = sb.toString();

        File carpeta = carpetaLocal();
        if (carpeta.exists() || carpeta.mkdirs()) {
            String prefijo = equipo + "_" + TIPO_RESUMEN + "-" + operacion + "_";
            File archivo = new File(carpeta, prefijo + sello + ".txt");
            try (OutputStream out = new FileOutputStream(archivo)) {
                out.write(texto.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                rotarLocal(carpeta, prefijo, ".txt", 10);
                HILO.execute(() -> subirADrive(app, archivo, TIPO_RESUMEN + "-" + operacion, 10));
            } catch (Exception e) {
                Log.e(TAG, "No se pudo guardar el resumen", e);
            }
        }
        return texto;
    }


    // ═════════════════════════════════════════════════════════════
    // ⭐ NUEVO (18-nov, Jorge): alerta de cierres y tabla de últimos respaldos
    // ═════════════════════════════════════════════════════════════

    /** Texto del resumen más reciente de una operación (de cualquier equipo), o null. */
    public static String ultimoResumen(String operacion) {
        File[] archivos = carpetaLocal().listFiles((d, n) ->
                n.contains("_" + TIPO_RESUMEN + "-" + operacion + "_") && n.endsWith(".txt"));
        if (archivos == null || archivos.length == 0) return null;
        File ultimo = archivos[0];
        for (File f : archivos) if (f.lastModified() > ultimo.lastModified()) ultimo = f;
        try (InputStream in = new FileInputStream(ultimo)) {
            byte[] datos = new byte[(int) Math.min(ultimo.length(), 20000)];
            int leidos = in.read(datos);
            return new String(datos, 0, Math.max(0, leidos), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Fila de la tabla de últimos respaldos: {tipo, nombre, fecha en ms}. */
    public static class Fila {
        public String tipo;
        public String nombre;
        public long fecha;
    }

    /**
     * El respaldo más reciente de cada tipo en este equipo: copias completas (.db) por tipo,
     * resúmenes (.txt) por operación y CSV del historial por número de archivo.
     */
    public static List<Fila> ultimosPorTipo() {
        Map<String, Fila> porTipo = new LinkedHashMap<>();
        File[] completos = carpetaLocal().listFiles();
        if (completos != null) {
            for (File f : completos) {
                String n = f.getName();
                String tipo;
                if (n.endsWith(".db")) {
                    tipo = "Copia completa (.db) · " + segmentoTipo(n);
                } else if (n.endsWith(".txt")) {
                    tipo = "Resumen · " + segmentoTipo(n).replaceFirst("^" + TIPO_RESUMEN + "-", "");
                } else {
                    continue;
                }
                anotarSiEsMasNuevo(porTipo, tipo, f);
            }
        }
        File[] csv = new File(Environment.getExternalStorageDirectory(), "Balance/Historial").listFiles();
        if (csv != null) {
            for (File f : csv) {
                if (!f.getName().endsWith(".csv")) continue;
                anotarSiEsMasNuevo(porTipo, "CSV · " + tipoCsv(f.getName()), f);
            }
        }
        List<Fila> filas = new ArrayList<>(porTipo.values());
        Collections.sort(filas, (a, b) -> Long.compare(b.fecha, a.fecha));
        return filas;
    }

    private static void anotarSiEsMasNuevo(Map<String, Fila> porTipo, String tipo, File f) {
        Fila actual = porTipo.get(tipo);
        if (actual == null || f.lastModified() > actual.fecha) {
            Fila fila = new Fila();
            fila.tipo = tipo;
            fila.nombre = f.getName();
            fila.fecha = f.lastModified();
            porTipo.put(tipo, fila);
        }
    }

    /** "PAD5_auto_v31_2026…" → "auto"; "PAD5_resumen-cierre-total_2026…" → "resumen-cierre-total". */
    private static String segmentoTipo(String nombre) {
        String[] partes = nombre.split("_");
        return partes.length > 1 ? partes[1] : nombre;
    }

    /** Nombre corto de un CSV según su número (sin prefijo de equipo ni fecha). */
    private static String tipoCsv(String nombre) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{3}(_\\d)?)_").matcher(nombre);
        String numero = m.find() ? m.group(1) : "";
        switch (numero.length() >= 3 ? numero.substring(0, 3) : numero) {
            case "121": return "121 transacciones (cada GUARDAR)";
            case "211": return "211 cuentas";
            case "213": return "213 renglones de renta";
            case "214": return "214 plantillas";
            case "222": return numero + " cuentas desde Sheets";
            case "231": return "231 cuentas antes de restaurar";
            case "411": return "411 transacciones antes de cierres";
            case "421": return "421 antes del cierre total";
            case "422": return "422 saldos del cierre total";
            case "423": return "423 fotos de renta";
            case "425": return "425 ajustes de renta";
            case "426": return "426 soportes de renta";
            case "431": return "431 antes del cierre de algunas";
            case "432": return "432 saldos del cierre de algunas";
            case "442": return numero + " transacciones desde Sheets";
            case "451": return "451 antes de restaurar transacciones";
            case "452": return "452 antes de restaurar desde Drive";
            default: return numero.isEmpty() ? nombre : numero;
        }
    }


    /**
     * ⭐ NUEVO (18-nov, Jorge): CSV locales de un tipo (por su nombre base, con o sin prefijo de
     * equipo), del más nuevo al más viejo: el "último" de Balance y las copias con fecha de
     * Balance/Historial. Para restaurar eligiendo la fecha, como en Drive.
     */
    public static List<File> csvLocalesPorBase(String base) {
        List<File> lista = new ArrayList<>();
        File raiz = new File(Environment.getExternalStorageDirectory(), "Balance");
        for (File carpeta : new File[]{raiz, new File(raiz, "Historial")}) {
            File[] archivos = carpeta.listFiles((d, n) -> n.contains(base) && n.endsWith(".csv"));
            if (archivos != null) lista.addAll(Arrays.asList(archivos));
        }
        Collections.sort(lista, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        // ⭐ (18-nov, Jorge): el "último" de Balance es el mismo archivo que la copia más nueva
        // del historial (se guardan juntos). Si hay una copia igual (mismo tamaño y casi la
        // misma hora), el "último" no se muestra, para no ver el mismo respaldo dos veces.
        List<File> sinRepetir = new ArrayList<>();
        for (File f : lista) {
            boolean enHistorial = "Historial".equals(f.getParentFile() == null ? "" : f.getParentFile().getName());
            boolean repetido = false;
            if (!enHistorial) {
                for (File h : lista) {
                    boolean hEnHistorial = "Historial".equals(h.getParentFile() == null ? "" : h.getParentFile().getName());
                    if (hEnHistorial && h.length() == f.length() && Math.abs(h.lastModified() - f.lastModified()) < 5000) {
                        repetido = true;
                        break;
                    }
                }
            }
            if (!repetido) sinRepetir.add(f);
        }
        return sinRepetir;
    }

    /** Texto para una lista de elección: nombre, fecha y si es el "último" de Balance. */
    public static String etiquetaArchivo(File f) {
        String fecha = new SimpleDateFormat("d MMM yyyy HH:mm:ss", new Locale("es", "CO")).format(new Date(f.lastModified()));
        boolean ultimo = !"Historial".equals(f.getParentFile() == null ? "" : f.getParentFile().getName());
        return f.getName() + "\n   " + fecha + (ultimo ? " · último (carpeta Balance)" : " · historial");
    }


    // ═════════════════════════════════════════════════════════════
    // ⭐ NUEVO (18-nov, Jorge): exportar una tabla de un respaldo .db a CSV (solo lectura)
    // ═════════════════════════════════════════════════════════════

    /** Tablas de un respaldo con su número de filas: {nombre, filas}. */
    public static List<String[]> tablasDeRespaldo(File db) {
        List<String[]> lista = new ArrayList<>();
        SQLiteDatabase base = SQLiteDatabase.openDatabase(db.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try {
            for (Map.Entry<String, Long> e : contarFilas(base).entrySet()) {
                lista.add(new String[]{e.getKey(), String.valueOf(e.getValue())});
            }
        } finally {
            base.close();
        }
        return lista;
    }

    /**
     * Exporta una tabla del respaldo a Balance/<equipo>_export-<tabla>_<fecha>.csv (con
     * encabezado y comillas cuando hace falta) y lo sube a Drive en segundo plano. Devuelve el
     * archivo o null. Fuera del hilo principal.
     */
    public static File exportarTablaCsv(Context context, File db, String tabla) {
        final Context app = context.getApplicationContext();
        File carpeta = new File(Environment.getExternalStorageDirectory(), "Balance");
        String tipo = "export-" + tabla.replaceAll("[^A-Za-z0-9]", "");
        String sello = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File destino = new File(carpeta, equipo(app) + "_" + tipo + "_" + sello + ".csv");
        SQLiteDatabase base = SQLiteDatabase.openDatabase(db.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try {
            if (escribirTablaCsv(base, tabla, destino) < 0) return null;
        } finally {
            base.close();
        }
        HILO.execute(() -> subirADrive(app, destino, tipo, 5));
        return destino;
    }

    /** ⭐ (21-nov): extraído de exportarTablaCsv. Escribe la tabla en CSV; devuelve las filas o -1. */
    private static long escribirTablaCsv(SQLiteDatabase base, String tabla, File destino) {
        long filas = 0;
        try (Cursor c = base.rawQuery("SELECT * FROM \"" + tabla.replace("\"", "\"\"") + "\"", null);
             java.io.Writer w = new java.io.OutputStreamWriter(new FileOutputStream(destino),
                     java.nio.charset.StandardCharsets.UTF_8)) {
            String[] columnas = c.getColumnNames();
            for (int i = 0; i < columnas.length; i++) {
                if (i > 0) w.write(',');
                w.write(celdaCsv(columnas[i]));
            }
            w.write('\n');
            while (c.moveToNext()) {
                for (int i = 0; i < columnas.length; i++) {
                    if (i > 0) w.write(',');
                    w.write(c.isNull(i) ? "" : celdaCsv(c.getString(i)));
                }
                w.write('\n');
                filas++;
            }
            return filas;
        } catch (Exception e) {
            Log.e(TAG, "No se pudo exportar " + tabla, e);
            destino.delete();
            return -1;
        }
    }

    // ═════════════════════════════════════════════════════════════
    // ⭐ NUEVO (21-nov, Jorge, build B): exportar TODAS las tablas de un respaldo .db a una
    // carpeta con fecha: Balance/Exportaciones/<equipo>_export-todas_<fecha>/<tabla>.csv, y la
    // misma carpeta en Drive como hija de Backups. Se conservan las 5 más recientes por equipo.
    // ═════════════════════════════════════════════════════════════
    public static final String CARPETA_EXPORTACIONES = "Balance/Exportaciones";
    public static final String TIPO_EXPORT_TODAS = "export-todas";
    private static final int MAX_EXPORT_TODAS = 5;

    public static class Exportacion {
        public boolean ok;
        public String mensaje = "";
        public File carpeta;
        public int tablas;
        public long filas;
    }

    /** Fuera del hilo principal. La subida a Drive sigue en segundo plano y avisa con 'alTerminarDrive'. */
    public static Exportacion exportarTodasLasTablas(Context context, File db, Escucha escucha) {
        final Context app = context.getApplicationContext();
        Exportacion r = new Exportacion();
        File raiz = new File(Environment.getExternalStorageDirectory(), CARPETA_EXPORTACIONES);
        String prefijo = equipo(app) + "_" + TIPO_EXPORT_TODAS + "_";
        String sello = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
        File carpeta = new File(raiz, prefijo + sello);
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            r.mensaje = "No se pudo crear la carpeta " + carpeta.getPath();
            return r;
        }
        SQLiteDatabase base = SQLiteDatabase.openDatabase(db.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
        try {
            for (String tabla : contarFilas(base).keySet()) {
                File destino = new File(carpeta, tabla.replaceAll("[^A-Za-z0-9_]", "") + ".csv");
                long n = escribirTablaCsv(base, tabla, destino);
                if (n < 0) {
                    r.mensaje = "No se pudo exportar la tabla " + tabla;
                    borrarCarpeta(carpeta);
                    return r;
                }
                r.tablas++;
                r.filas += n;
            }
        } finally {
            base.close();
        }
        // Rotación local: las 5 carpetas más recientes de este equipo.
        File[] previas = raiz.listFiles(f -> f.isDirectory() && f.getName().startsWith(prefijo));
        if (previas != null && previas.length > MAX_EXPORT_TODAS) {
            java.util.Arrays.sort(previas, (a, b) -> b.getName().compareTo(a.getName()));
            for (int i = MAX_EXPORT_TODAS; i < previas.length; i++) borrarCarpeta(previas[i]);
        }
        r.ok = true;
        r.carpeta = carpeta;
        r.mensaje = r.tablas + " tablas · " + r.filas + " filas";
        HILO.execute(() -> {
            String[] drive = subirCarpetaADrive(app, carpeta, prefijo);
            if (escucha != null) {
                PRINCIPAL.post(() -> escucha.alTerminarDrive("ok".equals(drive[0]), drive[1]));
            }
        });
        return r;
    }

    private static void borrarCarpeta(File carpeta) {
        File[] hijos = carpeta.listFiles();
        if (hijos != null) for (File h : hijos) h.delete();
        carpeta.delete();
    }

    /** Crea la carpeta en Drive (hija de Backups), sube cada CSV y rota a 5 por equipo. */
    private static String[] subirCarpetaADrive(Context context, File carpeta, String prefijo) {
        try {
            Drive drive = servicioDrive(context);
            com.google.api.services.drive.model.File meta = new com.google.api.services.drive.model.File();
            meta.setName(carpeta.getName());
            meta.setMimeType("application/vnd.google-apps.folder");
            meta.setParents(Collections.singletonList(FOLDER_ID_BACKUPS));
            com.google.api.services.drive.model.File nueva = drive.files().create(meta).setFields("id").execute();
            File[] csvs = carpeta.listFiles();
            int subidos = 0;
            if (csvs != null) {
                for (File csv : csvs) {
                    com.google.api.services.drive.model.File m = new com.google.api.services.drive.model.File();
                    m.setName(csv.getName());
                    m.setParents(Collections.singletonList(nueva.getId()));
                    drive.files().create(m, new FileContent("text/csv", csv)).setFields("id").execute();
                    subidos++;
                }
            }
            FileList lista = drive.files().list()
                    .setQ("'" + FOLDER_ID_BACKUPS + "' in parents and mimeType='application/vnd.google-apps.folder'"
                            + " and name contains '" + prefijo + "' and trashed=false")
                    .setSpaces("drive")
                    .setFields("files(id, name)")
                    .setPageSize(200)
                    .setOrderBy("name desc")
                    .execute();
            if (lista.getFiles() != null) {
                for (int i = MAX_EXPORT_TODAS; i < lista.getFiles().size(); i++) {
                    try {
                        drive.files().delete(lista.getFiles().get(i).getId()).execute();
                    } catch (Exception e) {
                        Log.e(TAG, "No se pudo borrar en Drive: " + lista.getFiles().get(i).getName(), e);
                    }
                }
            }
            return new String[]{"ok", "Carpeta subida a Drive › Backups: " + carpeta.getName() + " (" + subidos + " CSV)"};
        } catch (Exception e) {
            Log.e(TAG, "Error al subir la carpeta a Drive", e);
            return new String[]{"error", "No se pudo subir la carpeta a Drive: " + e.getMessage()};
        }
    }

    static String celdaCsv(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

}
