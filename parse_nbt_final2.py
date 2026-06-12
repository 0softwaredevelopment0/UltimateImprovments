import sys
import gzip
sys.path.insert(0, '.')
from nbt_parser import nbt

data = open('src/main/java/com/mcplugin/core1/reactorcore1.nbt', 'rb').read()
d = gzip.decompress(data)

nbt_file = nbt.NBTFile(buffer=d)
print('Root tags:', list(nbt_file.keys()))

size = nbt_file['size']
print('Size:', size)

palette = nbt_file['Palette']
print('Palette entries:', len(palette))

blocks = nbt_file['blocks']
print('Block count:', len(blocks))

print()
print('=== PALETTE ===')
for i, entry in enumerate(palette):
    name = entry.get('Name', '?')
    props = entry.get('Properties', {})
    props_str = ', '.join(f'{k}={v}' for k,v in sorted(props.items())) if props else ''
    has_nbt = 'nbt' in entry
    print(f'  [{i}] {name} {{{props_str}}}' + (' [has NBT]' if has_nbt else ''))

print()
print('=== BLOCKS GROUPED BY Y ===')
print()

# Group blocks by Y
by_y = {}
for block in blocks:
    x, y, z = block['pos']
    state = block['state']
    if y not in by_y:
        by_y[y] = []
    by_y[y].append((x, z, state))

# Print each Y level as a grid
for y in sorted(by_y.keys(), reverse=True):
    print(f'\n{"="*70}')
    print(f'Y={y}  (layer {y} of structure, total layers: {len(by_y)})')
    print(f'{"="*70}')
    
    grid = {}
    for x, z, state in by_y[y]:
        grid[(x, z)] = state
    
    # Print header
    print('     ', end='')
    for x in range(size[0]):
        print(f'{x:^8}', end='')
    print()
    print('     ', end='')
    for x in range(size[0]):
        print(f'{"X="+str(x):^8}', end='')
    print()
    
    for z in range(size[2]):
        print(f'Z={z}  ', end='')
        for x in range(size[0]):
            state = grid.get((x, z))
            if state is None:
                print('  ???   ', end=' ')
            else:
                name = palette[state]['Name']
                short = name.split(':')[1] if ':' in name else name
                print(f'{short:<8s}', end=' ')
        print()
