package D_ADAPTERS;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jj.appbalancev31.R;

import java.util.ArrayList;

import A1BASES.A3_2_TipoTransaccionesGetsYSets;

public class D_F3_2_AdaptadorTransaccionesCuentaYoFecha extends RecyclerView.Adapter<D_F3_2_AdaptadorTransaccionesCuentaYoFecha.ViewHolder> implements Filterable {

    private int filtroActivo = 0; // 0=Todas, 1=Solo verificadas, 2=Sin verificar
    private final Context contexto;
    private ArrayList<A3_2_TipoTransaccionesGetsYSets> arrayListTipoPersonalizado;
    private final ArrayList<A3_2_TipoTransaccionesGetsYSets> arrayListCompleto;
    private final OnItemClickListener listener;
    private final ArrayList<A3_2_TipoTransaccionesGetsYSets> filasSeleccionadas = new ArrayList<>();
    private CheckUpdateListener checkUpdateListener;

    public D_F3_2_AdaptadorTransaccionesCuentaYoFecha(Context context,
                                                      ArrayList<A3_2_TipoTransaccionesGetsYSets> arrayListTipoPersonalizado,
                                                      OnItemClickListener listener) {
        this.contexto = context;
        this.arrayListTipoPersonalizado = arrayListTipoPersonalizado;
        this.arrayListCompleto = new ArrayList<>(arrayListTipoPersonalizado);
        this.listener = listener;
    }

    public void setCheckUpdateListener(CheckUpdateListener listener) {
        this.checkUpdateListener = listener;
    }

    public void clearData() {
        arrayListTipoPersonalizado.clear();
        arrayListCompleto.clear();
        notifyDataSetChanged();
    }

    public void updateData(ArrayList<A3_2_TipoTransaccionesGetsYSets> nuevaLista) {
        // Preservar estados de check antes de actualizar
        for (A3_2_TipoTransaccionesGetsYSets nuevaTransaccion : nuevaLista) {
            for (A3_2_TipoTransaccionesGetsYSets transaccionVieja : arrayListCompleto) {
                if (nuevaTransaccion.tipoTget_1DocumentoMetodoEnA52() != null &&
                        nuevaTransaccion.tipoTget_1DocumentoMetodoEnA52().equals(
                                transaccionVieja.tipoTget_1DocumentoMetodoEnA52())) {
                    nuevaTransaccion.setChecked(transaccionVieja.isChecked());
                    break;
                }
            }
        }

        this.arrayListTipoPersonalizado = new ArrayList<>(nuevaLista);
        this.arrayListCompleto.clear();
        this.arrayListCompleto.addAll(nuevaLista);
        notifyDataSetChanged();

        if (checkUpdateListener != null) {
            checkUpdateListener.onCheckUpdated();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(contexto).inflate(R.layout.df3_2_adaptador_buscar_cuenta_y_o_fecha, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        A3_2_TipoTransaccionesGetsYSets registroTD = arrayListTipoPersonalizado.get(position);

        // Asignar valores a los TextView
        holder.c1_documento_XTv.setText(registroTD.tipoTget_1DocumentoMetodoEnA52());

        Integer valor = registroTD.tipoTget_5ValorMetodoEnA52();
        String valorTexto = (valor != null) ? " $ " + valor : " $ 0";
        holder.c5_Valor_XTv.setText(valorTexto);

        if (valor != null && valor > 0) {
            holder.c5_Valor_XTv.setTextColor(Color.parseColor("#006400"));
        } else {
            holder.c5_Valor_XTv.setTextColor(Color.RED);
        }

        holder.c6_Descripcion_XTv.setText(" " + registroTD.tipoTget_6DescripcionMetodoEnA52());

        Integer fechaInicial = registroTD.tipoTget_8FechaInicialMetodoEnA52();
        String fechaInicialTexto = (fechaInicial != null) ? "" + fechaInicial : " 0";
        holder.c8_FechaInicial_XTv.setText(fechaInicialTexto);

        // Configurar el ícono de check
        if (registroTD.isChecked()) {
            holder.iconoCheck_XIv.setImageResource(R.drawable.ic_circle_checked);
        } else {
            holder.iconoCheck_XIv.setImageResource(R.drawable.ic_circle_unchecked);
        }

        // Click en el ícono de check
        holder.iconoCheck_XIv.setOnClickListener(v -> {
            registroTD.setChecked(!registroTD.isChecked());
            notifyItemChanged(position);

            // Notificar al fragment
            if (checkUpdateListener != null) {
                checkUpdateListener.onCheckUpdated();
            }
        });

        // Click SOLO en documento
        holder.c1_documento_XTv.setOnClickListener(v -> {
            if (listener != null) {
                String documento = registroTD.tipoTget_1DocumentoMetodoEnA52();
                listener.onItemClick(documento);
            }
        });

        // Click en la fila completa (selección amarilla)
        holder.itemView.setOnClickListener(v -> {
            if (holder.isSelected) {
                holder.itemView.setBackgroundColor(Color.WHITE);
                holder.c1_documento_XTv.setTextColor(Color.BLUE);
                holder.c5_Valor_XTv.setTextColor(Color.BLUE);
                holder.c6_Descripcion_XTv.setTextColor(Color.BLUE);
                holder.c8_FechaInicial_XTv.setTextColor(Color.BLUE);
                filasSeleccionadas.remove(registroTD);
            } else {
                holder.itemView.setBackgroundColor(Color.parseColor("#FFEB3B"));
                holder.c1_documento_XTv.setTextColor(Color.RED);
                holder.c5_Valor_XTv.setTextColor(Color.RED);
                holder.c6_Descripcion_XTv.setTextColor(Color.RED);
                holder.c8_FechaInicial_XTv.setTextColor(Color.RED);
                filasSeleccionadas.add(registroTD);
            }

            holder.isSelected = !holder.isSelected;
            copiarSeleccionAlPortapapeles();
        });
    }

    private void copiarSeleccionAlPortapapeles() {
        if (filasSeleccionadas.isEmpty()) return;

        ClipboardManager clipboard = (ClipboardManager) contexto.getSystemService(Context.CLIPBOARD_SERVICE);

        StringBuilder datosCopiar = new StringBuilder();
        for (A3_2_TipoTransaccionesGetsYSets item : filasSeleccionadas) {
            datosCopiar.append("Documento: ").append(item.tipoTget_1DocumentoMetodoEnA52())
                    .append("\nValor: ").append(item.tipoTget_5ValorMetodoEnA52())
                    .append("\nDescripción: ").append(item.tipoTget_6DescripcionMetodoEnA52())
                    .append("\nFecha Inicial: ").append(item.tipoTget_8FechaInicialMetodoEnA52())
                    .append("\n-----------------------\n");
        }

        ClipData clip = ClipData.newPlainText("Datos Transacciones", datosCopiar.toString());
        clipboard.setPrimaryClip(clip);

        Toast.makeText(contexto, "Se copiaron " + filasSeleccionadas.size() + " transacciones al portapapeles", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int getItemCount() {
        return arrayListTipoPersonalizado.size();
    }

    // Métodos para conteo de checks
    public int getCheckedCount() {
        int count = 0;
        for (A3_2_TipoTransaccionesGetsYSets transaccion : arrayListCompleto) {
            if (transaccion.isChecked()) {
                count++;
            }
        }
        return count;
    }

    public int getTotalCount() {
        return arrayListCompleto.size();
    }

    public void setFiltroCheck(int filtro) {
        this.filtroActivo = filtro;
        getFilter().filter("");
    }


    public ArrayList<A3_2_TipoTransaccionesGetsYSets> getCheckedTransactions() {
        ArrayList<A3_2_TipoTransaccionesGetsYSets> checked = new ArrayList<>();
        for (A3_2_TipoTransaccionesGetsYSets transaccion : arrayListCompleto) {
            if (transaccion.isChecked()) {
                checked.add(transaccion);
            }
        }
        return checked;
    }

    public int getSumaValoresChecked() {
        int suma = 0;
        for (A3_2_TipoTransaccionesGetsYSets transaccion : arrayListCompleto) {
            if (transaccion.isChecked()) {
                suma += transaccion.tipoTget_5ValorMetodoEnA52();
            }
        }
        return suma;
    }

    public void limpiarTodosLosChecks() {
        for (A3_2_TipoTransaccionesGetsYSets transaccion : arrayListCompleto) {
            transaccion.setChecked(false);
        }
        notifyDataSetChanged();
        if (checkUpdateListener != null) {
            checkUpdateListener.onCheckUpdated();
        }
    }

    public ArrayList<A3_2_TipoTransaccionesGetsYSets> obtenerListaCompleta() {
        return new ArrayList<>(arrayListCompleto);
    }

    @Override
    public Filter getFilter() {
        return filtroPersonalizado;
    }

    private final Filter filtroPersonalizado = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            ArrayList<A3_2_TipoTransaccionesGetsYSets> listaFiltrada = new ArrayList<>();

            if (constraint == null || constraint.length() == 0) {
                // Aplicar solo filtro de checks si no hay búsqueda de texto
                for (A3_2_TipoTransaccionesGetsYSets item : arrayListCompleto) {
                    if (filtroActivo == 0) { // Todas
                        listaFiltrada.add(item);
                    } else if (filtroActivo == 1 && item.isChecked()) { // Solo verificadas
                        listaFiltrada.add(item);
                    } else if (filtroActivo == 2 && !item.isChecked()) { // Sin verificar
                        listaFiltrada.add(item);
                    }
                }
            } else {
                String patronBusqueda = constraint.toString().toLowerCase().trim();

                // Filtrar por texto Y por checks
                for (A3_2_TipoTransaccionesGetsYSets item : arrayListCompleto) {
                    String documento = item.tipoTget_1DocumentoMetodoEnA52() != null ?
                            item.tipoTget_1DocumentoMetodoEnA52().toLowerCase() : "";

                    String descripcion = item.tipoTget_6DescripcionMetodoEnA52() != null ?
                            item.tipoTget_6DescripcionMetodoEnA52().toLowerCase() : "";

                    String valor = String.valueOf(item.tipoTget_5ValorMetodoEnA52());
                    String fecha = String.valueOf(item.tipoTget_8FechaInicialMetodoEnA52());

                    boolean coincideTexto = documento.contains(patronBusqueda) ||
                            descripcion.contains(patronBusqueda) ||
                            valor.contains(patronBusqueda) ||
                            fecha.contains(patronBusqueda);

                    // Aplicar ambos filtros
                    if (coincideTexto) {
                        if (filtroActivo == 0) { // Todas
                            listaFiltrada.add(item);
                        } else if (filtroActivo == 1 && item.isChecked()) { // Solo verificadas
                            listaFiltrada.add(item);
                        } else if (filtroActivo == 2 && !item.isChecked()) { // Sin verificar
                            listaFiltrada.add(item);
                        }
                    }
                }
            }

            FilterResults results = new FilterResults();
            results.values = listaFiltrada;
            return results;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            arrayListTipoPersonalizado.clear();
            arrayListTipoPersonalizado.addAll((ArrayList<A3_2_TipoTransaccionesGetsYSets>) results.values);
            notifyDataSetChanged();
        }
    };

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView c1_documento_XTv, c5_Valor_XTv, c6_Descripcion_XTv, c8_FechaInicial_XTv;
        ImageView iconoCheck_XIv;
        boolean isSelected = false;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            c1_documento_XTv = itemView.findViewById(R.id.c1_documento_XTv);
            c5_Valor_XTv = itemView.findViewById(R.id.c5_Valor_XTv);
            c6_Descripcion_XTv = itemView.findViewById(R.id.c6_Descripcion_XTv);
            c8_FechaInicial_XTv = itemView.findViewById(R.id.c8_FechaInicial_XTv);
            iconoCheck_XIv = itemView.findViewById(R.id.iconoCheck_XIv);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(String documento);
    }

    public interface CheckUpdateListener {
        void onCheckUpdated();
    }
}