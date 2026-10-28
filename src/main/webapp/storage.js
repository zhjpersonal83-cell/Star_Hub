'use strict';
// Only explicit, versioned fields are stored. Credentials and attachments never enter these records.
const StarStore = (() => {
  const PREFIX='starhub.v1.', storage=()=>window.localStorage;
  function read(name,fallback){try{const value=storage().getItem(PREFIX+name);return value===null?fallback:JSON.parse(value);}catch{return fallback;}}
  function write(name,value){try{storage().setItem(PREFIX+name,JSON.stringify(value));return true;}catch{return false;}}
  function remove(name){try{storage().removeItem(PREFIX+name);return true;}catch{return false;}}
  function preference(name,fallback){return read(name,fallback);}
  function selection(){const value=read('selection',{});return value&&typeof value==='object'&&!Array.isArray(value)?value:{};}
  function saveSelection(provider,models){return write('selection',{provider,models:Object.fromEntries([...models].filter(([p,m])=>typeof p==='string'&&typeof m==='string'))});}
  function historyEnabled(){return read('historyEnabled',false)===true;}
  function setHistoryEnabled(enabled){
    if(!enabled){const removed=remove('history');const saved=write('historyEnabled',false);return removed&&saved;}
    return write('historyEnabled',true);
  }
  function snapshot(chats,currentId){
    return {version:1,currentId,chats:chats.map(c=>({id:c.id,title:c.title,draft:c.draft||'',messages:c.messages.filter(m=>typeof m.content==='string'&&m.content.length).map(m=>({role:m.role,content:m.content,provider:m.provider||'',model:m.model||'',providerName:m.providerName||'',attachmentOmitted:Boolean(m.attachmentOmitted||m.image||m.media?.length),incomplete:Boolean(m.incomplete||m.revealing)}))}))};
  }
  function saveHistory(chats,currentId){
    if(!historyEnabled())return true;
    const data=snapshot(chats,currentId);
    if(data.chats.length>500||data.chats.some(c=>c.messages.length>1000||c.messages.some(m=>m.content.length>500000))||JSON.stringify(data).length>2000000)return false;
    return write('history',data);
  }
  function loadHistory(){
    if(!historyEnabled())return null;
    const value=read('history',null);
    if(!value||value.version!==1||!Array.isArray(value.chats)||value.chats.length>500||JSON.stringify(value).length>2000000)return null;
    const clean=[];const seen=new Set();
    for(const item of value.chats){
      if(!item||typeof item.id!=='string'||item.id.length>100||seen.has(item.id)||typeof item.title!=='string'||!Array.isArray(item.messages)||item.messages.length>1000)continue;
      seen.add(item.id);const messages=[];
      for(const m of item.messages){
        if(!m||!['user','assistant'].includes(m.role)||typeof m.content!=='string'||m.content.length>500000)continue;
        const record={role:m.role,content:m.content,attachmentOmitted:m.attachmentOmitted===true,incomplete:m.incomplete===true};
        for(const field of ['provider','model','providerName'])record[field]=typeof m[field]==='string'?m[field].slice(0,200):'';
        messages.push(record);
      }
      clean.push({id:item.id,title:item.title.slice(0,200),draft:typeof item.draft==='string'?item.draft.slice(0,30000):'',messages});
    }
    return clean.length?{chats:clean,currentId:value.currentId}:null;
  }
  return {PREFIX,preference,write,selection,saveSelection,historyEnabled,setHistoryEnabled,snapshot,saveHistory,loadHistory};
})();
if(typeof module!=='undefined')module.exports=StarStore;
