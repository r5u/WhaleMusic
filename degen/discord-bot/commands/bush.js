const fs = require('node:fs');
const path = require('node:path');
const { SlashCommandBuilder } = require('discord.js');
const pokespawn = require('./pokespawn');

const HOUR = 60 * 60 * 1000;
const cooldownFile = process.env.BUSH_COOLDOWN_FILE || path.join(__dirname, '..', 'bush-cooldowns.json');
const searching = new Set();

function readCooldowns() {
    try {
        const data = JSON.parse(fs.readFileSync(cooldownFile, 'utf8'));
        if (!data || typeof data !== 'object' || Array.isArray(data)
                || Object.values(data).some(value => !Number.isFinite(value) || value < 0)) {
            throw new Error('Invalid bush cooldown data');
        }
        return data;
    } catch (error) {
        if (error.code === 'ENOENT') return {};
        throw error;
    }
}

function saveCooldowns(data) {
    const temporary = cooldownFile + '.tmp';
    fs.writeFileSync(temporary, JSON.stringify(data), 'utf8');
    fs.renameSync(temporary, cooldownFile);
}

module.exports = {
    data: new SlashCommandBuilder().setName('bush').setDescription('Search a bush for a Pokémon once per hour.'),
    async execute(interaction) {
        const key = `${interaction.guildId}:${interaction.user.id}`;
        if (searching.has(key)) return interaction.reply('🌿 Your bush search is already being prepared.');
        searching.add(key);
        let reserved = false;
        let created = false;
        try {
            const data = readCooldowns();
            const now = Date.now();
            const remaining = (data[key] || 0) + HOUR - now;
            if (remaining > 0) {
                return await interaction.reply(`🌿 You can search another bush in ${Math.floor(Math.ceil(remaining / 1000) / 60)}m ${Math.ceil(remaining / 1000) % 60}s. Use \`pokecatch to catch an active Pokémon.`);
            }
            // Reserve before asynchronous work, including across channels. Failed searches are refunded.
            data[key] = now;
            saveCooldowns(data);
            reserved = true;
            created = await pokespawn.execute(interaction, { fromBush: true }) === true;
        } catch (error) {
            console.error('[Bush] Search failed:', error);
            const message = '🌿 Your bush search could not finish. Try again shortly.';
            if (interaction.deferred || interaction.replied) await interaction.editReply(message);
            else await interaction.reply(message);
        } finally {
            try {
                if (reserved && !created) {
                    const data = readCooldowns();
                    delete data[key];
                    saveCooldowns(data);
                }
            } finally {
                searching.delete(key);
            }
        }
    },
};
