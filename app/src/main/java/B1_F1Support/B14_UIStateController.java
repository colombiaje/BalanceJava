package B1_F1Support;

import android.graphics.Color;
import android.view.View;

import com.jj.appbalancev31.R;

import B_FRAGMENTS.F1_CrudDocumento;

/**
 * UIStateController — control de visibilidad de áreas para F1_CrudDocumento.
 *
 * REGLA: esta clase SOLO cambia visibilidad, colores de fondo y llama a
 * resetearSpinnerCuentasRevision(). No contiene lógica de negocio ni queries.
 *
 * Recibe el fragment como referencia y accede a sus vistas (package-private).
 * El fragment instancia esta clase una sola vez y delega todos los cambios
 * de visibilidad a través de ella.
 */
public class B14_UIStateController {

    // Referencia al fragment — acceso a todas sus vistas (package-private)
    private final F1_CrudDocumento f1;

    public B14_UIStateController(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    // ═════════════════════════════════════════════════════════════
    // 1. setVisibilityGoneTodo
    // Oculta TODAS las áreas — punto de partida antes de mostrar cualquier sección.
    // ═════════════════════════════════════════════════════════════
    public void setVisibilityGoneTodo() {
        f1.areaCreateNew_XGl.setVisibility(View.GONE);
        f1.areaTemplate_XGl.setVisibility(View.GONE);
        f1.areaUpdateAndDelete_XGl.setVisibility(View.GONE);
        f1.areaConciliacionYRegistro_XGl.setVisibility(View.GONE);
        f1.envolventeEncabezadoItemLista_XLl.setVisibility(View.GONE);
        f1.areaListaRevisarCuentas_XGl.setVisibility(View.GONE);
        f1.areaButtons_XLL.setVisibility(View.GONE);
        f1.resetearSpinnerCuentasRevision();
    }

    // ═════════════════════════════════════════════════════════════
    // 2. mostrarAreaCorrespondiente
    // Router central: según el radioButtonId activo, muestra el área correcta.
    // ═════════════════════════════════════════════════════════════
    public void mostrarAreaCorrespondiente(int radioButtonId) {
        f1.protegerSpinnersVacios();

        if (radioButtonId == R.id.create_XRb) {
            setvisibleCreateDespuesDeEditar();
            f1.areaButtons_XLL.setVisibility(View.VISIBLE);
        } else if (radioButtonId == R.id.template_XRb) {
            setVisibilityVisibleAreasForTemplate();
            f1.areaButtons_XLL.setVisibility(View.VISIBLE);
        } else if (radioButtonId == R.id.updateDelete_XRb) {
            setVisibilityVisibleAreasForUpdateAndDelete();
            f1.areaButtons_XLL.setVisibility(View.VISIBLE);
        }
    }

    // ═════════════════════════════════════════════════════════════
    // 3. setvisibleCreateDespuesDeEditar
    // Muestra el área Crear nuevo con tema amarillo.
    // ═════════════════════════════════════════════════════════════
    public void setvisibleCreateDespuesDeEditar() {
        f1.areaCreateNew_XGl.setVisibility(View.VISIBLE);
        f1.areaTemplate_XGl.setVisibility(View.GONE);
        f1.areaUpdateAndDelete_XGl.setVisibility(View.GONE);
        f1.areaConciliacionYRegistro_XGl.setVisibility(View.VISIBLE);
        f1.areaDocumento_XGl.setVisibility(View.VISIBLE);
        f1.areaListaRevisarCuentas_XGl.setVisibility(View.VISIBLE);
        f1.envolventeEncabezadoItemLista_XLl.setVisibility(View.VISIBLE);
        f1.embolventeItemsListaRevisar_XLl.setVisibility(View.VISIBLE);
        f1.envolventeEncabezadoItemListaRevisar_XLl.setVisibility(View.VISIBLE);
        f1.guardarCambiosALaBD.setVisibility(View.VISIBLE);
        f1.cleanSinCambiosInBD_XBt.setVisibility(View.VISIBLE);
        f1.salidaEsteFragment_XBt.setVisibility(View.VISIBLE);

        // Tema amarillo — identifica visualmente el área Crear
        f1.transacciones_crud_XCL.setBackgroundColor(Color.parseColor("#FAEF94"));
        f1.areaConciliacionYRegistro_XGl.setBackgroundResource(R.drawable.bg_yelow_square);
        f1.areaDocumento_XGl.setBackgroundResource(R.drawable.bg_yelow_square);
        f1.areaListaRevisarCuentas_XGl.setBackgroundResource(R.drawable.bg_yelow_square);

        f1.resetearSpinnerCuentasRevision();
    }

    // ═════════════════════════════════════════════════════════════
    // 4. setvisibleTemplateDespuesDeEditar
    // Muestra las áreas comunes después de editar una plantilla.
    // ═════════════════════════════════════════════════════════════
    public void setvisibleTemplateDespuesDeEditar() {
        f1.assignedDateInTemplate_XChB.setVisibility(View.VISIBLE);
        f1.guardarCambiosALaBD.setVisibility(View.VISIBLE);
        f1.cleanSinCambiosInBD_XBt.setVisibility(View.VISIBLE);
        f1.salidaEsteFragment_XBt.setVisibility(View.VISIBLE);
        f1.areaConciliacionYRegistro_XGl.setVisibility(View.VISIBLE);
        f1.areaDocumento_XGl.setVisibility(View.VISIBLE);
        f1.areaListaRevisarCuentas_XGl.setVisibility(View.VISIBLE);
        f1.embolventeItemsListaRevisar_XLl.setVisibility(View.VISIBLE);
        f1.envolventeEncabezadoItemListaRevisar_XLl.setVisibility(View.VISIBLE);

        f1.resetearSpinnerCuentasRevision();
    }

    // ═════════════════════════════════════════════════════════════
    // 5. setvisibleUpdateAndDeleteDespuesDeRecibirBundle
    // Muestra campos de fecha y botones específicos del área Editar/Eliminar.
    // ═════════════════════════════════════════════════════════════
    public void setvisibleUpdateAndDeleteDespuesDeRecibirBundle() {
        // Campos de fecha exclusivos del área Update
        f1.etiquetadateInUpdate_XTv.setVisibility(View.VISIBLE);
        f1.dateInUpdate_XTv.setVisibility(View.VISIBLE);
        f1.assignedOtherDateInUpdate_XChB.setVisibility(View.VISIBLE);
        f1.EtiquetaFechaNueva_XTv.setVisibility(View.VISIBLE);
        f1.changeOfDateInUpdate_XTv.setVisibility(View.VISIBLE);
        f1.eliminaDocumento_XBt.setVisibility(View.VISIBLE);

        // Áreas de layout
        f1.areaUpdateAndDelete_XGl.setVisibility(View.VISIBLE);
        f1.areaConciliacionYRegistro_XGl.setVisibility(View.VISIBLE);
        f1.areaDocumento_XGl.setVisibility(View.VISIBLE);
        f1.areaListaRevisarCuentas_XGl.setVisibility(View.VISIBLE);
        f1.envolventeEncabezadoItemLista_XLl.setVisibility(View.VISIBLE);
        f1.embolventeItemsListaRevisar_XLl.setVisibility(View.VISIBLE);
        f1.envolventeEncabezadoItemListaRevisar_XLl.setVisibility(View.VISIBLE);

        // Botones de acción
        f1.guardarCambiosALaBD.setVisibility(View.VISIBLE);
        f1.cleanSinCambiosInBD_XBt.setVisibility(View.VISIBLE);
        f1.salidaEsteFragment_XBt.setVisibility(View.VISIBLE);

        f1.resetearSpinnerCuentasRevision();
    }

    // ═════════════════════════════════════════════════════════════
    // 6. setInvisibleTemplateAntesDeEditar
    // Oculta las áreas comunes mientras el usuario escoge una plantilla.
    // ═════════════════════════════════════════════════════════════
    public void setInvisibleTemplateAntesDeEditar() {
        f1.assignedDateInTemplate_XChB.setVisibility(View.INVISIBLE);
        f1.guardarCambiosALaBD.setVisibility(View.INVISIBLE);
        f1.cleanSinCambiosInBD_XBt.setVisibility(View.INVISIBLE);
        f1.salidaEsteFragment_XBt.setVisibility(View.INVISIBLE);

        f1.areaConciliacionYRegistro_XGl.setVisibility(View.INVISIBLE);
        f1.areaDocumento_XGl.setVisibility(View.INVISIBLE);
        f1.areaListaRevisarCuentas_XGl.setVisibility(View.INVISIBLE);
        f1.embolventeItemsListaRevisar_XLl.setVisibility(View.INVISIBLE);
        f1.envolventeEncabezadoItemListaRevisar_XLl.setVisibility(View.INVISIBLE);

        f1.resetearSpinnerCuentasRevision();
    }

    // ═════════════════════════════════════════════════════════════
    // 7. setInvisibleUpdateAndDeleteAntesDeEditar
    // Oculta campos y áreas mientras el usuario busca el documento a editar.
    // ═════════════════════════════════════════════════════════════
    public void setInvisibleUpdateAndDeleteAntesDeEditar() {
        // Campos de fecha
        f1.etiquetadateInUpdate_XTv.setVisibility(View.INVISIBLE);
        f1.dateInUpdate_XTv.setVisibility(View.INVISIBLE);
        f1.assignedOtherDateInUpdate_XChB.setVisibility(View.INVISIBLE);
        f1.EtiquetaFechaNueva_XTv.setVisibility(View.INVISIBLE);
        f1.changeOfDateInUpdate_XTv.setVisibility(View.INVISIBLE);
        f1.eliminaDocumento_XBt.setVisibility(View.INVISIBLE);

        // Botones de acción
        f1.guardarCambiosALaBD.setVisibility(View.INVISIBLE);
        f1.cleanSinCambiosInBD_XBt.setVisibility(View.INVISIBLE);
        f1.salidaEsteFragment_XBt.setVisibility(View.INVISIBLE);

        // Áreas de layout
        f1.areaConciliacionYRegistro_XGl.setVisibility(View.INVISIBLE);
        f1.areaDocumento_XGl.setVisibility(View.INVISIBLE);
        f1.areaListaRevisarCuentas_XGl.setVisibility(View.INVISIBLE);
        f1.embolventeItemsListaRevisar_XLl.setVisibility(View.INVISIBLE);
        f1.envolventeEncabezadoItemListaRevisar_XLl.setVisibility(View.INVISIBLE);

        f1.resetearSpinnerCuentasRevision();
    }

    // ═════════════════════════════════════════════════════════════
    // 8. setVisibilityVisibleAreasForTemplate
    // Muestra el área Plantilla completa con tema rosa.
    // ═════════════════════════════════════════════════════════════
    public void setVisibilityVisibleAreasForTemplate() {
        f1.areaCreateNew_XGl.setVisibility(View.GONE);
        f1.areaTemplate_XGl.setVisibility(View.VISIBLE);
        f1.areaUpdateAndDelete_XGl.setVisibility(View.GONE);
        f1.envolventeEncabezadoItemLista_XLl.setVisibility(View.VISIBLE);
        f1.areaListaRevisarCuentas_XGl.setVisibility(View.VISIBLE);
        f1.areaButtons_XLL.setVisibility(View.VISIBLE);

        // Tema rosa — identifica visualmente el área Plantilla
        f1.transacciones_crud_XCL.setBackgroundColor(Color.parseColor("#FA94B7"));
        f1.areaConciliacionYRegistro_XGl.setBackgroundResource(R.drawable.bg_template);
        f1.areaDocumento_XGl.setBackgroundResource(R.drawable.bg_template);
        f1.areaListaRevisarCuentas_XGl.setBackgroundResource(R.drawable.bg_template);

        f1.resetearSpinnerCuentasRevision();
    }

    // ═════════════════════════════════════════════════════════════
    // 9. setVisibilityVisibleAreasForUpdateAndDelete
    // Muestra el área Editar/Eliminar completa con tema lila.
    // ═════════════════════════════════════════════════════════════
    public void setVisibilityVisibleAreasForUpdateAndDelete() {
        f1.areaCreateNew_XGl.setVisibility(View.GONE);
        f1.areaTemplate_XGl.setVisibility(View.GONE);
        f1.areaUpdateAndDelete_XGl.setVisibility(View.VISIBLE);
        f1.areaConciliacionYRegistro_XGl.setVisibility(View.VISIBLE);
        f1.areaDocumento_XGl.setVisibility(View.VISIBLE);
        f1.envolventeEncabezadoItemLista_XLl.setVisibility(View.VISIBLE);
        f1.areaListaRevisarCuentas_XGl.setVisibility(View.VISIBLE);
        f1.areaButtons_XLL.setVisibility(View.VISIBLE);

        // Tema lila — identifica visualmente el área Editar
        f1.transacciones_crud_XCL.setBackgroundColor(Color.parseColor("#AF87F6"));
        f1.areaConciliacionYRegistro_XGl.setBackgroundResource(R.drawable.bg_lilac_square);
        f1.areaDocumento_XGl.setBackgroundResource(R.drawable.bg_lilac_square);
        f1.areaListaRevisarCuentas_XGl.setBackgroundResource(R.drawable.bg_lilac_square);

        f1.resetearSpinnerCuentasRevision();
    }
}