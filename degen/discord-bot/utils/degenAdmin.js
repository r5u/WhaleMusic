exports.isDegenAdmin = (guildId, userId) => {
  const guild = process.env.GUILD_ID_1 || process.env.DEFAULT_GUILD_ID;
  return guildId === guild && [process.env.BOT_OWNER_ID, process.env.BOT_CO_OWNER_ID].filter(Boolean).includes(userId);
};
