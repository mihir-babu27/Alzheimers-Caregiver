package com.mihir.alzheimerscaregiver;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.mihir.alzheimerscaregiver.auth.FirebaseAuthManager;
import com.mihir.alzheimerscaregiver.auth.IAuthManager;
import com.mihir.alzheimerscaregiver.settings.SettingsViewModel;

public class SettingsActivity extends AppCompatActivity {

    private TextView patientIdValue;
    private TextInputEditText inputName;
    private TextInputEditText inputEmail;
    private TextInputEditText inputCurrentPassword;
    private TextInputEditText inputNewPassword;
    private View progress;

    private FirebaseAuth auth;
    private FirebaseAuthManager authManager;
    
    private SettingsViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        auth = FirebaseAuth.getInstance();
        authManager = new FirebaseAuthManager();

        patientIdValue = findViewById(R.id.patientIdValue);
        inputName = findViewById(R.id.inputName);
        inputEmail = findViewById(R.id.inputEmail);
        inputCurrentPassword = findViewById(R.id.inputCurrentPassword);
        inputNewPassword = findViewById(R.id.inputNewPassword);
        progress = findViewById(R.id.progress);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        patientIdValue.setText(user.getUid());

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        viewModel.getState().observe(this, ui -> {
            if (ui == null) return;
            progress.setVisibility(ui.loading ? View.VISIBLE : View.GONE);
            findViewById(R.id.saveButton).setEnabled(!ui.loading);
            inputName.setEnabled(!ui.loading);
            inputEmail.setEnabled(!ui.loading);
            inputCurrentPassword.setEnabled(!ui.loading);
            inputNewPassword.setEnabled(!ui.loading);
            if (ui.message != null) showFeedback(findViewById(R.id.saveButton), ui.message, ui.success);
        });

        viewModel.loadInitial((uid, data) -> {
            patientIdValue.setText(uid);
            String n = data.get("name");
            String e = data.get("email");
            if (!TextUtils.isEmpty(n)) inputName.setText(n);
            if (!TextUtils.isEmpty(e)) inputEmail.setText(e);
        });

        findViewById(R.id.saveButton).setOnClickListener(v -> saveChanges(v));

        // Logout button logic
        findViewById(R.id.logoutButton).setOnClickListener(v -> {
            // Use authManager to sign out
            authManager.signOut();
            // Navigate to authentication activity and clear back stack
            android.content.Intent intent = new android.content.Intent(SettingsActivity.this, com.mihir.alzheimerscaregiver.auth.AuthenticationActivity.class);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK | android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void saveChanges(View anchor) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String name = text(inputName);
        String email = text(inputEmail);
        String currentPassword = text(inputCurrentPassword);
        String newPassword = text(inputNewPassword);
        viewModel.save(name, email, currentPassword, newPassword);
    }

    private static String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private void showSnack(View anchor, String msg) {
        Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG).show();
    }

    private void showFeedback(View anchor, String msg, boolean success) {
        if (success) {
            Snackbar.make(anchor, msg, Snackbar.LENGTH_LONG).show();
        } else {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Update error")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
        }
    }
}


