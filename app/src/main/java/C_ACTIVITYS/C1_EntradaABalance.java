package C_ACTIVITYS;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.jj.appbalancev31.R;

import java.util.ArrayList;

//import a4.balance.R;

public class C1_EntradaABalance extends AppCompatActivity {

    EditText password_XEt, usuario_XEt;
    Button entrarALaApp_XBt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.c1_activity_main);
        //Toolbar toolbar = findViewById(R.id.toolbar);
       // setSupportActionBar(toolbar);

        usuario_XEt= findViewById(R.id.usuario_XEt);
        password_XEt= findViewById(R.id.password_XEt);
        entrarALaApp_XBt= findViewById(R.id.entrarALaApp_XBt);

        entrarALaApp_XBt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (usuario_XEt.getText().toString().equals("7600") && password_XEt.getText().toString().equals("7600")) {

                    Toast.makeText(C1_EntradaABalance.this, "Ok Entrada", Toast.LENGTH_SHORT).show();
                    Intent intent= new Intent(getApplication(), C2_ActivityMenu.class);
                    startActivity(intent);

                    //mostrar un dialogo que se cancela por tiempo

                    usuario_XEt.setText("");
                    password_XEt.setText("");

                    /*AlertDialog.Builder builder = new AlertDialog.Builder(C11_EntradaABalance.this
                    );

                    builder.setIcon(R.drawable.ic_check);
                    builder.setTitle("Inicia sesión con éxito !!!");
                    builder.setMessage("Bienvenido a Balance ...");

                    builder.setNegativeButton("Si", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.cancel();
                            Toast.makeText(C11_EntradaABalance.this, "Ok Entrada", Toast.LENGTH_SHORT).show();
                            Intent intent= new Intent(getApplication(), C21_ActivityMenu.class);
                            startActivity(intent);

                        }
                    });

                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();*/
                } else {
                    Toast.makeText(C1_EntradaABalance.this, "Nombre de usuario y contraseña inválidos", Toast.LENGTH_SHORT).show();
                }
            }
        });


        //ViewPager viewPager = findViewById(R.id.viewPager);

        AuthenticationPagerAdapter pagerAdapter = new AuthenticationPagerAdapter(getSupportFragmentManager());
    }

    class AuthenticationPagerAdapter extends FragmentPagerAdapter {
        private ArrayList<Fragment> fragmentList = new ArrayList<>();

        public AuthenticationPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return fragmentList.get(i);
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        void addFragmet(Fragment fragment) {
            fragmentList.add(fragment);
        }
    }
}
