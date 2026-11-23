package A1BASES;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * ⭐ NUEVO (23-nov, Jorge, historial P2): cierre por período, como el cierre contable real.
 *
 * Salen DOCUMENTOS COMPLETOS con fecha (Fecha inicial) hasta el corte; los posteriores quedan
 * intactos. Así ningún documento queda descuadrado (el problema del cierre de algunas cuentas).
 *  1. Revisar: documentos a cerrar, documentos que quedan y documentos "mixtos" (líneas a ambos
 *     lados del corte). Si hay mixtos no se cierra: se listan para corregirlos antes.
 *  2. Ejecutar, en UNA transacción:
 *     - las líneas de esos documentos de las cuentas "Con historial" se copian al historial;
 *     - se borran esos documentos y el doc 0 anterior;
 *     - el doc 0 nuevo trae, por cuenta, doc 0 anterior + movimientos del período, fechado el
 *       día siguiente al corte;
 *     - cotejo: el saldo de cada cuenta (suma de todas sus líneas) debe ser el mismo antes y
 *       después; si no, se deshace todo. Y el historial de cada cuenta debe sumar su doc 0.
 * Una fecha sin valor o 0 (filas antiguas) se toma como anterior al corte.
 */
public final class A37_CierrePeriodo {

    private A37_CierrePeriodo() { }

    private static final String DOC = "CAST(c1_Documento AS INTEGER)";
    private static final String FECHA = "IFNULL(c8_FechaInicial, 0)";

    public static class Revision {
        public int corte;
        public long documentosACerrar, lineasACerrar, documentosQuedan;
        public List<String> mixtos = new ArrayList<>();
        public List<String> cuentasConHistorial = new ArrayList<>();
        public long lineasAlHistorial;
    }

    public static class Resultado {
        public boolean ok;
        public String mensaje = "";
        public long documentos, lineas, saldosIniciales, lineasHistorial;
        public List<String> avisosHistorial = new ArrayList<>();
    }

    /** Día siguiente a una fecha AAAAMMDD. */
    public static int diaSiguiente(int aaaammdd) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(aaaammdd / 10000, (aaaammdd / 100) % 100 - 1, aaaammdd % 100);
        c.add(Calendar.DAY_OF_MONTH, 1);
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    public static Revision revisar(SQLiteDatabase db, int corte) {
        Revision r = new Revision();
        r.corte = corte;
        try (Cursor c = db.rawQuery("SELECT " + DOC + ", MIN(" + FECHA + "), MAX(" + FECHA + "), COUNT(*)"
                + " FROM transacciones WHERE " + DOC + " <> 0 GROUP BY " + DOC + " ORDER BY " + DOC, null)) {
            while (c.moveToNext()) {
                long min = c.getLong(1), max = c.getLong(2);
                if (max <= corte) {
                    r.documentosACerrar++;
                    r.lineasACerrar += c.getLong(3);
                } else if (min <= corte) {
                    r.mixtos.add("Doc " + c.getLong(0) + ": fechas del " + min + " al " + max);
                } else {
                    r.documentosQuedan++;
                }
            }
        }
        try (Cursor c = db.rawQuery("SELECT Cuenta FROM cuentas WHERE con_historial = 1 ORDER BY Cuenta", null)) {
            while (c.moveToNext()) r.cuentasConHistorial.add(c.getString(0));
        }
        try (Cursor c = db.rawQuery("SELECT COUNT(*) FROM transacciones t JOIN cuentas c ON c.cuenta_id = t.cuenta_id"
                + " WHERE c.con_historial = 1 AND " + DOC + " <> 0 AND " + DOC + " IN (" + docsACerrar(corte) + ")", null)) {
            if (c.moveToFirst()) r.lineasAlHistorial = c.getLong(0);
        }
        return r;
    }

    /** Subconsulta: números de documento (sin el 0) cuyas líneas son todas hasta el corte. */
    private static String docsACerrar(int corte) {
        return "SELECT " + DOC + " FROM transacciones WHERE " + DOC + " <> 0 GROUP BY " + DOC
                + " HAVING MAX(" + FECHA + ") <= " + corte;
    }

    private static Map<Long, Long> saldos(SQLiteDatabase db, String sql) {
        Map<Long, Long> m = new HashMap<>();
        try (Cursor c = db.rawQuery(sql, null)) {
            while (c.moveToNext()) m.put(c.isNull(0) ? -1L : c.getLong(0), c.getLong(1));
        }
        return m;
    }

    public static Resultado ejecutar(SQLiteDatabase db, int corte) {
        Resultado r = new Resultado();
        Revision rev = revisar(db, corte);
        if (!rev.mixtos.isEmpty()) {
            r.mensaje = "Hay " + rev.mixtos.size() + " documento(s) con líneas a ambos lados del corte; no se cerró nada.";
            return r;
        }
        String ahora = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.US).format(new Date());
        int fechaSaldo = diaSiguiente(corte);
        String sqlSaldoTotal = "SELECT cuenta_id, IFNULL(SUM(c5_Valor), 0) FROM transacciones GROUP BY cuenta_id";
        db.beginTransaction();
        try {
            Map<Long, Long> antes = saldos(db, sqlSaldoTotal);
            db.execSQL("DROP TABLE IF EXISTS temp.cierre_docs");
            db.execSQL("CREATE TEMP TABLE cierre_docs AS " + docsACerrar(corte));
            String enCierre = DOC + " IN (SELECT * FROM temp.cierre_docs)";
            // Saldo inicial nuevo: doc 0 anterior + movimientos del período, por cuenta.
            List<long[]> nuevos = new ArrayList<>(); // {cuenta_id (-1 = sin cuenta), saldo}
            Map<Long, Long> tipos = new HashMap<>();
            try (Cursor c = db.rawQuery("SELECT t.cuenta_id, IFNULL(SUM(t.c5_Valor), 0), MAX(cu.tipo_cuenta_id)"
                    + " FROM transacciones t LEFT JOIN cuentas cu ON cu.cuenta_id = t.cuenta_id"
                    + " WHERE " + DOC + " = 0 OR " + enCierre + " GROUP BY t.cuenta_id ORDER BY t.cuenta_id", null)) {
                while (c.moveToNext()) {
                    long id = c.isNull(0) ? -1L : c.getLong(0);
                    nuevos.add(new long[]{id, c.getLong(1)});
                    if (!c.isNull(2)) tipos.put(id, c.getLong(2));
                }
            }
            try (Cursor c = db.rawQuery("SELECT COUNT(*), COUNT(DISTINCT " + DOC + ") FROM transacciones WHERE " + enCierre, null)) {
                if (c.moveToFirst()) {
                    r.lineas = c.getLong(0);
                    r.documentos = c.getLong(1);
                }
            }
            // Historial: copia de las líneas del período de las cuentas "Con historial".
            db.execSQL("INSERT INTO " + A1_1_AyudanteBD.TABLE_HISTORIAL + " (cuenta_id, tipo, periodo_corte, fecha_registro,"
                    + " transaccion_id_original, c1_Documento, c2_ItemDoc, c5_Valor, c6_Descripcion, c7_FechaYHora,"
                    + " c8_FechaInicial, c9_FechaModificacion)"
                    + " SELECT t.cuenta_id, '" + A1_1_AyudanteBD.HISTORIAL_MOVIMIENTO + "', " + corte + ", '" + ahora + "',"
                    + " t.transaccion_id, t.c1_Documento, t.c2_ItemDoc, IFNULL(t.c5_Valor, 0), t.c6_Descripcion, t.c7_FechaYHora,"
                    + " t.c8_FechaInicial, t.c9_FechaModificacion"
                    + " FROM transacciones t JOIN cuentas c ON c.cuenta_id = t.cuenta_id"
                    + " WHERE c.con_historial = 1 AND " + enCierre
                    + " ORDER BY " + DOC + ", CAST(t.c2_ItemDoc AS INTEGER)");
            try (Cursor c = db.rawQuery("SELECT changes()", null)) {
                if (c.moveToFirst()) r.lineasHistorial = c.getLong(0);
            }
            // Borrar el período y el doc 0 anterior; escribir el doc 0 nuevo.
            db.execSQL("DELETE FROM transacciones WHERE " + DOC + " = 0 OR " + enCierre);
            int item = 1;
            String descripcion = "Saldo inicial al " + (fechaSaldo % 100) + "/" + ((fechaSaldo / 100) % 100) + "/"
                    + (fechaSaldo / 10000) + " (cierre por período)";
            for (long[] n : nuevos) {
                ContentValues v = new ContentValues();
                v.put("c1_Documento", "0");
                v.put("c2_ItemDoc", String.valueOf(item++));
                v.put("c5_Valor", n[1]);
                v.put("c6_Descripcion", descripcion);
                v.put("c7_FechaYHora", ahora);
                v.put("c8_FechaInicial", fechaSaldo);
                v.put("c9_FechaModificacion", "n a");
                if (n[0] >= 0) v.put("cuenta_id", n[0]); else v.putNull("cuenta_id");
                if (tipos.containsKey(n[0])) v.put("tipo_cuenta_id", tipos.get(n[0]));
                if (db.insert("transacciones", null, v) < 0) throw new IllegalStateException("No se pudo escribir el saldo inicial.");
            }
            r.saldosIniciales = nuevos.size();
            db.execSQL("DROP TABLE IF EXISTS temp.cierre_docs");
            // Cotejo 1: saldo de cada cuenta igual antes y después (si no, se deshace todo).
            Map<Long, Long> despues = saldos(db, sqlSaldoTotal);
            TreeSet<Long> todas = new TreeSet<>(antes.keySet());
            todas.addAll(despues.keySet());
            for (Long id : todas) {
                long a = antes.containsKey(id) ? antes.get(id) : 0L, d = despues.containsKey(id) ? despues.get(id) : 0L;
                if (a != d) throw new IllegalStateException("El saldo de la cuenta " + id + " cambiaría de " + a + " a " + d + ".");
            }
            // Cotejo 2: historial de cada cuenta = su doc 0 nuevo (aviso, no deshace).
            try (Cursor c = db.rawQuery("SELECT c.Cuenta, (SELECT IFNULL(SUM(h.c5_Valor), 0) FROM "
                    + A1_1_AyudanteBD.TABLE_HISTORIAL + " h WHERE h.cuenta_id = c.cuenta_id),"
                    + " (SELECT IFNULL(SUM(t.c5_Valor), 0) FROM transacciones t WHERE t.cuenta_id = c.cuenta_id AND "
                    + DOC + " = 0) FROM cuentas c WHERE c.con_historial = 1", null)) {
                while (c.moveToNext()) {
                    if (c.getLong(1) != c.getLong(2)) {
                        r.avisosHistorial.add(c.getString(0) + ": historial " + c.getLong(1) + " ≠ saldo inicial " + c.getLong(2));
                    }
                }
            }
            db.setTransactionSuccessful();
            r.ok = true;
            r.mensaje = "Cierre por período al " + (corte % 100) + "/" + ((corte / 100) % 100) + "/" + (corte / 10000) + " hecho.";
        } catch (Exception e) {
            r.ok = false;
            r.mensaje = "No se cerró nada: " + e.getMessage();
        } finally {
            db.endTransaction();
        }
        return r;
    }
}
