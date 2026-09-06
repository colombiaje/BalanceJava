package B1_F1Support.inflateunits;

import android.view.View;

import com.jj.appbalancev31.R;

import B_FRAGMENTS.F1_CrudDocumento;

public class CreateNewUnit {

    private final F1_CrudDocumento f1;

    public CreateNewUnit(F1_CrudDocumento fragment) {
        this.f1 = fragment;
    }

    public void setup(View view) {
        inflateViews(view);
        setupListeners();
    }

    private void inflateViews(View view) {
        f1.areaCreateNew_XGl                 = view.findViewById(R.id.areaCreateNew_XGl);
        f1.numeroConsecutivoDoc_XTv          = view.findViewById(R.id.numeroConsecutivoDoc_XTv);
        f1.dateInCreateNew_XTv               = view.findViewById(R.id.dateInCreateNew_XTv);
        f1.otherDateInCreateNew_XTv          = view.findViewById(R.id.otherDateInCreateNew_XTv);
        f1.assignedOtherDateInCreateNew_XChB = view.findViewById(R.id.assignedOtherDateInCreateNew_XChB);
    }

    private void setupListeners() {
        // Checkbox "asignar otra fecha" → oculta conciliación, muestra calendario
        f1.assignedOtherDateInCreateNew_XChB.setOnClickListener(v -> {
            f1.areaConciliacionYRegistro_XGl.setVisibility(View.GONE);
            f1.areaCalendario_XGl.setVisibility(View.VISIBLE);
            f1.assignedOtherDateInCreateNew_XChB.setChecked(true);
        });
    }
}