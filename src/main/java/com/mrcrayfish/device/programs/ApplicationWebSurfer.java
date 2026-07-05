package com.mrcrayfish.device.programs;

import com.mrcrayfish.device.api.app.Application;
import com.mrcrayfish.device.api.app.Layout;
import com.mrcrayfish.device.api.app.component.Label;
import com.mrcrayfish.device.util.MCEFUtil;
import net.minecraft.nbt.NBTTagCompound;

public class ApplicationWebSurfer extends Application
{
        @Override
        public void init(NBTTagCompound nbt)
        {
                Layout layout = new Layout(362, 164);

                if(!MCEFUtil.isMCEFInstalled())
                {
                        layout.addComponent(new Label("MCEF Required", 10, 10));
                        layout.addComponent(new Label("Install MCEF to use WebSurfer.", 10, 30));
                        layout.addComponent(new Label("Device Mod will still run without it.", 10, 45));
                        layout.addComponent(new Label("Chromium browser backend is disabled.", 10, 60));

                        setCurrentLayout(layout);
                        return;
                }

                layout.addComponent(new Label("WebSurfer", 10, 10));
                layout.addComponent(new Label("MCEF detected!", 10, 30));
                layout.addComponent(new Label("Chromium backend coming next.", 10, 45));

                setCurrentLayout(layout);
        }

        @Override
        public void load(NBTTagCompound nbt)
        {
        }

        @Override
        public void save(NBTTagCompound nbt)
        {
        }
}