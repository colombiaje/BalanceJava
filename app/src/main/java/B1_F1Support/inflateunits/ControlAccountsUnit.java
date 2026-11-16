package B1_F1Support.inflateunits;

import android.graphics.Color;
import android.view.View;
import android.widget.AdapterView;

import com.jj.appbalancev31.R;

import B_FRAGMENTS.F1_CrudDocumento;

public class ControlAccountsUnit {

    private final F1_CrudDocumento f1;

    public ControlAccountsUnit(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        setupListeners();
    }

    private void inflateViews(View view) {
        f1.sumaPositivos_XTv                    = view.findViewById(R.id.sumaPositivos_XTv);
        f1.sumaNegativos_XTv                    = view.findViewById(R.id.sumaNegativos_XTv);
        f1.controlACeroTotales_XTv              = view.findViewById(R.id.controlACeroTotales_XTv);
        f1.areaListaRevisarCuentas_XGl          = view.findViewById(R.id.areaListaRevisarCuentas_XGl);
        f1.listaCuentasDeRevision_XSp           = view.findViewById(R.id.listaCuentasDeRevision_XSp);
        f1.saldoInicialeListaCuentasRevisar_XTv = view.findViewById(R.id.saldoInicialeListaCuentasRevisar_XTv);
        f1.movimientoListaCuentasRevisar_XTv    = view.findViewById(R.id.movimientoListaCuentasRevisar_XTv);
        f1.saldoFinalListaCuentasRevisar_XTv    = view.findViewById(R.id.saldoFinalListaCuentasRevisar_XTv);
    }

    private void setupListeners() {
        setupReviewAccountSpinner();
        setupToqueDif();
    }

    // ⭐ NUEVO (16-nov, Jorge): tocar Dif. (en verde, con ítems y sin cuadrar) lleva el valor de
    // la diferencia al campo Valor, para cerrar el documento con el último registro sin
    // escribirlo. El signo lo elige el usuario al final, como siempre; se le indica cuál cuadra.
    private void setupToqueDif() {
        f1.controlACeroTotales_XTv.setOnClickListener(v -> {
            if (f1.listaDocumento_ArrayLTT == null || f1.listaDocumento_ArrayLTT.isEmpty()
                    || f1.$netoT == null || f1.$netoT == 0) {
                return;
            }
            int diferencia = f1.$netoT;
            f1.valor_XEt.setText(String.valueOf(Math.abs(diferencia)));
            f1.valor_XEt.setSelection(f1.valor_XEt.getText().length());
            android.widget.Toast.makeText(f1.getActivity(), "Valor " + Math.abs(diferencia) +
                    ": para cuadrar, al final elija el signo " + (diferencia > 0 ? "−" : "+"),
                    android.widget.Toast.LENGTH_LONG).show();
        });
    }

    // Spinner de revisión de cuentas — muestra saldo inicial, movimientos y saldo final.
    // Si hay un documento cargado para edición, resta sus movimientos originales
    // del saldo inicial para evitar doble conteo.
    private void setupReviewAccountSpinner() {
        f1.listaCuentasDeRevision_XSp.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int i, long l) {
                        // Guard: ignorar eventos automáticos durante restauración de estado
                        if (f1.estaRestaurando) return;

                        Object item = f1.listaCuentasDeRevision_XSp.getSelectedItem();
                        if (item == null) return;

                        String selected = item.toString();
                        if (selected.isEmpty() || selected.equals("1 No conciliar")) {
                            if (selected.isEmpty()) {
                                f1.listaCuentasDeRevision_XSp
                                        .setBackgroundColor(Color.parseColor("#1de9b6"));
                            }
                            return;
                        }

                        f1.dynamicQuerySumByAccountAcordingToArgument();
                        f1.dynamicQueryByAllAccountAz();
                        f1.listaCuentasDeRevision_XSp.setBackgroundColor(Color.parseColor("#1de9b6"));
                        f1.listaCuentasDeRevision_XSp.setVisibility(View.VISIBLE);

                        // Ajuste de saldo inicial en modo edición:
                        // resta los movimientos originales para no contarlos dos veces
                        f1.$sAc = Integer.parseInt(String.valueOf(f1.sumaTransaccionesCuenta));
                        Integer adjustedInitialBalance = f1.$sAc;
                        f1.asegurarAjusteDeSaldosEnEdicion(); // ⭐ (10-nov) mismo criterio en las 3 formas de editar

                        if (f1.documentoCargadoParaEdicion
                                && f1.movimientosOriginalesDocumento_HashMap.containsKey(selected)) {
                            adjustedInitialBalance =
                                    f1.$sAc - f1.movimientosOriginalesDocumento_HashMap.get(selected);
                        }

                        f1.saldoInicialeListaCuentasRevisar_XTv.setText("" + adjustedInitialBalance);
                        f1.sumarItemListaSaldosCuenta();

                        Integer finalBalance =
                                adjustedInitialBalance + f1.sumaMovimientoItemListaCuentaRevision_Integer;
                        f1.saldoFinalListaCuentasRevisar_XTv.setText("" + finalBalance);
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
    }
}