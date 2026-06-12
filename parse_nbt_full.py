import gzip
import struct
import json

# Read and decompress NBT
data = open('src/main/java/com/mcplugin/core1/reactorcore1.nbt', 'rb').read()
d = gzip.decompress(data)

# Simple NBT parser
class NBTParser:
    TAG_END = 0
    TAG_BYTE = 1
    TAG_SHORT = 2
    TAG_INT = 3
    TAG_LONG = 4
    TAG_FLOAT = 5
    TAG_DOUBLE = 6
    TAG_BYTE_ARRAY = 7
    TAG_STRING = 8
    TAG_LIST = 9
    TAG_COMPOUND = 10
    TAG_INT_ARRAY = 11
    TAG_LONG_ARRAY = 12

    def __init__(self, data):
        self.data = data
        self.pos = 0

    def read_byte(self):
        val = self.data[self.pos]
        self.pos += 1
        return val

    def read_short(self):
        val = struct.unpack_from('>h', self.data, self.pos)[0]
        self.pos += 2
        return val

    def read_int(self):
        val = struct.unpack_from('>i', self.data, self.pos)[0]
        self.pos += 4
        return val

    def read_long(self):
        val = struct.unpack_from('>q', self.data, self.pos)[0]
        self.pos += 8
        return val

    def read_float(self):
        val = struct.unpack_from('>f', self.data, self.pos)[0]
        self.pos += 4
        return val

    def read_double(self):
        val = struct.unpack_from('>d', self.data, self.pos)[0]
        self.pos += 8
        return val

    def read_string(self):
        length = self.read_short()
        val = self.data[self.pos:self.pos+length].decode('utf-8')
        self.pos += length
        return val

    def read_byte_array(self):
        length = self.read_int()
        val = list(self.data[self.pos:self.pos+length])
        self.pos += length
        return val

    def read_int_array(self):
        length = self.read_int()
        val = []
        for i in range(length):
            val.append(self.read_int())
        return val

    def read_long_array(self):
        length = self.read_int()
        val = []
        for i in range(length):
            val.append(self.read_long())
        return val

    def read_tag(self, tag_type):
        if tag_type == self.TAG_END:
            return None
        elif tag_type == self.TAG_BYTE:
            return self.read_byte()
        elif tag_type == self.TAG_SHORT:
            return self.read_short()
        elif tag_type == self.TAG_INT:
            return self.read_int()
        elif tag_type == self.TAG_LONG:
            return self.read_long()
        elif tag_type == self.TAG_FLOAT:
            return self.read_float()
        elif tag_type == self.TAG_DOUBLE:
            return self.read_double()
        elif tag_type == self.TAG_BYTE_ARRAY:
            return self.read_byte_array()
        elif tag_type == self.TAG_STRING:
            return self.read_string()
        elif tag_type == self.TAG_LIST:
            list_type = self.read_byte()
            list_length = self.read_int()
            items = []
            for i in range(list_length):
                items.append(self.read_tag(list_type))
            return items
        elif tag_type == self.TAG_COMPOUND:
            result = {}
            while True:
                tag_type_inner = self.read_byte()
                if tag_type_inner == self.TAG_END:
                    break
                name = self.read_string()
                value = self.read_tag(tag_type_inner)
                result[name] = value
            return result
        elif tag_type == self.TAG_INT_ARRAY:
            return self.read_int_array()
        elif tag_type == self.TAG_LONG_ARRAY:
            return self.read_long_array()
        else:
            raise ValueError(f"Unknown tag type: {tag_type}")

    def read_root(self):
        tag_type = self.read_byte()
        name = self.read_string()
        return self.read_tag(tag_type)


parser = NBTParser(d)
root = parser.read_root()

# Extract structure data
size = root.get('size', [0, 0, 0])
palette = root.get('Palette', [])
blocks = root.get('blocks', [])

print(f"Structure size: {size}")
print(f"Palette entries: {len(palette)}")
print(f"Block count: {len(blocks)}")
print()

# Print palette
print("=== PALETTE ===")
for i, p in enumerate(palette):
    name = p.get('Name', '?')
    props = p.get('Properties', {})
    props_str = ', '.join(f'{k}={v}' for k, v in sorted(props.items())) if props else ''
    print(f"  [{i}] {name}  ({props_str})")

print()
print("=== BLOCKS BY POSITION ===")
print("Format: (x, y, z) -> block_name [state_index]")
print()

# Group blocks by Y level for easy visualization
from collections import defaultdict
by_y = defaultdict(list)
for block in blocks:
    pos = block.get('pos', [0, 0, 0])
    state = block.get('state', 0)
    # Adjust: pos[1] is Y, pos[0] is X, pos[2] is Z
    # The structure is relative to item frame at the top
    by_y[pos[1]].append((pos, state))

# Sort by Y descending
for y in sorted(by_y.keys(), reverse=True):
    print(f"\n--- Y={y} (relative to item frame: offset=0) ---")
    y_blocks = by_y[y]
    for pos, state in sorted(y_blocks, key=lambda x: (x[0][2], x[0][0])):
        p = palette[state]
        name = p.get('Name', '?')
        props = p.get('Properties', {})
        props_str = ', '.join(f'{k}={v}' for k, v in sorted(props.items())) if props else ''
        print(f"  ({pos[0]}, {pos[1]}, {pos[2]}) -> {name}  [{state}]  {{{props_str}}}")
