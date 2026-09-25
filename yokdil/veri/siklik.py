"""Çıkmış YÖKDİL Sosyal kitapçıklarında (PDF veya TXT) AWL kelimelerinin sıklığını sayar.

Kullanım:
    python3 siklik.py kitapcik1.pdf kitapcik2.pdf ...

Çıktı:
    siklik.csv   -> kelime, alt liste, toplam geçiş, geçtiği kitapçık sayısı
    kelimeler.json içine her kelime için "yk" (toplam geçiş) alanı eklenir;
    uygulama yeni kelimeleri bu sıklığa göre sunar.

Sıralama, başlık kelimenin kendisi ve düzenli çekimleri (-s, -es, -ed, -d,
-ing, -ies/-ied) üzerinden yapılır. Kelime ailesindeki türevler (ör. analysis,
analytical) ayrı bir sütunda sayılır; aile listeleri öğretim amaçlı yazıldığı
için AWL aile tanımıyla birebir örtüşmeyebilir ve sıralamaya katılmaz.
"""
import csv, json, re, sys, pathlib
from collections import Counter

KOK = pathlib.Path(__file__).resolve().parent


def metin_oku(yol):
    yol = pathlib.Path(yol)
    if yol.suffix.lower() == ".pdf":
        from pypdf import PdfReader
        return "\n".join((s.extract_text() or "") for s in PdfReader(str(yol)).pages)
    return yol.read_text(encoding="utf-8", errors="ignore")


def cekimler(w):
    w = w.lower()
    b = {w, w + "s", w + "es", w + "ed", w + "d", w + "ing"}
    if w.endswith("e"):
        b |= {w[:-1] + "ing", w + "d"}
    if w.endswith("y"):
        b |= {w[:-1] + "ies", w[:-1] + "ied"}
    if w.endswith("ise"):          # İngiliz/Amerikan yazımı
        b |= {x.replace("is", "iz") for x in list(b)}
    return b


def aile_bicimleri(k):
    b = set()
    for f in k["fam"]:
        m = re.match(r"^(?:us:\s*)?([a-z-]+)(?:\s|\(|$)", f.strip().lower())
        if m:
            b |= cekimler(m.group(1))
    return b - cekimler(k["w"])


def main(dosyalar):
    veri = json.loads((KOK / "kelimeler.json").read_text(encoding="utf-8"))
    formdan, aileden = {}, {}
    for k in veri:
        for f in cekimler(k["w"]):
            formdan.setdefault(f, k["w"])
        for f in aile_bicimleri(k):
            aileden.setdefault(f, k["w"])
    toplam, belge, aile = Counter(), Counter(), Counter()
    for d in dosyalar:
        sozcukler = re.findall(r"[a-z]+(?:-[a-z]+)?", metin_oku(d).lower())
        burada = Counter(formdan[s] for s in sozcukler if s in formdan)
        aile.update(aileden[s] for s in sozcukler if s in aileden and s not in formdan)
        toplam.update(burada)
        belge.update(burada.keys())
        print(f"{d}: {len(sozcukler)} sözcük, {sum(burada.values())} AWL eşleşmesi")
    with open(KOK / "siklik.csv", "w", newline="", encoding="utf-8") as f:
        y = csv.writer(f)
        y.writerow(["kelime", "alt_liste", "toplam_gecis", "kitapcik_sayisi", "aile_turevleri_gecis"])
        for k in sorted(veri, key=lambda k: -toplam[k["w"]]):
            y.writerow([k["w"], k["sub"], toplam[k["w"]], belge[k["w"]], aile[k["w"]]])
    for k in veri:
        k["yk"] = toplam[k["w"]]
    (KOK / "kelimeler_siklik.json").write_text(json.dumps({k["w"]: k["yk"] for k in veri}), encoding="utf-8")
    print("yazıldı: siklik.csv, kelimeler_siklik.json")
    print("en sık 15:", ", ".join(f'{k["w"]} ({k["yk"]})' for k in sorted(veri, key=lambda k: -k["yk"])[:15]))


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    main(sys.argv[1:])
