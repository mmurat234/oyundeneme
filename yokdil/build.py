"""yokdil/app.html şablonuna kelime verisini gömer ve kelime-atolyesi.html üretir."""
import json, pathlib, subprocess, sys
kok = pathlib.Path(__file__).resolve().parent
subprocess.run([sys.executable, "derle.py"], cwd=kok / "veri", check=True)
veri = json.loads((kok / "veri" / "kelimeler.json").read_text(encoding="utf-8"))
sik = kok / "veri" / "kelimeler_siklik.json"   # siklik.py çalıştırıldıysa
if sik.exists():
    yk = json.loads(sik.read_text(encoding="utf-8"))
    for k in veri:
        k["yk"] = yk.get(k["w"], 0)
    print("YÖKDİL sıklık verisi eklendi")
# Köken içeriği (veri/koken/koken_*.jsonl)
import re, unicodedata
ALAN = ["kok", "kok_anlam", "dil", "parca", "yol", "imge", "hikaye", "tr_bag", "tuzak", "emin"]
sozluk = {k["w"]: k for k in veri}
koken_say, hatalar = 0, []
for f in sorted((kok / "veri" / "koken").glob("koken_*.jsonl")):
    for i, satir in enumerate(f.read_text(encoding="utf-8").splitlines(), 1):
        if not satir.strip():
            continue
        try:
            d = json.loads(satir)
        except Exception as e:
            hatalar.append(f"{f.name}:{i} JSON: {e}"); continue
        w = d.get("w")
        if w not in sozluk:
            hatalar.append(f"{f.name}:{i} bilinmeyen kelime {w}"); continue
        eksik = [a for a in ALAN if a not in d]
        if eksik:
            hatalar.append(f"{w}: eksik alan {eksik}"); continue
        if re.search(r"\b(1[0-9]{3}|[0-9]{1,2}\.? ?yüzyıl)", d["hikaye"] + d["imge"], re.I):
            hatalar.append(f"{w}: hikâyede tarih var")
        if not (isinstance(d["parca"], list) and d["parca"] and all(isinstance(p, list) and len(p) == 2 for p in d["parca"])):
            hatalar.append(f"{w}: parca biçimi"); continue
        d["kok"] = unicodedata.normalize("NFC", d["kok"].strip().lower())
        sozluk[w]["ko"] = {a: d[a] for a in ALAN}
        koken_say += 1
for h in hatalar:
    print("KÖKEN HATA", h)
print(f"köken: {koken_say}/{len(veri)} kelime")
sablon = (kok / "app.html").read_text(encoding="utf-8")
assert "/*__KELIMELER__*/[]" in sablon
cikti = sablon.replace("/*__KELIMELER__*/[]", json.dumps(veri, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/"))
hedef = kok.parent / "kelime-atolyesi.html"
hedef.write_text(cikti, encoding="utf-8")
print(f"{hedef.name}: {len(veri)} kelime, {len(cikti) // 1024} KB")
