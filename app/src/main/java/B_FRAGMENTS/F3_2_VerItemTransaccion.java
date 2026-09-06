package B_FRAGMENTS;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
import static A1BASES.A4_TemplatePDF.document;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.jj.appbalancev31.R;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import A1BASES.A1_1_AyudanteBD;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A4_TemplatePDF;
import A1BASES.A7_SharedViewModel;
import A1BASES.A99_MetodosVarios;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;
import D_ADAPTERS.D_F3_2_AdaptadorTransaccionesCuentaYoFecha;
//import a4.balance.R;

public class F3_2_VerItemTransaccion extends DialogFragment {

    // Variables de clase - NUEVAS para el flujo de Indicadores
    private int anioRecibido = -1;
    private int mesRecibido = -1;
    private boolean desdeIndicadores = false;

    private boolean vieneDeVerItemTransaccion = false;
    ImageButton limpiarChecks_XBt;
    Button filtroVerificadas_XBt, filtroSinVerificar_XBt;
    TextView sumaVerificadas_XTv;
    TextView contadorVerificadas_XTv;
    private EditText buscar_XTv;
    private Handler handlerBusqueda = new Handler();
    private Runnable runnableBusqueda;
    private static final int DELAY_BUSQUEDA = 300; // 300ms de delay para debouncing


    //Adaptadores
    ArrayAdapter transaccionesPorCuenta_ArrayAdapter;

    //Elementos de diseño
    Button compartirEstadoDeCuenta_XBt;
    Button crearPdfConTablaPorCuentaYFecha_XBt;
    CalendarView calendarView_XCv;
    ImageButton salida_XBt;
    LinearLayout consultasPorCuentaYFecha_XLl;
    LinearLayout areaCalendario_XGl;
    TextView fecha1Calendario_XTv;
    TextView fecha2Calendario_XTv;
    TextView contenidoCuentaPdf_XTv;
    TextView c3_Cuenta_XTv,c5_Valor_XTv,c5_0_Valor_XTv,etiquetaCuenta_XBt,etiquetaMovimiento_XBt;

    //Constructores
    public F3_2_VerItemTransaccion(int x, int y) {
        dialogx = x;
        dialogy = y;
    }
    public F3_2_VerItemTransaccion() {
    }

    //Variables
    ArrayAdapter consultaTransaccionesCuentayFechaInicialArrayAdapter = null;

    public int dialogx;
    public int dialogy;
    public int desplazamientoY;
    Integer integerFecha2 = 0;
    Integer saldoAnterior_int;
    Integer integerFecha1 = 0;
    private String [] header = {"Fecha","Documento","Descripcion","Valor"};
    private String shortText;
    private  String longText;
    private  String numeroPagina_String2 = "Pagina #: ";
    public static String receivedAccount,stringRecibidoC4,stringRecibidoC5;
    public  static File pdfFile;
    String fecha1_String = "";
    String fecha1_StringFormato ="";
    String stringFecha2 = "";
    String stringFecha2Formato ="";
    String numeroPagina_String;
    String fechaYHora_String;

    //Instancias
    private A4_TemplatePDF a5_templatePDF;
    ArrayList<A3_2_TipoTransaccionesGetsYSets> transaccionesFiltradasSoloCuenta_ArrayList;
    ArrayList<A3_2_TipoTransaccionesGetsYSets> transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT;
    private SQLiteDatabase db;
    ArrayList<A3_2_TipoTransaccionesGetsYSets> transaccionesPorCuenta_Result_ArrayLTT;

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    //SQLiteDatabase db;
    A1_1_AyudanteBD ayudante_Class;
    A22_QueryManager a22QueryManager;
    String[] datosTabularesSoloCuenta;
    int sumaPorCuenta_int;
    int sumaPorCuentaYFecha_int;
    D_F3_2_AdaptadorTransaccionesCuentaYoFecha adaptadorTransaccionesCuentaYoFecha;
    private RecyclerView recyclerViewTransacciones_XRv;

    private F6_Calculadora                 calculadora_Fragment;
    Button calculadoraLibre_XBt;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true); // Establecer como no modal, sale del dialogo a un click fuera del dialogo
        //getDialog().setCanceledOnTouchOutside(true);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Dialog dialogo_Dialog = new Dialog(getActivity(),android.R.style.Theme_Translucent_NoTitleBar);
        final View inflarViews_View = getActivity().getLayoutInflater().inflate(R.layout.f3_2_and_f3_4_ver_item_transacciones, null);

        final Drawable d_Drawable = new ColorDrawable(Color.BLACK);
        d_Drawable.setAlpha(200);

        //foco 13
        // Abrimos la base de datos cuando empieza la operación
        if (ayudante_Class != null) {
            ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null,  version1BalanceSqlite_int_PSF);
            db = ayudante_Class.getReadableDatabase();
        } else {
        }


        dialogo_Dialog.getWindow().setBackgroundDrawable(d_Drawable);
        dialogo_Dialog.getWindow().setContentView(inflarViews_View);

        final WindowManager.LayoutParams layoutParams = dialogo_Dialog.getWindow().getAttributes();
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER;

        dialogo_Dialog.setCanceledOnTouchOutside(true);

        return dialogo_Dialog;

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Abrimos la base de datos cuando empieza la operación
        if (ayudante_Class != null) {
            ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
            db = ayudante_Class.getReadableDatabase();
        }

        View inflarViews_View = inflater.inflate(R.layout.f3_2_and_f3_4_ver_item_transacciones, container, false);

        calculadora_Fragment = new F6_Calculadora();
        calculadoraLibre_XBt = inflarViews_View.findViewById(R.id.calculadoraLibre_XBt);

        // TODO TU CASTING EXISTENTE (sin cambios)
        limpiarChecks_XBt = inflarViews_View.findViewById(R.id.limpiarChecks_XBt);
        filtroVerificadas_XBt = inflarViews_View.findViewById(R.id.filtroVerificadas_XBt);
        filtroSinVerificar_XBt = inflarViews_View.findViewById(R.id.filtroSinVerificar_XBt);
        sumaVerificadas_XTv = inflarViews_View.findViewById(R.id.sumaVerificadas_XTv);
        recyclerViewTransacciones_XRv = inflarViews_View.findViewById(R.id.recyclerViewTransacciones_XRv);
        recyclerViewTransacciones_XRv.setLayoutManager(new LinearLayoutManager(getContext()));
        calendarView_XCv = (CalendarView) inflarViews_View.findViewById(R.id.calendarView_XCv);
        compartirEstadoDeCuenta_XBt = (Button) inflarViews_View.findViewById(R.id.compartirEstadoDeCuenta_XBt);
        salida_XBt = (ImageButton) inflarViews_View.findViewById(R.id.salida_XBt);
        crearPdfConTablaPorCuentaYFecha_XBt = (Button) inflarViews_View.findViewById(R.id.crearPdfConTablaPorCuentaYFecha_XBt);
        etiquetaMovimiento_XBt = (TextView) inflarViews_View.findViewById(R.id.etiquetaMovimiento_XBt);
        etiquetaCuenta_XBt = (TextView) inflarViews_View.findViewById(R.id.etiquetaCuenta_XBt);
        consultasPorCuentaYFecha_XLl = (LinearLayout) inflarViews_View.findViewById(R.id.consultasPorCuentaYFecha_XLl);
        areaCalendario_XGl = (LinearLayout) inflarViews_View.findViewById(R.id.areaCalendario_XGl);
        c3_Cuenta_XTv = (TextView) inflarViews_View.findViewById(R.id.c3_Cuenta_XTv);
        c5_Valor_XTv = (TextView) inflarViews_View.findViewById(R.id.c5_Valor_XTv);
        c5_0_Valor_XTv = (TextView) inflarViews_View.findViewById(R.id.c5_0_Valor_XTv);
        fecha1Calendario_XTv = (TextView) inflarViews_View.findViewById(R.id.fecha1Calendario_XTv);
        fecha2Calendario_XTv = (TextView) inflarViews_View.findViewById(R.id.fecha2Calendario_XTv);
        contenidoCuentaPdf_XTv = (TextView) inflarViews_View.findViewById(R.id.contenidoCuentaPdf_XTv);
        buscar_XTv = (EditText) inflarViews_View.findViewById(R.id.buscar_XTv);
        contadorVerificadas_XTv = inflarViews_View.findViewById(R.id.contadorVerificadas_XTv);

        a5_templatePDF = new A4_TemplatePDF(requireContext());

        // Uso de variables
        Date d = new Date();
        SimpleDateFormat fecc = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
        fechaYHora_String = fecc.format(d);

        // Instancias
        a22QueryManager = new A22_QueryManager(getActivity());

        // ⭐ CAMBIO CRÍTICO 1: Recibir Bundle PRIMERO
        recibirBundle();

        // ⭐ CAMBIO CRÍTICO 2: Verificar si viene de Indicadores ANTES del diálogo
        if (!verificarYProcesarDesdeIndicadores()) {
            dialogoInterface();
        }

        listenerCalendario();
        listenerOtros();
        listenerPdf();

        recyclerViewTransacciones_XRv.setLayoutManager(new LinearLayoutManager(getActivity()));

        // ⭐ CAMBIO CRÍTICO 3: NO ejecutar estos métodos aquí si viene de Indicadores
        // Se ejecutarán en verificarYProcesarDesdeIndicadores()
        if (!desdeIndicadores) {
            dynamic$Valores();
            dynamicqueryByAccount();
            dynamicqueryByAccountAndDateRange();
        }

        A7_SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(A7_SharedViewModel.class);

        viewModel.getClearRecyclerView().observe(getViewLifecycleOwner(), shouldClear -> {
            if (shouldClear != null && shouldClear) {
                adaptadorTransaccionesCuentaYoFecha.clearData();
            }
        });

        configurarBuscador();
        configurarFiltrosCheck();

        if (calculadoraLibre_XBt != null) {
            calculadoraLibre_XBt.setOnClickListener(v -> mostrarCalculadoraLibre());
        }

        return inflarViews_View;
    }

    // Agregar este método completo en cualquier parte de la clase F32_VerItemTransaccion

    private void configurarBuscador() {
        buscar_XTv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No se necesita implementación
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Cancelar la búsqueda anterior si el usuario sigue escribiendo
                if (runnableBusqueda != null) {
                    handlerBusqueda.removeCallbacks(runnableBusqueda);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Crear un nuevo Runnable para la búsqueda
                runnableBusqueda = new Runnable() {
                    @Override
                    public void run() {
                        String textoBusqueda = s.toString().trim();

                        // Solo filtrar si hay 3 o más caracteres, o si está vacío (para mostrar todo)
                        if (textoBusqueda.length() >= 1 || textoBusqueda.length() == 0) {
                            if (adaptadorTransaccionesCuentaYoFecha != null) {
                                adaptadorTransaccionesCuentaYoFecha.getFilter().filter(textoBusqueda);
                            }
                        }
                    }
                };

                // Ejecutar la búsqueda después del delay (debouncing)
                handlerBusqueda.postDelayed(runnableBusqueda, DELAY_BUSQUEDA);
            }
        });
    }

    private void abrirFragmentoConDocumento(String documento) {
        try {
            Bundle bundle = new Bundle();
            bundle.putString("keyDocumentNumber", documento);
            bundle.putBoolean("fromVerItemTransaccion", true); // 🔑 MARCADOR de origen

            Fragment fragment = new F1_CrudDocumento();
            fragment.setArguments(bundle);

            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.contenedor_fragments_f0_Xf, fragment)
                    .addToBackStack(null)
                    .commit();
        } catch (Exception e) {
            Toast.makeText(getActivity(), "No se puede ver la vista", Toast.LENGTH_SHORT).show();
        }
    }

    public void dynamicqueryByAccount() {
        A23_QueryResult transaccionesFiltradasSoloCuenta_ArrayList_Result =
                a22QueryManager.queryTransactionsByAccount(receivedAccount);
        transaccionesFiltradasSoloCuenta_ArrayList = transaccionesFiltradasSoloCuenta_ArrayList_Result.getDatos();

        ArrayList<A3_2_TipoTransaccionesGetsYSets> listaFiltrada = new ArrayList<>();

        if (transaccionesFiltradasSoloCuenta_ArrayList != null) {

            // ⭐ NUEVO: Variables para filtro de fechas (solo para Indicadores)
            int fechaInicio = -1;
            int fechaFin = -1;

            // ⭐ Solo calcula fechas SI viene de Indicadores
            if (desdeIndicadores && anioRecibido != -1 && mesRecibido != -1) {
                Calendar cal = Calendar.getInstance();
                cal.set(anioRecibido, mesRecibido - 1, 1);
                int diasDelMes = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

                fechaInicio = anioRecibido * 10000 + mesRecibido * 100 + 1;
                fechaFin = anioRecibido * 10000 + mesRecibido * 100 + diasDelMes;

                Log.d("FiltroFechas", "Filtrando del " + fechaInicio + " al " + fechaFin);
            }

            for (A3_2_TipoTransaccionesGetsYSets transaccion : transaccionesFiltradasSoloCuenta_ArrayList) {
                String columna1 = transaccion.tipoTget_1DocumentoMetodoEnA5();
                int columna2 = transaccion.tipoTget_5ValorMetodoEnA5();
                String columna3 = transaccion.tipoTget_6DescripcionMetodoEnA5();
                int columna4 = transaccion.tipoTget_8FechaInicialMetodoEnA5();

                // ⭐ FILTRO CONDICIONAL: Solo aplica si viene de Indicadores
                if (desdeIndicadores && fechaInicio != -1 && fechaFin != -1) {
                    if (columna4 < fechaInicio || columna4 > fechaFin) {
                        continue; // ✅ Solo salta transacciones fuera del rango
                    }
                }
                // ✅ Si NO viene de Indicadores, agrega TODAS las transacciones (comportamiento original)

                A3_2_TipoTransaccionesGetsYSets transaccionReducida =
                        new A3_2_TipoTransaccionesGetsYSets(columna1, columna2, columna3, columna4);
                listaFiltrada.add(transaccionReducida);
            }

            // ✅ TODO TU CÓDIGO DE ORDENAMIENTO Y ADAPTADOR SIN CAMBIOS
            Collections.sort(listaFiltrada, new Comparator<A3_2_TipoTransaccionesGetsYSets>() {
                @Override
                public int compare(A3_2_TipoTransaccionesGetsYSets t1, A3_2_TipoTransaccionesGetsYSets t2) {
                    int fechaCompare = Integer.compare(
                            t2.tipoTget_8FechaInicialMetodoEnA52(),
                            t1.tipoTget_8FechaInicialMetodoEnA52()
                    );
                    if (fechaCompare != 0) {
                        return fechaCompare;
                    }
                    return t1.tipoTget_1DocumentoMetodoEnA52()
                            .compareTo(t2.tipoTget_1DocumentoMetodoEnA52());
                }
            });

            if (adaptadorTransaccionesCuentaYoFecha == null) {
                adaptadorTransaccionesCuentaYoFecha = new D_F3_2_AdaptadorTransaccionesCuentaYoFecha(
                        getActivity(), listaFiltrada,
                        documento -> {
                            abrirFragmentoConDocumento(documento);
                            Log.d("VerButtons", "Documento seleccionado: " + documento);
                        }
                );
                adaptadorTransaccionesCuentaYoFecha.setCheckUpdateListener(() -> actualizarContador());
                recyclerViewTransacciones_XRv.setAdapter(adaptadorTransaccionesCuentaYoFecha);
                actualizarContador();
            } else {
                adaptadorTransaccionesCuentaYoFecha.updateData(listaFiltrada);
                actualizarContador();
            }

            if (buscar_XTv != null) {
                buscar_XTv.setText("");
            }

        } else {
            Toast.makeText(getActivity(), "La lista de transacciones está vacía o es nula.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void dynamicqueryByAccountAndDateRange() {
        A23_QueryResult transaccionesFiltradasPorCuentaYRangoFecha_Result =
                a22QueryManager.queryFilteredTransactionsByAccountAndDateRange(receivedAccount, integerFecha1, integerFecha2);
        transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT = transaccionesFiltradasPorCuentaYRangoFecha_Result.getDatos();

        ArrayList<A3_2_TipoTransaccionesGetsYSets> listaFiltrada = new ArrayList<>();

        if (transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT != null) {
            for (A3_2_TipoTransaccionesGetsYSets transaccion : transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT) {
                String columna1 = transaccion.tipoTget_1DocumentoMetodoEnA5();
                int columna2 = transaccion.tipoTget_5ValorMetodoEnA5();
                String columna3 = transaccion.tipoTget_6DescripcionMetodoEnA5();
                int columna4 = transaccion.tipoTget_8FechaInicialMetodoEnA5();

                A3_2_TipoTransaccionesGetsYSets transaccionReducida = new A3_2_TipoTransaccionesGetsYSets(columna1, columna2, columna3, columna4);
                listaFiltrada.add(transaccionReducida);
            }
        } else {
            Toast.makeText(getActivity(), "La lista de transacciones está vacía o es nula. ", Toast.LENGTH_SHORT).show();
        }

        Collections.sort(listaFiltrada, new Comparator<A3_2_TipoTransaccionesGetsYSets>() {
            @Override
            public int compare(A3_2_TipoTransaccionesGetsYSets t1, A3_2_TipoTransaccionesGetsYSets t2) {
                int fechaCompare = Integer.compare(t2.tipoTget_8FechaInicialMetodoEnA52(), t1.tipoTget_8FechaInicialMetodoEnA52());
                if (fechaCompare != 0) {
                    return fechaCompare;
                }
                return t1.tipoTget_1DocumentoMetodoEnA52().compareTo(t2.tipoTget_1DocumentoMetodoEnA52());
            }
        });

        // CAMBIO IMPORTANTE: Usar updateData en lugar de crear nuevo adaptador
        if (adaptadorTransaccionesCuentaYoFecha == null) {
            // Primera vez:
            adaptadorTransaccionesCuentaYoFecha = new D_F3_2_AdaptadorTransaccionesCuentaYoFecha(getActivity(), listaFiltrada,
                    documento -> {
                        abrirFragmentoConDocumento(documento);
                    });
            adaptadorTransaccionesCuentaYoFecha.setCheckUpdateListener(() -> actualizarContador()); // NUEVO
            recyclerViewTransacciones_XRv.setAdapter(adaptadorTransaccionesCuentaYoFecha);
            actualizarContador(); // NUEVO

// Update:
            adaptadorTransaccionesCuentaYoFecha.updateData(listaFiltrada);
            actualizarContador(); // NUEVO
        } else {
            adaptadorTransaccionesCuentaYoFecha.updateData(listaFiltrada);
        }

        // Limpiar el campo de búsqueda
        if (buscar_XTv != null) {
            buscar_XTv.setText("");
        }
    }

    public void dynamic$Valores () {

        A23_QueryResult<Void> sumaPorCuenta_Result = a22QueryManager.querySumTransactionsByAccount(receivedAccount);
        sumaPorCuenta_int = sumaPorCuenta_Result.getSuma();


        //resultado de movimiento cuenta
        A23_QueryResult sumaPorCuenta_ResultYFecha_Result = a22QueryManager.querySumTransactionsByAccountAndDateRange(
                receivedAccount,integerFecha1, integerFecha2);
        sumaPorCuentaYFecha_int = sumaPorCuenta_ResultYFecha_Result.getSuma();

    }

    public void ocultarTeclado (){

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

    private void listenerCalendario () {

        ocultarTeclado();

        calendarView_XCv.setOnDateChangeListener(new CalendarView.OnDateChangeListener() {
            @Override
            public void onSelectedDayChange(@NonNull CalendarView calendarView, int i, int i1, int i2) {

                if(fecha1Calendario_XTv.getText().length()==0) {

                    if((i1+1) < 10 && i2 < 10){
                        fecha1_String = ""+i+"0"+(i1+1)+"0"+i2;
                        fecha1_StringFormato = ""+i+"/"+"0"+(i1+1)+"/"+"0"+i2;
                        integerFecha1 = Integer.parseInt(fecha1_String);
                    }

                    if((i1+1) < 10 && i2 > 10){
                        fecha1_String = ""+i+"0"+(i1+1)+i2;
                        fecha1_StringFormato = ""+i+"/"+"0"+(i1+1)+"/"+i2;
                        integerFecha1 = Integer.parseInt(fecha1_String);
                    }

                    if((i1+1) >= 10 && i2 < 10){
                        fecha1_String = ""+i+(i1+1)+"0"+i2;
                        fecha1_StringFormato = ""+i+ "/"+(i1+1)+"/"+"0"+i2;
                        integerFecha1 = Integer.parseInt(fecha1_String);
                    }

                    if ((i1+1) >= 10 && i2 >= 10) {
                        fecha1_String = ""+i+(i1+1)+i2;
                        fecha1_StringFormato = ""+i+"/"+(i1+1)+"/"+i2;
                        integerFecha1 = Integer.parseInt(fecha1_String);
                    }
                    fecha1Calendario_XTv.setText(""+fecha1_StringFormato);
                    areaCalendario_XGl.setVisibility(View.GONE);

                    if(fecha1Calendario_XTv.getText().toString()!="" && fecha2Calendario_XTv.getText().toString()!=""){
                        areaCalendario_XGl.setVisibility(View.GONE);
                        //verTransaccionesPorCuentaYOFecha_XGv.setVisibility(View.VISIBLE);
                        recyclerViewTransacciones_XRv.setVisibility(View.VISIBLE);
                        //verTransaccionesPorCuentaYPorFecha();
                        verConsultaSumaValorTransaccionesEntreDosFechasYPorCuenta();
                        //listenerOnClicItemListView();
                        verTransaccionesPorCuentaYPorFecha();
                        contenidoCuentaPdf_XTv.setText("");
                        usarTransaccionesPorCuentaYFechaParaContenidoPdfEnF3_2();
                    }
                    crearPdfConTablaPorCuentaYFecha_XBt();
                }

                else if(fecha2Calendario_XTv.getText().length()==0) {

                    if((i1+1) < 10 && i2 < 10){
                        stringFecha2 = ""+i+"0"+(i1+1)+"0"+i2;
                        stringFecha2Formato = ""+i+"/"+"0"+(i1+1)+"/"+"0"+i2;
                        integerFecha2 = Integer.parseInt(stringFecha2);
                    }

                    if((i1+1) < 10 && i2 > 10){
                        stringFecha2 = ""+i+"0"+(i1+1)+i2;
                        stringFecha2Formato = ""+i+"/"+"0"+(i1+1)+"/"+i2;
                        integerFecha2 = Integer.parseInt(stringFecha2);
                    }

                    if((i1+1) >= 10 && i2 < 10){
                        stringFecha2 = ""+i+(i1+1)+"0"+i2;
                        stringFecha2Formato = ""+i+ "/"+(i1+1)+"/"+"0"+i2;
                        integerFecha2 = Integer.parseInt(stringFecha2);
                    }

                    if ((i1+1) >= 10 && i2 >= 10) {
                        stringFecha2 = ""+i+(i1+1)+i2;
                        stringFecha2Formato = ""+i+"/"+(i1+1)+"/"+i2;
                        integerFecha2 = Integer.parseInt(stringFecha2);
                    }

                    fecha2Calendario_XTv.setText(""+stringFecha2Formato);

                    areaCalendario_XGl.setVisibility(View.GONE);
                    recyclerViewTransacciones_XRv.setVisibility(View.VISIBLE);//verTransaccionesPorCuentaYPorFecha();
                    verConsultaSumaValorTransaccionesEntreDosFechasYPorCuenta();
                    //listenerOnClicItemListView();
                    verTransaccionesPorCuentaYPorFecha();
                    contenidoCuentaPdf_XTv.setText("");
                    usarTransaccionesPorCuentaYFechaParaContenidoPdfEnF3_2();
                    crearPdfConTablaPorCuentaYFecha_XBt();
                }
            }
        });

        fecha1Calendario_XTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View inflarViews_View) {

                //verTransaccionesPorCuentaYOFecha_XGv.setAdapter(null);
                //transaccionesCuentaYoFecha_XLv.setAdapter(null);
                fecha1Calendario_XTv.setText("");
                areaCalendario_XGl.setVisibility(View.VISIBLE);
                //verTransaccionesPorCuentaYOFecha_XGv.setVisibility(View.GONE);
                recyclerViewTransacciones_XRv.setVisibility(View.GONE);

                if (fecha2Calendario_XTv.getText().length()>0){
                    areaCalendario_XGl.setVisibility(View.VISIBLE);
                    //verTransaccionesPorCuentaYOFecha_XGv.setVisibility(View.GONE);
                    recyclerViewTransacciones_XRv.setVisibility(View.GONE);

                }
            }
        });

        fecha2Calendario_XTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View inflarViews_View) {

                //verTransaccionesPorCuentaYOFecha_XGv.setAdapter(null);
                //transaccionesCuentaYoFecha_XLv.setAdapter(null);
                fecha2Calendario_XTv.setText("");
                areaCalendario_XGl.setVisibility(View.VISIBLE);
                //verTransaccionesPorCuentaYOFecha_XGv.setVisibility(View.GONE);
                recyclerViewTransacciones_XRv.setVisibility(View.GONE);
            }
        });

        compartirEstadoDeCuenta_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mostrarDialogoOpcionesPdf(true); // true = para compartir
            }
        });

    }

    public void listenerPdf () {
        crearPdfConTablaPorCuentaYFecha_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mostrarDialogoOpcionesPdf(false); // false = solo ver
            }
        });
    }

    private void mostrarDialogoOpcionesPdf(final boolean esParaCompartir) {
        int checksRealizados = adaptadorTransaccionesCuentaYoFecha != null ?
                adaptadorTransaccionesCuentaYoFecha.getCheckedCount() : 0;

        AlertDialog.Builder dialogo = new AlertDialog.Builder(getActivity());

        if (esParaCompartir) {
            dialogo.setTitle("Compartir PDF");
        } else {
            dialogo.setTitle("Ver PDF");
        }

        dialogo.setIcon(R.drawable.bg_white_square);

        String[] opciones;

        if (checksRealizados == 0) {
            // Sin checks: Solo opción 1
            opciones = new String[]{"PDF con todas las transacciones"};

            dialogo.setItems(opciones, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    generarYProcesarPdf(0, esParaCompartir); // 0 = Todas
                }
            });
        } else {
            // Con checks: 4 opciones
            opciones = new String[]{
                    "PDF con SOLO transacciones verificadas ✓",
                    "PDF con SOLO transacciones sin verificar",
                    "PDF con AMBAS en tablas separadas (✓ primero)",
                    "PDF con todas SIN separar tablas"
            };

            dialogo.setItems(opciones, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                        case 0: // Solo verificadas
                            generarYProcesarPdf(1, esParaCompartir);
                            break;
                        case 1: // Solo sin verificar
                            generarYProcesarPdf(2, esParaCompartir);
                            break;
                        case 2: // Ambas separadas
                            generarYProcesarPdf(3, esParaCompartir);
                            break;
                        case 3: // Todas sin separar
                            generarYProcesarPdf(0, esParaCompartir);
                            break;
                    }
                }
            });
        }

        dialogo.setNegativeButton("Cancelar", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogo1, int id) {
                return;
            }
        });

        dialogo.show();
    }

    private void generarYProcesarPdf(int tipoFiltro, boolean esParaCompartir) {
        try {
            // Generar según fechas o solo cuenta
            if (integerFecha1 != 0 && integerFecha2 != 0) {
                crearPdfConTablaPorCuentaYFecha_XBt(tipoFiltro);
            } else {
                crearPdfConTablaPorSoloCuenta(tipoFiltro);
            }

            // Definir el archivo PDF
            String nombreCarpetaDestino_String = "Pdfs";
            String directorioDestino_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
            String rutaDestinoYnombreCarpetaDestino_String = directorioDestino_String + nombreCarpetaDestino_String;
            File rutaFinalArchivo_File = new File(rutaDestinoYnombreCarpetaDestino_String);
            pdfFile = new File(rutaFinalArchivo_File, "Informe por cuenta.pdf");

            if (esParaCompartir) {
                // Compartir el PDF
                String[] destinatarios = {"colombiaje@outlook.com", "colombiaje@gmail.com"};
                compartirPDFConTablaCuenta("Informe por cuenta.pdf", "Estado de Cuenta", destinatarios);
            } else {
                // Mostrar el PDF
                Fragment fragment = new F3_3_VerPdf();
                Bundle datosAEnviar_Bundle = new Bundle();
                datosAEnviar_Bundle.putString("path", String.valueOf(pdfFile));
                fragment.setArguments(datosAEnviar_Bundle);

                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.contenedor_fragments_f0_Xf, fragment)
                        .commit();
            }

        } catch (Exception e) {
            Toast.makeText(getActivity(), "Error al generar PDF: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    public void crearPdfConTablaPorSoloCuenta(int tipoFiltro) throws DocumentException {
        try {
            dynamic$Valores();
            dynamicqueryByAccount();
        } catch (Exception e) {
            e.printStackTrace();
        }

        numeroPagina();
        a5_templatePDF.openDocument();
        a5_templatePDF.addMetaData("Cuentas", "Movimientos", "Valores");
        a5_templatePDF.addTitles("Estado por cuenta", "", "");
        a5_templatePDF.addParagraph(shortText);
        a5_templatePDF.addParagraph(numeroPagina_String2 + numeroPagina_String);

        int chequeadas = adaptadorTransaccionesCuentaYoFecha != null ?
                adaptadorTransaccionesCuentaYoFecha.getCheckedCount() : 0;
        int total = adaptadorTransaccionesCuentaYoFecha != null ?
                adaptadorTransaccionesCuentaYoFecha.getTotalCount() : 0;
        int sumaVerificadas = adaptadorTransaccionesCuentaYoFecha != null ?
                adaptadorTransaccionesCuentaYoFecha.getSumaValoresChecked() : 0;

        String[][] data = {
                {"Cuenta", receivedAccount},
                {"Saldo final: $", "" + sumaPorCuenta_int},
                {"Verificadas", chequeadas + "/" + total + " ($ " + sumaVerificadas + ")"},
                {"Fecha y hora del informe:", "" + fechaYHora_String},
        };
        a5_templatePDF.createSimpleTable(data);

        try {
            document.add(new Paragraph("\n"));
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        // Generar tabla según filtro
        if (tipoFiltro == 3) {
            // Dos tablas separadas
            a5_templatePDF.addParagraph("TRANSACCIONES VERIFICADAS ✓");
            a5_templatePDF.createTable(header, crearTablaParaPdfSoloCuenta(1));
            try {
                document.add(new Paragraph("\n"));
            } catch (DocumentException e) {
                e.printStackTrace();
            }
            a5_templatePDF.addParagraph("TRANSACCIONES SIN VERIFICAR");
            a5_templatePDF.createTable(header, crearTablaParaPdfSoloCuenta(2));
        } else {
            // Una sola tabla
            a5_templatePDF.createTable(header, crearTablaParaPdfSoloCuenta(tipoFiltro));
        }

        a5_templatePDF.closeDocument();
    }

    public void listenerOtros (){
        //Eventos
        salida_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
    }

    // Método para mostrar transacciones
    public void recibirBundle() {
        try {
            Bundle bundle = getArguments();
            if (bundle != null) {
                receivedAccount = bundle.getString("keyAccount");

                // ⭐ NUEVO: Recibir datos adicionales SOLO si existen
                anioRecibido = bundle.getInt("keyAnio", -1);
                mesRecibido = bundle.getInt("keyMes", -1);
                desdeIndicadores = bundle.getBoolean("keyDesdeIndicadores", false);

                // ✅ Tu código original sigue funcionando igual
                if (receivedAccount != null) {
                    c3_Cuenta_XTv.setText(receivedAccount);
                }

                Log.d("BundleRecibido", "Cuenta: " + receivedAccount +
                        ", Año: " + anioRecibido +
                        ", Mes: " + mesRecibido +
                        ", Desde Indicadores: " + desdeIndicadores);
            }
        } catch (Exception e) {
            Log.e("BundleRecibido", "Error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void verTransaccionesSoloCuenta () {
        consultasPorCuentaYFecha_XLl.setVisibility(View.GONE);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(2, 20, 0, 40);
        //verTransaccionesPorCuentaYOFecha_XGv.setLayoutParams(lp);
        //transaccionesCuentaYoFecha_XLv.setLayoutParams(lp);

        verConsultaSumaValorTransaccionesPorCuenta();

        try {
            dynamicqueryByAccount();
            //verTransaccionesPorCuentaYOFecha_XGv.setAdapter(adapterCuenta);

        } catch (Exception e) {
            e.printStackTrace(); // Loguear el error
        }
    }

    public void verConsultaSumaValorTransaccionesPorCuenta() {

        try {
            etiquetaMovimiento_XBt.setVisibility(View.INVISIBLE);
            c5_Valor_XTv.setText("");
            dynamic$Valores();
            if (c3_Cuenta_XTv.getText() != null && !c3_Cuenta_XTv.getText().toString().trim().equals("")) {
                c5_Valor_XTv.append("$ " + sumaPorCuenta_int);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void verTransaccionesPorCuentaYPorFecha() {
        consultasPorCuentaYFecha_XLl.setVisibility(View.VISIBLE);
        dynamicqueryByAccountAndDateRange();
        //verTransaccionesPorCuentaYOFecha_XGv.setAdapter(adapter);
        //transaccionesCuentaYoFecha_XLv.setAdapter(adapter);
    }

    public void verConsultaSumaValorTransaccionesEntreDosFechasYPorCuenta() {
        if (receivedAccount == null || receivedAccount.trim().isEmpty()) {
            Toast.makeText(getActivity(), "Selecciona una cuenta", Toast.LENGTH_LONG).show();
            c5_0_Valor_XTv.setText("");
            c5_Valor_XTv.setText("");
            return;
        }

        dynamic$Valores();

        if (c3_Cuenta_XTv.getText() != null && !c3_Cuenta_XTv.getText().toString().trim().equals("")) {
            c5_0_Valor_XTv.setText("");
            c5_Valor_XTv.setText("");
            c5_0_Valor_XTv.append("$ " + sumaPorCuentaYFecha_int);
            c5_Valor_XTv.append("$ " + sumaPorCuenta_int);
        }

    }

    //Foco?? pendiente de clasificar
    /*public void restoreBackupCSVInAddNewViewsValues () {
        String rutaYnombreArchivo1 = Environment.getExternalStorageDirectory().getPath() +
                "/Balance/" + "115 Backup of the update and delete document’s header.csv";
        String rutaYnombreArchivo2 = Environment.getExternalStorageDirectory().getPath()
                + "/Balance/" + "116 Backup of the update and delete document’s records.csv";
        File archivo1 = new File(rutaYnombreArchivo1);
        File archivo2 = new File(rutaYnombreArchivo2);
        if (archivo1.exists() && archivo2.exists()) {
            AlertDialog.Builder dialogo1 = new AlertDialog.Builder(getActivity());
            dialogo1.setTitle("Importante");
            dialogo1.setMessage("¿ Borrar trabajo pendiente en edicion? ");
            dialogo1.setCancelable(false);
            dialogo1.setPositiveButton("Si", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogo1, int id) {
                    try {
                        archivo1.delete();
                        archivo2.delete();
                    } catch (Exception e) {
                    }
                }
            });
            dialogo1.setNegativeButton("No Borrar", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialogo1, int id) {
                    return;
                }
            });
            dialogo1.show();
        }
    }*/

    // Método común para crear fila en la tabla PDF
    private String[] crearFilaTransaccion(A3_2_TipoTransaccionesGetsYSets transaccion) {
        return new String[] {
                String.valueOf(transaccion.tipoT_8DateOfDocument_Integer),
                transaccion.tipoT_1NumberDocument_String,
                transaccion.tipoT_6Description_String,
                String.valueOf(transaccion.tipoT_5Value_Integer)
        };
    }

    private ArrayList<String[]> crearTablaParaPdfSoloCuenta(int tipoFiltro) {
        ArrayList<String[]> rows = new ArrayList<>();

        if (adaptadorTransaccionesCuentaYoFecha == null) return rows;

        // USAR LISTA DEL ADAPTADOR (tiene checks)
        ArrayList<A3_2_TipoTransaccionesGetsYSets> listaConChecks =
                adaptadorTransaccionesCuentaYoFecha.obtenerListaCompleta();

        for (A3_2_TipoTransaccionesGetsYSets t : listaConChecks) {
            boolean incluir = (tipoFiltro == 0) ||
                    (tipoFiltro == 1 && t.isChecked()) ||
                    (tipoFiltro == 2 && !t.isChecked());

            if (incluir) {
                rows.add(new String[] {
                        String.valueOf(t.tipoTget_8FechaInicialMetodoEnA52()),
                        t.tipoTget_1DocumentoMetodoEnA52(),
                        t.tipoTget_6DescripcionMetodoEnA52(),
                        String.valueOf(t.tipoTget_5ValorMetodoEnA52()),
                        t.isChecked() ? "✓" : ""
                });
            }
        }
        return rows;
    }

    public void crearPdfConTablaPorCuentaYFecha_XBt(int tipoFiltro) {

        if (a5_templatePDF == null) {
            Log.i("PDF", "TemplatePDF no inicializado");
            return;
        }

        dynamicqueryByAccountAndDateRange();
        Integer saldoAnterior_int = sumaPorCuenta_int - sumaPorCuentaYFecha_int;

        a5_templatePDF.openDocument();
        a5_templatePDF.addMetaData("Cuentas", "Movimientos", "Valores");
        a5_templatePDF.addTitles("Estado de cuenta por fecha", "", "");

        int chequeadas = adaptadorTransaccionesCuentaYoFecha != null ?
                adaptadorTransaccionesCuentaYoFecha.getCheckedCount() : 0;
        int total = adaptadorTransaccionesCuentaYoFecha != null ?
                adaptadorTransaccionesCuentaYoFecha.getTotalCount() : 0;
        int sumaVerificadas = adaptadorTransaccionesCuentaYoFecha != null ?
                adaptadorTransaccionesCuentaYoFecha.getSumaValoresChecked() : 0;

        String[][] data = {
                {"Cuenta", receivedAccount},
                {"Del Periodo", "" + integerFecha1 + " al " + integerFecha2},
                {"Saldo anterior del periodo: $", "" + saldoAnterior_int},
                {"Movimiento: $", "" + sumaPorCuentaYFecha_int},
                {"Saldo final: $", "" + sumaPorCuenta_int},
                {"Verificadas", chequeadas + "/" + total + " ($ " + sumaVerificadas + ")"},
                {"Fecha y hora del informe:", "" + fechaYHora_String},
        };
        a5_templatePDF.createSimpleTable(data);

        try {
            document.add(new Paragraph("\n"));
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        if (tipoFiltro == 3) {
            a5_templatePDF.addParagraph("TRANSACCIONES VERIFICADAS ✓");
            a5_templatePDF.createTable(header, crearTablaParaPdfCuentaYFecha(1));
            try {
                document.add(new Paragraph("\n"));
            } catch (DocumentException e) {
                e.printStackTrace();
            }
            a5_templatePDF.addParagraph("TRANSACCIONES SIN VERIFICAR");
            a5_templatePDF.createTable(header, crearTablaParaPdfCuentaYFecha(2));
        } else {
            a5_templatePDF.createTable(header, crearTablaParaPdfCuentaYFecha(tipoFiltro));
        }

        a5_templatePDF.closeDocument();
    }

    private ArrayList<String[]> crearTablaParaPdfCuentaYFecha(int tipoFiltro) {
        ArrayList<String[]> rows = new ArrayList<>();

        if (adaptadorTransaccionesCuentaYoFecha == null) return rows;

        ArrayList<A3_2_TipoTransaccionesGetsYSets> listaConChecks =
                adaptadorTransaccionesCuentaYoFecha.obtenerListaCompleta();

        for (A3_2_TipoTransaccionesGetsYSets t : listaConChecks) {
            boolean incluir = (tipoFiltro == 0) ||
                    (tipoFiltro == 1 && t.isChecked()) ||
                    (tipoFiltro == 2 && !t.isChecked());

            if (incluir) {
                rows.add(new String[] {
                        String.valueOf(t.tipoTget_8FechaInicialMetodoEnA52()),
                        t.tipoTget_1DocumentoMetodoEnA52(),
                        t.tipoTget_6DescripcionMetodoEnA52(),
                        String.valueOf(t.tipoTget_5ValorMetodoEnA52()),
                        t.isChecked() ? "✓" : ""
                });
            }
        }
        return rows;
    }



    public void usarTransaccionesPorCuentaParaContenidoPdf() {
        try {
            dynamic$Valores();
            dynamicqueryByAccount();
            transaccionesPorCuenta_ArrayAdapter = new ArrayAdapter<String>(
                    getActivity(), android.R.layout.simple_list_item_1, datosTabularesSoloCuenta);

            contenidoCuentaPdf_XTv.append("Estado de cuenta de: " + receivedAccount + "\n" + "\n" +
                    "Fecha: " + A99_MetodosVarios.stringFechaYHora + "\n" +
                    "................................................................................." + "\n" + "\n");
            contenidoCuentaPdf_XTv.append("Fecha          Doc. Descripcion         $ Valor  " + "\n" + "\n");

            // Usamos la función para iterar sobre las transacciones
            for (A3_2_TipoTransaccionesGetsYSets transaccion : transaccionesPorCuenta_Result_ArrayLTT) {
                contenidoCuentaPdf_XTv.append("  " + transaccion.tipoTget_8FechaInicialMetodoEnA5());
                contenidoCuentaPdf_XTv.append("  " + transaccion.tipoTget_1DocumentoMetodoEnA5());
                contenidoCuentaPdf_XTv.append("  " + transaccion.tipoTget_6DescripcionMetodoEnA5());
                contenidoCuentaPdf_XTv.append(" $ " + transaccion.tipoTget_5ValorMetodoEnA5() + "\n");
            }

            //consultasClass.consultarSumaTransacciones(receivedAccount, "c3_Cuenta");
            contenidoCuentaPdf_XTv.append(".............................................................................." + "\n" + "\n" +
                    // "Saldo final de la cuenta: ....... $ " + consultasClass.suma$TransaccionesCuenta_int);
                    "Saldo final de la cuenta: ....... $ " + sumaPorCuenta_int);

        } catch (Exception e) {
            // Manejo de error
        }
    }

    private ArrayList<String[]> crearTablaParaPdfCuentaYFecha() {
        ArrayList<String[]> rows = new ArrayList<>();
        dynamicqueryByAccountAndDateRange();
        try {

        } catch (Exception e) {
            // Manejo de error
        }

        //ordenar desde la ultima fecha hacia atras
        Collections.sort(transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT, new Comparator<A3_2_TipoTransaccionesGetsYSets>() {
            @Override
            public int compare(A3_2_TipoTransaccionesGetsYSets t1, A3_2_TipoTransaccionesGetsYSets t2) {
                return Integer.compare(t2.tipoT_8DateOfDocument_Integer, t1.tipoT_8DateOfDocument_Integer);
            }
        });

        for (int x = 0; x < transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT.size(); x++) {
            rows.add(new String[]{
                    String.valueOf(transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT.get(x).tipoT_8DateOfDocument_Integer),
                    transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT.get(x).tipoT_1NumberDocument_String,
                    transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT.get(x).tipoT_6Description_String,
                    String.valueOf(transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT.get(x).tipoT_5Value_Integer)
            });
        }
        return rows;
    }


    public void usarTransaccionesPorCuentaYFechaParaContenidoPdfEnF3_2() {
        dynamicqueryByAccountAndDateRange();
        saldoAnterior_int = sumaPorCuenta_int - sumaPorCuentaYFecha_int;

        contenidoCuentaPdf_XTv.append("Estado de cuenta de: " + receivedAccount + "\n" + "\n" +
                "Fecha: " + A99_MetodosVarios.stringFechaYHora + "\n" +
                "Saldo anterior $ : " + saldoAnterior_int + "\n" +
                "................................................................................." + "\n" + "\n");

        for (int i = 0; i < transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT.size(); i++) {
            contenidoCuentaPdf_XTv.append((i + 1) + ") " + transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT.get(i) + "." + "\n");
            contenidoCuentaPdf_XTv.append((i + 1) + ") " + transaccionesFiltradasPorCuentaYRangoFecha_Result_ArrayLTT.get(i) + "." + "\n");
        }

        contenidoCuentaPdf_XTv.append(".............................................................................." + "\n" + "\n" +
                "Movimiento en el periodo $ : " + sumaPorCuentaYFecha_int + "\n" + "\n" +
                "Saldo final de la cuenta: ....... $ " + sumaPorCuenta_int);

    }

    public void crearPdfConTablaPorCuentaYFecha_XBt() {

        dynamicqueryByAccountAndDateRange();
        Integer saldoAnterior_int = sumaPorCuenta_int - sumaPorCuentaYFecha_int;

        a5_templatePDF.openDocument();
        a5_templatePDF.addMetaData("Cuentas", "Movimientos", "Valores");
        a5_templatePDF.addTitles("Estado de cuenta por fecha", "", "");

        // Crear tabla con los datos
        String[][] data = {
                {"Cuenta", receivedAccount},
                {"Del Periodo", "" + integerFecha1 + " al " + integerFecha2},
                {"Saldo anterior del periodo: $", "" + saldoAnterior_int},
                {"Movimiento: $", "" + sumaPorCuentaYFecha_int},
                {"Saldo final: $", "" + sumaPorCuenta_int},
                {"Fecha y hora del informe:", "" + fechaYHora_String},
        };
        a5_templatePDF.createSimpleTable(data);
        try {
            document.add(new Paragraph("\n"));
        } catch (DocumentException e) {
            e.printStackTrace();
        }
        a5_templatePDF.createTable(header, crearTablaParaPdfCuentaYFecha());
        a5_templatePDF.closeDocument();
    }

    public String numeroPagina () {

        a5_templatePDF = new A4_TemplatePDF(getActivity());
        a5_templatePDF.openDocument();
        //Document document = new Document();

        for (int i = 1; i <= document.getPageNumber(); i++)
        {
            //contentByte = stamper.getOverContent(i);
            //numeroPagina_String = document.addParagraph("Page " + i + " of " + document.getPageNumber());  // my own paragraph font
        }
        return numeroPagina_String;
    }

    //Compartir con otras apps

    public void compartirPDFConTablaCuenta (String nombreArchivo, String asunto, String[] destinatarios) {
        String directorio_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/Pdfs/";
        File filePath = new File(directorio_String, nombreArchivo);

        if (!filePath.exists()) {
            Toast.makeText(getActivity(), "El archivo PDF no existe en la ruta especificada", Toast.LENGTH_LONG).show();
            return;
        }

        Uri uri = FileProvider.getUriForFile(requireContext(), "com.jj.appbalancev31.fileprovider", filePath);

        // **Opción 1: Compartir en WhatsApp**
        Intent whatsappIntent = new Intent(Intent.ACTION_SEND);
        whatsappIntent.setType("application/pdf");
        whatsappIntent.putExtra(Intent.EXTRA_STREAM, uri);
        whatsappIntent.setPackage("com.whatsapp");  // Esto hace que solo abra WhatsApp

        // **Opción 2: Compartir por Correo**
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(Uri.parse("mailto:")); // Solo apps de correo
        emailIntent.putExtra(Intent.EXTRA_EMAIL, destinatarios);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, asunto);
        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // **Opción 3: Compartir con otras apps (Google Drive, Telegram, etc.)**
        Intent compartirGeneral = new Intent(Intent.ACTION_SEND);
        compartirGeneral.setType("application/pdf");
        compartirGeneral.putExtra(Intent.EXTRA_STREAM, uri);
        compartirGeneral.putExtra(Intent.EXTRA_SUBJECT, asunto);
        compartirGeneral.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        // Crear un chooser con las tres opciones
        Intent chooser = Intent.createChooser(compartirGeneral, "Compartir PDF");
        chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{whatsappIntent, emailIntent});

        startActivity(chooser);
    }


    //Otros metodos

    public void dialogoInterface () {

        AlertDialog.Builder esteDialogo = new AlertDialog.Builder(getContext());
        esteDialogo.setTitle("¿ Consulta por ?:");
        esteDialogo.setIcon(R.drawable.bg_white_square);
        String[] opcionesEliminarOModificar_ArrayString = {"Cuenta", "Cuenta y fecha"};
        esteDialogo.setItems(opcionesEliminarOModificar_ArrayString, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {

                    case 0: //Por Cuenta
                        verTransaccionesSoloCuenta();
                        usarTransaccionesPorCuentaParaContenidoPdf();
                        break;

                    case 1: //Por Cuenta y fecha
                        break;
                }

            }
        });
        esteDialogo.setNegativeButton("Ninguna accion", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface esteDialogo1, int id) {
                //return;
                dismiss();
            }

        });
        esteDialogo.show();
    }

    //Cerrar la bd
    @Override
    public void onStop() {
        super.onStop();

        // Cancelar búsquedas pendientes
        if (handlerBusqueda != null && runnableBusqueda != null) {
            handlerBusqueda.removeCallbacks(runnableBusqueda);
        }

        // Cerrar la base de datos
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    //Metodos de clases abstractas
    @Override
    public void onResume() {
        super.onResume();

        if (getDialog() != null) {
            int with = getResources().getDimensionPixelSize(R.dimen.dialog_with);
            int heigth = ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setLayout(with,heigth);
        }
    }

    private void actualizarContador() {
        if (adaptadorTransaccionesCuentaYoFecha != null && contadorVerificadas_XTv != null) {
            int chequeadas = adaptadorTransaccionesCuentaYoFecha.getCheckedCount();
            int total = adaptadorTransaccionesCuentaYoFecha.getTotalCount();
            int sumaValores = adaptadorTransaccionesCuentaYoFecha.getSumaValoresChecked();

            contadorVerificadas_XTv.setText("✓: Verificadas " + chequeadas + "/" + total);
            sumaVerificadas_XTv.setText("Por $" + sumaValores);
        }
    }

    private void configurarFiltrosCheck() {
        final boolean[] filtroVerificadasActivo = {false};
        final boolean[] filtroSinVerificarActivo = {false};

        filtroVerificadas_XBt.setOnClickListener(v -> {
            if (adaptadorTransaccionesCuentaYoFecha != null) {
                if (filtroVerificadasActivo[0]) {
                    // Desactivar: Mostrar todas
                    adaptadorTransaccionesCuentaYoFecha.setFiltroCheck(0);
                    filtroVerificadasActivo[0] = false;
                    filtroVerificadas_XBt.setAlpha(1.0f);
                } else {
                    // Activar: Solo verificadas
                    adaptadorTransaccionesCuentaYoFecha.setFiltroCheck(1);
                    filtroVerificadasActivo[0] = true;
                    filtroSinVerificarActivo[0] = false;
                    filtroVerificadas_XBt.setAlpha(0.5f);
                    filtroSinVerificar_XBt.setAlpha(1.0f);
                }
            }
        });

        filtroSinVerificar_XBt.setOnClickListener(v -> {
            if (adaptadorTransaccionesCuentaYoFecha != null) {
                if (filtroSinVerificarActivo[0]) {
                    // Desactivar: Mostrar todas
                    adaptadorTransaccionesCuentaYoFecha.setFiltroCheck(0);
                    filtroSinVerificarActivo[0] = false;
                    filtroSinVerificar_XBt.setAlpha(1.0f);
                } else {
                    // Activar: Solo sin verificar
                    adaptadorTransaccionesCuentaYoFecha.setFiltroCheck(2);
                    filtroSinVerificarActivo[0] = true;
                    filtroVerificadasActivo[0] = false;
                    filtroSinVerificar_XBt.setAlpha(0.5f);
                    filtroVerificadas_XBt.setAlpha(1.0f);
                }
            }
        });

        limpiarChecks_XBt.setOnClickListener(v -> {
            if (adaptadorTransaccionesCuentaYoFecha != null) {
                adaptadorTransaccionesCuentaYoFecha.limpiarTodosLosChecks();
                actualizarContador();

                // Resetear filtros a estado normal
                filtroVerificadasActivo[0] = false;
                filtroSinVerificarActivo[0] = false;
                filtroVerificadas_XBt.setAlpha(1.0f);
                filtroSinVerificar_XBt.setAlpha(1.0f);
                adaptadorTransaccionesCuentaYoFecha.setFiltroCheck(0);

                Toast.makeText(getActivity(), "Checks limpiados", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean verificarYProcesarDesdeIndicadores() {
        if (desdeIndicadores && anioRecibido != -1 && mesRecibido != -1) {
            Log.d("DesdeIndicadores", "Procesando consulta automática para: " +
                    receivedAccount + " - " + anioRecibido + "/" + mesRecibido);

            // ⭐ EJECUTAR EN EL ORDEN CORRECTO
            dynamic$Valores();  // Primero los valores
            verTransaccionesSoloCuenta();  // Luego la vista
            dynamicqueryByAccount();  // Luego la consulta (que ya tiene el filtro)
            usarTransaccionesPorCuentaParaContenidoPdf();  // Finalmente el PDF

            return true;
        }
        return false;
    }

    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;

        // Usar factory method sin callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }

}
