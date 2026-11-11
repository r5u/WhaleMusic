process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const {registerPrefixCommands} = require('./prefixCommands');
const prefix = String.fromCharCode(96);
let listener, calls = 0;
const client = {on(_, callback) {listener = callback;}};
registerPrefixCommands(client, new Set(['pokecatch']), async interaction => {
    assert.equal(interaction.commandName, 'pokecatch');
    calls++;
});
const message = {client, guildId:'1',channelId:'2',author:{bot:false},
    member:{permissionsIn(){return {};}},
    guild:{commands:{async fetch(){return [{name:'pokecatch',type:1}];}}}};
(async () => {
    await listener({...message,content:prefix+'pokecatch'});
    await listener({...message,content:prefix+'degen pokecatch'});
    await listener({...message,channelId:'0',content:prefix+'pokecatch'});
    assert.equal(calls,2);
    console.log('Direct and Degen catch commands share the same dispatch; Pinecone stays blocked.');
})().catch(error=>{console.error(error);process.exitCode=1;});
