package com.example.sos;

import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SafetyTipsActivity extends AppCompatActivity {

    private TextView tvTipTitle, tvTipDescription;
    private List<Tip> tipsList;
    private Random random;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safety_tips);

        tvTipTitle = findViewById(R.id.tvTipTitle);
        tvTipDescription = findViewById(R.id.tvTipDescription);
        random = new Random();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        initializeTips();
        displayRandomTip();

        findViewById(R.id.btnNextTip).setOnClickListener(v -> displayRandomTip());
        findViewById(R.id.cvTip).setOnClickListener(v -> displayRandomTip());
    }

    private void initializeTips() {
        tipsList = new ArrayList<>();
        tipsList.add(new Tip("Stay Calm", "Take deep breaths. Panic can hinder your ability to make clear decisions. Use the SOS button immediately if you are in danger."));
        tipsList.add(new Tip("Know Your Location", "Always check for landmarks. This app automatically shares your GPS, but being able to describe where you are is vital."));
        tipsList.add(new Tip("Battery Management", "Keep your phone charged. In an emergency, turn off non-essential apps to save battery life."));
        tipsList.add(new Tip("Emergency Contacts", "Make sure your emergency contacts are up to date in the app settings so they can be notified instantly."));
        tipsList.add(new Tip("Find a Safe Place", "If possible, move to a well-lit public area or a secure building while waiting for help."));
        tipsList.add(new Tip("Medical Info", "Keep your basic medical information (blood type, allergies) handy or noted in your phone's emergency profile."));
        tipsList.add(new Tip("Communication", "If you can't speak, use the SMS feature to send your location and a short message to your contacts."));
        tipsList.add(new Tip("Trust Your Gut", "If a situation feels wrong, don't hesitate to use the SOS features or leave the area immediately."));
        tipsList.add(new Tip("Self Defense", "Awareness is your best defense. Stay alert of your surroundings and avoid using headphones in unfamiliar areas."));
        tipsList.add(new Tip("Fire Safety", "In case of fire, stay low to the ground to avoid smoke. Check doors for heat before opening them."));
        tipsList.add(new Tip("Road Safety", "If your car breaks down, stay inside with doors locked if you feel unsafe. Use the SOS app to alert your family."));
        tipsList.add(new Tip("Natural Disasters", "In an earthquake, Drop, Cover, and Hold on. In a flood, move to higher ground immediately."));
        tipsList.add(new Tip("First Aid Basics", "Learn basic CPR and how to stop bleeding. These skills can save lives before professional help arrives."));
        tipsList.add(new Tip("Personal Privacy", "Don't share your live location on social media. Use this app's secure sharing with trusted contacts only."));
        tipsList.add(new Tip("Home Security", "Keep your doors and windows locked. Install good lighting around your house to deter intruders."));
    }

    private void displayRandomTip() {
        int index = random.nextInt(tipsList.size());
        Tip randomTip = tipsList.get(index);
        tvTipTitle.setText(randomTip.getTitle());
        tvTipDescription.setText(randomTip.getDescription());
    }

    private static class Tip {
        private String title;
        private String description;

        public Tip(String title, String description) {
            this.title = title;
            this.description = description;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
    }
}