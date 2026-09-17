package A1BASES;

 // Created by JorgeEnrique on 6/08/2016.
public class A3_1_TipoCuentasGetsYSets {

    //declaramos atributos, los cuales se relacionan biunivomante con las columnas sub i de la TD
    public String tipoT_1Item_String;
    public String tipoT_2Cuenta_String;
    public String tipoT_3G1_String;
    public String tipoT_4G2_String;
    public String tipoT_5Fecha_String;

    // ⭐ NUEVO — Fase 4 (parte C): cuenta_id, codigo_cuenta y Cerrable, todas de "cuentas".
    // Puramente aditivo, igual que tipoT_14CuentaId en A3_2_TipoTransaccionesGetsYSets (Fase 3
    // parte B) — se llenan con setters DESPUÉS de construir el objeto (ver
    // A21_OptimizedQuery.mapCuentasFromCursor), sin tocar el constructor de 5 parámetros que ya
    // usa el único punto que lo construye hoy.
    public Long tipoT_6CuentaId_Long;
    public String tipoT_7CodigoCuenta_String;
    public String tipoT_8Cerrable_String;

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

    public Long tipoTgetCuenta_6CuentaId() {return tipoT_6CuentaId_Long;}
    public String tipoTgetCuenta_7CodigoCuenta() {return tipoT_7CodigoCuenta_String;}
    public String tipoTgetCuenta_8Cerrable() {return tipoT_8Cerrable_String;}

    public void tipoTsetCuenta_6CuentaId(Long tipoT_6CuentaId_Long) {this.tipoT_6CuentaId_Long = tipoT_6CuentaId_Long;}
    public void tipoTsetCuenta_7CodigoCuenta(String tipoT_7CodigoCuenta_String) {this.tipoT_7CodigoCuenta_String = tipoT_7CodigoCuenta_String;}
    public void tipoTsetCuenta_8Cerrable(String tipoT_8Cerrable_String) {this.tipoT_8Cerrable_String = tipoT_8Cerrable_String;}

}
