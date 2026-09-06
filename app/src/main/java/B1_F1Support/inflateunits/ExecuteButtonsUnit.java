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

                String radioName = f1.getResources().getResourceEntryName(selectedId);
                switch (radioName) {
                    case "create_XRb":
                    case "template_XRb":
                        f1.baseParaGuardarEnLaEnBDConListaDocumento(radioName);
                        break;
                    case "updateDelete_XRb":
                        if (!f1.documentoABuscarParaEditar_XATv.getText().toString().isEmpty()) {
                            f1.a2operacionesBD = new A1_2_OperacionesBD(f1.getActivity());
                            f1.a2operacionesBD.eliminarTransacciones(
                                    f1.documentoABuscarParaEditar_XATv.getText().toString());
                            f1.baseParaGuardarEnLaEnBDConListaDocumento(radioName);
                        }
                        break;
                    default:
                        Toast.makeText(f1.getActivity(),
                                "Opción no reconocida: " + radioName,
                                Toast.LENGTH_SHORT).show();
                        return;
                }
                f1.realizarOperacionesPostSeleccion(selectedId);

            } catch (Exception e) {
                Log.e("ExecuteButtonsUnit", "Error saving document", e);
                Toast.makeText(f1.getActivity(),
                        "Error al guardar: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
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