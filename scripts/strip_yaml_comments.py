#!/usr/bin/env python3
"""Strip all comments from YAML files. Usage: python strip_yaml_comments.py <input> <output>"""
import sys, re

def strip_yaml_comments(in_path, out_path):
    with open(in_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    result = []
    for line in lines:
        s = line.rstrip('\n').rstrip('\r')
        if s.strip().startswith('#'):
            continue
        if '#' in s:
            s = re.sub(r'\s+#.*$', '', s)
        result.append(s)
    # Remove trailing blank lines
    while result and not result[-1].strip():
        result.pop()
    txt = '\n'.join(result) + '\n'
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(txt)
    print(f"OK: {len(lines)} -> {len(result)} lines")

if __name__ == '__main__':
    strip_yaml_comments(sys.argv[1], sys.argv[2])
