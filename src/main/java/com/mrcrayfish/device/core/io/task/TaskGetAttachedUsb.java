package com.mrcrayfish.device.core.io.task;

import com.mrcrayfish.device.api.task.Task;
import com.mrcrayfish.device.core.io.FileSystem;
import com.mrcrayfish.device.core.io.drive.AbstractDrive;
import com.mrcrayfish.device.tileentity.TileEntityLaptop;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class TaskGetAttachedUsb extends Task
{
    private BlockPos pos;
    private boolean connected = false;
    private String usbName = "";
    private String usbUuid = "";

    private TaskGetAttachedUsb()
    {
        super("get_attached_usb");
    }

    public TaskGetAttachedUsb(BlockPos pos)
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
                connected = true;
                usbName = drive.getName();
                usbUuid = drive.getUUID().toString();
            }

            this.setSuccessful();
        }
    }

    @Override
    public void prepareResponse(NBTTagCompound nbt)
    {
        nbt.setBoolean("connected", connected);
        nbt.setString("usb_name", usbName);
        nbt.setString("usb_uuid", usbUuid);
    }

    @Override
    public void processResponse(NBTTagCompound nbt)
    {
    }
}