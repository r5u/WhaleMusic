process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const { registerPrefixCommands } = require('./prefixCommands');
let handler, calls = 0;
const client = {on(name, fn) { assert.equal(name, 'messageCreate'); handler = fn; }};
registerPrefixCommands(client, new Set(['coinflip']), async interaction => {
  calls++;
  assert.equal(interaction.options.getInteger('amount'), 25);
  assert.equal(interaction.options.getString('side'), 'heads');
  await interaction.deferReply();
  await interaction.editReply('Bet processed');
});
const sent = [];
const message = {
  content: String.fromCharCode(96) + 'degen coinflip 25 heads', guildId: '1', channelId: '2',
  author: {bot:false, id:'3'}, member: {permissionsIn() {return {};}},
  guild: {commands: {async fetch() {return [{name:'coinflip',type:1,options:[
    {name:'amount',type:4,required:true},{name:'side',type:3,required:true}
  ]}];}}},
  async reply(message) {sent.push(message);}, channel: {async send(message) {sent.push(message); return {};}}, client
};
(async () => {
 await handler(message);
 assert.equal(calls,1);
 assert.equal(sent[0].content,'Bet processed');
 await handler({...message,channelId:'0'});
 assert.equal(calls,1);
 await handler({...message,content:String.fromCharCode(96)+'degen coinflip'});
 assert.match(sent.at(-1).content, /Try this format.*\n.*coinflip <amount> <side>/);
 await handler({...message,content:String.fromCharCode(96)+'degen coinflp'});
 assert.match(sent.at(-1).content,/degen coinflip/);
 assert.equal(calls,1);
 console.log('Valid input, missing arguments, typo suggestions, and Pinecone blocking passed.');
})().catch(error => { console.error(error); process.exitCode = 1; });

