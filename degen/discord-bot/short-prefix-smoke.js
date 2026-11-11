process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const { registerPrefixCommands } = require('./prefixCommands');
let callback;
const calls=[];
const client={on(_,handler){callback=handler;}};
registerPrefixCommands(client,new Set(['balance','bail']),async interaction=>calls.push(interaction.commandName));
const msg={client,guildId:'1',channelId:'2',author:{bot:false},member:{permissionsIn(){return {};}},
 guild:{commands:{async fetch(){return [{name:'balance',type:1}];}}},
 async reply(){throw new Error('Unexpected usage suggestion');}};
(async()=>{
 const tick=String.fromCharCode(96);
 for(const content of ['d bal','degen bal','d balance']) await callback({...msg,content:tick+content});
 await callback({...msg,channelId:'0',content:tick+'d bal'});
 assert.deepEqual(calls,['balance','balance','balance']);
 console.log('Both prefixes and balance aliases work; Pinecone stays blocked.');
})().catch(e=>{console.error(e);process.exitCode=1;});
