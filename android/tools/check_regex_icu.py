#!/usr/bin/env python3
"""Проверяет, что все регулярные выражения из Kotlin-кода компилируются движком ICU — как на Android.
На JVM (в тестах) движок другой и пропускает то, на чём Android падает при запуске."""
import ctypes, ctypes.util, glob, os, re, sys

lib = ctypes.util.find_library('icui18n')
if not lib:
    print('ICU не найден — пропускаю проверку'); sys.exit(0)
icu = ctypes.CDLL(lib)
suffix = next((s for s in [''] + ['_%d' % v for v in range(50, 100)] if hasattr(icu, 'uregex_open' + s)), None)
if suffix is None:
    print('В ICU нет uregex_open — пропускаю проверку'); sys.exit(0)
uopen = getattr(icu, 'uregex_open' + suffix); uopen.restype = ctypes.c_void_p
uclose = getattr(icu, 'uregex_close' + suffix)

class UParseError(ctypes.Structure):
    _fields_ = [('line', ctypes.c_int32), ('offset', ctypes.c_int32), ('pre', ctypes.c_uint16 * 16), ('post', ctypes.c_uint16 * 16)]

def compile_icu(p, flags):
    b = p.encode('utf-16-le') or b'\0\0'
    buf = (ctypes.c_uint16 * (len(b) // 2)).from_buffer_copy(b)
    pe = UParseError(); st = ctypes.c_int(0)
    r = uopen(buf, len(p.encode('utf-16-le')) // 2, flags, ctypes.byref(pe), ctypes.byref(st))
    if r: uclose(ctypes.c_void_p(r))
    return st.value, pe.offset

ANDROID_U = ''  # на Android префикс U пустой (см. Parse.kt)

def kotlin_string(src, i):
    out = ''
    while True:
        c = src[i]
        if c == '\\':
            d = src[i + 1]; out += {'\\': '\\', '"': '"', 'n': '\n', 't': '\t', '$': '$'}.get(d, '\\' + d); i += 2; continue
        if c == '$' and src[i + 1] == 'U' and not src[i + 2].isalnum(): out += ANDROID_U; i += 2; continue
        if c == '"': return out, i + 1
        out += c; i += 1

root = os.path.join(os.path.dirname(__file__), '..', 'app', 'src', 'main', 'java')
bad = total = 0
for f in sorted(glob.glob(os.path.join(root, '**', '*.kt'), recursive=True)):
    src = open(f, encoding='utf-8').read()
    for m in re.finditer(r'Regex\(\s*"', src):
        p, j = kotlin_string(src, m.end())
        flags = 2 if re.match(r'\s*,\s*RegexOption\.IGNORE_CASE', src[j:]) else 0
        st, off = compile_icu(p, flags); total += 1
        if st > 0:
            bad += 1; print('ОШИБКА ICU %d в %s (позиция %d): %r' % (st, os.path.basename(f), off, p[:100]))
print('Проверено выражений: %d, ошибок: %d' % (total, bad))
sys.exit(1 if bad else 0)
