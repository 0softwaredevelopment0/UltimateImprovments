#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
Splits the monolithic ui-core/src/main/resources/config.yml into per-addon resource
fragments consumed by the new AddonConfigManager layout.

Reads:   ui-core/src/main/resources/config.yml
Writes:  ui-core/src/main/resources/config/<Addon>.yml      (settings + messages-ru)
         ui-core/src/main/resources/config/<Addon>_en.yml   (messages-en)
         ui-core/src/main/resources/config/<Addon>.toml     (readable settings template)
         ui-core/src/main/resources/config/<Addon>_ru.toml  (readable messages template)

Run from the UltimateImprovments repo root:  python Scripts/other/split_config.py
"""
import io
import os
import sys

# --- minimal YAML-ish dependency check -------------------------------------
try:
    import yaml  # PyYAML
except ImportError:
    sys.exit("PyYAML is required: pip install pyyaml")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SRC = os.path.join(ROOT, "ui-core", "src", "main", "resources", "config.yml")
OUT_DIR = os.path.join(ROOT, "ui-core", "src", "main", "resources", "config")
os.makedirs(OUT_DIR, exist_ok=True)

# ---------------------------------------------------------------------------
# Section (root key) -> addon.  Sections not listed stay with UI-Core.
# ---------------------------------------------------------------------------
SECTION_TO_ADDON = {
    # UI-MBS
    "reactor": "UI-MBS",
    # UI-Energy
    "energy": "UI-Energy",
    "energy_crafting": "UI-Energy",
    # UI-Chat
    "chat": "UI-Chat",
    "chat_ping": "UI-Chat",
    "chat_filter": "UI-Chat",
    "ojm": "UI-Chat",
    "auto_broadcast": "UI-Chat",
    # UI-Clans
    "clan": "UI-Clans",
    # UI-Combat
    "turret": "UI-Combat",
    # UI-Punish
    "maintenance": "UI-Punish",
    "access_control": "UI-Punish",
    # UI-Essentials
    "home": "UI-Essentials",
    "spawn": "UI-Essentials",
    "heal_feed": "UI-Essentials",
    "report": "UI-Essentials",
    "rtp": "UI-Essentials",
    "near": "UI-Essentials",
    "endersee": "UI-Essentials",
    "troll": "UI-Essentials",
    # UI-Anticheat
    "anticheat": "UI-Anticheat",
    # UI-Datapack
    "datapack": "UI-Datapack",
    # UI-Shared
    "space": "UI-Shared",
    "radiation": "UI-Shared",
    # UI-Other (features umbrella + everything mechanics-side)
    "features": "UI-Other",
    "brand_spoof": "UI-Other",
    "vanish": "UI-Other",
    "sunburn": "UI-Other",
    "netherite_upgrade": "UI-Other",
    "auth": "UI-Other",
    "void_protection": "UI-Other",
    "death_logger": "UI-Other",
    "power": "UI-Other",
    "suicide": "UI-Other",
    "packet_guard": "UI-Other",
    "proxy_server": "UI-Other",
    "redstone_guard": "UI-Other",
    "emergency_entity_kill": "UI-Other",
    "server_overload_warning": "UI-Other",
    "bot_protection": "UI-Other",
    "changedimmension": "UI-Other",
    "motd": "UI-Other",
    "tab": "UI-Other",
    "scoreboard": "UI-Other",
    "bossbar": "UI-Other",
    "wireless_redstone": "UI-Other",
    "structure_integrity": "UI-Other",
    "economy": "UI-Other",
    "enchant": "UI-Other",
    "meteor": "UI-Other",
    "particle_accelerator": "UI-Other",
    "block_friction": "UI-Other",
    "protection": "UI-Other",
    "codepanel": "UI-Other",
}

# Message top-level groups (under messages: / messages_en:) -> addon.
# Anything not listed stays in UI-Core. Derived from the call-site scan.
MSG_GROUP_TO_ADDON = {
    # UI-Chat
    "chat": "UI-Chat", "chat_ping": "UI-Chat", "chat_filter": "UI-Chat",
    "ojm": "UI-Chat", "broadcast": "UI-Chat",
    # UI-Clans
    "clan": "UI-Clans",
    # UI-Combat
    "turret": "UI-Combat", "combat": "UI-Combat",
    # UI-Essentials
    "home": "UI-Essentials", "report": "UI-Essentials", "spawn": "UI-Essentials",
    "rtp": "UI-Essentials", "near": "UI-Essentials", "endersee": "UI-Essentials",
    "troll": "UI-Essentials", "misc": "UI-Essentials", "invsee": "UI-Essentials",
    # UI-Anticheat
    "anticheat": "UI-Anticheat", "ac": "UI-Anticheat",
    # UI-Punish
    "punish": "UI-Punish", "maintenance": "UI-Punish", "blacklist": "UI-Punish",
    "opwhitelist": "UI-Punish", "access_control": "UI-Punish",
    # UI-Other (security/mechanics umbrella)
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

# UI-Core keeps a handful of global groups regardless of the table above.
CORE_MSG_GROUPS = {"general", "reputation", "help", "addons", "language", "prefix"}


def addon_of_section(name):
    return SECTION_TO_ADDON.get(name, "UI-Core")


def addon_of_msg_group(name):
    if name in CORE_MSG_GROUPS:
        return "UI-Core"
    return MSG_GROUP_TO_ADDON.get(name, "UI-Core")


def dump_yaml(data, path):
    with io.open(path, "w", encoding="utf-8") as f:
        yaml.safe_dump(data, f, allow_unicode=True, sort_keys=False, default_flow_style=False)


def dump_toml(data, path, header):
    """Serialize a nested dict as TOML text (settings-level fidelity; strings/numbers/bools/lists)."""
    lines = ["# " + header, "# Edit values below; the file is reloaded by /ui reload.", ""]

    def fmt(v):
        if isinstance(v, bool):
            return "true" if v else "false"
        if isinstance(v, (int, float)):
            return str(v)
        if isinstance(v, str):
            s = v.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n")
            return '"' + s + '"'
        if isinstance(v, list):
            if not v:
                return "[]"
            inner = ", ".join(fmt(x) for x in v)
            # single-line list is fine for toml4j
            return "[" + inner + "]"
        return '"' + str(v) + '"'

    def scalar_ok(v):
        return not isinstance(v, dict) and not isinstance(v, list) or isinstance(v, list)

    def emit(mapping, prefix):
        scalars = [(k, v) for k, v in mapping.items() if not isinstance(v, dict)]
        tables = [(k, v) for k, v in mapping.items() if isinstance(v, dict)]
        for k, v in scalars:
            lines.append("%s = %s" % (k, fmt(v)))
        for k, v in tables:
            if lines and lines[-1] != "":
                lines.append("")
            full = (prefix + "." + str(k)) if prefix else str(k)
            lines.append("[%s]" % full)
            emit(v, full)

    emit(data, "")
    with io.open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")


def main():
    with io.open(SRC, "r", encoding="utf-8") as f:
        cfg = yaml.safe_load(f)
    if not isinstance(cfg, dict):
        sys.exit("config.yml did not parse to a mapping")

    messages = cfg.pop("messages", {}) or {}
    messages_en = cfg.pop("messages_en", {}) or {}
    lang = "en"
    if isinstance(messages, dict) and isinstance(messages.get("lang"), str):
        lang = messages["lang"]

    # settings parts: addon -> {section: {...}}
    settings = {}
    for section, value in cfg.items():
        addon = addon_of_section(str(section))
        settings.setdefault(addon, {})[section] = value

    # message parts: addon -> ru group dict / en group dict
    msgs_ru, msgs_en = {}, {}
    for group, value in messages.items():
        if str(group) == "lang":
            continue
        addon = addon_of_msg_group(str(group))
        msgs_ru.setdefault(addon, {})[group] = value
    for group, value in messages_en.items():
        if str(group) == "lang":
            continue
        addon = addon_of_msg_group(str(group))
        msgs_en.setdefault(addon, {})[group] = value

    addons = sorted(set(settings) | set(msgs_ru) | set(msgs_en))
    for addon in addons:
        # ---- settings yml: settings + [messages] + [messages_en] ----
        doc = {}
        doc.update(settings.get(addon, {}))
        lang_header = {"lang": lang}
        if msgs_ru.get(addon):
            doc["messages"] = dict(lang_header, **msgs_ru[addon])
        if msgs_en.get(addon):
            doc["messages_en"] = msgs_en[addon]
        if addon == "UI-Core":
            # keep the global language switch readable at the top of UI-Core.yml
            pass
        dump_yaml(doc, os.path.join(OUT_DIR, addon + ".yml"))

        # ---- en-only yml ----
        if msgs_en.get(addon):
            dump_yaml({"messages_en": msgs_en[addon]},
                      os.path.join(OUT_DIR, addon + "_en.yml"))

        # ---- readable TOML templates ----
        dump_toml(settings.get(addon, {}),
                  os.path.join(OUT_DIR, addon + ".toml"),
                  addon + " settings (auto-generated from config.yml)")
        ru_doc = dict(lang_header)
        ru_doc.update(msgs_ru.get(addon, {}))
        dump_toml(ru_doc, os.path.join(OUT_DIR, addon + "_ru.toml"),
                  addon + " messages (RU)")

    # report
    for name in sorted(os.listdir(OUT_DIR)):
        p = os.path.join(OUT_DIR, name)
        print("%8d  %s" % (os.path.getsize(p), name))
    print("\naddons:", ", ".join(addons))


if __name__ == "__main__":
    main()
