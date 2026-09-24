"""APK içindeki sıkıştırılmamış dosyaları 4 bayta hizalar (zipalign eşdeğeri).
resources.arsc sıkıştırılmadan saklanır; Android 11+ bunu zorunlu tutar."""
import sys
import zipfile

STORED = {"resources.arsc"}

src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zin, open(dst, "wb") as f:
    with zipfile.ZipFile(f, "w") as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            yeni = zipfile.ZipInfo(info.filename, date_time=(2020, 1, 1, 0, 0, 0))
            yeni.external_attr = info.external_attr
            if info.filename in STORED or info.filename.endswith(".png"):
                yeni.compress_type = zipfile.ZIP_STORED
                name_len = len(yeni.filename.encode("utf-8"))
                offset = f.tell() + 30 + name_len
                pad = (4 - offset % 4) % 4
                yeni.extra = b"\x00" * pad
            else:
                yeni.compress_type = zipfile.ZIP_DEFLATED
            zout.writestr(yeni, data)

# Doğrula
with zipfile.ZipFile(dst) as z, open(dst, "rb") as f:
    for info in z.infolist():
        if info.compress_type == zipfile.ZIP_STORED:
            f.seek(info.header_offset + 26)
            n = int.from_bytes(f.read(2), "little")
            e = int.from_bytes(f.read(2), "little")
            veri = info.header_offset + 30 + n + e
            assert veri % 4 == 0, info.filename
print("hizalandı:", dst)
