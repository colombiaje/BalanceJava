package B1_F1Support;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

import A1BASES.A12_InventarioHelper;
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
    // ⭐ CAMBIO — v12 tanda 3 (segundo fix, 29-sep): pasa de void a boolean (true = se guardó,
    // false = bloqueado por una cuenta con inventario — nada se guardó). Antes, aunque el
    // guardado se bloqueara, ExecuteButtonsUnit igual llamaba a continuación a
    // realizarOperacionesPostSeleccion(), que limpia la lista de ítems en memoria Y muestra
    // "Backup local, en Drive y documento actualizado" — un mensaje de ÉXITO engañoso
    // apareciendo justo después (o casi encima) del aviso real de bloqueo, y la lista de
    // ítems desapareciendo de la pantalla como si sí se hubiera guardado. Esto explica el
    // reporte de Jorge de "no sale el aviso... y no queda registrado en la BD": el aviso sí
    // se mostraba, pero quedaba tapado/opacado por el segundo mensaje de "éxito" que llegaba
    // enseguida, y la pantalla se limpiaba igual que en un guardado real. Con el valor de
    // retorno, ExecuteButtonsUnit ahora puede saltarse ese post-procesamiento cuando el
    // guardado fue bloqueado.
    public boolean baseParaGuardarEnLaEnBDConListaDocumento(String nombreDelRadioButton) {
        assignDocumentDate(nombreDelRadioButton);
        f1.renumerarItemsListaDocumento();

        // ⭐ CORRECCIÓN — v12 tanda 3 (segundo fix, 29-sep): la verificación se movió a su
        // propio método público (bloqueadoPorCuentaConInventario(), más abajo) para poder
        // llamarla TAMBIÉN desde ExecuteButtonsUnit ANTES de borrar las transacciones viejas
        // del documento en el flujo "Modificar documento" — ver el comentario completo en esa
        // función.
        if (bloqueadoPorCuentaConInventario()) {
            return false;
        }

        SQLiteDatabase db = f1.ayudante_Class.getWritableDatabase();
        db.beginTransaction();
        try {
            for (A3_2_TipoTransaccionesGetsYSets p : f1.listaDocumento_ArrayLTT) {
                String nombreCuentaParaGuardar = p.tipoTget_3CuentaMetodoEnA5();

                // ⭐ NUEVO — Fase 3 (parte A) de la reestructuración de BD: resolver
                // cuenta_id por nombre al momento de guardar, para que las transacciones
                // nuevas queden ligadas a "cuentas" desde su creación. Antes de este
                // cambio, cuenta_id solo se llenaba en el backfill de la migración de la
                // Fase 1 (versión 3); ninguna transacción nueva guardada desde entonces lo
                // recibía, porque este INSERT no lo incluía. No se toca c3_Cuenta ni ningún
                // otro comportamiento existente: esto solo agrega el dato en paralelo.
                Long cuentaIdParaGuardar = null;
                Cursor cCuentaId = db.rawQuery(
                        "SELECT cuenta_id FROM cuentas WHERE Cuenta = ?",
                        new String[]{nombreCuentaParaGuardar});
                if (cCuentaId.moveToFirst()) {
                    cuentaIdParaGuardar = cCuentaId.getLong(0);
                } else {
                    Log.w(TAG, "cuenta_id no encontrado para '" + nombreCuentaParaGuardar +
                            "' al guardar transacción — quedará con cuenta_id NULL, igual " +
                            "que antes de este cambio");
                }
                cCuentaId.close();

                // ⭐ NUEVO v11 — Fase 6 (parte C): tipo_cuenta_id se toma del OBJETO (no se
                // reconsulta aquí por nombre de cuenta, a diferencia de cuenta_id arriba). Es a
                // propósito: cuenta_id es un simple enlace de identidad y debe reflejar siempre
                // la cuenta real por nombre; tipo_cuenta_id es una FOTO de clasificación (mismo
                // criterio que c10_Grupo1/c11_Grupo2, que también se toman del objeto) — si se
                // reconsultara aquí en vez de leerla del objeto, un documento con VARIOS ítems se
                // reescribiría completo (ver eliminarTransacciones + reinserción en
                // ExecuteButtonsUnit) cada vez que se edita CUALQUIER ítem, y los ítems NO
                // tocados de ese mismo documento perderían su foto histórica y quedarían con la
                // clasificación actual de su cuenta — exactamente el efecto que la foto existe
                // para evitar. El objeto ya trae el valor correcto: para un ítem nuevo, desde
                // B11_DocumentCalculator (atributosCuenta[7]); para un ítem cargado de la BD
                // (edición/plantilla), desde A21_OptimizedQuery.mapTransactionFromCursor; y para
                // el ítem que sí cambió de cuenta en esta edición, desde el refresco en
                // guardarModificacion() (ver ahí).
                Long tipoCuentaIdParaGuardar = p.tipoTget_16TipoCuentaIdMetodoEnA5();

                // ⭐ NUEVO — v12 tanda 4: un ítem de una cuenta con inventario (viene marcado
                // con tipoTget_19ItemInventarioIdMetodoEnA5() != null, ver
                // mostrarDialogoRegistroInventario) se guarda distinto: no con el INSERT crudo
                // de siempre, sino con A12_InventarioHelper.guardarTransaccionConInventario, que
                // inserta "transacciones" y su fila correspondiente en "transacciones_inventario"
                // de forma atómica (con su propio db.beginTransaction()/setTransactionSuccessful
                // anidado dentro de esta transacción de todo el documento — Android soporta
                // transacciones anidadas: si cualquiera de las dos falla, se revierte TODO el
                // documento, no solo este ítem — regla de negocio #1 del documento de
                // especificación). c5_Valor NO se pone aquí: ese método lo calcula y lo
                // sobreescribe como unidades × precio_unitario, para que quede siempre
                // consistente (regla de negocio #2).
                //
                // bloqueadoPorCuentaConInventario() (llamado arriba, antes de abrir esta
                // transacción) ya garantiza que, si llegamos aquí con un ítem de inventario, es
                // uno NUEVO (nunca antes guardado) — nunca uno cargado de la BD para editar —
                // así que precioUnitarioEntrada (21) siempre es el valor correcto a usar tal
                // cual para una entrada, y siempre null para una salida (el helper calcula el
                // costo promedio vigente solo, en este mismo momento).
                if (p.tipoTget_19ItemInventarioIdMetodoEnA5() != null) {
                    ContentValues valoresTransaccion = new ContentValues();
                    valoresTransaccion.put("c1_Documento", p.tipoTget_1DocumentoMetodoEnA5());
                    valoresTransaccion.put("c2_ItemDoc", p.tipoTget_2ItemDocMetodoEnA5());
                    valoresTransaccion.put("c3_Cuenta", nombreCuentaParaGuardar);
                    valoresTransaccion.put("c4_Signo", p.tipoTget_4MasMenosMetodoEnA5());
                    valoresTransaccion.put("c6_Descripcion", p.tipoTget_6DescripcionMetodoEnA5());
                    valoresTransaccion.put("c7_FechaYhora", p.tipoTget_7FechaYHoraMetodoEnA5());
                    valoresTransaccion.put("c8_FechaInicial", p.tipoTget_8FechaInicialMetodoEnA5());
                    valoresTransaccion.put("c9_FechaModificacion", p.tipoTget_9FechaModificacionMetodoEnA5());
                    valoresTransaccion.put("c12_ColumnaDisponible", p.tipoTget_12ColumnaDisponibleMetodoEnA5());
                    valoresTransaccion.put("c13_ColumnaDisponible", p.tipoTget_13ColumnaDisponibleMetodoEnA5());
                    valoresTransaccion.put("cuenta_id", cuentaIdParaGuardar);
                    valoresTransaccion.put("tipo_cuenta_id", tipoCuentaIdParaGuardar);

                    try {
                        new A12_InventarioHelper().guardarTransaccionConInventario(
                                db,
                                valoresTransaccion,
                                p.tipoTget_19ItemInventarioIdMetodoEnA5(),
                                p.tipoTget_20UnidadesInventarioMetodoEnA5(),
                                p.tipoTget_21PrecioUnitarioInventarioMetodoEnA5());
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        // Error de negocio esperable (p.ej. una salida sin saldo suficiente si
                        // cambió entre agregar el ítem y guardar) — se relanza como Exception
                        // genérica para que el catch de más abajo revierta TODO el documento
                        // (nunca a medias) y muestre un mensaje, con este detalle específico
                        // adjunto en vez del genérico.
                        throw new RuntimeException(
                                "Inventario — \"" + nombreCuentaParaGuardar + "\": " + e.getMessage(), e);
                    }
                    continue;
                }

                // ⭐ CAMBIO — v11 tanda 3 (parte D): "transacciones" pierde c10_Grupo1/c11_Grupo2
                // (ver A1_1_AyudanteBD, migración v13) — se quitan de la lista de columnas y de
                // los VALUES; ya no se escriben (desde parte C solo se guardaba "" de todas
                // formas). p.tipoTget_10Grupo1MetodoEnA5()/p.tipoTget_11Grupo2MetodoEnA5() dejan
                // de leerse aquí.
                db.execSQL(
                        "INSERT INTO transacciones (" +
                                "c1_Documento, c2_ItemDoc, c3_Cuenta, c4_Signo, c5_Valor, " +
                                "c6_Descripcion, c7_FechaYhora, c8_FechaInicial, " +
                                "c9_FechaModificacion, " +
                                "c12_ColumnaDisponible, c13_ColumnaDisponible, cuenta_id, " +
                                "tipo_cuenta_id) " +
                                "VALUES ('" +
                                p.tipoTget_1DocumentoMetodoEnA5()          + "','" +
                                p.tipoTget_2ItemDocMetodoEnA5()            + "','" +
                                nombreCuentaParaGuardar                    + "','" +
                                p.tipoTget_4MasMenosMetodoEnA5()           + "','" +
                                p.tipoTget_5ValorMetodoEnA5()              + "','" +
                                p.tipoTget_6DescripcionMetodoEnA5()        + "','" +
                                p.tipoTget_7FechaYHoraMetodoEnA5()         + "','" +
                                p.tipoTget_8FechaInicialMetodoEnA5()       + "','" +
                                p.tipoTget_9FechaModificacionMetodoEnA5()  + "','" +
                                p.tipoTget_12ColumnaDisponibleMetodoEnA5() + "','" +
                                p.tipoTget_13ColumnaDisponibleMetodoEnA5() + "'," +
                                (cuentaIdParaGuardar != null ? cuentaIdParaGuardar : "NULL") + "," +
                                (tipoCuentaIdParaGuardar != null ? tipoCuentaIdParaGuardar : "NULL") +
                                ")"
                );
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Log.e(TAG, "Error inserting transactions", e);
            // ⭐ CAMBIO — v12 tanda 4: si el mensaje trae detalle (ver el guardado de ítems de
            // inventario arriba), se muestra tal cual en vez del genérico de siempre — sigue
            // siendo Toast, no AlertDialog, porque este es un error inesperado de guardado (no
            // el aviso de bloqueo, que sí es AlertDialog desde la tanda 3).
            String detalle = e.getMessage();
            Toast.makeText(f1.getActivity(),
                    (detalle != null && !detalle.isEmpty())
                            ? "Error al guardar: " + detalle
                            : "Error al guardar las transacciones",
                    Toast.LENGTH_LONG).show();
        } finally {
            db.endTransaction();
            db.close();
        }

        f1.numerarDocumentoConsecutivo();
        f1.numeroConsecutivoDocEnEdicion_XTv.setText(f1.documentoRecibido_Resultado_String);
        f1.metodosVarios_Class.fechasYHoras();
        return true;
    }

    // ═══════════════════════════════════════════════════════════════
    // 1b. bloqueadoPorCuentaConInventario
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — v12 tanda 3: este formulario (el de siempre) todavía no sabe pedir
    // item/unidades/precio_unitario para una cuenta con con_inventario = 1 — eso llega en
    // la tanda 4, que va a conectar aquí la lógica ya lista y probada de
    // A12_InventarioHelper (tanda 2). Mientras tanto, si CUALQUIER ítem de la lista actual
    // del documento apunta a una cuenta con inventario, se bloquea el guardado — nunca a
    // medias — para que nunca pueda quedar una transacción sin su fila correspondiente en
    // transacciones_inventario.
    // ⭐ CORRECCIÓN — v12 tanda 3 (fix, 28-sep): se agrega .trim() al nombre antes de
    // comparar contra "cuentas.Cuenta" — c3_Cuenta se guarda tal cual viene del campo de
    // texto de este formulario (a diferencia de F2_Cuentas.registrarNuevas(), que SÍ recorta
    // espacios al crear la cuenta), así que un espacio de más al escribir o pegar el nombre
    // de la cuenta en ESTE formulario hacía que la comparación exacta no encontrara la
    // cuenta y el bloqueo se saltara en silencio.
    // ⭐ CORRECCIÓN — v12 tanda 3 (SEGUNDO fix, 29-sep): dos cambios más, a partir de la
    // segunda ronda de pruebas de Jorge (el aviso seguía sin verse y las transacciones
    // seguían sin registrarse):
    //   1) El aviso pasa de Toast a AlertDialog. Un Toast desaparece solo a los pocos
    //      segundos y es fácil perdérselo — con un diálogo que hay que cerrar a propósito,
    //      es imposible que pase inadvertido.
    //   2) Este método se EXTRAE de baseParaGuardarEnLaEnBDConListaDocumento() a su propia
    //      función pública para poder llamarlo, desde ExecuteButtonsUnit, ANTES de que el
    //      flujo "Modificar documento" borre las transacciones viejas del documento
    //      (A1_2_OperacionesBD.eliminarTransacciones()). Antes de este cambio, el orden real
    //      era: borrar TODO lo viejo del documento → intentar reinsertar la lista completa →
    //      SI esa reinserción se bloqueaba por una cuenta con inventario, el documento
    //      quedaba con sus transacciones viejas ya borradas y nada nuevo en su lugar —
    //      pérdida real de datos de ese documento, no solo "no se guardó el cambio nuevo".
    //      Esto probablemente explica lo que Jorge reportó como "no se guarda nada": si
    //      estaba editando un documento existente (no creando uno nuevo) y alguno de sus
    //      ítems apuntaba a una cuenta con inventario, el documento completo se vaciaba.
    // Se agrega también un try/catch alrededor de toda la verificación: si algo inesperado
    // falla al consultar con_inventario (por ejemplo, en un dispositivo donde la migración
    // no corrió como se esperaba), se bloquea el guardado POR SEGURIDAD en vez de dejar
    // pasar la transacción sin verificar, y se avisa con un mensaje claro en vez de fallar
    // en silencio.
    // ⭐ CAMBIO — v12 tanda 4: la tanda 4 ya conecta el formulario nuevo (diálogo de
    // artículo/unidades/precio, ver mostrarDialogoRegistroInventario) — así que este bloqueo
    // deja de ser un bloqueo TOTAL de cualquier ítem de una cuenta con inventario, y pasa a
    // cubrir 2 casos puntuales que SÍ siguen sin soportarse:
    //   1) Un ítem de una cuenta con inventario que llegó al guardado SIN pasar por ese
    //      diálogo (tipoTget_19ItemInventarioIdMetodoEnA5() == null) — no debería poder pasar
    //      desde la UI ahora que losDemasRegistrosAListaDocumento desvía al diálogo, pero se
    //      deja el bloqueo como red de seguridad (mismo espíritu que el resto de este método).
    //   2) Un ítem de inventario que YA estaba guardado en la BD antes de abrir este documento
    //      para modificarlo (tipoTget_15TransaccionIdMetodoEnA5() != null, es decir, viene
    //      cargado — ver A21_OptimizedQuery.mapTransactionFromCursor). "Modificar documento"
    //      borra TODAS las transacciones del documento y reinserta la lista completa (ver
    //      ExecuteButtonsUnit/baseParaGuardarEnLaEnBDConListaDocumento) — para una entrada esto
    //      sería seguro (mismo precio de siempre), pero para una SALIDA, volver a calcularle el
    //      costo promedio en el momento de reinsertar podría no coincidir con lo que se calculó
    //      la primera vez, reescribiendo silenciosamente un costo histórico. Editar documentos
    //      que ya tienen movimientos de inventario queda deliberadamente pendiente para una
    //      tanda aparte (hay que decidir primero cómo debe comportarse esa edición) — por ahora
    //      se bloquea con un mensaje claro en vez de arriesgar el dato.
    public boolean bloqueadoPorCuentaConInventario() {
        for (A3_2_TipoTransaccionesGetsYSets p : f1.listaDocumento_ArrayLTT) {
            if (p.tipoTget_19ItemInventarioIdMetodoEnA5() != null
                    && p.tipoTget_15TransaccionIdMetodoEnA5() != null) {
                Log.i(TAG, "Bloqueo de inventario ACTIVADO: el ítem de \"" +
                        p.tipoTget_3CuentaMetodoEnA5() + "\" (transaccion_id " +
                        p.tipoTget_15TransaccionIdMetodoEnA5() + ") ya estaba guardado — " +
                        "editar documentos con movimientos de inventario todavía no está " +
                        "soportado.");
                new AlertDialog.Builder(f1.getActivity())
                        .setTitle("Documento con inventario")
                        .setMessage("Este documento ya tiene un movimiento guardado sobre \"" +
                                p.tipoTget_3CuentaMetodoEnA5() + "\" (cuenta con inventario) — " +
                                "modificar documentos que ya tienen movimientos de inventario " +
                                "todavía está en construcción, no se puede guardar aquí por " +
                                "ahora.")
                        .setPositiveButton("Entendido", null)
                        .setCancelable(true)
                        .show();
                return true;
            }
        }

        SQLiteDatabase db = f1.ayudante_Class.getWritableDatabase();
        try {
            for (A3_2_TipoTransaccionesGetsYSets p : f1.listaDocumento_ArrayLTT) {
                if (p.tipoTget_19ItemInventarioIdMetodoEnA5() != null) {
                    // Ya pasó por el diálogo nuevo — este ítem sabe guardarse (ver el guardado
                    // en baseParaGuardarEnLaEnBDConListaDocumento), no se bloquea.
                    continue;
                }
                String nombreCuentaAVerificar = p.tipoTget_3CuentaMetodoEnA5();
                String nombreCuentaAVerificarRecortado =
                        nombreCuentaAVerificar == null ? null : nombreCuentaAVerificar.trim();
                Cursor cConInventario = db.rawQuery(
                        "SELECT con_inventario FROM cuentas WHERE Cuenta = ?",
                        new String[]{nombreCuentaAVerificarRecortado});
                boolean esConInventario = false;
                try {
                    if (cConInventario.moveToFirst() && !cConInventario.isNull(0)) {
                        esConInventario = cConInventario.getInt(0) != 0;
                    } else {
                        Log.w(TAG, "Bloqueo de inventario: no se encontró la cuenta \"" +
                                nombreCuentaAVerificarRecortado + "\" al verificar con_inventario " +
                                "— si esta cuenta SÍ existe y SÍ tiene inventario, revisar si el " +
                                "nombre guardado en el ítem del documento no coincide exactamente " +
                                "(mayúsculas/espacios) con el de \"cuentas\".");
                    }
                } finally {
                    cConInventario.close();
                }
                if (esConInventario) {
                    Log.i(TAG, "Bloqueo de inventario ACTIVADO para la cuenta \"" +
                            nombreCuentaAVerificarRecortado + "\" — se muestra el diálogo y no " +
                            "se guarda nada.");
                    new AlertDialog.Builder(f1.getActivity())
                            .setTitle("Cuenta con inventario")
                            .setMessage("\"" + nombreCuentaAVerificarRecortado + "\" maneja " +
                                    "inventario, y este ítem no pasó por el registro de " +
                                    "inventario (artículo/unidades/precio) — no se puede " +
                                    "guardar así.")
                            .setPositiveButton("Entendido", null)
                            .setCancelable(true)
                            .show();
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error verificando cuentas con inventario antes de guardar", e);
            Toast.makeText(f1.getActivity(),
                    "No se pudo verificar si alguna cuenta maneja inventario — por seguridad, " +
                            "no se guardó nada. Detalle: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            return true;
        } finally {
            db.close();
        }
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

        // ⭐ NUEVO — v12 tanda 4: si la cuenta elegida maneja inventario (índice 8 del arreglo,
        // ver A22_QueryManager.queryAttributesByAccount), el formulario de siempre no alcanza
        // — hace falta además el artículo, las unidades y el precio unitario (documento de
        // especificación, sección 4). En vez de agregar el ítem de una vez con lo que hay en
        // pantalla, se abre un diálogo a pedirlos (misma decisión que Jorge ya aprobó para el
        // bloqueo de la tanda 3: un diálogo, sin tocar el formulario existente) y es ESE
        // diálogo, al confirmar, el que arma y agrega el ítem — ver
        // mostrarDialogoRegistroInventario más abajo.
        boolean cuentaConInventario = f1.atributosCuenta_ArrayS.length > 8
                && "1".equals(f1.atributosCuenta_ArrayS[8]);
        if (cuentaConInventario) {
            mostrarDialogoRegistroInventario(cuentaAlItemList, f1.atributosCuenta_ArrayS);
            return;
        }

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

        agregarItemAListaYRefrescarUI(nuevoItem);
    }

    // ═══════════════════════════════════════════════════════════════
    // 5b. agregarItemAListaYRefrescarUI
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — v12 tanda 4: extraído de la cola de losDemasRegistrosAListaDocumento (sin
    // cambiar nada de lo que hacía ahí) para poder reusarlo también desde
    // mostrarDialogoRegistroInventario, al confirmar el diálogo de artículo/unidades/precio.
    private void agregarItemAListaYRefrescarUI(A3_2_TipoTransaccionesGetsYSets nuevoItem) {
        f1.listaDocumento_ArrayLTT.add(nuevoItem);

        try {
            f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT =
                    new D_F1_AdaptadorCrudDocumento(
                            f1.getActivity(), f1.listaDocumento_ArrayLTT, null);
            f1.listaDocumento_XLv.setAdapter(
                    f1.conexionListDocumentForGeneralWithListView_Adaptador1_TipoT);
        } catch (Exception e) {
            Log.e(TAG, "Error setting adapter in agregarItemAListaYRefrescarUI", e);
        }

        f1.sumarItemListaDocumento();
        f1.clearViewValuesAreaRecords();

        int size = f1.listaDocumento_ArrayLTT.size();
        f1.consecutivoItemRegistro_XTv.setText(
                size > 0 ? "Item:\n" + size + "/" + size : "0/0");
    }

    // ═══════════════════════════════════════════════════════════════
    // 5c. mostrarDialogoRegistroInventario
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — v12 tanda 4: pide artículo, unidades y precio unitario para un ítem sobre una
    // cuenta con inventario, y al confirmar arma el A3_2_TipoTransaccionesGetsYSets (con los 3
    // campos nuevos: 19 item_id, 20 unidades, 21 precio_unitario) y lo agrega a la lista del
    // documento, exactamente como losDemasRegistrosAListaDocumento hace para una cuenta normal.
    // El signo (entrada/salida) ya viene elegido en signo_XSp ANTES de llegar aquí — este
    // diálogo solo pide la magnitud de las unidades, no su signo, para no pedir el mismo dato
    // dos veces de forma contradictoria.
    //
    // Precio unitario:
    // - Entrada (signo "+"): el usuario lo digita aquí — obligatorio, mayor que 0.
    // - Salida (signo "-"): NUNCA lo pide — se muestra de solo lectura el costo promedio
    //   ponderado vigente del artículo (A12_InventarioHelper.calcularCostoPromedioPonderado),
    //   tal como Jorge confirmó. Este valor es solo una VISTA PREVIA para calcular el monto
    //   mostrado en la lista del documento antes de guardar: si el mismo documento (todavía sin
    //   guardar) tiene más de un movimiento del mismo artículo, el costo real que se guarda al
    //   final para cada salida se recalcula en el momento real de guardar, en orden — así que
    //   puede diferir de esta vista previa en ese caso puntual (ver el guardado en
    //   baseParaGuardarEnLaEnBDConListaDocumento, que por eso vuelve a pasar null como precio
    //   para toda salida NUEVA, nunca el valor mostrado aquí).
    private void mostrarDialogoRegistroInventario(String cuentaAlItemList, String[] atributosCuenta) {
        Long cuentaId = parseLongSeguro(atributosCuenta.length > 5 ? atributosCuenta[5] : null);
        if (cuentaId == null) {
            Toast.makeText(f1.getActivity(),
                    "No se pudo determinar la cuenta para el inventario de \"" +
                            cuentaAlItemList + "\"", Toast.LENGTH_LONG).show();
            return;
        }

        boolean esSalida = "-".equals(f1.signo_XSp.getSelectedItem().toString());

        List<A12_InventarioHelper.ItemInventario> items;
        SQLiteDatabase dbLectura = f1.ayudante_Class.getReadableDatabase();
        try {
            items = new A12_InventarioHelper().listarItemsActivosPorCuenta(dbLectura, cuentaId);
        } catch (Exception e) {
            Log.e(TAG, "Error listando artículos de inventario", e);
            Toast.makeText(f1.getActivity(),
                    "Error al consultar los artículos de \"" + cuentaAlItemList + "\": " +
                            e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        } finally {
            dbLectura.close();
        }

        if (items.isEmpty()) {
            new AlertDialog.Builder(f1.getActivity())
                    .setTitle("\"" + cuentaAlItemList + "\" no tiene artículos")
                    .setMessage("Esta cuenta maneja inventario pero todavía no tiene ningún " +
                            "artículo dado de alta — da de alta al menos uno para poder " +
                            "registrar transacciones aquí.")
                    .setPositiveButton("Crear artículo", (dialog, which) ->
                            mostrarDialogoNuevoArticulo(cuentaAlItemList, atributosCuenta, cuentaId))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }

        int paddingPx = (int) (16 * f1.getResources().getDisplayMetrics().density);

        LinearLayout contenedor = new LinearLayout(f1.getActivity());
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        List<String> nombresParaSpinner = new ArrayList<>();
        for (A12_InventarioHelper.ItemInventario item : items) {
            nombresParaSpinner.add(item.nombre);
        }
        final String OPCION_CREAR_NUEVO = "+ Crear nuevo artículo…";
        nombresParaSpinner.add(OPCION_CREAR_NUEVO);

        TextView etiquetaArticulo = new TextView(f1.getActivity());
        etiquetaArticulo.setText("Artículo");
        contenedor.addView(etiquetaArticulo);

        Spinner articuloSpinner = new Spinner(f1.getActivity());
        ArrayAdapter<String> articuloAdapter = new ArrayAdapter<>(f1.getActivity(),
                android.R.layout.simple_spinner_dropdown_item, nombresParaSpinner);
        articuloSpinner.setAdapter(articuloAdapter);
        contenedor.addView(articuloSpinner);

        TextView etiquetaUnidades = new TextView(f1.getActivity());
        etiquetaUnidades.setText("Unidades" +
                (esSalida ? " (salida — solo la cantidad, sin signo)" : " (entrada)"));
        contenedor.addView(etiquetaUnidades);

        EditText unidadesEt = new EditText(f1.getActivity());
        unidadesEt.setHint("Unidades");
        unidadesEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        contenedor.addView(unidadesEt);

        TextView etiquetaPrecio = new TextView(f1.getActivity());
        etiquetaPrecio.setText(esSalida
                ? "Precio unitario (costo promedio vigente — automático)"
                : "Precio unitario (COP)");
        contenedor.addView(etiquetaPrecio);

        EditText precioEt = new EditText(f1.getActivity());
        precioEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (esSalida) {
            precioEt.setEnabled(false);
            precioEt.setFocusable(false);
        } else {
            precioEt.setHint("Precio unitario");
        }
        contenedor.addView(precioEt);

        TextView montoCalculadoTv = new TextView(f1.getActivity());
        montoCalculadoTv.setText("Monto: $ 0");
        montoCalculadoTv.setGravity(Gravity.END);
        contenedor.addView(montoCalculadoTv);

        SQLiteDatabase dbParaPromedio = f1.ayudante_Class.getReadableDatabase();
        Runnable actualizarMonto = () -> {
            try {
                long unidades = unidadesEt.getText().toString().trim().isEmpty()
                        ? 0 : Long.parseLong(unidadesEt.getText().toString().trim());
                long precio;
                if (esSalida) {
                    precio = Long.parseLong(
                            precioEt.getText().toString().isEmpty() ? "0"
                                    : precioEt.getText().toString());
                } else {
                    precio = precioEt.getText().toString().trim().isEmpty()
                            ? 0 : Long.parseLong(precioEt.getText().toString().trim());
                }
                montoCalculadoTv.setText("Monto: $ " + (unidades * precio));
            } catch (NumberFormatException e) {
                montoCalculadoTv.setText("Monto: $ 0");
            }
        };

        TextWatcher recalcularAlEscribir = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { actualizarMonto.run(); }
            @Override public void afterTextChanged(Editable e) {}
        };
        unidadesEt.addTextChangedListener(recalcularAlEscribir);
        precioEt.addTextChangedListener(recalcularAlEscribir);

        // Al elegir un artículo (o cambiar de uno a otro), si es salida se consulta y se
        // muestra su costo promedio vigente; si es "+ Crear nuevo artículo…", queda pendiente
        // de resolver al confirmar el diálogo (ver el positive button más abajo).
        articuloSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!esSalida || position >= items.size()) return;
                try {
                    long costoPromedio = new A12_InventarioHelper()
                            .calcularCostoPromedioPonderado(dbParaPromedio, items.get(position).itemId);
                    precioEt.setText(String.valueOf(costoPromedio));
                } catch (IllegalStateException e) {
                    precioEt.setText("");
                    montoCalculadoTv.setText("Monto: $ 0 (sin saldo para vender)");
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        AlertDialog dialogo = new AlertDialog.Builder(f1.getActivity())
                .setTitle("Inventario — \"" + cuentaAlItemList + "\"")
                .setView(contenedor)
                .setCancelable(true)
                .setOnDismissListener(d -> dbParaPromedio.close())
                .setPositiveButton("Agregar", null) // se sobreescribe abajo para no cerrar en error
                .setNegativeButton("Cancelar", (d, which) -> d.dismiss())
                .create();

        dialogo.setOnShowListener(dialogInterface -> dialogo
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    int posicionSeleccionada = articuloSpinner.getSelectedItemPosition();

                    if (posicionSeleccionada == items.size()) {
                        // "+ Crear nuevo artículo…" — se abre el sub-diálogo y se cierra este;
                        // al terminar de crear el artículo se vuelve a abrir este mismo diálogo
                        // (ya con el artículo nuevo en la lista), para no duplicar la lógica de
                        // arriba.
                        dialogo.dismiss();
                        mostrarDialogoNuevoArticulo(cuentaAlItemList, atributosCuenta, cuentaId);
                        return;
                    }

                    String unidadesTexto = unidadesEt.getText().toString().trim();
                    if (unidadesTexto.isEmpty()) {
                        Toast.makeText(f1.getActivity(), "Falta la cantidad de unidades",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    long unidadesMagnitud;
                    try {
                        unidadesMagnitud = Long.parseLong(unidadesTexto);
                    } catch (NumberFormatException e) {
                        Toast.makeText(f1.getActivity(), "Unidades inválidas",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (unidadesMagnitud <= 0) {
                        Toast.makeText(f1.getActivity(), "Las unidades deben ser mayores que 0",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    A12_InventarioHelper.ItemInventario itemElegido = items.get(posicionSeleccionada);
                    long unidadesConSigno = esSalida ? -unidadesMagnitud : unidadesMagnitud;

                    Long precioParaEntrada; // lo que se guardará en el objeto (19/20/21)
                    long precioParaVistaPrevia; // solo para el monto que se muestra en la lista
                    if (esSalida) {
                        try {
                            precioParaVistaPrevia = new A12_InventarioHelper()
                                    .calcularCostoPromedioPonderado(dbParaPromedio, itemElegido.itemId);
                        } catch (IllegalStateException e) {
                            Toast.makeText(f1.getActivity(),
                                    "\"" + itemElegido.nombre + "\" no tiene saldo disponible " +
                                            "para vender.", Toast.LENGTH_LONG).show();
                            return;
                        }
                        // Se pasa null a propósito: el precio real de toda salida NUEVA se
                        // calcula en el momento real de guardar (ver el comentario de este
                        // método más arriba y baseParaGuardarEnLaEnBDConListaDocumento).
                        precioParaEntrada = null;
                    } else {
                        String precioTexto = precioEt.getText().toString().trim();
                        if (precioTexto.isEmpty()) {
                            Toast.makeText(f1.getActivity(), "Falta el precio unitario",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        long precioIngresado;
                        try {
                            precioIngresado = Long.parseLong(precioTexto);
                        } catch (NumberFormatException e) {
                            Toast.makeText(f1.getActivity(), "Precio unitario inválido",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (precioIngresado <= 0) {
                            Toast.makeText(f1.getActivity(),
                                    "El precio unitario debe ser mayor que 0", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        precioParaVistaPrevia = precioIngresado;
                        precioParaEntrada = precioIngresado;
                    }

                    long montoVistaPrevia = unidadesMagnitud * precioParaVistaPrevia;

                    A3_2_TipoTransaccionesGetsYSets nuevoItem = f1.calculator.construirItemRegistro(
                            f1.nuevoNumeroDocEnAdicionar_String,
                            f1.listaDocumento_ArrayLTT.size() + 1,
                            cuentaAlItemList,
                            f1.signo_XSp.getSelectedItem().toString(),
                            String.valueOf(montoVistaPrevia),
                            f1.descripcion_XAtv.getText().toString(),
                            A99_MetodosVarios.stringFechaYHora,
                            f1.DateOfDocument_Integer,
                            atributosCuenta);

                    if (nuevoItem == null) {
                        Toast.makeText(f1.getActivity(),
                                "Error: Atributos de cuenta no disponibles", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    nuevoItem.tipoTset_19ItemInventarioIdMetodoEnA5(itemElegido.itemId);
                    nuevoItem.tipoTset_20UnidadesInventarioMetodoEnA5(unidadesConSigno);
                    nuevoItem.tipoTset_21PrecioUnitarioInventarioMetodoEnA5(precioParaEntrada);

                    agregarItemAListaYRefrescarUI(nuevoItem);
                    dialogo.dismiss();
                }));

        dialogo.show();
    }

    // ═══════════════════════════════════════════════════════════════
    // 5d. mostrarDialogoNuevoArticulo
    // ═══════════════════════════════════════════════════════════════
    // ⭐ NUEVO — v12 tanda 4: crear un artículo nuevo "en el momento" desde el selector del
    // registro de transacciones (documento de especificación, sección 4: "con opción de crear
    // uno nuevo en el momento"). Mismo diálogo simple que F2_Cuentas.mostrarDialogoPrimerArticulo
    // (nombre obligatorio, unidad opcional). Al guardar, se vuelve a abrir
    // mostrarDialogoRegistroInventario para que el usuario complete unidades/precio con el
    // artículo recién creado ya disponible en el selector — evita duplicar esa lógica aquí.
    private void mostrarDialogoNuevoArticulo(String cuentaAlItemList, String[] atributosCuenta, long cuentaId) {
        int paddingPx = (int) (16 * f1.getResources().getDisplayMetrics().density);
        LinearLayout contenedor = new LinearLayout(f1.getActivity());
        contenedor.setOrientation(LinearLayout.VERTICAL);
        contenedor.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);

        final EditText nombreArticuloEt = new EditText(f1.getActivity());
        nombreArticuloEt.setHint("Nombre del artículo (obligatorio)");
        contenedor.addView(nombreArticuloEt);

        final EditText unidadArticuloEt = new EditText(f1.getActivity());
        unidadArticuloEt.setHint("Unidad (opcional)");
        contenedor.addView(unidadArticuloEt);

        new AlertDialog.Builder(f1.getActivity())
                .setTitle("Nuevo artículo de \"" + cuentaAlItemList + "\"")
                .setView(contenedor)
                .setCancelable(true)
                .setPositiveButton("Guardar artículo", (dialog, which) -> {
                    String nombre = nombreArticuloEt.getText().toString().trim();
                    if (nombre.isEmpty()) {
                        Toast.makeText(f1.getActivity(), "Falta el nombre del artículo",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String unidad = unidadArticuloEt.getText().toString().trim();
                    SQLiteDatabase db = f1.ayudante_Class.getWritableDatabase();
                    try {
                        new A12_InventarioHelper().insertarItemInventario(
                                db, cuentaId, nombre, unidad.isEmpty() ? null : unidad);
                        Toast.makeText(f1.getActivity(), "Artículo \"" + nombre + "\" guardado",
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error al guardar artículo nuevo de inventario", e);
                        Toast.makeText(f1.getActivity(),
                                "Error al guardar el artículo: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
                        return;
                    } finally {
                        db.close();
                    }
                    mostrarDialogoRegistroInventario(cuentaAlItemList, atributosCuenta);
                })
                .setNegativeButton("Cancelar", (dialog, which) ->
                        mostrarDialogoRegistroInventario(cuentaAlItemList, atributosCuenta))
                .show();
    }

    private Long parseLongSeguro(String valor) {
        if (valor == null || valor.isEmpty()) return null;
        try {
            return Long.valueOf(valor);
        } catch (NumberFormatException e) {
            return null;
        }
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

        // ⭐ NUEVO — v12 tanda 4: este método reasigna cuenta/valor/signo/descripción a mano,
        // sin pasar por mostrarDialogoRegistroInventario — así que no sabe actualizar
        // item_id/unidades/precio_unitario si el ítem es de inventario (podría, por ejemplo,
        // cambiarle la cuenta y dejarle el item_id de la cuenta VIEJA). Se bloquea con un
        // mensaje claro en vez de arriesgar esa inconsistencia; para cambiar un ítem de
        // inventario, se elimina de la lista y se vuelve a agregar desde el diálogo.
        if (f1.listaDocumento_ArrayLTT.get(posicion).tipoTget_19ItemInventarioIdMetodoEnA5() != null) {
            Toast.makeText(f1.getActivity(),
                    "Este ítem es de una cuenta con inventario — no se puede editar así. " +
                            "Elimínalo de la lista y agrégalo de nuevo desde el diálogo de " +
                            "inventario.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            Integer valorNuevo = Integer.parseInt(
                    f1.valor_XEt.getText().toString().trim());
            String signoNuevo = f1.signo_XSp.getSelectedItem().toString();
            if ("-".equals(signoNuevo)) valorNuevo = valorNuevo * -1;

            String cuentaNueva = f1.cuenta_XSp.getSelectedItem().toString();

            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_5Value_Integer   = valorNuevo;
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_4Sign_String      = signoNuevo;
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_3Accout_String    = cuentaNueva;
            f1.listaDocumento_ArrayLTT.get(posicion).tipoT_6Description_String =
                    f1.descripcion_XAtv.getText().toString().trim();

            // Refrescar la clasificación para la cuenta NUEVA — si no se hace,
            // el registro se queda con la clasificación de la cuenta
            // ORIGINAL con la que se creó, y con el tiempo una misma cuenta
            // termina con transacciones clasificadas de forma inconsistente
            // (causa raíz de la duplicación vista en el informe de cuentas).
            // ⭐ NUEVO v11 — Fase 6 (parte C): ya no se refresca Grupo1/Grupo2 con el valor
            // real de la cuenta nueva (atributos[2]/[3]) — se deja en "" igual que en un
            // ítem recién creado (ver B11_DocumentCalculator). El refresco que de verdad
            // importa para la Auditoría de Clasificación es el de tipo_cuenta_id, justo
            // abajo, que no se toca.
            A23_QueryResult<String[]> atributosCuentaNueva =
                    f1.a22QueryManager.queryAttributesByAccount(cuentaNueva);
            if (atributosCuentaNueva != null
                    && atributosCuentaNueva.getAtributosCuenta() != null
                    && atributosCuentaNueva.getAtributosCuenta().length >= 4) {
                String[] atributos = atributosCuentaNueva.getAtributosCuenta();
                f1.listaDocumento_ArrayLTT.get(posicion)
                        .tipoTset_10Grupo1MetodoEnA5("");
                f1.listaDocumento_ArrayLTT.get(posicion)
                        .tipoTset_11Grupo2MetodoEnA5("");

                // ⭐ NUEVO v11 — Fase 6 (parte C): mismo refresco de arriba, pero para
                // tipo_cuenta_id (índice 7, disponible desde la v10). Sin esto, el ítem editado
                // se reinsertaría (ver el comentario en el INSERT de
                // baseParaGuardarEnLaEnBDConListaDocumento) con la foto de tipo_cuenta_id de la
                // cuenta ORIGINAL con la que se creó, en vez de la cuenta nueva elegida en esta
                // edición — mismo riesgo que el de Grupo1/Grupo2 de arriba.
                Long tipoCuentaIdNuevo = null;
                if (atributos.length >= 8 && atributos[7] != null && !atributos[7].isEmpty()) {
                    try {
                        tipoCuentaIdNuevo = Long.valueOf(atributos[7]);
                    } catch (NumberFormatException e) {
                        Log.w(TAG, "tipo_cuenta_id no numérico en atributos[7]: " + atributos[7]);
                    }
                }
                f1.listaDocumento_ArrayLTT.get(posicion)
                        .tipoTset_16TipoCuentaIdMetodoEnA5(tipoCuentaIdNuevo);
            }

            f1.valor_XEt.setText("");
            f1.descripcion_XAtv.setText("");
            f1.signo_XSp.setSelection(0);
            f1.cuenta_XSp.setSelection(0);
            f1.consecutivoItemRegistro_XTv.setText("0/0");
            f1.consecutivoRegistroAModificar_StringStatic = "";

            f1.finalizarModoModificacion();
            f1.procesarActualizacionCompleta();

            // La cuenta pudo haber cambiado (o su Grupo1/Grupo2 recién se
            // refrescó arriba) — es el punto más probable donde una
            // transacción desalineada se corrige, así que el badge del
            // botón ✔️ de Auditoría debe reflejarlo de inmediato, sin
            // esperar a que el usuario salga y vuelva a entrar.
            f1.actualizarBadgeAuditoria();

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