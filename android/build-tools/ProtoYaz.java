import com.android.aapt.ConfigurationOuterClass.Configuration;
import com.android.aapt.Resources;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlAttribute;
import com.reandroid.arsc.chunk.xml.ResXmlElement;
import com.reandroid.arsc.value.ValueType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * İmzasız APK'dan (ARSCLib çıktısı) Android App Bundle için "base" modülü üretir.
 * Manifest ve kaynak tablosunu aapt2'nin protobuf biçimine çevirir; bundletool
 * build-bundle bu modülden .aab oluşturur.
 *
 * Kullanım: ProtoYaz <imzasiz.apk> <kaynak AndroidManifest.xml> <res dizini> <paket adı> <çıktı base.zip>
 */
public class ProtoYaz {
    static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";
    static final Map<Integer, String> UYGULAMA_ADLARI = new LinkedHashMap<>();

    public static void main(String[] a) throws Exception {
        File apk = new File(a[0]), kaynakManifest = new File(a[1]), resDizini = new File(a[2]);
        String paket = a[3];
        File cikti = new File(a[4]);

        Resources.ResourceTable tablo = tabloKur(resDizini, paket);

        DocumentBuilderFactory fabrika = DocumentBuilderFactory.newInstance();
        fabrika.setNamespaceAware(true);   // android: önekli özniteliklerin ham metnini okumak için
        Document src = fabrika.newDocumentBuilder().parse(kaynakManifest);
        AndroidManifestBlock ikili;
        try (ZipFile z = new ZipFile(apk); InputStream in = z.getInputStream(z.getEntry("AndroidManifest.xml"))) {
            ikili = AndroidManifestBlock.load(in);
        }
        ResXmlElement kok = ikili.getDocumentElement();
        Resources.XmlNode manifest = Resources.XmlNode.newBuilder()
                .setElement(elemanCevir(kok, src.getDocumentElement(), true)).build();

        try (ZipFile z = new ZipFile(apk); ZipOutputStream out = new ZipOutputStream(new FileOutputStream(cikti))) {
            yaz(out, "manifest/AndroidManifest.xml", manifest.toByteArray());
            yaz(out, "resources.pb", tablo.toByteArray());
            for (Iterator<? extends ZipEntry> it = z.stream().iterator(); it.hasNext(); ) {
                ZipEntry e = it.next();
                String ad = e.getName();
                if (ad.equals("AndroidManifest.xml") || ad.equals("resources.arsc") || ad.startsWith("META-INF/")) continue;
                byte[] veri = z.getInputStream(e).readAllBytes();
                if (ad.endsWith(".dex")) yaz(out, "dex/" + ad, veri);
                else yaz(out, ad, veri);   // res/..., assets/...
            }
        }
        System.out.println("base modülü yazıldı: " + cikti);
    }

    static void yaz(ZipOutputStream out, String ad, byte[] veri) throws Exception {
        out.putNextEntry(new ZipEntry(ad));
        out.write(veri);
        out.closeEntry();
    }

    /* ---------- Kaynak tablosu (res/values/public.xml + strings.xml + drawable dizinleri) ---------- */
    static Resources.ResourceTable tabloKur(File res, String paket) throws Exception {
        Map<String, Map<String, Integer>> tipler = new LinkedHashMap<>();   // tip -> ad -> id
        Document pub = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(res, "values/public.xml"));
        NodeList pl = pub.getElementsByTagName("public");
        for (int i = 0; i < pl.getLength(); i++) {
            Element e = (Element) pl.item(i);
            int id = (int) Long.parseLong(e.getAttribute("id").substring(2), 16);
            tipler.computeIfAbsent(e.getAttribute("type"), k -> new LinkedHashMap<>()).put(e.getAttribute("name"), id);
            UYGULAMA_ADLARI.put(id, e.getAttribute("type") + "/" + e.getAttribute("name"));
        }
        Map<String, String> metinler = new LinkedHashMap<>();
        Document str = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(res, "values/strings.xml"));
        NodeList sl = str.getElementsByTagName("string");
        for (int i = 0; i < sl.getLength(); i++) {
            Element e = (Element) sl.item(i);
            metinler.put(e.getAttribute("name"), e.getTextContent());
        }

        Resources.Package.Builder pk = Resources.Package.newBuilder()
                .setPackageId(Resources.PackageId.newBuilder().setId(0x7f))
                .setPackageName(paket);
        for (Map.Entry<String, Map<String, Integer>> t : tipler.entrySet()) {
            String tip = t.getKey();
            int tipId = (t.getValue().values().iterator().next() >> 16) & 0xff;
            Resources.Type.Builder tb = Resources.Type.newBuilder()
                    .setTypeId(Resources.TypeId.newBuilder().setId(tipId)).setName(tip);
            for (Map.Entry<String, Integer> g : t.getValue().entrySet()) {
                Resources.Entry.Builder eb = Resources.Entry.newBuilder()
                        .setEntryId(Resources.EntryId.newBuilder().setId(g.getValue() & 0xffff))
                        .setName(g.getKey())
                        .setVisibility(Resources.Visibility.newBuilder().setLevel(Resources.Visibility.Level.PUBLIC));
                if (tip.equals("string")) {
                    String deger = metinler.get(g.getKey());
                    if (deger == null) throw new IllegalStateException("strings.xml'de yok: " + g.getKey());
                    eb.addConfigValue(Resources.ConfigValue.newBuilder()
                            .setConfig(Configuration.getDefaultInstance())
                            .setValue(Resources.Value.newBuilder().setItem(Resources.Item.newBuilder()
                                    .setStr(Resources.String.newBuilder().setValue(deger)))));
                } else if (tip.equals("drawable")) {
                    int bulunan = 0;
                    for (File d : res.listFiles()) {
                        if (!d.getName().startsWith("drawable")) continue;
                        File f = new File(d, g.getKey() + ".png");
                        if (!f.exists()) continue;
                        eb.addConfigValue(Resources.ConfigValue.newBuilder()
                                .setConfig(Configuration.newBuilder().setDensity(yogunluk(d.getName())))
                                .setValue(Resources.Value.newBuilder().setItem(Resources.Item.newBuilder()
                                        .setFile(Resources.FileReference.newBuilder()
                                                .setPath("res/" + d.getName() + "/" + f.getName())
                                                .setType(Resources.FileReference.Type.PNG)))));
                        bulunan++;
                    }
                    if (bulunan == 0) throw new IllegalStateException("drawable dosyası yok: " + g.getKey());
                } else {
                    throw new IllegalStateException("desteklenmeyen kaynak tipi: " + tip);
                }
                tb.addEntry(eb);
            }
            pk.addType(tb);
        }
        return Resources.ResourceTable.newBuilder().addPackage(pk).build();
    }

    static int yogunluk(String dizin) {
        if (dizin.contains("xxxhdpi")) return 640;
        if (dizin.contains("xxhdpi")) return 480;
        if (dizin.contains("xhdpi")) return 320;
        if (dizin.contains("hdpi")) return 240;
        if (dizin.contains("mdpi")) return 160;
        return 0;
    }

    /* ---------- Manifest: ikili XML -> proto XML ---------- */
    static Resources.XmlElement elemanCevir(ResXmlElement e, Element src, boolean kok) {
        if (!e.getName().equals(src.getTagName()))
            throw new IllegalStateException("eşleşmeyen eleman: " + e.getName() + " / " + src.getTagName());
        Resources.XmlElement.Builder b = Resources.XmlElement.newBuilder().setName(e.getName());
        if (kok) b.addNamespaceDeclaration(Resources.XmlNamespace.newBuilder().setPrefix("android").setUri(ANDROID_NS));
        for (Iterator<ResXmlAttribute> it = e.getAttributes(); it.hasNext(); ) {
            ResXmlAttribute at = it.next();
            String uri = at.getUri() == null ? "" : at.getUri();
            String yerelAd = at.getName(false);
            String hamMetin = uri.isEmpty() ? src.getAttribute(yerelAd) : src.getAttributeNS(uri, yerelAd);
            Resources.XmlAttribute.Builder ab = Resources.XmlAttribute.newBuilder()
                    .setNamespaceUri(uri).setName(yerelAd).setValue(hamMetin);
            if (at.getNameId() != 0) ab.setResourceId(at.getNameId());
            ValueType vt = at.getValueType();
            int veri = at.getData();
            if (vt == ValueType.REFERENCE) {
                String ad = hamMetin.startsWith("@") ? hamMetin.substring(1) : hamMetin;
                ab.setCompiledItem(Resources.Item.newBuilder().setRef(
                        Resources.Reference.newBuilder().setId(veri).setName(ad)));
            } else if (vt == ValueType.DEC) {
                ab.setCompiledItem(Resources.Item.newBuilder().setPrim(Resources.Primitive.newBuilder().setIntDecimalValue(veri)));
            } else if (vt == ValueType.HEX) {
                ab.setCompiledItem(Resources.Item.newBuilder().setPrim(Resources.Primitive.newBuilder().setIntHexadecimalValue(veri)));
            } else if (vt == ValueType.BOOLEAN) {
                ab.setCompiledItem(Resources.Item.newBuilder().setPrim(Resources.Primitive.newBuilder().setBooleanValue(veri != 0)));
            } else if (vt != ValueType.STRING) {
                throw new IllegalStateException("desteklenmeyen öznitelik tipi " + vt + " (" + yerelAd + ")");
            }
            b.addAttribute(ab);
        }
        List<Element> kaynakCocuklar = new ArrayList<>();
        NodeList nl = src.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) if (nl.item(i).getNodeType() == Node.ELEMENT_NODE) kaynakCocuklar.add((Element) nl.item(i));
        int i = 0;
        for (Iterator<?> it = e.getElements(); it.hasNext(); ) {
            ResXmlElement c = (ResXmlElement) it.next();
            b.addChild(Resources.XmlNode.newBuilder().setElement(elemanCevir(c, kaynakCocuklar.get(i++), false)));
        }
        if (i != kaynakCocuklar.size()) throw new IllegalStateException("çocuk sayısı tutmuyor: " + e.getName());
        return b.build();
    }
}
