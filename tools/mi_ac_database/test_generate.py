# SPDX-License-Identifier: MIT
import copy
import json
from pathlib import Path
import tempfile
import unittest
import generate


class CompleteAcDatabaseTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory(prefix='palm-ac-source-test-')
        apk=generate.source.extract_verified_archive(generate.source.DEFAULT_ARCHIVE,Path(cls.temp.name)/'source.apk')
        cls.db=generate.source.open_database(apk)
        cls.outputs,cls.coverage=generate.build(cls.db)
        cls.index=json.loads(cls.outputs['index.json'])

    @classmethod
    def tearDownClass(cls):
        cls.db.close();cls.temp.cleanup()

    def test_complete_coverage_distinguishes_storage_and_controls(self):
        self.assertEqual(self.coverage,{'profiles':734,'brands':45,'recorded':33,'stateful':701,
                                        'controlsImplemented':36,'controlsUnavailable':698,'sourceRecordsOmitted':0})
        self.assertEqual(len(self.outputs),735)
        self.assertFalse(self.index['programsExecuted'])

    def test_every_source_row_reference_brand_and_definition_is_lossless(self):
        expected={(table,str(row['ID'])):row for table in generate.source.TABLES
                  for row in generate.source.rows(self.db,table) if int(row['DEVICE_ID'])==3}
        refs={str(row['ID']):row for row in generate.source.rows(self.db,'kk_remote_ircode_src')}
        brands={str(row['ID']):row for row in generate.source.rows(self.db,'brand')}
        seen=set()
        for p in self.index['profiles']:
            key=p['sourceTable'],p['model'];self.assertNotIn(key,seen);seen.add(key)
            row=expected[key];reference=refs[str(row['IR_ZIP_KEY'])] if int(row['INDEXED'])==1 else None
            body=self.outputs[p['asset'].removeprefix('ir/mi_ac_database/')];record=json.loads(body)
            self.assertEqual(record['sourceRow'],row)
            self.assertEqual(record['referencedSourceRow'],reference)
            self.assertEqual(record['brandSourceRow'],brands[p['brandId']])
            self.assertEqual(record['brandData'],generate.source.local.decode_json(brands[p['brandId']]['CONTENT']))
            self.assertEqual(record['definition'],generate.source.local.decode_json((reference or row)['IR_ZIP_KEY']))
            self.assertEqual(generate.source.source_hash(row,reference),p['sourceRecordSha256'])
            self.assertEqual(generate.source.local.sha256(body),p['assetSha256'])
        self.assertEqual(seen,set(expected))

    def test_script_text_is_preserved_without_evaluation(self):
        script_fields=0
        for p in self.index['profiles']:
            record=json.loads(self.outputs[p['asset'].removeprefix('ir/mi_ac_database/')])
            self.assertFalse(record['programsExecuted'])
            if '1518' in record['definition']:
                self.assertIsInstance(record['definition']['1518'],str);script_fields+=1
        self.assertEqual(script_fields,341)

    def test_only_exact_three_supported_stateful_source_ids_get_control_links(self):
        supported={p['model'] for p in self.index['profiles'] if p['control']['kind']=='panasonic216'}
        self.assertEqual(supported,generate.PANASONIC)
        raw_index=json.loads((generate.REPOSITORY/'app/src/main/assets/ir/mi_apk/index.json').read_bytes())
        raw={p['model']:p for p in raw_index['profiles'] if p['category']=='air_conditioner'}
        for p in self.index['profiles']:
            if p['control']['kind']=='recorded':
                self.assertEqual(p['control']['profile'],raw[p['model']]['id'])
                self.assertEqual(p['sourceRecordSha256'],raw[p['model']]['source']['sourceSha256'])
            elif p['control']['kind']=='unavailable':self.assertNotIn('profile',p['control'])

    def test_generation_is_deterministic(self):
        outputs,coverage=generate.build(self.db)
        self.assertEqual(outputs,self.outputs);self.assertEqual(coverage,self.coverage)

    def test_check_rejects_changed_missing_or_extra_source_assets(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory)
            for name,raw in self.outputs.items():
                p=root/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(raw)
            generate.write_or_check(self.outputs,root,check=True)
            p=root/'index.json';p.write_bytes(p.read_bytes()+b' ')
            with self.assertRaises(ValueError):generate.write_or_check(self.outputs,root,check=True)
            p.write_bytes(self.outputs['index.json']);p.unlink()
            with self.assertRaises(ValueError):generate.write_or_check(self.outputs,root,check=True)
            p.write_bytes(self.outputs['index.json']);(root/'extra.json').write_text('{}')
            with self.assertRaises(ValueError):generate.write_or_check(self.outputs,root,check=True)


if __name__=='__main__':unittest.main()
