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
            // La agregación (SUM) sigue siendo SOLO por cuenta: una fila por
            // cuenta, siempre — eso es lo que evita que una misma cuenta
            // aparezca partida en el Informe.
            //
            // La clasificación (Grupo1/Grupo2) YA NO se lee de la copia
            // guardada en "transacciones" (que puede quedar desactualizada
            // si algún registro no se refrescó al modificar la cuenta — ver
            // guardarModificacion() en B12_DocumentPersistence). Se trae con
            // un LEFT JOIN a "cuentas", la tabla autoritativa que
            // históricamente no se ha visto con atributos corruptos. Así:
            //   a) cada cuenta muestra SIEMPRE una única clasificación
            //      confiable, sin necesidad de "adivinar" con MAX() cuál de
            //      varios valores en conflicto mostrar;
            //   b) la posibilidad de agrupar/leer el Informe por Grupo1 y
            //      Grupo2 como un balance financiero (activo/pasivo y luego
            //      categoría) queda intacta, porque estos campos se siguen
            //      entregando en cada fila — solo que ahora desde una
            //      fuente única y correcta en vez de un snapshot que puede
            //      desalinearse;
            //   c) se usa LEFT JOIN (no INNER JOIN) para que una cuenta que
            //      por alguna razón no aparezca en "cuentas" (p.ej. un
            //      nombre que no calza exactamente) siga siendo VISIBLE en
            //      el Informe con Grupo1/Grupo2 en blanco, en vez de
            //      desaparecer silenciosamente — eso también es una señal
            //      de auditoría.
            // Las transacciones cuya copia interna quedó desalineada frente
            // a "cuentas" se pueden revisar en detalle con el botón de
            // Auditoría de Clasificación (ver A11_AuditoriaClasificacionDialogo).
            String query = "SELECT t.c3_Cuenta AS c3_Cuenta, t.c4_Signo AS c4_Signo, " +
                    "SUM(t.c5_Valor) AS suma, c.Grupo1 AS c10_Grupo1, c.Grupo2 AS c11_Grupo2 " +
                    "FROM transacciones t " +
                    "LEFT JOIN cuentas c ON c.Cuenta = t.c3_Cuenta " +
                    "GROUP BY t.c3_Cuenta";
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

    /**
     * Auditoría de clasificación: devuelve cada transacción cuyo Grupo1 o
     * Grupo2 guardado (snapshot en "transacciones") ya NO coincide con el
     * valor actual en "cuentas" — la causa raíz de la duplicación que se
     * veía antes en el Informe. Cada fila trae ambos valores (el guardado
     * en la transacción y el correcto según "cuentas") para poder
     * corregir el registro puntual desde la app.
     *
     * Se usa "IS NOT" (en vez de "!=") para que también se detecten los
     * casos donde uno de los dos valores quedó vacío/nulo — con "!=" una
     * comparación contra NULL no se marca como diferente y el caso pasaría
     * desapercibido.
     */
    public ArrayList<String[]> obtenerTransaccionesDesalineadas() {
        ArrayList<String[]> desalineadas = new ArrayList<>();
        Cursor cursor = null;
        try {
            openDB();
            String query = "SELECT DISTINCT t.c1_Documento, t.c2_ItemDoc, t.c3_Cuenta, t.c5_Valor, " +
                    "t.c10_Grupo1, t.c11_Grupo2, c.Grupo1, c.Grupo2 " +
                    "FROM transacciones t " +
                    "JOIN cuentas c ON c.Cuenta = t.c3_Cuenta " +
                    "WHERE t.c10_Grupo1 IS NOT c.Grupo1 OR t.c11_Grupo2 IS NOT c.Grupo2 " +
                    "ORDER BY t.c3_Cuenta, t.c1_Documento, t.c2_ItemDoc";
            cursor = db.rawQuery(query, null);

            while (cursor.moveToNext()) {
                desalineadas.add(new String[]{
                        cursor.getString(0), // c1_Documento
                        cursor.getString(1), // c2_ItemDoc
                        cursor.getString(2), // c3_Cuenta
                        cursor.getString(3), // c5_Valor
                        cursor.getString(4), // Grupo1 guardado en la transacción
                        cursor.getString(5), // Grupo2 guardado en la transacción
                        cursor.getString(6), // Grupo1 correcto (según cuentas)
                        cursor.getString(7)  // Grupo2 correcto (según cuentas)
                });
            }
        } finally {
            if (cursor != null) cursor.close();
            closeDB();
        }
        return desalineadas;
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
