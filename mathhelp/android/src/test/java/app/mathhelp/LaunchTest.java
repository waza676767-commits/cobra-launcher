package app.mathhelp;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {28, 31, 34, 36})
public class LaunchTest {
    static <T extends View> List<T> find(View v, Class<T> c, List<T> out) {
        if (c.isInstance(v)) out.add(c.cast(v));
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) find(((ViewGroup) v).getChildAt(i), c, out);
        return out;
    }

    static void drain() throws Exception {
        for (int i = 0; i < 20; i++) {
            Relay.q.submit(() -> {}).get(5, TimeUnit.SECONDS);
            ShadowLooper.idleMainLooper();
        }
    }

    @Test public void fullFlow() throws Exception {
        ActivityController<MainActivity> mc = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity m = mc.get();
        View root = m.getWindow().getDecorView();
        find(root, EditText.class, new ArrayList<>()).get(0).setText("tester");
        find(root, Button.class, new ArrayList<>()).get(0).performClick();
        drain();
        root = m.getWindow().getDecorView();
        find(root, EditText.class, new ArrayList<>()).get(0).setText("friend");
        find(root, Button.class, new ArrayList<>()).get(0).performClick();
        drain();
        Intent next = Shadows.shadowOf(m).getNextStartedActivity();
        mc.pause();
        ActivityController<ChatActivity> cc = Robolectric.buildActivity(ChatActivity.class, next).setup();
        View croot = cc.get().getWindow().getDecorView();
        find(croot, EditText.class, new ArrayList<>()).get(0).setText("hello https://example.com");
        List<Button> bs = find(croot, Button.class, new ArrayList<>());
        bs.get(bs.size() - 1).performClick();
        drain();
        cc.pause().stop().destroy();
        mc.resume();
        drain();
        mc.pause().stop().destroy();
    }
}
