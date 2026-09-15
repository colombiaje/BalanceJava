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
    TextView valorDiasTranscurrido_XTv, valorDiasDiario_XTv, valorDiasPorTranscurrir_XTv, valorDiasMesTotal_XTv;
    TextView valorPresupuestoTranscurrido_XTv, valorPresupuestoDiario_XTv, valorPresupuestoPorTranscurrir_XTv, valorPresupuestoMesTotal_XTv;
    TextView valorDepuradoTranscurrido_XTv, valorDepuradoDiario_XTv, valorDepuradoPorTranscurrir_XTv, valorDepuradoMesProyectado_XTv;
    TextView valorVsDepuradoTranscurrido_XTv, valorVsDepuradoDiario_XTv, valorVsDepuradoPorTranscurrir_XTv, valorVsDepuradoMesProyectado_XTv;
    TextView valorContableTranscurrido_XTv, valorContableDiario_XTv, valorContablePorTranscurrir_XTv, valorContableMesProyectado_XTv;
    TextView valorVsContableTranscurrido_XTv, valorVsContableDiario_XTv, valorVsContablePorTranscurrir_XTv, valorVsContableMesProyectado_XTv;
    ImageButton btnVerDetalleProrrateables_XBt;
    TextView valorActivoExigible_XTv, valorPasivoExigible_XTv, valorAhorroODeuda_XTv;

    // Atajo a las gráficas (F5_3_GraficasIndicadores, diálogo aparte)
    Button graficasIndicadores_XBt;

    EditText editPresupuestoTotal_XEt;
    ImageButton btnGuardarPresupuesto_XBt, salidaEsteFragment_XBt;
    TextView VerDetalleProrrateables_XTv;

    A22_QueryManager a22QueryManager;
    SQLiteDatabase db;
    A1_1_AyudanteBD ayudante_Class;
    SharedPreferences sharedPreferences;

    // Variables de cálculo
    int sumaActivo, sumaPasivo, saldoEnrique, presupuestoTotal;
    int diasMes, diaHoy, diasRestantes, diasTranscurridosReales;
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
        if (btnVerDetalleProrrateables_XBt != null) {
            btnVerDetalleProrrateables_XBt.setOnClickListener(v -> mostrarDetalleProrrateables());
        }

        irALaCuentaContable_XTv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                verTransaccionesLanzandoBundle();
            }
        });

        if (graficasIndicadores_XBt != null) {
            graficasIndicadores_XBt.setOnClickListener(v ->
                    new F5_3_GraficasIndicadores().show(getFragmentManager(), "graficas_indicadores"));
        }

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

        valorDiasTranscurrido_XTv = view.findViewById(R.id.valorDiasTranscurrido_XTv);
        valorDiasDiario_XTv = view.findViewById(R.id.valorDiasDiario_XTv);
        valorDiasPorTranscurrir_XTv = view.findViewById(R.id.valorDiasPorTranscurrir_XTv);
        valorDiasMesTotal_XTv = view.findViewById(R.id.valorDiasMesTotal_XTv);

        valorPresupuestoTranscurrido_XTv = view.findViewById(R.id.valorPresupuestoTranscurrido_XTv);
        valorPresupuestoDiario_XTv = view.findViewById(R.id.valorPresupuestoDiario_XTv);
        valorPresupuestoPorTranscurrir_XTv = view.findViewById(R.id.valorPresupuestoPorTranscurrir_XTv);
        valorPresupuestoMesTotal_XTv = view.findViewById(R.id.valorPresupuestoMesTotal_XTv);

        btnVerDetalleProrrateables_XBt = view.findViewById(R.id.btnVerDetalleProrrateables_XBt);
        valorDepuradoTranscurrido_XTv = view.findViewById(R.id.valorDepuradoTranscurrido_XTv);
        valorDepuradoDiario_XTv = view.findViewById(R.id.valorDepuradoDiario_XTv);
        valorDepuradoPorTranscurrir_XTv = view.findViewById(R.id.valorDepuradoPorTranscurrir_XTv);
        valorDepuradoMesProyectado_XTv = view.findViewById(R.id.valorDepuradoMesProyectado_XTv);

        valorVsDepuradoTranscurrido_XTv = view.findViewById(R.id.valorVsDepuradoTranscurrido_XTv);
        valorVsDepuradoDiario_XTv = view.findViewById(R.id.valorVsDepuradoDiario_XTv);
        valorVsDepuradoPorTranscurrir_XTv = view.findViewById(R.id.valorVsDepuradoPorTranscurrir_XTv);
        valorVsDepuradoMesProyectado_XTv = view.findViewById(R.id.valorVsDepuradoMesProyectado_XTv);

        valorContableTranscurrido_XTv = view.findViewById(R.id.valorContableTranscurrido_XTv);
        valorContableDiario_XTv = view.findViewById(R.id.valorContableDiario_XTv);
        valorContablePorTranscurrir_XTv = view.findViewById(R.id.valorContablePorTranscurrir_XTv);
        valorContableMesProyectado_XTv = view.findViewById(R.id.valorContableMesProyectado_XTv);

        valorVsContableTranscurrido_XTv = view.findViewById(R.id.valorVsContableTranscurrido_XTv);
        valorVsContableDiario_XTv = view.findViewById(R.id.valorVsContableDiario_XTv);
        valorVsContablePorTranscurrir_XTv = view.findViewById(R.id.valorVsContablePorTranscurrir_XTv);
        valorVsContableMesProyectado_XTv = view.findViewById(R.id.valorVsContableMesProyectado_XTv);

        valorActivoExigible_XTv = view.findViewById(R.id.valorActivoExigible_XTv);
        valorPasivoExigible_XTv = view.findViewById(R.id.valorPasivoExigible_XTv);
        valorAhorroODeuda_XTv = view.findViewById(R.id.valorAhorroODeuda_XTv);

        graficasIndicadores_XBt = view.findViewById(R.id.graficasIndicadores_XBt);
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

            // Aproximación "hoy/ayer": si ya hay una transacción registrada exactamente
            // con fecha de hoy, se asume que el día de hoy ya quedó contabilizado y se
            // usa diaHoy; si no, el día de hoy aún no cierra y se usa diaHoy - 1 (mínimo 1).
            boolean hoyYaRegistrado = false;

            if (transacciones != null) {
                int fechaInicio = año * 10000 + mes * 100 + 1;
                int fechaFin = año * 10000 + mes * 100 + diasMes;
                int fechaHoyExacta = año * 10000 + mes * 100 + diaHoy;

                for (A3_2_TipoTransaccionesGetsYSets transaccion : transacciones) {
                    int fecha = transaccion.tipoTget_8FechaInicialMetodoEnA5();

                    String desc = transaccion.tipoTget_6DescripcionMetodoEnA5();
                    Log.d("DEBUG_PRORRATEO", "Transaccion: fecha=" + fecha +
                            ", desc=" + desc +
                            ", fechaInicio=" + fechaInicio +
                            ", fechaFin=" + fechaFin);

                    if (fecha == fechaHoyExacta) {
                        hoyYaRegistrado = true;
                    }

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
            }

            diasTranscurridosReales = hoyYaRegistrado ? diaHoy : Math.max(1, diaHoy - 1);
            diasRestantes = diasMes - diasTranscurridosReales;

            a101CalculoDepuradoIndicadores.calcular(saldoEnrique, diasTranscurridosReales, diasMes);

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

            // Colores: azul = dentro de presupuesto / a favor, rojo = excedido
            int colorAzul = Color.parseColor("#2196F3");
            int colorRojo = Color.parseColor("#E57373");

            // Variables para cálculos
            int vrSaldoContable = saldoEnrique;
            int vrSaldoDepurado = (a101CalculoDepuradoIndicadores != null) ? a101CalculoDepuradoIndicadores.getGastoDepurado() : saldoEnrique;

            // ───────── Fila "Días" ─────────
            valorDiasTranscurrido_XTv.setText(String.valueOf(diasTranscurridosReales));
            valorDiasDiario_XTv.setText("1");
            valorDiasPorTranscurrir_XTv.setText(String.valueOf(diasRestantes));
            valorDiasMesTotal_XTv.setText(String.valueOf(diasMes));

            // ───────── Fila "Presupuesto" ─────────
            float promDiarioBase = (float) presupuestoTotal / diasMes;
            float presupuestoTranscurrido = promDiarioBase * diasTranscurridosReales;
            float presupuestoPorTranscurrir = promDiarioBase * diasRestantes;

            valorPresupuestoTranscurrido_XTv.setText(df.format(presupuestoTranscurrido));
            valorPresupuestoDiario_XTv.setText(df.format(promDiarioBase));
            valorPresupuestoPorTranscurrir_XTv.setText(df.format(presupuestoPorTranscurrir));
            valorPresupuestoMesTotal_XTv.setText(String.valueOf(presupuestoTotal));

            // ───────── Fila "Depurado" ─────────
            float depuradoDiario = (diasTranscurridosReales > 0) ? (float) vrSaldoDepurado / diasTranscurridosReales : 0;
            float depuradoPorTranscurrir = depuradoDiario * diasRestantes;
            float depuradoMesProyectado = vrSaldoDepurado + depuradoPorTranscurrir;

            valorDepuradoTranscurrido_XTv.setText(String.valueOf(vrSaldoDepurado));
            valorDepuradoDiario_XTv.setText(df.format(depuradoDiario));
            valorDepuradoPorTranscurrir_XTv.setText(df.format(depuradoPorTranscurrir));
            valorDepuradoMesProyectado_XTv.setText(String.valueOf(Math.round(depuradoMesProyectado)));

            // ───────── Fila "Vs Presupuesto" (Depurado) — color-only, sin texto de Estado ─────────
            asignarValorConColor(valorVsDepuradoTranscurrido_XTv,
                    Math.round(vrSaldoDepurado - presupuestoTranscurrido), colorAzul, colorRojo);
            asignarValorConColor(valorVsDepuradoDiario_XTv, df,
                    depuradoDiario - promDiarioBase, colorAzul, colorRojo);
            asignarValorConColor(valorVsDepuradoPorTranscurrir_XTv,
                    Math.round(depuradoPorTranscurrir - presupuestoPorTranscurrir), colorAzul, colorRojo);
            asignarValorConColor(valorVsDepuradoMesProyectado_XTv,
                    Math.round(depuradoMesProyectado - presupuestoTotal), colorAzul, colorRojo);

            // ───────── Fila "Contable" ─────────
            float contableDiario = (diasTranscurridosReales > 0) ? (float) vrSaldoContable / diasTranscurridosReales : 0;
            float contablePorTranscurrir = contableDiario * diasRestantes;
            float contableMesProyectado = vrSaldoContable + contablePorTranscurrir;

            valorContableTranscurrido_XTv.setText(String.valueOf(vrSaldoContable));
            valorContableDiario_XTv.setText(df.format(contableDiario));
            valorContablePorTranscurrir_XTv.setText(df.format(contablePorTranscurrir));
            valorContableMesProyectado_XTv.setText(String.valueOf(Math.round(contableMesProyectado)));

            // ───────── Fila "Vs Presupuesto" (Contable) — color-only, sin texto de Estado ─────────
            asignarValorConColor(valorVsContableTranscurrido_XTv,
                    Math.round(vrSaldoContable - presupuestoTranscurrido), colorAzul, colorRojo);
            asignarValorConColor(valorVsContableDiario_XTv, df,
                    contableDiario - promDiarioBase, colorAzul, colorRojo);
            asignarValorConColor(valorVsContablePorTranscurrir_XTv,
                    Math.round(contablePorTranscurrir - presupuestoPorTranscurrir), colorAzul, colorRojo);
            asignarValorConColor(valorVsContableMesProyectado_XTv,
                    Math.round(contableMesProyectado - presupuestoTotal), colorAzul, colorRojo);

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

    /**
     * Colorea un valor "Vs Presupuesto" (ya redondeado) de rojo si está excedido (> 0)
     * o azul si está dentro de presupuesto (<= 0). Reemplaza la antigua fila de "Estado".
     */
    private void asignarValorConColor(TextView tv, int valorRedondeado, int colorAzul, int colorRojo) {
        tv.setText(String.valueOf(valorRedondeado));
        tv.setTextColor(valorRedondeado > 0 ? colorRojo : colorAzul);
    }

    /**
     * Igual que el anterior, pero para valores diarios formateados con decimales.
     */
    private void asignarValorConColor(TextView tv, DecimalFormat df, float valorParaColor, int colorAzul, int colorRojo) {
        tv.setText(df.format(valorParaColor));
        tv.setTextColor(valorParaColor > 0 ? colorRojo : colorAzul);
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
                a101CalculoDepuradoIndicadores, diasTranscurridosReales);
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