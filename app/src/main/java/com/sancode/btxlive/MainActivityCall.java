package com.sancode.btxlive;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivityCall extends AppCompatActivity {

    TextView edtName;
    Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_activity_call);

        edtName = (TextView) findViewById(R.id.edtName);
        btnLogin= (Button) findViewById(R.id.btnLogin);

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent= new Intent(getApplicationContext(),MainActivity.class);
                String userName=edtName.getText().toString();
                intent.putExtra("userName",userName);
                startActivity(intent);
            }
        });
    }
}
