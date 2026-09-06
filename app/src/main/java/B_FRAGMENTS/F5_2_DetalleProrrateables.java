package B_FRAGMENTS;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.jj.appbalancev31.R;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

import A1BASES.A10_1_CalculoDepuradoIndicadores;
import A1BASES.A10_2_GastoProrrateableIndicadores;

public class F5_2_DetalleProrrateables extends DialogFragment {

    private A10_1_CalculoDepuradoIndicadores a101CalculoDepuradoIndicadores;
    private int diaActual;



    public static F5_2_DetalleProrrateables newInstance(A10_1_CalculoDepuradoIndicadores calculo, int diaActual) {
        F5_2_DetalleProrrateables fragment = new F5_2_DetalleProrrateables();
        fragment.a101CalculoDepuradoIndicadores = calculo;
        fragment.diaActual = diaActual;
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, android.R.style.Theme_Material_Light_Dialog);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = new Dialog(getActivity(), android.R.style.Theme_Translucent_NoTitleBar);
        View view = getActivity().getLayoutInflater().inflate(R.layout.f5_2_detalle_prorrateables, null);

        ColorDrawable d = new ColorDrawable(Color.BLACK);
        d.setAlpha(200);

        dialog.getWindow().setBackgroundDrawable(d);
        dialog.getWindow().setContentView(view);

        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;

        dialog.setCanceledOnTouchOutside(true);

        return dialog;
    }

    private F6_Calculadora calculadora_Fragment;
    Button calculadoraLibre_XBt;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.f5_2_detalle_prorrateables, container, false);

        calculadora_Fragment = new F6_Calculadora();
        calculadoraLibre_XBt = view.findViewById(R.id.calculadoraLibre_XBt);

        ImageButton btnCerrar = view.findViewById(R.id.btnCerrarDetalle_XBt);
        LinearLayout contenedorFilas = view.findViewById(R.id.contenedorFilasDetalle_XLl);
        TextView totalValorTotal = view.findViewById(R.id.totalValorTotal_XTv);
        TextView totalConsumoAcum = view.findViewById(R.id.totalConsumoAcum_XTv);

        btnCerrar.setOnClickListener(v -> dismiss());

        // Configurar formato decimal
        DecimalFormat df = new DecimalFormat("#,##0.0");
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator('.');
        df.setDecimalFormatSymbols(symbols);

        // Agregar filas dinámicamente
        for (A10_2_GastoProrrateableIndicadores gasto : a101CalculoDepuradoIndicadores.getDetalle()) {
            View filaView = crearFilaDetalle(gasto, df);
            contenedorFilas.addView(filaView);
        }

        // Totales
        totalValorTotal.setText(String.valueOf(a101CalculoDepuradoIndicadores.getTotalProrrateables()));
        totalConsumoAcum.setText(String.valueOf(Math.round(a101CalculoDepuradoIndicadores.getConsumoAcumulado())));

        if (calculadoraLibre_XBt != null) {
            calculadoraLibre_XBt.setOnClickListener(v -> mostrarCalculadoraLibre());
        }

        return view;
    }

    private View crearFilaDetalle(A10_2_GastoProrrateableIndicadores gasto, DecimalFormat df) {
        LinearLayout fila = new LinearLayout(getActivity());
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        fila.setPadding(4, 4, 4, 4);
        fila.setBackgroundColor(Color.parseColor("#E1BEE7"));

        // Día
        TextView tvDia = crearTextView(String.valueOf(gasto.getDiaRegistro()), 40, Color.BLUE);
        fila.addView(tvDia);

        // Doc
        TextView tvDoc = crearTextView(gasto.getDocumento(), 50, Color.BLUE);
        fila.addView(tvDoc);

        // Concepto (reducido)
        TextView tvConcepto = crearTextView(gasto.getConcepto(), 90, Color.BLUE);
        tvConcepto.setTextSize(11);
        fila.addView(tvConcepto);

        // Periodo
        TextView tvDias = crearTextView(String.valueOf(gasto.getDiasPeriodo()), 30, Color.BLUE);
        fila.addView(tvDias);

        // Días Efectivos (NUEVO)
        // Días Efectivos (NUEVO)
        TextView tvEfectivos = crearTextView(String.valueOf(gasto.getDiasEfectivos(diaActual)), 30, Color.parseColor("#4CAF50"));
        tvEfectivos.setTypeface(null, android.graphics.Typeface.BOLD);
        fila.addView(tvEfectivos);

        // Total
        TextView tvTotal = crearTextView(String.valueOf(gasto.getValorTotal()), 50, Color.BLUE);
        fila.addView(tvTotal);

        // /Día
        TextView tvDiario = crearTextView(df.format(gasto.getValorDiario()), 45, Color.BLUE);
        tvDiario.setTextSize(11);
        fila.addView(tvDiario);

        // Acumulado
        TextView tvAcum = crearTextView(
                String.valueOf(Math.round(gasto.getConsumoAcumulado(diaActual))),
                50, Color.parseColor("#FF9800"));
        tvEfectivos.setTypeface(null, android.graphics.Typeface.BOLD);
        fila.addView(tvAcum);

        return fila;
    }

    private TextView crearTextView(String texto, int ancho, int color) {
        TextView tv = new TextView(getActivity());
        tv.setText(texto);
        tv.setTextColor(color);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (ancho * getResources().getDisplayMetrics().density),
                LinearLayout.LayoutParams.WRAP_CONTENT));
        tv.setPadding(2, 2, 2, 2);
        return tv;
    }

    private void mostrarCalculadoraLibre() {
        if (getFragmentManager() == null) return;

        // Usar factory method sin callback
        F6_Calculadora calculadora = F6_Calculadora.newInstanceLibre();
        calculadora.show(getFragmentManager(), "calculadora_libre");
    }
}