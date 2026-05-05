package com.example.hagitai;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;
import android.text.method.PasswordTransformationMethod;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {

    // ── Views ─────────────────────────────────────────────────────────────────
    private EditText     emailInput, passwordInput;
    private Button       btnLogin;
    private ImageView    togglePassword;
    private boolean      isPasswordVisible = false;
    private TextView     btnSignup, forgotPasswordText;
    private CheckBox     rememberCheck;
    private LinearLayout btnGoogleSignIn;

    // ── Firebase ──────────────────────────────────────────────────────────────
    private FirebaseAuth       mAuth;
    private GoogleSignInClient googleSignInClient;
    private SharedPreferences  sharedPreferences;

    // ── Google Sign-In launcher ───────────────────────────────────────────────
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() == null) return;
                Task<GoogleSignInAccount> task =
                        GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    firebaseAuthWithGoogle(account);
                } catch (ApiException e) {
                    String msg;
                    switch (e.getStatusCode()) {
                        case 10:    msg = "Developer error — add SHA-1 in Firebase Console."; break;
                        case 12500: msg = "Enable Google Sign-In in Firebase Console.";       break;
                        case 12501: msg = "Sign-In cancelled.";                               break;
                        default:    msg = "Google Sign-In failed (code " + e.getStatusCode() + ")"; break;
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();

        // ── Bind views ────────────────────────────────────────────────────────
        emailInput         = findViewById(R.id.emailInput);
        passwordInput      = findViewById(R.id.passwordInput);
        togglePassword     = findViewById(R.id.togglePassword);
        btnLogin           = findViewById(R.id.btnLogin);
        btnSignup          = findViewById(R.id.btnSignup);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        rememberCheck      = findViewById(R.id.rememberCheck);
        btnGoogleSignIn    = findViewById(R.id.btnGoogleSignIn);

        // ── Remember Me ───────────────────────────────────────────────────────
        sharedPreferences = getSharedPreferences("loginPrefs", MODE_PRIVATE);
        if (sharedPreferences.getBoolean("remember", false)) {
            emailInput.setText(sharedPreferences.getString("email", ""));
            passwordInput.setText(sharedPreferences.getString("password", ""));
            rememberCheck.setChecked(true);
        }

        // ── Configure Google Sign-In ──────────────────────────────────────────
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        // ── Listeners ─────────────────────────────────────────────────────────
        togglePassword.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                // Show password
                passwordInput.setTransformationMethod(null);
            } else {
                // Hide password
                passwordInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            // Keep cursor at end
            passwordInput.setSelection(passwordInput.getText().length());
        });

        btnLogin.setOnClickListener(v -> loginUser());

        btnGoogleSignIn.setOnClickListener(v ->
                // Sign out first so account chooser always appears
                googleSignInClient.signOut().addOnCompleteListener(task ->
                        googleLauncher.launch(googleSignInClient.getSignInIntent())));

        btnSignup.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, SignupActivity.class)));

        forgotPasswordText.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, ForgotPasswordActivity.class)));
    }

    // ── Email / Password login ────────────────────────────────────────────────

    private void loginUser() {
        String email    = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(email)) {
            emailInput.setError("Email is required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError("Password is required");
            return;
        }

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        handleRememberMe(email, password);
                        fetchNameAndNavigate(mAuth.getCurrentUser());
                    } else {
                        Toast.makeText(this,
                                "Login failed. Check your credentials.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ── Google Sign-In → Firebase Auth ───────────────────────────────────────

    private void firebaseAuthWithGoogle(GoogleSignInAccount account) {
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);

        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        Toast.makeText(this,
                                "Google authentication failed.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user == null) { navigateToHome(); return; }

                    // Check if this is a brand-new user
                    boolean isNewUser = task.getResult().getAdditionalUserInfo() != null
                            && task.getResult().getAdditionalUserInfo().isNewUser();

                    if (isNewUser) {
                        saveGoogleUserToDatabase(user, account);
                    } else {
                        // Existing user — save name locally and proceed
                        String name = account.getDisplayName();
                        if (name != null) {
                            getSharedPreferences("userPrefs", MODE_PRIVATE)
                                    .edit().putString("username", name).apply();
                        }
                        navigateToHome();
                    }
                });
    }

    // ── Save new Google user to Realtime Database ─────────────────────────────

    private void saveGoogleUserToDatabase(FirebaseUser user, GoogleSignInAccount account) {
        String fullName = account.getDisplayName() != null ? account.getDisplayName() : "";
        String email    = account.getEmail()       != null ? account.getEmail()       : "";

        // Split display name into first / last
        String firstName = fullName, lastName = "";
        int spaceIdx = fullName.indexOf(" ");
        if (spaceIdx != -1) {
            firstName = fullName.substring(0, spaceIdx);
            lastName  = fullName.substring(spaceIdx + 1);
        }

        Map<String, Object> userData = new HashMap<>();
        userData.put("name",      fullName);
        userData.put("firstName", firstName);
        userData.put("lastName",  lastName);
        userData.put("email",     email);
        userData.put("xp",        0);
        userData.put("level",     0);
        userData.put("debates",   0);
        userData.put("wins",      0);

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .set(userData)
                .addOnCompleteListener(dbTask -> {
                    getSharedPreferences("userPrefs", MODE_PRIVATE)
                            .edit().putString("username", fullName).apply();
                    navigateToHome();
                });
    }

    // ── Fetch name from Firestore (email login) ───────────────────────

    private void fetchNameAndNavigate(FirebaseUser user) {
        if (user == null) { navigateToHome(); return; }

        FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.getUid())
                .get()
                .addOnCompleteListener(nameTask -> {
                    if (nameTask.isSuccessful() && nameTask.getResult().exists()) {
                        String fullName = nameTask.getResult().getString("name");
                        if (fullName != null) {
                            getSharedPreferences("userPrefs", MODE_PRIVATE)
                                    .edit().putString("username", fullName).apply();
                        }
                    }
                    navigateToHome();
                });
    }

    // ── Remember Me ───────────────────────────────────────────────────────────

    private void handleRememberMe(String email, String password) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        if (rememberCheck.isChecked()) {
            editor.putBoolean("remember", true);
            editor.putString("email",    email);
            editor.putString("password", password);
        } else {
            editor.clear();
        }
        editor.apply();
    }

    // ── Navigate to Home ──────────────────────────────────────────────────────

    private void navigateToHome() {
        Toast.makeText(this, "Login successful!", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // ── Auto-login ────────────────────────────────────────────────────────────

    @Override
    protected void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(LoginActivity.this, HomeActivity.class));
            finish();
        }
    }
}