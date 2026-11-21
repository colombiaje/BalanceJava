package A1BASES;

public enum A9_2_BackupFile {
    // ⭐ RETIRADOS E4 (18-nov, Jorge): 111–116, CSV del sistema anterior de borradores de F1.
    // ⭐ RETIRADOS (21-nov, Jorge, paso 3 respaldos): 121, 211, 222_1/222_2, 231/232, 411, 421,
    // 431, 442_1/442_2, 451_1/451_2 y 452_1/452_2 — eran copias CSV de tablas para respaldar o
    // restaurar. Los respaldos son la copia completa .db (A32_Respaldos).

    /* ─────────── ACCOUNTS ─────────── */
    CSV_ACCOUNTS_SHEETS_SYNCHRONIZED("221_BackupSheetsCuentasSincronizadas", A9_1_FileType.CSV),
    // ⭐ NUEVO (3-nov, BD v24, renta R1): catálogo de renglones de renta, sube con las cuentas.
    CSV_RENGLONES_RENTA("213_CatalogoRenglonesRenta", A9_1_FileType.CSV),

    /* ─────────── CLOSURES ─────────── */
    // 422 y 432: saldos al cerrar; el cierre los usa como paso intermedio (se conservan).
    CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_ALL_ACCOUNTS("422_saldos_al_cerrar_todas_las_cuentas.csv", A9_1_FileType.CSV),
    // ⭐ NUEVO (3-nov, BD v25, renta R3): todas las fotos anuales de renta; se genera y sube a
    // la carpeta de cierres de Drive en cada cierre total.
    CSV_FOTO_RENTA_ANUAL("423_FotoRentaCierre", A9_1_FileType.CSV),
    // ⭐ NUEVO (7-nov, renta R6): ajustes a años cerrados; el nombre lleva los años afectados.
    CSV_AJUSTES_RENTA("425_AjustesRentaCierre", A9_1_FileType.CSV),
    // ⭐ NUEVO (9-nov, renta R7): checklist de soportes; el nombre lleva los años.
    CSV_SOPORTES_RENTA("426_SoportesRenta", A9_1_FileType.CSV),
    // (4-nov) El reporte de renta (424) ya no usa un nombre fijo: A17_ReporteRentaDialogo lo arma
    // con el año y la vista, ej. "424_Renta_2026_cierre_depurada".
    CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_SOME_ACCOUNTS("432_saldos_al_cerrar_algunas cuentas.csv", A9_1_FileType.CSV),
    CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED("441_transacciones_sheets.csv", A9_1_FileType.CSV);

    // ─────────── IMPLEMENTACIÓN ───────────
    public static final String BALANCE_FOLDER = "Balance";
    private final String baseName;
    private final A9_1_FileType fileType;

    A9_2_BackupFile(String baseName, A9_1_FileType a9_1_FileType) {
        this.baseName = baseName;
        this.fileType = a9_1_FileType;
    }

    // ⭐ NUEVO (18-nov, Jorge): prefijo del equipo (13T, PAD5…) en todos los respaldos. Lo fija
    // C2_ActivityMenu al arrancar (A32_Respaldos.equipo). Los dos CSV que llegan DESDE Sheets
    // (221 y 441) no lo llevan: son copias de Sheets, no respaldos de un equipo.
    public static String prefijoEquipo = "";

    public static String prefijo() {
        return prefijoEquipo == null || prefijoEquipo.isEmpty() ? "" : prefijoEquipo + "_";
    }

    public String getFileName() {
        if (this == CSV_ACCOUNTS_SHEETS_SYNCHRONIZED || this == CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED) {
            return baseName + fileType.getExtension();
        }
        return prefijo() + baseName + fileType.getExtension();
    }

    // ⭐ NUEVO (18-nov): nombre base con el prefijo del equipo (para los nombres que se arman
    // agregando años o estados, como los de renta).
    public String getBaseNameConEquipo() {
        return prefijo() + baseName;
    }

    public A9_1_FileType getFileType() {
        return fileType;
    }

    public String getBaseName() {
        return baseName;
    }
}

