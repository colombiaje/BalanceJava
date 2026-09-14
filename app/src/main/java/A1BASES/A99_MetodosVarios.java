package A1BASES;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

public class A99_MetodosVarios {

    //declaracion de variables

    public static String stringFechaYHora = null;

    /**
     * Llena un "badge" numérico tipo WhatsApp (fondo circular/píldora,
     * número blanco) — usado en el botón ✔️ de Auditoría de Clasificación,
     * tanto en F1_CrudDocumento como en F3_1_VerInformePrincipal, para que
     * el usuario vea de un vistazo cuántas transacciones desalineadas hay,
     * sin tener que abrir el diálogo para enterarse.
     *
     * Siempre visible, incluyendo el "0" — a propósito (petición explícita
     * de Jorge, 2026-09-14): un botón ✔️ sin ningún número es ambiguo (¿no
     * hay errores, o el badge simplemente no cargó?), mientras que un "0"
     * confirma activamente que se revisó y no hay inconsistencias.
     *
     * Para que "0" no se lea como una alerta, el fondo cambia de color según
     * el caso: verde cuando cantidad es 0 (todo en orden), rojo cuando hay
     * una o más desalineaciones. El drawable (bg_badge_notificacion.xml) es
     * un GradientDrawable — se recolorea en tiempo de ejecución en vez de
     * necesitar dos recursos .xml separados.
     *
     * cantidad > 99 → muestra "99+" en vez del número exacto, igual que
     * hacen la mayoría de apps de mensajería con sus contadores.
     */
    public static void actualizarBadgeNumerico(TextView badge, int cantidad) {
        if (badge == null) return;

        int cantidadMostrada = Math.max(cantidad, 0);
        badge.setText(cantidadMostrada > 99 ? "99+" : String.valueOf(cantidadMostrada));
        badge.setVisibility(View.VISIBLE);

        int colorBadge = cantidadMostrada > 0
                ? Color.parseColor("#E53935")  // rojo — hay desalineamientos
                : Color.parseColor("#2E7D32"); // verde — sin inconsistencias

        Drawable fondo = badge.getBackground();
        if (fondo instanceof GradientDrawable) {
            ((GradientDrawable) fondo.mutate()).setColor(colorBadge);
        }
    }


    public A99_MetodosVarios(FragmentActivity activity) {
    }

    public A99_MetodosVarios() {

    }

    public Integer[] fechasYHoras(){

        //genera las fechas que se van a llamar en las demas clases de esta app

        //declaracion y asignacion de obejtos y variables
        Calendar cal = Calendar.getInstance();
        GregorianCalendar gregorianCalendar;
        Date d=new Date();
        SimpleDateFormat fecc=new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

        //No incluido en el array de retorno por ser este solo de Integers
        stringFechaYHora = fecc.format(d);

        Integer soloDia = cal.get(Calendar.DAY_OF_MONTH);
        Integer soloMes = cal.get(Calendar.MONTH) + 1;
        Integer soloAnno = cal.get(Calendar.YEAR);
        Integer soloHora =cal.get(Calendar.HOUR_OF_DAY);
        Integer soloMinuto = cal.get(Calendar.MINUTE);
        Integer soloSegundo = cal.get(Calendar.SECOND);

        //Integer diasMes = getLastDayOfMonth(soloAnno,0);
        Integer diasMes = getLastDayOfMonth(soloAnno, soloMes);

        //...
        Date fechaInicialEnAdicionar_Date = cal.getTime();
        SimpleDateFormat formato = new SimpleDateFormat("yyyyMMdd");
        String fechaInicial_string = formato.format(fechaInicialEnAdicionar_Date);
        Integer DateOfDocument_Integer = Integer.parseInt(fechaInicial_string);
        return  new Integer[] {soloAnno,soloMes,soloDia,soloHora, 0, DateOfDocument_Integer,diasMes,soloMinuto,soloSegundo};

    }

    /*public Integer getLastDayOfMonth(int year, int month) {
        Date date = new Date(year, month -1 , 0);
        return date.getDate();
    }*/

    public Integer getLastDayOfMonth(int year, int month) {
        // month 0 en Calendar es Enero, pero tu método recibe mes real (1-12)
        GregorianCalendar calendar = new GregorianCalendar(year, month - 1, 1);
        // Devuelve 28, 29, 30 o 31 según el mes y año (incluye bisiestos)
        return calendar.getActualMaximum(GregorianCalendar.DAY_OF_MONTH);
    }

    //TEXTO A IMAGEN
    public static Bitmap textoABitmap(String text, float textSize, int textColor) {
        Paint dibujo_Paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dibujo_Paint.setTextSize(textSize);
        dibujo_Paint.setColor(textColor);
        dibujo_Paint.setTextAlign(Paint.Align.LEFT);
        float baseline = -dibujo_Paint.ascent(); // ascent() is negative
        int width = (int) (dibujo_Paint.measureText(text) + 0.0f); // round
        int height = (int) (baseline + dibujo_Paint.descent() + 0.0f);
        Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        Canvas lienzo_Canvas = new Canvas(image);
        lienzo_Canvas.drawText(text, 0, baseline, dibujo_Paint);
        return image;
    }

}

