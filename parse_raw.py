import gzip, struct

f = gzip.open('src/main/java/com/mcplugin/core1/reactorcore1.nbt', 'rb')
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
    if t == 2: return struct.unpack_from('>h', d, idx[0])[0]; idx[0] += 2
    if t == 3: return r4()
    if t == 4: v = struct.unpack_from('>q', d, idx[0])[0]; idx[0] += 8; return v
    if t == 7: l = r4(); v = d[idx[0]:idx[0]+l]; idx[0] += l; return list(v)
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
    # Skip unknown tags by type
    print(f"Unknown tag {t} at {idx[0]}")
    return None

# Parse root
rt = r1()
rn = r_str()
root = r_tag(rt)

print('Keys:', list(root.keys()))

size = root.get('size')
print('Size:', size)

palette = root.get('palette')
print('Palette type:', type(palette).__name__ if palette else 'None')
if palette:
    print('Palette len:', len(palette))
    for i, entry in enumerate(palette):
        if isinstance(entry, dict):
            name = entry.get('Name', '?')
            props = entry.get('Properties', {})
            ps = ', '.join(f'{k}={v}' for k,v in sorted(props.items())) if props else ''
            print(f'  [{i}] {name} {{{ps}}}')
        else:
            print(f'  [{i}] {entry}')

blocks = root.get('blocks')
print('Blocks type:', type(blocks).__name__ if blocks else 'None')
if blocks:
    print('Blocks len:', len(blocks))
    
    # Group by Y
    by_y = {}
    for block in blocks:
        if isinstance(block, dict) and 'pos' in block and 'state' in block:
            x,y,z = block['pos']
            state = block['state']
            if y not in by_y: by_y[y] = []
            by_y[y].append((x,z,state))
    
    print(f'Y levels: {sorted(by_y.keys())}')
    for y in sorted(by_y.keys(), reverse=True):
        print(f'\nY={y}')
        grid = {}
        for x,z,state in by_y[y]:
            grid[(x,z)] = state
        for z in range(size[2]):
            row = ''
            for x in range(size[0]):
                s = grid.get((x,z))
                if s is None:
                    row += '  ??  '
                else:
                    name = palette[s]['Name'] if s < len(palette) and isinstance(palette[s], dict) else f'?{s}?'
                    short = name.split(':')[1][:5] if ':' in name else str(name)[:5]
                    row += f' {short:5s}'
            print(f'  {row}')
