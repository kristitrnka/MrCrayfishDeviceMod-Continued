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

public class TaskFlashOS extends Task
{
    private BlockPos pos;
    private String osId;
    private String osName;

    private String message = "";

    private TaskFlashOS()
    {
        super("flash_os");
    }

    public TaskFlashOS(BlockPos pos, String osId, String osName)
    {
        this();
        this.pos = pos;
        this.osId = osId;
        this.osName = osName;
    }

    @Override
    public void prepareRequest(NBTTagCompound nbt)
    {
        nbt.setLong("pos", pos.toLong());
        nbt.setString("os_id", osId);
        nbt.setString("os_name", osName);
    }

    @Override
    public void processRequest(NBTTagCompound nbt, World world, EntityPlayer player)
    {
        TileEntity tileEntity = world.getTileEntity(BlockPos.fromLong(nbt.getLong("pos")));

        if(!(tileEntity instanceof TileEntityLaptop))
        {
            message = "Laptop not found.";
            return;
        }

        TileEntityLaptop laptop = (TileEntityLaptop) tileEntity;
        FileSystem fileSystem = laptop.getFileSystem();
        AbstractDrive drive = fileSystem.getAttachedDrive();

        if(drive == null)
        {
            message = "No USB drive connected.";
            return;
        }

        String osId = nbt.getString("os_id");
        String osName = nbt.getString("os_name");

        ServerFolder root = drive.getRoot(world);
        if(root == null)
        {
            message = "USB root not found.";
            return;
        }

        NBTTagCompound installerData = new NBTTagCompound();
        installerData.setString("type", "installer");
        installerData.setString("os_id", osId);
        installerData.setString("os_name", osName);
        installerData.setBoolean("bootable", true);
        installerData.setString("content", "Flashator 3000 installer for " + osName);

        NBTTagCompound infoData = new NBTTagCompound();
        infoData.setString("type", "usb_info");
        infoData.setString("os_id", osId);
        infoData.setString("os_name", osName);
        infoData.setBoolean("bootable", true);
        infoData.setString("flashed_by", "Flashator 3000");
        infoData.setString("content", "bootable=true\nos=" + osId + "\nname=" + osName + "\nflashed_by=Flashator 3000");

        NBTTagCompound setupData = new NBTTagCompound();
        setupData.setString("type", "setup_data");
        setupData.setString("content", "setup files for " + osName);

        NBTTagCompound driverData = new NBTTagCompound();
        driverData.setString("type", "driver");
        driverData.setString("content", "generic display/audio/network drivers");

        NBTTagCompound licenseData = new NBTTagCompound();
        licenseData.setString("type", "license");
        licenseData.setString("content", osName + " license files\nDo not ask why this exists. MUHAHAHA.");

        ServerFolder sources = new ServerFolder("sources");
        sources.add(new ServerFile("setup.dat", "text_editor", setupData), true);

        ServerFolder drivers = new ServerFolder("drivers");
        drivers.add(new ServerFile("generic driver.drv", "text_editor", driverData), true);

        ServerFolder license = new ServerFolder("license");
        license.add(new ServerFile("license.txt", "text_editor", licenseData), true);

        root.add(new ServerFile("installOS.xex", "text_editor", installerData), true);
        root.add(new ServerFile("USB.info", "text_editor", infoData), true);
        root.add(sources, true);
        root.add(drivers, true);
        root.add(license, true);

        laptop.markDirty();
        laptop.sync();

        message = "USB flashed with " + osName + ".";
        this.setSuccessful();
    }

    @Override
    public void prepareResponse(NBTTagCompound nbt)
    {
        nbt.setString("message", message);
    }

    @Override
    public void processResponse(NBTTagCompound nbt)
    {
    }
}