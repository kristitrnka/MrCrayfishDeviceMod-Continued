package com.mrcrayfish.device.programs.system;

import com.mrcrayfish.device.api.ApplicationManager;
import com.mrcrayfish.device.api.app.Dialog;
import com.mrcrayfish.device.api.app.Icons;
import com.mrcrayfish.device.api.app.Layout;
import com.mrcrayfish.device.api.app.component.Button;
import com.mrcrayfish.device.api.app.component.CheckBox;
import com.mrcrayfish.device.api.app.component.ComboBox;
import com.mrcrayfish.device.api.app.renderer.ItemRenderer;
import com.mrcrayfish.device.api.utils.RenderUtil;
import com.mrcrayfish.device.core.Laptop;
import com.mrcrayfish.device.core.Settings;
import com.mrcrayfish.device.object.AppInfo;
import com.mrcrayfish.device.object.TrayItem;
import com.mrcrayfish.device.programs.system.component.Palette;
import com.mrcrayfish.device.programs.system.object.ColorScheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.awt.*;
import java.util.List;
import java.util.Stack;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class ApplicationSettings extends SystemApplication
{
	private Button buttonPrevious;

	private Layout layoutMain;
	private Layout layoutGeneral;
	private CheckBox checkBoxShowApps;
        private CheckBox checkBoxShowBootMenu;

	private Layout layoutPersonalise;
	private Button buttonWallpaperLeft;
	private Button buttonWallpaperRight;
	private Button buttonWallpaperUrl;

	private Layout layoutColorScheme;
	private Button buttonColorSchemeApply;

	private Stack<Layout> predecessor = new Stack<>();

	@Override
	public void init(@Nullable NBTTagCompound intent)
	{
		buttonPrevious = new Button(2, 2, Icons.ARROW_LEFT);
		buttonPrevious.setVisible(false);
		buttonPrevious.setClickListener((mouseX, mouseY, mouseButton) ->
		{
			if(mouseButton == 0)
			{
				if(predecessor.size() > 0)
				{
					setCurrentLayout(predecessor.pop());
				}
				if(predecessor.isEmpty())
				{
					buttonPrevious.setVisible(false);
				}
			}
		});

		layoutMain = new Menu("Home");

		Button buttonColorScheme = new Button(5, 26, "Personalise", Icons.EDIT);
		buttonColorScheme.setSize(90, 20);
		buttonColorScheme.setToolTip("Personalise", "Change the wallpaper, UI colors, and more!");
		buttonColorScheme.setClickListener((mouseX, mouseY, mouseButton) ->
		{
			if(mouseButton == 0)
			{
				showMenu(layoutPersonalise);
			}
		});
		layoutMain.addComponent(buttonColorScheme);

                Button buttonGeneral = new Button(5, 52, "General", Icons.WRENCH);
                buttonGeneral.setSize(90, 20);
                buttonGeneral.setToolTip("General", "Change system behaviour and boot options");
                buttonGeneral.setClickListener((mouseX, mouseY, mouseButton) ->
                {
                        if(mouseButton == 0)
                        {
                                showMenu(layoutGeneral);
                        }
                });
                layoutMain.addComponent(buttonGeneral);

		layoutGeneral = new Menu("General");
		layoutGeneral.addComponent(buttonPrevious);

		checkBoxShowApps = new CheckBox("All Apps", 10, 18);
		checkBoxShowApps.setSelected(Settings.isShowAllApps());
		checkBoxShowApps.setClickListener((mouseX, mouseY, mouseButton) ->
		{
			Settings.setShowAllApps(checkBoxShowApps.isSelected());
			Laptop laptop = getLaptop();
			laptop.getTaskBar().setupApplications(laptop.getApplications());
		});
		layoutGeneral.addComponent(checkBoxShowApps);

                checkBoxShowBootMenu = new CheckBox("Boot Menu", 10, 42);
                checkBoxShowBootMenu.setSelected(Settings.isShowBootMenu());
                checkBoxShowBootMenu.setClickListener((mouseX, mouseY, mouseButton) ->
                {
                        Settings.setShowBootMenu(checkBoxShowBootMenu.isSelected());
                });
                layoutGeneral.addComponent(checkBoxShowBootMenu);

		layoutPersonalise = new Menu("Personalise");
		layoutPersonalise.addComponent(buttonPrevious);
		layoutPersonalise.setBackground((gui, mc, x, y, width, height, mouseX, mouseY, windowActive) ->
		{
			int wallpaperX = 7;
			int wallpaperY = 28;
			Gui.drawRect(x + wallpaperX - 1, y + wallpaperY - 1, x + wallpaperX - 1 + 122, y + wallpaperY - 1 + 70, getLaptop().getSettings().getColorScheme().getHeaderColor());
			GlStateManager.color(1.0F, 1.0F, 1.0F);
			List<ResourceLocation> wallpapers = getLaptop().getWallapapers();
			mc.getTextureManager().bindTexture(wallpapers.get(getLaptop().getCurrentWallpaper()));
			RenderUtil.drawRectWithFullTexture(x + wallpaperX, y + wallpaperY, 0, 0, 120, 68);
			mc.fontRenderer.drawString("Wallpaper", x + wallpaperX + 3, y + wallpaperY + 3, getLaptop().getSettings().getColorScheme().getTextColor(), true);
		});

		buttonWallpaperLeft = new Button(135, 27, Icons.ARROW_LEFT);
		buttonWallpaperLeft.setSize(25, 20);
		buttonWallpaperLeft.setClickListener((mouseX, mouseY, mouseButton) ->
		{
			if(mouseButton != 0)
				return;

			Laptop laptop = getLaptop();
			if(laptop != null)
			{
				laptop.prevWallpaper();
			}
        });
		layoutPersonalise.addComponent(buttonWallpaperLeft);

		buttonWallpaperRight = new Button(165, 27, Icons.ARROW_RIGHT);
		buttonWallpaperRight.setSize(25, 20);
		buttonWallpaperRight.setClickListener((mouseX, mouseY, mouseButton) ->
		{
			if(mouseButton != 0)
				return;

			Laptop laptop = getLaptop();
			if(laptop != null)
			{
				laptop.nextWallpaper();
			}
		});
		layoutPersonalise.addComponent(buttonWallpaperRight);
                buttonWallpaperUrl = new Button(135, 52, "Load", Icons.EARTH);
                buttonWallpaperUrl.setSize(55, 20);
                buttonWallpaperUrl.setClickListener((mouseX, mouseY, mouseButton) ->
                {
                        if(mouseButton != 0)
                                return;

                        Dialog.Input dialog = new Dialog.Input("Enter image URL");
                        dialog.setInputText("https://i.imgur.com/");
                        dialog.setResponseHandler((success, input) ->
                        {
                                if(!success)
                                {
                                        return true;
                                }

                                final String urlText = input.trim();
                                if(urlText.isEmpty() || !(urlText.startsWith("http://") || urlText.startsWith("https://")))
                                {
                                        openDialog(new Dialog.Message("Invalid URL. Use http or https direct image link."));
                                        return false;
                                }

                                new Thread(() ->
                                {
                                        try
                                        {
                                                BufferedImage image = null;

                                                String[] urlsToTry;
                                                if(urlText.contains("imgur.com/") && !urlText.contains("i.imgur.com/"))
                                                {
                                                        String id = urlText.substring(urlText.lastIndexOf("/") + 1);
                                                        int q = id.indexOf("?");
                                                        if(q != -1) id = id.substring(0, q);
                                                        urlsToTry = new String[] {
                                                                "https://i.imgur.com/" + id + ".png",
                                                                "https://i.imgur.com/" + id + ".jpg",
                                                                "https://i.imgur.com/" + id + ".jpeg",
                                                                urlText
                                                        };
                                                }
                                                else if(urlText.contains("i.imgur.com/") && !(urlText.endsWith(".png") || urlText.endsWith(".jpg") || urlText.endsWith(".jpeg")))
                                                {
                                                        urlsToTry = new String[] {
                                                                urlText + ".png",
                                                                urlText + ".jpg",
                                                                urlText + ".jpeg",
                                                                urlText
                                                        };
                                                }
                                                else
                                                {
                                                        urlsToTry = new String[] { urlText };
                                                }

                                                for(String candidate : urlsToTry)
                                                {
                                                        try
                                                        {
                                                                URL url = new URL(candidate);
                                                                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                                                                conn.setConnectTimeout(5000);
                                                                conn.setReadTimeout(10000);
                                                                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                                                                conn.setInstanceFollowRedirects(true);
                                                                conn.setRequestProperty("Accept", "image/png,image/jpeg,image/*,*/*");
                                                                conn.setRequestProperty("Referer", "https://imgur.com/");

                                                                int code = conn.getResponseCode();
                                                                if(code >= 200 && code < 300)
                                                                {
                                                                        InputStream in = conn.getInputStream();
                                                                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                                                                        byte[] buffer = new byte[8192];
                                                                        int read;
                                                                        while((read = in.read(buffer)) != -1)
                                                                        {
                                                                                out.write(buffer, 0, read);
                                                                        }
                                                                        in.close();

                                                                        byte[] bytes = out.toByteArray();
                                                                        image = ImageIO.read(new ByteArrayInputStream(bytes));

                                                                        if(image != null)
                                                                        {
                                                                                break;
                                                                        }
                                                                        else
                                                                        {
                                                                                System.out.println("[CDM Wallpaper] ImageIO returned null for " + candidate + " content-type=" + conn.getContentType() + " bytes=" + bytes.length);
                                                                        }
                                                                }
                                                                else
                                                                {
                                                                        System.out.println("[CDM Wallpaper] HTTP " + code + " for " + candidate);
                                                                }
                                                        }
                                                        catch(Exception ignored) {}
                                                }
                                                if(image == null)
                                                {
                                                        Minecraft.getMinecraft().addScheduledTask(() ->
                                                                openDialog(new Dialog.Message("Could not load image. Use a direct PNG/JPG image URL."))
                                                        );
                                                        return;
                                                }

                                                final BufferedImage finalImage = image;

                                                try
                                                {
                                                        java.io.File dir = new java.io.File(Minecraft.getMinecraft().mcDataDir, "config/cdm");
                                                        if(!dir.exists())
                                                        {
                                                                dir.mkdirs();
                                                        }

                                                        javax.imageio.ImageIO.write(finalImage, "png", new java.io.File(dir, "web_wallpaper.png"));
                                                }
                                                catch(Exception e)
                                                {
                                                        e.printStackTrace();
                                                }

                                                Minecraft.getMinecraft().addScheduledTask(() ->
                                                {
                                                        ResourceLocation location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(
                                                                "web_wallpaper",
                                                                new net.minecraft.client.renderer.texture.DynamicTexture(finalImage)
                                                        );

                                                        Laptop.addWallpaper(location);

                                                        Laptop laptop = getLaptop();
                                                        if(laptop != null)
                                                        {
                                                                int target = laptop.getWallapapers().size() - 1;
                                                                while(laptop.getCurrentWallpaper() < target)
                                                                {
                                                                        laptop.nextWallpaper();
                                                                }
                                                        }
                                                });
                                        }
                                        catch(Exception e)
                                        {
                                                e.printStackTrace();
                                                Minecraft.getMinecraft().addScheduledTask(() ->
                                                        openDialog(new Dialog.Message("Failed to load wallpaper from web."))
                                                );
                                        }
                                }, "Wallpaper Loader").start();

                                return true;
                        });
                        openDialog(dialog);
                });
                layoutPersonalise.addComponent(buttonWallpaperUrl);

		Button buttonReset = new Button(6, 100, "Reset Color Scheme");
		buttonReset.setClickListener((mouseX, mouseY, mouseButton) ->
		{
            if(mouseButton == 0)
			{
				Laptop.getSystem().getSettings().getColorScheme().resetDefault();
			}
        });
		layoutPersonalise.addComponent(buttonReset);

		layoutColorScheme = new Menu("UI Colors");
		layoutPersonalise.addComponent(buttonPrevious);

		ComboBox.Custom<Integer> comboBoxTextColor = createColorPicker(145, 26);
		layoutColorScheme.addComponent(comboBoxTextColor);

		ComboBox.Custom<Integer> comboBoxTextSecondaryColor = createColorPicker(145, 44);
		layoutColorScheme.addComponent(comboBoxTextSecondaryColor);

		ComboBox.Custom<Integer> comboBoxHeaderColor = createColorPicker(145, 62);
		layoutColorScheme.addComponent(comboBoxHeaderColor);

		ComboBox.Custom<Integer> comboBoxBackgroundColor = createColorPicker(145, 80);
		layoutColorScheme.addComponent(comboBoxBackgroundColor);

		ComboBox.Custom<Integer> comboBoxBackgroundSecondaryColor = createColorPicker(145, 98);
		layoutColorScheme.addComponent(comboBoxBackgroundSecondaryColor);

		ComboBox.Custom<Integer> comboBoxItemBackgroundColor = createColorPicker(145, 116);
		layoutColorScheme.addComponent(comboBoxItemBackgroundColor);

		ComboBox.Custom<Integer> comboBoxItemHighlightColor = createColorPicker(145, 134);
		layoutColorScheme.addComponent(comboBoxItemHighlightColor);

		buttonColorSchemeApply = new Button(5, 79, Icons.CHECK);
		buttonColorSchemeApply.setEnabled(false);
		buttonColorSchemeApply.setToolTip("Apply", "Set these colors as the new color scheme");
		buttonColorSchemeApply.setClickListener((mouseX, mouseY, mouseButton) ->
		{
			if(mouseButton == 0)
			{
				ColorScheme colorScheme = Laptop.getSystem().getSettings().getColorScheme();
				colorScheme.setBackgroundColor(comboBoxHeaderColor.getValue());
				buttonColorSchemeApply.setEnabled(false);
			}
		});
		layoutColorScheme.addComponent(buttonColorSchemeApply);

		setCurrentLayout(layoutMain);
	}

	@Override
	public void load(NBTTagCompound tagCompound)
	{

	}

	@Override
	public void save(NBTTagCompound tagCompound)
	{

	}

	private void showMenu(Layout layout)
	{
		predecessor.push(getCurrentLayout());
		buttonPrevious.setVisible(true);
		setCurrentLayout(layout);
	}

	@Override
	public void onClose()
	{
		super.onClose();
		predecessor.clear();
	}

	private static class Menu extends Layout
	{
		private String title;

		public Menu(String title)
		{
			super(200, 150);
			this.title = title;
		}

		@Override
		public void render(Laptop laptop, Minecraft mc, int x, int y, int mouseX, int mouseY, boolean windowActive, float partialTicks)
		{
			Color color = new Color(Laptop.getSystem().getSettings().getColorScheme().getHeaderColor());
			Gui.drawRect(x, y, x + width, y + 20, color.getRGB());
			Gui.drawRect(x, y + 20, x + width, y + 21, color.darker().getRGB());
			mc.fontRenderer.drawString(title, x + 22, y + 6, Color.WHITE.getRGB(), true);
			super.render(laptop, mc, x, y, mouseX, mouseY, windowActive, partialTicks);
		}
	}

	public ComboBox.Custom<Integer> createColorPicker(int left, int top)
	{
		ComboBox.Custom<Integer> colorPicker = new ComboBox.Custom<>(left, top, 50, 100, 100);
		colorPicker.setValue(Color.RED.getRGB());
		colorPicker.setItemRenderer(new ItemRenderer<Integer>()
		{
			@Override
			public void render(Integer integer, Gui gui, Minecraft mc, int x, int y, int width, int height)
			{
				if(integer != null)
				{
					Gui.drawRect(x, y, x + width, y + height, integer);
				}
			}
		});
		colorPicker.setChangeListener((oldValue, newValue) ->
		{
			buttonColorSchemeApply.setEnabled(true);
		});

		Palette palette = new Palette(5, 5, colorPicker);
		Layout layout = colorPicker.getLayout();
		layout.addComponent(palette);

		return colorPicker;
	}

	public static class SettingsTrayItem extends TrayItem
	{
		public SettingsTrayItem()
		{
			super(Icons.WRENCH);
		}

		@Override
		public void handleClick(int mouseX, int mouseY, int mouseButton)
		{
			AppInfo info = ApplicationManager.getApplication("cdm:settings");
			if(info != null)
			{
				Laptop.getSystem().openApplication(info);
			}
		}
	}
}
