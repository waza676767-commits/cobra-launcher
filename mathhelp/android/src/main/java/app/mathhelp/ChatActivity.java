package app.mathhelp;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.text.util.Linkify;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static app.mathhelp.MainActivity.dp;
import static app.mathhelp.MainActivity.toast;

public class ChatActivity extends Activity {
    /** Peer whose chat is currently on screen (so it isn't counted as unread). */
    static String openPeer;

    private String peer;
    private final List<JSONObject> msgs = new ArrayList<>();
    private final Set<Long> ids = new HashSet<>();
    private ListView lv;
    private BaseAdapter adapter;
    private EditText input;
    private boolean loadingOlder, hasMore = true;
    private int bubbleMax;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Api.init(this);
        peer = getIntent().getStringExtra("peer");
        setTitle(peer);
        bubbleMax = getResources().getDisplayMetrics().widthPixels * 4 / 5;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        lv = new ListView(this);
        lv.setDivider(null);
        lv.setStackFromBottom(true);
        lv.setTranscriptMode(AbsListView.TRANSCRIPT_MODE_NORMAL);
        lv.setClipToPadding(false);
        lv.setPadding(0, dp(this, 6), 0, dp(this, 6));
        adapter = new Adapter();
        lv.setAdapter(adapter);
        lv.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView v, int s) {}
            public void onScroll(AbsListView v, int first, int vis, int total) {
                if (first == 0 && total > 0 && hasMore && !loadingOlder) loadOlder();
            }
        });
        root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.BOTTOM);
        int p = dp(this, 4);
        bar.setPadding(p, p, p, p);
        Button attach = new Button(this);
        attach.setText("+");
        attach.setMinWidth(0);
        attach.setMinimumWidth(dp(this, 48));
        input = new GifEditText(this, this::uploadKeyboardContent);
        input.setHint("Message");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMaxLines(5);
        Button send = new Button(this);
        send.setText("Send");
        bar.addView(attach, new LinearLayout.LayoutParams(-2, -2));
        bar.addView(input, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(send, new LinearLayout.LayoutParams(-2, -2));
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        setContentView(root);

        send.setOnClickListener(v -> {
            String t = input.getText().toString().trim();
            if (t.isEmpty()) return;
            input.setText("");
            send(t, null);
        });
        attach.setOnClickListener(v -> startActivityForResult(
                new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 1));

        loadHistory(null);
    }

    // ---------- data ----------

    private boolean ours(JSONObject m) {
        String f = m.optString("from"), t = m.optString("to");
        return (f.equalsIgnoreCase(peer) && t.equalsIgnoreCase(Api.user))
                || (f.equalsIgnoreCase(Api.user) && t.equalsIgnoreCase(peer));
    }

    /** Merge messages, keeping order by id; returns how many were new. */
    private int merge(List<JSONObject> in) {
        int added = 0;
        for (JSONObject m : in) if (m != null && ours(m) && ids.add(m.optLong("id"))) { msgs.add(m); added++; }
        if (added > 0) {
            Collections.sort(msgs, (a, c) -> Long.compare(a.optLong("id"), c.optLong("id")));
            adapter.notifyDataSetChanged();
        }
        return added;
    }

    private void loadHistory(Long before) {
        String q = "/history?with=" + Api.enc(peer) + "&limit=60" + (before == null ? "" : "&before=" + before);
        Api.io.execute(() -> {
            try {
                List<JSONObject> l = Api.list(new JSONArray(Api.get(q)));
                Api.ui.post(() -> {
                    if (before == null) { merge(l); lv.setSelection(msgs.size() - 1); }
                    else {
                        View top = lv.getChildAt(0);
                        int off = top == null ? 0 : top.getTop();
                        int n = merge(l);
                        lv.setSelectionFromTop(n, off);
                    }
                    if (l.size() < 60) hasMore = false;
                    loadingOlder = false;
                });
            } catch (Exception e) {
                Api.ui.post(() -> { loadingOlder = false; toast(this, String.valueOf(e.getMessage())); });
            }
        });
    }

    private void loadOlder() {
        if (msgs.isEmpty()) return;
        loadingOlder = true;
        loadHistory(msgs.get(0).optLong("id"));
    }

    private void send(String text, String media) {
        Api.io.execute(() -> {
            try {
                JSONObject body = Api.obj("to", peer, "text", text == null ? "" : text);
                if (media != null) body.put("media", media);
                JSONObject m = new JSONObject(Api.postJson("/send", body));
                Api.ui.post(() -> {
                    merge(Collections.singletonList(m));
                    lv.setSelection(msgs.size() - 1);
                });
            } catch (Exception e) {
                Api.ui.post(() -> {
                    toast(this, "Not sent: " + e.getMessage());
                    if (text != null && input.getText().length() == 0) input.setText(text);
                });
            }
        });
    }

    // ---------- attachments ----------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && res == RESULT_OK && data != null && data.getData() != null)
            upload(data.getData(), getContentResolver().getType(data.getData()), null);
    }

    private void uploadKeyboardContent(InputContentInfo info) {
        upload(info.getContentUri(), info.getDescription().getMimeTypeCount() > 0
                ? info.getDescription().getMimeType(0) : null, info::releasePermission);
    }

    private void upload(Uri uri, String mime, Runnable done) {
        ContentResolver cr = getContentResolver();
        toast(this, "Sending image…");
        Api.io.execute(() -> {
            try {
                byte[] b = Api.readAll(cr.openInputStream(uri));
                String type = mime == null || !mime.startsWith("image/") ? "image/jpeg" : mime;
                // Shrink big photos; keep GIF/WebP untouched so animation survives.
                if (b.length > 700_000 && !type.equals("image/gif") && !type.equals("image/webp")) {
                    Bitmap bm = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(b)), (d, i, s) -> {
                        int w = i.getSize().getWidth(), h = i.getSize().getHeight(), max = 1600;
                        if (Math.max(w, h) > max) {
                            float k = (float) max / Math.max(w, h);
                            d.setTargetSize(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)));
                        }
                    });
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    bm.compress(Bitmap.CompressFormat.JPEG, 85, o);
                    b = o.toByteArray();
                    type = "image/jpeg";
                }
                String id = new JSONObject(new String(Api.req(Api.server, "POST", "/upload", b, type, 60000), "UTF-8")).getString("id");
                send(null, id);
            } catch (Exception e) {
                Api.ui.post(() -> toast(this, "Upload failed: " + e.getMessage()));
            } finally {
                if (done != null) try { done.run(); } catch (Exception ignored) {}
            }
        });
    }

    /** EditText that accepts GIFs/stickers/images straight from the keyboard (Gboard etc.). */
    static final class GifEditText extends EditText {
        interface Sink { void accept(InputContentInfo i); }
        private final Sink sink;

        GifEditText(Context c, Sink s) { super(c); sink = s; }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo out) {
            InputConnection ic = super.onCreateInputConnection(out);
            if (ic == null) return null;
            out.contentMimeTypes = new String[]{"image/gif", "image/png", "image/jpeg", "image/webp"};
            return new InputConnectionWrapper(ic, true) {
                @Override
                public boolean commitContent(InputContentInfo info, int flags, Bundle opts) {
                    if ((flags & InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                        try { info.requestPermission(); } catch (Exception e) { return false; }
                    }
                    sink.accept(info);
                    return true;
                }
            };
        }
    }

    // ---------- list ----------

    private static final class Row {
        LinearLayout outer, bubble;
        TextView text, time;
        ImageView img;
        android.content.res.ColorStateList fg;
    }

    private final class Adapter extends BaseAdapter {
        public int getCount() { return msgs.size(); }
        public Object getItem(int i) { return msgs.get(i); }
        public long getItemId(int i) { return msgs.get(i).optLong("id"); }
        @Override public boolean hasStableIds() { return true; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Context c = ChatActivity.this;
            Row r;
            if (cv == null) {
                r = new Row();
                r.outer = new LinearLayout(c);
                r.outer.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
                r.bubble = new LinearLayout(c);
                r.bubble.setOrientation(LinearLayout.VERTICAL);
                int p = dp(c, 8);
                r.bubble.setPadding(p + dp(c, 2), p - dp(c, 2), p + dp(c, 2), p - dp(c, 2));
                r.img = new ImageView(c);
                r.img.setAdjustViewBounds(true);
                r.img.setMaxWidth(bubbleMax);
                r.img.setMaxHeight(dp(c, 360));
                r.img.setScaleType(ImageView.ScaleType.FIT_START);
                r.text = new TextView(c);
                r.text.setTextSize(16);
                r.text.setMaxWidth(bubbleMax);
                r.time = new TextView(c);
                r.time.setTextSize(11);
                r.time.setAlpha(0.6f);
                r.bubble.addView(r.img, new LinearLayout.LayoutParams(-2, -2));
                r.bubble.addView(r.text, new LinearLayout.LayoutParams(-2, -2));
                r.bubble.addView(r.time, new LinearLayout.LayoutParams(-2, -2));
                r.outer.addView(r.bubble, new LinearLayout.LayoutParams(-2, -2));
                r.fg = r.text.getTextColors();
                r.outer.setTag(r);
                cv = r.outer;
            } else r = (Row) cv.getTag();

            JSONObject m = msgs.get(pos);
            boolean mine = m.optString("from").equalsIgnoreCase(Api.user);
            r.outer.setGravity(mine ? Gravity.END : Gravity.START);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(c, 14));
            bg.setColor(mine ? 0xFF2F6FDB : 0x40808080);
            r.bubble.setBackground(bg);
            if (mine) { r.text.setTextColor(0xFFFFFFFF); r.time.setTextColor(0xFFFFFFFF); }
            else { r.text.setTextColor(r.fg); r.time.setTextColor(r.fg); }
            r.text.setLinkTextColor(mine ? 0xFFDDEEFF : 0xFF3B82F6);

            String text = m.optString("text");
            r.text.setAutoLinkMask(Linkify.WEB_URLS);
            r.text.setText(text);
            r.text.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            r.time.setText(DateUtils.formatSameDayTime(m.optLong("ts"), System.currentTimeMillis(),
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT));

            String media = m.isNull("media") ? null : m.optString("media", null);
            String key = media != null ? media : (Img.isImageUrl(text) ? text : null);
            if (key != null) {
                Img.bind(r.img, key);
                r.img.setOnClickListener(v -> fullscreen(key));
            } else {
                r.img.setTag(null);
                r.img.setImageDrawable(null);
                r.img.setVisibility(View.GONE);
            }
            View.OnLongClickListener copy = v -> {
                String t = text.isEmpty() ? (key == null ? "" : key) : text;
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("msg", t));
                toast(c, "Copied");
                return true;
            };
            r.bubble.setOnLongClickListener(copy);
            r.text.setOnLongClickListener(copy);
            return cv;
        }
    }

    private void fullscreen(String key) {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setOnClickListener(v -> d.dismiss());
        d.setContentView(iv);
        Img.bind(iv, key);
        d.show();
    }

    // ---------- lifecycle ----------

    @Override
    protected void onResume() {
        super.onResume();
        openPeer = peer;
        Api.unread.remove(peer);
        Api.listener = l -> {
            boolean atBottom = lv.getLastVisiblePosition() >= msgs.size() - 1;
            if (merge(l) > 0 && atBottom) lv.setSelection(msgs.size() - 1);
        };
        Api.startPoll();
        if (!msgs.isEmpty()) loadHistory(null); // catch up anything missed while paused
    }

    @Override
    protected void onPause() {
        super.onPause();
        openPeer = null;
        Api.stopPoll();
    }
}
