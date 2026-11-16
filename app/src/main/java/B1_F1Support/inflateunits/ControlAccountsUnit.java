package B1_F1Support.inflateunits;

import android.view.View;

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
    }

    private void setupListeners() {
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

}