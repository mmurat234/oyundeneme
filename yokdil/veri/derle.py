"""kelimeler_*.txt dosyalarını denetler ve kelimeler.json üretir."""
import json, re, glob, sys
from awl import AWL
from wordfreq import zipf_frequency as z

hedef = [w for n in range(1, 7) for w in AWL[n].split() if z(w, "en") < 5.0]
alt = {w: n for n in AWL for w in AWL[n].split()}
TUR = {"n", "v", "adj", "adv", "conj"}
kayit, hatalar = [], []
for dosya in sorted(glob.glob("kelimeler_*.txt")):
    for no, satir in enumerate(open(dosya, encoding="utf-8"), 1):
        satir = satir.strip()
        if not satir or satir.startswith("#"):
            continue
        p = satir.split("|")
        if len(p) != 8:
            hatalar.append(f"{dosya}:{no} alan sayısı {len(p)}"); continue
        w, pos, tr, df, ex, syn, fam, col = [x.strip() for x in p]
        if pos not in TUR: hatalar.append(f"{w}: tür {pos}")
        m = re.findall(r"\[(.+?)\]", ex)
        if len(m) != 1: hatalar.append(f"{w}: örnekte köşeli parantez {len(m)}")
        elif not m[0].lower().startswith(w[:4].lower()) and w[:3].lower() not in m[0].lower():
            hatalar.append(f"{w}: hedef biçim '{m[0]}' kelimeye benzemiyor")
        for f in ([] if fam == "—" else re.split(r";(?![^()]*\))", fam)):
            if f.count("(") != f.count(")"): hatalar.append(f"{w}: aile parantezi bozuk: {f}")
        kayit.append({"w": w, "pos": pos, "sub": alt.get(w), "tr": tr, "def": df, "ex": ex,
                      "syn": [s.strip() for s in syn.split(";") if s.strip() and s.strip() != "—"],
                      "fam": [] if fam == "—" else [s.strip() for s in re.split(r";(?![^()]*\))", fam)],
                      "col": [s.strip() for s in col.split(";") if s.strip()]})
adlar = [k["w"] for k in kayit]
eksik = [w for w in hedef if w not in adlar]
fazla = [w for w in adlar if w not in hedef]
tekrar = {w for w in adlar if adlar.count(w) > 1}
print("kayıt:", len(kayit), "| hedef:", len(hedef))
print("eksik:", eksik, "| fazla:", fazla, "| tekrar:", tekrar)
for h in hatalar: print("HATA", h)
if eksik or fazla or tekrar or hatalar: sys.exit(1)
json.dump(kayit, open("kelimeler.json", "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
print("yazıldı: kelimeler.json")
