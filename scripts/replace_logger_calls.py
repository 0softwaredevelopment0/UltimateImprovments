"""
Mass replace getLogger().info/warning/severe() calls with ConsoleLogger.info/warn/error()
and add the required import.
"""
import os
import re

BASE = os.path.join("src", "main", "java", "com", "mcplugin")

REPLACEMENTS = [
    # Main.getInstance().getLogger().info( → ConsoleLogger.info(
    ("Main.getInstance().getLogger().info(", "ConsoleLogger.info("),
    # Main.getInstance().getLogger().warning( → ConsoleLogger.warn(
    ("Main.getInstance().getLogger().warning(", "ConsoleLogger.warn("),
    # Main.getInstance().getLogger().severe( → ConsoleLogger.error(
    ("Main.getInstance().getLogger().severe(", "ConsoleLogger.error("),
    # plugin.getLogger().info( → ConsoleLogger.info(
    ("plugin.getLogger().info(", "ConsoleLogger.info("),
    # plugin.getLogger().warning( → ConsoleLogger.warn(
    ("plugin.getLogger().warning(", "ConsoleLogger.warn("),
    # plugin.getLogger().severe( → ConsoleLogger.error(
    ("plugin.getLogger().severe(", "ConsoleLogger.error("),
    # getLogger().info( → ConsoleLogger.info(
    ("getLogger().info(", "ConsoleLogger.info("),
    # getLogger().warning( → ConsoleLogger.warn(
    ("getLogger().warning(", "ConsoleLogger.warn("),
    # getLogger().severe( → ConsoleLogger.error(
    ("getLogger().severe(", "ConsoleLogger.error("),
]

IMPORT_LINE = "import com.mcplugin.infrastructure.util.ConsoleLogger;"
IMPORT_PATTERN = re.compile(r'^(import\s+com\.mcplugin\.infrastructure\.)', re.MULTILINE)

def add_import(content):
    """Add ConsoleLogger import if not present, after the last mcplugin import."""
    if IMPORT_LINE in content:
        return content
    
    # Find the last mcplugin import line
    matches = list(IMPORT_PATTERN.finditer(content))
    if matches:
        last_import = matches[-1]
        pos = last_import.end()
        # Insert after the last mcplugin import
        insert_at = content.index('\n', pos) + 1
        return content[:insert_at] + IMPORT_LINE + "\n" + content[insert_at:]
    
    # Fallback: insert after package declaration
    pkg_match = re.search(r'^package\s+[^;]+;\s*$', content, re.MULTILINE)
    if pkg_match:
        insert_at = pkg_match.end() + 1
        return content[:insert_at] + "\n" + IMPORT_LINE + "\n" + content[insert_at:]
    
    return content


def process_file(filepath):
    """Process a single Java file: replace getLogger calls and add import."""
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    original = content
    
    # Apply replacements
    for old, new in REPLACEMENTS:
        if old in content:
            content = content.replace(old, new)
    
    if content == original:
        return False  # No changes
    
    # Add import
    content = add_import(content)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
    
    return True


def main():
    # Read the list of files with getLogger() calls
    import subprocess
    result = subprocess.run(
        ['grep', '-r', '-l', r'getLogger()\.(info|warning|severe)', '--include=*.java'],
        cwd=BASE,
        capture_output=True,
        text=True
    )
    
    files = [f.strip() for f in result.stdout.split('\n') if f.strip()]
    print(f"Found {len(files)} files with getLogger() calls")
    
    changed = 0
    for f in files:
        filepath = os.path.join(BASE, f)
        if os.path.exists(filepath):
            if process_file(filepath):
                print(f"  ✓ {f}")
                changed += 1
        else:
            print(f"  ✗ File not found: {f}")
    
    print(f"\nDone! {changed} files updated.")


if __name__ == "__main__":
    main()
