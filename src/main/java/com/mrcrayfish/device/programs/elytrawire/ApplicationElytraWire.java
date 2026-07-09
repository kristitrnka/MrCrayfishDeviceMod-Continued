package com.mrcrayfish.device.programs.elytrawire;

import com.mrcrayfish.device.api.app.Application;
import com.mrcrayfish.device.api.app.Layout;
import com.mrcrayfish.device.api.app.component.Button;
import com.mrcrayfish.device.api.app.component.Label;
import com.mrcrayfish.device.api.app.component.TextArea;
import com.mrcrayfish.device.api.app.component.TextField;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.text.TextFormatting;

public class ApplicationElytraWire extends Application
{
    public static ApplicationElytraWire OPEN_APP = null;

    private Layout layoutSetup;
    private Layout layoutMessenger;

    private TextField fieldName;
    private TextField fieldTarget;
    private TextField fieldMessage;

    private TextArea chatArea;
    private Label labelTitle;
    private Label labelStatus;

    private String username = "";
    private String target = "";
    private String history = "";

    @Override
    public void init(NBTTagCompound tag)
    {
        if(tag != null)
        {
            username = tag.getString("username");
            target = tag.getString("target");
            history = tag.getString("history");
        }

        if(history == null)
        {
            history = "";
        }

        createSetupLayout();
        createMessengerLayout();

        OPEN_APP = this;

        if(username == null || username.trim().isEmpty())
        {
            setCurrentLayout(layoutSetup);
        }
        else
        {
            setCurrentLayout(layoutMessenger);
            refresh();
        }
    }

    private void createSetupLayout()
    {
        layoutSetup = new Layout(250, 130);

        Label title = new Label(TextFormatting.BOLD + "" + TextFormatting.AQUA + "Welcome to ElytraWire", 18, 15);
        layoutSetup.addComponent(title);

        Label info = new Label(TextFormatting.GRAY + "Choose your display name:", 18, 40);
        layoutSetup.addComponent(info);

        fieldName = new TextField(18, 58, 160);
        fieldName.setPlaceholder("Your name");
        layoutSetup.addComponent(fieldName);

        Button continueButton = new Button(18, 88, "Continue");
        continueButton.setSize(85, 20);
        continueButton.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            String name = fieldName.getText();

            if(name != null && !name.trim().isEmpty())
            {
                username = name.trim();

                if(history == null || history.isEmpty())
                {
                    addLine(TextFormatting.DARK_GRAY + "Account created as " + username);
                }

                setCurrentLayout(layoutMessenger);
                refresh();
            }
        });
        layoutSetup.addComponent(continueButton);
    }

    private void createMessengerLayout()
    {
        layoutMessenger = new Layout(362, 190);

        labelTitle = new Label(TextFormatting.BOLD + "" + TextFormatting.AQUA + "ElytraWire", 6, 6);
        layoutMessenger.addComponent(labelTitle);

        Label dmLabel = new Label(TextFormatting.GRAY + "DM:", 6, 28);
        layoutMessenger.addComponent(dmLabel);

        fieldTarget = new TextField(35, 24, 105);
        fieldTarget.setPlaceholder("Player name");
        layoutMessenger.addComponent(fieldTarget);

        Button openButton = new Button(145, 24, "Open");
        openButton.setSize(50, 20);
        openButton.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            String t = fieldTarget.getText();

            if(t != null && !t.trim().isEmpty())
            {
                target = t.trim();
                addLine(TextFormatting.DARK_GRAY + "Opened DM with " + target);
                refresh();
            }
        });
        layoutMessenger.addComponent(openButton);

        Button refreshButton = new Button(200, 24, "Refresh");
        refreshButton.setSize(62, 20);
        refreshButton.setClickListener((mouseX, mouseY, mouseButton) -> refresh());
        layoutMessenger.addComponent(refreshButton);

        Button nameButton = new Button(267, 24, "Name");
        nameButton.setSize(50, 20);
        nameButton.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            fieldName.setText(username);
            setCurrentLayout(layoutSetup);
        });
        layoutMessenger.addComponent(nameButton);

        chatArea = new TextArea(6, 50, 344, 90);
        chatArea.setEditable(false);
        chatArea.setPadding(3);
        layoutMessenger.addComponent(chatArea);

        fieldMessage = new TextField(6, 148, 275);
        fieldMessage.setPlaceholder("Message");
        layoutMessenger.addComponent(fieldMessage);

        Button sendButton = new Button(288, 148, "Send");
        sendButton.setSize(62, 20);
        sendButton.setClickListener((mouseX, mouseY, mouseButton) -> sendMessage());
        layoutMessenger.addComponent(sendButton);

        labelStatus = new Label(TextFormatting.GRAY + "ElytraWire uses real Minecraft /msg.", 6, 174);
        layoutMessenger.addComponent(labelStatus);
    }

    private void sendMessage()
    {
        String msg = fieldMessage.getText();

        if(target == null || target.trim().isEmpty())
        {
            labelStatus.setText(TextFormatting.RED + "Open a DM first.");
            return;
        }

        if(msg == null || msg.trim().isEmpty())
        {
            labelStatus.setText(TextFormatting.RED + "Type a message first.");
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        if(mc.player == null)
        {
            labelStatus.setText(TextFormatting.RED + "Player not loaded.");
            return;
        }

        String cleanTarget = target.trim();
        String cleanMsg = msg.trim();

        String command = "/msg " + cleanTarget + " [ElytraWire] " + username + ": " + cleanMsg;
        mc.player.sendChatMessage(command);

        addLine(TextFormatting.AQUA + "You -> " + cleanTarget + ": " + TextFormatting.WHITE + cleanMsg);

        fieldMessage.clear();
        labelStatus.setText(TextFormatting.GREEN + "Sent to " + cleanTarget);
        refresh();
    }

    public static void receiveMessage(String message)
    {
        ElytraWireEvents.addGlobalMessage(message);

        if(OPEN_APP != null)
        {
            OPEN_APP.addLine(TextFormatting.LIGHT_PURPLE + message);
            OPEN_APP.refresh();
        }
    }

    private void addLine(String line)
    {
        if(history == null)
        {
            history = "";
        }

        history += line + "\n";

        if(history.length() > 5000)
        {
            history = history.substring(history.length() - 5000);
        }
    }

    private void refresh()
    {
        if(labelTitle != null)
        {
            String dm = target == null || target.trim().isEmpty() ? "No DM" : "DM: " + target;
            labelTitle.setText(TextFormatting.BOLD + "" + TextFormatting.AQUA + "ElytraWire " + TextFormatting.GRAY + "- " + username + " - " + dm);
        }

        if(fieldTarget != null && target != null && !target.trim().isEmpty())
        {
            fieldTarget.setText(target);
        }

        if(fieldMessage != null)
        {
            if(target == null || target.trim().isEmpty())
            {
                fieldMessage.setPlaceholder("Open a DM first");
            }
            else
            {
                fieldMessage.setPlaceholder("Message @" + target);
            }
        }

        if(chatArea != null)
        {
            chatArea.clear();

            String global = ElytraWireEvents.getGlobalHistory();

            if(history != null && !history.isEmpty())
            {
                chatArea.writeText(history);
            }

            if(global != null && !global.isEmpty())
            {
                chatArea.writeText(TextFormatting.DARK_GRAY + "\n--- Incoming ---\n");
                chatArea.writeText(global);
            }
        }
    }

    @Override
    public void load(NBTTagCompound tag)
    {
        if(tag != null)
        {
            username = tag.getString("username");
            target = tag.getString("target");
            history = tag.getString("history");
        }
    }

    @Override
    public void save(NBTTagCompound tag)
    {
        tag.setString("username", username == null ? "" : username);
        tag.setString("target", target == null ? "" : target);
        tag.setString("history", history == null ? "" : history);
    }
}