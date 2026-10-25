"""Convert life_analysis output to an auditable, repeatable Riposte import package.
No third-party dependencies. Source files are read only; output contains private data.
"""
import argparse, csv, hashlib, json, math, zipfile
from pathlib import Path
from datetime import datetime, timezone

def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('source',type=Path)
    parser.add_argument('output',type=Path)
    args=parser.parse_args()
    source=args.source/'data'
    now=datetime.now(timezone.utc).isoformat().replace('+00:00','Z')
    def rows(name):
        with (source/(name+'.csv')).open(encoding='utf-8-sig',newline='') as f: return list(csv.DictReader(f))
    def ident(text): return hashlib.sha256(text.encode()).hexdigest()[:32]
    def number(v):
        try:
            n=float(v)
            return n if math.isfinite(n) else None
        except (TypeError,ValueError): return None
    def stamp(day='',time=''):
        # Date-only exports do not acquire fabricated occurrence timestamps.
        return dict(occurredAt='',createdAt=now,zone='Europe/Rome',day=day,dayOverride=False)
    manifests=[(p,p.read_bytes()) for p in sorted(source.glob('*.csv'))]
    totalhash=hashlib.sha256(b''.join(hashlib.sha256(b).digest() for p,b in manifests)).hexdigest()
    batch='history-'+totalhash[:24]
    attachments=[]
    by_table={}
    for path,data in manifests:
        h=hashlib.sha256(data).hexdigest();by_table[path.stem]=h
        attachments.append(dict(id=h,name=path.name,mime='text/csv',file=h,batchId=batch))
    def origin(table,key,raw=None,uncertain=False):
        return dict(source='Historical '+table,externalId=str(key),batchId=batch,precision='date',dateBasis='calendar',raw=json.dumps(raw,ensure_ascii=False) if raw is not None else '',uncertain=uncertain,attachmentId=by_table.get(table,''),inferred=bool(raw and str(raw.get('date_inferred','')).lower()=='true'))
    people={'me':dict(id='me',name='Me',isMe=True)}
    def person(name,key):
        clean=' '.join(name.split())
        pid='fencer-'+ident(clean.casefold() if clean else 'unknown:'+key)
        people.setdefault(pid,dict(id=pid,name=clean or 'Unrecorded opponent',notes='' if clean else 'Identity missing in the original record.',identityKnown=bool(clean)))
        return pid
    sessions={};bouts=[]
    raw_sessions={r['session_id']:r for r in rows('fencing_sessions')}
    for r in rows('fencing_bouts'):
        sid='session-'+ident(r['session_id']);key=r['bout_id'];date=r['date'];ambiguous=r['date_ambiguous'].lower()=='true'
        if sid not in sessions:
            sr=raw_sessions.get(r['session_id'],{})
            context=r.get('context','').strip()
            sessions[sid]=dict(id=sid,name=context or 'Historical fencing',stamp=stamp(date),context='Competition' if context.lower()=='competition' else context or 'Unknown',weapon='Foil',target=15,notes='Original session: '+r['session_id'],origin=origin('fencing_sessions',r['session_id'],sr,ambiguous))
        fmt=r.get('format','');target=int(fmt.split('-')[0]) if fmt in ('5-touch','10-touch','15-touch') else None
        rating=number(r.get('subjective_performance'))
        bouts.append(dict(id='bout-'+ident(key),sessionId=sid,a='me',b=person(r['opponent_raw'],key),scoreA=int(r['my_score']),scoreB=int(r['opponent_score']),target=target,
            stamp=stamp(date),outcome='auto',detailsA=dict(sword=r.get('sword',''),glove=r.get('glove',''),rating=rating,ratingScale='historical-bout-execution-raw'),notes=r.get('notes',''),origin=origin('fencing_bouts',key,r,ambiguous)))
    metrics={};observations=[]
    def metric(key,name,kind='Number',unit='',scale='historical-raw',symptom=False):
        mid='legacy:'+key
        metrics.setdefault(mid,dict(id=mid,name=name,kind=kind,unit=unit,min=-1e12,max=1e12,scale=scale,hidden=True,order=1000+len(metrics),symptom=symptom))
        return mid
    wanted={
        'unified_mood':('Mood · history','rating-1-5',''), 'unified_stress':('Stress · history','rating-1-5',''),
        'scale_energy':('Energy · history','historical-energy',''), 'scale_productivity':('Productivity · history','historical-productivity',''),
        'scale_sleep_quality':('Sleep quality · history','historical-sleep-quality',''), 'fit_steps':('Steps · history','steps','steps'),
        'fit_sleep_hours':('Sleep duration · history','hours','hours'), 'scale_socializing':('Social contact · history','minutes','min'),
        'scale_caffeine':('Caffeine · history','historical-caffeine',''), 'scale_social_media':('Social media · history','minutes','min'),
        'scale_time_outside_the_house':('Time outside · history','minutes','min'), 'tag_amici':('Friends · history','boolean',''),
        'tag_esercizio':('Exercise · history','boolean',''), 'explicit_sick':('Explicit illness · history','boolean',''),
    }
    daily=rows('unified_daily')
    for key in daily[0]:
        if key.startswith('unified_symptom_') or key.startswith('unified_tag_'):
            wanted[key]=(key.replace('unified_symptom_','').replace('unified_tag_','').replace('_',' ').capitalize()+' · history','boolean','')
    for r in daily:
        for key,(name,scale,unit) in wanted.items():
            val=number(r.get(key))
            if val is None: continue
            mid=metric(key,name,unit=unit,scale=scale)
            observations.append(dict(id='history-'+ident('daily:'+r['date']+':'+key),metric=mid,stamp=stamp(r['date']),value=val,scope='aggregate',unit=unit,scale=scale,origin=origin('unified_daily',r['date']+':'+key)))
    # Detailed source events remain a separate archive; they never add to daily aggregates.
    detail_tables=['daylio_entries','mysymptoms_events','nutrilio_events','fitnotes_sets','food_events','symptom_intensities','fencing_day_info','fencing_undated_rows']
    for table in detail_tables:
        mid=metric(table,table.replace('_',' ').capitalize()+' · source records','Text',scale='source-text')
        for i,r in enumerate(rows(table)):
            day=r.get('date','')
            if day and len(day)!=10: day=''
            text=json.dumps({k:v for k,v in r.items() if v!=''},ensure_ascii=False)
            observations.append(dict(id='history-'+ident(table+':'+str(i)),metric=mid,stamp=stamp(day),text=text,scope='source',scale='source-text',origin=origin(table,i,uncertain=not bool(day))))
    snapshot=dict(version=1,fencers=list(people.values()),sessions=list(sessions.values()),bouts=bouts,metrics=list(metrics.values()),observations=observations,
        batches=[dict(id=batch,name='Life analysis history',hash=totalhash,createdAt=now,count=len(bouts)+len(observations))],attachments=attachments)
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(args.output,'w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('snapshot.json',json.dumps(snapshot,ensure_ascii=False,separators=(',',':')))
        for path,data in manifests: z.writestr('sources/'+by_table[path.stem],data)
    report=dict(fencers=len(people),sessions=len(sessions),bouts=len(bouts),ambiguous_bouts=sum(b['origin']['uncertain'] for b in bouts),life_records=len(observations),metrics=len(metrics),attachments=len(attachments),bytes=args.output.stat().st_size)
    print(json.dumps(report,indent=2))
    args.output.with_suffix('.validation.json').write_text(json.dumps(report,indent=2),encoding='utf-8')

if __name__=='__main__': main()
