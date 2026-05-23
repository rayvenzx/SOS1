package com.example.sos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class AlertsActivity extends AppCompatActivity {

    private LinearLayout containerAlerts;
    private TextView tvEmptyState;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);

        containerAlerts = findViewById(R.id.containerAlerts);
        tvEmptyState = findViewById(R.id.tvEmptyState);
        sharedPreferences = getSharedPreferences("SOS_Prefs", Context.MODE_PRIVATE);

        loadAlerts();
    }

    private void loadAlerts() {
        String alertsJson = sharedPreferences.getString("recent_alerts", "[]");
        try {
            JSONArray array = new JSONArray(alertsJson);
            if (array.length() == 0) {
                tvEmptyState.setVisibility(View.VISIBLE);
            } else {
                tvEmptyState.setVisibility(View.GONE);
                for (int i = array.length() - 1; i >= 0; i--) {
                    JSONObject obj = array.getJSONObject(i);
                    addAlertView(obj.getString("title"), obj.getString("time"), obj.getString("desc"));
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
            tvEmptyState.setVisibility(View.VISIBLE);
        }
    }

    private void addAlertView(String title, String time, String desc) {
        View view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, containerAlerts, false);
        TextView text1 = view.findViewById(android.R.id.text1);
        TextView text2 = view.findViewById(android.R.id.text2);

        text1.setText(title + " (" + time + ")");
        text1.setTextColor(getResources().getColor(R.color.primary));
        text1.setTextSize(18);

        text2.setText(desc);
        text2.setTextColor(getResources().getColor(R.color.text_dark));
        
        view.setPadding(0, 16, 0, 16);
        containerAlerts.addView(view);
        
        // Add a separator
        View separator = new View(this);
        separator.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2));
        separator.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));
        containerAlerts.addView(separator);
    }
}