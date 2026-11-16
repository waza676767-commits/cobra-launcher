package app.mathhelp;

import android.util.Base64;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** P-256 ECDH key agreement + AES-256-GCM. */
final class Crypto {
    static final SecureRandom rng = new SecureRandom();

    static String b64(byte[] b) { return Base64.encodeToString(b, Base64.NO_WRAP); }
    static byte[] unb64(String s) { return Base64.decode(s, Base64.NO_WRAP); }

    static KeyPair generate() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"), rng);
        return g.generateKeyPair();
    }

    static PublicKey pub(String b64) throws Exception {
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(unb64(b64)));
    }

    static PrivateKey priv(String b64) throws Exception {
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(unb64(b64)));
    }

    /** Symmetric key shared between me and a peer. */
    static byte[] shared(PrivateKey mine, String theirPub) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance("ECDH");
        ka.init(mine);
        ka.doPhase(pub(theirPub), true);
        MessageDigest d = MessageDigest.getInstance("SHA-256");
        d.update("mathhelp-v1".getBytes("UTF-8"));
        return d.digest(ka.generateSecret());
    }

    static byte[] random(int n) { byte[] b = new byte[n]; rng.nextBytes(b); return b; }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    static String sha256hex(String s) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"))); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    /** iv(12) || ciphertext+tag */
    static byte[] seal(byte[] key, byte[] pt) throws Exception {
        byte[] iv = random(12);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ct = c.doFinal(pt);
        byte[] out = new byte[12 + ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(ct, 0, out, 12, ct.length);
        return out;
    }

    static byte[] open(byte[] key, byte[] blob) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, blob, 0, 12));
        return c.doFinal(blob, 12, blob.length - 12);
    }

    private Crypto() {}
}
