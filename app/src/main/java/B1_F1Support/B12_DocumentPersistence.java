package B1_F1Support;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A1BASES.A99_MetodosVarios;
import A2QueryBD.A23_QueryResult;
import B_FRAGMENTS.F1_CrudDocumento;
import D_ADAPTERS.D_F1_AdaptadorCrudDocumento;

/**
 * DocumentPersistence — persistencia y construcción de ítems de documento.
 *
 * Responsabilidades:
 * - Guardar lista de ítems en SQLite (baseParaGuardarEnLaEnBDConListaDocumento)
 * - Asignar fecha al documento según área activa (assignDocumentDate)
 * - Construir primer registro de conciliación (primerRegistroAListaDocumentoCuentaConciliable)
 * - Agregar ítems adicionales a la lista (losDemasRegistrosAListaDocumento)
 * - Calcular diferencia físico vs contable (digitarFisicoVsSaldoConciliacion)
 * - Guardar modificación inline de ítem (guardarModificacion)
 */
public class B12_DocumentPersistence {

    private static final String TAG = "DocumentPersistence";
    private final F1_CrudDocumento f1;

    public B12_DocumentPersistence(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    // ═══════════════════════════════════════════════════════════════
    // 1. baseParaGuardarEnLaEnBDConListaDocumento
    // ═══════════════════════════════════════════════════════════════
    public void baseParaGuardarEnLaEnBDConListaDocumento(String nombreDelRadioButton) {
        assignDocumentDate(nombreDelRadioButton);
        f1.renumerarItemsListaDocumento();

        SQLiteDatabase db = f1.ayudante_Class.getWritableDatabase();
        db.beginTransaction();
        try {
            for (A3_2_TipoTransaccionesGetsYSets p : f1.listaDocumento_ArrayLTT) {
                db.execSQL(
                        "INSERT INTO transacciones (" +
                                "c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                                "c6_Descripcion, c7_FechaYhora, c8_FechaInicial, " +
                                "c9_FechaModificacion, c10_Grupo1, c11_Grupo2, " +
                                "c12_ColumnaDisponible, c13_ColumnaDisponible) " +
                                "VALUES ('" +
                                p.tipoTget_1DocumentoMetodoEnA5()          + "','" +
                                p.tipoTget_2ItemDocMetodoEnA5()            + "','" +
                                p.tipoTget_3CuentaMetodoEnA5()             + "','" +
                                p.tipoTget_4MasMenosMetodoEnA5()           + "','" +
                                p.tipoTget_5ValorMetodoEnA5()              + "','" +
                                p.tipoTget_6DescripcionMetodoEnA5()        + "','" +
                                p.tipoTget_7FechaYHoraMetodoEnA5()         + "','" +
                                p.tipoTget_8FechaInicialMetodoEnA5()       + "','" +
                                p.tipoTget_9FechaModificacionMetodoEnA5()  + "','" +
                                p.tipoTget_10Grupo1MetodoEnA5()            + "','" +
                                p.tipoTget_11Grupo2MetodoEnA5()            + "','" +
                                p.tipoTget_12ColumnaDisponibleMetodoEnA5() + "','" +
                                p.tipoTget_13ColumnaDisponibleMetodoEnA5() + "')"
                );
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error inserting transactions", e);
            Toast.makeText(f1.getActivity(),
                    "Error al guardar las transacciones", Toast.LENGTH_SHORT).show();
        } finally {
            db.endTransaction();
            db.close();
        }

        f1.numerarDocumentoConsecutivo();
        f1.numeroConsecutivoDocEnEdicion_XTv.setText(f1.documentoRecibido_Resultado_String);
        f1.metodosVarios_Class.fechasYHoras();
    }

    // ═══════════════════════════════════════════════════════════════
    // 2. assignDocumentDate
    // ═══════════════════════════════════════════════════════════════
    public void assignDocumentDate(String radioButtonName) {
        switch (radioButtonName) {
            case "create_XRb":
                if (!f1.dateInCreateNew_XTv.getText().toString().isEmpty()) {
                    f1.DateOfDocument_Integer = f1.dateCurrent_ArrayInteger[5];
                }
                if (!f1.otherDateInCreateNew_XTv.getText().toString().isEmpty()) {
                    f1.DateOfDocument_Integer = f1.otherDateAssignedInCreateNew_Int;
                    for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                        item.tipoTset_8FechaInicialMetodoEnA5(f1.DateOfDocument_Integer);
                    }
                }
                break;

            case "template_XRb":
                if (f1.dateTemplateAssignedInCaledarView_Int > 0) {
                    f1.DateOfDocument_Integer = f1.dateTemplateAssignedInCaledarView_Int;
                    for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                        item.tipoTset_1DocumentoMetodoEnA5(f1.nuevoNumeroDocEnAdicionar_String);
                        item.tipoTset_7FechaYHoraMetodoEnA5(A99_MetodosVarios.stringFechaYHora);
                        item.tipoTset_8FechaInicialMetodoEnA5(f1.DateOfDocument_Integer);
                        item.tipoTset_9FechaModificacionMetodoEnA5("No Aplica");
                    }
                }
                break;

            case "updateDelete_XRb":
                String docAModificar = f1.documentoABuscarParaEditar_XATv.getText().toString();
                if (docAModificar.isEmpty()) break;

                if (f1.dateInUpdateAssignedInCaledarView_Int > 0) {
                    f1.DateOfDocument_Integer = f1.dateInUpdateAssignedInCaledarView_Int;
                    for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                        item.tipoTset_8FechaInicialMetodoEnA5(f1.DateOfDocument_Integer);
                        item.tipoTset_9FechaModificacionMetodoEnA5(
                                f1.dateInUpdate_XTv.getText().toString());
                    }
                } else {
                    f1.DateOfDocument_Integer = Integer.parseInt(
                            f1.dateInUpdate_XTv.getText().toString());
                    for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                        item.tipoTset_8FechaInicialMetodoEnA5(f1.DateOfDocument_Integer);
                    }
                }
                for (A3_2_TipoTransaccionesGetsYSets item : f1.listaDocumento_ArrayLTT) {
                    item.tipoTset_1DocumentoMetodoEnA5(docAModificar);
                }
                break;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 3. digitarFisicoVsSaldoConciliacion
    // ═══════════════════════════════════════════════════════════════
    public void digitarFisicoVsSaldoConciliacion() {
        try {
            String strFisico = f1.inputPhysicalVsAccounting_XEt.getText().toString();
            f1.$valorAc = strFisico.isEmpty() ? 0 : Integer.parseInt(strFisico);

            if (f1.$valorAc != 0) {
                f1.$diferenciaAConciliar_Integer = f1.$sAc - f1.$valorAc;
                f1.diferenciaAConciliar_XTv.setText("Dif.\n" + f1.$diferenciaAConciliar_Integer);
            } else {
                f1.$diferenciaAConciliar_Integer = 0;
                f1.diferenciaAConciliar_XTv.setText("");
            }

            if (!f1.cuentaConciliacion_XSp.getSelectedItem().toString().isEmpty()) {
                f1.dynamicQuerySumByAccountAcordingToArgument();
                f1.dynamicQueryByAllAccountAz();
                f1.$sAc = Integer.parseInt(String.valueOf(f1.sumaTransaccionesCuenta2));
            } else {
                f1.$sAc = 0;
            }

            f1.$dAc = f1.$sAc - f1.$valorAc;
            f1.sumarItemListaDocumento();
            f1.$adicionesAc = f1.$netoMC + f1.$dAc;
            f1.saldoCuentaAconciliar_XTv.setText("Saldo:\n" + f1.$sAc);

            if (f1.$mTP == 0 && f1.$mTN == 0) {
                f1.$quedaPorRegistrar = f1.$dAc;
            } else {
                f1.$quedaPorRegistrar = f1.$sAc - f1.$valorAc
                        - (f1.$mTP - f1.$mCP)
                        + (f1.$mTN * (-1) - f1.$mCN * (-1));
            }

            f1.controlACeroTotales_XTv.setText("" + f1.$netoT);

            if (f1.enModoModificacion) {
                f1.fabModificar.post(() -> {
                    if (f1.enModoModificacion
                            && f1.fabModificar.getVisibility() != View.VISIBLE) {
                        f1.fabModificar.setVisibility(View.VISIBLE);
                        f1.fabModificar.show();
                    }
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Error en digitarFisicoVsSaldoConciliacion", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // 4. primerRegistroAListaDocumentoCuentaConciliable
    // ═══════════════════════════════════════════════════════════════
    public void primerRegistroAListaDocumentoCuentaConciliable() {
        if (f1.listaDocumento_ArrayLTT.size() != 0) return;
        if (f1.$dAc == 0) return;

        String cuentaConciliable = f1.cuentaConciliacion_XSp.getSelectedItem().toString();
        if ("1 No conciliar".equals(cuentaConciliable)) return;

        f1.DateOfDocument_Integer = f1.dateCurrent_ArrayInteger[5];
        f1.dateCurrent_ArrayInteger = f1.metodosVarios_Class.fechasYHoras();

        f1.dynamicQueryAtributtesAccount();
        f1.dynamicQuerySumByAccountAcordingToArgument();
        f1.dynamicQueryByAllAccountAz();

        A3_2_TipoTransaccionesGetsYSets item =
                f1.calculator.construirItemRegistroInicial(
                        f1.$dAc,
                        cuentaConciliable,
                        f1.nuevoNumeroDocEnAdicionar_String,
                        A99_MetodosVarios.stringFechaYHora,
                        f1.DateOfDocument_Integer,
                        f1.atributosCuenta_ArrayS);

        if (item == null) return;

        f1.listaDocumento_ArrayLTT.add(item);
        f1.listaCuentasRevisionParaAdapterSpinner_ArrayListString.add(cuentaConciliable);
        f1.conectarListaCuentasRevisionConSpinner_arrayAdapterString.notifyDataSetChanged();
        f1.listaCuentasDeRevision_XSp.setAdapter(
                f1.conectarListaCuentasRevisionConSpinner_arrayAdapterString);

        Toast.makeText(f1.getActivity(), "Primer registro", Toast.LENGTH_SHORT).show();
    }

    // ═══════════════════════════════════════════════════════════════
    // 5. losDemasRegistrosAListaDocumento
    // ═══════════════════════════════════════════════════════════════
    public void losDemasRegistrosAListaDocumento() {
        f1.DateOfDocument_Integer = f1.dateCurrent_ArrayInteger[5];

        // Validaciones
        if (f1.valor_XEt.getText().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta el valor", Toast.LENGTH_SHORT).show();
            return;
        }
        if (f1.signo_XSp.getSelectedItem().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta el signo", Toast.LENGTH_SHORT).show();
            return;
        }
        if (f1.descripcion_XAtv.getText().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta la descripcion", Toast.LENGTH_SHORT).show();
            return;
        }
        if (f1.cuenta_XAtv.getText().toString().isEmpty()
                && f1.cuenta_XSp.getSelectedItem().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta la cuenta", Toast.LENGTH_SHORT).show();
            return;
        }

        f1.dateCurrent_ArrayInteger = f1.metodosVarios_Class.fechasYHoras();

        // Determinar cuenta

        String cuentaAlItemList = f1.enModoModificacion
                ? f1.cuenta_XSp.getSelectedItem().toString()
                : f1.cuenta_XAtv.getText().toString();

        if (cuentaAlItemList == null || cuentaAlItemList.isEmpty()) {
            Toast.makeText(f1.getActivity(), "Falta la cuenta", Toast.LENGTH_SHORT).show();
            return;
        }

        A23_QueryResult<String[]> obtenerAtributo =
                f1.a22QueryManager.queryAttributesByAccount(cuentaAlItemList);

        if (obtenerAtributo == null
                || obtenerAtributo.getAtributosCuenta() == null
                || obtenerAtributo.getAtributosCuenta().length == 0) {
            Toast.makeText(f1.getActivity(),
                    "Cuenta no encontrada: " + cuentaAlItemList, Toast.LENGTH_SHORT).show();
            return;
        }

        f1.atributosCuenta_ArrayS = obtenerAtributo.getAtributosCuenta();
        f1.cuentaDemasRegistros_ArrayS = new String[]{cuentaAlItemList};
        f1.dynamicQuerySumByAccountAcordingToArgument();
        f1.dynamicQueryByAllAccountAz();
// ✅ hasta aquí

        // Construir ítem via DocumentCalculator
        A3_2_TipoTransaccionesGetsYSets nuevoItem = f1.calculator.construirItemRegistro(
                f1.nuevoNumeroDocEnAdicionar_String,
                f1.listaDocumento_ArrayLTT.size() + 1,
                cuentaAlItemList,
                f1.signo_XSp.getSelectedItem().toString(),
                f1.valor_XEt.getText().toString(),
                f1.descripcion_XAtv.getText().toString(),
                A99_MetodosVarios.stringFechaYHora,
                f1.DateOfDocument_Integer,
                f1.atributosCuenta_ArrayS);

        if (nuevoItem == null) {
            Toast.makeText(f1.getActivity(),
                    "Error: Atributos de cuenta no disponibles",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        f1.listaDocumento_ArrayLTT.add(nuevoItem);

        try {
            f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                    new D_F1_AdaptadorCrudDocumento(
                            f1.getActivity(), f1.listaDocumento_ArrayLTT, null);
            f1.listaDocumento_XLv.setAdapter(
                    f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);
        } catch (Exception e) {
            Log.e(TAG, "Error setting adapter in losDemasRegistros", e);
        }

        f1.sumarItemListaDocumento();
        f1.clearViewValuesAreaRecords();

        int size = f1.listaDocumento_ArrayLTT.size();
        f1.consecutivoItemRegistro_XTv.setText(
                size > 0 ? "Item:\n" + size + "/" + size : "0/0");
    }

    // ═══════════════════════════════════════════════════════════════
    // 6. guardarModificacion
    // ═══════════════════════════════════════════════════════════════
    public void guardarModificacion() {
        if (f1.consecutivoRegistroAModificar_StringStatic == null
                || f1.consecutivoRegistroAModificar_StringStatic.isEmpty()) {
            Toast.makeText(f1.getActivity(),
                    "Error: No hay registro seleccionado", Toast.LENGTH_SHORT).show();
            return;
        }
        if (f1.valor_XEt.getText().toString().trim().isEmpty()) {
            Toast.makeText(f1.getActivity(),
                    "Por favor ingresa un valor", Toast.LENGTH_SHORT).show();
            f1.valor_XEt.requestFocus();
            return;
        }
        if (f1.cuenta_XSp.getSelectedItem() == null
                || f1.cuenta_XSp.getSelectedItem().toString().isEmpty()) {
            Toast.makeText(f1.getActivity(),
                    "Por favor selecciona una cuenta", Toast.LENGTH_SHORT).show();
            return;
        }

        // Buscar posición del registro a modificar
        int posicion = -1;
        for (int i = 0; i < f1.listaDocumento_ArrayLTT.size(); i++) {
            if (f1.listaDocumento_ArrayLTT.get(i).tipoT_2DocumentItems_String
                    .equals(f1.consecutivoRegistroAModificar_StringStatic)) {
                posicion = i;
                break;
            }
        }
        if (posicion == -1) {
            Toast.makeText(f1.getActivity(),
                    "Error: Registro no encontrado", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Integer valorNuevo = Integer.parseInt(
                    f1.valor_XEt.getText().toString().trim());
            String signoNuevo = f1.signo_XSp.getSelectedItem().toString();
            if ("-".equals(signoNuevo)) valorNuevo = valorNuevo * -1;

            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_5Value_Integer   = valorNuevo;
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_4Sign_String      = signoNuevo;
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_3Accout_String    =
                    f1.cuenta_XSp.getSelectedItem().toString();
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_6Description_String =
                    f1.descripcion_XAtv.getText().toString().trim();

            f1.valor_XEt.setText("");
            f1.descripcion_XAtv.setText("");
            f1.signo_XSp.setSelection(0);
            f1.cuenta_XSp.setSelection(0);
            f1.consecutivoItemRegistro_XTv.setText("0/0");
            f1.consecutivoRegistroAModificar_StringStatic = "";

            f1.finalizarModoModificacion();
            f1.procesarActualizacionCompleta();

            Toast.makeText(f1.getActivity(),
                    "✅ Registro modificado exitosamente", Toast.LENGTH_SHORT).show();

        } catch (NumberFormatException e) {
            Toast.makeText(f1.getActivity(),
                    "Error: El valor debe ser un número válido", Toast.LENGTH_SHORT).show();
            f1.valor_XEt.requestFocus();
        } catch (Exception e) {
            Toast.makeText(f1.getActivity(),
                    "Error al guardar: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error en guardarModificacion", e);
        }
    }
}