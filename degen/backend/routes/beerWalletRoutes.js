const express = require('express');
const mongoose = require('mongoose');
const { timingSafeEqual } = require('node:crypto');
const User = require('../models/User');
const Wallet = require('../models/Wallet');

const router = express.Router();
const State = mongoose.model('MusicBotBeerState', new mongoose.Schema({
  _id: String,
  revision: { type: Number, default: 0 },
  properties: mongoose.Schema.Types.Mixed,
  migrated: { type: Boolean, default: false },
  separated: { type: Boolean, default: false },
}));
const id = /^\d{1,20}$/;
const balanceKey = /^(\d{1,20})\.ml$/;
const propertyKey = /^\d{1,20}\.(?:rank|last|lastReset|ml|glass|tap|cooldown)$/;
const validBalance = value => Number.isSafeInteger(Number(value)) && Number(value) >= 0;

router.use((req, res, next) => {
  const secret = process.env.BEER_BRIDGE_SECRET;
  const actual = Buffer.from(req.get('authorization') || '');
  const expected = Buffer.from('Bearer ' + secret);
  if (!secret || actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
    return res.sendStatus(401);
  }
  next();
});

router.post('/:guild', async (req, res) => {
  const guildId = req.params.guild;
  const { legacy = {}, changes, revision, expected = {}, conversion } = req.body;
  if (!id.test(guildId) || !legacy || typeof legacy !== 'object'
      || (changes !== undefined && (!changes || typeof changes !== 'object'))
      || [...Object.keys(legacy), ...Object.keys(changes || {})].some(key => !propertyKey.test(key))) {
    return res.sendStatus(400);
  }
  if (conversion !== undefined && (changes !== undefined || !id.test(conversion?.user || '')
      || !Number.isSafeInteger(conversion.amount) || conversion.amount < 1
      || !['toBeer', 'toDegen'].includes(conversion.direction)
      || (conversion.direction === 'toDegen' && conversion.amount > Math.floor(Number.MAX_SAFE_INTEGER / 10))
      || (conversion.direction === 'toBeer' && (conversion.amount % 100 !== 0 || !validBalance(conversion.capacity))))) {
    return res.sendStatus(400);
  }
  try {
    await State.updateOne({ _id: guildId }, { $setOnInsert: { revision: 0 } }, { upsert: true });
    let result;
    await mongoose.connection.transaction(async session => {
      const state = await State.findById(guildId).session(session);
      if (!state.separated) {
        const properties = { ...(state.migrated ? state.properties : legacy) };
        if (state.migrated) {
          const users = await User.find({ guildId }, 'discordId').session(session).lean();
          const ids = new Map(users.map(user => [String(user._id), user.discordId]));
          const wallets = await Wallet.find({ guildId }, 'user balance').session(session).lean();
          for (const wallet of wallets) {
            const discordId = ids.get(String(wallet.user));
            if (discordId && validBalance(wallet.balance)) properties[discordId + '.ml'] = String(wallet.balance);
          }
        }
        state.properties = properties;
        state.separated = true;
        await state.save({ session });
      }
      if (conversion !== undefined) {
        if (state.revision !== revision) throw Object.assign(new Error('State changed'), { conflict: true });
        const key = conversion.user + '.ml';
        const properties = { ...state.properties };
        const balance = Number(properties[key] ?? 0);
        const toBeer = conversion.direction === 'toBeer';
        const ml = toBeer ? conversion.amount / 100 : -conversion.amount;
        const points = toBeer ? -conversion.amount : conversion.amount * 10;
        if (toBeer && balance + ml > Number(conversion.capacity)) {
          const room = Math.max(0, Number(conversion.capacity) - balance);
          result = { error: 'That would add ' + ml + 'mL. Your glass has ' + balance + '/' + conversion.capacity
            + 'mL, with ' + room + 'mL free. ' + (room > 0 ? 'Try `convertdegen ' + room * 100 + '. ' : '')
            + 'Use `drink or `buyglass to increase capacity. No Degen points were spent.' };
          return;
        }
        if (!toBeer && balance < conversion.amount) {
          result = { error: 'You do not have enough beer to convert.' };
          return;
        }
        let user = await User.findOne({ discordId: conversion.user, guildId }).session(session).lean();
        if (!toBeer) {
          user = user || await User.findOneAndUpdate({ discordId: conversion.user, guildId },
            { $setOnInsert: { username: conversion.user } }, { upsert: true, new: true, session });
          await Wallet.updateOne({ user: user._id, guildId }, { $setOnInsert: { balance: 0 } },
            { upsert: true, session });
        }
        const limit = toBeer ? { $gte: conversion.amount } : { $lte: Number.MAX_SAFE_INTEGER - points };
        const wallet = user && await Wallet.findOneAndUpdate(
          { user: user._id, guildId, balance: limit },
          { $inc: { balance: points, __v: 1 } }, { new: true, session });
        if (!wallet) {
          result = { error: toBeer ? 'You do not have enough Degen points.' : 'Your Degen wallet is full.' };
          return;
        }
        properties[key] = String(balance + ml);
        state.properties = properties;
        state.revision += 1;
        await state.save({ session });
        result = { ml, balance: balance + ml, points: wallet.balance };
        return;
      }
      if (changes !== undefined) {
        if (state.revision !== revision) throw Object.assign(new Error('State changed'), { conflict: true });
        const properties = { ...state.properties };
        for (const [key, value] of Object.entries(changes)) {
          const match = key.match(balanceKey);
          if (match) {
            if (!validBalance(value) || !validBalance(expected[key])) throw new Error('Invalid balance');
            if (Number(properties[key] ?? 0) !== Number(expected[key])) {
              throw Object.assign(new Error('Beer balance changed'), { conflict: true });
            }
            properties[key] = String(value);
          } else if (value === null) {
            delete properties[key];
          } else {
            properties[key] = String(value);
          }
        }
        state.properties = properties;
        state.revision += 1;
        await state.save({ session });
        result = { revision: state.revision };
      } else {
        result = { revision: state.revision, properties: state.properties || {} };
      }
    });
    res.json(result);
  } catch (error) {
    if (error.conflict) return res.sendStatus(409);
    console.error('Beer wallet operation failed:', error.message);
    res.sendStatus(503);
  }
});

module.exports = router;
