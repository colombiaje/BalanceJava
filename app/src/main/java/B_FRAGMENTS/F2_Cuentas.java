package B_FRAGMENTS;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_AFTER_CLOSING_RESTORING_SHEETS;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_AFTER_RESTORING_BACKUP_INITIAL;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_BEFORE_CLOSING_RESTORING_SHEETS;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_BEFORE_RESTORING_BACKUP_INITIAL;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_BEFORE_STARTING_CLOSING;
import static A1BASES.A9_2_BackupFile.CSV_ACCOUNTS_SHEETS_SYNCHRONIZED;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.gridlayout.widget.GridLayout;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputLayout;
import com.jj.appbalancev31.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import A1BASES.A1_1_AyudanteBD;
import A1BASES.A1_2_OperacionesBD;
import A1BASES.A3_1_TipoCuentasGetsYSets;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A5_1_BackupManager;
import A1BASES.A6_2_GoogleDriveDownloader;
import A1BASES.A6_3_CSVDriveUploader;
import A1BASES.A6_5_SheetsDownloader;
import A1BASES.A99_MetodosVarios;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;
import D_ADAPTERS.D_F2_AdaptadorCuentas;
//import a4.balance.R;

public class F2_Cuentas extends DialogFragment {

    RadioButton _2_1_opcionSincronizarCuentasDesdeSheets_XRb;
    // ==================== CONSTANTES NUEVAS ====================
    private static final String TAG = "F2_Cuentas";
    private static final String FOLDER_ID_DRIVE = "1jrcnKozSB2okTStCaPyxkGz39GwBKL_y";;
    private static final int DELAY_TOAST_CORTO = 4000;
    private static final int DELAY_TOAST_LARGO = 8000;
    private static final int DELAY_SUBIDA_DRIVE = 2500;

    // Nuevas variables para Drive y Sheets
    private A6_2_GoogleDriveDownloader driveDownloader;
    private A6_5_SheetsDownloader sheetsDownloader;

    //declaracion de objetos y variables

    Button salidaEsteFragment_XBt;
    //1 Ver secciones 2 y 3

    G1_Prueba prueba;

    CheckBox seeNewXChB;
    CheckBox seeModifyXChB;
    GridLayout area2crud_XGl;

    //2 Nuevas cuentas
    Button clickSave_XBt,clickUpdate_XBt;
    TextView item_XTv;
    AutoCompleteTextView account_XAct;
    Spinner grupo1CuentaNueva_XSp,grupo2CuentaNueva_XSp;
    String itemCuentaNueva_String;
    String cuentaNueva_String;
    String grupo1CuentaNueva_String;
    String grupo2CuentaNueva_String;
    String fechaCuentaNueva_String;

    //Referencias para instancias
    A1_1_AyudanteBD ayudante_Class;
    A22_QueryManager a22QueryManager;
    A1_2_OperacionesBD a3_operacionesBD;

    //Button modificarCuenta_XBt;

    TextInputLayout alertaAlEscribirEnModificar_XTiL;
    ArrayAdapter cuentasPorNombre_ArrayAdapterString;

    String [] nombreGrupo1Cuentas_ArrayString = new String[0];
    String [] nombreGrupo2Cuentas_ArrayString = new String[0];

    //4 Consultar cuentas
    Spinner cuentasOrdenAzParaVistaDetalleCuenta_XSp;
    public static String cuentaSeleccionadaAqui;
    ArrayAdapter<String> cuentasOrdenAscendente_ArrayAdapterString;
    Spinner consultaPorCuentaYFechaEnOtroFragment_XSp;
    TextView valorRecibidoResultadoCalculadora_XTv;
    F6_Calculadora calculadora_Fragment;
    RadioButton opcionSaleDeLaApp_XRb;
    ArrayAdapter <String> cuentasOrdenAscendenteConsultarTransaccionesOtrosFragments_ArrayAdapter;
    Button cleanFieldsAccount_XBt;
    GridLayout area4SeeAccountsXGl;
    TextView tituloConsultarDEtallesCuentaXTV;
    TextView areaTituloBackupsYCierreXTv;
    CheckBox verCuentas_XChb;
    CheckBox verBackupsYCierres_XChb;
    RadioGroup OpcionesCierre_XRg;
    RadioButton _11_opcionBackupDeReservaCuentas_XRb;
    RadioButton _2_2_opcionImportarCuentasGoogleSheets_XRb;
    RadioButton _31_opcionRestaurarCuentasBackup_XRb;
    TextView mensajeInformativo_XTv;
    TextView opcionActualCierre_XTv;
    String nombreArchivo_String;
    ListView todasLasCuentas_XLv;
    D_F2_AdaptadorCuentas adaptadorTodasLasCuentas_TipoCuentas;
    GridLayout areaDocumento_XGl;
    A99_MetodosVarios a99_metodosVarios;
    Date d=new Date();
    SimpleDateFormat fecc=new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
    String nombreArchivoBackupCuentas_String;
    A5_1_BackupManager a6_backupManager;
    String[] cuentaSeleccionadaAqui_ArrayS_Result;

    //Abrir la bd en lectura
    private SQLiteDatabase db;


    TableLayout atributos_XTL;
    LinearLayout area3AttributesXLY;

    LinearLayout area5BackupsClosingXLL;
    TextView cuentaConTransacciones_XTv;
    LinearLayout transacciones_XLL;
    //CheckBox verTransacciones_XChB;
    GridView vercuentaConTransacciones_XGv;
    CheckBox copyPasteNombreEnNueva_XChb,copyCuentaABuscarXChb;
    private String copiedText = "";
    private boolean isCopyMode = true;
    Button clickDeleteXBt;
    ArrayList<A3_2_TipoTransaccionesGetsYSets> cuentaConTransacciones_Result_ArrayList;
    String existingText;
    AutoCompleteTextView emulateAttributesAccount_XAct;
    TextView titleModifyXTV;
    TextView titleNewXTv;

    RadioButton outAccounts_XRb;
    private CheckBox lastCheckedBox = null;

    Cursor llamadaCursorUnaCuenta;

    ArrayList<String> queryAzAllAccounts_Result_ArrayList;
    //private F6_Calculadora calculadora_Fragment;
    Button calculadoraLibre_XBt;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true); // Establecer como no modal, sale del dialogoFragment a un click fuera del dialogo
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        Log.d("F2_DEBUG", "========================================");
        Log.d("F2_DEBUG", "onCreateDialog() llamado");
        Log.d("F2_DEBUG", "getParentFragment() = " + getParentFragment());
        Log.d("F2_DEBUG", "getActivity() = " + getActivity());
        Log.d("F2_DEBUG", "getFragmentManager() = " + getFragmentManager());
        Log.d("F2_DEBUG", "========================================");

        Dialog dialogo_Dialog = new Dialog(getActivity(), R.style.CustomDialogTheme);

        //Dialog dialogo_Dialog = new Dialog(getActivity(), android.R.style.Theme_Translucent_NoTitleBar);
        final View inflarViews_View = getActivity().getLayoutInflater().inflate(R.layout.f2_cuentas, null);


        /*Dialog dialogo_Dialog = new Dialog(getActivity(), android.R.style.Theme_Translucent_NoTitleBar);
        final View inflarViews_View = getActivity().getLayoutInflater().inflate(R.layout.f2_cuentas, null);*/

        final Drawable d_Drawable = new ColorDrawable(Color.BLACK);
        d_Drawable.setAlpha(200);

        dialogo_Dialog.setContentView(inflarViews_View);
        dialogo_Dialog.setCanceledOnTouchOutside(true);
        dialogo_Dialog.setCancelable(true);

        // Abrir base de datos
        if (ayudante_Class != null) {
            ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
            db = ayudante_Class.getReadableDatabase();
        }

        dialogo_Dialog.getWindow().setBackgroundDrawable(d_Drawable);
        dialogo_Dialog.getWindow().setContentView(inflarViews_View);

        // Configuración de tamaño y posición
        final WindowManager.LayoutParams layoutParams = dialogo_Dialog.getWindow().getAttributes();
        layoutParams.width = 1200;
        layoutParams.height = 1200;
        layoutParams.gravity = Gravity.CENTER;
        dialogo_Dialog.getWindow().setAttributes(layoutParams);

        // ✅ INICIALIZAR EL SPINNER AQUÍ
        initializeSpinner(inflarViews_View);

        Spinner spinner = inflarViews_View.findViewById(R.id.consultaPorCuentaYFechaEnOtroFragment_XSp);
        if (spinner != null) {
            Log.d("F2_DEBUG", "Spinner encontrado correctamente");

            // Forzar que el Spinner use un modo específico
            spinner.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    Log.d("F2_DEBUG", "Spinner tocado! Event: " + event.getAction());
                    return false;
                }
            });
        } else {
            Log.e("F2_DEBUG", "ERROR: Spinner es NULL");
        }

        initializeSpinner(inflarViews_View);

        return dialogo_Dialog;
    }

    // Método para inicializar el Spinner
    private void initializeSpinner(View view) {
        Spinner spinner = view.findViewById(R.id.consultaPorCuentaYFechaEnOtroFragment_XSp);

        if (spinner != null) {
            // Cargar datos en el Spinner
            resetearSpinnerCuentasRevision(spinner);
        } else {
            Log.e("F2_Cuentas", "El Spinner es null en initializeSpinner");
        }
    }

    // Modificar resetearSpinnerCuentasRevision para recibir el Spinner como parámetro
    private void resetearSpinnerCuentasRevision(Spinner spinner2) {
        try {
            // Obtener la vista del diálogo
            View dialogView = null;

            if (getDialog() != null && getDialog().getWindow() != null) {
                dialogView = getDialog().getWindow().getDecorView();
            }

            if (dialogView == null) {
                Log.e("F2_Cuentas", "La vista del diálogo es null");
                return;
            }

            Spinner spinner = dialogView.findViewById(R.id.consultaPorCuentaYFechaEnOtroFragment_XSp);

            if (spinner == null) {
                Log.e("F2_Cuentas", "El Spinner es null");
                return;
            }

        // Tu lógica para cargar datos en el Spinner
        //Variables generales
        A22_QueryManager a22QueryManager = new A22_QueryManager(getActivity());

        A23_QueryResult queryAzAllAccounts_Result = a22QueryManager.queryAzAllAccounts();
        ArrayList<String> queryAzAllAccounts_ArrayList_Result = queryAzAllAccounts_Result.getDatos();

        //Adaptador
        cuentasOrdenAscendenteConsultarTransaccionesOtrosFragments_ArrayAdapter =
                new ArrayAdapter<String>(getActivity(), android.R.layout.simple_list_item_multiple_choice,
                        queryAzAllAccounts_ArrayList_Result);
        cuentasOrdenAscendenteConsultarTransaccionesOtrosFragments_ArrayAdapter.notifyDataSetChanged();

        // ✅ USA EL PARÁMETRO spinner EN LUGAR DE consultaPorCuentaYFechaEnOtroFragment_XSp
        spinner.setAdapter(cuentasOrdenAscendenteConsultarTransaccionesOtrosFragments_ArrayAdapter);

        //Eventos
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if(spinner.getSelectedItem().toString().isEmpty()){
                    return;
                }
                else{
                    // ⚠️ IMPORTANTE: Asignar el spinner a la variable de instancia para usarlo en otros métodos
                    consultaPorCuentaYFechaEnOtroFragment_XSp = spinner;
                    _93_vercuentaConTransaccionesConSpinnerEnF2();
                    spinner.setSelection(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

            Log.d("F2_Cuentas", "Spinner inicializado correctamente");

        } catch (Exception e) {
            Log.e("F2_Cuentas", "Error al inicializar Spinner: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // La vista ya está completamente inflada aquí
        resetearSpinnerCuentasRevision(consultaPorCuentaYFechaEnOtroFragment_XSp);
    }

    @Override
    public void onStart() {
        super.onStart();

        // ✅ CONFIGURAR BOTÓN DE SALIDA (este método está en F2, no en F1)
        configurarBotonSalida();

        // Configurar tamaño del diálogo
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = 1100;
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setLayout(width, height);
        }
    }

    // ✅ ESTE MÉTODO VA EN F2_Cuentas, NO EN F1
    private void configurarBotonSalida() {
        Button salidaEsteFragment_XBt = null;

        // Buscar el botón en la vista DEL DIALOG (F2)
        if (getDialog() != null && getDialog().getWindow() != null) {
            // Si se muestra como Dialog, buscar en la vista del dialog
            salidaEsteFragment_XBt = getDialog().findViewById(R.id.salidaEsteFragment_XBt);
        } else if (getView() != null) {
            // Si se muestra como Fragment normal, buscar en getView()
            salidaEsteFragment_XBt = getView().findViewById(R.id.salidaEsteFragment_XBt);
        }

        if (salidaEsteFragment_XBt != null) {
            // ✅ Si getDialog() existe, estamos en modo Dialog → MOSTRAR botón
            if (getDialog() != null) {
                salidaEsteFragment_XBt.setVisibility(View.VISIBLE);
                Log.d("F2_Cuentas", "Botón salida VISIBLE (modo Dialog)");
            } else {
                // ❌ Si no hay Dialog, estamos en modo Fragment normal → OCULTAR botón
                salidaEsteFragment_XBt.setVisibility(View.GONE);
                Log.d("F2_Cuentas", "Botón salida OCULTO (modo Fragment)");
            }
        } else {
            Log.e("F2_Cuentas", "salidaEsteFragment_XBt es NULL");
        }
    }

    @SuppressLint("WrongViewCast")
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflarViews_View=inflater.inflate(R.layout.f2_cuentas,container,false);

        calculadora_Fragment = new F6_Calculadora();
        calculadoraLibre_XBt = inflarViews_View.findViewById(R.id.calculadoraLibre_XBt);

        // Abrimos la base de datos cuando empieza la operación
        if (ayudante_Class != null) {
            ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null,  version1BalanceSqlite_int_PSF);
            db = ayudante_Class.getReadableDatabase();
        } else {
        }


        inflarViews_View.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Vacio para que no se activen los fragments de la lista
            }
        });

        area2crud_XGl = (GridLayout)inflarViews_View.findViewById(R.id.area2crud_XGl);
        seeNewXChB= inflarViews_View.findViewById(R.id.seeNewXChB);
        seeModifyXChB=inflarViews_View.findViewById(R.id.seeModifyXChB);
        verCuentas_XChb = inflarViews_View.findViewById(R.id.verCuentas_XChb);
        verBackupsYCierres_XChb = inflarViews_View.findViewById(R.id.verBackupsYCierres_XChb);
        atributos_XTL= inflarViews_View.findViewById(R.id.atributos_XTL);
        area3AttributesXLY= inflarViews_View.findViewById(R.id.area3AttributesXLY);
        area5BackupsClosingXLL= inflarViews_View.findViewById(R.id.area5BackupsClosingXLL);
        cuentaConTransacciones_XTv= inflarViews_View.findViewById(R.id.cuentaConTransacciones_XTv);
        transacciones_XLL= inflarViews_View.findViewById(R.id.transacciones_XLL);
        vercuentaConTransacciones_XGv= inflarViews_View.findViewById(R.id.verCuentaConTransacciones_XGV);
        clickDeleteXBt= inflarViews_View.findViewById(R.id.clickDeleteXBt);
        copyPasteNombreEnNueva_XChb= inflarViews_View.findViewById(R.id.copyPasteNombreEnNueva_XChb);
        copyCuentaABuscarXChb= inflarViews_View.findViewById(R.id.copyCuentaABuscarXChb);
        emulateAttributesAccount_XAct= inflarViews_View.findViewById(R.id.emulateAttributesAccount_XAct);
        titleModifyXTV= inflarViews_View.findViewById(R.id.titleModifyXTV);
        titleNewXTv= inflarViews_View.findViewById(R.id.titleNewXTv);
        cleanFieldsAccount_XBt = (Button)inflarViews_View.findViewById(R.id.cleanFieldsAccount_XBt);

        //2 Nuevas cuentas y modify
        //casting
        item_XTv= inflarViews_View.findViewById(R.id.item_XTv);
        account_XAct= inflarViews_View.findViewById(R.id.account_XAct);
        clickSave_XBt=(Button)inflarViews_View.findViewById(R.id.clickSave_XBt);
        clickUpdate_XBt=(Button)inflarViews_View.findViewById(R.id.clickUpdate_XBt);
        grupo1CuentaNueva_XSp=(Spinner) inflarViews_View.findViewById(R.id.grupo1CuentaNueva_XSp);
        grupo2CuentaNueva_XSp=(Spinner) inflarViews_View.findViewById(R.id.grupo2CuentaNueva_XSp);
        cuentasOrdenAzParaVistaDetalleCuenta_XSp=(Spinner) inflarViews_View.findViewById(R.id.cuentasOrdenAzParaVistaDetalleCuenta_XSp);
        //Casting otros fragments
        consultaPorCuentaYFechaEnOtroFragment_XSp = (Spinner)inflarViews_View.findViewById(R.id.consultaPorCuentaYFechaEnOtroFragment_XSp);
        calculadora_Fragment = new F6_Calculadora();
        valorRecibidoResultadoCalculadora_XTv = (TextView) inflarViews_View.findViewById(R.id.valorRecibidoResultadoCalculadora_XTv);
        opcionSaleDeLaApp_XRb = (RadioButton) inflarViews_View.findViewById(R.id.opcionSaleDeLaApp_XRb);
        area4SeeAccountsXGl = (GridLayout) inflarViews_View.findViewById(R.id.area4SeeAccountsXGl);
        tituloConsultarDEtallesCuentaXTV = (TextView) inflarViews_View.findViewById(R.id.tituloConsultarDEtallesCuentaXTV);
        areaTituloBackupsYCierreXTv = (TextView) inflarViews_View.findViewById(R.id.areaTituloBackupsYCierreXTv);
        verCuentas_XChb = (CheckBox) inflarViews_View.findViewById(R.id.verCuentas_XChb);
        OpcionesCierre_XRg = (RadioGroup) inflarViews_View.findViewById(R.id.OpcionesCierre_XRg);
        _11_opcionBackupDeReservaCuentas_XRb = (RadioButton) inflarViews_View.findViewById(R.id._11_opcionBackupDeReservaCuentas_XRb);
        _2_2_opcionImportarCuentasGoogleSheets_XRb = (RadioButton) inflarViews_View.findViewById(R.id._2_2_opcionImportarCuentasGoogleSheets_XRb);
        _31_opcionRestaurarCuentasBackup_XRb = (RadioButton) inflarViews_View.findViewById(R.id._31_opcionRestaurarCuentasBackup_XRb);
        //areaDocumento_XGl = (GridLayout) inflarViews_View.findViewById(R.id.areaDocumento_XGl);
        mensajeInformativo_XTv= (TextView)inflarViews_View.findViewById(R.id.mensajeInformativo_XTv);
        opcionActualCierre_XTv= (TextView)inflarViews_View.findViewById(R.id.opcionActualCierre_XTv);
        outAccounts_XRb= inflarViews_View.findViewById(R.id.outAccounts_XRb);

        salidaEsteFragment_XBt=(Button)inflarViews_View.findViewById(R.id.salidaEsteFragment_XBt);

        //Salir de este fragment
        salidaEsteFragment_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        cleanFieldsAccount_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                cleanClickFieldsAccount();

            }
        });

        //instancias
        ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null,  version1BalanceSqlite_int_PSF);
        a22QueryManager = new A22_QueryManager(getActivity());
        a3_operacionesBD = new A1_2_OperacionesBD(getActivity());
        A6_3_CSVDriveUploader csvDriveUploader;

        //arrays de String para los spinner de grupos 1 y 2
        nombreGrupo1Cuentas_ArrayString = new String[]{"", "Activo", "Pasivo", "Patrimonio", "Ingresos", "Costo de ventas", "Gastos",
                "Costos de produccion", "Cuentas de orden Db", "Cuentas de orden Cr"};

        nombreGrupo2Cuentas_ArrayString = new String[]{"","Exigible Conciliable",
                "Exigible Conciliable Cerrable",
                "Exigible No conciliable",
                "No exigible Conciliable",
                "No exigible No conciliable",
                "No exigible No conciliable Cerrable"};

        //spinners con array adapters para cuentas
        grupo1CuentaNueva_XSp.setAdapter(new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_multiple_choice, nombreGrupo1Cuentas_ArrayString));
        grupo2CuentaNueva_XSp.setAdapter(new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_multiple_choice, nombreGrupo2Cuentas_ArrayString));

        //eventos
        clickSave_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View inflarViews_View) {

                if(seeNewXChB.isChecked()) {
                    registrarNuevas();
                    verItemsPorCuenta();
                }

                if(seeModifyXChB.isChecked()){
                    clickModify();
                }

            }
        });

        clickUpdate_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View inflarViews_View) {

                if(seeModifyXChB.isChecked()) {
                    clickModify();
                    verItemsPorCuenta();
                    cleanClickFieldsAccount();
                }

                /*if(seeModifyXChB.isChecked()){
                    clickModify();
                }*/

            }
        });


        account_XAct.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_NEXT) {
                    ocultarTeclado();
                    verTeclado();
                    v.clearFocus();
                    grupo1CuentaNueva_XSp.requestFocus();
                    grupo1CuentaNueva_XSp.performClick();
                    focalizarNuevasSpinerGrupo2();

                }
                return true;
            }
        });

        account_XAct.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                count = account_XAct.getText().toString().length();
                if (count>29) {
                    //mensajeSimilaresParaNuevas();
                    Toast.makeText(getActivity(), "Maximo 30 caracteres", Toast.LENGTH_SHORT).show();
                    account_XAct.setFilters(new InputFilter[] { new InputFilter.LengthFilter(30) });
                }

                if (account_XAct.length()>0) {
                    alertaAlEscribirEnModificar_XTiL(String.valueOf(s));
                    selectNameAccount(s);
                    //mensajeSimilaresParaNuevas();
                }
            }
            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        emulateAttributesAccount_XAct.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                count = emulateAttributesAccount_XAct.getText().toString().length();
                if (count>29) {
                    //mensajeSimilaresParaNuevas();
                    Toast.makeText(getActivity(), "Maximo 30 caracteres", Toast.LENGTH_SHORT).show();
                    emulateAttributesAccount_XAct.setFilters(new InputFilter[] { new InputFilter.LengthFilter(30) });
                }

                if (emulateAttributesAccount_XAct.length()>0) {
                    alertaAlEscribirEnModificar_XTiL(String.valueOf(s));
                    selectNameAccount(s);
                    //mensajeSimilaresParaNuevas();
                }
            }
            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        area2crud_XGl.setVisibility(View.INVISIBLE);

        //3 Modificar cuentas

        //modificarCuenta_XBt=(Button)inflarViews_View.findViewById(R.id.modificarCuenta_XBt);
        alertaAlEscribirEnModificar_XTiL = (TextInputLayout) inflarViews_View.findViewById(R.id.alertaAlEscribirEnModificar_XTiL);

        //instancias
        ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null,  version1BalanceSqlite_int_PSF);

        Animation animation = new AlphaAnimation(0.0f,1.0f);
        animation.setDuration(1000);
        animation.setStartOffset((Animation.REVERSE));
        animation.setRepeatCount(Animation.START_ON_FIRST_FRAME);

        //eventos
        dynamicQuery();

        cuentasOrdenAzParaVistaDetalleCuenta_XSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                cuentaSeleccionadaAqui=cuentasOrdenAzParaVistaDetalleCuenta_XSp.getSelectedItem().toString();

                if (cuentasOrdenAzParaVistaDetalleCuenta_XSp.getSelectedItem().equals("")) {
                    atributos_XTL.removeAllViews();
                }
                else {
                    dynamicQuery2();
                    verItemsPorCuenta();
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        listenerOtrosFragments();

        a99_metodosVarios = new A99_MetodosVarios();

        _11_opcionBackupDeReservaCuentas_XRb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mensajeInformativo_XTv.setText("0");
                opcionActualCierre_XTv.setText(_11_opcionBackupDeReservaCuentas_XRb.getText().toString());

                //nombreArchivoBackupCuentas_String = "211_BackupInicialDeTodasLasCuentas.csv";
                nombreArchivoBackupCuentas_String = CSV_ACCOUNTS_BEFORE_STARTING_CLOSING.getFileName();

                        subirCsvBackupCrud(nombreArchivoBackupCuentas_String, 5); // CAMBIO: agregado parámetro

                populateList();

                _2_2_opcionImportarCuentasGoogleSheets_XRb.setChecked(false);
                _31_opcionRestaurarCuentasBackup_XRb.setChecked(false);
                _2_1_opcionSincronizarCuentasDesdeSheets_XRb.setChecked(false); // NUEVO

                mostrarToastConDelay("Backup local y a Drive", DELAY_TOAST_CORTO);
            }
        });

        _2_2_opcionImportarCuentasGoogleSheets_XRb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                mensajeInformativo_XTv.setText("0");

                dialogoCierreCuentas();

                opcionActualCierre_XTv.setText(""+_2_2_opcionImportarCuentasGoogleSheets_XRb.getText());
                //mensajeInformativo_XTv.setText(""+queryAzAllAccounts_Result_ArrayList.size());//? Revisar

                _11_opcionBackupDeReservaCuentas_XRb.setChecked(false);
                _31_opcionRestaurarCuentasBackup_XRb.setChecked(false);

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    Toast.makeText(getActivity(), "Backup local, en Drive y Cuentas restauradas de Sheets", Toast.LENGTH_SHORT).show();

                }, 6000); // 5 segundos
            }
        });

        _31_opcionRestaurarCuentasBackup_XRb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mensajeInformativo_XTv.setText("0");
                opcionActualCierre_XTv.setText("");

                // NUEVO: Diálogo para elegir origen (Local o Drive)
                _3_dialogoElegirOrigenRestauracion();

                _11_opcionBackupDeReservaCuentas_XRb.setChecked(false);
                _2_2_opcionImportarCuentasGoogleSheets_XRb.setChecked(false);
                _2_1_opcionSincronizarCuentasDesdeSheets_XRb.setChecked(false);

                mostrarToastConDelay("Backup local, en Drive y cuentas restauradas", DELAY_TOAST_LARGO);
            }
        });;

        todasLasCuentas_XLv = (ListView) inflarViews_View.findViewById(R.id.todasLasCuentas_XLv);

        setvisibilityOcultarAreas();

        clickDeleteXBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    // ⭐ OBTENER EL NOMBRE DE LA CUENTA DEL CAMPO DE TEXTO
                    String cuentaAEliminar = account_XAct.getText().toString().trim();

                    // Validar que no esté vacío
                    if (esTextoVacio(cuentaAEliminar, "Falta el nombre de la cuenta")) {
                        return;
                    }

                    // ⭐ AGREGAR LOG PARA DEBUG
                    Log.e("DEBUG_DELETE", "Cuenta a eliminar: [" + cuentaAEliminar + "]");
                    Log.e("DEBUG_DELETE", "Transacciones: " + cuentaConTransacciones_Result_ArrayList.size());

                    if (cuentaConTransacciones_Result_ArrayList.size() == 0) {
                        // ⭐ USAR LA VARIABLE LOCAL en lugar de cuentaSeleccionadaAqui
                        a3_operacionesBD.eliminarCuenta(cuentaAEliminar);

                        notificarActualizacionCuentas();
                        limpiarTextoEnCuentaABuscar();
                        cleanClickFieldsAccount();
                        dynamicQuery();

                        mostrarToast("Se borró la cuenta: " + cuentaAEliminar);
                    } else {
                        mostrarToast("La Cuenta no se puede borrar porque tiene movimiento");
                    }

                } catch (Exception e) {
                    mostrarToast("Error al eliminar: " + e.getMessage());
                    Log.e("cuenta", "Error: " + e.getMessage());
                }
            }
        });


        copyPasteNombreEnNueva_XChb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCopyMode) {
                    // Copiar solo si hay texto
                    String currentText = account_XAct.getText().toString();
                    if (!TextUtils.isEmpty(currentText)) {
                        copiedText = currentText;
                        account_XAct.setText("");
                        copyPasteNombreEnNueva_XChb.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_paste,0,0,0);
                        limpiarTextoEnCuentaABuscar();
                        isCopyMode = false;
                    }
                } else {
                    // Pegar si hay texto copiado
                    if (!TextUtils.isEmpty(copiedText)) {
                        account_XAct.setText(copiedText);
                        copyPasteNombreEnNueva_XChb.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_copy,0,0,0);
                        isCopyMode = true;
                    }
                }
            }
        });

        copyCuentaABuscarXChb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCopyMode) {
                    // Copiar solo si hay texto
                    String currentText = emulateAttributesAccount_XAct.getText().toString();
                    if (!TextUtils.isEmpty(currentText)) {
                        copiedText = currentText;
                        emulateAttributesAccount_XAct.setText("");
                        account_XAct.setText("");
                        grupo1CuentaNueva_XSp.setSelection(0);
                        grupo2CuentaNueva_XSp.setSelection(0);
                        copyCuentaABuscarXChb.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_paste,0,0,0);
                        limpiarTextoEnCuentaABuscar();
                        isCopyMode = false;
                    }
                } else {
                    // Pegar si hay texto copiado
                    if (!TextUtils.isEmpty(copiedText)) {
                        emulateAttributesAccount_XAct.setText(copiedText);
                        copyCuentaABuscarXChb.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_copy,0,0,0);
                        isCopyMode = true;
                    }
                }
            }
        });

        setCheckBoxListeners();

        emulateAttributesAccount_XAct.setText("");

        _2_1_opcionSincronizarCuentasDesdeSheets_XRb =
                (RadioButton) inflarViews_View.findViewById(R.id._2_1_opcionSincronizarCuentasDesdeSheets_XRb);

        // Nuevos helpers
        driveDownloader = new A6_2_GoogleDriveDownloader(getActivity(), FOLDER_ID_DRIVE);
        sheetsDownloader = new A6_5_SheetsDownloader(getActivity());

        _2_1_opcionSincronizarCuentasDesdeSheets_XRb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mensajeInformativo_XTv.setText("0");
                opcionActualCierre_XTv.setText("");
                opcionActualCierre_XTv.setText(_2_1_opcionSincronizarCuentasDesdeSheets_XRb.getText().toString());

                _2_1_dialogoConfirmarSincronizacion();

                _11_opcionBackupDeReservaCuentas_XRb.setChecked(false);
                _2_2_opcionImportarCuentasGoogleSheets_XRb.setChecked(false);
                _31_opcionRestaurarCuentasBackup_XRb.setChecked(false);
            }
        });

        _2_1_opcionSincronizarCuentasDesdeSheets_XRb = (RadioButton) inflarViews_View.findViewById(R.id._2_1_opcionSincronizarCuentasDesdeSheets_XRb);

        if (calculadoraLibre_XBt != null) {
            calculadoraLibre_XBt.setOnClickListener(v -> mostrarCalculadoraLibre());
        }

        return inflarViews_View;
    }

    //Life cycle methods
    @Override
    public void onStop() {
        super.onStop();
        // Cerramos la base de datos cuando se detiene el fragment
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Ajusta el tamaño del diálogo aquí
        if (getDialog() != null && getDialog().getWindow() != null) {
            int width = 1400; // Ancho deseado
            int height = ViewGroup.LayoutParams.WRAP_CONTENT;
            getDialog().getWindow().setLayout(width, height);

        }
    }

    public void dynamicQuery () {

        //Consulta todas las cuentas en orden ascendente
        A23_QueryResult queryAzAllAccounts_Result = a22QueryManager.queryAzAllAccounts();
        queryAzAllAccounts_Result_ArrayList = queryAzAllAccounts_Result.getDatos();

        cuentasOrdenAscendente_ArrayAdapterString = new ArrayAdapter<String> (getActivity(),
                android.R.layout.simple_list_item_multiple_choice, queryAzAllAccounts_Result_ArrayList);
        cuentasOrdenAscendente_ArrayAdapterString.setDropDownViewResource(android.R.layout.simple_list_item_multiple_choice);
        cuentasOrdenAzParaVistaDetalleCuenta_XSp.setAdapter(cuentasOrdenAscendente_ArrayAdapterString);

        //Cursor al llamar una cuenta
        if (cuentaNueva_String == null) {
            return;
        }
        llamadaCursorUnaCuenta = A22_QueryManager.queryCursorGenericTableColumnArguments
                ("cuentas","Cuenta",new String[]{cuentaNueva_String});

    }

    public void dynamicQuery2 () {

        //Atributos de cuenta
        // Asegurarse de que no sea null antes de asignar
        A23_QueryResult<String []> queryAttributesByAccount_Result = a22QueryManager.queryAttributesByAccount(cuentaSeleccionadaAqui);

        if (queryAttributesByAccount_Result == null || queryAttributesByAccount_Result.getAtributosCuenta() == null) {
            cuentaSeleccionadaAqui_ArrayS_Result = new String[0]; // Evita la excepción
        } else {
            cuentaSeleccionadaAqui_ArrayS_Result = queryAttributesByAccount_Result.getAtributosCuenta();
        }

        ArrayList<String> cuentaSeleccionadaAqui_ArrayList_Result = new ArrayList<>(Arrays.asList(cuentaSeleccionadaAqui_ArrayS_Result));

    }

    private void setCheckBoxListeners() {
        CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
            if (isChecked) {
                handleCheckBoxSelection((CheckBox) buttonView);
            }
        };

        seeNewXChB.setOnCheckedChangeListener(listener);
        seeModifyXChB.setOnCheckedChangeListener(listener);
        verCuentas_XChb.setOnCheckedChangeListener(listener);
        verBackupsYCierres_XChb.setOnCheckedChangeListener(listener);
    }

    // Listener global para manejar la lógica de los CheckBox
    private CompoundButton.OnCheckedChangeListener listener = (buttonView, isChecked) -> {
        if (isChecked) {
            // Manejar la selección del CheckBox actual
            handleCheckBoxSelection((CheckBox) buttonView);
        }
    };

    private void handleCheckBoxSelection(CheckBox selectedCheckBox) {
        if (lastCheckedBox == null) {
            // No hay checkbox previo seleccionado, seleccionamos el actual.
            lastCheckedBox = selectedCheckBox;
            selectedCheckBox.setChecked(true); // Aseguramos que quede seleccionado.
            executeMethodForCheckBox(selectedCheckBox); // Ejecutamos el método del primer checkbox.
        } else if (lastCheckedBox != selectedCheckBox) {
            boolean hasPendingWork = !emulateAttributesAccount_XAct.getText().toString().trim().isEmpty();

            // Desmarcar temporalmente el nuevo CheckBox hasta que el usuario confirme.
            selectedCheckBox.setOnCheckedChangeListener(null); // Deshabilitar el listener temporalmente.
            selectedCheckBox.setChecked(false);
            selectedCheckBox.setOnCheckedChangeListener(listener); // Rehabilitar el listener.

            if (hasPendingWork && (lastCheckedBox == seeNewXChB || lastCheckedBox == seeModifyXChB)) {
                // Si hay trabajo pendiente, mostramos el diálogo de confirmación.
                showConfirmationDialog(selectedCheckBox);
            } else {
                // Cambiamos al nuevo checkbox directamente.
                switchToCheckBox(selectedCheckBox);
            }
        }
    }

    private void executeMethodForCheckBox(CheckBox checkBox) {
        if (checkBox == seeNewXChB) {
            handleClickViewNew();
        } else if (checkBox == seeModifyXChB) {
            handleClickViewModify();
        } else if (checkBox == verCuentas_XChb) {
            setvisibilityOcultarAreas();
            area4SeeAccountsXGl.setVisibility(View.VISIBLE);
            verBackupsYCierres_XChb.setChecked(false);
            populateList();
        } else if (checkBox == verBackupsYCierres_XChb) {
            setvisibilityOcultarAreas();
            area4SeeAccountsXGl.setVisibility(View.VISIBLE);
            //areaTituloBackupsYCierreXTv.setVisibility(View.VISIBLE);
            area5BackupsClosingXLL.setVisibility(View.VISIBLE);
            populateList();

        }
    }

    private void switchToCheckBox(CheckBox selectedCheckBox) {
        // Desmarcar el checkbox previo
        if (lastCheckedBox != null) {
            lastCheckedBox.setOnCheckedChangeListener(null);
            lastCheckedBox.setChecked(false);
            lastCheckedBox.setOnCheckedChangeListener(listener);
        }

        // Actualizar el checkbox actual como seleccionado
        selectedCheckBox.setOnCheckedChangeListener(null);
        selectedCheckBox.setChecked(true);
        selectedCheckBox.setOnCheckedChangeListener(listener);

        // Limpiar el EditText
        emulateAttributesAccount_XAct.setText("");

        // Actualizar el checkbox previo
        lastCheckedBox = selectedCheckBox;

        // Ejecutar el método correspondiente
        executeMethodForCheckBox(selectedCheckBox);
    }

    private void showConfirmationDialog(CheckBox selectedCheckBox) {
        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Confirmar cambio")
                .setMessage("Tienes trabajo pendiente. ¿Quieres cambiar de opción?")
                .setPositiveButton("Sí", (dialog, which) -> {
                    // Cambiar al nuevo CheckBox
                    selectedCheckBox.setOnCheckedChangeListener(null);
                    selectedCheckBox.setChecked(true);
                    selectedCheckBox.setOnCheckedChangeListener(listener);
                    switchToCheckBox(selectedCheckBox);
                })
                .setNegativeButton("No", (dialog, which) -> {
                    // Mantener el CheckBox previo
                    lastCheckedBox.setOnCheckedChangeListener(null);
                    lastCheckedBox.setChecked(true);
                    lastCheckedBox.setOnCheckedChangeListener(listener);
                })
                .setOnCancelListener(dialog -> {
                    // Cancelar vuelve al CheckBox previo
                    lastCheckedBox.setOnCheckedChangeListener(null);
                    lastCheckedBox.setChecked(true);
                    lastCheckedBox.setOnCheckedChangeListener(listener);
                })
                .show();
    }

    private void handleClickViewModify () {

        try {
            cleanClickFieldsAccount();

            emulateAttributesAccount_XAct.requestFocus();

            setvisibilityOcultarAreas();
            area2crud_XGl.setVisibility(View.VISIBLE);
            area2crud_XGl.setBackgroundColor(Color.parseColor("#FF5722"));
            titleNewXTv.setVisibility(View.GONE);
            titleModifyXTV.setVisibility(View.VISIBLE);
            area3AttributesXLY.setVisibility(View.VISIBLE);
            clickSave_XBt.setVisibility(View.GONE);
            clickUpdate_XBt.setVisibility(View.VISIBLE);
            clickDeleteXBt.setVisibility(View.VISIBLE);

        }
        catch (Exception e) {
            mostrarToast("Error desconocido para el usuario");
        }

        //seeNewXChB.setChecked(false);

    }

    private void handleClickViewNew () {

        try {
            cleanClickFieldsAccount();

            emulateAttributesAccount_XAct.requestFocus();

            setvisibilityOcultarAreas();
            area2crud_XGl.setVisibility(View.VISIBLE);
            area2crud_XGl.setBackgroundColor(Color.parseColor("#F25087"));
            titleNewXTv.setVisibility(View.VISIBLE);
            titleModifyXTV.setVisibility(View.GONE);
            area3AttributesXLY.setVisibility(View.VISIBLE);
            clickSave_XBt.setVisibility(View.VISIBLE);
            clickUpdate_XBt.setVisibility(View.GONE);
            clickDeleteXBt.setVisibility(View.GONE);

            //seeModifyXChB.setChecked(false);
        }
        catch (Exception e) {
            mostrarToast("Error desconocido para el usuario");
        }
    }

//New Methods

    public void registrarNuevas (){

        dateAndTime();
        // CAMBIO AQUÍ: Obtener el siguiente item dinámicamente
        int ultimoItem = a3_operacionesBD.obtenerUltimoItem();
        itemCuentaNueva_String = String.valueOf(ultimoItem + 1);

        cuentaNueva_String = account_XAct.getText().toString();
        grupo1CuentaNueva_String = grupo1CuentaNueva_XSp.getSelectedItem().toString();
        grupo2CuentaNueva_String = grupo2CuentaNueva_XSp.getSelectedItem().toString();
        String [] args = new String [] {cuentaNueva_String};

        dynamicQuery();

        //caso 1 falta el nombre de la cuenta
        if (account_XAct.getText().toString().equals("")) {
            Toast.makeText(getActivity(), "Falta el nombre de la cuenta", Toast.LENGTH_LONG).show();
        }

        //caso 2 verificar si la cuenta existe en la tabla de la bd
        else if ((llamadaCursorUnaCuenta != null && llamadaCursorUnaCuenta.moveToNext())){
            Toast.makeText(getActivity() , "! Este nombre de cuenta ya existe ! ", Toast.LENGTH_SHORT).show();
        }

        //caso 3 falta seleccione grupo y cta mayor
        else if (grupo1CuentaNueva_XSp.getSelectedItem().toString() == "" && grupo2CuentaNueva_XSp.getSelectedItem().toString() == "") {
            Toast.makeText(getActivity(), "Falta seleccione Grupo 1 y 2", Toast.LENGTH_LONG).show();
        }

        //caso 4 falta seleccione grupo
        else if (grupo1CuentaNueva_XSp.getSelectedItem().toString() == "") {
            Toast.makeText(getActivity(), "Falta seleccione Grupo1", Toast.LENGTH_LONG).show();
        }

        //caso 5 falta seleccione cuenta mayor
        else if (grupo2CuentaNueva_XSp.getSelectedItem().toString() == "") {
            Toast.makeText(getActivity(), "Falta seleccione Grupo2", Toast.LENGTH_LONG).show();
        }

        else {
            // Insertar en la base de datos
            a3_operacionesBD.insertarCuentas(itemCuentaNueva_String, cuentaNueva_String,
                    grupo1CuentaNueva_String, grupo2CuentaNueva_String, fechaCuentaNueva_String);

            cleanClickFieldsAccount();
            Toast.makeText(getActivity(), "! Registro de cuenta nueva guardado ! ", Toast.LENGTH_SHORT).show();
            cuentaSeleccionadaAqui = cuentaNueva_String;
            emulateAttributesAccount_XAct.setText("");

            // ⭐ NOTIFICAR A OTROS FRAGMENTS QUE SE ACTUALIZÓ LA BD
            notificarActualizacionCuentas();

            dynamicQuery();
        }
    }

    // ⭐ NUEVO MÉTODO para notificar actualización
    private void notificarActualizacionCuentas() {
        // Opción 1: Usar LocalBroadcastManager (recomendado)
        Intent intent = new Intent("CUENTAS_ACTUALIZADAS");
        LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(intent);
    }

    public void interrelationsAccountsGroups () {
        A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(getActivity(),"balance.db",null , version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudanteBD_Class.getWritableDatabase();

        if (!existingText.isEmpty()) {
            Cursor fila = db.rawQuery
                    ("select Item, Grupo1, Grupo2  from" +
                            " cuentas where Cuenta like '" +
                            existingText + "';",null);

            if (fila.moveToFirst()) {

                String numeroItem_String = fila.getString(0);
                item_XTv.setText(numeroItem_String);

                String nombreCursorAbuscarEnArrayG1= fila.getString(1);
                int indiceEnArrayG1 = 0;
                for (int i = 0; i < nombreGrupo1Cuentas_ArrayString.length; i++) {
                    if (nombreGrupo1Cuentas_ArrayString[i].equals(nombreCursorAbuscarEnArrayG1))
                        indiceEnArrayG1 = i;
                }
                grupo1CuentaNueva_XSp.setSelection(indiceEnArrayG1);

                String nombreCursorAbuscarEnArrayG2= fila.getString(2);
                int indiceEnArrayG2=0;
                for (int i = 0; i < nombreGrupo2Cuentas_ArrayString.length; i++) {
                    if (nombreGrupo2Cuentas_ArrayString[i].equals(nombreCursorAbuscarEnArrayG2))
                        indiceEnArrayG2 = i;
                }
                grupo2CuentaNueva_XSp.setSelection(indiceEnArrayG2);

                //aplica en nuevas

                if(seeNewXChB.isChecked()) {
                    String additionalText = " Renombrar";
                    String arrow = "←"; // Flecha hacia la izquier

                    // Crear un SpannableStringBuilder para combinar ambos textos con diferentes colores
                    SpannableStringBuilder spannable = new SpannableStringBuilder();
                    spannable.append(existingText); // Agregar el texto inicial
                    spannable.append(additionalText); // Agregar el texto adicional
                    int arrowStart = spannable.length(); // Posición inicial de la flecha
                    spannable.append(arrow); // Agregar la flecha

                    // Aplicar un color al texto adicional
                    spannable.setSpan(
                            new ForegroundColorSpan(Color.RED), // Estilo: color rojo
                            existingText.length(), // Inicio del texto adicional
                            spannable.length(), // Fin del texto adicional
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    // Aumentar el tamaño de la flecha
                    spannable.setSpan(
                            new RelativeSizeSpan(2.5f), // Tamaño 1.5 veces mayor
                            arrowStart,
                            spannable.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    // Establecer el texto con el estilo aplicado

                    if(seeNewXChB.isChecked()) {

                        account_XAct.setText(spannable);
                        account_XAct.setEnabled(true);
                    }

                }

                else if (seeModifyXChB.isChecked()) {
                    // Tu lógica aquí
                    account_XAct.setText(existingText);
                    account_XAct.setEnabled(false);
                }


                db.close();

                if(seeNewXChB.isChecked()){
                    int ultimoItem = a3_operacionesBD.obtenerUltimoItem();
                    itemCuentaNueva_String = String.valueOf(ultimoItem + 1);
                    item_XTv.setText(itemCuentaNueva_String);
                }

            } else {
                Toast.makeText(getActivity(), "No existe la cuenta", Toast.LENGTH_SHORT).show();
                db.close();
            }
        }

        else {
            Toast.makeText(getActivity(), "Escribir el nombre de la cuenta a consultar", Toast.LENGTH_SHORT).show();
        }
    }

    public void selectNameAccount (CharSequence s){

        dynamicQuery();

        if(queryAzAllAccounts_Result_ArrayList.size()==0) {
            return;}
        else {
            cuentasPorNombre_ArrayAdapterString = new ArrayAdapter<String>(getActivity(),
                    android.R.layout.simple_dropdown_item_1line, queryAzAllAccounts_Result_ArrayList);
            emulateAttributesAccount_XAct.setAdapter(cuentasPorNombre_ArrayAdapterString) ;
            cuentasPorNombre_ArrayAdapterString.getFilter().filter(s);

            //Evento para cuando se selecciona un detalle de la lista
            emulateAttributesAccount_XAct.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View viewx, int i, long l) {
                    // Texto existente
                    existingText = emulateAttributesAccount_XAct.getText().toString();
                    cuentaSeleccionadaAqui = existingText; //foco campos

                    //establecer atributos
                    String mensajeParaElegir = "Puede aplicar los atributos de una cuenta de la lista";
                    int backgroudColor = Color.parseColor("#E91E63"); // Color del texto de la acción

                    if(seeNewXChB.isChecked()) {

                        showCustomSnackbar(
                                getView(), // Vista raíz
                                mensajeParaElegir, // Mensaje
                                10000, // Duración en milisegundos
                                Color.RED, // Color de fondo
                                Color.WHITE, // Color del texto
                                R.id.emulateAttributesAccount_XAct, // Vista ancla
                                "Aceptar", // Texto del botón positivo
                                Color.YELLOW, // Color del texto del botón positivo
                                view -> {

                                    interrelationsAccountsGroups(); // Acción al aceptar

                                    account_XAct.requestFocus(); // Establece el foco en el campo
                                    account_XAct.setSelection(account_XAct.getText().length());// Coloca el cursor al final del texto actual
                                    account_XAct.setCursorVisible(true);// Asegura que el cursor parpadee
                                    Toast.makeText(view.getContext(), "Acción realizada", Toast.LENGTH_SHORT).show();
                                },
                                "No Aceptar", // Texto del botón negativo
                                Color.YELLOW, // Color del texto del botón negativo
                                view -> {
                                    // Acción al no aceptar
                                    //emulateAttributesAccount_XAct.setText(""); // Limpia el contenido del campo
                                    account_XAct.requestFocus(); // Establece el foco en el campo

                                    // Mostrar el teclado
                                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                                    if (imm != null) {
                                        imm.showSoftInput(account_XAct, InputMethodManager.SHOW_IMPLICIT);
                                    }

                                    // Añadir un efecto visual (opcional)
                                    account_XAct.setBackgroundColor(Color.GREEN); // Cambia el fondo a amarillo
                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                        account_XAct.setBackgroundColor(Color.parseColor("#F4D7F5"));
                                    }, 5000); // 5 segundos
                                }
                        );
                    }

                    if(seeModifyXChB.isChecked()) {
                        interrelationsAccountsGroups(); // Acción al aceptar
                        cuentaConTransacciones();
                        verItemsPorCuenta();
                    }

                }
            });

            emulateAttributesAccount_XAct.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                    limpiarTextoEnCuentaABuscar();
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
                @Override
                public void afterTextChanged(Editable s) {
                }
            });

        }
    }

    public void focalizarNuevasSpinerGrupo2 () {

        grupo1CuentaNueva_XSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ocultarTeclado();
                grupo1CuentaNueva_XSp.clearFocus();
                grupo2CuentaNueva_XSp.requestFocus();
                grupo2CuentaNueva_XSp.performClick();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        grupo2CuentaNueva_XSp.setSelection(0);
        grupo2CuentaNueva_XSp.clearFocus();

    }

    public void cleanClickFieldsAccount () {

        //emulateAttributesAccount_XAct.setText("");
        emulateAttributesAccount_XAct.setText("");
        item_XTv.setText("");
        account_XAct.setText("");
        grupo1CuentaNueva_XSp.setSelection(0);
        grupo2CuentaNueva_XSp.setSelection(0);

        atributos_XTL.removeAllViews();

    }

//update Methods

    public void clickModify () {

        A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(getActivity(),"balance.db",null , version1BalanceSqlite_int_PSF);
        SQLiteDatabase db = ayudanteBD_Class.getWritableDatabase();

        String cuenta = account_XAct.getText().toString();
        String g1 = grupo1CuentaNueva_XSp.getSelectedItem().toString();
        String g2 = grupo2CuentaNueva_XSp.getSelectedItem().toString();

        if (!cuenta.isEmpty() && !g1.isEmpty() && !g2.isEmpty()) {
            ContentValues contenedor_ContentValues = new ContentValues();
            contenedor_ContentValues.put("Cuenta",cuenta);
            contenedor_ContentValues.put("Grupo1",g1);
            contenedor_ContentValues.put("Grupo2",g2);

            int actualizar = db.update("cuentas",contenedor_ContentValues,"Cuenta like '" +
                    cuenta + "';", null);

            db.close();
            if (actualizar == 1) {

                atributos_XTL.removeAllViews();
                Toast.makeText(getActivity(), "Registro modificado", Toast.LENGTH_SHORT).show();
            }

            else{
                Toast.makeText(getActivity(), "La cuenta no existe", Toast.LENGTH_SHORT).show();
            }
        }

        else {
            Toast.makeText(getActivity(), "Debes escriba el nombre de la cuenta", Toast.LENGTH_SHORT).show();
        }
    }

    public void verItemsPorCuenta (){

        atributos_XTL.removeAllViews();
        //Contexto 1 obtener Atributos Cuenta para postrar en el textview

        //Atributos de cuenta
        if (cuentasOrdenAzParaVistaDetalleCuenta_XSp == null || cuentaSeleccionadaAqui == null) {
            return;
        }

        //consultasClass.consultarDetalleCuentaDeCuentaSeleccionada();

        String []  atributos_ArrayS = new String[]{"item", "Nombre cuenta", "Grupo 1", "Grupo 2", "Fecha"};
        // Crear un ArrayList de HashMaps
        ArrayList<HashMap<String, String>> listaConColumnas = new ArrayList<>();

        if (cuentaSeleccionadaAqui_ArrayS_Result == null) {
            return;
        }

        for (int i = 0; i < cuentaSeleccionadaAqui_ArrayS_Result.length; i++) {
            HashMap<String, String> columna = new HashMap<>();
            columna.put(atributos_ArrayS[i], cuentaSeleccionadaAqui_ArrayS_Result[i]); // Nombre del atributo
            listaConColumnas.add(columna);
        }

        // Agregar filas dinámicamente
        for (HashMap<String, String> columna : listaConColumnas) {
            for (Map.Entry<String, String> entry : columna.entrySet()) {
                TableRow tableRow = new TableRow(getActivity());

                // Crear TextViews para atributo y valor
                TextView atributoView = new TextView(getActivity());
                TextView valorView = new TextView(getActivity());

                atributoView.setText(entry.getKey());
                atributoView.setPadding(10, 10, 10, 10);

                valorView.setText(entry.getValue());
                valorView.setPadding(10, 10, 10, 10);

                // Agregar TextViews a la fila
                tableRow.addView(atributoView);
                tableRow.addView(valorView);

                // Agregar fila al TableLayout
                atributos_XTL.addView(tableRow);
            }

            atributos_XTL.setVisibility(View.VISIBLE);
        }

    }

    //Backups and closures Methods

    public void pedirPermisoAlDispositivo () {
        // PERMISOS PARA ANDROID 6 O SUPERIOR
        if(ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(getActivity(), new String[]
                            {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    0);
        }
    }

    private long verNumeroDeRegistrosCuentas () {
        A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF,null, version1BalanceSqlite_int_PSF);
        SQLiteDatabase sqLiteDataBase_Abstracta = ayudanteBD_Class.getReadableDatabase();
        long numeroDeRegistros_Long= DatabaseUtils.queryNumEntries(sqLiteDataBase_Abstracta,"cuentas");
        sqLiteDataBase_Abstracta.close();
        // NUEVO LOG
        return  numeroDeRegistros_Long;
    }

    public void borrarHistorialCuentas (String tablaX_String) {
        A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null,  version1BalanceSqlite_int_PSF);
        SQLiteDatabase sqliteDatabase_Abstracta = ayudanteBD_Class.getWritableDatabase();
        A1_2_OperacionesBD.borrarRegistros(tablaX_String, sqliteDatabase_Abstracta);
        // NUEVO LOG
    }
    public void iniciarCierreConCSVCuentasDeGoogleSheets () {

        pedirPermisoAlDispositivo();

        // En iniciarCierreConCSVCuentasDeGoogleSheets(), cambiar nombres de archivos:
        //nombreArchivoBackupCuentas_String = "222_1_BackupAntesDeRestaurarCuentasDesdeSheets.csv";
        nombreArchivoBackupCuentas_String = CSV_ACCOUNTS_BEFORE_CLOSING_RESTORING_SHEETS.getFileName();

        subirCsvBackupCrud(nombreArchivoBackupCuentas_String, 5);

        File carpeta_File = new File(Environment.getExternalStorageDirectory() + "/Balance/");

        String nombreArchivo_String = carpeta_File.toString() + "/" + CSV_ACCOUNTS_SHEETS_SYNCHRONIZED.getFileName();
        if(!carpeta_File.exists()) {
            Toast.makeText(getActivity(), "No existe la carpeta Balance", Toast.LENGTH_SHORT).show();
            }

        else {
            String lecturaDeCadaLineaDelArchivo_String;
            String[] lineasLeidasDelArchivo_ArrayString;

            try {

                borrarHistorialCuentas("cuentas");
                FileReader leerArchivo_FileReader = new FileReader(nombreArchivo_String);
                BufferedReader lecturaDeCadaLineaDelArchivo_BufferedReader = new BufferedReader(leerArchivo_FileReader);

                while ((lecturaDeCadaLineaDelArchivo_String = lecturaDeCadaLineaDelArchivo_BufferedReader.readLine()) != null) {

                    lineasLeidasDelArchivo_ArrayString = lecturaDeCadaLineaDelArchivo_String.split(",");

                    A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null,  version1BalanceSqlite_int_PSF);
                    SQLiteDatabase sqLiteDatabase_Abstracta = ayudanteBD_Class.getWritableDatabase();

                    ContentValues contenedor_ContentValues = new ContentValues();

                    contenedor_ContentValues.put("Item", lineasLeidasDelArchivo_ArrayString[0]);
                    contenedor_ContentValues.put("Cuenta", lineasLeidasDelArchivo_ArrayString[1]);
                    contenedor_ContentValues.put("Grupo1", lineasLeidasDelArchivo_ArrayString[2]);
                    contenedor_ContentValues.put("Grupo2", lineasLeidasDelArchivo_ArrayString[3]);
                    contenedor_ContentValues.put("Fecha", lineasLeidasDelArchivo_ArrayString[4]);

                    // los inserto en la base de datos
                    sqLiteDatabase_Abstracta.insert("cuentas", null, contenedor_ContentValues);
                    sqLiteDatabase_Abstracta.close();
                }

                } catch (Exception e) {

                Toast.makeText(getActivity(), "Hubo un error", Toast.LENGTH_SHORT).show();
                }
        }
        // Y al final:
        //nombreArchivoBackupCuentas_String = "222_2_BackupDespuesDeRestaurarCuentasDesdeSheets.csv";
        nombreArchivoBackupCuentas_String = CSV_ACCOUNTS_AFTER_CLOSING_RESTORING_SHEETS.getFileName();
        subirCsvBackupCrud(nombreArchivoBackupCuentas_String, 5);
        Toast.makeText(getActivity(), "Archivo 2 guardado localmente", Toast.LENGTH_SHORT).show();
        }

    public void dialogoCierreCuentas () {

        AlertDialog.Builder dialogo_AlertDialog = new AlertDialog.Builder(getActivity());
        dialogo_AlertDialog.setTitle("Primeros registros tabla cuentas");
        dialogo_AlertDialog.setMessage("Actualmente hay: "+verNumeroDeRegistrosCuentas()+" Cuentas"+" " +
                "¿Eliminarlas y colocar nuevas cuentas ?");
        dialogo_AlertDialog.setCancelable(false);
        dialogo_AlertDialog.setPositiveButton("Si", new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialogo_AlertDialog, int id) {

                nombreArchivo_String = CSV_ACCOUNTS_SHEETS_SYNCHRONIZED.getFileName();

                //Ruta y archivo_File
                String rutaDestino_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
                String rutaDestinoYNombreArchivo_String = rutaDestino_String+nombreArchivo_String;
                File archivo_File = new File(rutaDestinoYNombreArchivo_String);

                A23_QueryResult queryAzAllAccounts_Result = a22QueryManager.queryAzAllAccounts();
                ArrayList<String> queryAzAllAccounts_Result_ArrayList = queryAzAllAccounts_Result.getDatos();

                if (archivo_File.exists()) {

                    archivo_File = new File(rutaDestinoYNombreArchivo_String);
                    iniciarCierreConCSVCuentasDeGoogleSheets();

                    opcionActualCierre_XTv.setText(_2_2_opcionImportarCuentasGoogleSheets_XRb.getText().toString());

                    mensajeInformativo_XTv.setText(""+ queryAzAllAccounts_Result_ArrayList.size());
                    populateList();
                    }
                else {

                    Toast.makeText(getActivity(), "No esta el archivo_File de Sheets: "+archivo_File, Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        });

        dialogo_AlertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogo_AlertDialog, int id) {
                populateList();
                Toast.makeText(getActivity(), "No se realizaron acciones", Toast.LENGTH_LONG).show();
            }
        });
        dialogo_AlertDialog.show();
    }

    public void _3_backupAndRestoreDialog () {
         //backup antes de restaurar
        nombreArchivoBackupCuentas_String = CSV_ACCOUNTS_BEFORE_RESTORING_BACKUP_INITIAL.getFileName();
        subirCsvBackupCrud(nombreArchivoBackupCuentas_String, 5);
        AlertDialog.Builder dialogo_AlertDialog = new AlertDialog.Builder(getActivity());
        dialogo_AlertDialog.setTitle("Advertencia");
        dialogo_AlertDialog.setMessage("¿Hay: "+verNumeroDeRegistrosCuentas()+" Cuentas"+" " +
                "¿sustituirlas por las del Backup: de todas las Cuentas csv?");
        dialogo_AlertDialog.setCancelable(false);
        dialogo_AlertDialog.setPositiveButton("Si", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogo_AlertDialog, int id) {

                nombreArchivo_String = CSV_ACCOUNTS_BEFORE_STARTING_CLOSING.getFileName();

                //Ruta y archivo base de restauracion
                String rutaDestino_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/";
                String rutaDestinoYNombreArchivo_String = rutaDestino_String+nombreArchivo_String;//f1
                File archivo_File = new File(rutaDestinoYNombreArchivo_String);

                if (archivo_File.exists()) {

                    archivo_File = new File(rutaDestinoYNombreArchivo_String);

                    //backup despues de restaurar
                    _3_starBackupAndRestore();

                    verNumeroDeRegistrosCuentas();
                    opcionActualCierre_XTv.setText(_31_opcionRestaurarCuentasBackup_XRb.getText().toString());
                    populateList();
                }

                else {

                    Toast.makeText(getActivity(), "No esta el archivo_File: "+archivo_File, Toast.LENGTH_SHORT).show();

                    return;
                }

            }
        });
        dialogo_AlertDialog.setNegativeButton("No", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogo_AlertDialog, int id) {
                Toast.makeText(getActivity(), "No se realizo el proceso", Toast.LENGTH_LONG).show();
            }
        });
        dialogo_AlertDialog.show();
    }

    /**
     * Método mejorado para subir CSV con rotación automática
     */
    public void subirCsvBackupCrud(String nombreArchivoBackupCuentas_String, int maxBackups){
        a6_backupManager = new A5_1_BackupManager();
        a6_backupManager.backupCuentasArchivoCSV(nombreArchivoBackupCuentas_String, a22QueryManager);

        A6_3_CSVDriveUploader.guardarYSubirCuentas(
                requireContext(),
                nombreArchivoBackupCuentas_String,
                FOLDER_ID_DRIVE
        );

        }

    public void _3_starBackupAndRestore () {

        pedirPermisoAlDispositivo();

        borrarHistorialCuentas("cuentas");

        File carpeta_File = new File(Environment.getExternalStorageDirectory() + "/Balance/");
        String nombreArchivo_String = carpeta_File.toString() + "/" + CSV_ACCOUNTS_BEFORE_STARTING_CLOSING.getFileName();

        if(!carpeta_File.exists()) {
            Toast.makeText(getActivity(), "Error en carpeta o archivo", Toast.LENGTH_SHORT).show();

        } else {
            String lecturaDeCadaLineaDelArchivo_String;
            String[] lineasLeidasDelArchivo_ArrayString;
            try {

                FileReader leerArchivo_FileReader = new FileReader(nombreArchivo_String);
                BufferedReader lecturaDeCadaLineaDelArchivo_BufferedReader = new BufferedReader(leerArchivo_FileReader);

                while ((lecturaDeCadaLineaDelArchivo_String = lecturaDeCadaLineaDelArchivo_BufferedReader.readLine()) != null) {

                    lineasLeidasDelArchivo_ArrayString = lecturaDeCadaLineaDelArchivo_String.split(",");

                    A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null,  version1BalanceSqlite_int_PSF);
                    SQLiteDatabase sqliteDatabase_Abstracta = ayudanteBD_Class.getWritableDatabase();

                    ContentValues contenedor_ContentValues = new ContentValues();

                    contenedor_ContentValues.put("Item", lineasLeidasDelArchivo_ArrayString[0]);
                    contenedor_ContentValues.put("Cuenta", lineasLeidasDelArchivo_ArrayString[1]);
                    contenedor_ContentValues.put("Grupo1", lineasLeidasDelArchivo_ArrayString[2]);
                    contenedor_ContentValues.put("Grupo2", lineasLeidasDelArchivo_ArrayString[3]);
                    contenedor_ContentValues.put("Fecha", lineasLeidasDelArchivo_ArrayString[4]);


                    // los inserto en la base de datos
                    sqliteDatabase_Abstracta.insert("cuentas", null, contenedor_ContentValues);
                    sqliteDatabase_Abstracta.close();

                }

                nombreArchivoBackupCuentas_String = CSV_ACCOUNTS_AFTER_RESTORING_BACKUP_INITIAL.getFileName();
                subirCsvBackupCrud(nombreArchivoBackupCuentas_String, 5);
                } catch (Exception e) {
                Toast.makeText(getActivity(), "Hubo un error", Toast.LENGTH_SHORT).show();//f1
            }
        }
    }

//See accounts

    private void populateList () {

        A23_QueryResult queryAllAccounts_Result = a22QueryManager.queryAllAccounts();
        ArrayList<A3_1_TipoCuentasGetsYSets> queryAzAllAccounts_Result_ArrayList = queryAllAccounts_Result.getDatos();

        // IMPORTANTE: Remover encabezados previos antes de agregar uno nuevo
        if (todasLasCuentas_XLv.getHeaderViewsCount() > 0) {
            todasLasCuentas_XLv.removeHeaderView(todasLasCuentas_XLv.getChildAt(0));
        }

        // Crear el encabezado fijo usando el NUEVO layout
        View headerView = getLayoutInflater().inflate(R.layout.df2_1_adaptador_cuentas_encabezado, null);

        final D_F2_AdaptadorCuentas adaptadorTodasLasCuentas_TipoCuentas = new D_F2_AdaptadorCuentas(getActivity(),
                queryAzAllAccounts_Result_ArrayList);
        adaptadorTodasLasCuentas_TipoCuentas.notifyDataSetChanged();
        todasLasCuentas_XLv.setAdapter(adaptadorTodasLasCuentas_TipoCuentas);

        todasLasCuentas_XLv.addHeaderView(headerView);

        mensajeInformativo_XTv.setText(""+ queryAzAllAccounts_Result_ArrayList.size());
    }

    //others fragments
    public void listenerOtrosFragments () {

        //Variables generales
        A23_QueryResult queryAzAllAccounts_Result = a22QueryManager.queryAzAllAccounts();
        ArrayList<String> queryAzAllAccounts_ArrayList_Result = queryAzAllAccounts_Result.getDatos();
        //Adaptador

        cuentasOrdenAscendenteConsultarTransaccionesOtrosFragments_ArrayAdapter =
                new ArrayAdapter <String>(getActivity(), android.R.layout.simple_list_item_multiple_choice,
                queryAzAllAccounts_ArrayList_Result);
        cuentasOrdenAscendenteConsultarTransaccionesOtrosFragments_ArrayAdapter.notifyDataSetChanged();

        consultaPorCuentaYFechaEnOtroFragment_XSp.setAdapter(cuentasOrdenAscendenteConsultarTransaccionesOtrosFragments_ArrayAdapter);
        //Eventos
        consultaPorCuentaYFechaEnOtroFragment_XSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

                if(consultaPorCuentaYFechaEnOtroFragment_XSp.getSelectedItem().toString().isEmpty()){
                    return;
                }
                else{
                    _93_vercuentaConTransaccionesConSpinnerEnF2();
                    consultaPorCuentaYFechaEnOtroFragment_XSp.setSelection(0);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {

            }
        });

        opcionSaleDeLaApp_XRb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {


                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                //builder.setMessage("Necesita conexión a Internet para esta aplicación. Encienda la red móvil o Wi-Fi en Configuración.")
                builder.setTitle("¿Salir de la App ?")
                        .setCancelable(false)
                        .setPositiveButton("Si",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        android.os.Process.killProcess(android.os.Process.myPid()); //Su funcion es algo similar a lo que se llama
                                    }
                                }
                        )
                        .setNegativeButton("Continuar",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                    }
                                }
                        );
                AlertDialog alert = builder.create();
                alert.show();

            }
        });

        outAccounts_XRb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

    }


    public void _93_vercuentaConTransaccionesConSpinnerEnF2 () {

        try {
            String accountToQuery = consultaPorCuentaYFechaEnOtroFragment_XSp.getSelectedItem().toString();
            Fragment fragment = new F3_2_VerItemTransaccion();
            Bundle bundle = new Bundle();
            bundle.putString("keyAccount", accountToQuery);
            fragment.setArguments(bundle);
            getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.contenedor_fragments_f0_Xf,
                    fragment).commit();
        } catch (Exception e) {
        }
    }

//Generic Methods

    private boolean alertaAlEscribirEnModificar_XTiL (String nombre) {
        Pattern patron = Pattern.compile("^[1-9 /]+$");
        if (patron.matcher(nombre).matches() || nombre.length() > 1|| nombre.length() > 21) {
            alertaAlEscribirEnModificar_XTiL.setError(" Continua ");
            return false;
        } else {
            alertaAlEscribirEnModificar_XTiL.setError(null);
        }    return true;
    }

    public static void showCustomSnackbar(View view, String message, int durationMillis,
                                          int backgroundColor, int textColor,
                                          int anchorView, String positiveActionText,
                                          Integer positiveActionColor, View.OnClickListener positiveActionListener,
                                          String negativeActionText, Integer negativeActionColor, View.OnClickListener negativeActionListener) {
        Snackbar snackbar = Snackbar.make(view, "", Snackbar.LENGTH_INDEFINITE);

        // Obtener el diseño del Snackbar y personalizarlo
        Snackbar.SnackbarLayout snackbarLayout = (Snackbar.SnackbarLayout) snackbar.getView();
        snackbarLayout.setBackgroundColor(backgroundColor);

        // Crear un contenedor principal para el contenido
        LinearLayout mainLayout = new LinearLayout(view.getContext());
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Crear un TextView para el mensaje
        TextView messageTextView = new TextView(view.getContext());
        messageTextView.setText(message);
        messageTextView.setTextColor(textColor);
        messageTextView.setTextSize(16);
        messageTextView.setPadding(0, 0, 0, 16); // Espaciado inferior
        messageTextView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Crear un contenedor horizontal para los botones
        LinearLayout buttonLayout = new LinearLayout(view.getContext());
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.END); // Alinear botones a la derecha
        buttonLayout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        // Crear botón "Aceptar"
        Button positiveButton = new Button(view.getContext());
        positiveButton.setText(positiveActionText);
        positiveButton.setTextColor(positiveActionColor != null ? positiveActionColor : Color.WHITE);
        positiveButton.setBackgroundColor(Color.TRANSPARENT);
        positiveButton.setPadding(16, 8, 16, 8);
        positiveButton.setOnClickListener(v -> {
            positiveActionListener.onClick(v);
            snackbar.dismiss();
        });

        // Crear botón "No Aceptar"
        Button negativeButton = new Button(view.getContext());
        negativeButton.setText(negativeActionText);
        negativeButton.setTextColor(negativeActionColor != null ? negativeActionColor : Color.WHITE);
        negativeButton.setBackgroundColor(Color.TRANSPARENT);
        negativeButton.setPadding(16, 8, 16, 8);
        negativeButton.setOnClickListener(v -> {
            negativeActionListener.onClick(v);
            snackbar.dismiss();
        });

        // Agregar botones al layout de botones
        buttonLayout.addView(negativeButton);
        buttonLayout.addView(positiveButton);

        // Agregar el mensaje y los botones al layout principal
        mainLayout.addView(messageTextView);
        mainLayout.addView(buttonLayout);

        // Remplazar el contenido del Snackbar con el diseño personalizado
        snackbarLayout.removeAllViews();
        snackbarLayout.addView(mainLayout);

        // Configurar vista ancla si se proporciona
        if (anchorView != 0) {
            snackbar.setAnchorView(anchorView);
        }

        // Mostrar Snackbar
        snackbar.show();

        // Programar el cierre después del tiempo definido
        new Handler().postDelayed(snackbar::dismiss, durationMillis);
    }

    private boolean esTextoVacio (String texto, String mensajeError) {
        if (texto.isEmpty()) {
            if (mensajeError != null) {
                mostrarToast(mensajeError);
            }
            return true;
        }
        return false;
    }

    private void mostrarToast (String mensaje) {
        Toast.makeText(getActivity(), mensaje, Toast.LENGTH_SHORT).show();
    }

    public void limpiarTextoEnCuentaABuscar () {

        vercuentaConTransacciones_XGv.setVisibility(View.GONE);
        transacciones_XLL.setVisibility(View.GONE);
        //grupo1ModificarCuenta_XSp.setSelection(0);
        //grupo2ModificarCuenta_XSp.setSelection(0);
        atributos_XTL.removeAllViews();
    }

    public  void cuentaConTransacciones () {

        A23_QueryResult cuentaConTransacciones_Result = a22QueryManager.queryTransactionsByAccount(cuentaSeleccionadaAqui);
        cuentaConTransacciones_Result_ArrayList = cuentaConTransacciones_Result.getDatos();

        if (cuentaConTransacciones_Result_ArrayList.size() == 0) {
            return;
        }
        else {
            cuentaConTransacciones_XTv.setText("La cuenta tiene: " + String.valueOf(cuentaConTransacciones_Result_ArrayList.size()) +
                    " transacciones" + ". Con el documento: " + cuentaConTransacciones_Result_ArrayList.get(0).tipoT_1NumberDocument_String +
                    " de fecha: " + cuentaConTransacciones_Result_ArrayList.get(0).tipoT_8DateOfDocument_Integer + ". No se puede borrar");

            cuentaConTransacciones_XTv.setText("La cuenta tiene: " + String.valueOf(cuentaConTransacciones_Result_ArrayList.size()) +
                    " transacciones" + ". No se puede borrar");
            transacciones_XLL.setVisibility(View.VISIBLE);
        }
    }

    public void setvisibilityOcultarAreas () {

        area2crud_XGl.setVisibility(View.GONE);
        area3AttributesXLY.setVisibility(View.GONE);
        area4SeeAccountsXGl.setVisibility(View.GONE);
        area5BackupsClosingXLL.setVisibility(View.GONE);
        atributos_XTL.removeAllViews();

    }

    public void dateAndTime(){

        Date date = new Date();
        fechaCuentaNueva_String= DateFormat.getDateTimeInstance(). format(date);
    }

    public void  verTeclado () {

        View view = getActivity().getCurrentFocus();
        if(view == null) {
            InputMethodManager imm = (InputMethodManager) (getActivity()).getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput((View) view.getWindowToken(), InputMethodManager.SHOW_IMPLICIT);

        }
    }

    public void ocultarTeclado(){
        View view = getActivity().getCurrentFocus();
        if(view != null){
            InputMethodManager input = (InputMethodManager) (getActivity().getSystemService(Context.INPUT_METHOD_SERVICE));
            input.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    //f1

    /**
     * Muestra un Toast con delay
     */
    private void mostrarToastConDelay(String mensaje, int delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() ->
                        Toast.makeText(getActivity(), mensaje, Toast.LENGTH_SHORT).show(),
                delay
        );
    }

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
     * NUEVO: Diálogo para elegir origen de restauración
     */
    private void _3_dialogoElegirOrigenRestauracion() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Elegir origen de restauración");
        builder.setMessage("¿Desde dónde deseas restaurar las cuentas?");

        LinearLayout layout = new LinearLayout(getActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

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
            if (selectedId == 1) {
                _3_backupAndRestoreDialog();
            } else {
                _3_dialogoRestaurarDesdeGoogleDrive();
            }
        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
        });

        builder.setCancelable(false);
        builder.show();
    }

    /**
     * NUEVO: Restaurar desde Google Drive
     */
    private void _3_dialogoRestaurarDesdeGoogleDrive() {
        String prefijo = "211 Backup";
        AlertDialog loadingDialog = new AlertDialog.Builder(getActivity())
                .setTitle("Cargando...")
                .setMessage("Buscando archivos en Drive...")
                .setCancelable(false)
                .create();
        loadingDialog.show();

        driveDownloader.listarArchivos(prefijo, new A6_2_GoogleDriveDownloader.OnFilesListedListener() {
            @Override
            public void onFilesListed(List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
                loadingDialog.dismiss();

                if (archivos == null || archivos.isEmpty()) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Sin archivos")
                            .setMessage("No se encontraron backups de cuentas en Drive.\n\n" +
                                    "Usa primero la Opción 1 para crear un backup.")
                            .setPositiveButton("Entendido", null)
                            .show();
                    return;
                }

                _3_mostrarListaArchivosParaRestaurar(archivos);
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
     * NUEVO: Mostrar lista de archivos disponibles
     */
    private void _3_mostrarListaArchivosParaRestaurar(List<A6_2_GoogleDriveDownloader.DriveFileInfo> archivos) {
        String[] nombresArchivos = new String[archivos.size()];
        for (int i = 0; i < archivos.size(); i++) {
            nombresArchivos[i] = archivos.get(i).nombre;
            }

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Seleccionar archivo de backup");
        builder.setItems(nombresArchivos, (dialog, position) -> {
            A6_2_GoogleDriveDownloader.DriveFileInfo archivoSeleccionado = archivos.get(position);
            _3_confirmarYDescargarArchivo(archivoSeleccionado);
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
        });
        builder.show();
    }

    /**
     * NUEVO: Confirmar y descargar archivo
     */
    private void _3_confirmarYDescargarArchivo(A6_2_GoogleDriveDownloader.DriveFileInfo archivo) {
        long cuentasActuales = verNumeroDeRegistrosCuentas();

        nombreArchivoBackupCuentas_String = CSV_ACCOUNTS_BEFORE_RESTORING_BACKUP_INITIAL.getFileName();
        subirCsvBackupCrud(nombreArchivoBackupCuentas_String, 5);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle("Confirmar restauración");
        builder.setMessage("¿Restaurar desde este archivo?\n\n" +
                "Archivo: " + archivo.nombre + "\n\n" +
                "Cuentas actuales: " + cuentasActuales + "\n" +
                "Se eliminarán y restaurarán desde el backup.");

        builder.setPositiveButton("Sí, restaurar", (dialog, which) -> {
            borrarHistorialCuentas("cuentas");
            _3_descargarYRestaurarDesdeGoogleDrive(archivo);

            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    nombreArchivoBackupCuentas_String = CSV_ACCOUNTS_AFTER_RESTORING_BACKUP_INITIAL.getFileName();
                    subirCsvBackupCrud(nombreArchivoBackupCuentas_String, 5);
                }
            }, 3000);

        });

        builder.setNegativeButton("Cancelar", (dialog, which) -> {
            Toast.makeText(getActivity(), "Operación cancelada", Toast.LENGTH_SHORT).show();
        });

        builder.show();
    }

    /**
     * NUEVO: Descargar y restaurar desde Drive
     */
    private void _3_descargarYRestaurarDesdeGoogleDrive(A6_2_GoogleDriveDownloader.DriveFileInfo archivo) {
        final android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getActivity());
        progressDialog.setMessage("Descargando desde Drive...\n\nPor favor espere");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.show();

        driveDownloader.descargarArchivo(archivo.id, archivo.nombre,
                new A6_2_GoogleDriveDownloader.OnFileDownloadedListener() {
                    @Override
                    public void onFileDownloaded(String localPath) {
                        progressDialog.setMessage("Restaurando cuentas...\n\nÚltimo paso");

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                _3_ejecutarRestauracionDesdeArchivoLocal(localPath);

                                if (progressDialog.isShowing()) {
                                    progressDialog.dismiss();
                                }

                                mostrarMensajeExito("✓ Cuentas restauradas desde Drive");

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
     * NUEVO: Ejecutar restauración desde archivo local
     */
    private void _3_ejecutarRestauracionDesdeArchivoLocal(String rutaArchivo) {
        try {
            File archivo = new File(rutaArchivo);
            String nombreArchivo = archivo.getName();
            importarCuentasDesdeCSV(nombreArchivo);

            long cuentasRestauradas = verNumeroDeRegistrosCuentas();
            mensajeInformativo_XTv.setText(String.valueOf(cuentasRestauradas));
            populateList();

            } catch (Exception e) {
            Log.e(TAG, "Error al restaurar: " + e.getMessage(), e);
            Toast.makeText(getActivity(),
                    "Error al restaurar: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * NUEVO: Importar cuentas desde CSV (busca en ambas ubicaciones)
     */
    private void importarCuentasDesdeCSV(String nombreArchivo) {
        // Ubicación 1: Carpeta de la app (Android 10+)
        File carpetaApp = requireContext().getExternalFilesDir(null);
        String rutaApp = carpetaApp.getAbsolutePath() + "/Balance/" + nombreArchivo;
        File archivoApp = new File(rutaApp);

        // Ubicación 2: Carpeta tradicional
        String rutaTradicional = Environment.getExternalStorageDirectory() + "/Balance/" + nombreArchivo;
        File archivoTradicional = new File(rutaTradicional);

        File archivoAUsar = null;
        String rutaFinal = null;

        if (archivoApp.exists()) {
            archivoAUsar = archivoApp;
            rutaFinal = rutaApp;
            } else if (archivoTradicional.exists()) {
            archivoAUsar = archivoTradicional;
            rutaFinal = rutaTradicional;
            } else {
            Log.e(TAG, "Archivo no encontrado en ninguna ubicación");
            Toast.makeText(getActivity(),
                    "Archivo no encontrado: " + nombreArchivo,
                    Toast.LENGTH_LONG).show();
            return;
        }

        int lineasImportadas = 0;
        try (FileReader fileReader = new FileReader(rutaFinal);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            String linea;
            while ((linea = bufferedReader.readLine()) != null) {
                String[] datos = linea.split(",");
                insertarCuenta(datos);
                lineasImportadas++;
            }

            } catch (Exception e) {
            Log.e(TAG, "Error al importar CSV: " + e.getMessage(), e);
            Toast.makeText(getActivity(),
                    "Error al importar: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * NUEVO: Insertar una cuenta en la base de datos
     */
    private void insertarCuenta(String[] datos) {
        if (datos == null || datos.length < 5) {
            Log.e(TAG, "Datos insuficientes para insertar cuenta");
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
            valores.put("Item", datos[0]);
            valores.put("Cuenta", datos[1]);
            valores.put("Grupo1", datos[2]);
            valores.put("Grupo2", datos[3]);
            valores.put("Fecha", datos[4]);

            db.insert("cuentas", null, valores);
            db.close();
        } catch (Exception e) {
            Log.e(TAG, "Error al insertar cuenta: " + e.getMessage(), e);
        }
    }

    /**
     * NUEVO: Diálogo de confirmación para sincronizar
     */
    private void _2_1_dialogoConfirmarSincronizacion() {
        long cuentasActuales = verNumeroDeRegistrosCuentas();

        new AlertDialog.Builder(getActivity())
                .setTitle("Sincronizar desde Google Sheets")
                .setMessage("Esta operación:\n\n" +
                        "1. Descargará las Cuentas actualizadas desde Sheets\n" +
                        "2. Las subirá a Drive como backup\n" +
                        "3. Estarán disponibles para restaurar en Opción 3\n\n" +
                        "Cuentas actuales en app: " + cuentasActuales + "\n\n" +
                        "¿Continuar?")
                .setPositiveButton("Sí, sincronizar", (dialog, which) -> {
                    _2_1_iniciarSincronizacionSheets();
                })
                .setNegativeButton("Cancelar", (dialog, which) -> {
                    Toast.makeText(getActivity(), "Sincronización cancelada", Toast.LENGTH_SHORT).show();
                })
                .setCancelable(false)
                .show();
    }

    /**
     * NUEVO: Inicia proceso de sincronización desde Sheets
     */
    private void _2_1_iniciarSincronizacionSheets() {
        final android.app.ProgressDialog progressDialog = new android.app.ProgressDialog(getActivity());
        progressDialog.setTitle("Sincronizando");
        progressDialog.setMessage("Descargando desde Google Sheets...\n\nPor favor espere");
        progressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(false);
        progressDialog.show();

        // ⚠️ CAMBIAR POR TU ID REAL DE SHEETS DE CUENTAS
        String sheetsId = "1cPjezc6zSOJHK7WfufhPDBqI4G29z0Zt8_I1Ls0qCXk";//aqui pendiente
        //String nombreArchivo = "221_BackupSheetsCuentasSincronizadas.csv";
        String nombreArchivo = CSV_ACCOUNTS_SHEETS_SYNCHRONIZED.getFileName();

        //String SheetsAApp = "cuentas_sheets_a_app";
        /*sheetsDownloader.descargarCSV(sheetsId,nombreArchivo, SheetsAApp ,
                new A6_5_SheetsDownloader.OnDownloadListener() {*/

        sheetsDownloader.descargarCSV(sheetsId,nombreArchivo,
                new A6_5_SheetsDownloader.OnDownloadListener() {

                    @Override
                    public void onDownloadSuccess(String rutaLocal, int numLineas) {
                        progressDialog.setMessage("Subiendo a Google Drive...\n\nÚltimo paso");

                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            try {
                                A6_3_CSVDriveUploader.guardarYSubirCuentas(
                                        requireContext(),
                                        nombreArchivo,  // ✅ CORRECTO - usar nombreArchivo
                                        FOLDER_ID_DRIVE
                                );                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    progressDialog.dismiss();
                                    _2_1_mostrarResultadoSincronizacion(numLineas, rutaLocal);
                                }, 1500);

                            } catch (Exception e) {
                                progressDialog.dismiss();
                                Log.e("sheets", "Error al subir a Drive: " + e.getMessage(), e);
                                Toast.makeText(getActivity(),
                                        "Error al subir a Drive: " + e.getMessage(),
                                        Toast.LENGTH_LONG).show();
                            }
                        }, 800);
                    }

                    @Override
                    public void onDownloadError(String error) {
                        progressDialog.dismiss();
                        Log.e("sheets", "✗ Error en descarga: " + error);

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
     * NUEVO: Muestra resultado exitoso de la sincronización
     */
    private void _2_1_mostrarResultadoSincronizacion(int numRegistros, String rutaLocal) {

        String nombreArchivo = CSV_ACCOUNTS_SHEETS_SYNCHRONIZED.getFileName();

        String infoArchivo = A6_5_SheetsDownloader.obtenerInfoArchivo(nombreArchivo);

        new AlertDialog.Builder(getActivity())
                .setTitle("✓ Sincronización exitosa")
                .setMessage(
                        "Cuentas descargadas desde Sheets:\n\n" +
                                "• Registros: " + numRegistros + "\n" +
                                "• " + infoArchivo + "\n" +
                                "• Ubicación local: /Balance/\n" +
                                "• Subido a Drive: ✓\n\n" +
                                "Ahora puedes usar la Opción 3 para restaurar estas cuentas."
                )
                .setPositiveButton("Entendido", (dialog, which) -> {
                    mostrarMensajeExito("✓ Cuentas sincronizadas y listas para usar");
                })
                .show();

        }

    //f2

    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;

        // Usar factory method sin callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }

}
