package B_FRAGMENTS;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_ALL_ACCOUNTS;
import static A1BASES.A9_2_BackupFile.CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_SOME_ACCOUNTS;
import android.content.ContentValues;
import android.database.Cursor;
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
import android.widget.EditText;
import A1BASES.A9_2_BackupFile;
import A1BASES.A99_MetodosVarios;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;
import D_ADAPTERS.D_F4_AdaptadorTransaccionesCierre;

/**
 * Fragment mejorado para gestión de cierres contables con sistema de backups rotativos
 * Mantiene automáticamente los últimos 5 backups de cada tipo en Google Drive
 */
public class F4_Cierres extends Fragment {

    private static final String TAG = "F51_CierresPrincipal";
    private static final String FOLDER_ID_DRIVE = "1_45-JDssinDjj7NOykxAh2xHzRNhuwEJ";
    private static final int DELAY_TOAST_CORTO = 4000;
    private static final int DELAY_TOAST_LARGO = 8000;
    private static final int DELAY_SUBIDA_DRIVE = 2500;

    // Views
    private RadioButton _2_opcionAutonomoTransacciones_XRb;
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


    // Data statics
    public static Integer[] dateCurrent_ArrayInteger;
    private F6_Calculadora calculadora_Fragment;
    Button calculadoraLibre_XBt;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /** ⭐ (8-nov) Señal de F1: abrir la opción 6 directo en Ajustes (ver B12_DocumentPersistence). */
    public static final String PREF_ABRIR_AJUSTES_RENTA = "abrirAjustesRentaAlVolver";

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

        // ⭐ NUEVO (8-nov, renta R6, pedido de Jorge): al volver de F1 después de guardar el
        // documento de un ajuste fiscal, se abre directo la pantalla de Ajustes de la opción 6.
        android.content.SharedPreferences prefs =
                requireActivity().getSharedPreferences("MyAppPreferences", android.content.Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_ABRIR_AJUSTES_RENTA, false)) {
            prefs.edit().putBoolean(PREF_ABRIR_AJUSTES_RENTA, false).apply();
            inflarViews_View.post(() ->
                    new A1BASES.A17_ReporteRentaDialogo().mostrarEnAjustes(getActivity(), FOLDER_ID_DRIVE));
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
        _2_opcionAutonomoTransacciones_XRb = view.findViewById(R.id._2_opcionAutonomoTransacciones_XRb);
        // ⭐ NUEVO (3-nov, renta R4, pedido de Jorge): "6. Reporte renta fiscal".
        RadioButton _6_opcionReporteRentaFiscal_XRb = view.findViewById(R.id._6_opcionReporteRentaFiscal_XRb);
        _6_opcionReporteRentaFiscal_XRb.setOnClickListener(v -> {
            _6_opcionReporteRentaFiscal_XRb.setChecked(false);
            new A1BASES.A17_ReporteRentaDialogo().mostrar(getActivity(), FOLDER_ID_DRIVE);
        });
        // (4-nov, Jorge) La antigua opción 7 (depurar saldos contrarios) quedó dentro del
        // recorrido guiado de la opción 6 (A17_ReporteRentaDialogo).
        _3_opcionTransaccionesAlgunasCuentas_XRb = view.findViewById(R.id._3_opcionTransaccionesAlgunasCuentas_XRb);
        opcionSaleDeLaApp_XRb = view.findViewById(R.id.opcionSaleDeLaApp_XRb);

        mensajeInformativo_XTv = view.findViewById(R.id.mensajeInformativo_XTv);
        envolventeEncabezado_XLl = view.findViewById(R.id.envolventeEncabezado_XLl);
        opcionActualCierre_XTv = view.findViewById(R.id.opcionActualCierre_XTv);
        todasLasTransacciones_XLv = view.findViewById(R.id.todasLasTransacciones_XLv);
        consultaPorCuentaYFechaEnOtroFragment_XSp = view.findViewById(R.id.consultaPorCuentaYFechaEnOtroFragment_XSp);

        envolventeEncabezado_XLl.setVisibility(View.INVISIBLE);
    }

    /**
     * Inicializa helpers y managers
     */

    /**
     * Configura todos los listeners de los botones
     */
    private void configurarListeners() {
        _2_configurarListener_OpcionAutonomo();
        _3_configurarListener_OpcionAlgunasCuentas();
        // ⭐ RETIRADAS (23-nov, Jorge, S3): 3.1 Sincronizar Balance desde Sheets y 3.2 Importar
        // Balance desde Sheets (hoja fija compartida + CSV 441). Las reemplazan Respaldos › Enviar
        // a Sheets y Traer de Sheets. El reporte de renta pasa a ser la opción 3.
        configurarListener_SpinnerCuentas();
        configurarListener_SalirApp();
    }
    

    // ==================== LISTENERS ====================

    // ⭐ RETIRADA (21-nov, Jorge): la antigua opción 1 (backup de reserva 411). Los respaldos
    // manuales quedan centralizados en la pantalla Respaldos (copia completa .db).

    // ⭐ CAMBIO (21-nov, Jorge): cada cierre tiene UN diálogo antes (todo lo que hay que decidir)
    // y UNO al final (cerrarOperacion). Se retiró el aviso "Backup local, en Drive y todas las
    // cuentas cerradas" que salía al tocar la opción, antes de confirmar nada.
    private void _2_configurarListener_OpcionAutonomo() {
        _2_opcionAutonomoTransacciones_XRb.setOnClickListener(v -> {
            _7_limpiarLista();
            mensajeInformativo_XTv.setText("0");
            opcionActualCierre_XTv.setText("");
            dynamicQuery();
            if (todasLasTransacciones_Result_List.size() != 0) {
                opcionActualCierre_XTv.setText(_2_opcionAutonomoTransacciones_XRb.getText().toString());
                dialogoCierreTotal();
            } else {
                Toast.makeText(getActivity(), "No hay transacciones para cerrar", Toast.LENGTH_LONG).show();
            }
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
                dialogoCierreAlgunas();
            } else {
                Toast.makeText(getActivity(), "No hay transacciones para cerrar", Toast.LENGTH_LONG).show();
            }
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
    public void _2iniciarCierreAutonomoTransaccionesTodasLasCuentas(int anioFoto) {

        // ⭐ CAMBIO E0 (17-nov): el respaldo previo se hace YA y verificado.
        if (!respaldoPrevioOk("cierre-total")) {
            return;
        }
        // ⭐ CAMBIO (21-nov, Jorge): la foto de renta va DESPUÉS del respaldo completo, así el
        // punto "antes" es de verdad anterior a cualquier cambio. Si la foto falla, no se borra nada.
        String foto = tomarFotoRenta(anioFoto);
        if (foto == null) {
            operacionEnCurso = null;
            return;
        }

        // ⭐ CAMBIO E3 (17-nov): los pasos van en secuencia; ya no hay espera fija de 10 s
        // (los respaldos previos ya quedaron escritos y verificados arriba).
        {

            try {

                // 1. Crear archivo resumen por cuenta
                //String nombreArchivoResumen = "422 Backup saldos de todas las cuentas.csv";
                String nombreArchivoResumen = CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_ALL_ACCOUNTS.getFileName();

                A5_1_BackupManager._2csvConsultaResumenTodasLasCuentasAntesDeCerrarParaTablaTransaccionesDespuesDeCerrar(
                        getActivity(),
                        nombreArchivoResumen
                );
                // ⭐ E0 (17-nov): sin resumen de saldos no se borra nada.
                if (!archivoConContenido(nombreArchivoResumen)) {
                    Toast.makeText(getActivity(), "No se generó el resumen de saldos; no se borró nada.", Toast.LENGTH_LONG).show();
                    return;
                }

                // 2. Borrar historial de transacciones
                _7borrarHistorialTransacciones("transacciones");

                // 3. Reimportar saldos desde CSV
                importarTransaccionesDesdeCSV(nombreArchivoResumen);

                // 4. Subir a Drive (rotación automática), sin espera fija
                A6_3_CSVDriveUploader.guardarYSubirTransacciones(requireContext(),
                        CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_ALL_ACCOUNTS.getFileName(), FOLDER_ID_DRIVE, 5);

                // 5. Actualizar vista
                _7verNumeroDeTransacciones();
                _7verTransaccionesAdaptadorColumnas();

                // 6. ⭐ E3: punto "después" y resumen
                cerrarOperacion("Cierre total",
                        "Saldos iniciales: " + lineasDeDatos(nombreArchivoResumen) + " cuentas (archivo " + nombreArchivoResumen + ")\n\n" + foto);
            } catch (Exception e) {
                Log.e(TAG, "Error en cierre autónomo: " + e.getMessage(), e);
                Toast.makeText(getActivity(), "Error en el proceso de cierre", Toast.LENGTH_SHORT).show();
            }

        }

    }

    // ==================== DIÁLOGOS DE CIERRE (⭐ 21-nov, Jorge) ====================
    // Reemplazan la cadena anterior: último cierre → advertencia → foto → reemplazo de foto →
    // foto tomada (y al final diagnóstico + resumen). Ahora: uno antes, uno al final.

    /** Línea con la fecha del último cierre del mismo tipo (control de cierres, 18-nov). */
    private String lineaUltimoCierre(String operacion, String nombre) {
        String ultimo = A1BASES.A32_Respaldos.ultimoResumen(operacion);
        if (ultimo == null) return "Sin " + nombre + " anterior registrado.\n";
        for (String l : ultimo.split("\n")) {
            if (l.startsWith("Fecha: ")) return "Último " + nombre + ": " + l.substring(7) + "\n";
        }
        return "";
    }

    private static String miles(long n) {
        return String.format(new java.util.Locale("es", "CO"), "%,d", n);
    }

    private static int anioValido(String texto) {
        try {
            String t = texto.trim();
            if (t.length() != 4) return -1;
            int a = Integer.parseInt(t);
            return a >= 1990 && a <= 2100 ? a : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** Período del año fiscal y aviso si la foto de ese año ya existe. */
    private String textoAnioFoto(String texto) {
        int anio = anioValido(texto);
        if (anio < 0) return "Escribe un año válido (ej.: 2026).";
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getReadableDatabase();
        try {
            int[] limites = A1_1_AyudanteBD.limitesAnioFiscal(db, anio);
            boolean existe = A1_1_AyudanteBD.existeFotoRenta(db, anio);
            return "Período: " + fechaLegible(limites[0]) + " a " + fechaLegible(limites[1])
                    + (existe ? "\n⚠ La foto de " + anio + " ya existe y se reemplazará." : "");
        } catch (Exception e) {
            return "";
        } finally {
            db.close();
        }
    }

    /**
     * Cierre total: un solo diálogo con el último cierre, las transacciones de hoy, el año de la
     * foto de renta (editable; el cierre total es también el cierre del período fiscal, 3-nov) y
     * los pasos. No hay "cerrar sin foto" (3-nov, Jorge).
     */
    private void dialogoCierreTotal() {
        int propuesto;
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getReadableDatabase();
        try {
            propuesto = A1_1_AyudanteBD.anioFiscalPropuestoParaFoto(db);
        } finally {
            db.close();
        }
        int pad = (int) (22 * getResources().getDisplayMetrics().density);
        LinearLayout caja = new LinearLayout(getActivity());
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setPadding(pad, pad / 2, pad, 0);
        TextView info = new TextView(getActivity());
        info.setTextSize(15);
        info.setText(lineaUltimoCierre("cierre-total", "cierre total")
                + "Hoy: " + miles(_7verNumeroDeTransacciones()) + " transacciones.\n\nAño fiscal de la foto de renta:");
        EditText anio_XEt = new EditText(getActivity());
        anio_XEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        anio_XEt.setText(String.valueOf(propuesto));
        TextView periodo = new TextView(getActivity());
        periodo.setTextSize(14);
        TextView pasos = new TextView(getActivity());
        pasos.setTextSize(14);
        pasos.setText("\nSe hará: respaldo completo → foto de renta → borrar las transacciones → saldos iniciales por cuenta.");
        caja.addView(info);
        caja.addView(anio_XEt);
        caja.addView(periodo);
        caja.addView(pasos);
        Runnable revisarAnio = () -> periodo.setText(textoAnioFoto(anio_XEt.getText().toString()));
        revisarAnio.run();
        anio_XEt.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence t, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence t, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable t) { revisarAnio.run(); }
        });
        AlertDialog dialogo = new AlertDialog.Builder(getActivity())
                .setTitle("Cierre total")
                .setView(caja)
                .setCancelable(false)
                .setPositiveButton("Cerrar", null)
                .setNegativeButton("Cancelar", null)
                .create();
        dialogo.setOnShowListener(x -> dialogo.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b -> {
            int anio = anioValido(anio_XEt.getText().toString());
            if (anio < 0) {
                anio_XEt.setError("Año no válido");
                return;
            }
            dialogo.dismiss();
            _2iniciarCierreAutonomoTransaccionesTodasLasCuentas(anio);
        }));
        dialogo.show();
    }

    /**
     * Toma la foto anual de renta y la respalda (local + Drive). Devuelve el texto para el
     * resumen final, o null si falló (con su aviso; el cierre no sigue).
     */
    private String tomarFotoRenta(int anio) {
        StringBuilder resumen = new StringBuilder();
        int cuentas;
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getWritableDatabase();
        try {
            cuentas = A1_1_AyudanteBD.tomarFotoRentaAnual(db, anio);
            java.text.DecimalFormat formato = new java.text.DecimalFormat("#,##0");
            for (Object[] r : A1_1_AyudanteBD.resumenFotoRentaPorRenglon(db, anio)) {
                resumen.append(r[0]).append(" ").append(r[1]).append(": $ ")
                        .append(formato.format((Long) r[2]))
                        .append((Integer) r[3] > 0 ? "  (" + r[3] + " cuentas)" : "").append("\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "No se pudo tomar la foto de renta: " + e.getMessage(), e);
            new AlertDialog.Builder(getActivity())
                    .setTitle("No se tomó la foto")
                    .setMessage("Error: " + e.getMessage() + "\n\nEl cierre no se realizó: no se borró nada.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return null;
        } finally {
            db.close();
        }
        // Al cierre la foto aún no tiene depuración: el nombre lo dice.
        String nombreFoto = A9_2_BackupFile.CSV_FOTO_RENTA_ANUAL.getBaseNameConEquipo() + "_sin_depurar"
                + A9_2_BackupFile.CSV_FOTO_RENTA_ANUAL.getFileType().getExtension();
        if (A5_1_BackupManager.backupFotoRentaCSV(getActivity(), nombreFoto)) {
            A6_3_CSVDriveUploader.subirArchivoCuentasExistenteADrive(requireContext(), nombreFoto, FOLDER_ID_DRIVE);
        }
        return "Foto de renta " + anio + ": " + cuentas + " cuentas con saldo o movimiento\n"
                + resumen.toString().trim()
                + "\n(Los saldos contrarios se depuran en la opción 4, Reporte renta fiscal.)";
    }

    private static String fechaLegible(int aaaammdd) {
        return (aaaammdd % 100) + "/" + ((aaaammdd / 100) % 100) + "/" + (aaaammdd / 10000);
    }

    /** Cierre de algunas cuentas: un solo diálogo antes. */
    // ⭐ NUEVO (23-nov, Jorge): cotejo del cierre de algunas cuentas. El saldo de cada cuenta
    // (suma de sus valores) debe ser el mismo antes y después: lo borrado queda como saldo inicial.
    private java.util.Map<Long, Long> saldosPorCuenta() {
        java.util.Map<Long, Long> m = new java.util.HashMap<>();
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(requireContext(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getReadableDatabase();
        try (Cursor c = db.rawQuery("SELECT IFNULL(cuenta_id, -1), IFNULL(SUM(c5_Valor), 0) FROM transacciones GROUP BY cuenta_id", null)) {
            while (c.moveToNext()) m.put(c.getLong(0), c.getLong(1));
        } finally {
            db.close();
        }
        return m;
    }

    private String cotejoDeSaldos(java.util.Map<Long, Long> antes, java.util.Map<Long, Long> despues) {
        java.util.Set<Long> cuentas = new java.util.TreeSet<>(antes.keySet());
        cuentas.addAll(despues.keySet());
        StringBuilder dif = new StringBuilder();
        int distintas = 0;
        for (Long id : cuentas) {
            long a = antes.containsKey(id) ? antes.get(id) : 0L;
            long d = despues.containsKey(id) ? despues.get(id) : 0L;
            if (a == d) continue;
            distintas++;
            if (distintas <= 10) dif.append("\n  cuenta ").append(id).append(": ").append(miles(a)).append(" → ").append(miles(d));
        }
        if (distintas == 0) return "✓ Saldos cotejados: las " + cuentas.size() + " cuentas tienen el mismo saldo antes y después.";
        return "⚠ " + distintas + " cuenta(s) cambiaron de saldo (antes → después):" + dif
                + (distintas > 10 ? "\n  …" : "") + "\nRevisa antes de seguir; el respaldo previo está en Respaldos.";
    }

    private void dialogoCierreAlgunas() {
        new AlertDialog.Builder(getActivity())
                .setTitle("Cierre de algunas cuentas")
                .setMessage(lineaUltimoCierre("cierre-algunas", "cierre de algunas cuentas")
                        + "Hoy: " + miles(_7verNumeroDeTransaccionesCerrable()) + " transacciones de cuentas cerrables.\n\n"
                        + "Se hará: respaldo completo → borrar esas transacciones → saldos iniciales por cuenta.")
                .setCancelable(false)
                .setPositiveButton("Cerrar", (d, w) -> {
                    _3iniciarCierreAutonomoTransaccionesAlgunasCuentas();
                    _7verNumeroDeTransacciones();
                    _7verTransaccionesAdaptadorColumnas();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    public void _3iniciarCierreAutonomoTransaccionesAlgunasCuentas() {

        // ⭐ CAMBIO E0 (17-nov): el 431 se hace YA y verificado (antes, con retardo y sin revisar).
        if (!respaldoPrevioOk("cierre-algunas")) {
            return;
        }

        // ⭐ CAMBIO E3 (17-nov): pasos en secuencia, sin espera fija de 10 s.
        {

            try {
                // 1. Crear archivo resumen de algunas cuentas
                //String nombreArchivoResumen = "432 Backup saldos de algunas cuentas.csv";
                String nombreArchivoResumen = CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_SOME_ACCOUNTS.getFileName();

                A5_1_BackupManager._3csvConsultaResumenAlgunasCuentasAntesDeCerrarParaTablaTransaccionesDespuesDeCerrar(
                        getActivity(),
                        nombreArchivoResumen
                );
                // ⭐ E0 (17-nov): sin resumen de saldos no se borra nada.
                if (!archivoConContenido(nombreArchivoResumen)) {
                    Toast.makeText(getActivity(), "No se generó el resumen de saldos; no se borró nada.", Toast.LENGTH_LONG).show();
                    return;
                }

                // ⭐ (23-nov, Jorge): saldo de cada cuenta antes, para cotejarlo con el de después.
                java.util.Map<Long, Long> saldosAntes = saldosPorCuenta();

                // 2. Borrar transacciones de algunas cuentas
                A1_2_OperacionesBD operacionesBD_class = new A1_2_OperacionesBD(getActivity());
                operacionesBD_class.eliminarTransaccionesAlgunasCuentas();

                // 3. Reimportar saldos iniciales de algunas cuentas
                importarTransaccionesDesdeCSV(nombreArchivoResumen);

                // 4. Subir a Drive (rotación automática), sin espera fija
                A6_3_CSVDriveUploader.guardarYSubirTransacciones(requireContext(),
                        CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_SOME_ACCOUNTS.getFileName(), FOLDER_ID_DRIVE, 5);

                // 5. Actualizar vista
                _7verNumeroDeTransacciones();
                _7verTransaccionesAdaptadorColumnas();

                // 6. ⭐ E3: punto "después" y resumen
                cerrarOperacion("Cierre de algunas cuentas",
                        "Saldos trasladados: " + lineasDeDatos(nombreArchivoResumen) + " cuentas (archivo " + nombreArchivoResumen + ")"
                                + "\n" + cotejoDeSaldos(saldosAntes, saldosPorCuenta()));
            } catch (Exception e) {
                Log.e(TAG, "Error en cierre algunas cuentas: " + e.getMessage(), e);
                Toast.makeText(getActivity(), "Error en el proceso de cierre", Toast.LENGTH_SHORT).show();
            }

        }

    }

    // ==================== MÉTODOS DE RESTAURACIÓN ====================

    // ⭐ RETIRADA (21-nov, Jorge): la antigua opción 5 (restaurar transacciones desde un CSV 411)
    // salió: se restaura siempre la copia completa .db desde la pantalla Respaldos.

    // ==================== MÉTODOS AUXILIARES ====================

      /**
     * Importa transacciones desde un archivo CSV
     * Busca en carpeta de app y en carpeta tradicional
     */
    // ⭐ CAMBIO E0 respaldos (17-nov, Jorge): por NOMBRE se lee solo la carpeta "Balance".
    // Antes se prefería la carpeta interna de la app, donde quedan las descargas de Drive, y
    // una copia vieja descargada podía tapar el respaldo local más nuevo del mismo nombre.
    private void importarTransaccionesDesdeCSV(String nombreArchivo) {
        Log.d(TAG, "=== IMPORTANDO CSV ===");
        Log.d(TAG, "Archivo solicitado: " + nombreArchivo);
        File archivo = archivoLocalBalance(nombreArchivo);
        if (!archivo.exists()) {
            Log.e(TAG, "Archivo no encontrado: " + archivo.getAbsolutePath());
            Importacion imp = new Importacion();
            imp.conProblemas = true;
            imp.diagnostico = "Archivo no encontrado: " + archivo.getAbsolutePath();
            ultimaImportacion = imp;
            return;
        }
        importarTransaccionesDesdeArchivo(archivo);
    }

    // ⭐ NUEVO E0 (17-nov): importa EXACTAMENTE el archivo indicado (p. ej. el recién descargado
    // de Drive), sin buscarlo por nombre en otras carpetas.
    private void importarTransaccionesDesdeArchivo(File archivoAUsar) {
        String rutaFinal = archivoAUsar.getAbsolutePath();
        Log.d(TAG, "Importando desde: " + rutaFinal);

        Log.d(TAG, "Tamaño archivo: " + archivoAUsar.length() + " bytes");

        // Importar desde el archivo encontrado
        int lineasImportadas = 0;
        // ⭐ NUEVO — diagnóstico (27-sep) para el bug de "faltan transacciones" al restaurar
        // desde Google Drive. No cambia ningún comportamiento existente: solo cuenta, en base
        // al valor de retorno de insertarTransaccion() (ya existía, antes se ignoraba), cuántas
        // líneas terminaron insertadas de verdad contra cuántas fueron encabezado o fallaron.
        int insertadasOk = 0;
        int fallidasDuplicado = 0;
        int encabezados = 0;
        int otrosErrores = 0;
        primerErrorInsercion = null;
        reubicadasItemRepetido = 0;
        ejemplosReubicadas.setLength(0);
        detalleFallidas.setLength(0);
        columnasCsv = null; // ⭐ (1-nov) el encabezado de ESTE archivo define las columnas
        try (FileReader fileReader = new FileReader(rutaFinal);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            // ⭐ CAMBIO (9-nov, Jorge): UNA conexión y UNA transacción para todo el archivo. Antes
            // se abría y cerraba la base por cada fila (con ~7.700 filas tardaba minutos y parecía
            // colgada). Las filas que fallan se siguen omitiendo una a una, como antes.
            A1_1_AyudanteBD ayudanteImp = new A1_1_AyudanteBD(getActivity(),
                    balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
            SQLiteDatabase dbImp = ayudanteImp.getWritableDatabase();
            dbImp.beginTransaction();
            try {
            String linea;
            while ((linea = bufferedReader.readLine()) != null) {
                String[] datos = linea.split(",");
                long resultado = insertarTransaccion(dbImp, datos);
                lineasImportadas++;
                if (resultado == RESULTADO_ENCABEZADO) {
                    encabezados++;
                } else if (resultado == RESULTADO_ERROR) {
                    otrosErrores++;
                } else if (resultado == -1L) {
                    // db.insert() devolvió -1: la fila no se pudo insertar (típicamente,
                    // choque con la restricción UNIQUE de (c1_Documento, c2_ItemDoc))
                    fallidasDuplicado++;
                } else {
                    insertadasOk++;
                }
            }
            dbImp.setTransactionSuccessful();
            } finally {
                dbImp.endTransaction();
                dbImp.close();
            }

            // ⭐ NUEVO — fix Informe (31-oct): las líneas del CSV que no traen cuenta_id (p.ej.
            // los saldos iniciales que generan los cierres) entran con cuenta_id NULL. Antes,
            // en el dispositivo original, ya habían sido reemparejadas por nombre en alguna
            // migración; en una base nueva nadie lo hacía, y el Informe (que agrupa por
            // cuenta_id) juntaba todas esas transacciones en UNA sola fila sin tipo de cuenta.
            // Se reemparejan aquí por nombre (c3_Cuenta = cuentas.Cuenta), con el mismo método
            // que ya usa F2_Cuentas tras restaurar cuentas. Solo toca filas con cuenta_id NULL:
            // repetirlo no cambia nada de lo que ya estaba bien.
            long sinCuentaIdAntes = -1;
            int huerfanasDespues = -1;
            try {
                A1_1_AyudanteBD ayudanteRep = new A1_1_AyudanteBD(getActivity(),
                        balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
                SQLiteDatabase dbRep = ayudanteRep.getWritableDatabase();
                sinCuentaIdAntes = DatabaseUtils.queryNumEntries(dbRep, "transacciones",
                        "cuenta_id IS NULL");
                huerfanasDespues = A1_2_OperacionesBD.repararReferenciasCuentaIdPorNombre(dbRep);
                // ⭐ NUEVO (31-oct): la "foto" de tipo_cuenta_id que traen las transacciones del
                // CSV puede ser del catálogo provisional (backups antiguos); se alinea con el
                // tipo actual de su cuenta.
                A1_1_AyudanteBD.alinearTipoCuentaDeTransacciones(dbRep);
                dbRep.close();
            } catch (Exception eRep) {
                Log.e(TAG, "No se pudo reemparejar cuenta_id por nombre: " + eRep.getMessage(), eRep);
            }

            Log.d(TAG, "=== IMPORTACIÓN COMPLETADA ===");
            Log.d(TAG, "Líneas leídas: " + lineasImportadas);
            Log.d(TAG, "Insertadas correctamente: " + insertadasOk);
            Log.d(TAG, "Encabezados detectados: " + encabezados);
            Log.d(TAG, "Fallidas (posible duplicado): " + fallidasDuplicado);
            Log.d(TAG, "Otros errores: " + otrosErrores);
            Log.d(TAG, "Desde: " + rutaFinal);

            // ⭐ E0 (17-nov): origen según la carpeta real del archivo importado.
            String origen = rutaFinal.contains("/Android/data/") ? "Descarga de Drive (carpeta de la app)" : "Carpeta Balance";
            String resumen = "Diagnóstico de importación de transacciones\n\n"
                    + "Archivo: " + archivoAUsar.getName() + "\n"
                    + "Origen: " + origen + "\n"
                    + "Ruta: " + rutaFinal + "\n\n"
                    + "Líneas leídas: " + lineasImportadas + "\n"
                    + "Insertadas correctamente: " + insertadasOk + "\n"
                    + "Encabezados (no cuentan): " + encabezados + "\n"
                    + "Fallidas al insertar: " + fallidasDuplicado + detalleFallidas + "\n"
                    + "Con Documento+Item repetido, insertadas con ítem nuevo: " + reubicadasItemRepetido
                    + ejemplosReubicadas + "\n"
                    + "Otros errores: " + otrosErrores + "\n\n"
                    + "Transacciones sin cuenta asociada antes de reemparejar: " + sinCuentaIdAntes + "\n"
                    + "Sin cuenta después de reemparejar por nombre: " + huerfanasDespues + "\n\n"
                    // ⭐ NUEVO — diagnóstico (30-oct): mensaje real de SQLite del primer fallo.
                    + "Primer error SQLite: "
                    + (primerErrorInsercion != null ? primerErrorInsercion : "ninguno");
            // ⭐ CAMBIO (21-nov, Jorge): el diagnóstico ya no es un diálogo aparte; va dentro del
            // resumen final (cerrarOperacion), completo solo si hubo fallas.
            Importacion imp = new Importacion();
            imp.insertadas = insertadasOk;
            imp.fallidas = fallidasDuplicado + otrosErrores;
            imp.reubicadas = reubicadasItemRepetido;
            imp.conProblemas = fallidasDuplicado > 0 || otrosErrores > 0 || huerfanasDespues > 0;
            imp.diagnostico = resumen;
            ultimaImportacion = imp;

        } catch (Exception e) {
            Log.e(TAG, "Error al importar CSV: " + e.getMessage(), e);
            Importacion imp = new Importacion();
            imp.conProblemas = true;
            imp.diagnostico = "Error al importar " + archivoAUsar.getName() + ": " + e.getMessage();
            ultimaImportacion = imp;
        }
    }

    // ⭐ NUEVO (21-nov, Jorge): resultado de la última importación, para el resumen final.
    private static class Importacion {
        int insertadas;
        int fallidas;
        int reubicadas;
        boolean conProblemas;
        String diagnostico = "";
    }

    private Importacion ultimaImportacion;

    /**
     * ⭐ NUEVO (12-nov, pendiente del checklist, opción A elegida por Jorge): la restauración de
     * cuentas y transacciones no trae la renta (respaldos 423 fotos, 425 ajustes, 426 soportes).
     * Sin la foto anual, F4 › 6 oculta Ajustes y Checklist, y parece que se hubiera perdido lo
     * construido. Si después de restaurar la base no tiene ninguna foto de renta, se avisa cómo
     * traerla; ⭐ (12-nov, opción B) con un botón que la restaura (A24_RestauradorRenta).
     */
    private void avisarSiFaltaRestaurarRenta() {
        if (getActivity() == null) return;
        boolean sinFotos;
        A1_1_AyudanteBD ayudante = new A1_1_AyudanteBD(getActivity(), "balance.db", null,
                A1_1_AyudanteBD.version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudante.getReadableDatabase();
        try {
            sinFotos = A1_1_AyudanteBD.aniosConFotoRenta(db).isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "No se pudo revisar las fotos de renta: " + e.getMessage());
            return;
        } finally {
            db.close();
        }
        if (!sinFotos) return;
        // ⭐ CAMBIO (12-nov, opción B): además del camino manual, un botón restaura la renta sola
        // (Drive y, si falla, la carpeta Balance del equipo — A24_RestauradorRenta).
        new AlertDialog.Builder(getActivity())
                .setTitle("Falta restaurar la renta")
                .setView(A1BASES.A20_Informacion.vista(getActivity(),
                        "Las cuentas y las transacciones ya quedaron. Si llevas la renta en la app, "
                                + "falta traer sus respaldos (fotos 423, ajustes 425 y soportes 426).\n\n"
                                + "\"Restaurar renta\" toma los más recientes de Drive y, si no hay "
                                + "conexión, los de la carpeta Balance del equipo.\n\n"
                                + "Si nunca hiciste un cierre con foto, no necesitas hacer nada."))
                .setPositiveButton("Restaurar renta", (d, w) -> new A1BASES.A24_RestauradorRenta(
                        getActivity(), FOLDER_ID_DRIVE, null).restaurar())
                .setNegativeButton("Ahora no", null)
                .show();
    }

    // ⭐ NUEVO — códigos de resultado de insertarTransaccion(), solo para el diagnóstico de
    // arriba. No afectan el comportamiento de inserción, que sigue siendo exactamente el mismo.
    private static final long RESULTADO_ENCABEZADO = -2L;
    private static final long RESULTADO_ERROR = -3L;
    // ⭐ NUEVO — diagnóstico (30-oct): primer mensaje de error de SQLite al insertar.
    private String primerErrorInsercion = null;
    /** ⭐ (9-nov) Filas con Documento+Item repetido que entraron con un ítem nuevo. */
    private int reubicadasItemRepetido = 0;
    private final StringBuilder ejemplosReubicadas = new StringBuilder();
    /** ⭐ (9-nov) Detalle de las filas que no se pudieron insertar. */
    private final StringBuilder detalleFallidas = new StringBuilder();

    // ⭐ NUEVO (1-nov, paso 4 / cierre, con Jorge): formato nuevo del CSV de transacciones, sin
    // las columnas retiradas de la tabla (c3, c4, c10, c11, c12, c13). Para aceptar CSV viejos y
    // nuevos, las columnas se ubican POR NOMBRE según la fila de encabezado del archivo. Si el
    // archivo no trae encabezado (CSV muy antiguos), se usan las posiciones fijas de siempre.
    private java.util.Map<String, Integer> columnasCsv = null;

    /** Posición de la columna en el CSV: por encabezado si lo hubo; si no, la posición fija antigua. */
    private int indiceCsv(String nombreColumna, int posicionAntigua) {
        if (columnasCsv == null) return posicionAntigua;
        Integer i = columnasCsv.get(nombreColumna.toLowerCase(java.util.Locale.ROOT));
        return i == null ? -1 : i;
    }

    /** Valor de la columna, o null si no existe en este CSV / la línea es más corta. */
    private static String campoCsv(String[] datos, int indice) {
        if (indice < 0 || indice >= datos.length) return null;
        return datos[indice];
    }

    /**
     * Inserta una transacción en la base de datos.
     * Devuelve el resultado de db.insert() (id de la fila, o -1 si falló la inserción —
     * por ejemplo por choque con la restricción UNIQUE de (c1_Documento, c2_ItemDoc)),
     * o uno de los códigos RESULTADO_* de arriba para los casos especiales. Este valor de
     * retorno es nuevo (antes el método era void); no cambia qué se inserta ni cómo.
     */
    private long insertarTransaccion(SQLiteDatabase db, String[] datos) {
        if (datos == null || datos.length == 0) {
            Log.e(TAG, "Datos insuficientes para insertar transacción");
            return RESULTADO_ERROR;
        }

        // ⭐ NUEVO — a pedido de Jorge (27-sep): los CSV de transacciones ahora traen una fila
        // de encabezado (ver A5_1_BackupManager: guardarTodasLasTransancionsAUnArchivoCSV y los
        // 2 generadores de saldo inicial). Se detecta por contenido: el primer campo de un
        // encabezado es siempre el literal "c1_Documento"; el de una fila real siempre es un
        // número de documento real o el "0000" fijo de saldo inicial — nunca ese texto. Cubre
        // los 6 flujos que llaman a este método con un solo cambio.
        if (datos[0] != null && datos[0].trim().equalsIgnoreCase("c1_Documento")) {
            Log.d(TAG, "Fila de encabezado detectada en el CSV de transacciones, se ignora sin insertar");
            // ⭐ NUEVO (1-nov): el encabezado define dónde está cada columna (formato viejo o nuevo).
            columnasCsv = new java.util.HashMap<>();
            for (int i = 0; i < datos.length; i++) {
                if (datos[i] != null) {
                    columnasCsv.put(datos[i].trim().toLowerCase(java.util.Locale.ROOT), i);
                }
            }
            return RESULTADO_ENCABEZADO;
        }

        // ⭐ NUEVO (1-nov): posiciones de cada columna en este CSV (ver indiceCsv). Sin
        // encabezado se exigen las 13 columnas de siempre, como antes.
        if (columnasCsv == null && datos.length < 13) {
            Log.e(TAG, "Datos insuficientes para insertar transacción");
            return RESULTADO_ERROR;
        }
        String csvDocumento   = campoCsv(datos, indiceCsv("c1_Documento", 0));
        String csvItemDoc     = campoCsv(datos, indiceCsv("c2_ItemDoc", 1));
        String csvNombreCta   = campoCsv(datos, indiceCsv("c3_Cuenta", 2)); // solo CSV viejos
        String csvValor       = campoCsv(datos, indiceCsv("c5_Valor", 4));
        String csvDescripcion = campoCsv(datos, indiceCsv("c6_Descripcion", 5));
        String csvFechaYHora  = campoCsv(datos, indiceCsv("c7_FechaYHora", 6));
        String csvFechaIni    = campoCsv(datos, indiceCsv("c8_FechaInicial", 7));
        String csvFechaMod    = campoCsv(datos, indiceCsv("c9_FechaModificacion", 8));
        String csvCuentaId    = campoCsv(datos, indiceCsv("cuenta_id", 13));
        String csvTipoCtaId   = campoCsv(datos, indiceCsv("tipo_cuenta_id", 14));
        String csvTransId     = campoCsv(datos, indiceCsv("transaccion_id", 15));
        if (csvDocumento == null || csvItemDoc == null || csvValor == null) {
            Log.e(TAG, "Línea sin Documento/Item/Valor, se omite");
            return RESULTADO_ERROR;
        }

        try {
            // ⭐ CAMBIO (9-nov): la conexión la abre y cierra quien llama (una sola por archivo).
            ContentValues valores = new ContentValues();
            valores.put("c1_Documento", csvDocumento);
            valores.put("c2_ItemDoc", csvItemDoc);
            // (1-nov, BD v22) c3_Cuenta retirada: el nombre del CSV (columna 3) solo se usa para
            // obtener cuenta_id cuando la línea no lo trae (ver más abajo).
            // (1-nov, BD v21) c4_Signo retirada: el signo sale del valor; la columna 4 del CSV se ignora.
            valores.put("c5_Valor", csvValor);
            valores.put("c6_Descripcion", csvDescripcion);
            valores.put("c7_FechaYhora", csvFechaYHora);
            valores.put("c8_FechaInicial", csvFechaIni);
            valores.put("c9_FechaModificacion", csvFechaMod);
            // ⭐ CAMBIO — v11 tanda 3 (parte D): "transacciones" pierde c10_Grupo1/c11_Grupo2
            // (ver A1_1_AyudanteBD, migración v13) — se quitan estos 2 .put(). datos[9]/datos[10]
            // (las columnas Grupo1/Grupo2 del CSV, que se mantienen en blanco para siempre — ver
            // A5_1_BackupManager) ya no se leen aquí; el resto de posiciones del array
            // (datos[11] en adelante) no se ve afectado.
            // (31-oct, BD v20) c12 retirada: "Cerrable" se toma de la cuenta; la columna 12 del CSV se ignora.
            // (31-oct, BD v19) c13_ColumnaDisponible retirada: la columna 13 del CSV se ignora.

            // ⭐ NUEVO — Fase 4 (parte C): cuenta_id, si el CSV lo trae (backup nuevo de
            // A5_1_BackupManager.guardarTodasLasTransancionsAUnArchivoCSV, columna 14).
            // Compatible con CSVs viejos de 13 columnas: si no viene, se deja sin poner —
            // igual que hoy, queda NULL y puede repararse después por c3_Cuenta (ver backfills
            // en A1_1_AyudanteBD). Los resúmenes de saldo inicial (_2 y _3 en A5_1_BackupManager)
            // tampoco escriben esta columna todavía, así que sus líneas seguirán entrando aquí
            // sin cuenta_id, como siempre.
            if (csvCuentaId != null && !csvCuentaId.trim().isEmpty()) {
                try {
                    valores.put("cuenta_id", Long.parseLong(csvCuentaId.trim()));
                } catch (NumberFormatException nfe) {
                    Log.e(TAG, "cuenta_id inválido en CSV, se omite: " + csvCuentaId);
                }
            }
            // ⭐ NUEVO (1-nov, paso 4 / Fase E1): si la línea no trae cuenta_id (saldos iniciales
            // de cierres, CSV antiguos, Sheets), el NOMBRE de la cuenta del CSV se convierte en
            // cuenta_id aquí mismo, al importar — ya no se deja para "repararlo después" desde
            // transacciones.c3_Cuenta. Si el nombre no coincide con ninguna cuenta, la línea
            // queda sin cuenta y se cuenta en el diagnóstico.
            // (1-nov) Solo aplica a CSV del formato viejo: el nuevo no trae nombre de cuenta.
            if (!valores.containsKey("cuenta_id") && csvNombreCta != null && !csvNombreCta.trim().isEmpty()) {
                Cursor cCuenta = db.rawQuery("SELECT cuenta_id FROM cuentas WHERE Cuenta = ?",
                        new String[]{csvNombreCta.trim()});
                if (cCuenta.moveToFirst()) {
                    valores.put("cuenta_id", cCuenta.getLong(0));
                }
                cCuenta.close();
            }

            // ⭐ NUEVO v11 — a pedido de Jorge (28-sep): tipo_cuenta_id (columna 15), si el CSV
            // lo trae. Mismo criterio que cuenta_id arriba: compatible con CSVs de 13/14
            // columnas (no viene, queda sin poner, NULL). Los 3 generadores de
            // A5_1_BackupManager ya la escriben desde esta misma versión.
            if (csvTipoCtaId != null && !csvTipoCtaId.trim().isEmpty()) {
                try {
                    valores.put("tipo_cuenta_id", Long.parseLong(csvTipoCtaId.trim()));
                } catch (NumberFormatException nfe) {
                    Log.e(TAG, "tipo_cuenta_id inválido en CSV, se omite: " + csvTipoCtaId);
                }
            }

            // ⭐ NUEVO v11 (28-sep, segunda ronda) — a pedido de Jorge: transaccion_id (columna
            // 16), solo presente en el backup completo (A5_1_BackupManager.
            // guardarTodasLasTransancionsAUnArchivoCSV — los 2 resúmenes de cierre nunca la
            // traen, son filas nuevas de saldo inicial). Si viene, se reusa el id ORIGINAL en vez
            // de dejar que AUTOINCREMENT genere uno nuevo — mantiene transaccion_id estable entre
            // backup y restauración (necesario si más adelante "transacciones_inventario" lo
            // referencia como FK). Seguro: los 6 flujos que restauran este backup completo
            // siempre vacían la tabla antes (ver _7borrarHistorialTransacciones), así que no hay
            // riesgo de choque con una fila existente. Si no viene (CSV de 13/14/15 columnas, o
            // los resúmenes de cierre), se omite y se sigue autogenerando como hasta ahora.
            if (csvTransId != null && !csvTransId.trim().isEmpty()) {
                try {
                    valores.put("transaccion_id", Long.parseLong(csvTransId.trim()));
                } catch (NumberFormatException nfe) {
                    Log.e(TAG, "transaccion_id inválido en CSV, se omite (se autogenerará uno nuevo): " + csvTransId);
                }
            }

            // ⭐ CAMBIO — diagnóstico (30-oct): insertOrThrow en vez de insert, SOLO para
            // capturar el mensaje real de SQLite (db.insert() lo traga y devuelve -1). El
            // comportamiento no cambia: si falla, la fila se omite y se devuelve -1 igual que antes.
            long idInsertado;
            try {
                idInsertado = db.insertOrThrow("transacciones", null, valores);
            } catch (android.database.SQLException sqle) {
                idInsertado = -1L;
                // ⭐ NUEVO (9-nov, Jorge): bases antiguas (v2) permitían repetir Documento+Item
                // (p. ej. los saldos "0/0" de cierres parciales viejos y los documentos 1124 y
                // 2648). Esas filas SÍ suman en los saldos del teléfono, así que no se pierden:
                // se insertan con el siguiente ítem libre de su documento y se cuentan aparte.
                String msg = sqle.getMessage() == null ? "" : sqle.getMessage();
                if (msg.contains("UNIQUE") && !valores.containsKey("transaccion_id")) {
                    try {
                        long siguiente = DatabaseUtils.longForQuery(db,
                                "SELECT COALESCE(MAX(CAST(c2_ItemDoc AS INTEGER)), 0) + 1 FROM transacciones " +
                                        "WHERE c1_Documento = ?", new String[]{csvDocumento});
                        valores.put("c2_ItemDoc", String.valueOf(siguiente));
                        idInsertado = db.insertOrThrow("transacciones", null, valores);
                        reubicadasItemRepetido++;
                        if (ejemplosReubicadas.length() < 300) {
                            ejemplosReubicadas.append("\n• Doc ").append(csvDocumento).append(" ítem ")
                                    .append(csvItemDoc).append(" → ").append(siguiente);
                        }
                    } catch (android.database.SQLException sqle2) {
                        idInsertado = -1L;
                        msg = sqle2.getMessage() == null ? msg : sqle2.getMessage();
                    }
                }
                if (idInsertado == -1L && primerErrorInsercion == null) {
                    primerErrorInsercion = msg;
                }
            }
            if (idInsertado == -1L) {
                // ⭐ NUEVO (9-nov, Jorge): detalle de cada fila que no entró, para el diagnóstico.
                if (detalleFallidas.length() < 600) {
                    detalleFallidas.append("\n• Doc ").append(csvDocumento).append(" ítem ").append(csvItemDoc)
                            .append(" · ").append(csvNombreCta != null ? csvNombreCta.trim() : "(sin nombre)")
                            .append(" · valor ").append(csvValor)
                            .append(" · cuenta_id ").append(valores.containsKey("cuenta_id")
                                    ? valores.getAsLong("cuenta_id") : "(ninguno)")
                            .append(" · fecha ").append(csvFechaIni);
                }
                Log.e(TAG, "db.insert() devolvió -1 para Documento=" + csvDocumento + " Item=" + csvItemDoc
                        + " (posible choque con la restricción UNIQUE de Documento+Item)");
            }
            return idInsertado;
        } catch (Exception e) {
            Log.e(TAG, "Error al insertar transacción: " + e.getMessage(), e);
            return RESULTADO_ERROR;
        }
    }

    /**
     * Sube múltiples archivos a Drive con delay
     * Incluye rotación automática (mantiene últimos 5)
     */
    // ⭐ NUEVO E0 respaldos (17-nov, Jorge): respaldo previo OBLIGATORIO antes de borrar
    // transacciones. Si no queda escrito y verificado, se avisa y el proceso no sigue.
    // ⭐ CAMBIO (21-nov, Jorge, paso 3 respaldos): el respaldo previo es SOLO el punto de
    // restauración completo .db ("antes-<operación>"); se retiraron los CSV 421, 431 y 442_1.
    // Si no queda escrito y verificado, se avisa y el proceso no sigue.
    private A1BASES.A32_Respaldos.Resultado puntoAntes;
    private String operacionEnCurso;

    private boolean respaldoPrevioOk(String operacion) {
        ultimaImportacion = null;
        puntoAntes = A1BASES.A32_Respaldos.puntoDeRestauracion(requireContext(), "antes-" + operacion);
        operacionEnCurso = operacion;
        if (!puntoAntes.ok) {
            operacionEnCurso = null;
            new AlertDialog.Builder(getActivity())
                    .setTitle("No se hizo ningún cambio")
                    .setMessage("No se pudo hacer el punto de restauración completo:\n\n" + puntoAntes.mensaje +
                            "\n\nPor seguridad, el proceso se detuvo antes de borrar nada.")
                    .setPositiveButton("Entendido", null)
                    .show();
            return false;
        }
        return true;
    }

    // ⭐ NUEVO E3 (17-nov): punto "después" + resumen de la operación (en la app y en archivo).
    private void cerrarOperacion(String titulo, String detalle) {
        cerrarOperacion(titulo, detalle, true, false);
    }

    // conDespues = false en restauraciones (18-nov, Jorge): el resumen compara con el estado
    // actual sin guardar otra copia.
    // ⭐ CAMBIO (21-nov, Jorge): es el ÚNICO diálogo al terminar. Incluye el resultado de la
    // importación (y el diagnóstico completo solo si hubo fallas). avisarRenta: al importar de
    // Sheets, si la base quedó sin fotos de renta, se ofrece restaurarla al cerrar este diálogo.
    private void cerrarOperacion(String titulo, String detalle, boolean conDespues, boolean avisarRenta) {
        if (operacionEnCurso == null || getActivity() == null) return;
        A1BASES.A32_Respaldos.Resultado despues = conDespues
                ? A1BASES.A32_Respaldos.puntoDeRestauracion(requireContext(), "despues-" + operacionEnCurso)
                : A1BASES.A32_Respaldos.estadoActual(requireContext());
        Importacion imp = ultimaImportacion;
        StringBuilder det = new StringBuilder();
        if (imp != null) {
            det.append("Importadas: ").append(miles(imp.insertadas)).append(" · fallidas: ").append(imp.fallidas);
            if (imp.reubicadas > 0) det.append(" · con ítem nuevo: ").append(imp.reubicadas);
            det.append('\n');
        }
        if (detalle != null) det.append(detalle);
        String texto = A1BASES.A32_Respaldos.resumenOperacion(requireContext(), operacionEnCurso, titulo,
                puntoAntes, despues, det.toString());
        operacionEnCurso = null;
        puntoAntes = null;
        ultimaImportacion = null;
        boolean problemas = imp != null && imp.conProblemas;
        boolean renta = avisarRenta && imp != null && imp.insertadas > 0;
        new AlertDialog.Builder(getActivity())
                .setTitle((problemas ? "⚠ " : "✓ ") + titulo)
                .setMessage(texto + (problemas ? "\n⚠ Hubo fallas en la importación:\n\n" + imp.diagnostico : ""))
                .setPositiveButton("Entendido", null)
                .setOnDismissListener(d -> { if (renta) avisarSiFaltaRestaurarRenta(); })
                .show();
    }

    // ⭐ NUEVO E3 (17-nov): número de líneas de datos de un CSV de la carpeta Balance.
    private int lineasDeDatos(String nombreArchivo) {
        int n = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(archivoLocalBalance(nombreArchivo)))) {
            while (br.readLine() != null) n++;
        } catch (Exception e) {
            return 0;
        }
        return Math.max(0, n - 1);
    }

    // ⭐ NUEVO E0 (17-nov): archivo en la carpeta local "Balance".
    private File archivoLocalBalance(String nombreArchivo) {
        return new File(Environment.getExternalStorageDirectory() + "/Balance/" + nombreArchivo);
    }

    // ⭐ NUEVO E0 (17-nov): true si el archivo existe y tiene contenido.
    private boolean archivoConContenido(String nombreArchivo) {
        File f = archivoLocalBalance(nombreArchivo);
        return f.exists() && f.length() > 0;
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

    






//FIN MÉTODOS OPCIÓN 4


// INICIO PROCESO CON SHEETS
// ==================== MÉTODOS OPCIÓN 6: SINCRONIZAR DESDE SHEETS ====================



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

    // ⭐ NUEVO — Fase 4: corrige el diálogo del cierre parcial ("interno de algunas cuentas"),
    // que venía usando _7verNumeroDeTransacciones() (el total de TODA la tabla, sin filtro) para
    // anunciar cuántas transacciones se iban a borrar. Ese número nunca fue el correcto para el
    // cierre parcial — el correcto es este, filtrado por el snapshot Cerrable (ver
    // A1_1_AyudanteBD, migración v6, y A1_2_OperacionesBD.eliminarTransaccionesAlgunasCuentas(),
    // que es la que de verdad borra por esta misma condición).
    private long _7verNumeroDeTransaccionesCerrable() {
        try {
            A1_1_AyudanteBD ayudanteBD = new A1_1_AyudanteBD(
                    getActivity(),
                    balanceSqlite_String_PSF,
                    null,
                    version1BalanceSqlite_int_PSF
            );
            SQLiteDatabase db = ayudanteBD.getReadableDatabase();
            Cursor cursor = db.rawQuery(
                    // ⭐ CAMBIO (31-oct, Fase C): marca actual de la cuenta, no c12.
                    "SELECT COUNT(*) FROM transacciones WHERE " + A1_1_AyudanteBD.CONDICION_TRANSACCION_CERRABLE, null);
            long numeroRegistros = 0;
            if (cursor.moveToFirst()) {
                numeroRegistros = cursor.getLong(0);
            }
            cursor.close();
            db.close();
            return numeroRegistros;
        } catch (Exception e) {
            Log.e(TAG, "Error al contar transacciones Cerrable: " + e.getMessage(), e);
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