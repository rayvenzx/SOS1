package com.example.sos;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class GroupSosActivity extends AppCompatActivity {

    private LinearLayout llMembersContainer, llEmptyState;
    private SharedPreferences sharedPreferences;
    private List<GroupMember> memberList = new ArrayList<>();

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

        // Drawer Setup
        androidx.drawerlayout.widget.DrawerLayout drawerLayout = findViewById(R.id.drawerLayout);
        findViewById(R.id.btnMenu).setOnClickListener(v -> drawerLayout.openDrawer(androidx.core.view.GravityCompat.START));
        findViewById(R.id.ivLocationIcon).setOnClickListener(v -> startActivity(new Intent(this, FullMapActivity.class)));

        com.google.android.material.navigation.NavigationView navigationView = findViewById(R.id.navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_add_member) {
                showSelectMemberTypeBottomSheet();
            } else if (id == R.id.nav_view_members) {
                // Already displaying members in this activity
                Toast.makeText(this, "You are viewing members", Toast.LENGTH_SHORT).show();
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

        loadMembers();
        setupBottomNavigation();
        
        findViewById(R.id.fabAddMember).setOnClickListener(v -> {
            showSelectMemberTypeBottomSheet();
        });
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

        // Icon based on type (simplified)
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
        String json = sharedPreferences.getString("contacts_json", "[]");
        try {
            JSONArray array = new JSONArray(json);
            if (array.length() == 0) {
                showCallInputDialog("Emergency", "911");
            } else if (array.length() == 1) {
                JSONObject obj = array.getJSONObject(0);
                showCallInputDialog(obj.getString("name"), obj.getString("number"));
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
                        showCallInputDialog(obj.getString("name"), obj.getString("number"));
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
            android.content.Intent callIntent = new android.content.Intent(android.content.Intent.ACTION_CALL);
            callIntent.setData(android.net.Uri.parse("tel:" + number));
            startActivity(callIntent);

            Intent statusIntent = new Intent(this, CallingActivity.class);
            statusIntent.putExtra("contact_name", name);
            statusIntent.putExtra("contact_number", number);
            startActivity(statusIntent);
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CALL_PHONE}, 101);
        }
    }
}