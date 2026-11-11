process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const { registerPrefixCommands } = require('./prefixCommands');
let handler, hint;
const calls = [];
const client = {on(_, callback) { handler = callback; }};
registerPrefixCommands(client, new Set(['jackpot']), async i =>
    calls.push([i.options.getString('action'),i.options.getInteger('amount')]));
const prefix = String.fromCharCode(96);
const msg = {client, guildId:'1',channelId:'2',author:{bot:false},member:{permissionsIn(){return {};}},
  guild:{commands:{async fetch(){return [{name:'jackpot',type:1,options:[
    {name:'action',type:3,required:true,choices:[{value:'view'},{value:'contribute'}]},
    {name:'amount',type:4}]}];}}},
  async reply(data){hint=data.content;}};
(async()=>{
  await handler({...msg, content:prefix+'degen jackpot 11'});
  assert.equal(calls.length,0);
  const commands=hint.split(prefix.repeat(3))[1].trim().split('\n');
  assert.equal(commands.length,2);
  for(const content of commands) await handler({...msg,content});
  assert.deepEqual(calls,[['view',null],['contribute',11]]);
  console.log('Both suggested jackpot commands work when copied back into the parser.');
})().catch(error=>{console.error(error);process.exitCode=1;});
