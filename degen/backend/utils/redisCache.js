const NodeCache = require('node-cache');
const cache = new NodeCache();
const redis = {
  async get(key) { return cache.get(key) ?? null; },
  async set(key, value, mode, ttl) { cache.set(key, value, mode === 'EX' ? ttl : 0); return 'OK'; },
  async setex(key, ttl, value) { cache.set(key, value, ttl); return 'OK'; },
  async keys(pattern) {
    const prefix = pattern.endsWith('*') ? pattern.slice(0, -1) : pattern;
    return cache.keys().filter(key => pattern.endsWith('*') ? key.startsWith(prefix) : key === prefix);
  }
};
module.exports = { redis, REDIS_KEY: setId => 'set:' + setId, CACHE_TTL_SEC: 86400 };
