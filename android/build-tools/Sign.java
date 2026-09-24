import com.android.apksig.ApkSigner;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** APK'yı v2 şemasıyla imzalar (minSdk 24 için yeterli). Argümanlar: girdi çıktı keystore şifre takmaAd */
public class Sign {
    public static void main(String[] args) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(args[2])) {
            ks.load(in, args[3].toCharArray());
        }
        PrivateKey key = (PrivateKey) ks.getKey(args[4], args[3].toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate(args[4]);
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(
                "CERT", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(args[0]))
                .setOutputApk(new File(args[1]))
                .setV1SigningEnabled(false)
                .setV2SigningEnabled(true)
                .build()
                .sign();
    }
}
