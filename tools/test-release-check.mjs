import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const contract=JSON.parse(fs.readFileSync(path.join(root,'tools/release-contract.json'),'utf8'));
test('release guard rejects removal of implementation, tests, or permission',()=>{
  const fixture=fs.mkdtempSync(path.join(os.tmpdir(),'riposte-release-guard-'));
  try {
    for(const file of new Set(['tools/release-check.mjs','tools/release-contract.json',...Object.keys(contract.required),...contract.tests.map(t=>t.file)])){
      fs.mkdirSync(path.dirname(path.join(fixture,file)),{recursive:true});
      fs.copyFileSync(path.join(root,file),path.join(fixture,file));
    }
    const run=()=>spawnSync(process.execPath,[path.join(fixture,'tools/release-check.mjs'),'source'],{encoding:'utf8'});
    assert.equal(run().status,0);
    const source='core/src/main/kotlin/dev/riposte/core/ScreenTime.kt';
    const testFile='core/src/test/kotlin/dev/riposte/core/ScreenTimeTest.kt';
    fs.unlinkSync(path.join(fixture,source));fs.unlinkSync(path.join(fixture,testFile));
    assert.match(run().stderr,/Required feature file missing/);
    fs.copyFileSync(path.join(root,source),path.join(fixture,source));
    fs.copyFileSync(path.join(root,testFile),path.join(fixture,testFile));
    fs.writeFileSync(path.join(fixture,testFile),'package dev.riposte.core');
    assert.match(run().stderr,/Required test missing/);
    fs.copyFileSync(path.join(root,testFile),path.join(fixture,testFile));
    const manifest=path.join(fixture,'app/src/main/AndroidManifest.xml');
    fs.writeFileSync(manifest,fs.readFileSync(manifest,'utf8').replace('android.permission.PACKAGE_USAGE_STATS','removed'));
    assert.match(run().stderr,/Required feature wiring missing/);
  } finally { fs.rmSync(fixture,{recursive:true,force:true}); }
});
