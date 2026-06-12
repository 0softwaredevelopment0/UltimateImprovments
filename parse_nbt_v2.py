import gzip
import struct
import json

# Read and decompress NBT
data = open('src/main/java/com/mcplugin/core1/reactorcore1.nbt', 'rb').read()
d = gzip.decompress(data)

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

    def read_string(self):
        length = self.read_short()
        val = self.data[self.pos:self.pos+length].decode('utf-8')
        self.pos += length
        return val

    def read_int_array(self):
        length = self.read_int()
        val = []
        for i in range(length):
            val.append(self.read_int())
        return val

    def read_tag(self, tag_type):
        if tag_type == self.TAG_END:
            return ('END', None)
        elif tag_type == self.TAG_BYTE:
            return ('BYTE', self.read_byte())
        elif tag_type == self.TAG_SHORT:
            return ('SHORT', self.read_short())
        elif tag_type == self.TAG_INT:
            return ('INT', self.read_int())
        elif tag_type == self.TAG_LONG:
            return ('LONG', self.read_long())
        elif tag_type == self.TAG_STRING:
            return ('STRING', self.read_string())
        elif tag_type == self.TAG_LIST:
            list_type = self.read_byte()
            list_length = self.read_int()
            items = []
            for i in range(list_length):
                _, val = self.read_tag(list_type)
                items.append(val)
            return ('LIST', (list_type, items))
        elif tag_type == self.TAG_COMPOUND:
            result = {}
            while True:
                tag_type_inner = self.read_byte()
                if tag_type_inner == self.TAG_END:
                    break
                name = self.read_string()
                t, value = self.read_tag(tag_type_inner)
                result[name] = (t, value)
            return ('COMPOUND', result)
        elif tag_type == self.TAG_INT_ARRAY:
            return ('INT_ARRAY', self.read_int_array())
        elif tag_type == self.TAG_BYTE_ARRAY:
            length = self.read_int()
            val = list(self.data[self.pos:self.pos+length])
            self.pos += length
            return ('BYTE_ARRAY', val)
        elif tag_type == self.TAG_LONG_ARRAY:
            length = self.read_int()
            val = []
            for i in range(length):
                val.append(self.read_long())
            return ('LONG_ARRAY', val)
        else:
            raise ValueError(f"Unknown tag type: {tag_type}")

    def read_root(self):
        tag_type = self.read_byte()
        name = self.read_string()
        return self.read_tag(tag_type)

parser = NBTParser(d)
root_type, root = parser.read_root()
print(f"Root type: {root_type}")
print(f"Root keys: {list(root.keys())}")

# Print all keys recursively (limited depth)
def print_keys(data, indent=0):
    prefix = '  ' * indent
    for key, (t, val) in sorted(data.items()):
        if t == 'COMPOUND':
            print(f"{prefix}{key}: COMPOUND {{")
            print_keys(val, indent+1)
            print(f"{prefix}}}")
        elif t == 'LIST':
            list_type = val[0]
            items = val[1]
            if items and len(items) > 0:
                first = items[0]
                if isinstance(first, dict):
                    print(f"{prefix}{key}: LIST of COMPOUND ({len(items)} items) {{")
                    if len(items) <= 3:
                        for item in items:
                            print(f"{prefix}  {{")
                            print_keys(item, indent+2)
                            print(f"{prefix}  }}")
                    else:
                        # Show first 2 and last 1
                        for item in items[:2]:
                            print(f"{prefix}  {{")
                            print_keys(item, indent+2)
                            print(f"{prefix}  }}")
                        print(f"{prefix}  ... ({len(items)-3} more)")
                        print_keys(items[-1], indent+2)
                        print(f"{prefix}  }}")
                    print(f"{prefix}}}")
                else:
                    print(f"{prefix}{key}: LIST ({len(items)} items)")
                    if len(items) <= 5:
                        for item in items:
                            print(f"{prefix}  - {item}")
                    else:
                        print(f"{prefix}  - {items[:5]}...")
            else:
                print(f"{prefix}{key}: LIST (empty)")
        elif t == 'STRING':
            print(f"{prefix}{key}: \"{val}\"")
        elif t == 'INT_ARRAY':
            print(f"{prefix}{key}: {val}")
        elif t in ('INT', 'BYTE', 'SHORT', 'LONG'):
            print(f"{prefix}{key}: {val}")

print()
print_keys(root)

# Now try to extract blocks manually
if 'blocks' in root:
    _, blocks_list = root['blocks']
    list_type, blocks = blocks_list
    print(f"\nFound {len(blocks)} blocks")
    if blocks:
        sample = blocks[0]
        print(f"Sample block keys: {list(sample.keys())}")
        
        # Try 'Palette' or 'palette' if it exists
        pal = root.get('Palette') or root.get('palette')
        if pal:
            print(f"Palette found!")
        else:
            print("No palette found in root")
            
        # Check position data
        for blk in blocks[:5]:
            for k, (t, v) in blk.items():
                print(f"  {k}: {t} = {v}")

if 'size' in root:
    _, size = root['size']
    print(f"\nSize: {size}")
