package A2QueryBD;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

import A1BASES.A1_1_AyudanteBD;
import A1BASES.A3_1_TipoCuentasGetsYSets;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;

public class A22_QueryManager {
    private final Context context;
    private final A21_OptimizedQuery a3_2_consultas_para_queryManager;

    private static A1_1_AyudanteBD a1AyudanteBD;  // Instancia local para cada QueryManager

    // Constructor donde instanciamos A3_2_a3_2_consultas_para_queryManager y A1_AyudanteBD
    public A22_QueryManager(Context context) {
        this.context = context;
        this.a3_2_consultas_para_queryManager = new A21_OptimizedQuery(context);  // Instancia una vez
        this.a1AyudanteBD = new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF); // Instanciamos A1_AyudanteBD
    }

    //Inicio metodos
    public A23_QueryResult<A3_2_TipoTransaccionesGetsYSets> queryAllTransactions() {
        ArrayList<A3_2_TipoTransaccionesGetsYSets> todasLasTransacciones = a3_2_consultas_para_queryManager.consultarTransacciones(
                (A21_OptimizedQuery.TransactionQueryBuilder) new A21_OptimizedQuery.TransactionQueryBuilder()
                        .select("*")
                        .from("transacciones")
        );
        return new A23_QueryResult<>(todasLasTransacciones.size(), todasLasTransacciones, "Consulta exitosa");
    }
    
    public A23_QueryResult<A3_2_TipoTransaccionesGetsYSets> queryTransactionsByDocument(String documentoABuscar) {
        // Construir el query builder
        A21_OptimizedQuery.TransactionQueryBuilder queryBuilder =
                (A21_OptimizedQuery.TransactionQueryBuilder) new A21_OptimizedQuery.TransactionQueryBuilder()
                        .select("*")
                        .from("transacciones")
                        .where("c1_Documento LIKE ?", "%" + documentoABuscar + "%"); // Permite búsqueda parcial

        // Ejecutar la consulta y obtener los resultados
        ArrayList<A3_2_TipoTransaccionesGetsYSets> transacciones = a3_2_consultas_para_queryManager.consultarTransacciones(queryBuilder);

        // Retornar los resultados en un QueryResult
        return new A23_QueryResult<>(transacciones.size(), transacciones, "Consulta exitosa");
    }

    public A23_QueryResult<A3_2_TipoTransaccionesGetsYSets> queryTransactionsByAccount(
            String cuentaDeConsulta) {
        // Construir el query builder con las columnas específicas
        A21_OptimizedQuery.TransactionQueryBuilder queryBuilder =
                (A21_OptimizedQuery.TransactionQueryBuilder) new A21_OptimizedQuery.TransactionQueryBuilder()
                        .select("*") // Solo las columnas necesarias
                        .from("transacciones")
                        .where("c3_Cuenta = ?" ,cuentaDeConsulta);

        // Ejecutar la consulta y obtener los resultados
        ArrayList<A3_2_TipoTransaccionesGetsYSets> transacciones = a3_2_consultas_para_queryManager.consultarTransacciones(queryBuilder);

        // Retornar los resultados en un QueryResult
        return new A23_QueryResult<>(transacciones.size(), transacciones, "Consulta exitosa");
    }

    //Con filtro de 4 columnas para el GridView
    public A23_QueryResult<A3_2_TipoTransaccionesGetsYSets> queryFilteredTransactionsByAccountAndDateRange(
            String cuentaDeConsulta, int fechaInicio, int fechaFin) {
        // Construir el query builder con las columnas específicas
        A21_OptimizedQuery.TransactionQueryBuilder queryBuilder =
                (A21_OptimizedQuery.TransactionQueryBuilder) new A21_OptimizedQuery.TransactionQueryBuilder()
                        .select("*") // Solo las columnas necesarias
                        .from("transacciones")
                        .where("c3_Cuenta = ? AND c8_FechaInicial >= ? AND c8_FechaInicial <= ?",
                                cuentaDeConsulta,  // Filtro exacto para cuenta
                                String.valueOf(fechaInicio), // Filtro exacto para la fecha de inicio
                                String.valueOf(fechaFin)); // Filtro exacto para la fecha de fin

        // Ejecutar la consulta y obtener los resultados
        ArrayList<A3_2_TipoTransaccionesGetsYSets> transacciones = a3_2_consultas_para_queryManager.consultarTransacciones(queryBuilder);

        // Retornar los resultados en un QueryResult
        return new A23_QueryResult<>(transacciones.size(), transacciones, "Consulta exitosa");
    }

    public String queryTransactionsByDocumentDescription(String descripcionPlantillaABuscar) {

        // Construir el query builder para buscar el documento correspondiente
        A21_OptimizedQuery.QueryBuilder queryBuilder = new A21_OptimizedQuery.QueryBuilder()
                .select("c1_Documento") // Seleccionamos solo la columna del documento
                .from("transacciones")
                .where("c6_Descripcion LIKE ?", "%" + descripcionPlantillaABuscar + "%"); // Permite búsqueda parcial

        // Ejecutar la consulta y obtener los resultados
        List<String> documentos = a3_2_consultas_para_queryManager.executeQuery(queryBuilder, cursor -> cursor.getString(0)); // Mapear resultados

        // Retornar el primer resultado si existe, o un mensaje de error si no hay coincidencias
        return documentos.isEmpty() ? "No se encontró ningún documento para la descripción ingresada" : documentos.get(0);

    }

    public A23_QueryResult<String> queryTransactionsByDescriptions() {
        // Construimos el TransactionQueryBuilder para seleccionar y ordenar descripciones únicas
        A21_OptimizedQuery.TransactionQueryBuilder transactionBuilder =
                (A21_OptimizedQuery.TransactionQueryBuilder) new A21_OptimizedQuery.TransactionQueryBuilder()
                        .select("DISTINCT c6_Descripcion")  // Selecciona solo descripciones únicas
                        .from("transacciones")             // Nombre de la tabla
                        .orderBy("c6_Descripcion", true);  // Orden alfabético ascendente

        // Ejecutamos la consulta y mapeamos los resultados
        List<String> descripciones = a3_2_consultas_para_queryManager.executeQuery(transactionBuilder, cursor -> cursor.getString(cursor.getColumnIndex("c6_Descripcion")));

        if (!descripciones.isEmpty()) {
            return new A23_QueryResult<>(descripciones.size(), new ArrayList<>(descripciones), "Consulta exitosa");
        } else {
            return new A23_QueryResult<>(0, new ArrayList<>(), "No se encontraron descripciones de transacciones");
        }
    }

    public boolean tablaExiste(String nombreTabla) {
        return a3_2_consultas_para_queryManager.tablaExiste(nombreTabla);
    }

    public A23_QueryResult<String> queryTransactionsByDescriptionsWithPlantillaFilter() {
        // Construimos el query para buscar solo descripciones que contengan "plantilla"
        A21_OptimizedQuery.TransactionQueryBuilder transactionBuilder =
                (A21_OptimizedQuery.TransactionQueryBuilder) new A21_OptimizedQuery.TransactionQueryBuilder()
                        .select("DISTINCT c6_Descripcion")
                        .from("transacciones")
                        .where("c6_Descripcion LIKE ?", new String[]{"%plantilla%"})  // Filtro base
                        .orderBy("c6_Descripcion", true);  // Orden alfabético ascendente

        // Ejecutamos la consulta
            List<String> descripciones = a3_2_consultas_para_queryManager.executeQuery(
                transactionBuilder,
                cursor -> cursor.getString(cursor.getColumnIndex("c6_Descripcion"))
        );

        if (!descripciones.isEmpty()) {
            return new A23_QueryResult<>(descripciones.size(), new ArrayList<>(descripciones),
                    "Consulta exitosa - plantillas encontradas");
        } else {
            return new A23_QueryResult<>(0, new ArrayList<>(),
                    "No se encontraron plantillas");
        }
    }

    public A23_QueryResult<A3_1_TipoCuentasGetsYSets> queryAllAccounts() {
        ArrayList<A3_1_TipoCuentasGetsYSets> todasLasCuentas = a3_2_consultas_para_queryManager.consultarCuentas(
                (A21_OptimizedQuery.TransactionQueryBuilder) new A21_OptimizedQuery.TransactionQueryBuilder()
                        .select("*")
                        .from("cuentas")
        );
        return new A23_QueryResult<>(todasLasCuentas.size(), todasLasCuentas, "Consulta exitosa");
    }

    // Método para obtener las sumas agrupadas por cuenta y signo
    public ArrayList<A3_2_TipoTransaccionesGetsYSets> obtenerConsultaSumaPorSignoCuentaPorCuenta() {
        return a3_2_consultas_para_queryManager.obtenerSumaPorSignoCuentaPorCuenta();
    }

    // Método para obtener las sumas agrupadas por cuenta
    public ArrayList<A3_2_TipoTransaccionesGetsYSets> queryNetSumTransactionsAccountByAccount() {
        return a3_2_consultas_para_queryManager.obtenerSumaNetoCuentaPorCuenta();
    }

    // Auditoría: transacciones cuyo Grupo1/Grupo2 guardado no coincide hoy
    // con el valor autoritativo en "cuentas" (ver A11_AuditoriaClasificacionDialogo)
    public ArrayList<String[]> queryTransaccionesDesalineadas() {
        return a3_2_consultas_para_queryManager.obtenerTransaccionesDesalineadas();
    }

    // Método para sumar transacciones por cuenta específica
    public A23_QueryResult<Void> querySumTransactionsByAccount(String cuentaDeConsulta) {
        String whereClause = "c3_Cuenta = ?";
        String[] whereArgs = new String[]{cuentaDeConsulta};

        //Realizar la consulta de suma
        int suma = a3_2_consultas_para_queryManager.consultarSuma(whereClause, whereArgs);

        // Crear y retornar un objeto QueryResult con la suma
        return new A23_QueryResult<>(0,     null, "Consulta de suma exitosa", suma);
    }

    public A23_QueryResult<Void> querySumTransactionsForStringWhere(String stringWhereClause, String[] whereArgs) {
        int suma = a3_2_consultas_para_queryManager.consultarSuma(stringWhereClause, whereArgs);
        return new A23_QueryResult<>(0, null, "Consulta de suma exitosa", suma);
    }

    // Método para sumar transacciones por cuenta específica
    /*public QueryResult<Void> querySumTransactionsForStringWhere(String stringWhereClause, String StringWhere) {
        //String whereClauseRecibida = "c10_Grupo1 = ?";
        String whereClauseRecibida = stringWhereClause;
        String[] whereArgs = new String[]{StringWhere};

        // Realizar la consulta de suma
        int suma = a3_2_consultas_para_queryManager.consultarSuma(whereClauseRecibida, whereArgs);

        // Crear y retornar un objeto QueryResult con la suma
        return new QueryResult<>(0,     null, "Consulta de suma exitosa", suma);
    }*/

    // Método para sumar transacciones por cuenta específica y rango de fechas
    public A23_QueryResult<Void> querySumTransactionsByAccountAndDateRange(String cuentaDeConsulta, int fechaInicio, int fechaFin) {
        // Cláusula WHERE para filtrar por cuenta y rango de fechas
        String whereClause = "c3_Cuenta LIKE ? AND c8_FechaInicial >= ? AND c8_FechaInicial <= ?";
        String[] whereArgs = new String[]{
                "%" + cuentaDeConsulta + "%",  // Búsqueda parcial en la columna c3_Cuenta
                String.valueOf(fechaInicio),  // Rango de fecha inicial
                String.valueOf(fechaFin)      // Rango de fecha final
        };

        // Realizar la consulta de suma
        int suma = a3_2_consultas_para_queryManager.consultarSuma(whereClause, whereArgs);

        // Crear y retornar un objeto QueryResult con la suma
        return new A23_QueryResult<>(0, null, "Consulta de suma exitosa", suma);
    }

    // Método para obtener el último documento de la tabla transacciones
    public A23_QueryResult<String> queryTransactionsEndDocument() {
        //A3_2_a3_2_consultas_para_queryManager a3_2_consultas_para_queryManager = new A3_2_a3_2_consultas_para_queryManager(context);

        // Crear el QueryBuilder para obtener el último documento en orden descendente
        A21_OptimizedQuery.QueryBuilder queryBuilder = new A21_OptimizedQuery.QueryBuilder()
                .select("c1_Documento")
                .from("transacciones")
                .orderBy("CAST(c1_Documento AS INTEGER)", false)  // Orden descendente numérico
                .limit(1);  // Limitar a un solo resultado

        // Crear el ResultMapper para mapear el documento
        A21_OptimizedQuery.ResultMapper<String> mapper = cursor -> cursor.getString(0);

        // Ejecutar la consulta usando el método executeQuery
        List<String> documentos = a3_2_consultas_para_queryManager.executeQuery(queryBuilder, mapper);

        if (!documentos.isEmpty()) {
            //return new QueryResult<>(1, documentos, "Consulta exitosa");
            return new A23_QueryResult<>(1, new ArrayList<>(documentos), "Consulta exitosa");

        } else {
            return new A23_QueryResult<>(0, new ArrayList<>(), "No hay registros");

        }
    }

    public A23_QueryResult<String> queryAzConciliablesAccountsWithFilter () {

        //A3_2_a3_2_consultas_para_queryManager a3_2_consultas_para_queryManager = new A3_2_a3_2_consultas_para_queryManager(context);
        A21_OptimizedQuery.QueryBuilder queryBuilder= new A21_OptimizedQuery.QueryBuilder()
                .select("Cuenta") // Nombre de la columna que deseas seleccionar
                .from("cuentas")  // Nombre de la tabla
                .where("Grupo2 LIKE ?", "%Exigible Conciliable%") // Filtro para palabras que contienen "Conciliable"
                .orderBy("Cuenta", true);

        List<String>  cuentasConciliablesOrdenAscendete_List = a3_2_consultas_para_queryManager.executeQuery(queryBuilder, cursor -> cursor.getString(0));

        if (!cuentasConciliablesOrdenAscendete_List.isEmpty()) {
            return new A23_QueryResult<>(cuentasConciliablesOrdenAscendete_List.size(), new ArrayList<>(cuentasConciliablesOrdenAscendete_List), "Consulta exitosa");
        } else {
            return new A23_QueryResult<>(0, new ArrayList<>(), "No se encontraron cuentas");
        }

    }

    public A23_QueryResult<String> queryAzAllAccounts() {

        // Construir el QueryBuilder para seleccionar y ordenar las cuentas
        A21_OptimizedQuery.QueryBuilder queryBuilder = new A21_OptimizedQuery.QueryBuilder()
                .select("Cuenta")  // Nombre de la columna que deseas seleccionar
                .from("cuentas")   // Nombre de la tabla
                .orderBy("Cuenta COLLATE NOCASE", true);  // Orden ascendente

        // Ejecutar la consulta y mapear los resultados
        List<String> cuentas_List = a3_2_consultas_para_queryManager.executeQuery(queryBuilder, cursor -> cursor.getString(0));

        if (!cuentas_List.isEmpty()) {
            return new A23_QueryResult<>(cuentas_List.size(), new ArrayList<>(cuentas_List), "Consulta exitosa");
        } else {
            return new A23_QueryResult<>(0, new ArrayList<>(), "No se encontraron cuentas");
        }
    }

    public A23_QueryResult<String[]> queryAttributesByAccount (String nombreCuenta) {
        //A3_2_a3_2_consultas_para_queryManager a3_2_consultas_para_queryManager = new A3_2_a3_2_consultas_para_queryManager(context);

        // Crear el QueryBuilder para la consulta en la tabla "cuentas"
        A21_OptimizedQuery.QueryBuilder queryBuilder = new A21_OptimizedQuery.QueryBuilder()
                .select("Item", "Cuenta", "Grupo1", "Grupo2", "Fecha")
                .from("cuentas")
                .where("Cuenta = ?", nombreCuenta);

        // Ejecutar la consulta y mapear el resultado
        /*List<String[]> resultado = a3_2_consultas_para_queryManager.executeQuery(queryBuilder, cursor -> new String[]{
                cursor.getString(0), // Item
                cursor.getString(1), // Cuenta
                cursor.getString(2), // Grupo1
                cursor.getString(3), // Grupo2
                cursor.getString(4)  // Fecha
        });*/

        List<String[]> resultado = a3_2_consultas_para_queryManager.executeQuery(queryBuilder, cursor -> new String[]{
                cursor.getString(cursor.getColumnIndexOrThrow("Item")),
                cursor.getString(cursor.getColumnIndexOrThrow("Cuenta")),
                cursor.getString(cursor.getColumnIndexOrThrow("Grupo1")),
                cursor.getString(cursor.getColumnIndexOrThrow("Grupo2")),
                cursor.getString(cursor.getColumnIndexOrThrow("Fecha"))
        });

        // Retornar el primer resultado como un QueryResult
        if (!resultado.isEmpty()) {
            return new A23_QueryResult<>(resultado.get(0), "Consulta exitosa");
        } else {
            return new A23_QueryResult<>(new String[0], "No se encontraron atributos para la cuenta");
        }
    }

    public static Cursor queryCursorGenericTableColumnArguments(String tabla, String columna, String[] args) {
       // a1AyudanteBD = new A1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);

        SQLiteDatabase db = a1AyudanteBD.getReadableDatabase();
        String consultaSQL = "SELECT " + columna + " FROM " + tabla + " WHERE " + columna + " = ?";

        Cursor cursor = null;

        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        return db.rawQuery(consultaSQL, args);
    }

    // Método refactorizado 'queryTransacitonsAzDocuments'
    public A23_QueryResult<String> queryTransacitonsAzDocuments() {
        // Construir el QueryBuilder para seleccionar y ordenar las cuentas
        A21_OptimizedQuery.TransactionQueryBuilder transactionBuilder = (A21_OptimizedQuery.TransactionQueryBuilder)
                new A21_OptimizedQuery.TransactionQueryBuilder()
                .select("DISTINCT c1_Documento")  // Selecciona la columna de descripción
                .from("transacciones")    // Nombre de la tabla
                .orderBy("c1_Documento", true);  // Orden alfabético Az

        // Ejecutar la consulta y mapear los resultados
        List<String> descripciones = a3_2_consultas_para_queryManager.executeQuery(transactionBuilder,
                cursor -> cursor.getString(cursor.getColumnIndex("c1_Documento")));

        if (!descripciones.isEmpty()) {
            return new A23_QueryResult<>(descripciones.size(), new ArrayList<>(descripciones), "Consulta exitosa");
        } else {
            return new A23_QueryResult<>(0, new ArrayList<>(), "No se encontraron descripciones de transacciones");
        }
    }
}
