package D_ADAPTERS;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.jj.appbalancev31.R;

import java.util.ArrayList;

import A1BASES.A3_2_TipoTransaccionesGetsYSets;
//import a4.balance.R;

public class D_F4_AdaptadorTransaccionesCierre extends ArrayAdapter<A3_2_TipoTransaccionesGetsYSets> {

    private final Context contexto;

    private final ArrayList<A3_2_TipoTransaccionesGetsYSets> arrayListTipoPersonalizado;

    public D_F4_AdaptadorTransaccionesCierre(Context context, ArrayList<A3_2_TipoTransaccionesGetsYSets>
            arrayListTipoPersonalizado) {
        super(context, -1, arrayListTipoPersonalizado);
        this.contexto = context;
        this.arrayListTipoPersonalizado = arrayListTipoPersonalizado;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater inflater = (LayoutInflater) contexto
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View inflarViews_View = inflater.inflate(R.layout.df5_adaptador_cierre_columnas, parent, false);

        TextView vTlistItem_1 = (TextView) inflarViews_View.findViewById(R.id.c1_documento_XTv);
        TextView vTlistItem_2 = (TextView) inflarViews_View.findViewById(R.id.c2_ItemDoc_XTv);
        TextView vTlistItem_3 = (TextView) inflarViews_View.findViewById(R.id.c3_Cuenta_XTv);
        TextView vTlistItem_4 = (TextView) inflarViews_View.findViewById(R.id.c4_Signo_XTv);
        TextView vTlistItem_5 = (TextView) inflarViews_View.findViewById(R.id.c5_Valor_XTv);
        TextView vTlistItem_6 = (TextView) inflarViews_View.findViewById(R.id.c6_Descripcion_XTv);
        TextView vTlistItem_7 = (TextView) inflarViews_View.findViewById(R.id.c7_FechaYhora_XTv);
        TextView vTlistItem_8 = (TextView) inflarViews_View.findViewById(R.id.c8_FechaInicial_XTv);
        TextView vTlistItem_9 = (TextView) inflarViews_View.findViewById(R.id.c9_FechaModificacion_XTv);
        TextView vTlistItem_10 = (TextView) inflarViews_View.findViewById(R.id.c10_Grupo1_XTv);
        TextView vTlistItem_11 = (TextView) inflarViews_View.findViewById(R.id.c11_Grupo2_XTv);

        A3_2_TipoTransaccionesGetsYSets registroTD = arrayListTipoPersonalizado.get(position);

        vTlistItem_1.setText(registroTD.tipoTget_1DocumentoMetodoEnA5());
        vTlistItem_2.setText(registroTD.tipoTget_2ItemDocMetodoEnA5());
        vTlistItem_3.setText(" "+registroTD.tipoTget_3CuentaMetodoEnA5());
        vTlistItem_4.setText(" "+registroTD.tipoTget_4MasMenosMetodoEnA5());
        vTlistItem_5.setText(" $ "+Integer.toString(registroTD.tipoTget_5ValorMetodoEnA5()));
        vTlistItem_6.setText(" "+registroTD.tipoTget_6DescripcionMetodoEnA5());
        vTlistItem_7.setText(" "+registroTD.tipoTget_7FechaYHoraMetodoEnA5());
        vTlistItem_8.setText(" "+ Integer.toString( registroTD.tipoTget_8FechaInicialMetodoEnA5()));
        vTlistItem_9.setText(" "+registroTD.tipoTget_9FechaModificacionMetodoEnA5());
        vTlistItem_10.setText(" "+registroTD.tipoTget_10Grupo1MetodoEnA5());
        vTlistItem_11.setText(" "+registroTD.tipoTget_11Grupo2MetodoEnA5());

        return inflarViews_View;
    }

}

