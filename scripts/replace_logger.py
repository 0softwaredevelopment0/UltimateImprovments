"""
Mass replace getLogger().info/warning/severe() with ConsoleLogger.info/warn/error().
"""
import os
import subprocess

BASE = "src/main/java/com/mcplugin"
CWD = os.getcwd()
BASE_PATH = os.path.join(CWD, BASE)

# Step 1: Find all files with getLogger() calls
result = subprocess.run(
    ["grep", "-r", "-l", r"getLogger()\.", "--include=*.java", "."],
    cwd=BASE_PATH,
    capture_output=True,
    text=True,
)

files = [f.strip().lstrip("./") for f in result.stdout.split("\n") if f.strip()]
print(f"Found {len(files)} files with getLogger() calls\n")

IMPORT_LINE = "import com.mcplugin.infrastructure.util.ConsoleLogger;"

replacements = {
    "Main.getInstance().getLogger().info(": "ConsoleLogger.info(",
    "Main.getInstance().getLogger().warning(": "ConsoleLogger.warn(",
    "Main.getInstance().getLogger().severe(": "ConsoleLogger.error(",
    "plugin.getLogger().info(": "ConsoleLogger.info(",
    "plugin.getLogger().warning(": "ConsoleLogger.warn(",
    "plugin.getLogger().severe(": "ConsoleLogger.error(",
    "getLogger().info(": "ConsoleLogger.info(",
    "getLogger().warning(": "ConsoleLogger.warn(",
    "getLogger().severe(": "ConsoleLogger.error(",
}

def add_import(content):
    if IMPORT_LINE in content:
        return content
    lines = content.split("\n")
    insert_at = -1
    for i, line in enumerate(lines):
        if line.startswith("import com.mcplugin.infrastructure."):
            insert_at = i + 1
    if insert_at > 0 and insert_at < len(lines):
        lines.insert(insert_at, IMPORT_LINE)
        return "\n".join(lines)
    # Fallback: after package
    for i, line in enumerate(lines):
        if line.startswith("package "):
            lines.insert(i + 1, "")
            lines.insert(i + 2, IMPORT_LINE)
            return "\n".join(lines)
    return content

changed = 0
for f in files:
    fp = os.path.join(BASE_PATH, f)
    if not os.path.exists(fp):
        continue

    with open(fp, "r", encoding="utf-8") as fh:
        content = fh.read()

    original = content
    for old, new in replacements.items():
        content = content.replace(old, new)

    if content == original:
        continue

    content = add_import(content)

    with open(fp, "w", encoding="utf-8") as fh:
        fh.write(content)

    print(f"  OK: {f}")
    changed += 1

print(f"\nDone! {changed} files updated.")
