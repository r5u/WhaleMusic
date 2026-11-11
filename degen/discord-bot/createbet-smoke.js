process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const { registerPrefixCommands } = require('./prefixCommands');
let handler, calls = 0, reply;
const client = { on(name, fn) { handler = fn; } };
registerPrefixCommands(client, new Set(['createbet']), async interaction => {
  calls++;
  assert.equal(interaction.options.getString('description'), 'Who wins the match?');
  assert.equal(interaction.options.getString('options'), 'Team A, Team B');
  assert.equal(interaction.options.getInteger('duration_minutes'), 1);
});
const message = {
  client, guildId:'1', channelId:'2', author:{bot:false},
  member:{permissionsIn(){return {};}},
  guild:{commands:{async fetch(){return [{name:'createbet',type:1,options:[
    {name:'description',type:3,required:true},{name:'options',type:3,required:true},
    {name:'duration_minutes',type:4}
  ]}];}}},
  async reply(payload){reply=payload.content;}
};
(async () => {
  const prefix = String.fromCharCode(96) + 'degen createbet ';
  await handler({...message,content:prefix+'Who wins the match? Team A, Team B 1_minutes'});
  await handler({...message,content:prefix+'"Who wins the match?" "Team A, Team B" 1'});
  await handler({...message,content:prefix+'"Who wins the match?" "Team A, Team B" tomorrow'});
  await handler({...message,content:prefix.replace('createbet','creatbet')+'"Who wins the match?" "Team A, Team B" 1'});
  assert.equal(calls,2);
  const suggested = reply.split(String.fromCharCode(96).repeat(3))[1].trim();
  await handler({...message,content:suggested});
  assert.equal(calls,3);
  assert.match(reply, /"Who wins the match\?" "Team A, Team B" 1/);
  console.log('Natural input, quoted input, and concrete syntax hint passed.');
})().catch(error=>{console.error(error);process.exitCode=1;});

