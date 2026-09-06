package B1_F1Support.inflateunits;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.jj.appbalancev31.R;

import java.util.ArrayList;

import B_FRAGMENTS.F1_CrudDocumento;
import D_ADAPTERS.D_F1_AdaptadorCrudDocumento;

public class RecordDocumentUnit {

    private final F1_CrudDocumento f1;

    public RecordDocumentUnit(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        loadInitialData();
        setupListeners();
    }

    private void inflateViews(View view) {
        // Sub-área conciliación
        f1.areaConciliacion_XGl            = view.findViewById(R.id.areaConciliacion_XGl);
        f1.cuentaConciliacion_XSp          = view.findViewById(R.id.cuentaConciliacion_XSp);
        f1.saldoCuentaAconciliar_XTv       = view.findViewById(R.id.saldoCuentaAconciliar_XTv);
        f1.inputPhysicalVsAccounting_XEt   = view.findViewById(R.id.inputPhysicalVsAccounting_XEt);
        f1.diferenciaAConciliar_XTv        = view.findViewById(R.id.diferenciaAConciliar_XTv);

        // Sub-área registro
        f1.areaConciliacionYRegistro_XGl   = view.findViewById(R.id.areaConciliacionYRegistro_XGl);
        f1.valor_XEt                       = view.findViewById(R.id.valor_XEt);
        f1.signo_XSp                       = view.findViewById(R.id.signo_XSp);
        f1.descripcion_XAtv                = view.findViewById(R.id.descripcion_XAtv);
        f1.cuenta_XSp                      = view.findViewById(R.id.cuenta_XSp);
        f1.cuenta_XAtv                     = view.findViewById(R.id.cuenta_XAtv);
        f1.cuenta_XGL                      = view.findViewById(R.id.cuenta_XGL);
        f1.consecutivoItemRegistro_XTv     = view.findViewById(R.id.consecutivoItemRegistro_XTv);
        f1.seccion1HaceRegistrosItemsLista_XGl = view.findViewById(R.id.seccion1HaceRegistrosItemsLista_XGl);
        f1.seeAccountsXChB                 = view.findViewById(R.id.seeAccountsXChB);
        f1.limpiarCamposRegistro_XBt       = view.findViewById(R.id.limpiarCamposRegistro_XBt);

        // Botones pegar
        f1.pegarValor_XChB               = view.findViewById(R.id.pegarValor_XChB);
        f1.pegarValorAnterior_XChB       = view.findViewById(R.id.pegarValorAnterior_XChB);
        f1.pegarDescripcion_XChB         = view.findViewById(R.id.pegarDescripcion_XChB);
        f1.pegarDescripcionAnterior_XChB = view.findViewById(R.id.pegarDescripcionAnterior_XChB);
        f1.pegarCuentaSpinner_XChB       = view.findViewById(R.id.pegarCuentaSpinner_XChB);
        f1.pegarCuetaAnterior_XChB       = view.findViewById(R.id.pegarCuetaAnterior_XChB);
    }

    private void loadInitialData() {
        f1.configurarValidadorProrrateable();
        f1.dynamicQueryByDescriptionWithoutRepetition(f1.descripcion_XAtv);
        f1.dynamicQueryNameAzAllAccount(f1.cuenta_XAtv);
        f1.dynamicQueryNameAzAllAccount(f1.cuenta_XSp);

        // Construir spinner de signos: "", "+", "-"
        f1.signosAlRegistrar_ArrayString = new String[]{"", "+", "-"};
        f1.signos_ListString = new ArrayList<>();
        f1.signos_ListString.add("");
        f1.signos_ListString.add("+");
        f1.signos_ListString.add("-");

        ArrayAdapter<String> signosAdapter = new ArrayAdapter<>(
                f1.getActivity(),
                android.R.layout.simple_list_item_multiple_choice,
                f1.signosAlRegistrar_ArrayString);
        signosAdapter.notifyDataSetChanged();
        f1.signo_XSp.setAdapter(signosAdapter);
    }

    private void setupListeners() {
        setupConciliacionSpinner();
        setupPhysicalInputField();
        setupValorField();
        setupDescripcionField();
        setupCuentaAtvField();
        setupCuentaSpinner();
        setupSignoSpinner();
        setupPasteButtons();
        setupSeeAccountsCheckbox();
        setupClearRecordButton();
    }

    // Spinner de cuenta conciliable — muestra/oculta campos de saldo
    private void setupConciliacionSpinner() {
        f1.cuentaConciliacion_XSp.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int i, long l) {
                        String selected = f1.cuentaConciliacion_XSp.getSelectedItem().toString();

                        if (selected.isEmpty()) {
                            f1.cuentaConciliacion_XSp.setBackgroundColor(Color.parseColor("#F4D7F5"));
                            return;
                        }

                        f1.dynamicQuerySumByAccountAcordingToArgument();
                        f1.dynamicQueryByAllAccountAz();
                        f1.cuentaConciliacion_XSp.setBackgroundColor(Color.parseColor("#1de9b6"));
                        f1.cuentaConciliacion_XSp.setVisibility(View.VISIBLE);

                        if (selected.equals("1 No conciliar")) {
                            f1.saldoCuentaAconciliar_XTv.setVisibility(View.GONE);
                            f1.inputPhysicalVsAccounting_XEt.setVisibility(View.GONE);
                            f1.diferenciaAConciliar_XTv.setVisibility(View.GONE);
                            f1.inputPhysicalVsAccounting_XEt.setText("");
                        } else {
                            f1.saldoCuentaAconciliar_XTv.setText("saldo: \n" + f1.$sAc);
                            f1.saldoCuentaAconciliar_XTv.setVisibility(View.VISIBLE);
                            f1.inputPhysicalVsAccounting_XEt.setVisibility(View.VISIBLE);
                            f1.diferenciaAConciliar_XTv.setVisibility(View.VISIBLE);
                            f1.$sAc = 0;
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });
    }

    // Campo físico vs contable — dispara cálculo de conciliación
    private void setupPhysicalInputField() {
        f1.inputPhysicalVsAccounting_XEt.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                f1.ocultarTeclado();
                return true;
            }
            return false;
        });

        f1.inputPhysicalVsAccounting_XEt.setOnFocusChangeListener(
                (v, hasFocus) -> { if (!hasFocus) f1.ocultarTeclado(); });

        f1.inputPhysicalVsAccounting_XEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0 && count < 3) {
                    if (f1.cuentaConciliacion_XSp.getSelectedItem().toString().isEmpty()) {
                        Toast.makeText(f1.getActivity(),
                                "Antes de comenzar debe elegir una cuenta de conciliacion",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    f1.digitarFisicoVsSaldoConciliacion();
                    f1.inputPhysicalVsAccounting_XEt
                            .setBackgroundColor(Color.parseColor("#1de9b6"));
                }
                if (count == 0) {
                    f1.digitarFisicoVsSaldoConciliacion();
                    f1.inputPhysicalVsAccounting_XEt
                            .setBackgroundColor(Color.parseColor("#F4D7F5"));
                }
            }

            @Override public void afterTextChanged(Editable e) {}
        });

        f1.inputPhysicalVsAccounting_XEt.setOnLongClickListener(v -> {
            f1.mostrarCalculadora();
            return false;
        });

        f1.inputPhysicalVsAccounting_XEt.setOnClickListener(v ->
                f1.inputPhysicalVsAccounting_XEt.setText(
                        f1.valorRecibidoResultadoCalculadora_XTv.getText().toString()));
    }

    // Campo valor — actualiza sumas y conciliación en cada pulsación
    private void setupValorField() {
        f1.valor_XEt.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_ENTER) {
                f1.ocultarTeclado();
                return true;
            }
            return false;
        });

        f1.valor_XEt.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                f1.ocultarTeclado();
                f1.primerRegistroAListaDocumentoCuentaConciliable();
            }
        });

        f1.valor_XEt.setOnLongClickListener(v -> {
            f1.mostrarCalculadora();
            f1.valor_XEt.setText("?");
            return true;
        });

        f1.valor_XEt.setOnClickListener(v ->
                f1.valor_XEt.setText(
                        f1.valorRecibidoResultadoCalculadora_XTv.getText().toString()));

        f1.valor_XEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                try {
                    if (count > 0) {
                        f1.actualizarSumasListado();
                        f1.digitarFisicoVsSaldoConciliacion();
                        f1.valor_XEt.setBackgroundColor(Color.parseColor("#1de9b6"));
                        f1.primerRegistroAListaDocumentoCuentaConciliable();
                        f1.$quedaPorRegistrarConRegistroActual = 0;
                        f1.seeGridLAyoutAccount();
                    } else {
                        f1.valor_XEt.setBackgroundColor(Color.parseColor("#F4D7F5"));
                        f1.seeGridLAyoutAccount();
                    }
                } catch (Exception e) {
                    Log.e("RecordDocumentUnit", "Error in valor TextWatcher", e);
                }
            }

            @Override public void afterTextChanged(Editable e) {}
        });
    }

    // Campo descripción — actualiza color y guarda valor anterior para pegar
    private void setupDescripcionField() {
        f1.dynamicQueryByDescriptionWithoutRepetition(f1.descripcion_XAtv);

        f1.descripcion_XAtv.setOnItemClickListener(
                (parent, view, i, l) -> f1.ocultarTeclado());

        f1.descripcion_XAtv.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0) {
                    f1.descripcion_XAtv.setBackgroundColor(Color.parseColor("#1de9b6"));
                    f1.copiarValorAnterior_String = f1.valor_XEt.getText().toString();
                    f1.copiarSigno_String = f1.signo_XSp.getSelectedItem().toString();
                    f1.seeGridLAyoutAccount();
                } else {
                    f1.descripcion_XAtv.setBackgroundColor(Color.parseColor("#F4D7F5"));
                    f1.seeGridLAyoutAccount();
                }
            }

            @Override public void afterTextChanged(Editable e) {}
        });
    }

    // Campo cuenta autocomplete — sincroniza con spinner
    private void setupCuentaAtvField() {
        f1.cuenta_XAtv.setOnFocusChangeListener(
                (v, hasFocus) -> { if (!hasFocus) f1.ocultarTeclado(); });

        f1.cuenta_XAtv.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0) {
                    f1.cuenta_XAtv.setBackgroundColor(Color.parseColor("#1de9b6"));
                    f1.copiarDesripcionAnterior_String =
                            f1.descripcion_XAtv.getText().toString();
                } else {
                    f1.cuenta_XAtv.setBackgroundColor(Color.parseColor("#F4D7F5"));
                }
            }

            @Override public void afterTextChanged(Editable e) {}
        });

        f1.cuenta_XAtv.setOnItemClickListener((parent, view, i, l) -> {
            if (f1.valor_XEt.getText().toString().isEmpty()
                    || f1.signo_XSp.getSelectedItem().toString().isEmpty()
                    || f1.descripcion_XAtv.getText().toString().isEmpty()) {
                f1.cuenta_XSp.setSelection(0);
                Toast.makeText(f1.getActivity(), "Faltan datos", Toast.LENGTH_SHORT).show();
            } else {
                f1.cuenta_XSp.setSelection(
                        f1.accountAllAz_List.indexOf(
                                f1.cuenta_XAtv.getText().toString()));
            }
        });
    }

    // Spinner cuenta — agrega item al documento al seleccionar cuenta
    private void setupCuentaSpinner() {
        f1.cuenta_XSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int i, long l) {
                String selected = f1.cuenta_XSp.getSelectedItem().toString();

                if (f1.enModoModificacion) {
                    try {
                        f1.digitarFisicoVsSaldoConciliacion();
                        f1.copiarValorAnterior_String = f1.valor_XEt.getText().toString();
                        f1.copiarSigno_String = f1.signo_XSp.getSelectedItem().toString();
                        f1.copiarDesripcionAnterior_String =
                                f1.descripcion_XAtv.getText().toString();
                        f1.cuenta_XAtv.setVisibility(View.GONE);
                        f1.cuenta_XSp.setBackgroundColor(selected.isEmpty()
                                ? Color.parseColor("#F4D7F5")
                                : Color.parseColor("#1de9b6"));
                        Toast.makeText(f1.getActivity(),
                                "Modo modificacion", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e("RecordDocumentUnit", "Error in modification mode", e);
                    }
                } else {
                    f1.cuenta_XAtv.setVisibility(View.VISIBLE);
                    f1.cuenta_XSp.setBackgroundColor(selected.isEmpty()
                            ? Color.parseColor("#F4D7F5")
                            : Color.parseColor("#1de9b6"));

                    if (!selected.isEmpty()) {
                        f1.copiarValorAnterior_String = f1.valor_XEt.getText().toString();
                        f1.copiarSigno_String = f1.signo_XSp.getSelectedItem().toString();
                        f1.copiarDesripcionAnterior_String =
                                f1.descripcion_XAtv.getText().toString();
                        f1.copiarCuentaSpinnerAnterior_String = selected;
                        f1.losDemasRegistrosAListaDocumento();
                        f1.digitarFisicoVsSaldoConciliacion();
                        f1.ocultarTeclado();
                        f1.renumerarItemsListaDocumento();
                        f1.pasarItemListaTodoResumidoAItemListaRevision();
                    }
                }
                f1.digitarFisicoVsSaldoConciliacion();
                f1.sumarItemListaDocumento();
                f1.actualizarSumasListado();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // Spinner signo (+/-) — refresca adaptador al seleccionar
    private void setupSignoSpinner() {
        f1.signo_XSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String selected = f1.signo_XSp.getSelectedItem().toString();
                if (selected.isEmpty()) {
                    f1.signo_XSp.setBackgroundTintList(
                            ColorStateList.valueOf(Color.parseColor("#F4D7F5")));
                    f1.seeGridLAyoutAccount();
                    return;
                }
                f1.ocultarTeclado();
                f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                        new D_F1_AdaptadorCrudDocumento(
                                f1.getActivity(), f1.listaDocumento_ArrayLTT, null);
                f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.notifyDataSetChanged();
                f1.listaDocumento_XLv.setAdapter(
                        f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);
                f1.signo_XSp.setBackgroundTintList(
                        ColorStateList.valueOf(Color.parseColor("#1de9b6")));
                f1.seeGridLAyoutAccount();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // Botones copiar/pegar para valor, descripción y cuenta
    private void setupPasteButtons() {
        f1.pegarValor_XChB.setOnClickListener(v ->
                handleCopyPaste(f1.valor_XEt, f1.pegarValor_XChB));
        f1.pegarValorAnterior_XChB.setOnClickListener(v ->
                f1.valor_XEt.setText(f1.copiarValorAnterior_String));
        f1.pegarDescripcion_XChB.setOnClickListener(v ->
                handleCopyPaste(f1.descripcion_XAtv, f1.pegarDescripcion_XChB));
        f1.pegarDescripcionAnterior_XChB.setOnClickListener(v ->
                f1.descripcion_XAtv.setText(f1.copiarDesripcionAnterior_String));
        f1.pegarCuentaSpinner_XChB.setOnClickListener(v ->
                handleCopyPaste(f1.cuenta_XAtv, f1.pegarCuentaSpinner_XChB));
        f1.pegarCuetaAnterior_XChB.setOnClickListener(v ->
                f1.cuenta_XAtv.setText(f1.copiarCuentaSpinnerAnterior_String));
    }

    // Lógica compartida copiar/pegar usada por los 3 pares de botones
    private void handleCopyPaste(TextView field, android.widget.Button button) {
        if (f1.isCopyMode) {
            String currentText = field.getText().toString();
            if (!TextUtils.isEmpty(currentText)) {
                f1.copiedText = currentText;
                field.setText("");
                button.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_clean_paste, 0, 0, 0);
                f1.isCopyMode = false;
            }
        } else {
            if (!TextUtils.isEmpty(f1.copiedText)) {
                field.setText(f1.copiedText);
                button.setCompoundDrawablesWithIntrinsicBounds(
                        R.drawable.ic_clean_paste, 0, 0, 0);
                f1.isCopyMode = true;
            }
        }
    }

    // Checkbox ver cuentas
    private void setupSeeAccountsCheckbox() {
        f1.seeAccountsXChB.setOnClickListener(v ->
                f1.seeAccountsDialogFragment(f1.seeAccountsXChB.isChecked()));
    }

    // Botón limpiar campos de registro
    private void setupClearRecordButton() {
        f1.limpiarCamposRegistro_XBt.setOnClickListener(v -> {
            f1.clearViewValuesAreaRecords();
            f1.ocultarTeclado();
        });
    }
}