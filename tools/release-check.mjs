// Explicit feature/test inventory: deleting implementation AND tests must not pass unnoticed.
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const read=p=>{
  const bytes=fs.readFileSync(path.resolve(root,p));
  return bytes.toString(bytes[0]===0xff&&bytes[1]===0xfe?'utf16le':'utf8').replace(/^\uFEFF/,'');
};
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const fail=message=>{throw new Error(message);};
const contract=JSON.parse(read('tools/release-contract.json'));
function walk(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]);}
function fingerprint(){
  const files=['build.gradle.kts','settings.gradle.kts','gradle.properties','tools/release-contract.json'];
  for(const module of ['core','fencing','life','insights','imports','health','app']) {
    files.push(`${module}/build.gradle.kts`);
    files.push(...walk(path.join(root,module,'src')).map(p=>path.relative(root,p).replaceAll('\\','/')));
  }
  return crypto.createHash('sha256').update(files.sort().map(p=>p+':'+hash(path.join(root,p))).join('\n')).digest('hex');
}
for(const [file,tokens] of Object.entries(contract.required)){
  if(!fs.existsSync(path.join(root,file)))fail(`Required feature file missing: ${file}`);
  for(const token of tokens)if(!read(file).includes(token))fail(`Required feature wiring missing: ${file}: ${token}`);
}
for(const test of contract.tests)if(!read(test.file).includes(`fun ${test.name}(`))fail(`Required test missing: ${test.class}.${test.name}`);
const mode=process.argv[2]||'source';
const build=()=>read('.tools/build-root.txt').trim();
const apk=type=>path.join(build(),`app/outputs/apk/${type}/app-${type}.apk`);
if(mode==='begin-build')fs.writeFileSync(path.join(root,'.tools/build-start.json'),JSON.stringify({fingerprint:fingerprint()}));
if(mode==='record-build'){
  if(JSON.parse(read('.tools/build-start.json')).fingerprint!==fingerprint())fail('Source changed during the build. Rebuild before packaging.');
  fs.writeFileSync(path.join(root,'.tools/build-receipt.json'),JSON.stringify({fingerprint:fingerprint(),tasks:process.argv.slice(3),release:fs.existsSync(apk('release'))?hash(apk('release')):null,debug:fs.existsSync(apk('debug'))?hash(apk('debug')):null},null,2));
}
if(mode==='device'||mode==='release'){
  const log=read('.tools/device-tests.log');
  if(!/OK \(\d+ tests?\)/.test(log))fail('Device suite did not pass.');
  const passed=new Set();
  let cls='',name='';
  for(const line of log.split(/\r?\n/)){
    if(line.startsWith('INSTRUMENTATION_STATUS: class='))cls=line.split('class=')[1].trim();
    if(line.startsWith('INSTRUMENTATION_STATUS: test='))name=line.split('test=')[1].trim();
    if(line.trim()==='INSTRUMENTATION_STATUS_CODE: 0')passed.add(cls+'.'+name);
  }
  for(const t of contract.tests.filter(t=>t.kind==='androidTest'))if(!passed.has(t.class+'.'+t.name))fail(`Required device test did not pass: ${t.class}.${t.name}`);
  if(mode==='device')fs.writeFileSync(path.join(root,'.tools/device-receipt.json'),JSON.stringify({fingerprint:fingerprint(),debug:hash(apk('debug')),log:hash(path.join(root,'.tools/device-tests.log'))},null,2));
}
if(mode==='release'){
  const receipt=JSON.parse(read('.tools/build-receipt.json'));
  const device=JSON.parse(read('.tools/device-receipt.json'));
  if(receipt.fingerprint!==fingerprint()||device.fingerprint!==fingerprint())fail('Source changed since build/device tests. Rebuild and rerun tests.');
  if(receipt.release!==hash(apk('release'))||receipt.debug!==hash(apk('debug'))||device.debug!==hash(apk('debug')))fail('APK changed since validation.');
  if(device.log!==hash(path.join(root,'.tools/device-tests.log')))fail('Device log changed since validation.');
  for(const task of [':core:test',':fencing:test',':life:test',':insights:test',':imports:testDebugUnitTest',':app:testDebugUnitTest',':app:lintDebug',':app:assembleRelease'])if(!receipt.tasks.includes(task))fail(`Required build task not recorded: ${task}`);
  for(const test of contract.tests.filter(t=>t.kind==='test')){
    const dir=path.join(build(),test.module,'test-results',test.module==='app'||test.module==='imports'?'testDebugUnitTest':'test');
    const xml=fs.readFileSync(path.join(dir,`TEST-${test.class}.xml`),'utf8');
    if(!xml.includes(`name="${test.name}"`)||/<(?:failure|error|skipped)\b/.test(xml))fail(`Required unit test did not pass: ${test.class}.${test.name}`);
  }
  const metadata=JSON.parse(fs.readFileSync(path.join(build(),'app/outputs/apk/release/output-metadata.json'),'utf8'));
  if(metadata.applicationId!==contract.package||metadata.elements[0].versionCode<contract.minimumVersionCode)fail('Wrong application ID or reused old version code.');
  const previous=path.join(root,'delivery/release-manifest.json');
  if(fs.existsSync(previous)){
    const old=JSON.parse(read(previous));
    if(metadata.elements[0].versionCode<old.versionCode||(metadata.elements[0].versionCode===old.versionCode&&old.apkSha256!==hash(apk('release'))))fail('Different APK requires a newer version code.');
  }
}
console.log(`Release check passed (${mode}): ${contract.tests.length} required tests and feature wiring present.`);
