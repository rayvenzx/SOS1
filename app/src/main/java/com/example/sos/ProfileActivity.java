package com.example.sos;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private SharedPreferences sharedPreferences;
    private TextView tvProfileSubtitle, tvProfileTitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        sharedPreferences = getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE);
        FirebaseUser currentUser = mAuth.getCurrentUser();

        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        findViewById(R.id.btnMenu).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.ivLocationIcon).setOnClickListener(v -> startActivity(new Intent(this, FullMapActivity.class)));

        com.google.android.material.navigation.NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_add_member) {
                showAddMemberDialog();
            } else if (id == R.id.nav_view_members) {
                showViewMembersDialog();
            } else if (id == R.id.nav_sos_message) {
                showEditMessageDialog();
            } else if (id == R.id.nav_share || id == R.id.nav_suggestions || id == R.id.nav_support 
                    || id == R.id.nav_feedback || id == R.id.nav_rate || id == R.id.nav_terms 
                    || id == R.id.nav_privacy || id == R.id.nav_disclaimer) {
                Toast.makeText(this, item.getTitle() + " feature coming soon", Toast.LENGTH_SHORT).show();
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        tvProfileTitle = findViewById(R.id.tvProfileTitle);
        tvProfileSubtitle = findViewById(R.id.tvProfileSubtitle);
        updateProfileDisplay(currentUser);

        setupClickListeners(currentUser);
        setupBottomNavigation();
    }

    private void updateProfileDisplay(FirebaseUser user) {
        if (user != null) {
            String name = user.getDisplayName();
            String blood = sharedPreferences.getString("user_blood_type", "");
            
            if (!TextUtils.isEmpty(name)) {
                tvProfileTitle.setText(name);
                String subtitle = "Manage Account";
                if (!TextUtils.isEmpty(blood)) {
                    subtitle += " | Blood: " + blood;
                }
                tvProfileSubtitle.setText(subtitle);
            } else {
                tvProfileTitle.setText("Profile");
                tvProfileSubtitle.setText(user.getEmail());
            }
        }
    }

    private void setupClickListeners(FirebaseUser user) {
        findViewById(R.id.cvProfileItem).setOnClickListener(v -> showEditProfileDialog(user));

        findViewById(R.id.cvAddMember).setOnClickListener(v -> showAddMemberDialog());

        findViewById(R.id.cvViewMembers).setOnClickListener(v -> showViewMembersDialog());

        findViewById(R.id.cvGroupSosMembers).setOnClickListener(v -> {
            startActivity(new Intent(this, GroupSosActivity.class));
        });

        findViewById(R.id.cvEditMessage).setOnClickListener(v -> showEditMessageDialog());

        findViewById(R.id.cvSafetyTips).setOnClickListener(v -> {
            startActivity(new Intent(this, SafetyTipsActivity.class));
        });

        findViewById(R.id.cvRecentAlerts).setOnClickListener(v -> {
            startActivity(new Intent(this, AlertsActivity.class));
        });

        findViewById(R.id.cvHowToUse).setOnClickListener(v -> showHowToUseDialog());

        findViewById(R.id.cvDeleteAccount).setOnClickListener(v -> confirmDeleteAccount(user));

        findViewById(R.id.cvLogout).setOnClickListener(v -> {
            mAuth.signOut();
            Intent intent = new Intent(ProfileActivity.this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void showEditProfileDialog(FirebaseUser user) {
        if (user == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Profile");

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        nameInput.setText(user.getDisplayName());

        final EditText phoneInput = new EditText(this);
        phoneInput.setHint("Phone Number");
        phoneInput.setText(sharedPreferences.getString("user_phone", ""));
        phoneInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        final EditText bloodInput = new EditText(this);
        bloodInput.setHint("Blood Type (e.g., O+)");
        bloodInput.setText(sharedPreferences.getString("user_blood_type", ""));

        final EditText noteInput = new EditText(this);
        noteInput.setHint("Emergency Medical Note");
        noteInput.setText(sharedPreferences.getString("user_emergency_note", ""));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 10, 0, 10);

        layout.addView(nameInput, params);
        layout.addView(phoneInput, params);
        layout.addView(bloodInput, params);
        layout.addView(noteInput, params);

        builder.setView(layout);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();
            String blood = bloodInput.getText().toString().trim();
            String note = noteInput.getText().toString().trim();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("user_phone", phone);
            editor.putString("user_blood_type", blood);
            editor.putString("user_emergency_note", note);
            editor.apply();

            if (!name.isEmpty()) {
                UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
                        .setDisplayName(name)
                        .build();
                user.updateProfile(profileUpdates).addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        updateProfileDisplay(user);
                        Toast.makeText(this, "Profile Updated", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                updateProfileDisplay(user);
                Toast.makeText(this, "Details Saved", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showAddMemberDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Contact");

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Name");
        final EditText numberInput = new EditText(this);
        numberInput.setHint("Phone Number");
        numberInput.setInputType(android.text.InputType.TYPE_CLASS_PHONE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);
        layout.addView(nameInput);
        layout.addView(numberInput);
        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String number = numberInput.getText().toString().trim();
            if (!name.isEmpty() && !number.isEmpty()) {
                saveNewContact(name, number);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void saveNewContact(String name, String number) {
        String json = sharedPreferences.getString("contacts_json", "[]");
        try {
            JSONArray array = new JSONArray(json);
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("number", number);
            array.put(obj);
            sharedPreferences.edit().putString("contacts_json", array.toString()).apply();
            Toast.makeText(this, "Contact Added", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showViewMembersDialog() {
        String json = sharedPreferences.getString("contacts_json", "[]");
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) {
                Toast.makeText(this, "No contacts saved", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] items = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                items[i] = obj.getString("name") + " (" + obj.getString("number") + ")";
            }
            new AlertDialog.Builder(this)
                    .setTitle("Emergency Contacts (Tap to delete)")
                    .setItems(items, (dialog, which) -> {
                        confirmDeleteContact(which);
                    })
                    .setPositiveButton("Close", null)
                    .show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void confirmDeleteContact(int index) {
        String json = sharedPreferences.getString("contacts_json", "[]");
        try {
            JSONArray array = new JSONArray(json);
            JSONObject contact = array.getJSONObject(index);
            new AlertDialog.Builder(this)
                    .setTitle("Delete Contact")
                    .setMessage("Remove " + contact.getString("name") + " from emergency contacts?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        deleteContact(index);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void deleteContact(int index) {
        String json = sharedPreferences.getString("contacts_json", "[]");
        try {
            JSONArray array = new JSONArray(json);
            JSONArray newArray = new JSONArray();
            for (int i = 0; i < array.length(); i++) {
                if (i != index) {
                    newArray.put(array.get(i));
                }
            }
            sharedPreferences.edit().putString("contacts_json", newArray.toString()).apply();
            Toast.makeText(this, "Contact Deleted", Toast.LENGTH_SHORT).show();
            showViewMembersDialog(); // Refresh list
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void showEditMessageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit SOS Message");
        final EditText input = new EditText(this);
        String currentMsg = sharedPreferences.getString("custom_sos_message", "EMERGENCY! I need help.");
        input.setText(currentMsg);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 20, 60, 20);
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newMsg = input.getText().toString().trim();
            sharedPreferences.edit().putString("custom_sos_message", newMsg).apply();
            Toast.makeText(this, "Message Saved", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showHowToUseDialog() {
        new AlertDialog.Builder(this)
                .setTitle("SOS Guide")
                .setMessage("Getting Started with S.O.S:\n\n" +
                        "1. Emergency Contacts: Use 'Add Member' to save your primary contacts. They will receive SMS alerts when you hit the SOS button.\n\n" +
                        "2. SOS Button: Long press or tap the big red button on Home to trigger a global alert. Your location is automatically attached.\n\n" +
                        "3. Quick Services: Tap the icons for Police, Fire, or Medical for direct emergency dialing and automated location sharing.\n\n" +
                        "4. Group SOS: Manage specialized groups (Family, Friends, Work) in the Group SOS section for targeted alerts.\n\n" +
                        "5. Safety Tips: Swipe through safety guidelines in the 'Safety & Help' section to stay prepared.\n\n" +
                        "6. Recent Alerts: Check the history of your triggered alerts to see when and where you sent for help.\n\n" +
                        "7. Profile & Medical: Keep your profile updated with your blood type and medical notes so responders can help you better.\n\n" +
                        "8. Custom Message: Personalize your SOS message in 'Edit Message' to provide specific context to your contacts.")
                .setPositiveButton("I Understand", null)
                .show();
    }

    private void confirmDeleteAccount(FirebaseUser user) {
        if (user == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Are you sure? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    user.delete().addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Account Deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(this, "Authentication required to delete. Please re-login.", Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_settings);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_group_sos) {
                startActivity(new Intent(this, GroupSosActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_call) {
                handleCallAction();
                return true;
            } else if (id == R.id.nav_panic_alarm) {
                startActivity(new Intent(this, PanicActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_settings) {
                return true;
            }
            return false;
        });
    }

    private void handleCallAction() {
        String json = sharedPreferences.getString("contacts_json", "[]");
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) {
                showCallInputDialog("Emergency", "911");
            } else if (array.length() == 1) {
                JSONObject obj = array.getJSONObject(0);
                makeQuickCall(obj.getString("name"), obj.getString("number"));
            } else {
                showContactSelectionDialog(array);
            }
        } catch (JSONException e) {
            showCallInputDialog("Emergency", "911");
        }
    }

    private void showContactSelectionDialog(JSONArray array) throws JSONException {
        String[] items = new String[array.length()];
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            items[i] = obj.getString("name") + " (" + obj.getString("number") + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Contact to Call")
                .setItems(items, (dialog, which) -> {
                    try {
                        JSONObject obj = array.getJSONObject(which);
                        makeQuickCall(obj.getString("name"), obj.getString("number"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCallInputDialog(String serviceName, String defaultNumber) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Call " + serviceName);
        final EditText input = new EditText(this);
        input.setHint("Enter Number");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        input.setText(defaultNumber);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(50, 20, 50, 0);
        input.setLayoutParams(params);
        container.addView(input);
        builder.setView(container);
        builder.setPositiveButton("Call", (dialog, which) -> {
            String number = input.getText().toString().trim();
            if (!number.isEmpty()) {
                makeQuickCall(serviceName, number);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void makeQuickCall(String name, String number) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            String cleanNumber = number.replaceAll("[^0-9+*#]", "");
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(android.net.Uri.parse("tel:" + cleanNumber));
            startActivity(callIntent);

            Intent statusIntent = new Intent(this, CallingActivity.class);
            statusIntent.putExtra("contact_name", name);
            statusIntent.putExtra("contact_number", cleanNumber);
            startActivity(statusIntent);
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CALL_PHONE}, 101);
        }
    }
}