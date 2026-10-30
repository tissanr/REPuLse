const OC={bg:'#131928',ed:'#0d1421',cp:'#10172a',bd:'#1e2a45',bd2:'#1a2440',text:'#abb2bf',hi:'#d7dce4',mid:'#5a6a8a',dim:'#3a4868',title:'#4a5a7e',pink:'#e94560',cyan:'#56b6c2',yellow:'#e5c07b',purple:'#c678dd',green:'#98c379',orange:'#d19a66',hover:'#151f37',cur:'#1b2744',play:'#122634',curPlay:'#1a3040'};
const KIND_C={def:OC.purple,defn:OC.green,synth:OC.orange,track:OC.yellow,song:OC.pink};
const TRK_C={drums:OC.pink,bass:OC.cyan,keys:OC.yellow,pad:OC.purple};
const MONO="'JetBrains Mono',monospace";

const CODE=`(def kick  (seq :bd :_ :bd :_ :bd :_ :bd :_))
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
  (amp 0.85)
  (fx :filter :freq 0.7))

(track :bass
  (->> (scale :minor :e1
         (seq 0 :_ 0 3 :_ 5 :_ 7))
       (synth :saw)
       (amp 0.7)
       (decay 0.25)))

(track :keys
  (->> (chord :minor7 :e3)
       (slow 2)
       (amp 0.4)
       (pan -0.3)))

(track :pad
  (->> (pad :e2)
       (slow 4)
       (amp 0.35)
       (fx :reverb 0.8)))

;; ── arrangement
(song
  (section :intro  4 [:pad :keys])
  (section :verse  8 [:drums :bass :pad])
  (section :chorus 8 [:drums :bass :keys :pad])
  (section :verse  8 [:drums :bass :pad])
  (section :outro  4 [:pad]))`;

const KW=new Set(['def','defn','defsynth','track','seq','fast','stack','amp','decay','scale','chord','slow','pan','fx','song','section','saw','detune','lpf','attack','nudge','rand','synth']);
function tok(line){const o=[];let i=0;while(i<line.length){const c=line[i];
 if(c===';'){o.push([OC.mid,line.slice(i)]);break;}
 if(c==='('||c===')'){o.push([OC.pink,c]);i++;continue;}
 if(c==='['||c===']'){o.push([OC.purple,c]);i++;continue;}
 if(c===' '){let j=i;while(line[j]===' ')j++;o.push([null,line.slice(i,j)]);i=j;continue;}
 let j=i;while(j<line.length&&!/[\s()[\]]/.test(line[j]))j++;const w=line.slice(i,j);
 o.push([w[0]===':'?OC.cyan:/^-?[0-9.]+$/.test(w)?OC.orange:w==='->>'?OC.yellow:KW.has(w)?OC.yellow:OC.text,w]);i=j;}
 return o;}

const ENTRIES=[
 {k:'def',n:'kick',l:1},{k:'def',n:'snare',l:2},{k:'def',n:'hats',l:3},
 {k:'defn',n:'humanize',l:5,e:7},{k:'synth',n:'pad',l:9,e:11},
 {k:'track',n:':drums',t:'drums',l:14,e:18},{k:'track',n:':bass',t:'bass',l:20,e:25},
 {k:'track',n:':keys',t:'keys',l:27,e:31},{k:'track',n:':pad',t:'pad',l:33,e:37},
 {k:'song',n:'',l:40,e:45,meta:'32 bars'}];
const SECTIONS=[
 {n:':intro',b:4,tr:['pad','keys'],l:41},{n:':verse',b:8,tr:['drums','bass','pad'],l:42},
 {n:':chorus',b:8,tr:['drums','bass','keys','pad'],l:43},{n:':verse',b:8,tr:['drums','bass','pad'],l:44},
 {n:':outro',b:4,tr:['pad'],l:45}];

function ORow({kind,name,meta,dots,line,indent=0,hover,cursor,playing,progress=0,muted,audible,off,uneval,fold}){
 const bg=playing?(cursor?OC.curPlay:OC.play):cursor?OC.cur:hover?OC.hover:'transparent';
 const glyph=fold||(playing?'▶':muted?'■':uneval?'○':audible?'•':'');
 const gc=fold?OC.mid:playing||audible?OC.cyan:OC.mid;
 const nc=playing?OC.cyan:muted?OC.dim:off?OC.mid:(hover||cursor)?OC.hi:OC.text;
 return <div style={{position:'relative',display:'flex',alignItems:'center',height:22,background:bg,fontSize:11,cursor:'pointer',opacity:muted?.55:1}}>
  {cursor&&<div style={{position:'absolute',left:0,top:0,bottom:0,width:2,background:playing?OC.cyan:OC.mid}}></div>}
  <span style={{width:18,flexShrink:0,textAlign:'center',color:gc,fontSize:fold?9:(glyph==='•'?13:8)}}>{glyph}</span>
  {indent>0&&<span style={{width:indent,flexShrink:0}}></span>}
  {kind&&<span style={{width:40,flexShrink:0,color:muted?OC.dim:KIND_C[kind],fontSize:10}}>{kind}</span>}
  <span style={{flex:1,minWidth:0,color:nc,fontWeight:playing?700:400,overflow:'hidden',textOverflow:'ellipsis',whiteSpace:'nowrap'}}>{name}{meta&&<span style={{color:OC.mid,fontWeight:400,marginLeft:name?6:0}}>{meta}</span>}</span>
  {dots&&<span style={{display:'flex',gap:3,marginRight:6}}>{dots.map(d=><span key={d} title={':'+d} style={{width:5,height:5,borderRadius:'50%',background:TRK_C[d],opacity:playing?1:.75}}></span>)}</span>}
  <span style={{width:30,flexShrink:0,textAlign:'right',paddingRight:10,color:cursor?OC.text:OC.mid,fontSize:10}}>{line}</span>
  {playing&&<div style={{position:'absolute',left:18,bottom:0,height:1,width:`calc((100% - 18px) * ${progress})`,background:OC.cyan,opacity:.7}}></div>}
 </div>;}

function Kbd({children}){return <span style={{border:`1px solid ${OC.bd}`,borderRadius:2,padding:'0 4px',color:OC.text,fontSize:9,lineHeight:'14px'}}>{children}</span>;}

function OutlineHeader({tag,count,onCollapse}){
 return <div style={{height:30,flexShrink:0,display:'flex',alignItems:'center',gap:6,padding:'0 4px 0 10px',borderBottom:`1px solid ${OC.bd2}`}}>
  <span style={{fontSize:9,letterSpacing:'.14em',color:OC.title,textTransform:'uppercase'}}>outline</span>
  {count!=null&&<span style={{fontSize:9,color:OC.dim}}>{count}</span>}
  {tag&&<span title="Syntax error — showing what still parses" style={{fontSize:9,color:OC.orange,border:`1px solid ${OC.orange}44`,padding:'0 4px',borderRadius:2}}>~ partial</span>}
  <div style={{flex:1}}></div>
  <span title="Hide outline  ⌘⇧O" style={{width:22,height:22,display:'flex',alignItems:'center',justifyContent:'center',color:OC.mid,fontSize:13,cursor:'pointer'}}>‹</span>
 </div>;}

function OutlineFooter(){
 return <div style={{flexShrink:0,borderTop:`1px solid ${OC.bd2}`,padding:'7px 10px',display:'flex',flexDirection:'column',gap:4,fontSize:9,color:OC.mid}}>
  <div style={{display:'flex',alignItems:'center',gap:4}}><Kbd>⌘ click</Kbd><Kbd>F12</Kbd><span style={{marginLeft:2}}>go to def</span></div>
  <div style={{display:'flex',alignItems:'center',gap:4}}><Kbd>⌥ ←</Kbd><span style={{marginLeft:2}}>back</span><div style={{flex:1}}></div><Kbd>⌘⇧O</Kbd><span>hide</span></div>
 </div>;}

function Outline({w=192,h='100%',mode='normal',cursorLine=22,playIdx=2,progress=.3,muted=['keys'],playing=true,hoverIdx=null,songOpen=true}){
 const sec=playing?SECTIONS[playIdx]:null;
 const inside=(e)=>cursorLine>=e.l&&cursorLine<=(e.e||e.l);
 const base={width:w,height:h,flexShrink:0,background:OC.cp,borderRight:`1px solid ${OC.bd2}`,display:'flex',flexDirection:'column',fontFamily:MONO,overflow:'hidden'};
 if(mode==='empty') return <div style={base}><OutlineHeader/>
  <div style={{padding:'14px 12px',fontSize:10,lineHeight:'1.7em',color:OC.mid,flex:1}}>No definitions yet.<br/><span style={{color:OC.pink}}>(</span><span style={{color:OC.yellow}}>def</span> <span style={{color:OC.text}}>name</span> …<span style={{color:OC.pink}}>)</span> and<br/><span style={{color:OC.pink}}>(</span><span style={{color:OC.yellow}}>track</span> <span style={{color:OC.cyan}}>:name</span> …<span style={{color:OC.pink}}>)</span><br/>appear here.</div><OutlineFooter/></div>;
 let list=ENTRIES;
 if(mode==='partial') list=ENTRIES.filter(e=>e.n!==':keys');
 return <div style={base}><OutlineHeader tag={mode==='partial'} count={mode==='partial'?13:15}/>
  <div style={{flex:1,overflow:'auto',padding:'4px 0'}}>
   {list.map((e,i)=>{
    if(mode==='partial'&&e.n===':pad') return <React.Fragment key={i}>
     <div style={{display:'flex',alignItems:'center',height:22,fontSize:10,color:OC.dim}}><span style={{width:18,textAlign:'center'}}>~</span><span style={{flex:1}}>not parsed</span><span style={{width:56,textAlign:'right',paddingRight:10}}>L27–31</span></div>
     <ORow kind="track" name=":pad" line={'L'+e.l}/></React.Fragment>;
    if(e.k==='song') return <React.Fragment key={i}>
     <ORow kind="song" meta={e.meta} line={'L'+e.l} fold={songOpen?'▾':'▸'} hover={hoverIdx===i} cursor={false}/>
     {songOpen&&SECTIONS.map((s,j)=><ORow key={j} name={s.n} meta={s.b+'b'} dots={s.tr} indent={12} line={'L'+s.l} playing={mode!=='partial'&&playing&&j===playIdx} progress={progress} cursor={inside(s)}/>)}
    </React.Fragment>;
    const isT=e.k==='track';const m=isT&&muted.includes(e.t);
    return <ORow key={i} kind={e.k} name={e.n} line={'L'+e.l} hover={hoverIdx===i} cursor={mode!=='partial'&&inside(e)}
      muted={m} audible={isT&&sec&&!m&&sec.tr.includes(e.t)} off={isT&&sec&&!sec.tr.includes(e.t)}/>;
   })}
  </div><OutlineFooter/></div>;}

function OutlineRail({h='100%',playIdx=2,playing=true}){
 const total=SECTIONS.reduce((a,s)=>a+s.b,0);
 return <div style={{width:28,height:h,flexShrink:0,background:OC.cp,borderRight:`1px solid ${OC.bd2}`,display:'flex',flexDirection:'column',alignItems:'center',fontFamily:MONO,cursor:'pointer'}} title="Show outline  ⌘⇧O">
  <div style={{height:30,width:'100%',display:'flex',alignItems:'center',justifyContent:'center',color:OC.text,fontSize:13,borderBottom:`1px solid ${OC.bd2}`}}>›</div>
  <div style={{writingMode:'vertical-rl',fontSize:9,letterSpacing:'.2em',color:OC.mid,textTransform:'uppercase',marginTop:12}}>outline</div>
  <div style={{display:'flex',flexDirection:'column',gap:3,marginTop:16}}>
   {SECTIONS.map((s,j)=><div key={j} title={s.n} style={{width:4,height:s.b/total*120,borderRadius:1,background:playing&&j===playIdx?OC.cyan:'#2a3a55'}}></div>)}
  </div>
 </div>;}

function TopNav(){
 const b={border:`1px solid ${OC.bd}`,color:OC.mid,fontSize:12,letterSpacing:'.06em',padding:'4px 12px'};
 return <div style={{height:40,flexShrink:0,display:'flex',alignItems:'center',gap:6,padding:'0 12px',background:OC.cp,borderBottom:`1px solid ${OC.bd2}`,fontFamily:MONO}}>
  <img src="repulse-icon.svg" style={{width:22,height:22}}/>
  <span style={{color:OC.pink,fontWeight:700,fontSize:15,letterSpacing:'.04em',marginRight:18}}>REP<span style={{color:OC.cyan}}>u</span>Lse</span>
  {['tap','lib','ai','share'].map(x=><span key={x} style={b}>{x}</span>)}
  <div style={{flex:1}}></div>
  <span style={{...b,display:'flex',alignItems:'center',gap:8,padding:'3px 12px 3px 4px'}}><span style={{width:20,height:20,borderRadius:'50%',background:'#2a3555',color:OC.text,fontSize:10,display:'flex',alignItems:'center',justifyContent:'center'}}>S</span>Stephan</span>
  <span style={{...b,borderColor:OC.pink,color:OC.pink,fontWeight:700}}>■ stop</span>
  <span style={{width:8,height:8,borderRadius:'50%',background:OC.cyan,boxShadow:`0 0 6px ${OC.cyan}`,marginLeft:4}}></span>
 </div>;}

function Editor({cursorLine=22}){
 return <div style={{flex:1,minHeight:0,background:OC.ed,overflow:'hidden',padding:'10px 0',fontFamily:MONO,fontSize:13,lineHeight:'21px'}}>
  {CODE.split('\n').map((ln,i)=>{const n=i+1;const cur=n===cursorLine;return <div key={i} style={{display:'flex',whiteSpace:'pre',background:cur?'#ffffff07':undefined}}>
   <span style={{width:36,flexShrink:0,textAlign:'right',paddingRight:14,color:cur?OC.mid:'#2a3860',fontSize:11}}>{n}</span>
   <span>{tok(ln).map(([c,v],k)=><span key={k} style={{color:c||undefined}}>{v}</span>)}{cur&&<span style={{display:'inline-block',width:0,height:15,borderLeft:`2px solid ${OC.pink}`,verticalAlign:'-3px',marginLeft:1}}></span>}</span>
  </div>;})}
 </div>;}

function Bottom(){
 const lanes=[['drums',[[0,100]]],['bass',[[0,12],[14,10],[26,24],[52,12],[66,34]]],['keys',[[0,50],[50,50]]],['pad',[[0,100]]]];
 return <div style={{flexShrink:0,fontFamily:MONO}}>
  <div style={{background:'#070d1c',borderTop:`1px solid ${OC.bd2}`,padding:'4px 12px',fontSize:12,lineHeight:'20px'}}>
   <div style={{color:OC.cyan}}>⇒ section :chorus · bar 15/32</div>
   <div style={{color:OC.pink}}>&gt;</div></div>
  <div style={{height:24,display:'flex',alignItems:'center',gap:14,padding:'0 12px',borderTop:`1px solid ${OC.bd2}`,background:'#090f1e',fontSize:10,color:OC.dim}}>
   <span>VIZ</span><span>∿ wave</span><span>⊠ spec</span><span>◐ loud</span><span>║ beat</span><span>⊟ roll</span><span style={{color:OC.pink,border:`1px solid ${OC.pink}88`,padding:'1px 6px'}}>▬ seg</span></div>
  <div style={{background:'#060b17',padding:'4px 0'}}>{lanes.map(([t,segs])=>{const m=t==='keys';return <div key={t} style={{position:'relative',height:20,margin:'2px 0',opacity:m?.3:1}}>
   {segs.map(([l,w],k)=><div key={k} style={{position:'absolute',left:l+'%',width:`calc(${w}% - 2px)`,top:2,bottom:2,background:TRK_C[t]+'30',border:`1px solid ${TRK_C[t]}66`}}></div>)}
   <span style={{position:'absolute',left:8,top:2,fontSize:10,color:TRK_C[t]}}>:{t}</span></div>;})}</div>
  <div style={{height:24,display:'flex',alignItems:'center',justifyContent:'flex-end',padding:'0 12px',background:'#070d1c',borderTop:`1px solid ${OC.bd2}`,fontSize:10,color:OC.dim}}>Alt+Enter to eval</div>
 </div>;}

function Slider({label,v,dim}){
 return <div style={{display:'grid',gridTemplateColumns:'40px 1fr 30px',alignItems:'center',gap:6,height:18,paddingLeft:14,opacity:dim?.4:1}}>
  <span style={{color:OC.mid,fontSize:10}}>{label}</span>
  <div style={{position:'relative',height:2,background:OC.bd}}><div style={{position:'absolute',left:`calc(${v*100}% - 4px)`,top:-3,width:8,height:8,borderRadius:'50%',background:OC.cyan}}></div></div>
  <span style={{fontSize:10,color:OC.text,textAlign:'right'}}>{v.toFixed(2)}</span></div>;}

function CPTitle({children}){return <div style={{fontSize:9,letterSpacing:'.14em',color:OC.title,textTransform:'uppercase',margin:'2px 0 6px'}}>{children}</div>;}

function ContextPanel({w=195}){
 const tracks=[
  {n:'bass',st:'synth saw',p:[['amp',.7],['decay',.25]]},
  {n:'drums',p:[['amp',.85]],fx:[['filter',[['freq',.7],['q',.3]]]]},
  {n:'keys',muted:true},
  {n:'pad',p:[['amp',.35]],fx:[['reverb',[['mix',.8]]]]}];
 const sec={padding:'8px 10px',borderBottom:`1px solid ${OC.bd2}`};
 return <div style={{width:w,flexShrink:0,background:OC.cp,borderLeft:`1px solid ${OC.bd2}`,fontFamily:MONO,overflow:'hidden',display:'flex',flexDirection:'column'}}>
  <div style={{...sec,display:'flex',flexWrap:'wrap',gap:'4px 8px',alignItems:'center'}}>
   <span style={{color:OC.yellow,fontWeight:700,fontSize:13}}>124 BPM</span>
   <span style={{color:OC.dim,fontSize:10,border:`1px solid ${OC.bd}`,padding:'0 4px'}}>wasm</span>
   <span style={{color:OC.cyan,fontSize:10}}>● playing</span>
   <span style={{color:OC.cyan,fontSize:10,fontWeight:700}}>▶ chorus · bar 15/32</span></div>
  <div style={sec}><CPTitle>tracks</CPTitle>
   {tracks.map(t=><div key={t.n} style={{marginBottom:6,opacity:t.muted?.45:1}}>
    <div style={{display:'flex',gap:6,alignItems:'center',fontSize:12,height:20}}>
     <span style={{fontSize:9,color:t.muted?OC.dim:OC.cyan}}>{t.muted?'■':'▶'}</span>
     <span style={{color:OC.cyan}}>:{t.n}</span>
     <span style={{color:OC.mid,fontSize:10}}>{t.muted?'(muted)':t.st||''}</span></div>
    {(t.p||[]).map(([l,v])=><Slider key={l} label={l} v={v}/>)}
    {t.fx&&<div style={{paddingLeft:14,marginTop:2}}><div style={{fontSize:10,color:OC.mid,height:18}}>▾ fx ({t.fx.length})</div>
     {t.fx.map(([n,ps])=><div key={n}><div style={{fontSize:10,color:OC.text,paddingLeft:6,height:16}}>{n}</div>{ps.map(([l,v])=><Slider key={l} label={l} v={v}/>)}</div>)}</div>}
   </div>)}</div>
  <div style={sec}><CPTitle>fx</CPTitle>
   <div style={{fontSize:11,color:OC.text,height:18}}>delay</div><Slider label="time" v={.375}/><Slider label="fb" v={.4}/>
   <div style={{fontSize:11,color:OC.text,height:18,marginTop:4}}>reverb</div><Slider label="size" v={.8}/><Slider label="mix" v={.3}/></div>
  <div style={{...sec,borderBottom:'none'}}><CPTitle>sources</CPTitle><div style={{fontSize:10,color:OC.text,lineHeight:'1.5em'}}>♫ tidalcycles/Dirt-Samples <span style={{color:OC.mid}}>(217)</span></div></div>
 </div>;}

function AppShell({collapsed=false}){
 return <div style={{width:'100%',height:'100%',display:'flex',flexDirection:'column',background:OC.bg,color:OC.text,fontFamily:MONO}}>
  <TopNav/>
  <div style={{flex:1,minHeight:0,display:'flex'}}>
   {collapsed?<OutlineRail/>:<Outline/>}
   <div style={{flex:1,minWidth:0,display:'flex',flexDirection:'column'}}><Editor/><Bottom/></div>
   <ContextPanel/>
  </div></div>;}

Object.assign(window,{OC,ORow,Outline,OutlineRail,AppShell,MONO,Kbd,KIND_C,TRK_C,tok,Bottom,Slider});
