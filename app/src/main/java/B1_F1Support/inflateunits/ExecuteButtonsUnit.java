package B1_F1Support.inflateunits;

import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.jj.appbalancev31.R;

import A1BASES.A1_2_OperacionesBD;
import B_FRAGMENTS.F1_CrudDocumento;

public class ExecuteButtonsUnit {

    private final F1_CrudDocumento f1;

    public ExecuteButtonsUnit(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        setupListeners();
    }

    private void inflateViews(View view) {
        f1.salidaEsteFragment_XBt  = view.findViewById(R.id.salidaEsteFragment_XBt);
        f1.guardarCambiosALaBD     = view.findViewById(R.id.guardarCambiosALaBD);
        f1.cleanSinCambiosInBD_XBt = view.findViewById(R.id.cleanSinCambiosInBD_XBt);
    }

    private void setupListeners() {
        setupExitButton();
        setupSaveButton();
        setupCleanButton();
    }

    // Cierra el DialogFragment
    private void setupExitButton() {
        f1.salidaEsteFragment_XBt.setOnClickListener(v -> f1.dismiss());
    }

    // Valida campos y guarda el documento activo en la BD
    private void setupSaveButton() {
        f1.guardarCambiosALaBD.setOnClickListener(rootView -> {
            try {
                f1.sumarItemListaDocumento();
                f1.actualizarSumasListado();

                RadioGroup radioGroup = f1.requireView().findViewById(R.id.optionsDoc_XRg);
                int selectedId = radioGroup.getCheckedRadioButtonId();
                if (selectedId == -1) return;

                String validationError = f1.validarCampos(selectedId);
                if (validationError != null) {
                    showSnackbar(rootView, validationError, selectedId);
                    return;
                }

                // ⭐ NUEVO (10-nov, Jorge): control de registros repetidos antes de guardar. En
                // el doc. 1124 una plantilla quedó con sus registros duplicados; la regla UNIQUE
                // (Documento, Item) no lo ve porque cada copia lleva otro ítem. Si hay registros
                // con la misma cuenta, valor y descripción se avisa y el usuario decide.
                String repetidos = describirRegistrosRepetidos();
                if (repetidos != null) {
                    new AlertDialog.Builder(f1.requireContext())
                            .setTitle("Registros repetidos")
                            .setMessage("El documento tiene registros con la misma cuenta, valor "
                                    + "y descripción:\n" + repetidos
                                    + "\n\n¿Guardar igual?")
                            .setPositiveButton("Guardar igual", (d, w) -> guardarDocumento(selectedId))
                            .setNegativeButton("Revisar", null)
                            .show();
                    return;
                }

                guardarDocumento(selectedId);

            } catch (Exception e) {
                Log.e("ExecuteButtonsUnit", "Error saving document", e);
                Toast.makeText(f1.getActivity(),
                        "Error al guardar: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * ⭐ NUEVO (10-nov): lista de los registros que se repiten en el documento en pantalla (misma
     * cuenta, valor y descripción), o null si no hay ninguno.
     */
    private String describirRegistrosRepetidos() {
        if (f1.listaDocumento_ArrayLTT == null || f1.listaDocumento_ArrayLTT.size() < 2) return null;
        java.util.LinkedHashMap<String, Integer> veces = new java.util.LinkedHashMap<>();
        for (A1BASES.A3_2_TipoTransaccionesGetsYSets p : f1.listaDocumento_ArrayLTT) {
            String clave = String.valueOf(p.tipoTget_3CuentaMetodoEnA5()).trim()
                    + " · " + p.tipoTget_5ValorMetodoEnA5()
                    + " · " + String.valueOf(p.tipoTget_6DescripcionMetodoEnA5()).trim();
            Integer n = veces.get(clave);
            veces.put(clave, n == null ? 1 : n + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, Integer> e : veces.entrySet()) {
            if (e.getValue() > 1) {
                sb.append("\n• ").append(e.getKey()).append("  (").append(e.getValue()).append(" veces)");
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Guardado del documento según el área (antes iba dentro del listener de Guardar). */
    private void guardarDocumento(int selectedId) {
        try {
                // ⭐ CAMBIO — v12 tanda 3 (segundo fix, 29-sep): guardadoExitoso rastrea si el
                // documento realmente se guardó, para no llamar a
                // f1.realizarOperacionesPostSeleccion() cuando el guardado fue bloqueado (esa
                // función limpia la lista de ítems en pantalla y muestra un Toast de ÉXITO —
                // "Backup local, en Drive y documento actualizado" — que antes se mostraba
                // IGUAL aunque el guardado se hubiera bloqueado por una cuenta con inventario,
                // tapando/contradiciendo el aviso real de bloqueo).
                boolean guardadoExitoso;

                String radioName = f1.getResources().getResourceEntryName(selectedId);
                switch (radioName) {
                    case "create_XRb":
                    case "template_XRb":
                        guardadoExitoso = f1.baseParaGuardarEnLaEnBDConListaDocumento(radioName);
                        break;
                    case "updateDelete_XRb":
                        if (!f1.documentoABuscarParaEditar_XATv.getText().toString().isEmpty()) {
                            // ⭐ CORRECCIÓN — v12 tanda 3 (segundo fix, 29-sep): la verificación
                            // de cuenta-con-inventario se hace ANTES de borrar las transacciones
                            // viejas del documento. Antes de este cambio, el orden era: borrar
                            // TODO lo viejo del documento (eliminarTransacciones) → recién ahí
                            // intentar reinsertar la lista completa, que podía bloquearse si
                            // algún ítem apuntaba a una cuenta con inventario — dejando el
                            // documento con sus transacciones viejas ya borradas y nada nuevo en
                            // su lugar (pérdida real de datos de ese documento). Validar primero
                            // cierra ese hueco: si está bloqueado, no se borra nada.
                            if (f1.persistence.bloqueadoPorCuentaConInventario()) {
                                guardadoExitoso = false;
                            } else {
                                f1.a2operacionesBD = new A1_2_OperacionesBD(f1.getActivity());
                                f1.a2operacionesBD.eliminarTransacciones(
                                        f1.documentoABuscarParaEditar_XATv.getText().toString());
                                guardadoExitoso = f1.baseParaGuardarEnLaEnBDConListaDocumento(radioName);
                            }
                        } else {
                            guardadoExitoso = false;
                        }
                        break;
                    default:
                        Toast.makeText(f1.getActivity(),
                                "Opción no reconocida: " + radioName,
                                Toast.LENGTH_SHORT).show();
                        return;
                }
                if (guardadoExitoso) {
                    f1.realizarOperacionesPostSeleccion(selectedId);
                }

            } catch (Exception e) {
                Log.e("ExecuteButtonsUnit", "Error saving document", e);
                Toast.makeText(f1.getActivity(),
                        "Error al guardar: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
    }

    // Muestra snackbar de error anclado al botón guardar
    private void showSnackbar(View rootView, String message, int anchorId) {
        F1_CrudDocumento.showSnackbar(
                rootView,
                message,
                2000,
                Color.parseColor("#E91E63"),
                Color.WHITE,
                anchorId,
                null,
                Color.YELLOW,
                v -> Toast.makeText(f1.getActivity(),
                        "Acción realizada", Toast.LENGTH_SHORT).show()
        );
    }

    // Descarta el trabajo pendiente del área activa tras confirmación
    private void setupCleanButton() {
        f1.cleanSinCambiosInBD_XBt.setOnClickListener(v -> {
            new AlertDialog.Builder(f1.getActivity())
                    .setTitle("Importante")
                    .setMessage("¿ Eliminar la informacion pendiente de terminar ? No se podrán recuperar.")
                    .setCancelable(false)
                    .setPositiveButton("Sí", (dialog, id) -> {
                        int activeArea = f1.optionsDoc_XRg.getCheckedRadioButtonId();

                        if (activeArea == R.id.create_XRb) {
                            f1.deleteCsvBackupsCRUD(R.id.create_XRb);
                        } else if (activeArea == R.id.template_XRb) {
                            f1.deleteCsvBackupsCRUD(R.id.template_XRb);
                            f1.setInvisibleTemplateAntesDeEditar();
                        } else if (activeArea == R.id.updateDelete_XRb) {
                            f1.deleteCsvBackupsCRUD(R.id.updateDelete_XRb);
                            f1.setInvisibleUpdateAndDeleteAntesDeEditar();
                        } else {
                            Toast.makeText(f1.getActivity(),
                                    "No se seleccionó una opción válida",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        f1.clearViewsValuesForInitializeCRUD();
                        f1.clearArrayListsCRUD();
                        Toast.makeText(f1.getActivity(),
                                "Se limpió el documento", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("No", null)
                    .show();
        });
    }
}