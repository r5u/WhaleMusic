const assert = require('node:assert/strict');
const express = require('express');
const mongoose = require('mongoose');
const Bet = require('./models/Bet');
const { router: betRouter } = require('./routes/betRoutes');
(async () => {
  await mongoose.connect('mongodb://127.0.0.1:27028/?replicaSet=beer', { dbName: 'bet_id_smoke_' + Date.now(), serverSelectionTimeoutMS:3000 });
  let server;
  try {
    const fields = { guildId:'1', description:'test', options:['A','B'], creator:new mongoose.Types.ObjectId() };
    const legacy = await Bet.create(fields);
    await Bet.backfillShortIds();
    const old = await Bet.findById(legacy._id);
    const fresh = await new Bet(fields).saveWithShortId();
    assert.match(old.shortId,/^[A-Z0-9]{5}$/);
    assert.match(fresh.shortId,/^[A-Z0-9]{5}$/);
    assert.notEqual(old.shortId, fresh.shortId);
    server = express().use(express.json()).use(betRouter).listen(0,'127.0.0.1');
    await new Promise(resolve=>server.once('listening',resolve));
    const base = 'http://127.0.0.1:'+server.address().port+'/';
    for (const id of [String(old._id), old.shortId.toLowerCase()]) {
      const response = await fetch(base+id,{headers:{'x-guild-id':'1'}});
      assert.equal(response.status,200);
    }
    assert.equal((await fetch(base+old.shortId,{headers:{'x-guild-id':'2'}})).status,404);
    console.log('New and backfilled IDs, full and short lookup, and server isolation passed.');
  } finally {
    if(server) server.close();
    await mongoose.connection.dropDatabase();
    await mongoose.disconnect();
  }
})().catch(e=>{console.error(e.message);process.exitCode=1;});


