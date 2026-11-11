require('dotenv').config();
const mongoose = require('mongoose');
const User = require('./models/User');
(async () => {
  try {
    await mongoose.connect(process.env.MONGODB_URI);
    const identity = { discordId: process.env.BOT_OWNER_ID, guildId: process.env.DEFAULT_GUILD_ID };
    if (!/^\d{17,20}$/.test(identity.discordId || '') || !/^\d{17,20}$/.test(identity.guildId || '')) throw new Error('Configure BOT_OWNER_ID and DEFAULT_GUILD_ID first.');
    const user = await User.findOneAndUpdate(identity, { $setOnInsert: { username: identity.discordId } }, { upsert: true, new: true });
    await User.updateOne({ _id: user._id, role: { $ne: 'superadmin' } }, { $set: { role: 'superadmin' } });
    const result = await User.findById(user._id).select('role').lean();
    console.log('Degen role: ' + result.role);
  } finally {
    await mongoose.disconnect();
  }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
