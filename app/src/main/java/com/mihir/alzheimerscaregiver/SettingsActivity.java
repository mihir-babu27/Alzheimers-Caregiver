package com.mihir.alzheimerscaregiver;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
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
import com.mihir.alzheimerscaregiver.utils.LanguagePreferenceManager;

public class SettingsActivity extends AppCompatActivity {

    private TextView patientIdValue;
    private TextInputEditText inputName;
    private TextInputEditText inputEmail;
    private TextInputEditText inputCurrentPassword;
    private TextInputEditText inputNewPassword;
    private View progress;
    private Spinner languageSpinner;

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
        languageSpinner = findViewById(R.id.languageSpinner);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            finish();
            return;
        }

        patientIdValue.setText(user.getUid());
        
        // Initialize language preference spinner
        setupLanguageSpinner();

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
    
    /**
     * Setup the language preference spinner with supported languages
     */
    private void setupLanguageSpinner() {
        // Create adapter with supported languages
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this,
            android.R.layout.simple_spinner_item,
            LanguagePreferenceManager.SUPPORTED_LANGUAGES
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(adapter);
        
        // Set current selection based on saved preference
        String currentLanguage = LanguagePreferenceManager.getPreferredLanguage(this);
        int currentIndex = LanguagePreferenceManager.getLanguageIndex(currentLanguage);
        languageSpinner.setSelection(currentIndex);
        
        // Set up selection listener to save preference immediately
        languageSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedLanguage = LanguagePreferenceManager.SUPPORTED_LANGUAGES[position];
                String currentLanguage = LanguagePreferenceManager.getPreferredLanguage(SettingsActivity.this);
                
                // Only save if the selection has actually changed (avoid saving on initial setup)
                if (!selectedLanguage.equals(currentLanguage)) {
                    LanguagePreferenceManager.setPreferredLanguage(SettingsActivity.this, selectedLanguage);
                    showSnack(languageSpinner, "Story language preference updated to " + selectedLanguage);
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }
}


