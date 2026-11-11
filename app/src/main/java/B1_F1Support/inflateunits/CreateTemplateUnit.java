package B1_F1Support.inflateunits;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import com.jj.appbalancev31.R;

import D_ADAPTERS.D_F1_AdaptadorCrudDocumento;
import B_FRAGMENTS.F1_CrudDocumento;

public class CreateTemplateUnit {

    private final F1_CrudDocumento f1;

    public CreateTemplateUnit(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        loadInitialData();
        setupListeners();
    }

    private void inflateViews(View view) {
        f1.areaTemplate_XGl                            = view.findViewById(R.id.areaTemplate_XGl);
        f1.descripcionABuscarEnPlantilla_XAtv          = view.findViewById(R.id.descripcionABuscarEnPlantilla_XAtv);
        f1.copyPasteDescriptionTemplate_XChb           = view.findViewById(R.id.copyPasteDescriptionTemplate_XChb);
        f1.consecutivoNuevoDocEnPLantilla_XTv          = view.findViewById(R.id.consecutivoNuevoDocEnPLantilla_XTv);
        f1.documentoYFechaInicialBaseDeLaPLantilla_XTv = view.findViewById(R.id.documentoYFechaInicialBaseDeLaPLantilla_XTv);
        f1.dateInTemplate_XTv                          = view.findViewById(R.id.dateInTemplate_XTv);
        f1.assignedDateInTemplate_XChB                 = view.findViewById(R.id.assignedDateInTemplate_XChB);
    }

    private void loadInitialData() {
        f1.dynamicQueryByDescriptionPlantillaOnly(f1.descripcionABuscarEnPlantilla_XAtv);
    }

    private void setupListeners() {
        setupOnItemClick();
        setupTextWatcher();
        setupCopyPasteButton();
        setupAssignDateCheckbox();
    }

    // Selección de plantilla en el dropdown
    private void setupOnItemClick() {
        f1.descripcionABuscarEnPlantilla_XAtv.setOnItemClickListener(
                (adapterView, view, i, l) -> {
                    // NOTE: llamado dos veces — comportamiento original preservado
                    f1.colocarDocConsultadoEnListaItemDoc();
                    f1.colocarDocConsultadoEnListaItemDoc();
                    f1.actualizarSumasListado();
                    f1.sumarItemListaDocumento();

                    f1.siguienteDocEnAdicionar_Integer =
                            Integer.parseInt(f1.ultimoDocumentoEnLaTabla) + 1;
                    f1.nuevoNumeroDocEnAdicionar_String =
                            String.format("%04d", f1.siguienteDocEnAdicionar_Integer);
                    f1.consecutivoNuevoDocEnPLantilla_XTv.setText(f1.nuevoNumeroDocEnAdicionar_String);

                    f1.pasarItemListaTodoResumidoAItemListaRevision();
                    f1.ocultarTeclado();
                });
    }

    // Escritura en el campo de búsqueda de plantilla
    private void setupTextWatcher() {
        f1.descripcionABuscarEnPlantilla_XAtv.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                int currentLength = f1.descripcionABuscarEnPlantilla_XAtv.getText().length();

                if (count > 0) {
                    if (f1.descripcionABuscarEnPlantilla_XAtv.getText().toString().isEmpty()) {
                        Toast.makeText(f1.getActivity(),
                                "Falta el nombre de la plantilla", Toast.LENGTH_LONG).show();
                        return;
                    }
                    f1.descripcionPLantillaABuscar_String =
                            f1.descripcionABuscarEnPlantilla_XAtv.getText().toString();
                    f1.setvisibleTemplateDespuesDeEditar();

                    if (f1.llamadaCursorUnDocumento != null
                            && f1.llamadaCursorUnDocumento.moveToNext()) {
                        f1.descripcionABuscarEnPlantilla_XAtv
                                .setBackgroundColor(Color.parseColor("#90E0D8"));
                    }
                } else if (before > 0) {
                    f1.limpiarInformacionTemplateUpdateAlDisminuirCaracteres(currentLength);
                }

                if (count == 0) {
                    f1.descripcionABuscarEnPlantilla_XAtv
                            .setBackgroundColor(Color.parseColor("#F4D7F5"));
                    f1.consecutivoNuevoDocEnPLantilla_XTv.setText("");
                }
            }

            @Override public void afterTextChanged(Editable editable) {}
        });
    }

    // ⭐ REDISEÑO (11-nov, Jorge): el ↻ de la descripción de plantilla usa el ciclo de
    // A18_CicloCampo (lleno → vacío → lo borrado → anterior), con memoria propia guardada en
    // preferencias. Antes compartía isCopyMode / copiedText con el buscador de cuenta y con el
    // número del documento en Edición, y al pegar no cargaba la plantilla.
    //   Con texto → carga la plantilla igual que al elegirla de la lista.
    //   Vacío     → limpia la lista, como antes.
    private void setupCopyPasteButton() {
        if (f1.getContext() == null) return;
        final A1BASES.A18_CicloCampo ciclo = new A1BASES.A18_CicloCampo(f1.getContext(),
                "f1_plantilla", f1.descripcionABuscarEnPlantilla_XAtv, texto -> {
                    f1.descripcionABuscarEnPlantilla_XAtv.dismissDropDown();
                    if (TextUtils.isEmpty(texto)) {
                        f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                                new D_F1_AdaptadorCrudDocumento(
                                        f1.getActivity(), f1.listaDocumento_ArrayLTT, null);
                        f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.clear();
                    } else if (f1.descripcionABuscarEnPlantilla_XAtv.getOnItemClickListener() != null) {
                        f1.descripcionABuscarEnPlantilla_XAtv.getOnItemClickListener()
                                .onItemClick(null, null, 0, 0);
                    }
                });
        f1.copyPasteDescriptionTemplate_XChb.setOnClickListener(v -> ciclo.tocar());
        f1.copyPasteDescriptionTemplate_XChb.setOnLongClickListener(v -> ciclo.tocarLargo());
    }

    // Checkbox "asignar fecha" → oculta conciliación, muestra calendario
    private void setupAssignDateCheckbox() {
        f1.assignedDateInTemplate_XChB.setOnClickListener(v -> {
            f1.areaConciliacionYRegistro_XGl.setVisibility(View.GONE);
            f1.areaCalendario_XGl.setVisibility(View.VISIBLE);
            f1.assignedDateInTemplate_XChB.setChecked(true);
        });
    }
}