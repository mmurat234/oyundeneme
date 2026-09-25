import com.android.apksig.ApkSigner;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** apksig kütüphanesiyle APK'yı v2 şemasıyla imzalar (hizalamayı da yapar). */
public class Signer {
    public static void main(String[] a) throws Exception {
        File in = new File(a[0]), out = new File(a[1]), ks = new File(a[2]);
        char[] pw = a[3].toCharArray();
        String alias = a[4];
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream f = new FileInputStream(ks)) { store.load(f, pw); }
        PrivateKey key = (PrivateKey) store.getKey(alias, pw);
        X509Certificate cert = (X509Certificate) store.getCertificate(alias);
        ApkSigner.SignerConfig sc = new ApkSigner.SignerConfig.Builder(
                "pamuk", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(sc))
                .setInputApk(in)
                .setOutputApk(out)
                .setMinSdkVersion(24)
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();
        System.out.println("İmzalandı: " + out);
    }
}
