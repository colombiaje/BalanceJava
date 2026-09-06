package D_ADAPTERS;

import android.content.Context;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.jj.appbalancev31.R;

import java.util.ArrayList;

import A1BASES.A3_2_TipoTransaccionesGetsYSets;
//import a4.balance.R;

public class D_F3_1_AdaptadorTransaccionesInformes extends ArrayAdapter<A3_2_TipoTransaccionesGetsYSets> {

    private final Context contexto;

    private final ArrayList<A3_2_TipoTransaccionesGetsYSets> arrayListTipoPersonalizado;
    private ArrayList<A3_2_TipoTransaccionesGetsYSets> listDatos;

    ArrayList<A3_2_TipoTransaccionesGetsYSets> copyDelArrayList = new ArrayList<>();

    public D_F3_1_AdaptadorTransaccionesInformes(Context context, ArrayList<A3_2_TipoTransaccionesGetsYSets>
            arrayListTipoPersonalizado) {
        super(context, -1, arrayListTipoPersonalizado);
        this.contexto = context;
        this.arrayListTipoPersonalizado = arrayListTipoPersonalizado;
        this.copyDelArrayList.addAll(arrayListTipoPersonalizado);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        LayoutInflater inflater = (LayoutInflater) contexto
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View inflarViews_View = inflater.inflate(R.layout.d_f3_1_adaptador_informes_columnas, parent, false);

        TextView vTlistItem_3 = (TextView) inflarViews_View.findViewById(R.id.c3_Cuenta_XTv);
        TextView vTlistItem_4 = (TextView) inflarViews_View.findViewById(R.id.c4_Signo_XTv);
        TextView vTlistItem_5 = (TextView) inflarViews_View.findViewById(R.id.c5_Valor_XTv);
        TextView vTlistItem_10 = (TextView) inflarViews_View.findViewById(R.id.c10_Grupo1_XTv);
        TextView vTlistItem_11 = (TextView) inflarViews_View.findViewById(R.id.c11_Grupo2_XTv);

        A3_2_TipoTransaccionesGetsYSets registroTD = arrayListTipoPersonalizado.get(position);

        vTlistItem_3.setText(""+registroTD.tipoTget_3CuentaMetodoEnA5());
        vTlistItem_4.setText(" "+registroTD.tipoTget_4MasMenosMetodoEnA5());
        //vTlistItem_5.setText(" $ "+vTlistItem_4.getText().toString()+" "+ Integer.toString(registroTD.tipoTget_5ValorMetodoEnA5()));
        vTlistItem_5.setText(" $ " + Integer.toString(registroTD.tipoTget_5ValorMetodoEnA5()));
        vTlistItem_10.setText(" "+registroTD.tipoTget_10Grupo1MetodoEnA5());
        vTlistItem_11.setText(" "+registroTD.tipoTget_11Grupo2MetodoEnA5());

        return inflarViews_View;
    }

    public void getFilter(Editable s) {
    }

    public void filtrarPorTextoExcrito(String texto) {
        arrayListTipoPersonalizado.clear();

        if (texto.length() == 0) {
            arrayListTipoPersonalizado.addAll(copyDelArrayList);
        } else {
            // Convertir el texto ingresado a minúsculas
            String textoLower = texto.toLowerCase();

            for (A3_2_TipoTransaccionesGetsYSets listaDelaCopia : copyDelArrayList) {
                // Convertir los valores a minúsculas antes de comparar
                if (listaDelaCopia.tipoT_3Accout_String.toLowerCase().contains(textoLower)
                        || String.valueOf(listaDelaCopia.tipoT_5Value_Integer).toLowerCase().contains(textoLower)
                        || listaDelaCopia.tipoT_10BalanceItems_String.toLowerCase().contains(textoLower)
                        || listaDelaCopia.tipoT_11BalanceItemsClassification_String.toLowerCase().contains(textoLower)) {

                    arrayListTipoPersonalizado.add(listaDelaCopia);
                }
            }
        }
        notifyDataSetChanged();
    }

}
