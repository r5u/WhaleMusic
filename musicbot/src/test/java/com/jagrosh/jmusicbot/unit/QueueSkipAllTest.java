package com.jagrosh.jmusicbot.unit;

import static org.mockito.Mockito.*;
import com.jagrosh.jmusicbot.*;
import com.jagrosh.jmusicbot.audio.*;
import com.jagrosh.jmusicbot.service.*;
import com.jagrosh.jmusicbot.listener.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.message.react.*;
import java.util.*;
public class QueueSkipAllTest {
 @org.junit.jupiter.api.Test
 @SuppressWarnings("unchecked") public void queueControlsEnforceDjPermissions()throws Exception {
  Bot bot=mock(Bot.class,RETURNS_DEEP_STUBS);MusicService service=mock(MusicService.class);when(bot.getMusicService()).thenReturn(service);
  var listener=new QueueSkipAllListener(bot);
  Guild guild=mock(Guild.class,RETURNS_DEEP_STUBS);when(guild.getIdLong()).thenReturn(1L);
  Member member=mock(Member.class,RETURNS_DEEP_STUBS);when(member.getIdLong()).thenReturn(2L);when(member.getRoles()).thenReturn(List.of());
  Role dj=mock(Role.class);when(dj.getIdLong()).thenReturn(9L);when(bot.getSettingsManager().getSettings(guild).getRole(guild)).thenReturn(dj);
  MessageReactionAddEvent event=mock(MessageReactionAddEvent.class,RETURNS_DEEP_STUBS);when(event.getGuild()).thenReturn(guild);when(event.getMessageIdLong()).thenReturn(123L);
  var controlsField=QueueSkipAllListener.class.getDeclaredField("controls");controlsField.setAccessible(true);Map<Long,Object> controls=(Map<Long,Object>)controlsField.get(null);
  var controlType=Class.forName("com.jagrosh.jmusicbot.listener.QueueSkipAllListener$Control");var constructor=controlType.getDeclaredConstructor(long.class,long.class);constructor.setAccessible(true);
  Object control=constructor.newInstance(1L,System.nanoTime()+60_000_000_000L);controls.put(123L,control);
  var action=QueueSkipAllListener.class.getDeclaredMethod("skip",MessageReactionAddEvent.class,Member.class,controlType);action.setAccessible(true);
  action.invoke(listener,event,member,control);verifyNoInteractions(service);if(!controls.containsKey(123L))throw new AssertionError("Non-DJ consumed control");
  when(member.getRoles()).thenReturn(List.of(dj));action.invoke(listener,event,member,control);verify(service).skipAll(guild);
  action.invoke(listener,event,member,control);verify(service,times(1)).skipAll(guild);
  Object expired=constructor.newInstance(1L,System.nanoTime()-1);controls.put(123L,expired);action.invoke(listener,event,member,expired);verify(service,times(1)).skipAll(guild);
  when(member.getRoles()).thenReturn(List.of());when(bot.getConfig().isOwnerId(2L)).thenReturn(true);
  control=constructor.newInstance(1L,System.nanoTime()+60_000_000_000L);controls.put(123L,control);action.invoke(listener,event,member,control);verify(service,times(2)).skipAll(guild);
  AudioHandler handler=mock(AudioHandler.class);when(guild.getAudioManager().getSendingHandler()).thenReturn(handler);
  new MusicService(bot).skipAll(guild);verify(handler).stopAndClearQueuePreserveHistory();verify(guild.getAudioManager(),never()).closeAudioConnection();
  when(guild.getAudioManager().getSendingHandler()).thenReturn(null);new MusicService(bot).skipAll(guild);
  var text=new com.jagrosh.jmusicbot.commands.v1.dj.SkipAllCmd(bot);var slash=new com.jagrosh.jmusicbot.commands.v2.dj.SkipAllSlashCmd(bot);
  if(!text.getName().equals("skipall")||!slash.getName().equals("skipall"))throw new AssertionError("Command registration");
  if(!(text instanceof com.jagrosh.jmusicbot.commands.v1.DJCommand)||!(slash instanceof com.jagrosh.jmusicbot.commands.v2.DJSlashCommand))throw new AssertionError("DJ guards");
  controls.clear();System.out.println("PASS: non-DJs blocked; DJs and owners allowed; duplicate/expired reactions ignored; active/default queues cleared without leaving voice; both commands retain DJ guards.");
 }
}
