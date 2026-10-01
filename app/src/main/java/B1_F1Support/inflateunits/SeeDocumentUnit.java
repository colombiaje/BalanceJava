package B1_F1Support.inflateunits;

import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.jj.appbalancev31.R;

import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A2QueryBD.A23_QueryResult;
import B_FRAGMENTS.F1_CrudDocumento;

public class SeeDocumentUnit {

    private final F1_CrudDocumento f1;

    public SeeDocumentUnit(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        setupListeners();
    }

    private void inflateViews(View view) {
        f1.areaDocumento_XGl               = view.findViewById(R.id.areaDocumento_XGl);
        f1.envolventeEncabezadoItemLista_XLl = view.findViewById(R.id.envolventeEncabezadoItemLista_XLl);
        f1.listaDocumento_XLv              = view.findViewById(R.id.listaDocumento_XLv);
    }

    private void setupListeners() {
        setupListLongClick();
    }

    // Long press en ítem → diálogo: Modificar o Eliminar
    private void setupListLongClick() {
        f1.listaDocumento_XLv.setOnItemLongClickListener(
                (adapterView, view, position, l) -> {
                    new AlertDialog.Builder(f1.getContext())
                            .setTitle("¿ Que hacer con el registro de la lista ?:")
                            .setIcon(R.drawable.bg_white_square)
                            .setItems(
                                    new String[]{"Modificar", "Eliminar"},
                                    (dialog, which) -> handleItemAction(which, position))
                            .setNegativeButton("Ninguna accion", (dialog, id) ->
                                    Toast.makeText(f1.getActivity(),
                                            "Accion cancelada", Toast.LENGTH_LONG).show())
                            .show();
                    return false;
                });
    }

    private void handleItemAction(int action, int position) {
        switch (action) {
            case 0: // Modificar
                // ⭐ NUEVO — combinado A.1/B.a (pedido de Jorge): un ítem de una cuenta con
                // inventario no sabe editarse con el flujo inline de abajo (ver el bloqueo
                // explícito que ya existía en B12_DocumentPersistence.guardarModificacion) — se
                // redirige al diálogo de inventario, ahora en modo edición.
                A3_2_TipoTransaccionesGetsYSets itemAModificar =
                        f1.listaDocumento_ArrayLTT.get(position);
                if (itemAModificar.tipoTget_19ItemInventarioIdMetodoEnA5() != null) {
                    iniciarModificacionItemInventario(itemAModificar, position);
                    break;
                }

                // Edición inline de siempre, para ítems que no son de inventario.
                updateConsecutivoLabel(position);
                loadItemIntoFields(position);
                f1.iniciarModoModificacion(position);
                f1.digitarFisicoVsSaldoConciliacion();
                f1.actualizarTotales();
                f1.actualizarListView();
                ensureFabVisible();
                break;

            case 1: // Eliminar — remueve ítem y refresca
                f1.listaDocumento_ArrayLTT.remove(position);
                f1.renumerarItemsListaDocumento();
                f1.procesarActualizacionCompleta();
                break;
        }
    }

    // ⭐ NUEVO — combinado A.1/B.a (pedido de Jorge): abre el diálogo de inventario en modo
    // edición para el ítem seleccionado. Si el ítem YA está guardado en la base de datos
    // (documento reabierto con "Editar documento"), se rehúsa con un mensaje claro — mismo
    // límite que ya existía desde antes en B12_DocumentPersistence.bloqueadoPorCuentaConInventario()
    // (editar documentos con movimientos de inventario ya guardados sigue sin soportarse); esta
    // función respeta ese límite en vez de ampliarlo.
    private void iniciarModificacionItemInventario(A3_2_TipoTransaccionesGetsYSets item, int position) {
        if (item.tipoTget_15TransaccionIdMetodoEnA5() != null) {
            new AlertDialog.Builder(f1.getContext())
                    .setTitle("Movimiento de inventario ya guardado")
                    .setMessage("Este movimiento de inventario ya está guardado en la base de " +
                            "datos — modificar movimientos de inventario ya guardados todavía " +
                            "está en construcción, no se puede editar aquí por ahora.")
                    .setPositiveButton("Entendido", null)
                    .setCancelable(true)
                    .show();
            return;
        }

        String cuenta = item.tipoTget_3CuentaMetodoEnA5();
        A23_QueryResult<String[]> obtenerAtributo = f1.a22QueryManager.queryAttributesByAccount(cuenta);
        if (obtenerAtributo == null
                || obtenerAtributo.getAtributosCuenta() == null
                || obtenerAtributo.getAtributosCuenta().length == 0) {
            Toast.makeText(f1.getActivity(), "Cuenta no encontrada: " + cuenta, Toast.LENGTH_SHORT).show();
            return;
        }

        f1.persistence.mostrarDialogoRegistroInventarioParaEditar(
                cuenta, obtenerAtributo.getAtributosCuenta(), position);
    }

    // Actualiza etiqueta de posición (ej. "Item: 2/5")
    private void updateConsecutivoLabel(int position) {
        if (!f1.listaDocumento_ArrayLTT.isEmpty()) {
            f1.consecutivoItemRegistro_XTv.setText(
                    "Item:\n"
                            + f1.listaDocumento_ArrayLTT.get(position).tipoT_2DocumentItems_String
                            + "/" + f1.listaDocumento_ArrayLTT.size());
        } else {
            f1.consecutivoItemRegistro_XTv.setText("0/0");
        }
        f1.consecutivoRegistroAModificar_StringStatic =
                f1.listaDocumento_ArrayLTT.get(position).tipoT_2DocumentItems_String;
    }

    // Popula los campos de entrada con los datos del ítem seleccionado
    private void loadItemIntoFields(int position) {
        int valor = f1.listaDocumento_ArrayLTT.get(position).tipoT_5Value_Integer;
        f1.valor_XEt.setText("" + (valor < 0 ? valor * -1 : valor));

        f1.signo_XSp.setSelection(
                f1.signos_ListString.indexOf(
                        f1.listaDocumento_ArrayLTT.get(position).tipoT_4Sign_String));
        f1.descripcion_XAtv.setText(
                f1.listaDocumento_ArrayLTT.get(position).tipoT_6Description_String);
        f1.cuenta_XSp.setSelection(
                f1.accountAllAz_List.indexOf(
                        f1.listaDocumento_ArrayLTT.get(position).tipoT_3Accout_String));
    }

    // Garantiza que el FAB sea visible al entrar en modo modificación
    private void ensureFabVisible() {
        f1.fabModificar.post(() -> {
            if (f1.enModoModificacion
                    && f1.fabModificar.getVisibility() != View.VISIBLE) {
                f1.fabModificar.setVisibility(View.VISIBLE);
                f1.fabModificar.show();
                f1.animarBotonModificar();
            }
        });
    }
}