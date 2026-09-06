package A1BASES;

public enum A9_1_FileType {
    CSV(".csv"),
    PDF(".pdf"),
    XLSX(".xlsx"),
    TXT(".txt");

    private final String extension;

    A9_1_FileType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }
}

