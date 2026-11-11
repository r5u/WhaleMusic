process.env.GUILD_ID_1='0'; process.env.BOT_OWNER_ID='0'; process.env.MUSIC_ONLY_CHANNEL_ID='0';
const assert = require('node:assert/strict');
const {registerPrefixCommands}=require('./prefixCommands');
let handler, calls=0, replies=[];
const client={on(_,fn){handler=fn;}};
registerPrefixCommands(client,new Set(['crime','work','daily']),async i=>{
  calls++;
  if(i.commandName!=='daily') assert.equal(i.options.getSubcommand(),'do');
});
const schema=['crime','work'].map(name=>({name,type:1,options:[{name:'do',type:1}]}));
schema.push({name:'daily',type:1});
const message={client,guildId:'1',channelId:'2',author:{bot:false},member:{permissionsIn(){return {};}},
 guild:{commands:{async fetch(){return schema;}}},async reply(data){replies.push(data.content);}};
(async()=>{
 for(const command of ['crime do','work do','daily','crime do extra']) {
   await handler({...message,content:String.fromCharCode(96)+'degen '+command});
 }
 assert.equal(calls,3);
 assert.match(replies[0],/Too many arguments/);
 console.log('No-argument commands and subcommands work; extra arguments still show usage.');
})().catch(e=>{console.error(e.message);process.exitCode=1;});
