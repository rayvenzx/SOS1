package com.example.sos;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class GroupSosActivity extends AppCompatActivity {

    private LinearLayout llMembersContainer, llEmptyState;
    private SharedPreferences sharedPreferences;
    private List<GroupMember> memberList = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationClient;
    private String lastKnownLocationUrl = "";

    private static final int REQUEST_CODE_PERMISSIONS = 101;
    private final String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.SEND_SMS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    private static class GroupMember {
        String type;
        String number;

        GroupMember(String type, String number) {
            this.type = type;
            this.number = number;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_sos);

        llMembersContainer = findViewById(R.id.llMembersContainer);
        llEmptyState = findViewById(R.id.llEmptyState);
        sharedPreferences = getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Drawer Setup
        DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        findViewById(R.id.btnMenu).setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));
        findViewById(R.id.ivLocationIcon).setOnClickListener(v -> startActivity(new Intent(this, FullMapActivity.class)));

        NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_add_member) {
                showSelectMemberTypeBottomSheet();
            } else if (id == R.id.nav_sos_message) {
                startActivity(new Intent(this, ProfileActivity.class));
            }
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        });

        loadMembers();
        fetchLocation();
        setupBottomNavigation();
        
        findViewById(R.id.fabAddMember).setOnClickListener(v -> showSelectMemberTypeBottomSheet());

        findViewById(R.id.btnCallGroup).setOnClickListener(v -> {
            triggerGroupSOS();
        });
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

    private void triggerGroupSOS() {
        if (memberList.isEmpty()) {
            Toast.makeText(this, "No members in group. Please add some first.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            return;
        }

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Toast.makeText(this, "Please enable GPS for accurate location", Toast.LENGTH_LONG).show();
        }

        Toast.makeText(this, "Acquiring location...", Toast.LENGTH_SHORT).show();

        // Fallback to last known location quickly
        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                lastKnownLocationUrl = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
            }
        });

        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        lastKnownLocationUrl = "https://www.google.com/maps?q=" + location.getLatitude() + "," + location.getLongitude();
                    }
                    proceedWithGroupSOS();
                })
                .addOnFailureListener(this, e -> {
                    proceedWithGroupSOS();
                });
    }

    private void proceedWithGroupSOS() {
        String message = buildGroupMessage();
        saveAlert("Group SOS", "Emergency alert initiated.");

        boolean hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        if (hasSms) {
            try {
                SmsManager smsManager;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    smsManager = getSystemService(SmsManager.class);
                } else {
                    smsManager = SmsManager.getDefault();
                }

                ArrayList<String> parts = smsManager.divideMessage(message);
                for (GroupMember member : memberList) {
                    String cleanNumber = member.number.replaceAll("[^0-9+*#]", "");
                    smsManager.sendMultipartTextMessage(cleanNumber, null, parts, null, null);
                }
                Toast.makeText(this, "SOS Sent to all " + memberList.size() + " group members", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Group SOS failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        // Prompt for call
        GroupMember first = memberList.get(0);
        new AlertDialog.Builder(this)
                .setTitle("Group SOS Alert Sent")
                .setMessage("SMS sent to all group members. Call " + first.type + " (" + first.number + ") now?")
                .setPositiveButton("Call Now", (dialog, which) -> makeQuickCall(first.type, first.number))
                .setNegativeButton("Choose Other", (dialog, which) -> showContactSelectionDialog())
                .setNeutralButton("Cancel", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private String buildGroupMessage() {
        String customTemplate = sharedPreferences.getString("custom_sos_message", "EMERGENCY! I need help.");
        String locationPart = lastKnownLocationUrl.isEmpty() ? "Location unavailable" : lastKnownLocationUrl;
        return customTemplate + " My current location: " + locationPart + ". (Group SOS)";
    }

    private void showContactSelectionDialog() {
        String[] items = new String[memberList.size()];
        for (int i = 0; i < memberList.size(); i++) {
            items[i] = memberList.get(i).type + " (" + memberList.get(i).number + ")";
        }

        new AlertDialog.Builder(this)
                .setTitle("Select Member to Call")
                .setItems(items, (dialog, which) -> {
                    GroupMember m = memberList.get(which);
                    makeQuickCall(m.type, m.number);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSelectMemberTypeBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.layout_select_member_type, null);
        
        view.findViewById(R.id.ivClose).setOnClickListener(v -> bottomSheetDialog.dismiss());
        
        LinearLayout container = view.findViewById(R.id.llMemberTypesContainer);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView) {
                TextView tv = (TextView) child;
                tv.setOnClickListener(v -> {
                    String type = tv.getText().toString();
                    bottomSheetDialog.dismiss();
                    showNumberInputDialog(type);
                });
            }
        }
        
        bottomSheetDialog.setContentView(view);
        bottomSheetDialog.show();
    }

    private void showNumberInputDialog(String type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add " + type);

        final EditText input = new EditText(this);
        input.setHint("Enter phone number");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 10);
        layout.addView(input);
        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String number = input.getText().toString().trim();
            if (!number.isEmpty()) {
                memberList.add(new GroupMember(type, number));
                saveMembers();
                refreshMemberListView();
            } else {
                Toast.makeText(this, "Number is required", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void loadMembers() {
        memberList.clear();
        String json = sharedPreferences.getString("group_members_json", null);
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    memberList.add(new GroupMember(obj.getString("type"), obj.getString("number")));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        refreshMemberListView();
    }

    private void saveMembers() {
        JSONArray array = new JSONArray();
        for (GroupMember m : memberList) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("type", m.type);
                obj.put("number", m.number);
                array.put(obj);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        sharedPreferences.edit().putString("group_members_json", array.toString()).apply();
    }

    private void refreshMemberListView() {
        llMembersContainer.removeAllViews();
        if (memberList.isEmpty()) {
            llMembersContainer.addView(llEmptyState);
        } else {
            for (int i = 0; i < memberList.size(); i++) {
                llMembersContainer.addView(createMemberView(memberList.get(i), i));
            }
        }
    }

    private View createMemberView(GroupMember member, int index) {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        card.setLayoutParams(params);
        card.setRadius(24f);
        card.setCardElevation(4f);
        card.setCardBackgroundColor(ContextCompat.getColor(this, R.color.white));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(32, 32, 32, 32);
        root.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setLayoutParams(new LinearLayout.LayoutParams(80, 80));
        icon.setImageResource(R.drawable.ic_nav_group);
        icon.setColorFilter(ContextCompat.getColor(this, R.color.black));
        root.addView(icon);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMarginStart(24);
        info.setLayoutParams(infoParams);

        TextView type = new TextView(this);
        type.setText(member.type);
        type.setTextColor(ContextCompat.getColor(this, R.color.black));
        type.setTextSize(16);
        type.setTypeface(null, android.graphics.Typeface.BOLD);
        info.addView(type);

        TextView number = new TextView(this);
        number.setText(member.number);
        number.setTextColor(ContextCompat.getColor(this, R.color.black));
        number.setAlpha(0.6f);
        number.setTextSize(14);
        info.addView(number);
        root.addView(info);

        ImageView delete = new ImageView(this);
        delete.setLayoutParams(new LinearLayout.LayoutParams(50, 50));
        delete.setImageResource(android.R.drawable.ic_menu_delete);
        delete.setOnClickListener(v -> {
            memberList.remove(index);
            saveMembers();
            refreshMemberListView();
        });
        root.addView(delete);

        card.addView(root);
        return card;
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setSelectedItemId(R.id.nav_group_sos);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.nav_group_sos) {
                return true;
            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, ProfileActivity.class));
                finish();
                return true;
            } else if (id == R.id.nav_call) {
                handleCallAction();
                return true;
            } else if (id == R.id.nav_panic_alarm) {
                startActivity(new Intent(this, PanicActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void handleCallAction() {
        if (memberList.isEmpty()) {
            showCallInputDialog("Emergency", "911");
        } else if (memberList.size() == 1) {
            makeQuickCall(memberList.get(0).type, memberList.get(0).number);
        } else {
            showContactSelectionDialog();
        }
    }

    private void showCallInputDialog(String serviceName, String defaultNumber) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Call " + serviceName);
        final EditText input = new EditText(this);
        input.setHint("Enter Number");
        input.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
        input.setText(defaultNumber);
        builder.setView(input);
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            String cleanNumber = number.replaceAll("[^0-9+*#]", "");
            try {
                Intent callIntent = new Intent(Intent.ACTION_CALL);
                callIntent.setData(Uri.parse("tel:" + cleanNumber));
                startActivity(callIntent);

                Intent statusIntent = new Intent(this, CallingActivity.class);
                statusIntent.putExtra("contact_name", name);
                statusIntent.putExtra("contact_number", cleanNumber);
                startActivity(statusIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Call failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 101);
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
            sharedPreferences.edit().putString("recent_alerts", array.toString()).apply();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}