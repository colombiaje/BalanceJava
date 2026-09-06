package B_FRAGMENTS;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.jj.appbalancev31.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import A1BASES.A3_2_TipoTransaccionesGetsYSets;
import A2QueryBD.A22_QueryManager;
import A2QueryBD.A23_QueryResult;
import D_ADAPTERS.D_F3_2_AdaptadorTransaccionesCuentaYoFecha;

public class G1_Prueba3 extends DialogFragment {
    A22_QueryManager a22QueryManager;
    public static String receivedAccount;
    ArrayList<A3_2_TipoTransaccionesGetsYSets> transaccionesFiltradasSoloCuenta_ArrayList;
    D_F3_2_AdaptadorTransaccionesCuentaYoFecha adaptadorTransaccionesCuentaYoFecha;
    LinearLayout consultasPorCuentaYFecha_XLl;
    private RecyclerView recyclerViewTransacciones_XRv;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(true); // Establecer como no modal, sale del dialogoFragment a un click fuera del dialogo
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, final ViewGroup container, Bundle savedInstanceState) {
        View inflarViews_View = inflater.inflate(R.layout.g1_prueba, container, false);

        consultasPorCuentaYFecha_XLl = (LinearLayout) inflarViews_View.findViewById(R.id.consultasPorCuentaYFecha_XLl);
        recyclerViewTransacciones_XRv = inflarViews_View.findViewById(R.id.recyclerViewTransacciones_XRv);

        a22QueryManager =  new A22_QueryManager(getActivity());
        dynamicqueryByAccount();
        verTransaccionesSoloCuenta();
        recyclerViewTransacciones_XRv.setLayoutManager(new LinearLayoutManager(getActivity()));

        return inflarViews_View;
    }
    public void dynamicqueryByAccount() {
        A23_QueryResult transaccionesFiltradasSoloCuenta_ArrayList_Result =
                a22QueryManager.queryTransactionsByAccount("CxC Enrique");
        transaccionesFiltradasSoloCuenta_ArrayList = transaccionesFiltradasSoloCuenta_ArrayList_Result.getDatos();

        ArrayList<A3_2_TipoTransaccionesGetsYSets> listaFiltrada = new ArrayList<>();

        if (transaccionesFiltradasSoloCuenta_ArrayList != null) {
            for (A3_2_TipoTransaccionesGetsYSets transaccion : transaccionesFiltradasSoloCuenta_ArrayList) {
                String columna1 = transaccion.tipoTget_1DocumentoMetodoEnA5();
                int columna2 = transaccion.tipoTget_5ValorMetodoEnA5();
                String columna3 = transaccion.tipoTget_6DescripcionMetodoEnA5();
                int columna4 = transaccion.tipoTget_8FechaInicialMetodoEnA5();

                A3_2_TipoTransaccionesGetsYSets transaccionReducida = new A3_2_TipoTransaccionesGetsYSets(columna1, columna2, columna3, columna4);
                listaFiltrada.add(transaccionReducida);
            }
            Collections.sort(listaFiltrada, new Comparator<A3_2_TipoTransaccionesGetsYSets>() {
                @Override
                public int compare(A3_2_TipoTransaccionesGetsYSets t1, A3_2_TipoTransaccionesGetsYSets t2) {
                    int fechaCompare = Integer.compare(t2.tipoTget_8FechaInicialMetodoEnA52(), t1.tipoTget_8FechaInicialMetodoEnA52());
                    if (fechaCompare != 0) {
                        return fechaCompare;
                    }
                    return t1.tipoTget_1DocumentoMetodoEnA52().compareTo(t2.tipoTget_1DocumentoMetodoEnA52());
                }
            });
            if (adaptadorTransaccionesCuentaYoFecha == null) {
                adaptadorTransaccionesCuentaYoFecha = new D_F3_2_AdaptadorTransaccionesCuentaYoFecha(getActivity(), listaFiltrada,
                        documento -> {
                        });
                recyclerViewTransacciones_XRv.setAdapter(adaptadorTransaccionesCuentaYoFecha);
            }
        } else {
            Toast.makeText(getActivity(), "La lista de transacciones está vacía o es nula. ", Toast.LENGTH_SHORT).show();
        }
    }
    public void verTransaccionesSoloCuenta () {
        consultasPorCuentaYFecha_XLl.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(2, 20, 0, 40);
        try {
            dynamicqueryByAccount();
        } catch (Exception e) {
            e.printStackTrace(); // Loguear el error
        }
    }
}