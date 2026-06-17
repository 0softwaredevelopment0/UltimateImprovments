import gzip, struct

data = open('src/main/java/com/mcplugin/core1/reactorcore1.nbt', 'rb').read()
d = gzip.decompress(data)

# Find all block names and positions by searching for known patterns
# In NBT, strings are prefixed with a short length, then the string

def find_all_strings(d, min_len=3):
    """Simple approach - find all readable strings"""
    strings = []
    i = 0
    while i < len(d):
        # Look for UTF-8 strings preceded by a valid short length
        if i + 2 < len(d):
            length = struct.unpack_from('>H', d, i)[0]
            if 1 <= length <= 100 and i + 2 + length <= len(d):
                chunk = d[i+2:i+2+length]
                try:
                    s = chunk.decode('utf-8')
                    if all(32 <= ord(c) < 127 or ord(c) == 10 for c in s):
                        strings.append((s, i))
                        i += 2 + length
                        continue
                except:
                    pass
        i += 1
    return strings

strings = find_all_strings(d)
# Deduplicate by string content
seen = set()
for s, pos in strings:
    if s not in seen and len(s) >= 3:
        print(s)
        seen.add(s)
