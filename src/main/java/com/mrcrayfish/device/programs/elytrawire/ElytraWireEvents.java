package com.mrcrayfish.device.programs.elytrawire;

import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ElytraWireEvents
{
    private static String globalHistory = "";

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event)
    {
        if(event == null || event.getMessage() == null)
        {
            return;
        }

        String text = event.getMessage().getUnformattedText();

        if(text == null)
        {
            return;
        }

        if(text.contains("[ElytraWire]"))
        {
            ApplicationElytraWire.receiveMessage(text);
        }
    }

    public static void addGlobalMessage(String message)
    {
        if(message == null)
        {
            return;
        }

        globalHistory += message + "\n";

        if(globalHistory.length() > 5000)
        {
            globalHistory = globalHistory.substring(globalHistory.length() - 5000);
        }
    }

    public static String getGlobalHistory()
    {
        return globalHistory == null ? "" : globalHistory;
    }
}