package B_FRAGMENTS;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
import static A1BASES.A9_2_BackupFile.BALANCE_FOLDER;
import static A1BASES.A9_2_BackupFile.CSV_DOCUMENT_TRANSACTIONS;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.gridlayout.widget.GridLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.jj.appbalancev31.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import A1BASES.A10_3_ValidadorProrrateable;
import A1BASES.A11_AuditoriaClasificacionDialogo;
import A1BASES.A1_1_AyudanteBD;
import A1BASES.A1_2_OperacionesBD;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A5_4_CSVRestoreRecordsDocument;
import A1BASES.A5_CacheManager;
import A1BASES.A6_3_CSVDriveUploader;
import A1BASES.A8_CalculadoraCallback;
import A1BASES.A99_MetodosVarios;
import A1BASES.A9_VisorTablasDialogo;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;
import B1_F1Support.B11_DocumentCalculator;
import B1_F1Support.B12_DocumentPersistence;
import B1_F1Support.B13_NavigationManager;
import B1_F1Support.B14_UIStateController;
import B1_F1Support.inflateunits.CalendarioUnit;
import B1_F1Support.inflateunits.ControlAccountsUnit;
import B1_F1Support.inflateunits.CreateNewUnit;
import B1_F1Support.inflateunits.CreateTemplateUnit;
import B1_F1Support.inflateunits.CrudOptionsUnit;
import B1_F1Support.inflateunits.ExecuteButtonsUnit;
import B1_F1Support.inflateunits.RecordDocumentUnit;
import B1_F1Support.inflateunits.SeeDocumentUnit;
import B1_F1Support.inflateunits.UpdateDeleteUnit;
import D_ADAPTERS.D_F1_AdaptadorCrudDocumento;

public class F1_CrudDocumento extends DialogFragment implements A8_CalculadoraCallback {

    // =========================================================
    // SECTION 1 — Fragment state flags
    // Controls lifecycle, restoration and navigation behavior
    // =========================================================
    public boolean estaRestaurando          = false;
    private boolean esReanudacionDeSistema   = false;
    public boolean vieneDeVerItemTransaccion = false;
    private boolean saliendoHaciaF2          = false;
    public boolean vieneDeNavController = false;
    public boolean vieneDeHome          = false;
    public int     areaGuardadaCanalA   = 0;
    public int     currentRadioButtonId;

    // Item puntual a abrir automáticamente (detalle) apenas termine de
    // cargarse el documento por Canal D — usado desde
    // A11_AuditoriaClasificacionDialogo (cargarDocumentoDesdeAuditoria) para
    // aterrizar directo en el registro con el error, no solo en el
    // documento. Se consume una sola vez (ver abrirItemPendienteDeAuditoriaSiExiste()).
    private String itemPendienteDeAuditoria_String;

    // =========================================================
    // SECTION 2 — UI state flags
    // Controls modes, dialogs and copy/paste behavior
    // =========================================================
    public boolean enModoModificacion            = false;
    private boolean ascending_Boolean             = true;
    public boolean isCopyMode                    = true;
    private String  cuentaOriginalEnModificacion  = "";
    public String  copiedText                    = "";

    // =========================================================
    // SECTION 3 — Canal D state
    // Swap button slot 3 ↔ slot 4 and its snackbar control
    // =========================================================
    public Button  botonVerde_XBt          = null;
    public boolean snackbarVerdeYaMostrado = false;

    // =========================================================
    // SECTION 4 — Class instances (helpers and DB)
    // =========================================================
    public A1_1_AyudanteBD                ayudante_Class;
    public A99_MetodosVarios              metodosVarios_Class;
    public A1_2_OperacionesBD             a2operacionesBD;
    private A5_4_CSVRestoreRecordsDocument a11_csvRestoreRecordsDocument;
    private A6_3_CSVDriveUploader          csvDriveUploader;
    private F6_Calculadora                 calculadora_Fragment;
    public A22_QueryManager a22QueryManager;
    private SQLiteDatabase                 db;
    private SQLiteDatabase                 sqliteDatabase_Abstracta;
    public B11_DocumentCalculator calculator;
    B11_DocumentCalculator.Sumas sumasActuales = new B11_DocumentCalculator.Sumas();
    B14_UIStateController uiState;

    public B13_NavigationManager navManager;

    public B12_DocumentPersistence persistence;

    // =========================================================
    // SECTION 5 — Query results (data from DB)
    // =========================================================
    private A23_QueryResult<String> resultadoCuentasOrdenAZ;
    private List<String>                                   cuentasConciliablesAZ_List;
    public List<String>                                   accountAllAz_List;
    private List<String>                                   documentosAz_Lista_Resultado;
    public List<A3_2_TipoTransaccionesGetsYSets>          transaccionesUnDocumento_ArrayListTT_Result;
    public String                                         documentoRecibido_Resultado_String;
    public String[]                                       atributosCuenta_ArrayS;
    public HashMap<String, Integer>                       movimientosOriginalesDocumento_HashMap = new HashMap<>();
    public boolean                                        documentoCargadoParaEdicion = false;
    private String                                         documentoEnEdicion_ID       = "";

    // =========================================================
    // SECTION 6 — Document list (items and adapters)
    // =========================================================
    public ArrayList<A3_2_TipoTransaccionesGetsYSets> listaDocumento_ArrayLTT      = new ArrayList<>();
    ArrayList<A3_2_TipoTransaccionesGetsYSets> itemsListaBackup_ArrayListTipoT;
    public ArrayList<String> listaCuentasRevisionParaAdapterSpinner_ArrayListString  = new ArrayList<>();
    ArrayList        cuentasConciliablesDeItemsLista_ArrayList                = new ArrayList<String>();
    public D_F1_AdaptadorCrudDocumento conexionListDocumentForGeneralWithListView_Adaptador1_TipoT;
    public ArrayAdapter<String> adapterConsecutivoDocAz;
    ArrayAdapter<String> consultacuentasOrdenAscendente_List_ArrayAdapter;
    ArrayAdapter         adaptadorCuentasConciliables;
    ArrayAdapter         adaptadorCuentaVaciaParaIniciar_ArrayAdapter;
    public ArrayAdapter         conectarListaCuentasRevisionConSpinner_arrayAdapterString;
    public List                 signos_ListString;

    // =========================================================
    // SECTION 7 — Calculation variables (financial totals)
    // Prefix $ = monetary accumulator
    // =========================================================
    public Integer $sAc = 0;
    public Integer $dAc = 0;
    public Integer $valorAc = 0;
    public Integer $mTP;
    public Integer $mTN;
    public Integer $mCP;
    public Integer $mCN;
    public Integer $netoT;
    public Integer $netoMC;
    Integer $netoMTSinMC;
    Integer $netoTSinCaja;
    public Integer $quedaPorRegistrar = 0;
    public Integer $quedaPorRegistrarConRegistroActual = 0;
    public Integer $adicionesAc;
    public Integer $diferenciaAConciliar_Integer              = 0;
    public Integer sumaMovimientoItemListaCuentaRevision_Integer = null;
    public int sumaTransaccionesCuenta;
    public int         sumaTransaccionesCuenta2;

    // =========================================================
    // SECTION 8 — Date variables
    // =========================================================
    public static Integer[] dateCurrent_ArrayInteger;
    public Integer DateOfDocument_Integer          = 0;
    public Integer otherDateAssignedInCreateNew_Int;
    public int     dateTemplateAssignedInCaledarView_Int;
    public int     dateInUpdateAssignedInCaledarView_Int;
    Date    fechaInicialEnAdicionar_Date;
    String  fechaFormateada_String;
    String  fechayHoraTransacciones_String;

    // =========================================================
    // SECTION 9 — String working variables
    // =========================================================
    public static String documentoABuscarRecibido;
    public static String        consecutivoRegistroAModificar_StringStatic = null;
    String nombreIdrB;
    public String docABuscar_String;
    String cuentaRegistroInicial_String;
    String cuentaConciliableASumar;
    String cuentaConciliableASumar2;
    String cuentaConciliableASumar3;
    public String descripcionPLantillaABuscar_String;
    String descripcionABuscarEnPlantilla_String;
    String leeDocODescricionRecibido_String;
    public String nuevoNumeroDocEnAdicionar_String;
    String estadoChBOtraFechaInicialEnAdicionar_String;
    public String copiarValorAnterior_String;
    public String copiarSigno_String;
    public String copiarDesripcionAnterior_String;
    public String copiarCuentaSpinnerAnterior_String;
    public String ultimoDocumentoEnLaTabla;
    public String[] cuentaDemasRegistros_ArrayS;
    public String[] signosAlRegistrar_ArrayString;

    // =========================================================
    // SECTION 10 — Views: layout areas (GridLayout / LinearLayout)
    // =========================================================
    public GridLayout  crudOptionsArea_XGl;
    public GridLayout  areaCreateNew_XGl;
    public GridLayout  areaTemplate_XGl;
    public GridLayout  areaUpdateAndDelete_XGl;
    public GridLayout  areaConciliacion_XGl;
    public GridLayout  areaConciliacionYRegistro_XGl;
    public GridLayout  areaCalendario_XGl;
    public GridLayout  areaDocumento_XGl;
    public GridLayout  areaListaRevisarCuentas_XGl;
    public GridLayout  seccion1HaceRegistrosItemsLista_XGl;
    public GridLayout  cuenta_XGL;
    public GridLayout  transacciones_crud_XCL;
    public LinearLayout areaButtons_XLL;
    public LinearLayout envolventeEncabezadoItemLista_XLl;
    public LinearLayout embolventeItemsListaRevisar_XLl;
    public LinearLayout envolventeEncabezadoItemListaRevisar_XLl;
    public ListView    listaDocumento_XLv;
    public CalendarView calendarView_XCv;

    // =========================================================
    // SECTION 11 — Views: RadioGroup and RadioButtons
    // =========================================================
    public RadioGroup optionsDoc_XRg;
    public RadioButton create_XRb;
    public RadioButton template_XRb;
    public RadioButton updateDelete_XRb;

    // =========================================================
    // SECTION 12 — Views: Spinners
    // =========================================================
    public Spinner signo_XSp;
    public Spinner cuenta_XSp;
    public Spinner cuentaConciliacion_XSp;
    public Spinner listaCuentasDeRevision_XSp;
    public Spinner consultaPorCuentaYFechaEnOtroFragment_XSp;

    // =========================================================
    // SECTION 13 — Views: AutoCompleteTextView
    // =========================================================
    public AutoCompleteTextView descripcion_XAtv;
    public AutoCompleteTextView cuenta_XAtv;
    public AutoCompleteTextView         descripcionABuscarEnPlantilla_XAtv;
    public AutoCompleteTextView         buscarCuentaParaTransaccionest_XAT;
    public AutoCompleteTextView  documentoABuscarParaEditar_XATv;

    // =========================================================
    // SECTION 14 — Views: EditText
    // =========================================================
    public EditText valor_XEt;
    public EditText inputPhysicalVsAccounting_XEt;
    EditText campoActivo;

    // =========================================================
    // SECTION 15 — Views: TextView
    // =========================================================
    public TextView valorRecibidoResultadoCalculadora_XTv;
    TextView contenidoCalculadora_XTv;
    public TextView numeroConsecutivoDoc_XTv;
    public TextView consecutivoItemRegistro_XTv;
    public TextView controlACeroTotales_XTv;
    public TextView sumaPositivos_XTv;
    public TextView sumaNegativos_XTv;
    public TextView dateInCreateNew_XTv;
    public TextView otherDateInCreateNew_XTv;
    public TextView dateInTemplate_XTv;
    public TextView consecutivoNuevoDocEnPLantilla_XTv;
    public TextView documentoYFechaInicialBaseDeLaPLantilla_XTv;
    public TextView diferenciaAConciliar_XTv;
    public TextView saldoCuentaAconciliar_XTv;
    public TextView saldoInicialeListaCuentasRevisar_XTv;
    public TextView movimientoListaCuentasRevisar_XTv;
    public TextView saldoFinalListaCuentasRevisar_XTv;
    public TextView dateInUpdate_XTv;
    public TextView changeOfDateInUpdate_XTv;
    // NOTE: EtiquetaFechaNueva_XTv and etiquetadateInUpdate_XTv are aliases
    // of changeOfDateInUpdate_XTv — consolidated in UpdateDeleteUnit
    public TextView EtiquetaFechaNueva_XTv;
    public TextView etiquetadateInUpdate_XTv;
    public TextView numeroConsecutivoDocEnEdicion_XTv;

    // =========================================================
    // SECTION 16 — Views: Buttons and CheckBoxes
    // =========================================================
    public Button seeButtons_XB;
    public Button salir_XBt;
    public Button salidaEsteFragment_XBt;
    public Button eliminaDocumento_XBt;
    public Button guardarCambiosALaBD;
    public Button limpiarCamposRegistro_XBt;
    public Button cleanSinCambiosInBD_XBt;
    public Button cleanPasteQueryAccount_XChB;
    public Button pegarValor_XChB;
    public Button pegarDescripcion_XChB;
    public Button pegarCuentaSpinner_XChB;
    public Button pegarValorAnterior_XChB;
    public Button pegarDescripcionAnterior_XChB;
    public Button pegarCuetaAnterior_XChB;
    Button calculadoraLibre_XBt;
    public ImageButton cerrarCalendario_XBt;
    public CheckBox assignedOtherDateInCreateNew_XChB;
    public CheckBox assignedDateInTemplate_XChB;
    public CheckBox assignedOtherDateInUpdate_XChB;
    public CheckBox limpiarDocumentoModificar_XChB;
    public CheckBox copyPasteDescriptionTemplate_XChb;
    public CheckBox seeAccountsXChB;

    // =========================================================
    // SECTION 17 — Views: FAB and animators
    // =========================================================
    public ExtendedFloatingActionButton fabModificar;
    ObjectAnimator               fabPulseAnimator;
    ObjectAnimator               fabColorAnimator;

    // =========================================================
    // SECTION 18 — TextWatchers and BroadcastReceiver
    // =========================================================
    BroadcastReceiver cuentasActualizadasReceiver;
    PopupWindow       popupValidacion;

    // =========================================================
    // SECTION 19 — Cursor and misc legacy
    // =========================================================
    public Cursor llamadaCursorUnDocumento;
    public int siguienteDocEnAdicionar_Integer;

    // Variables de snapshot — se actualizan mientras las vistas están vivas
    private A5_CacheManager.Encabezado encabezadoSnapshot = null;
    private ArrayList<A3_2_TipoTransaccionesGetsYSets> listaSnapshot = null;

    // Variable de instancia en F1
    private Bundle savedInstanceState;
    public boolean estaRestaurandoCanalA = false;

    private int filaContador = 0; // declara este campo en la clase

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true); // Establecer como no modal, sale del dialogoFragment a un click fuera del dialogo
        // ✅ Guardar referencia para usarla en onStart()
        this.savedInstanceState = savedInstanceState;
    }

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        View inflarViews_View = inflater.inflate(R.layout.f1_1_crud_documento, container, false);

        // 🛡️ RECUPERACIÓN: Si el maletín no está vacío, recuperamos el ID
        if (savedInstanceState != null) {
            currentRadioButtonId = savedInstanceState.getInt("ID_RADIO_GUARDADO");
            estaRestaurando = true;
            esReanudacionDeSistema = true;  // ← agregar esta línea
        } else {
            SharedPreferences prefs = getContext().getSharedPreferences(
                    "MyAppPreferences", Context.MODE_PRIVATE);
            currentRadioButtonId = prefs.getInt(
                    "lastSelectedRadioButtonId", R.id.create_XRb);
            esReanudacionDeSistema = false;  // ← agregar esta línea
        }

        // Localizamos el TextView que servirá de botón secreto
        //TextView tvOpciones = inflarViews_View.findViewById(R.id.opcionesDeTrabajo_XTv);
        Button btCacheBackup = inflarViews_View.findViewById(R.id.cacheBackup_XBt);

        // Configuramos el clic largo
        /*tvOpciones.setOnLongClickListener(v -> {
            abrirVisorDeCache();
            return true; // Indica que el evento fue manejado
        });*/

        btCacheBackup.setOnClickListener(v -> {
            // Sin RadioButton — muestra toda la caché con tabs
            A9_VisorTablasDialogo.newInstance()
                    .show(getChildFragmentManager(), "visor_debug");
        });

        Button btAuditoriaClasificacion = inflarViews_View.findViewById(R.id.auditoriaClasificacion_XBt);
        if (btAuditoriaClasificacion != null) {
            btAuditoriaClasificacion.setOnClickListener(v -> {
                A11_AuditoriaClasificacionDialogo.newInstance()
                        .show(getChildFragmentManager(), "auditoria_clasificacion");
            });
        }

        calculadora_Fragment = new F6_Calculadora();

        valorRecibidoResultadoCalculadora_XTv = (TextView) inflarViews_View.findViewById(R.id.valorRecibidoResultadoCalculadora_XTv);
        calculadoraLibre_XBt = inflarViews_View.findViewById(R.id.calculadoraLibre_XBt);
        contenidoCalculadora_XTv = (TextView) inflarViews_View.findViewById(R.id.contenidoCalculadora_XTv);

        //Declaracion de instacias generales de las clases
        a22QueryManager = new A22_QueryManager(getActivity());
        calculator = new B11_DocumentCalculator(getActivity());
        uiState    = new B14_UIStateController(this);
        navManager = new B13_NavigationManager(this);
        persistence = new B12_DocumentPersistence(this);

        //Ejecutar metodos

        crearCarpetaDeLaAppBalance();
        insertarCuentaVacia();

        transacciones_crud_XCL = inflarViews_View.findViewById(R.id.transacciones_crud_XCL);
        areaButtons_XLL = (LinearLayout) inflarViews_View.findViewById(R.id.areaButtons_XLL);

        new CrudOptionsUnit(this).setup(inflarViews_View);
        new CreateNewUnit(this).setup(inflarViews_View);
        new CreateTemplateUnit(this).setup(inflarViews_View);
        new UpdateDeleteUnit(this).setup(inflarViews_View);
        new ExecuteButtonsUnit(this).setup(inflarViews_View);
        new RecordDocumentUnit(this).setup(inflarViews_View);
        new CalendarioUnit(this).setup(inflarViews_View);
        new SeeDocumentUnit(this).setup(inflarViews_View);
        new ControlAccountsUnit(this).setup(inflarViews_View);
        setVisibilityGoneTodo();

        if (listaDocumento_ArrayLTT.size() == 0) {
            consecutivoItemRegistro_XTv.setText("0/0");
        }

       //instancias a llamada de otras clases
        ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
        metodosVarios_Class = new A99_MetodosVarios(getActivity());
        dateCurrent_ArrayInteger = metodosVarios_Class.fechasYHoras();

        //Fechas
        Calendar cal = Calendar.getInstance();
        fechaInicialEnAdicionar_Date = cal.getTime();
        SimpleDateFormat formatoFecha = new SimpleDateFormat("yyyy-MM-dd");
        fechaFormateada_String = formatoFecha.format(fechaInicialEnAdicionar_Date);
        dateInCreateNew_XTv.setText(fechaFormateada_String);

        // ✅ PASO 1: Cargar primero los datos
        dynamicQueryByAllAccountAz();  // ← Mover aquí arriba

        // PASO 2: Ahora sí usar resultadoCuentasOrdenAZ con seguridad
        accountAllAz_List = null;
        if (resultadoCuentasOrdenAZ != null && !resultadoCuentasOrdenAZ.getDatos().isEmpty()) {
            accountAllAz_List = resultadoCuentasOrdenAZ.getDatos();
        } else {
            accountAllAz_List = new ArrayList<>();  // ← Lista vacía en lugar de null
            if (resultadoCuentasOrdenAZ != null) {
                Toast.makeText(getActivity(), resultadoCuentasOrdenAZ.getMensaje(), Toast.LENGTH_LONG).show();
            }
        }

// PASO 3: Crear el Adapter (ahora accountAllAz_List nunca será null)
        consultacuentasOrdenAscendente_List_ArrayAdapter = new ArrayAdapter<>(
                getActivity(),
                android.R.layout.simple_list_item_multiple_choice,
                accountAllAz_List
        );
        consultacuentasOrdenAscendente_List_ArrayAdapter.notifyDataSetChanged();

        dynamicQueryByAzAccountReconciliable();

        cuentaConciliacion_XSp.setSelection(0);

        pasarItemListaTodoResumidoAItemListaRevision();

        salidaEsteFragment_XBt.setVisibility(View.VISIBLE);
        ocultarTeclado();

        sumarItemListaDocumento();
        actualizarSumasListado();

        dynamicQuerySumByAccountAcordingToArgument();
        numerarDocumentoConsecutivo();

        try {

            numeroConsecutivoDocEnEdicion_XTv.setText(String.format("%04d", Integer.parseInt(documentoRecibido_Resultado_String)));

        } catch (Exception e) {

        }

        setupCampoConCalculadora(valor_XEt);
        setupCampoConCalculadora(inputPhysicalVsAccounting_XEt);

        if (calculadoraLibre_XBt != null) {
            calculadoraLibre_XBt.setOnClickListener(v -> mostrarCalculadoraLibre());
        }
//4
        // ===== EN onCreateView o onViewCreated =====
        fabModificar = inflarViews_View.findViewById(R.id.fabModificar);

        fabModificar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Tu lógica de guardar modificación
                guardarModificacion();
            }
        });

        // Esperar a que el layout esté completamente dibujado
        fabModificar.post(new Runnable() {
            @Override
            public void run() {
                posicionarFABRelativoABoton();//ojo
            }
        });

        seeButtons_XB.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick (View v){
                setvisibleCreateDespuesDeEditar();
            }
        });

        // ✅ DETECTAR si viene de VerItemTransaccion
        Bundle bundle = getArguments();
        if (bundle != null && bundle.getBoolean("fromVerItemTransaccion", false)) {
            vieneDeVerItemTransaccion = true; // ⭐ Activar bandera ANTES de procesar

            // Procesar el bundle en el siguiente frame para asegurar que las vistas estén listas
            inflarViews_View.post(() -> {
                recibirBundleDeVerItemTransaction(bundle);
            });
        }

        resetearSpinnerCuentasRevision();
        //ojo2
        // ⭐ REGISTRAR EL RECEIVER
        cuentasActualizadasReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Actualizar los spinners/autocomplete cuando se notifique
                actualizarTodosLosAdaptadores();
            }
        };

        LocalBroadcastManager.getInstance(getActivity())
                .registerReceiver(cuentasActualizadasReceiver,
                        new IntentFilter("CUENTAS_ACTUALIZADAS"));

        // En onViewCreated() o donde inicializas listaDocumento_XLv

        GestureDetector gestureDetector = new GestureDetector(getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        int pos = listaDocumento_XLv.pointToPosition((int) e.getX(), (int) e.getY());
                        if (pos != ListView.INVALID_POSITION
                                && pos < listaDocumento_ArrayLTT.size()) {
                            mostrarCamposItem(listaDocumento_ArrayLTT.get(pos));
                        }
                        return true;
                    }
                });

        listaDocumento_XLv.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            // Al envolver toda la pantalla en un NestedScrollView (para que
            // funcionara el scroll de página en horizontal — ver el
            // comentario en f1_1_crud_documento.xml) ese scroll exterior
            // empezó a "robarse" el arrastre vertical dentro de esta lista,
            // en vez de dejarla desplazarse ella misma. Mientras el dedo
            // esté sobre listaDocumento_XLv le pedimos al padre que NO
            // intercepte el gesto, para que sea la lista la que haga scroll;
            // se libera al soltar el dedo para no bloquear el scroll de
            // página en el resto de la pantalla.
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (v.getParent() != null) {
                        v.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    break;
            }
            return false; // false = no bloquea touch ni long press existentes
        });

        return inflarViews_View;
    }


    private void mostrarCamposItem(A3_2_TipoTransaccionesGetsYSets item) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());

        ScrollView scroll = new ScrollView(getContext());
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 48);

        // ✅ Título con número de item y cuenta
        TextView tvTitulo = new TextView(getContext());
        tvTitulo.setText("Registro " + item.tipoT_2DocumentItems_String
                + "  —  " + item.tipoT_3Accout_String);
        tvTitulo.setTextSize(16);
        tvTitulo.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitulo.setTextColor(Color.parseColor("#425DF6")); // ✅ color azul
        tvTitulo.setGravity(Gravity.CENTER);                // ✅ centrado
        tvTitulo.setPadding(0, 0, 0, 24);
        layout.addView(tvTitulo);

        // El resto igual que antes
        agregarFila(layout, "c1 Documento",   item.tipoT_1NumberDocument_String);
        agregarFila(layout, "c2 Item",        item.tipoT_2DocumentItems_String);
        agregarFila(layout, "c3 Cuenta",      item.tipoT_3Accout_String);
        agregarFila(layout, "c4 Signo",       item.tipoT_4Sign_String);
        agregarFila(layout, "c5 Valor",       String.valueOf(item.tipoT_5Value_Integer));
        agregarFila(layout, "c6 Descripcion", item.tipoT_6Description_String);
        agregarFila(layout, "c7 FechaHora",   item.tipoT_7TimeOfProcess_String);
        agregarFila(layout, "c8 FechaDoc",    String.valueOf(item.tipoT_8DateOfDocument_Integer));
        agregarFila(layout, "c9 FechaMod",    item.tipoT_9DateUpdateDocument_String);
        agregarFila(layout, "c10 Grupo1",     item.tipoT_10BalanceItems_String);
        agregarFila(layout, "c11 Grupo2",     item.tipoT_11BalanceItemsClassification_String);
        agregarFila(layout, "c12 Flag",       item.tipoT_12AccountWhitFlag_String);
        agregarFila(layout, "c13 Disponible", item.tipoT_13ColumnaDisponible_String);

        // ─────────────────────────────────────────────────────────────────
        // "Modificar este registro" — antes (hasta el commit anterior) este
        // botón quitaba el ítem de la lista y precargaba los campos de
        // REGISTRO NUEVO (cuenta_XAtv), para que el usuario lo agregara de
        // nuevo. Jorge notó (2026-09-12) que eso usaba un mecanismo distinto
        // al de "Modificar" del long-press (que usa cuenta_XSp) — dos caminos
        // para lo mismo, y el nuevo (agregar) fue el que tuvo el bug de la
        // cuenta equivocada.
        //
        // Al revisar de nuevo: la razón original para NO reusar "Modificar"
        // (evitar reabrir el bug de Grupo1/Grupo2 no refrescado) ya no aplica
        // — ese bug se corrigió hace varios commits directamente en
        // guardarModificacion() (ver B12_DocumentPersistence, sección 6): ahí
        // SIEMPRE se re-consulta Grupo1/Grupo2 frescos de "cuentas" para la
        // cuenta seleccionada al guardar. Y "Modificar" solo permite cambiar
        // valor/signo/cuenta/descripcion — nunca Grupo1/Grupo2 a mano — así
        // que no reabre el riesgo de edición libre que preocupaba a Jorge.
        //
        // Por eso ahora este botón simplemente entra al mismo "modo
        // modificación" que usa el long-press → "Modificar" (ver
        // SeeDocumentUnit.handleItemAction, case 0), pre-cargado con este
        // ítem — es un atajo directo desde el detalle/auditoría a ese mismo
        // camino ya probado, con una sola cuenta_XSp / un solo botón de
        // confirmar (el FAB, que llama guardarModificacion()) para toda la
        // app. El ítem NO se quita de la lista; se edita en su lugar.
        Button btCorregirYVolverARegistrar = new Button(getContext());
        btCorregirYVolverARegistrar.setText("✏️ Modificar este registro");
        btCorregirYVolverARegistrar.setOnClickListener(v -> {
            int posicion = listaDocumento_ArrayLTT.indexOf(item);
            if (posicion < 0) {
                Toast.makeText(getActivity(),
                        "No se encontró el registro en la lista (¿ya se corrigió?)",
                        Toast.LENGTH_LONG).show();
                dialog.dismiss();
                return;
            }

            entrarModoModificacionDesdeItem(posicion);

            Toast.makeText(getActivity(),
                    "Modo modificación — revisa los campos y confirma con el botón flotante",
                    Toast.LENGTH_LONG).show();

            dialog.dismiss();
        });
        LinearLayout.LayoutParams lpBoton = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBoton.setMargins(0, 32, 0, 0);
        btCorregirYVolverARegistrar.setLayoutParams(lpBoton);
        layout.addView(btCorregirYVolverARegistrar);

        scroll.addView(layout);
        dialog.setContentView(scroll);
        dialog.show();
    }


    private void agregarFila(LinearLayout parent, String campo, String valor) {
        LinearLayout fila = new LinearLayout(getContext());
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setPadding(16, 20, 16, 20);

        // ✅ Filas alternas: blanco suave y azul muy claro
        int colorFondo = (filaContador % 2 == 0)
                ? Color.parseColor("#F0F4FF")  // azul pálido
                : Color.parseColor("#FFFFFF"); // blanco
        fila.setBackgroundColor(colorFondo);
        filaContador++;

        TextView tvCampo = new TextView(getContext());
        tvCampo.setText(campo);
        tvCampo.setTypeface(null, android.graphics.Typeface.BOLD);
        tvCampo.setTextColor(Color.parseColor("#425DF6")); // ✅ etiqueta en azul
        tvCampo.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

        TextView tvValor = new TextView(getContext());
        tvValor.setText(valor != null ? valor : "—");
        tvValor.setTextColor(Color.parseColor("#222222")); // gris oscuro legible
        tvValor.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.8f));

        fila.addView(tvCampo);
        fila.addView(tvValor);
        parent.addView(fila);
    }

    private void abrirVisorDeCache() {
        // 1. Preguntamos DIRECTAMENTE al RadioGroup qué hay marcado AHORA
        int idSeleccionado = optionsDoc_XRg.getCheckedRadioButtonId();

        // 2. Convertimos ese ID al ID de área (1, 2 o 3)
        int areaActual = A5_CacheManager.radioButtonToAreaId(
                idSeleccionado,
                R.id.create_XRb,
                R.id.template_XRb,
                R.id.updateDelete_XRb
        );

        // 🛡️ SEGURIDAD: Si por alguna razón no hay nada marcado, no abrimos el visor con 0
        if (areaActual <= 0) {
            // Opcional: un Log o Toast indicando que no hay área seleccionada
            return;
        }

        // 3. Enviamos el área real al Visor
        // Usamos el '2' para ver el ENCABEZADO (como querías)
        A9_VisorTablasDialogo.newInstance(2, areaActual)
                .show(getChildFragmentManager(), "visor_debug");

    }

    private void actualizarTodosLosAdaptadores() {
        if (!isAdded() || getActivity() == null) return;
        dynamicQueryNameAzAllAccount(cuenta_XAtv);
        dynamicQueryNameAzAllAccount(cuenta_XSp);
        dynamicQueryNameAzAllAccount(consultaPorCuentaYFechaEnOtroFragment_XSp);
        dynamicQueryNameAzAllAccount(buscarCuentaParaTransaccionest_XAT);

        // ✅ AGREGAR ESTA LÍNEA — actualizar también el spinner de conciliación
        dynamicQueryByAzAccountReconciliable();
    }

    public void protegerSpinnersVacios() {
        // Lista de TODOS tus spinners críticos
        Spinner[] spinners = {
                cuentaConciliacion_XSp,
                signo_XSp,
                listaCuentasDeRevision_XSp
                // Agrega aquí otros spinners que uses en las áreas
        };

        for (Spinner sp : spinners) {
            if (sp != null && (sp.getAdapter() == null || sp.getAdapter().isEmpty())) {
                // Asignar adaptador dummy temporal
                ArrayAdapter<String> dummyAdapter = new ArrayAdapter<>(
                        getActivity(),
                        android.R.layout.simple_spinner_item,
                        new String[]{""}
                );
                sp.setAdapter(dummyAdapter);
            }
        }
    }

    private void posicionarFABRelativoABoton() {
        // Buscar el botón de referencia (ajusta el ID según tu layout)
        View botonReferencia = getView().findViewById(R.id.salir_XBt);

        if (botonReferencia != null && fabModificar != null) {

            // Obtener la posición del botón de referencia
            int[] locationReferencia = new int[2];
            botonReferencia.getLocationInWindow(locationReferencia);

            // Obtener parámetros del FAB
            CoordinatorLayout.LayoutParams params =
                    (CoordinatorLayout.LayoutParams) fabModificar.getLayoutParams();

            // Calcular la posición: debajo del botón + separación
            int nuevaPosicionTop = locationReferencia[1] +
                    botonReferencia.getHeight() +
                    dpToPx(16); // 16dp de separación

            // Ajustar considerando la posición del CoordinatorLayout
            View coordinatorLayout = getView().findViewById(R.id.layoutPrincipal_XCl);
            if (coordinatorLayout != null) {
                int[] locationCoordinator = new int[2];
                coordinatorLayout.getLocationInWindow(locationCoordinator);
                nuevaPosicionTop -= locationCoordinator[1];
            }

            // Aplicar la nueva posición
            params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            params.topMargin = nuevaPosicionTop;
            params.leftMargin = dpToPx(16);
            params.rightMargin = dpToPx(16);

            fabModificar.setLayoutParams(params);

        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public void iniciarModoModificacion(int posicion) {

        Toast.makeText(getActivity(), "Iniciando modo modificación al inicio", Toast.LENGTH_SHORT).show();

        enModoModificacion = true;

        // Guardar la cuenta original antes de modificar
        cuentaOriginalEnModificacion = listaDocumento_ArrayLTT.get(posicion).tipoT_3Accout_String;

        // Configurar UI para modo modificación
        cuenta_XAtv.setText("Modo modificacion"); // Solo para UI visual
        cuenta_XAtv.setVisibility(View.GONE);
        pegarCuetaAnterior_XChB.setVisibility(View.GONE);
        pegarCuentaSpinner_XChB.setVisibility(View.GONE);

        // ✅ MOSTRAR Y ANIMAR EL FAB
        animarBotonModificar();

    }

    public void finalizarModoModificacion() {
        enModoModificacion = false;
        cuentaOriginalEnModificacion = "";

        // Detener animaciones
        animarBotonModificar();
        cuenta_XAtv.setVisibility(View.VISIBLE);
        cuenta_XAtv.setText(""); // Limpiar
        pegarCuetaAnterior_XChB.setVisibility(View.VISIBLE);
        pegarCuentaSpinner_XChB.setVisibility(View.VISIBLE);
    }

    // ─────────────────────────────────────────────────────────────────
    // Reutiliza EXACTAMENTE la secuencia de
    // SeeDocumentUnit.handleItemAction(0, posicion) ("Modificar" del
    // long-press sobre listaDocumento_XLv) para poder entrar al mismo modo
    // modificación desde otro punto de entrada (el botón "Modificar este
    // registro" del detalle de un ítem — mostrarCamposItem()). No se tocó
    // SeeDocumentUnit porque su método es privado y no vale la pena exponerlo
    // solo para esto; los campos que usa ya son todos de este mismo fragment.
    //
    // OJO con el orden: cuenta_XSp.setSelection(...) se llama ANTES de
    // iniciarModoModificacion(posicion) (que recién ahí pone
    // enModoModificacion = true) — igual que en el original. Esto es a
    // propósito: Spinner difiere el aviso onItemSelected (lo publica, no lo
    // llama de inmediato), así que para cuando esa notificación realmente se
    // dispara, enModoModificacion ya es true y se entra por la rama segura
    // de RecordDocumentUnit.setupCuentaSpinner() (la que NO auto-registra).
    // Si se invierte el orden, se reabre el bug que ya se corrigió en el
    // botón "Corregir y volver a registrar".
    private void entrarModoModificacionDesdeItem(int posicion) {
        if (!listaDocumento_ArrayLTT.isEmpty()) {
            consecutivoItemRegistro_XTv.setText(
                    "Item:\n" + listaDocumento_ArrayLTT.get(posicion).tipoT_2DocumentItems_String
                            + "/" + listaDocumento_ArrayLTT.size());
        } else {
            consecutivoItemRegistro_XTv.setText("0/0");
        }
        consecutivoRegistroAModificar_StringStatic =
                listaDocumento_ArrayLTT.get(posicion).tipoT_2DocumentItems_String;

        int valor = listaDocumento_ArrayLTT.get(posicion).tipoT_5Value_Integer;
        valor_XEt.setText("" + (valor < 0 ? valor * -1 : valor));
        signo_XSp.setSelection(
                signos_ListString.indexOf(
                        listaDocumento_ArrayLTT.get(posicion).tipoT_4Sign_String));
        descripcion_XAtv.setText(
                listaDocumento_ArrayLTT.get(posicion).tipoT_6Description_String);
        cuenta_XSp.setSelection(
                accountAllAz_List.indexOf(
                        listaDocumento_ArrayLTT.get(posicion).tipoT_3Accout_String));

        iniciarModoModificacion(posicion);
        digitarFisicoVsSaldoConciliacion();
        actualizarTotales();
        actualizarListView();

        fabModificar.post(() -> {
            if (enModoModificacion && fabModificar.getVisibility() != View.VISIBLE) {
                fabModificar.setVisibility(View.VISIBLE);
                fabModificar.show();
                animarBotonModificar();
            }
        });
    }

    private void setupCampoConCalculadora(final EditText campo) {
        if (campo == null) return;

        // Long click: abrir calculadora y recordar qué campo la llamó
        campo.setOnLongClickListener(v -> {
            campoActivo = campo;
            mostrarCalculadoraDesdeCampo();
            return true; // Consumir el evento
        });

        // Click simple: opcional, puedes agregar lógica adicional
        campo.setOnClickListener(v -> {
        });
    }

    private void mostrarCalculadoraDesdeCampo() {
        if (getFragmentManager() == null) return;

        // Usar factory method con callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceDesdeCampo((A8_CalculadoraCallback) this);
        calculadora.show(getFragmentManager(), "calculadora_desde_campo");
    }

    /**
     * Muestra la calculadora en modo "libre" (sin campo asociado)
     * Solo para cálculos generales
     */
    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;

        // Usar factory method sin callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }

    /**
     * (Opcional) Se ejecuta si el usuario cancela la calculadora sin usar el resultado.
     * Por defecto no hace nada, pero puedes override para agregar lógica.
     */
    @Override
    public void onCalculadoraCancelada() {
        // Puedes agregar lógica aquí si necesitas
        // Por ejemplo: mostrar un Toast, limpiar algo, etc.
        campoActivo = null;
    }

    public void mostrarCalculadora() {
        F6_Calculadora fragmentF7 = new F6_Calculadora();
        fragmentF7.show(getFragmentManager().beginTransaction(), "llamadoDesdeF1");
    }

    public void seeGridLAyoutAccount() {

        if (valor_XEt.getText().length() > 0 && descripcion_XAtv.getText().length() > 0
                && signo_XSp.getSelectedItem().toString().length() > 0) {

            cuenta_XGL.setVisibility(View.VISIBLE);

        } else {
            cuenta_XGL.setVisibility(View.GONE);
        }
    }

    private void dynamicQueryLastDocument() {

        A23_QueryResult<String> resultado = a22QueryManager.queryTransactionsEndDocument();

        if (resultado.getDatos() != null && !resultado.getDatos().isEmpty()) {
            documentoRecibido_Resultado_String = resultado.getDatos().get(0);
        } else {
            documentoRecibido_Resultado_String = null; // numerarDocumentoConsecutivo() ya maneja este caso
        }

    }

    // ANTES: creaba un QueryManager local nuevo — redundante, ya existe f1.queryManager
    public void dynamicQueryByAzAccountReconciliable() {
        // DESPUÉS: usar queryManager existente
        A23_QueryResult<String> cuentasConiliablesAz = a22QueryManager.queryAzConciliablesAccountsWithFilter();
        cuentasConciliablesAZ_List = cuentasConiliablesAz.getDatos();

        cuentasConciliablesAZ_List.add("");
        cuentasConciliablesAZ_List.add("1 No conciliar");

        adaptadorCuentasConciliables = new ArrayAdapter<>(
                getActivity(),
                android.R.layout.simple_list_item_multiple_choice,
                cuentasConciliablesAZ_List);
        adaptadorCuentasConciliables.notifyDataSetChanged();
        cuentaConciliacion_XSp.setAdapter(adaptadorCuentasConciliables);
        ordenAzZaPorCuentaConciliacion(ascending_Boolean);
    }

    public void dynamicQueryByDescriptionWithoutRepetition(View miVista) {

        // Validar que la vista no sea nula
        if (miVista == null) {
            return;
        }

        // Validar que la vista sea AutoCompleteTextView
        if (!(miVista instanceof AutoCompleteTextView)) {
            return;
        }

        // Obtener los datos de la consulta
        A23_QueryResult detalleTransacciones_Resultado_Consulta = a22QueryManager.queryTransactionsByDescriptions();

        // Validar que la consulta no haya fallado
        if (detalleTransacciones_Resultado_Consulta == null) {
            return;
        }

        List<String> detalleTransacciones_List_Resultado = detalleTransacciones_Resultado_Consulta.getDatos();

        // Validar que la lista tenga datos
        if (detalleTransacciones_List_Resultado == null || detalleTransacciones_List_Resultado.isEmpty()) {
            return;
        }

        // Crear el adaptador
        final ArrayAdapter<String> adaptadorDescripcion = new ArrayAdapter<>(
                getActivity(), android.R.layout.simple_list_item_1, detalleTransacciones_List_Resultado
        );

        adaptadorDescripcion.notifyDataSetChanged();

        // Asignar el adaptador a la vista
        ((AutoCompleteTextView) miVista).setAdapter(adaptadorDescripcion);

        // Confirmar en Logcat
    }

    public void dynamicQueryByDescriptionPlantillaOnly(View miVista) {

        if (miVista == null || !(miVista instanceof AutoCompleteTextView)) return;

        // ✅ Ahora usa queryManager que sí tiene acceso a la DB
        if (!a22QueryManager.tablaExiste("transacciones")) {
            return;
        }

        A23_QueryResult<String> plantillas_Resultado_Consulta =
                a22QueryManager.queryTransactionsByDescriptionsWithPlantillaFilter();

        if (plantillas_Resultado_Consulta == null) return;

        List<String> plantillas_List_Resultado = plantillas_Resultado_Consulta.getDatos();

        if (plantillas_List_Resultado == null || plantillas_List_Resultado.isEmpty()) {
            Toast.makeText(getActivity(), "No hay plantillas disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        final ArrayAdapter<String> adaptadorPlantillas = new ArrayAdapter<>(
                getActivity(),
                android.R.layout.simple_list_item_1,
                plantillas_List_Resultado
        );

        adaptadorPlantillas.notifyDataSetChanged();
        ((AutoCompleteTextView) miVista).setAdapter(adaptadorPlantillas);
        ((AutoCompleteTextView) miVista).setThreshold(2);
    }

    public void dynamicQueryNameAzAllAccount(View miVista) {

        if (!isAdded() || getActivity() == null) return; // ← guardia de seguridad

        // Obtener los datos
        A23_QueryResult cuentasAz_Result = a22QueryManager.queryAzAllAccounts();
        accountAllAz_List = cuentasAz_Result.getDatos();

        ordenAzZaPorCuentaConciliacion(ascending_Boolean);

        // Determinar el tipo de vista
        boolean esSpinner = (miVista instanceof Spinner);
        int layoutItem = esSpinner ? android.R.layout.simple_list_item_multiple_choice : android.R.layout.simple_list_item_1;

        // Crear el adaptador
        ArrayAdapter<String> adaptadorCuentas = new ArrayAdapter<>(getActivity(), layoutItem, accountAllAz_List);

        adaptadorCuentas.notifyDataSetChanged();

        // Asignar el adaptador a la vista correspondiente
        if (miVista instanceof Spinner) {
            ((Spinner) miVista).setAdapter(adaptadorCuentas);
        } else if (miVista instanceof AutoCompleteTextView) {
            ((AutoCompleteTextView) miVista).setAdapter(adaptadorCuentas);
        }

    }

    public void dynamicQueryByDocumentinUpdate() {
        A23_QueryResult documentoAz = a22QueryManager.queryTransacitonsAzDocuments();
        documentosAz_Lista_Resultado = documentoAz.getDatos();
        adapterConsecutivoDocAz = new ArrayAdapter<>(
                getActivity(),
                android.R.layout.simple_list_item_1,
                documentosAz_Lista_Resultado);
        adapterConsecutivoDocAz.notifyDataSetChanged();
        // NOTA: setAdapter al AutoComplete lo hace UpdateDeleteUnit en loadInitialData()
        // No se asigna aquí para no crear dependencia de vista en el método de query
    }

    public void dynamicQuerySumByAccountAcordingToArgument() {

        if (cuentaConciliacion_XSp == null || cuentaConciliacion_XSp.getAdapter() == null) {
            return;
        }
        cuentaConciliableASumar = cuentaConciliacion_XSp.getSelectedItem().toString();
        A23_QueryResult<Void> sumaPorCuenta = a22QueryManager.querySumTransactionsByAccount(cuentaConciliableASumar);
        $sAc = sumaPorCuenta.getSuma();

        //
        if (listaCuentasDeRevision_XSp == null || listaCuentasDeRevision_XSp.getAdapter() == null || listaCuentasDeRevision_XSp.getAdapter().isEmpty()) {
            return;
        }

        Object selectedItem = listaCuentasDeRevision_XSp.getSelectedItem();
        if (selectedItem == null) {
            return;
        }

        cuentaConciliableASumar3 = selectedItem.toString();

        A23_QueryResult<Void> resultadoSuma = a22QueryManager.querySumTransactionsByAccount(cuentaConciliableASumar3);
        sumaTransaccionesCuenta = resultadoSuma.getSuma();

        cuentaConciliableASumar2 = cuentaConciliacion_XSp.getSelectedItem().toString();
        A23_QueryResult<Void> sumaPorCuenta2 = a22QueryManager.querySumTransactionsByAccount(cuentaConciliableASumar2);
        sumaTransaccionesCuenta2 = sumaPorCuenta2.getSuma();

    }

    public void dynamicQueryByAllAccountAz() {

        resultadoCuentasOrdenAZ = a22QueryManager.queryAzAllAccounts();
    }

    public void dynamicQueryTransactionOneDocument(String argumento_String) {

        leeDocODescricionRecibido_String = a22QueryManager.queryTransactionsByDocumentDescription(descripcionABuscarEnPlantilla_String);

        A23_QueryResult<A3_2_TipoTransaccionesGetsYSets> transaccionesUnDocumento_Result = a22QueryManager.queryTransactionsByDocument(
                argumento_String);
        transaccionesUnDocumento_ArrayListTT_Result = transaccionesUnDocumento_Result.getDatos();

    }

    public void dynamicQueryAtributtesAccount() {

        if (cuentaConciliacion_XSp == null ||
                cuentaConciliacion_XSp.getAdapter() == null ||
                cuentaConciliacion_XSp.getSelectedItem() == null) {
            atributosCuenta_ArrayS = new String[0];
            return;
        }

        cuentaRegistroInicial_String = cuentaConciliacion_XSp.getSelectedItem().toString().trim();

        if (cuentaRegistroInicial_String.isEmpty()) {
            atributosCuenta_ArrayS = new String[0];
            return;
        }

        A23_QueryResult<String[]> obtenerAtributo =
                a22QueryManager.queryAttributesByAccount(cuentaRegistroInicial_String);

        if (obtenerAtributo == null || obtenerAtributo.getAtributosCuenta() == null) {
            atributosCuenta_ArrayS = new String[0];
            return;
        }

        atributosCuenta_ArrayS = obtenerAtributo.getAtributosCuenta();
    }

    private void recibirBundleDeVerItemTransaction(Bundle bundle) {
        try {
            if (bundle == null) {
                return;
            }

            // 🔒 VALIDAR que venga de F3_2_VerItemTransaccion
            if (!bundle.getBoolean("fromVerItemTransaccion", false)) {
                return;
            }

            // ✅ Confirmar que la bandera sigue activa
            vieneDeVerItemTransaccion = bundle.getBoolean("fromVerItemTransaccion", false);

            // Procesar el documento recibido
            String documentoRecibido = bundle.getString("keyDocumentNumber");

            if (documentoRecibido != null && !documentoRecibido.isEmpty()) {

                // ─────────────────────────────────────────────────────────────
                // CANAL D — Decisión central.
                // Llegamos aquí porque el usuario eligió un documento en F2.
                // Siempre aterrizamos en Área 3 (updateDelete).
                // La decisión depende de si el slot 3 tiene backup previo.
                // NO tocar esta lógica desde Canal A, B ni C.
                // ─────────────────────────────────────────────────────────────

                // Restablecer bandera de salida — ya regresamos de F2
                saliendoHaciaF2 = false;

                int slotUpdate = A1_1_AyudanteBD.AREA_UPDATE; // = 3
                boolean hayBackupEnSlot3 = A5_CacheManager.existeCache(
                        getContext(), slotUpdate);
                if (!hayBackupEnSlot3) {
                    // ─────────────────────────────────────────
                    // ESCENARIO A — Slot 3 vacío. Carga directa.
                    // ─────────────────────────────────────────
                    cargarDocumentoEnArea3CanalD(documentoRecibido);
                    mostrarMensajeLightCanalD("Documento cargado en Área 3");

                } else {
                    // ─────────────────────────────────────────
                    // ESCENARIOS B / C — Slot 3 tiene backup.
                    // Mostrar diálogo de dos botones.
                    // ─────────────────────────────────────────
                    mostrarDialogoCanalD(documentoRecibido);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Punto de entrada para cargar un documento directamente en Área 3
     * (updateDelete) SIN pasar por el autocomplete
     * documentoABuscarParaEditar_XATv — usado desde
     * A11_AuditoriaClasificacionDialogo cuando el usuario toca una fila y
     * F1_CrudDocumento ya está abierto (el diálogo se abrió desde el botón
     * de este mismo fragmento).
     *
     * Reutiliza tal cual recibirBundleDeVerItemTransaction() — la misma
     * lógica de Canal D que ya maneja el caso "hay backup pendiente en
     * Área 3" cuando se llega desde F3_2_VerItemTransaccion — sin tocarla.
     */
    public void cargarDocumentoDesdeAuditoria(String numeroDocumento) {
        cargarDocumentoDesdeAuditoria(numeroDocumento, null);
    }

    /**
     * Igual que cargarDocumentoDesdeAuditoria(String), pero además, si se
     * indica numeroItem, abre automáticamente el detalle de ESE ítem
     * puntual (mostrarCamposItem) apenas termine de cargarse el documento
     * — así el usuario aterriza directo en el registro con el error, sin
     * tener que buscarlo a mano en la lista del documento completo.
     *
     * Se abre automático tanto en el camino más común (Escenario A de
     * recibirBundleDeVerItemTransaction — Área 3 vacía, carga directa) como
     * cuando Área 3 tiene trabajo pendiente y el usuario elige
     * "EDITAR NUEVO O SOBREESCRIBIR" en el diálogo de dos botones — en ambos
     * casos el documento con el error termina cargado en Área 3, así que en
     * ambos tiene sentido aterrizar directo en el ítem (ver
     * B13_NavigationManager.mostrarDialogoCanalD()).
     *
     * La única excepción sigue siendo "GUARDAR AMBOS DOCUMENTOS": ahí el
     * documento de la auditoría NO se carga ahora (queda "en espera" y sigue
     * viéndose el documento que ya se estaba editando), así que no hay nada
     * que abrir todavía — el usuario debe ubicar el ítem a mano cuando más
     * tarde entre a ese documento en espera.
     */
    public void cargarDocumentoDesdeAuditoria(String numeroDocumento, String numeroItem) {
        if (numeroDocumento == null || numeroDocumento.isEmpty()) return;
        itemPendienteDeAuditoria_String = numeroItem;
        Bundle bundle = new Bundle();
        bundle.putString("keyDocumentNumber", numeroDocumento);
        bundle.putBoolean("fromVerItemTransaccion", true);
        recibirBundleDeVerItemTransaction(bundle);
    }

    /**
     * Consume (una sola vez) el ítem pendiente dejado por
     * cargarDocumentoDesdeAuditoria(documento, item) y, si lo encuentra ya
     * cargado en listaDocumento_ArrayLTT, abre su detalle automáticamente.
     *
     * Público porque B13_NavigationManager.mostrarDialogoCanalD() también la
     * llama (rama "EDITAR NUEVO O SOBREESCRIBIR" del diálogo de Área 3 con
     * trabajo pendiente) — ahí el documento sí termina cargado igual que en
     * el camino directo, así que también corresponde abrir el ítem.
     */
    public void abrirItemPendienteDeAuditoriaSiExiste() {
        if (itemPendienteDeAuditoria_String == null || itemPendienteDeAuditoria_String.isEmpty()) {
            return;
        }
        String itemBuscado = itemPendienteDeAuditoria_String;
        itemPendienteDeAuditoria_String = null; // se usa una sola vez

        if (listaDocumento_ArrayLTT == null) return;
        for (A3_2_TipoTransaccionesGetsYSets item : listaDocumento_ArrayLTT) {
            if (itemBuscado.equals(item.tipoT_2DocumentItems_String)) {
                mostrarCamposItem(item);
                return;
            }
        }
    }

    // En cualquier método de F1_CrudDocumento donde quieras cerrar F3_2_VerItemTransaccion
    public void cerrarVerItemTransaccion() {
        if (getActivity() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
        }
    }

    public void verTransaccionesPorCuentaConSpinner() {

        try {

            String accountToQuery = consultaPorCuentaYFechaEnOtroFragment_XSp.getSelectedItem().toString();

            Fragment fragment = new F3_2_VerItemTransaccion();

            Bundle bundle = new Bundle();
            bundle.putString("keyAccount", accountToQuery);
            fragment.setArguments(bundle);

            getActivity().getSupportFragmentManager().beginTransaction().
                    replace(R.id.contenedor_fragments_f0_Xf,
                            fragment).commit();
        } catch (Exception e) {
            Toast.makeText(getActivity(), "No se puede ver la vista", Toast.LENGTH_SHORT).show();
        }
    }

    public void seeAccountsDialogFragment(Boolean decision) {
        try {
            // ✅ CAMBIO CRÍTICO: usar getParentFragmentManager()
            F2_Cuentas f21_cuentas = (F2_Cuentas) getParentFragmentManager().findFragmentByTag("F21_CUENTAS");

            if (decision) {
                if (f21_cuentas == null) {
                    f21_cuentas = new F2_Cuentas();
                    // ✅ CAMBIO CRÍTICO: usar getParentFragmentManager()
                    f21_cuentas.show(getParentFragmentManager(), "F21_CUENTAS");
                }
                seeAccountsXChB.setChecked(false);
            } else {
                if (f21_cuentas != null) {
                    f21_cuentas.dismiss();
                }
            }
        } catch (Exception e) {
            Toast.makeText(getActivity(), "Error al mostrar vista", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public static void showSnackbar(View view, String message, int durationMillis,
                                    int backgroundColor, int textColor,
                                    int anchorView, String actionText,
                                    Integer actionColor, View.OnClickListener actionListener) {
        Snackbar snackbar = Snackbar.make(view, message, Snackbar.LENGTH_INDEFINITE);

        // Personalizar colores
        snackbar.setBackgroundTint(backgroundColor);
        snackbar.setTextColor(textColor);

        // Configurar vista ancla si se proporciona
        if (anchorView != 0) {
            snackbar.setAnchorView(anchorView);
        }

        // Configurar acción si se proporciona
        if (actionText != null && actionListener != null) {
            snackbar.setAction(actionText, actionListener);
            if (actionColor != null) {
                snackbar.setActionTextColor(actionColor);
            }
        }

        // Mostrar Snackbar
        snackbar.show();

        // Programar el cierre después del tiempo definido
        new Handler().postDelayed(snackbar::dismiss, durationMillis);
    }

    public void limpiarInformacionTemplateUpdateAlDisminuirCaracteres(int largo) {
        listaDocumento_ArrayLTT.clear();
        if (conexionListDocumentForGeneralWithListView_Adaptador1_TipoT != null) {
            listaDocumento_XLv.setAdapter(conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);
            conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.notifyDataSetChanged();
        }
    }

    public String deleteCsvBackupsCRUD(int opcion) {
        int areaId = A5_CacheManager.radioButtonToAreaId(
                opcion,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        A5_CacheManager.eliminar(getContext(), areaId);
        return "cache_eliminado_area_" + areaId; // retorno informativo (compatible con usos anteriores)
    }

    public void aplicarEncabezadoAVistas(A5_CacheManager.Encabezado enc) {
        // Restauración de CheckBoxes
        assignedOtherDateInCreateNew_XChB.setChecked("true".equals(enc.campo_0_otraFechaChb));
        assignedDateInTemplate_XChB.setChecked("true".equals(enc.campo_4_fechaChbTemplate));
        assignedOtherDateInUpdate_XChB.setChecked("true".equals(enc.campo_9_otraFechaUpChb));

        // Restauración de TextViews y AutoCompletes
        otherDateInCreateNew_XTv.setText(enc.campo_1_otraFechaTv);
        descripcionABuscarEnPlantilla_XAtv.setText(enc.campo_2_descPlantilla);
        dateInUpdate_XTv.setText(enc.campo_3_fechaUpdate);
        consecutivoNuevoDocEnPLantilla_XTv.setText(enc.campo_5_numDocPlantilla);
        dateInTemplate_XTv.setText(enc.campo_6_fechaTemplate);
        documentoYFechaInicialBaseDeLaPLantilla_XTv.setText(enc.campo_7_docFechaBase);
        documentoABuscarParaEditar_XATv.setText(enc.campo_8_docBuscarEditar);
        changeOfDateInUpdate_XTv.setText(enc.campo_10_cambioFechaUp);

        // Restauración de Spinners (Ajustar según tu lógica de posición)

        // Restauración de EditTexts
        inputPhysicalVsAccounting_XEt.setText(enc.campo_12_fisico);
        valor_XEt.setText(enc.campo_13_valor);
        descripcion_XAtv.setText(enc.campo_14_descripcion);

        // Cuentas y Signos
    }

    public void actualizarListView() {

        conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                new D_F1_AdaptadorCrudDocumento(getActivity(), listaDocumento_ArrayLTT, null);

        conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.notifyDataSetChanged();
        listaDocumento_XLv.setAdapter(conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);

    }

    public void actualizarSpinnerCuentasRevision() {
        if (listaCuentasDeRevision_XSp.getSelectedItem() != null &&
                !listaCuentasDeRevision_XSp.getSelectedItem().toString().equals("") &&
                !listaCuentasDeRevision_XSp.getSelectedItem().toString().equals("1 No conciliar")) {

            dynamicQuerySumByAccountAcordingToArgument();
            dynamicQueryByAllAccountAz();

            $sAc = Integer.parseInt(String.valueOf(sumaTransaccionesCuenta));

            // === NUEVO: Ajustar saldo si estamos en modo edición ===
            Integer saldoInicialAjustado = $sAc;

            if (documentoCargadoParaEdicion) {
                String cuentaSeleccionada = listaCuentasDeRevision_XSp.getSelectedItem().toString();

                if (movimientosOriginalesDocumento_HashMap.containsKey(cuentaSeleccionada)) {
                    Integer movimientoOriginal = movimientosOriginalesDocumento_HashMap.get(cuentaSeleccionada);
                    saldoInicialAjustado = $sAc - movimientoOriginal;
                }
            }

            saldoInicialeListaCuentasRevisar_XTv.setText("" + saldoInicialAjustado);

            sumarItemListaSaldosCuenta();

            Integer saldo2CuentaARevisar_Integer =
                    saldoInicialAjustado + sumaMovimientoItemListaCuentaRevision_Integer;
            saldoFinalListaCuentasRevisar_XTv.setText("" + saldo2CuentaARevisar_Integer);

            listaCuentasDeRevision_XSp.setBackgroundColor(Color.parseColor("#1de9b6"));
            listaCuentasDeRevision_XSp.setVisibility(View.VISIBLE);
            resetearSpinnerCuentasRevision();
        }
    }

    public void actualizarTotales() {
        sumarItemListaDocumento();
        actualizarSumasListado();
        controlACeroTotales_XTv.setText("" + $netoT);
    }

    public void procesarActualizacionCompleta() {
        digitarFisicoVsSaldoConciliacion();
        actualizarTotales();
        actualizarSpinnerCuentasRevision();
        actualizarListView();
    }

    public void realizarOperacionesPostSeleccion(Object selectedRadioButtonId) {

        dynamicQueryLastDocument();

        // Código comun después de que se pase la validación
        String nombreArchivo = CSV_DOCUMENT_TRANSACTIONS.getFileName();
        String folderId = "1zQ55zDrNlyr3nmcK-toeBLUKKC4tMZjO";
        csvDriveUploader = new A6_3_CSVDriveUploader();
        A6_3_CSVDriveUploader.guardarYSubirTransacciones(requireContext(), nombreArchivo, folderId, 10);

        clearViewsValuesForInitializeCRUD();
        listaDocumento_ArrayLTT.clear();
        deleteCsvBackupsCRUD((Integer) selectedRadioButtonId);

        numeroConsecutivoDocEnEdicion_XTv.setText(String.format("%04d", Integer.parseInt(documentoRecibido_Resultado_String)));

        Toast.makeText(getActivity(), "Backup local, en Drive y documento actualizado", Toast.LENGTH_SHORT).show();

    }

    public void colocarDocConsultadoEnListaItemDoc() {

        resetearSpinnerCuentasRevision();

        try {
            String leeDocODescricionABuscar_String = null;
            descripcionABuscarEnPlantilla_String = descripcionABuscarEnPlantilla_XAtv.getText().toString();

            dynamicQuerySumByAccountAcordingToArgument();
            dynamicQueryByAllAccountAz();

            if (!descripcionABuscarEnPlantilla_String.equals("")) {
                leeDocODescricionABuscar_String = leeDocODescricionRecibido_String;
            } else if (!documentoABuscarParaEditar_XATv.getText().toString().equals("")) {
                leeDocODescricionABuscar_String = documentoABuscarParaEditar_XATv.getText().toString();
            }

            dynamicQueryTransactionOneDocument(leeDocODescricionABuscar_String);
            dynamicQuerySumByAccountAcordingToArgument();
            dynamicQueryByAllAccountAz();

            if (transaccionesUnDocumento_ArrayListTT_Result.size() > 0) {

                // === NUEVO: Detectar si estamos en modo edición ===
                if (!documentoABuscarParaEditar_XATv.getText().toString().equals("")) {
                    documentoCargadoParaEdicion = true;
                    documentoEnEdicion_ID = leeDocODescricionABuscar_String;

                    // === CALCULAR MOVIMIENTOS ORIGINALES POR CUENTA ===
                    calcularMovimientosOriginalesDelDocumento();
                } else {
                    documentoCargadoParaEdicion = false;
                    movimientosOriginalesDocumento_HashMap.clear();
                }

                // ... resto del código original ...

                A3_2_TipoTransaccionesGetsYSets encabezado = (A3_2_TipoTransaccionesGetsYSets)
                        transaccionesUnDocumento_ArrayListTT_Result.get(0);
                documentoYFechaInicialBaseDeLaPLantilla_XTv.setText("  No. " + encabezado.tipoTget_1DocumentoMetodoEnA5() + " de " +
                        encabezado.tipoT_8DateOfDocument_Integer);

                A3_2_TipoTransaccionesGetsYSets encabezado2 = (A3_2_TipoTransaccionesGetsYSets)
                        transaccionesUnDocumento_ArrayListTT_Result.get(0);
                dateInUpdate_XTv.setText("" + transaccionesUnDocumento_ArrayListTT_Result.get(0).
                        tipoTget_8FechaInicialMetodoEnA5());

                fechayHoraTransacciones_String = encabezado2.tipoTget_7FechaYHoraMetodoEnA5();

                listaDocumento_ArrayLTT = (ArrayList<A3_2_TipoTransaccionesGetsYSets>) transaccionesUnDocumento_ArrayListTT_Result;

                final D_F1_AdaptadorCrudDocumento conexionListDocumentForGeneralWithListView_Adaptador1_TipoT = new D_F1_AdaptadorCrudDocumento(getActivity(),
                        listaDocumento_ArrayLTT, null);
                conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.notifyDataSetChanged();
                listaDocumento_XLv.setAdapter(conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);

                if (listaDocumento_ArrayLTT.size() > 0) {
                    consecutivoItemRegistro_XTv.setText("Item:" + "\n" + (listaDocumento_ArrayLTT.size() + "/" +
                            listaDocumento_ArrayLTT.size()));
                } else if (listaDocumento_ArrayLTT.size() == 0) {
                    consecutivoItemRegistro_XTv.setText("0/0");
                }

                sumarItemListaDocumento();
            }

        } catch (Exception e) {
            Log.e("DocumentLoad", "Error loading document into list", e);
            Toast.makeText(getActivity(),
                    "Error al cargar el documento", Toast.LENGTH_SHORT).show();
        }
    }

    //metodos para sumas
    public void actualizarSumasListado() {
        sumarItemListaDocumento();
        sumaPositivos_XTv.setText("Suma +" + "\n" + $mTP);
        sumaNegativos_XTv.setText("Suma -" + "\n" + $mTN);
        controlACeroTotales_XTv.setText("Dif." + "\n" + $netoT);
        $quedaPorRegistrar = $sAc - $valorAc - ($mTP - $mCP) + ($mTN * (-1) - $mCN * (-1));
    }

    // ANTES — borrar todo el cuerpo del método, dejar solo esto:
    public void sumarItemListaDocumento() {
        try {
            String cuentaAConciliar = (cuentaConciliacion_XSp.getAdapter() != null
                    && cuentaConciliacion_XSp.getAdapter().getCount() > 0
                    && cuentaConciliacion_XSp.getSelectedItem() != null)
                    ? cuentaConciliacion_XSp.getSelectedItem().toString() : "";

            sumasActuales = calculator.sumarItems(listaDocumento_ArrayLTT, cuentaAConciliar);

            // Copiar resultados a los camp os que el resto del código lee directamente
            $mTP = sumasActuales.mTP;
            $mTN = sumasActuales.mTN;
            $mCP = sumasActuales.mCP;
            $mCN = sumasActuales.mCN;
            $netoT        = sumasActuales.netoT;
            $netoMC       = sumasActuales.netoMC;
            $netoMTSinMC  = sumasActuales.netoMTSinMC;
            $netoTSinCaja = sumasActuales.netoTSinCaja;
        } catch (Exception e) {
            Log.e("F1", "Error en sumarItemListaDocumento", e);
        }
    }

    public void sumarItemListaSaldosCuenta() {
        sumarItemListaDocumento();
        actualizarSumasListado();

        if (listaCuentasDeRevision_XSp == null
                || listaCuentasDeRevision_XSp.getAdapter() == null
                || listaCuentasDeRevision_XSp.getSelectedItem() == null) return;

        String cuentaRevision = listaCuentasDeRevision_XSp.getSelectedItem().toString();
        sumaMovimientoItemListaCuentaRevision_Integer =
                calculator.sumarCuentaRevision(listaDocumento_ArrayLTT, cuentaRevision);

        movimientoListaCuentasRevisar_XTv.setText(
                "" + sumaMovimientoItemListaCuentaRevision_Integer);
    }


    //methods clear
    public void clearViewValuesAreaRecords() {
        valor_XEt.setText("");
        descripcion_XAtv.setText("");
        signo_XSp.setSelection(0);
        cuenta_XAtv.setText("");
        cuenta_XSp.setSelection(0);
    }

    public void clearViewsValuesForInitializeCRUD() {
        // Create new area
        estadoChBOtraFechaInicialEnAdicionar_String = null;
        assignedOtherDateInCreateNew_XChB.setChecked(false);
        otherDateInCreateNew_XTv.setText("");
        // Template area
        descripcionABuscarEnPlantilla_XAtv.setText("");
        assignedDateInTemplate_XChB.setChecked(false);
        dateInTemplate_XTv.setText("");
        documentoYFechaInicialBaseDeLaPLantilla_XTv.setText("");
        // Update/delete area
        dateInUpdate_XTv.setText("");
        documentoABuscarParaEditar_XATv.setText("");
        assignedOtherDateInUpdate_XChB.setChecked(false);
        changeOfDateInUpdate_XTv.setText("");
        // Common fields
        cuentaConciliacion_XSp.setSelection(0);
        saldoCuentaAconciliar_XTv.setText("");
        inputPhysicalVsAccounting_XEt.setText("");
        diferenciaAConciliar_XTv.setText("");
        consecutivoItemRegistro_XTv.setText("");
        clearViewValuesAreaRecords();
        sumaPositivos_XTv.setText("");
        sumaNegativos_XTv.setText("");
        controlACeroTotales_XTv.setText("");
        try {
            ordenAzZaPorCuentaConciliacion(ascending_Boolean);
        } catch (Exception e) {
            Log.e("UIState", "Error ordering accounts on clear", e);
        }
        ocultarTeclado();
    }

    public void clearArrayListsCRUD() {
        listaDocumento_ArrayLTT.clear();

        // itemsListaBackup_ArrayListTipoT puede ser null si nunca se inicializó
        if (itemsListaBackup_ArrayListTipoT != null) {
            itemsListaBackup_ArrayListTipoT.clear();
        }

        // Refresh adapter and ListView after clearing
        conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                new D_F1_AdaptadorCrudDocumento(getActivity(), listaDocumento_ArrayLTT, null);
        conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.notifyDataSetChanged();
        listaDocumento_XLv.setAdapter(conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);

        cuentasConciliablesDeItemsLista_ArrayList.clear();
        listaCuentasRevisionParaAdapterSpinner_ArrayListString.clear();
    }

    //metodos read bases de datos
    private void ordenAzZaPorCuentaConciliacion(boolean asc) {
        try {
            if (asc) {
                Collections.sort(cuentasConciliablesAZ_List, (o1, o2) -> o1.compareTo(o2));
            } else {
                Collections.sort(accountAllAz_List, (o1, o2) -> o1.compareTo(o2));
            }
            adaptadorCuentasConciliables.notifyDataSetChanged();
        } catch (Exception e) {
            Log.e("UIState", "Error ordering accounts list", e);
        }
    }

    public void numerarDocumentoConsecutivo() {
        dynamicQueryLastDocument();

        if (documentoRecibido_Resultado_String != null) {
            ultimoDocumentoEnLaTabla = documentoRecibido_Resultado_String;
        } else {
            ultimoDocumentoEnLaTabla = "0";
            Toast.makeText(getActivity(), "No hay registros", Toast.LENGTH_LONG).show();
        }

        nuevoNumeroDocEnAdicionar_String =
                calculator.siguienteNumeroDoc(ultimoDocumentoEnLaTabla);

        numeroConsecutivoDoc_XTv.setText(nuevoNumeroDocEnAdicionar_String);
        consecutivoNuevoDocEnPLantilla_XTv.setText(nuevoNumeroDocEnAdicionar_String);
    }

    //renumera los items de lista documento
    public void renumerarItemsListaDocumento() {
        calculator.renumerarItems(listaDocumento_ArrayLTT);

        conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                new D_F1_AdaptadorCrudDocumento(getActivity(), listaDocumento_ArrayLTT, null);
        conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.notifyDataSetChanged();
        listaDocumento_XLv.setAdapter(
                conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);

        if (listaDocumento_ArrayLTT.size() > 0) {
            consecutivoItemRegistro_XTv.setText("Item:\n" +
                    listaDocumento_ArrayLTT.size() + "/" + listaDocumento_ArrayLTT.size());
        } else {
            consecutivoItemRegistro_XTv.setText("0/0");
        }
    }

    @Override
    public void onResultadoConfirmado(String resultado) {
        if (campoActivo != null) {
            campoActivo.setText(resultado);
        }
    }

    @FunctionalInterface
    public interface BooleanSupplier {
        boolean get();
    }

    // Matriz que combina opciones, validaciones y mensajes
    private final Object[][] matrizValidaciones = new Object[][]{
            {R.id.create_XRb, (BooleanSupplier) () -> listaDocumento_ArrayLTT.size() == 0, "No hay registros en el Documento"},
            {R.id.create_XRb, (BooleanSupplier) () -> $netoT != 0, "El documento está descuadrado"},

            {R.id.template_XRb, (BooleanSupplier) () -> listaDocumento_ArrayLTT.size() == 0, "No hay registros en el Documento"},
            {R.id.template_XRb, (BooleanSupplier) () -> $netoT != 0, "El documento está descuadrado"},
            {R.id.template_XRb, (BooleanSupplier) () -> descripcionABuscarEnPlantilla_XAtv.getText().toString().isEmpty(), "Debe consultar un tipo de plantilla"},
            {R.id.template_XRb, (BooleanSupplier) () -> dateInTemplate_XTv.getText().toString().isEmpty(), "Debe asignar una fecha"},

            {R.id.updateDelete_XRb, (BooleanSupplier) () -> listaDocumento_ArrayLTT.size() == 0, "No hay registros en el Documento"},
            {R.id.updateDelete_XRb, (BooleanSupplier) () -> $netoT != 0, "El documento está descuadrado"},
            {R.id.updateDelete_XRb, (BooleanSupplier) () -> documentoABuscarParaEditar_XATv.getText().toString().isEmpty(), "Debe consultar un documento"},
    };

    public String validarCampos(int opcionSeleccionada) {
        for (Object[] fila : matrizValidaciones) {
            int opcion = (int) fila[0];
            BooleanSupplier validacion = (BooleanSupplier) fila[1];
            String mensajeError = (String) fila[2];

            if (opcion == opcionSeleccionada && validacion.get()) {
                return mensajeError; // Retorna el primer mensaje de error encontrado
            }
        }
        return null; // Todas las validaciones pasan
    }

    public void baseParaGuardarEnLaEnBDConListaDocumento(String nombreDelRadioButton) {
        persistence.baseParaGuardarEnLaEnBDConListaDocumento(nombreDelRadioButton);
    }

    public void digitarFisicoVsSaldoConciliacion() {
        persistence.digitarFisicoVsSaldoConciliacion();
    }

    public void primerRegistroAListaDocumentoCuentaConciliable() {
        persistence.primerRegistroAListaDocumentoCuentaConciliable();
    }

    public void losDemasRegistrosAListaDocumento() {
        persistence.losDemasRegistrosAListaDocumento();
    }

    private void guardarModificacion() {
        persistence.guardarModificacion();
    }

    public void pasarItemListaTodoResumidoAItemListaRevision() {
        ArrayList<String> nuevaLista =
                calculator.extraerCuentasUnicas(listaDocumento_ArrayLTT);

        listaCuentasRevisionParaAdapterSpinner_ArrayListString = nuevaLista;
        conectarListaCuentasRevisionConSpinner_arrayAdapterString = new ArrayAdapter<>(
                getActivity(),
                android.R.layout.simple_list_item_multiple_choice,
                listaCuentasRevisionParaAdapterSpinner_ArrayListString);
        conectarListaCuentasRevisionConSpinner_arrayAdapterString.notifyDataSetChanged();

        listaCuentasDeRevision_XSp.setSelection(0);
        listaCuentasDeRevision_XSp.setAdapter(
                conectarListaCuentasRevisionConSpinner_arrayAdapterString);
    }

    //metodos de instalacion de la aplicacion
    public void crearCarpetaDeLaAppBalance() {
        pedirPermisoAlDispositivo();
        File folder = new File(
                Environment.getExternalStorageDirectory() + File.separator + BALANCE_FOLDER);
        if (!folder.exists()) {
            folder.mkdirs();
        }
    }

    public void insertarCuentaVacia() {
        //Asigna la cuenta a buscar en la consulta de transacciones por cuenta

        A23_QueryResult<String> cuentas = a22QueryManager.queryAzAllAccounts();
        accountAllAz_List = cuentas.getDatos();

        adaptadorCuentaVaciaParaIniciar_ArrayAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_1,
                accountAllAz_List);
        adaptadorCuentaVaciaParaIniciar_ArrayAdapter.notifyDataSetChanged();

        if (accountAllAz_List.isEmpty()) {

            try {

                sqliteDatabase_Abstracta = ayudante_Class.getWritableDatabase();


                sqliteDatabase_Abstracta.execSQL("INSERT INTO " + "cuentas" + "(Item, Cuenta, Grupo1, Grupo2, Fecha ) " +
                        "VALUES ('0','','n a','n a','27/12/2020')");
            } catch (Exception e) {

            }

        } else {

            return;
        }
    }

    //metodos especiales

    // ===== ANIMACIÓN MEJORADA =====
    public void animarBotonModificar() {

        if (enModoModificacion) {

            // ✅ DETENER ANIMACIONES PREVIAS
            if (fabPulseAnimator != null && fabPulseAnimator.isRunning()) {
                fabPulseAnimator.cancel();
            }
            if (fabColorAnimator != null && fabColorAnimator.isRunning()) {
                fabColorAnimator.cancel();
            }

            // Resetear estado visual
            fabModificar.setTranslationY(0f);
            fabModificar.setScaleX(1f);
            fabModificar.setScaleY(1f);

            // ✅ FORZAR VISIBILIDAD ANTES DE MOSTRAR
            fabModificar.clearAnimation();
            fabModificar.setVisibility(View.VISIBLE);
            fabModificar.setAlpha(1f); // Asegurar opacidad completa
            fabModificar.show();

            // Animación de pulso vertical
            fabPulseAnimator = ObjectAnimator.ofFloat(fabModificar, "translationY", 0f, -15f, 0f);
            fabPulseAnimator.setDuration(1000);
            fabPulseAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            fabPulseAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
            fabPulseAnimator.start();

            // Animación de color
            fabColorAnimator = ObjectAnimator.ofObject(
                    fabModificar,
                    "backgroundTint",
                    new ArgbEvaluator(),
                    Color.parseColor("#FF5722"),
                    Color.parseColor("#FF9800"),


                    Color.parseColor("#FF5722")
            );
            fabColorAnimator.setDuration(1500);
            fabColorAnimator.setRepeatMode(ObjectAnimator.REVERSE);
            fabColorAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            fabColorAnimator.start();

        } else {
            // ✅ SOLO OCULTAR SI REALMENTE NO ESTAMOS EN MODO MODIFICACIÓN

            // Detener animaciones
            if (fabPulseAnimator != null) {
                fabPulseAnimator.cancel();
            }
            if (fabColorAnimator != null) {
                fabColorAnimator.cancel();
            }

            // Ocultar FAB
            fabModificar.hide();
            fabModificar.postDelayed(new Runnable() {
                @Override
                public void run() {
                    fabModificar.setVisibility(View.GONE);
                }
            }, 200); // Esperar a que termine la animación hide()

            // Resetear transformaciones
            fabModificar.setTranslationY(0f);
            fabModificar.setScaleX(1f);
            fabModificar.setScaleY(1f);
        }
    }

    public void pedirPermisoAlDispositivo() {
        // PERMISOS PARA ANDROID 6 O SUPERIOR
        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(getActivity(), new String[]
                            {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0);
        }
    }

    public void ocultarTeclado() {
        // ⭐ VALIDAR que el Fragment esté attached a la Activity
        if (getActivity() == null || !isAdded()) {
            return; // Salir si no hay Activity o el Fragment no está agregado
        }

        View vieww = getActivity().getCurrentFocus(); // Obtener la vista actual
        //Forzar el enfoque a una vista vacia.
        if (vieww == null) {
            // Si no hay una vista, usa la vista raíz del fragmento
            vieww = getView();
        }

        if (vieww != null) {
            InputMethodManager input = (InputMethodManager) (getActivity().getSystemService(Context.INPUT_METHOD_SERVICE));
            input.hideSoftInputFromWindow(vieww.getWindowToken(), 0);
        }
    }

    private void calcularMovimientosOriginalesDelDocumento() {
        resetearSpinnerCuentasRevision();
        movimientosOriginalesDocumento_HashMap =
                calculator.calcularMovimientosOriginales(listaDocumento_ArrayLTT);
    }

    public void resetearSpinnerCuentasRevision() {
        if (listaCuentasDeRevision_XSp != null &&
                listaCuentasDeRevision_XSp.getAdapter() != null &&
                listaCuentasDeRevision_XSp.getAdapter().getCount() > 0) {

            listaCuentasDeRevision_XSp.setSelection(0, false); // Seleccionar item vacío (posición 0)

            // Limpiar TextViews asociados
            saldoInicialeListaCuentasRevisar_XTv.setText("");
            movimientoListaCuentasRevisar_XTv.setText("");
            saldoFinalListaCuentasRevisar_XTv.setText("");

        }
    }

    public void restaurarListenerRadioGroup() {
        optionsDoc_XRg.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId != -1 && checkedId != currentRadioButtonId) {
                procesarCambioDeRadioButton(checkedId);
            }
        });
    }

    public boolean hayDatosEnAreaActual() {
        // Verificación directa sin método auxiliar
        boolean hayRegistros = listaDocumento_ArrayLTT != null
                && listaDocumento_ArrayLTT.size() > 0;
        boolean hayValorEt   = valor_XEt != null
                && !valor_XEt.getText().toString().isEmpty();
        boolean hayDesc      = descripcion_XAtv != null
                && !descripcion_XAtv.getText().toString().isEmpty();
        boolean hayDoc       = documentoABuscarParaEditar_XATv != null
                && !documentoABuscarParaEditar_XATv.getText().toString().isEmpty();
        boolean hayPlantilla = descripcionABuscarEnPlantilla_XAtv != null
                && !descripcionABuscarEnPlantilla_XAtv.getText().toString().isEmpty();

        return hayRegistros || hayValorEt || hayDesc || hayDoc || hayPlantilla;
    }

    public void hacerBackupSilencioso(int radioButtonId) {
        int areaId = A5_CacheManager.radioButtonToAreaId(
                radioButtonId,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        Log.d("hacer_backup","aqui 11 F1 # de registros: "+listaDocumento_ArrayLTT.size());
        // ✅ Registros primero — emula CSV_RECORDS
        A5_CacheManager.guardarRegistros(getContext(), areaId, listaDocumento_ArrayLTT);
        Log.d("hacer_backup","aqui 12 F1  # de registros:  "+listaDocumento_ArrayLTT.size());
        Log.d("hacer_backup","aqui 13 F1");
        
        // ✅ Encabezado después — emula CSV_HEADER
        A5_CacheManager.guardarEncabezado(getContext(), areaId, buildEncabezado());
        Log.d("hacer_backup","aqui 14 F1");

    }

    /**
     * Verifica si hay caché pendiente para el área indicada (antes revisaba File.exists()).
     * Se llama desde onResume() y procesarCambioDeRadioButton() — sin cambios en esos puntos.
     */
    public boolean existenBackupsPara(int radioButtonId) {

        int areaId = A5_CacheManager.radioButtonToAreaId(
                radioButtonId,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        return A5_CacheManager.existeCache(getContext(), areaId);
    }

    public void restoreBackups(int radioButtonId) {
        int areaId = A5_CacheManager.radioButtonToAreaId(
                radioButtonId,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        // 1. Limpiar antes de restaurar
        limpiarListaYAdaptador();

        // 2. Restaurar encabezado (18 campos de vistas)
        A5_CacheManager.Encabezado enc =
                A5_CacheManager.restaurarEncabezado(getContext(), areaId);
        if (enc != null) {
            aplicarEncabezadoAVistas(enc);
        }

        // 3. Restaurar registros
        listaDocumento_ArrayLTT = A5_CacheManager.restaurarRegistros(getContext(), areaId);

        if (listaDocumento_ArrayLTT == null) {
            listaDocumento_ArrayLTT = new ArrayList<>(); // Inicialización de seguridad
        }

        actualizarListView();

        // ⭐ AQUÍ APAGAMOS EL ESCUDO
        // Usamos un pequeño post para asegurar que el Spinner procese el cambio
        // antes de reactivar la lógica pesada.
        listaCuentasDeRevision_XSp.postDelayed(() -> estaRestaurando = false, 100);

        // 4. Actualizar contador
        int total = listaDocumento_ArrayLTT != null
                ? listaDocumento_ArrayLTT.size() : 0;
        consecutivoItemRegistro_XTv.setText(
                total > 0 ? "Item:\n" + total + "/" + total : "0/0");

        pasarItemListaTodoResumidoAItemListaRevision();
        sumarItemListaDocumento();
        actualizarSumasListado();
        inputPhysicalVsAccounting_XEt.requestFocus();
    }

    public void eliminarBackups(int radioButtonId) {
        int areaId = A5_CacheManager.radioButtonToAreaId(
                radioButtonId,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        A5_CacheManager.eliminar(getContext(), areaId);
    }

    //
    // 1
    public void setVisibilityGoneTodo() {
        uiState.setVisibilityGoneTodo();
    }

    // 2
    public void mostrarAreaCorrespondiente(int radioButtonId) {
        uiState.mostrarAreaCorrespondiente(radioButtonId);
    }

    // 3
    public void setvisibleCreateDespuesDeEditar() {
        uiState.setvisibleCreateDespuesDeEditar();
    }

    // 4
    public void setvisibleTemplateDespuesDeEditar() {
        uiState.setvisibleTemplateDespuesDeEditar();
    }

    // 5
    public void setvisibleUpdateAndDeleteDespuesDeRecibirBundle() {
        uiState.setvisibleUpdateAndDeleteDespuesDeRecibirBundle();
    }

    // 6
    public void setInvisibleTemplateAntesDeEditar() {
        uiState.setInvisibleTemplateAntesDeEditar();
    }

    // 7
    public void setInvisibleUpdateAndDeleteAntesDeEditar() {
        uiState.setInvisibleUpdateAndDeleteAntesDeEditar();
    }

    // 9
    public void setVisibilityVisibleAreasForUpdateAndDelete() {
        uiState.setVisibilityVisibleAreasForUpdateAndDelete();
    }

    public void ejecutarLimpiezaDeInterfaz() {
        clearViewsValuesForInitializeCRUD();

        if (listaDocumento_ArrayLTT != null) {
            listaDocumento_ArrayLTT.clear();
        }
        if (conexionListDocumentForGeneralWithListView_Adaptador1_TipoT != null) {
            conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.notifyDataSetChanged();
        }
        if (consecutivoItemRegistro_XTv != null) {
            consecutivoItemRegistro_XTv.setText("0/0");
        }
    }

    public void configurarValidadorProrrateable() {
        descripcion_XAtv.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String texto = s.toString();

                // Solo validar si contiene indicios de formato prorrateable
                if (!texto.contains("[") && !texto.contains("(")) {
                    ocultarTooltipValidacion();
                    return;
                }

                A10_3_ValidadorProrrateable.ResultadoValidacion resultado =
                        A10_3_ValidadorProrrateable.validarFormato(texto);

                if (!resultado.esValido) {
                    // ❌ Error en formato
                    mostrarTooltipValidacion(
                            "❌",
                            resultado.mensaje + "\n💡 " + resultado.sugerencia,
                            Color.parseColor("#D32F2F"), // Texto rojo
                            Color.parseColor("#FFEBEE")  // Fondo rojo claro
                    );
                } else if (!resultado.mensaje.isEmpty()) {
                    // ✅ Formato correcto
                    mostrarTooltipValidacion(
                            "✅",
                            resultado.mensaje,
                            Color.parseColor("#388E3C"), // Texto verde
                            Color.parseColor("#E8F5E9")  // Fondo verde claro
                    );
                } else {
                    ocultarTooltipValidacion();
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        // Ocultar popup cuando el campo pierde foco
        descripcion_XAtv.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                ocultarTooltipValidacion();
            }
        });
    }

    public void procesarSalidaApp() {
        // Hacer backup silencioso si hay datos
        if (hayDatosEnAreaActual()) {
            hacerBackupSilencioso(currentRadioButtonId);
        }

        // Ocultar todas las áreas
        setVisibilityGoneTodo();

        // Mostrar Snackbar de confirmación
        View rootView = getView();
        if (rootView != null) {
            showSnackbar(
                    rootView,
                    "¿Desea salir de la aplicación?",
                    5000,
                    Color.parseColor("#E91E63"),
                    Color.WHITE,
                    R.id.guardarCambiosALaBD,
                    "Aceptar",
                    Color.YELLOW,
                    view -> {
                        if (getActivity() != null) {
                            getActivity().finishAffinity();
                        }
                        Toast.makeText(getActivity(), "Aplicación cerrada", Toast.LENGTH_SHORT).show();
                    }
            );
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (saliendoHaciaF2) return;
        if (getContext() == null || currentRadioButtonId == 0) return;

        // ✅ GUARDIA — backup bueno ya existe, no sobreescribir
        if (estaRestaurandoCanalA) {
            Log.d("hacer_backup", "onPause — SKIP backup, restaurando canal A");
            return;
        }

        nombreIdrB = getResources().getResourceEntryName(currentRadioButtonId);
        int areaIdActual = A5_CacheManager.radioButtonToAreaId(
                currentRadioButtonId,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        getContext().getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
                .edit()
                .putInt("lastSelectedRadioButtonId", currentRadioButtonId)
                .apply();

        if (hayDatosEnAreaActual()) {
            hacerBackupSilencioso(currentRadioButtonId);
            vieneDeHome = true;
        } else {
            A5_CacheManager.eliminarRegistrosCache(getContext(), areaIdActual);
            vieneDeHome = false;
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        // ════════════════════════════════════════════════════════════════
        // NINGÚN CANAL tiene lógica aquí.
        // Solo cierre de DB como recurso del sistema.
        // ════════════════════════════════════════════════════════════════
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    // Router — evaluates flags in strict priority order, delegates to each canal.
    // Zero business logic here — routing only.
    // Priority: 1=CanalA (NavController)  2=CanalB (Home/system)
    //           3=CanalD (return from F2)  4=CanalInicio (first load)
    // Canal C never passes through onStart — it lives in onChange().

    @Override
    public void onStart() {
        super.onStart();
        if (getActivity() == null || !isAdded() || getView() == null) return;
        if (getContext() == null) return;

        // ✅ Recuperar bandera que sobrevive muerte de proceso
        if (savedInstanceState != null
                && savedInstanceState.getBoolean("vieneDeNavController", false)) {
            vieneDeNavController = true;
            currentRadioButtonId = savedInstanceState.getInt("radioButtonIdCanalA", 0);
        }

        if (vieneDeNavController) { ejecutarCanalA(); return; }
        if (vieneDeHome)          { ejecutarCanalB(); return; }
        if (vieneDeVerItemTransaccion) { ejecutarCanalD(); return; }

        ejecutarCanalInicio();
    }

    private void ejecutarCanalInicio() {
        navManager.ejecutarCanalInicio();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        vieneDeNavController = true;

        if (getContext() != null && currentRadioButtonId != 0) {
            getContext().getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("lastSelectedRadioButtonId", currentRadioButtonId)
                    .putInt("areaGuardadaCanalA", currentRadioButtonId)
                    .apply();
        }

        // ✅ onPause() ya hizo el backup — aquí solo SharedPreferences
        // No es necesario guardar de nuevo, el proceso puede haber cambiado
        Log.d("CanalA", "onDestroyView — backup ya fue hecho en onPause()");

        ocultarTooltipValidacion();
        if (popupValidacion != null) popupValidacion = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        // Intentionally empty — restoration logic runs in onStart()
    }

    // ═══════════════════════════════════════════════════════════════
    // CANAL A — Regreso desde el menú lateral
    // Comportamiento: recreación total del Fragment.
    // El backup ya se hizo en onDestroyView() al salir.
    // Aquí solo se restaura el área correcta y se ofrece el diálogo.
    // ═══════════════════════════════════════════════════════════════

    private void ejecutarCanalA() {
        navManager.ejecutarCanalA();
    }

    private void ejecutarCanalB() {
        navManager.ejecutarCanalB();
    }

    private void ejecutarCanalD() {
        navManager.ejecutarCanalD();
    }

    private void procesarCambioDeRadioButton(int nuevoRadioButtonId) {
        navManager.procesarCambioDeRadioButton(nuevoRadioButtonId);
    }

    public void mostrarDialogoRestauracionUnificado(int radioButtonIdDestino, boolean esDesdeOnResume) {
        navManager.mostrarDialogoRestauracionUnificado(radioButtonIdDestino, esDesdeOnResume);
    }

    /** Escenario C — "✏️ Editar esta área" desde A9_VisorTablasDialogo. */
    public void irAAreaYRestaurarDesdeVisor(int areaId) {
        navManager.irAAreaYRestaurarDesdeVisor(areaId);
    }

    /** Escenario C — "🔄 Enviar a Área 3" desde la pestaña "Espera" del panel de auditoría. */
    public void irAEsperaYIntercambiarDesdeVisor() {
        navManager.irAEsperaYIntercambiarDesdeVisor();
    }

    /**
     * Escenario C — "🗑️ Borrar backup" desde A9_VisorTablasDialogo. Si el
     * área que se acaba de borrar en el panel es la que está visible ahora
     * mismo en esta pantalla, también se limpia visualmente (si no, el
     * usuario seguía viendo en pantalla datos que ya no existen en caché).
     */
    public void limpiarAreaVisibleSiCoincide(int areaId) {
        int areaVisible = A5_CacheManager.radioButtonToAreaId(
                currentRadioButtonId,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);
        if (areaVisible == areaId) {
            ejecutarLimpiezaDeInterfaz();
        }
    }
    private void mostrarDialogoCanalD(String documentoRecibido) {
        navManager.mostrarDialogoCanalD(documentoRecibido);
    }

    private void cargarDocumentoEnArea3CanalD(String documentoRecibido) {
        navManager.cargarDocumentoEnArea3CanalD(documentoRecibido);
        abrirItemPendienteDeAuditoriaSiExiste();
    }

    public void intercambiarSlot3YSlot4CanalD() {
        navManager.intercambiarSlot3YSlot4CanalD();
    }

    public void actualizarVisibilidadBotonVerde() {
        navManager.actualizarVisibilidadBotonVerde();
    }

    private void mostrarMensajeLightCanalD(String mensaje) {
        navManager.mostrarMensajeLightCanalD(mensaje);
    }

    //limpiarListaYAdaptador() — método nuevo auxiliar que centraliza la limpieza:
    public void limpiarListaYAdaptador() {
        if (listaDocumento_ArrayLTT != null) {
            listaDocumento_ArrayLTT.clear();
        }
        if (conexionListDocumentForGeneralWithListView_Adaptador1_TipoT != null) {
            conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.notifyDataSetChanged();
        }
        if (consecutivoItemRegistro_XTv != null) {
            consecutivoItemRegistro_XTv.setText("0/0");
        }
    }

    //verTransaccionesPorCuentaConAutocompeteText() — hacer backup explícito ANTES de navegar, no depender de onPause():
    public void verTransaccionesPorCuentaConAutocompeteText() {
        try {
            // ⭐ CANAL D — Backup silencioso de salida hacia F2.
            // Respeta identidad referencial: cada área guarda en su slot propio.
            // create_XRb→slot1, template_XRb→slot2, updateDelete_XRb→slot3.
            // El slot 3 NO se toca si el usuario viene de área 1 o 2.
            saliendoHaciaF2 = true;

            int areaIdSalida = A5_CacheManager.radioButtonToAreaId(
                    currentRadioButtonId,
                    R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);
            if (hayDatosEnAreaActual()) {
                hacerBackupSilenciosoCanalD(areaIdSalida);
                //hacerBackupSilencioso(currentRadioButtonId);//aqui 1
                Log.d("foco", "aqui 2");

            }

            // Persistir área origen para referencia
            getActivity().getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE)
                    .edit()
                    .putInt("areaOrigenCanalD", areaIdSalida)
                    .apply();

            String accountToQuery = buscarCuentaParaTransaccionest_XAT
                    .getText().toString();

            Fragment fragment = new F3_2_VerItemTransaccion();
            Bundle bundle = new Bundle();
            bundle.putString("keyAccount", accountToQuery);
            fragment.setArguments(bundle);

            getActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.contenedor_fragments_f0_Xf, fragment)
                    .addToBackStack("F3_2_VerItemTransaccion")
                    .commit();

            saliendoHaciaF2 = false;

        } catch (Exception e) {
            saliendoHaciaF2 = false;
            Toast.makeText(getActivity(),
                    "No se puede ver la vista", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Este método está GARANTIZADO antes de matar el proceso
        if (getContext() == null || currentRadioButtonId == 0) return;
        if (!hayDatosEnAreaActual()) return;

        // Backup inmediato — SQLite persiste aunque el proceso muera
        hacerBackupSilencioso(currentRadioButtonId);

        // Guardar bandera para que onStart() sepa que debe restaurar
        outState.putInt("radioButtonIdCanalA", currentRadioButtonId);
        outState.putBoolean("vieneDeNavController", true);

        Log.d("CanalA", "onSaveInstanceState — backup garantizado area="
                + currentRadioButtonId);
    }

    // Builds a cache header snapshot from all current view values.
    // Called by both hacerBackupSilencioso and hacerBackupSilenciosoCanalD.
    private A5_CacheManager.Encabezado buildEncabezado() {
        A5_CacheManager.Encabezado enc = new A5_CacheManager.Encabezado();

        enc.campo_0_otraFechaChb     = String.valueOf(assignedOtherDateInCreateNew_XChB.isChecked());
        enc.campo_1_otraFechaTv      = otherDateInCreateNew_XTv.getText().toString();
        enc.campo_2_descPlantilla    = descripcionABuscarEnPlantilla_XAtv.getText().toString();
        enc.campo_3_fechaUpdate      = dateInUpdate_XTv.getText().toString();
        enc.campo_4_fechaChbTemplate = String.valueOf(assignedDateInTemplate_XChB.isChecked());
        enc.campo_5_numDocPlantilla  = consecutivoNuevoDocEnPLantilla_XTv.getText().toString();
        enc.campo_6_fechaTemplate    = dateInTemplate_XTv.getText().toString();
        enc.campo_7_docFechaBase     = documentoYFechaInicialBaseDeLaPLantilla_XTv.getText().toString();
        enc.campo_8_docBuscarEditar  = documentoABuscarParaEditar_XATv.getText().toString();
        enc.campo_9_otraFechaUpChb   = String.valueOf(assignedOtherDateInUpdate_XChB.isChecked());
        enc.campo_10_cambioFechaUp   = changeOfDateInUpdate_XTv.getText().toString();

        enc.campo_11_cuentaConcilia  = (cuentaConciliacion_XSp.getAdapter() != null
                && cuentaConciliacion_XSp.getAdapter().getCount() > 0)
                ? cuentaConciliacion_XSp.getSelectedItem().toString() : "";

        enc.campo_12_fisico          = inputPhysicalVsAccounting_XEt.getText().toString();
        enc.campo_13_valor           = valor_XEt.getText().toString();
        enc.campo_14_descripcion     = descripcion_XAtv.getText().toString();

        enc.campo_15_signo           = (signo_XSp.getAdapter() != null
                && signo_XSp.getAdapter().getCount() > 0)
                ? signo_XSp.getSelectedItem().toString() : "";

        enc.campo_16_cuentaAtv       = cuenta_XAtv.getText().toString();
        enc.campo_17_cuentaSp        = (cuenta_XSp.getAdapter() != null
                && cuenta_XSp.getAdapter().getCount() > 0)
                ? cuenta_XSp.getSelectedItem().toString() : "";

        return enc;
    }

    public void hacerBackupSilenciosoCanalD(int areaId) {
        // ✅ Registros primero — emula CSV_RECORDS
        A5_CacheManager.guardarRegistros(getContext(), areaId, listaDocumento_ArrayLTT);
        Log.d("hacer_backup","aqui 21 F1");

        // ✅ Encabezado después — emula CSV_HEADER
        A5_CacheManager.guardarEncabezado(getContext(), areaId, buildEncabezado());
        Log.d("hacer_backup","aqui 22 F1");
    }

    void mostrarTooltipValidacion(String icono, String mensaje, int colorTexto, int colorFondo) {
        try {
            // Crear popup si no existe
            if (popupValidacion == null) {
                View popupView = LayoutInflater.from(getContext())
                        .inflate(R.layout.f1_2_popup_validacion_formato, null);

                popupValidacion = new PopupWindow(
                        popupView,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        false
                );
                popupValidacion.setElevation(10f);
                popupValidacion.setOutsideTouchable(true);
            }

            // Actualizar contenido
            View contentView = popupValidacion.getContentView();
            TextView tvIcono = contentView.findViewById(R.id.tvIconoPopup_XTv);
            TextView tvMensaje = contentView.findViewById(R.id.tvMensajePopup_XTv);

            tvIcono.setText(icono);
            tvMensaje.setText(mensaje);
            tvMensaje.setTextColor(colorTexto);
            contentView.setBackgroundColor(colorFondo);

            // Mostrar ARRIBA del campo de descripción
            if (!popupValidacion.isShowing()) {
                // Medir el popup para posicionarlo correctamente
                contentView.measure(
                        View.MeasureSpec.UNSPECIFIED,
                        View.MeasureSpec.UNSPECIFIED
                );

                int popupHeight = contentView.getMeasuredHeight();
                int yOffset = -(descripcion_XAtv.getHeight() + popupHeight + 8); // 8dp margen

                popupValidacion.showAsDropDown(descripcion_XAtv, 0, yOffset);
            } else {
                popupValidacion.update();
            }

        } catch (Exception e) {
            Log.e("ValidadorPopup", "Error mostrando tooltip: " + e.getMessage());
        }
    }
    void ocultarTooltipValidacion() {
        if (popupValidacion != null && popupValidacion.isShowing()) {
            popupValidacion.dismiss();
        }
    }

    /**
     * Captura el estado actual de vistas y lista en memoria.
     * Llamar SIEMPRE mientras las vistas estén activas (onPause, onChange, etc.)
     */
    public void actualizarSnapshot() {
        if (getView() == null) return; // vistas ya destruidas, no hacer nada
        encabezadoSnapshot = buildEncabezado();
        listaSnapshot = listaDocumento_ArrayLTT != null
                ? new ArrayList<>(listaDocumento_ArrayLTT)
                : new ArrayList<>();
    }
}
