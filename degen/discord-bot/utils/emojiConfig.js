const EMOJI = {
  pokeball_normal:'🔴',pokeball_great:'🔵',pokeball_ultra:'🟡',pokeball_master:'🟣',pokeball:'🔴',
  pokeball_success:'✅',pokeball_shake:'🔴',protein:'💪',iron:'🛡️',calcium:'🦴',zinc:'⚙️',carbos:'⚡',rarecandy:'🍬',
  empty_lb_right:'⬜',empty_lb_middle:'⬜',empty_lb_left:'⬜',filled_lb_right:'🟩',filled_lb_middle:'🟩',filled_lb_left:'🟩',mew_kiss:'💜'
};
const getEmoji=name=>EMOJI[name]||'✨';
module.exports={getEmoji,getEmojiString:getEmoji,getAnimatedEmojiString:getEmoji,BOT_ENVIRONMENT:'portable'};
