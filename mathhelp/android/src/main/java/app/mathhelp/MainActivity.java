package app.mathhelp;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** First run: pick a username. After that: the contact list. */
public class MainActivity extends Activity {
    private final List<Store.Contact> peers = new ArrayList<>();
    private BaseAdapter adapter;

    static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static void toast(Context c, String s) { Toast.makeText(c, s, Toast.LENGTH_SHORT).show(); }

    static boolean validName(String s) {
        return s.matches("[A-Za-z0-9_.-]{2,24}");
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Relay.init(this);
        if (Store.hasUser()) showContacts(); else showSetup();
        showLastCrash();
    }

    private void showLastCrash() {
        java.io.File f = App.crashFile;
        if (f == null || !f.exists()) return;
        String report;
        try { report = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8"); } catch (Exception e) { return; }
        f.delete();
        TextView tv = new TextView(this);
        tv.setText(report);
        tv.setTextSize(11);
        tv.setTextIsSelectable(true);
        int p = dp(this, 16);
        tv.setPadding(p, p, p, p);
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle("mathhelp crashed last time").setView(sv)
                .setPositiveButton("Copy", (d, w) -> {
                    ((android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                            .setPrimaryClip(android.content.ClipData.newPlainText("crash", report));
                    toast(this, "Copied — paste it to the developer");
                })
                .setNegativeButton("Close", null).show();
    }

    // ---------- first run ----------

    private void showSetup() {
        setTitle("mathhelp");
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        int p = dp(this, 20);
        l.setPadding(p, p, p, p);
        EditText usr = new EditText(this);
        usr.setHint("Choose a username");
        usr.setSingleLine(true);
        usr.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        l.addView(usr, new LinearLayout.LayoutParams(-1, -2));
        Button go = new Button(this);
        go.setText("Start");
        l.addView(go, new LinearLayout.LayoutParams(-1, -2));
        TextView note = new TextView(this);
        note.setText("Friends add you by this username. Pick something unique — 2-24 letters, digits, _ . -\n\n" +
                "No account or password needed. Messages are end-to-end encrypted and your history stays on this phone.");
        note.setPadding(0, dp(this, 12), 0, 0);
        l.addView(note);
        setContentView(l);
        go.setOnClickListener(v -> {
            String u = usr.getText().toString().trim();
            if (!validName(u)) { toast(this, "2-24 characters: letters, digits, _ . -"); return; }
            go.setEnabled(false);
            Relay.q.execute(() -> {
                try {
                    Store.createUser(u);
                    Relay.ui.post(() -> { showContacts(); goLive(); });
                } catch (Exception e) {
                    Relay.ui.post(() -> { go.setEnabled(true); toast(this, "Error: " + e); });
                }
            });
        });
    }

    // ---------- contacts ----------

    private void showContacts() {
        setTitle(Store.user);
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
        adapter = new BaseAdapter() {
            public int getCount() { return peers.size(); }
            public Object getItem(int i) { return peers.get(i); }
            public long getItemId(int i) { return i; }
            public View getView(int pos, View cv, ViewGroup parent) {
                TextView t;
                if (cv != null) t = (TextView) cv;
                else {
                    t = new TextView(MainActivity.this);
                    t.setTextSize(17);
                    t.setPadding(dp(MainActivity.this, 16), dp(MainActivity.this, 14), dp(MainActivity.this, 16), dp(MainActivity.this, 14));
                }
                Store.Contact c = peers.get(pos);
                String s = c.name;
                if (c.unread > 0) s += "   (" + c.unread + " new)";
                if (c.bad) s += "   ⚠ key changed — long-press";
                else if (c.pub == null) s += "   (waiting for them to open the app)";
                t.setText(s);
                t.setTypeface(null, c.unread > 0 ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                return t;
            }
        };
        lv.setAdapter(adapter);
        lv.setOnItemClickListener((a, v, pos, id) -> open(peers.get(pos).name));
        lv.setOnItemLongClickListener((a, v, pos, id) -> { menu(peers.get(pos)); return true; });
        TextView empty = new TextView(this);
        empty.setText("No chats yet. Add someone by their username above.\n\nYour username: " + Store.user);
        empty.setPadding(dp(this, 16), dp(this, 24), dp(this, 16), 0);
        l.addView(empty);
        lv.setEmptyView(empty);
        l.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(l);

        Runnable doAdd = () -> {
            String u = add.getText().toString().trim();
            if (!validName(u)) { toast(this, "Not a valid username"); return; }
            if (u.equalsIgnoreCase(Store.user)) { toast(this, "That's you"); return; }
            add.setText("");
            Relay.addContact(u);
            open(u);
        };
        btn.setOnClickListener(v -> doAdd.run());
        add.setOnEditorActionListener((v, a, e) -> { doAdd.run(); return true; });
    }

    private void menu(Store.Contact c) {
        new AlertDialog.Builder(this).setTitle(c.name)
                .setItems(new String[]{"Re-trust key (if they reinstalled)", "Delete chat"}, (d, which) -> Relay.q.execute(() -> {
                    if (which == 0) { Store.setPub(c.name, null); Relay.addContact(c.name); }
                    else Store.deleteContact(c.name);
                    Relay.ui.post(this::refresh);
                })).show();
    }

    private void open(String peer) {
        startActivity(new Intent(this, ChatActivity.class).putExtra("peer", peer));
    }

    private void refresh() {
        if (adapter == null) return;
        Relay.q.execute(() -> {
            List<Store.Contact> r = Store.contacts();
            Relay.ui.post(() -> { peers.clear(); peers.addAll(r); adapter.notifyDataSetChanged(); });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Store.hasUser()) goLive();
    }

    private void goLive() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        SyncJob.schedule(this);
        Relay.onChange = this::refresh;
        Relay.start();
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Relay.stop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        if (Store.hasUser()) m.add("My username: " + Store.user);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem i) {
        toast(this, "Tell friends to add: " + Store.user);
        return true;
    }
}
