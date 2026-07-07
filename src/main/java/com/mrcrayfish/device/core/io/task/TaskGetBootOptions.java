package com.mrcrayfish.device.core.io.task;

import com.mrcrayfish.device.api.task.Task;
import com.mrcrayfish.device.core.io.FileSystem;
import com.mrcrayfish.device.core.io.ServerFile;
import com.mrcrayfish.device.core.io.ServerFolder;
import com.mrcrayfish.device.core.io.drive.AbstractDrive;
import com.mrcrayfish.device.tileentity.TileEntityLaptop;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TaskGetBootOptions extends Task
{
    public static boolean LAST_USB_BOOTABLE = false;
    public static String LAST_USB_OS_NAME = "";
    public static String LAST_USB_OS_ID = "";

    private BlockPos pos;

    private boolean usbBootable = false;
    private String usbOsName = "";
    private String usbOsId = "";

    private TaskGetBootOptions()
    {
        super("get_boot_options");
    }

    public TaskGetBootOptions(BlockPos pos)
    {
        this();
        this.pos = pos;
    }

    @Override
    public void prepareRequest(NBTTagCompound nbt)
    {
        nbt.setLong("pos", pos.toLong());
    }

    @Override
    public void processRequest(NBTTagCompound nbt, World world, EntityPlayer player)
    {
        TileEntity tileEntity = world.getTileEntity(BlockPos.fromLong(nbt.getLong("pos")));

        if(tileEntity instanceof TileEntityLaptop)
        {
            TileEntityLaptop laptop = (TileEntityLaptop) tileEntity;
            FileSystem fileSystem = laptop.getFileSystem();
            AbstractDrive drive = fileSystem.getAttachedDrive();

            if(drive != null)
            {
                ServerFolder root = drive.getRoot(world);

                if(root != null)
                {
                    ServerFile info = root.getFile("USB.info");

                    if(info != null && info.getData() != null)
                    {
                        NBTTagCompound data = info.getData();

                        if(data.getBoolean("bootable"))
                        {
                            usbBootable = true;
                            usbOsName = data.getString("os_name");
                            usbOsId = data.getString("os_id");

                            if(usbOsName == null || usbOsName.isEmpty())
                            {
                                usbOsName = "Bootable USB";
                            }

                            if(usbOsId == null)
                            {
                                usbOsId = "";
                            }
                        }
                    }
                }
            }

            this.setSuccessful();
        }
    }

    @Override
    public void prepareResponse(NBTTagCompound nbt)
    {
        nbt.setBoolean("usb_bootable", usbBootable);
        nbt.setString("usb_os_name", usbOsName);
        nbt.setString("usb_os_id", usbOsId);
    }

    @Override
    public void processResponse(NBTTagCompound nbt)
    {
        LAST_USB_BOOTABLE = nbt.getBoolean("usb_bootable");
        LAST_USB_OS_NAME = nbt.getString("usb_os_name");
        LAST_USB_OS_ID = nbt.getString("usb_os_id");
    }
}