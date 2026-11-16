package app.tinymsg;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Login screen, then the contact list. */
public class MainActivity extends Activity {
    private final List<String> peers = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Api.init(this);
        if (Api.loggedIn()) showContacts(); else showLogin();
    }

    // ---------- login ----------

    private EditText field(LinearLayout parent, String hint, String val, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(val);
        e.setSingleLine(true);
        e.setInputType(type);
        parent.addView(e, new LinearLayout.LayoutParams(-1, -2));
        return e;
    }

    private void showLogin() {
        setTitle("Msg — sign in");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(this, 20);
        l.setPadding(p, p, p, p);
        EditText srv = field(l, "Server (e.g. 1.2.3.4:8080)", Api.server, InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_CLASS_TEXT);
        EditText usr = field(l, "Username", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        EditText pw = field(l, "Password", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        LinearLayout row = new LinearLayout(this);
        Button login = new Button(this), reg = new Button(this);
        login.setText("Log in");
        reg.setText("Create account");
        row.addView(login, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(reg, new LinearLayout.LayoutParams(0, -2, 1));
        l.addView(row);
        TextView note = new TextView(this);
        note.setText("Pick any username — others add you by it.");
        note.setPadding(0, dp(this, 12), 0, 0);
        l.addView(note);
        setContentView(l);

        View.OnClickListener go = v -> {
            String s = Api.normServer(srv.getText().toString());
            String u = usr.getText().toString().trim(), pass = pw.getText().toString();
            if (u.isEmpty() || pass.isEmpty()) { toast(this, "Enter username and password"); return; }
            String path = v == reg ? "/register" : "/login";
            login.setEnabled(false); reg.setEnabled(false);
            Api.io.execute(() -> {
                try {
                    JSONObject r = new JSONObject(Api.postJson(s, path, Api.obj("user", u, "pass", pass)));
                    Api.ui.post(() -> {
                        Api.saveLogin(s, r.optString("user"), r.optString("token"));
                        showContacts();
                        Api.startPoll();
                    });
                } catch (Exception e) {
                    Api.ui.post(() -> {
                        login.setEnabled(true); reg.setEnabled(true);
                        toast(this, e.getMessage() == null ? "Can't reach server" : e.getMessage());
                    });
                }
            });
        };
        login.setOnClickListener(go);
        reg.setOnClickListener(go);
    }

    // ---------- contacts ----------

    private void showContacts() {
        setTitle(Api.user);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(this, 8);
        row.setPadding(p, p, p, 0);
        EditText add = new EditText(this);
        add.setHint("Add by username");
        add.setSingleLine(true);
        add.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        add.setImeOptions(EditorInfo.IME_ACTION_DONE);
        Button btn = new Button(this);
        btn.setText("Add");
        row.addView(add, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(btn);
        l.addView(row);

        ListView lv = new ListView(this);
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, peers) {
            @Override
            public View getView(int pos, View cv, ViewGroup parent) {
                TextView t = (TextView) super.getView(pos, cv, parent);
                String u = peers.get(pos);
                Integer n = Api.unread.get(u);
                t.setText(n == null ? u : u + "   (" + n + " new)");
                t.setTypeface(null, n == null ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
                return t;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((a, v, pos, id) -> open(peers.get(pos)));
        TextView empty = new TextView(this);
        empty.setText("No chats yet. Add someone by their username above.");
        empty.setPadding(dp(this, 16), dp(this, 24), dp(this, 16), 0);
        l.addView(empty);
        lv.setEmptyView(empty);
        l.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(l);
        invalidateOptionsMenu();
        Api.listener = msgs -> refresh();
        refresh();

        Runnable doAdd = () -> {
            String u = add.getText().toString().trim();
            if (u.isEmpty()) return;
            Api.io.execute(() -> {
                try {
                    String name = new JSONObject(Api.postJson("/contacts", Api.obj("user", u))).optString("user");
                    Api.ui.post(() -> { add.setText(""); open(name); });
                } catch (Exception e) {
                    Api.ui.post(() -> toast(this, String.valueOf(e.getMessage())));
                }
            });
        };
        btn.setOnClickListener(v -> doAdd.run());
        add.setOnEditorActionListener((v, a, e) -> { doAdd.run(); return true; });
    }

    private void open(String peer) {
        startActivity(new Intent(this, ChatActivity.class).putExtra("peer", peer));
    }

    private void refresh() {
        if (adapter == null || !Api.loggedIn()) return;
        Api.io.execute(() -> {
            try {
                JSONArray a = new JSONArray(Api.get("/contacts"));
                List<String> r = new ArrayList<>();
                for (int i = 0; i < a.length(); i++) r.add(a.getJSONObject(i).getString("user"));
                Api.ui.post(() -> { peers.clear(); peers.addAll(r); adapter.notifyDataSetChanged(); });
            } catch (Exception ignored) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!Api.loggedIn()) return;
        Api.listener = msgs -> refresh();
        Api.startPoll();
        refresh();
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Api.stopPoll();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        if (Api.loggedIn()) m.add("Log out");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        Api.logout();
        adapter = null;
        showLogin();
        invalidateOptionsMenu();
        return true;
    }
}
