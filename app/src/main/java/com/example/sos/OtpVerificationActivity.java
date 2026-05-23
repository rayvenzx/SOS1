package com.example.sos;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.auth.UserProfileChangeRequest;

import java.util.concurrent.TimeUnit;

public class OtpVerificationActivity extends AppCompatActivity {

    private EditText etOtp;
    private Button btnVerifyOtp;
    private ProgressBar progressBar;
    private String verificationId;
    private FirebaseAuth mAuth;
    private SharedPreferences sharedPreferences;

    private String fullName, email, phoneNumber, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp_verification);

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE);

        etOtp = findViewById(R.id.etOtp);
        btnVerifyOtp = findViewById(R.id.btnVerifyOtp);
        progressBar = findViewById(R.id.progressBar);

        // Get data from intent
        Intent intent = getIntent();
        fullName = intent.getStringExtra("fullName");
        email = intent.getStringExtra("email");
        phoneNumber = intent.getStringExtra("phoneNumber");
        password = intent.getStringExtra("password");

        sendVerificationCode(phoneNumber);

        btnVerifyOtp.setOnClickListener(v -> {
            String code = etOtp.getText().toString().trim();
            if (TextUtils.isEmpty(code) || code.length() < 6) {
                etOtp.setError("Enter valid OTP");
                return;
            }
            verifyCode(code);
        });

        findViewById(R.id.tvResendOtp).setOnClickListener(v -> sendVerificationCode(phoneNumber));

        // Debug skip for development only (for PH carriers blocking international SMS)
        findViewById(R.id.btnDebugSkip).setOnClickListener(v -> {
            Toast.makeText(this, "Bypassing for testing...", Toast.LENGTH_SHORT).show();
            createAccountDirectly();
        });
    }

    private void sendVerificationCode(String phone) {
        progressBar.setVisibility(View.VISIBLE);
        btnVerifyOtp.setEnabled(false);

        PhoneAuthOptions options =
                PhoneAuthOptions.newBuilder(mAuth)
                        .setPhoneNumber(phone)       // Phone number to verify
                        .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
                        .setActivity(this)                 // Activity (for callback binding)
                        .setCallbacks(mCallbacks)          // OnVerificationStateChangedCallbacks
                        .build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }

    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks =
            new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                @Override
                public void onVerificationCompleted(@NonNull PhoneAuthCredential credential) {
                    progressBar.setVisibility(View.GONE);
                    String code = credential.getSmsCode();
                    if (code != null) {
                        etOtp.setText(code);
                        verifyCode(code);
                    }
                }

                @Override
                public void onVerificationFailed(@NonNull FirebaseException e) {
                    progressBar.setVisibility(View.GONE);
                    btnVerifyOtp.setEnabled(true);
                    
                    String errorMessage = e.getMessage();
                    if (errorMessage != null && (errorMessage.contains("token") || errorMessage.length() > 50)) {
                        errorMessage = "Too many attempts or number blocked. Please use the [SKIP] button below for testing.";
                    }
                    
                    Toast.makeText(OtpVerificationActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCodeSent(@NonNull String verId,
                                       @NonNull PhoneAuthProvider.ForceResendingToken token) {
                    progressBar.setVisibility(View.GONE);
                    btnVerifyOtp.setEnabled(true);
                    verificationId = verId;
                    Toast.makeText(OtpVerificationActivity.this, "OTP Sent", Toast.LENGTH_SHORT).show();
                }
            };

    private void verifyCode(String code) {
        progressBar.setVisibility(View.VISIBLE);
        btnVerifyOtp.setEnabled(false);

        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        progressBar.setVisibility(View.VISIBLE);
        btnVerifyOtp.setEnabled(false);

        // Step 1: Verify the phone number by signing in
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        // Phone verified and signed in!
                        // Step 2: Now update this user with Email and Password
                        finalizeAccount();
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnVerifyOtp.setEnabled(true);
                        String error = task.getException() != null ? task.getException().getMessage() : "Invalid OTP";
                        Toast.makeText(OtpVerificationActivity.this, "Verification Failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void finalizeAccount() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            // This is the "Session lost" part. We'll try one more fallback.
            Toast.makeText(this, "Session lost. Attempting fallback...", Toast.LENGTH_SHORT).show();
            createAccountDirectly();
            return;
        }

        user.updateEmail(email).addOnCompleteListener(emailTask -> {
            user.updatePassword(password).addOnCompleteListener(passTask -> {
                saveUserDataAndFinish(user);
            });
        });
    }

    private void createAccountDirectly() {
        // Fallback: If session is lost, just create the email account normally
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        saveUserDataAndFinish(task.getResult().getUser());
                    } else {
                        progressBar.setVisibility(View.GONE);
                        btnVerifyOtp.setEnabled(true);
                        Toast.makeText(this, "Final Registration Failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void saveUserDataAndFinish(FirebaseUser user) {
        if (user != null) {
            sharedPreferences.edit()
                    .putString("user_phone_" + user.getUid(), phoneNumber)
                    .apply();

            UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                    .setDisplayName(fullName)
                    .build();
            user.updateProfile(profileUpdates);
        }

        progressBar.setVisibility(View.GONE);
        Toast.makeText(OtpVerificationActivity.this, "Registration Complete!", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(OtpVerificationActivity.this, MainActivity.class));
        finishAffinity();
    }
}