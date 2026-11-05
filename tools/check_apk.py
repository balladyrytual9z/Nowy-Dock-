"""Weryfikacja APK bez Android SDK: zawartosc + binarny AndroidManifest.xml.

Sprawdza twarde wymagania projektu Nowy-Dock: Android 9 / API 28
(minSdk <= 28, targetSdk == 28) i brak niechcianych architektur natywnych.
"""
import struct
import sys
import zipfile

STRING_POOL = 0x0001
START_ELEMENT = 0x0102

TYPE_NAMES = {
    0x00: "NULL", 0x01: "REFERENCE", 0x02: "ATTRIBUTE", 0x03: "STRING",
    0x04: "FLOAT", 0x05: "DIMENSION", 0x06: "FRACTION", 0x07: "DYNAMIC_REF",
    0x08: "DYNAMIC_ATTRIBUTE", 0x10: "INT_DEC", 0x11: "INT_HEX",
    0x12: "INT_BOOLEAN", 0x1C: "INT_COLOR_ARGB8",
}


def _read_len8(data, p):
    n = data[p]
    p += 1
    if n & 0x80:
        n = ((n & 0x7F) << 8) | data[p]
        p += 1
    return n, p


def _read_len16(data, p):
    n = struct.unpack_from("<H", data, p)[0]
    p += 2
    if n & 0x8000:
        n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
        p += 2
    return n, p


def parse_axml(data):
    """Zwraca (lista elementow, bledy). Element = (nazwa, [(atrybut, typ, wartosc)])."""
    total = struct.unpack_from("<I", data, 4)[0]
    _, hdr, _ = struct.unpack_from("<HHI", data, 0)
    strings, elements, errors = [], [], []
    pos = hdr
    while pos + 8 <= min(total, len(data)):
        ctype, hsize, csize = struct.unpack_from("<HHI", data, pos)
        if csize <= 0:
            break
        if ctype == STRING_POOL:
            cnt, _sc, flags, sstart, _st = struct.unpack_from("<IIIII", data, pos + 8)
            utf8 = bool(flags & (1 << 8))
            offs = struct.unpack_from("<%dI" % cnt, data, pos + hsize)
            base = pos + sstart
            for o in offs:
                p = base + o
                try:
                    if utf8:
                        _n, p = _read_len8(data, p)
                        m, p = _read_len8(data, p)
                        strings.append(data[p:p + m].decode("utf-8", "replace"))
                    else:
                        n, p = _read_len16(data, p)
                        strings.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
                except Exception as exc:  # pragma: no cover
                    errors.append("string pool @%d: %s" % (o, exc))
        elif ctype == START_ELEMENT:
            _ns_i, name_i = struct.unpack_from("<ii", data, pos + 16)
            a_start, a_size, a_count = struct.unpack_from("<HHH", data, pos + 24)
            attrs = []
            abase = pos + 16 + a_start
            for i in range(a_count):
                ao = abase + i * a_size
                _ans, aname, araw = struct.unpack_from("<iii", data, ao)
                _sz, _r0, atype, adata = struct.unpack_from("<HBBI", data, ao + 12)
                nm = strings[aname] if 0 <= aname < len(strings) else "?%d" % aname
                if atype == 0x03 and 0 <= adata < len(strings):
                    val = strings[adata]
                elif atype == 0x12:
                    val = "true" if adata else "false"
                else:
                    val = str(adata)
                attrs.append((nm, TYPE_NAMES.get(atype, hex(atype)), val, araw))
            elements.append((strings[name_i] if 0 <= name_i < len(strings) else "?%d" % name_i, attrs))
        pos += csize
    return elements, errors


def main(apk_path):
    zf = zipfile.ZipFile(apk_path)
    names = zf.namelist()
    print("=== ZAWARTOSC APK ===")
    print("wpisow w archiwum: %d" % len(names))
    so = [n for n in names if n.endswith(".so")]
    print("biblioteki natywne (.so): %d" % len(so))
    for n in so:
        print("   %s" % n)
    abis = sorted({n.split("/")[1] for n in names if n.startswith("lib/") and n.count("/") >= 2})
    print("architektury w APK (lib/<abi>/): %s" % (abis if abis else "BRAK (zadnych .so)"))

    print()
    print("=== MANIFEST (binarny AndroidManifest.xml) ===")
    raw = zf.read("AndroidManifest.xml")
    elements, errors = parse_axml(raw)
    print("sparsowanych elementow: %d" % len(elements))
    if errors:
        print("ostrzezenia parsera: %s" % errors[:3])

    def show(elem_name, wanted):
        for nm, attrs in elements:
            if nm == elem_name:
                d = {a[0]: a[2] for a in attrs}
                hit = {k: d.get(k) for k in wanted if k in d}
                if hit:
                    print("  <%s> %s" % (elem_name, hit))

    show("manifest", ["package", "versionCode", "versionName", "compileSdkVersion",
                      "compileSdkVersionCodename", "platformBuildVersionCode"])
    show("uses-sdk", ["minSdkVersion", "targetSdkVersion", "maxSdkVersion"])
    show("application", ["debuggable", "extractNativeLibs", "allowBackup"])

    perms = sorted({a[2] for nm, attrs in elements if nm == "uses-permission" for a in attrs if a[0] == "name"})
    print("  uprawnienia (%d):" % len(perms))
    for p in perms:
        print("     %s" % p)
    return elements


if __name__ == "__main__":
    main(sys.argv[1])
