package A1BASES;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
import static A1BASES.A99_MetodosVarios.stringFechaYHora;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_AFTER_RESTORING_BACKUP_INITIAL;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_BEFORE_RESTORING_BACKUP_INITIAL;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_BEFORE_STARTING_CLOSING;
import static A1BASES.A9_2_BackupFile.CSV_DOCUMENT_TRANSACTIONS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_AFTER_CLOSING_RESTORING_SHEETS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL_FROM_DRIVE;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_CLOSING_ALL_ACCOUNTS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_CLOSING_RESTORING_SHEETS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_CLOSING_SOME_ACCOUNTS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL_FROM_DRIVE;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_STARTING_CLOSURES;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;

public class A5_1_BackupManager {
    private Context context;
    A22_QueryManager a22QueryManager;

    public A5_1_BackupManager() {
        this.context = context;
        this.a22QueryManager = a22QueryManager;
    }

    static A99_MetodosVarios a99_metodosVarios;
    public static Integer [] dateCurrent_ArrayInteger;

    // Resto del código del BackupManager

    public boolean backupCuentasArchivoCSV(String nombreArchivo, A22_QueryManager a22QueryManager) {

        if (a22QueryManager == null) {
            Log.e("BackupManager", "Error: queryManager es NULL. No se puede ejecutar la consulta.");
            return false;
        }

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            try {
                String rutaDestino = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
                String rutaDestinoYNombreArchivo = rutaDestino + nombreArchivo;
                File archivo = new File(rutaDestinoYNombreArchivo);

                // Intentar eliminar el archivo anterior si existe
                if (archivo.exists() && !archivo.delete()) {
                    Log.e("BackupManager", "Error: No se pudo eliminar el archivo anterior");
                    return false;
                }

                // Crear un nuevo archivo
                if (!archivo.createNewFile()) {
                    Log.e("BackupManager", "Error: No se pudo crear el archivo nuevo");
                    return false;
                }
                archivo.setWritable(true);

                // Obtener los datos
                A23_QueryResult todasLasCuentas_result = a22QueryManager.queryAllAccounts();
                List<A3_1_TipoCuentasGetsYSets> todasLasCuentas_List_Result = todasLasCuentas_result.getDatos();

                // Verificar si hay datos
                if (todasLasCuentas_List_Result.isEmpty()) {
                    Log.e("BackupManager", "La consulta no devolvió datos. El archivo estará vacío.");
                    return false;
                } else {
                    Log.d("BackupManager", "Cantidad de cuentas a guardar: " + todasLasCuentas_List_Result.size());
                }

                OutputStreamWriter salidaArchivo = new OutputStreamWriter(new FileOutputStream(archivo));

                // ⭐ NUEVO — a pedido de Jorge (27-sep): fila de encabezado con los nombres de
                // columna, para que el CSV se entienda solo al abrirlo (revisión de diseño). Los
                // 3 lectores de este archivo (insertarCuenta(), ver F2_Cuentas) ya saben saltarla
                // — detectan la fila por su primer campo literal "Item" (nunca un dato real, que
                // siempre es numérico), así que esto no afecta ninguna cuenta real al restaurar.
                salidaArchivo.write("Item,Cuenta,Grupo1,Grupo2,Fecha,cuenta_id,codigo_cuenta,Cerrable,tipo_cuenta_id,cuenta_seguimiento,conciliable\n");

                // Escribir datos
                // ⭐ CAMBIO — Fase 4 (parte C): se agregan cuenta_id, codigo_cuenta y Cerrable al
                // final de la línea (columnas 6, 7 y 8) — las 5 columnas de siempre quedan en el
                // mismo orden, así que un restaurador viejo que solo lea las primeras 5 sigue
                // funcionando igual. Ver F2_Cuentas.insertarCuenta(), que ya sabe leer estas 3
                // columnas nuevas si están presentes.
                // ⭐ NUEVO — v11 (prep): se agregan tipo_cuenta_id y cuenta_seguimiento al final
                // (columnas 9 y 10), para que las 3 restauraciones por CSV (Sheets, backup local,
                // Drive) puedan traer la clasificación directamente en vez de reconstruirla
                // adivinando por Grupo1/Grupo2 después de reinsertar. Mismo criterio: puramente
                // aditivo al final, así que un backup viejo de 8 columnas se sigue leyendo igual
                // (F2_Cuentas.insertarCuenta() detecta cuántas columnas trae la línea).
                // ⭐ NUEVO — v11 tanda 3 (parte A): se agrega conciliable al final (columna 11),
                // mismo criterio aditivo — un backup viejo de hasta 10 columnas se sigue leyendo
                // igual.
                for (A3_1_TipoCuentasGetsYSets cuenta : todasLasCuentas_List_Result) {
                    String linea = cuenta.tipoTgetCuenta_1Item() + "," +
                            cuenta.tipoTgetCuenta_2Cuenta() + "," +
                            cuenta.tipoTgetCuenta_3G1() + "," +
                            cuenta.tipoTgetCuenta_3G2() + "," +
                            cuenta.tipoTgetCuenta_5Fecha() + "," +
                            (cuenta.tipoTgetCuenta_6CuentaId() == null ? "" : cuenta.tipoTgetCuenta_6CuentaId()) + "," +
                            (cuenta.tipoTgetCuenta_7CodigoCuenta() == null ? "" : cuenta.tipoTgetCuenta_7CodigoCuenta()) + "," +
                            (cuenta.tipoTgetCuenta_8Cerrable() == null ? "" : cuenta.tipoTgetCuenta_8Cerrable()) + "," +
                            (cuenta.tipoTgetCuenta_9TipoCuentaId() == null ? "" : cuenta.tipoTgetCuenta_9TipoCuentaId()) + "," +
                            (cuenta.tipoTgetCuenta_10CuentaSeguimiento() == null ? "" : (cuenta.tipoTgetCuenta_10CuentaSeguimiento() ? "1" : "0")) + "," +
                            (cuenta.tipoTgetCuenta_11Conciliable() == null ? "" : cuenta.tipoTgetCuenta_11Conciliable()) + "\n";

                    salidaArchivo.write(linea);
                    Log.d("BackupManager", "Escribiendo línea: " + linea.trim());
                }

                // Asegurar que los datos se escriban en el archivo antes de cerrarlo
                salidaArchivo.flush();
                salidaArchivo.close();

                Log.d("BackupManager", "Backup guardado correctamente.");
                return true;
            } catch (Exception ex) {
                Log.e("BackupManager", "Error al guardar el archivo: " + ex.getMessage());
            }
        } else {
            Log.e("BackupManager", "No se encuentra la tarjeta SD");
        }
        return false;
    }


    private static final String[] archivoTransaccionesADrive = {

                //CSV_CREATE_HEADER Se ejecuta en otro tipo de metodo ??? unificar
                //CSV_CREATE_RECORDS Se ejecuta en otro tipo de metodo ??? unificar
                //CSV_TEMPLATE_HEADER Se ejecuta en otro tipo de metodo ??? unificar
                //CSV_TEMPLATE_RECORDS Se ejecuta en otro tipo de metodo ??? unificar
                //CSV_UPDATE_HEADER Se ejecuta en otro tipo de metodo ??? unificar
                //CSV_UPDATE_RECORDS Se ejecuta en otro tipo de metodo ??? unificar
                CSV_DOCUMENT_TRANSACTIONS.getFileName(),
                CSV_ACCOUNTS_BEFORE_STARTING_CLOSING.getFileName(),
                CSV_ACCOUNTS_BEFORE_RESTORING_BACKUP_INITIAL.getFileName(),
                CSV_ACCOUNTS_AFTER_RESTORING_BACKUP_INITIAL.getFileName(),
                //CUENTAS_SHEETS_BALANCE.getFileName() //Se ejecuta en otro proceso
                CSV_TRANSACTIONS_BEFORE_STARTING_CLOSURES.getFileName(),
                CSV_TRANSACTIONS_BEFORE_CLOSING_ALL_ACCOUNTS.getFileName(),
                CSV_TRANSACTIONS_BEFORE_CLOSING_SOME_ACCOUNTS.getFileName(),
                //CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED.getFileName(),//No debe generarse aqui se descarga desde drive-sheets
                CSV_TRANSACTIONS_BEFORE_CLOSING_RESTORING_SHEETS.getFileName(),
                CSV_TRANSACTIONS_AFTER_CLOSING_RESTORING_SHEETS.getFileName(),
                CSV_TRANSACTIONS_BEFORE_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL.getFileName(),
                CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL.getFileName(),
                CSV_TRANSACTIONS_BEFORE_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL_FROM_DRIVE.getFileName(),
                CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL_FROM_DRIVE.getFileName()

        };

        public static void guardarTodasLasTransancionsAUnArchivoCSV(Context context, String nombreArchivoTransaccionesADrive) {
            String nombreArchivoBackupTransacciones_String = null;

            for (String archivo : archivoTransaccionesADrive) {
                if (nombreArchivoTransaccionesADrive.equals(archivo)) {
                    nombreArchivoBackupTransacciones_String = archivo;
                    Log.d("CSV", "Nombre de archivo encontrado: " + nombreArchivoBackupTransacciones_String);
                    break;
                }
            }

            /*if (nombreArchivoBackupTransacciones_String == null) {
                Toast.makeText(context, "Nombre de archivo no válido", Toast.LENGTH_SHORT).show();
                return;
            }*/

            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                try {
                    String rutaDestino_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
                    String rutaDestinoYNombreArchivo_String = rutaDestino_String + nombreArchivoBackupTransacciones_String;
                    File archivo_File = new File(rutaDestinoYNombreArchivo_String);

                    if (archivo_File.exists()) {
                        archivo_File.delete();
                    }

                    //A2ConsultasAnterior consultasClass = new A2ConsultasAnterior(context);
                    //consultasClass.consultarTodasLasTransacciones();

                    A22_QueryManager a22QueryManager = new A22_QueryManager(context);

                    A23_QueryResult todasLAsTransacciones_Result =  a22QueryManager.queryAllTransactions();
                    ArrayList<A3_2_TipoTransaccionesGetsYSets> todasLasTransacciones_Result_ArrayLTT = todasLAsTransacciones_Result.getDatos();

                    OutputStreamWriter salidaArchivo_OutputStreamWriter = new OutputStreamWriter(new FileOutputStream(archivo_File));

                    // ⭐ CAMBIO — Fase 4 (parte C): se agrega cuenta_id como 14ta columna al
                    // final — las 13 de siempre quedan en el mismo orden y posición. Ver
                    // F4_Cierres.insertarTransaccion(), que ya sabe leer esta columna extra si
                    // está presente.
                    // ⭐ NUEVO — a pedido de Jorge (27-sep): fila de encabezado. insertarTransaccion()
                    // ya sabe saltarla (primer campo literal "c1_Documento", nunca un dato real).
                    // ⭐ NUEVO v11 — a pedido de Jorge (28-sep): "la idea de los CSV y sus columnas
                    // es que reflejen los campos completos como los de sus tablas" — se agrega
                    // tipo_cuenta_id como 15ta columna (cuenta_id ya era la 14ta). Puramente
                    // aditivo al final, igual criterio que cuenta_id: F4_Cierres.insertarTransaccion()
                    // ya sabe leerla si está presente y no rompe CSVs viejos de 13/14 columnas.
                    // ⭐ NUEVO v11 (28-sep, segunda ronda): transaccion_id como 16ta columna — solo
                    // en ESTE backup completo (no en los resúmenes de cierre _2/_3: esos generan
                    // filas NUEVAS de saldo inicial vía SUM(), sin transaccion_id original que
                    // preservar). Objetivo: hoy, restaurar un backup completo vacía la tabla y
                    // reinserta todo con ids NUEVOS (AUTOINCREMENT nunca reutiliza los viejos), así
                    // que transaccion_id no era estable entre backup y restauración. Guardando el id
                    // original aquí y reusándolo al reinsertar (ver insertarTransaccion), queda
                    // estable — necesario si más adelante "transacciones_inventario" lo referencia
                    // como FK. Seguro: los 6 flujos de restauración que leen este backup completo
                    // siempre vacían la tabla antes de reinsertar, así que no hay riesgo de choque
                    // de PK al reusar el id original.
                    salidaArchivo_OutputStreamWriter.write(
                            "c1_Documento,c2_ItemDoc,c3_Cuenta,c4_Signo,c5_Valor,c6_Descripcion,c7_FechaYHora," +
                                    "c8_FechaInicial,c9_FechaModificacion,c10_Grupo1,c11_Grupo2," +
                                    "c12_ColumnaDisponible,c13_ColumnaDisponible,cuenta_id,tipo_cuenta_id,transaccion_id\n");
                    for (int i = 0; i < todasLasTransacciones_Result_ArrayLTT.size(); i++) {
                        A3_2_TipoTransaccionesGetsYSets TransaccionX = todasLasTransacciones_Result_ArrayLTT.get(i);
                        salidaArchivo_OutputStreamWriter.write(
                                TransaccionX.tipoTget_1DocumentoMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_2ItemDocMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_3CuentaMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_4MasMenosMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_5ValorMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_6DescripcionMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_7FechaYHoraMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_8FechaInicialMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_9FechaModificacionMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_10Grupo1MetodoEnA5() + "," +
                                        TransaccionX.tipoTget_11Grupo2MetodoEnA5() + "," +
                                        TransaccionX.tipoTget_12ColumnaDisponibleMetodoEnA5() + "," +
                                        TransaccionX.tipoTget_13ColumnaDisponibleMetodoEnA5() + "," +
                                        (TransaccionX.tipoTget_14CuentaIdMetodoEnA5() == null ? "" : TransaccionX.tipoTget_14CuentaIdMetodoEnA5()) + "," +
                                        (TransaccionX.tipoTget_16TipoCuentaIdMetodoEnA5() == null ? "" : TransaccionX.tipoTget_16TipoCuentaIdMetodoEnA5()) + "," +
                                        (TransaccionX.tipoTget_15TransaccionIdMetodoEnA5() == null ? "" : TransaccionX.tipoTget_15TransaccionIdMetodoEnA5()) +
                                        "\n");
                    }

                    salidaArchivo_OutputStreamWriter.close();
                    //Toast.makeText(context, "Archivo 1 guardado localmente", Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    Toast.makeText(context, "Error al guardar el archivo: " + ex.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(context, "No se encuentra la memoria externa", Toast.LENGTH_SHORT).show();
            }
        }

    //El contenido del archivo csv por ser de cierre varia en el contenido de las transacciones en algunos de sus campos o columnas,
    //con respecto al de antes de cierre 7exportarBackupTodasLasTransaccionesAUnArchivoCSV (String nombreArchivo_String).

    public static void _2csvConsultaResumenTodasLasCuentasAntesDeCerrarParaTablaTransaccionesDespuesDeCerrar (Context context, String nombreArchivoResumenPorCuenta) {

        //Se exportara a la memoria interna del dispositivo en una carpeta_File especifica fuera de la carpeta_File de la aplicacion

        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))   {

            //si esta disponible y tiene acceso a escritura*/

            try {

                //Ruta y archivo_File
                String rutaDestino_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
                String rutaDestinoYNombreArchivo_String = rutaDestino_String+nombreArchivoResumenPorCuenta;
                File archivo_File = new File(rutaDestinoYNombreArchivo_String);

                if (archivo_File.exists()) {

                    archivo_File.delete();
                    archivo_File = new File(rutaDestinoYNombreArchivo_String);
                }

                //Con la clase OutputStreamWriter se logra el mismo resultado que con la clase FileWriter
                //escriba en el archivo_File
                FileWriter escrituraDeArchivo_FileWriter = new FileWriter(archivo_File);

                // ⭐ NUEVO — a pedido de Jorge (27-sep): fila de encabezado. F4_Cierres.insertarTransaccion()
                // ya sabe saltarla (primer campo literal "c1_Documento", nunca un dato real).
                // ⭐ NUEVO v11 — a pedido de Jorge (28-sep): "la idea de los CSV y sus columnas es
                // que reflejen los campos completos como los de sus tablas" — hasta ahora este
                // resumen no traía cuenta_id NI tipo_cuenta_id (a diferencia del backup completo
                // de guardarTodasLasTransancionsAUnArchivoCSV, que ya traía cuenta_id desde la
                // Fase 4 parte C). Se agregan ambas al final, en el mismo orden que allá.
                // ⭐ NUEVO v11 (28-sep, tercera ronda) — a pedido de Jorge: transaccion_id como
                // 16ta columna, para que los 3 CSV de transacciones queden con el mismo número de
                // columnas (facilita comparar/homologar entre opciones del menú). Siempre vacía en
                // este resumen (ver comentario junto a la escritura de la fila, más abajo).
                escrituraDeArchivo_FileWriter.append(
                        "c1_Documento,c2_ItemDoc,c3_Cuenta,c4_Signo,c5_Valor,c6_Descripcion,c7_FechaYHora," +
                                "c8_FechaInicial,c9_FechaModificacion,c10_Grupo1,c11_Grupo2," +
                                "c12_ColumnaDisponible,c13_ColumnaDisponible,cuenta_id,tipo_cuenta_id,transaccion_id\n");

                A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(context, balanceSqlite_String_PSF,null, version1BalanceSqlite_int_PSF);
                SQLiteDatabase sqliteDatabase_Abstracta= ayudanteBD_Class.getWritableDatabase();

                // ⭐ CAMBIO — Fase 4 (parte C): se agrega un LEFT JOIN a "cuentas" para poder
                // escribir el Cerrable real de cada cuenta (columna 12 de abajo) en vez del texto
                // fijo "n a" que tenía siempre — este resumen genera transacciones NUEVAS de
                // saldo inicial, así que debe reflejar el estado ACTUAL de la cuenta en "cuentas"
                // (la fuente autoritativa), igual que ya hace B11_DocumentCalculator para
                // cualquier transacción nueva. LEFT JOIN (no INNER) para que una cuenta sin match
                // exacto por nombre siga apareciendo en el resumen, igual que antes.
                // ⭐ CAMBIO — tanda 3 v10: el JOIN por nombre (c.Cuenta = t.c3_Cuenta) fallaba en
                // silencio siempre que el nombre guardado en la transacción no calzara EXACTO con
                // el nombre actual de la cuenta — por una cuenta renombrada después, o por una
                // simple diferencia de codificación (confirmado que existen nombres duplicados así
                // en datos reales, p.ej. "García" vs "GarcÃ­a"). Se cambia a cuenta_id, el mismo
                // identificador técnico estable que ya usa el resto de la v10, y que cada
                // transacción nueva recibe desde que se guarda (ver B12_DocumentPersistence). Se
                // conserva el match por nombre SOLO como respaldo para filas viejas que todavía
                // tengan cuenta_id NULL, para no perder ninguna fila que antes sí aparecía.
                // ⭐ NUEVO v11 (28-sep): se agregan c.cuenta_id y c.tipo_cuenta_id al SELECT
                // (columnas 4 y 5 del cursor) — mismo criterio que Cerrable arriba: constantes
                // por cuenta, así que agrupar por t.c3_Cuenta no las hace ambiguas.
                // ⭐ CAMBIO — v11 tanda 3 (parte D): t.c10_Grupo1/t.c11_Grupo2 se retiran del
                // SELECT — la columna ya no existe en "transacciones" (ver A1_1_AyudanteBD,
                // migración v13). El resto de columnas del cursor se recorren 2 posiciones a la
                // izquierda (Cerrable: 5→3, cuenta_id: 6→4, tipo_cuenta_id: 7→5 — ver más abajo,
                // donde se escriben "" literales para las columnas 10/11 del CSV en su lugar).
                final Cursor transacciones_Cursor = sqliteDatabase_Abstracta.rawQuery
                        ("SELECT t.c3_Cuenta, t.c4_Signo, SUM(t.c5_Valor), c.Cerrable, " +
                                "c.cuenta_id, c.tipo_cuenta_id " +
                                "FROM transacciones t LEFT JOIN cuentas c " +
                                "ON (c.cuenta_id = t.cuenta_id) OR (t.cuenta_id IS NULL AND c.Cuenta = t.c3_Cuenta) " +
                                "WHERE t.c4_Signo != '?' GROUP BY t.c3_Cuenta;", null);

                a99_metodosVarios = new A99_MetodosVarios();
                dateCurrent_ArrayInteger= a99_metodosVarios.fechasYHoras();

                // ⭐ CAMBIO — tanda 3 v10: antes esta función escribía Item="00" para TODAS las
                // cuentas del resumen — como Documento se queda fijo en "0" (convención ya
                // existente para saldo inicial), todas esas filas terminaban con la misma llave
                // (Documento,Item), chocando entre sí y con las de cualquier cierre anterior (esto
                // es justo lo que detectamos en el cruce de CSVs: 13 filas con Documento=0,Item=0
                // en el teléfono). Documento sigue en "0" (no se toca esa convención); Item ahora
                // continúa desde el máximo ItemDoc que ya exista bajo Documento=0, en vez de
                // reiniciar en 0 cada vez.
                int siguienteItemDocCero = 1;
                Cursor maxItemDocCero_Cursor = sqliteDatabase_Abstracta.rawQuery(
                        "SELECT MAX(CAST(c2_ItemDoc AS INTEGER)) FROM transacciones " +
                                "WHERE CAST(c1_Documento AS INTEGER) = 0", null);
                if (maxItemDocCero_Cursor != null && maxItemDocCero_Cursor.moveToFirst()
                        && !maxItemDocCero_Cursor.isNull(0)) {
                    siguienteItemDocCero = maxItemDocCero_Cursor.getInt(0) + 1;
                }
                if (maxItemDocCero_Cursor != null) {
                    maxItemDocCero_Cursor.close();
                }

                if (transacciones_Cursor != null & transacciones_Cursor.getCount() !=0) {
                    transacciones_Cursor.moveToFirst();
                    do {
                        escrituraDeArchivo_FileWriter.append("0000");escrituraDeArchivo_FileWriter.append(","); //1 documento
                        escrituraDeArchivo_FileWriter.append(String.valueOf(siguienteItemDocCero));escrituraDeArchivo_FileWriter.append(","); //2 item documento
                        siguienteItemDocCero++;
                        escrituraDeArchivo_FileWriter.append(transacciones_Cursor.getString(0));escrituraDeArchivo_FileWriter.append(",");//3cuenta
                        escrituraDeArchivo_FileWriter.append( transacciones_Cursor.getString(1) );escrituraDeArchivo_FileWriter.append(",");// 4 mas menos
                        escrituraDeArchivo_FileWriter.append(String.valueOf(transacciones_Cursor.getInt(2)));escrituraDeArchivo_FileWriter.append(",");//5 valor
                        escrituraDeArchivo_FileWriter.append("Saldo inicial por cierre total");escrituraDeArchivo_FileWriter.append(",");// 6 descripcion
                        escrituraDeArchivo_FileWriter.append(stringFechaYHora );escrituraDeArchivo_FileWriter.append(","); // 7 fecha y hora
                        escrituraDeArchivo_FileWriter.append(""+dateCurrent_ArrayInteger[5]);escrituraDeArchivo_FileWriter.append(","); // 8 fecha inicial
                        //escrituraDeArchivo_FileWriter.append(dateCurrent_ArrayInteger[0] + "/"+dateCurrent_ArrayInteger[1] + "/"+dateCurrent_ArrayInteger[2] );escrituraDeArchivo_FileWriter.append(","); // 8 fecha inicial
                        escrituraDeArchivo_FileWriter.append("n a");escrituraDeArchivo_FileWriter.append(","); //9 fecha de modificacion
                        // ⭐ CAMBIO — v11 tanda 3 (parte D): Grupo1/Grupo2 ya no se leen del cursor
                        // (ver el SELECT más arriba) — el CSV conserva estas 2 columnas en blanco
                        // para siempre (formato congelado, ver A5_1_BackupManager arriba).
                        escrituraDeArchivo_FileWriter.append("");escrituraDeArchivo_FileWriter.append(",");// 10 grupo 1
                        escrituraDeArchivo_FileWriter.append("");escrituraDeArchivo_FileWriter.append(",");// 11 grupo 2
                        // ⭐ CAMBIO — Fase 4 (parte C): antes "n a" fijo; ahora el Cerrable real de "cuentas".
                        escrituraDeArchivo_FileWriter.append("Cerrable".equals(transacciones_Cursor.getString(3)) ? "Cerrable" : "No Aplica");escrituraDeArchivo_FileWriter.append(","); // 12 columna disponible 1
                        escrituraDeArchivo_FileWriter.append("n a");escrituraDeArchivo_FileWriter.append(","); // 13 columna disponible 2
                        // ⭐ NUEVO v11 (28-sep): cuenta_id y tipo_cuenta_id, leídos del JOIN (columnas
                        // 4 y 5 del cursor, tras retirar Grupo1/Grupo2 del SELECT — parte D) — mismo
                        // criterio nullable-safe que en el backup completo.
                        escrituraDeArchivo_FileWriter.append(transacciones_Cursor.isNull(4) ? "" : transacciones_Cursor.getString(4));escrituraDeArchivo_FileWriter.append(","); // 14 cuenta_id
                        escrituraDeArchivo_FileWriter.append(transacciones_Cursor.isNull(5) ? "" : transacciones_Cursor.getString(5));escrituraDeArchivo_FileWriter.append(","); // 15 tipo_cuenta_id
                        // ⭐ NUEVO v11 (28-sep, tercera ronda) — a pedido de Jorge: transaccion_id
                        // como 16ta columna, por consistencia de conteo de columnas entre los 3
                        // CSV de transacciones (facilita comparar/homologar CSVs de distintas
                        // opciones del menú). Siempre vacía aquí a propósito: esta fila es NUEVA
                        // (saldo inicial agregado por SUM(), no corresponde a ninguna transacción
                        // real existente), así que no hay un transaccion_id original que escribir
                        // — igual de vacía que cuenta_id/tipo_cuenta_id lo estarían para una cuenta
                        // sin match. insertarTransaccion() ya trata una columna 16 vacía igual que
                        // ausente: autogenera un id nuevo, que es lo correcto para una fila nueva.
                        escrituraDeArchivo_FileWriter.append("\n"); // 16 transaccion_id (vacía)

                    } while (transacciones_Cursor.moveToNext());

                }else {
                }
                sqliteDatabase_Abstracta.close();
                escrituraDeArchivo_FileWriter.close();

            } catch (Exception ex) {     }
        }
    }

    //..........

    public static void _3csvConsultaResumenAlgunasCuentasAntesDeCerrarParaTablaTransaccionesDespuesDeCerrar(Context context, String nombreArchivoResumenPorCuenta) {

        // Se exportará a la memoria interna del dispositivo en una carpeta específica fuera de la carpeta de la aplicación

        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {

            // Si está disponible y tiene acceso a escritura

            try {

                // Ruta y archivo
                String rutaDestino = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
                String rutaDestinoYNombreArchivo = rutaDestino + nombreArchivoResumenPorCuenta;
                File archivo = new File(rutaDestinoYNombreArchivo);

                if (archivo.exists()) {
                    archivo.delete();
                    archivo = new File(rutaDestinoYNombreArchivo);
                }

                // Escribir en el archivo
                FileWriter escrituraDeArchivo = new FileWriter(archivo);

                // ⭐ NUEVO — a pedido de Jorge (27-sep): fila de encabezado. F4_Cierres.insertarTransaccion()
                // ya sabe saltarla (primer campo literal "c1_Documento", nunca un dato real).
                // ⭐ NUEVO v11 — a pedido de Jorge (28-sep): mismo criterio que en
                // _2csvConsultaResumenTodasLasCuentasAntesDeCerrar... — se agregan cuenta_id y
                // tipo_cuenta_id al final, que este resumen tampoco traía todavía.
                // ⭐ NUEVO v11 (28-sep, tercera ronda) — a pedido de Jorge: transaccion_id como
                // 16ta columna, mismo criterio que en _2csvConsultaResumenTodasLasCuentasAntesDeCerrar...
                // (siempre vacía aquí — ver comentario junto a la escritura de la fila).
                escrituraDeArchivo.append(
                        "c1_Documento,c2_ItemDoc,c3_Cuenta,c4_Signo,c5_Valor,c6_Descripcion,c7_FechaYHora," +
                                "c8_FechaInicial,c9_FechaModificacion,c10_Grupo1,c11_Grupo2," +
                                "c12_ColumnaDisponible,c13_ColumnaDisponible,cuenta_id,tipo_cuenta_id,transaccion_id\n");

                A1_1_AyudanteBD ayudanteBD = new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
                SQLiteDatabase sqliteDatabase = ayudanteBD.getWritableDatabase();

                // ⭐ CAMBIO — Fase 4 (parte A): misma migración que en
                // A1_2_OperacionesBD.eliminarTransaccionesAlgunasCuentas() — antes identificaba
                // las cuentas "Cerrable" comparando el texto exacto de Grupo2; ahora lee el
                // snapshot en c12_ColumnaDisponible (ver A1_1_AyudanteBD, migración v6). Debe
                // seguir resumiendo exactamente las mismas cuentas que antes del cierre parcial.
                // ⭐ CAMBIO — Fase 4 (parte C): se agrega el LEFT JOIN a "cuentas" (mismo criterio
                // que en _2csvConsultaResumenTodasLasCuentasAntesDeCerrar...) para escribir el
                // Cerrable real en vez de "n a" fijo. Aquí, por el WHERE, en la práctica todas
                // las filas ya deberían ser Cerrable — se lee igual del JOIN, no del filtro, para
                // que quede consistente si alguna cuenta cambiara de estado justo antes de cerrar.
                // ⭐ CAMBIO — tanda 3 v10: mismo cambio que en
                // _2csvConsultaResumenTodasLasCuentasAntesDeCerrar... — JOIN por cuenta_id en vez
                // de por nombre, con el nombre como respaldo solo si cuenta_id viene NULL.
                // ⭐ NUEVO v11 (28-sep): c.cuenta_id y c.tipo_cuenta_id al SELECT (columnas 4 y 5,
                // tras retirar Grupo1/Grupo2 en la parte D — ver abajo), mismo criterio que en el
                // otro resumen.
                // ⭐ CAMBIO — v11 tanda 3 (parte D): t.c10_Grupo1/t.c11_Grupo2 se retiran del
                // SELECT — la columna ya no existe en "transacciones" (ver A1_1_AyudanteBD,
                // migración v13). El resto de columnas del cursor se recorren 2 posiciones a la
                // izquierda (Cerrable: 5→3, cuenta_id: 6→4, tipo_cuenta_id: 7→5).
                final Cursor transaccionesCursor = sqliteDatabase.rawQuery(
                        "SELECT t.c3_Cuenta, t.c4_Signo, SUM(t.c5_Valor), c.Cerrable, " +
                                "c.cuenta_id, c.tipo_cuenta_id " +
                                "FROM transacciones t LEFT JOIN cuentas c " +
                                "ON (c.cuenta_id = t.cuenta_id) OR (t.cuenta_id IS NULL AND c.Cuenta = t.c3_Cuenta) " +
                                "WHERE t.c4_Signo != '?' AND t.c12_ColumnaDisponible = 'Cerrable' " +
                                "GROUP BY t.c3_Cuenta;", null);

                A99_MetodosVarios metodosVarios = new A99_MetodosVarios();
                dateCurrent_ArrayInteger = metodosVarios.fechasYHoras();

                // ⭐ CAMBIO — tanda 3 v10: mismo fix que en
                // _2csvConsultaResumenTodasLasCuentasAntesDeCerrar... — Item ya no se repite fijo
                // en "00" para cada cuenta reseteada, sino que continúa desde el máximo ItemDoc
                // que ya exista bajo Documento=0 (evita el choque de (Documento,Item) que causaba
                // el bug de cuentas duplicadas al hacer "cierre parcial de algunas cuentas").
                int siguienteItemDocCero = 1;
                Cursor maxItemDocCero_Cursor2 = sqliteDatabase.rawQuery(
                        "SELECT MAX(CAST(c2_ItemDoc AS INTEGER)) FROM transacciones " +
                                "WHERE CAST(c1_Documento AS INTEGER) = 0", null);
                if (maxItemDocCero_Cursor2 != null && maxItemDocCero_Cursor2.moveToFirst()
                        && !maxItemDocCero_Cursor2.isNull(0)) {
                    siguienteItemDocCero = maxItemDocCero_Cursor2.getInt(0) + 1;
                }
                if (maxItemDocCero_Cursor2 != null) {
                    maxItemDocCero_Cursor2.close();
                }

                if (transaccionesCursor != null && transaccionesCursor.getCount() != 0) {
                    transaccionesCursor.moveToFirst();
                    do {
                        escrituraDeArchivo.append("0000"); escrituraDeArchivo.append(","); // 1 documento
                        escrituraDeArchivo.append(String.valueOf(siguienteItemDocCero)); escrituraDeArchivo.append(","); // 2 item documento
                        siguienteItemDocCero++;
                        escrituraDeArchivo.append(transaccionesCursor.getString(0)); escrituraDeArchivo.append(","); // 3 cuenta
                        escrituraDeArchivo.append(transaccionesCursor.getString(1)); escrituraDeArchivo.append(","); // 4 mas menos
                        escrituraDeArchivo.append(String.valueOf(transaccionesCursor.getInt(2))); escrituraDeArchivo.append(","); // 5 valor
                        escrituraDeArchivo.append("Saldo inicial por cierre de algunas cuentas"); escrituraDeArchivo.append(","); // 6 descripcion
                        escrituraDeArchivo.append(stringFechaYHora); escrituraDeArchivo.append(","); // 7 fecha y hora
                        escrituraDeArchivo.append("" + dateCurrent_ArrayInteger[5]); escrituraDeArchivo.append(","); // 8 fecha inicial
                        // escrituraDeArchivo.append(dateCurrent_ArrayInteger[0] + "/" + dateCurrent_ArrayInteger[1] + "/" + dateCurrent_ArrayInteger[2]); escrituraDeArchivo.append(","); // 8 fecha inicial
                        escrituraDeArchivo.append("n a"); escrituraDeArchivo.append(","); // 9 fecha de modificacion
                        // ⭐ CAMBIO — v11 tanda 3 (parte D): Grupo1/Grupo2 ya no se leen del cursor
                        // (ver el SELECT más arriba) — el CSV conserva estas 2 columnas en blanco
                        // para siempre (formato congelado).
                        escrituraDeArchivo.append(""); escrituraDeArchivo.append(","); // 10 grupo 1
                        escrituraDeArchivo.append(""); escrituraDeArchivo.append(","); // 11 grupo 2
                        // ⭐ CAMBIO — Fase 4 (parte C): antes escribía "n a" fijo; ahora usa el
                        // Cerrable real leído del JOIN a "cuentas" (columna 3 del cursor, tras
                        // retirar Grupo1/Grupo2 del SELECT — parte D).
                        escrituraDeArchivo.append("Cerrable".equals(transaccionesCursor.getString(3)) ? "Cerrable" : "No Aplica"); escrituraDeArchivo.append(","); // 12 columna disponible 1
                        escrituraDeArchivo.append("n a"); escrituraDeArchivo.append(","); // 13 columna disponible 2 (sin cambios, espacio libre genuino)
                        // ⭐ NUEVO v11 (28-sep): cuenta_id y tipo_cuenta_id, leídos del JOIN
                        // (columnas 4 y 5 del cursor, tras retirar Grupo1/Grupo2 — parte D).
                        escrituraDeArchivo.append(transaccionesCursor.isNull(4) ? "" : transaccionesCursor.getString(4)); escrituraDeArchivo.append(","); // 14 cuenta_id
                        escrituraDeArchivo.append(transaccionesCursor.isNull(5) ? "" : transaccionesCursor.getString(5)); escrituraDeArchivo.append(","); // 15 tipo_cuenta_id
                        // ⭐ NUEVO v11 (28-sep, tercera ronda) — a pedido de Jorge: transaccion_id
                        // como 16ta columna, siempre vacía (mismo criterio que en
                        // _2csvConsultaResumenTodasLasCuentasAntesDeCerrar... — fila NUEVA de saldo
                        // inicial, sin transaccion_id original que escribir).
                        escrituraDeArchivo.append("\n"); // 16 transaccion_id (vacía)

                    } while (transaccionesCursor.moveToNext());

                } else {
                    // Toast.makeText(context, "No hay transacciones", Toast.LENGTH_LONG).show();
                }

                sqliteDatabase.close();
                escrituraDeArchivo.close();

            } catch (Exception ex) {
                // Manejo de excepciones
            }
        }
    }

    //......................

        public static void origenBackupCsvCRUDListaDocumento(String nombreArchivo, ArrayList<A3_2_TipoTransaccionesGetsYSets> dataList) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                try {
                    String rutaDestino = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
                    String rutaDestinoYNombreArchivo = rutaDestino + nombreArchivo;
                    File archivo = new File(rutaDestinoYNombreArchivo);

                    if (dataList.size() == 0) {
                        if (archivo.exists()) {
                            archivo.delete();
                        }
                        return;
                    } else {

                        if (archivo.exists()) {
                            archivo.delete();
                        }

                        OutputStreamWriter salidaArchivo = new OutputStreamWriter(new FileOutputStream(archivo));

                        for (int i = 0; i < dataList.size(); i++) {
                            A3_2_TipoTransaccionesGetsYSets TransaccionX = dataList.get(i);
                            salidaArchivo.write(
                                    TransaccionX.tipoTget_1DocumentoMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_2ItemDocMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_3CuentaMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_4MasMenosMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_5ValorMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_6DescripcionMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_7FechaYHoraMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_8FechaInicialMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_9FechaModificacionMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_10Grupo1MetodoEnA5() + "," +
                                            TransaccionX.tipoTget_11Grupo2MetodoEnA5() + "," +
                                            TransaccionX.tipoTget_12ColumnaDisponibleMetodoEnA5() + "," +
                                            TransaccionX.tipoTget_13ColumnaDisponibleMetodoEnA5() + "," +
                                            "\n");
                        }
                        salidaArchivo.close();
                        // Puedes mostrar un mensaje de éxito aquí si deseas.
                    }
                } catch (Exception ex) {
                    // Puedes mostrar un mensaje de error aquí si deseas.
                }
            } else {
                // Puedes mostrar un mensaje de que no se encuentra la micro SD aquí si deseas.
            }
        }
}