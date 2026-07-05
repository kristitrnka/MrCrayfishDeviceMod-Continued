package com.mrcrayfish.device.util;

import net.minecraftforge.fml.common.Loader;

public class MCEFUtil
{
        private static boolean checked = false;
        private static boolean installed = false;

        public static boolean isMCEFInstalled()
        {
                if(!checked)
                {
                        installed = Loader.isModLoaded("mcef");
                        checked = true;
                }
                return installed;
        }
}