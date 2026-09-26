# Köken ve hikâye içeriği: yazım kuralları

Amaç: Türk bir YÖKDİL Sosyal adayı kelimeyi okuyup onunla ilişki kursun, anlamı kökteki imgeden
yeniden kurabilsin. Doğruluk süslemeden önemlidir. Okuyan bir akademisyendir; şüpheli bilgi istemez.

## Doğruluk
1. Yalnız iyi bilinen, sözlüklerde yerleşik köken bilgisini yaz (Latince/Yunanca/Fransızca/Germen kaynağı, biçimbirimler).
2. TARİH VERME (yüzyıl, yıl, "ilk kez ... geçti" yok). Kişi/olay anekdotu yok, efsane yok.
3. Emin olmadığın aracı dil yolunu yazma. "Latince kökenli" demek yeterli. Fransızca aracılığı iyi biliniyorsa yaz.
4. Köken tartışmalı ya da belirsizse "emin":"orta" yaz ve metinde bunu belirt ("kökeni kesin değildir").
5. Hikâye, kökteki somut imgeden bugünkü akademik anlama giden ANLAM KÖPRÜSÜNÜ anlatır. Tarihsel anlam
   kayması ancak çok iyi bilinen ve belgelenmiş ise (ör. economy < Yun. oikonomia "ev yönetimi",
   symbol < Yun. symbolon "ikiye bölünmüş tanıma nişanı", category < Yun. kategoria) anlatılır.
6. Türkçe bağlantı: Türkçede aynı kökten yerleşmiş bir alıntı kelime varsa (analiz, faktör, fonksiyon,
   enstitü, strüktür, konsept...) belirt. Uydurma ya da nadir alıntı yazma. Yoksa null.
7. Tuzak: Türk öğrencinin sık yaptığı somut bir hata ya da yalancı eşdeğer varsa (actual ≠ aktüel;
   affect/effect; "consist" edilgen kullanılmaz) yaz. Yoksa null. Genel tavsiye yazma.

## Dil
- Kısa, net, doğal Türkçe cümleler. "-mektedir/-maktadır" zinciri yok. Abartı, süs, "büyüleyici" gibi sıfatlar yok.
- Hikâye 3-4 cümle, en fazla ~70 kelime. İmge tek cümle.
- Latince/Yunanca sözcükleri italik işareti olmadan düz yaz.

## Biçim: her satır bir JSON nesnesi (JSON Lines), kelime sırası kelimeler_N.txt ile aynı
{"w": "kelime",
 "kok": "kök sözlük biçimi (fiilse mastar: struere, capere, ducere, videre...; Yunanca Latin harfiyle: oikos)",
 "kok_anlam": "kökün Türkçe anlamı",
 "dil": "Latince | Yunanca | Fransızca | Germen | ...",
 "parca": [["ön ek ya da parça", "Türkçe anlamı"], ...],   // kelimeyi oluşturan biçimbirimler, sırayla
 "yol": "ör. Latince evidens → Fransızca → İngilizce" ya da "Latince kökenli",
 "imge": "kökteki somut resim, tek cümle",
 "hikaye": "3-4 cümle: kökteki imge → anlam köprüsü → bugünkü akademik kullanım",
 "tr_bag": "Türkçedeki aynı kökten kelime(ler) ve bağlantı, tek cümle" | null,
 "tuzak": "tek cümle" | null,
 "emin": "yüksek" | "orta"}

## Örnek
{"w":"evident","kok":"videre","kok_anlam":"görmek","dil":"Latince","parca":[["e- (ex-)","dışarı, açığa"],["vid","görmek"],["-ent","sıfat eki (-en/-an)"]],"yol":"Latince evidens → Fransızca → İngilizce","imge":"Dışarıda, herkesin gözü önünde duran bir şey.","hikaye":"Latince evidens, 'açıkça görülen' demektir. Gözle görülen şeyden akılla açıkça kavranan şeye geçmek kısa bir adımdır. Akademik metinde evident, verinin kendini gösterdiği durumu anlatır: 'It is evident that...'. Aynı kökten evidence 'kanıt' da 'görünür kılınan şey'dir.","tr_bag":"Türkçedeki 'vizyon' ve 'video' da aynı videre kökünden gelir.","tuzak":null,"emin":"yüksek"}
