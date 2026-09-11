package A2QueryBD;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import A1BASES.A1_1_AyudanteBD;
import A1BASES.A3_1_TipoCuentasGetsYSets;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;

public class A21_OptimizedQuery {

    private A1_1_AyudanteBD dbHelper;
    private SQLiteDatabase db;
    private Context context;
    //private SQLiteDatabase sqliteDatabase_Abstracta; // v2

    // Constructor
    public A21_OptimizedQuery(Context context) {
        this.context = context;
        dbHelper = new A1_1_AyudanteBD(context, "balance", null, 1);
    }

    // Método base para manejo de conexión
    private SQLiteDatabase openDB() throws SQLException {
        if (db == null || !db.isOpen()) {
            db = dbHelper.getReadableDatabase();
        }
        return db;
    }

    private void closeDB() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    /**
     * Método unificado para consultas genéricas
     * Reemplaza a: consultarCuentasOrdenAscendente(), consultarCuentasConciliablesOrdenAscendente(),
     *             consultarCuentasPorNombreCuenta(), consultarDocumentosOrdenAscendente()
     */

    public boolean tablaExiste(String nombreTabla) {
        boolean existe = false;
        try {
            openDB();
            Cursor cursor = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
                    new String[]{nombreTabla}
            );
            existe = cursor != null && cursor.getCount() > 0;
            if (cursor != null) cursor.close();
        } catch (Exception e) {
            existe = false;
        } finally {
            closeDB();
        }
        return existe;
    }

    public <T> List<T> executeQuery(QueryBuilder queryBuilder, ResultMapper<T> mapper) {
        List<T> result = new ArrayList<>();
        Cursor cursor = null;
        try {
            openDB();
            cursor = db.rawQuery(queryBuilder.build(), queryBuilder.getArgs());
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    T item = mapper.map(cursor);
                    if (item != null) result.add(item);
                } while (cursor.moveToNext());
            }
        } catch (android.database.sqlite.SQLiteException e) {
            // ✅ NUEVO: tabla no existe aún, retornar lista vacía sin crashear
            Log.w("A3OptimizedQuery", "Tabla no disponible: " + e.getMessage());
        } finally {
            if (cursor != null) cursor.close();
            closeDB();
        }
        return result;
    }

    /**
     * Método unificado para consultas de transacciones
     * Reemplaza a: queryTransactionsByAccountYFechaTipoTPDf(),
     *             queryTransactionsByAccountTipoT(),
     *             queryTransactionsByDocument()
     */
    public ArrayList<A3_2_TipoTransaccionesGetsYSets> consultarTransacciones(TransactionQueryBuilder builder) {
        ArrayList<A3_2_TipoTransaccionesGetsYSets> resultado = new ArrayList<>();
        Cursor cursor = null;
        try {
            openDB();
            cursor = db.rawQuery(builder.build(), builder.getArgs());
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    resultado.add(mapTransactionFromCursor(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
            closeDB();
        }
        return resultado;
    }

    public ArrayList<A3_1_TipoCuentasGetsYSets> consultarCuentas(TransactionQueryBuilder builder) {
        ArrayList<A3_1_TipoCuentasGetsYSets> resultado = new ArrayList<>();
        Cursor cursor = null;
        try {
            openDB();
            cursor = db.rawQuery(builder.build(), builder.getArgs());
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    resultado.add(mapCuentasFromCursor(cursor));
                } while (cursor.moveToNext());
            }
        } finally {
            if (cursor != null) cursor.close();
            closeDB();
        }
        return resultado;
    }

// Implementacion en prueba fin
    /**
     * Método unificado para sumas y agregaciones
     * Reemplaza a: consultarSumaTransacciones(),
     *             consultarSumaValorTransaccionesPorCuentaEntreDosFechas(),
     *             consultarSumaCuentas()
     */
    public int consultarSuma(String whereClause, String[] whereArgs) {
        int suma = 0;
        Cursor cursor = null;
        try {
            openDB();
            //String query = "SELECT SUM(c5_Valor) FROM transacciones WHERE " + whereClause + "GROUP BY";
            String query = "SELECT SUM(c5_Valor) FROM transacciones WHERE " + whereClause;
            cursor = db.rawQuery(query, whereArgs);
            if (cursor.moveToFirst()) {
                suma = cursor.getInt(0);
            }
        } finally {
            if (cursor != null) cursor.close();
            closeDB();
        }
        return suma;
    }

    // Método para obtener todas las sumas por cuenta
    //**Suma aparte positivos aparte negativos
    public ArrayList<A3_2_TipoTransaccionesGetsYSets> obtenerSumaPorSignoCuentaPorCuenta() {
        ArrayList<A3_2_TipoTransaccionesGetsYSets> cuentasSumadas = new ArrayList<>();
        Cursor cursor = null;
        try {
            openDB();
            // Consulta para obtener las cuentas y sus detalles
            String query = "SELECT DISTINCT c3_Cuenta, c4_Signo, SUM(c5_Valor) AS suma, c10_Grupo1, c11_Grupo2 " +
                    "FROM transacciones GROUP BY c3_Cuenta, c4_Signo, c10_Grupo1, c11_Grupo2";
            cursor = db.rawQuery(query, null);

            while (cursor.moveToNext()) {
                String nombreCuenta = cursor.getString(cursor.getColumnIndex("c3_Cuenta"));
                String signo = cursor.getString(cursor.getColumnIndex("c4_Signo"));
                int suma = cursor.getInt(cursor.getColumnIndex("suma"));
                String grupo1 = cursor.getString(cursor.getColumnIndex("c10_Grupo1"));
                String grupo2 = cursor.getString(cursor.getColumnIndex("c11_Grupo2"));

                cuentasSumadas.add(new A3_2_TipoTransaccionesGetsYSets(nombreCuenta, signo, suma, grupo1, grupo2));
            }
        } finally {
            if (cursor != null) cursor.close();
            closeDB();
        }
        return cuentasSumadas;
    }

    // Método para obtener las sumas agrupadas por cuenta y campos adicionales

    public ArrayList<A3_2_TipoTransaccionesGetsYSets> obtenerSumaNetoCuentaPorCuenta() {
        ArrayList<A3_2_TipoTransaccionesGetsYSets> cuentasSumadas = new ArrayList<>();
        Cursor cursor = null;
        try {
            openDB();
            // Agrupar SOLO por cuenta: antes se agrupaba también por
            // c10_Grupo1/c11_Grupo2, así que si una misma cuenta tenía
            // transacciones con Grupo1/Grupo2 no idénticos entre sí (p.ej.
            // por una modificación que cambió de cuenta sin refrescar su
            // clasificación — ver guardarModificacion() en
            // B12_DocumentPersistence), esa cuenta aparecía partida en
            // varias filas en vez de una sola consolidada. MAX() sobre
            // Grupo1/Grupo2 conserva una clasificación única y visible por
            // fila aunque existan inconsistencias históricas de fondo.
            String query = "SELECT c3_Cuenta, c4_Signo, SUM(c5_Valor) AS suma, " +
                    "MAX(c10_Grupo1) AS c10_Grupo1, MAX(c11_Grupo2) AS c11_Grupo2 " +
                    "FROM transacciones GROUP BY c3_Cuenta";
            cursor = db.rawQuery(query, null);

            while (cursor.moveToNext()) {
                // Obtener los valores de cada columna
                String nombreCuenta = cursor.getString(cursor.getColumnIndex("c3_Cuenta"));
                String signo = cursor.getString(cursor.getColumnIndex("c4_Signo"));
                int suma = cursor.getInt(cursor.getColumnIndex("suma"));
                String grupo1 = cursor.getString(cursor.getColumnIndex("c10_Grupo1"));
                String grupo2 = cursor.getString(cursor.getColumnIndex("c11_Grupo2"));

                // Agregar a la lista como un objeto CuentaSuma
                cuentasSumadas.add(new A3_2_TipoTransaccionesGetsYSets(nombreCuenta, signo, suma, grupo1, grupo2));

            }
        } finally {
            if (cursor != null) cursor.close();
            closeDB();
        }
        return cuentasSumadas;
    }

    // Clases auxiliares
    public static class QueryBuilder {
        private StringBuilder query = new StringBuilder();
        private ArrayList<String> args = new ArrayList<>();

        public QueryBuilder select(String... columns) {
            query.append("SELECT ");
            if (columns.length == 0) {
                query.append("* ");
            } else {
                query.append(String.join(", ", columns)).append(" ");
            }
            return this;
        }

        public QueryBuilder from(String table) {
            query.append("FROM ").append(table).append(" ");
            return this;
        }

        public QueryBuilder where(String condition, String... args) {
            query.append("WHERE ").append(condition).append(" ");
            for (String arg : args) {
                this.args.add(arg);
            }
            return this;
        }

        public QueryBuilder orderBy(String column, boolean asc) {
            query.append("ORDER BY ").append(column);
            query.append(asc ? " ASC" : " DESC");
            return this;
        }

        // Agregando el método limit corregido
        public QueryBuilder limit(int limit) {
            query.append(" LIMIT ").append(limit).append(" ");
            return this;
        }

        public String build() {
            return query.toString();
        }

        public String[] getArgs() {
            return args.toArray(new String[0]);
        }
    }

    public static class TransactionQueryBuilder extends QueryBuilder {
        public TransactionQueryBuilder porCuenta(String cuenta) {
            return (TransactionQueryBuilder) where("c3_Cuenta LIKE ?", cuenta);
        }

        public TransactionQueryBuilder entreFechas(int fecha1, int fecha2) {
            return (TransactionQueryBuilder) where(
                    "c8_FechaInicial >= ? AND c8_FechaInicial <= ?",
                    String.valueOf(fecha1), String.valueOf(fecha2)
            );
        }
    }

    public interface ResultMapper<T> {
        T map(Cursor cursor);
    }

    // Métodos auxiliares de mapeo
    private A3_2_TipoTransaccionesGetsYSets mapTransactionFromCursor(Cursor cursor) {
        return new A3_2_TipoTransaccionesGetsYSets(
                cursor.getString(0),  // documento
                cursor.getString(1),  // tipo
                cursor.getString(2),  // fecha
                cursor.getString(3),  // cuenta
                cursor.getInt(4),    // valor
                cursor.getString(5),  // descripcion
                cursor.getString(6),  // conciliacion
                cursor.getInt(7),    // fechaInicial
                cursor.getString(8),  // fechaFinal
                cursor.getString(9),  // documento_soporte
                cursor.getString(10), // grupo1
                cursor.getString(11), // grupo2
                cursor.getString(12)  // grupo3
        );
    }

    // Métodos auxiliares de mapeo
    private A3_1_TipoCuentasGetsYSets mapCuentasFromCursor(Cursor cursor) {
        return new A3_1_TipoCuentasGetsYSets(
                cursor.getString(0),  // documento
                cursor.getString(1),  // tipo
                cursor.getString(2),  // fecha
                cursor.getString(3),  // cuenta
                cursor.getString(4)   // valor

        );
    }
}
