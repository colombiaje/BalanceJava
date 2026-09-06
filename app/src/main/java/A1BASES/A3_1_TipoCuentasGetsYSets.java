package A1BASES;

 // Created by JorgeEnrique on 6/08/2016.
public class A3_1_TipoCuentasGetsYSets {

    //declaramos atributos, los cuales se relacionan biunivomante con las columnas sub i de la TD
    public String tipoT_1Item_String;
    public String tipoT_2Cuenta_String;
    public String tipoT_3G1_String;
    public String tipoT_4G2_String;
    public String tipoT_5Fecha_String;

// metodo constructor
    public A3_1_TipoCuentasGetsYSets(String tipoT_1Item_String, String tipoT_2Cuenta_String, String tipoT_3G1_String, String tipoT_4G2_String,
                                     String tipoT_5Fecha_String) {
        
        this.tipoT_1Item_String = tipoT_1Item_String;
        this.tipoT_2Cuenta_String = tipoT_2Cuenta_String;
        this.tipoT_3G1_String = tipoT_3G1_String;
        this.tipoT_4G2_String = tipoT_4G2_String;
        this.tipoT_5Fecha_String = tipoT_5Fecha_String;
    }

    public String tipoTgetCuenta_1Item() {return tipoT_1Item_String; }
    public String tipoTgetCuenta_2Cuenta() {return tipoT_2Cuenta_String;}
    public String tipoTgetCuenta_3G1() {return tipoT_3G1_String;  }
    public String tipoTgetCuenta_3G2() {
        return tipoT_4G2_String;
    }
    public String tipoTgetCuenta_5Fecha() {
        return tipoT_5Fecha_String;
    }

}
