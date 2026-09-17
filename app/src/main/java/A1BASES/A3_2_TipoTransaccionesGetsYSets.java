package A1BASES;

// Created by JorgeEnrique on 6/08/2016.

public class A3_2_TipoTransaccionesGetsYSets {

    //declaramos atributos, los cuales se relacionan biunivomante con las columnas sub i de la TD
    public String tipoT_1NumberDocument_String;
    public String tipoT_2DocumentItems_String;
    public String tipoT_3Accout_String;
    public String tipoT_4Sign_String;
    public Integer tipoT_5Value_Integer;
    public String tipoT_6Description_String;
    public String tipoT_7TimeOfProcess_String;
    public Integer tipoT_8DateOfDocument_Integer;
    public String tipoT_9DateUpdateDocument_String;
    public String tipoT_10BalanceItems_String;
    public String tipoT_11BalanceItemsClassification_String;
    public String tipoT_12AccountWhitFlag_String;
    public String tipoT_13ColumnaDisponible_String;

    // ⭐ NUEVO — Fase 3 (parte B) de la reestructuración de BD: cuenta_id real de la cuenta
    // (tabla "cuentas"), resuelto por nombre en el mismo momento en que ya se resuelven
    // Grupo1/Grupo2 al construir el ítem (ver B11_DocumentCalculator). Puramente aditivo:
    // no reemplaza tipoT_3Accout_String (el nombre sigue siendo lo que usa hoy toda la app
    // para guardar, mostrar y reportar) y no se usa todavía en el guardado en BD — ese sigue
    // resolviendo cuenta_id por su cuenta en B12_DocumentPersistence, exactamente igual que
    // antes de este cambio. Queda disponible en el objeto para cuando se necesite.
    public Long tipoT_14CuentaId_Long;

    private String columna1;
    private int columna2;
    private String columna3;
    private int columna4;


// metodo constructor

    public A3_2_TipoTransaccionesGetsYSets(String tipoT_1NumberDocument_String, String tipoT_2DocumentItems_String, String tipoT_3Accout_String, String tipoT_4Sign_String,
                                           Integer tipoT_5Value_Integer, String tipoT_6Description_String, String tipoT_7TimeOfProcess_String, Integer tipoT_8DateOfDocument_Integer,
                                           String tipoT_9DateUpdateDocument_String, String tipoT_10BalanceItems_String, String tipoT_11BalanceItemsClassification_String, String
                                         tipoT_12AccountWhitFlag_String, String tipoT_13ColumnaDisponible_String) {

        this.tipoT_1NumberDocument_String = tipoT_1NumberDocument_String;
        this.tipoT_2DocumentItems_String = tipoT_2DocumentItems_String;
        this.tipoT_3Accout_String = tipoT_3Accout_String;
        this.tipoT_4Sign_String = tipoT_4Sign_String;
        this.tipoT_5Value_Integer = tipoT_5Value_Integer;
        this.tipoT_6Description_String = tipoT_6Description_String;
        this.tipoT_7TimeOfProcess_String = tipoT_7TimeOfProcess_String;
        this.tipoT_8DateOfDocument_Integer = tipoT_8DateOfDocument_Integer;
        this.tipoT_9DateUpdateDocument_String = tipoT_9DateUpdateDocument_String;
        this.tipoT_10BalanceItems_String = tipoT_10BalanceItems_String;
        this.tipoT_11BalanceItemsClassification_String = tipoT_11BalanceItemsClassification_String;
        this.tipoT_12AccountWhitFlag_String = tipoT_12AccountWhitFlag_String;
        this.tipoT_13ColumnaDisponible_String = tipoT_13ColumnaDisponible_String;
    }

    public A3_2_TipoTransaccionesGetsYSets(String tipoT_3Accout_String, String tipoT_4Sign_String,
                                           Integer tipoT_5Value_Integer, String tipoT_10BalanceItems_String, String tipoT_11BalanceItemsClassification_String) {

        this.tipoT_3Accout_String = tipoT_3Accout_String;
        this.tipoT_4Sign_String = tipoT_4Sign_String;
        this.tipoT_5Value_Integer = tipoT_5Value_Integer;
        this.tipoT_10BalanceItems_String = tipoT_10BalanceItems_String;
        this.tipoT_11BalanceItemsClassification_String = tipoT_11BalanceItemsClassification_String;

            }

    public A3_2_TipoTransaccionesGetsYSets() {

    }

    public String tipoTget_1DocumentoMetodoEnA5() {return tipoT_1NumberDocument_String; }
    public String tipoTget_2ItemDocMetodoEnA5() {
        return tipoT_2DocumentItems_String;
    }
    public String tipoTget_3CuentaMetodoEnA5() {return tipoT_3Accout_String;  }
    public String tipoTget_4MasMenosMetodoEnA5() {
        return tipoT_4Sign_String;
    }
    public Integer tipoTget_5ValorMetodoEnA5() {
        return tipoT_5Value_Integer;
    }
    public String tipoTget_6DescripcionMetodoEnA5() {
        return tipoT_6Description_String;
    }
    public String tipoTget_7FechaYHoraMetodoEnA5() {
        return tipoT_7TimeOfProcess_String;
    }
    public Integer tipoTget_8FechaInicialMetodoEnA5() {
        return tipoT_8DateOfDocument_Integer;
    }
    public String tipoTget_9FechaModificacionMetodoEnA5() {return tipoT_9DateUpdateDocument_String;}
    public String tipoTget_10Grupo1MetodoEnA5() {return tipoT_10BalanceItems_String;}
    public String tipoTget_11Grupo2MetodoEnA5() {return tipoT_11BalanceItemsClassification_String;}
    public String tipoTget_12ColumnaDisponibleMetodoEnA5() {return tipoT_12AccountWhitFlag_String;}
    public String tipoTget_13ColumnaDisponibleMetodoEnA5() {return tipoT_13ColumnaDisponible_String;}
    public Long tipoTget_14CuentaIdMetodoEnA5() {return tipoT_14CuentaId_Long;}

    public void tipoTset_1DocumentoMetodoEnA5(String tipoT_1NumberDocument_String) {this.tipoT_1NumberDocument_String = tipoT_1NumberDocument_String;}
    public void tipoTset_2ItemDocMetodoEnA5(String tipoT_2DocumentItems_String) {this.tipoT_2DocumentItems_String = tipoT_2DocumentItems_String;}
    public void tipoTset_3CuentaMetodoEnA5(String tipoT_3Accout_String) {this.tipoT_3Accout_String = tipoT_3Accout_String;}
    public void tipoTset_4MasMenosMetodoEnA5(String tipoT_4Sign_String) {this.tipoT_4Sign_String = tipoT_4Sign_String;}
    public void tipoTset_5ValorMetodoEnA5(Integer tipoT_5Value_Integer) {this.tipoT_5Value_Integer = tipoT_5Value_Integer;}
    public void tipoTset_6DescripcionMetodoEnA5(String tipoT_6Description_String) {this.tipoT_6Description_String = tipoT_6Description_String;}
    public void tipoTset_7FechaYHoraMetodoEnA5(String tipoT_7TimeOfProcess_String) {this.tipoT_7TimeOfProcess_String = tipoT_7TimeOfProcess_String;}
    public void tipoTset_8FechaInicialMetodoEnA5(Integer tipoT_8DateOfDocument_Integer) {this.tipoT_8DateOfDocument_Integer = tipoT_8DateOfDocument_Integer;}
    public void tipoTset_9FechaModificacionMetodoEnA5(String tipoT_9DateUpdateDocument_String) {this.tipoT_9DateUpdateDocument_String = tipoT_9DateUpdateDocument_String;}
    public void tipoTset_10Grupo1MetodoEnA5(String tipoT_10BalanceItems_String) {this.tipoT_10BalanceItems_String = tipoT_10BalanceItems_String;}
    public void tipoTset_11Grupo2MetodoEnA5(String tipoT_11BalanceItemsClassification_String) {this.tipoT_11BalanceItemsClassification_String = tipoT_11BalanceItemsClassification_String;}
    public void tipoTset_12ColumnaDisponibleMetodoEnA5(String tipoT_12AccountWhitFlag_String) {this.tipoT_12AccountWhitFlag_String = tipoT_12AccountWhitFlag_String;}
    public void tipoTset_13ColumnaDisponibleMetodoEnA5(String tipoT_13ColumnaDisponible_String) {this.tipoT_13ColumnaDisponible_String = tipoT_13ColumnaDisponible_String;}
    public void tipoTset_14CuentaIdMetodoEnA5(Long tipoT_14CuentaId_Long) {this.tipoT_14CuentaId_Long = tipoT_14CuentaId_Long;}

    public A3_2_TipoTransaccionesGetsYSets(String columna1, int columna2, String columna3, int columna4) {

        this.columna1 = columna1;
        this.columna2 = columna2;
        this.columna3 = columna3;
        this.columna4 = columna4;

    }

    public String tipoTget_1DocumentoMetodoEnA52() { return columna1; }
    public int tipoTget_5ValorMetodoEnA52() { return columna2; }
    public String tipoTget_6DescripcionMetodoEnA52() { return columna3; }
    public int tipoTget_8FechaInicialMetodoEnA52() { return columna4; }


    // Campo para el estado del check (temporal, no en BD)
    private boolean isChecked = false;

    // Getter y Setter para isChecked
    public boolean isChecked() {
        return isChecked;
    }

    public void setChecked(boolean checked) {
        isChecked = checked;
    }

}
