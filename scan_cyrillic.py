import os, re, sys

cyr = re.compile(r'[А-Яа-яЁё]')
base = 'src/main/java'
files = []
total_lines = 0
for root, dirs, fs in os.walk(base):
    for f in fs:
        if not f.endswith('.java'):
            continue
        p = os.path.join(root, f)
        with open(p, encoding='utf-8', errors='replace') as fh:
            lines = fh.readlines()
        n = sum(1 for ln in lines if cyr.search(ln))
        if n > 0:
            files.append((n, p))
            total_lines += n

files.sort(reverse=True)
print('files_with_cyrillic:', len(files))
print('cyrillic_lines_total:', total_lines)
print('--- top 30 ---')
for n, p in files[:30]:
    print(f'{n:6d}  {p}')
print('--- all files (for batching) ---')
for n, p in files:
    print(f'{n:6d}  {p}')
