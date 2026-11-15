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
        SQLiteDatabase db = abrir(ctx, true);
        db.beginTransaction();
        try {
            String[] id = {String.valueOf(plantillaId)};
            db.delete(A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM_ANTERIOR, "plantilla_id = ?", id);
            db.execSQL("INSERT INTO " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM_ANTERIOR +
                    " SELECT * FROM " + A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM + " WHERE plantilla_id = ?", id);
            db.delete(A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, "plantilla_id = ?", id);
            escribirItems(db, A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, plantillaId, items);
            if (periodicidad != null) {
                ContentValues v = new ContentValues();
                v.put("periodicidad", periodicidad);
                db.update(A1_1_AyudanteBD.TABLE_PLANTILLA, v, "plantilla_id = ?", id);
            }
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

    /** Agrega " · periodo" a la descripción, una sola vez. */
    public static String conPeriodo(String descripcion, String periodo) {
        String d = descripcion == null ? "" : descripcion.trim();
        if (periodo.isEmpty() || d.endsWith(periodo)) return d;
        return d.isEmpty() ? periodo : d + " · " + periodo;
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
