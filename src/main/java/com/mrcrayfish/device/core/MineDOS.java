package com.mrcrayfish.device.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MineDOS
{
        private static final ResourceLocation UNKNOWN_ICON_GUI = new ResourceLocation("cdm:textures/app/icon/unknown.png");

        private boolean oobeDone = false;
        private int oobePage = 0;
        private boolean typingUserName = false;
        private String userName = "User";

        private boolean startMenuOpen = false;

        private int activeWindow = 0;
        private static final int WINDOW_NONE = 0;
        private static final int WINDOW_CMD = 1;
        private static final int WINDOW_FILES = 2;
        private static final int WINDOW_SETTINGS = 3;
        private static final int WINDOW_ABOUT = 4;

        private int cmdX = 74;
        private int cmdY = 34;
        private int filesX = 88;
        private int filesY = 42;
        private int settingsX = 96;
        private int settingsY = 48;
        private int aboutX = 106;
        private int aboutY = 56;

        private boolean dragging = false;
        private int draggingWindow = 0;
        private int dragOffX = 0;
        private int dragOffY = 0;

        private String cmdInput = "";
        private final List<String> cmdLines = new ArrayList<String>();

        private String requestedAction = "";

        public MineDOS()
        {
                loadState();
                resetCmd();
        }

        public boolean isOobeDone()
        {
                loadState();
                return oobeDone;
        }

        public void resetOobe()
        {
                oobeDone = false;
                oobePage = 0;
                typingUserName = false;
                userName = "User";
                startMenuOpen = false;
                activeWindow = WINDOW_NONE;
                saveState();
        }

        public String consumeAction()
        {
                String action = requestedAction;
                requestedAction = "";
                return action;
        }

        public void render(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                if(dragging && Mouse.isButtonDown(0))
                {
                        updateDrag(x, y, mouseX, mouseY);
                }
                else
                {
                        dragging = false;
                }

                if(!oobeDone)
                {
                        renderOobe(mc, x, y, mouseX, mouseY);
                        return;
                }

                renderDesktop(mc, x, y, mouseX, mouseY);
        }

        public boolean handleClick(int x, int y, int mouseX, int mouseY)
        {
                if(!oobeDone)
                {
                        handleOobeClick(x, y, mouseX, mouseY);
                        return true;
                }

                handleDesktopClick(x, y, mouseX, mouseY);
                return true;
        }

        public boolean handleKeyTyped(char character, int code)
        {
                if(!oobeDone)
                {
                        if(oobePage == 1 && typingUserName)
                        {
                                if(code == Keyboard.KEY_BACK)
                                {
                                        if(userName.length() > 0)
                                        {
                                                userName = userName.substring(0, userName.length() - 1);
                                        }
                                        return true;
                                }

                                if(code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER)
                                {
                                        typingUserName = false;
                                        return true;
                                }

                                if(character >= 32 && character < 127 && userName.length() < 16)
                                {
                                        userName += character;
                                        return true;
                                }
                        }

                        return true;
                }

                if(activeWindow != WINDOW_CMD)
                {
                        return false;
                }

                if(code == Keyboard.KEY_BACK)
                {
                        if(cmdInput.length() > 0)
                        {
                                cmdInput = cmdInput.substring(0, cmdInput.length() - 1);
                        }
                        return true;
                }

                if(code == Keyboard.KEY_RETURN || code == Keyboard.KEY_NUMPADENTER)
                {
                        executeCommand(cmdInput);
                        cmdInput = "";
                        return true;
                }

                if(character >= 32 && character < 127)
                {
                        if(cmdInput.length() < 48)
                        {
                                cmdInput += character;
                        }
                        return true;
                }

                return true;
        }

        private void renderOobe(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int bottom = y + TaskBar.BAR_HEIGHT;
                int centerX = x + Laptop.SCREEN_WIDTH / 2;

                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, bottom, new Color(0, 45, 30).getRGB());
                Gui.drawRect(x + 7, screenTop + 7, x + Laptop.SCREEN_WIDTH - 7, bottom - 7, new Color(0, 105, 70).getRGB());

                mc.fontRenderer.drawString("MineDOS First Time Setup", x + 18, screenTop + 18, Color.WHITE.getRGB(), true);

                if(oobePage == 0)
                {
                        drawUnknownIcon(mc, centerX - 8, screenTop + 46);
                        mc.fontRenderer.drawString("Welcome to MineDOS", centerX - 58, screenTop + 72, Color.WHITE.getRGB(), true);
                        mc.fontRenderer.drawString("A real fake OS for your Minecraft laptop.", centerX - 106, screenTop + 91, Color.WHITE.getRGB(), false);
                        drawButton(mc, centerX - 50, screenTop + 126, 100, 18, "Next", mouseX, mouseY);
                }
                else if(oobePage == 1)
                {
                        mc.fontRenderer.drawString("Create user", centerX - 38, screenTop + 58, Color.WHITE.getRGB(), true);
                        mc.fontRenderer.drawString("Click the box and type your name:", centerX - 92, screenTop + 78, Color.WHITE.getRGB(), false);

                        int boxX = centerX - 70;
                        int boxY = screenTop + 96;

                        Gui.drawRect(boxX, boxY, boxX + 140, boxY + 18, Color.BLACK.getRGB());
                        Gui.drawRect(boxX + 1, boxY + 1, boxX + 139, boxY + 17, new Color(240, 240, 240).getRGB());

                        String cursor = typingUserName ? "_" : "";
                        mc.fontRenderer.drawString(userName + cursor, boxX + 5, boxY + 6, Color.BLACK.getRGB(), false);

                        drawButton(mc, centerX - 105, screenTop + 130, 90, 18, "Back", mouseX, mouseY);
                        drawButton(mc, centerX + 15, screenTop + 130, 90, 18, "Next", mouseX, mouseY);
                }
                else
                {
                        mc.fontRenderer.drawString("Ready", centerX - 18, screenTop + 72, Color.WHITE.getRGB(), true);
                        mc.fontRenderer.drawString("User: " + userName, centerX - 38, screenTop + 92, Color.WHITE.getRGB(), false);
                        drawButton(mc, centerX - 60, screenTop + 126, 120, 18, "Finish", mouseX, mouseY);
                }
        }

        private void handleOobeClick(int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int centerX = x + Laptop.SCREEN_WIDTH / 2;

                if(oobePage == 0)
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 50, screenTop + 126, centerX + 50, screenTop + 144))
                        {
                                oobePage = 1;
                        }
                }
                else if(oobePage == 1)
                {
                        int boxX = centerX - 70;
                        int boxY = screenTop + 96;

                        typingUserName = isMouseInside(mouseX, mouseY, boxX, boxY, boxX + 140, boxY + 18);

                        if(isMouseInside(mouseX, mouseY, centerX - 105, screenTop + 130, centerX - 15, screenTop + 148))
                        {
                                typingUserName = false;
                                oobePage = 0;
                        }
                        else if(isMouseInside(mouseX, mouseY, centerX + 15, screenTop + 130, centerX + 105, screenTop + 148))
                        {
                                if(userName.trim().isEmpty())
                                {
                                        userName = "User";
                                }

                                typingUserName = false;
                                oobePage = 2;
                        }
                }
                else
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 60, screenTop + 126, centerX + 60, screenTop + 144))
                        {
                                oobeDone = true;
                                saveState();
                                activeWindow = WINDOW_NONE;
                        }
                }
        }

        private void renderDesktop(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int bottom = y + TaskBar.BAR_HEIGHT;

                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, bottom, new Color(0, 70, 55).getRGB());
                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, screenTop + 18, new Color(0, 45, 35).getRGB());

                mc.fontRenderer.drawString("MineDOS Desktop", x + 8, screenTop + 6, Color.WHITE.getRGB(), true);

                drawDesktopIcon(mc, x + 18, screenTop + 30, "Computer");
                drawDesktopIcon(mc, x + 18, screenTop + 58, "Files");
                drawDesktopIcon(mc, x + 18, screenTop + 86, "MineCMD");
                drawDesktopIcon(mc, x + 18, screenTop + 114, "Settings");

                if(activeWindow == WINDOW_FILES)
                {
                        renderFiles(mc, x, y, mouseX, mouseY);
                }
                else if(activeWindow == WINDOW_SETTINGS)
                {
                        renderSettings(mc, x, y, mouseX, mouseY);
                }
                else if(activeWindow == WINDOW_ABOUT)
                {
                        renderAbout(mc, x, y, mouseX, mouseY);
                }
                else if(activeWindow == WINDOW_CMD)
                {
                        renderMineCmd(mc, x, y, mouseX, mouseY);
                }

                Gui.drawRect(x, y, x + Laptop.SCREEN_WIDTH, bottom, new Color(28, 28, 28).getRGB());
                drawButton(mc, x + 2, y + 2, 44, 14, "MINE", mouseX, mouseY);

                String clock = new SimpleDateFormat("HH:mm").format(new Date());
                mc.fontRenderer.drawString("MineDOS", x + 56, y + 6, Color.WHITE.getRGB(), false);
                mc.fontRenderer.drawString(clock, x + Laptop.SCREEN_WIDTH - 32, y + 6, Color.WHITE.getRGB(), false);

                if(startMenuOpen)
                {
                        renderStartMenu(mc, x, y, mouseX, mouseY);
                }
        }

        private void handleDesktopClick(int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);

                if(activeWindow != WINDOW_NONE)
                {
                        int wx = getWindowX(activeWindow);
                        int wy = getWindowY(activeWindow);
                        int ww = getWindowW(activeWindow);

                        if(isMouseInside(mouseX, mouseY, wx + ww - 16, wy + 3, wx + ww - 4, wy + 15))
                        {
                                activeWindow = WINDOW_NONE;
                                return;
                        }

                        if(isMouseInside(mouseX, mouseY, wx, wy, wx + ww, wy + 18))
                        {
                                dragging = true;
                                draggingWindow = activeWindow;
                                dragOffX = mouseX - wx;
                                dragOffY = mouseY - wy;
                                return;
                        }
                }

                if(isMouseInside(mouseX, mouseY, x + 2, y + 2, x + 46, y + 16))
                {
                        startMenuOpen = !startMenuOpen;
                        return;
                }

                if(startMenuOpen)
                {
                        int menuX = x + 2;
                        int menuY = y - 112;

                        if(isMouseInside(mouseX, mouseY, menuX + 6, menuY + 22, menuX + 102, menuY + 38))
                        {
                                activeWindow = WINDOW_CMD;
                                startMenuOpen = false;
                                return;
                        }

                        if(isMouseInside(mouseX, mouseY, menuX + 6, menuY + 40, menuX + 102, menuY + 56))
                        {
                                activeWindow = WINDOW_FILES;
                                startMenuOpen = false;
                                return;
                        }

                        if(isMouseInside(mouseX, mouseY, menuX + 6, menuY + 58, menuX + 102, menuY + 74))
                        {
                                activeWindow = WINDOW_SETTINGS;
                                startMenuOpen = false;
                                return;
                        }

                        if(isMouseInside(mouseX, mouseY, menuX + 6, menuY + 76, menuX + 102, menuY + 92))
                        {
                                requestedAction = "reboot";
                                startMenuOpen = false;
                                return;
                        }

                        if(isMouseInside(mouseX, mouseY, menuX + 6, menuY + 94, menuX + 102, menuY + 110))
                        {
                                requestedAction = "shutdown";
                                startMenuOpen = false;
                                return;
                        }

                        startMenuOpen = false;
                }

                if(isMouseInside(mouseX, mouseY, x + 18, screenTop + 58, x + 92, screenTop + 78))
                {
                        activeWindow = WINDOW_FILES;
                        startMenuOpen = false;
                }
                else if(isMouseInside(mouseX, mouseY, x + 18, screenTop + 86, x + 92, screenTop + 106))
                {
                        activeWindow = WINDOW_CMD;
                        startMenuOpen = false;
                }
                else if(isMouseInside(mouseX, mouseY, x + 18, screenTop + 114, x + 100, screenTop + 134))
                {
                        activeWindow = WINDOW_SETTINGS;
                        startMenuOpen = false;
                }
                else if(isMouseInside(mouseX, mouseY, x + 18, screenTop + 30, x + 100, screenTop + 50))
                {
                        activeWindow = WINDOW_ABOUT;
                        startMenuOpen = false;
                }
        }

        private void renderStartMenu(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int menuX = x + 2;
                int menuY = y - 112;

                Gui.drawRect(menuX, menuY, menuX + 114, menuY + 112, new Color(16, 16, 16).getRGB());
                Gui.drawRect(menuX + 2, menuY + 2, menuX + 112, menuY + 18, new Color(0, 95, 65).getRGB());
                mc.fontRenderer.drawString("MineDOS", menuX + 8, menuY + 7, Color.WHITE.getRGB(), true);

                drawButton(mc, menuX + 6, menuY + 22, 96, 16, "MineCMD", mouseX, mouseY);
                drawButton(mc, menuX + 6, menuY + 40, 96, 16, "Files", mouseX, mouseY);
                drawButton(mc, menuX + 6, menuY + 58, 96, 16, "Settings", mouseX, mouseY);
                drawButton(mc, menuX + 6, menuY + 76, 96, 16, "Restart PC", mouseX, mouseY);
                drawButton(mc, menuX + 6, menuY + 94, 96, 16, "Shutdown", mouseX, mouseY);
        }

        private void renderMineCmd(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                renderWindow(mc, cmdX, cmdY, 230, 128, "MineCMD", mouseX, mouseY);

                Gui.drawRect(cmdX + 4, cmdY + 21, cmdX + 226, cmdY + 124, Color.BLACK.getRGB());

                int lineY = cmdY + 26;
                int start = Math.max(0, cmdLines.size() - 7);

                for(int i = start; i < cmdLines.size(); i++)
                {
                        mc.fontRenderer.drawString(cmdLines.get(i), cmdX + 8, lineY, new Color(70, 255, 70).getRGB(), false);
                        lineY += 11;
                }

                mc.fontRenderer.drawString("C:\\Users\\" + userName + ">" + cmdInput + "_", cmdX + 8, cmdY + 113, new Color(70, 255, 70).getRGB(), false);
        }

        private void renderFiles(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                renderWindow(mc, filesX, filesY, 220, 116, "Files - C:\\", mouseX, mouseY);

                Gui.drawRect(filesX + 6, filesY + 24, filesX + 214, filesY + 110, new Color(245, 245, 245).getRGB());

                mc.fontRenderer.drawString("Name", filesX + 12, filesY + 30, Color.BLACK.getRGB(), true);
                mc.fontRenderer.drawString("Type", filesX + 120, filesY + 30, Color.BLACK.getRGB(), true);

                drawFileRow(mc, filesX + 12, filesY + 46, "Desktop", "<DIR>");
                drawFileRow(mc, filesX + 12, filesY + 58, "Documents", "<DIR>");
                drawFileRow(mc, filesX + 12, filesY + 70, "System", "<DIR>");
                drawFileRow(mc, filesX + 12, filesY + 82, "MineCMD.exe", "APP");
                drawFileRow(mc, filesX + 12, filesY + 94, "readme.txt", "TXT");
        }

        private void renderSettings(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                renderWindow(mc, settingsX, settingsY, 206, 104, "Settings", mouseX, mouseY);

                mc.fontRenderer.drawString("User: " + userName, settingsX + 12, settingsY + 28, Color.BLACK.getRGB(), false);
                mc.fontRenderer.drawString("OS: MineDOS 1.0", settingsX + 12, settingsY + 42, Color.BLACK.getRGB(), false);
                mc.fontRenderer.drawString("Shell: MineCMD", settingsX + 12, settingsY + 56, Color.BLACK.getRGB(), false);
                mc.fontRenderer.drawString("Filesystem: C:\\", settingsX + 12, settingsY + 70, Color.BLACK.getRGB(), false);
        }

        private void renderAbout(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                renderWindow(mc, aboutX, aboutY, 206, 96, "About MineDOS", mouseX, mouseY);

                drawUnknownIcon(mc, aboutX + 12, aboutY + 30);
                mc.fontRenderer.drawString("MineDOS 1.0", aboutX + 36, aboutY + 30, Color.BLACK.getRGB(), true);
                mc.fontRenderer.drawString("Minecraft laptop OS", aboutX + 36, aboutY + 44, Color.BLACK.getRGB(), false);
                mc.fontRenderer.drawString("Type help in MineCMD.", aboutX + 12, aboutY + 66, Color.BLACK.getRGB(), false);
        }

        private void renderWindow(Minecraft mc, int wx, int wy, int ww, int wh, String title, int mouseX, int mouseY)
        {
                Gui.drawRect(wx - 1, wy - 1, wx + ww + 1, wy + wh + 1, new Color(175, 175, 175).getRGB());
                Gui.drawRect(wx, wy, wx + ww, wy + wh, new Color(235, 235, 235).getRGB());
                Gui.drawRect(wx, wy, wx + ww, wy + 18, new Color(0, 80, 55).getRGB());

                mc.fontRenderer.drawString(title, wx + 6, wy + 6, Color.WHITE.getRGB(), true);

                Gui.drawRect(wx + ww - 16, wy + 3, wx + ww - 4, wy + 15, new Color(140, 0, 0).getRGB());
                mc.fontRenderer.drawString("X", wx + ww - 13, wy + 6, Color.WHITE.getRGB(), true);
        }

        private void drawFileRow(Minecraft mc, int x, int y, String name, String type)
        {
                mc.fontRenderer.drawString(name, x, y, Color.BLACK.getRGB(), false);
                mc.fontRenderer.drawString(type, x + 108, y, Color.BLACK.getRGB(), false);
        }

        private void updateDrag(int screenX, int taskbarY, int mouseX, int mouseY)
        {
                int nx = mouseX - dragOffX;
                int ny = mouseY - dragOffY;

                int top = taskbarY - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int bottom = taskbarY - 20;

                if(nx < screenX)
                {
                        nx = screenX;
                }

                if(ny < top)
                {
                        ny = top;
                }

                if(ny > bottom)
                {
                        ny = bottom;
                }

                if(draggingWindow == WINDOW_CMD)
                {
                        cmdX = nx;
                        cmdY = ny;
                }
                else if(draggingWindow == WINDOW_FILES)
                {
                        filesX = nx;
                        filesY = ny;
                }
                else if(draggingWindow == WINDOW_SETTINGS)
                {
                        settingsX = nx;
                        settingsY = ny;
                }
                else if(draggingWindow == WINDOW_ABOUT)
                {
                        aboutX = nx;
                        aboutY = ny;
                }
        }

        private int getWindowX(int window)
        {
                if(window == WINDOW_CMD) return cmdX;
                if(window == WINDOW_FILES) return filesX;
                if(window == WINDOW_SETTINGS) return settingsX;
                if(window == WINDOW_ABOUT) return aboutX;
                return 0;
        }

        private int getWindowY(int window)
        {
                if(window == WINDOW_CMD) return cmdY;
                if(window == WINDOW_FILES) return filesY;
                if(window == WINDOW_SETTINGS) return settingsY;
                if(window == WINDOW_ABOUT) return aboutY;
                return 0;
        }

        private int getWindowW(int window)
        {
                if(window == WINDOW_CMD) return 230;
                if(window == WINDOW_FILES) return 220;
                if(window == WINDOW_SETTINGS) return 206;
                if(window == WINDOW_ABOUT) return 206;
                return 0;
        }

        private void resetCmd()
        {
                cmdLines.clear();
                cmdLines.add("MineDOS [Version 1.0]");
                cmdLines.add("(c) Kristihack MineSystems");
                cmdLines.add("Type help for commands.");
        }

        private void executeCommand(String raw)
        {
                String command = raw.trim();
                cmdLines.add("C:\\Users\\" + userName + ">" + raw);

                if(command.equalsIgnoreCase("help"))
                {
                        cmdLines.add("help ver dir cls echo whoami apps time reboot shutdown");
                }
                else if(command.equalsIgnoreCase("ver"))
                {
                        cmdLines.add("MineDOS Version 1.0");
                }
                else if(command.equalsIgnoreCase("dir"))
                {
                        cmdLines.add("C:\\");
                        cmdLines.add("<DIR> Desktop");
                        cmdLines.add("<DIR> Documents");
                        cmdLines.add("<DIR> System");
                        cmdLines.add("      MineCMD.exe");
                        cmdLines.add("      readme.txt");
                }
                else if(command.equalsIgnoreCase("cls"))
                {
                        resetCmd();
                }
                else if(command.toLowerCase().startsWith("echo "))
                {
                        cmdLines.add(command.substring(5));
                }
                else if(command.equalsIgnoreCase("whoami"))
                {
                        cmdLines.add(userName);
                }
                else if(command.equalsIgnoreCase("apps"))
                {
                        cmdLines.add("Computer Files MineCMD Settings");
                }
                else if(command.equalsIgnoreCase("time"))
                {
                        cmdLines.add(new SimpleDateFormat("HH:mm:ss").format(new Date()));
                }
                else if(command.equalsIgnoreCase("reboot") || command.equalsIgnoreCase("restart"))
                {
                        cmdLines.add("Restarting...");
                        requestedAction = "reboot";
                }
                else if(command.equalsIgnoreCase("shutdown"))
                {
                        cmdLines.add("Shutting down...");
                        requestedAction = "shutdown";
                }
                else if(command.length() == 0)
                {
                }
                else
                {
                        cmdLines.add("'" + command + "' is not recognized.");
                }
        }

        private void drawDesktopIcon(Minecraft mc, int x, int y, String label)
        {
                drawUnknownIcon(mc, x, y);
                mc.fontRenderer.drawString(label, x + 22, y + 5, Color.WHITE.getRGB(), true);
        }

        private void drawUnknownIcon(Minecraft mc, int x, int y)
        {
                mc.getTextureManager().bindTexture(UNKNOWN_ICON_GUI);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 16, 16, 16);
        }

        private void drawButton(Minecraft mc, int x, int y, int width, int height, String text, int mouseX, int mouseY)
        {
                int color = isMouseInside(mouseX, mouseY, x, y, x + width, y + height) ? new Color(0, 130, 80).getRGB() : new Color(60, 60, 60).getRGB();

                Gui.drawRect(x, y, x + width, y + height, Color.BLACK.getRGB());
                Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, color);

                int textWidth = mc.fontRenderer.getStringWidth(text);
                mc.fontRenderer.drawString(text, x + width / 2 - textWidth / 2, y + 5, Color.WHITE.getRGB(), true);
        }

        private File getStateFile()
        {
                File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");

                if(!configDir.exists())
                {
                        configDir.mkdirs();
                }

                return new File(configDir, "minedos_state.txt");
        }

        private void saveState()
        {
                try
                {
                        PrintWriter writer = new PrintWriter(getStateFile());
                        writer.println("oobeDone=" + oobeDone);
                        writer.println("userName=" + userName);
                        writer.close();
                }
                catch(Exception e)
                {
                }
        }

        private void loadState()
        {
                try
                {
                        File file = getStateFile();

                        if(!file.exists())
                        {
                                oobeDone = false;
                                return;
                        }

                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        String line;

                        while((line = reader.readLine()) != null)
                        {
                                if(line.startsWith("oobeDone="))
                                {
                                        oobeDone = line.substring("oobeDone=".length()).equalsIgnoreCase("true");
                                }
                                else if(line.startsWith("userName="))
                                {
                                        String value = line.substring("userName=".length()).trim();

                                        if(!value.isEmpty())
                                        {
                                                userName = value;
                                        }
                                }
                        }

                        reader.close();
                }
                catch(Exception e)
                {
                        oobeDone = false;
                }
        }

        private boolean isMouseInside(int mouseX, int mouseY, int left, int top, int right, int bottom)
        {
                return mouseX >= left && mouseY >= top && mouseX < right && mouseY < bottom;
        }
}