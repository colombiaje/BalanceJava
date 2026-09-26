//dialog fragment
package B_FRAGMENTS;

import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.jj.appbalancev31.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;

import A1BASES.A1_1_AyudanteBD;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A99_MetodosVarios;
import A1BASES.A10_1_CalculoDepuradoIndicadores;
import A1BASES.A10_2_GastoProrrateableIndicadores;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;

/**
 * Diálogo independiente con las gráficas de Indicadores (antes incrustadas al final de
 * F5_1_Indicadores, movidas aquí para que esa pantalla vuelva a ser una vista rápida).
 * Se abre desde el ícono 📊 en el encabezado de F5_1_Indicadores, y también desde
 * F3_2_VerItemTransaccion cuando la cuenta consultada es la marcada como "cuenta_seguimiento"
 * (ver A1_1_AyudanteBD, migración v10 — antes era el nombre fijo "CxC Enrique"). Es autocontenido:
 * consulta y calcula sus propios datos exactamente igual que F5_1_Indicadores, así que
 * no depende de que haya una instancia de Indicadores abierta — puede lanzarse solo.
 */
public class F5_3_GraficasIndicadores extends DialogFragment {

    private static final String PREFS_NAME = "IndicadoresPrefs";
    private static final String KEY_PRESUPUESTO = "presupuesto_mensual";
    private static final int PRESUPUESTO_DEFAULT = 3000;
    // ⭐ CAMBIO v10 — Fase 6 (parte B): ya no es una constante fija — se resuelve dinámicamente
    // desde la cuenta marcada como "cuenta_seguimiento" (ver A1_1_AyudanteBD, migración v10, y
    // el mismo cambio en F5_1_Indicadores). Puede quedar null si ninguna cuenta está marcada.
    private String cuentaSeguimiento_String;
    private static final String ARG_VIENE_DE_PANEL_CIFRAS = "argVieneDePanelCifras";

    // true si se abrió desde el ícono 📊 de F5_1_Indicadores (panel de cifras ya abierto
    // detrás de este diálogo); false si vino del atajo en F3_2_VerItemTransaccion (no hay
    // panel de cifras abierto todavía). Determina si el botón 🔙 tiene sentido mostrarse.
    private boolean vieneDePanelCifras = false;

    ImageButton salidaEsteFragment_XBt;
    BarChart graficaComparacion_XBc;
    LineChart graficaTendencia_XLc;
    TextView deltaGraficaDepurado_XTv, deltaGraficaContable_XTv;

    private F6_Calculadora calculadora_Fragment;
    Button calculadoraLibre_XBt;

    // Atajo de vuelta a F5_1_Indicadores (panel de cifras)
    Button irAPanelCifras_XBt;

    A22_QueryManager a22QueryManager;
    SQLiteDatabase db;
    A1_1_AyudanteBD ayudante_Class;
    SharedPreferences sharedPreferences;

    int saldoEnrique, presupuestoTotal;
    int diasMes, diaHoy, diasRestantes, diasTranscurridosReales;
    A10_1_CalculoDepuradoIndicadores a101CalculoDepuradoIndicadores;
    float[] serieDepuradoDiaria;

    public F5_3_GraficasIndicadores() {
        // Constructor público vacío requerido
    }

    /**
     * @param vieneDePanelCifras true si se abre desde F5_1_Indicadores (ese panel ya está
     *                           abierto detrás), false si se abre desde el atajo de
     *                           F3_2_VerItemTransaccion.
     */
    public static F5_3_GraficasIndicadores newInstance(boolean vieneDePanelCifras) {
        F5_3_GraficasIndicadores fragment = new F5_3_GraficasIndicadores();
        Bundle args = new Bundle();
        args.putBoolean(ARG_VIENE_DE_PANEL_CIFRAS, vieneDePanelCifras);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
        sharedPreferences = getActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Bundle args = getArguments();
        if (args != null) {
            vieneDePanelCifras = args.getBoolean(ARG_VIENE_DE_PANEL_CIFRAS, false);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialogo_Dialog = new Dialog(getActivity(), android.R.style.Theme_Translucent_NoTitleBar);
        final View inflarViews_View = getActivity().getLayoutInflater().inflate(R.layout.f5_3_graficas_indicadores, null);

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
        View inflarViews_View = inflater.inflate(R.layout.f5_3_graficas_indicadores, container, false);

        calculadora_Fragment = new F6_Calculadora();
        calculadoraLibre_XBt = inflarViews_View.findViewById(R.id.calculadoraLibre_XBt);
        irAPanelCifras_XBt = inflarViews_View.findViewById(R.id.irAPanelCifras_XBt);

        if (ayudante_Class == null) {
            ayudante_Class = new A1_1_AyudanteBD(getActivity(), balanceSqlite_String_PSF, null, version1BalanceSqlite_int_PSF);
        }
        if (db == null || !db.isOpen()) {
            db = ayudante_Class.getReadableDatabase();
        }

        inicializarViews(inflarViews_View);

        a22QueryManager = new A22_QueryManager(getActivity());
        presupuestoTotal = sharedPreferences.getInt(KEY_PRESUPUESTO, PRESUPUESTO_DEFAULT);

        salidaEsteFragment_XBt.setOnClickListener(v -> dismiss());
        if (calculadoraLibre_XBt != null) {
            calculadoraLibre_XBt.setOnClickListener(v -> mostrarCalculadoraLibre());
        }
        if (irAPanelCifras_XBt != null) {
            if (vieneDePanelCifras) {
                // El panel de cifras ya está abierto detrás de este diálogo (se llegó
                // desde su ícono 📊): el atajo sobraría, haría lo mismo que la ❌.
                irAPanelCifras_XBt.setVisibility(View.GONE);
            } else {
                irAPanelCifras_XBt.setOnClickListener(v -> irAPanelDeCifras());
            }
        }

        calcularYMostrarGraficas();

        return inflarViews_View;
    }

    private void inicializarViews(View view) {
        salidaEsteFragment_XBt = view.findViewById(R.id.salidaEsteFragment_XBt);
        graficaComparacion_XBc = view.findViewById(R.id.graficaComparacion_XBc);
        graficaTendencia_XLc = view.findViewById(R.id.graficaTendencia_XLc);
        deltaGraficaDepurado_XTv = view.findViewById(R.id.deltaGraficaDepurado_XTv);
        deltaGraficaContable_XTv = view.findViewById(R.id.deltaGraficaContable_XTv);
    }

    /**
     * Consulta y calcula todo lo necesario (idéntico a como lo hacía F5_1_Indicadores) y
     * puebla las dos gráficas.
     */
    private void calcularYMostrarGraficas() {
        try {
            cuentaSeguimiento_String = a22QueryManager.queryNombreCuentaSeguimiento();
            if (cuentaSeguimiento_String != null) {
                A23_QueryResult sumaEnrique_Result =
                        a22QueryManager.querySumTransactionsByAccount(cuentaSeguimiento_String);
                saldoEnrique = sumaEnrique_Result.getSuma();
            } else {
                saldoEnrique = 0;
                Log.w("F5_3_Graficas", "Ninguna cuenta está marcada como cuenta_seguimiento — " +
                        "las gráficas quedan sin cuenta de gasto diario hasta que el usuario " +
                        "marque una desde F2_Cuentas.");
            }

            A99_MetodosVarios metodosVarios_Class = new A99_MetodosVarios(getActivity());
            Integer[] dateCurrent_ArrayInteger = metodosVarios_Class.fechasYHoras();
            diasMes = dateCurrent_ArrayInteger[6];
            diaHoy = dateCurrent_ArrayInteger[2];

            calcularGastosDepurados(dateCurrent_ArrayInteger[0], dateCurrent_ArrayInteger[1]);

            DecimalFormat df = new DecimalFormat("#,##0.0");
            DecimalFormatSymbols symbols = new DecimalFormatSymbols();
            symbols.setDecimalSeparator(',');
            symbols.setGroupingSeparator('.');
            df.setDecimalFormatSymbols(symbols);

            // Colores: azul = dentro de presupuesto / a favor, rojo = excedido (mismos que en la grilla)
            int colorAzul = Color.parseColor("#2196F3");
            int colorRojo = Color.parseColor("#E57373");

            int vrSaldoContable = saldoEnrique;
            int vrSaldoDepurado = (a101CalculoDepuradoIndicadores != null) ? a101CalculoDepuradoIndicadores.getGastoDepurado() : saldoEnrique;

            float promDiarioBase = (diasMes > 0) ? (float) presupuestoTotal / diasMes : 0;
            float presupuestoTranscurrido = promDiarioBase * diasTranscurridosReales;
            float presupuestoPorTranscurrir = promDiarioBase * diasRestantes;

            float depuradoDiario = (diasTranscurridosReales > 0) ? (float) vrSaldoDepurado / diasTranscurridosReales : 0;
            float depuradoPorTranscurrir = depuradoDiario * diasRestantes;
            float depuradoMesProyectado = vrSaldoDepurado + depuradoPorTranscurrir;

            float contableDiario = (diasTranscurridosReales > 0) ? (float) vrSaldoContable / diasTranscurridosReales : 0;
            float contablePorTranscurrir = contableDiario * diasRestantes;
            float contableMesProyectado = vrSaldoContable + contablePorTranscurrir;

            actualizarGraficaComparacion(presupuestoTranscurrido, presupuestoPorTranscurrir,
                    vrSaldoDepurado, depuradoPorTranscurrir, depuradoMesProyectado,
                    vrSaldoContable, contablePorTranscurrir, contableMesProyectado,
                    colorAzul, colorRojo, df);
            actualizarGraficaTendencia();

        } catch (Exception e) {
            Log.e("F5_3_Graficas", "Error al calcular/mostrar gráficas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reconstruye el detalle de gastos prorrateables del mes en curso, determina
     * diasTranscurridosReales (¿ya hay una transacción registrada con la fecha exacta de
     * hoy?) y calcula la serie diaria del Depurado acumulado — idéntico a la lógica de
     * F5_1_Indicadores.calcularGastosDepurados().
     */
    private void calcularGastosDepurados(int año, int mes) {
        a101CalculoDepuradoIndicadores = new A10_1_CalculoDepuradoIndicadores();

        ArrayList<A3_2_TipoTransaccionesGetsYSets> transacciones = null;
        if (cuentaSeguimiento_String != null) {
            A23_QueryResult<A3_2_TipoTransaccionesGetsYSets> resultado =
                    a22QueryManager.queryTransactionsByAccount(cuentaSeguimiento_String);
            transacciones = resultado.getDatos();
        }

        boolean hoyYaRegistrado = false;

        if (transacciones != null) {
            int fechaInicio = año * 10000 + mes * 100 + 1;
            int fechaFin = año * 10000 + mes * 100 + diasMes;
            int fechaHoyExacta = año * 10000 + mes * 100 + diaHoy;

            for (A3_2_TipoTransaccionesGetsYSets transaccion : transacciones) {
                int fecha = transaccion.tipoTget_8FechaInicialMetodoEnA5();

                if (fecha == fechaHoyExacta) {
                    hoyYaRegistrado = true;
                }

                if (fecha >= fechaInicio && fecha <= fechaFin) {
                    String documento = transaccion.tipoTget_1DocumentoMetodoEnA5();
                    int valor = transaccion.tipoTget_5ValorMetodoEnA5();
                    String descripcion = transaccion.tipoTget_6DescripcionMetodoEnA5();
                    String fechaStr = transaccion.tipoTget_7FechaYHoraMetodoEnA5();
                    int fechaInicial = transaccion.tipoTget_8FechaInicialMetodoEnA5();

                    A10_2_GastoProrrateableIndicadores gasto =
                            A10_2_GastoProrrateableIndicadores.parseDescripcion(
                                    descripcion, valor, fechaStr, documento, fechaInicial);

                    if (gasto != null) {
                        a101CalculoDepuradoIndicadores.agregarGasto(gasto);
                    }
                }
            }
        }

        diasTranscurridosReales = hoyYaRegistrado ? diaHoy : Math.max(1, diaHoy - 1);
        diasRestantes = diasMes - diasTranscurridosReales;

        a101CalculoDepuradoIndicadores.calcular(saldoEnrique, diasTranscurridosReales, diasMes);

        calcularSerieDepuradoDiaria(transacciones, año, mes);
    }

    /**
     * Serie diaria del Depurado acumulado (días 1..diasTranscurridosReales), para la curva
     * de tendencia. Reutiliza la misma lista de transacciones (todo el historial de
     * "CxC Enrique") ya consultada arriba — sin queries nuevas. Misma fórmula que
     * A10_1_CalculoDepuradoIndicadores.calcular(), aplicada día a día — idéntico a
     * F5_1_Indicadores.calcularSerieDepuradoDiaria().
     */
    private void calcularSerieDepuradoDiaria(ArrayList<A3_2_TipoTransaccionesGetsYSets> transacciones, int año, int mes) {
        serieDepuradoDiaria = new float[diasTranscurridosReales + 1]; // índice 0 no se usa

        if (transacciones == null || diasTranscurridosReales <= 0) return;

        int fechaInicioMes = año * 10000 + mes * 100 + 1;
        int fechaFinMes = año * 10000 + mes * 100 + diasMes;

        int baseAntesDelMes = 0;
        int[] incrementoPorDia = new int[diasMes + 1];

        for (A3_2_TipoTransaccionesGetsYSets transaccion : transacciones) {
            int fecha = transaccion.tipoTget_8FechaInicialMetodoEnA5();
            int valor = transaccion.tipoTget_5ValorMetodoEnA5();

            if (fecha < fechaInicioMes) {
                baseAntesDelMes += valor;
            } else if (fecha <= fechaFinMes) {
                int dia = fecha % 100;
                if (dia >= 1 && dia <= diasMes) {
                    incrementoPorDia[dia] += valor;
                }
            }
        }

        int totalProrrateables = a101CalculoDepuradoIndicadores.getTotalProrrateables();
        ArrayList<A10_2_GastoProrrateableIndicadores> detalle = a101CalculoDepuradoIndicadores.getDetalle();

        int contableAcumulado = baseAntesDelMes;
        for (int dia = 1; dia <= diasTranscurridosReales; dia++) {
            contableAcumulado += incrementoPorDia[dia];

            float consumoAcumuladoDia = 0;
            for (A10_2_GastoProrrateableIndicadores gasto : detalle) {
                consumoAcumuladoDia += gasto.getConsumoAcumulado(dia);
            }

            serieDepuradoDiaria[dia] = contableAcumulado - totalProrrateables + consumoAcumuladoDia;
        }
    }

    /**
     * Gráfica A: barras apiladas Presupuesto / Depurado / Contable del "Mes proyectado".
     * Cada barra se divide en 2 tonos de su misma familia de color: el tono sólido es
     * "Transcurrido" (ya es un hecho) y el tono claro es "Por transcurrir" (proyección).
     * Debajo de la gráfica se muestra el delta vs presupuesto de Depurado y Contable
     * (el de Presupuesto contra sí mismo siempre es 0, por eso no se muestra).
     * Cada barra lleva además un 3er tramo invisible (transparente) que sirve solo de
     * "ancla" para la etiqueta del total "Mes Proyectado": con setDrawValueAboveBar(false),
     * MPAndroidChart dibuja la etiqueta de CADA tramo apilado ligeramente por debajo del
     * borde superior de ese mismo tramo — así "Transcurrido" y "Por transcurrir" caen
     * dentro de su propio color, y el tramo-ancla (el más alto) recibe la etiqueta del
     * total justo encima de la parte visible de la columna. El tramo-ancla debe tener
     * una altura mínima (no ínfima) para que su etiqueta no se solape con la de "Por
     * transcurrir" justo debajo.
     * Idéntico a F5_1_Indicadores.actualizarGraficaComparacion().
     */
    private void actualizarGraficaComparacion(float presupuestoTranscurrido, float presupuestoPorTranscurrir,
                                               int vrSaldoDepurado, float depuradoPorTranscurrir, float depuradoMesProyectado,
                                               int vrSaldoContable, float contablePorTranscurrir, float contableMesProyectado,
                                               int colorAzul, int colorRojo, DecimalFormat df) {
        if (graficaComparacion_XBc == null) return;

        int verdeOscuro = Color.parseColor("#0CA30C");
        int verdeClaro  = Color.parseColor("#92D692");
        int ambarOscuro = Color.parseColor("#FAB219");
        int ambarClaro  = Color.parseColor("#FDDC98");
        int rojoOscuro  = Color.parseColor("#D03B3B");
        int rojoClaro   = Color.parseColor("#EAA7A7");
        int colorEtiqueta = Color.parseColor("#212121");

        float presupuestoPorTranscurrirPos = Math.max(presupuestoPorTranscurrir, 0);
        float depuradoPorTranscurrirPos = Math.max(depuradoPorTranscurrir, 0);
        float contablePorTranscurrirPos = Math.max(contablePorTranscurrir, 0);

        float totalPresupuesto = presupuestoTranscurrido + presupuestoPorTranscurrirPos;
        float totalDepurado = vrSaldoDepurado + depuradoPorTranscurrirPos;
        float totalContable = vrSaldoContable + contablePorTranscurrirPos;

        // Tramo-ancla invisible: mismo alto (en valor) para las 3 barras, ~18% de la
        // barra más alta de las 3 — suficiente para que la etiqueta del total no se
        // solape con la de "Por transcurrir" justo debajo, sin depender del tamaño de
        // cada barra individual.
        float mayorTotal = Math.max(totalPresupuesto, Math.max(totalDepurado, totalContable));
        float alturaTramoAncla = Math.max(mayorTotal * 0.18f, 1f);

        int totalPresupuestoRedondeado = Math.round(totalPresupuesto);
        int totalDepuradoRedondeado = Math.round(depuradoMesProyectado);
        int totalContableRedondeado = Math.round(contableMesProyectado);

        BarEntry entryPresupuesto = new BarEntry(0f, new float[]{presupuestoTranscurrido, presupuestoPorTranscurrirPos, alturaTramoAncla});
        BarEntry entryDepurado = new BarEntry(1f, new float[]{vrSaldoDepurado, depuradoPorTranscurrirPos, alturaTramoAncla});
        BarEntry entryContable = new BarEntry(2f, new float[]{vrSaldoContable, contablePorTranscurrirPos, alturaTramoAncla});

        BarDataSet dsPresupuesto = new BarDataSet(new ArrayList<>(Collections.singletonList(entryPresupuesto)), "Presupuesto");
        dsPresupuesto.setColors(verdeOscuro, verdeClaro, Color.TRANSPARENT);

        BarDataSet dsDepurado = new BarDataSet(new ArrayList<>(Collections.singletonList(entryDepurado)), "Depurado");
        dsDepurado.setColors(ambarOscuro, ambarClaro, Color.TRANSPARENT);

        BarDataSet dsContable = new BarDataSet(new ArrayList<>(Collections.singletonList(entryContable)), "Contable");
        dsContable.setColors(rojoOscuro, rojoClaro, Color.TRANSPARENT);

        // Cada barra usa su propio formateador: redondea los 2 tramos reales normalmente,
        // y cuando el valor es el tramo-ancla muestra el total "Mes Proyectado".
        dsPresupuesto.setValueFormatter(crearFormateadorConTotal(alturaTramoAncla, totalPresupuestoRedondeado));
        dsDepurado.setValueFormatter(crearFormateadorConTotal(alturaTramoAncla, totalDepuradoRedondeado));
        dsContable.setValueFormatter(crearFormateadorConTotal(alturaTramoAncla, totalContableRedondeado));

        for (BarDataSet ds : new BarDataSet[]{dsPresupuesto, dsDepurado, dsContable}) {
            ds.setDrawValues(true);
            ds.setValueTextColor(colorEtiqueta);
            ds.setValueTextSize(10f);
        }

        BarData barData = new BarData(dsPresupuesto, dsDepurado, dsContable);
        barData.setBarWidth(0.55f);

        graficaComparacion_XBc.setData(barData);
        graficaComparacion_XBc.setFitBars(true);
        // Con esto en false, la etiqueta de CADA tramo (incluido el superior) se dibuja
        // debajo de su propio borde, es decir dentro de su color — ya no "flota" sobre
        // toda la barra. Es lo que hace que el tramo-ancla funcione como se explica arriba.
        graficaComparacion_XBc.setDrawValueAboveBar(false);
        graficaComparacion_XBc.getDescription().setEnabled(false);
        graficaComparacion_XBc.getLegend().setEnabled(false);
        graficaComparacion_XBc.setExtraTopOffset(14f);
        graficaComparacion_XBc.setExtraBottomOffset(6f);
        graficaComparacion_XBc.setDoubleTapToZoomEnabled(false);
        graficaComparacion_XBc.setPinchZoom(false);
        graficaComparacion_XBc.setScaleEnabled(false);
        graficaComparacion_XBc.setDragEnabled(false);
        graficaComparacion_XBc.setHighlightPerTapEnabled(false);

        XAxis xAxis = graficaComparacion_XBc.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setValueFormatter(new IndexAxisValueFormatter(new String[]{"Presupuesto", "Depurado", "Contable"}));
        xAxis.setTextColor(Color.parseColor("#424242"));
        xAxis.setTextSize(11f);

        YAxis ejeIzquierdo = graficaComparacion_XBc.getAxisLeft();
        ejeIzquierdo.setAxisMinimum(0f);
        ejeIzquierdo.setDrawGridLines(true);
        ejeIzquierdo.setGridColor(Color.parseColor("#E1E0D9"));
        ejeIzquierdo.setTextColor(Color.parseColor("#5D5A52"));
        graficaComparacion_XBc.getAxisRight().setEnabled(false);

        graficaComparacion_XBc.animateY(500);
        graficaComparacion_XBc.invalidate();

        int deltaDepurado = Math.round(depuradoMesProyectado - presupuestoTotal);
        int deltaContable = Math.round(contableMesProyectado - presupuestoTotal);
        float pctDepurado = (presupuestoTotal != 0) ? (deltaDepurado * 100f / presupuestoTotal) : 0;
        float pctContable = (presupuestoTotal != 0) ? (deltaContable * 100f / presupuestoTotal) : 0;

        if (deltaGraficaDepurado_XTv != null) {
            String signo = deltaDepurado >= 0 ? "+" : "";
            String signoPct = pctDepurado >= 0 ? "+" : "";
            deltaGraficaDepurado_XTv.setText("Depurado " + signo + deltaDepurado + " (" + signoPct + df.format(pctDepurado) + "%)");
            deltaGraficaDepurado_XTv.setTextColor(deltaDepurado > 0 ? colorRojo : colorAzul);
        }
        if (deltaGraficaContable_XTv != null) {
            String signo = deltaContable >= 0 ? "+" : "";
            String signoPct = pctContable >= 0 ? "+" : "";
            deltaGraficaContable_XTv.setText("Contable " + signo + deltaContable + " (" + signoPct + df.format(pctContable) + "%)");
            deltaGraficaContable_XTv.setTextColor(deltaContable > 0 ? colorRojo : colorAzul);
        }
    }

    /**
     * Formateador de valores para una barra apilada de 3 tramos [Transcurrido, Por
     * transcurrir, tramo-ancla]: redondea los 2 tramos reales de siempre, y cuando el
     * valor que MPAndroidChart le pide formatear es el del tramo-ancla (el más alto,
     * por eso su etiqueta se dibuja encima de toda la columna) devuelve el total "Mes
     * Proyectado" en su lugar.
     */
    private ValueFormatter crearFormateadorConTotal(float valorTramoAncla, int totalRedondeado) {
        return new ValueFormatter() {
            @Override
            public String getBarStackedLabel(float value, BarEntry stackedEntry) {
                if (value == valorTramoAncla) {
                    return String.valueOf(totalRedondeado);
                }
                return String.valueOf(Math.round(value));
            }
        };
    }

    /**
     * Gráfica B: curva de tendencia del Depurado acumulado del mes en curso (día a día,
     * solo CxC Enrique), más una línea de referencia del ritmo ideal de presupuesto.
     * Idéntico a F5_1_Indicadores.actualizarGraficaTendencia().
     */
    private void actualizarGraficaTendencia() {
        if (graficaTendencia_XLc == null) return;

        if (serieDepuradoDiaria == null || diasTranscurridosReales <= 0) {
            graficaTendencia_XLc.clear();
            graficaTendencia_XLc.invalidate();
            return;
        }

        ArrayList<Entry> entradasDepurado = new ArrayList<>();
        ArrayList<Entry> entradasPresupuesto = new ArrayList<>();
        float promDiarioBase = (diasMes > 0) ? (float) presupuestoTotal / diasMes : 0;

        for (int dia = 1; dia <= diasTranscurridosReales; dia++) {
            entradasDepurado.add(new Entry(dia, serieDepuradoDiaria[dia]));
            entradasPresupuesto.add(new Entry(dia, promDiarioBase * dia));
        }

        LineDataSet dsDepurado = new LineDataSet(entradasDepurado, "Depurado real");
        dsDepurado.setColor(Color.parseColor("#FAB219"));
        dsDepurado.setLineWidth(2f);
        dsDepurado.setDrawCircles(true);
        dsDepurado.setCircleColor(Color.parseColor("#FAB219"));
        dsDepurado.setCircleRadius(3f);
        dsDepurado.setDrawCircleHole(false);
        dsDepurado.setDrawValues(false);
        dsDepurado.setMode(LineDataSet.Mode.LINEAR);

        LineDataSet dsPresupuesto = new LineDataSet(entradasPresupuesto, "Presupuesto (ritmo ideal)");
        dsPresupuesto.setColor(Color.parseColor("#5D5A52"));
        dsPresupuesto.setLineWidth(1.5f);
        dsPresupuesto.enableDashedLine(8f, 4f, 0f);
        dsPresupuesto.setDrawCircles(false);
        dsPresupuesto.setDrawValues(false);
        dsPresupuesto.setMode(LineDataSet.Mode.LINEAR);

        LineData lineData = new LineData(dsDepurado, dsPresupuesto);
        graficaTendencia_XLc.setData(lineData);
        graficaTendencia_XLc.getDescription().setEnabled(false);
        graficaTendencia_XLc.getLegend().setEnabled(false);
        graficaTendencia_XLc.setDoubleTapToZoomEnabled(false);
        graficaTendencia_XLc.setPinchZoom(false);
        graficaTendencia_XLc.setScaleEnabled(false);
        graficaTendencia_XLc.setDragEnabled(false);

        XAxis xAxis = graficaTendencia_XLc.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setGranularity(1f);
        xAxis.setGranularityEnabled(true);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(Color.parseColor("#424242"));
        xAxis.setTextSize(10f);
        xAxis.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf(Math.round(value));
            }
        });

        YAxis ejeIzquierdo = graficaTendencia_XLc.getAxisLeft();
        ejeIzquierdo.setDrawGridLines(true);
        ejeIzquierdo.setGridColor(Color.parseColor("#E1E0D9"));
        ejeIzquierdo.setTextColor(Color.parseColor("#5D5A52"));
        graficaTendencia_XLc.getAxisRight().setEnabled(false);

        graficaTendencia_XLc.animateX(500);
        graficaTendencia_XLc.invalidate();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }

    /**
     * Botón 🔙: cierra este diálogo de gráficas y abre F5_1_Indicadores (panel de cifras)
     * como diálogo flotante — igual patrón que el resto de accesos desde aquí (calculadora,
     * etc.), sin tocar el fragmento del contenedor principal. Funciona igual sin importar
     * desde dónde se abrieron las gráficas (F5_1 o el atajo en F3_2_VerItemTransaccion).
     */
    private void irAPanelDeCifras() {
        FragmentManager fm = getFragmentManager();
        if (fm == null) return;
        dismiss();
        new F5_1_Indicadores().show(fm, "panel_cifras_indicadores");
    }
}
