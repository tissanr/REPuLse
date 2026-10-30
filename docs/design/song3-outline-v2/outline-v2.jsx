const {useState,useEffect,useRef}=React;
const V={sel:'#1f2d4e',fx:'#6f9bd8',insp:'#0e1526'};
const KC2={...KIND_C,master:V.fx};
const PM={
 'drums.amp':{l:'amp',min:0,max:1,v:.85},
 'drums.filter.freq':{l:'freq',min:20,max:20000,v:2400,log:true},
 'drums.filter.q':{l:'q',min:0,max:1,v:.3},
 'bass.amp':{l:'amp',min:0,max:1,v:.7},
 'bass.decay':{l:'decay',min:0,max:2,v:.25},
 'keys.amp':{l:'amp',min:0,max:1,v:.4},
 'keys.pan':{l:'pan',min:-1,max:1,v:-.3},
 'pad.amp':{l:'amp',min:0,max:1,v:.35},
 'pad.reverb.mix':{l:'mix',min:0,max:1,v:.8},
 'master.delay.time':{l:'time',min:0,max:2,v:.375},
 'master.delay.fb':{l:'fb',min:0,max:1,v:.4},
 'master.reverb.size':{l:'size',min:0,max:1,v:.8},
 'master.reverb.mix':{l:'mix',min:0,max:1,v:.3}};
const INL0=['bass.amp','drums.filter.freq','pad.reverb.mix'];
const TPL=`(def kick  (seq :bd :_ :bd :_ :bd :_ :bd :_))
(def snare (seq :_ :_ :sd :_ :_ :_ :sd :_))
(def hats  (fast 2 (seq :hh :hh :oh :hh)))

(defn humanize [p]
  (->> p (nudge (rand -0.01 0.01))
         (amp (rand 0.8 1.0))))

(defsynth pad [freq]
  (->> (saw freq) (detune 0.12)
       (lpf 1400) (attack 0.6)))
;; ── tracks

(track :drums
  (stack kick snare
         (humanize hats))
  (amp {drums.amp})
  (fx :filter :freq {drums.filter.freq} :q {drums.filter.q}))

(track :bass
  (->> (scale :minor :e1
         (seq 0 :_ 0 3 :_ 5 :_ 7))
       (synth :saw)
       (amp {bass.amp})
       (decay {bass.decay})))

(track :keys
  (->> (chord :minor7 :e3)
       (slow 2)
       (amp {keys.amp})
       (pan {keys.pan})))

(track :pad
  (->> (pad :e2)
       (slow 4)
       (amp {pad.amp})
       (fx :reverb :mix {pad.reverb.mix})))

;; ── arrangement
(song
  (section :intro  4 [:pad :keys])
  (section :verse  8 [:drums :bass :pad])
  (section :chorus 8 [:drums :bass :keys :pad])
  (section :verse  8 [:drums :bass :pad])
  (section :outro  4 [:pad]))

;; ── master
(master
  (fx :delay  :time {master.delay.time} :fb {master.delay.fb})
  (fx :reverb :size {master.reverb.size} :mix {master.reverb.mix}))`;
const TL=TPL.split('\n');
const ENT=(()=>{const E=[];TL.forEach((ln,i)=>{const n=i+1;let m;
 if(m=ln.match(/^\(def (\S+)/))E.push({id:'def:'+m[1],k:'def',n:m[1],l:n});
 else if(m=ln.match(/^\(defn (\S+)/))E.push({id:'defn:'+m[1],k:'defn',n:m[1],l:n});
 else if(m=ln.match(/^\(defsynth (\S+)/))E.push({id:'synth:'+m[1],k:'synth',n:m[1],l:n});
 else if(m=ln.match(/^\(track :(\S+)/))E.push({id:'track:'+m[1],k:'track',n:':'+m[1],t:m[1],l:n});
 else if(ln.startsWith('(song'))E.push({id:'song',k:'song',n:'',l:n,ch:[]});
 else if(ln.startsWith('(master'))E.push({id:'master',k:'master',n:'',l:n,ch:[]});
 else if(m=ln.match(/^  \(section :(\S+)\s+(\d+) \[(.*?)\]/)){const s=E[E.length-1];if(s&&s.ch)s.ch.push({id:'sec:'+s.ch.length,k:'sec',i:s.ch.length,n:':'+m[1],b:+m[2],tr:m[3].split(' ').map(x=>x.slice(1)),l:n});}
 else if(m=ln.match(/^  \(fx :(\S+)/)){const s=E[E.length-1];if(s&&s.ch)s.ch.push({id:'mfx:'+m[1],k:'mfx',n:m[1],fx:m[1],l:n});}});
 const end=l=>{let d=0,j=l-1;do{for(const c of TL[j]){if(c==='(')d++;else if(c===')')d--;}j++;}while(d>0&&j<TL.length);return j;};
 E.forEach(e=>{e.e=end(e.l);(e.ch||[]).forEach(c=>c.e=end(c.l));});return E;})();
const BYID={};ENT.forEach(e=>{BYID[e.id]=e;(e.ch||[]).forEach(c=>BYID[c.id]=c);});
const SECS=BYID.song.ch;let _acc=0;SECS.forEach(s=>{s.start=_acc;_acc+=s.b;});const TOTAL=_acc;

const clamp=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
const toT=(m,v)=>m.log?Math.log(v/m.min)/Math.log(m.max/m.min):(v-m.min)/(m.max-m.min);
const fromT=(m,t)=>m.log?m.min*Math.pow(m.max/m.min,t):m.min+t*(m.max-m.min);
const fmt=(m,v)=>m.max>=100?String(Math.round(v)):v.toFixed(2);
const rng=m=>m.max>=1000?`${m.min}–${m.max/1000}k${m.log?' · log':''}`:`${m.min}–${m.max}`;

function useStudio({initSel=[],initPos=14.4,animate=false,cursorLine=22}={}){
 const [P,setP]=useState(()=>Object.fromEntries(Object.entries(PM).map(([k,m])=>[k,m.v])));
 const [inl,setInl]=useState(INL0);const [sel,setSel]=useState(initSel);
 const [muted,setMuted]=useState(['keys']);const [solo,setSolo]=useState([]);
 const [bpm,setBpm]=useState(124);const [pos,setPos]=useState(initPos);const [cur,setCur]=useState(cursorLine);
 useEffect(()=>{if(!animate)return;const id=setInterval(()=>setPos(p=>(p+.1/(240/bpm))%TOTAL),100);return()=>clearInterval(id);},[animate,bpm]);
 const tg=(arr,set,x)=>set(arr.includes(x)?arr.filter(y=>y!==x):[...arr,x]);
 const playIdx=SECS.findIndex(s=>pos>=s.start&&pos<s.start+s.b);
 const pick=(id,shift)=>{const isT=id.startsWith('track:');
  if(shift&&isT&&sel.every(x=>x.startsWith('track:'))){const n=sel.includes(id)?sel.filter(x=>x!==id):[...sel,id];setSel(n);return;}
  setSel(sel.length===1&&sel[0]===id?[]:[id]);};
 return {P,setV:(k,v)=>setP(p=>({...p,[k]:v})),inl,toggleInl:k=>tg(inl,setInl,k),sel,setSel,pick,muted,solo,toggleM:t=>tg(muted,setMuted,t),toggleS:t=>tg(solo,setSolo,t),
  bpm,setBpm,pos,setPos,playIdx,cur,setCur};}

function InlineSlider({id,ctx,force}){
 const m=PM[id],v=ctx.P[id],t=toT(m,v);const [hv,setHv]=useState(false);const [dr,setDr]=useState(null);
 const st=force||(dr?'drag':hv?'hover':'');
 const down=e=>{e.preventDefault();let tc=t,x=e.clientX;setDr(e.shiftKey?'fine':'drag');
  const mv=ev=>{tc=clamp(tc+(ev.clientX-x)/140*(ev.shiftKey?.1:1));x=ev.clientX;setDr(ev.shiftKey?'fine':'drag');ctx.setV(id,fromT(m,tc));};
  const up=()=>{setDr(null);removeEventListener('pointermove',mv);removeEventListener('pointerup',up);};
  addEventListener('pointermove',mv);addEventListener('pointerup',up);};
 const on=st==='hover'||st==='drag'||st==='fine';
 return <span onPointerDown={down} onMouseEnter={()=>setHv(true)} onMouseLeave={()=>setHv(false)} title={`(slider ${fmt(m,v)} ${m.min} ${m.max}) · ${rng(m)} · drag, ⇧ fine`}
  style={{position:'relative',display:'inline-flex',alignItems:'center',justifyContent:'flex-end',width:70,height:15,verticalAlign:'-3px',border:`1px solid ${on?OC.cyan:OC.bd}`,background:'#0a1020',cursor:'ew-resize',userSelect:'none',boxSizing:'border-box',padding:'0 5px'}}>
  <span style={{position:'absolute',left:0,top:0,bottom:0,width:`${t*100}%`,background:OC.cyan,opacity:on?.32:.2}}></span>
  <span style={{position:'relative',fontSize:11,color:on?OC.hi:OC.cyan,lineHeight:'13px'}}>{fmt(m,v)}</span>
  {(st==='fine')&&<span style={{position:'absolute',left:'100%',marginLeft:6,fontSize:9,color:OC.cyan,whiteSpace:'nowrap'}}>×0.1</span>}
 </span>;}

function CodeLine({ln,ctx,raw}){
 return ln.split(/(\{[\w.]+\})/).map((p,i)=>{
  if(p[0]==='{'&&p.length>2){const k=p.slice(1,-1),m=PM[k];
   if(ctx.inl.includes(k)){if(raw)return <span key={i}><span style={{color:OC.pink}}>(</span><span style={{color:OC.yellow,textDecoration:`underline dotted ${OC.cyan}`}}>slider</span> <span style={{color:OC.orange}}>{fmt(m,ctx.P[k])}</span> <span style={{color:OC.orange}}>{m.min} {m.max}</span><span style={{color:OC.pink}}>)</span></span>;
    return <InlineSlider key={i} id={k} ctx={ctx}/>;}
   return <span key={i} style={{color:OC.orange}}>{fmt(m,ctx.P[k])}</span>;}
  return tok(p).map(([c,v],j)=><span key={i+'_'+j} style={{color:c||undefined}}>{v}</span>);});}

function Editor2({ctx,edRef}){
 const hl=ctx.sel.map(id=>BYID[id]).filter(Boolean).map(e=>[e.l,e.e]);
 return <div ref={edRef} style={{flex:1,minHeight:0,background:OC.ed,overflow:'hidden',padding:'10px 0',fontFamily:MONO,fontSize:13,lineHeight:'21px'}}>
  {TL.map((ln,i)=>{const n=i+1,c=n===ctx.cur,h=hl.some(([a,b])=>n>=a&&n<=b);
   return <div key={i} style={{display:'flex',whiteSpace:'pre',background:h?'#1f2d4e70':c?'#ffffff07':undefined}}>
    <span style={{width:36,flexShrink:0,textAlign:'right',paddingRight:12,color:c?OC.mid:h?OC.title:'#2a3860',fontSize:11,borderRight:`2px solid ${h?OC.title:'transparent'}`,marginRight:10}}>{n}</span>
    <span><CodeLine ln={ln} ctx={ctx}/>{c&&<span style={{display:'inline-block',width:0,height:15,borderLeft:`2px solid ${OC.pink}`,verticalAlign:'-3px',marginLeft:1}}></span>}</span>
   </div>;})}
 </div>;}

function Row({kind,name,meta,dots,indent=0,line,st={},onClick}){
 const [hv,setHv]=useState(false);
 const {sel,cursor,playing,progress=0,muted,audible,off,fold,solo}=st;
 const bg=sel?V.sel:playing?(cursor?OC.curPlay:OC.play):cursor?OC.cur:hv?OC.hover:'transparent';
 const glyph=fold||(playing?'▶':muted?'■':solo?'S':audible?'•':'');
 const gc=fold?OC.mid:solo?OC.yellow:playing||audible?OC.cyan:OC.mid;
 const nc=playing?OC.cyan:muted?OC.dim:off?OC.mid:(hv||cursor||sel)?OC.hi:OC.text;
 return <div onClick={onClick} onMouseEnter={()=>setHv(true)} onMouseLeave={()=>setHv(false)} style={{position:'relative',display:'flex',alignItems:'center',height:22,background:bg,fontSize:11,cursor:'pointer',userSelect:'none',opacity:muted?.55:1}}>
  {(cursor||sel)&&<div style={{position:'absolute',left:0,top:0,bottom:0,width:2,background:sel?OC.hi:playing?OC.cyan:OC.mid}}></div>}
  <span style={{width:18,flexShrink:0,textAlign:'center',color:gc,fontSize:fold?9:(glyph==='•'?13:8),fontWeight:700}}>{glyph}</span>
  {indent>0&&<span style={{width:indent,flexShrink:0}}></span>}
  {kind&&<span style={{width:44,flexShrink:0,color:muted?OC.dim:KC2[kind],fontSize:10}}>{kind}</span>}
  <span style={{flex:1,minWidth:0,color:nc,fontWeight:playing?700:400,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{name}{meta&&<span style={{color:OC.mid,fontWeight:400,marginLeft:name?6:0}}>{meta}</span>}</span>
  {dots&&<span style={{display:'flex',gap:3,marginRight:6}}>{dots.map(d=><span key={d} style={{width:5,height:5,borderRadius:'50%',background:TRK_C[d],opacity:playing?1:.75}}></span>)}</span>}
  <span style={{width:30,flexShrink:0,textAlign:'right',paddingRight:sel?4:10,color:cursor?OC.text:OC.mid,fontSize:10}}>{line}</span>
  {sel&&<span style={{width:8,flexShrink:0,color:OC.hi,fontSize:10}}>›</span>}
  {playing&&<div style={{position:'absolute',left:18,bottom:0,height:1,width:`calc((100% - 18px) * ${progress})`,background:OC.cyan,opacity:.7}}></div>}
 </div>;}

function StatusHead({ctx}){
 const [ed,setEd]=useState(false);
 const commit=v=>{const n=parseInt(v,10);if(n>=40&&n<=300)ctx.setBpm(n);setEd(false);};
 return <div style={{flexShrink:0,padding:'9px 10px 8px',borderBottom:`1px solid ${OC.bd2}`,display:'flex',flexDirection:'column',gap:7}}>
  <div style={{display:'flex',alignItems:'baseline',gap:6}}>
   {ed?<input autoFocus defaultValue={ctx.bpm} onBlur={e=>commit(e.target.value)} onKeyDown={e=>{if(e.key==='Enter')commit(e.target.value);if(e.key==='Escape')setEd(false);}} style={{width:40,background:'#0a1020',border:`1px solid ${OC.yellow}`,color:OC.yellow,font:`700 15px ${MONO}`,padding:'0 3px',outline:'none'}}/>
    :<span onClick={()=>setEd(true)} title="click to edit · ↑↓ ±1 · ⇧↑↓ ±10" style={{color:OC.yellow,fontWeight:700,fontSize:15,cursor:'text',borderBottom:`1px dashed ${OC.yellow}66`,lineHeight:'18px'}}>{ctx.bpm}</span>}
   <span style={{fontSize:9,color:OC.mid,letterSpacing:'.12em'}}>BPM</span>
   <div style={{flex:1}}></div>
   <span style={{color:OC.mid,fontSize:9,border:`1px solid ${OC.bd}`,padding:'0 4px',lineHeight:'14px'}}>wasm</span>
  </div>
  <div title={`bar ${Math.floor(ctx.pos)+1}/${TOTAL}`} style={{display:'flex',gap:2,height:5}}>
   {SECS.map((s,j)=>{const f=clamp((ctx.pos-s.start)/s.b);return <div key={j} onClick={()=>ctx.setPos(s.start)} style={{flex:s.b,position:'relative',background:OC.bd,cursor:'pointer'}}>
    <div style={{position:'absolute',left:0,top:0,bottom:0,width:`${f*100}%`,background:OC.cyan,opacity:j===ctx.playIdx?1:.4}}></div></div>;})}
  </div>
  <div style={{display:'flex',gap:10,fontSize:9,color:OC.mid}}><span>cpu <span style={{color:OC.text}}>12%</span></span><span>latency <span style={{color:OC.text}}>5.3 ms</span></span></div>
 </div>;}

function OutlineV2({ctx}){
 const sec=SECS[ctx.playIdx];const inside=e=>ctx.cur>=e.l&&ctx.cur<=e.e;
 const S=id=>ctx.sel.includes(id);const click=id=>e=>ctx.pick(id,e.shiftKey);
 const prog=sec?(ctx.pos-sec.start)/sec.b:0;
 const audibleT=t=>!ctx.muted.includes(t)&&(!ctx.solo.length||ctx.solo.includes(t));
 return <div style={{width:192,flexShrink:0,background:OC.cp,borderRight:`1px solid ${OC.bd2}`,display:'flex',flexDirection:'column',fontFamily:MONO,overflow:'hidden'}}>
  <StatusHead ctx={ctx}/>
  <div style={{height:28,flexShrink:0,display:'flex',alignItems:'center',gap:6,padding:'0 4px 0 10px',borderBottom:`1px solid ${OC.bd2}`}}>
   <span style={{fontSize:9,letterSpacing:'.14em',color:OC.title,textTransform:'uppercase'}}>outline</span>
   <span style={{fontSize:9,color:OC.dim}}>{Object.keys(BYID).length}</span><div style={{flex:1}}></div>
   <span title="Hide outline  ⌘⇧O" style={{width:22,height:22,display:'flex',alignItems:'center',justifyContent:'center',color:OC.mid,fontSize:13,cursor:'pointer'}}>‹</span></div>
  <div style={{flex:1,overflow:'auto',padding:'4px 0'}}>
   {ENT.map(e=>{
    if(e.k==='song')return <React.Fragment key={e.id}><Row kind="song" meta={TOTAL+' bars'} line={'L'+e.l} st={{fold:'▾'}}/>
     {e.ch.map((s,j)=><Row key={s.id} name={s.n} meta={s.b+'b'} dots={s.tr} indent={12} line={'L'+s.l} onClick={click(s.id)} st={{sel:S(s.id),playing:j===ctx.playIdx,progress:prog,cursor:inside(s)}}/>)}</React.Fragment>;
    if(e.k==='master')return <React.Fragment key={e.id}><div style={{height:1,background:OC.bd2,margin:'4px 0'}}></div>
     <Row kind="master" name="fx" meta={e.ch.length} line={'L'+e.l} onClick={click(e.id)} st={{fold:'▾',sel:S(e.id)}}/>
     {e.ch.map(c=><Row key={c.id} name={c.n} indent={12} line={'L'+c.l} onClick={click(c.id)} st={{sel:S(c.id),cursor:inside(c)}}/>)}</React.Fragment>;
    const isT=e.k==='track',m=isT&&ctx.muted.includes(e.t);
    return <Row key={e.id} kind={e.k} name={e.n} line={'L'+e.l} onClick={click(e.id)} st={{sel:S(e.id),cursor:inside(e),muted:m,solo:isT&&ctx.solo.includes(e.t),audible:isT&&sec&&audibleT(e.t)&&sec.tr.includes(e.t),off:isT&&sec&&(!sec.tr.includes(e.t)||!audibleT(e.t))}}/>;})}
  </div>
  <div style={{flexShrink:0,borderTop:`1px solid ${OC.bd2}`,padding:'7px 10px',display:'flex',flexDirection:'column',gap:4,fontSize:9,color:OC.mid}}>
   <div style={{display:'flex',alignItems:'center',gap:4}}><span>click</span><span>inspect</span><div style={{flex:1}}></div><Kbd>⇧ click</Kbd><span>add</span></div>
   <div style={{display:'flex',alignItems:'center',gap:4}}><Kbd>F12</Kbd><span>go to def</span><div style={{flex:1}}></div><Kbd>esc</Kbd><span>close</span></div>
   <div style={{display:'flex',alignItems:'center',gap:4}}><Kbd>⌘K S</Kbd><span>slider</span><div style={{flex:1}}></div><Kbd>⌘⇧O</Kbd><span>hide</span></div>
  </div></div>;}

function ISlider({id,ctx}){
 const m=PM[id],v=ctx.P[id],t=toT(m,v),il=ctx.inl.includes(id);const ref=useRef();
 const down=e=>{const r=ref.current.getBoundingClientRect();let tc=e.shiftKey?t:clamp((e.clientX-r.left)/r.width),x=e.clientX;ctx.setV(id,fromT(m,tc));
  const mv=ev=>{tc=clamp(tc+(ev.clientX-x)/r.width*(ev.shiftKey?.1:1));x=ev.clientX;ctx.setV(id,fromT(m,tc));};
  const up=()=>{removeEventListener('pointermove',mv);removeEventListener('pointerup',up);};addEventListener('pointermove',mv);addEventListener('pointerup',up);};
 return <div style={{display:'grid',gridTemplateColumns:'36px minmax(0,1fr) 38px 12px',alignItems:'center',gap:6,height:20}}>
  <span style={{color:OC.mid,fontSize:10}}>{m.l}</span>
  <div ref={ref} onPointerDown={down} title={`${rng(m)} · ⇧ fine`} style={{position:'relative',height:14,cursor:'ew-resize',userSelect:'none'}}>
   <div style={{position:'absolute',left:0,right:0,top:6,height:2,background:OC.bd}}></div>
   <div style={{position:'absolute',left:0,top:6,height:2,width:`${t*100}%`,background:OC.cyan,opacity:.5}}></div>
   <div style={{position:'absolute',left:`calc(${t*100}% - 4px)`,top:3,width:8,height:8,borderRadius:'50%',background:OC.cyan}}></div></div>
  <span style={{fontSize:10,color:OC.text,textAlign:'right'}}>{fmt(m,v)}</span>
  <span onClick={()=>ctx.toggleInl(id)} title={il?'shown as inline slider in code — click to unwrap':'wrap in (slider …) in code  ⌘K S'} style={{color:il?OC.cyan:OC.dim,cursor:'pointer',fontSize:11,textAlign:'center'}}>≋</span>
 </div>;}

const Lbl=({children})=><div style={{fontSize:9,letterSpacing:'.14em',color:OC.title,textTransform:'uppercase',margin:'8px 0 3px'}}>{children}</div>;
function MS({on,c,onClick,children,title}){return <span onClick={onClick} title={title} style={{width:18,height:16,display:'flex',alignItems:'center',justifyContent:'center',fontSize:9,fontWeight:700,cursor:'pointer',border:`1px solid ${on?c:OC.bd}`,color:on?OC.ed:OC.mid,background:on?c:'transparent'}}>{children}</span>;}
function Jump({line,ctx}){return <span onClick={()=>ctx.jump(line)} title="Go to code  F12" style={{fontSize:10,color:OC.mid,cursor:'pointer',border:`1px solid ${OC.bd}`,padding:'0 4px',lineHeight:'14px'}}>↗ L{line}</span>;}
const blk={padding:'8px 10px 10px',borderBottom:`1px solid ${OC.bd2}`};

function TrackBlock({t,ctx,head}){
 const ks=Object.keys(PM).filter(k=>k.startsWith(t+'.'));const base=ks.filter(k=>k.split('.').length===2);
 const fx=[...new Set(ks.filter(k=>k.split('.').length===3).map(k=>k.split('.')[1]))];const e=BYID['track:'+t];
 return <div style={blk}>
  {head&&<div style={{display:'flex',alignItems:'center',gap:6,height:22}}><span style={{width:6,height:6,borderRadius:'50%',background:TRK_C[t]}}></span><span style={{fontSize:12,color:OC.hi}}>:{t}</span><div style={{flex:1}}></div><Jump line={e.l} ctx={ctx}/></div>}
  <div style={{display:'flex',alignItems:'center',gap:4,height:22}}>
   <MS on={ctx.muted.includes(t)} c={OC.orange} onClick={()=>ctx.toggleM(t)} title="mute">M</MS><MS on={ctx.solo.includes(t)} c={OC.yellow} onClick={()=>ctx.toggleS(t)} title="solo">S</MS>
   <span style={{fontSize:10,color:OC.mid,marginLeft:4}}>{ctx.muted.includes(t)?'muted':ctx.solo.includes(t)?'solo':'live'}</span></div>
  <Lbl>params</Lbl>{base.map(k=><ISlider key={k} id={k} ctx={ctx}/>)}
  {fx.length>0&&<><Lbl>fx chain</Lbl>{fx.map((f,i)=><div key={f} style={{marginTop:i?6:0}}><div style={{fontSize:11,color:OC.text,height:18,display:'flex',gap:6}}><span style={{color:OC.dim}}>{i+1}</span>{f}</div>
   {ks.filter(k=>k.startsWith(`${t}.${f}.`)).map(k=><ISlider key={k} id={k} ctx={ctx}/>)}</div>)}</>}
 </div>;}

function SectionBlock({s,ctx}){
 const playing=ctx.playIdx===s.i,bar=playing?Math.floor(ctx.pos-s.start):-1;
 return <><div style={blk}>
  <div style={{display:'flex',alignItems:'center',gap:6,fontSize:10,color:OC.mid,height:18}}><span>{s.b} bars</span><div style={{flex:1}}></div>
   {playing?<span style={{color:OC.cyan,fontWeight:700}}>▶ bar {bar+1}/{s.b}</span>:<span onClick={()=>ctx.setPos(s.start)} style={{color:OC.text,cursor:'pointer',border:`1px solid ${OC.bd}`,padding:'0 5px',lineHeight:'14px'}}>▶ cue</span>}</div>
  <div style={{display:'flex',gap:2,marginTop:6}}>{Array.from({length:s.b}).map((_,i)=><div key={i} style={{flex:1,height:14,background:i===bar?OC.cyan:i<bar?OC.cyan+'44':OC.bd,display:'flex',alignItems:'flex-end',justifyContent:'center',fontSize:8,color:i===bar?OC.ed:OC.dim}}>{i===0||i===s.b-1||i===bar?i+1:''}</div>)}</div>
  <Lbl>tracks · {s.tr.length}</Lbl>
  {s.tr.map(t=><div key={t} style={{marginBottom:4,opacity:ctx.muted.includes(t)?.5:1}}>
   <div style={{display:'flex',alignItems:'center',gap:6,height:20}}><span style={{width:6,height:6,borderRadius:'50%',background:TRK_C[t]}}></span><span style={{fontSize:11,color:OC.text,flex:1}}>:{t}</span>
    <MS on={ctx.muted.includes(t)} c={OC.orange} onClick={()=>ctx.toggleM(t)}>M</MS><MS on={ctx.solo.includes(t)} c={OC.yellow} onClick={()=>ctx.toggleS(t)}>S</MS></div>
   <ISlider id={t+'.amp'} ctx={ctx}/></div>)}
 </div></>;}

function MasterBlock({fxs,ctx}){return <div style={blk}>{fxs.map((f,i)=><div key={f} style={{marginTop:i?8:0}}>
 {fxs.length>1&&<div style={{fontSize:11,color:OC.text,height:18,display:'flex',gap:6}}><span style={{color:OC.dim}}>{i+1}</span>{f}<div style={{flex:1}}></div><Jump line={BYID['mfx:'+f].l} ctx={ctx}/></div>}
 {Object.keys(PM).filter(k=>k.startsWith(`master.${f}.`)).map(k=><ISlider key={k} id={k} ctx={ctx}/>)}</div>)}
 <div style={{fontSize:9,color:OC.mid,marginTop:8}}>applies after all tracks</div></div>;}

function DefBlock({e,ctx}){return <div style={blk}>
 <div style={{fontSize:11,lineHeight:'17px',whiteSpace:'pre',overflow:'hidden',background:OC.ed,padding:'6px 8px',border:`1px solid ${OC.bd2}`}}>{TL.slice(e.l-1,e.e).map((ln,i)=><div key={i}><CodeLine ln={ln} ctx={ctx}/></div>)}</div>
 <div style={{fontSize:10,color:OC.mid,marginTop:8,lineHeight:1.5}}>No live params. Used by {e.n==='hats'||e.n==='humanize'||e.n==='kick'||e.n==='snare'?':drums':':pad'}.</div></div>;}

function Inspector({ctx,w,setW}){
 const items=ctx.sel.map(id=>BYID[id]).filter(Boolean);if(!items.length)return null;
 const one=items.length===1?items[0]:null;
 const dn=e=>{e.preventDefault();const x0=e.clientX,w0=w;const mv=ev=>setW(clamp(w0+ev.clientX-x0,180,380));const up=()=>{removeEventListener('pointermove',mv);removeEventListener('pointerup',up);};addEventListener('pointermove',mv);addEventListener('pointerup',up);};
 const kind=one?(one.k==='sec'?'section':one.k==='mfx'?'master fx':one.k==='master'?'master':one.k):'tracks';
 const name=one?(one.k==='master'?'fx':one.n):items.length;
 let body;
 if(!one)body=<>{items.map(e=><TrackBlock key={e.id} t={e.t} ctx={ctx} head/>)}<div style={{fontSize:9,color:OC.mid,padding:'8px 10px'}}>⇧ click a track to add / remove</div></>;
 else if(one.k==='track')body=<TrackBlock t={one.t} ctx={ctx}/>;
 else if(one.k==='sec')body=<SectionBlock s={one} ctx={ctx}/>;
 else if(one.k==='master')body=<MasterBlock fxs={one.ch.map(c=>c.fx)} ctx={ctx}/>;
 else if(one.k==='mfx')body=<MasterBlock fxs={[one.fx]} ctx={ctx}/>;
 else body=<DefBlock e={one} ctx={ctx}/>;
 return <div style={{width:w,position:'relative',flexShrink:0,background:V.insp,borderRight:`1px solid ${OC.bd2}`,display:'flex',flexDirection:'column',fontFamily:MONO,overflow:'hidden'}}>
  <div style={{height:28,flexShrink:0,display:'flex',alignItems:'center',gap:6,padding:'0 8px 0 10px',borderBottom:`1px solid ${OC.bd2}`}}>
   <span style={{fontSize:10,color:one?(KC2[one.k]||(one.k==='mfx'?V.fx:OC.text)):KC2.track}}>{kind}</span>
   <span style={{fontSize:12,color:OC.hi,fontWeight:500,flex:1,minWidth:0,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{name}</span>
   {one&&<Jump line={one.l} ctx={ctx}/>}<Kbd>esc</Kbd></div>
  <div style={{flex:1,overflow:'auto'}}>{body}</div>
  <div onPointerDown={dn} className="rsz" title="drag to resize" style={{position:'absolute',top:0,bottom:0,right:-1,width:5,cursor:'col-resize'}}></div>
 </div>;}

function TopNav2({aiOpen}){
 const b={border:`1px solid ${OC.bd}`,color:OC.mid,fontSize:12,letterSpacing:'.06em',padding:'4px 12px'};
 return <div style={{height:40,flexShrink:0,display:'flex',alignItems:'center',gap:6,padding:'0 12px',background:OC.cp,borderBottom:`1px solid ${OC.bd2}`,fontFamily:MONO}}>
  <img src="repulse-icon.svg" style={{width:22,height:22}}/>
  <span style={{color:OC.pink,fontWeight:700,fontSize:15,letterSpacing:'.04em',marginRight:18}}>REP<span style={{color:OC.cyan}}>u</span>Lse</span>
  {['tap','lib','share'].map(x=><span key={x} style={b}>{x}</span>)}
  <div style={{flex:1}}></div>
  <span style={{...b,display:'flex',alignItems:'center',gap:8,padding:'3px 12px 3px 4px'}}><span style={{width:20,height:20,borderRadius:'50%',background:'#2a3555',color:OC.text,fontSize:10,display:'flex',alignItems:'center',justifyContent:'center'}}>S</span>Stephan</span>
  <span style={{...b,borderColor:OC.pink,color:OC.pink,fontWeight:700}}>■ stop</span>
  <span style={{width:8,height:8,borderRadius:'50%',background:OC.cyan,boxShadow:`0 0 6px ${OC.cyan}`,marginLeft:4}}></span>
 </div>;}

function AIRail({open,busy,onToggle}){
 return <div onClick={onToggle} title="AI  ⌘I" style={{width:28,flexShrink:0,background:OC.cp,borderLeft:`1px solid ${OC.bd2}`,display:'flex',flexDirection:'column',alignItems:'center',cursor:'pointer',fontFamily:MONO}}>
  <div style={{height:28,width:'100%',display:'flex',alignItems:'center',justifyContent:'center',color:OC.text,fontSize:13,borderBottom:`1px solid ${OC.bd2}`}}>{open?'›':'‹'}</div>
  <div style={{writingMode:'vertical-rl',fontSize:9,letterSpacing:'.2em',color:open?OC.cyan:OC.mid,textTransform:'uppercase',marginTop:12}}>ai</div>
  {busy&&<span className="aidot" style={{width:5,height:5,borderRadius:'50%',background:OC.cyan,marginTop:10}}></span>}
 </div>;}

function AIPanel({busy,ctx}){
 const one=ctx.sel.length===1?BYID[ctx.sel[0]]:null;
 return <div style={{width:300,flexShrink:0,background:OC.cp,borderLeft:`1px solid ${OC.bd2}`,display:'flex',flexDirection:'column',fontFamily:MONO,fontSize:11}}>
  <div style={{height:28,flexShrink:0,display:'flex',alignItems:'center',gap:8,padding:'0 10px',borderBottom:`1px solid ${OC.bd2}`}}>
   <span style={{fontSize:9,letterSpacing:'.14em',color:OC.title,textTransform:'uppercase'}}>ai</span><div style={{flex:1}}></div>
   {one&&<span title="Selection is sent as context" style={{fontSize:9,color:OC.mid,border:`1px solid ${OC.bd}`,padding:'0 4px',lineHeight:'14px'}}>ctx {one.n||one.k}</span>}</div>
  <div style={{flex:1,overflow:'auto',padding:'10px',display:'flex',flexDirection:'column',gap:12,lineHeight:1.55}}>
   <div style={{color:OC.hi}}><span style={{color:OC.pink}}>&gt; </span>bass in the chorus feels static, give it more movement</div>
   <div style={{color:OC.text}}>Adding an octave jump on the off-beats and a slow filter sweep on <span style={{color:OC.cyan}}>:bass</span>.
    <div style={{marginTop:6,background:OC.ed,border:`1px solid ${OC.bd2}`,padding:'6px 8px',fontSize:10,lineHeight:'16px',whiteSpace:'pre'}}><div style={{color:OC.pink}}>- (seq 0 :_ 0 3 :_ 5 :_ 7)</div><div style={{color:OC.green}}>+ (seq 0 12 0 3 12 5 :_ 7)</div></div>
    <div style={{display:'flex',gap:6,marginTop:6}}><span style={{border:`1px solid ${OC.cyan}`,color:OC.cyan,padding:'1px 8px',fontSize:10}}>apply</span><span style={{border:`1px solid ${OC.bd}`,color:OC.mid,padding:'1px 8px',fontSize:10}}>discard</span></div></div>
   <div style={{color:OC.hi}}><span style={{color:OC.pink}}>&gt; </span>now make the pad breathe with the kick</div>
   {busy&&<div style={{display:'flex',alignItems:'center',gap:8,color:OC.mid}}><span style={{display:'flex',gap:4}}>{[0,1,2].map(i=><span key={i} className="aidot" style={{width:5,height:5,borderRadius:'50%',background:OC.cyan,animationDelay:i*.18+'s'}}></span>)}</span>thinking</div>}
  </div>
  <div style={{position:'relative',margin:10,border:`1px solid ${OC.bd}`,padding:'8px 10px',display:'flex',alignItems:'center',gap:6,color:OC.dim}}>
   {busy&&<div className="aibar" style={{position:'absolute',left:0,right:0,top:-1,height:1}}></div>}
   <span style={{flex:1}}>{busy?'working…':'describe a change'}</span><Kbd>⏎</Kbd></div>
 </div>;}

function AppV2({initSel=[],ai=false,busy=true,animate=false,initPos=14.4,initW=220,live=false}){
 const ctx=useStudio({initSel,initPos,animate});const [w,setW]=useState(initW);const [aiOpen,setAi]=useState(ai);const ed=useRef();
 const scrollTo=l=>{const el=ed.current;if(el)el.scrollTo({top:Math.max(0,(l-1)*21-el.clientHeight/3),behavior:'smooth'});};
 ctx.jump=l=>{ctx.setCur(l);scrollTo(l);};
 useEffect(()=>{const e=BYID[ctx.sel[ctx.sel.length-1]];if(e)scrollTo(e.l);},[ctx.sel.join()]);
 useEffect(()=>{if(!live)return;const f=e=>{if(e.key==='Escape')ctx.setSel([]);};addEventListener('keydown',f);return()=>removeEventListener('keydown',f);},[live]);
 return <div style={{width:'100%',height:'100%',display:'flex',flexDirection:'column',background:OC.bg,color:OC.text,fontFamily:MONO}}>
  <TopNav2/>
  <div style={{flex:1,minHeight:0,display:'flex'}}>
   <OutlineV2 ctx={ctx}/><Inspector ctx={ctx} w={w} setW={setW}/>
   <div style={{flex:1,minWidth:0,display:'flex',flexDirection:'column'}}><Editor2 ctx={ctx} edRef={ed}/><Bottom/></div>
   {aiOpen&&<AIPanel busy={busy} ctx={ctx}/>}<AIRail open={aiOpen} busy={busy} onToggle={()=>setAi(!aiOpen)}/>
  </div></div>;}

function InspectorDemo({sel,h=420,pos=14.4,w=220}){const ctx=useStudio({initSel:sel,initPos:pos});ctx.jump=()=>{};const [ww,setW]=useState(w);
 return <div style={{height:h,display:'flex',border:`1px solid ${OC.bd2}`}}><Inspector ctx={ctx} w={ww} setW={setW}/></div>;}
function SliderDemo({id,force,raw,pre,post}){const ctx=useStudio();
 return <div style={{fontFamily:MONO,fontSize:13,lineHeight:'21px',whiteSpace:'pre',background:OC.ed,padding:'8px 12px',border:`1px solid ${OC.bd2}`}}>
  {tok(pre).map(([c,v],j)=><span key={j} style={{color:c||undefined}}>{v}</span>)}
  {raw?<CodeLine ln={'{'+id+'}'} ctx={ctx} raw/>:<InlineSlider id={id} ctx={ctx} force={force}/>}
  {tok(post).map(([c,v],j)=><span key={'p'+j} style={{color:c||undefined}}>{v}</span>)}</div>;}

Object.assign(window,{AppV2,InspectorDemo,SliderDemo,PM,V});
