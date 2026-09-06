package B1_F1Support.inflateunits;

import android.view.View;

import com.jj.appbalancev31.R;

import B_FRAGMENTS.F1_CrudDocumento;

public class CalendarioUnit {

    private final F1_CrudDocumento f1;

    public CalendarioUnit(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        setupListeners();
    }

    private void inflateViews(View view) {
        f1.areaCalendario_XGl   = view.findViewById(R.id.areaCalendario_XGl);
        f1.calendarView_XCv     = view.findViewById(R.id.calendarView_XCv);
        f1.cerrarCalendario_XBt = view.findViewById(R.id.cerrarCalendario_XBt);
    }

    private void setupListeners() {
        setupLongClick();
        setupDateChangeListener();
        setupCloseButton();
    }

    // Long click sobre el calendario → cerrar y volver al área de registro
    private void setupLongClick() {
        f1.calendarView_XCv.setOnLongClickListener(v -> {
            closeCalendar();
            return false;
        });
    }

    // Ruta la fecha seleccionada al checkbox que abrió el calendario
    private void setupDateChangeListener() {
        f1.calendarView_XCv.setOnDateChangeListener(
                (view, year, month, dayOfMonth) -> {
                    int selectedDate = Integer.parseInt(
                            String.format("%04d%02d%02d", year, month + 1, dayOfMonth));

                    if (f1.assignedOtherDateInCreateNew_XChB.isChecked()) {
                        f1.otherDateAssignedInCreateNew_Int = selectedDate;
                        f1.otherDateInCreateNew_XTv.setText("" + selectedDate);
                        f1.assignedOtherDateInCreateNew_XChB.setChecked(false);
                        closeCalendar();
                    }

                    if (f1.assignedDateInTemplate_XChB.isChecked()) {
                        f1.dateTemplateAssignedInCaledarView_Int = selectedDate;
                        f1.dateInTemplate_XTv.setText("" + selectedDate);
                        f1.assignedDateInTemplate_XChB.setChecked(false);
                        closeCalendar();
                    }

                    if (f1.assignedOtherDateInUpdate_XChB.isChecked()) {
                        f1.dateInUpdateAssignedInCaledarView_Int = selectedDate;
                        f1.changeOfDateInUpdate_XTv.setText("" + selectedDate);
                        f1.assignedOtherDateInUpdate_XChB.setChecked(false);
                        closeCalendar();
                    }
                });
    }

    // Botón cerrar — resetea checkboxes de fecha y vuelve al registro
    private void setupCloseButton() {
        f1.cerrarCalendario_XBt.setOnClickListener(v -> {
            f1.DateOfDocument_Integer = f1.dateCurrent_ArrayInteger[5];
            f1.assignedOtherDateInCreateNew_XChB.setChecked(false);
            f1.assignedDateInTemplate_XChB.setChecked(false);
            f1.assignedOtherDateInUpdate_XChB.setChecked(false);
            closeCalendar();
        });
    }

    // Helper compartido: oculta calendario, muestra área de registro
    private void closeCalendar() {
        f1.areaCalendario_XGl.setVisibility(View.GONE);
        f1.areaConciliacionYRegistro_XGl.setVisibility(View.VISIBLE);
    }
}