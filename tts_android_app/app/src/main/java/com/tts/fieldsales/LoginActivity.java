package com.tts.fieldsales;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class LoginActivity extends Activity {

    private EditText urlInput, usernameInput, passwordInput;
    private Button loginBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        urlInput = findViewById(R.id.urlInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginBtn = findViewById(R.id.loginBtn);

        // Pre-fill from SharedPreferences
        SharedPreferences prefs = getSharedPreferences("TTS_Prefs", MODE_PRIVATE);
        urlInput.setText(prefs.getString("odoo_url", ""));
        usernameInput.setText(prefs.getString("username", ""));
        passwordInput.setText(prefs.getString("password", ""));

        loginBtn.setOnClickListener(v -> {
            String url = urlInput.getText().toString().trim();
            String username = usernameInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Ensure URL has protocol
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            // Save credentials
            prefs.edit()
                    .putString("odoo_url", url)
                    .putString("username", username)
                    .putString("password", password)
                    .apply();

            Toast.makeText(this, "Settings Saved", Toast.LENGTH_SHORT).show();

            // Start MainActivity
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
    }
}
