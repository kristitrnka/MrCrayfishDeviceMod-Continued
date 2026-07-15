package com.mrcrayfish.device.programs;

import com.mrcrayfish.device.api.app.Application;
import com.mrcrayfish.device.api.app.Component;
import com.mrcrayfish.device.api.app.Icons;
import com.mrcrayfish.device.api.app.Layout;
import com.mrcrayfish.device.api.app.component.Button;
import com.mrcrayfish.device.api.app.component.ItemList;
import com.mrcrayfish.device.api.app.component.Label;
import com.mrcrayfish.device.api.app.renderer.ListItemRenderer;
import com.mrcrayfish.device.api.task.Task;
import com.mrcrayfish.device.api.task.TaskManager;
import com.mrcrayfish.device.api.utils.RenderUtil;
import com.mrcrayfish.device.core.Laptop;
import com.mrcrayfish.device.core.client.CameraViewRenderer;
import com.mrcrayfish.device.core.network.NetworkDevice;
import com.mrcrayfish.device.core.network.task.TaskGetDevices;
import com.mrcrayfish.device.programs.system.object.ColorScheme;
import com.mrcrayfish.device.tileentity.TileEntitySecurityCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.Constants;

import javax.annotation.Nullable;
import java.awt.Color;

/**
 * Lists security cameras connected to the laptop's router and displays a live
 * view from the selected camera while its chunk is loaded on the client.
 */
public class ApplicationSecurityCameras extends Application
{
    private ItemList<NetworkDevice> cameraList;
    private Label statusLabel;
    private CameraPreview preview;

    public ApplicationSecurityCameras()
    {
        this.setDefaultWidth(320);
        this.setDefaultHeight(150);
    }

    @Override
    public void init(@Nullable NBTTagCompound intent)
    {
        Layout layout = new Layout(320, 150);

        Label title = new Label("Network cameras", 5, 6);
        layout.addComponent(title);

        Button refreshButton = new Button(301, 2, Icons.RELOAD);
        refreshButton.setPadding(2);
        refreshButton.setToolTip("Refresh", "Scan the laptop's router for connected cameras");
        refreshButton.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0)
            {
                refreshCameras();
            }
        });
        layout.addComponent(refreshButton);

        statusLabel = new Label("Ready to scan", 5, 19);
        statusLabel.setShadow(false);
        layout.addComponent(statusLabel);

        cameraList = new ItemList<>(5, 31, 105, 6);
        cameraList.setListItemRenderer(new ListItemRenderer<NetworkDevice>(16)
        {
            @Override
            public void render(NetworkDevice camera, Gui gui, Minecraft mc, int x, int y,
                               int width, int height, boolean selected)
            {
                ColorScheme colors = Laptop.getSystem().getSettings().getColorScheme();
                Gui.drawRect(x, y, x + width, y + height,
                        selected ? colors.getItemHighlightColor() : colors.getItemBackgroundColor());
                Icons.CAMERA.draw(mc, x + 3, y + 3);
                RenderUtil.drawStringClipped(camera.getName(), x + 17, y + 3, width - 20,
                        colors.getTextColor(), true);
            }
        });
        cameraList.setItemClickListener((camera, index, mouseButton) ->
        {
            if(mouseButton == 0)
            {
                preview.select(camera);
                updateSelectedStatus(camera);
            }
        });
        cameraList.sortBy((first, second) -> first.getName().compareToIgnoreCase(second.getName()));
        layout.addComponent(cameraList);

        preview = new CameraPreview(115, 31, 200, 112);
        layout.addComponent(preview);

        this.setCurrentLayout(layout);
        refreshCameras();
    }

    private void updateSelectedStatus(NetworkDevice camera)
    {
        BlockPos pos = camera.getPos();
        if(pos == null)
        {
            statusLabel.setText(camera.getName());
            return;
        }
        statusLabel.setText(camera.getName() + " - " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    private void refreshCameras()
    {
        cameraList.removeAll();
        cameraList.setLoading(true);
        preview.select(null);
        statusLabel.setText("Scanning router...");

        Task task = new TaskGetDevices(Laptop.getPos(), TileEntitySecurityCamera.class);
        task.setCallback((tag, success) ->
        {
            cameraList.setLoading(false);
            if(!success)
            {
                statusLabel.setText("Connect this laptop to a router first");
                return;
            }

            NBTTagList cameras = tag.getTagList("network_devices", Constants.NBT.TAG_COMPOUND);
            for(int i = 0; i < cameras.tagCount(); i++)
            {
                cameraList.addItem(NetworkDevice.fromTag(cameras.getCompoundTagAt(i)));
            }
            cameraList.sort();
            statusLabel.setText(cameras.tagCount() == 0
                    ? "No connected cameras found"
                    : "Select a camera to watch");
        });
        TaskManager.sendTask(task);
    }

    @Override
    public void onClose()
    {
        if(preview != null)
        {
            preview.close();
        }
        super.onClose();
    }

    @Override
    public void load(NBTTagCompound tagCompound)
    {
    }

    @Override
    public void save(NBTTagCompound tagCompound)
    {
    }

    private static class CameraPreview extends Component
    {
        private static final int UPDATE_INTERVAL_TICKS = 4;

        private final int width;
        private final int height;
        private final CameraViewRenderer renderer = new CameraViewRenderer();
        private NetworkDevice selectedCamera;
        private int updateTimer;

        private CameraPreview(int left, int top, int width, int height)
        {
            super(left, top);
            this.width = width;
            this.height = height;
        }

        private void select(@Nullable NetworkDevice camera)
        {
            this.selectedCamera = camera;
            this.updateTimer = 0;
        }

        @Override
        protected void handleTick()
        {
            if(updateTimer > 0)
            {
                updateTimer--;
            }
        }

        @Override
        protected void render(Laptop laptop, Minecraft mc, int x, int y, int mouseX, int mouseY,
                              boolean windowActive, float partialTicks)
        {
            Gui.drawRect(x, y, x + width, y + height, Color.BLACK.getRGB());

            if(selectedCamera != null && selectedCamera.getPos() != null && windowActive && updateTimer <= 0)
            {
                renderer.update(selectedCamera.getPos(), partialTicks);
                updateTimer = UPDATE_INTERVAL_TICKS;
            }

            renderer.draw(x + 1, y + 1, width - 2, height - 2);
            drawHorizontalLine(x, x + width - 1, y, Color.DARK_GRAY.getRGB());
            drawHorizontalLine(x, x + width - 1, y + height - 1, Color.DARK_GRAY.getRGB());
            drawVerticalLine(x, y, y + height - 1, Color.DARK_GRAY.getRGB());
            drawVerticalLine(x + width - 1, y, y + height - 1, Color.DARK_GRAY.getRGB());

            if(selectedCamera == null)
            {
                drawCenteredString(Laptop.fontRenderer, "SELECT A CAMERA", x + width / 2,
                        y + height / 2 - 4, Color.LIGHT_GRAY.getRGB());
            }
            else if(!renderer.hasSignal())
            {
                drawCenteredString(Laptop.fontRenderer, "NO SIGNAL", x + width / 2,
                        y + height / 2 - 4, Color.RED.getRGB());
            }
            else
            {
                Icons.LIVE.draw(mc, x + 5, y + 5);
                Laptop.fontRenderer.drawString("LIVE", x + 18, y + 6, Color.RED.getRGB(), true);
            }
        }

        private void close()
        {
            renderer.close();
        }
    }
}
