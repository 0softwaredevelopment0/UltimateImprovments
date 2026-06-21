import sys, gzip, struct, io

# Force UTF-8 output
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')

f = gzip.open('src/main/resources/NBT-Files/lightning_str.nbt', 'rb')
d = f.read()
f.close()

idx = [0]
def r1():
    v = d[idx[0]]
    idx[0] += 1
    return v
def r2():
    v = struct.unpack_from('>H', d, idx[0])[0]
    idx[0] += 2
    return v
def r4():
    v = struct.unpack_from('>I', d, idx[0])[0]
    idx[0] += 4
    return v
def r_str():
    l = r2()
    s = d[idx[0]:idx[0]+l].decode('utf-8')
    idx[0] += l
    return s
def r_tag(t):
    if t == 0: return None
    if t == 1: return r1()
    if t == 2: v = struct.unpack_from('>h', d, idx[0])[0]; idx[0] += 2; return v
    if t == 3: return r4()
    if t == 4: v = struct.unpack_from('>q', d, idx[0])[0]; idx[0] += 8; return v
    if t == 5: v = struct.unpack_from('>f', d, idx[0])[0]; idx[0] += 4; return v
    if t == 6: v = struct.unpack_from('>d', d, idx[0])[0]; idx[0] += 8; return v
    if t == 7: l = r4(); v = list(d[idx[0]:idx[0]+l]); idx[0] += l; return v
    if t == 8: return r_str()
    if t == 9:
        lt = r1()
        ll = r4()
        return [r_tag(lt) for _ in range(ll)]
    if t == 10:
        r = {}
        while True:
            ti = r1()
            if ti == 0: break
            n = r_str()
            r[n] = r_tag(ti)
        return r
    if t == 11: return [r4() for _ in range(r4())]
    if t == 12: 
        l = r4(); vals = []
        for _ in range(l):
            vals.append(struct.unpack_from('>q', d, idx[0])[0])
            idx[0] += 8
        return vals
    print(f"Unknown tag {t} at {idx[0]}")
    return None

rt = r1()
rn = r_str()
root = r_tag(rt)

size = root.get('size')
sx, sy, sz = size[0], size[1], size[2]
palette = root.get('palette')
blocks = root.get('blocks')

# Short names for palette
short_names = []
for entry in palette:
    if isinstance(entry, dict):
        name = entry.get('Name', '?')
        props = entry.get('Properties', {})
        ps = ', '.join(f'{k}={v}' for k,v in sorted(props.items())) if props else ''
        short = name.split(':')[1] if ':' in name else name
        short_names.append((short, name, ps))
    else:
        short_names.append((str(entry), str(entry), ''))

print('=' * 60)
print('  STRUCTURE: lightning_str.nbt')
print('=' * 60)
print(f'  Size:  X={sx}  Y={sy}  Z={sz}')
print(f'  Palette entries: {len(palette)}')
print(f'  Total block records: {len(blocks)}')

# Count non-air blocks
non_air = 0
for block in blocks:
    s = block.get('state', 0)
    name = short_names[s][0] if s < len(short_names) else '?'
    if name not in ('air', 'structure_void'):
        non_air += 1
print(f'  Non-air blocks: {non_air}')
print()

print('-' * 60)
print('  PALETTE')
print('-' * 60)
for i, (short, full, props) in enumerate(short_names):
    pstr = f'  [{i}] {full}'
    if props:
        pstr += f'  ({props})'
    print(pstr)
print()

# Group blocks by Y
by_y = {}
for block in blocks:
    if isinstance(block, dict) and 'pos' in block and 'state' in block:
        x, y, z = block['pos']
        state = block['state']
        if y not in by_y:
            by_y[y] = []
        by_y[y].append((x, z, state))

print('-' * 60)
print('  COORDINATE GRID (by Y level)')
print('  Legend: X=right, Z=down')
print('-' * 60)

for y in sorted(by_y.keys(), reverse=True):
    print(f'\n  +--- Y = {y} ---+')
    
    grid = {}
    for x, z, state in by_y[y]:
        grid[(x, z)] = state
    
    # Header
    header = '  Z\\X '
    for x in range(sx):
        header += f'  {x:^3d}  '
    print(header)
    print('  ' + '-' * (5 * sx + 4))
    
    for z in range(sz):
        row = f'  {z:3d} '
        for x in range(sx):
            s = grid.get((x, z))
            if s is None:
                row += '  ---  '
            else:
                name = short_names[s][0] if s < len(short_names) else f'?{s}?'
                row += f' {name:^6s}'
        print(row)
    print()

# Detailed list 
print('-' * 60)
print('  DETAILED BLOCK LIST')
print('-' * 60)

for y in sorted(by_y.keys(), reverse=True):
    print(f'\n  Y = {y}:')
    y_blocks = sorted(by_y[y], key=lambda b: (b[1], b[0]))
    for x, z, state in y_blocks:
        name = short_names[state][0] if state < len(short_names) else '?'
        props = short_names[state][2] if state < len(short_names) else ''
        line = f'    (X={x:2d}, Y={y:2d}, Z={z:2d})  ->  {name}  [state={state}]'
        if props:
            line += f'  {{{props}}}'
        print(line)

print()
print('-' * 60)
print('  ITEM FRAME PLACEMENT')
print('-' * 60)
print(f'  Structure size: {sx}x{sy}x{sz}')
print(f'  Top-center block (attach frame TO):')
print(f'    X = {sx // 2}  (center of X-axis)')
print(f'    Y = {sy - 1}  (top layer)')
print(f'    Z = {sz // 2}  (center of Z-axis)')
print()
print(f'  The item frame hangs ON the SOUTH face of that block')
print(f'  (oriented NORTH -- i.e. frame faces SOUTH, attached to NORTH face)')
print(f'  Coordinates in structure: frame block = (0, {sy-1}, 0)')
