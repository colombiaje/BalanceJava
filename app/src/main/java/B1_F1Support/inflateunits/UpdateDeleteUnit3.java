package B1_F1Support.inflateunits;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.jj.appbalancev31.R;

import B_FRAGMENTS.F1_CrudDocumento;

public class UpdateDeleteUnit3 {

    private final F1_CrudDocumento f1;

    // ── Debounce para búsqueda manual por teclado ──────────────────────────
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    public UpdateDeleteUnit3(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        loadInitialData();
        setupListeners();
    }

    // ── Inflate ────────────────────────────────────────────────────────────

    private void inflateViews(View view) {
        f1.envolventeEncabezadoItemListaRevisar_XLl = view.findViewById(R.id.envolventeEncabezadoItemListaRevisar_XLl);
        f1.embolventeItemsListaRevisar_XLl          = view.findViewById(R.id.embolventeItemsListaRevisar_XLl);
        f1.areaUpdateAndDelete_XGl                  = view.findViewById(R.id.areaUpdateAndDelete_XGl);
        f1.documentoABuscarParaEditar_XATv          = view.findViewById(R.id.documentoABuscarParaEditar_XATv);
        f1.dateInUpdate_XTv                         = view.findViewById(R.id.dateInUpdate_XTv);
        f1.limpiarDocumentoModificar_XChB           = view.findViewById(R.id.limpiarDocumentoModificar_XChB);
        f1.assignedOtherDateInUpdate_XChB           = view.findViewById(R.id.assignedOtherDateInUpdate_XChB);
        f1.numeroConsecutivoDocEnEdicion_XTv        = view.findViewById(R.id.numeroConsecutivoDocEnEdicion_XTv);
        f1.eliminaDocumento_XBt                     = view.findViewById(R.id.eliminaDocumento_XBt);

        // NOTE: changeOfDateInUpdate_XTv, EtiquetaFechaNueva_XTv y etiquetadateInUpdate_XTv
        // apuntan al mismo ID de layout — solo un inflate necesario, los otros son alias.
        f1.changeOfDateInUpdate_XTv = view.findViewById(R.id.changeOfDateInUpdate_XTv);
        f1.EtiquetaFechaNueva_XTv   = f1.changeOfDateInUpdate_XTv;  // alias
        f1.etiquetadateInUpdate_XTv = f1.changeOfDateInUpdate_XTv;  // alias
    }

    // ── Carga inicial ──────────────────────────────────────────────────────

    private void loadInitialData() {
        f1.dynamicQueryByDocumentinUpdate();
        f1.dynamicQuerySumByAccountAcordingToArgument();
        f1.dynamicQueryByAllAccountAz();
        f1.documentoABuscarParaEditar_XATv.setAdapter(f1.adapterConsecutivoDocAz);
    }

    // ── Listeners ──────────────────────────────────────────────────────────

    private void setupListeners() {
        setupDocumentSearchItemClick();
        setupDocumentSearchTextWatcher();
        setupCopyPasteButton();
        setupDeleteDocumentButton();
        setupChangeDateCheckbox();
        setupConsecutivoClick();
    }

    // ── Selección desde la lista desplegable del autocomplete ──────────────
    // El usuario eligió un ítem → documento confirmado como existente.
    // Se dispara la búsqueda completa y se pinta verde.

    private void setupDocumentSearchItemClick() {
        f1.documentoABuscarParaEditar_XATv.setOnItemClickListener(
                (adapterView, view, i, l) -> {

                    // Cancelar cualquier debounce pendiente por escritura manual
                    searchHandler.removeCallbacks(searchRunnable);

                    f1.docABuscar_String =
                            f1.documentoABuscarParaEditar_XATv.getText().toString();

                    // NOTE: llamado dos veces — comportamiento original preservado
                    f1.colocarDocConsultadoEnListaItemDoc();
                    f1.colocarDocConsultadoEnListaItemDoc();
                    f1.sumarItemListaDocumento();
                    f1.actualizarSumasListado();
                    f1.controlACeroTotales_XTv.setText("" + f1.$netoT);
                    f1.pasarItemListaTodoResumidoAItemListaRevision();
                    f1.ocultarTeclado();

                    // El ítem viene de la lista → existe en BD → verde directo
                    f1.documentoABuscarParaEditar_XATv
                            .setBackgroundColor(Color.parseColor("#90E0D8"));

                    f1.setvisibleUpdateAndDeleteDespuesDeRecibirBundle();
                    f1.areaButtons_XLL.setVisibility(View.VISIBLE);
                });
    }

    // ── Escritura manual en el campo de búsqueda ───────────────────────────
    // Mientras escribe → amarillo.
    // Después de 500 ms sin cambios → consulta BD y pinta verde o amarillo.

    private void setupDocumentSearchTextWatcher() {
        f1.documentoABuscarParaEditar_XATv.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int currentLength = s.length();

                // Cancelar búsqueda anterior pendiente
                searchHandler.removeCallbacks(searchRunnable);

                if (currentLength == 0) {
                    // Campo vacío → rosado y limpiar estado
                    f1.documentoABuscarParaEditar_XATv
                            .setBackgroundColor(Color.parseColor("#F4D7F5"));
                    f1.limpiarInformacionTemplateUpdateAlDisminuirCaracteres(0);
                    f1.docABuscar_String = "";
                    return;
                }

                // Mientras escribe → amarillo (pendiente de confirmar)
                f1.documentoABuscarParaEditar_XATv
                        .setBackgroundColor(Color.parseColor("#FFC107"));

                f1.docABuscar_String = s.toString();

                if (before > 0) {
                    f1.limpiarInformacionTemplateUpdateAlDisminuirCaracteres(currentLength);
                }

                if (count > 0) {
                    if (f1.documentoABuscarParaEditar_XATv.getText().toString().isEmpty()) {
                        Toast.makeText(f1.getActivity(),
                                "Falta el número del documento", Toast.LENGTH_LONG).show();
                        return;
                    }
                    f1.setvisibleUpdateAndDeleteDespuesDeRecibirBundle();
                    f1.areaButtons_XLL.setVisibility(View.VISIBLE);
                }

                // Debounce: espera 500 ms de inactividad antes de consultar la BD.
                // Funciona sin importar la cantidad de dígitos del documento.
                searchRunnable = () -> {
                    String docIngresado =
                            f1.documentoABuscarParaEditar_XATv.getText().toString().trim();

                    if (docIngresado.isEmpty()) return;

                    boolean documentFound = buscarDocumentoEnBD(docIngresado);

                    if (documentFound) {
                        // Verde — documento existe
                        f1.documentoABuscarParaEditar_XATv
                                .setBackgroundColor(Color.parseColor("#90E0D8"));
                        f1.setvisibleUpdateAndDeleteDespuesDeRecibirBundle();
                        f1.areaButtons_XLL.setVisibility(View.VISIBLE);
                    } else {
                        // Amarillo — no existe: limpiar pantalla para evitar confusión
                        f1.documentoABuscarParaEditar_XATv
                                .setBackgroundColor(Color.parseColor("#FFC107"));
                        f1.limpiarInformacionTemplateUpdateAlDisminuirCaracteres(docIngresado.length());
                    }
                };                searchHandler.postDelayed(searchRunnable, 500);
            }

            @Override
            public void afterTextChanged(Editable editable) {}
        });
    }

    // ── Consulta a la BD ───────────────────────────────────────────────────
    // Adapta el nombre de tabla y columna según tu esquema en A1_1_AyudanteBD.

    private boolean buscarDocumentoEnBD(String docIngresado) {
        try {
            android.database.sqlite.SQLiteDatabase db =
                    f1.ayudante_Class.getReadableDatabase();

            f1.llamadaCursorUnDocumento = db.rawQuery(
                    "SELECT * FROM tu_tabla WHERE numero_documento = ?",
                    new String[]{docIngresado});

            // getCount() es más confiable que moveToNext() para solo verificar existencia
            return f1.llamadaCursorUnDocumento != null
                    && f1.llamadaCursorUnDocumento.getCount() > 0;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Botón copiar / pegar número de documento ───────────────────────────

    private void setupCopyPasteButton() {
        f1.limpiarDocumentoModificar_XChB.setOnClickListener(v -> {
            if (f1.isCopyMode) {
                String currentText = f1.documentoABuscarParaEditar_XATv.getText().toString();
                if (!TextUtils.isEmpty(currentText)) {
                    f1.copiedText = currentText;
                    f1.documentoABuscarParaEditar_XATv.setText("");
                    f1.limpiarDocumentoModificar_XChB
                            .setCompoundDrawablesWithIntrinsicBounds(
                                    R.drawable.ic_clean_paste, 0, 0, 0);
                    f1.isCopyMode = false;
                }
            } else {
                if (!TextUtils.isEmpty(f1.copiedText)) {
                    f1.documentoABuscarParaEditar_XATv.setText(f1.copiedText);
                    f1.limpiarDocumentoModificar_XChB
                            .setCompoundDrawablesWithIntrinsicBounds(
                                    R.drawable.ic_clean_paste, 0, 0, 0);
                    f1.isCopyMode = true;
                }
            }
        });
    }

    // ── Botón eliminar documento — pide confirmación antes de borrar ────────

    private void setupDeleteDocumentButton() {
        f1.eliminaDocumento_XBt.setOnClickListener(v -> {
            String docAEliminar = f1.documentoABuscarParaEditar_XATv.getText().toString();
            if (docAEliminar.isEmpty()) {
                Toast.makeText(f1.getActivity(),
                        "No hay documento seleccionado", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(f1.getActivity())
                    .setTitle("Eliminar Documento")
                    .setMessage("¿Está seguro de eliminar el documento " + docAEliminar + "?")
                    .setCancelable(false)
                    .setPositiveButton("Sí", (dialog, id) -> {
                        f1.a2operacionesBD = new A1BASES.A1_2_OperacionesBD(f1.getActivity());
                        f1.a2operacionesBD.eliminarTransacciones(docAEliminar);
                        f1.clearViewsValuesForInitializeCRUD();
                        f1.clearArrayListsCRUD();
                        f1.dynamicQueryByDocumentinUpdate();
                        Toast.makeText(f1.getActivity(),
                                "Documento eliminado", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }

    // ── Checkbox "cambiar fecha" — pide confirmación antes de abrir calendario

    private void setupChangeDateCheckbox() {
        f1.assignedOtherDateInUpdate_XChB.setOnClickListener(v -> {
            new AlertDialog.Builder(f1.getActivity())
                    .setTitle("La fecha inicial del Documento es: "
                            + f1.dateInUpdate_XTv.getText().toString())
                    .setMessage("¿ Desea cambiarla ?")
                    .setCancelable(false)
                    .setPositiveButton("Si", (dialog, id) -> {
                        f1.areaConciliacionYRegistro_XGl.setVisibility(View.GONE);
                        f1.areaCalendario_XGl.setVisibility(View.VISIBLE);
                    })
                    .setNegativeButton("No", (dialog, id) -> {
                        Toast.makeText(f1.getActivity(),
                                "No se realizo el proceso", Toast.LENGTH_LONG).show();
                        f1.assignedOtherDateInUpdate_XChB.setChecked(false);
                    })
                    .show();
        });
    }

    // ── Click en el número consecutivo — carga ese documento directamente ──

    private void setupConsecutivoClick() {
        f1.numeroConsecutivoDocEnEdicion_XTv.setOnClickListener(v -> {
            f1.documentoABuscarParaEditar_XATv.setText(
                    f1.numeroConsecutivoDocEnEdicion_XTv.getText());
            Log.d("foco", "aqui");
            // NOTE: llamado dos veces — comportamiento original preservado
            f1.colocarDocConsultadoEnListaItemDoc();
            f1.colocarDocConsultadoEnListaItemDoc();
            Toast.makeText(f1.getActivity(),
                    "Se edito el Documento", Toast.LENGTH_SHORT).show();
            f1.pasarItemListaTodoResumidoAItemListaRevision();
        });
    }
}