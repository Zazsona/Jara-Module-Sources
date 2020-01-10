package com.Zazsona.MinecraftChatLink;

import com.Zazsona.ChatLinkCommon.DiscordMessagePacket;
import com.Zazsona.ChatLinkCommon.MinecraftMessagePacket;
import com.Zazsona.minecraftCommon.FileManager;
import commands.CmdUtil;
import jara.Core;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;

public class MinecraftMessageManager
{
    private static final Logger LOGGER = LoggerFactory.getLogger(MinecraftMessageManager.class);

    private final static int PORT = 25599;
    private static HashMap<String, MinecraftMessageManager> instances = new HashMap<>();
    private static MessageListener MESSAGE_LISTENER;
    private Guild guild;

    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private Thread listeningThread;

    /**
     * Gets the instance for the specified guild.
     * @param guild the guild to get the instance for
     * @return the instance
     */
    public static MinecraftMessageManager getInstance(Guild guild)
    {
        if (instances.containsKey(guild.getId()))
        {
            return instances.get(guild.getId());
        }
        else
        {
            MinecraftMessageManager mmm = new MinecraftMessageManager(guild);
            instances.put(guild.getId(), mmm);
            return mmm;
        }
    }

    private MinecraftMessageManager(Guild guild)
    {
        if (MESSAGE_LISTENER == null)
        {
            MESSAGE_LISTENER = new MessageListener();
            Core.getShardManagerNotNull().addEventListener(MESSAGE_LISTENER);
        }
        this.guild = guild;
    }

    /**
     * Gets the embed styling for messages from Minecraft
     * @param channel the context
     * @param minecraftMessagePacket the message from Minecraft
     * @return a pre-styled embed
     */
    private EmbedBuilder getEmbed(TextChannel channel, MinecraftMessagePacket minecraftMessagePacket)
    {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setAuthor(minecraftMessagePacket.getMinecraftUsername(), null, "https://minotar.net/avatar/"+ minecraftMessagePacket.getMinecraftUsername()+".png");
        embed.setColor(CmdUtil.getHighlightColour(channel.getGuild().getSelfMember()));
        embed.setDescription(minecraftMessagePacket.getMessageContent());
        embed.setFooter("Minecraft");
        return embed;
    }

    /**
     * Sends the message to the correct channel for the guild specified.
     * @param guild the guild
     * @param minecraftMessagePacket the message
     */
    private void sendMessageToDiscord(Guild guild, MinecraftMessagePacket minecraftMessagePacket)
    {
        String channelID = ChatLinkFileManager.getChannelIDForGuild(guild.getId());
        TextChannel channel = guild.getTextChannelById(channelID);
        if (channel != null)
        {
            channel.sendMessage(getEmbed(channel, minecraftMessagePacket).build()).queue();
        }
    }

    /**
     * Sends the provided Discord message to the Minecraft server
     * @param message the message to send
     */
    private void sendMessageToMinecraft(Message message)
    {
        if (output != null && ChatLinkFileManager.getChannelIDForGuild(guild.getId()) != null)
        {
            try
            {
                output.writeObject(new DiscordMessagePacket(Core.getShardManagerNotNull().getShards().get(0).getSelfUser().getId(), message.getMember().getEffectiveName(), message.getContentDisplay()));
                output.flush();
            }
            catch (IOException e)
            {
                startConnection();
            }
        }
    }

    private class MessageListener extends ListenerAdapter
    {
        @Override
        public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event)
        {
            super.onGuildMessageReceived(event);
            if (!event.getMember().getUser().isBot() && event.getChannel().getId().equals(ChatLinkFileManager.getChannelIDForGuild(event.getGuild().getId())))
            {
                sendMessageToMinecraft(event.getMessage());
            }
        }
    }

    /**
     * Waits for incoming messages from the Minecraft server, and sends them to Discord<br>
     * In the case that the connection dies, this will attempt to reestablish it automatically.
     */
    private void listenForMinecraftMessages()
    {
        listeningThread = Thread.currentThread();
        while (ChatLinkFileManager.getChannelIDForGuild(guild.getId()) != null)
        {
            try
            {
                Object receivedObject = input.readObject();
                if (receivedObject instanceof MinecraftMessagePacket)
                {
                    MinecraftMessagePacket minecraftMessagePacket = (MinecraftMessagePacket) receivedObject;
                    sendMessageToDiscord(guild, minecraftMessagePacket);
                }
            }
            catch (IOException | ClassNotFoundException e)
            {
                startConnection();
            }
            catch (ClassCastException e)
            {
                //Ignore packet
            }
        }
        stopConnection();
        Thread.currentThread().interrupt();
    }

    /**
     * Starts a new connection.<br>
     * This will reserve the thread, and will only terminate the thread when the ChatLink is disabled in user settings, or stopConnection() is called. As such, any statements after this will never run.
     */
    public void startConnection()
    {
        try
        {
            stopConnection();
            String ip = FileManager.getIpForGuild(guild.getId());
            if (ip != null && ChatLinkFileManager.getChannelIDForGuild(guild.getId()) != null)
            {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, PORT));
                output = new ObjectOutputStream(socket.getOutputStream());
                input = new ObjectInputStream(socket.getInputStream());
                listenForMinecraftMessages();
                instances.put(guild.getId(), this);
            }
        }
        catch (IOException e)
        {
            try
            {
                Thread.sleep(1000*60);
            }
            catch (InterruptedException e1)
            {
                //It's fine, just cut it short
            }
            startConnection();
        }
    }

    /**
     * Kills the connection and terminates the connection thread.
     */
    public void stopConnection()
    {
        try
        {
            if (listeningThread != null && listeningThread != Thread.currentThread())
            {
                listeningThread.interrupt();
                listeningThread = null;
            }

            if (socket != null)
                socket.close();
            if (input != null)
                input.close();
            if (output != null)
                output.close();

            socket = null;
            input = null;
            output = null;
            instances.remove(guild.getId());
        }
        catch (IOException e)
        {
            LoggerFactory.getLogger(this.getClass()).error(e.toString());
        }
    }

}
