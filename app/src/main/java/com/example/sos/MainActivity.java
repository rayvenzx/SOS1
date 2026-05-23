package com.example.sos;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.auth.FirebaseAuth;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_CONTACTS
    };

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentBestLocation;
    private String lastKnownLocationUrl = "";
    private TextView tvLocationStatus;
    private EditText etSosReason;
    private LinearLayout llContactList;
    private SharedPreferences sharedPreferences;
    private List<Contact> contactList = new ArrayList<>();

    private ActivityResultLauncher<Intent> contactPickerLauncher;

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
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        tvLocationStatus = findViewById(R.id.tvLocationStatus);
        etSosReason = findViewById(R.id.etSosReason);
        llContactList = findViewById(R.id.llContactList);
        sharedPreferences = getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE);

        loadContacts();

        contactPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri contactUri = result.getData().getData();
                        handleContactSelection(contactUri);
                    }
                }
        );

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        } else {
            startLocationUpdates();
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView btnSos = findViewById(R.id.btnSos);
        if (btnSos != null) {
            btnSos.setOnClickListener(v -> {
                if (allPermissionsGranted()) {
                    triggerSOS();
                } else {
                    ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
                }
            });
        }

        findViewById(R.id.btnAddContact).setOnClickListener(v -> {
            if (contactList.size() >= 20) {
                Toast.makeText(this, "Maximum 20 contacts allowed", Toast.LENGTH_SHORT).show();
            } else {
                showAddContactDialog();
            }
        });

        setupToolbarButtons();
        setupQuickServiceButtons();
        setupBottomNavigation();
        setupTrackingCard();
    }

    private void setupTrackingCard() {
        findViewById(R.id.cvTracking).setOnClickListener(v -> {
            if (currentBestLocation != null) {
                // Force a fresh lookup
                new Thread(() -> {
                    String address = getAddressFromLocation(currentBestLocation);
                    runOnUiThread(() -> {
                        Toast.makeText(this, "Current Address: " + address, Toast.LENGTH_LONG).show();
                        if (tvLocationStatus != null) {
                            tvLocationStatus.setText(address);
                        }
                    });
                }).start();
            } else {
                Toast.makeText(this, "Waiting for GPS signal...", Toast.LENGTH_SHORT).show();
                if (allPermissionsGranted()) {
                    startLocationUpdates();
                }
            }
        });
    }

    private String getAddressFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address address = addresses.get(0);
                return address.getAddressLine(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        // Fallback to coordinates if address lookup fails
        return String.format(Locale.getDefault(), "Lat: %.4f, Lng: %.4f", location.getLatitude(), location.getLongitude());
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
        } else {
            // Initial default contact if none exists
            contactList.add(new Contact("Emergency", "911"));
        }
        refreshContactListView();
    }

    private void saveContacts() {
        JSONArray array = new JSONArray();
        for (Contact c : contactList) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("name", c.name);
                obj.put("number", c.number);
                array.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        sharedPreferences.edit().putString("contacts_json", array.toString()).apply();
        refreshContactListView();
    }

    private void refreshContactListView() {
        llContactList.removeAllViews();
        for (int i = 0; i < contactList.size(); i++) {
            Contact contact = contactList.get(i);
            View contactView = createContactView(contact, i);
            llContactList.addView(contactView);
        }
    }

    private View createContactView(Contact contact, int index) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(40f); // approx 20dp
        card.setCardElevation(8f);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(40, 40, 40, 40);
        root.setGravity(android.view.Gravity.CENTER_VERTICAL);

        // Avatar
        CardView avatarCard = new CardView(this);
        avatarCard.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
        avatarCard.setRadius(60f);
        avatarCard.setCardElevation(0f);
        avatarCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary_light));
        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.cll);
        avatar.setPadding(30, 30, 30, 30);
        avatarCard.addView(avatar);
        root.addView(avatarCard);

        // Name and Number
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMarginStart(32);
        info.setLayoutParams(infoParams);

        TextView name = new TextView(this);
        name.setText(contact.name);
        name.setTextColor(ContextCompat.getColor(this, R.color.text_dark));
        name.setTextSize(18f);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(name);

        TextView number = new TextView(this);
        number.setText(contact.number);
        number.setTextColor(ContextCompat.getColor(this, R.color.text_grey));
        number.setTextSize(14f);
        info.addView(number);
        root.addView(info);

        // Delete Button
        ImageView delete = new ImageView(this);
        delete.setLayoutParams(new LinearLayout.LayoutParams(60, 60));
        delete.setImageResource(android.R.drawable.ic_menu_delete);
        delete.setColorFilter(ContextCompat.getColor(this, R.color.text_grey));
        delete.setOnClickListener(v -> {
            contactList.remove(index);
            saveContacts();
        });
        root.addView(delete);

        card.addView(root);
        return card;
    }

    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Emergency Contact");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        Button btnPickContact = new Button(this);
        btnPickContact.setText("Pick from Device");
        btnPickContact.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
        btnPickContact.setTextColor(ContextCompat.getColor(this, R.color.white));
        layout.addView(btnPickContact);

        TextView tvOr = new TextView(this);
        tvOr.setText("— OR MANUALLY ADD —");
        tvOr.setGravity(android.view.Gravity.CENTER);
        tvOr.setPadding(0, 30, 0, 30);
        layout.addView(tvOr);

        final EditText inputName = new EditText(this);
        inputName.setHint("Name");
        layout.addView(inputName);

        final EditText inputNumber = new EditText(this);
        inputNumber.setHint("Number");
        inputNumber.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        layout.addView(inputNumber);

        builder.setView(layout);
        AlertDialog dialog = builder.create();

        btnPickContact.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                contactPickerLauncher.launch(intent);
                dialog.dismiss();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CONTACTS}, 102);
            }
        });

        builder.setPositiveButton("Add", (d, which) -> {
            String newName = inputName.getText().toString().trim();
            String newNumber = inputNumber.getText().toString().trim();
            if (!newName.isEmpty() && !newNumber.isEmpty()) {
                contactList.add(new Contact(newName, newNumber));
                saveContacts();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void handleContactSelection(Uri contactUri) {
        String[] projection = {ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};
        Cursor cursor = getContentResolver().query(contactUri, projection, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
            int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
            String name = cursor.getString(nameIndex);
            String number = cursor.getString(numberIndex);
            contactList.add(new Contact(name, number));
            saveContacts();
            cursor.close();
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentBestLocation = location;
                        lastKnownLocationUrl = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                        
                        String address = getAddressFromLocation(location);
                        if (tvLocationStatus != null) {
                            tvLocationStatus.setText(address.equals("Address not found") ? "Live tracking active" : address);
                        }
                        
                        // Log location update occasionally
                        saveAlert("Location Update", "Tracked at: " + (address.equals("Address not found") ? location.getLatitude() + ", " + location.getLongitude() : address));
                    }
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void setupBottomNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                return true;
            } else if (id == R.id.nav_safety) {
                startActivity(new Intent(this, SafetyTipsActivity.class));
                return true;
            } else if (id == R.id.nav_history) {
                startActivity(new Intent(this, AlertsActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
    }

    private void setupQuickServiceButtons() {
        findViewById(R.id.cvPolice).setOnClickListener(v -> makeQuickCall("911"));
        findViewById(R.id.cvMedical).setOnClickListener(v -> makeQuickCall("911"));
        findViewById(R.id.cvFire).setOnClickListener(v -> makeQuickCall("911"));
    }

    private void makeQuickCall(String number) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + number));
            startActivity(callIntent);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, REQUEST_CODE_PERMISSIONS);
        }
    }

    private void triggerSOS() {
        if (contactList.isEmpty()) {
            Toast.makeText(this, "Please add at least one emergency contact", Toast.LENGTH_SHORT).show();
            return;
        }

        String reason = etSosReason != null ? etSosReason.getText().toString().trim() : "";
        String customTemplate = sharedPreferences.getString("custom_sos_message", "EMERGENCY! I need help.");
        
        String message = customTemplate + 
                (reason.isEmpty() ? "" : " Reason: " + reason) +
                " My current location is: " + 
                (lastKnownLocationUrl.isEmpty() ? "Detecting..." : lastKnownLocationUrl) + 
                ". SOS from S.O.S. App.";
        
        saveAlert("SOS Triggered", "Emergency alert sent to " + contactList.size() + " contacts." + 
                (reason.isEmpty() ? "" : " Reason: " + reason));

        try {
            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            // Notify ALL contacts via SMS
            for (Contact contact : contactList) {
                String cleanNumber = contact.number.replaceAll("\\s+", "");
                smsManager.sendTextMessage(cleanNumber, null, message, null, null);
            }
            
            Toast.makeText(this, "SOS Sent to " + contactList.size() + " contacts", Toast.LENGTH_SHORT).show();

            // Call the FIRST contact in the list
            String firstContactNumber = contactList.get(0).number.replaceAll("\\s+", "");
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + firstContactNumber));
            startActivity(callIntent);

            startActivity(new Intent(MainActivity.this, CallingActivity.class));

        } catch (Exception e) {
            Toast.makeText(this, "Failed to send SOS: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
            
            // Keep only last 20 alerts
            if (array.length() > 20) {
                JSONArray limitedArray = new JSONArray();
                for (int i = array.length() - 20; i < array.length(); i++) {
                    limitedArray.put(array.get(i));
                }
                array = limitedArray;
            }
            
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

    private void setupToolbarButtons() {
        findViewById(R.id.ivLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });

        findViewById(R.id.ivSettings).setOnClickListener(v -> showCustomMessageDialog());
    }

    private void showCustomMessageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Customize SOS Message");
        
        final EditText input = new EditText(this);
        input.setHint("e.g., I'm in trouble, please help!");
        String currentMessage = sharedPreferences.getString("custom_sos_message", "EMERGENCY! I need help.");
        input.setText(currentMessage);
        
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        input.setLayoutParams(lp);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newMessage = input.getText().toString().trim();
            if (!newMessage.isEmpty()) {
                sharedPreferences.edit().putString("custom_sos_message", newMessage).apply();
                Toast.makeText(this, "SOS Message updated", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS || requestCode == 102) {
            if (allPermissionsGranted()) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Permissions updated", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startLocationUpdates();
        }
    }
}