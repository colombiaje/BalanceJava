package B1_F1Support.inflateunits;

import android.content.Context;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;

import com.jj.appbalancev31.R;

import A1BASES.A5_CacheManager;
import B_FRAGMENTS.F1_CrudDocumento;

public class CrudOptionsUnit {

    private final F1_CrudDocumento f1;

    public CrudOptionsUnit(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        loadInitialData();
        setupListeners();
    }

    private void inflateViews(View view) {
        f1.optionsDoc_XRg    = view.findViewById(R.id.optionsDoc_XRg);
        f1.create_XRb        = view.findViewById(R.id.create_XRb);
        f1.template_XRb      = view.findViewById(R.id.template_XRb);
        f1.updateDelete_XRb  = view.findViewById(R.id.updateDelete_XRb);

        // Tags para identificación en lógica de caché/backup
        f1.create_XRb.setTag("create_XRb");
        f1.template_XRb.setTag("template_XRb");
        f1.updateDelete_XRb.setTag("updateDelete_XRb");

        f1.crudOptionsArea_XGl               = view.findViewById(R.id.crudOptionsArea_XGl);
        f1.seeButtons_XB                     = view.findViewById(R.id.seeButtons_XB);
        f1.salir_XBt                         = view.findViewById(R.id.salir_XBt);
        f1.cleanPasteQueryAccount_XChB       = view.findViewById(R.id.cleanPasteQueryAccount_XChB);
        f1.consultaPorCuentaYFechaEnOtroFragment_XSp = view.findViewById(R.id.consultaPorCuentaYFechaEnOtroFragment_XSp);
        f1.buscarCuentaParaTransaccionest_XAT = view.findViewById(R.id.buscarCuentaParaTransaccionest_XAT);

        // Canal D — botón verde (slot 3 ↔ slot 4)
        f1.botonVerde_XBt = view.findViewById(R.id.botonVerde_CanalD_XBt);
        f1.botonVerde_XBt.setVisibility(View.GONE);
    }

    private void loadInitialData() {
        f1.dynamicQueryNameAzAllAccount(f1.consultaPorCuentaYFechaEnOtroFragment_XSp);
        f1.dynamicQueryNameAzAllAccount(f1.buscarCuentaParaTransaccionest_XAT);

        // Restaurar estado del botón verde Canal D desde SharedPreferences
        f1.snackbarVerdeYaMostrado = f1.getActivity()
                .getSharedPreferences("prefs", Context.MODE_PRIVATE)
                .getBoolean("snackbarVerdeYaMostrado", false);

        f1.actualizarVisibilidadBotonVerde();
    }

    private void setupListeners() {
        setupRadioGroup();
        setupExitButton();
        setupCanalDButton();
        setupAccountSearchSpinner();
        setupAccountSearchAutocomplete();
        setupCopyPasteAccountButton();
    }

    // RadioGroup — navegación central entre crear / plantilla / editar
    private void setupRadioGroup() {
        f1.optionsDoc_XRg.setOnCheckedChangeListener((group, checkedId) -> {
            if (f1.estaRestaurando) return;

            if (f1.listaDocumento_ArrayLTT != null) {
                f1.listaDocumento_ArrayLTT.clear();
            }
            f1.limpiarListaYAdaptador();
            f1.currentRadioButtonId = checkedId;

            if (A5_CacheManager.tieneTrabajoPendienteReal(f1.getContext(), checkedId)) {
                f1.mostrarDialogoRestauracionUnificado(checkedId, false);
            } else {
                f1.ejecutarLimpiezaDeInterfaz();
            }
        });
    }

    // Botón salir — dispara flujo completo de salida de la app
    private void setupExitButton() {
        f1.salir_XBt.setOnClickListener(v -> f1.procesarSalidaApp());
    }

    // Botón verde Canal D — intercambia slot 3 y slot 4
    private void setupCanalDButton() {
        f1.botonVerde_XBt.setOnClickListener(v -> f1.intercambiarSlot3YSlot4CanalD());
    }

    // Spinner: seleccionar cuenta navega al visor de transacciones (F3_2)
    private void setupAccountSearchSpinner() {
        f1.consultaPorCuentaYFechaEnOtroFragment_XSp.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int i, long l) {
                        String selected = f1.consultaPorCuentaYFechaEnOtroFragment_XSp
                                .getSelectedItem().toString();
                        if (!selected.isEmpty()) {
                            f1.verTransaccionesPorCuentaConSpinner();
                            f1.consultaPorCuentaYFechaEnOtroFragment_XSp.setSelection(0);
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
    }

    // Autocomplete: escribir cierra el visor al borrar, seleccionar navega
    private void setupAccountSearchAutocomplete() {
        f1.buscarCuentaParaTransaccionest_XAT.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (before > 0) f1.cerrarVerItemTransaccion();
            }

            @Override public void afterTextChanged(Editable e) {}
        });

        f1.buscarCuentaParaTransaccionest_XAT.setOnItemClickListener(
                (parent, view, i, l) -> f1.verTransaccionesPorCuentaConAutocompeteText());
    }

    // Botón copiar/pegar para el campo de búsqueda de cuenta
    private void setupCopyPasteAccountButton() {
        f1.cleanPasteQueryAccount_XChB.setOnClickListener(v -> {
            String currentText = f1.buscarCuentaParaTransaccionest_XAT.getText().toString();
            if (f1.isCopyMode) {
                if (!TextUtils.isEmpty(currentText)) {
                    f1.copiedText = currentText;
                    f1.buscarCuentaParaTransaccionest_XAT.setText("");
                    f1.cleanPasteQueryAccount_XChB.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_clean_paste, 0, 0, 0);
                    f1.cerrarVerItemTransaccion();
                    f1.isCopyMode = false;
                }
            } else {
                if (!TextUtils.isEmpty(f1.copiedText)) {
                    f1.buscarCuentaParaTransaccionest_XAT.setText(f1.copiedText);
                    f1.cleanPasteQueryAccount_XChB.setCompoundDrawablesWithIntrinsicBounds(
                            R.drawable.ic_clean_paste, 0, 0, 0);
                    f1.isCopyMode = true;
                }
            }
        });
    }
}