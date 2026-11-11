const { isDegenAdmin } = require('./utils/degenAdmin');
const { ApplicationCommandOptionType: Type, CommandInteractionOptionResolver } = require('discord.js');
const prefix = String.fromCharCode(96);
const pinecone = (process.env.MUSIC_ONLY_CHANNEL_ID || '');

function splitArgs(text) {
    const tokens = text.match(/"[^"]*"|'[^']*'|[^\s]+/g) || [];
    return tokens.map(token => /^["']/.test(token) && token.endsWith(token[0]) ? token.slice(1, -1) : token);
}

function createBetArgs(input, args) {
    const durationSuffix = /^(\d+)_?min(?:ute)?s?$/i;
    if (/^["']/.test(input)) {
        const duration = args[2]?.match(durationSuffix);
        return duration ? [args[0], args[1], duration[1], ...args.slice(3)] : args;
    }
    const questionEnd = input.indexOf('?');
    if (questionEnd < 0) {
        throw new Error('Put the bet description and comma-separated choices in quotes.');
    }
    const question = input.slice(0, questionEnd + 1).trim();
    let choices = input.slice(questionEnd + 1).trim();
    const duration = choices.match(/\s+(\d+)(?:_?min(?:ute)?s?)$/i);
    if (duration) choices = choices.slice(0, duration.index).trim();
    if (choices.split(',').filter(choice => choice.trim()).length < 2) {
        throw new Error('Add at least two choices separated by commas.');
    }
    return duration ? [question, choices, duration[1]] : [question, choices];
}

async function optionsFor(message, schema = [], args) {
    const result = [];
    for (const option of schema) {
        if (option.type === Type.Subcommand || option.type === Type.SubcommandGroup) {
            if (args[0]?.toLowerCase() !== option.name) continue;
            return [{ name: option.name, type: option.type, options: await optionsFor(message, option.options, args.slice(1)) }];
        }
        const raw = args.shift();
        if (raw === undefined || raw === '-') {
            if (option.required) throw new Error('Missing ' + option.name + '.');
            continue;
        }
        let value = raw;
        const entry = { name: option.name, type: option.type };
        if (option.type === Type.Integer || option.type === Type.Number) {
            value = Number(raw);
            if (!Number.isFinite(value) || (option.type === Type.Integer && !Number.isSafeInteger(value))
                    || value < (option.minValue ?? -Infinity) || value > (option.maxValue ?? Infinity)) {
                throw new Error('Invalid number for ' + option.name + '.');
            }
        } else if (option.type === Type.Boolean) {
            if (!/^(true|false)$/i.test(raw)) throw new Error(option.name + ' must be true or false.');
            value = raw.toLowerCase() === 'true';
        } else if ([Type.User, Type.Channel, Type.Role, Type.Mentionable].includes(option.type)) {
            const match = raw.match(/^(?:<[@#][!&]?)?(\d{17,20})>?$/);
            if (!match) throw new Error('Mention or enter an ID for ' + option.name + '.');
            value = match[1];
            if (option.type === Type.User || (option.type === Type.Mentionable && !raw.startsWith('<@&'))) {
                entry.member = await message.guild.members.fetch(value);
                entry.user = entry.member.user;
            } else if (option.type === Type.Channel) {
                entry.channel = await message.guild.channels.fetch(value);
                if (!entry.channel || (option.channelTypes?.length && !option.channelTypes.includes(entry.channel.type))) {
                    throw new Error('Invalid channel for ' + option.name + '.');
                }
            } else {
                entry.role = await message.guild.roles.fetch(value);
                if (!entry.role) throw new Error('Role not found.');
            }
        } else if (option.type === Type.Attachment) {
            entry.attachment = message.attachments.get(raw) || message.attachments.find(item => item.name === raw);
            if (!entry.attachment) throw new Error('Attach a file and use its filename for ' + option.name + '.');
            value = entry.attachment.id;
        } else if (raw.length < (option.minLength ?? 0) || raw.length > (option.maxLength ?? Infinity)) {
            throw new Error('Invalid length for ' + option.name + '.');
        }
        if (option.choices?.length && !option.choices.some(choice => choice.value === value)) {
            throw new Error(option.name + ' must be one of: ' + option.choices.map(choice => choice.value).join(', ') + '.');
        }
        result.push({ ...entry, value });
    }
    if (schema.some(option => option.type === Type.Subcommand || option.type === Type.SubcommandGroup)) {
        throw new Error('Choose a subcommand: ' + schema.map(option => option.name).join(', ') + '.');
    }
    if (args.length) throw new Error('Too many arguments. Use quotes around multi-word values.');
    return result;
}

function messageInteraction(message, commandName, options) {
    let response;
    function payload(input) {
        const data = typeof input === 'string' ? { content: input } : { ...input };
        const { ephemeral, flags: ignoredFlags, fetchReply, withResponse, ...output } = data;
        return { allowedMentions: { parse: [] }, ...output };
    }
    async function send(input) {
        return message.channel.send(payload(input));
    }
    const interaction = {
        id: message.id, user: message.author, member: message.member, client: message.client,
        guild: message.guild, guildId: message.guildId, channel: message.channel, channelId: message.channelId,
        memberPermissions: message.member.permissionsIn(message.channel), commandName, options,
        deferred: false, replied: false,
        isCommand: () => true, isChatInputCommand: () => true,
        isAutocomplete: () => false, isButton: () => false, isStringSelectMenu: () => false,
        async deferReply() {
            this.deferred = true;
        },
        async reply(input) {
            response = await send(input);
            this.replied = true;
            return response;
        },
        async editReply(input) {
            const output = payload(input);
            return response ? response.edit(output) : this.reply(input);
        },
        async fetchReply() { return response; },
        async deleteReply() { if (response) await response.delete(); },
        followUp: send,
        async showModal() { throw new Error('Open this form with the slash command.'); },
    };
    return interaction;
}

function nearestName(input, names) {
    if (!input || input.length > 40) return null;
    let best = null;
    let score = 3;
    for (const name of names) {
        let row = Array.from({ length: name.length + 1 }, (_, i) => i);
        for (let i = 0; i < input.length; i++) {
            const next = [i + 1];
            for (let j = 0; j < name.length; j++) {
                next.push(Math.min(row[j + 1] + 1, next[j] + 1, row[j] + (input[i] !== name[j])));
            }
            row = next;
        }
        if (row[name.length] < score) {
            best = name;
            score = row[name.length];
        }
    }
    return best;
}

function usageFor(name, options, args) {
    const start = prefix + 'degen ' + name;
    if (name === 'jackpot') {
        const entered = args.find(arg => /^\d+$/.test(arg));
        const amount = Number.isSafeInteger(Number(entered)) && Number(entered) >= 10 ? Number(entered) : 10;
        return start + ' view\n' + start + ' contribute ' + amount;
    }
    if (name === 'bail') return start + ' @player\n' + start + ' all';
    if (name === 'cs2open') return start + ' recoil';
    if (name === 'createbet') {
        return start + ' "Who wins the match?" "Team A, Team B" 1';
    }
    const samples = {
        amount: '100', points: '100', bet_id: 'K7M4X', option: 'Team A',
        winning_option: 'Team A', description: 'Who wins the match?', options: 'Team A, Team B',
        question: 'Who wins the match?', reason: 'Off-topic messages', case: 'recoil',
        pokemon: 'Pikachu', pokemonid: '25', name: 'Pikachu', item: 'Poké Ball',
        skin: 'Ripple', skin_id: 'SKIN_ID', code: 'YOUR_CODE', search: 'AK-47',
        duration: '10m', duration_minutes: '10', additional_minutes: '10',
        choice: 'heads', role: 'admin', action: 'view',
    };
    function example(option, choice) {
        if (choice !== undefined) return String(choice);
        if (option.choices?.length) return String(option.choices[0].value);
        if (option.type === Type.Integer || option.type === Type.Number) {
            const preferred = Number(samples[option.name]) || (option.name === 'limit' ? 5 : 1);
            return String(Math.min(option.maxValue ?? Infinity, Math.max(option.minValue ?? 0, preferred)));
        }
        if (option.type === Type.Boolean) return 'true';
        if (option.type === Type.User || option.type === Type.Mentionable) return '@player';
        if (option.type === Type.Channel) return '#channel';
        if (option.type === Type.Role) return '@role';
        if (option.type === Type.Attachment) return 'your-file.png';
        return samples[option.name] || 'YOUR_' + option.name.toUpperCase();
    }
    function quote(value) {
        return /\s/.test(value) ? '"' + value + '"' : value;
    }
    function formats(items, path, remaining) {
        const subcommands = items.filter(item => item.type === Type.Subcommand || item.type === Type.SubcommandGroup);
        if (subcommands.length) {
            const selected = subcommands.find(item => item.name === remaining[0]?.toLowerCase());
            const ordered = selected ? [selected, ...subcommands.filter(item => item !== selected)] : subcommands;
            return ordered.flatMap(item => formats(item.options || [], path + ' ' + item.name,
                item === selected ? remaining.slice(1) : []));
        }
        const firstChoice = items.find(item => item.choices?.length && item.required);
        const choices = firstChoice ? firstChoice.choices.map(item => item.value) : [undefined];
        const required = items.filter(item => item.required);
        const examples = choices.map(choice => [path, ...required.map(item =>
            quote(example(item, item === firstChoice ? choice : undefined)))].join(' '));
        const optional = items.find(item => !item.required);
        if (optional && !firstChoice) {
            examples.push([path, ...required.map(item => quote(example(item))), quote(example(optional))].join(' '));
        }
        return examples;
    }
    return formats(options || [], start, args).slice(0, 10).join('\n');
}

function commandArgsFor(name, rawArgs, args) {
    if (name === 'createbet') return createBetArgs(rawArgs, args);
    if (name === 'bail') {
        if (args.length === 1 && args[0].toLowerCase() === 'all') return ['-', 'true'];
        if (args.length === 2 && args[0] === '-' && args[1].toLowerCase() === 'true') return ['-', 'true'];
        if (args.length === 1 && /^(?:<@!?\d{17,20}>|\d{17,20})$/.test(args[0])) return args;
        throw new Error('Mention the player you want to bail out, or type all to bail everyone.');
    }
    if (name === 'jackpot') {
        const action = args[0]?.toLowerCase();
        if (action === 'view' && args.length === 1) return [action];
        if (action === 'contribute' && args.length === 2 && /^\d+$/.test(args[1])
                && Number.isSafeInteger(Number(args[1])) && Number(args[1]) >= 10) {
            return [action, args[1]];
        }
        throw new Error('View the jackpot or contribute at least 10 points.');
    }
    if (name === 'cs2open' && args.length) return [args.join(' ')];
    return args;
}

function formatHint(usage) {
    const fence = prefix.repeat(3);
    const safeLines = usage.replaceAll(fence, prefix + ' ' + prefix.repeat(2)).split('\n');
    const examples = [];
    let length = 0;
    for (const line of safeLines) {
        if (length + line.length > 1250) break;
        examples.push(line);
        length += line.length + 1;
    }
    const lines = examples.join('\n');
    const notes = [];
    if (/@player|@role|#channel/.test(lines)) notes.push('Replace the sample mentions with real Discord mentions.');
    if (/K7M4X/.test(lines)) notes.push('Use your actual bet ID. Find open bets with ' + prefix + 'degen listbets.');
    if (/YOUR_|SKIN_ID|your-file\.png/.test(lines)) notes.push('Replace sample IDs, text, or filenames with your own values.');
    const note = notes.length ? '\n' + notes.join(' ') : '';
    return '**Try this format**\n' + fence + '\n' + lines + '\n' + fence + note;
}

function registerPrefixCommands(client, names, execute) {
    const guildCommands = new Map();
    client.on('messageCreate', async message => {
        if (!message.guild || !message.member || message.author.bot || message.webhookId || message.channelId === pinecone) return;
        const input = message.content.trim();
        const directCatch = input.toLowerCase() === prefix + 'pokecatch';
        const directBush = /^bush(?:\s|$)/i.test(input.slice(1)) && input.startsWith(prefix);
        const group = input.slice(1).match(/^(?:degen|d)\s+(.+)$/is);
        if (!directCatch && !directBush && (!input.startsWith(prefix) || !group)) return;
        const content = directCatch ? 'pokecatch' : directBush ? input.slice(1) : group[1].trim();
        const [name, ...args] = splitArgs(content);
        const rawArgs = content.slice(name?.length || 0).trim();
        const typedName = name?.toLowerCase() === 'bal' ? 'balance' : name?.toLowerCase();
        const typo = !names.has(typedName);
        const commandName = typo ? nearestName(typedName, names) : typedName;
        if (!commandName) {
            await message.reply({ content: 'Use ' + String.fromCharCode(92) + prefix + 'degen to see the command list.',
                allowedMentions: { parse: [], repliedUser: false } }).catch(() => {});
            return;
        }
        let usage;
        try {
            if (typedName === 'bush') {
                if (args.length) throw new Error('Search a bush with ' + prefix + 'bush (no extra words needed).');
                await execute(messageInteraction(message, 'bush', new CommandInteractionOptionResolver(client, [])));
                return;
            }
            if (!guildCommands.has(message.guildId)) {
                guildCommands.set(message.guildId, { until: Date.now() + 60_000, commands: await message.guild.commands.fetch() });
            }
            let cached = guildCommands.get(message.guildId);
            if (cached.until <= Date.now()) {
                cached = { until: Date.now() + 60_000, commands: await message.guild.commands.fetch() };
                guildCommands.set(message.guildId, cached);
            }
            const schema = cached.commands.find(command => command.name === commandName && command.type === 1);
            if (!schema) throw new Error('This Degen command is not registered in this server.');
            const required = schema.defaultMemberPermissions;
            if (required && !isDegenAdmin(message.guildId, message.author.id) && (required.bitfield === 0n ? !message.member.permissions.has('Administrator')
                    : !message.member.permissions.has(required))) {
                throw new Error('You do not have Discord permission to use that command.');
            }
            usage = usageFor(commandName, schema.options, args);
            const commandArgs = commandArgsFor(commandName, rawArgs, args);
            const options = await optionsFor(message, schema.options, commandArgs);
            if (typo) {
                const corrected = prefix + 'degen ' + commandName + (rawArgs ? ' ' + rawArgs : '');
                await message.reply({ content: formatHint(corrected),
                    allowedMentions: { parse: [], repliedUser: false } }).catch(() => {});
                return;
            }
            usage = undefined;
            await execute(messageInteraction(message, commandName, new CommandInteractionOptionResolver(client, options)));
        } catch (error) {
            const text = String(error.message).slice(0, 300);
            await message.reply({ content: usage ? text + '\n' + formatHint(usage) : text,
                allowedMentions: { parse: [], repliedUser: false } }).catch(() => {});
        }
    });
}
module.exports = { registerPrefixCommands, messageInteraction };
