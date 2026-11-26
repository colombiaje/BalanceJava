package A1BASES;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * ⭐ NUEVO (26-nov, Jorge): fusionar dos cuentas. La cuenta ORIGEN desaparece y todo lo suyo
 * pasa a la cuenta DESTINO. Decisiones de Jorge (a–d, 26-nov):
 *  a. Los documentos donde aparecen ambas quedan con sus dos líneas (ya de la misma cuenta, se
 *     anulan entre sí): no se pierde la traza.
 *  b. Saldo inicial (doc 0): si ambas lo tienen, se suma en UNA sola línea de la destino.
 *  c. La cuenta origen se borra. Las fotos, ajustes y soportes de renta de años anteriores
 *     conservan su nombre como texto (no se tocan: son el pasado).
 *  d. Si la origen tiene inventario (artículos), NO se fusiona en esta versión.
 * Pasan a la destino: transacciones, historial, plantillas y borradores. La destino queda "Con
 * historial" si alguna lo tenía, desde la fecha más antigua; y con la marca de seguimiento si la
 * origen la tenía. No cambia la estructura de la base (sin versión nueva).
 * Cotejo: saldo destino = suma de las dos, y total general igual; si no, se deshace todo.
 */
public final class A39_FusionCuentas {

    private A39_FusionCuentas() { }

    public static class Revision {
        public long origen, destino;
        public String nombreOrigen = "", nombreDestino = "";
        public String tipoOrigen = "", tipoDestino = "";
        public long lineasOrigen, lineasDestino, saldoOrigen, saldoDestino;
        public long lineasHistorialOrigen, lineasPlantillas;
        public int saldosInicialesOrigen, saldosInicialesDestino;
        public List<String> documentosAmbas = new ArrayList<>();
        public List<String> bloqueos = new ArrayList<>();
        public List<String> avisos = new ArrayList<>();
    }

    public static class Resultado {
        public boolean ok;
        public String mensaje = "";
    }

    private static long numero(SQLiteDatabase db, String sql, Object... args) {
        String[] a = new String[args.length];
        for (int i = 0; i < args.length; i++) a[i] = String.valueOf(args[i]);
        try (Cursor c = db.rawQuery(sql, a)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getLong(0) : 0;
        }
    }

    private static String texto(SQLiteDatabase db, String sql, Object... args) {
        String[] a = new String[args.length];
        for (int i = 0; i < args.length; i++) a[i] = String.valueOf(args[i]);
        try (Cursor c = db.rawQuery(sql, a)) {
            return c.moveToFirst() && !c.isNull(0) ? c.getString(0) : "";
        }
    }

    private static boolean existeTabla(SQLiteDatabase db, String tabla) {
        return numero(db, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", tabla) > 0;
    }

    /** Lista "Nombre" de todas las cuentas con su id, en orden alfabético: {id, nombre}. */
    public static List<Object[]> cuentas(SQLiteDatabase db) {
        List<Object[]> l = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT cuenta_id, Cuenta FROM cuentas ORDER BY Cuenta COLLATE NOCASE", null)) {
            while (c.moveToNext()) l.add(new Object[]{c.getLong(0), c.getString(1)});
        }
        return l;
    }

    private static final String TIPO = "SELECT IFNULL(t.nombre, '(sin tipo)') FROM cuentas c"
            + " LEFT JOIN " + A1_1_AyudanteBD.TABLE_TIPO_CUENTA + " t ON t.tipo_cuenta_id = c.tipo_cuenta_id"
            + " WHERE c.cuenta_id = ?";

    public static Revision revisar(SQLiteDatabase db, long origen, long destino) {
        Revision r = new Revision();
        r.origen = origen;
        r.destino = destino;
        r.nombreOrigen = texto(db, "SELECT Cuenta FROM cuentas WHERE cuenta_id = ?", origen);
        r.nombreDestino = texto(db, "SELECT Cuenta FROM cuentas WHERE cuenta_id = ?", destino);
        if (origen == destino) r.bloqueos.add("La cuenta origen y la destino son la misma.");
        if (r.nombreOrigen.isEmpty() || r.nombreDestino.isEmpty()) {
            r.bloqueos.add("No se encontró alguna de las dos cuentas.");
            return r;
        }
        r.tipoOrigen = texto(db, TIPO, origen);
        r.tipoDestino = texto(db, TIPO, destino);
        r.lineasOrigen = numero(db, "SELECT COUNT(*) FROM transacciones WHERE cuenta_id = ?", origen);
        r.lineasDestino = numero(db, "SELECT COUNT(*) FROM transacciones WHERE cuenta_id = ?", destino);
        r.saldoOrigen = numero(db, "SELECT SUM(c5_Valor) FROM transacciones WHERE cuenta_id = ?", origen);
        r.saldoDestino = numero(db, "SELECT SUM(c5_Valor) FROM transacciones WHERE cuenta_id = ?", destino);
        r.saldosInicialesOrigen = (int) numero(db, "SELECT COUNT(*) FROM transacciones WHERE cuenta_id = ?"
                + " AND CAST(c1_Documento AS INTEGER) = 0", origen);
        r.saldosInicialesDestino = (int) numero(db, "SELECT COUNT(*) FROM transacciones WHERE cuenta_id = ?"
                + " AND CAST(c1_Documento AS INTEGER) = 0", destino);
        if (existeTabla(db, A1_1_AyudanteBD.TABLE_HISTORIAL)) {
            r.lineasHistorialOrigen = numero(db, "SELECT COUNT(*) FROM " + A1_1_AyudanteBD.TABLE_HISTORIAL
                    + " WHERE cuenta_id = ?", origen);
        }
        for (String t : new String[]{A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM_ANTERIOR}) {
            if (existeTabla(db, t)) r.lineasPlantillas += numero(db, "SELECT COUNT(*) FROM " + t + " WHERE cuenta_id = ?", origen);
        }
        try (Cursor c = db.rawQuery("SELECT c1_Documento FROM transacciones WHERE cuenta_id IN (?, ?)"
                + " AND CAST(c1_Documento AS INTEGER) <> 0 GROUP BY c1_Documento"
                + " HAVING COUNT(DISTINCT cuenta_id) = 2 ORDER BY CAST(c1_Documento AS INTEGER)",
                new String[]{String.valueOf(origen), String.valueOf(destino)})) {
            while (c.moveToNext()) r.documentosAmbas.add(c.getString(0));
        }
        // d. Inventario: bloqueo si la origen tiene artículos.
        if (numero(db, "SELECT COUNT(*) FROM items_inventario WHERE cuenta_id = ?", origen) > 0) {
            r.bloqueos.add("\"" + r.nombreOrigen + "\" tiene artículos de inventario: en esta versión no se fusiona"
                    + " una cuenta con inventario. Fusiona antes de asignar inventario.");
        }
        if (!r.tipoOrigen.equals(r.tipoDestino)) {
            r.avisos.add("Son de distinto tipo de cuenta: \"" + r.tipoOrigen + "\" y \"" + r.tipoDestino
                    + "\". Queda el tipo de la destino.");
        }
        long conInvDestino = numero(db, "SELECT con_inventario FROM cuentas WHERE cuenta_id = ?", destino);
        if (conInvDestino == 1) {
            r.avisos.add("\"" + r.nombreDestino + "\" maneja inventario: las líneas que llegan quedan pendientes"
                    + " en 📦 Pendientes.");
        }
        return r;
    }

    /** Ejecuta la fusión en UNA transacción. Quien llama hace antes el respaldo. */
    public static Resultado ejecutar(SQLiteDatabase db, long origen, long destino) {
        Resultado res = new Resultado();
        Revision r = revisar(db, origen, destino);
        if (!r.bloqueos.isEmpty()) {
            res.mensaje = String.join("\n", r.bloqueos);
            return res;
        }
        String[] o = {String.valueOf(origen)};
        long saldoEsperado = r.saldoOrigen + r.saldoDestino;
        long lineasEsperadas = r.lineasOrigen + r.lineasDestino;
        long totalAntes = numero(db, "SELECT SUM(c5_Valor) FROM transacciones");
        long lineasTotalesAntes = numero(db, "SELECT COUNT(*) FROM transacciones");

        db.beginTransaction();
        try {
            // b. Saldo inicial: si ambas lo tienen, una sola línea en la destino con la suma.
            int lineasBorradasDoc0 = 0;
            // Si alguna de esas líneas tiene inventario asignado no se unen (borrarla se llevaría
            // sus unidades): quedan como dos líneas.
            long doc0ConInventario = numero(db, "SELECT COUNT(*) FROM transacciones_inventario ti"
                    + " JOIN transacciones t ON t.transaccion_id = ti.transaccion_id"
                    + " WHERE t.cuenta_id IN (?, ?) AND CAST(t.c1_Documento AS INTEGER) = 0", origen, destino);
            if (r.saldosInicialesOrigen > 0 && r.saldosInicialesDestino > 0 && doc0ConInventario == 0) {
                long suma = numero(db, "SELECT SUM(c5_Valor) FROM transacciones WHERE cuenta_id IN (?, ?)"
                        + " AND CAST(c1_Documento AS INTEGER) = 0", origen, destino);
                long queda = numero(db, "SELECT MIN(transaccion_id) FROM transacciones WHERE cuenta_id = ?"
                        + " AND CAST(c1_Documento AS INTEGER) = 0", destino);
                lineasBorradasDoc0 = db.delete("transacciones", "cuenta_id IN (?, ?) AND CAST(c1_Documento AS INTEGER) = 0"
                        + " AND transaccion_id <> ?", new String[]{String.valueOf(origen), String.valueOf(destino),
                        String.valueOf(queda)});
                ContentValues v = new ContentValues();
                v.put("c5_Valor", suma);
                db.update("transacciones", v, "transaccion_id = ?", new String[]{String.valueOf(queda)});
            }

            // Transacciones (a: las de documentos con ambas cuentas se conservan).
            ContentValues v = new ContentValues();
            v.put("cuenta_id", destino);
            db.update("transacciones", v, "cuenta_id = ?", o);

            // Historial, plantillas y borradores.
            if (existeTabla(db, A1_1_AyudanteBD.TABLE_HISTORIAL)) {
                db.update(A1_1_AyudanteBD.TABLE_HISTORIAL, v, "cuenta_id = ?", o);
            }
            for (String t : new String[]{A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM, A1_1_AyudanteBD.TABLE_PLANTILLA_ITEM_ANTERIOR}) {
                if (existeTabla(db, t)) db.update(t, v, "cuenta_id = ?", o);
            }
            if (existeTabla(db, A1_1_AyudanteBD.TABLE_CACHE_RECORDS)) {
                ContentValues vc = new ContentValues();
                vc.put("cuenta_id", destino);
                vc.put("c3_Cuenta", r.nombreDestino);
                db.update(A1_1_AyudanteBD.TABLE_CACHE_RECORDS, vc, "cuenta_id = ?", o);
            }

            // Marcas de la destino: historial (la más antigua) y seguimiento.
            try (Cursor c = db.rawQuery("SELECT con_historial, historial_desde, cuenta_seguimiento FROM cuentas"
                    + " WHERE cuenta_id = ?", o)) {
                if (c.moveToFirst()) {
                    ContentValues m = new ContentValues();
                    if (c.getInt(0) == 1) {
                        m.put("con_historial", 1);
                        if (!c.isNull(1)) {
                            long desdeO = c.getLong(1);
                            long desdeD = numero(db, "SELECT IFNULL(historial_desde, 0) FROM cuentas WHERE cuenta_id = ?", destino);
                            if (desdeD == 0 || desdeO < desdeD) m.put("historial_desde", desdeO);
                        }
                    }
                    if (!c.isNull(2) && c.getInt(2) == 1) m.put("cuenta_seguimiento", 1);
                    if (m.size() > 0) db.update("cuentas", m, "cuenta_id = ?", new String[]{String.valueOf(destino)});
                }
            }

            // c. Se borra la cuenta origen.
            if (db.delete("cuentas", "cuenta_id = ?", o) != 1) {
                throw new IllegalStateException("No se pudo borrar la cuenta origen.");
            }

            // Cotejo.
            long saldoDespues = numero(db, "SELECT SUM(c5_Valor) FROM transacciones WHERE cuenta_id = ?", destino);
            long lineasDespues = numero(db, "SELECT COUNT(*) FROM transacciones WHERE cuenta_id = ?", destino);
            long totalDespues = numero(db, "SELECT SUM(c5_Valor) FROM transacciones");
            long lineasTotalesDespues = numero(db, "SELECT COUNT(*) FROM transacciones");
            long quedanOrigen = numero(db, "SELECT COUNT(*) FROM transacciones WHERE cuenta_id = ?", origen);
            if (saldoDespues != saldoEsperado || totalDespues != totalAntes || quedanOrigen != 0
                    || lineasDespues != lineasEsperadas - lineasBorradasDoc0
                    || lineasTotalesDespues != lineasTotalesAntes - lineasBorradasDoc0) {
                throw new IllegalStateException("El cotejo no cuadró (saldo " + saldoDespues + " ≠ " + saldoEsperado
                        + ", total " + totalDespues + " vs " + totalAntes + "). No se hizo ningún cambio.");
            }
            db.setTransactionSuccessful();
            res.ok = true;
            res.mensaje = "\"" + r.nombreOrigen + "\" quedó fusionada en \"" + r.nombreDestino + "\".\n\n"
                    + "Líneas movidas: " + r.lineasOrigen
                    + (lineasBorradasDoc0 > 0 ? " (saldos iniciales unidos en una línea)" : "") + "\n"
                    + "Saldo de \"" + r.nombreDestino + "\": " + saldoDespues + " ✓ (= " + r.saldoDestino + " + " + r.saldoOrigen + ")\n"
                    + "Total general sin cambio ✓";
        } catch (Exception e) {
            res.ok = false;
            res.mensaje = e.getMessage();
        } finally {
            db.endTransaction();
        }
        return res;
    }
}
