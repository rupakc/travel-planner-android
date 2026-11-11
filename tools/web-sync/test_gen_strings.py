"""Tests for gen_strings.py (web i18next catalogues -> Android strings_web.xml).

Each test copies tools/web-sync/fixtures/strings into a temporary repo root with
its own config/web-source.properties, then drives the CLI through main(argv).
Run: python3 -m unittest tools/web-sync/test_gen_strings.py -v
"""

import contextlib
import io
import json
import shutil
import sys
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import gen_strings  # noqa: E402

FIXTURE = HERE / "fixtures" / "strings"
SHA = "0123456789abcdef0123456789abcdef01234567"
LANGS = ["de", "fr", "hi", "zh", "bn"]
CATEGORIES = {"de": ["one", "other"], "fr": ["one", "many", "other"], "hi": ["one", "other"], "zh": ["other"], "bn": ["one", "other"]}


class Repo:
    """A throwaway repo root built from the fixture."""

    def __init__(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        shutil.copytree(FIXTURE, self.root, dirs_exist_ok=True)
        (self.root / "config").mkdir(exist_ok=True)
        (self.root / "config" / "web-source.properties").write_text(f"web.repo=rupakc/travel-planner\nweb.sha={SHA}\n")
        self.web = self.root / "locales"
        self.res = self.root / "mod" / "src" / "main" / "res"
        self.map_path = self.root / "config" / "i18n" / "web-keys" / "mod.json"

    def close(self):
        self._tmp.cleanup()

    def run(self, *args):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = gen_strings.main([args[0], "--root", str(self.root), "--web", str(self.web), *args[1:]])
        return code, out.getvalue() + err.getvalue()

    def write_map(self, strings=None, plurals=None):
        self.map_path.parent.mkdir(parents=True, exist_ok=True)
        self.map_path.write_text(json.dumps({"strings": strings or {}, "plurals": plurals or {}}, indent=2) + "\n")

    def read_map(self):
        return json.loads(self.map_path.read_text())

    def generated(self, lang):
        return self.res / f"values-{lang}" / "strings_web.xml"

    def gen_text(self, lang):
        return self.generated(lang).read_text(encoding="utf-8")

    def gen_string(self, lang, name):
        """The raw (still Android-escaped) text of one generated <string>."""
        text = self.gen_text(lang)
        start = text.index(f'<string name="{name}">') + len(f'<string name="{name}">')
        return text[start:text.index("</string>", start)]

    def gen_plural(self, lang, name):
        root = ET.parse(self.generated(lang)).getroot()
        for el in root.findall("plurals"):
            if el.get("name") == name:
                return {item.get("quantity"): item.text for item in el.findall("item")}
        return None

    def set_english(self, name, raw):
        path = self.res / "values" / "strings.xml"
        text = path.read_text(encoding="utf-8")
        start = text.index(f'<string name="{name}">') + len(f'<string name="{name}">')
        end = text.index("</string>", start)
        path.write_text(text[:start] + raw + text[end:], encoding="utf-8")

    def fill_hand_translations(self):
        """Write values-<lang>/strings.xml with every key the generated files lack (English copy)."""
        english = ET.parse(self.res / "values" / "strings.xml").getroot()
        for lang in LANGS:
            have = set()
            if self.generated(lang).exists():
                for el in ET.parse(self.generated(lang)).getroot():
                    have.add((el.tag, el.get("name")))
            lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
            for el in english:
                if el.get("translatable") == "false" or (el.tag, el.get("name")) in have:
                    continue
                if el.tag == "string":
                    copy = ET.fromstring(ET.tostring(el))
                    copy.tail = None
                    lines.append("    " + ET.tostring(copy, encoding="unicode"))
                else:
                    other = el.find("item[@quantity='other']").text
                    lines.append(f'    <plurals name="{el.get("name")}">')
                    lines += [f'        <item quantity="{q}">{other}</item>' for q in CATEGORIES[lang]]
                    lines.append("    </plurals>")
            lines.append("</resources>")
            (self.res / f"values-{lang}").mkdir(exist_ok=True)
            (self.res / f"values-{lang}" / "strings.xml").write_text("\n".join(lines) + "\n", encoding="utf-8")


class GenStringsTest(unittest.TestCase):
    def setUp(self):
        self.repo = Repo()
        self.addCleanup(self.repo.close)

    def suggest_and_generate(self):
        code, out = self.repo.run("suggest", "--module", "mod")
        self.assertEqual(code, 0, out)
        code, out = self.repo.run("generate", "--module", "mod")
        self.assertEqual(code, 0, out)

    # --- matching -----------------------------------------------------------

    def test_exact_match_maps(self):
        self.suggest_and_generate()
        self.assertEqual(self.repo.read_map()["strings"]["mod_save"], "x:exact")
        self.assertEqual(self.repo.read_map()["strings"]["mod_apostrophe"], "x:apostrophe")
        self.assertEqual(self.repo.gen_string("de", "mod_save"), "Reise speichern")

    def test_brand_literal_maps_to_placeholder(self):
        self.suggest_and_generate()
        self.assertEqual(self.repo.read_map()["strings"]["mod_brand"], "x:brand")
        self.assertEqual(self.repo.gen_string("de", "mod_brand"), "Hilf, %1$s zu verbessern")
        self.assertEqual(self.repo.gen_string("zh", "mod_brand"), "帮助改进 %1$s")

    def test_placeholder_order_mapping(self):
        self.suggest_and_generate()
        self.assertEqual(self.repo.read_map()["strings"]["mod_order"], "x:order")
        self.assertEqual(self.repo.gen_string("de", "mod_order"), "%2$s von %1$s")
        self.assertEqual(self.repo.gen_string("fr", "mod_order"), "%1$s sur %2$s")

    def test_non_positional_specifier_is_written_positional(self):
        self.suggest_and_generate()
        self.assertEqual(self.repo.gen_string("de", "mod_hello"), "Hallo, %1$s")

    def test_escapes_apostrophe_quote_ampersand_lt_leading_at(self):
        self.suggest_and_generate()
        self.assertEqual(self.repo.gen_string("fr", "mod_esc"), "l\\'itinéraire &amp; &lt;plan>")
        self.assertEqual(self.repo.gen_string("fr", "mod_handle"), "\\@votre pseudo")
        self.assertEqual(self.repo.gen_string("fr", "mod_apostrophe"), 'Impossible de charger \\"ça\\"')
        # The written file is well-formed XML that decodes back to the translation.
        ET.parse(self.repo.generated("fr"))

    def test_percent_becomes_double_only_with_args(self):
        self.suggest_and_generate()
        self.assertEqual(self.repo.gen_string("fr", "mod_pct_plain"), "100 % gratuit")
        self.assertEqual(self.repo.gen_string("fr", "mod_pct_args"), "%1$d %% de réduction")
        self.assertEqual(self.repo.gen_string("zh", "mod_pct_args"), "%1$d%% 折扣")

    def test_plural_categories_per_language(self):
        self.suggest_and_generate()
        self.assertEqual(self.repo.read_map()["plurals"]["mod_items"], "x:items")
        self.assertEqual(self.repo.gen_plural("fr", "mod_items"), {"one": "%1$d élément", "many": "%1$d d\\'éléments", "other": "%1$d éléments"})
        self.assertEqual(self.repo.gen_plural("zh", "mod_items"), {"other": "%1$d 项"})
        self.assertEqual(list(self.repo.gen_plural("de", "mod_items")), ["one", "other"])

    def test_plural_form_without_count_keeps_other_placeholders(self):
        self.suggest_and_generate()
        self.assertEqual(self.repo.gen_plural("fr", "mod_stops")["one"], "Une escale à %2$s")
        self.assertEqual(self.repo.gen_plural("zh", "mod_stops"), {"other": "在 %2$s 停靠 %1$d 次"})

    def test_markup_is_unmappable(self):
        self.suggest_and_generate()
        self.assertNotIn("mod_markup", self.repo.read_map()["strings"])
        self.repo.write_map(strings={"mod_markup": "x:markup"})
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("mod_markup", out)
        self.assertIn("markup", out)

    def test_translatable_false_is_skipped(self):
        self.suggest_and_generate()
        self.assertNotIn("mod_brand_name", self.repo.read_map()["strings"])
        self.assertNotIn("mod_brand_name", self.repo.gen_text("de"))

    # --- check ---------------------------------------------------------------

    def test_check_passes_without_map_files(self):
        code, out = self.repo.run("check")
        self.assertEqual(code, 0, out)

    def test_check_passes_when_complete(self):
        self.suggest_and_generate()
        self.repo.fill_hand_translations()
        code, out = self.repo.run("check")
        self.assertEqual(code, 0, out)

    def test_check_detects_english_drift(self):
        self.suggest_and_generate()
        self.repo.fill_hand_translations()
        self.repo.set_english("mod_save", "Save this trip")
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("mod_save", out)
        self.assertIn("x:exact", out)

    def test_check_detects_web_key_missing(self):
        self.suggest_and_generate()
        self.repo.fill_hand_translations()
        self.repo.write_map(strings={**self.repo.read_map()["strings"], "mod_only": "x:nope"}, plurals=self.repo.read_map()["plurals"])
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("x:nope", out)

    def test_check_detects_missing_hand_translation(self):
        self.suggest_and_generate()
        self.repo.fill_hand_translations()
        path = self.repo.res / "values-fr" / "strings.xml"
        text = path.read_text(encoding="utf-8")
        path.write_text("\n".join(line for line in text.splitlines() if "mod_only" not in line) + "\n", encoding="utf-8")
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("mod_only", out)
        self.assertIn("values-fr", out)

    def test_check_detects_extra_and_duplicate_keys(self):
        self.suggest_and_generate()
        self.repo.fill_hand_translations()
        path = self.repo.res / "values-de" / "strings.xml"
        text = path.read_text(encoding="utf-8").replace(
            "</resources>", '    <string name="mod_gone">Weg</string>\n    <string name="mod_save">Doppelt</string>\n</resources>'
        )
        path.write_text(text, encoding="utf-8")
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("mod_gone", out)
        self.assertIn("mod_save", out)

    def test_check_detects_wrong_plural_categories(self):
        self.repo.write_map()
        self.repo.run("generate", "--module", "mod")
        self.repo.fill_hand_translations()
        path = self.repo.res / "values-zh" / "strings.xml"
        text = path.read_text(encoding="utf-8").replace('<item quantity="other">%1$d items', '<item quantity="one">%1$d items</item>\n        <item quantity="other">%1$d items', 1)
        path.write_text(text, encoding="utf-8")
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("mod_items", out)
        self.assertIn("zh", out)

    def test_check_detects_hand_specifier_mismatch(self):
        self.suggest_and_generate()
        self.repo.fill_hand_translations()
        path = self.repo.res / "values-hi" / "strings.xml"
        text = path.read_text(encoding="utf-8").replace("Only on Android, %1$s", "Only on Android, %1$d")
        path.write_text(text, encoding="utf-8")
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("mod_only", out)
        self.assertIn("values-hi", out)

    def test_check_detects_stale_and_missing_generated_file(self):
        self.suggest_and_generate()
        self.repo.fill_hand_translations()
        self.repo.generated("de").write_text(self.repo.gen_text("de").replace("Reise speichern", "Hand edit"), encoding="utf-8")
        self.repo.generated("bn").unlink()
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("values-de/strings_web.xml", out)
        self.assertIn("values-bn/strings_web.xml", out)
        self.assertIn("generate", out)

    def test_check_detects_generated_file_without_map(self):
        self.suggest_and_generate()
        self.repo.map_path.unlink()
        code, out = self.repo.run("check")
        self.assertEqual(code, 1)
        self.assertIn("strings_web.xml", out)

    # --- generate / suggest ----------------------------------------------------

    def test_generate_is_idempotent(self):
        self.suggest_and_generate()
        first = {lang: self.repo.generated(lang).read_bytes() for lang in LANGS}
        code, out = self.repo.run("generate", "--module", "mod")
        self.assertEqual(code, 0, out)
        self.assertEqual(first, {lang: self.repo.generated(lang).read_bytes() for lang in LANGS})
        text = first["de"].decode("utf-8")
        self.assertIn(f"rupakc/travel-planner@{SHA}. Do not edit; see docs/Localization.md. -->", text)
        names = [el.get("name") for el in ET.parse(self.repo.generated("de")).getroot()]
        self.assertEqual(names, sorted(names))

    def test_generate_omits_strings_the_web_has_not_translated(self):
        self.suggest_and_generate()
        self.assertNotIn("mod_save", self.repo.gen_text("hi"))
        code, out = self.repo.run("check", "--module", "mod")
        self.assertEqual(code, 1)
        self.assertIn("mod_save", out)
        self.assertIn("web has no hi", out)

    def test_suggest_prefers_module_namespace(self):
        code, out = self.repo.run("suggest", "--module", "mod")
        self.assertEqual(code, 0, out)
        # "a:dup" sorts first, but "misc" is preferred for this module.
        self.assertEqual(self.repo.read_map()["strings"]["mod_open"], "misc:map.open")

    def test_suggest_keeps_existing_entries(self):
        self.repo.write_map(strings={"mod_open": "a:dup"})
        code, out = self.repo.run("suggest", "--module", "mod")
        self.assertEqual(code, 0, out)
        self.assertEqual(self.repo.read_map()["strings"]["mod_open"], "a:dup")
        self.assertEqual(self.repo.read_map()["strings"]["mod_save"], "x:exact")

    def test_namespace_preference_table(self):
        self.assertEqual(gen_strings.preferred_namespaces("feature/results"), ["results", "sections"])
        self.assertEqual(gen_strings.preferred_namespaces("core/ui"), ["sections", "itinerary", "privacy", "common"])
        self.assertEqual(gen_strings.preferred_namespaces("feature/nearby"), ["common", "misc"])

    # --- CLI robustness ----------------------------------------------------------

    def test_bad_web_dir_is_reported(self):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = gen_strings.main(["check", "--root", str(self.repo.root), "--web", str(self.repo.root / "nope")])
        self.assertEqual(code, 2)
        self.assertIn("en/", out.getvalue() + err.getvalue())

    def test_unknown_module_is_reported(self):
        code, out = self.repo.run("check", "--module", "feature/missing")
        self.assertEqual(code, 2)
        self.assertIn("feature/missing", out)

    def test_module_spelling_is_normalised(self):
        self.assertEqual(gen_strings.normalise_module(":feature:results"), "feature/results")
        self.assertEqual(gen_strings.normalise_module("feature/results/"), "feature/results")
        self.assertEqual(gen_strings.map_file_name("feature/results"), "feature-results.json")


if __name__ == "__main__":
    unittest.main()
