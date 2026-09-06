package B_FRAGMENTS;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_AFTER_CLOSING_RESTORING_SHEETS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL_FROM_DRIVE;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_ALL_ACCOUNTS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_SOME_ACCOUNTS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_CLOSING_ALL_ACCOUNTS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_CLOSING_RESTORING_SHEETS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_CLOSING_SOME_ACCOUNTS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BEFORE_STARTING_CLOSURES;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED;
import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.jj.appbalancev31.R;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import A1BASES.A6_3_CSVDriveUploader;
import A1BASES.A1_1_AyudanteBD;
import A1BASES.A1_2_OperacionesBD;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A5_1_BackupManager;
import A1BASES.A6_2_GoogleDriveDownloader;
import A1BASES.A99_MetodosVarios;
import A1BASES.A6_5_SheetsDownloader;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;
import D_ADAPTERS.D_F4_AdaptadorTransaccionesCierre;

/**
 * Fragment mejorado para gestión de cierres contables con sistema de backups rotativos
 * Mantiene automáticamente los últimos 5 backups de cada tipo en Google Drive
 */
public class F4_Cierres extends Fragment {

    private RadioButton _4_1_opcionSincronizarBalance_desde_Sheets_XRb;
    private A6_5_SheetsDownloader sheetsDownloader;
    private static final String TAG = "F51_CierresPrincipal";
    private static final String FOLDER_ID_DRIVE = "1_45-JDssinDjj7NOykxAh2xHzRNhuwEJ";
    private static final int DELAY_TOAST_CORTO = 4000;
    private static final int DELAY_TOAST_LARGO = 8000;
    private static final int DELAY_SUBIDA_DRIVE = 2500;

    // Views
    private RadioButton _1_opcionBackupDeReservaTransacciones_XRb;
    private RadioButton _2_opcionAutonomoTransacciones_XRb;
    private RadioButton _4_2_opcion_Importar_Balance_desde_Sheets_XRb;
    private RadioButton _5_opcionRecuperarBackupTransacciones_XRb;
    private RadioButton _3_opcionTransaccionesAlgunasCuentas_XRb;
    private RadioButton opcionSaleDeLaApp_XRb;
    private ListView todasLasTransacciones_XLv;
    private TextView mensajeInformativo_XTv;
    private LinearLayout envolventeEncabezado_XLl;
    private TextView opcionActualCierre_XTv;
    private Spinner consultaPorCuentaYFechaEnOtroFragment_XSp;

    // Data
    private ArrayList<A3_2_TipoTransaccionesGetsYSets> todasLasTransacciones_Result_List;
    private D_F4_AdaptadorTransaccionesCierre adaptadorTodasLasCuentas_TipoCuentas;
    private ArrayAdapter<String> consultaCuentasOrdenAscendente_ArrayAdapter;

    // Helpers
    private A99_MetodosVarios metodosVarios_Class;
    private A22_QueryManager a22QueryManager;
    private SQLiteDatabase db;
    private A1_1_AyudanteBD ayudante_Class;
    private A6_3_CSVDriveUploader csvDriveUploader;

    private A6_2_GoogleDriveDownloader driveDownloader;

    // Data statics
    public static Integer[] dateCurrent_ArrayInteger;
    private F6_Calculadora calculadora_Fragment;
    Button calculadoraLibre_XBt;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflarViews_View = inflater.inflate(R.layout.f4_cierres, container, false);

        calculadora_Fragment = new F6_Calculadora();
        calculadoraLibre_XBt = inflarViews_View.findViewById(R.id.calculadoraLibre_XBt);

        // Prevenir clicks en el fondo
        inflarViews_View.setOnClickListener(v -> {
            // Vacío para que no se activen los fragments de la lista
        });

        inicializarBaseDatos();
        inicializarViews(inflarViews_View);
        inicializarHelpers();
        configurarListeners();
        cargarDatosIniciales();

        if (calculadoraLibre_XBt != null) {
            calculadoraLibre_XBt.setOnClickListener(v -> mostrarCalculadoraLibre());
        }

        return inflarViews_View;
    }

    // ==================== LIFECYCLE ====================

    @Override
    public void onStop() {
        super.onStop();
        if (db != null && db.isOpen()) {
            db.close();
            Log.d(TAG, "Base de datos cerrada");
        }
    }
    
    /**
     * Inicializa todas las vistas
     */
    private void inicializarViews(View view) {
        _1_opcionBackupDeReservaTransacciones_XRb = view.findViewById(R.id._1_opcionBackupDeReservaTransacciones_XRb);
        _2_opcionAutonomoTransacciones_XRb = view.findViewById(R.id._2_opcionAutonomoTransacciones_XRb);
        _4_2_opcion_Importar_Balance_desde_Sheets_XRb = view.findViewById(R.id._4_2_opcion_Importar_Balance_desde_Sheets_XRb);
        _5_opcionRecuperarBackupTransacciones_XRb = view.findViewById(R.id._5_opcionRecuperarBackupTransacciones_XRb);
        _3_opcionTransaccionesAlgunasCuentas_XRb = view.findViewById(R.id._3_opcionTransaccionesAlgunasCuentas_XRb);
        opcionSaleDeLaApp_XRb = view.findViewById(R.id.opcionSaleDeLaApp_XRb);

        mensajeInformativo_XTv = view.findViewById(R.id.mensajeInformativo_XTv);
        envolventeEncabezado_XLl = view.findViewById(R.id.envolventeEncabezado_XLl);
        opcionActualCierre_XTv = view.findViewById(R.id.opcionActualCierre_XTv);
        todasLasTransacciones_XLv = view.findViewById(R.id.todasLasTransacciones_XLv);
        consultaPorCuentaYFechaEnOtroFragment_XSp = view.findViewById(R.id.consultaPorCuentaYFechaEnOtroFragment_XSp);

        envolventeEncabezado_XLl.setVisibility(View.INVISIBLE);
        _4_1_opcionSincronizarBalance_desde_Sheets_XRb = view.findViewById(R.id._4_1_opcionSincronizarBalance_desde_Sheets_XRb);
    }

    /**
     * Inicializa helpers y managers
     */

    /**
     * Configura todos los listeners de los botones
     */
    private void configurarListeners() {
        _1_configurarListener_OpcionBackupReserva();
        _2_configurarListener_OpcionAutonomo();
        _3_configurarListener_OpcionAlgunasCuentas();
        _4_2_configurarListener_OpcionSaldosIniciales();
        _5_configurarListener_OpcionRecuperarBackup();
        configurarListener_SpinnerCuentas();
        configurarListener_SalirApp();
        _4_1_configurarListener_SincronizarSheets();
    }
    

    // ==================== LISTENERS ====================

    private void _1_configurarListener_OpcionBackupReserva() {
        _1_opcionBackupDeReservaTransacciones_XRb.setOnClickListener(v -> {
            _7_limpiarLista();
            mensajeInformativo_XTv.setText("0");
            opcionActualCierre_XTv.setText("");

            dynamicQuery();
            if (todasLasTransacciones_Result_List.size() != 0) {
                opcionActualCierre_XTv.setText(_1_opcionBackupDeReservaTransacciones_XRb.getText().toString());

                // Guardar y subir con rotación automática
                //String nombreArchivo = "411 Backup antes de cierres.csv";
                String nombreArchivo = CSV_TRANSACTIONS_BEFORE_STARTING_CLOSURES.getFileName();
                A6_3_CSVDriveUploader.guardarYSubirTransacciones(requireContext(), nombreArchivo, FOLDER_ID_DRIVE,10);

                mostrarToastConDelay("Archivo local y en Drive", DELAY_TOAST_CORTO);
                _7verTransaccionesAdaptadorColumnas();
            } else {
                Toast.makeText(getActivity(), "No hay transacciones para hacer backup", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void _2_configurarListener_OpcionAutonomo() {

        _2_opcionAutonomoTransacciones_XRb.setOnClickListener(v -> {
            _7_limpiarLista();
            mensajeInformativo_XTv.setText("0");
            opcionActualCierre_XTv.setText("");

            dynamicQuery();
            if (todasLasTransacciones_Result_List.size() != 0) {
                opcionActualCierre_XTv.setText(_2_opcionAutonomoTransacciones_XRb.getText().toString());
                _2dialogoCierreAutonomoMetodoEnF51();
            } else {
                Toast.makeText(getActivity(), "No hay transacciones para hacer backup", Toast.LENGTH_LONG).show();
            }

            mostrarToastConDelay("Backup local, en Drive y todas las cuentas cerradas", DELAY_TOAST_LARGO);
        });
    }

    private void _3_configurarListener_OpcionAlgunasCuentas() {
        _3_opcionTransaccionesAlgunasCuentas_XRb.setOnClickListener(v -> {
            _7_limpiarLista();
            mensajeInformativo_XTv.setText("0");
            opcionActualCierre_XTv.setText("");

            dynamicQuery();
            if (todasLasTransacciones_Result_List.size() != 0) {
                opcionActualCierre_XTv.setText(_3_opcionTransaccionesAlgunasCuentas_XRb.getText().toString());
                _3dialogoCierreAutonomoAlgunasCuentas();
            } else {
                Toast.makeText(getActivity(), "No hay transacciones para hacer backup", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void _5_configurarListener_OpcionRecuperarBackup() {
        _5_opcionRecuperarBackupTransacciones_XRb.setOnClickListener(view -> {
            _7_limpiarLista();
            mensajeInformativo_XTv.setText("0");
            opcionActualCierre_XTv.setText("");
            opcionActualCierre_XTv.setText(_5_opcionRecuperarBackupTransacciones_XRb.getText().toString());

            // Diálogo para elegir origen: Local o Drive
            _5_dialogoElegirOrigenRestauracion();
        });
    }

    
// Continúa en parte 2...
// ==================== MÉTODOS DE CIERRE AUTÓNOMO ====================

    /**
     * Proceso de cierre autónomo general:
     * 1. Genera backup de transacciones
     * 2. Crea archivo CSV resumen por cuenta
     * 3. Borra historial de transacciones
     * 4. Reimporta saldos iniciales desde CSV
     * 5. Sube archivos a Drive con rotación automática
     */
    public void _2iniciarCierreAutonomoTransaccionesTodasLasCuentas() {

        subirArchivosADriveConDelay(new String[]{
                //"421 Backup antes de cerrar todas las cuentas.csv",
                CSV_TRANSACTIONS_BEFORE_CLOSING_ALL_ACCOUNTS.getFileName(),
        });

        new Handler().postDelayed(() -> {

            try {

                // 1. Crear archivo resumen por cuenta
                //String nombreArchivoResumen = "422 Backup saldos de todas las cuentas.csv";
                String nombreArchivoResumen = CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_ALL_ACCOUNTS.getFileName();

                A5_1_BackupManager._2csvConsultaResumenTodasLasCuentasAntesDeCerrarParaTablaTransaccionesDespuesDeCerrar(
                        getActivity(),
                        nombreArchivoResumen
                );

                // 2. Borrar historial de transacciones
                _7borrarHistorialTransacciones("transacciones");

                // 3. Reimportar saldos desde CSV
                importarTransaccionesDesdeCSV(nombreArchivoResumen);

                // 4. Subir archivos a Drive con delay (rotación automática)
                subirArchivosADriveConDelay(new String[]{
                        //"421 Backup antes de cerrar todas las cuentas.csv",
                        //"422 Backup saldos de todas las cuentas.csv",
                        CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_ALL_ACCOUNTS.getFileName(),
                });

                // 5. Actualizar vista
                _7verNumeroDeTransacciones();
                _7verTransaccionesAdaptadorColumnas();
            } catch (Exception e) {
                Log.e(TAG, "Error en cierre autónomo: " + e.getMessage(), e);
                Toast.makeText(getActivity(), "Error en el proceso de cierre", Toast.LENGTH_SHORT).show();
            }

        }, 10000);

    }

    public void _2dialogoCierreAutonomoMetodoEnF51() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Advertencia")
                .setMessage("Actualmente hay: " + _7verNumeroDeTransacciones() + " Transacciones. " +
                        "¿Eliminarlas todas y recuperar un resumen por cuenta para saldos iniciales?")
                .setCancelable(false)
                .setPositiveButton("Si", (dialog, id) ->{

                _2iniciarCierreAutonomoTransaccionesTodasLasCuentas();})

                .setNegativeButton("No", (dialog, id) -> {

                    _7verNumeroDeTransacciones();
                    _7verTransaccionesAdaptadorColumnas();
                    Toast.makeText(getActivity(), "No se realizaron acciones", Toast.LENGTH_SHORT).show();

                })

                .show();
    }

    // ==================== MÉTODOS DE CIERRE ALGUNAS CUENTAS ====================

    public void _3dialogoCierreAutonomoAlgunasCuentas() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Advertencia")
                .setMessage("Actualmente hay: " + _7verNumeroDeTransacciones() +
                        ", ¿Eliminarlas, hará un resumen de algunas cuentas y lo colocará como saldos iniciales?")
                .setCancelable(false)
                .setPositiveButton("Si", (dialog, id) -> {
                    _3iniciarCierreAutonomoTransaccionesAlgunasCuentas();
                    _7verNumeroDeTransacciones();
                    _7verTransaccionesAdaptadorColumnas();
                })
                .setNegativeButton("No", (dialog, id) -> {
                    _7verNumeroDeTransacciones();
                    _7verTransaccionesAdaptadorColumnas();
                    Toast.makeText(getActivity(), "No se realizaron acciones", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    public void _3iniciarCierreAutonomoTransaccionesAlgunasCuentas() {

        subirArchivosADriveConDelay(new String[]{
                //"431 Backup antes de cerrar algunas cuentas.csv",
                CSV_TRANSACTIONS_BEFORE_CLOSING_SOME_ACCOUNTS.getFileName()
        });

        new Handler().postDelayed(() -> {

            try {
                // 1. Crear archivo resumen de algunas cuentas
                //String nombreArchivoResumen = "432 Backup saldos de algunas cuentas.csv";
                String nombreArchivoResumen = CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_SOME_ACCOUNTS.getFileName();

                A5_1_BackupManager._3csvConsultaResumenAlgunasCuentasAntesDeCerrarParaTablaTransaccionesDespuesDeCerrar(
                        getActivity(),
                        nombreArchivoResumen
                );

                // 2. Borrar transacciones de algunas cuentas
                A1_2_OperacionesBD operacionesBD_class = new A1_2_OperacionesBD(getActivity());
                operacionesBD_class.eliminarTransaccionesAlgunasCuentas();

                // 3. Reimportar saldos iniciales de algunas cuentas
                importarTransaccionesDesdeCSV(nombreArchivoResumen);

                // 4. Subir archivos a Drive con rotación automática
                subirArchivosADriveConDelay(new String[]{
                        //"432 Backup saldos de algunas cuentas.csv",
                        CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_SOME_ACCOUNTS.getFileName(),
                });

                // 5. Actualizar vista
                _7verNumeroDeTransacciones();
                _7verTransaccionesAdaptadorColumnas();
            } catch (Exception e) {
                Log.e(TAG, "Error en cierre algunas cuentas: " + e.getMessage(), e);
                Toast.makeText(getActivity(), "Error en el proceso de cierre", Toast.LENGTH_SHORT).show();
            }


        }, 10000);

    }

    // ==================== MÉTODOS DE RESTAURACIÓN ====================

    public void _5_1_dialogoRestaurarDesdeLocalTransacciones() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Advertencia")
                .setMessage("Actualmente hay: " + _7verNumeroDeTransacciones() + " Transacciones. " +
                        "¿Se eliminarán y se restablecerán las del backup?")
                .setCancelable(false)
                .setPositiveButton("Si", (dialog, id) -> {

                    //String nombreArchivo = "411 Backup antes de cierres.csv";
                    String nombreArchivo = CSV_TRANSACTIONS_BEFORE_STARTING_CLOSURES.getFileName();
                    File archivo = new File(Environment.getExternalStorageDirectory() + "/Balance/" + nombreArchivo);

                    if (archivo.exists()) {
                        _5_1_iniciarCierreConImportacionDeCSVExternoTransaccionesBackup();
                        _7verNumeroDeTransacciones();
                        _7verTransaccionesAdaptadorColumnas();
                        Log.d(TAG, "Backup restaurado correctamente");
                    } else {
                        Toast.makeText(getActivity(),
                                "No está el archivo del último backup: " + archivo,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("No", (dialog, id) -> {
                    _7verNumeroDeTransacciones();
                    _7verTransaccionesAdaptadorColumnas();
                    Toast.makeText(getActivity(), "No se realizaron acciones", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    public void _5_1_iniciarCierreConImportacionDeCSVExternoTransaccionesBackup() {
        try {
            dynamicQuery();

            // 1. Si hay transacciones, hacer backup antes de restaurar
            if (todasLasTransacciones_Result_List.size() != 0) {
                _7borrarHistorialTransacciones("transacciones");

                new Handler().postDelayed(() -> {
                    A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                            requireContext(),
                            //"451 Backup antes de restaurar transacciones.csv",
                            CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL.getFileName(),
                            FOLDER_ID_DRIVE,
                            5
                    );
                    Log.d(TAG, "Backup pre-restauración guardado");
                }, 15000);
            } else {
                Toast.makeText(getActivity(), "No hay transacciones para hacer backup", Toast.LENGTH_LONG).show();
            }

            // 2. Restaurar desde backup
            //String nombreArchivo = "411 Backup antes de cierres.csv";
            String nombreArchivo = CSV_TRANSACTIONS_BEFORE_STARTING_CLOSURES.getFileName();
            importarTransaccionesDesdeCSV(nombreArchivo);

            Log.d(TAG, "Transacciones restauradas correctamente");
        } catch (Exception e) {
            Log.e(TAG, "Error al restaurar backup: " + e.getMessage(), e);
            Toast.makeText(getActivity(), "No se han podido recuperar transacciones del backup", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== MÉTODOS AUXILIARES ====================

      /**
     * Importa transacciones desde un archivo CSV
     * Busca en carpeta de app y en carpeta tradicional
     */
    private void importarTransaccionesDesdeCSV(String nombreArchivo) {
        Log.d(TAG, "=== IMPORTANDO CSV ===");
        Log.d(TAG, "Archivo solicitado: " + nombreArchivo);

        // Ubicación 1: Carpeta de la app (Android 10+)
        File carpetaApp = requireContext().getExternalFilesDir(null);
        String rutaApp = carpetaApp.getAbsolutePath() + "/Balance/" + nombreArchivo;
        File archivoApp = new File(rutaApp);

        // Ubicación 2: Carpeta tradicional
        String rutaTradicional = Environment.getExternalStorageDirectory() + "/Balance/" + nombreArchivo;
        File archivoTradicional = new File(rutaTradicional);

        Log.d(TAG, "Buscando en carpeta app: " + rutaApp);
        Log.d(TAG, "Archivo existe en app: " + archivoApp.exists());
        Log.d(TAG, "Buscando en carpeta tradicional: " + rutaTradicional);
        Log.d(TAG, "Archivo existe en tradicional: " + archivoTradicional.exists());

        // Determinar qué archivo usar
        File archivoAUsar = null;
        String rutaFinal = null;

        if (archivoApp.exists()) {
            archivoAUsar = archivoApp;
            rutaFinal = rutaApp;
            Log.d(TAG, "Usando archivo de carpeta app");
        } else if (archivoTradicional.exists()) {
            archivoAUsar = archivoTradicional;
            rutaFinal = rutaTradicional;
            Log.d(TAG, "Usando archivo de carpeta tradicional");
        } else {
            Log.e(TAG, "Archivo no encontrado en ninguna ubicación");
            Toast.makeText(getActivity(),
                    "Archivo no encontrado: " + nombreArchivo,
                    Toast.LENGTH_LONG).show();
            return;
        }

        Log.d(TAG, "Tamaño archivo: " + archivoAUsar.length() + " bytes");

        // Importar desde el archivo encontrado
        int lineasImportadas = 0;
        try (FileReader fileReader = new FileReader(rutaFinal);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            String linea;
            while ((linea = bufferedReader.readLine()) != null) {
                String[] datos = linea.split(",");
                insertarTransaccion(datos);
                lineasImportadas++;
            }

            Log.d(TAG, "=== IMPORTACIÓN COMPLETADA ===");
            Log.d(TAG, "Líneas importadas: " + lineasImportadas);
            Log.d(TAG, "Desde: " + rutaFinal);

        } catch (Exception e) {
            Log.e(TAG, "Error al importar CSV: " + e.getMessage(), e);
            Toast.makeText(getActivity(),
                    "Error al importar: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Inserta una transacción en la base de datos
     */
    private void insertarTransaccion(String[] datos) {
        if (datos == null || datos.length < 13) {
            Log.e(TAG, "Datos insuficientes para insertar transacción");
            return;
        }

        try {
            A1_1_AyudanteBD ayudanteBD = new A1_1_AyudanteBD(
                    getActivity(),
                    balanceSqlite_String_PSF,
                    null,
                    version1BalanceSqlite_int_PSF
            );
            SQLiteDatabase db = ayudanteBD.getWritableDatabase();

            ContentValues valores = new ContentValues();
            valores.put("c1_Documento", datos[0]);
            valores.put("c2_ItemDoc", datos[1]);
            valores.put("c3_Cuenta", datos[2]);
            valores.put("c4_Signo", datos[3]);
            valores.put("c5_Valor", datos[4]);
            valores.put("c6_Descripcion", datos[5]);
            valores.put("c7_FechaYhora", datos[6]);
            valores.put("c8_FechaInicial", datos[7]);
            valores.put("c9_FechaModificacion", datos[8]);
            valores.put("c10_Grupo1", datos[9]);
            valores.put("c11_Grupo2", datos[10]);
            valores.put("c12_ColumnaDisponible", datos[11]);
            valores.put("c13_ColumnaDisponible", datos[12]);

            db.insert("transacciones", null, valores);
            db.close();
        } catch (Exception e) {
            Log.e(TAG, "Error al insertar transacción: " + e.getMessage(), e);
        }
    }

    /**
     * Sube múltiples archivos a Drive con delay
     * Incluye rotación automática (mantiene últimos 5)
     */
    private void subirArchivosADriveConDelay(String[] nombresArchivos) {
        new Handler().postDelayed(() -> {
            for (String nombreArchivo : nombresArchivos) {
                A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                        requireContext(),
                        nombreArchivo,
                        FOLDER_ID_DRIVE,
                        6
                );
            }
            Log.d(TAG, "Archivos subidos a Drive con rotación automática");
        }, DELAY_SUBIDA_DRIVE);
    }

    /**
     * Muestra un Toast con delay
     */
    private void mostrarToastConDelay(String mensaje, int delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                        Toast.makeText(getActivity(), mensaje, Toast.LENGTH_SHORT).show(),
                delay
        );
    }
    
//INICIO MÉTODOS OPCIÓN 4

    /**
     * Configura listener para botón de sincronización con Sheets
     */
    private void _4_1_configurarListener_SincronizarSheets() {
        _4_1_opcionSincronizarBalance_desde_Sheets_XRb.setOnClickListener(v -> {
            _7_limpiarLista();
            mensajeInformativo_XTv.setText("0");
            opcionActualCierre_XTv.setText("");
            opcionActualCierre_XTv.setText(_4_1_opcionSincronizarBalance_desde_Sheets_XRb.getText().toString());

            // Mostrar diálogo de confirmación
            _4_1_dialogoConfirmarSincronizacion();
        });
    }
    
    private void _4_2_configurarListener_OpcionSaldosIniciales() {

        _4_2_opcion_Importar_Balance_desde_Sheets_XRb.setOnClickListener(v -> {
            Log.d(TAG, "=== CLICK EN OPCIÓN 4 ===");

            _7_limpiarLista();
            mensajeInformativo_XTv.setText("0");
            opcionActualCierre_XTv.setText("");
            opcionActualCierre_XTv.setText(_4_2_opcion_Importar_Balance_desde_Sheets_XRb.getText().toString());

            Log.d(TAG, "Mostrando diálogo de selección de origen");

            // Diálogo para elegir origen: Local o Drive

            A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                    requireContext(),
                    //"441 Backup antes de Balance_desde_Sheets.csv",
                    CSV_TRANSACTIONS_BEFORE_CLOSING_RESTORING_SHEETS.getFileName(),
                    FOLDER_ID_DRIVE,
                    5
            );
            _4_2_dialogoElegirOrigenRestauracion();
        });
    }

    /**
     * Diálogo para elegir entre restaurar desde Local o Drive
     */
    private void _4_2_dialogoElegirOrigenRestauracion() {
        Log.d(TAG, "=== INICIANDO DIÁLOGO OPCIÓN 4 ===");

        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle("Elegir origen de restauración");
            builder.setMessage("¿Desde dónde deseas restaurar los saldos iniciales?");

            // Crear LinearLayout para los botones
            LinearLayout layout = new LinearLayout(getActivity());
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setPadding(50, 40, 50, 40);

            // RadioGroup
            final RadioGroup radioGroup = new RadioGroup(getActivity());
            radioGroup.setOrientation(RadioGroup.VERTICAL);

            final RadioButton rbLocal = new RadioButton(getActivity());
            rbLocal.setText("Almacenamiento Local");
            rbLocal.setId(1);
            rbLocal.setChecked(true);
            rbLocal.setTextSize(16);
            rbLocal.setPadding(10, 20, 10, 20);

            final RadioButton rbDrive = new RadioButton(getActivity());
            rbDrive.setText("Google Drive");
            rbDrive.setId(2);
            rbDrive.setTextSize(16);
            rbDrive.setPadding(10, 20, 10, 20);

            radioGroup.addView(rbLocal);
            radioGroup.addView(rbDrive);
            layout.addView(radioGroup);

            builder.setView(layout);

            builder.setPositiveButton("Continuar", (dialog, which) -> {
                int selectedId = radioGroup.getCheckedRadioButtonId();
                Log.d(TAG, "Opción seleccionada: " + (selectedId == 1 ? "Local" : "Drive"));

                if (selectedId == 1) {
                    // Restaurar desde local
                    Log.d(TAG, "Iniciando restauración desde LOCAL");
                    _4_2_1_dialogoRestaurarDesdeLocalTransacciones();
                } else {
                    // Restaurar desde Drive
                    Log.d(TAG, "Iniciando restauración desde DRIVE");
                    _4_2_dialogoRestaurarDesdeGoogleDrive();
                }
            });

            builder.setNegativeButton("Cancelar", (dialog, which) -> {
                Log.d(TAG, "Restauración cancelada por usuario");
                Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
            });

            builder.setCancelable(false);

            AlertDialog dialog = builder.create();
            dialog.show();

            Log.d(TAG, "Diálogo de selección mostrado correctamente");

        } catch (Exception e) {
            Log.e(TAG, "ERROR al mostrar diálogo opción 4: " + e.getMessage(), e);
            Toast.makeText(getActivity(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Diálogo para restaurar desde almacenamiento local
     */
    public void _4_2_1_dialogoRestaurarDesdeLocalTransacciones() {
        Log.d(TAG, "=== RESTAURAR DESDE LOCAL - OPCIÓN 4 ===");

        //String nombreArchivo = "440 Backup Balance_desde_Sheets.csv";
        String nombreArchivo = CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED.getFileName();
        File archivo = new File(Environment.getExternalStorageDirectory() + "/Balance/" + nombreArchivo);

        Log.d(TAG, "Verificando archivo local: " + archivo.getAbsolutePath());
        Log.d(TAG, "Archivo existe: " + archivo.exists());

        if (!archivo.exists()) {
            new AlertDialog.Builder(getActivity())
                    .setTitle("Archivo no encontrado")
                    .setMessage("No se encontró el archivo:\n\n" + nombreArchivo + "\n\n" +
                            "Usa primero la Opción 6 para sincronizar desde Sheets.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }

        long transaccionesActuales = _7verNumeroDeTransacciones();

        new AlertDialog.Builder(getActivity())
                .setTitle("Confirmar restauración")
                .setMessage("¿Restaurar saldos iniciales desde archivo local?\n\n" +
                        "Archivo: " + nombreArchivo + "\n" +
                        "Tamaño: " + (archivo.length() / 1024) + " KB\n\n" +
                        "Transacciones actuales: " + transaccionesActuales + "\n" +
                        "Se eliminarán y restaurarán desde el backup.")
                .setCancelable(false)
                .setPositiveButton("Sí, restaurar", (dialog, id) -> {
                    Log.d(TAG, "Usuario confirmó restauración desde LOCAL");
                    _4_iniciarCierreConImportacionDeCSVExternoTransaccionesBackup();
                    _7verNumeroDeTransacciones();
                    _7verTransaccionesAdaptadorColumnas();
                })
                .setNegativeButton("No", (dialog, id) -> {
                    Log.d(TAG, "Usuario canceló restauración");
                    _7verNumeroDeTransacciones();
                    _7verTransaccionesAdaptadorColumnas();
                    Toast.makeText(getActivity(), "No se realizaron acciones", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /**
     * Diálogo para listar y elegir archivo desde Drive
     */
    private void _4_2_dialogoRestaurarDesdeGoogleDrive() {
        Log.d(TAG, "=== RESTAURAR DESDE DRIVE - OPCIÓN 4 ===");

        // Inicializar downloader
        driveDownloader = new A6_2_GoogleDriveDownloader(getActivity(), FOLDER_ID_DRIVE);

        // Prefijo de archivos a buscar (serie 440)
        String prefijo = "441_transacciones";
        Log.d(TAG, "Buscando en Drive con prefijo: " + prefijo);

        // Mostrar diálogo de carga
        AlertDialog loadingDialog = new AlertDialog.Builder(getActivity())
                .setTitle("Cargando...")
                .setMessage("Buscando archivos en Drive...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        // Listar archivos
        driveDownloader.listarArchivos(prefijo, new A6_2_GoogleDriveDownloader.OnFilesListedListener() {
            @Override
            public void onFilesListed(List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
                loadingDialog.dismiss();

                Log.d(TAG, "Archivos encontrados en Drive: " + (archivos != null ? archivos.size() : 0));

                if (archivos == null || archivos.isEmpty()) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Sin archivos")
                            .setMessage("No se encontraron archivos de Balance en Drive.\n\n" +
                                    "Usa primero la Opción 6 para sincronizar desde Sheets.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                // Mostrar lista de archivos
                _4_2_mostrarListaArchivosParaRestaurar(archivos);
            }

            @Override
            public void onError(String error) {
                loadingDialog.dismiss();
                Log.e(TAG, "Error al buscar en Drive: " + error);

                Toast.makeText(getActivity(),
                        "Error al buscar archivos: " + error,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Muestra lista de archivos disponibles para restaurar
     */
    private void _4_2_mostrarListaArchivosParaRestaurar(List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
        Log.d(TAG, "Mostrando lista con " + archivos.size() + " archivos");

        // Convertir a array de strings
        String[] nombresArchivos = new String[archivos.size()];
        for (int i = 0; i < archivos.size(); i++) {
            nombresArchivos[i] = archivos.get(i).nombre;
            Log.d(TAG, "Archivo " + i + ": " + nombresArchivos[i]);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Seleccionar archivo de backup");
        builder.setItems(nombresArchivos, (dialog, position) -> {
            A6_2_GoogleDriveDownloader.DriveFileInfo archivoSeleccionado = archivos.get(position);
            Log.d(TAG, "Usuario seleccionó: " + archivoSeleccionado.nombre);
            _4_2_confirmarYDescargarArchivo(archivoSeleccionado);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Log.d(TAG, "Usuario canceló selección");
            Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    /**
     * Confirma y descarga el archivo seleccionado
     */
    private void _4_2_confirmarYDescargarArchivo(A6_2_GoogleDriveDownloader.DriveFileInfo archivo) {
        Log.d(TAG, "Confirmando descarga de: " + archivo.nombre);

        long transaccionesActuales = _7verNumeroDeTransacciones();

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Confirmar restauración");
        builder.setMessage("¿Restaurar desde este archivo?\n\n" +
                "Archivo: " + archivo.nombre + "\n\n" +
                "Transacciones actuales: " + transaccionesActuales + "\n" +
                "Se eliminarán y restaurarán desde el backup.");

        builder.setPositiveButton("Sí, restaurar", (dialog, which) -> {
            _7borrarHistorialTransacciones("transacciones");
            Log.d(TAG, "Usuario confirmó descarga y restauración");
            _4_2_descargarYRestaurarDesdeGoogleDrive(archivo);
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Log.d(TAG, "Usuario canceló descarga");
            Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    /**
     * Descarga archivo de Drive y ejecuta restauración
     */
    private void _4_2_descargarYRestaurarDesdeGoogleDrive(A6_2_GoogleDriveDownloader.DriveFileInfo archivo) {
        Log.d(TAG, "=== INICIANDO DESCARGA DESDE DRIVE ===");
        Log.d(TAG, "Archivo ID: " + archivo.id);
        Log.d(TAG, "Archivo nombre: " + archivo.nombre);

        final android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getActivity());
        progressDialog.setMessage("Descargando desde Drive...\n\nPor favor espere");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.show();

        driveDownloader.descargarArchivo(archivo.id, archivo.nombre,
                new A6_2_GoogleDriveDownloader.OnFileDownloadedListener() {
                    @Override
                    public void onFileDownloaded(String localPath) {
                        Log.d(TAG, "✓ Descarga exitosa: " + localPath);

                        progressDialog.setMessage("Restaurando transacciones...\n\nÚltimo paso");

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                _4_ejecutarRestauracionDesdeArchivoLocal(localPath);

                                if (progressDialog.isShowing()) {
                                    progressDialog.dismiss();
                                }

                                mostrarMensajeExito("✓ Saldos iniciales restaurados desde Drive");
                                Log.d(TAG, "=== RESTAURACIÓN COMPLETADA ===");

                                /*new Handler().postDelayed(() -> {
                                    A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                                            requireContext(),
                                            //"441 Backup antes de Balance_desde_Sheets.csv",
                                            CSV_TRANSACTIONS_AFTER_CLOSING_RESTORING_SHEETS.getFileName(),
                                            FOLDER_ID_DRIVE,
                                            5
                                    );
                                }, 20000);*/

                                A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                                        requireContext(),
                                        //"441 Backup antes de Balance_desde_Sheets.csv",
                                        CSV_TRANSACTIONS_AFTER_CLOSING_RESTORING_SHEETS.getFileName(),
                                        FOLDER_ID_DRIVE,
                                        5
                                );

                            } catch (Exception e) {
                                if (progressDialog.isShowing()) {
                                    progressDialog.dismiss();
                                }
                                Log.e(TAG, "Error en restauración: " + e.getMessage(), e);
                                Toast.makeText(getActivity(),
                                        "Error en restauración: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }, 800);
                    }

                    @Override
                    public void onError(String error) {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Log.e(TAG, "Error al descargar: " + error);
                        Toast.makeText(getActivity(),
                                "Error al descargar: " + error,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Ejecuta restauración desde archivo local
     */
    private void _4_ejecutarRestauracionDesdeArchivoLocal(String rutaArchivo) {
        Log.d(TAG, "=== EJECUTANDO RESTAURACIÓN LOCAL ===");
        Log.d(TAG, "Ruta: " + rutaArchivo);

        try {
            dynamicQuery();

            long transaccionesActuales = todasLasTransacciones_Result_List.size();
            Log.d(TAG, "Transacciones antes de borrar: " + transaccionesActuales);

            // Importar desde archivo descargado
            File archivo = new File(rutaArchivo);
            String nombreArchivo = archivo.getName();
            Log.d(TAG, "Importando desde: " + nombreArchivo);

            importarTransaccionesDesdeCSV(nombreArchivo);

            // Actualizar vista
            _7verNumeroDeTransacciones();
            _7verTransaccionesAdaptadorColumnas();

            Log.d(TAG, "Transacciones después de restaurar: " + _7verNumeroDeTransacciones());

        } catch (Exception e) {
            Log.e(TAG, "Error al restaurar: " + e.getMessage(), e);
            Toast.makeText(getActivity(),
                    "Error al restaurar: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Restauración desde archivo local (compatibilidad con método anterior)
     */
    public void _4_iniciarCierreConImportacionDeCSVExternoTransaccionesBackup() {
        Log.d(TAG, "=== RESTAURACIÓN DESDE LOCAL (MÉTODO ANTIGUO) ===");

        try {
            dynamicQuery();

            // Si hay transacciones, hacer backup antes de restaurar
            if (todasLasTransacciones_Result_List.size() != 0) {
                _7borrarHistorialTransacciones("transacciones");

                new Handler().postDelayed(() -> {
                    A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                            requireContext(),
                            //"441 Backup antes de Balance_desde_Sheets.csv",
                            CSV_TRANSACTIONS_BEFORE_CLOSING_RESTORING_SHEETS.getFileName(),
                            FOLDER_ID_DRIVE,
                            5
                    );
                    Log.d(TAG, "Backup pre-restauración guardado");
                }, 5000);
            } else {
                Toast.makeText(getActivity(), "No hay transacciones para hacer backup", Toast.LENGTH_LONG).show();
            }

            // Restaurar desde backup
            //String nombreArchivo = "440 Backup Balance_desde_Sheets.csv";
            String nombreArchivo = CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED.getFileName();
            Log.d(TAG, "Importando: " + nombreArchivo);
            importarTransaccionesDesdeCSV(nombreArchivo);

            Log.d(TAG, "Transacciones restauradas: " + _7verNumeroDeTransacciones());

        } catch (Exception e) {
            Log.e(TAG, "Error al restaurar backup: " + e.getMessage(), e);
            Toast.makeText(getActivity(), "No se han podido recuperar transacciones del backup", Toast.LENGTH_SHORT).show();
        }
    }

//FIN MÉTODOS OPCIÓN 4

//INIIO MÉTODOS OPCIÓN 5

    /**
     * Diálogo para elegir entre restaurar desde Local o Drive
     */
    private void _5_dialogoElegirOrigenRestauracion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Elegir origen de restauración");

        // Crear RadioGroup
        final RadioGroup radioGroup = new RadioGroup(getActivity());
        radioGroup.setOrientation(RadioGroup.VERTICAL);
        radioGroup.setPadding(50, 40, 50, 40);

        final RadioButton rbLocal = new RadioButton(getActivity());
        rbLocal.setText("Almacenamiento Local");
        rbLocal.setId(1);
        rbLocal.setChecked(true);

        final RadioButton rbDrive = new RadioButton(getActivity());
        rbDrive.setText("Google Drive");
        rbDrive.setId(2);

        radioGroup.addView(rbLocal);
        radioGroup.addView(rbDrive);

        builder.setView(radioGroup);

        builder.setPositiveButton("Continuar", (dialog, which) -> {
            int selectedId = radioGroup.getCheckedRadioButtonId();
            if (selectedId == 1) {
                // Restaurar desde local (método actual)
                A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                        requireContext(),
                        //"451 Backup antes de restaurar transacciones.csv",
                        CSV_TRANSACTIONS_BEFORE_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL.getFileName(),
                        FOLDER_ID_DRIVE,
                        5
                );

                _5_1_dialogoRestaurarDesdeLocalTransacciones();
            } else {
                // Restaurar desde Drive (nuevo)
                _5_2_dialogoRestaurarDesdeGoogleDrive();
            }
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    /**
     * Diálogo para listar y elegir archivo desde Drive
     */
    private void _5_2_dialogoRestaurarDesdeGoogleDrive() {
        // Inicializar downloader
        driveDownloader = new A6_2_GoogleDriveDownloader(getActivity(), FOLDER_ID_DRIVE);

        // Prefijo de archivos a buscar (serie 411)
        String prefijo = "411_Antes";

        // Mostrar diálogo de carga
        AlertDialog loadingDialog = new AlertDialog.Builder(getActivity())
                .setTitle("Cargando...")
                .setMessage("Buscando archivos en Drive...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        // Listar archivos
        driveDownloader.listarArchivos(prefijo, new A6_2_GoogleDriveDownloader.OnFilesListedListener() {
            @Override
            public void onFilesListed(List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
                loadingDialog.dismiss();

                if (archivos == null || archivos.isEmpty()) {
                    Toast.makeText(getActivity(),
                            "No se encontraron archivos de backup en Drive",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Mostrar lista de archivos
                _5_2_mostrarListaArchivosParaRestaurar(archivos);
            }

            @Override
            public void onError(String error) {
                loadingDialog.dismiss();
                Toast.makeText(getActivity(),
                        "Error al buscar archivos: " + error,
                        Toast.LENGTH_LONG).show();
                Log.e(TAG, "Error al listar archivos: " + error);
            }
        });
    }

    /**
     * Muestra lista de archivos disponibles para restaurar
     */
    private void _5_2_mostrarListaArchivosParaRestaurar(List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
        // Convertir a array de strings para el diálogo
        String[] nombresArchivos = new String[archivos.size()];
        for (int i = 0; i < archivos.size(); i++) {
            nombresArchivos[i] = archivos.get(i).nombre;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Seleccionar archivo de backup en Drive");
        builder.setItems(nombresArchivos, (dialog, position) -> {
            A6_2_GoogleDriveDownloader.DriveFileInfo archivoSeleccionado = archivos.get(position);
            _5_2_confirmarYDescargarArchivo(archivoSeleccionado);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    /**
     * Confirma y descarga el archivo seleccionado
     */
    private void _5_2_confirmarYDescargarArchivo(A6_2_GoogleDriveDownloader.DriveFileInfo archivo) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Confirmar restauración");
        builder.setMessage("¿Restaurar desde este archivo?\n\n" +
                "Archivo: " + archivo.nombre + "\n\n" +
                "Actualmente hay: " + _7verNumeroDeTransacciones() + " transacciones.\n" +
                "Se eliminarán y restaurarán desde el backup.");

        builder.setPositiveButton("Sí, restaurar", (dialog, which) -> {
            //backup antes
            
            _5_descargarYRestaurarDesdeGoogleDrive(archivo);

            //backup despues
            new Handler().postDelayed(() -> {
                A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                        requireContext(),
                        //"451 Backup antes de restaurar transacciones.csv",
                        CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL_FROM_DRIVE.getFileName(),
                        FOLDER_ID_DRIVE,
                        5
                );
                Log.d(TAG, "Backup pre-restauración guardado");
            }, 20000);

        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    /**
     * Descarga archivo de Drive y ejecuta restauración
     */
    // ==================== EN F51_CierresPrincipal.java ====================
// REEMPLAZAR el método _5_descargarYRestaurarDesdeGoogleDrive() COMPLETO por este:

    /**
     * Descarga archivo de Drive y ejecuta restauración
     */
    private void _5_descargarYRestaurarDesdeGoogleDrive(A6_2_GoogleDriveDownloader.DriveFileInfo archivo) {
        // ProgressDialog más visible y robusto
        final android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getActivity());
        progressDialog.setMessage("Descargando desde Drive...\n\nPor favor espere, no cierre la app");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.show();

        _7borrarHistorialTransacciones("transacciones");

        // Descargar archivo
        driveDownloader.descargarArchivo(archivo.id, archivo.nombre,
                new A6_2_GoogleDriveDownloader.OnFileDownloadedListener() {
                    @Override
                    public void onFileDownloaded(String localPath) {
                        // Actualizar mensaje
                        progressDialog.setMessage("Restaurando transacciones...\n\nÚltimo paso");

                        // Delay para que el usuario vea el mensaje de restauración
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                // Ejecutar restauración usando método existente
                                _5_ejecutarRestauracionDesdeArchivoLocal(localPath);

                                // Cerrar diálogo
                                if (progressDialog.isShowing()) {
                                    progressDialog.dismiss();
                                }

                                // Mensaje de éxito (verde)
                                mostrarMensajeExito("✓ Backup restaurado exitosamente desde Drive");

                            } catch (Exception e) {
                                if (progressDialog.isShowing()) {
                                    progressDialog.dismiss();
                                }
                                Toast.makeText(getActivity(),
                                        "Error en restauración: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                                Log.e(TAG, "Error en restauración: " + e.getMessage(), e);
                            }
                        }, 800); // Pequeño delay para mostrar mensaje de restauración
                    }

                    @Override
                    public void onError(String error) {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                        Toast.makeText(getActivity(),
                                "Error al descargar: " + error,
                                Toast.LENGTH_LONG).show();
                        Log.e(TAG, "Error en descarga: " + error);
                    }
                });
    }

    /**
     * Ejecuta restauración desde archivo local (reutiliza lógica existente)
     */
    private void _5_ejecutarRestauracionDesdeArchivoLocal(String rutaArchivo) {
        try {
            dynamicQuery();

            // Si hay transacciones, hacer backup antes de restaurar
            /*if (todasLasTransacciones_Result_List.size() != 0) {
                _7borrarHistorialTransacciones("transacciones");

                new Handler().postDelayed(() -> {
                    A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                            requireContext(),
                            //"451 Backup antes de restaurar transacciones.csv",
                            CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL.getBaseName(),
                            FOLDER_ID_DRIVE,
                            5
                    );
                    Log.d(TAG, "Backup pre-restauración guardado");
                }, 5000);
            }*/

            // Importar desde archivo descargado
            File archivo = new File(rutaArchivo);
            String nombreArchivo = archivo.getName();
            importarTransaccionesDesdeCSV(nombreArchivo);

            // Actualizar vista
            _7verNumeroDeTransacciones();
            _7verTransaccionesAdaptadorColumnas();

            Log.d(TAG, "Restauración desde Drive completada");
        } catch (Exception e) {
            Log.e(TAG, "Error al restaurar desde Drive: " + e.getMessage(), e);
            Toast.makeText(getActivity(),
                    "Error al restaurar: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }
    

//FIN MÉTODOS OPCIÓN 5

// INICIO PROCESO CON SHEETS
// ==================== MÉTODOS OPCIÓN 6: SINCRONIZAR DESDE SHEETS ====================

    /**
     * Diálogo de confirmación para sincronizar
     */
    private void _4_1_dialogoConfirmarSincronizacion() {
        long transaccionesActuales = _7verNumeroDeTransacciones();

        new AlertDialog.Builder(getActivity())
                .setTitle("Sincronizar desde Google Sheets")
                .setMessage("Esta operación:\n\n" +
                        "1. Descargará el Balance actualizado desde Sheets\n" +
                        "2. Lo subirá a Drive como backup\n" +
                        "3. Estará disponible para restaurar en Opción 4\n\n" +
                        "Transacciones actuales en app: " + transaccionesActuales + "\n\n" +
                        "¿Continuar?")
                .setPositiveButton("Sí, sincronizar", (dialog, which) -> {
                    _4_1_iniciarSincronizacionSheets();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    Toast.makeText(getActivity(), "Sincronización cancelada", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * Inicia proceso de sincronización desde Sheets
     */
    private void _4_1_iniciarSincronizacionSheets() {
        // Mostrar diálogo de progreso
        final android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getActivity());
        progressDialog.setTitle("Sincronizando");
        progressDialog.setMessage("Descargando desde Google Sheets...\n\nPor favor espere");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.show();

        // ID de tu archivo Sheets
        String sheetsId = "1fxxl97K0Mt1-sWQ9pDZ6k6qMuvL3g9ksg3gNszqwWBo";
        //String nombreArchivo = "440 Backup Balance_desde_Sheets.csv";
        String nombreArchivo = CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED.getFileName();
        //String SheetsAApp = "balance_sheets_a_app";

        Log.d(TAG, "=== INICIANDO SINCRONIZACIÓN SHEETS ===");
        Log.d(TAG, "Sheets ID: " + sheetsId);
        Log.d(TAG, "Archivo destino: " + nombreArchivo);

        // Descargar desde Sheets
        sheetsDownloader.descargarCSV(sheetsId, nombreArchivo,
                 new A6_5_SheetsDownloader.OnDownloadListener() {

                    @Override
                    public void onDownloadSuccess(String rutaLocal, int numLineas) {
                        Log.d(TAG, "✓ Descarga exitosa: " + rutaLocal);
                        Log.d(TAG, "✓ Líneas (registros): " + numLineas);

                        // Cambiar mensaje del diálogo
                        progressDialog.setMessage("Subiendo a Google Drive...\n\nÚltimo paso");

                        // Delay para mostrar mensaje
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                // Subir a Drive con rotación (mantener últimos 5)
                                A6_3_CSVDriveUploader.guardarYSubirTransacciones(
                                        requireContext(),
                                        nombreArchivo,
                                        FOLDER_ID_DRIVE,
                                        5
                                );

                                Log.d(TAG, "✓ Archivo subido a Drive");

                                // Delay antes de cerrar diálogo
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    progressDialog.dismiss();
                                    _6mostrarResultadoSincronizacion(numLineas, rutaLocal);
                                }, 1500);

                            } catch (Exception e) {
                                progressDialog.dismiss();
                                Log.e(TAG, "Error al subir a Drive: " + e.getMessage(), e);
                                Toast.makeText(getActivity(),
                                        "Error al subir a Drive: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }, 800);
                    }

                    @Override
                    public void onDownloadError(String error) {
                        progressDialog.dismiss();
                        Log.e(TAG, "✗ Error en descarga: " + error);

                        new AlertDialog.Builder(getActivity())
                                .setTitle("Error en sincronización")
                                .setMessage("No se pudo descargar desde Sheets:\n\n" + error + "\n\n" +
                                        "Verifica:\n" +
                                        "• Conexión a internet\n" +
                                        "• Permisos del archivo en Sheets\n" +
                                        "• El archivo está compartido con 'Cualquiera con el enlace'")
                                .setPositiveButton("Entendido", null)
                                .show();
                    }
                });
    }

    /**
     * Muestra resultado exitoso de la sincronización
     */
    private void _6mostrarResultadoSincronizacion(int numRegistros, String rutaLocal) {
        // Obtener info del archivo
        String nombreArchivo = CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED.getFileName();
        String infoArchivo = A6_5_SheetsDownloader.obtenerInfoArchivo(nombreArchivo);

        // Diálogo de éxito
        new AlertDialog.Builder(getActivity())
                .setTitle("✓ Sincronización exitosa")
                .setMessage(
                        "Balance descargado desde Sheets:\n\n" +
                                "• Registros: " + numRegistros + "\n" +
                                "• " + infoArchivo + "\n" +
                                "• Ubicación local: /Balance/\n" +
                                "• Subido a Drive: ✓\n\n" +
                                "Ahora puedes usar la Opción 4 para restaurar estos saldos iniciales."
                )
                .setPositiveButton("Entendido", (dialog, which) -> {
                    // Mensaje visual de éxito
                    mostrarMensajeExito("✓ Balance sincronizado y listo para usar");
                })
                .show();

        Log.d(TAG, "=== SINCRONIZACIÓN COMPLETADA ===");
    }

// FIN MÉTODOS OPCIÓN 6

    // ==================== MÉTODOS COMUNES ====================

    /**
     * Muestra mensaje de éxito con fondo verde
     */
    private void mostrarMensajeExito(String mensaje) {
        Toast toast = Toast.makeText(getActivity(), mensaje, Toast.LENGTH_LONG);
        View view = toast.getView();
        if (view != null) {
            view.setBackgroundColor(Color.parseColor("#4CAF50")); // Verde material
            TextView text = view.findViewById(android.R.id.message);
            if (text != null) {
                text.setTextColor(Color.WHITE);
                text.setGravity(Gravity.CENTER);
                text.setPadding(20, 20, 20, 20);
            }
        }
        toast.show();
    }

    /**
     * Carga datos iniciales
     */
    private void cargarDatosIniciales() {
        mensajeInformativo_XTv.setText("0");
        opcionActualCierre_XTv.setText("Actualmente");
        dynamicQuery();
        mensajeInformativo_XTv.setText(String.valueOf(todasLasTransacciones_Result_List.size()));
        _7verTransaccionesAdaptadorColumnas();
    }

    private void configurarListener_SpinnerCuentas() {
        consultaPorCuentaYFechaEnOtroFragment_XSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (consultaPorCuentaYFechaEnOtroFragment_XSp.getSelectedItem().toString().isEmpty()) {
                    return;
                } else {
                    _9_verTransaccionesPorCuentaConSpinnerEnF2();
                    consultaPorCuentaYFechaEnOtroFragment_XSp.setSelection(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    private void configurarListener_SalirApp() {
        opcionSaleDeLaApp_XRb.setOnClickListener(view -> {
            new AlertDialog.Builder(getActivity())
                    .setTitle("¿Salir de la App?")
                    .setCancelable(false)
                    .setPositiveButton("Si", (dialog, id) ->
                            android.os.Process.killProcess(android.os.Process.myPid()))
                    .setNegativeButton("Continuar", (dialog, id) -> {})
                    .create()
                    .show();
        });
    }

    // ==================== QUERY METHODS ====================

    /**
     * Consulta dinámica de transacciones y cuentas
     */
    public void dynamicQuery() {
        if (consultaPorCuentaYFechaEnOtroFragment_XSp == null) {
            return;
        }

        try {
            // Cuentas en orden ascendente
            A23_QueryResult queryAzAllAccounts_Result = a22QueryManager.queryAzAllAccounts();
            List<String> cuentasOrdenAscendente_Result_List = queryAzAllAccounts_Result.getDatos();

            consultaCuentasOrdenAscendente_ArrayAdapter = new ArrayAdapter<>(
                    getActivity(),
                    android.R.layout.simple_list_item_multiple_choice,
                    cuentasOrdenAscendente_Result_List
            );

            consultaPorCuentaYFechaEnOtroFragment_XSp.setAdapter(consultaCuentasOrdenAscendente_ArrayAdapter);

            // Todas las transacciones
            A23_QueryResult todasLasTransacciones_Result = a22QueryManager.queryAllTransactions();
            todasLasTransacciones_Result_List = todasLasTransacciones_Result.getDatos();

            adaptadorTodasLasCuentas_TipoCuentas = new D_F4_AdaptadorTransaccionesCierre(
                    getActivity(),
                    todasLasTransacciones_Result_List
            );

            todasLasTransacciones_XLv.setAdapter(adaptadorTodasLasCuentas_TipoCuentas);
        } catch (Exception e) {
            Log.e(TAG, "Error en dynamicQuery: " + e.getMessage(), e);
        }
    }

    private void inicializarHelpers() {
        a22QueryManager = new A22_QueryManager(getActivity());
        metodosVarios_Class = new A99_MetodosVarios(getActivity());
        dateCurrent_ArrayInteger = metodosVarios_Class.fechasYHoras();
        csvDriveUploader = new A6_3_CSVDriveUploader();
        sheetsDownloader = new A6_5_SheetsDownloader(getActivity());

        // Crear bitmaps para botones (si es necesario)
        A99_MetodosVarios llamarA9 = new A99_MetodosVarios(getActivity());
    }
    
    /**
     * Inicializa la base de datos
     */
    private void inicializarBaseDatos() {
        try {
            if (ayudante_Class != null) {
                ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
                db = ayudante_Class.getReadableDatabase();
            } else {
                Log.e(TAG, "a1_ayudanteBD es nulo en onStart()");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar BD: " + e.getMessage(), e);
        }
    }


    private long _7verNumeroDeTransacciones() {
        try {
            A1_1_AyudanteBD ayudanteBD = new A1_1_AyudanteBD(
                    getActivity(),
                    balanceSqlite_String_PSF,
                    null,
                    version1BalanceSqlite_int_PSF
            );
            SQLiteDatabase db = ayudanteBD.getReadableDatabase();
            long numeroRegistros = DatabaseUtils.queryNumEntries(db, "transacciones");
            db.close();
            return numeroRegistros;
        } catch (Exception e) {
            Log.e(TAG, "Error al contar transacciones: " + e.getMessage(), e);
            return 0;
        }
    }

    public void _7borrarHistorialTransacciones(String tabla) {
        try {
            A1_1_AyudanteBD ayudanteBD = new A1_1_AyudanteBD(
                    getActivity(),
                    balanceSqlite_String_PSF,
                    null,
                    version1BalanceSqlite_int_PSF
            );
            SQLiteDatabase db = ayudanteBD.getWritableDatabase();
            A1_2_OperacionesBD.borrarRegistros(tabla, db);
            Log.d(TAG, "Historial borrado de tabla: " + tabla);
        } catch (Exception e) {
            Log.e(TAG, "Error al borrar historial: " + e.getMessage(), e);
        }
    }

    public void _7verTransaccionesAdaptadorColumnas() {
        envolventeEncabezado_XLl.setVisibility(View.VISIBLE);
        dynamicQuery();
        mensajeInformativo_XTv.setText(String.valueOf(todasLasTransacciones_Result_List.size()));
    }

    public void _7_limpiarLista() {
        if (adaptadorTodasLasCuentas_TipoCuentas != null) {
            adaptadorTodasLasCuentas_TipoCuentas.clear();
            adaptadorTodasLasCuentas_TipoCuentas.notifyDataSetChanged();
            todasLasTransacciones_XLv.setAdapter(adaptadorTodasLasCuentas_TipoCuentas);
            Log.d(TAG, "Lista limpiada. Elementos: " + adaptadorTodasLasCuentas_TipoCuentas.getCount());
        }
    }

    public void _9_verTransaccionesPorCuentaConSpinnerEnF2() {
        try {
            String accountToQuery = consultaPorCuentaYFechaEnOtroFragment_XSp.getSelectedItem().toString();
            Fragment fragment = new F3_2_VerItemTransaccion();
            Bundle bundle = new Bundle();
            bundle.putString("keyAccount", accountToQuery);
            fragment.setArguments(bundle);
            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.contenedor_fragments_f0_Xf, fragment)
                    .commit();
        } catch (Exception e) {
            Log.e(TAG, "Error al navegar a transacciones por cuenta: " + e.getMessage(), e);
        }
    }

    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;

        // Usar factory method sin callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }

}