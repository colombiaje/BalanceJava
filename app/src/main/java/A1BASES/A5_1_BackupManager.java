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

                // Escribir datos
                for (A3_1_TipoCuentasGetsYSets cuenta : todasLasCuentas_List_Result) {
                    String linea = cuenta.tipoTgetCuenta_1Item() + "," +
                            cuenta.tipoTgetCuenta_2Cuenta() + "," +
                            cuenta.tipoTgetCuenta_3G1() + "," +
                            cuenta.tipoTgetCuenta_3G2() + "," +
                            cuenta.tipoTgetCuenta_5Fecha() + "\n";

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

                A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(context, balanceSqlite_String_PSF,null, version1BalanceSqlite_int_PSF);
                SQLiteDatabase sqliteDatabase_Abstracta= ayudanteBD_Class.getWritableDatabase();

                final Cursor transacciones_Cursor = sqliteDatabase_Abstracta.rawQuery
                        ("SELECT c3_Cuenta, c4_Signo,SUM(c5_Valor),c10_Grupo1,c11_Grupo2 " +
                                "as transacciones  FROM transacciones  " +
                                "where c4_Signo !='?' Group By c3_Cuenta ; ", null);

                a99_metodosVarios = new A99_MetodosVarios();
                dateCurrent_ArrayInteger= a99_metodosVarios.fechasYHoras();

                if (transacciones_Cursor != null & transacciones_Cursor.getCount() !=0) {
                    transacciones_Cursor.moveToFirst();
                    do {
                        escrituraDeArchivo_FileWriter.append("0000");escrituraDeArchivo_FileWriter.append(","); //1 documento
                        escrituraDeArchivo_FileWriter.append("00");escrituraDeArchivo_FileWriter.append(","); //2 item documento
                        escrituraDeArchivo_FileWriter.append(transacciones_Cursor.getString(0));escrituraDeArchivo_FileWriter.append(",");//3cuenta
                        escrituraDeArchivo_FileWriter.append( transacciones_Cursor.getString(1) );escrituraDeArchivo_FileWriter.append(",");// 4 mas menos
                        escrituraDeArchivo_FileWriter.append(String.valueOf(transacciones_Cursor.getInt(2)));escrituraDeArchivo_FileWriter.append(",");//5 valor
                        escrituraDeArchivo_FileWriter.append("Saldo inicial por cierre total");escrituraDeArchivo_FileWriter.append(",");// 6 descripcion
                        escrituraDeArchivo_FileWriter.append(stringFechaYHora );escrituraDeArchivo_FileWriter.append(","); // 7 fecha y hora
                        escrituraDeArchivo_FileWriter.append(""+dateCurrent_ArrayInteger[5]);escrituraDeArchivo_FileWriter.append(","); // 8 fecha inicial
                        //escrituraDeArchivo_FileWriter.append(dateCurrent_ArrayInteger[0] + "/"+dateCurrent_ArrayInteger[1] + "/"+dateCurrent_ArrayInteger[2] );escrituraDeArchivo_FileWriter.append(","); // 8 fecha inicial
                        escrituraDeArchivo_FileWriter.append("n a");escrituraDeArchivo_FileWriter.append(","); //9 fecha de modificacion
                        escrituraDeArchivo_FileWriter.append( transacciones_Cursor.getString(3) );escrituraDeArchivo_FileWriter.append(",");// 10 grupo 1
                        escrituraDeArchivo_FileWriter.append( transacciones_Cursor.getString(4) );escrituraDeArchivo_FileWriter.append(",");// 11 grupo 2
                        escrituraDeArchivo_FileWriter.append("n a");escrituraDeArchivo_FileWriter.append(","); // 12 columna disponible 1
                        escrituraDeArchivo_FileWriter.append("n a");escrituraDeArchivo_FileWriter.append("\n"); // 13 columna disponible 2

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

                A1_1_AyudanteBD ayudanteBD = new A1_1_AyudanteBD(context, balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
                SQLiteDatabase sqliteDatabase = ayudanteBD.getWritableDatabase();

                final Cursor transaccionesCursor = sqliteDatabase.rawQuery(
                        "SELECT c3_Cuenta, c4_Signo, SUM(c5_Valor), c10_Grupo1, c11_Grupo2 " +
                                "AS transacciones FROM transacciones " +
                                "WHERE c4_Signo != '?' AND (c11_Grupo2 = 'Exigible Conciliable Cerrable' OR c11_Grupo2 = 'No exigible No conciliable Cerrable') " +
                                "GROUP BY c3_Cuenta;", null);

                A99_MetodosVarios metodosVarios = new A99_MetodosVarios();
                dateCurrent_ArrayInteger = metodosVarios.fechasYHoras();

                if (transaccionesCursor != null && transaccionesCursor.getCount() != 0) {
                    transaccionesCursor.moveToFirst();
                    do {
                        escrituraDeArchivo.append("0000"); escrituraDeArchivo.append(","); // 1 documento
                        escrituraDeArchivo.append("00"); escrituraDeArchivo.append(","); // 2 item documento
                        escrituraDeArchivo.append(transaccionesCursor.getString(0)); escrituraDeArchivo.append(","); // 3 cuenta
                        escrituraDeArchivo.append(transaccionesCursor.getString(1)); escrituraDeArchivo.append(","); // 4 mas menos
                        escrituraDeArchivo.append(String.valueOf(transaccionesCursor.getInt(2))); escrituraDeArchivo.append(","); // 5 valor
                        escrituraDeArchivo.append("Saldo inicial por cierre de algunas cuentas"); escrituraDeArchivo.append(","); // 6 descripcion
                        escrituraDeArchivo.append(stringFechaYHora); escrituraDeArchivo.append(","); // 7 fecha y hora
                        escrituraDeArchivo.append("" + dateCurrent_ArrayInteger[5]); escrituraDeArchivo.append(","); // 8 fecha inicial
                        // escrituraDeArchivo.append(dateCurrent_ArrayInteger[0] + "/" + dateCurrent_ArrayInteger[1] + "/" + dateCurrent_ArrayInteger[2]); escrituraDeArchivo.append(","); // 8 fecha inicial
                        escrituraDeArchivo.append("n a"); escrituraDeArchivo.append(","); // 9 fecha de modificacion
                        escrituraDeArchivo.append(transaccionesCursor.getString(3)); escrituraDeArchivo.append(","); // 10 grupo 1
                        escrituraDeArchivo.append(transaccionesCursor.getString(4)); escrituraDeArchivo.append(","); // 11 grupo 2
                        escrituraDeArchivo.append("n a"); escrituraDeArchivo.append(","); // 12 columna disponible 1
                        escrituraDeArchivo.append("n a"); escrituraDeArchivo.append("\n"); // 13 columna disponible 2

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