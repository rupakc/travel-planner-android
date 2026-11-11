#!/usr/bin/env python3
"""Web i18next catalogues -> Android `values-<lang>/strings_web.xml`.

The web repo (rupakc/travel-planner, commit pinned in config/web-source.properties)
is the source of the de/fr/hi/zh/bn copy. A module opts in with a map file,
config/i18n/web-keys/<module with / -> ->.json:

    {"strings": {"<android name>": "<ns>:<dotted.key>"},
     "plurals": {"<android name>": "<ns>:<base key without _one/_other>"}}

A mapping is valid only when the Android English equals the web English once both
are normalised (placeholders -> one marker, Android escapes undone; the web literal
`Voyager` may stand for an Android `%n$s`, the brand rule). Mapped strings are
generated; every other translatable string is hand-translated in files named like
the module's values/ files.

    gen_strings.py suggest  --web <locales dir> --module <M>
    gen_strings.py generate --web <locales dir> [--module <M>]...
    gen_strings.py check    --web <locales dir> [--module <M>]...

<locales dir> is the web's frontend/src/i18n/locales (holding en/<ns>.json, ...).
<M> is a module path such as feature/results (":feature:results" also works).
Exit status: 0 OK, 1 problems found, 2 bad usage or unreadable input.
Python 3 standard library only.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from websource import read_pin  # noqa: E402

LANGS = ["de", "fr", "hi", "zh", "bn"]
PLURAL_CATEGORIES = {"de": ["one", "other"], "fr": ["one", "many", "other"], "hi": ["one", "other"], "zh": ["other"], "bn": ["one", "other"]}
QUANTITIES = ("zero", "one", "two", "few", "many", "other")
BRAND = "Voyager"
GENERATED_FILE = "strings_web.xml"
MAP_DIR = Path("config") / "i18n" / "web-keys"
NAMESPACE_PREFERENCE = {
    "feature/results": ["results", "sections"],
    "feature/search": ["search"],
    "feature/chat": ["chat"],
    "feature/plan": ["plan", "itinerary"],
    "feature/alerts": ["alerts"],
    "feature/settings": ["account", "privacy", "common"],
    "feature/auth": ["auth"],
    "feature/share": ["account", "plan"],
    "core/ui": ["sections", "itinerary", "privacy", "common"],
}
DEFAULT_NAMESPACES = ["common", "misc"]
CMD = "python3 tools/web-sync/gen_strings.py"

# The specifier forms the brief names: %n$s %n$d %n$f %s %d (and %f); `%%` is a literal percent.
SPEC_RE = re.compile(r"%%|%(?:(\d+)\$)?([sdf])")
WEB_PH_RE = re.compile(r"\{\{\s*([^{},\s]+)\s*(?:,[^{}]*)?\}\}")
WEB_MARKUP_RE = re.compile(r"</?[A-Za-z][A-Za-z0-9:_-]*(?:\s[^<>]*)?/?>|<\d+>|</\d+>|<\d+/>")


class UsageError(Exception):
    """Bad arguments or unreadable input (exit status 2)."""


# --- naming ---------------------------------------------------------------------


def normalise_module(module: str) -> str:
    """`:feature:results`, `feature/results/` -> `feature/results`."""
    m = module.strip().replace("\\", "/")
    if m.startswith(":"):
        m = m[1:].replace(":", "/")
    return m.strip("/")


def map_file_name(module: str) -> str:
    return normalise_module(module).replace("/", "-") + ".json"


def preferred_namespaces(module: str) -> list[str]:
    return list(NAMESPACE_PREFERENCE.get(normalise_module(module), DEFAULT_NAMESPACES))


# --- Android side -------------------------------------------------------------------


@dataclass
class Spec:
    """One format specifier: its text as written and its 1-based argument index."""

    text: str
    index: int
    conv: str

    @property
    def positional(self) -> str:
        """The specifier with an explicit argument index (`%s` -> `%2$s`)."""
        if "$" in self.text:
            return self.text
        return f"%{self.index}${self.text[1:]}"


def aapt_decode(raw: str) -> str:
    """Undo Android string escaping the way aapt2 reads a value.

    Backslash escapes (\\' \\" \\n \\t \\@ \\? \\\\ \\uXXXX) are resolved, unescaped double
    quotes are dropped (text inside them keeps its whitespace), and outside quotes
    runs of whitespace collapse to one space and the ends are trimmed. `%%` is kept;
    format tokens are handled by `tokens_android`.
    """
    out: list[tuple[str, bool]] = []  # (char, collapsible whitespace)
    quoted = False
    i = 0
    while i < len(raw):
        c = raw[i]
        if c == "\\" and i + 1 < len(raw):
            n = raw[i + 1]
            if n == "u" and re.fullmatch(r"[0-9A-Fa-f]{4}", raw[i + 2:i + 6] or ""):
                out.append((chr(int(raw[i + 2:i + 6], 16)), False))
                i += 6
                continue
            out.append(({"n": "\n", "t": "\t"}.get(n, n), False))
            i += 2
            continue
        if c == '"':
            quoted = not quoted
        elif c in " \t\r\n" and not quoted:
            if not (out and out[-1][1]):
                out.append((" ", True))
        else:
            out.append((c, False))
        i += 1
    while out and out[0][1]:
        out.pop(0)
    while out and out[-1][1]:
        out.pop()
    return "".join(c for c, _ in out)


def tokens_android(decoded: str) -> list[str | Spec]:
    """Split decoded Android text into literal text and specifiers (`%%` -> `%`)."""
    tokens: list[str | Spec] = []
    lit = ""
    pos = 0
    implicit = 0
    for m in SPEC_RE.finditer(decoded):
        lit += decoded[pos:m.start()]
        pos = m.end()
        if m.group(0) == "%%":
            lit += "%"
            continue
        if lit:
            tokens.append(lit)
            lit = ""
        if m.group(1):
            index = int(m.group(1))
        else:
            implicit += 1
            index = implicit
        tokens.append(Spec(m.group(0), index, m.group(2)))
    lit += decoded[pos:]
    if lit:
        tokens.append(lit)
    return tokens


def spec_set(decoded: str) -> set[tuple[int, str]]:
    return {(t.index, t.conv) for t in tokens_android(decoded) if isinstance(t, Spec)}


def fmt_specs(specs) -> str:
    return "{" + ", ".join(f"%{i}${c}" for i, c in sorted(specs)) + "}" if specs else "{none}"


@dataclass
class AndroidEntry:
    """One <string> or <plurals> from a resource file."""

    kind: str  # "string" | "plurals"
    name: str
    file: Path
    translatable: bool
    markup: bool
    value: str | None = None  # decoded, strings only
    items: dict[str, str] = field(default_factory=dict)  # quantity -> decoded, plurals only
    raw_items: list[str] = field(default_factory=list)  # quantities in file order (duplicates kept)

    @property
    def key(self) -> tuple[str, str]:
        return (self.kind, self.name)

    def label(self) -> str:
        return f"{self.kind}/{self.name}"

    def all_values(self) -> list[str]:
        return [self.value or ""] if self.kind == "string" else list(self.items.values())

    def specs(self) -> set[tuple[int, str]]:
        out: set[tuple[int, str]] = set()
        for v in self.all_values():
            out |= spec_set(v)
        return out


def _inner_text(el: ET.Element) -> tuple[str, bool]:
    """(text of the element including any child markup's text, has child elements)."""
    return "".join(el.itertext()), len(el) > 0


def read_resource_file(path: Path) -> list[AndroidEntry]:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as e:
        raise UsageError(f"{path}: not well-formed XML ({e})") from None
    if root.tag != "resources":
        return []
    entries = []
    for el in root:
        if el.tag not in ("string", "plurals"):
            continue
        name = el.get("name")
        if not name:
            raise UsageError(f"{path}: a <{el.tag}> has no name attribute")
        translatable = el.get("translatable", "true") != "false"
        if el.tag == "string":
            text, markup = _inner_text(el)
            entries.append(AndroidEntry("string", name, path, translatable, markup, value=aapt_decode(text)))
        else:
            entry = AndroidEntry("plurals", name, path, translatable, False)
            for item in el.findall("item"):
                q = item.get("quantity", "")
                text, markup = _inner_text(item)
                entry.markup |= markup
                entry.raw_items.append(q)
                entry.items[q] = aapt_decode(text)
            entries.append(entry)
    return entries


def read_values_dir(directory: Path, skip: str | None = None) -> list[AndroidEntry]:
    entries: list[AndroidEntry] = []
    if directory.is_dir():
        for path in sorted(directory.glob("*.xml")):
            if path.name != skip:
                entries += read_resource_file(path)
    return entries


# --- web side -----------------------------------------------------------------------


class Web:
    """The web catalogues: {lang: {"ns:dotted.key": value}}."""

    def __init__(self, locales: Path):
        self.dir = Path(locales)
        en_dir = self.dir / "en"
        if not en_dir.is_dir() or not any(en_dir.glob("*.json")):
            raise UsageError(
                f"--web {self.dir}: no en/*.json there; pass the web locales directory "
                f"(frontend/src/i18n/locales of the pinned commit, e.g. extracted with "
                f"git -C ~/travel-planner archive <sha> frontend/src/i18n/locales | tar -x -C <tmp>)"
            )
        self.values: dict[str, dict[str, str]] = {lang: self._load(lang) for lang in ["en", *LANGS]}
        en = self.values["en"]
        self.plural_bases = sorted({k.rsplit("_", 1)[0] for k in en if k.endswith("_other")})
        plural_set = set(self.plural_bases)
        self.plural_keys = {k for k in en if "_" in k and k.rsplit("_", 1)[1] in QUANTITIES and k.rsplit("_", 1)[0] in plural_set}

    def _load(self, lang: str) -> dict[str, str]:
        out: dict[str, str] = {}
        d = self.dir / lang
        if not d.is_dir():
            return out
        for path in sorted(d.glob("*.json")):
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
            except (OSError, ValueError) as e:
                raise UsageError(f"{path}: cannot read JSON ({e})") from None
            self._flatten(path.stem + ":", data, out)
        return out

    @staticmethod
    def _flatten(prefix: str, data, out: dict[str, str]) -> None:
        if isinstance(data, dict):
            for k, v in data.items():
                sep = "" if prefix.endswith(":") else "."
                Web._flatten(f"{prefix}{sep}{k}", v, out)
        elif isinstance(data, str):
            out[prefix] = data

    def get(self, lang: str, key: str) -> str | None:
        return self.values.get(lang, {}).get(key)


def has_web_markup(value: str) -> bool:
    return bool(WEB_MARKUP_RE.search(value))


# --- matching -----------------------------------------------------------------------


class Unmappable(Exception):
    """An Android/web pair that cannot be mapped; the message says why."""


@dataclass
class Binding:
    """How the web placeholders of one key map onto the Android specifiers."""

    names: dict[str, Spec] = field(default_factory=dict)  # web {{name}} -> Android spec
    brand: Spec | None = None  # Android spec standing for the literal "Voyager"
    has_args: bool = False  # the Android English has format arguments

    def merge(self, other: Binding) -> None:
        for name, spec in other.names.items():
            if name in self.names and self.names[name].index != spec.index:
                raise Unmappable(f"web {{{{{name}}}}} maps to %{self.names[name].index}$ in one form and %{spec.index}$ in another")
            self.names[name] = spec
        if other.brand:
            if self.brand and self.brand.index != other.brand.index:
                raise Unmappable("the brand maps to different specifiers in different forms")
            self.brand = other.brand
        self.has_args |= other.has_args
        self._check_bijective()

    def _check_bijective(self) -> None:
        seen: dict[int, str] = {}
        for name, spec in self.names.items():
            if spec.index in seen and seen[spec.index] != name:
                raise Unmappable(f"web {{{{{seen[spec.index]}}}}} and {{{{{name}}}}} both map to %{spec.index}$")
            seen[spec.index] = name
        if self.brand and self.brand.index in seen:
            raise Unmappable(f"%{self.brand.index}$ stands for both {BRAND} and {{{{{seen[self.brand.index]}}}}}")


def bind(android: str, web: str) -> Binding:
    """Match one decoded Android English value with one web English value."""
    if has_web_markup(web):
        raise Unmappable("the web value has <tag> markup")
    tokens = tokens_android(android)
    pattern = ""
    specs: list[Spec] = []
    for t in tokens:
        if isinstance(t, Spec):
            ph = r"\{\{\s*[^{},\s]+\s*(?:,[^{}]*)?\}\}"
            pattern += f"({ph}|{re.escape(BRAND)})" if t.conv == "s" else f"({ph})"
            specs.append(t)
        else:
            pattern += re.escape(t)
    m = re.fullmatch(pattern, web, re.DOTALL)
    if not m:
        raise Unmappable(f"English differs (Android {android!r}, web {web!r})")
    binding = Binding(has_args=bool(specs))
    for spec, text in zip(specs, m.groups()):
        if text == BRAND:
            if binding.brand and binding.brand.index != spec.index:
                raise Unmappable(f"{BRAND} maps to two different specifiers")
            binding.brand = spec
            continue
        name = WEB_PH_RE.fullmatch(text).group(1)
        if name in binding.names and binding.names[name].index != spec.index:
            raise Unmappable(f"web {{{{{name}}}}} repeats but maps to %{binding.names[name].index}$ and %{spec.index}$")
        binding.names[name] = spec
    binding._check_bijective()
    return binding


def bind_entry(entry: AndroidEntry, web: Web, key: str) -> Binding:
    """Validate a map entry against the web English; raise Unmappable with the reason."""
    if not entry.translatable:
        raise Unmappable('the Android string is translatable="false"')
    if entry.markup:
        raise Unmappable("the Android value has <tag> markup")
    if entry.kind == "string":
        value = web.get("en", key)
        if value is None:
            raise Unmappable(f"web key {key} is missing from en" + (" (it is a plural; map it under \"plurals\")" if key in web.plural_bases else ""))
        return bind(entry.value or "", value)
    binding = Binding()
    if web.get("en", key + "_other") is None:
        raise Unmappable(f"web plural {key} is missing from en (no {key}_other)")
    for q, text in entry.items.items():
        value = web.get("en", f"{key}_{q}")
        if value is None:
            raise Unmappable(f"web key {key}_{q} is missing from en (Android has quantity {q})")
        try:
            binding.merge(bind(text, value))
        except Unmappable as e:
            raise Unmappable(f"quantity {q}: {e}") from None
    return binding


def convert(translation: str, binding: Binding, plural_form: bool) -> str:
    """Turn one web translation into an escaped Android value."""
    used: set[str] = set()
    parts: list[tuple[str, bool]] = []  # (text, is_spec)
    pos = 0
    for m in WEB_PH_RE.finditer(translation):
        parts += _literal_parts(translation[pos:m.start()], binding)
        name = m.group(1)
        if name not in binding.names:
            raise Unmappable(f"uses {{{{{name}}}}}, which the English does not have")
        used.add(name)
        parts.append((binding.names[name].positional, True))
        pos = m.end()
    parts += _literal_parts(translation[pos:], binding)
    missing = set(binding.names) - used
    if plural_form:
        count_specs = {n for n in missing if n == "count"}
        missing -= count_specs
    if missing:
        raise Unmappable("drops " + ", ".join(f"{{{{{n}}}}}" for n in sorted(missing)))
    if binding.brand and not any(is_spec and text == binding.brand.positional for text, is_spec in parts):
        raise Unmappable(f"does not contain the literal {BRAND} (the brand placeholder %{binding.brand.index}$s)")
    return escape_android(parts, binding.has_args)


def _literal_parts(text: str, binding: Binding) -> list[tuple[str, bool]]:
    if not binding.brand or BRAND not in text:
        return [(text, False)] if text else []
    out: list[tuple[str, bool]] = []
    for i, piece in enumerate(text.split(BRAND)):
        if i:
            out.append((binding.brand.positional, True))
        if piece:
            out.append((piece, False))
    return out


def escape_android(parts: list[tuple[str, bool]], has_args: bool) -> str:
    out = []
    for text, is_spec in parts:
        if is_spec:
            out.append(text)
            continue
        s = text.replace("\\", "\\\\").replace("&", "&amp;").replace("<", "&lt;")
        s = s.replace("'", "\\'").replace('"', '\\"').replace("\n", "\\n").replace("\t", "\\t")
        if has_args:
            s = s.replace("%", "%%")
        out.append(s)
    value = "".join(out)
    # aapt2 trims and collapses plain spaces; keep the ones that matter.
    value = re.sub(r"(?<= ) ", r"\\u0020", value)
    if value.startswith(" "):
        value = "\\u0020" + value[1:]
    if value.endswith(" "):
        value = value[:-1] + "\\u0020"
    if value[:1] in ("@", "?"):
        value = "\\" + value
    return value


# --- modules and map files -------------------------------------------------------------


def discover_modules(root: Path) -> list[str]:
    """Gradle modules (from settings.gradle.kts) that have src/main/res/values."""
    settings = root / "settings.gradle.kts"
    if settings.exists():
        candidates = [normalise_module(p) for p in re.findall(r'"(:[A-Za-z0-9_:.-]+)"', settings.read_text(encoding="utf-8"))]
    else:
        candidates = sorted(
            str(p.parent.parent.parent.parent.relative_to(root))
            for p in root.glob("**/src/main/res/values")
            if not any(part.startswith(".") or part == "build" for part in p.relative_to(root).parts)
        )
    return sorted({m for m in candidates if (root / m / "src" / "main" / "res" / "values").is_dir()})


@dataclass
class ModuleMap:
    path: Path
    strings: dict[str, str]
    plurals: dict[str, str]


def load_map(root: Path, module: str) -> ModuleMap | None:
    path = root / MAP_DIR / map_file_name(module)
    if not path.exists():
        return None
    rel = path.relative_to(root)
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except ValueError as e:
        raise UsageError(f"{rel}: not valid JSON ({e})") from None
    if not isinstance(data, dict) or set(data) - {"strings", "plurals"}:
        raise UsageError(f'{rel}: expected an object with only "strings" and "plurals"')
    out = {}
    for section in ("strings", "plurals"):
        value = data.get(section, {})
        if not isinstance(value, dict) or not all(isinstance(k, str) and isinstance(v, str) and ":" in v for k, v in value.items()):
            raise UsageError(f'{rel}: "{section}" must map Android names to "<ns>:<key>" strings')
        out[section] = value
    return ModuleMap(path, out["strings"], out["plurals"])


def write_map(path: Path, strings: dict[str, str], plurals: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = {"strings": dict(sorted(strings.items())), "plurals": dict(sorted(plurals.items()))}
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


class Module:
    def __init__(self, root: Path, name: str):
        self.root = root
        self.name = name
        self.res = root / name / "src" / "main" / "res"
        if not (self.res / "values").is_dir():
            raise UsageError(f"module {name}: no {name}/src/main/res/values directory")
        self.english = read_values_dir(self.res / "values")
        self.by_key: dict[tuple[str, str], AndroidEntry] = {}
        self.duplicates: list[AndroidEntry] = []
        for e in self.english:
            if e.key in self.by_key:
                self.duplicates.append(e)
            self.by_key.setdefault(e.key, e)
        self.map = load_map(root, name)

    def rel(self, path: Path) -> str:
        return str(path.relative_to(self.root))

    def generated_path(self, lang: str) -> Path:
        return self.res / f"values-{lang}" / GENERATED_FILE

    def map_rel(self) -> str:
        return str(MAP_DIR / map_file_name(self.name))


# --- generation ------------------------------------------------------------------------


@dataclass
class Generated:
    files: dict[str, str]  # lang -> content
    entries: dict[str, set[tuple[str, str]]]  # lang -> generated keys
    untranslated: dict[tuple[str, str], tuple[str, list[str]]]  # key -> (web key, langs without a web translation)
    errors: list[str]


def build(module: Module, web: Web, sha_line: str) -> Generated:
    """Validate the module's map and render its strings_web.xml files (nothing is written)."""
    errors: list[str] = []
    per_lang: dict[str, list[tuple[str, str]]] = {lang: [] for lang in LANGS}  # (name, xml)
    keys: dict[str, set[tuple[str, str]]] = {lang: set() for lang in LANGS}
    untranslated: dict[tuple[str, str], tuple[str, list[str]]] = {}
    m = module.map
    assert m is not None
    where = module.map_rel()
    for kind, section in (("string", m.strings), ("plurals", m.plurals)):
        for name, key in sorted(section.items()):
            entry = module.by_key.get((kind, name))
            label = f"{where}: {kind}/{name} -> {key}"
            if entry is None:
                other = "plurals" if kind == "string" else "string"
                hint = f' (it is a <{other}>; move it to "{"plurals" if other == "plurals" else "strings"}")' if (other, name) in module.by_key else ""
                errors.append(f"{label}: no such <{kind}> in {module.name}/src/main/res/values{hint}")
                continue
            try:
                binding = bind_entry(entry, web, key)
            except Unmappable as e:
                errors.append(f"{label}: {e}; fix the copy on one side or remove the entry")
                continue
            missing_langs = []
            for lang in LANGS:
                try:
                    xml = _render(kind, name, key, lang, web, binding)
                except Unmappable as e:
                    errors.append(f"{label}: web {lang} translation {e}; fix the web copy or remove the entry and hand-translate it")
                    continue
                if xml is None:
                    missing_langs.append(lang)
                    continue
                per_lang[lang].append((name, xml))
                keys[lang].add((kind, name))
            if missing_langs:
                untranslated[(kind, name)] = (key, missing_langs)
    files = {}
    for lang in LANGS:
        body = "".join(xml for _, xml in sorted(per_lang[lang], key=lambda p: p[0]))
        files[lang] = f'<?xml version="1.0" encoding="utf-8"?>\n{sha_line}\n<resources>\n{body}</resources>\n'
    return Generated(files, keys, untranslated, errors)


def _render(kind: str, name: str, key: str, lang: str, web: Web, binding: Binding) -> str | None:
    if kind == "string":
        value = web.get(lang, key)
        if value is None:
            return None
        return f'    <string name="{name}">{convert(value, binding, plural_form=False)}</string>\n'
    items = []
    for q in PLURAL_CATEGORIES[lang]:
        value = web.get(lang, f"{key}_{q}")
        if value is None:
            value = web.get(lang, f"{key}_other")
        if value is None:
            return None
        try:
            items.append(f'        <item quantity="{q}">{convert(value, binding, plural_form=True)}</item>\n')
        except Unmappable as e:
            raise Unmappable(f"({key}_{q}) {e}") from None
    return f'    <plurals name="{name}">\n{"".join(items)}    </plurals>\n'


def header(root: Path) -> str:
    repo, sha = read_pin(root)
    return f"<!-- Generated by tools/web-sync/gen_strings.py from {repo}@{sha}. Do not edit; see docs/Localization.md. -->"


# --- commands --------------------------------------------------------------------------


def cmd_suggest(root: Path, web: Web, modules: list[str]) -> int:
    for name in modules:
        module = Module(root, name)
        existing = module.map or ModuleMap(root / MAP_DIR / map_file_name(name), {}, {})
        strings, plurals = dict(existing.strings), dict(existing.plurals)
        prefs = preferred_namespaces(name)

        def rank(key: str, prefs: list[str] = prefs) -> tuple[int, str]:
            ns = key.split(":", 1)[0]
            return (prefs.index(ns) if ns in prefs else len(prefs), key)

        en = web.values["en"]
        string_keys = sorted((k for k in en if k not in web.plural_keys), key=rank)
        plural_keys = sorted(web.plural_bases, key=rank)
        added = []
        for entry in sorted(module.english, key=lambda e: e.name):
            target = strings if entry.kind == "string" else plurals
            if not entry.translatable or entry.markup or entry.name in target:
                continue
            for key in string_keys if entry.kind == "string" else plural_keys:
                try:
                    binding = bind_entry(entry, web, key)
                    for lang in LANGS:
                        _render(entry.kind, entry.name, key, lang, web, binding)
                except Unmappable:
                    continue
                target[entry.name] = key
                english = entry.value if entry.kind == "string" else entry.items.get("other", "")
                added.append(f"  + {entry.label()} = {key}    {english!r}")
                break
        write_map(existing.path, strings, plurals)
        kept = len(existing.strings) + len(existing.plurals)
        print(f"{name}: {len(added)} suggested, {kept} kept -> {module.map_rel()}")
        for line in added:
            print(line)
        if added:
            print(f"  Review each pair (same meaning and context on both sides), delete the wrong ones, then run: {CMD} generate --web <locales> --module {name}")
    return 0


def cmd_generate(root: Path, web: Web, modules: list[str], explicit: bool) -> int:
    sha_line = header(root)
    status = 0
    for name in modules:
        module = Module(root, name)
        if module.map is None:
            stale = [module.generated_path(lang) for lang in LANGS if module.generated_path(lang).exists()]
            for path in stale:
                path.unlink()
                print(f"{name}: removed {module.rel(path)} (no {module.map_rel()})")
            if explicit and not stale:
                print(f"ERROR {name}: no {module.map_rel()}; run: {CMD} suggest --web <locales> --module {name}", file=sys.stderr)
                status = 1
            continue
        result = build(module, web, sha_line)
        if result.errors:
            for e in result.errors:
                print(f"ERROR {name}: {e}", file=sys.stderr)
            print(f"{name}: nothing written", file=sys.stderr)
            status = 1
            continue
        for lang in LANGS:
            path = module.generated_path(lang)
            path.parent.mkdir(parents=True, exist_ok=True)
            if not path.exists() or path.read_text(encoding="utf-8") != result.files[lang]:
                path.write_text(result.files[lang], encoding="utf-8")
        counts = ", ".join(f"{lang} {len(result.entries[lang])}" for lang in LANGS)
        print(f"{name}: generated {GENERATED_FILE} ({counts})")
    return status


def cmd_check(root: Path, web: Web, modules: list[str], explicit: bool) -> int:
    sha_line = header(root)
    problems: list[str] = []
    if not explicit:
        known = {map_file_name(m) for m in modules}
        map_dir = root / MAP_DIR
        for path in sorted(map_dir.glob("*.json")) if map_dir.is_dir() else []:
            if path.name not in known:
                problems.append(f"{path.relative_to(root)}: no module with src/main/res/values matches this map file (expected <module with / -> ->.json)")
    for name in modules:
        problems += [f"{name}: {p}" for p in check_module(Module(root, name), web, sha_line)]
    for p in problems:
        print(f"ERROR {p}", file=sys.stderr)
    if problems:
        print(f"gen_strings check: {len(problems)} problem(s) in {len(modules)} module(s)", file=sys.stderr)
        return 1
    print(f"gen_strings check: OK ({len(modules)} module(s))")
    return 0


def check_module(module: Module, web: Web, sha_line: str) -> list[str]:
    problems: list[str] = []
    for e in module.duplicates:
        problems.append(f"{module.rel(e.file)}: {e.label()} is declared twice in values/")
    generated_keys: dict[str, set[tuple[str, str]]] = {lang: set() for lang in LANGS}
    untranslated: dict[tuple[str, str], tuple[str, list[str]]] = {}
    if module.map is None:
        for lang in LANGS:
            path = module.generated_path(lang)
            if path.exists():
                problems.append(f"{module.rel(path)} exists but there is no {module.map_rel()}; delete it or restore the map")
    else:
        result = build(module, web, sha_line)
        problems += result.errors
        generated_keys, untranslated = result.entries, result.untranslated
        if not result.errors:
            for lang in LANGS:
                path = module.generated_path(lang)
                if not path.exists():
                    problems.append(f"{module.rel(path)} is missing; run: {CMD} generate --web <locales> --module {module.name}")
                elif path.read_text(encoding="utf-8") != result.files[lang]:
                    problems.append(f"{module.rel(path)} is stale (hand-edited, or the map, English or pin changed); run: {CMD} generate --web <locales> --module {module.name}")

    translated_dirs = [lang for lang in LANGS if (module.res / f"values-{lang}").is_dir()]
    if module.map is None and not translated_dirs:
        return problems  # not translated yet: nothing to compare

    english = {e.key: e for e in module.english if e.translatable}
    missing: dict[tuple[str, str], list[str]] = {}
    for lang in LANGS:
        directory = module.res / f"values-{lang}"
        seen: dict[tuple[str, str], str] = {}
        if module.map is not None:
            for key in generated_keys[lang]:
                seen[key] = GENERATED_FILE
        for e in read_values_dir(directory, skip=GENERATED_FILE):
            where = module.rel(e.file)
            if not e.translatable:
                continue
            if e.key in seen:
                problems.append(f"{where}: {e.label()} is duplicated (also in values-{lang}/{seen[e.key]})" + ("; the generated copy wins, delete this one" if seen[e.key] == GENERATED_FILE else ""))
                continue
            seen[e.key] = e.file.name
            source = english.get(e.key)
            if source is None:
                problems.append(f"{where}: {e.label()} is not in values/ (extra translation); delete it")
                continue
            if e.kind == "plurals":
                want = PLURAL_CATEGORIES[lang]
                if sorted(e.raw_items) != sorted(want):
                    problems.append(f"{where}: {e.label()} has quantities {e.raw_items}; {lang} needs exactly {want}")
                english_specs = source.specs()
                got: set[tuple[int, str]] = set()
                for q, v in e.items.items():
                    s = spec_set(v)
                    got |= s
                    if not s <= english_specs:
                        problems.append(f"{where}: {e.label()} quantity {q} has specifiers {fmt_specs(s)}, not in the English {fmt_specs(english_specs)}")
                if got != english_specs and got <= english_specs:
                    problems.append(f"{where}: {e.label()} uses specifiers {fmt_specs(got)}; the English uses {fmt_specs(english_specs)}")
            else:
                if spec_set(e.value or "") != spec_set(source.value or ""):
                    problems.append(f"{where}: {e.label()} has specifiers {fmt_specs(spec_set(e.value or ''))}; the English has {fmt_specs(spec_set(source.value or ''))}")
        for key in english:
            if key not in seen:
                missing.setdefault(key, []).append(lang)
    for key in sorted(missing, key=lambda k: (k[1], k[0])):
        langs = missing[key]
        source = english[key]
        line = (
            f"{key[0]}/{key[1]} (values/{source.file.name}) is not translated in "
            + ", ".join(f"values-{lang}" for lang in langs)
            + f"; add it to values-<lang>/{source.file.name}"
        )
        if key in untranslated:
            web_key, no_web = untranslated[key]
            line += f" (it is mapped to {web_key}, but the web has no {', '.join(no_web)} translation)"
        problems.append(line)
    return problems


# --- CLI -------------------------------------------------------------------------------


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="gen_strings.py", description=__doc__.split("\n\n")[0])
    parser.add_argument("command", choices=["suggest", "generate", "check"])
    parser.add_argument("--web", required=True, help="the web's frontend/src/i18n/locales directory (holds en/<ns>.json, de/..., ...)")
    parser.add_argument("--module", action="append", default=[], help="module path such as feature/results; repeatable; default: every module")
    parser.add_argument("--root", default=str(Path(__file__).resolve().parents[2]), help="Android repo root (default: this checkout)")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    try:
        if args.command == "suggest" and len(args.module) != 1:
            raise UsageError("suggest needs exactly one --module")
        web = Web(Path(args.web))
        known = discover_modules(root)
        explicit = bool(args.module)
        modules = [normalise_module(m) for m in args.module] if explicit else known
        for m in modules:
            if not (root / m / "src" / "main" / "res" / "values").is_dir():
                raise UsageError(f"module {m}: no {m}/src/main/res/values (known modules: {', '.join(known) or 'none'})")
        if args.command == "suggest":
            return cmd_suggest(root, web, modules)
        if args.command == "generate":
            return cmd_generate(root, web, modules, explicit)
        return cmd_check(root, web, modules, explicit)
    except UsageError as e:
        print(f"gen_strings.py {args.command}: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
