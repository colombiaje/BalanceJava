package A1BASES;

public enum A9_2_BackupFile {

    /* ─────────── DOCUMENTS ─────────── */
    CSV_DOCUMENT_TRANSACTIONS("121_BackupTodasLasTransaccionesCrud",A9_1_FileType.CSV),
    CSV_CREATE_HEADER("111_backupOfTheCreateNewDocumentHeader", A9_1_FileType.CSV),
    CSV_CREATE_RECORDS("112_backupOfTheCreateNewDocumentRecords", A9_1_FileType.CSV),
    CSV_TEMPLATE_HEADER("113_backupOfTheTe1mplateDocumentHeader", A9_1_FileType.CSV),
    CSV_TEMPLATE_RECORDS("114_backupOfTheTemplateDocumentRecords", A9_1_FileType.CSV),
    CSV_UPDATE_HEADER("115_backup_update_header", A9_1_FileType.CSV),
    CSV_UPDATE_RECORDS("116_backup_update_records", A9_1_FileType.CSV),

    /* ─────────── ACCOUNTS ─────────── */
    CSV_ACCOUNTS_BEFORE_STARTING_CLOSING("211_BackupInicialDeTodasLasCuentas", A9_1_FileType.CSV),
    CSV_ACCOUNTS_SHEETS_SYNCHRONIZED("221_BackupSheetsCuentasSincronizadas", A9_1_FileType.CSV),
    CSV_ACCOUNTS_BEFORE_CLOSING_RESTORING_SHEETS("222_1_BackupAntesDeRestaurarCuentasDesdeSheets", A9_1_FileType.CSV),
    CSV_ACCOUNTS_AFTER_CLOSING_RESTORING_SHEETS("222_2_BackupDespuesDeRestaurarCuentasDesdeSheets", A9_1_FileType.CSV),
    CSV_ACCOUNTS_BEFORE_RESTORING_BACKUP_INITIAL("231_BackupAntesDeRestaurarCuentasDesdeBackupInicial", A9_1_FileType.CSV),
    CSV_ACCOUNTS_AFTER_RESTORING_BACKUP_INITIAL("232_BackuPDespuesDeRestaurarCuentasDesdeBackupInicial", A9_1_FileType.CSV),

    /* ─────────── CLOSURES ─────────── */
    CSV_TRANSACTIONS_BEFORE_STARTING_CLOSURES("411_AntesDeIniciarCierres", A9_1_FileType.CSV),
    //"411 Backup antes de cierres.csv"
    CSV_TRANSACTIONS_BEFORE_CLOSING_ALL_ACCOUNTS("421_BackupAntesDeCerrarTodasLasCuentas", A9_1_FileType.CSV),
    //"421 Backup antes de cerrar todas las cuentas.csv"
    CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_ALL_ACCOUNTS("422_saldos_al_cerrar_todas_las_cuentas.csv", A9_1_FileType.CSV),
    //"422 Backup saldos de todas las cuentas.csv"
    CSV_TRANSACTIONS_BEFORE_CLOSING_SOME_ACCOUNTS("431_BackupAntesDeCerrarAlgunasCuentas", A9_1_FileType.CSV),
    //"431 Backup antes de cerrar algunas cuentas.csv"    CSV_TRANSACTIONS_BEFORE_CLOSING_RESTORING_SHEETS("441_BackupAntesDeBalanceDesdeSheets", A9_1_FileType.CSV),

    CSV_TRANSACTIONS_BALANCES_UPON_CLOSING_SOME_ACCOUNTS("432_saldos_al_cerrar_algunas cuentas.csv", A9_1_FileType.CSV),
    // "432 Backup saldos de algunas cuentas.csv"

    CSV_TRANSACTIONS_SHEETS_SYNCHRONIZED("441_transacciones_sheets.csv", A9_1_FileType.CSV),
    //"440 Backup Balance_desde_Sheets.csv"

    CSV_TRANSACTIONS_BEFORE_CLOSING_RESTORING_SHEETS("442_1_BackupAntesDeRestaurarSaldosInicialesSheetsTransacciones", A9_1_FileType.CSV),
    //"441 Backup antes de Balance_desde_Sheets.csv"
    CSV_TRANSACTIONS_AFTER_CLOSING_RESTORING_SHEETS("442_2_BackupDespuesDeRestaurarSaldosInicialesSheetsTransacciones", A9_1_FileType.CSV),
    CSV_TRANSACTIONS_BEFORE_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL("451_1_BackupAntesDeRestaurarTransacciones", A9_1_FileType.CSV),
    CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL("451_2_BackupDespuesDeRestaurarTransacciones", A9_1_FileType.CSV),
    CSV_TRANSACTIONS_BEFORE_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL_FROM_DRIVE("452_1_BackupAntesDeRestaurarTransaccionesDesdeDrive", A9_1_FileType.CSV),
    CSV_TRANSACTIONS_AFTER_RESTORING_BACKUP_INITIAL_CLOSURES_FROM_LOCAL_FROM_DRIVE("452_2_BackupDespuesDeRestaurarTransaccionesDesdeDrive", A9_1_FileType.CSV);

    // ─────────── IMPLEMENTACIÓN ───────────
    public static final String BALANCE_FOLDER = "Balance";
    private final String baseName;
    private final A9_1_FileType fileType;

    A9_2_BackupFile(String baseName, A9_1_FileType a9_1_FileType) {
        this.baseName = baseName;
        this.fileType = a9_1_FileType;
    }

    public String getFileName() {
        return baseName + fileType.getExtension();
    }

    public A9_1_FileType getFileType() {
        return fileType;
    }

    public String getBaseName() {
        return baseName;
    }
}

