package A1BASES;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ⭐ NUEVO (15-nov, BD v31, plantillas P1, diseño de Jorge): acceso a las plantillas.
 * Un tipo de plantilla = un modelo vigente (plantilla_item). Las transacciones guardan solo
 * movimientos reales; al registrar, cada ítem lleva su concepto y el periodo del documento.
 * Ver el doc "BalanceJava — Diseño de plantillas (área Template)".
 */
public final class A27_Plantillas {

    private static final String TAG = "A27_Plantillas";

    public static final String QUINCENAL = "quincenal";
    public static final String MENSUAL = "mensual";
    public static final String SIN_PERIODO = "sin periodo";

    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun",
            "jul", "ago", "sep", "oct", "nov", "dic"};

    private A27_Plantillas() {}

    /** Una plantilla (tipo) con su resumen. */
    public static final class Plantilla {
        public long id;
        public String nombre;
        public String periodicidad;
        public int ultimoUso;
        public int usos;
        public int items;
        public String origen;
    }

    /** Un ítem del modelo, con el nombre actual de su cuenta. */
    public static final class Item {
        public long cuentaId;
        public String cuenta;
        public String signo;
        public int valor;
        public String concepto;
    }

    private static SQLiteDatabase abrir(Context ctx, boolean escritura) {
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(ctx, A1_1_AyudanteBD.balanceSqlite_String_PSF,
                null, A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        return escritura ? ayudante.getWritableDatabase() : ayudante.getReadableDatabase();
    }

    // ═══════════════════════ Consultas ═══════════════════════

    /** Activas, por uso reciente (las nunca usadas al final), luego por nombre. */
    public static List<Plantilla> listarActivas(Context ctx) {
        List<Plantilla> lista = new ArrayList<>();
        if (ctx == null) return lista;
        SQLiteDatabase db = abrir(ctx, false);
        try (Cursor c = db.rawQuery("SELECT p.plantilla_id, p.nombre, p.periodicidad, " +
                "IFNULL(p.ultimo_uso, 0), p.usos, p.origen, " +
                "(SELECT COUNT(*) FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM +
                " i WHERE i.plantilla_id = p.plantilla_id) " +
                "FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA + " p WHERE p.activa = 1 " +
                "ORDER BY IFNULL(p.ultimo_uso, 0) DESC, p.nombre COLLATE NOCASE", null)) {
            while (c.moveToNext()) lista.add(leer(c));
        } catch (Exception e) {
            Log.e(TAG, "listarActivas: " + e.getMessage());
        } finally {
            db.close();
        }
        return lista;
    }

    /** Todas (activas o no), para el asistente: saber qué ya se convirtió. */
    public static List<Plantilla> listarTodas(Context ctx) {
        List<Plantilla> lista = new ArrayList<>();
        if (ctx == null) return lista;
        SQLiteDatabase db = abrir(ctx, false);
        try (Cursor c = db.rawQuery("SELECT p.plantilla_id, p.nombre, p.periodicidad, " +
                "IFNULL(p.ultimo_uso, 0), p.usos, p.origen, " +
                "(SELECT COUNT(*) FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM +
                " i WHERE i.plantilla_id = p.plantilla_id) " +
                "FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA + " p ORDER BY p.nombre COLLATE NOCASE", null)) {
            while (c.moveToNext()) lista.add(leer(c));
        } catch (Exception e) {
            Log.e(TAG, "listarTodas: " + e.getMessage());
        } finally {
            db.close();
        }
        return lista;
    }

    private static Plantilla leer(Cursor c) {
        Plantilla p = new Plantilla();
        p.id = c.getLong(0);
        p.nombre = c.getString(1);
        p.periodicidad = c.getString(2);
        p.ultimoUso = c.getInt(3);
        p.usos = c.getInt(4);
        p.origen = c.getString(5);
        p.items = c.getInt(6);
        return p;
    }

    /** Busca por nombre (sin distinguir mayúsculas). Null si no existe o el nombre está vacío. */
    public static Plantilla buscarPorNombre(Context ctx, String nombre) {
        if (ctx == null || nombre == null || nombre.trim().isEmpty()) return null;
        for (Plantilla p : listarTodas(ctx)) {
            if (p.nombre.equalsIgnoreCase(nombre.trim())) return p;
        }
        return null;
    }

    /** Ítems del modelo vigente, con el nombre actual de la cuenta (por cuenta_id). */
    public static List<Item> items(Context ctx, long plantillaId) {
        List<Item> lista = new ArrayList<>();
        SQLiteDatabase db = abrir(ctx, false);
        try (Cursor c = db.rawQuery("SELECT i.cuenta_id, c.Cuenta, i.signo, i.valor, i.concepto " +
                "FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM + " i " +
                "LEFT JOIN cuentas c ON c.cuenta_id = i.cuenta_id " +
                "WHERE i.plantilla_id = ? ORDER BY i.orden", new String[]{String.valueOf(plantillaId)})) {
            while (c.moveToNext()) {
                Item it = new Item();
                it.cuentaId = c.getLong(0);
                it.cuenta = c.getString(1);
                it.signo = c.getString(2);
                it.valor = c.getInt(3);
                it.concepto = c.getString(4);
                lista.add(it);
            }
        } finally {
            db.close();
        }
        return lista;
    }

    /**
     * Ítems del modelo como renglones de documento, listos para la lista de F1 (mismo formato
     * que un registro manual: valor con signo, tipo de cuenta y cerrable de la cuenta actual).
     * Las cuentas que ya no existen se omiten.
     */
    public static ArrayList<A3_2_TipoTransaccionesGetsYSets> itemsComoDocumento(Context ctx, long plantillaId) {
        ArrayList<A3_2_TipoTransaccionesGetsYSets> lista = new ArrayList<>();
        SQLiteDatabase db = abrir(ctx, false);
        try (Cursor c = db.rawQuery("SELECT i.cuenta_id, c.Cuenta, i.signo, i.valor, i.concepto, " +
                "c.tipo_cuenta_id, tc.nombre, c.Cerrable, IFNULL(c.con_inventario, 0) " +
                "FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM + " i " +
                "JOIN cuentas c ON c.cuenta_id = i.cuenta_id " +
                "LEFT JOIN tipo_cuenta tc ON tc.tipo_cuenta_id = c.tipo_cuenta_id " +
                "WHERE i.plantilla_id = ? ORDER BY i.orden", new String[]{String.valueOf(plantillaId)})) {
            int n = 0;
            while (c.moveToNext()) {
                n++;
                String signo = "-".equals(c.getString(2)) ? "-" : "+";
                int valor = Math.abs(c.getInt(3));
                if ("-".equals(signo)) valor = -valor;
                String concepto = c.getString(4);
                if (concepto == null || concepto.trim().isEmpty()) concepto = conceptoDeCuenta(c.getString(1));
                A3_2_TipoTransaccionesGetsYSets t = new A3_2_TipoTransaccionesGetsYSets(
                        "", String.valueOf(n), c.getString(1), signo, valor, concepto,
                        "", 0, "No Aplica", "", "",
                        c.getInt(7) == 1 ? "Cerrable" : "No Aplica", "na");
                t.tipoTset_14CuentaIdMetodoEnA5(c.getLong(0));
                if (!c.isNull(5)) t.tipoTset_16TipoCuentaIdMetodoEnA5(c.getLong(5));
                t.tipoT_17TipoCuentaNombre_String = c.getString(6);
                t.tipoT_18ConInventario_Boolean = c.getInt(8) == 1;
                lista.add(t);
            }
        } finally {
            db.close();
        }
        return lista;
    }

    // ═══════════════════════ Escritura ═══════════════════════

    /** Ítem para crear o actualizar un modelo. */
    public static final class NuevoItem {
        public final long cuentaId;
        public final String signo;
        public final int valor;
        public final String concepto;

        public NuevoItem(long cuentaId, String signo, int valor, String concepto) {
            this.cuentaId = cuentaId;
            this.signo = "-".equals(signo) ? "-" : "+";
            this.valor = Math.abs(valor);
            this.concepto = concepto;
        }
    }

    /** Crea una plantilla. Devuelve su id, o -1 si el nombre ya existe o falla. */
    public static long crear(Context ctx, String nombre, String periodicidad, String origen,
                             List<NuevoItem> items) {
        SQLiteDatabase db = abrir(ctx, true);
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("nombre", nombre.trim());
            v.put("periodicidad", periodicidad);
            v.put("activa", 1);
            v.put("creada", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(new Date()));
            v.put("origen", origen);
            long id = db.insert(A1_1_AyudanteBD.TABLE_PLANTILLA, null, v);
            if (id < 0) return -1;
            escribirItems(db, A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, id, items);
            db.setTransactionSuccessful();
            return id;
        } catch (Exception e) {
            Log.e(TAG, "crear: " + e.getMessage());
            return -1;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /**
     * Reemplaza el modelo vigente: el actual pasa a ser la versión anterior (solo una) y los
     * ítems nuevos quedan como modelo.
     */
    public static boolean reemplazarModelo(Context ctx, long plantillaId, String periodicidad,
                                           List<NuevoItem> items) {
        return reemplazarModelo(ctx, plantillaId, periodicidad, null, items);
    }

    /** Igual, anotando además la descripción antigua de donde salió el modelo (origen). */
    public static boolean reemplazarModelo(Context ctx, long plantillaId, String periodicidad,
                                           String origen, List<NuevoItem> items) {
        SQLiteDatabase db = abrir(ctx, true);
        db.beginTransaction();
        try {
            String[] id = {String.valueOf(plantillaId)};
            db.delete(A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM_ANTERIOR, "plantilla_id = ?", id);
            db.execSQL("INSERT INTO " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM_ANTERIOR +
                    " SELECT * FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM + " WHERE plantilla_id = ?", id);
            db.delete(A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, "plantilla_id = ?", id);
            escribirItems(db, A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, plantillaId, items);
            ContentValues v = new ContentValues();
            if (periodicidad != null) v.put("periodicidad", periodicidad);
            if (origen != null) v.put("origen", origen);
            if (v.size() > 0) db.update(A1_1_AyudanteBD.TABLE_PLANTILLA, v, "plantilla_id = ?", id);
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "reemplazarModelo: " + e.getMessage());
            return false;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    private static void escribirItems(SQLiteDatabase db, String tabla, long plantillaId, List<NuevoItem> items) {
        int orden = 0;
        for (NuevoItem it : items) {
            ContentValues v = new ContentValues();
            v.put("plantilla_id", plantillaId);
            v.put("orden", ++orden);
            v.put("cuenta_id", it.cuentaId);
            v.put("signo", it.signo);
            v.put("valor", it.valor);
            v.put("concepto", it.concepto);
            db.insertOrThrow(tabla, null, v);
        }
    }

    /** Marca el uso al guardar un documento con la plantilla (dentro de la transacción del guardado). */
    public static void registrarUso(SQLiteDatabase db, long plantillaId, int fechaDocumento) {
        db.execSQL("UPDATE " + A1_1_AyudanteBD.TABLE_PLANTILLA + " SET usos = usos + 1, " +
                        "ultimo_uso = MAX(IFNULL(ultimo_uso, 0), ?) WHERE plantilla_id = ?",
                new Object[]{fechaDocumento, plantillaId});
    }

    // ═══════════════════════ P2: administrar y comparar ═══════════════════════

    /** Ítems del documento en pantalla como ítems de modelo (concepto = descripción sin periodo). */
    public static List<NuevoItem> desdeDocumento(List<A3_2_TipoTransaccionesGetsYSets> documento) {
        List<NuevoItem> l = new ArrayList<>();
        if (documento == null) return l;
        for (A3_2_TipoTransaccionesGetsYSets t : documento) {
            if (t.tipoT_14CuentaId_Long == null || t.tipoT_5Value_Integer == null) continue;
            if (Boolean.TRUE.equals(t.tipoT_18ConInventario_Boolean)) continue; // inventario: no entra
            int v = t.tipoT_5Value_Integer;
            String concepto = sinPeriodo(t.tipoT_6Description_String);
            if (concepto.isEmpty()) concepto = conceptoDeCuenta(t.tipoT_3Accout_String);
            l.add(new NuevoItem(t.tipoT_14CuentaId_Long, v < 0 ? "-" : "+", v, concepto));
        }
        return l;
    }

    /** ¿El documento es distinto del modelo vigente (cuentas, signos, valores o conceptos)? */
    public static boolean difiereDelModelo(Context ctx, long plantillaId, List<NuevoItem> documento) {
        List<Item> modelo = items(ctx, plantillaId);
        if (modelo.size() != documento.size()) return true;
        for (int i = 0; i < modelo.size(); i++) {
            Item m = modelo.get(i);
            NuevoItem d = documento.get(i);
            String cm = m.concepto == null ? "" : m.concepto.trim();
            String cd = d.concepto == null ? "" : d.concepto.trim();
            if (m.cuentaId != d.cuentaId || !m.signo.equals(d.signo) || Math.abs(m.valor) != d.valor
                    || !cm.equalsIgnoreCase(cd)) {
                return true;
            }
        }
        return false;
    }

    /** Resultado de comparar por contenido: la plantilla más parecida y cuántas cuentas comparten. */
    public static final class Parecido {
        public Plantilla plantilla;
        public int comunes;
        public int total;
    }

    /**
     * La plantilla activa que comparte más cuentas, si comparte al menos el 70 % (sobre la
     * lista más larga). Null si ninguna llega.
     */
    public static Parecido masParecida(Context ctx, List<NuevoItem> documento, Long excluir) {
        java.util.Set<Long> propias = new java.util.HashSet<>();
        for (NuevoItem n : documento) propias.add(n.cuentaId);
        Parecido mejor = null;
        for (Plantilla p : listarActivas(ctx)) {
            if (excluir != null && p.id == excluir) continue;
            java.util.Set<Long> otras = new java.util.HashSet<>();
            for (Item it : items(ctx, p.id)) otras.add(it.cuentaId);
            int comunes = 0;
            for (Long c : propias) if (otras.contains(c)) comunes++;
            int total = Math.max(propias.size(), otras.size());
            if (total == 0 || comunes * 100 < 70 * total) continue;
            if (mejor == null || comunes * mejor.total > mejor.comunes * total) {
                mejor = new Parecido();
                mejor.plantilla = p;
                mejor.comunes = comunes;
                mejor.total = total;
            }
        }
        return mejor;
    }

    /** Cambia el nombre (id fijo). False si el nombre ya lo usa otra. */
    public static boolean renombrar(Context ctx, long plantillaId, String nombre) {
        Plantilla otra = buscarPorNombre(ctx, nombre);
        if (otra != null && otra.id != plantillaId) return false;
        SQLiteDatabase db = abrir(ctx, true);
        try {
            ContentValues v = new ContentValues();
            v.put("nombre", nombre.trim());
            return db.update(A1_1_AyudanteBD.TABLE_PLANTILLA, v, "plantilla_id = ?",
                    new String[]{String.valueOf(plantillaId)}) == 1;
        } catch (Exception e) {
            Log.e(TAG, "renombrar: " + e.getMessage());
            return false;
        } finally {
            db.close();
        }
    }

    public static void activar(Context ctx, long plantillaId, boolean activa) {
        SQLiteDatabase db = abrir(ctx, true);
        try {
            ContentValues v = new ContentValues();
            v.put("activa", activa ? 1 : 0);
            db.update(A1_1_AyudanteBD.TABLE_PLANTILLA, v, "plantilla_id = ?", new String[]{String.valueOf(plantillaId)});
        } finally {
            db.close();
        }
    }

    public static void cambiarPeriodicidad(Context ctx, long plantillaId, String periodicidad) {
        SQLiteDatabase db = abrir(ctx, true);
        try {
            ContentValues v = new ContentValues();
            v.put("periodicidad", periodicidad);
            db.update(A1_1_AyudanteBD.TABLE_PLANTILLA, v, "plantilla_id = ?", new String[]{String.valueOf(plantillaId)});
        } finally {
            db.close();
        }
    }

    /** Cambia solo los conceptos del modelo (en el orden de sus ítems); no crea versión anterior. */
    public static void actualizarConceptos(Context ctx, long plantillaId, List<String> conceptos) {
        SQLiteDatabase db = abrir(ctx, true);
        db.beginTransaction();
        try {
            for (int i = 0; i < conceptos.size(); i++) {
                ContentValues v = new ContentValues();
                v.put("concepto", conceptos.get(i).trim());
                db.update(A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, v, "plantilla_id = ? AND orden = ?",
                        new String[]{String.valueOf(plantillaId), String.valueOf(i + 1)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    public static boolean tieneAnterior(Context ctx, long plantillaId) {
        SQLiteDatabase db = abrir(ctx, false);
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM_ANTERIOR +
                " WHERE plantilla_id = ?", new String[]{String.valueOf(plantillaId)})) {
            return c.moveToFirst() && c.getInt(0) > 0;
        } finally {
            db.close();
        }
    }

    /** Deshace el último reemplazo: intercambia el modelo vigente con la versión anterior. */
    public static boolean restaurarAnterior(Context ctx, long plantillaId) {
        if (!tieneAnterior(ctx, plantillaId)) return false; // sin anterior no se toca el modelo
        SQLiteDatabase db = abrir(ctx, true);
        db.beginTransaction();
        try {
            String[] id = {String.valueOf(plantillaId)};
            String it = A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, ant = A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM_ANTERIOR;
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS plantilla_intercambio AS SELECT * FROM " + it + " WHERE 0");
            db.execSQL("DELETE FROM plantilla_intercambio");
            db.execSQL("INSERT INTO plantilla_intercambio SELECT * FROM " + it + " WHERE plantilla_id = ?", id);
            db.delete(it, "plantilla_id = ?", id);
            db.execSQL("INSERT INTO " + it + " SELECT * FROM " + ant + " WHERE plantilla_id = ?", id);
            db.delete(ant, "plantilla_id = ?", id);
            db.execSQL("INSERT INTO " + ant + " SELECT * FROM plantilla_intercambio");
            db.execSQL("DELETE FROM plantilla_intercambio");
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "restaurarAnterior: " + e.getMessage());
            return false;
        } finally {
            db.endTransaction();
            db.close();
        }
    }

    /** "16-nov" a partir de aaaammdd. */
    public static String fechaCorta(int fecha) {
        if (fecha < 19000101) return "";
        int mes = (fecha / 100) % 100;
        if (mes < 1 || mes > 12) return String.valueOf(fecha);
        return (fecha % 100) + "-" + MESES[mes - 1];
    }

    // ═══════════════════════ P3: respaldo, restauración y sin uso ═══════════════════════

    /** Carpeta de Drive de las cuentas (F2): las plantillas viajan con ellas. */
    public static final String FOLDER_ID_DRIVE = "1jrcnKozSB2okTStCaPyxkGz39GwBKL_y";
    public static final String PREFIJO_CSV = "214_Plantillas";
    private static final String ENCABEZADO_CSV = "plantilla,periodicidad,activa,creada,ultimo_uso,usos,origen," +
            "orden,cuenta_id,cuenta,signo,valor,concepto";

    /** Escribe 214_Plantillas.csv en la carpeta Balance (un renglón por ítem). Devuelve el archivo o null. */
    public static java.io.File exportarCsv(Context ctx) {
        if (ctx == null || !android.os.Environment.MEDIA_MOUNTED.equals(android.os.Environment.getExternalStorageState())) {
            return null;
        }
        java.io.File archivo = new java.io.File(android.os.Environment.getExternalStorageDirectory().getPath() +
                "/Balance/" + PREFIJO_CSV + ".csv");
        SQLiteDatabase db = abrir(ctx, false);
        try (Cursor c = db.rawQuery("SELECT p.nombre, p.periodicidad, p.activa, p.creada, p.ultimo_uso, p.usos, " +
                "p.origen, i.orden, i.cuenta_id, cu.Cuenta, i.signo, i.valor, i.concepto " +
                "FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA + " p JOIN " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM +
                " i ON i.plantilla_id = p.plantilla_id LEFT JOIN cuentas cu ON cu.cuenta_id = i.cuenta_id " +
                "ORDER BY p.nombre COLLATE NOCASE, i.orden", null);
             java.io.OutputStreamWriter salida = new java.io.OutputStreamWriter(
                     new java.io.FileOutputStream(archivo, false), java.nio.charset.StandardCharsets.UTF_8)) {
            salida.write(ENCABEZADO_CSV + "\n");
            while (c.moveToNext()) {
                StringBuilder linea = new StringBuilder();
                for (int k = 0; k < 13; k++) {
                    if (k > 0) linea.append(",");
                    String v = c.isNull(k) ? "" : c.getString(k);
                    linea.append(campoCsv(v));
                }
                salida.write(linea.append("\n").toString());
            }
            return archivo;
        } catch (Exception e) {
            Log.e(TAG, "exportarCsv: " + e.getMessage());
            return null;
        } finally {
            db.close();
        }
    }

    /** Exporta y sube a Drive (rota los respaldos viejos). Se llama tras cada cambio de plantillas. */
    public static void respaldar(Context ctx) {
        java.io.File archivo = exportarCsv(ctx);
        if (archivo == null) return;
        try {
            new A6_1_GoogleDriveManager(ctx, archivo.getParent() + "/", archivo.getName(), FOLDER_ID_DRIVE)
                    .uploadFileToDrive();
        } catch (Exception e) {
            Log.e(TAG, "respaldar: " + e.getMessage());
        }
    }

    /**
     * Importa un 214_Plantillas: por nombre, crea las que faltan y reemplaza el modelo de las que
     * ya existen (el actual queda como anterior). La cuenta se busca por cuenta_id y, si no
     * coincide el nombre, por nombre. Devuelve un resumen.
     */
    public static String importarCsv(Context ctx, String texto) {
        java.util.LinkedHashMap<String, List<String[]>> porPlantilla = new java.util.LinkedHashMap<>();
        String[] lineas = texto.replace("\r", "").split("\n");
        for (String l : lineas) {
            if (l.trim().isEmpty() || l.startsWith("plantilla,")) continue;
            String[] f = partirCsv(l);
            if (f.length < 13 || f[0].trim().isEmpty()) continue;
            List<String[]> grupo = porPlantilla.get(f[0].trim());
            if (grupo == null) porPlantilla.put(f[0].trim(), grupo = new ArrayList<>());
            grupo.add(f);
        }
        int creadas = 0, actualizadas = 0, omitidos = 0;
        SQLiteDatabase lectura = abrir(ctx, false);
        java.util.Map<Long, String> nombrePorId = new java.util.HashMap<>();
        java.util.Map<String, Long> idPorNombre = new java.util.HashMap<>();
        try (Cursor c = lectura.rawQuery("SELECT cuenta_id, Cuenta FROM cuentas", null)) {
            while (c.moveToNext()) {
                nombrePorId.put(c.getLong(0), c.getString(1));
                idPorNombre.put(c.getString(1).trim().toLowerCase(Locale.ROOT), c.getLong(0));
            }
        } finally {
            lectura.close();
        }
        for (java.util.Map.Entry<String, List<String[]>> e : porPlantilla.entrySet()) {
            List<NuevoItem> items = new ArrayList<>();
            for (String[] f : e.getValue()) {
                Long id = null;
                try { id = Long.parseLong(f[8].trim()); } catch (NumberFormatException ignorado) { }
                String nombreCuenta = f[9].trim();
                if (id == null || (!nombreCuenta.isEmpty() && !nombreCuenta.equalsIgnoreCase(nombrePorId.get(id)))) {
                    id = idPorNombre.get(nombreCuenta.toLowerCase(Locale.ROOT));
                }
                if (id == null) { omitidos++; continue; }
                int valor = 0;
                try { valor = Integer.parseInt(f[11].trim()); } catch (NumberFormatException ignorado) { }
                items.add(new NuevoItem(id, f[10].trim(), valor, f[12]));
            }
            if (items.isEmpty()) continue;
            String[] cab = e.getValue().get(0);
            String periodicidad = cab[1].trim().isEmpty() ? MENSUAL : cab[1].trim();
            Plantilla existente = buscarPorNombre(ctx, e.getKey());
            long id;
            if (existente != null) {
                reemplazarModelo(ctx, existente.id, periodicidad, cab[6].isEmpty() ? null : cab[6], items);
                id = existente.id;
                actualizadas++;
            } else {
                id = crear(ctx, e.getKey(), periodicidad, cab[6].isEmpty() ? null : cab[6], items);
                if (id < 0) continue;
                creadas++;
            }
            SQLiteDatabase db = abrir(ctx, true);
            try {
                ContentValues v = new ContentValues();
                v.put("activa", "0".equals(cab[2].trim()) ? 0 : 1);
                if (!cab[3].trim().isEmpty()) v.put("creada", cab[3].trim());
                try { v.put("ultimo_uso", Integer.parseInt(cab[4].trim())); } catch (NumberFormatException ignorado) { }
                try { v.put("usos", Integer.parseInt(cab[5].trim())); } catch (NumberFormatException ignorado) { }
                db.update(A1_1_AyudanteBD.TABLE_PLANTILLA, v, "plantilla_id = ?", new String[]{String.valueOf(id)});
            } finally {
                db.close();
            }
        }
        return creadas + " creadas, " + actualizadas + " actualizadas" +
                (omitidos > 0 ? ", " + omitidos + " ítems omitidos (cuenta no encontrada)" : "");
    }

    private static String campoCsv(String v) {
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }

    private static String[] partirCsv(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean comillas = false;
        for (int i = 0; i < linea.length(); i++) {
            char ch = linea.charAt(i);
            if (comillas) {
                if (ch == '"') {
                    if (i + 1 < linea.length() && linea.charAt(i + 1) == '"') { actual.append('"'); i++; }
                    else comillas = false;
                } else actual.append(ch);
            } else if (ch == '"') comillas = true;
            else if (ch == ',') { campos.add(actual.toString()); actual.setLength(0); }
            else actual.append(ch);
        }
        campos.add(actual.toString());
        return campos.toArray(new String[0]);
    }

    /**
     * ¿Lleva 12 meses o más sin uso? Cuenta desde el último uso o, si nunca se usó, desde su
     * creación.
     */
    public static boolean sinUso12Meses(Plantilla p, String creada) {
        int hoy = Integer.parseInt(new SimpleDateFormat("yyyyMMdd", Locale.ROOT).format(new Date()));
        int referencia = p.ultimoUso;
        if (referencia <= 0 && creada != null && creada.length() >= 10) {
            try { referencia = Integer.parseInt(creada.substring(0, 10).replace("-", "")); }
            catch (NumberFormatException ignorado) { referencia = 0; }
        }
        return referencia > 0 && referencia <= hoy - 10000;
    }

    /** Fecha de creación ("aaaa-mm-dd hh:mm") de una plantilla. */
    public static String creada(Context ctx, long plantillaId) {
        SQLiteDatabase db = abrir(ctx, false);
        try (Cursor c = db.rawQuery("SELECT creada FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA +
                " WHERE plantilla_id = ?", new String[]{String.valueOf(plantillaId)})) {
            return c.moveToFirst() ? c.getString(0) : null;
        } finally {
            db.close();
        }
    }

    // ═══════════════════════ Textos ═══════════════════════

    /**
     * Referencia al periodo según la fecha del documento (aaaammdd): "Q1 nov 2026" (quincenal),
     * "nov 2026" (mensual) o "" (sin periodo o fecha inválida).
     */
    public static String periodo(String periodicidad, int fecha) {
        if (fecha < 19000101 || SIN_PERIODO.equals(periodicidad)) return "";
        int anio = fecha / 10000, mes = (fecha / 100) % 100, dia = fecha % 100;
        if (mes < 1 || mes > 12) return "";
        String mesAnio = MESES[mes - 1] + " " + anio;
        if (QUINCENAL.equals(periodicidad)) return (dia <= 15 ? "Q1 " : "Q2 ") + mesAnio;
        return mesAnio;
    }

    /**
     * Pone " · periodo" al final de la descripción, quitando antes uno anterior (si la fecha
     * del documento cambió, el periodo se reemplaza, no se acumula).
     */
    public static String conPeriodo(String descripcion, String periodo) {
        String d = sinPeriodo(descripcion);
        if (periodo.isEmpty()) return d;
        return d.isEmpty() ? periodo : d + " · " + periodo;
    }

    /** Quita un " · Q1 nov 2026" o " · nov 2026" final, si lo hay. */
    public static String sinPeriodo(String descripcion) {
        String d = descripcion == null ? "" : descripcion.trim();
        return d.replaceFirst("\\s*·\\s*(Q[12] )?(ene|feb|mar|abr|may|jun|jul|ago|sep|oct|nov|dic) \\d{4}$", "").trim();
    }

    /** Concepto por defecto: el nombre de la cuenta sin el prefijo CxP, CxC o PAT. */
    public static String conceptoDeCuenta(String cuenta) {
        if (cuenta == null) return "";
        String c = cuenta.trim();
        String[] prefijos = {"CxP ", "CxC ", "PAT "};
        for (String p : prefijos) {
            if (c.regionMatches(true, 0, p, 0, p.length()) && c.length() > p.length()) {
                return c.substring(p.length()).trim();
            }
        }
        return c;
    }

    public static String textoPeriodicidad(String periodicidad) {
        if (QUINCENAL.equals(periodicidad)) return "Quincenal";
        if (SIN_PERIODO.equals(periodicidad)) return "Sin periodo";
        return "Mensual";
    }
}
