package D_ADAPTERS;

import android.content.Context;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.jj.appbalancev31.R;

import java.util.ArrayList;

import A1BASES.A3_1_TipoCuentasGetsYSets;
//import a4.balance.R;

public class D_F2_AdaptadorCuentas extends ArrayAdapter<A3_1_TipoCuentasGetsYSets> {

    private final Context contexto;

    private final ArrayList<A3_1_TipoCuentasGetsYSets> arrayListTipoPersonalizado;
    private ArrayList<A3_1_TipoCuentasGetsYSets> listDatos;

    ArrayList<A3_1_TipoCuentasGetsYSets> copyDelArrayList = new ArrayList<>();

    public D_F2_AdaptadorCuentas(Context context, ArrayList<A3_1_TipoCuentasGetsYSets>
            arrayListTipoPersonalizado) {
        super(context, -1, arrayListTipoPersonalizado);
        this.contexto = context;
        this.arrayListTipoPersonalizado = arrayListTipoPersonalizado;
        this.copyDelArrayList.addAll(arrayListTipoPersonalizado);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) contexto.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View inflarViews_View = inflater.inflate(R.layout.df2_2_adaptador_cuentas, parent, false);

        TextView vTlistItem_1 = (TextView) inflarViews_View.findViewById(R.id.itemXTv);
        TextView vTlistItem_2 = (TextView) inflarViews_View.findViewById(R.id.cuentaXTv);
        TextView vTlistItem_3 = (TextView) inflarViews_View.findViewById(R.id.g1XTv);
        TextView vTlistItem_4 = (TextView) inflarViews_View.findViewById(R.id.g2XTv);
        TextView vTlistItem_5 = (TextView) inflarViews_View.findViewById(R.id.fechaXTv);

        A3_1_TipoCuentasGetsYSets registroTablaCuentas = arrayListTipoPersonalizado.get(position);

        // Set text to the TextViews
        vTlistItem_1.setText(""+registroTablaCuentas.tipoT_1Item_String);
        vTlistItem_2.setText(" "+registroTablaCuentas.tipoT_2Cuenta_String);
        vTlistItem_3.setText(registroTablaCuentas.tipoT_3G1_String);
        vTlistItem_4.setText(" "+registroTablaCuentas.tipoT_4G2_String);
        vTlistItem_5.setText(" "+registroTablaCuentas.tipoT_5Fecha_String);

        // Alternar colores de fondo
        if (position % 2 == 0) {
            inflarViews_View.setBackgroundColor(ContextCompat.getColor(contexto, R.color.color_row_even));
        } else {
            inflarViews_View.setBackgroundColor(ContextCompat.getColor(contexto, R.color.color_row_odd));
        }

        return inflarViews_View;
    }

    public void getFilter(Editable s) {
    }

    public void filtrarPorTextoExcrito(String texto) {

        arrayListTipoPersonalizado.clear();

        if (texto.length() == 0) {
            arrayListTipoPersonalizado.addAll(copyDelArrayList);
        } else {

            for (A3_1_TipoCuentasGetsYSets listaDelaCopia : copyDelArrayList) {

                if (listaDelaCopia.tipoT_1Item_String.contains(texto)
                        || String.valueOf(listaDelaCopia.tipoT_2Cuenta_String).contains(texto)
                        ||listaDelaCopia.tipoT_3G1_String.contains(texto)
                        ||listaDelaCopia.tipoT_4G2_String.contains(texto)
                        ||listaDelaCopia.tipoT_5Fecha_String.contains(texto)

                )
                {
                    arrayListTipoPersonalizado.add(listaDelaCopia);
                }
            }
        }
        notifyDataSetChanged();
    }
}
