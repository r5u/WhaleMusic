require('dotenv').config();
const mongoose = require('mongoose');
const fs = require('node:fs');
const path = require('node:path');
(async () => {
  try {
    const settings = JSON.parse(fs.readFileSync(path.resolve(__dirname, '../../settings.json'), 'utf8').replace(/^\uFEFF/, ''));
    await mongoose.connect(process.env.MONGODB_URI);
    const User = require('./models/User');
    await User.findOneAndUpdate({discordId:settings.ownerId,guildId:settings.guildId},
      {$set:{role:'superadmin'},$setOnInsert:{username:settings.ownerId}}, {upsert:true});
    if (settings.coOwnerId) await User.findOneAndUpdate({discordId:settings.coOwnerId,guildId:settings.guildId},
      {$set:{role:'superadmin'},$setOnInsert:{username:settings.coOwnerId}},{upsert:true});
    const CardPack = require('./models/CardPack');
    if (await CardPack.countDocuments() === 0) await CardPack.createDefaultPacks();
    await require('./services/cs2DataService').initialize();
    console.log('Owner access and game catalogs are ready. Existing player progress was preserved.');
  } finally { await mongoose.disconnect(); }
})().catch(error => { console.error(error.message);process.exitCode=1; });
