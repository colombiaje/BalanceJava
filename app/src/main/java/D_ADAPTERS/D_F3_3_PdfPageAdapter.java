package D_ADAPTERS;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.jj.appbalancev31.R;

import java.util.List;

public class D_F3_3_PdfPageAdapter extends RecyclerView.Adapter<D_F3_3_PdfPageAdapter.PdfViewHolder> {
    private final List<Bitmap> pdfPages;

    public D_F3_3_PdfPageAdapter(List<Bitmap> pdfPages) {
        this.pdfPages = pdfPages;
    }

    @NonNull
    @Override
    public PdfViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.f3_4_2_item_pdf_page, parent, false);
        return new PdfViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PdfViewHolder holder, int position) {
        holder.pdfPageImage.setImageBitmap(pdfPages.get(position));
    }

    @Override
    public int getItemCount() {
        return pdfPages.size();
    }

    static class PdfViewHolder extends RecyclerView.ViewHolder {
        ImageView pdfPageImage;

        public PdfViewHolder(@NonNull View itemView) {
            super(itemView);
            pdfPageImage = itemView.findViewById(R.id.pdfPageImage);
        }
    }
}
