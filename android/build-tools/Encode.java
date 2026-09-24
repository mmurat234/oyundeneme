import com.reandroid.apk.ApkModule;
import com.reandroid.apk.ApkModuleXmlEncoder;
import java.io.File;

/** Düz XML kaynak dizinini (ARSCLib düzeni) imzasız bir APK'ya çevirir. */
public class Encode {
    public static void main(String[] args) throws Exception {
        ApkModuleXmlEncoder encoder = new ApkModuleXmlEncoder();
        encoder.scanDirectory(new File(args[0]));
        ApkModule module = encoder.getApkModule();
        module.writeApk(new File(args[1]));
    }
}
