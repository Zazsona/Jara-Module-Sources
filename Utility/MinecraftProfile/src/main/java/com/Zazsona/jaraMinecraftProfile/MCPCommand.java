package com.Zazsona.jaraMinecraftProfile;

import com.Zazsona.jaraMinecraftProfile.data.PlayerData;
import com.Zazsona.jaraMinecraftProfile.data.ResponseData;
import com.Zazsona.jaraMinecraftProfile.data.StatusCode;
import com.Zazsona.jaraMinecraftProfile.json.UsernameUUID;
import com.google.gson.Gson;
import commands.CmdUtil;
import module.Command;
import net.dv8tion.jda.core.EmbedBuilder;
import net.dv8tion.jda.core.events.message.guild.GuildMessageReceivedEvent;

import java.time.Instant;

public class MCPCommand extends Command
{
    @Override
    public void run(GuildMessageReceivedEvent msgEvent, String... parameters)
    {
        if (parameters.length > 1)
        {
            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(CmdUtil.getHighlightColour(msgEvent.getGuild().getSelfMember()));
            String uuid = getUUIDFromUsername(parameters[parameters.length-1]);
            if (uuid != null)
            {
                ResponseData rd = CommunicationManager.requestPlayerData(parameters[1], uuid);
                if (rd == null)
                {
                    embed.setDescription("Server does not have the required plugin.");
                    msgEvent.getChannel().sendMessage(embed.build()).queue();
                }
                else if (rd.getStatusCode() == StatusCode.SERVER_OFFLINE)
                {
                    embed.setDescription("The server is offline.");
                    msgEvent.getChannel().sendMessage(embed.build()).queue();
                }
                else if (rd.getStatusCode() == StatusCode.PLUGIN_DISABLED)
                {
                    embed.setDescription("The plugin is disabled on this server.");
                    msgEvent.getChannel().sendMessage(embed.build()).queue();
                }
                else if (rd.getStatusCode() == StatusCode.UNKNOWN_PLAYER)
                {
                    embed.setDescription("Unable to find "+parameters[parameters.length-1]+" on the server.");
                    msgEvent.getChannel().sendMessage(embed.build()).queue();
                }
                else if (rd.getStatusCode() == StatusCode.OK)
                {
                    msgEvent.getChannel().sendMessage(buildEmbed(embed, rd.getPlayerData()).build()).queue();
                }
            }
            else
            {
                embed.setDescription("No player with that username exists.");
                msgEvent.getChannel().sendMessage(embed.build()).queue();
            }
        }
        else
        {
            CmdUtil.sendHelpInfo(msgEvent, getClass());
        }
    }

    private String getUUIDFromUsername(String username)
    {
        try
        {
            String json = CmdUtil.sendHTTPRequest("https://api.mojang.com/users/profiles/minecraft/"+username+"?at="+ Instant.now().getEpochSecond());
            UsernameUUID usernameUUID = new Gson().fromJson(json, UsernameUUID.class);
            String uuid = usernameUUID.getId();
            return uuid.substring(0, 8)+"-"+uuid.substring(8, 12)+"-"+uuid.substring(12, 16)+"-"+uuid.substring(16, 20)+"-"+uuid.substring(20);
        }
        catch (NullPointerException e)
        {
            return null;
        }
    }

    private EmbedBuilder buildEmbed(EmbedBuilder embed, PlayerData playerData)
    {
        embed.setThumbnail("https://minotar.net/armor/bust/"+playerData.getName()+"/100.png");
        embed.setTitle("===== "+playerData.getName()+" =====");
        embed.addField("Name", playerData.getName(), true);
        embed.addField("Status", ((playerData.isOnline()) ? "Online" : "Offline"), true);
        embed.addField("HP", String.valueOf(playerData.getHp()), true);
        embed.addField("Hunger", String.valueOf(playerData.getHunger()), true);
        embed.addField("Level", String.valueOf(playerData.getLevel()), true);
        embed.addField("Location", playerData.getLocation()[0]+", "+playerData.getLocation()[1]+", "+playerData.getLocation()[2], true);
        return embed;
    }

}
