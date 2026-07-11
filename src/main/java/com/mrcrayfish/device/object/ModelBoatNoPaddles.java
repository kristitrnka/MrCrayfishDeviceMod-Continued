package com.mrcrayfish.device.object;

import net.minecraft.client.model.ModelBoat;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;

public class ModelBoatNoPaddles extends ModelBoat
{
    @Override
    public void render(Entity entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale)
    {
        GlStateManager.rotate(90.0F, 0.0F, 1.0F, 0.0F);

        for(int i = 0; i < 5; i++)
        {
            this.boatSides[i].render(scale);
        }
    }
}