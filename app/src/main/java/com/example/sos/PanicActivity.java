package com.example.sos;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PanicActivity extends AppCompatActivity {

    private FusedLocationProviderClient fusedLocationClient;
    private SharedPreferences sharedPreferences;
    private String lastKnownLocationUrl = "";
    private List<Contact> contactList = new ArrayList<>();

    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private static class Contact {
        String name;
        String number;
        Contact(String name, String number) {
            this.name = name;
            this.number = number;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_panic);

        sharedPreferences = getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Drawer Setup
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        findViewById(R.id.btnMenu).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.ivLocationIcon).setOnClickListener(v -> startActivity(new Intent(this, FullMapActivity.class)));

        NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_add_member || id == R.id.nav_view_members) {
                startActivity(new Intent(this, MainActivity.class));
            } else if (id == R.id.nav_sos_message) {
                startActivity(new Intent(this, ProfileActivity.class));
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        loadContacts();
        fetchLocation();
        setupBottomNavigation();

        ImageView btnSos = findViewById(R.id.btnSosPanic);
        btnSos.setOnClickListener(v -> {
            if (allPermissionsGranted()) {
                triggerPanicSOS();
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }
        });
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_panic_alarm);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_group_sos) {
                startActivity(new Intent(this, GroupSosActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, ProfileActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_call) {
                handleCallAction();
                return true;
            } else if (id == R.id.nav_panic_alarm) {
                return true;
            }
            return false;
        });
    }

    private void handleCallAction() {
        if (contactList.isEmpty()) {
            showCallInputDialog("Emergency", "911");
        } else if (contactList.size() == 1) {
            sendSmsAndCall(contactList.get(0).name, contactList.get(0).number, "Emergency call initiated. I need help.");
        } else {
            showContactSelectionDialog();
        }
    }

    private void showContactSelectionDialog() {
        String[] names = new String[contactList.size()];
        for (int i = 0; i < contactList.size(); i++) {
            names[i] = contactList.get(i).name + " (" + contactList.get(i).number + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Contact to Call & Text")
                .setItems(names, (dialog, which) -> {
                    Contact contact = contactList.get(which);
                    sendSmsAndCall(contact.name, contact.number, "Emergency call initiated. I need help.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCallInputDialog(String serviceName, String defaultNumber) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Call " + serviceName);
        final android.widget.EditText input = new android.widget.EditText(this);
        input.setHint("Enter Number");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        input.setText(defaultNumber);
        builder.setView(input);
        builder.setPositiveButton("Call & Text", (dialog, which) -> {
            String number = input.getText().toString().trim();
            if (!number.isEmpty()) {
                sendSmsAndCall(serviceName, number, "Emergency " + serviceName + " alert triggered.");
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                if (location != null) {
                    lastKnownLocationUrl = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                }
            });
        }
    }

    private void loadContacts() {
        contactList.clear();
        String json = sharedPreferences.getString("contacts_json", null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    contactList.add(new Contact(obj.getString("name"), obj.getString("number")));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String groupJson = sharedPreferences.getString("group_members_json", null);
        if (groupJson != null) {
            try {
                JSONArray array = new JSONArray(groupJson);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String number = obj.getString("number");
                    boolean exists = false;
                    for (Contact c : contactList) {
                        if (c.number.equals(number)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        contactList.add(new Contact(obj.getString("type"), number));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    private void triggerPanicSOS() {
        if (contactList.isEmpty()) {
            Toast.makeText(this, "Please add emergency contacts first", Toast.LENGTH_SHORT).show();
            return;
        }

        String customTemplate = sharedPreferences.getString("custom_sos_message", "EMERGENCY! I need help.");
        String locationPart = lastKnownLocationUrl.isEmpty() ? "Location unavailable" : lastKnownLocationUrl;
        String message = customTemplate + " My current location: " + locationPart + ". SOS from Panic Alarm.";

        saveAlert("Panic SOS", "Emergency alert sent to " + contactList.size() + " contacts.");

        try {
            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            // Send SMS to ALL contacts
            ArrayList<String> parts = smsManager.divideMessage(message);
            for (Contact contact : contactList) {
                String cleanNumber = contact.number.replaceAll("[^0-9+*#]", "");
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null);
            }
            
            Toast.makeText(this, "SOS Text sent to all contacts", Toast.LENGTH_SHORT).show();

            // Then prompt for Call immediately
            Contact firstContact = contactList.get(0);
            String sosLabel = contactList.size() > 1 ? "Group SOS (" + contactList.size() + ")" : "SOS Call";
            
            new AlertDialog.Builder(this)
                    .setTitle("SOS Call")
                    .setMessage("SMS sent to all. Call " + firstContact.name + " now?")
                    .setPositiveButton("Call Now", (dialog, which) -> {
                        makeQuickCall(sosLabel, firstContact.number);
                    })
                    .setNegativeButton("Choose Other", (dialog, which) -> {
                        showContactSelectionDialog();
                    })
                    .setNeutralButton("Cancel", null)
                    .setCancelable(false)
                    .show();

        } catch (Exception e) {
            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void sendSmsAndCall(String name, String number, String customNote) {
        String locationPart = lastKnownLocationUrl.isEmpty() ? "Location unavailable" : lastKnownLocationUrl;
        String message = customNote + " My location: " + locationPart;

        try {
            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            String cleanNumber = number.replaceAll("[^0-9+*#]", "");
            ArrayList<String> parts = smsManager.divideMessage(message);
            smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null);
            
            Toast.makeText(this, "SMS Alert Sent", Toast.LENGTH_SHORT).show();
            makeQuickCall(name, number);

        } catch (Exception e) {
            Toast.makeText(this, "Action failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void makeQuickCall(String name, String number) {
        String cleanNumber = number.replaceAll("[^0-9+*#]", "");
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + cleanNumber));
        startActivity(callIntent);

        Intent statusIntent = new Intent(this, CallingActivity.class);
        statusIntent.putExtra("contact_name", name);
        statusIntent.putExtra("contact_number", cleanNumber);
        startActivity(statusIntent);
    }

    private void saveAlert(String title, String desc) {
        String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String alertsJson = sharedPreferences.getString("recent_alerts", "[]");
        try {
            JSONArray array = new JSONArray(alertsJson);
            JSONObject newAlert = new JSONObject();
            newAlert.put("title", title);
            newAlert.put("time", time);
            newAlert.put("desc", desc);
            array.put(newAlert);
            sharedPreferences.edit().putString("recent_alerts", array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                triggerPanicSOS();
            }
        }
    }
}