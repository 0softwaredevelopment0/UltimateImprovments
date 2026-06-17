import gzip, struct

data = open('src/main/java/com/mcplugin/core1/reactorcore1.nbt', 'rb').read()
d = gzip.decompress(data)

class P:
    def __init__(self, d):
        self.d = d; self.p = 0
    def b(self): v = self.d[self.p]; self.p+=1; return v
    def s(self): v = struct.unpack_from('>h',self.d,self.p)[0]; self.p+=2; return v
    def i(self): v = struct.unpack_from('>i',self.d,self.p)[0]; self.p+=4; return v
    def l(self): v = struct.unpack_from('>q',self.d,self.p)[0]; self.p+=8; return v
    def st(self): return self.d[self.p:self.p+self.s()].decode('utf-8')
    def r(self, t):
        if t==0: return None
        if t==1: return self.b()
        if t==2: return self.s()
        if t==3: return self.i()
        if t==4: return self.l()
        if t==7: l=self.i(); return list(self.d[self.p:self.p+l]); self.p+=l
        if t==8: return self.st()
        if t==9:
            lt=self.b(); ll=self.i()
            return [self.r(lt) for _ in range(ll)]
        if t==10:
            r = {}
            while True:
                ti = self.b()
                if ti == 0: break
                n = self.st()
                r[n] = self.r(ti)
            return r
        if t==11: return [self.i() for _ in range(self.i())]
        if t==12: return [self.l() for _ in range(self.i())]
        raise ValueError(f'Unknown tag {t}')

p = P(d)
assert p.b() == 10  # TAG_Compound
root_name = p.st()
root = p.r(10)

size = root['size']
palette = root['Palette']
blocks = root['blocks']

print(f"Structure size: {size}")
print(f"DataVersion: {root.get('DataVersion', '?')}")
print(f"Palette entries: {len(palette)}")
print(f"Block count: {len(blocks)}")
print()

# Print palette
print("=== PALETTE ===")
for i, entry in enumerate(palette):
    name = entry.get('Name', '?')
    props = entry.get('Properties', {})
    props_str = ', '.join(f'{k}={v}' for k,v in sorted(props.items())) if props else ''
    has_nbt = 'nbt' in entry
    print(f"  [{i}] {name}  {{{props_str}}}" + (' [has NBT]' if has_nbt else ''))

print()
print("=== BLOCKS GROUPED BY Y (relative to item frame) ===")
print()

# Structure is 7x7x7. Item frame on top center.
# Coordinates are 0..6 for X, Y, Z
# Center of top layer = (3, 6, 3) for item frame (or (3, 6, 3) with the structure origin at item frame?)

# Let's figure out the origin. The size is 7x7x7.
# The structure file was created from a template.
# Let me find the item frame position.

# Group by Y (Y is the middle index)
from collections import defaultdict
by_y = defaultdict(list)
for block in blocks:
    x, y, z = block['pos']
    state = block['state']
    by_y[y].append((x, y, z, state))

# Print each Y level as a grid
for y in sorted(by_y.keys(), reverse=True):
    print(f"\n{'='*50}")
    print(f"Y={y}  (relative Y offset from bottom = {y})")
    print(f"{'='*50}")
    
    # Build grid
    grid = {}
    for x, yy, z, state in by_y[y]:
        grid[(x, z)] = state
    
    # Print header
    print("     ", end="")
    for x in range(7):
        print(f"X={x}    ", end="")
    print()
    
    for z in range(7):
        print(f"Z={z} ", end="")
        for x in range(7):
            state = grid.get((x,z), -1)
            if state == -1:
                print("  ???  ", end=" ")
            else:
                name = palette[state]['Name'].split(':')[1] if ':' in palette[state]['Name'] else palette[state]['Name']
                print(f"{name:6s}", end=" ")
        print()
