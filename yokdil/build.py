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
sablon = (kok / "app.html").read_text(encoding="utf-8")
assert "/*__KELIMELER__*/[]" in sablon
cikti = sablon.replace("/*__KELIMELER__*/[]", json.dumps(veri, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/"))
hedef = kok.parent / "kelime-atolyesi.html"
hedef.write_text(cikti, encoding="utf-8")
print(f"{hedef.name}: {len(veri)} kelime, {len(cikti) // 1024} KB")
