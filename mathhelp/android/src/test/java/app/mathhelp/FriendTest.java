package app.mathhelp;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Method;
import java.security.KeyPair;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FriendTest {
    static int n = 0;
    static Method handle;

    static String deliver(JSONObject env) throws Exception {
        if (handle == null) { handle = Relay.class.getDeclaredMethod("handle", JSONObject.class); handle.setAccessible(true); }
        JSONObject ev = new JSONObject().put("event", "message").put("id", "ev" + (n++)).put("time", 1)
                .put("message", env.toString());
        String r = (String) Relay.in.submit(() -> { try { return (String) handle.invoke(null, ev); } catch (Exception e) { throw new RuntimeException(e); } }).get(10, TimeUnit.SECONDS);
        Relay.out.submit(() -> {}).get(60, TimeUnit.SECONDS);
        return r;
    }

    static JSONObject msg(String from, KeyPair kp, String text) throws Exception {
        String pub = Crypto.b64(kp.getPublic().getEncoded());
        byte[] key = Crypto.shared(kp.getPrivate(), Store.pub);
        String c = Crypto.b64(Crypto.seal(key, new JSONObject().put("id", "x" + n).put("x", text).toString().getBytes("UTF-8")));
        return new JSONObject().put("t", "m").put("f", from).put("k", pub).put("c", c);
    }

    @Test public void friendRules() throws Exception {
        Relay.init(RuntimeEnvironment.getApplication());
        Store.createUser("me");
        KeyPair bob = Crypto.generate(), eve = Crypto.generate();
        String bobPub = Crypto.b64(bob.getPublic().getEncoded());

        // stranger messages are dropped
        assertNull(deliver(msg("eve", eve, "spam")));
        assertNull(Store.contact("eve"));

        // bob's friend request shows up as incoming
        assertEquals("bob: wants to be your friend",
                deliver(new JSONObject().put("t", "h").put("f", "bob").put("k", bobPub).put("a", 0)));
        assertEquals(Store.IN, Store.contact("bob").state);

        // messages before accepting are dropped
        assertNull(deliver(msg("bob", bob, "too early")));
        assertEquals(0, Store.messages("bob", Long.MAX_VALUE, 10).size());

        // accept -> friends; messages now arrive and decrypt
        Relay.acceptFriend("bob");
        Relay.out.submit(() -> {}).get(60, TimeUnit.SECONDS);
        assertEquals(Store.FRIEND, Store.contact("bob").state);
        assertEquals("bob: hello me", deliver(msg("bob", bob, "hello me")));
        assertEquals("hello me", Store.messages("bob", Long.MAX_VALUE, 10).get(0).text);

        // someone else pretending to be bob (different key) is dropped
        assertNull(deliver(msg("bob", eve, "i am bob trust me")));
        assertEquals(1, Store.messages("bob", Long.MAX_VALUE, 10).size());

        // outgoing request + their accept -> friends
        Store.addContact("carol", null, Store.OUT);
        KeyPair carol = Crypto.generate();
        assertEquals("carol: accepted your friend request", deliver(new JSONObject().put("t", "h").put("f", "carol")
                .put("k", Crypto.b64(carol.getPublic().getEncoded())).put("a", 1)));
        assertEquals(Store.FRIEND, Store.contact("carol").state);

        // an "accept" from someone we never asked does nothing
        assertNull(deliver(new JSONObject().put("t", "h").put("f", "dave")
                .put("k", Crypto.b64(eve.getPublic().getEncoded())).put("a", 1)));
        assertNull(Store.contact("dave"));
    }
}
