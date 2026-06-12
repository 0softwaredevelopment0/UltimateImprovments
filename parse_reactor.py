import sys, gzip
sys.path.insert(0, '.')
from nbt_parser import NBTFile

f = gzip.open('src/main/java/com/mcplugin/core1/reactorcore1.nbt', 'rb')
nbt = NBTFile(buffer=f)
f.close()

print('Keys:', list(nbt.keys()))

size = nbt['size'].value
print('Size:', size)

palette_list = nbt['palette'].value
print('Palette:', len(palette_list))

blocks_list = nbt['blocks'].value
print('Blocks:', len(blocks_list))

print('\n=== PALETTE ===')
for i, entry in enumerate(palette_list):
    name = entry['Name'].value
    props = entry.get('Properties')
    s = name
    if props:
        plist = [f'{k}={v.value}' for k,v in props.value.items()]
        s += '{' + ','.join(plist) + '}'
    print(f'  [{i}] {s}')

print('\n=== BLOCKS BY Y ===')
import collections
by_y = collections.defaultdict(list)
for b in blocks_list:
    x,y,z = b['pos'].value
    state = b['state'].value
    by_y[y].append((x,z,state))

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
                row += '  ???  '
            else:
                name = palette_list[s]['Name'].value
                short = name.split(':')[1][:5]
                row += f' {short:5s} '
        print(f'  {row}')
