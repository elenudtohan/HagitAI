package com.example.hagitai;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;

public class ForgotPasswordActivity extends AppCompatActivity {

    private EditText emailResetInput;
    private Button btnSendReset;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        mAuth = FirebaseAuth.getInstance();

        emailResetInput = findViewById(R.id.emailResetInput);
        btnSendReset = findViewById(R.id.btnSendReset);

        btnSendReset.setOnClickListener(v -> {
            String email = emailResetInput.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                emailResetInput.setError("Please enter your email");
                return;
            }

            // Send reset link via Firebase
            mAuth.sendPasswordResetEmail(email)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Password reset link sent! Check your email.",
                                    Toast.LENGTH_LONG).show();

                            // Option 1: Stay on screen
                            // Option 2: Go back to Login screen
                            finish(); // returns to LoginActivity
                        } else {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    "Failed to send reset link. Please try again.",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}
