package com.mrcrayfish.device.core;

import com.mrcrayfish.device.api.task.TaskManager;
import com.mrcrayfish.device.api.task.Task;
import com.mrcrayfish.device.core.io.task.TaskGetBootOptions;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.Random;

public class BootUI
{
        private MineDOS mineDOS;

        private MineDOS getMineDOS()
        {
                if(this.mineDOS == null)
                {
                        this.mineDOS = new MineDOS();
                }

                return this.mineDOS;
        }

        private static final ResourceLocation UNKNOWN_ICON_GUI = new ResourceLocation("cdm:textures/app/icon/unknown.png");

        private static final ResourceLocation POWER_ICONS_GUI = new ResourceLocation("cdm:textures/gui/icons.png");
        private static final ResourceLocation COMPONENTS_GUI = new ResourceLocation("cdm:textures/gui/components.png");

        private final Laptop laptop;
        private final Random random = new Random();

        private int barX;
        private int barY;

        private boolean powerMenuOpen = false;
        private boolean poweredOff = false;
        private boolean bootLoaderOpen = false;
        private boolean bootingOs = false;
        private boolean installerOpen = false;
        private boolean installing = false;

        private int selectedBootOption = 0;
        private boolean usbBootable = true;
        private String usbBootName = "USB Drive";
        private int usbInfoRequestTimer = 0;

        private int bootTimer = 0;
        private int osBootTimer = 0;
        private int osBootTotal = 0;
        private String osBootName = "DeviceOS";
        private String installedOsName = "DeviceOS";

        private boolean keyHeld = false;
        private int spinnerTick = 0;

        private int installerPage = 0;
        private boolean licenseAccepted = false;
        private int installTimer = 0;
        private int installTotal = 0;
        private int installDisplayPercent = 0;
        private String installerMessage = "";

        private boolean mineDosOobeOpen = false;
        private boolean mineDosDesktopOpen = false;
        private boolean mineDosOobeDone = false;
        private int mineDosOobePage = 0;
        private String mineDosUserName = "User";

        public BootUI(Laptop laptop)
        {
                this.laptop = laptop;
                loadInstalledOs();
                loadMineDosState();
        }

        public void init(int x, int y)
        {
                this.barX = x;
                this.barY = y;
                restoreInstalledOsSession();
                // forceMineDosSession disabled - was global for every laptop
        }

        public boolean isFullScreen()
        {
                return poweredOff || bootLoaderOpen || bootingOs || installerOpen || installing;
        }

        public boolean onTick()
        {
                // forceMineDosSession disabled - was global for every laptop
                spinnerTick++;

                if(installing)
                {
                        if(installTimer > 0)
                        {
                                installTimer--;
                        }

                        int realPercent = installTotal <= 0 ? 100 : Math.min(100, (installTotal - installTimer) * 100 / installTotal);
                        int maxFakePercent = Math.min(99, realPercent + 8);

                        if(installDisplayPercent < maxFakePercent && random.nextInt(3) == 0)
                        {
                                installDisplayPercent += 1 + random.nextInt(3);

                                if(installDisplayPercent > maxFakePercent)
                                {
                                        installDisplayPercent = maxFakePercent;
                                }
                        }

                        if(installTimer <= 0)
                        {
                                installDisplayPercent = 100;
                                installing = false;
                                installedOsName = usbBootName;
                                saveInstalledOs();
                                installerPage = 4;
                                installerMessage = usbBootName + " installed. Restart required.";
                        }

                        return true;
                }

                if(mineDosOobeOpen || mineDosDesktopOpen)
                {
                        return true;
                }

                if(installerOpen)
                {
                        return true;
                }

                if(bootingOs)
                {
                        if(osBootTimer > 0)
                        {
                                osBootTimer--;
                        }

                        if(osBootTimer <= 0)
                        {
                                bootingOs = false;
                                bootLoaderOpen = false;

                                if(selectedBootOption == 1)
                                {
                                        installerOpen = true;
                                        installerPage = 0;
                                        licenseAccepted = false;
                                        installerMessage = "";
                                }
                                else if(isMineDOS(installedOsName))
                                {
                                        if(!mineDosOobeDone)
                                        {
                                                mineDosOobeOpen = false;
                                                mineDosOobePage = 0;
                                        }
                                        else
                                        {
                                                mineDosDesktopOpen = false;
                                        }
                                }
                        }

                        return true;
                }

                if(bootLoaderOpen)
                {
                        refreshUsbBootInfo();

                        if(usbInfoRequestTimer > 0)
                        {
                                usbInfoRequestTimer--;
                        }

                        if(usbInfoRequestTimer <= 0)
                        {
                                requestUsbBootInfo();
                                usbInfoRequestTimer = 20;
                        }
                        handleBootKeyboard();

                        if(bootTimer > 0)
                        {
                                bootTimer--;
                        }

                        if(bootTimer <= 0)
                        {
                                beginSelectedBoot();
                        }

                        return true;
                }

                return poweredOff;
        }

        public boolean renderFullScreen(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                // forceMineDosSession disabled - was global for every laptop
                if(false && mineDosOobeOpen)
                {
                        getMineDOS().render(mc, x, y, mouseX, mouseY);
                        applyMineDosAction();
                        return true;
                }

                if(false && mineDosDesktopOpen)
                {
                        getMineDOS().render(mc, x, y, mouseX, mouseY);
                        applyMineDosAction();
                        return true;
                }

                if(installing || installerOpen)
                {
                        renderInstaller(mc, x, y, mouseX, mouseY);
                        return true;
                }

                if(poweredOff)
                {
                        renderPoweredOff(mc, x, y, mouseX, mouseY);
                        return true;
                }

                if(bootLoaderOpen || bootingOs)
                {
                        renderBootLoader(mc, x, y);
                        return true;
                }

                return false;
        }

        public void renderTaskbar(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                drawPowerButton(mc, x + 257, y + 2, mouseX, mouseY);

                if(powerMenuOpen)
                {
                        renderPowerMenu(mc, x, y, mouseX, mouseY);
                }
        }

        public boolean handleClick(int x, int y, int mouseX, int mouseY, int mouseButton)
        {
                // forceMineDosSession disabled - was global for every laptop
                if(mouseButton != 0)
                {
                        return false;
                }

                if(installing)
                {
                        return true;
                }

                if(false && mineDosOobeOpen)
                {
                        getMineDOS().handleClick(x, y, mouseX, mouseY);
                        applyMineDosAction();
                        return true;
                }

                if(false && mineDosDesktopOpen)
                {
                        getMineDOS().handleClick(x, y, mouseX, mouseY);
                        applyMineDosAction();
                        return true;
                }

                if(installerOpen)
                {
                        handleInstallerClick(x, y, mouseX, mouseY);
                        return true;
                }

                if(poweredOff)
                {
                        int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                        int btnX = x + (Laptop.SCREEN_WIDTH / 2) - 34;
                        int btnY = screenTop + (Laptop.SCREEN_HEIGHT / 2) - 8;

                        if(isMouseInside(mouseX, mouseY, btnX, btnY, btnX + 68, btnY + 16))
                        {
                                startBootLoader();
                        }
                        return true;
                }

                if(bootLoaderOpen || bootingOs)
                {
                        return true;
                }

                int powerX = x + 257;
                int powerY = y + 2;

                if(powerMenuOpen)
                {
                        int menuX = x + 238;
                        int menuY = y - 43;

                        if(isMouseInside(mouseX, mouseY, menuX + 3, menuY + 4, menuX + 75, menuY + 19))
                        {
                                powerMenuOpen = false;
                                startBootLoader();
                                return true;
                        }

                        if(isMouseInside(mouseX, mouseY, menuX + 3, menuY + 22, menuX + 75, menuY + 37))
                        {
                                powerMenuOpen = false;
                                poweredOff = true;
                                return true;
                        }
                }

                if(isMouseInside(mouseX, mouseY, powerX, powerY, powerX + 14, powerY + 14))
                {
                        powerMenuOpen = !powerMenuOpen;
                        return true;
                }

                return powerMenuOpen;
        }

        private void startBootLoader()
        {
                loadInstalledOs();
                requestUsbBootInfo();
                refreshUsbBootInfo();
                poweredOff = false;
                bootLoaderOpen = true;
                bootingOs = false;
                installerOpen = false;
                installing = false;
                selectedBootOption = 0;
                bootTimer = 100;
                osBootTimer = 0;
                osBootTotal = 0;
                keyHeld = false;
        }

        private void beginSelectedBoot()
        {
                bootingOs = true;
                bootLoaderOpen = true;
                loadInstalledOs();
                osBootName = selectedBootOption == 1 ? usbBootName : installedOsName;
                osBootTotal = 60 + random.nextInt(81);
                osBootTimer = osBootTotal;
        }

        private void handleBootKeyboard()
        {
                boolean keyDown = Keyboard.isKeyDown(Keyboard.KEY_UP) ||
                        Keyboard.isKeyDown(Keyboard.KEY_DOWN) ||
                        Keyboard.isKeyDown(Keyboard.KEY_RETURN) ||
                        Keyboard.isKeyDown(Keyboard.KEY_NUMPADENTER);

                if(!keyDown)
                {
                        keyHeld = false;
                        return;
                }

                if(keyHeld)
                {
                        return;
                }

                keyHeld = true;

                if(Keyboard.isKeyDown(Keyboard.KEY_UP) || Keyboard.isKeyDown(Keyboard.KEY_DOWN))
                {
                        selectedBootOption = usbBootable ? (selectedBootOption == 0 ? 1 : 0) : 0;
                        bootTimer = 100;
                        return;
                }

                if(Keyboard.isKeyDown(Keyboard.KEY_RETURN) || Keyboard.isKeyDown(Keyboard.KEY_NUMPADENTER))
                {
                        beginSelectedBoot();
                }
        }

        private void renderBootLoader(Minecraft mc, int x, int y)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, y + TaskBar.BAR_HEIGHT, Color.BLACK.getRGB());

                int left = x + 28;
                int top = screenTop + 22;

                if(bootingOs)
                {
                        int centerX = x + Laptop.SCREEN_WIDTH / 2;
                        int centerY = screenTop + Laptop.SCREEN_HEIGHT / 2;

                        mc.fontRenderer.drawString("Kristihack's Boot Loader", left, top, Color.WHITE.getRGB(), true);

                        String title = "Booting " + osBootName;
                        int titleWidth = mc.fontRenderer.getStringWidth(title);
                        mc.fontRenderer.drawString(title, centerX - titleWidth / 2, centerY - 18, new Color(85, 255, 85).getRGB(), true);

                        String wait = "Please wait...";
                        int waitWidth = mc.fontRenderer.getStringWidth(wait);
                        mc.fontRenderer.drawString(wait, centerX - waitWidth / 2, centerY - 3, new Color(170, 170, 170).getRGB(), true);

                        drawSpinner(mc, centerX - 6, screenTop + Laptop.SCREEN_HEIGHT - 42);
                        return;
                }

                mc.fontRenderer.drawString("Kristihack's Boot Loader", left, top, Color.WHITE.getRGB(), true);
                mc.fontRenderer.drawString("Select boot device:", left, top + 22, new Color(180, 180, 180).getRGB(), true);

                drawBootOption(mc, left, top + 43, 0, installedOsName, "Installed OS");
                if(usbBootable)
                {
                        drawBootOption(mc, left, top + 61, 1, "USB: " + usbBootName, "Bootable flash drive");
                }

                int seconds = Math.max(1, (bootTimer + 19) / 20);
                mc.fontRenderer.drawString("Booting default in " + seconds + "s...", left, top + 96, new Color(255, 255, 85).getRGB(), true);
                mc.fontRenderer.drawString("Use arrow keys and Enter", left, top + 110, new Color(150, 150, 150).getRGB(), true);
        }

        private void drawBootOption(Minecraft mc, int x, int y, int index, String name, String desc)
        {
                boolean selected = selectedBootOption == index;
                String prefix = selected ? "> " : "  ";

                mc.fontRenderer.drawString(prefix + name, x, y, selected ? new Color(85, 255, 85).getRGB() : Color.WHITE.getRGB(), true);
                mc.fontRenderer.drawString(desc, x + 90, y, new Color(150, 150, 150).getRGB(), false);
        }

        private void renderPoweredOff(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, y + TaskBar.BAR_HEIGHT, Color.BLACK.getRGB());

                mc.fontRenderer.drawString("Powered off", x + 8, screenTop + 8, Color.WHITE.getRGB(), true);

                int btnX = x + (Laptop.SCREEN_WIDTH / 2) - 34;
                int btnY = screenTop + (Laptop.SCREEN_HEIGHT / 2) - 8;

                drawButton(mc, btnX, btnY, 68, 16, "Power on", mouseX, mouseY, new Color(85, 255, 85));
        }

        private void renderInstaller(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int centerX = x + Laptop.SCREEN_WIDTH / 2;
                int left = x + 16;
                int contentY = screenTop + 46;

                Color bg = getThemeBg();
                Color dark = getThemeDark();
                Color accent = getThemeAccent();

                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, y + TaskBar.BAR_HEIGHT, bg.getRGB());

                if(getTheme() == 0)
                {
                        Gui.drawRect(x + 4, screenTop + 4, x + Laptop.SCREEN_WIDTH - 4, y + TaskBar.BAR_HEIGHT - 4, new Color(0, 25, 0).getRGB());
                        mc.fontRenderer.drawString("C:\\MINE-DOS\\SETUP.EXE", x + 8, screenTop + 8, accent.getRGB(), false);
                        mc.fontRenderer.drawString("====================================", x + 8, screenTop + 20, accent.getRGB(), false);
                }
                else if(getTheme() == 1)
                {
                        Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, screenTop + 22, dark.getRGB());
                        Gui.drawRect(x + 8, screenTop + 30, x + Laptop.SCREEN_WIDTH - 8, y + TaskBar.BAR_HEIGHT - 8, new Color(8, 18, 45).getRGB());
                        mc.fontRenderer.drawString("[ Minux graphical installer ]", x + 10, screenTop + 8, Color.WHITE.getRGB(), true);
                }
                else
                {
                        Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, screenTop + 24, dark.getRGB());
                        Gui.drawRect(x + 8, screenTop + 32, x + Laptop.SCREEN_WIDTH - 8, y + TaskBar.BAR_HEIGHT - 8, new Color(45, 8, 8).getRGB());
                        mc.fontRenderer.drawString("!!! DEVICE RECOVERY ENVIRONMENT !!!", x + 10, screenTop + 8, accent.getRGB(), true);
                }

                String title = usbBootName + " Setup";
                int titleWidth = mc.fontRenderer.getStringWidth(title);
                mc.fontRenderer.drawString(title, centerX - titleWidth / 2, screenTop + 31, accent.getRGB(), true);

                if(installing)
                {
                        renderMineDosInstallingScreen(mc, x, y, mouseX, mouseY);
                        return;
                }
                if(installerPage == 0)
                {
                        mc.fontRenderer.drawString("> Welcome to " + usbBootName + " Setup", left, contentY, accent.getRGB(), true);
                        mc.fontRenderer.drawString("> Booted from USB installer environment.", left, contentY + 14, Color.WHITE.getRGB(), false);
                        mc.fontRenderer.drawString("> DeviceOS is not running here.", left, contentY + 28, Color.WHITE.getRGB(), false);
                        drawButton(mc, centerX - 70, contentY + 62, 140, 18, "Continue", mouseX, mouseY, accent);
                        drawButton(mc, centerX - 70, contentY + 86, 140, 18, "Power Off", mouseX, mouseY, accent);
                }
                else if(installerPage == 1)
                {
                        mc.fontRenderer.drawString("License Agreement", left, contentY, accent.getRGB(), true);
                        mc.fontRenderer.drawString(usbBootName + " experimental installer license.", left, contentY + 16, Color.WHITE.getRGB(), false);
                        mc.fontRenderer.drawString("No warranty. If it explodes, blame the user.", left, contentY + 30, Color.WHITE.getRGB(), false);
                        mc.fontRenderer.drawString("Installing may overwrite system files.", left, contentY + 44, Color.WHITE.getRGB(), false);

                        drawButton(mc, left, contentY + 64, 150, 18, licenseAccepted ? "[X] Accept license" : "[ ] Accept license", mouseX, mouseY, accent);
                        drawButton(mc, centerX - 105, contentY + 94, 90, 18, "Back", mouseX, mouseY, accent);
                        drawButton(mc, centerX + 15, contentY + 94, 90, 18, "Next", mouseX, mouseY, accent);
                }
                else if(installerPage == 2)
                {
                        mc.fontRenderer.drawString("Drive Configuration", left, contentY, accent.getRGB(), true);
                        mc.fontRenderer.drawString(getTheme() == 1 ? "Disk: /dev/laptop0" : "Target drive: C: Main HDD", left, contentY + 18, Color.WHITE.getRGB(), false);
                        mc.fontRenderer.drawString(getTheme() == 1 ? "Layout: /boot + /root" : "Filesystem: MDFS / FAT-like", left, contentY + 32, Color.WHITE.getRGB(), false);
                        mc.fontRenderer.drawString(getTheme() == 2 ? "Mode: Repair + reinstall" : "Boot mode: USB installer", left, contentY + 46, Color.WHITE.getRGB(), false);

                        drawButton(mc, left, contentY + 66, 190, 18, "Selected: Main Drive", mouseX, mouseY, accent);
                        drawButton(mc, centerX - 105, contentY + 104, 90, 18, "Back", mouseX, mouseY, accent);
                        drawButton(mc, centerX + 15, contentY + 104, 90, 18, "Next", mouseX, mouseY, accent);
                }
                else if(installerPage == 3)
                {
                        mc.fontRenderer.drawString("Final Confirmation", left, contentY, accent.getRGB(), true);
                        mc.fontRenderer.drawString("OS: " + usbBootName, left, contentY + 18, Color.WHITE.getRGB(), false);
                        mc.fontRenderer.drawString("Target: Main Drive", left, contentY + 32, Color.WHITE.getRGB(), false);
                        mc.fontRenderer.drawString("Install time: 20-35 seconds", left, contentY + 46, Color.WHITE.getRGB(), false);

                        String sure = getTheme() == 2 ? "Are you sure you want to recover this laptop?" : "Are you sure you want to install?";
                        int w = mc.fontRenderer.getStringWidth(sure);
                        mc.fontRenderer.drawString(sure, centerX - w / 2, contentY + 72, new Color(255, 230, 120).getRGB(), true);

                        drawButton(mc, centerX - 105, contentY + 98, 90, 18, "Back", mouseX, mouseY, accent);
                        drawButton(mc, centerX + 15, contentY + 98, 90, 18, "Install", mouseX, mouseY, accent);
                }
                else
                {
                        int w = mc.fontRenderer.getStringWidth(installerMessage);
                        mc.fontRenderer.drawString(installerMessage, centerX - w / 2, contentY + 24, accent.getRGB(), true);
                        mc.fontRenderer.drawString("Restart required.", centerX - 40, contentY + 48, Color.WHITE.getRGB(), false);
                        drawButton(mc, centerX - 70, contentY + 80, 140, 18, "Restart", mouseX, mouseY, accent);
                }
        }


        private void renderMineDosInstallingScreen(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int bottom = y + TaskBar.BAR_HEIGHT;

                int percent = installDisplayPercent;

                if(installTimer <= 0)
                {
                        percent = 100;
                }

                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, bottom, new Color(0, 48, 0).getRGB());
                Gui.drawRect(x + 6, screenTop + 6, x + Laptop.SCREEN_WIDTH - 6, bottom - 6, new Color(0, 76, 0).getRGB());

                int winX = x + 22;
                int winY = screenTop + 22;
                int winW = Laptop.SCREEN_WIDTH - 44;
                int winH = Laptop.SCREEN_HEIGHT - 50;

                Gui.drawRect(winX - 2, winY - 2, winX + winW + 2, winY + winH + 2, new Color(20, 80, 20).getRGB());
                Gui.drawRect(winX, winY, winX + winW, winY + 18, new Color(220, 245, 220).getRGB());
                Gui.drawRect(winX, winY + 18, winX + winW, winY + winH, new Color(238, 248, 238).getRGB());

                mc.fontRenderer.drawString("MineDOS Setup", winX + 7, winY + 6, new Color(0, 55, 0).getRGB(), false);
                mc.fontRenderer.drawString("Installing MineDOS...", winX + 24, winY + 28, new Color(0, 70, 0).getRGB(), true);

                mc.fontRenderer.drawString("Setup is installing MineDOS.", winX + 24, winY + 48, Color.BLACK.getRGB(), false);
                mc.fontRenderer.drawString("Do not power off the laptop.", winX + 24, winY + 60, Color.BLACK.getRGB(), false);

                int stepX = winX + 44;
                int stepY = winY + 82;

                drawInstallStep(mc, stepX, stepY, 0, percent, "Copying files");
                drawInstallStep(mc, stepX, stepY + 13, 1, percent, "Expanding files");
                drawInstallStep(mc, stepX, stepY + 26, 2, percent, "Features");
                drawInstallStep(mc, stepX, stepY + 39, 3, percent, "Updates");
                drawInstallStep(mc, stepX, stepY + 52, 4, percent, "Completing");

                drawPhaseBar(mc, x, bottom, percent);
        }
        private void drawInstallStep(Minecraft mc, int x, int y, int index, int percent, String text)
        {
                int step = getInstallStep(percent);
                int color = new Color(95, 95, 95).getRGB();

                if(index < step)
                {
                        mc.fontRenderer.drawString("v", x - 14, y, new Color(20, 170, 40).getRGB(), true);
                        color = new Color(80, 80, 80).getRGB();
                }
                else if(index == step)
                {
                        mc.fontRenderer.drawString(">", x - 14, y, Color.BLACK.getRGB(), true);
                        color = Color.BLACK.getRGB();
                }

                String label = text;

                if(index == step && index == 1)
                {
                        label = text + " " + percent + "%";
                }

                mc.fontRenderer.drawString(label, x, y, color, index == step);
        }
        private int getInstallStep(int percent)
        {
                if(percent < 15)
                {
                        return 0;
                }

                if(percent < 65)
                {
                        return 1;
                }

                if(percent < 78)
                {
                        return 2;
                }

                if(percent < 92)
                {
                        return 3;
                }

                return 4;
        }

        private void drawPhaseBar(Minecraft mc, int x, int bottom, int percent)
        {
                int y = bottom - 22;
                int w1 = 105;
                int w2 = 145;

                Gui.drawRect(x, y, x + Laptop.SCREEN_WIDTH, bottom, new Color(10, 90, 25).getRGB());

                Gui.drawRect(x, y, x + w1, y + 4, new Color(60, 210, 80).getRGB());
                Gui.drawRect(x + w1 + 2, y, x + w1 + 2 + w2, y + 4, new Color(20, 120, 35).getRGB());

                int fill = Math.min(w2, Math.max(0, percent * w2 / 100));
                Gui.drawRect(x + w1 + 2, y, x + w1 + 2 + fill, y + 4, new Color(90, 255, 90).getRGB());

                mc.fontRenderer.drawString("1", x + 6, y + 8, Color.WHITE.getRGB(), true);
                mc.fontRenderer.drawString("Collecting info", x + 24, y + 9, Color.WHITE.getRGB(), false);

                mc.fontRenderer.drawString("2", x + w1 + 10, y + 8, Color.WHITE.getRGB(), true);
                mc.fontRenderer.drawString("Installing MineDOS", x + w1 + 28, y + 9, Color.WHITE.getRGB(), false);
        }
        private void handleInstallerClick(int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int centerX = x + Laptop.SCREEN_WIDTH / 2;
                int left = x + 16;
                int contentY = screenTop + 46;

                if(installerPage == 0)
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 70, contentY + 62, centerX + 70, contentY + 80))
                        {
                                installerPage = 1;
                        }
                        else if(isMouseInside(mouseX, mouseY, centerX - 70, contentY + 86, centerX + 70, contentY + 104))
                        {
                                installerOpen = false;
                                poweredOff = true;
                        }
                }
                else if(installerPage == 1)
                {
                        if(isMouseInside(mouseX, mouseY, left, contentY + 64, left + 150, contentY + 82))
                        {
                                licenseAccepted = !licenseAccepted;
                        }
                        else if(isMouseInside(mouseX, mouseY, centerX - 105, contentY + 94, centerX - 15, contentY + 112))
                        {
                                installerPage = 0;
                        }
                        else if(isMouseInside(mouseX, mouseY, centerX + 15, contentY + 94, centerX + 105, contentY + 112) && licenseAccepted)
                        {
                                installerPage = 2;
                        }
                }
                else if(installerPage == 2)
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 105, contentY + 104, centerX - 15, contentY + 122))
                        {
                                installerPage = 1;
                        }
                        else if(isMouseInside(mouseX, mouseY, centerX + 15, contentY + 104, centerX + 105, contentY + 122))
                        {
                                installerPage = 3;
                        }
                }
                else if(installerPage == 3)
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 105, contentY + 98, centerX - 15, contentY + 116))
                        {
                                installerPage = 2;
                        }
                        else if(isMouseInside(mouseX, mouseY, centerX + 15, contentY + 98, centerX + 105, contentY + 116))
                        {
                                installing = true;
                                installTotal = 400 + random.nextInt(301);
                                installTimer = installTotal;
                        }
                }
                else
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 70, contentY + 80, centerX + 70, contentY + 98))
                        {
                                installerOpen = false;
                                startBootLoader();
                        }
                }
        }

        private void drawPowerButton(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                mc.getTextureManager().bindTexture(POWER_ICONS_GUI);
                Gui.drawModalRectWithCustomSizedTexture(x + 2, y + 2, 170, 30, 10, 10, 200, 200);
        }

        private void renderPowerMenu(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int menuX = x + 238;
                int menuY = y - 43;

                Gui.drawRect(menuX, menuY, menuX + 82, menuY + 41, new Color(20, 20, 20).getRGB());
                drawButton(mc, menuX + 3, menuY + 4, 72, 15, "Restart", mouseX, mouseY, new Color(85, 255, 85));
                drawButton(mc, menuX + 3, menuY + 22, 72, 15, "Shut down", mouseX, mouseY, new Color(255, 85, 85));
        }

        private void drawButton(Minecraft mc, int x, int y, int width, int height, String text, int mouseX, int mouseY, Color accent)
        {
                int color = isMouseInside(mouseX, mouseY, x, y, x + width, y + height) ? accent.darker().getRGB() : new Color(55, 55, 55).getRGB();
                Gui.drawRect(x, y, x + width, y + height, new Color(25, 25, 25).getRGB());
                Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, color);

                int textWidth = mc.fontRenderer.getStringWidth(text);
                mc.fontRenderer.drawString(text, x + width / 2 - textWidth / 2, y + 5, Color.WHITE.getRGB(), true);
        }

        private void drawLoadingBar(Minecraft mc, int x, int y, int width, int height, int progress)
        {
                Gui.drawRect(x, y, x + width, y + height, new Color(35, 35, 35).getRGB());
                Gui.drawRect(x + 1, y + 1, x + width - 1, y + height - 1, Color.BLACK.getRGB());

                int fill = (width - 2) * progress / 100;
                Gui.drawRect(x + 1, y + 1, x + 1 + fill, y + height - 1, getThemeAccent().getRGB());
        }

        private void drawSpinner(Minecraft mc, int x, int y)
        {
                int frame = spinnerTick % 32;
                int u = (frame % 8) * 12;
                int v = 12 + (frame / 8) * 12;

                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GlStateManager.enableBlend();
                mc.getTextureManager().bindTexture(COMPONENTS_GUI);
                Gui.drawModalRectWithCustomSizedTexture(x, y, u, v, 12, 12, 256, 256);
                GlStateManager.disableBlend();
        }

        private int getTheme()
        {
                String lower = usbBootName.toLowerCase();

                if(lower.contains("minux"))
                {
                        return 1;
                }

                if(lower.contains("recovery"))
                {
                        return 2;
                }

                return 0;
        }

        private Color getThemeBg()
        {
                return getTheme() == 1 ? new Color(4, 10, 28) : getTheme() == 2 ? new Color(30, 4, 4) : new Color(0, 10, 0);
        }

        private Color getThemeDark()
        {
                return getTheme() == 1 ? new Color(12, 42, 90) : getTheme() == 2 ? new Color(95, 15, 15) : new Color(5, 65, 20);
        }

        private Color getThemeAccent()
        {
                return getTheme() == 1 ? new Color(80, 170, 255) : getTheme() == 2 ? new Color(255, 75, 75) : new Color(70, 255, 70);
        }


        public void setUsbBootInfo(boolean bootable, String name)
        {
                this.usbBootable = bootable;

                if(name != null && !name.isEmpty())
                {
                        this.usbBootName = name;
                }
                else
                {
                        this.usbBootName = "USB Drive";
                }
        }

        private void refreshUsbBootInfo()
        {
                usbBootable = TaskGetBootOptions.LAST_USB_BOOTABLE;

                if(TaskGetBootOptions.LAST_USB_OS_NAME != null && !TaskGetBootOptions.LAST_USB_OS_NAME.isEmpty())
                {
                        usbBootName = TaskGetBootOptions.LAST_USB_OS_NAME;
                }
                else
                {
                        usbBootName = "USB Drive";
                }

                if(!usbBootable && selectedBootOption == 1)
                {
                        selectedBootOption = 0;
                }
        }

        private void requestUsbBootInfo()
        {
                BlockPos pos = findLaptopPos();

                if(pos == null)
                {
                        return;
                }

                try
                {
                        java.lang.reflect.Method method = TaskManager.class.getMethod("sendTask", Task.class);
                        method.invoke(null, new TaskGetBootOptions(pos));
                }
                catch(Exception e)
                {
                }
        }

        private BlockPos findLaptopPos()
        {
                if(laptop == null)
                {
                        return null;
                }

                try
                {
                        String[] methodNames = new String[] { "getPos", "getPosition", "getBlockPos" };

                        for(String name : methodNames)
                        {
                                try
                                {
                                        java.lang.reflect.Method method = laptop.getClass().getMethod(name);
                                        Object value = method.invoke(laptop);

                                        if(value instanceof BlockPos)
                                        {
                                                return (BlockPos) value;
                                        }
                                }
                                catch(Exception e)
                                {
                                }
                        }

                        java.lang.reflect.Field[] fields = laptop.getClass().getDeclaredFields();

                        for(java.lang.reflect.Field field : fields)
                        {
                                field.setAccessible(true);
                                Object value = field.get(laptop);

                                if(value instanceof BlockPos)
                                {
                                        return (BlockPos) value;
                                }

                                if(value instanceof TileEntity)
                                {
                                        return ((TileEntity) value).getPos();
                                }
                        }
                }
                catch(Exception e)
                {
                }

                return null;
        }

        private java.io.File getInstalledOsFile()
        {
                java.io.File configDir = new java.io.File(Minecraft.getMinecraft().mcDataDir, "config");

                if(!configDir.exists())
                {
                        configDir.mkdirs();
                }

                return new java.io.File(configDir, "device_installed_os.txt");
        }

        private void saveInstalledOs()
        {
                try
                {
                        java.io.File file = getInstalledOsFile();
                        java.io.PrintWriter writer = new java.io.PrintWriter(file);
                        writer.println(installedOsName == null || installedOsName.isEmpty() ? "DeviceOS" : installedOsName);
                        writer.close();
                }
                catch(Exception e)
                {
                }
        }

        private void loadInstalledOs()
        {
                try
                {
                        java.io.File file = getInstalledOsFile();

                        if(!file.exists())
                        {
                                installedOsName = "DeviceOS";
                                return;
                        }

                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                        String line = reader.readLine();
                        reader.close();

                        if(line != null && !line.trim().isEmpty())
                        {
                                installedOsName = line.trim();
                        }
                        else
                        {
                                installedOsName = "DeviceOS";
                        }
                }
                catch(Exception e)
                {
                        installedOsName = "DeviceOS";
                }
        }

        private boolean isMineDOS(String name)
        {
                if(name == null)
                {
                        return false;
                }

                return name.toLowerCase().contains("minedos") || name.toLowerCase().contains("mine-dos");
        }

        private void renderMineDosOobe(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int centerX = x + Laptop.SCREEN_WIDTH / 2;

                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, y + TaskBar.BAR_HEIGHT, new Color(0, 38, 78).getRGB());
                Gui.drawRect(x + 8, screenTop + 8, x + Laptop.SCREEN_WIDTH - 8, y + TaskBar.BAR_HEIGHT - 8, new Color(12, 78, 128).getRGB());

                mc.fontRenderer.drawString("MineDOS First Time Setup", x + 18, screenTop + 18, Color.WHITE.getRGB(), true);

                mc.getTextureManager().bindTexture(UNKNOWN_ICON_GUI);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

                for(int i = 0; i < 4; i++)
                {
                        Gui.drawModalRectWithCustomSizedTexture(centerX - 38 + i * 22, screenTop + 42, 0, 0, 16, 16, 16, 16);
                }

                if(mineDosOobePage == 0)
                {
                        mc.fontRenderer.drawString("Welcome to MineDOS", centerX - 58, screenTop + 72, Color.WHITE.getRGB(), true);
                        mc.fontRenderer.drawString("This setup will prepare your laptop.", centerX - 92, screenTop + 88, new Color(220, 240, 255).getRGB(), false);
                        mc.fontRenderer.drawString("Unknown icons are temporary placeholders.", centerX - 102, screenTop + 102, new Color(220, 240, 255).getRGB(), false);

                        drawButton(mc, centerX - 50, screenTop + 126, 100, 18, "Next", mouseX, mouseY, new Color(80, 170, 255));
                }
                else if(mineDosOobePage == 1)
                {
                        mc.fontRenderer.drawString("User account", centerX - 44, screenTop + 72, Color.WHITE.getRGB(), true);
                        mc.fontRenderer.drawString("Username: " + mineDosUserName, centerX - 55, screenTop + 90, new Color(220, 240, 255).getRGB(), false);
                        mc.fontRenderer.drawString("Password: none", centerX - 55, screenTop + 104, new Color(220, 240, 255).getRGB(), false);

                        drawButton(mc, centerX - 105, screenTop + 126, 90, 18, "Back", mouseX, mouseY, new Color(80, 170, 255));
                        drawButton(mc, centerX + 15, screenTop + 126, 90, 18, "Next", mouseX, mouseY, new Color(80, 170, 255));
                }
                else
                {
                        mc.fontRenderer.drawString("Ready to start", centerX - 48, screenTop + 72, Color.WHITE.getRGB(), true);
                        mc.fontRenderer.drawString("MineDOS will now open the desktop.", centerX - 86, screenTop + 90, new Color(220, 240, 255).getRGB(), false);

                        drawButton(mc, centerX - 60, screenTop + 126, 120, 18, "Finish", mouseX, mouseY, new Color(80, 170, 255));
                }
        }

        private void handleMineDosOobeClick(int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);
                int centerX = x + Laptop.SCREEN_WIDTH / 2;

                if(mineDosOobePage == 0)
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 50, screenTop + 126, centerX + 50, screenTop + 144))
                        {
                                mineDosOobePage = 1;
                        }
                }
                else if(mineDosOobePage == 1)
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 105, screenTop + 126, centerX - 15, screenTop + 144))
                        {
                                mineDosOobePage = 0;
                        }
                        else if(isMouseInside(mouseX, mouseY, centerX + 15, screenTop + 126, centerX + 105, screenTop + 144))
                        {
                                mineDosOobePage = 2;
                        }
                }
                else
                {
                        if(isMouseInside(mouseX, mouseY, centerX - 60, screenTop + 126, centerX + 60, screenTop + 144))
                        {
                                mineDosOobeDone = true;
                                mineDosOobeOpen = false;
                                mineDosDesktopOpen = false;
                                saveMineDosState();
                        }
                }
        }

        private void renderMineDosDesktop(Minecraft mc, int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);

                Gui.drawRect(x, screenTop, x + Laptop.SCREEN_WIDTH, y + TaskBar.BAR_HEIGHT, new Color(0, 90, 105).getRGB());

                mc.fontRenderer.drawString("MineDOS Desktop", x + 8, screenTop + 8, Color.WHITE.getRGB(), true);

                drawMineDosIcon(mc, x + 18, screenTop + 30, "Computer");
                drawMineDosIcon(mc, x + 18, screenTop + 58, "Files");
                drawMineDosIcon(mc, x + 18, screenTop + 86, "MineCMD");
                drawMineDosIcon(mc, x + 18, screenTop + 114, "Settings");

                Gui.drawRect(x, y, x + Laptop.SCREEN_WIDTH, y + TaskBar.BAR_HEIGHT, new Color(40, 40, 40).getRGB());
                Gui.drawRect(x + 2, y + 2, x + 44, y + 16, new Color(70, 70, 70).getRGB());
                mc.fontRenderer.drawString("MINE", x + 10, y + 6, Color.WHITE.getRGB(), true);

                mc.fontRenderer.drawString("Logged in as " + mineDosUserName, x + 58, y + 6, Color.WHITE.getRGB(), false);
        }

        private void drawMineDosIcon(Minecraft mc, int x, int y, String label)
        {
                mc.getTextureManager().bindTexture(UNKNOWN_ICON_GUI);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, 16, 16, 16, 16);
                mc.fontRenderer.drawString(label, x + 22, y + 5, Color.WHITE.getRGB(), true);
        }

        private void handleMineDosDesktopClick(int x, int y, int mouseX, int mouseY)
        {
                int screenTop = y - (Laptop.SCREEN_HEIGHT - TaskBar.BAR_HEIGHT);

                if(isMouseInside(mouseX, mouseY, x + 2, y + 2, x + 44, y + 16))
                {
                        mineDosDesktopOpen = false;
                        startBootLoader();
                }
        }

        private java.io.File getMineDosStateFile()
        {
                java.io.File configDir = new java.io.File(Minecraft.getMinecraft().mcDataDir, "config");

                if(!configDir.exists())
                {
                        configDir.mkdirs();
                }

                return new java.io.File(configDir, "minedos_oobe.txt");
        }

        private void saveMineDosState()
        {
                try
                {
                        java.io.PrintWriter writer = new java.io.PrintWriter(getMineDosStateFile());
                        writer.println(mineDosOobeDone ? "done=true" : "done=false");
                        writer.println("user=" + mineDosUserName);
                        writer.close();
                }
                catch(Exception e)
                {
                }
        }

        private void loadMineDosState()
        {
                try
                {
                        java.io.File file = getMineDosStateFile();

                        if(!file.exists())
                        {
                                mineDosOobeDone = false;
                                return;
                        }

                        java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
                        String line;

                        while((line = reader.readLine()) != null)
                        {
                                if(line.startsWith("done="))
                                {
                                        mineDosOobeDone = line.substring(5).trim().equalsIgnoreCase("true");
                                }
                                else if(line.startsWith("user="))
                                {
                                        String user = line.substring(5).trim();

                                        if(!user.isEmpty())
                                        {
                                                mineDosUserName = user;
                                        }
                                }
                        }

                        reader.close();
                }
                catch(Exception e)
                {
                        mineDosOobeDone = false;
                }
        }

        private void restoreInstalledOsSession()
        {
                loadInstalledOs();
                loadMineDosState();

                if(isMineDOS(installedOsName))
                {
                        poweredOff = false;
                        bootLoaderOpen = false;
                        bootingOs = false;
                        installerOpen = false;
                        installing = false;

                        if(getMineDOS().isOobeDone())
                        {
                                mineDosOobeOpen = false;
                                mineDosDesktopOpen = false;
                        }
                        else
                        {
                                mineDosDesktopOpen = false;
                                mineDosOobeOpen = false;
                                mineDosOobePage = 0;
                        }
                }
        }

        public boolean handleKeyTyped(char character, int code)
        {
                if(mineDosOobeOpen || mineDosDesktopOpen)
                {
                        boolean handled = getMineDOS().handleKeyTyped(character, code);
                        applyMineDosAction();
                        return handled;
                }

                return false;
        }

        private boolean forceMineDosSession()
        {
                return false;
        }

        private void applyMineDosAction()
        {
                String action = getMineDOS().consumeAction();

                if(action.equals("reboot"))
                {
                        mineDosOobeOpen = false;
                        mineDosDesktopOpen = false;
                        startBootLoader();
                }
                else if(action.equals("shutdown"))
                {
                        mineDosOobeOpen = false;
                        mineDosDesktopOpen = false;
                        poweredOff = true;
                }
        }
        private boolean isMouseInside(int mouseX, int mouseY, int left, int top, int right, int bottom)
        {
                return mouseX >= left && mouseY >= top && mouseX < right && mouseY < bottom;
        }
}