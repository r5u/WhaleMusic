process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const axios = require('axios');
const {readFileSync} = require('node:fs');
const inventory = require('./commands/cs2inventory');
const skins = Array.from({length:6}, () => ({weapon:'P250',skinName:'Ripple',rarity:'mil-spec',wear:'factory new',marketValue:50,imageUrl:'https://raw.githubusercontent.com/example/skin.png'}));
axios.get = async url => url.includes('/inventory/') ? { data: { inventory: { skins } } }
  : { data: readFileSync('inventory-smoke-input.png') };
(async () => {
 for (const page of [0,1]) {
   let deferred = false;
   await inventory.showSkins({
     isButton: () => true,
     async deferUpdate() { deferred = true; },
     async editReply(payload) {
       assert(deferred);
       assert.equal(payload.embeds.length,page===0?6:2);
       assert.equal(payload.files.length,page===0?5:1);
       assert.match(payload.embeds[1].toJSON().thumbnail.url,/^attachment:\/\//);
       if (page===0) require('node:fs').writeFileSync('inventory-smoke-output.png',payload.files[0].attachment);
     }
   },'123','1','http://localhost',page);
 }
 console.log('Button acknowledgement, paged thumbnails, and attachment rendering passed.');
})().catch(error => { console.error(error); process.exitCode = 1; });
