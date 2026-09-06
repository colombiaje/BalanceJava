package B1_F1Support;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.snackbar.Snackbar;
import com.jj.appbalancev31.R;

import java.util.ArrayList;

import A1BASES.A1_1_AyudanteBD;
import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A5_CacheManager;
import B_FRAGMENTS.F1_CrudDocumento;
import D_ADAPTERS.D_F1_AdaptadorCrudDocumento;

/**
 * NavigationManager — gestión de canales de navegación y restauración de F1.
 *
 * Responsabilidades:
 * - Canal A: regreso desde menú lateral (NavController)
 * - Canal B: regreso desde Home / interrupciones del sistema
 * - Canal C: cambio de RadioButton (procesarCambioDeRadioButton)
 * - Canal D: regreso desde F2 con documento seleccionado
 * - Diálogos de restauración/borrado de caché
 * - Backup/restore de encabezado y lista
 * - Botón verde (slot 3 ↔ slot 4)
 */
public class B13_NavigationManager {

    private static final String TAG = "NavigationManager";
    private final F1_CrudDocumento f1;

    public B13_NavigationManager(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    // ═══════════════════════════════════════════════════════════════
    // CANAL A — Regreso desde el menú lateral (NavController)
    // ═══════════════════════════════════════════════════════════════
    public void ejecutarCanalA() {
        f1.vieneDeNavController = false;
        f1.setVisibilityGoneTodo();

        SharedPreferences prefs = f1.getContext().getSharedPreferences(
                "MyAppPreferences", Context.MODE_PRIVATE);
        int radioButtonIdARestaurar = prefs.getInt("areaGuardadaCanalA", R.id.create_XRb);
        f1.areaGuardadaCanalA = 0;

        final int areaFinal = radioButtonIdARestaurar;
        f1.optionsDoc_XRg.setOnCheckedChangeListener(null);
        f1.optionsDoc_XRg.check(areaFinal);
        f1.currentRadioButtonId = areaFinal;

        f1.listaDocumento_ArrayLTT = new ArrayList<>();
        f1.limpiarListaYAdaptador();
        f1.clearViewsValuesForInitializeCRUD();
        f1.clearArrayListsCRUD();

        int areaId = A5_CacheManager.radioButtonToAreaId(
                areaFinal, R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        if (A5_CacheManager.existeCache(f1.getContext(), areaId)) {
            f1.estaRestaurandoCanalA = true; // ← activar antes del diálogo
            mostrarDialogoCanalA(areaFinal, areaId);
        } else {
            mostrarAreaLimpia(areaFinal);
            f1.restaurarListenerRadioGroup();
        }

    }

    public void mostrarDialogoCanalA(int radioButtonId, int areaId) {
        if (f1.getActivity() == null || !f1.isAdded()) return;
        f1.optionsDoc_XRg.setOnCheckedChangeListener(null);

        new AlertDialog.Builder(f1.getActivity())
                .setTitle("Documento pendiente")
                .setMessage("Hay un borrador guardado en esta área. ¿Desea recuperarlo o empezar de cero?")
                .setCancelable(false)
                .setPositiveButton("RESTAURAR", (dialog, which) -> {
                    f1.limpiarListaYAdaptador();
                    A5_CacheManager.Encabezado enc =
                            A5_CacheManager.restaurarEncabezado(f1.getContext(), areaId);
                    f1.listaDocumento_ArrayLTT =
                            A5_CacheManager.restaurarRegistros(f1.getContext(), areaId);
                    conectarAdaptador();
                    if (enc != null) f1.aplicarEncabezadoAVistas(enc);
                    actualizarContador();
                    f1.pasarItemListaTodoResumidoAItemListaRevision();
                    f1.sumarItemListaDocumento();
                    f1.actualizarSumasListado();
                    f1.mostrarAreaCorrespondiente(radioButtonId);
                    f1.restaurarListenerRadioGroup();
                    f1.estaRestaurandoCanalA = false; // ← reset aquí, backup ya cargado
                })
                .setNegativeButton("BORRAR", (dialog, which) -> {
                    A5_CacheManager.eliminar(f1.getContext(), areaId);
                    f1.clearViewsValuesForInitializeCRUD();
                    f1.limpiarListaYAdaptador();
                    mostrarAreaLimpia(radioButtonId);
                    f1.restaurarListenerRadioGroup();
                    f1.estaRestaurandoCanalA = false; // ← reset también en borrar
                })
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // CANAL B — Regreso desde Home / interrupciones del sistema
    // ═══════════════════════════════════════════════════════════════
    public void ejecutarCanalB() {
        f1.vieneDeHome = false;

        SharedPreferences prefs = f1.getContext().getSharedPreferences(
                "MyAppPreferences", Context.MODE_PRIVATE);
        int radioButtonIdARestaurar = prefs.getInt(
                "lastSelectedRadioButtonId", R.id.create_XRb);

        f1.optionsDoc_XRg.setOnCheckedChangeListener(null);
        f1.optionsDoc_XRg.check(radioButtonIdARestaurar);
        f1.currentRadioButtonId = radioButtonIdARestaurar;

        f1.setVisibilityGoneTodo();
        f1.listaDocumento_ArrayLTT = new ArrayList<>();
        f1.limpiarListaYAdaptador();
        f1.clearViewsValuesForInitializeCRUD();
        f1.clearArrayListsCRUD();

        int areaId = A5_CacheManager.radioButtonToAreaId(
                radioButtonIdARestaurar,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        if (A5_CacheManager.existeCache(f1.getContext(), areaId)) {
            mostrarDialogoCanalB(radioButtonIdARestaurar, areaId);
        } else {
            mostrarAreaLimpia(radioButtonIdARestaurar);
            f1.restaurarListenerRadioGroup();
        }
    }

    public void mostrarDialogoCanalB(int radioButtonId, int areaId) {
        if (f1.getActivity() == null || !f1.isAdded()) return;
        f1.optionsDoc_XRg.setOnCheckedChangeListener(null);

        new AlertDialog.Builder(f1.getActivity())
                .setTitle("Documento pendiente")
                .setMessage("Hay un borrador guardado. ¿Desea recuperarlo o empezar de cero?")
                .setCancelable(false)
                .setPositiveButton("RESTAURAR", (dialog, which) -> {
                    Log.d("canal B","aqui B");
                    f1.setVisibilityGoneTodo();
                    f1.limpiarListaYAdaptador();
                    A5_CacheManager.Encabezado enc =
                            A5_CacheManager.restaurarEncabezado(f1.getContext(), areaId);
                    f1.listaDocumento_ArrayLTT =
                            A5_CacheManager.restaurarRegistros(f1.getContext(), areaId);
                    conectarAdaptador();
                    if (enc != null) f1.aplicarEncabezadoAVistas(enc);
                    actualizarContador();
                    f1.pasarItemListaTodoResumidoAItemListaRevision();
                    f1.sumarItemListaDocumento();
                    f1.actualizarSumasListado();
                    f1.mostrarAreaCorrespondiente(radioButtonId);
                    f1.restaurarListenerRadioGroup();
                })
                .setNegativeButton("BORRAR", (dialog, which) -> {
                    A5_CacheManager.eliminar(f1.getContext(), areaId);
                    f1.clearViewsValuesForInitializeCRUD();
                    f1.limpiarListaYAdaptador();
                    mostrarAreaLimpia(radioButtonId);
                    f1.restaurarListenerRadioGroup();
                })
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // CANAL C — Cambio de RadioButton
    // ═══════════════════════════════════════════════════════════════
    public void procesarCambioDeRadioButton(int nuevoRadioButtonId) {
        if (f1.hayDatosEnAreaActual()) {
            f1.actualizarSnapshot(); // ✅ capturar antes de limpiar
            f1.hacerBackupSilencioso(f1.currentRadioButtonId);
        }
        f1.listaDocumento_ArrayLTT = new ArrayList<>();
        f1.limpiarListaYAdaptador();
        f1.clearViewsValuesForInitializeCRUD();
        f1.clearArrayListsCRUD();
        f1.optionsDoc_XRg.setOnCheckedChangeListener(null);
        f1.currentRadioButtonId = nuevoRadioButtonId;

        if (f1.existenBackupsPara(nuevoRadioButtonId)) {
            mostrarDialogoRestauracionUnificado(nuevoRadioButtonId, false);
        } else {
            mostrarAreaLimpia(nuevoRadioButtonId);
            f1.restaurarListenerRadioGroup();
        }
    }

    public void mostrarDialogoRestauracionUnificado(int radioButtonIdDestino,
                                                    boolean esDesdeOnResume) {
        if (f1.getActivity() == null || !f1.isAdded()) return;
        f1.optionsDoc_XRg.setOnCheckedChangeListener(null);

        new AlertDialog.Builder(f1.getActivity())
                .setTitle("Documento pendiente")
                .setMessage("Se detectó un borrador en curso para esta área. ¿Desea recuperarlo o empezar de cero?")
                .setCancelable(!esDesdeOnResume)
                .setPositiveButton("CONTINUAR", (dialog, which) -> {
                    f1.currentRadioButtonId = radioButtonIdDestino;
                    f1.limpiarListaYAdaptador();
                    f1.restoreBackups(radioButtonIdDestino);
                    f1.estaRestaurando = false;
                    f1.mostrarAreaCorrespondiente(radioButtonIdDestino);
                    f1.restaurarListenerRadioGroup();
                })
                .setNegativeButton("BORRAR", (dialog, which) -> {
                    f1.eliminarBackups(radioButtonIdDestino);
                    f1.currentRadioButtonId = radioButtonIdDestino;
                    f1.estaRestaurando = false;
                    f1.clearViewsValuesForInitializeCRUD();
                    f1.limpiarListaYAdaptador();
                    mostrarAreaLimpia(radioButtonIdDestino);
                    f1.restaurarListenerRadioGroup();
                })
                .setOnCancelListener(dialog -> {
                    f1.estaRestaurando = false;
                    f1.restaurarListenerRadioGroup();
                })
                .show();
    }

    // ═══════════════════════════════════════════════════════════════
    // CANAL D — Regreso desde F2 con documento seleccionado
    // ═══════════════════════════════════════════════════════════════
    public void ejecutarCanalD() {
        f1.vieneDeVerItemTransaccion = false;
        actualizarVisibilidadBotonVerde();
    }

    public void cargarDocumentoEnArea3CanalD(String documentoRecibido) {
        f1.documentoABuscarRecibido = documentoRecibido;
        f1.documentoABuscarParaEditar_XATv.setText(documentoRecibido);

        f1.setVisibilityGoneTodo();
        f1.currentRadioButtonId = R.id.updateDelete_XRb;
        f1.setVisibilityVisibleAreasForUpdateAndDelete();
        f1.colocarDocConsultadoEnListaItemDoc();
        f1.pasarItemListaTodoResumidoAItemListaRevision();

        if (f1.listaCuentasDeRevision_XSp.getAdapter() != null
                && f1.listaCuentasDeRevision_XSp.getAdapter().getCount() > 1) {
            f1.listaCuentasDeRevision_XSp.setSelection(1);
            f1.actualizarSpinnerCuentasRevision();
        }
        f1.resetearSpinnerCuentasRevision();

        f1.updateDelete_XRb.post(() -> {
            f1.optionsDoc_XRg.setOnCheckedChangeListener(null);
            f1.optionsDoc_XRg.clearCheck();
            f1.updateDelete_XRb.setChecked(true);
            f1.optionsDoc_XRg.postDelayed(() ->
                    f1.optionsDoc_XRg.setOnCheckedChangeListener((group, checkedId) -> {
                        if (checkedId != -1 && checkedId != f1.currentRadioButtonId) {
                            procesarCambioDeRadioButton(checkedId);
                        }
                    }), 100);
        });
        actualizarVisibilidadBotonVerde();
    }

    public void mostrarDialogoCanalD(String documentoRecibido) {
        String docEnSlot3 = obtenerNumeroDocDeSlot(A1_1_AyudanteBD.AREA_UPDATE);

        new AlertDialog.Builder(f1.requireContext())
                .setTitle("Área 3 tiene trabajo pendiente")
                .setMessage("Doc. en edición: #" + docEnSlot3
                        + "\nDoc. nuevo de F2: #" + documentoRecibido
                        + "\n\n¿Qué deseas hacer?")
                .setCancelable(false)
                .setPositiveButton("EDITAR NUEVO O SOBREESCRIBIR", (dialog, which) -> {
                    A5_CacheManager.eliminar(f1.getContext(), A1_1_AyudanteBD.AREA_UPDATE);
                    Log.d("canal D","aqui D");
                    cargarDocumentoEnArea3CanalD(documentoRecibido);
                })
                .setNegativeButton("GUARDAR AMBOS DOCUMENTOS", (dialog, which) -> {
                    f1.documentoABuscarParaEditar_XATv.setText(documentoRecibido);
                    f1.dynamicQueryTransactionOneDocument(documentoRecibido);

                    A5_CacheManager.Encabezado encEspera = new A5_CacheManager.Encabezado();
                    encEspera.campo_8_docBuscarEditar = documentoRecibido;
                    if (f1.transaccionesUnDocumento_ArrayListTT_Result != null
                            && f1.transaccionesUnDocumento_ArrayListTT_Result.size() > 0) {
                        encEspera.campo_3_fechaUpdate = String.valueOf(
                                f1.transaccionesUnDocumento_ArrayListTT_Result
                                        .get(0).tipoTget_8FechaInicialMetodoEnA5());
                    }

                    ArrayList<A3_2_TipoTransaccionesGetsYSets> registrosDocF2 =
                            (ArrayList<A3_2_TipoTransaccionesGetsYSets>)
                                    f1.transaccionesUnDocumento_ArrayListTT_Result;

                    A5_CacheManager.guardarRegistros(f1.getContext(),
                            A1_1_AyudanteBD.AREA_UPDATE_ESPERA, registrosDocF2);
                            Log.d("hacer_backup","aqui 1 NavManager");
                    A5_CacheManager.guardarEncabezado(f1.getContext(),
                            A1_1_AyudanteBD.AREA_UPDATE_ESPERA, encEspera);
                    Log.d("hacer_backup","aqui 2 NavManager");

                    String docSlot3 = obtenerNumeroDocDeSlot(A1_1_AyudanteBD.AREA_UPDATE);
                    f1.documentoABuscarParaEditar_XATv.setText(docSlot3);

                    f1.setVisibilityGoneTodo();
                    f1.currentRadioButtonId = R.id.updateDelete_XRb;
                    f1.setVisibilityVisibleAreasForUpdateAndDelete();
                    f1.restoreBackups(R.id.updateDelete_XRb);

                    f1.updateDelete_XRb.post(() -> {
                        f1.optionsDoc_XRg.setOnCheckedChangeListener(null);
                        f1.optionsDoc_XRg.clearCheck();
                        f1.updateDelete_XRb.setChecked(true);
                        f1.optionsDoc_XRg.postDelayed(() ->
                                f1.optionsDoc_XRg.setOnCheckedChangeListener((group, checkedId) -> {
                                    if (checkedId != -1 && checkedId != f1.currentRadioButtonId) {
                                        procesarCambioDeRadioButton(checkedId);
                                    }
                                }), 100);
                    });
                    actualizarVisibilidadBotonVerde();
                })
                .show();
    }

    public void intercambiarSlot3YSlot4CanalD() {
        f1.hacerBackupSilenciosoCanalD(A1_1_AyudanteBD.AREA_UPDATE);

        A5_CacheManager.Encabezado encSlot4 = A5_CacheManager.restaurarEncabezado(
                f1.getContext(), A1_1_AyudanteBD.AREA_UPDATE_ESPERA);
        ArrayList<A3_2_TipoTransaccionesGetsYSets> registrosSlot4 =
                A5_CacheManager.restaurarRegistros(
                        f1.getContext(), A1_1_AyudanteBD.AREA_UPDATE_ESPERA);

        A5_CacheManager.Encabezado encSlot3 = A5_CacheManager.restaurarEncabezado(
                f1.getContext(), A1_1_AyudanteBD.AREA_UPDATE);
        ArrayList<A3_2_TipoTransaccionesGetsYSets> registrosSlot3 =
                A5_CacheManager.restaurarRegistros(
                        f1.getContext(), A1_1_AyudanteBD.AREA_UPDATE);

        if (encSlot3 != null) {
            A5_CacheManager.guardarRegistros(f1.getContext(),
                    A1_1_AyudanteBD.AREA_UPDATE_ESPERA, registrosSlot3);
            A5_CacheManager.guardarEncabezado(f1.getContext(),
                    A1_1_AyudanteBD.AREA_UPDATE_ESPERA, encSlot3);
        }
        if (encSlot4 != null) {

            A5_CacheManager.guardarRegistros(f1.getContext(),
                    A1_1_AyudanteBD.AREA_UPDATE, registrosSlot4);
            A5_CacheManager.guardarEncabezado(f1.getContext(),
                    A1_1_AyudanteBD.AREA_UPDATE, encSlot4);

        }

        f1.restoreBackups(R.id.updateDelete_XRb);
        actualizarVisibilidadBotonVerde();
    }

    public void actualizarVisibilidadBotonVerde() {
        if (f1.botonVerde_XBt == null || f1.getContext() == null) return;

        boolean hayEnSlot4 = A5_CacheManager.existeCache(
                f1.getContext(), A1_1_AyudanteBD.AREA_UPDATE_ESPERA);

        if (hayEnSlot4) {

            // 1. Obtenemos los LayoutParams actuales del botón
            ViewGroup.LayoutParams params = f1.botonVerde_XBt.getLayoutParams();

            // 2. Modificamos la altura (en píxeles)
            params.height = 120; // Cambia 180 por el valor que necesites

            // 3. Le aplicamos los parámetros modificados de vuelta al botón
            f1.botonVerde_XBt.setLayoutParams(params);

            String docEspera = obtenerNumeroDocDeSlot(A1_1_AyudanteBD.AREA_UPDATE_ESPERA);
            f1.botonVerde_XBt.setText("⇄ En espera: #" + docEspera);
            f1.botonVerde_XBt.setVisibility(View.VISIBLE);

            if (!f1.snackbarVerdeYaMostrado) {
                f1.snackbarVerdeYaMostrado = true;
                f1.getActivity().getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("snackbarVerdeYaMostrado", true).apply();
                View root = f1.getView();
                if (root != null) {
                    Snackbar.make(root,
                            "Toca el botón verde para intercambiar documentos entre Área 3 y espera",
                            Snackbar.LENGTH_LONG).show();
                }
            }
        } else {
            f1.botonVerde_XBt.setVisibility(View.GONE);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers compartidos por los 4 canales
    // ═══════════════════════════════════════════════════════════════

    public void mostrarAreaLimpia(int radioButtonId) {
        f1.mostrarAreaCorrespondiente(radioButtonId);
        if (radioButtonId == R.id.create_XRb) {
            f1.setvisibleCreateDespuesDeEditar();
        } else if (radioButtonId == R.id.template_XRb) {
            f1.setInvisibleTemplateAntesDeEditar();
        } else if (radioButtonId == R.id.updateDelete_XRb) {
            if (f1.documentoABuscarParaEditar_XATv != null
                    && f1.documentoABuscarParaEditar_XATv.length() > 0) {
                f1.setvisibleUpdateAndDeleteDespuesDeRecibirBundle();
            } else {
                f1.setInvisibleUpdateAndDeleteAntesDeEditar();
            }
        }
    }

    public String obtenerNumeroDocDeSlot(int areaId) {
        A5_CacheManager.Encabezado enc =
                A5_CacheManager.restaurarEncabezado(f1.getContext(), areaId);
        if (enc != null && enc.campo_8_docBuscarEditar != null
                && !enc.campo_8_docBuscarEditar.isEmpty()) {
            return enc.campo_8_docBuscarEditar;
        }
        return "?";
    }

    public void mostrarMensajeLightCanalD(String mensaje) {
        View root = f1.getView();
        if (root != null) {
            Snackbar.make(root, mensaje, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void conectarAdaptador() {
        if (f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT == null) {
            f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                    new D_F1_AdaptadorCrudDocumento(
                            f1.getActivity(), f1.listaDocumento_ArrayLTT, null);
            f1.listaDocumento_XLv.setAdapter(
                    f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);
        } else {
            f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT.clear();
            f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT
                    .addAll(f1.listaDocumento_ArrayLTT);
            f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT
                    .notifyDataSetChanged();
        }
    }

    private void actualizarContador() {
        if (f1.listaDocumento_ArrayLTT != null
                && f1.listaDocumento_ArrayLTT.size() > 0) {
            f1.consecutivoItemRegistro_XTv.setText("Item:\n" +
                    f1.listaDocumento_ArrayLTT.size() + "/" +
                    f1.listaDocumento_ArrayLTT.size());
        } else {
            f1.consecutivoItemRegistro_XTv.setText("0/0");
        }
    }

    public void ejecutarCanalInicio() {
        SharedPreferences prefs = f1.getContext()
                .getSharedPreferences("MyAppPreferences", Context.MODE_PRIVATE);
        int areaGuardada = prefs.getInt(
                "lastSelectedRadioButtonId", R.id.create_XRb);

        f1.currentRadioButtonId = areaGuardada;
        f1.optionsDoc_XRg.setOnCheckedChangeListener(null);
        f1.optionsDoc_XRg.check(areaGuardada);

        int areaId = A5_CacheManager.radioButtonToAreaId(
                areaGuardada,
                R.id.create_XRb, R.id.template_XRb, R.id.updateDelete_XRb);

        if (A5_CacheManager.existeCache(f1.getContext(), areaId)) {
            f1.estaRestaurandoCanalA = true; // ← activar antes del diálogo
            mostrarDialogoCanalA(areaGuardada, areaId);
        } else {
            mostrarAreaLimpia(areaGuardada);
            f1.restaurarListenerRadioGroup();
        }

    }
    //104 local
}
