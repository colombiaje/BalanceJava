package D_ADAPTERS;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.jj.appbalancev31.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import A1BASES.A3_2_TipoTransaccionesGetsYSets;
//import a4.balance.R;

public class D_F1_AdaptadorCrudDocumento2 extends ArrayAdapter<A3_2_TipoTransaccionesGetsYSets> {

    private final Context contexto;
    private final ArrayList<A3_2_TipoTransaccionesGetsYSets> arrayListTipoPersonalizado;
    private final List<Integer> columnasVisibles;

    public D_F1_AdaptadorCrudDocumento2(Context contexto,
                                        ArrayList<A3_2_TipoTransaccionesGetsYSets> arrayListTipoPersonalizado,
                                        List<Integer> columnasVisibles) {
        super(contexto, -1, arrayListTipoPersonalizado);
        this.contexto = contexto;
        this.arrayListTipoPersonalizado = arrayListTipoPersonalizado;

        // Si no se pasa una lista de columnas visibles, asumir que todas las columnas están visibles
        if (columnasVisibles == null || columnasVisibles.isEmpty()) {
            // Agregar todos los índices de columnas por defecto (supón que tienes 11 columnas)
            this.columnasVisibles = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        } else {
            this.columnasVisibles = columnasVisibles;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater inflater = (LayoutInflater) contexto.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View inflarViews_View = inflater.inflate(R.layout.d_f1_adaptador_registros_a_documento_columnas, parent, false);

        // Recogemos las vistas para cada columna
        TextView vTlistItem_1 = inflarViews_View.findViewById(R.id.c1_documento_XTv);
        TextView vTlistItem_2 = inflarViews_View.findViewById(R.id.c2_ItemDoc_XTv);
        TextView vTlistItem_3 = inflarViews_View.findViewById(R.id.c3_Cuenta_XTv);
        TextView vTlistItem_4 = inflarViews_View.findViewById(R.id.c4_Signo_XTv);
        TextView vTlistItem_5 = inflarViews_View.findViewById(R.id.c5_Valor_XTv);
        TextView vTlistItem_6 = inflarViews_View.findViewById(R.id.c6_Descripcion_XTv);
        TextView vTlistItem_7 = inflarViews_View.findViewById(R.id.c7_FechaYhora_XTv);
        TextView vTlistItem_8 = inflarViews_View.findViewById(R.id.c8_FechaInicial_XTv);
        TextView vTlistItem_9 = inflarViews_View.findViewById(R.id.c9_FechaModificacion_XTv);
        TextView vTlistItem_10 = inflarViews_View.findViewById(R.id.c10_Grupo1_XTv);
        TextView vTlistItem_11 = inflarViews_View.findViewById(R.id.c11_Grupo2_XTv);

        // Se obtiene el registro para esa posición
        A3_2_TipoTransaccionesGetsYSets registroTD = arrayListTipoPersonalizado.get(position);

        // Asignamos los valores a las vistas solo si la columna está visible
        if (columnasVisibles.contains(0)) {
            vTlistItem_1.setText(registroTD.tipoTget_1DocumentoMetodoEnA5());
        } else {
            vTlistItem_1.setVisibility(View.GONE); // Ocultamos la vista si no está en columnas visibles
        }

        if (columnasVisibles.contains(1)) {
            vTlistItem_2.setText(registroTD.tipoTget_2ItemDocMetodoEnA5());
        } else {
            vTlistItem_2.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(2)) {
            vTlistItem_3.setText("" + registroTD.tipoTget_3CuentaMetodoEnA5());
        } else {
            vTlistItem_3.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(3)) {
            vTlistItem_4.setText("" + registroTD.tipoTget_4MasMenosMetodoEnA5());
        } else {
            vTlistItem_4.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(4)) {
            vTlistItem_5.setText("" + Integer.toString(registroTD.tipoTget_5ValorMetodoEnA5()));
        } else {
            vTlistItem_5.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(5)) {
            vTlistItem_6.setText("" + registroTD.tipoTget_6DescripcionMetodoEnA5());
        } else {
            vTlistItem_6.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(6)) {
            vTlistItem_7.setText("" + registroTD.tipoTget_7FechaYHoraMetodoEnA5());
        } else {
            vTlistItem_7.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(7)) {
            vTlistItem_8.setText("" + Integer.toString(registroTD.tipoTget_8FechaInicialMetodoEnA5()));
        } else {
            vTlistItem_8.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(8)) {
            vTlistItem_9.setText("" + registroTD.tipoTget_9FechaModificacionMetodoEnA5());
        } else {
            vTlistItem_9.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(9)) {
            vTlistItem_10.setText("" + registroTD.tipoTget_10Grupo1MetodoEnA5());
        } else {
            vTlistItem_10.setVisibility(View.GONE);
        }

        if (columnasVisibles.contains(10)) {
            vTlistItem_11.setText("" + registroTD.tipoTget_11Grupo2MetodoEnA5());
        } else {
            vTlistItem_11.setVisibility(View.GONE);
        }

        return inflarViews_View;
    }
}

