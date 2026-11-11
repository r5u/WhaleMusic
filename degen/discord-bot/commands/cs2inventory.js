const { SlashCommandBuilder, EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle, ModalBuilder, TextInputBuilder, TextInputStyle } = require('discord.js');
const axios = require('axios');

const { spawn } = require('node:child_process');

const whiteThumbnail = [
  'import sys, io',
  'from PIL import Image',
  'Image.MAX_IMAGE_PIXELS = 4000000',
  'skin = Image.open(io.BytesIO(sys.stdin.buffer.read())).convert("RGBA")',
  'skin.thumbnail((112, 72), Image.Resampling.LANCZOS)',
  'tile = Image.new("RGB", (120, 80), "white")',
  'tile.paste(skin, ((120-skin.width)//2, (80-skin.height)//2), skin)',
  'tile.save(sys.stdout.buffer, "PNG")',
].join('\n');

async function skinThumbnail(url) {
  if (!url) return null;
  try {
    const image = new URL(url);
    if (image.protocol !== 'https:') return null;
    if (image.hostname !== 'raw.githubusercontent.com') return null;
    const { data } = await axios.get(image.href, {
      responseType: 'arraybuffer', timeout: 2500, maxContentLength: 2 * 1024 * 1024, maxRedirects: 0
    });
    return await new Promise(resolve => {
      const child = spawn(process.env.PYTHON_PATH || 'python', ['-c', whiteThumbnail],
        { windowsHide: true, timeout: 3000, stdio: ['pipe', 'pipe', 'ignore'] });
      const chunks = [];
      child.on('error', () => resolve(null));
      child.stdout.on('data', chunk => chunks.push(chunk));
      child.stdin.on('error', () => {});
      child.on('close', code => resolve(code === 0 ? Buffer.concat(chunks) : null));
      child.stdin.end(data);
    });
  } catch {
    return null;
  }
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName('cs2inventory')
    .setDescription('View your CS2 skin collection and inventory!')
    .addUserOption(option =>
      option.setName('user')
        .setDescription('User whose inventory to view (leave empty for your own)')
        .setRequired(false)
    ),

  async execute(interaction) {
    await interaction.deferReply({ ephemeral: false });
    const targetUser = interaction.options.getUser('user') || interaction.user;
    const userId = targetUser.id;
    const guildId = interaction.guildId;
    const backendUrl = process.env.BACKEND_API_URL;

    try {
      // Get user's CS2 inventory
      const response = await axios.get(`${backendUrl}/cs2/inventory/${userId}`, {
        headers: { 'x-guild-id': guildId }
      });
      
      const { inventory } = response.data;
      
      if (!inventory || inventory.skins.length === 0) {
        const emptyEmbed = new EmbedBuilder()
          .setTitle('🎒 CS2 Inventory')
          .setDescription(`${targetUser.username} hasn't opened any CS2 cases yet!`)
          .setColor(0x808080)
          .setThumbnail(targetUser.displayAvatarURL())
          .addFields(
            { name: '📦 Cases Opened', value: '0', inline: true },
            { name: '🎨 Total Skins', value: '0', inline: true },
            { name: '💰 Total Spent', value: '0 currency', inline: true }
          );

        return interaction.editReply({ embeds: [emptyEmbed] });
      }

      // Create main inventory embed
      const mainEmbed = new EmbedBuilder()
        .setTitle(`🎒 ${targetUser.username}'s CS2 Inventory`)
        .setDescription(`**${inventory.totalSkins}** skins • **${inventory.casesOpened}** cases opened`)
        .setColor(0x00ff00)
        .setThumbnail(targetUser.displayAvatarURL())
        .addFields(
          { name: '📦 Cases Opened', value: inventory.casesOpened.toString(), inline: true },
          { name: '🎨 Total Skins', value: inventory.totalSkins.toString(), inline: true },
          { name: '💰 Total Spent', value: `${inventory.totalSpent} currency`, inline: true }
        );

      // Add estimated total value if available
      if (inventory.totalValue) {
        mainEmbed.addFields({
          name: '💎 Estimated Value',
          value: `${inventory.totalValue} currency`,
          inline: true
        });
      }

      // Add rarity breakdown
      const rarityBreakdown = Object.entries(inventory.rarityBreakdown)
        .filter(([_, count]) => count > 0)
        .map(([rarity, count]) => {
          const rarityEmoji = this.getRarityEmoji(rarity.replace(/([A-Z])/g, ' $1').toLowerCase());
          const rarityName = rarity.replace(/([A-Z])/g, ' $1');
          return `${rarityEmoji} **${rarityName}**: ${count}`;
        })
        .join('\n');

      if (rarityBreakdown) {
        mainEmbed.addFields({
          name: '🎨 Rarity Breakdown',
          value: rarityBreakdown,
          inline: false
        });
      }

      // Add notable skins
      if (inventory.rarestSkin) {
        mainEmbed.addFields({
          name: '💎 Rarest Skin',
          value: inventory.rarestSkin,
          inline: true
        });
      }

      if (inventory.mostExpensiveSkin) {
        mainEmbed.addFields({
          name: '💰 Most Expensive',
          value: inventory.mostExpensiveSkin,
          inline: true
        });
      }

      // Create navigation buttons
      const buttonRow = new ActionRowBuilder()
        .addComponents(
          new ButtonBuilder()
            .setCustomId(`cs2_view_skins_${userId}_0`)
            .setLabel('View Skins')
            .setStyle(ButtonStyle.Primary)
            .setEmoji('🎨'),
          new ButtonBuilder()
            .setCustomId(`cs2_stats_${userId}`)
            .setLabel('View Stats')
            .setStyle(ButtonStyle.Secondary)
            .setEmoji('📊'),
          new ButtonBuilder()
            .setCustomId(`cs2_best_drops_${userId}`)
            .setLabel('Best Drops')
            .setStyle(ButtonStyle.Success)
            .setEmoji('🏆'),
          new ButtonBuilder()
            .setCustomId(`cs2_rarest_drops_${userId}`)
            .setLabel('Rarest Drops')
            .setStyle(ButtonStyle.Danger)
            .setEmoji('💎')
        );

      const message = await interaction.editReply({
        embeds: [mainEmbed],
        components: [buttonRow]
      });

      // Note: Button and modal interactions are now handled globally in index.js
      // No need for local collectors here
      
      // Set a timeout to disable buttons after 5 minutes
      setTimeout(async () => {
        try {
          buttonRow.components.forEach(btn => btn.setDisabled(true));
          await interaction.editReply({
            embeds: [mainEmbed],
            components: [buttonRow]
          });
        } catch (error) {
          console.error('Error disabling buttons after timeout:', error);
        }
      }, 300000); // 5 minutes

    } catch (error) {
      console.error('Error fetching CS2 inventory:', error);
      await interaction.editReply('❌ **Failed to load inventory.** Please try again later.');
    }
  },

  getRarityEmoji(rarity) {
    const emojis = {
      'consumer grade': '⚪',
      'industrial grade': '🔵',
      'mil-spec': '🔷',
      'restricted': '🟣',
      'classified': '🩷',
      'covert': '🔴',
      'special': '🟡'
    };
    return emojis[rarity] || '⚪';
  },

  async showSkins(interaction, userId, guildId, backendUrl, page = 0, searchQuery = '') {
    const button = interaction.isButton();
    try {
      if (button) {
        await interaction.deferUpdate();
      } else {
        await interaction.deferReply({ ephemeral: true });
      }
      const response = await axios.get(`${backendUrl}/cs2/inventory/${userId}`, {
        headers: { 'x-guild-id': guildId }
      });
      
      const { inventory } = response.data;
      let skins = inventory.skins;
      
      if (skins.length === 0) {
        await interaction.editReply({ content: 'No skins found in inventory.', embeds: [], components: [], attachments: [] });
        return;
      }

      // Apply search filter if query exists
      if (searchQuery && searchQuery.trim() !== '') {
        const query = searchQuery.toLowerCase().trim();
        const originalCount = skins.length;
        
        skins = skins.filter(skin => 
          skin.weapon?.toLowerCase().includes(query) ||
          skin.skinName?.toLowerCase().includes(query) ||
          skin.rarity?.toLowerCase().includes(query) ||
          skin.wear?.toLowerCase().includes(query) ||
          (skin.formattedName && skin.formattedName.toLowerCase().includes(query))
        );
        
      }

      const skinsPerPage = 5;
      const totalPages = Math.max(1, Math.ceil(skins.length / skinsPerPage));
      page = Math.max(0, Math.min(page || 0, totalPages - 1));
      const startIndex = page * skinsPerPage;
      const displaySkins = skins.slice(startIndex, startIndex + skinsPerPage);
      const header = new EmbedBuilder().setTitle('🎨 CS2 Skins').setColor(0x00ff00)
        .setDescription(searchQuery ? 'Search: ' + searchQuery + ' • ' + skins.length + ' matches'
          : skins.length + ' owned skins')
        .setFooter({ text: 'Page ' + (page + 1) + ' of ' + totalPages });
      const files = [];
      const cards = await Promise.all(displaySkins.map(async (skin, index) => {
        const card = new EmbedBuilder()
          .setTitle((startIndex + index + 1) + '. ' + skin.weapon + ' | ' + skin.skinName)
          .setDescription(this.getRarityEmoji(skin.rarity) + ' ' + skin.rarity + ' • '
            + skin.wear + ' • ' + skin.marketValue + ' points');
        if (skin.imageUrl?.startsWith('https://')) {
          const tile = await skinThumbnail(skin.imageUrl);
          if (tile?.length) {
            const name = 'skin-' + index + '.png';
            files.push({ attachment: tile, name });
            card.setThumbnail('attachment://' + name);
          } else {
            card.setThumbnail(skin.imageUrl);
          }
        }
        return card;
      }));

      // Create pagination and search buttons
      while (encodeURIComponent(searchQuery).length > 50) {
        searchQuery = Array.from(searchQuery).slice(0, -1).join('');
      }
      const buttonRow = new ActionRowBuilder();

      // Navigation buttons
      if (page > 0) {
        buttonRow.addComponents(
          new ButtonBuilder()
            .setCustomId(`cs2_skins_prev_${userId}_${page}_${encodeURIComponent(searchQuery)}`)
            .setLabel('◀️ Previous')
            .setStyle(ButtonStyle.Secondary)
        );
      }
      
      if (page < totalPages - 1) {
        buttonRow.addComponents(
          new ButtonBuilder()
            .setCustomId(`cs2_skins_next_${userId}_${page}_${encodeURIComponent(searchQuery)}`)
            .setLabel('Next ▶️')
            .setStyle(ButtonStyle.Secondary)
        );
      }

      // Search button
      buttonRow.addComponents(
        new ButtonBuilder()
          .setCustomId(`cs2_skins_search_${userId}_${page}_${encodeURIComponent(searchQuery)}`)
          .setLabel('🔍 Search')
          .setStyle(ButtonStyle.Primary)
      );

      // Clear search button (only show if there's a search query)
      if (searchQuery) {
        buttonRow.addComponents(
          new ButtonBuilder()
            .setCustomId(`cs2_skins_clear_${userId}_0_`)
            .setLabel('❌ Clear Search')
            .setStyle(ButtonStyle.Danger)
        );
      }

      // Close button
      buttonRow.addComponents(
        new ButtonBuilder()
          .setCustomId(`cs2_skins_close_${userId}`)
          .setLabel('❌ Close')
          .setStyle(ButtonStyle.Secondary)
      );

      await interaction.editReply({ content: null, embeds: [header, ...cards],
        components: [buttonRow], attachments: [], files });
    } catch (error) {
      console.error('Error showing skins:', error);
      const content = '❌ Failed to load skins. Please try again later.';
      if (button && interaction.deferred) {
        await interaction.followUp({ content, ephemeral: true });
      } else if (interaction.deferred) {
        await interaction.editReply(content);
      } else {
        await interaction.reply({ content, ephemeral: true });
      }
    }
  },

  async showSearchModal(interaction, userId, guildId, backendUrl, currentPage, currentSearch) {
    try {
      // Create a modal for search input
      const modal = new ModalBuilder()
        .setCustomId(`cs2_search_modal_${userId}_${currentPage}_${encodeURIComponent(currentSearch)}`)
        .setTitle('🔍 Search CS2 Skins');

      const searchInput = new TextInputBuilder()
        .setCustomId('search_query')
        .setLabel('Search skins (weapon, skin, rarity, wear, pattern, phase)')
        .setStyle(TextInputStyle.Short)
        .setPlaceholder('e.g., AK-47, Doppler, Phase 4, Ruby, mil-spec, factory new')
        .setValue(currentSearch)
        .setRequired(false)
        .setMaxLength(100);

      const firstActionRow = new ActionRowBuilder().addComponents(searchInput);
      modal.addComponents(firstActionRow);

      await interaction.showModal(modal);

    } catch (error) {
      console.error('Error showing search modal:', error);
      await interaction.reply({ 
        content: '❌ Failed to show search modal. Please try again.', 
        ephemeral: true 
      });
    }
  },

  async showStats(interaction, userId, guildId, backendUrl) {
    try {
      const response = await axios.get(`${backendUrl}/cs2/stats/${userId}`, {
        headers: { 'x-guild-id': guildId }
      });
      
      const { stats } = response.data;
      
      const embed = new EmbedBuilder()
        .setTitle('📊 CS2 Statistics')
        .setColor(0x00ff00)
        .addFields(
          { name: '📦 Cases Opened', value: stats.totalOpenings.toString(), inline: true },
          { name: '💰 Total Spent', value: `${stats.totalSpent} currency`, inline: true },
          { name: '💎 Total Value', value: `${stats.totalValue} currency`, inline: true },
          { name: '💵 Total Profit', value: `${stats.totalProfit} currency`, inline: true },
          { name: '📈 Profit Margin', value: `${stats.profitMargin.toFixed(2)}%`, inline: true },
          { name: '🎯 Profitable Opens', value: `${stats.profitableOpenings}/${stats.totalOpenings}`, inline: true }
        );

      // Check if this is a new interaction or an update
      if (interaction.replied || interaction.deferred) {
        await interaction.editReply({ embeds: [embed] });
      } else {
        await interaction.reply({ embeds: [embed], ephemeral: true });
      }
    } catch (error) {
      console.error('Error loading stats:', error);
      if (interaction.replied || interaction.deferred) {
        await interaction.editReply({ content: '❌ Failed to load stats.', ephemeral: true });
      } else {
        await interaction.reply({ content: '❌ Failed to load stats.', ephemeral: true });
      }
    }
  },

  async showBestDrops(interaction, userId, guildId, backendUrl) {
    try {
      const response = await axios.get(`${backendUrl}/cs2/drops/${userId}/best`, {
        headers: { 'x-guild-id': guildId }
      });
      
      const { drops } = response.data;
      
      if (drops.length === 0) {
        if (interaction.replied || interaction.deferred) {
          await interaction.editReply({ content: 'No case openings found.', ephemeral: true });
        } else {
          await interaction.reply({ content: 'No case openings found.', ephemeral: true });
        }
        return;
      }

      const embed = new EmbedBuilder()
        .setTitle('🏆 Best CS2 Drops')
        .setDescription(`Top ${drops.length} most valuable skins`)
        .setColor(0x00ff00);

      drops.forEach((drop, index) => {
        const rarityEmoji = this.getRarityEmoji(drop.result.rarity);
        const profitEmoji = drop.profit > 0 ? '🟢' : drop.profit < 0 ? '🔴' : '⚪';
        
        embed.addFields({
          name: `${index + 1}. ${drop.result.name}`,
          value: `${rarityEmoji} **${drop.result.rarity}** • 💰 **${drop.result.marketValue}** currency • ${profitEmoji} **${drop.profit}** profit\n📦 From: ${drop.caseName}`,
          inline: false
        });
      });

      // Check if this is a new interaction or an update
      if (interaction.replied || interaction.deferred) {
        await interaction.editReply({ embeds: [embed] });
      } else {
        await interaction.reply({ embeds: [embed], ephemeral: true });
      }
    } catch (error) {
      console.error('Error loading best drops:', error);
      if (interaction.replied || interaction.deferred) {
        await interaction.editReply({ content: '❌ Failed to load best drops.', ephemeral: true });
      } else {
        await interaction.reply({ content: '❌ Failed to load best drops.', ephemeral: true });
      }
    }
  },

  async showRarestDrops(interaction, userId, guildId, backendUrl) {
    try {
      const response = await axios.get(`${backendUrl}/cs2/drops/${userId}/rarest`, {
        headers: { 'x-guild-id': guildId }
      });
      
      const { drops } = response.data;
      
      if (drops.length === 0) {
        if (interaction.replied || interaction.deferred) {
          await interaction.editReply({ content: 'No case openings found.', ephemeral: true });
        } else {
          await interaction.reply({ content: 'No case openings found.', ephemeral: true });
        }
        return;
      }

      const embed = new EmbedBuilder()
        .setTitle('💎 Rarest CS2 Drops')
        .setDescription(`Top ${drops.length} rarest skins by rarity`)
        .setColor(0x00ff00);

      drops.forEach((drop, index) => {
        const rarityEmoji = this.getRarityEmoji(drop.result.rarity);
        const profitEmoji = drop.profit > 0 ? '🟢' : drop.profit < 0 ? '🔴' : '⚪';
        
        embed.addFields({
          name: `${index + 1}. ${drop.result.name}`,
          value: `${rarityEmoji} **${drop.result.rarity}** • 💰 **${drop.result.marketValue}** currency • ${profitEmoji} **${drop.profit}** profit\n📦 From: ${drop.caseName}`,
          inline: false
        });
      });

      // Check if this is a new interaction or an update
      if (interaction.replied || interaction.deferred) {
        await interaction.editReply({ embeds: [embed] });
      } else {
        await interaction.reply({ embeds: [embed], ephemeral: true });
      }
    } catch (error) {
      console.error('Error loading rarest drops:', error);
      if (interaction.replied || interaction.deferred) {
        await interaction.editReply({ content: '❌ Failed to load rarest drops.', ephemeral: true });
      } else {
        await interaction.reply({ content: '❌ Failed to load rarest drops.', ephemeral: true });
      }
    }
  },

  getWearEmoji(wear) {
    const wearEmojis = {
      'factory new': '✨',
      'minimal wear': '🌟',
      'field-tested': '⭐',
      'well-worn': '💫',
      'battle-scarred': '🌙'
    };
    return wearEmojis[wear] || '⭐';
  }
};
