package dev.saygo.app;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Android-only fixture running in the separate test APK process. */
public class ControlTargetActivity extends Activity {
    private LinearLayout layout;
    private TextView result;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setFocusableInTouchMode(true);
        result = new TextView(this);
        result.setText("No action");
        layout.addView(result);
        button("Search", "Exact tap");
        button("Search more", "Wrong tap");
        Button icon = button("Icon", "Description tap");
        icon.setContentDescription("Play media");
        button("Hold item", "Wrong short tap").setOnLongClickListener(v -> { result.setText("Long press received"); return true; });
        button("Duplicate", "Wrong duplicate one");
        button("Duplicate", "Wrong duplicate two");
        button("Unavailable", "Wrong disabled tap").setEnabled(false);
        LinearLayout parent = new LinearLayout(this);
        parent.setOnClickListener(v -> result.setText("Parent tap"));
        TextView label = new TextView(this);
        label.setText("Nested control");
        parent.addView(label);
        layout.addView(parent);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setHint("Message");
        field.setContentDescription("Message");
        field.setShowSoftInputOnFocus(false);
        layout.addView(field);
        EditText password = new EditText(this);
        password.setSingleLine(true);
        password.setContentDescription("Password field");
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        password.setShowSoftInputOnFocus(false);
        layout.addView(password);
        setContentView(layout);
        layout.requestFocus();
    }
    private Button button(String label, String outcome) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> result.setText(outcome));
        layout.addView(button);
        return button;
    }
}
