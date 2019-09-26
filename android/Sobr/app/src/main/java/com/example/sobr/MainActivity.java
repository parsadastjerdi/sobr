package com.example.sobr;


import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.IdpResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Arrays;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity{

    private static final int RC_SIGN_IN = 123;

    Button login;
    Button createAccount;
    EditText emailText, passwordText;

    int dbID = 222;

    FirebaseDatabase fbdb;
    DatabaseReference dbrf;

    FirebaseAuth mAuth;
    FirebaseUser user = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        emailText = (EditText) findViewById(R.id.email);
        passwordText = (EditText) findViewById(R.id.password);
        login = (Button) findViewById(R.id.login);
        createAccount = (Button) findViewById(R.id.createAccount);

        FirebaseApp.initializeApp(this);

        fbdb = FirebaseDatabase.getInstance();
        dbrf = fbdb.getReference();

        mAuth = FirebaseAuth.getInstance();
    }

    public void createAccount(View view) {

        String email = emailText.getText().toString();
        String password = passwordText.getText().toString();

        mAuth.createUserWithEmailAndPassword(email,password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(task.isSuccessful()){
                            Toast.makeText(getApplicationContext(),"Account created",Toast.LENGTH_SHORT).show();
                            user = mAuth.getCurrentUser();
                        }
                        else {
                            Toast.makeText(getApplicationContext(),"Unable to create account",Toast.LENGTH_SHORT).show();
                        }
                    }
                });

    }


    public void login(View view) {

        String email = emailText.getText().toString();
        String password = passwordText.getText().toString();

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(getApplicationContext(),"Successfully logged in",Toast.LENGTH_SHORT).show();
                            user = mAuth.getCurrentUser(); //The user is signed in
                            startActivityForResult(new Intent(MainActivity.this, SobrActivity.class), RC_SIGN_IN);
                        } else {
                            Toast.makeText(getApplicationContext(),"Unable to log in",Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    public void onStart() {
        super.onStart();
        // Check if user is signed in (non-null) and update UI accordingly.
        user = mAuth.getCurrentUser();
    }
}