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
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

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
    private MapView map = null;
    private Marker userMarker = null;

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
        // OSMDroid configuration
        Configuration.getInstance().load(this, sharedPreferences != null ? sharedPreferences : getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE));

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Drawer Setup
        androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        findViewById(R.id.btnMenu).setOnClickListener(v -> drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));
        
        com.google.android.material.navigation.NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_add_member) {
                showAddContactDialog();
            } else if (id == R.id.nav_view_members) {
                showViewMembersDialog();
            } else if (id == R.id.nav_sos_message) {
                startActivity(new Intent(this, ProfileActivity.class));
            } else if (id == R.id.nav_share || id == R.id.nav_suggestions || id == R.id.nav_support 
                    || id == R.id.nav_feedback || id == R.id.nav_rate || id == R.id.nav_terms 
                    || id == R.id.nav_privacy || id == R.id.nav_disclaimer) {
                Toast.makeText(this, item.getTitle() + " feature coming soon", Toast.LENGTH_SHORT).show();
            }
            drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START);
            return true;
        });

        // Handle Profile/Login icon logic
        ImageView ivProfileIcon = findViewById(R.id.ivProfileIcon);
        if (ivProfileIcon != null) {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                // User is logged in, hide the icon as requested
                ivProfileIcon.setVisibility(View.GONE);
            } else {
                // User is NOT logged in, show the icon and allow redirect to login
                ivProfileIcon.setVisibility(View.VISIBLE);
                ivProfileIcon.setOnClickListener(v -> startActivity(new Intent(this, LoginActivity.class)));
            }
        }

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

        setupQuickServiceButtons();
        setupBottomNavigation();
        setupTrackingCard();
        initMap();
    }

    private void initMap() {
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);
    }

    private void setupTrackingCard() {
        // Location area is now static/non-clickable
        findViewById(R.id.ivLocationIcon).setOnClickListener(v -> {
            // Open the map to show where the user is
            startActivity(new Intent(MainActivity.this, FullMapActivity.class));
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
        
        // Load from Emergency Contacts
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

        // Load from Group SOS Members as well (unify "saved numbers")
        String groupJson = sharedPreferences.getString("group_members_json", null);
        if (groupJson != null) {
            try {
                JSONArray array = new JSONArray(groupJson);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String number = obj.getString("number");
                    // Avoid duplicates
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

    private void showViewMembersDialog() {
        if (contactList.isEmpty()) {
            Toast.makeText(this, "No contacts saved", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] items = new String[contactList.size()];
        for (int i = 0; i < contactList.size(); i++) {
            items[i] = contactList.get(i).name + " (" + contactList.get(i).number + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Emergency Contacts (Tap to delete)")
                .setItems(items, (dialog, which) -> {
                    Contact contact = contactList.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle("Delete Contact")
                            .setMessage("Remove " + contact.name + " from emergency contacts?")
                            .setPositiveButton("Delete", (d, w) -> {
                                contactList.remove(which);
                                saveContacts();
                                Toast.makeText(this, "Contact Deleted", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                })
                .setPositiveButton("Close", null)
                .show();
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

        // Optimized for high accuracy and faster updates (especially useful for PH urban areas)
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .setWaitForAccurateLocation(true)
                .setMaxUpdateDelayMillis(10000)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentBestLocation = location;
                        lastKnownLocationUrl = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                        
                        float accuracy = location.getAccuracy();
                        String address = getAddressFromLocation(location);
                        
                        if (tvLocationStatus != null) {
                            String statusText = (address.equals("Address not found") ? "Tracking Active" : address) + 
                                              " (" + Math.round(accuracy) + "m)";
                            tvLocationStatus.setText(statusText);
                        }

                        updateMapLocation(location);
                        
                        // Log location update occasionally
                        saveAlert("Location Update", "Accuracy: " + Math.round(accuracy) + "m | At: " + address);
                    }
                }
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private void updateMapLocation(Location location) {
        if (map == null) return;

        GeoPoint startPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
        map.getController().animateTo(startPoint);

        if (userMarker == null) {
            userMarker = new Marker(map);
            userMarker.setTitle("You are here");
            userMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            map.getOverlays().add(userMarker);
        }
        userMarker.setPosition(startPoint);
        map.invalidate();
    }

    private void setupBottomNavigation() {
        com.google.android.material.bottomnavigation.BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_home);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                android.widget.ScrollView scrollView = findViewById(R.id.mainScrollView);
                if (scrollView != null) scrollView.smoothScrollTo(0, 0);
                return true;
            } else if (id == R.id.nav_group_sos) {
                startActivity(new Intent(this, GroupSosActivity.class));
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_call) {
                handleCallAction();
                return true;
            } else if (id == R.id.nav_panic_alarm) {
                startActivity(new Intent(this, PanicActivity.class));
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
                .setTitle("Select Contact to Call")
                .setItems(names, (dialog, which) -> {
                    Contact contact = contactList.get(which);
                    sendSmsAndCall(contact.name, contact.number, "Emergency call initiated. I need help.");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupQuickServiceButtons() {
        findViewById(R.id.cvPolice).setOnClickListener(v -> showCallInputDialog("Police", "911"));
        findViewById(R.id.cvMedical).setOnClickListener(v -> showCallInputDialog("Medical", "911"));
        findViewById(R.id.cvFire).setOnClickListener(v -> showCallInputDialog("Fire", "911"));
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

        builder.setPositiveButton("Call & Text", (dialog, which) -> {
            String number = input.getText().toString().trim();
            if (!number.isEmpty()) {
                sendSmsAndCall(serviceName, number, "Emergency " + serviceName + " alert triggered. I need assistance.");
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void sendSmsAndCall(String name, String number, String customNote) {
        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            return;
        }

        String locationPart = lastKnownLocationUrl.isEmpty() ? "Location unavailable" : lastKnownLocationUrl;
        String message = customNote + " My current location: " + locationPart;

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

            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + cleanNumber));
            startActivity(callIntent);
            
            Intent statusIntent = new Intent(MainActivity.this, CallingActivity.class);
            statusIntent.putExtra("contact_name", name);
            statusIntent.putExtra("contact_number", cleanNumber);
            startActivity(statusIntent);

        } catch (Exception e) {
            Toast.makeText(this, "Action failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void makeQuickCall(String name, String number) {
        sendSmsAndCall(name, number, "Emergency call initiated.");
    }

    private void triggerSOS() {
        if (contactList.isEmpty()) {
            Toast.makeText(this, "Please add at least one emergency contact", Toast.LENGTH_SHORT).show();
            return;
        }

        // Try to get immediate location if not yet detected
        if (lastKnownLocationUrl.isEmpty()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
                    if (location != null) {
                        currentBestLocation = location;
                        lastKnownLocationUrl = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                        proceedWithSOS();
                    } else {
                        proceedWithSOS(); // Send anyway if location still null
                    }
                });
            } else {
                proceedWithSOS();
            }
        } else {
            proceedWithSOS();
        }
    }

    private void proceedWithSOS() {
        String reason = etSosReason != null ? etSosReason.getText().toString().trim() : "";
        String customTemplate = sharedPreferences.getString("custom_sos_message", "EMERGENCY! I need help.");
        
        String locationPart = lastKnownLocationUrl.isEmpty() ? "Location unavailable" : lastKnownLocationUrl;
        String message = customTemplate + 
                (reason.isEmpty() ? "" : " Reason: " + reason) +
                " My current location: " + locationPart + 
                ". SOS from S.O.S. App.";
        
        saveAlert("SOS Triggered", "Emergency alert sent to " + contactList.size() + " contacts.");

        try {
            SmsManager smsManager;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                smsManager = getSystemService(SmsManager.class);
            } else {
                smsManager = SmsManager.getDefault();
            }

            // Notify ALL contacts via SMS
            ArrayList<String> parts = smsManager.divideMessage(message);
            for (Contact contact : contactList) {
                String cleanNumber = contact.number.replaceAll("[^0-9+*#]", "");
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null);
            }
            
            Toast.makeText(this, "SOS Sent to " + contactList.size() + " contacts", Toast.LENGTH_SHORT).show();

            // Prompt for SOS Voice Call after SMS are sent
            String sosLabel = contactList.size() > 1 ? "Group SOS (" + contactList.size() + ")" : "SOS Call";
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("SOS Voice Call");
            builder.setCancelable(false);

            if (contactList.size() > 1) {
                String[] names = new String[contactList.size()];
                for (int i = 0; i < contactList.size(); i++) {
                    names[i] = contactList.get(i).name + " (" + contactList.get(i).number + ")";
                }
                builder.setMessage("SMS alerts sent to " + contactList.size() + " contacts. Select one to call now:");
                builder.setItems(names, (dialog, which) -> {
                    Contact c = contactList.get(which);
                    sendSmsAndCall(sosLabel, c.number, "EMERGENCY: " + customTemplate);
                });
            } else {
                Contact c = contactList.get(0);
                builder.setMessage("SMS alert sent to " + c.name + ". Call them now?");
                builder.setPositiveButton("Call Now", (dialog, which) -> {
                    sendSmsAndCall(sosLabel, c.number, "EMERGENCY: " + customTemplate);
                });
            }

            builder.setNegativeButton("Skip Call", (dialog, which) -> {
                Intent intent = new Intent(MainActivity.this, CallingActivity.class);
                intent.putExtra("contact_name", sosLabel);
                intent.putExtra("contact_number", "SMS Sent to All");
                startActivity(intent);
            });
            builder.show();

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
    public void onPause() {
        super.onPause();
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
        if (map != null) {
            map.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startLocationUpdates();
        }
        if (map != null) {
            map.onResume();
        }
    }
}