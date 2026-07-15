package com.mrcrayfish.device.tileentity;

/**
 * Network identity and router connection state for a security camera.
 */
public class TileEntitySecurityCamera extends TileEntityNetworkDevice
{
    @Override
    public String getDeviceName()
    {
        return "Security Camera";
    }
}
