process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const Module = require('node:module');
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'bush-test-'));
process.env.BUSH_COOLDOWN_FILE = path.join(tmp, 'cooldowns.json');
const original = Module._load;
let ready = true;
Module._load = function(request, parent, main) {
    if (request === '../utils/pokeCache') return {
        isKantoCacheReady:()=>ready, isGen2CacheReady:()=>ready, isGen3CacheReady:()=>ready, isGen4CacheReady:()=>ready,
        getRandomPokemonIdByGeneration:()=>431,
        getPokemonDataById:async()=>({name:'glameow',types:[{type:{name:'normal'}}],sprites:{other:{'official-artwork':{front_default:'https://example.com/pokemon.png'}}}}),
    };
    if (request === 'node-fetch') return async()=>({json:async()=>({id:431,flavor_text_entries:[]})});
    return original.apply(this, arguments);
};
const spawn = require('./commands/pokespawn');
const timers = require('./utils/despawnTimerManager');
let bush = require('./commands/bush');
let next = 0;
function interaction(user='u', channel='c') {
    const output=[];
    const result={id:String(++next),react:async emoji=>{assert.equal(emoji,'🔴');},edit:async data=>output.push(data)};
    const i={guildId:'g',channelId:channel,user:{id:user},memberPermissions:{has:()=>false},output,
        client:{channels:{fetch:async()=>({messages:{fetch:async()=>result}})}},
        deferReply:async()=>{i.deferred=true;},reply:async data=>{i.replied=true;output.push(data);return result;},
        editReply:async data=>{output.push(data);return result;}};
    return i;
}
(async()=>{
    let i=interaction(); await bush.execute(i);
    assert.equal(spawn.activeSpawns.get('c').pokemonId,431);
    const embed=i.output[0].embeds[0].toJSON();
    assert.ok(embed.thumbnail);assert.ok(!embed.image);assert.ok(!embed.title.includes('pokeball'));
    assert.match(embed.description,/bush/);
    assert.ok(JSON.parse(fs.readFileSync(process.env.BUSH_COOLDOWN_FILE)).hasOwnProperty('g:u'));
    delete require.cache[require.resolve('./commands/bush')]; bush=require('./commands/bush');
    i=interaction('u','another');await bush.execute(i);assert.match(i.output[0],/another bush in/);
    i=interaction('v');await bush.execute(i);assert.match(i.output[0],/already a Pokémon/);
    assert.ok(!JSON.parse(fs.readFileSync(process.env.BUSH_COOLDOWN_FILE)).hasOwnProperty('g:v'));
    const old=timers.getDespawnTimer('c');const expire=old.timeout._onTimeout;timers.clearAllDespawnTimers('c');await expire();
    assert.ok(!spawn.activeSpawns.has('c'));
    const gone=i; // Despawn uses the original encounter message.

    spawn.activeSpawns.clear();
    ready=false;i=interaction('v');await bush.execute(i);assert.match(i.output[0].content,/loading/);
    assert.ok(!JSON.parse(fs.readFileSync(process.env.BUSH_COOLDOWN_FILE)).hasOwnProperty('g:v'));
    ready=true;
    const a=interaction('w','one'),b=interaction('w','two');await Promise.all([bush.execute(a),bush.execute(b)]);
    assert.equal(spawn.activeSpawns.size,1);assert.match(b.output[0],/already being prepared/);
    i=interaction('admin'); await spawn.execute(i);assert.match(i.output[0].content,/Only admins/);
    for(const channel of spawn.activeSpawns.keys()) timers.clearAllDespawnTimers(channel);
    const {registerPrefixCommands}=require('./prefixCommands');let listener;const seen=[];
    const client={on:(_,fn)=>{listener=fn;}};
    registerPrefixCommands(client,new Set(['bush']),async i=>seen.push(i.commandName));
    const message={client,guild:{},member:{permissionsIn:()=>({})},author:{id:'p'},guildId:'g',channelId:'c'};
    for(const text of ['`bush','`degen bush','`d bush']) await listener({...message,content:text});
    await listener({...message,channelId:'0',content:'`bush'});
    assert.deepEqual(seen,['bush','bush','bush']);
    console.log('PASS: bush encounters, persisted hourly cooldown, failed-search refunds, concurrent searches, admin guard, smaller artwork, prefix aliases and channel restriction.');
})().catch(error=>{console.error(error);process.exitCode=1;}).finally(()=>{
    Module._load=original;
    for(const channel of spawn.activeSpawns.keys()) timers.clearAllDespawnTimers(channel);
    fs.rmSync(tmp,{recursive:true,force:true});
});
