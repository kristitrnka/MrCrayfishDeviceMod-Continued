package com.mrcrayfish.device.programs;

import com.mrcrayfish.device.api.app.Application;
import com.mrcrayfish.device.api.app.Component;
import com.mrcrayfish.device.api.app.Layout;
import com.mrcrayfish.device.api.app.component.Button;
import com.mrcrayfish.device.api.task.TaskManager;
import com.mrcrayfish.device.core.Laptop;
import com.mrcrayfish.device.core.io.task.TaskFlashOS;
import com.mrcrayfish.device.core.io.task.TaskGetAttachedUsb;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

public class ApplicationFlashator extends Application
{
    private static final OSImage[] IMAGES = new OSImage[]
    {
            new OSImage("MineDOS 1.0", "minedos", "47 MB"),
            new OSImage("Minux Alpha", "minux", "128 MB"),
            new OSImage("DeviceOS Recovery", "deviceos_recovery", "64 MB")
    };

    private int selectedImage = 0;

    private int downloadProgress = 0;
    private int flashProgress = 0;
    private int tick = 0;

    private boolean downloading = false;
    private boolean downloaded = false;
    private boolean flashing = false;
    private boolean flashed = false;
    private boolean flashWriteSent = false;

    private boolean osDropdownOpen = false;

    private boolean usbConnected = false;
    private String usbName = "No USB connected";
    private String usbUuid = "";

    private String status = "Select an ISO image and download it.";

    private Button btnOsDropdown;
    private Button btnOsMineDOS;
    private Button btnOsMinux;
    private Button btnOsRecovery;

    @Override
    public void init(@Nullable NBTTagCompound intent)
    {
        Layout layout = new Layout(355, 160);

        FlashatorPanel panel = new FlashatorPanel(8, 8);
        layout.addComponent(panel);

        btnOsDropdown = new Button(72, 27, 155, 16, getSelectedImage().name);
        btnOsDropdown.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0 && !downloading && !flashing)
            {
                osDropdownOpen = !osDropdownOpen;
                updateDropdowns();
            }
        });
        layout.addComponent(btnOsDropdown);

        btnOsMineDOS = createOsButton(72, 43, 0);
        btnOsMinux = createOsButton(72, 59, 1);
        btnOsRecovery = createOsButton(72, 75, 2);

        layout.addComponent(btnOsMineDOS);
        layout.addComponent(btnOsMinux);
        layout.addComponent(btnOsRecovery);

        Button btnRefreshUsb = new Button(72, 89, 90, 16, "Refresh USB");
        btnRefreshUsb.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0 && !downloading && !flashing)
            {
                refreshUsb();
            }
        });
        layout.addComponent(btnRefreshUsb);

        Button btnDownload = new Button(72, 135, 105, 16, "Download ISO");
        btnDownload.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0 && !downloading && !flashing)
            {
                startDownload();
            }
        });
        layout.addComponent(btnDownload);

        Button btnFlash = new Button(187, 135, 95, 16, "Flash USB");
        btnFlash.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0 && !downloading && !flashing)
            {
                startFlash();
            }
        });
        layout.addComponent(btnFlash);

        updateDropdowns();
        setCurrentLayout(layout);

        refreshUsb();
    }

    private Button createOsButton(int x, int y, int index)
    {
        Button button = new Button(x, y, 155, 16, IMAGES[index].name);
        button.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0 && !downloading && !flashing)
            {
                selectImage(index);
                osDropdownOpen = false;
                updateDropdowns();
            }
        });
        return button;
    }

    private void updateDropdowns()
    {
        btnOsMineDOS.setVisible(osDropdownOpen);
        btnOsMinux.setVisible(osDropdownOpen);
        btnOsRecovery.setVisible(osDropdownOpen);
    }

    private void refreshUsb()
    {
        status = "Scanning USB slot...";

        TaskGetAttachedUsb task = new TaskGetAttachedUsb(Laptop.getPos());
        task.setCallback((nbt, success) ->
        {
            if(success && nbt.getBoolean("connected"))
            {
                usbConnected = true;
                usbName = nbt.getString("usb_name");
                usbUuid = nbt.getString("usb_uuid");
                status = "USB detected: " + usbName;
            }
            else
            {
                usbConnected = false;
                usbName = "No USB connected";
                usbUuid = "";
                status = "No USB drive connected.";
            }
        });
        TaskManager.sendTask(task);
    }

    private void selectImage(int index)
    {
        selectedImage = index;
        downloadProgress = 0;
        flashProgress = 0;
        downloading = false;
        downloaded = false;
        flashing = false;
        flashed = false;
        flashWriteSent = false;

        btnOsDropdown.setText(getSelectedImage().name);
        status = "Selected " + getSelectedImage().name + ". Ready to download.";
    }

    private void startDownload()
    {
        downloadProgress = 0;
        flashProgress = 0;
        downloading = true;
        downloaded = false;
        flashing = false;
        flashed = false;
        flashWriteSent = false;
        osDropdownOpen = false;
        updateDropdowns();

        status = "Downloading " + getSelectedImage().name + " ISO...";
    }

    private void startFlash()
    {
        osDropdownOpen = false;
        updateDropdowns();

        if(!downloaded)
        {
            status = "Download the ISO before flashing.";
            return;
        }

        if(!usbConnected)
        {
            status = "Connect a USB drive before flashing.";
            return;
        }

        flashProgress = 0;
        flashing = true;
        flashed = false;
        flashWriteSent = false;
        status = "Flashing " + getSelectedImage().name + " to " + usbName + "...";
    }

    private void writeBootFiles()
    {
        if(flashWriteSent)
        {
            return;
        }

        flashWriteSent = true;
        status = "Writing boot files to USB...";

        OSImage image = getSelectedImage();
        TaskFlashOS task = new TaskFlashOS(Laptop.getPos(), image.id, image.name);
        task.setCallback((nbt, success) ->
        {
            if(success)
            {
                status = "Done. USB is now bootable. MUHAHAHA.";
            }
            else
            {
                status = "Flash failed: " + nbt.getString("message");
            }
        });
        TaskManager.sendTask(task);
    }

    private OSImage getSelectedImage()
    {
        return IMAGES[selectedImage];
    }

    @Override
    public void load(NBTTagCompound tagCompound)
    {
    }

    @Override
    public void save(NBTTagCompound tagCompound)
    {
    }

    private static class OSImage
    {
        private final String name;
        private final String id;
        private final String sizeText;
        private final int sizeMb;

        private OSImage(String name, String id, String sizeText)
        {
            this.name = name;
            this.id = id;
            this.sizeText = sizeText;
            this.sizeMb = Integer.parseInt(sizeText.replace(" MB", ""));
        }
    }

    private class FlashatorPanel extends Component
    {
        private FlashatorPanel(int left, int top)
        {
            super(left, top);
        }

        @Override
        protected void handleTick()
        {
            tick++;

            if(downloading && tick % 2 == 0)
            {
                downloadProgress++;

                if(downloadProgress >= 100)
                {
                    downloadProgress = 100;
                    downloading = false;
                    downloaded = true;
                    status = "Download complete. ISO ready to flash.";
                }
            }

            if(flashing && tick % 3 == 0)
            {
                flashProgress++;

                if(flashProgress >= 100)
                {
                    flashProgress = 100;
                    flashing = false;
                    flashed = true;
                    writeBootFiles();
                }
            }
        }

        @Override
        public void render(Laptop laptop, Minecraft mc, int x, int y, int mouseX, int mouseY, boolean windowActive, float partialTicks)
        {
            if(!this.visible)
            {
                return;
            }

            OSImage image = getSelectedImage();

            drawString(mc.fontRenderer, "Flashator 3000", x, y, 0xFFFFFF);

            drawString(mc.fontRenderer, "ISO:", x, y + 22, 0xFFFFFF);
            drawString(mc.fontRenderer, "Size: " + image.sizeText, x + 235, y + 30, 0xAAAAAA);

            drawString(mc.fontRenderer, "Download:", x, y + 55, 0xFFFFFF);
            drawProgressBar(mc, x + 80, y + 54, 170, 10, downloadProgress);

            drawString(mc.fontRenderer, "USB:", x, y + 86, 0xFFFFFF);
            drawString(mc.fontRenderer, usbName, x + 170, y + 92, usbConnected ? 0x55FF55 : 0xFF5555);

            drawString(mc.fontRenderer, "Flash:", x, y + 112, 0xFFFFFF);
            drawProgressBar(mc, x + 80, y + 111, 170, 10, flashProgress);

            drawString(mc.fontRenderer, "ETA: " + getEta(), x + 260, y + 113, 0xAAAAAA);

            drawString(mc.fontRenderer, status, x, y + 145, 0xFFFF55);
        }

        private String getEta()
        {
            if(downloading)
            {
                int remaining = Math.max(0, 100 - downloadProgress);
                return (remaining / 10 + 1) + "s";
            }

            if(flashing)
            {
                int remaining = Math.max(0, 100 - flashProgress);
                return (remaining / 7 + 1) + "s";
            }

            return "-";
        }

        private void drawProgressBar(Minecraft mc, int x, int y, int width, int height, int progress)
        {
            drawRect(x, y, x + width, y + height, 0xFF202020);
            drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF050505);

            int fill = (width - 2) * progress / 100;
            drawRect(x + 1, y + 1, x + 1 + fill, y + height - 1, 0xFF33AA33);

            drawString(mc.fontRenderer, progress + "%", x + width + 6, y + 1, 0xFFFFFF);
        }
    }
}