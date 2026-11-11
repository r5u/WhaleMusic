const assert = require('node:assert/strict');
const express = require('express');
const mongoose = require('mongoose');
process.env.BEER_BRIDGE_SECRET = 'temporary-smoke-secret';
const route = require('./routes/beerWalletRoutes');
const User = require('./models/User');
const Wallet = require('./models/Wallet');
(async () => {
  await mongoose.connect('mongodb://127.0.0.1:27028/?replicaSet=beer', { dbName: 'beer_conversion_smoke_' + Date.now() });
  let server;
  try {
    const user = await User.create({discordId:'2', guildId:'1', username:'test'});
    const wallet = await Wallet.create({user:user._id, guildId:'1', balance:50000});
    const state = mongoose.model('MusicBotBeerState');
    await state.create({_id:'1', migrated:true, properties:{'2.rank':'1'}});
    const app = express().use(express.json()).use(route);
    server = app.listen(0,'127.0.0.1');
    await new Promise(resolve => server.once('listening',resolve));
    async function post(body) {
      const res = await fetch('http://127.0.0.1:' + server.address().port + '/1',{method:'POST',headers:{authorization:'Bearer temporary-smoke-secret','content-type':'application/json'},body:JSON.stringify(body)});
      assert.equal(res.status,200);
      return res.json();
    }
    const before = await post({});
    assert.equal(before.properties['2.ml'],'50000');
    assert.equal(before.properties['2.rank'],'1');
    assert.equal((await Wallet.findById(wallet._id)).balance,50000);
    const result = await post({revision:before.revision,conversion:{user:'2',amount:10000,direction:"toBeer",capacity:100000}});
    assert.deepEqual(result,{ml:100,balance:50100,points:40000});
    const after = await post({});
    assert.equal(after.properties['2.ml'],'50100');
    const rejected = await post({revision:after.revision,conversion:{user:'2',amount:100,direction:"toBeer",capacity:50100}});
    assert.match(rejected.error,/room/);
    assert.equal((await Wallet.findById(wallet._id)).balance,40000);
    const reverse = await post({revision:after.revision,conversion:{user:'2',amount:100,direction:'toDegen'}});
    assert.deepEqual(reverse,{ml:-100,balance:50000,points:41000});
    const latest = await post({});
    const insufficient = await post({revision:latest.revision,conversion:{user:'2',amount:50001,direction:'toDegen'}});
    assert.match(insufficient.error,/enough beer/);
    assert.equal((await Wallet.findById(wallet._id)).balance,41000);
    console.log('Both conversion directions, insufficient balance, and full-glass rejection passed.');
  } finally {
    if (server) server.close();
    await mongoose.connection.dropDatabase();
    await mongoose.disconnect();
  }
})().catch(error => { console.error(error.message); process.exitCode=1; });





