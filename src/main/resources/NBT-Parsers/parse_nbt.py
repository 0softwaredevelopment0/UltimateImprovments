import gzip
import struct
import json

# Read and decompress NBT
data = open('src/main/java/com/mcplugin/core1/reactorcore1.nbt', 'rb').read()
d = gzip.decompress(data)

# Scan for readable strings
strings = []
current = ''
for byte in d:
    if 32 <= byte < 127:
        current += chr(byte)
    else:
        if len(current) >= 3:
            strings.append(current)
        current = ''
if len(current) >= 3:
    strings.append(current)

print('=== All strings found in NBT ===')
for s in sorted(set(strings), key=len, reverse=True):
    print(s)
