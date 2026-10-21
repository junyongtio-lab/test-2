"""Checks source priority, complete associations and reproducible TV metadata."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest

import generate_order as generate


class MatchingOrderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.raw, cls.manifest = generate.build()
        cls.order = json.loads(cls.raw)
        cls.index = json.loads(generate.BASE_INDEX.read_bytes())

    def test_actual_daewoo_candidates_follow_tree_then_regional_other_priority(self):
        group = next(g for g in self.order["groups"] if g["brand"] == "Daewoo")
        self.assertEqual([p["modelId"] for p in group["profiles"]], [
            "xm_1_3399", "xm_1_3390", "xm_1_4267", "xm_1_4703",
            "xm_1_4265", "xm_1_633", "xm_1_4853", "xm_1_4723",
            "xm_1_3147", "xm_1_1697", "xm_1_3640",
            "kk_1_195_3001", "kk_1_195_2995",
        ])
        # The old first choice is an 'others' capture with lower source priority.
        self.assertEqual(group["profiles"][-1]["id"], "mi_f7742cf932264c8ed91c27e3")
        self.assertEqual(group["sourceBrandFile"], "1_TV/Daewoo_195.json")
        self.assertEqual(group["sourceCountry"], "IN")

    def test_every_accepted_tv_association_is_retained_exactly_once(self):
        accepted = {p["id"]: p for p in self.index["profiles"] if p["category"] == "tv"}
        seen = set()
        for group in self.order["groups"]:
            self.assertEqual(group["category"], "tv")
            for row in group["profiles"]:
                self.assertNotIn(row["id"], seen)
                profile = accepted[row["id"]]
                self.assertEqual(profile["brand"], group["brand"])
                self.assertEqual(profile["source"]["brandFile"], group["sourceBrandFile"])
                self.assertEqual(profile["source"]["brandSha256"], group["sourceBrandSha256"])
                self.assertEqual(profile["model"], row["modelId"])
                seen.add(row["id"])
        self.assertEqual(seen, set(accepted))
        self.assertEqual((len(self.order["groups"]), len(seen)), (1023, 2810))

    def test_cycle_missing_edge_and_detached_nodes_preserve_source_references(self):
        data = {"tree": {"root_index": 0, "nodes": [
            {"index": 3, "keysetids": ["detached", "child"]},
            {"index": 0, "keysetids": ["root"], "children_index": [2, 99]},
            {"index": 2, "keysetids": ["child"], "children_index": [0]},
            {"keysetids": ["unindexed"]},
        ]}, "others": [{"_id": "other", "order": 1}]}
        self.assertEqual(generate.model_order(data), ["root", "child", "detached", "unindexed", "other"])

    def test_regional_priority_then_declared_order_then_stable_ties(self):
        data = {"others": [
            {"_id": "regional", "order": 0, "locales": {"MY": 8}},
            {"_id": "declared", "order": 2},
            {"_id": "regional-first", "order": 10, "locales": {"MY": 1}},
            {"_id": "tie", "order": 2},
            {"_id": "unknown"},
        ]}
        self.assertEqual(generate.model_order(data), ["regional-first", "declared", "tie", "regional", "unknown"])

    def test_original_index_stays_pinned_and_output_contains_no_signals(self):
        self.assertEqual(hashlib.sha256(generate.BASE_INDEX.read_bytes()).hexdigest(), generate.BASE_INDEX_SHA256)
        for group in self.order["groups"]:
            for row in group["profiles"]:
                self.assertEqual(set(row), {"id", "modelId"})
        self.assertNotIn(b'"commands"', self.raw)
        self.assertNotIn(b'"pattern"', self.raw)

    def test_changed_archive_is_rejected_before_reading_members(self):
        with tempfile.TemporaryDirectory() as temporary:
            archive = Path(temporary) / "changed.tar.xz"
            content = bytearray(generate.ARCHIVE.read_bytes())
            content[20] ^= 1
            archive.write_bytes(content)
            with self.assertRaisesRegex(ValueError, "pinned GitHub release"):
                generate.build(archive=archive)

    def test_changed_index_cannot_inject_a_new_profile_or_order(self):
        with tempfile.TemporaryDirectory() as temporary:
            index = Path(temporary) / "index.json"
            data = json.loads(generate.BASE_INDEX.read_bytes())
            data["profiles"].reverse()
            index.write_text(json.dumps(data))
            with self.assertRaisesRegex(ValueError, "pinned accepted catalogue"):
                generate.build(index_path=index)

    def test_deterministic_output_and_check_rejects_changed_metadata(self):
        raw, manifest = generate.build()
        self.assertEqual((raw, manifest), (self.raw, self.manifest))
        with tempfile.TemporaryDirectory() as temporary:
            output, meta = Path(temporary) / "order.json", Path(temporary) / "manifest.json"
            generate.write_or_check(output, meta)
            generate.write_or_check(output, meta, check=True)
            output.write_bytes(output.read_bytes() + b" ")
            with self.assertRaisesRegex(ValueError, "metadata differs"):
                generate.write_or_check(output, meta, check=True)


if __name__ == "__main__":
    unittest.main()
