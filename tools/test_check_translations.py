import importlib.util
import tempfile
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("ct", Path(__file__).with_name("check-translations.py"))
ct = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ct)


def write(res, folder, body):
    d = Path(res) / folder
    d.mkdir(parents=True)
    (d / "strings.xml").write_text(f"<resources>{body}</resources>", encoding="utf-8")


BASE = '<string name="a">Hi %1$s, %2$d</string><string name="b" translatable="false">x</string><plurals name="p"><item quantity="one">%d day</item><item quantity="other">%d days</item></plurals><string-array name="r"><item>1</item><item>2</item></string-array>'


class Checks(unittest.TestCase):
    def run_with(self, other):
        with tempfile.TemporaryDirectory() as res:
            write(res, "values", BASE)
            write(res, "values-it", other)
            return ct.check(res)[1]

    def test_complete_translation_passes(self):
        self.assertEqual([], self.run_with('<string name="a">Ciao %1$s, %2$d</string><plurals name="p"><item quantity="one">un giorno</item><item quantity="other">%d giorni</item></plurals><string-array name="r"><item>1</item><item>2</item></string-array>'))

    def test_missing_extra_and_placeholders_are_found(self):
        found = "\n".join(self.run_with('<string name="a">Ciao %s</string><string name="z">z</string><plurals name="p"><item quantity="other">%s</item></plurals><string-array name="r"><item>1</item></string-array>'))
        for part in ("a has other placeholders", "string not in the default ones: z", "p[other]", "array r has 1 items"):
            self.assertIn(part, found)

    def test_real_resources_pass(self):
        self.assertEqual([], ct.check(Path(__file__).resolve().parent.parent / "app/src/main/res")[1])


if __name__ == "__main__":
    unittest.main()
