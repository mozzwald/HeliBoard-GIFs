package helium314.keyboard.latin;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.widget.SwitchCompat;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.View;

/**
 * Settings screen for Klipy GIF search.
 */
public class TenorSettingsActivity extends AppCompatActivity {
    private SwitchCompat tenorSwitch;
    private EditText apiKeyEdit;
    private TextView warningText;
    private Button getKeyButton;
    private Spinner shareSizeSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tenor_settings);
        setTitle(R.string.settings_screen_gif_tenor);

        final Context ctx = this;
        tenorSwitch = findViewById(R.id.switch_tenor_enabled);
        apiKeyEdit   = findViewById(R.id.edit_tenor_api_key);
        warningText  = findViewById(R.id.text_tenor_warning);
        getKeyButton = findViewById(R.id.btn_get_api_key);
        shareSizeSpinner = findViewById(R.id.spinner_gif_share_size);

        // Load prefs
        boolean enabled = GifPrefs.isKlipyEnabled(ctx);
        tenorSwitch.setChecked(enabled);
        String stored = GifPrefs.getStoredApiKey(ctx);
        if (stored != null) apiKeyEdit.setText(stored);

        tenorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            GifPrefs.setKlipyEnabled(ctx, isChecked);
            updateWarning();
        });
        apiKeyEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                GifPrefs.setApiKey(ctx, s.toString().trim());
                updateWarning();
            }
        });
        getKeyButton.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://partner.klipy.com/"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(ctx, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show();
            }
        });
        // Initialize GIF share size spinner
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(this,
            R.array.gif_share_size_entries, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        shareSizeSpinner.setAdapter(spinnerAdapter);
        // Set current selection from preferences
        String currentSize = GifPrefs.getShareSize(ctx);
        String[] sizeValues = getResources().getStringArray(R.array.gif_share_size_values);
        int selectedIndex = 0;
        for (int i = 0; i < sizeValues.length; i++) {
            if (sizeValues[i].equals(currentSize)) {
                selectedIndex = i;
                break;
            }
        }
        shareSizeSpinner.setSelection(selectedIndex);
        // Save selection on change
        final String[] sizeValuesFinal = sizeValues;
        shareSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                GifPrefs.setShareSize(ctx, sizeValuesFinal[position]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { /* no-op */ }
        });
        updateWarning();
    }

    private void updateWarning() {
        boolean show = tenorSwitch.isChecked()
            && (apiKeyEdit.getText() == null || apiKeyEdit.getText().toString().trim().isEmpty());
        warningText.setVisibility(show ? TextView.VISIBLE : TextView.GONE);
    }
}
