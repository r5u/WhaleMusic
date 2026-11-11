const mongoose = require('mongoose');
const { randomInt } = require('node:crypto');

const betSchema = new mongoose.Schema({
  guildId: { type: String, required: true },
  shortId: { type: String },
  description: { type: String, required: true },
  options: [{ type: String, required: true }], // Array of possible outcomes
  creator: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  status: { type: String, enum: ['open', 'closed', 'resolved', 'refunded'], default: 'open' },
  winningOption: { type: String }, // To be filled upon resolution
  createdAt: { type: Date, default: Date.now },
  closingTime: { type: Date }, // Time when betting closes
  notified: { type: Boolean, default: false }, // Whether closure has been announced to Discord
  // Add other bet-related fields as needed (e.g., end time, related message ID)
});

betSchema.index({ guildId: 1, shortId: 1 }, {
  unique: true, partialFilterExpression: { shortId: { $type: 'string' } }
});

const alphabet = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
function shortId() {
  return Array.from({ length: 5 }, () => alphabet[randomInt(alphabet.length)]).join('');
}

betSchema.methods.saveWithShortId = async function () {
  await this.constructor.init();
  for (let attempt = 0; attempt < 10; attempt++) {
    this.shortId = shortId();
    try {
      return await this.save();
    } catch (error) {
      if (error.code !== 11000 || !error.keyPattern?.shortId) throw error;
    }
  }
  throw new Error('Could not allocate a unique bet ID.');
};

betSchema.statics.backfillShortIds = async function () {
  await this.init();
  for await (const bet of this.find({ shortId: { $exists: false } }).select('_id guildId').lean().cursor()) {
    for (let attempt = 0; attempt < 10; attempt++) {
      try {
        await this.updateOne({ _id: bet._id, shortId: { $exists: false } }, { $set: { shortId: shortId() } });
        break;
      } catch (error) {
        if (error.code !== 11000 || !error.keyPattern?.shortId || attempt === 9) throw error;
      }
    }
  }
};

const Bet = mongoose.model('Bet', betSchema);

module.exports = Bet; 