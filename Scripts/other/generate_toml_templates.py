#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Generates the fully commented English TOML templates consumed by AddonConfigManager.

Source of truth:  ui-core/src/main/resources/config.yml  (settings + messages + messages_en)
Output (overwrite): ui-core/src/main/resources/config/UI-<Addon>.toml
                    one file per addon: settings + messages (RU) + messages_en (EN),
                    every table and every settings key commented, defaults shown.

Comment policy:
  - every table [a.b]        -> curated description (SECTION_DESC) or derived one
  - every settings leaf key  -> description (KEY_DESC or derived from the key name)
                                + "Default: <value>" line
  - message tables           -> description + auto-extracted placeholder list (%name%)
  - message leaves           -> "Default: <value>" line only

Structural rules (matches what CommentedTomlWriter/Plugin parse):
  - leaves of a table are emitted BEFORE its subtables, so a flat table header
    is never re-opened after a nested one (TOML forbids that);
  - table headers quote any bare / numeric / mixed segment: ["1"] instead of [1];
  - YAML None becomes an empty list ([]).

Run from the UltimateImprovments repo root:
  "$LOCALAPPDATA/Programs/Python/Python314/python.exe" Scripts/other/generate_toml_templates.py
"""
import io
import os
import re
import sys

try:
    import yaml  # PyYAML
except ImportError:
    sys.exit("PyYAML is required: pip install pyyaml")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "ui-core", "src", "main", "resources", "config.yml")
OUT_DIR = os.path.join(ROOT, "ui-core", "src", "main", "resources", "config")
os.makedirs(OUT_DIR, exist_ok=True)

# ---------------------------------------------------------------------------
# Routing — keep in sync with AddonCatalog.java
# ---------------------------------------------------------------------------
SECTION_TO_ADDON = {
    "reactor": "UI-MBS",
    "energy": "UI-Energy", "energy_crafting": "UI-Energy",
    "chat": "UI-Chat", "chat_ping": "UI-Chat", "chat_filter": "UI-Chat",
    "ojm": "UI-Chat", "auto_broadcast": "UI-Chat",
    "clan": "UI-Clans",
    "turret": "UI-Combat",
    "maintenance": "UI-Punish", "access_control": "UI-Punish",
    "home": "UI-Essentials", "spawn": "UI-Essentials", "heal_feed": "UI-Essentials",
    "report": "UI-Essentials", "rtp": "UI-Essentials", "near": "UI-Essentials",
    "endersee": "UI-Essentials", "troll": "UI-Essentials",
    "anticheat": "UI-Anticheat",
    "datapack": "UI-Datapack",
    "space": "UI-Shared", "radiation": "UI-Shared",
    "features": "UI-Other", "brand_spoof": "UI-Other", "vanish": "UI-Other",
    "sunburn": "UI-Other", "netherite_upgrade": "UI-Other", "auth": "UI-Other",
    "void_protection": "UI-Other", "death_logger": "UI-Other", "power": "UI-Other",
    "suicide": "UI-Other", "packet_guard": "UI-Other", "proxy_server": "UI-Other",
    "redstone_guard": "UI-Other", "emergency_entity_kill": "UI-Other",
    "server_overload_warning": "UI-Other", "bot_protection": "UI-Other",
    "changedimmension": "UI-Other", "motd": "UI-Other", "tab": "UI-Other",
    "scoreboard": "UI-Other", "bossbar": "UI-Other", "wireless_redstone": "UI-Other",
    "structure_integrity": "UI-Other", "economy": "UI-Other", "enchant": "UI-Other",
    "meteor": "UI-Other", "particle_accelerator": "UI-Other",
    "block_friction": "UI-Other", "protection": "UI-Other", "codepanel": "UI-Other",
}

MSG_GROUP_TO_ADDON = {
    "chat": "UI-Chat", "chat_ping": "UI-Chat", "chat_filter": "UI-Chat",
    "ojm": "UI-Chat", "broadcast": "UI-Chat",
    "clan": "UI-Clans",
    "turret": "UI-Combat", "combat": "UI-Combat",
    "home": "UI-Essentials", "report": "UI-Essentials", "spawn": "UI-Essentials",
    "rtp": "UI-Essentials", "near": "UI-Essentials", "endersee": "UI-Essentials",
    "troll": "UI-Essentials", "misc": "UI-Essentials", "invsee": "UI-Essentials",
    "anticheat": "UI-Anticheat", "ac": "UI-Anticheat",
    "punish": "UI-Punish", "maintenance": "UI-Punish", "blacklist": "UI-Punish",
    "opwhitelist": "UI-Punish", "access_control": "UI-Punish",
    "auth": "UI-Other", "check": "UI-Other", "packet_guard": "UI-Other",
    "bot_protection": "UI-Other", "sudo": "UI-Other", "codepanel": "UI-Other",
    "protection": "UI-Other", "changedimmension": "UI-Other", "motd": "UI-Other",
    "tab": "UI-Other", "scoreboard": "UI-Other", "bossbar": "UI-Other",
    "vanish": "UI-Other", "suicide": "UI-Other", "economy": "UI-Other",
    "notes": "UI-Other", "power": "UI-Other", "death_logger": "UI-Other",
    "structures": "UI-Other", "meteor": "UI-Other", "space": "UI-Other",
    "enchant": "UI-Other", "wireless_redstone": "UI-Other",
    "structure_integrity": "UI-Other", "maintenance": "UI-Other",
}
CORE_MSG_GROUPS = {"general", "reputation", "help", "addons", "language", "prefix"}


def addon_of_section(name):
    return SECTION_TO_ADDON.get(name, "UI-Core")


def addon_of_msg_group(name):
    if name in CORE_MSG_GROUPS:
        return "UI-Core"
    return MSG_GROUP_TO_ADDON.get(name, "UI-Core")


# ---------------------------------------------------------------------------
# Curated descriptions (dotted path or table path -> comment line(s))
# ---------------------------------------------------------------------------
SECTION_DESC = {
    "prefix": "Plugin prefix shown before most plugin messages (MiniMessage format).",
    "messages": "Russian messages (active when lang = \"ru\"). MiniMessage formatting.",
    "messages_en": "English messages (active when lang = \"en\"). MiniMessage formatting.",
    "energy": "Energy system: production, transfer limits and per-machine behaviour.",
    "energy_crafting": "Energy cost of the crafting-related energy features.",
    "reactor": "Nuclear reactor: fuel, heat, explosions and energy output.",
    "features": "Mechanics feature toggles and parameters (UI-Other umbrella).",
    "brand_spoof": "Server brand (mod name) shown in the client's F3 screen.",
    "vanish": "Vanish feature (hidden players).",
    "radiation": "Radiation zones: damage, protection suits and effects.",
    "sunburn": "Sunburn mechanic: players burn in daylight under conditions.",
    "netherite_upgrade": "Custom netherite upgrade recipe options.",
    "auth": "Authentication (registration/login) system options.",
    "void_protection": "Saves players who fall into the void (configurable teleport).",
    "death_logger": "Logs recent deaths so admins can inspect them.",
    "power": "Power/jump mechanics parameters.",
    "suicide": "/kill-style suicide command behaviour.",
    "packet_guard": "Rejects illegal/oversized packets from clients.",
    "proxy_server": "Proxy-related settings (forwarding, IP handling).",
    "redstone_guard": "Redstone lag protection (limits per area/time).",
    "emergency_entity_kill": "Emergency cleanup of excessive entities.",
    "server_overload_warning": "Warns staff when the server is overloaded (TPS drop).",
    "chat_ping": "Sound ping when someone mentions your name in chat.",
    "chat_filter": "Chat word filter (regex-based blocking).",
    "bot_protection": "Blocks bot-like joins/behaviour.",
    "home": "/ui home: number of homes and related limits.",
    "clan": "Clans: creation cost, limits and mechanics.",
    "report": "Player report command toggles.",
    "changedimmension": "/ui chgdim world switching: cooldowns and permissions.",
    "motd": "Server MOTD (server list icon and text).",
    "tab": "Tab-list header/footer customization.",
    "scoreboard": "Sidebar scoreboard (stats display).",
    "bossbar": "Server boss bar shown to all players.",
    "maintenance": "Maintenance mode (kick/knock non-staff players).",
    "chat": "Chat formatting: formats, channels, colors, cooldowns, links.",
    "access_control": "Access control for the punish module.",
    "wireless_redstone": "Wireless redstone transmitters/receivers.",
    "endersee": "/ui endersee — inspect other players' ender chests.",
    "structure_integrity": "Multi-block structure integrity checks and decay.",
    "economy": "Server economy: starting balance and income sources.",
    "heal_feed": "/ui heal and /ui feed behaviour and amounts.",
    "reputation": "Reputation auto-penalties (staff-issued numeric scale).",
    "enchant": "Custom enchantments: global parameters.",
    "spawn": "Spawn command/location settings.",
    "anticheat": "Anti-cheat: detection modules, thresholds and punishments.",
    "rtp": "Random teleport (/ui rtp): radius, cooldown and world rules.",
    "near": "/ui near — shows nearby players (staff utility).",
    "meteor": "Meteor events: spawn chance, size, loot.",
    "particle_accelerator": "Particle accelerator machine parameters.",
    "block_friction": "Custom block friction behaviour.",
    "protection": "Protection block: radius, points, fuel.",
    "troll": "Fake/troll commands aimed at suspected hackers.",
    "plugin_version": "Internal plugin version marker. Do not edit.",
    "auto_broadcast": "Automatic broadcast messages shown on a timer.",
    "ojm": "OJM (chat/join message) module settings.",
    "space": "Space dimension: oxygen, effects and environment.",
    "datapack": "Bundled datapack: which custom content is enabled.",
    "turret": "Turret block: targeting, damage and energy use.",

    "messages.general": "General shared messages (errors, player-only, etc.).",
    "messages_en.general": "General shared messages (errors, player-only, etc.).",
    "messages.reputation": "Reputation command output (view, give/set/top/history).",
    "messages_en.reputation": "Reputation command output (view, give/set/top/history).",
    "messages.reputation.view.dialog": "Native reputation dialog window (/ui rep): title, body lines and the Close button.",
    "messages_en.reputation.view.dialog": "Native reputation dialog window (/ui rep): title, body lines and the Close button.",
    "messages.reputation.status": "Account Standing status names (Discord-style scale).",
    "messages_en.reputation.status": "Account Standing status names (Discord-style scale).",
    "messages.help": "/ui help command pages.",
    "messages_en.help": "/ui help command pages.",
    "messages.addons": "/ui addon command output (per-addon management).",
    "messages_en.addons": "/ui addon command output (per-addon management).",
    "messages.language": "/ui language command messages.",
    "messages_en.language": "/ui language command messages.",
    "messages.prefix": "Prefix-related message fragments.",
    "messages_en.prefix": "Prefix-related message fragments.",
    "messages.punishment": "Ban/kick/mute/warn screens and messages. %discord_url% is taken from punishment.discord_url.",
    "messages_en.punishment": "Ban/kick/mute/warn screens and messages. %discord_url% is taken from punishment.discord_url.",
}

KEY_DESC = {
    "prefix": "Prefix string (MiniMessage).",
    "messages.lang": "Global UI language: \"en\" or \"ru\" (per-addon files may override it with their own lang key).",
    "plugin_version": "Internal marker, do not edit.",
    "reputation.report_confirmed": "Rep penalty for a CONFIRMED report verdict (0 disables).",
    "reputation.punish_warn": "Rep penalty per warning (0 disables).",
    "reputation.punish_mute": "Rep penalty per mute (0 disables).",
    "reputation.punish_ban": "Rep penalty per ban (0 disables).",
    "punishment.discord_url": "Discord invite shown in punishment screens (clickable).",
    "heal_feed.heal_amount": "Hearts restored by /ui heal.",
    "heal_feed.feed_amount": "Food points restored by /ui feed.",
    "home.max_homes": "Maximum homes per player (without extra permissions).",
    "chat.cooldown": "Chat cooldown in seconds (anti-spam).",
    "chat.max_length": "Maximum chat message length in characters.",
    "economy.start_balance": "Balance given to a player on first join.",
    "bossbar.enabled": "Master switch: show the server boss bar.",
    "scoreboard.enabled": "Master switch: show the sidebar scoreboard.",
    "chat_ping.enabled": "Master switch: play a sound when your name is mentioned.",
    "maintenance.enabled": "Maintenance mode switch: non-staff players are kicked/blocked.",
    "spawn.enabled": "Enable the /ui spawn command.",
    "endersee.enabled": "Enable the /ui endersee command.",
    "near.enabled": "Enable the /ui near command.",
    "vanish.enabled": "Enable the vanish feature.",
    "wireless_redstone.enabled": "Enable wireless redstone transmitters/receivers.",
    "troll.enabled": "Enable the fake troll commands.",
    "block_friction.enabled": "Enable the custom block friction behaviour.",
}

# Key-name heuristics: substring -> description (first match wins)
NAME_HINTS = [
    ("enabled", "Master switch (true = feature on)."),
    ("interval", "Interval in ticks (20 ticks = 1 second)."),
    ("cooldown", "Cooldown."),
    ("timeout", "Timeout."),
    ("delay", "Delay."),
    ("duration", "Duration."),
    ("period", "Period."),
    ("radius", "Radius in blocks."),
    ("distance", "Distance in blocks."),
    ("range", "Range."),
    ("limit", "Limit."),
    ("max", "Maximum value."),
    ("min", "Minimum value."),
    ("amount", "Amount."),
    ("cost", "Cost."),
    ("price", "Price."),
    ("chance", "Chance (0.0-1.0 or percent, see the value)."),
    ("damage", "Damage."),
    ("energy", "Energy amount."),
    ("size", "Size."),
    ("height", "Height."),
    ("length", "Length."),
    ("weight", "Weight."),
    ("power", "Power."),
    ("speed", "Speed."),
    ("sound", "Sound name (minecraft:...)."),
    ("world", "World name."),
    ("url", "URL."),
    ("message", "Message text (MiniMessage)."),
    ("title", "Title text (MiniMessage)."),
    ("format", "Format string (MiniMessage)."),
    ("name", "Display name (MiniMessage)."),
    ("reason", "Reason text."),
    ("permission", "Permission node."),
    ("command", "Command."),
    ("whitelist", "Whitelist."),
    ("blacklist", "Blacklist."),
    ("icon", "Icon configuration."),
    ("color", "Color."),
    ("material", "Material name (minecraft:...)."),
]


def trunc(s, n):
    s = str(s).replace("\n", "\\n")
    return s if len(s) <= n else s[: n - 1] + "…"


def derive_key_desc(path, value):
    leaf = path.rsplit(".", 1)[-1]
    low = leaf.lower()
    for frag, desc in NAME_HINTS:
        if frag in low:
            return desc
    return None


PLACEHOLDER_RE = re.compile(r"%[A-Za-z_][A-Za-z0-9_]*%")


def collect_placeholders(node):
    found = []
    seen = set()

    def walk(v):
        if isinstance(v, str):
            for ph in PLACEHOLDER_RE.findall(v):
                if ph not in seen:
                    seen.add(ph)
                    found.append(ph)
        elif isinstance(v, list):
            for item in v:
                walk(item)
        elif isinstance(v, dict):
            for item in v.values():
                walk(item)

    walk(node)
    return found


# ---------------------------------------------------------------------------
# TOML serialization
# ---------------------------------------------------------------------------
BARE_RE = re.compile(r"^[A-Za-z0-9_-]+$")
NUM_RE = re.compile(r"^\d")


def quote_segment(seg):
    """Quote table-header segments that are not clean bare keys (e.g. '1', 'to-2')."""
    if BARE_RE.match(seg) and not NUM_RE.match(seg) and seg not in ("true", "false"):
        return seg
    return '"' + seg.replace("\\", "\\\\").replace('"', '\\"') + '"'


def header(path):
    return ".".join(quote_segment(seg) for seg in path.split("."))


def norm(v):
    """Normalize YAML values: None -> [] (null has no TOML scalar form)."""
    if v is None:
        return []
    return v


def fmt_scalar(v):
    if isinstance(v, bool):
        return "true" if v else "false"
    if isinstance(v, int):
        return str(v)
    if isinstance(v, float):
        return repr(v)
    if isinstance(v, str):
        return '"' + v.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t") + '"'
    return '"' + str(v) + '"'


def fmt_inline(v):
    """Value that may contain lists/maps -> inline TOML (toml4j-compatible)."""
    if isinstance(v, list):
        if not v:
            return "[]"
        return "[" + ", ".join(fmt_inline(x) for x in v) + "]"
    if isinstance(v, dict):
        if not v:
            return "{}"
        return "{ " + ", ".join("%s = %s" % (k, fmt_inline(norm(x))) for k, x in v.items()) + " }"
    return fmt_scalar(v)


def default_line(value, is_message):
    if is_message:
        if isinstance(value, str):
            return "# Default: %s" % trunc(value, 110)
        return "# Default: %s" % fmt_inline(value)
    if isinstance(value, str):
        return "# Default: \"%s\"" % trunc(value, 90)
    return "# Default: %s" % fmt_inline(value)


# ---------------------------------------------------------------------------
# Emission
# ---------------------------------------------------------------------------
def emit_table(lines, path, node, emitted):
    """Emit one table: description, header, leaves (with comments), then subtables."""
    emitted.add(path)
    is_message = path.startswith("messages")
    desc = SECTION_DESC.get(path)
    if desc is None:
        tail = path.rsplit(".", 1)[-1]
        desc = "%s section." % tail.replace("_", " ")
    lines.append("# " + desc)
    if is_message:
        phs = collect_placeholders(node)
        if phs:
            lines.append("# Placeholders: " + ", ".join(phs))
    lines.append("[%s]" % header(path))

    scalars = [(k, v) for k, v in node.items() if not isinstance(v, dict)]
    subtables = [(k, v) for k, v in node.items() if isinstance(v, dict)]

    for k, v in scalars:
        leaf = str(k)
        desc = KEY_DESC.get(path + "." + leaf) or derive_key_desc(path + "." + leaf, v)
        if desc:
            lines.append("# " + desc)
        lines.append(default_line(v, is_message))
        lines.append("%s = %s" % (leaf, fmt_inline(norm(v))))

    for k, v in subtables:
        lines.append("")
        emit_table(lines, path + "." + str(k), v, emitted)


def normalize_node(node):
    """Recursively convert None -> [] and int-like keys to strings for emission order."""
    out = {}
    for k, v in node.items():
        key = str(k)
        if isinstance(v, dict):
            out[key] = normalize_node(v)
        else:
            out[key] = norm(v)
    return out


def main():
    with io.open(SRC, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    if not isinstance(cfg, dict):
        sys.exit("config.yml did not parse to a mapping")

    messages = cfg.pop("messages", {}) or {}
    messages_en = cfg.pop("messages_en", {}) or {}
    lang = messages.get("lang", "en") if isinstance(messages, dict) else "en"

    # ---- per-addon buckets ----
    settings = {}
    for section, value in cfg.items():
        settings.setdefault(addon_of_section(str(section)), {})[str(section)] = value

    msgs_ru, msgs_en = {}, {}
    if isinstance(messages, dict):
        for group, value in messages.items():
            if str(group) == "lang":
                continue
            msgs_ru.setdefault(addon_of_msg_group(str(group)), {})[str(group)] = value
    if isinstance(messages_en, dict):
        for group, value in messages_en.items():
            if str(group) == "lang":
                continue
            msgs_en.setdefault(addon_of_msg_group(str(group)), {})[str(group)] = value

    addons = sorted(set(settings) | set(msgs_ru) | set(msgs_en))
    total_lines = 0
    for addon in addons:
        lines = []
        lines.append("# UltimateImprovments — %s configuration." % addon)
        lines.append("# This single file holds the addon's settings AND its messages (RU + EN).")
        lines.append("# Language: the global switch is messages.lang in UI-Core.toml (\"en\" or \"ru\").")
        lines.append("# Every value can be edited; the file is re-read by /ui reload.")
        lines.append("# Missing keys are auto-repaired from the bundled defaults on startup.")
        lines.append("")

        lines.append("# ==================================================================")
        lines.append("# SETTINGS")
        lines.append("# ==================================================================")
        lines.append("")
        emitted = set()
        settings_doc = normalize_node(settings.get(addon, {}))
        # TOML semantics: root scalars must precede the FIRST table header,
        # otherwise they would land inside the previous table.
        root_scalars = [(k, v) for k, v in settings_doc.items() if not isinstance(v, dict)]
        if root_scalars:
            for k, v in root_scalars:
                desc = KEY_DESC.get(k) or derive_key_desc(k, v) or "%s setting." % k.replace("_", " ")
                lines.append("# " + desc)
                lines.append(default_line(v, False))
                lines.append("%s = %s" % (k, fmt_inline(v)))
            lines.append("")
        for section, subtree in settings_doc.items():
            if not isinstance(subtree, dict):
                continue
            lines.append("")
            emit_table(lines, section, subtree, emitted)

        if addon == "UI-Core":
            lines.append("")
            lines.append("# ==================================================================")
            lines.append("# GLOBAL LANGUAGE")
            lines.append("# ==================================================================")
            lines.append("")
            lines.append('# Active UI language: "en" or "ru".')
            lines.append('# Default: "%s"' % lang)
            lines.append("[messages]")
            lines.append('lang = "%s"' % lang)
        if msgs_ru.get(addon):
            lines.append("")
            lines.append("# ==================================================================")
            lines.append('# MESSAGES — RUSSIAN (active when lang = "ru")')
            lines.append("# ==================================================================")
            lines.append("")
            for group, subtree in normalize_node(msgs_ru[addon]).items():
                lines.append("")
                emit_table(lines, "messages." + group, subtree, emitted)
        if msgs_en.get(addon):
            lines.append("")
            lines.append("# ==================================================================")
            lines.append('# MESSAGES — ENGLISH (active when lang = "en")')
            lines.append("# ==================================================================")
            lines.append("")
            for group, subtree in normalize_node(msgs_en[addon]).items():
                lines.append("")
                emit_table(lines, "messages_en." + group, subtree, emitted)

        # drop the leading blank line (cosmetic)
        while lines and lines[0] == "":
            lines.pop(0)

        out_path = os.path.join(OUT_DIR, addon + ".toml")
        with io.open(out_path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines) + "\n")
        total_lines += len(lines)
        print("%-14s %6d lines  ->  %s" % (addon, len(lines), os.path.basename(out_path)))

    print("\nTotal: %d addons, %d lines" % (len(addons), total_lines))


if __name__ == "__main__":
    main()
