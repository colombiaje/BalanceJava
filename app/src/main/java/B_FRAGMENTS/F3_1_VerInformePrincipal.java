package B_FRAGMENTS;


import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.content.DialogInterface;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.jj.appbalancev31.R;

import org.apache.http.util.TextUtils;

import java.util.ArrayList;

import A1BASES.A1_1_AyudanteBD;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;
import D_ADAPTERS.D_F3_1_AdaptadorTransaccionesInformes;
//import a4.balance.R;

public class F3_1_VerInformePrincipal extends Fragment {

    Button cleanPasteQueryAccount_XChB;
    private boolean isCopyMode = true;
    private String copiedText = "";
    EditText buscar_XTv;

    //Seccion 1 declaracion de variables e instancias

    //Variables xml
    LinearLayout envolventeEncabezado_XLl;
    ListView todasLasTransacciones_XLv;

    //Instancias
    A1_1_AyudanteBD ayudanteBD_Class;
    A22_QueryManager a22QueryManager;
    SQLiteDatabase sqLiteDatabase_Abstracta;
    //Views
    Spinner consultaPorCuentaYFechaEnOtroFragment_XSp;
    TextView valorRecibidoResultadoCalculadora_XTv;
    F6_Calculadora calculadora_Fragment;
    RadioButton opcionSaleDeLaApp_XRb;
    ArrayAdapter<String> consultaCuentasOrdenAscendente_ArrayAdapter;
    //CheckBox limpiarBuscar_XChB;
    String copiarBuscar_String;

    /*
    //Abrir la bd en lectura
    A1_AyudanteBD a1_ayudanteBD;
    private SQLiteDatabase db;
    @Override
    public void onStart() {
        super.onStart();
        // Abrimos la base de datos cuando empieza la operación
        db = a1_ayudanteBD.getReadableDatabase();
    }*/

    SQLiteDatabase db;
    A1_1_AyudanteBD ayudante_Class;

    D_F3_1_AdaptadorTransaccionesInformes sumaPorCuenta_AdaptadorTransaccionesInformes_Class;
    public F3_1_VerInformePrincipal() {
        sumaPorCuenta_AdaptadorTransaccionesInformes_Class = null;
    }

    Button calculadoraLibre_XBt;

    //Seccion 2 onCreateView
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View inflarViews_View = inflater.inflate(R.layout.f3_2_informes_adaptador_transacciones,container,false);

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

        //Casting

        envolventeEncabezado_XLl = (LinearLayout)inflarViews_View.findViewById(R.id.envolventeEncabezado_XLl);
        todasLasTransacciones_XLv = (ListView) inflarViews_View.findViewById(R.id.todasLasTransacciones_XLv);
        //limpiarBuscar_XChB = (CheckBox) inflarViews_View.findViewById(R.id.limpiarBuscar_XChB);
        //Casting otros fragments
        consultaPorCuentaYFechaEnOtroFragment_XSp = (Spinner)inflarViews_View.findViewById(R.id.consultaPorCuentaYFechaEnOtroFragment_XSp);
        calculadora_Fragment = new F6_Calculadora();
        valorRecibidoResultadoCalculadora_XTv = (TextView) inflarViews_View.findViewById(R.id.valorRecibidoResultadoCalculadora_XTv);
        opcionSaleDeLaApp_XRb = (RadioButton) inflarViews_View.findViewById(R.id.opcionSaleDeLaApp_XRb);

        //Variables e instancias

        ayudanteBD_Class = new A1_1_AyudanteBD(getActivity(),balanceSqlite_String_PSF,null,version1BalanceSqlite_int_PSF);
        //consultasClass = new A2ConsultasAnterior(getActivity());
        a22QueryManager = new A22_QueryManager(getActivity());

        sqLiteDatabase_Abstracta = ayudanteBD_Class.getReadableDatabase();

        envolventeEncabezado_XLl.setVisibility(View.VISIBLE);
        verTransaccionesPorCuentaConSpinner();
        dynamicCrudUpdate();
        listenerOtrosFragments();
        todasLasTransacciones_XLv.setAdapter(sumaPorCuenta_AdaptadorTransaccionesInformes_Class);

        buscar_XTv = inflarViews_View.findViewById(R.id.buscar_XTv);
        buscar_XTv.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                sumaPorCuenta_AdaptadorTransaccionesInformes_Class.filtrarPorTextoExcrito(buscar_XTv.getText().toString());

            }
            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        /*limpiarBuscar_XChB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (limpiarBuscar_XChB.isChecked()){
                    copiarBuscar_String = buscar_XTv.getText().toString();
                    buscar_XTv.setText("");
                    buscar_XTv.setBackgroundColor(Color.parseColor("#F4D7F5"));
                    limpiarBuscar_XChB.setChecked(true);
                    limpiarBuscar_XChB.setText("+");

                    // ✅ CERRAR/REMOVER F32
                    Fragment f32 = getActivity().getSupportFragmentManager().findFragmentByTag("F32_TAG");
                    if (f32 != null) {
                        getActivity().getSupportFragmentManager().beginTransaction()
                                .remove(f32)
                                .commit();
                    }
                }
                else if (!limpiarBuscar_XChB.isChecked()) {
                    buscar_XTv.setText(copiarBuscar_String);
                    buscar_XTv.setBackgroundColor(Color.parseColor("#1de9b6"));
                    limpiarBuscar_XChB.setChecked(false);
                    limpiarBuscar_XChB.setText("-");

                }
            }
        });*/

        cleanPasteQueryAccount_XChB = (Button) inflarViews_View.findViewById(R.id.cleanPasteQueryAccount_XChB);

        cleanPasteQueryAccount_XChB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCopyMode) {
                    // Copiar solo si hay texto
                    String currentText = buscar_XTv.getText().toString();
                    if (!TextUtils.isEmpty(currentText)) {
                        copiedText = currentText;
                        buscar_XTv.setText("");
                        cleanPasteQueryAccount_XChB.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_clean_paste,0,0,0);
                        isCopyMode = false;

                        //SharedViewModel viewModel = new ViewModelProvider(requireActivity()).get(SharedViewModel.class);
                        // viewModel.triggerClear();
                    }
                } else {
                    // Pegar si hay texto copiado
                    if (!TextUtils.isEmpty(copiedText)) {
                        buscar_XTv.setText(copiedText);
                        cleanPasteQueryAccount_XChB.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_clean_paste,0,0,0);
                        isCopyMode = true;
                        //Falta colocar los datos de la consulta
                    }
                }
            }
        });

        if (calculadoraLibre_XBt != null) {
            calculadoraLibre_XBt.setOnClickListener(v -> mostrarCalculadoraLibre());
        }

        return inflarViews_View;

    }

    public void dynamicCrudUpdate() {
        ArrayList<A3_2_TipoTransaccionesGetsYSets> resultadoCompleto =
                a22QueryManager.queryNetSumTransactionsAccountByAccount();

        // Filtrar valores distintos de cero
        ArrayList<A3_2_TipoTransaccionesGetsYSets> resultadoFiltrado = new ArrayList<>();
        for (A3_2_TipoTransaccionesGetsYSets item : resultadoCompleto) {
            // Usar el getter correcto: tipoTget_5ValorMetodoEnA5()
            if (item.tipoTget_5ValorMetodoEnA5() != 0) {
                resultadoFiltrado.add(item);
            }
        }

        sumaPorCuenta_AdaptadorTransaccionesInformes_Class =
                new D_F3_1_AdaptadorTransaccionesInformes(getActivity(), resultadoFiltrado);
        todasLasTransacciones_XLv.setAdapter(sumaPorCuenta_AdaptadorTransaccionesInformes_Class);

        A23_QueryResult queryAzAllAccounts_Result = a22QueryManager.queryAzAllAccounts();
        ArrayList<String> queryAzAllAccounts_Result_ArrayLS = queryAzAllAccounts_Result.getDatos();
        consultaCuentasOrdenAscendente_ArrayAdapter = new ArrayAdapter<String>(getActivity(),
                android.R.layout.simple_list_item_multiple_choice,
                queryAzAllAccounts_Result_ArrayLS);
        consultaCuentasOrdenAscendente_ArrayAdapter.notifyDataSetChanged();
        consultaPorCuentaYFechaEnOtroFragment_XSp.setAdapter(consultaCuentasOrdenAscendente_ArrayAdapter);

    }

    public void listenerOtrosFragments () {
         dynamicCrudUpdate();

         //foco 2 listener spiner
        //Eventos
        consultaPorCuentaYFechaEnOtroFragment_XSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {

                if(consultaPorCuentaYFechaEnOtroFragment_XSp.getSelectedItem().toString().isEmpty()){
                    return;
                }
                else{
                    verTransaccionesPorCuentaConSpinnerEnF2();
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
                builder.setTitle("¿Salir de la App ?")
                        //.setTitle("Sale")
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

    }
//foco 1 metodo spiner
    public void verTransaccionesPorCuentaConSpinnerEnF2 () {
        try {
            String accountToQuery = consultaPorCuentaYFechaEnOtroFragment_XSp.getSelectedItem().toString();
            Fragment fragment = new F3_2_VerItemTransaccion();
            Bundle bundle = new Bundle();
            bundle.putString("keyAccount", accountToQuery);
            fragment.setArguments(bundle);
            getActivity().getSupportFragmentManager().beginTransaction().replace(R.id.contenedor_fragments_f0_Xf,
                    fragment).commit();
        }
        catch (Exception e) {
            Toast.makeText(getActivity(), "Error no se puede ver la vista", Toast.LENGTH_SHORT).show();
        }
    }


    //Seccion 3

    //Metodos

    public void verTransaccionesPorCuentaConSpinner() {

//foco 3 item click listview
        todasLasTransacciones_XLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                try {

                    String c3_Cuenta_String = ((TextView) view.findViewById(R.id.c3_Cuenta_XTv)).getText().toString();

                    Fragment fragment = new F3_2_VerItemTransaccion();
                    Bundle bundle = new Bundle();
                    bundle.putString("keyAccount", c3_Cuenta_String);

                    fragment.setArguments(bundle);
                    getActivity().getSupportFragmentManager().beginTransaction()
                            .replace(R.id.contenedor_fragments_f0_Xf, fragment, "F32_TAG") // ✅ AGREGAR TAG AQUÍ
                            .commit();

                } catch (Exception e) {

                    Toast.makeText(getActivity(), "No se puede ver la vista", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    //Cerrar la bd
    @Override
    public void onStop() {
        super.onStop();
        // Cerramos la base de datos cuando se detiene el fragment
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;

        // Usar factory method sin callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }

}