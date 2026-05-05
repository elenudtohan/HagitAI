package com.example.hagitai;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    // ── UI components (ids match XML exactly) ────────────────────────────────
    private EditText inputFirstName, inputLastName, inputEmail;
    private EditText inputPassword, inputConfirmPassword;
    private ImageView togglePassword, toggleConfirmPassword;
    private CheckBox checkboxTerms;
    private Button btnCreateAccount;
    private TextView btnSignIn;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // ── Password visibility state ─────────────────────────────────────────────
    private boolean isPasswordVisible        = false;
    private boolean isConfirmPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        // Firebase
        mAuth  = FirebaseAuth.getInstance();
        db     = FirebaseFirestore.getInstance();

        // Bind views — every id taken directly from the XML
        inputFirstName          = findViewById(R.id.inputFirstName);
        inputLastName           = findViewById(R.id.inputLastName);
        inputEmail              = findViewById(R.id.inputEmail);
        inputPassword           = findViewById(R.id.inputPassword);
        inputConfirmPassword    = findViewById(R.id.inputConfirmPassword);
        togglePassword          = findViewById(R.id.togglePassword);
        toggleConfirmPassword   = findViewById(R.id.toggleConfirmPassword);
        checkboxTerms           = findViewById(R.id.checkboxTerms);
        btnCreateAccount        = findViewById(R.id.btnCreateAccount);
        btnSignIn               = findViewById(R.id.btnSignIn);

        // ── Password toggle ───────────────────────────────────────────────────
        togglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            toggleVisibility(inputPassword, togglePassword, isPasswordVisible);
        });

        toggleConfirmPassword.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            toggleVisibility(inputConfirmPassword, toggleConfirmPassword, isConfirmPasswordVisible);
        });

        // ── Create account ────────────────────────────────────────────────────
        btnCreateAccount.setOnClickListener(v -> registerUser());

        // ── Already have an account → go to Login ─────────────────────────────
        btnSignIn.setOnClickListener(v -> {
            startActivity(new Intent(SignupActivity.this, LoginActivity.class));
            finish();
        });
    }

    // ── Registration logic ────────────────────────────────────────────────────

    private void registerUser() {
        String firstName  = inputFirstName.getText().toString().trim();
        String lastName   = inputLastName.getText().toString().trim();
        String email      = inputEmail.getText().toString().trim();
        String password   = inputPassword.getText().toString().trim();
        String confirm    = inputConfirmPassword.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(firstName)) {
            inputFirstName.setError("First name is required");
            inputFirstName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(lastName)) {
            inputLastName.setError("Last name is required");
            inputLastName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email)) {
            inputEmail.setError("Email is required");
            inputEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            inputPassword.setError("Password is required");
            inputPassword.requestFocus();
            return;
        }
        if (password.length() < 6) {
            inputPassword.setError("Password must be at least 6 characters");
            inputPassword.requestFocus();
            return;
        }
        if (!password.equals(confirm)) {
            inputConfirmPassword.setError("Passwords do not match");
            inputConfirmPassword.requestFocus();
            return;
        }
        if (!checkboxTerms.isChecked()) {
            Toast.makeText(this, "Please agree to the Terms of Service", Toast.LENGTH_SHORT).show();
            return;
        }

        String fullName = firstName + " " + lastName;

        // Create Firebase user
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            // Set display name in Firebase Auth
                            UserProfileChangeRequest profileUpdate =
                                    new UserProfileChangeRequest.Builder()
                                            .setDisplayName(fullName)
                                            .build();
                            user.updateProfile(profileUpdate);

                            // Save user details in Firestore
                            Map<String, Object> userData = new HashMap<>();
                            userData.put("name",      fullName);
                            userData.put("firstName", firstName);
                            userData.put("lastName",  lastName);
                            userData.put("email",     email);
                            userData.put("xp",        0);
                            userData.put("level",     0);
                            userData.put("debates",   0);
                            userData.put("wins",      0);

                            db.collection("users").document(user.getUid()).set(userData)
                                    .addOnCompleteListener(dbTask -> {
                                        Toast.makeText(this, "Account created successfully!", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(SignupActivity.this, HomeActivity.class));
                                        finish();
                                    });
                        }
                    } else {
                        String errorMsg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Registration failed.";
                        Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ── Helper: toggle password visibility ───────────────────────────────────

    private void toggleVisibility(EditText field, ImageView icon, boolean visible) {
        if (visible) {
            // Show password
            field.setTransformationMethod(null);
            icon.setImageResource(R.drawable.ic_eye); // use your "eye open" drawable
        } else {
            // Hide password
            field.setTransformationMethod(PasswordTransformationMethod.getInstance());
            icon.setImageResource(R.drawable.ic_eye);     // use your "eye closed" drawable
        }
        // Keep cursor at end
        field.setSelection(field.getText().length());
    }
}