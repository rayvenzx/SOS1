package com.example.sos;

import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class CallingActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calling);

        // End Call Button
        findViewById(R.id.btnEndCall).setOnClickListener(v -> finish());

        // Control Buttons
        findViewById(R.id.llMute).setOnClickListener(v -> 
            Toast.makeText(this, "Muted", Toast.LENGTH_SHORT).show());
        
        findViewById(R.id.llKeypad).setOnClickListener(v -> 
            Toast.makeText(this, "Keypad opened", Toast.LENGTH_SHORT).show());
        
        findViewById(R.id.llSpeaker).setOnClickListener(v -> 
            Toast.makeText(this, "Speaker toggled", Toast.LENGTH_SHORT).show());
        
        findViewById(R.id.llVideoCall).setOnClickListener(v -> 
            Toast.makeText(this, "Video call unavailable", Toast.LENGTH_SHORT).show());
        
        findViewById(R.id.llAddCall).setOnClickListener(v -> 
            Toast.makeText(this, "Add call functionality coming soon", Toast.LENGTH_SHORT).show());
        
        findViewById(R.id.llContacts).setOnClickListener(v -> 
            Toast.makeText(this, "Contacts list", Toast.LENGTH_SHORT).show());
    }
}