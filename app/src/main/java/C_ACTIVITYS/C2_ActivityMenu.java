package C_ACTIVITYS;

import static android.content.ContentValues.TAG;
import static A1BASES.A1_1_AyudanteBD.balanceSqlite_String_PSF;
import static A1BASES.A1_1_AyudanteBD.version1BalanceSqlite_int_PSF;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.navigation.NavigationView;
import com.google.api.services.drive.DriveScopes;
import com.jj.appbalancev31.R;

import java.io.InputStream;

import A1BASES.A1_1_AyudanteBD;
import B_FRAGMENTS.F1_CrudDocumento;
import B_FRAGMENTS.F2_Cuentas;
import B_FRAGMENTS.F4_Cierres;
import B_FRAGMENTS.F5_1_Indicadores;
import B_FRAGMENTS.F6_Calculadora;
//import a4.balance.R;

public class C2_ActivityMenu extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    public NavigationView navigationView;

    F6_Calculadora llamarF9;
    F1_CrudDocumento f1_1_transacciones_Fragment;
    int menu_id;
    FragmentManager fm = getSupportFragmentManager();

    Boolean isUserClickedBackButton = false;

    F1_CrudDocumento f1_1_transacciones;
    F4_Cierres f5_1_cierres;

    String directorio_String = Environment.getExternalStorageDirectory().getPath() + "/Balance/";

    //private static final String CLIENT_ID = "947251576983-2mgthn1emm7qihr3dftmr0g2u69279cl.apps.googleusercontent.com";

    // Cargar el archivo de credenciales
    //InputStream inputStream = getResources().openRawResource(R.raw.credentials);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.c2_1_activity_menu);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        NavigationView navigationView = findViewById(R.id.nav_view);
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navf1,
                R.id.navf2, R.id.navf3, R.id.navf5, R.id.navf99)
                .setDrawerLayout(drawer)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.contenedor_fragments_f0_Xf);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        navigationView.setItemIconTintList(null);

        llamarF9 = new F6_Calculadora();

        InputStream inputStream = getResources().openRawResource(R.raw.drive_credentials);

        final Handler handler2= new Handler();
        handler2.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    _113_activarRedMovilSiNoHayRedWifi();
                }
                catch (Exception e) {
                    //Toast.makeText(getApplication(), "Error de codigo (Excepcion)", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "_112_comprobarSeñalDeRedesWifiOMovil: "+" Error de codigo (Excepcion)");
                    e.printStackTrace();
                }
            }
        },10000);//empezara a ejecutarse

        cambiarElFragmentPredeterminado();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            //Toast.makeText(getApplicationContext(), "Autorizar el uso de la App en Drive", Toast.LENGTH_SHORT).show();
            _123_solicitudIniciarSesiónEnDrive();
        }, 4000); // 5 segundos

    }

    public void _123_solicitudIniciarSesiónEnDrive () {
        GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_FILE))
                .build();
        GoogleSignInClient client = GoogleSignIn.getClient(getApplicationContext(),signInOptions);
        startActivityForResult(client.getSignInIntent(),400);

        Log.d("instalar", "101: : "+"solicitud iniciar sesion en drive");
    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_adicional, menu);
        return true;
    }

    WifiManager wifiManager;
    boolean isWifiConn = false;

    public void _111_activarRedlWiFiSihayRedDisponible () {

        Toast.makeText(getApplicationContext(), "Esperando Wifi disponible " + isWifiConn, Toast.LENGTH_SHORT).show();

        wifiManager = (WifiManager) getApplication().getSystemService(Context.WIFI_SERVICE);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!wifiManager.isWifiEnabled()) {
                wifiManager.setWifiEnabled(true);
                //Toast.makeText(getApplication(), "Onn", Toast.LENGTH_SHORT).show();

            } else {
                wifiManager.setWifiEnabled(false);
                //Toast.makeText(getApplication(), "Off", Toast.LENGTH_SHORT).show();
            }
        } else {
            Intent panelIntent = new Intent(Settings.Panel.ACTION_WIFI);
            startActivityForResult(panelIntent, 1);
        }

    }

    boolean isMobileConn = false;
    public void _112_comprobarSeñalDeRedesWifiOMovil () {

        try {
            ConnectivityManager connMgr = (ConnectivityManager) getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);

            for (Network network : connMgr.getAllNetworks()) {
                NetworkInfo networkInfo = connMgr.getNetworkInfo(network);
                if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    isWifiConn = networkInfo.isConnected();
                    //Toast.makeText(getApplicationContext(), " Si hay red wifi disponible", Toast.LENGTH_SHORT).show();
                }
                if (networkInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
                    isMobileConn = networkInfo.isConnected();
                    //Toast.makeText(getApplicationContext(), " Si hay Red movil", Toast.LENGTH_SHORT).show();
                }
            }

            if(isWifiConn == false) {
                //Toast.makeText(getApplicationContext(),"No hay red wifi disponible", Toast.LENGTH_SHORT).show();
            }

            if(isWifiConn == false & isMobileConn == false) {
                //Toast.makeText(getApplicationContext(),"No hay red wifi disponible ni red Movil", Toast.LENGTH_SHORT).show();
            }
        }

        catch (Exception e) {

            //Toast.makeText(getApplication(), "Error de codigo (Excepcion)", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "_112_comprobarSeñalDeRedesWifiOMovil: "+" Error de codigo (Excepcion)");
            e.printStackTrace();
        }
    }

    public void _113_activarRedMovilSiNoHayRedWifi () {

        _112_comprobarSeñalDeRedesWifiOMovil();
        //opciones generadas despues de comprobar
        //1
        //cuando la wifi este activa subir el archivo
        if (isWifiConn == true) {
            //...
        }
        //21
        //activar la red mobil cuando la wifi y la red mobil no esten activas
        if (isWifiConn == false && isMobileConn == false) {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(C2_ActivityMenu.this);
                builder.setMessage("No hay Wifi. Necesita activar la red móvil.");
                builder.setTitle("¿ Asumir costo en Datos ?")
                        .setCancelable(false)
                        .setPositiveButton("Activar Red Movil",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Intent i = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);

                                        Toast.makeText(getApplicationContext(), "Esperando la activacion de datos: " + isWifiConn, Toast.LENGTH_SHORT).show();
                                        //22
                                        //si activa los datos empezara a ejecutarse a los 10 segundos la subida del archivo
                                        //puede suceder que aunque hay la opcion de activar los datos no haga la activacion
                                        final Handler handler= new Handler();
                                        handler.postDelayed(new Runnable() {
                                            @Override
                                            public void run() {
                                                _112_comprobarSeñalDeRedesWifiOMovil();
                                                if (isMobileConn == true) {
                                                    //...
                                                    Toast.makeText(getApplicationContext(), "Ya Hay red movil", Toast.LENGTH_SHORT).show();
                                                }
                                                if (isMobileConn == false) {
                                                    Toast.makeText(getApplicationContext(), "No hay red movil", Toast.LENGTH_SHORT).show();
                                                }
                                            }
                                        },5000);//empezara a ejecutarse
                                        startActivity(i);
                                    }
                                }
                        )
                        .setNegativeButton("No",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Toast.makeText(getApplicationContext(), "No se activo la Red de Datos.", Toast.LENGTH_SHORT).show();
                                    }
                                }
                        );
                AlertDialog alert = builder.create();
                alert.show();
            }
            catch (Exception e) {
            }
        }
        //31
        //la red mobil esta activa el usuario debe haceptar o rechazar asumir el costo de datos
        if (isWifiConn == false && isMobileConn == true) {
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(getApplicationContext());

                builder.setTitle("La red movil se encuanta activa");
                builder.setMessage("¿ Asume el costo de datos ?.")
                        .setCancelable(false)
                        .setPositiveButton("Si",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        //...
                                        Toast.makeText(getApplicationContext(), "Subir Archivo a Drive", Toast.LENGTH_SHORT).show();
                                    }
                                }
                        )
                        .setNegativeButton("No",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        Toast.makeText(getApplicationContext(), "El archivo no subio porque no se asume costo de datos", Toast.LENGTH_SHORT).show();
                                    }
                                }
                        );
                AlertDialog alert = builder.create();
                alert.show();
            }
            catch (Exception e) {
            }
        }
    }

    /*public void cambiarElFragmentPredeterminado () {

        //Cambiar el fragment predeterminado
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.contenedor_fragments_f0_Xf);  // Hostfragment
        NavInflater inflater = navHostFragment.getNavController().getNavInflater();
        NavGraph graph = inflater.inflate(R.navigation.mobile_navigation);
        graph.setStartDestination(R.id.navf1);
        navHostFragment.getNavController().setGraph(graph);

    }*/

    public void cambiarElFragmentPredeterminado() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.contenedor_fragments_f0_Xf);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            navController.popBackStack(); // Limpia el backstack
            navController.navigate(R.id.navf1); // Va directamente al fragmento inicial
        }
    }



    @Override
    public void onBackPressed() {
        if(!isUserClickedBackButton) {
            Toast.makeText(this, "Esta presionando el boton atras", Toast.LENGTH_SHORT).show();
            isUserClickedBackButton = true;

            new AlertDialog.Builder(C2_ActivityMenu.this)
                    //.setIcon(R.drawable.alacran)
                    .setTitle("¿ Salir de la aplicación ?")
                    .setCancelable(false)
                    .setNegativeButton("No", null)
                    .setPositiveButton("Si", new DialogInterface.OnClickListener() {// un listener que al pulsar, cierre la aplicacion

                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    }).show();
        }


        else {
            super.onBackPressed();

        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.contenedor_fragments_f0_Xf);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

    //Este es el menu de la Action Bar

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item == null || item.getTitle() == null) {
            return super.onOptionsItemSelected(item);  // Maneja el caso donde 'item' o su título son null
        }

        String title = item.getTitle().toString();  // Obtener el título del item como String

        switch (title) {
            case "Eliminar Documentos":
                // Acción para "Eliminar Documentos"
                return true;

            case "Calculadora":
                FragmentTransaction f9 = fm.beginTransaction();
                f9.replace(R.id.contenedor_fragments_f0_Xf, new F6_Calculadora()).commit();
                return true;

            case "Todas las transacciones":
                FragmentTransaction f1_2 = fm.beginTransaction();
                //f1_2.replace(R.id.contenedor_fragments_f0_Xf, new zzzF3_4_VeTodasLasTransacciones()).commit();
                return true;

            case "Indicadores":
                FragmentTransaction f61 = fm.beginTransaction();
                f61.replace(R.id.contenedor_fragments_f0_Xf, new F5_1_Indicadores()).commit();
                return true;

            case "Cuentas":
                FragmentTransaction f2 = fm.beginTransaction();
                f2.replace(R.id.contenedor_fragments_f0_Xf, new F2_Cuentas()).commit();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*@Override
    protected void onResume() {
        super.onResume();

        // Verificar si la condición se cumple antes de ejecutar
        if (cumpleCondicion()) {
            ejecutarBackupDesdeFragment();
            Toast.makeText(getApplicationContext(), "Se ejecuto el backup", Toast.LENGTH_SHORT).show();
            Log.d("cuentas", "onResume 101: "+numeroDeRegistros_Long);
        }
    }*/

    private void ejecutarBackupDesdeFragment() {
        // Obtener la instancia del fragmento
        F2_Cuentas fragment = (F2_Cuentas) getSupportFragmentManager().findFragmentByTag("F21_Cuentas");

        if (fragment != null) {
            fragment._3_backupAndRestoreDialog();
        }
    }

    // Método de condición (ajústalo según tu lógica)
    private boolean cumpleCondicion() {
        // Aquí debes colocar la lógica real de tu condición
        verNumeroDeRegistrosCuentas();
        if (numeroDeRegistros_Long == 0) {
        }
        return true; // Cambia esto según lo que necesites
    }

    long numeroDeRegistros_Long;
    private long verNumeroDeRegistrosCuentas () {

        A1_1_AyudanteBD ayudanteBD_Class = new A1_1_AyudanteBD(getApplicationContext(), balanceSqlite_String_PSF,null, version1BalanceSqlite_int_PSF);

        SQLiteDatabase sqLiteDataBase_Abstracta = ayudanteBD_Class.getReadableDatabase();
        numeroDeRegistros_Long= DatabaseUtils.queryNumEntries(sqLiteDataBase_Abstracta,"cuentas");
        sqLiteDataBase_Abstracta.close();

        return  numeroDeRegistros_Long;

    }

    @Override
    public void onStart() {
        super.onStart();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            //Autorizacion de uso de almacenamiento del telefono
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {

                    //Toast.makeText(getApplicationContext(), "Autorizar el uso de la App en Drive", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    // Usamos getActivity() para acceder al contexto de la actividad
                    startActivityForResult(intent, 1); // 1 es un código arbitrario para el request

                }
            }

        }, 8000); // 5 segundos

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) { // 1 es el código que usaste al pedir el permiso
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (Environment.isExternalStorageManager()) {
                    //if (Environment.isExternalStorageManager()) {
                    Log.e("Permisos", "Acceso concedido");
                } else {
                    Log.e("Permisos", "Acceso denegado");
                }
            }
        }
    }
}
