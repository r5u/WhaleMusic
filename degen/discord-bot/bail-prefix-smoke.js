process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const { registerPrefixCommands } = require('./prefixCommands');
let handler, hint;
const calls = [];
const client={on(_,fn){handler=fn;}};
registerPrefixCommands(client,new Set(['bail']), async i =>
    calls.push([i.options.getUser('user')?.id ?? null, i.options.getBoolean('all')]));
const id='0', prefix=String.fromCharCode(96)+'degen bail';
const msg={client,guildId:'1',channelId:'2',author:{bot:false},member:{permissionsIn(){return {};}},
    guild:{members:{async fetch(id){return {user:{id}};}},commands:{async fetch(){return [
        {name:'bail',type:1,options:[{name:'user',type:6},{name:'all',type:5}]}
    ];}}}, async reply(payload){hint=payload.content;}};
(async()=>{
  await handler({...msg,content:prefix+' <@'+id+'>'});
  await handler({...msg,content:prefix+' '+id});
  await handler({...msg,content:prefix+' all'});
  await handler({...msg,content:prefix});
  assert.deepEqual(calls,[[id,null],[id,null],[null,true]]);
  assert.match(hint,/degen bail @player/);
  console.log('Mention, user ID, all, and missing-target hint passed.');
})().catch(e=>{console.error(e);process.exitCode=1;});
