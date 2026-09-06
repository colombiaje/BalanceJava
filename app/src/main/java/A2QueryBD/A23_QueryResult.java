    package A2QueryBD;

    import java.util.ArrayList;

    import A1BASES.A3_2_TipoTransaccionesGetsYSets;

    public class A23_QueryResult<T> {
        private int size;
        private ArrayList<T> datos;
        private String mensaje;
        private int suma;
        private String ultimoDocumento;
        private String[] atributosCuenta;

        // Constructor con suma
        public A23_QueryResult(int size, ArrayList<T> datos, String mensaje, int suma) {
            this.size = size;
            this.datos = (datos != null) ? datos : new ArrayList<>(); // Asignar lista vacía si datos es null
            this.mensaje = mensaje;
            this.suma = suma;
        }

        // Constructor sin suma
        public A23_QueryResult(int size, ArrayList<T> datos, String mensaje) {
            this.size = size;
            this.datos = (datos != null) ? datos : new ArrayList<>(); // Asignar lista vacía si datos es null
            this.mensaje = mensaje;
            this.suma = 0;  // Si no se proporciona suma, asignamos 0
        }

        // Constructor para manejar solo el último documento
        public A23_QueryResult(int size, String ultimoDocumento, String mensaje) {
            this.size = size;
            this.datos = new ArrayList<>(); // Inicializar datos como lista vacía
            this.ultimoDocumento = ultimoDocumento;
            this.mensaje = mensaje;
        }

        // Constructor para manejar un array de strings
        public A23_QueryResult(String[] atributosCuenta, String mensaje) {
            this.size = (atributosCuenta != null) ? atributosCuenta.length : 0;
            this.atributosCuenta = atributosCuenta;
            this.mensaje = mensaje;
        }

        public A23_QueryResult(ArrayList<A3_2_TipoTransaccionesGetsYSets> listaResultados) {
        }

        // Getters
        public int getSize() {
            return size;
        }

        public ArrayList<T> getDatos() {
            return datos;
        }

        public String getMensaje() {
            return mensaje;
        }

        public int getSuma() {
            return suma;
        }

        public String getUltimoDocumento() {
            return ultimoDocumento;
        }

        public String[] getAtributosCuenta() {
            return atributosCuenta;
        }

    }
