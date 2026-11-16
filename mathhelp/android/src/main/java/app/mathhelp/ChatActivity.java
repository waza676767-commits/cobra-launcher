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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static app.mathhelp.MainActivity.dp;
import static app.mathhelp.MainActivity.toast;

public class ChatActivity extends Activity {
    /** Peer whose chat is on screen (its messages aren't counted as unread). */
    static volatile String openPeer;

    private static final int PAGE = 80;

    private String peer;
    private final List<Store.Msg> msgs = new ArrayList<>();
    private ListView lv;
    private BaseAdapter adapter;
    private EditText input;
    private boolean loadingOlder, hasMore = true;
    private int bubbleMax;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Relay.init(this);
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
        input = new GifEditText(this, this::fromKeyboard);
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
            if (t.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2000) { toast(this, "Too long — split it into smaller messages"); return; }
            input.setText("");
            Relay.send(peer, t, null, null);
        });
        attach.setOnClickListener(v -> startActivityForResult(
                new Intent(Intent.ACTION_GET_CONTENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 1));
    }

    // ---------- data (all local) ----------

    /** Re-read everything from the oldest loaded message onwards (new messages, state changes). */
    private void reload() {
        final long from = msgs.isEmpty() ? -1 : msgs.get(0).id;
        Relay.q.execute(() -> {
            List<Store.Msg> l = from < 0 ? Store.messages(peer, Long.MAX_VALUE, PAGE) : Store.messagesFrom(peer, from);
            Store.clearUnread(peer);
            Relay.ui.post(() -> {
                boolean first = msgs.isEmpty();
                boolean atBottom = lv.getLastVisiblePosition() >= msgs.size() - 1;
                boolean grew = l.size() > msgs.size();
                if (first && l.size() < PAGE) hasMore = false;
                msgs.clear();
                msgs.addAll(l);
                adapter.notifyDataSetChanged();
                if (first || (grew && atBottom) || (grew && l.get(l.size() - 1).mine)) lv.setSelection(msgs.size() - 1);
            });
        });
    }

    private void loadOlder() {
        if (msgs.isEmpty()) return;
        loadingOlder = true;
        final long before = msgs.get(0).id;
        Relay.q.execute(() -> {
            List<Store.Msg> l = Store.messages(peer, before, PAGE);
            Relay.ui.post(() -> {
                if (l.size() < PAGE) hasMore = false;
                if (!l.isEmpty() && !msgs.isEmpty() && msgs.get(0).id == before) {
                    View top = lv.getChildAt(0);
                    int off = top == null ? 0 : top.getTop();
                    msgs.addAll(0, l);
                    adapter.notifyDataSetChanged();
                    lv.setSelectionFromTop(l.size(), off);
                }
                loadingOlder = false;
            });
        });
    }

    // ---------- attachments ----------

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == 1 && res == RESULT_OK && data != null && data.getData() != null)
            attach(data.getData(), getContentResolver().getType(data.getData()), null, null);
    }

    private void fromKeyboard(InputContentInfo info) {
        String mime = info.getDescription().getMimeTypeCount() > 0 ? info.getDescription().getMimeType(0) : null;
        Uri link = info.getLinkUri();
        attach(info.getContentUri(), mime, link == null ? null : link.toString(), info::releasePermission);
    }

    /** Copy the picked image into app storage (shrinking big photos to fit the 2 MB relay limit), then send. */
    private void attach(Uri uri, String mime, String fallbackLink, Runnable done) {
        ContentResolver cr = getContentResolver();
        Relay.q.execute(() -> {
            try {
                byte[] b = Relay.readAll(cr.openInputStream(uri));
                String type = mime == null || !mime.startsWith("image/") ? "image/jpeg" : mime;
                boolean animated = type.equals("image/gif") || type.equals("image/webp");
                if (b.length > 600_000 && !animated) {
                    b = shrink(b, 1600, 82);
                    if (b.length > Relay.MAX_FILE) b = shrink(b, 1024, 70);
                    type = "image/jpeg";
                }
                if (b.length > Relay.MAX_FILE) {
                    if (fallbackLink != null) { Relay.send(peer, fallbackLink, null, null); return; }
                    Relay.ui.post(() -> toast(this, "Too big — the free relay allows 2 MB per image/GIF"));
                    return;
                }
                File f = new File(Store.mediaDir, "s" + Crypto.hex(Crypto.random(10)));
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(b); }
                Relay.send(peer, "", f.getAbsolutePath(), type);
            } catch (Exception e) {
                Relay.ui.post(() -> toast(this, "Couldn't read image: " + e.getMessage()));
            } finally {
                if (done != null) try { done.run(); } catch (Exception ignored) {}
            }
        });
    }

    private static byte[] shrink(byte[] b, int max, int quality) throws Exception {
        Bitmap bm = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(b)), (d, i, s) -> {
            int w = i.getSize().getWidth(), h = i.getSize().getHeight();
            if (Math.max(w, h) > max) {
                float k = (float) max / Math.max(w, h);
                d.setTargetSize(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)));
            }
        });
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        bm.compress(Bitmap.CompressFormat.JPEG, quality, o);
        return o.toByteArray();
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
        public long getItemId(int i) { return msgs.get(i).id; }
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

            Store.Msg m = msgs.get(pos);
            r.outer.setGravity(m.mine ? Gravity.END : Gravity.START);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(c, 14));
            bg.setColor(m.mine ? 0xFF2F6FDB : 0x40808080);
            r.bubble.setBackground(bg);
            if (m.mine) { r.text.setTextColor(0xFFFFFFFF); r.time.setTextColor(0xFFFFFFFF); }
            else { r.text.setTextColor(r.fg); r.time.setTextColor(r.fg); }
            r.text.setLinkTextColor(m.mine ? 0xFFDDEEFF : 0xFF3B82F6);

            String text = m.text;
            r.text.setAutoLinkMask(Linkify.WEB_URLS);
            r.text.setText(text);
            r.text.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            String time = DateUtils.formatSameDayTime(m.ts, System.currentTimeMillis(),
                    java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).toString();
            r.time.setText(m.state == Store.PENDING ? time + "  · waiting to send" : time);

            String key = m.media != null ? m.media : (Img.isImageUrl(text) ? text : null);
            if (key != null) {
                Img.bind(r.img, key);
                r.img.setOnClickListener(v -> fullscreen(key));
            } else {
                r.img.setTag(null);
                r.img.setImageDrawable(null);
                r.img.setVisibility(View.GONE);
            }
            View.OnLongClickListener copy = v -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("msg", text));
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
        Relay.onChange = this::reload;
        Relay.start();
        reload();
    }

    @Override
    protected void onPause() {
        super.onPause();
        openPeer = null;
        Relay.stop();
    }
}
