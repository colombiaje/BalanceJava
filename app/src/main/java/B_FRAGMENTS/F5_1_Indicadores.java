//dialog fragment
package B_FRAGMENTS;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.jj.appbalancev31.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;

import A1BASES.A1_1_AyudanteBD;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A99_MetodosVarios;
import A1BASES.A10_1_CalculoDepuradoIndicadores;
import A1BASES.A10_2_GastoProrrateableIndicadores;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;

public class F5_1_Indicadores extends DialogFragment implements DialogInterface.OnCancelListener  {

    private int anioSeleccionado;
    private int mesSeleccionado;

    TextView irALaCuentaContable_XTv;

    public F5_1_Indicadores() {
        // Required empty public constructor
    }

    // Views
    TextView valorDiasMes_XTv, valorPresupuestoDepurado_XTv;
    TextView valorDiasBase_XTv, valorPromDiarioBaseContable_XTv, valorPromDiarioBaseDepurado_XTv;
    TextView valorDiasPorEjecutar_XTv, valorPorEjecutarContable_XTv, valorPorEjecutarDepurado_XTv;
    TextView valorDiasPorEjec_XTv, valorPromDiaPorEjecContable_XTv, valorPromDiaPorEjecDepurado_XTv;
    TextView valorDiasEjecutado_XTv, valorEjecutadoContable_XTv, valorEjecutadoDepurado_XTv;
    TextView valorDiasAHoy_XTv, valorPromDiaHoyContable_XTv, valorPromDiaHoyDepurado_XTv;
    TextView valorDiasProyHoy_XTv, valorProyHoyContable_XTv, valorProyHoyDepurado_XTv;
    TextView valorDiasAyer_XTv, valorPromDiaAyerContable_XTv, valorPromDiaAyerDepurado_XTv;
    TextView valorDiasProyAyer_XTv, valorProyAyerContable_XTv, valorProyAyerDepurado_XTv;
    TextView valorActivoExigible_XTv, valorPasivoExigible_XTv, valorAhorroODeuda_XTv;

    EditText editPresupuestoTotal_XEt;
    ImageButton btnGuardarPresupuesto_XBt, salidaEsteFragment_XBt;
    TextView VerDetalleProrrateables_XTv;

    A22_QueryManager a22QueryManager;
    SQLiteDatabase db;
    A1_1_AyudanteBD ayudante_Class;
    SharedPreferences sharedPreferences;

    // Variables de cálculo
    int sumaActivo, sumaPasivo, saldoEnrique, presupuestoTotal;
    int diasMes, diaHoy, diasRestantes;
    A10_1_CalculoDepuradoIndicadores a101CalculoDepuradoIndicadores;

    private static final String PREFS_NAME = "IndicadoresPrefs";
    private static final String KEY_PRESUPUESTO = "presupuesto_mensual";
    private static final int PRESUPUESTO_DEFAULT = 3000;

    private F6_Calculadora calculadora_Fragment;
    Button calculadoraLibre_XBt;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
        sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialogo_Dialog = new Dialog(getActivity(), android.R.style.Theme_Translucent_NoTitleBar);
        final View inflarViews_View = getActivity().getLayoutInflater().inflate(R.layout.f5_1_indicadores, null);

        final Drawable d_Drawable = new ColorDrawable(Color.BLACK);
        d_Drawable.setAlpha(200);

        dialogo_Dialog.getWindow().setBackgroundDrawable(d_Drawable);
        dialogo_Dialog.getWindow().setContentView(inflarViews_View);

        final WindowManager.LayoutParams layoutParams = dialogo_Dialog.getWindow().getAttributes();
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.gravity = Gravity.CENTER;

        dialogo_Dialog.setCanceledOnTouchOutside(true);

        return dialogo_Dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View inflarViews_View = inflater.inflate(R.layout.f5_1_indicadores, container, false);

        calculadora_Fragment = new F6_Calculadora();
        calculadoraLibre_XBt = inflarViews_View.findViewById(R.id.calculadoraLibre_XBt);


        // Abrir BD
        if (ayudante_Class == null) {
            ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
        }
        if (db == null || !db.isOpen()) {
            db = ayudante_Class.getReadableDatabase();
        }

        // Inicializar views
        inicializarViews(inflarViews_View);

        a22QueryManager = new A22_QueryManager(getActivity());

        // Cargar presupuesto guardado
        presupuestoTotal = sharedPreferences.getInt(KEY_PRESUPUESTO, PRESUPUESTO_DEFAULT);
        editPresupuestoTotal_XEt.setText(String.valueOf(presupuestoTotal));
        editPresupuestoTotal_XEt.setInputType(InputType.TYPE_CLASS_NUMBER);

        // Eventos
        salidaEsteFragment_XBt.setOnClickListener(v -> dismiss());
        btnGuardarPresupuesto_XBt.setOnClickListener(v -> guardarPresupuesto());

        VerDetalleProrrateables_XTv.setOnClickListener(v -> mostrarDetalleProrrateables());

        irALaCuentaContable_XTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verTransaccionesLanzandoBundle();
            }
        });

        // Ejecutar cálculos y mostrar valores
        calcularIndicadores();
        asignarValoresALosIndicadores();

        if (calculadoraLibre_XBt != null) {
            calculadoraLibre_XBt.setOnClickListener(v -> mostrarCalculadoraLibre());
        }

        return inflarViews_View;
    }

    private void inicializarViews(View view) {

        irALaCuentaContable_XTv = view.findViewById(R.id.irALaCuentaContable_XTv);

        salidaEsteFragment_XBt = view.findViewById(R.id.salidaEsteFragment_XBt);
        editPresupuestoTotal_XEt = view.findViewById(R.id.editPresupuestoTotal_XEt);
        btnGuardarPresupuesto_XBt = view.findViewById(R.id.btnGuardarPresupuesto_XBt);
        VerDetalleProrrateables_XTv = view.findViewById(R.id.VerDetalleProrrateables_XTv);

        valorDiasMes_XTv = view.findViewById(R.id.valorDiasMes_XTv);
        valorPresupuestoDepurado_XTv = view.findViewById(R.id.valorPresupuestoDepurado_XTv);

        valorDiasBase_XTv = view.findViewById(R.id.valorDiasBase_XTv);
        valorPromDiarioBaseContable_XTv = view.findViewById(R.id.valorPromDiarioBaseContable_XTv);
        valorPromDiarioBaseDepurado_XTv = view.findViewById(R.id.valorPromDiarioBaseDepurado_XTv);

        valorDiasPorEjecutar_XTv = view.findViewById(R.id.valorDiasPorEjecutar_XTv);
        valorPorEjecutarContable_XTv = view.findViewById(R.id.valorPorEjecutarContable_XTv);
        valorPorEjecutarDepurado_XTv = view.findViewById(R.id.valorPorEjecutarDepurado_XTv);

        valorDiasPorEjec_XTv = view.findViewById(R.id.valorDiasPorEjec_XTv);
        valorPromDiaPorEjecContable_XTv = view.findViewById(R.id.valorPromDiaPorEjecContable_XTv);
        valorPromDiaPorEjecDepurado_XTv = view.findViewById(R.id.valorPromDiaPorEjecDepurado_XTv);

        valorDiasEjecutado_XTv = view.findViewById(R.id.valorDiasEjecutado_XTv);
        valorEjecutadoContable_XTv = view.findViewById(R.id.valorEjecutadoContable_XTv);
        valorEjecutadoDepurado_XTv = view.findViewById(R.id.valorEjecutadoDepurado_XTv);

        valorDiasAHoy_XTv = view.findViewById(R.id.valorDiasAHoy_XTv);
        valorPromDiaHoyContable_XTv = view.findViewById(R.id.valorPromDiaHoyContable_XTv);
        valorPromDiaHoyDepurado_XTv = view.findViewById(R.id.valorPromDiaHoyDepurado_XTv);

        valorDiasProyHoy_XTv = view.findViewById(R.id.valorDiasProyHoy_XTv);
        valorProyHoyContable_XTv = view.findViewById(R.id.valorProyHoyContable_XTv);
        valorProyHoyDepurado_XTv = view.findViewById(R.id.valorProyHoyDepurado_XTv);

        valorDiasAyer_XTv = view.findViewById(R.id.valorDiasAyer_XTv);
        valorPromDiaAyerContable_XTv = view.findViewById(R.id.valorPromDiaAyerContable_XTv);
        valorPromDiaAyerDepurado_XTv = view.findViewById(R.id.valorPromDiaAyerDepurado_XTv);

        valorDiasProyAyer_XTv = view.findViewById(R.id.valorDiasProyAyer_XTv);
        valorProyAyerContable_XTv = view.findViewById(R.id.valorProyAyerContable_XTv);
        valorProyAyerDepurado_XTv = view.findViewById(R.id.valorProyAyerDepurado_XTv);

        valorActivoExigible_XTv = view.findViewById(R.id.valorActivoExigible_XTv);
        valorPasivoExigible_XTv = view.findViewById(R.id.valorPasivoExigible_XTv);
        valorAhorroODeuda_XTv = view.findViewById(R.id.valorAhorroODeuda_XTv);
    }

    private void guardarPresupuesto() {
        try {
            String inputPresupuesto = editPresupuestoTotal_XEt.getText().toString().trim();
            if (!inputPresupuesto.isEmpty()) {
                presupuestoTotal = Integer.parseInt(inputPresupuesto);

                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt(KEY_PRESUPUESTO, presupuestoTotal);
                editor.apply();

                calcularIndicadores();
                asignarValoresALosIndicadores();

                Toast.makeText(getActivity(), "Presupuesto guardado: $" + presupuestoTotal,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getActivity(), "Ingrese un valor válido",
                        Toast.LENGTH_SHORT).show();
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getActivity(), "Error: Ingrese solo números",
                    Toast.LENGTH_SHORT).show();
            Log.e("F5_Indicadores", "Error al guardar presupuesto: " + e.getMessage());
        }
    }

    public void dynamicQuery$Values() {
        // Activo Exigible
        String argumento1WhereActivo_String = "c10_Grupo1 = ? AND (c11_Grupo2 = ? OR c11_Grupo2 = ? OR c11_Grupo2= ? OR c11_Grupo2= ?)";
        String[] argumento2WhereArgs = new String[]{"Activo", "Exigible", "Exigible Conciliable",
                "Exigible Conciliable Cerrable", "Exigible No conciliable"};
        A23_QueryResult sumaActivo_Result = a22QueryManager.querySumTransactionsForStringWhere(
                argumento1WhereActivo_String, argumento2WhereArgs);
        sumaActivo = sumaActivo_Result.getSuma();

        // Pasivo exigible
        String argumento1WhereActivo_String2 = "c10_Grupo1 = ? AND (c11_Grupo2 = ? OR c11_Grupo2 = ? OR c11_Grupo2= ? OR c11_Grupo2= ?)";
        String[] argumento2WhereArgs2 = new String[]{"Pasivo", "Exigible", "Exigible Conciliable",
                "Exigible Conciliable Cerrable", "Exigible No conciliable"};
        A23_QueryResult sumaPasivo_Result = a22QueryManager.querySumTransactionsForStringWhere(
                argumento1WhereActivo_String2, argumento2WhereArgs2);
        sumaPasivo = sumaPasivo_Result.getSuma();

        // Saldo CxC Enrique
        A23_QueryResult sumaEnrique_Result = a22QueryManager.querySumTransactionsByAccount("CxC Enrique");
        saldoEnrique = sumaEnrique_Result.getSuma();
    }

    public void calcularIndicadores() {
        dynamicQuery$Values();

        A99_MetodosVarios metodosVarios_Class = new A99_MetodosVarios(getActivity());
        Integer[] dateCurrent_ArrayInteger = metodosVarios_Class.fechasYHoras();

        diasMes = dateCurrent_ArrayInteger[6];
        Log.d("dias","#: "+diasMes);
        diaHoy = dateCurrent_ArrayInteger[2];
        diasRestantes = diasMes - diaHoy;

        calcularGastosDepurados(dateCurrent_ArrayInteger[0], dateCurrent_ArrayInteger[1]);
    }

    private void calcularGastosDepurados(int año, int mes) {
        this.anioSeleccionado = año;
        this.mesSeleccionado = mes;

        a101CalculoDepuradoIndicadores = new A10_1_CalculoDepuradoIndicadores();

        try {
            A23_QueryResult<A3_2_TipoTransaccionesGetsYSets> resultado =
                    a22QueryManager.queryTransactionsByAccount("CxC Enrique");

            ArrayList<A3_2_TipoTransaccionesGetsYSets> transacciones = resultado.getDatos();

            if (transacciones != null) {
                int fechaInicio = año * 10000 + mes * 100 + 1;
                int fechaFin = año * 10000 + mes * 100 + diasMes;

                for (A3_2_TipoTransaccionesGetsYSets transaccion : transacciones) {
                    int fecha = transaccion.tipoTget_8FechaInicialMetodoEnA5();

                    String desc = transaccion.tipoTget_6DescripcionMetodoEnA5();
                    Log.d("DEBUG_PRORRATEO", "Transaccion: fecha=" + fecha +
                            ", desc=" + desc +
                            ", fechaInicio=" + fechaInicio +
                            ", fechaFin=" + fechaFin);

                    if (fecha >= fechaInicio && fecha <= fechaFin) {
                        String documento  = transaccion.tipoTget_1DocumentoMetodoEnA5();
                        int valor         = transaccion.tipoTget_5ValorMetodoEnA5();
                        String descripcion = transaccion.tipoTget_6DescripcionMetodoEnA5();
                        String fechaStr   = transaccion.tipoTget_7FechaYHoraMetodoEnA5();
                        int fechaInicial  = transaccion.tipoTget_8FechaInicialMetodoEnA5(); // ← c8_FechaInicial (YYYYMMDD)

                        A10_2_GastoProrrateableIndicadores gasto =
                                A10_2_GastoProrrateableIndicadores.parseDescripcion(
                                        descripcion, valor, fechaStr, documento, fechaInicial); // ← +1 parámetro

                        if (gasto != null) {
                            a101CalculoDepuradoIndicadores.agregarGasto(gasto);
                        }
                    }
                }

                verificarConsistenciaProrrateables(transacciones);

                a101CalculoDepuradoIndicadores.calcular(saldoEnrique, diaHoy, diasMes);
            }

            a101CalculoDepuradoIndicadores.calcular(saldoEnrique, diaHoy, diasMes);

        } catch (Exception e) {
            Log.e("F5_Indicadores", "Error al calcular gastos depurados: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void asignarValoresALosIndicadores() {
        try {
            DecimalFormat df = new DecimalFormat("#,##0.0");
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setDecimalSeparator(',');
            symbols.setGroupingSeparator('.');
            df.setDecimalFormatSymbols(symbols);

            // Colores
            int colorAzul = Color.parseColor("#2196F3");
            int colorRojo = Color.parseColor("#E57373");

            // Variables para cálculos
            int vrSaldoContable = saldoEnrique;
            int vrSaldoDepurado = (a101CalculoDepuradoIndicadores != null) ? a101CalculoDepuradoIndicadores.getGastoDepurado() : saldoEnrique;

            // Fila 1: Presupuesto
            valorDiasMes_XTv.setText(String.valueOf(diasMes));
            valorPresupuestoDepurado_XTv.setText(String.valueOf(presupuestoTotal));

            // Fila 2: Promedio diario base
            float promDiarioBase = (float) presupuestoTotal / diasMes;
            valorDiasBase_XTv.setText(String.valueOf(diasMes));
            valorPromDiarioBaseContable_XTv.setText(df.format(promDiarioBase));
            valorPromDiarioBaseDepurado_XTv.setText(df.format(promDiarioBase));

            // Fila 3: Por ejecutar
            int porEjecutarContable = presupuestoTotal - vrSaldoContable;
            int porEjecutarDepurado = presupuestoTotal - vrSaldoDepurado;
            valorDiasPorEjecutar_XTv.setText(String.valueOf(diasRestantes));
            valorPorEjecutarContable_XTv.setText(String.valueOf(porEjecutarContable));
            valorPorEjecutarDepurado_XTv.setText(String.valueOf(porEjecutarDepurado));

            valorPorEjecutarContable_XTv.setTextColor(porEjecutarContable >= 0 ? colorAzul : colorRojo);
            valorPorEjecutarDepurado_XTv.setTextColor(porEjecutarDepurado >= 0 ? colorAzul : colorRojo);

            // Fila 4: Promedio día por ejecutar
            float promDiaPorEjecContable = (diasRestantes > 0) ? (float) porEjecutarContable / diasRestantes : 0;
            float promDiaPorEjecDepurado = (diasRestantes > 0) ? (float) porEjecutarDepurado / diasRestantes : 0;
            valorDiasPorEjec_XTv.setText(String.valueOf(diasRestantes));
            valorPromDiaPorEjecContable_XTv.setText(df.format(promDiaPorEjecContable));
            valorPromDiaPorEjecDepurado_XTv.setText(df.format(promDiaPorEjecDepurado));

            valorPromDiaPorEjecContable_XTv.setTextColor(promDiaPorEjecContable >= 0 ? colorAzul : colorRojo);
            valorPromDiaPorEjecDepurado_XTv.setTextColor(promDiaPorEjecDepurado >= 0 ? colorAzul : colorRojo);

            // Fila 5: Ejecutado
            valorDiasEjecutado_XTv.setText(String.valueOf(diaHoy));
            valorEjecutadoContable_XTv.setText(String.valueOf(vrSaldoContable));
            valorEjecutadoDepurado_XTv.setText(String.valueOf(vrSaldoDepurado));

            valorEjecutadoContable_XTv.setTextColor(vrSaldoContable <= presupuestoTotal ? colorAzul : colorRojo);
            valorEjecutadoDepurado_XTv.setTextColor(vrSaldoDepurado <= presupuestoTotal ? colorAzul : colorRojo);

            // Fila 6: Promedio día a hoy
            float promHoyContable = (diaHoy > 0) ? (float) vrSaldoContable / diaHoy : 0;
            float promHoyDepurado = (diaHoy > 0) ? (float) vrSaldoDepurado / diaHoy : 0;
            valorDiasAHoy_XTv.setText(String.valueOf(diaHoy));
            valorPromDiaHoyContable_XTv.setText(df.format(promHoyContable));
            valorPromDiaHoyDepurado_XTv.setText(df.format(promHoyDepurado));

            valorPromDiaHoyContable_XTv.setTextColor(promHoyContable <= promDiarioBase ? colorAzul : colorRojo);
            valorPromDiaHoyDepurado_XTv.setTextColor(promHoyDepurado <= promDiarioBase ? colorAzul : colorRojo);

            // Fila 7: Proyectado desde hoy
            float proyHoyContable = promHoyContable * diasMes;
            float proyHoyDepurado = promHoyDepurado * diasMes;
            valorDiasProyHoy_XTv.setText(String.valueOf(diasMes));
            Log.d("dias","#: "+diasMes);
            valorProyHoyContable_XTv.setText(String.valueOf(Math.round(proyHoyContable)));
            valorProyHoyDepurado_XTv.setText(String.valueOf(Math.round(proyHoyDepurado)));

            valorProyHoyContable_XTv.setTextColor(proyHoyContable <= presupuestoTotal ? colorAzul : colorRojo);
            valorProyHoyDepurado_XTv.setTextColor(proyHoyDepurado <= presupuestoTotal ? colorAzul : colorRojo);

            // Fila 8: Promedio día ayer
            int diasAyer = (diaHoy > 1) ? (diaHoy - 1) : 1;
            float promAyerContable = (float) vrSaldoContable / diasAyer;
            float promAyerDepurado = (float) vrSaldoDepurado / diasAyer;
            valorDiasAyer_XTv.setText(String.valueOf(diasAyer));
            valorPromDiaAyerContable_XTv.setText(df.format(promAyerContable));
            valorPromDiaAyerDepurado_XTv.setText(df.format(promAyerDepurado));

            valorPromDiaAyerContable_XTv.setTextColor(promAyerContable <= promDiarioBase ? colorAzul : colorRojo);
            valorPromDiaAyerDepurado_XTv.setTextColor(promAyerDepurado <= promDiarioBase ? colorAzul : colorRojo);

            // Fila 9: Proyectado desde ayer
            float proyAyerContable = promAyerContable * diasMes;
            float proyAyerDepurado = promAyerDepurado * diasMes;
            valorDiasProyAyer_XTv.setText(String.valueOf(diasMes));
            valorProyAyerContable_XTv.setText(String.valueOf(Math.round(proyAyerContable)));
            valorProyAyerDepurado_XTv.setText(String.valueOf(Math.round(proyAyerDepurado)));

            valorProyAyerContable_XTv.setTextColor(proyAyerContable <= presupuestoTotal ? colorAzul : colorRojo);
            valorProyAyerDepurado_XTv.setTextColor(proyAyerDepurado <= presupuestoTotal ? colorAzul : colorRojo);

            // Balance General
            valorActivoExigible_XTv.setText(String.valueOf(sumaActivo));
            valorPasivoExigible_XTv.setText(String.valueOf(sumaPasivo));
            int diferenciaExigible = sumaActivo + sumaPasivo;
            valorAhorroODeuda_XTv.setText(String.valueOf(diferenciaExigible));

            if (diferenciaExigible < 0) {
                valorAhorroODeuda_XTv.setTextColor(Color.RED);
            } else {
                valorAhorroODeuda_XTv.setTextColor(Color.GREEN);
                valorAhorroODeuda_XTv.setBackgroundColor(R.color.colorPrimary);
            }

        } catch (Exception e) {
            Log.e("F5_Indicadores", "Error al asignar valores: " + e.getMessage());
        }
    }

    private void mostrarDetalleProrrateables() {
        if (a101CalculoDepuradoIndicadores == null || a101CalculoDepuradoIndicadores.getCantidadProrrateables() == 0) {
            Toast.makeText(getActivity(),
                    "No hay gastos prorrateables registrados este mes\n\n" +
                            "Para registrar use:\n[15 dias Nómina quincenal]",
                    Toast.LENGTH_LONG).show();
            return;
        }

        F5_2_DetalleProrrateables dialogoDetalle = F5_2_DetalleProrrateables.newInstance(
                a101CalculoDepuradoIndicadores, diaHoy);
        dialogoDetalle.show(getFragmentManager(), "detalle_prorrateables");
    }

    @Override
    public void onStop() {
        super.onStop();
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    public void verTransaccionesLanzandoBundle() {
        try {
            String accountToQuery = "CxC Enrique";
            Fragment fragment = new F3_2_VerItemTransaccion();

            Bundle bundle = new Bundle();
            bundle.putString("keyAccount", accountToQuery);
            bundle.putInt("keyAnio", anioSeleccionado);
            bundle.putInt("keyMes", mesSeleccionado);
            bundle.putBoolean("keyDesdeIndicadores", true);

            fragment.setArguments(bundle);

            getActivity().getSupportFragmentManager().beginTransaction()
                    .add(R.id.contenedor_fragments_f0_Xf, fragment)
                    .addToBackStack(null)
                    .commit();

            Log.d("CxCE", "Navegando a transacciones: Año=" + anioSeleccionado +
                    ", Mes=" + mesSeleccionado);

        } catch (Exception e) {
            Toast.makeText(getActivity(), "No se puede ver la vista",
                    Toast.LENGTH_SHORT).show();
            Log.e("CxCE", "Error: " + e.getMessage());
        }
    }

    /**
     * Verifica inconsistencias entre alias encontrados y registros parseados
     */
    private void verificarConsistenciaProrrateables(ArrayList<A3_2_TipoTransaccionesGetsYSets> transacciones) {
        int conteoAlias = 0;
        int conteoParsedExitoso = 0;
        ArrayList<String> aliasNoDetectados = new ArrayList<>();

        for (A3_2_TipoTransaccionesGetsYSets transaccion : transacciones) {
            String desc = transaccion.tipoTget_6DescripcionMetodoEnA5();

            if (desc != null && (desc.contains("[") || desc.contains("("))) {
                if (desc.matches(".*[\\[\\(].*\\d+.*d[ií]as.*[\\]\\)].*")) {
                    conteoAlias++;

                    int fechaInicial = transaccion.tipoTget_8FechaInicialMetodoEnA5(); // ← c8_FechaInicial

                    A10_2_GastoProrrateableIndicadores test =
                            A10_2_GastoProrrateableIndicadores.parseDescripcion(
                                    desc,
                                    transaccion.tipoTget_5ValorMetodoEnA5(),
                                    transaccion.tipoTget_7FechaYHoraMetodoEnA5(),
                                    transaccion.tipoTget_1DocumentoMetodoEnA5(),
                                    fechaInicial  // ← +1 parámetro
                            );

                    if (test != null) {
                        conteoParsedExitoso++;
                    } else {
                        aliasNoDetectados.add("Doc " + transaccion.tipoTget_1DocumentoMetodoEnA5() +
                                ": '" + desc + "'");
                    }
                }
            }
        }

        int registrosEnCalculo = a101CalculoDepuradoIndicadores.getCantidadProrrateables();

        Log.i("VERIFICACION_PRORRATEO", "═══════════════════════════════════");
        Log.i("VERIFICACION_PRORRATEO", "📊 INFORME DE PRORRATEABLES:");
        Log.i("VERIFICACION_PRORRATEO", "   Alias detectados: " + conteoAlias);
        Log.i("VERIFICACION_PRORRATEO", "   Parseados exitosos: " + conteoParsedExitoso);
        Log.i("VERIFICACION_PRORRATEO", "   En cálculo final: " + registrosEnCalculo);

        if (conteoAlias != registrosEnCalculo) {
            Log.w("VERIFICACION_PRORRATEO", "⚠️ INCONSISTENCIA DETECTADA!");
            Log.w("VERIFICACION_PRORRATEO", "   Diferencia: " + (conteoAlias - registrosEnCalculo) + " registros");
        }

        if (!aliasNoDetectados.isEmpty()) {
            Log.e("VERIFICACION_PRORRATEO", "❌ REGISTROS NO DETECTADOS:");
            for (String error : aliasNoDetectados) {
                Log.e("VERIFICACION_PRORRATEO", "   • " + error);
            }
        }

        if (conteoAlias == registrosEnCalculo && aliasNoDetectados.isEmpty()) {
            Log.i("VERIFICACION_PRORRATEO", "✅ TODO CORRECTO");
        }
        Log.i("VERIFICACION_PRORRATEO", "═══════════════════════════════════");
    }

    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;

        // Usar factory method sin callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }

}