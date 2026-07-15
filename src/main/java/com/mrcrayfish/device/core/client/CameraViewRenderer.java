package com.mrcrayfish.device.core.client;

import com.mojang.authlib.GameProfile;
import com.mrcrayfish.device.block.BlockSecurityCamera;
import com.mrcrayfish.device.tileentity.TileEntitySecurityCamera;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

import java.util.UUID;

/**
 * Renders a second first-person world view into a texture for the laptop app.
 * All Minecraft and OpenGL state changed for the camera pass is restored before
 * returning to normal GUI rendering.
 */
@SideOnly(Side.CLIENT)
public class CameraViewRenderer
{
    private static final int FRAMEBUFFER_WIDTH = 320;
    private static final int FRAMEBUFFER_HEIGHT = 180;
    private static final GameProfile CAMERA_PROFILE =
            new GameProfile(new UUID(0L, 0L), "SecurityCamera");

    private Framebuffer framebuffer;
    private EntityOtherPlayerMP cameraEntity;
    private net.minecraft.world.World cameraWorld;
    private boolean hasSignal;

    public boolean update(BlockPos cameraPos, float partialTicks)
    {
        Minecraft mc = Minecraft.getMinecraft();
        if(!OpenGlHelper.isFramebufferEnabled() || mc.world == null || cameraPos == null
                || !mc.world.isBlockLoaded(cameraPos))
        {
            hasSignal = false;
            return false;
        }

        if(!(mc.world.getTileEntity(cameraPos) instanceof TileEntitySecurityCamera))
        {
            hasSignal = false;
            return false;
        }

        IBlockState state = mc.world.getBlockState(cameraPos);
        if(state.getBlock() != com.mrcrayfish.device.init.DeviceBlocks.SECURITY_CAMERA)
        {
            hasSignal = false;
            return false;
        }

        ensureFramebuffer();
        positionCamera(mc, cameraPos, state.getValue(BlockSecurityCamera.FACING));

        Entity oldViewEntity = mc.getRenderViewEntity();
        int oldDisplayWidth = mc.displayWidth;
        int oldDisplayHeight = mc.displayHeight;
        int oldThirdPersonView = mc.gameSettings.thirdPersonView;
        RayTraceResult oldMouseOver = mc.objectMouseOver;
        boolean oldRenderHand = ReflectionHelper.getPrivateValue(
                EntityRenderer.class, mc.entityRenderer, "renderHand", "field_175074_C");
        boolean scissorEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        try
        {
            if(scissorEnabled)
            {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
            }

            mc.displayWidth = FRAMEBUFFER_WIDTH;
            mc.displayHeight = FRAMEBUFFER_HEIGHT;
            mc.gameSettings.thirdPersonView = 0;
            mc.setRenderViewEntity(cameraEntity);
            ReflectionHelper.setPrivateValue(
                    EntityRenderer.class, mc.entityRenderer, false, "renderHand", "field_175074_C");

            framebuffer.bindFramebuffer(true);
            framebuffer.framebufferClear();
            framebuffer.bindFramebuffer(true);
            mc.entityRenderer.renderWorld(partialTicks, System.nanoTime() + 16000000L);
            hasSignal = true;
            return true;
        }
        catch(RuntimeException exception)
        {
            hasSignal = false;
            return false;
        }
        finally
        {
            mc.setRenderViewEntity(oldViewEntity);
            mc.gameSettings.thirdPersonView = oldThirdPersonView;
            mc.displayWidth = oldDisplayWidth;
            mc.displayHeight = oldDisplayHeight;
            mc.objectMouseOver = oldMouseOver;
            ReflectionHelper.setPrivateValue(
                    EntityRenderer.class, mc.entityRenderer, oldRenderHand, "renderHand", "field_175074_C");

            mc.getFramebuffer().bindFramebuffer(true);
            GlStateManager.viewport(0, 0, mc.getFramebuffer().framebufferWidth,
                    mc.getFramebuffer().framebufferHeight);
            mc.entityRenderer.setupOverlayRendering();
            if(scissorEnabled)
            {
                GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
        }
    }

    private void ensureFramebuffer()
    {
        if(framebuffer == null)
        {
            framebuffer = new Framebuffer(FRAMEBUFFER_WIDTH, FRAMEBUFFER_HEIGHT, true);
            framebuffer.setFramebufferColor(0.02F, 0.02F, 0.02F, 1.0F);
            framebuffer.setFramebufferFilter(GL11.GL_LINEAR);
        }
    }

    private void positionCamera(Minecraft mc, BlockPos pos, EnumFacing facing)
    {
        if(cameraEntity == null || cameraWorld != mc.world)
        {
            cameraWorld = mc.world;
            cameraEntity = new EntityOtherPlayerMP(mc.world, CAMERA_PROFILE);
            cameraEntity.noClip = true;
            cameraEntity.setInvisible(true);
        }

        double x = pos.getX() + 0.5 + facing.getFrontOffsetX() * 0.38;
        double eyeY = pos.getY() + 0.5;
        double y = eyeY - cameraEntity.getEyeHeight();
        double z = pos.getZ() + 0.5 + facing.getFrontOffsetZ() * 0.38;
        float yaw = facing.getHorizontalAngle();

        cameraEntity.setPositionAndRotation(x, y, z, yaw, 0.0F);
        cameraEntity.lastTickPosX = cameraEntity.prevPosX = cameraEntity.posX;
        cameraEntity.lastTickPosY = cameraEntity.prevPosY = cameraEntity.posY;
        cameraEntity.lastTickPosZ = cameraEntity.prevPosZ = cameraEntity.posZ;
        cameraEntity.prevRotationYaw = cameraEntity.rotationYaw;
        cameraEntity.prevRotationPitch = cameraEntity.rotationPitch;
    }

    public void draw(int x, int y, int width, int height)
    {
        if(!hasSignal || framebuffer == null)
        {
            return;
        }

        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        framebuffer.bindFramebufferTexture();

        float maxU = (float) framebuffer.framebufferWidth / framebuffer.framebufferTextureWidth;
        float maxV = (float) framebuffer.framebufferHeight / framebuffer.framebufferTextureHeight;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0).tex(0, 0).endVertex();
        buffer.pos(x + width, y + height, 0).tex(maxU, 0).endVertex();
        buffer.pos(x + width, y, 0).tex(maxU, maxV).endVertex();
        buffer.pos(x, y, 0).tex(0, maxV).endVertex();
        tessellator.draw();

        framebuffer.unbindFramebufferTexture();
        GlStateManager.depthMask(true);
    }

    public boolean hasSignal()
    {
        return hasSignal;
    }

    public void close()
    {
        if(framebuffer != null)
        {
            framebuffer.deleteFramebuffer();
            framebuffer = null;
        }
        cameraEntity = null;
        cameraWorld = null;
        hasSignal = false;
    }
}
