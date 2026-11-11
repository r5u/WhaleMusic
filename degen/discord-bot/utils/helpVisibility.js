const { isDegenAdmin } = require('./degenAdmin');
const axios = require('axios');

async function helpAccess(interaction, backendUrl) {
    if (isDegenAdmin(interaction.guildId, interaction.user.id)) return { admin: true, superadmin: true };
    let role = 'user';
    try {
        const response = await axios.get(`${backendUrl}/users/discord/${interaction.user.id}`, {
            headers: { 'x-guild-id': interaction.guildId, 'x-user-id': interaction.user.id }, timeout: 3000,
        });
        role = response.data.role;
    } catch (_) { /* Restricted help stays hidden when role lookup is unavailable. */ }
    return {
        superadmin: role === 'superadmin',
        admin: ['admin', 'superadmin'].includes(role) || !!interaction.memberPermissions?.has('Administrator'),
    };
}

function visibleHelp(embed, access) {
    const restricted = new Set();
    if (!access.superadmin) for (const name of ['changerole', 'resetcooldowns', 'spawncustompokemon', 'pokesteal']) restricted.add(name);
    if (!access.admin) for (const name of ['pokespawn', 'setpokechannel', 'setlogchannel', 'weekend', 'doubleweekend']) restricted.add(name);
    const fields = (embed.fields || []).map(field => {
        let hidden = false;
        const value = field.value.split('\n').filter(line => {
            const command = line.match(/`degen ([\w-]+)/);
            if (command) hidden = restricted.has(command[1]);
            return !hidden;
        }).join('\n').trim();
        return { ...field, value };
    }).filter(field => field.value);
    return { ...embed, fields };
}
module.exports = { helpAccess, visibleHelp };
