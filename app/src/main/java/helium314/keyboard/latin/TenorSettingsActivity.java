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

/**
 * Settings screen for Tenor GIF search.
 */
public class TenorSettingsActivity extends AppCompatActivity {
    private SwitchCompat tenorSwitch;
    private EditText apiKeyEdit;
    private TextView warningText;
    private Button getKeyButton;

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

        // Load prefs
        boolean enabled = GifPrefs.isTenorEnabled(ctx);
        tenorSwitch.setChecked(GifPrefs.isTenorEnabled(ctx));
        String stored = GifPrefs.getStoredApiKey(ctx);
        if (stored != null) apiKeyEdit.setText(stored);

        tenorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            GifPrefs.setTenorEnabled(ctx, isChecked);
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
                    Uri.parse("https://developers.google.com/tenor/guides/quickstart"));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                Toast.makeText(ctx, R.string.unable_to_open_link, Toast.LENGTH_SHORT).show();
            }
        });
        updateWarning();
    }

    private void updateWarning() {
        boolean show = tenorSwitch.isChecked()
            && (apiKeyEdit.getText() == null || apiKeyEdit.getText().toString().trim().isEmpty());
        warningText.setVisibility(show ? TextView.VISIBLE : TextView.GONE);
    }
}