package com.mrcrayfish.device.programs;

import com.mrcrayfish.device.api.app.Application;
import com.mrcrayfish.device.api.app.Layout;
import com.mrcrayfish.device.api.app.component.Button;
import com.mrcrayfish.device.api.utils.OnlineRequest;
import com.mrcrayfish.device.api.utils.RenderUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import paulscode.sound.SoundSystem;
import paulscode.sound.SoundSystemConfig;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class ApplicationGitTube extends Application
{
    private static final String DEFAULT_BASE_URL = "https://raw.githubusercontent.com/kristitrnka/GitTube/main";
    private static final int MAX_VISIBLE_VIDEOS = 6;
    private static final long MAX_GIF_FILE_SIZE = 100L * 1024L * 1024L;
    private static final int MAX_GIF_WIDTH = 4096;
    private static final int MAX_GIF_HEIGHT = 4096;
    private static final int MAX_GIF_FRAMES = 5000;
    private static final long MAX_AUDIO_FILE_SIZE = 25L * 1024L * 1024L;

    // GIFs can be huge, but the DeviceOS player is tiny.
    // Decode source GIF, then store frames downscaled to this size.
    private static final int GIF_RENDER_WIDTH = 266;
    private static final int GIF_RENDER_HEIGHT = 96;

    private Layout layoutMain;

    private Button btnReload;
    private Button btnHome;
    private Button btnChannels;
    private Button btnTrending;

    private Button btnBack;
    private Button btnPlayPause;
    private Button btnRestart;

    private Button[] videoButtons = new Button[MAX_VISIBLE_VIDEOS];
    private Button[] channelButtons = new Button[4];

    private String baseUrl = DEFAULT_BASE_URL;
    private String statusText = "Loading GitTube...";
    private String searchText = "";
    private boolean searchFocused = false;
    private int homeShuffleSeed = 1;

    private boolean watchPage = false;
    private int selectedVideo = -1;
    private int pageMode = PAGE_HOME;
    private String selectedChannelId = "";
    private String selectedChannelName = "";

    private boolean playing = false;
    private boolean loadingGif = false;
    private boolean loadingAudio = false;
    private boolean playbackStarted = false;

    private GifData currentGif;
    private int currentFrame = 0;
    private long lastFrameTime = 0L;

    private DynamicTexture gifTexture;
    private ResourceLocation gifTextureLocation;
    private File audioFile;
    private String audioSourceName;
    private boolean audioSourceCreated = false;

    private final ArrayList<VideoEntry> videos = new ArrayList<>();

    private static final int PAGE_HOME = 0;
    private static final int PAGE_CHANNELS = 1;
    private static final int PAGE_TRENDING = 2;
    private static final int PAGE_CHANNEL_PROFILE = 3;

    @Override
    public void init(@Nullable NBTTagCompound intent)
    {
        layoutMain = new Layout(362, 164);
        layoutMain.setBackground((gui, mc, x, y, width, height, mouseX, mouseY, windowActive) ->
        {
            drawGitTubeUI(mc, x, y);
        });

        btnReload = new GitTubeButton(302, 6, "Reload");
        btnReload.setSize(54, 14);
        btnReload.setToolTip("Reload", "Reloads videos from the GitTube repo.");
        btnReload.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0)
            {
                loadCatalog();
            }
        });
        layoutMain.addComponent(btnReload);

        btnHome = new GitTubeButton(5, 32, "Home");
        btnHome.setSize(62, 14);
        btnHome.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0)
            {
                watchPage = false;
                pageMode = PAGE_HOME;
                selectedVideo = -1;
                selectedChannelId = "";
                selectedChannelName = "";
                playing = false;
                playbackStarted = false;
                stopAudio();
                statusText = "Home";
                syncButtons();
            }
        });
        layoutMain.addComponent(btnHome);

        btnChannels = new GitTubeButton(5, 50, "Channels");
        btnChannels.setSize(62, 14);
        btnChannels.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0)
            {
                pageMode = PAGE_CHANNELS;
                selectedChannelId = "";
                selectedChannelName = "";
                statusText = "Channels";
                syncButtons();
            }
        });
        layoutMain.addComponent(btnChannels);

        btnTrending = new GitTubeButton(5, 68, "Trending");
        btnTrending.setSize(62, 14);
        btnTrending.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0)
            {
                pageMode = PAGE_TRENDING;
                selectedChannelId = "";
                selectedChannelName = "";
                statusText = "Trending";
                syncButtons();
            }
        });
        layoutMain.addComponent(btnTrending);

        btnBack = new GitTubeButton(5, 32, "Back");
        btnBack.setSize(62, 14);
        btnBack.setVisible(false);
        btnBack.setEnabled(false);
        btnBack.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0)
            {
                watchPage = false;
                playing = false;
                playbackStarted = false;
                stopAudio();
                statusText = "Back to videos.";
                syncButtons();
            }
        });
        layoutMain.addComponent(btnBack);

        btnPlayPause = new GitTubeButton(82, 145, "Play/Pause");
        btnPlayPause.setSize(78, 14);
        btnPlayPause.setVisible(false);
        btnPlayPause.setEnabled(false);
        btnPlayPause.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0 && currentGif != null && currentGif.frames.size() > 0)
            {
                playing = !playing;
                lastFrameTime = System.currentTimeMillis();
                if(playing)
                {
                    playAudio(false);
                }
                else
                {
                    pauseAudio();
                }
                statusText = playing ? "Playing." : "Paused.";
            }
        });
        layoutMain.addComponent(btnPlayPause);

        btnRestart = new GitTubeButton(166, 145, "Restart");
        btnRestart.setSize(58, 14);
        btnRestart.setVisible(false);
        btnRestart.setEnabled(false);
        btnRestart.setClickListener((mouseX, mouseY, mouseButton) ->
        {
            if(mouseButton == 0 && currentGif != null && currentGif.frames.size() > 0)
            {
                currentFrame = 0;
                lastFrameTime = System.currentTimeMillis();
                playing = true;
                uploadGifFrame(Minecraft.getMinecraft());
                playAudio(true);
                statusText = "Restarted.";
            }
        });
        layoutMain.addComponent(btnRestart);

        for(int i = 0; i < MAX_VISIBLE_VIDEOS; i++)
        {
            int col = i % 2;
            int row = i / 2;

            int cardX = 82 + col * 137;
            int cardY = 54 + row * 32;

            final int index = i;

            Button open = new GitTubeButton(cardX + 96, cardY + 14, "Open");
            open.setSize(32, 12);
            open.setVisible(false);
            open.setEnabled(false);
            open.setClickListener((mouseX, mouseY, mouseButton) ->
            {
                if(mouseButton == 0 && index < getDisplayedVideos().size())
                {
                    openVideo(getDisplayedVideos().get(index));
                }
            });

            videoButtons[i] = open;
            layoutMain.addComponent(open);
        }

        for(int i = 0; i < channelButtons.length; i++)
        {
            final int index = i;

            Button openChannel = new GitTubeHitboxButton(82, 58 + i * 24);
            openChannel.setSize(260, 20);
            openChannel.setVisible(false);
            openChannel.setEnabled(false);
            openChannel.setClickListener((mouseX, mouseY, mouseButton) ->
            {
                ArrayList<ChannelEntry> channels = getDisplayedChannels();

                if(mouseButton == 0 && index < channels.size())
                {
                    openChannel(channels.get(index));
                }
            });

            channelButtons[i] = openChannel;
            layoutMain.addComponent(openChannel);
        }

        setCurrentLayout(layoutMain);
        syncButtons();
        loadCatalog();
    }

    @Override
    public void handleMouseClick(int mouseX, int mouseY, int mouseButton)
    {
        searchFocused = false;

        if(!watchPage && mouseButton == 0 && layoutMain != null)
        {
            int searchX = layoutMain.xPosition + 87;
            int searchY = layoutMain.yPosition + 7;

            searchFocused = mouseX >= searchX && mouseY >= searchY && mouseX < searchX + 208 && mouseY < searchY + 12;
        }

        super.handleMouseClick(mouseX, mouseY, mouseButton);
    }

    @Override
    public void handleKeyTyped(char character, int code)
    {
        if(searchFocused && !watchPage)
        {
            if(code == Keyboard.KEY_ESCAPE)
            {
                searchText = "";
                searchFocused = false;
                statusText = "Search cleared.";
                syncButtons();
                return;
            }
            else if(code == Keyboard.KEY_BACK)
            {
                if(searchText.length() > 0)
                {
                    searchText = searchText.substring(0, searchText.length() - 1);
                    statusText = getSearchStatus();
                    syncButtons();
                }

                return;
            }
            else if(character >= 32 && character < 127 && searchText.length() < 32)
            {
                searchText += character;
                statusText = getSearchStatus();
                syncButtons();
                return;
            }
        }

        super.handleKeyTyped(character, code);
    }

    private void openVideo(int index)
    {
        if(index < 0 || index >= videos.size())
        {
            return;
        }

        openVideo(videos.get(index));
    }

    private void openVideo(VideoEntry video)
    {
        int index = videos.indexOf(video);

        if(index < 0)
        {
            return;
        }

        selectedVideo = index;
        watchPage = true;
        playing = false;
        stopAudio();
        loadingGif = false;
        loadingAudio = false;
        playbackStarted = false;
        currentGif = null;
        currentFrame = 0;
        gifTexture = null;
        gifTextureLocation = null;

        statusText = "Opening " + video.title + "...";

        syncButtons();
        startGifLoad(video);
    }

    private void openChannel(ChannelEntry channel)
    {
        watchPage = false;
        pageMode = PAGE_CHANNEL_PROFILE;
        selectedChannelId = channel.channelId;
        selectedChannelName = channel.channelName;
        statusText = channel.channelName + " | " + channel.videoCount + " video(s)";
        syncButtons();
    }

    private void startGifLoad(VideoEntry video)
    {
        if(video.gif == null || video.gif.trim().isEmpty())
        {
            statusText = "This video has no GIF set in video.txt";
            return;
        }

        loadingGif = true;
        statusText = "Downloading GIF...";

        new Thread(() ->
        {
            try
            {
                File file = downloadToCache(video.gif, video.channelId + "_" + video.videoId + ".gif");
                loadAudio(video);
                statusText = "Decoding GIF...";
                GifData data = decodeGif(file);

                currentGif = data;
                currentFrame = 0;
                lastFrameTime = System.currentTimeMillis();
                playing = false;
                playbackStarted = false;
                loadingGif = false;

                statusText = "Ready " + video.title + " | " + data.frames.size() + " frames";
            }
            catch(Exception e)
            {
                e.printStackTrace();
                loadingGif = false;
                playing = false;
                currentGif = null;
                stopAudio();
                statusText = "GIF failed: " + e.getMessage();
            }
        }, "GitTube GIF Loader").start();
    }

    private File downloadToCache(String url, String fileName) throws Exception
    {
        return downloadToCache(url, fileName, MAX_GIF_FILE_SIZE, "GIF");
    }

    private File downloadToCache(String url, String fileName, long maxFileSize, String label) throws Exception
    {
        File cacheDir = new File(Minecraft.getMinecraft().mcDataDir, "config/cdm/gittube/cache");
        if(!cacheDir.exists())
        {
            cacheDir.mkdirs();
        }

        String safeName = fileName.replaceAll("[^A-Za-z0-9._-]", "_");
        File outFile = new File(cacheDir, safeName);

        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(20000);

        long length = connection.getContentLengthLong();

        if(length > maxFileSize)
        {
            throw new Exception(label + " too large: " + (length / 1024L / 1024L) + " MB");
        }

        long total = 0L;

        try(InputStream in = connection.getInputStream(); FileOutputStream out = new FileOutputStream(outFile))
        {
            byte[] buffer = new byte[8192];
            int read;

            while((read = in.read(buffer)) != -1)
            {
                total += read;

                if(total > maxFileSize)
                {
                    throw new Exception(label + " too large while downloading");
                }

                out.write(buffer, 0, read);
            }
        }

        return outFile;
    }

    private void loadAudio(VideoEntry video)
    {
        stopAudio();

        if(video.sound == null || video.sound.trim().isEmpty())
        {
            return;
        }

        String audioUrl = video.sound;
        String extension = getExtension(audioUrl);
        if(!".ogg".equalsIgnoreCase(extension) && !".wav".equalsIgnoreCase(extension))
        {
            if(".mp3".equalsIgnoreCase(extension))
            {
                audioUrl = replaceExtension(audioUrl, ".ogg");
                extension = ".ogg";
            }
            else
            {
                statusText = "Audio format not supported: use .ogg or .wav";
                return;
            }
        }

        loadingAudio = true;

        try
        {
            statusText = "Downloading audio...";
            audioFile = downloadToCache(audioUrl, video.channelId + "_" + video.videoId + "_audio" + extension, MAX_AUDIO_FILE_SIZE, "Audio");
            audioSourceName = "gittube_" + video.channelId + "_" + video.videoId + "_" + System.nanoTime();
            audioSourceCreated = false;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            audioFile = null;
            audioSourceName = null;
            audioSourceCreated = false;
            statusText = "Audio failed: " + e.getMessage();
        }

        loadingAudio = false;
    }

    private String getExtension(String url)
    {
        try
        {
            String path = new URL(url).getPath();
            int slash = path.lastIndexOf('/');
            int dot = path.lastIndexOf('.');

            if(dot > slash && dot >= 0)
            {
                return path.substring(dot);
            }
        }
        catch(Exception ignored)
        {
        }

        return ".audio";
    }

    private String replaceExtension(String url, String extension)
    {
        int query = url.indexOf('?');
        String suffix = "";

        if(query != -1)
        {
            suffix = url.substring(query);
            url = url.substring(0, query);
        }

        int slash = url.lastIndexOf('/');
        int dot = url.lastIndexOf('.');

        if(dot > slash)
        {
            return url.substring(0, dot) + extension + suffix;
        }

        return url + extension + suffix;
    }

    private void playAudio(boolean restart)
    {
        if(audioFile == null)
        {
            return;
        }

        SoundSystem soundSystem = getMinecraftSoundSystem();
        if(soundSystem == null)
        {
            statusText = "Audio failed: sound system unavailable";
            return;
        }

        try
        {
            if(audioSourceName == null)
            {
                audioSourceName = "gittube_audio_" + System.nanoTime();
            }

            if(restart || !audioSourceCreated)
            {
                if(audioSourceCreated)
                {
                    soundSystem.stop(audioSourceName);
                    soundSystem.removeSource(audioSourceName);
                }

                soundSystem.newStreamingSource(false, audioSourceName, audioFile.toURI().toURL(), audioFile.getName(), false, 0.0F, 0.0F, 0.0F, SoundSystemConfig.ATTENUATION_NONE, 0.0F);
                audioSourceCreated = true;
            }

            soundSystem.setVolume(audioSourceName, getAudioVolume());
            soundSystem.play(audioSourceName);
        }
        catch(Exception e)
        {
            e.printStackTrace();
            statusText = "Audio failed: " + e.getMessage();
        }
    }

    private void pauseAudio()
    {
        SoundSystem soundSystem = getMinecraftSoundSystem();
        if(soundSystem != null && audioSourceName != null && audioSourceCreated)
        {
            soundSystem.pause(audioSourceName);
        }
    }

    private void stopAudio()
    {
        SoundSystem soundSystem = getMinecraftSoundSystem();
        if(soundSystem != null && audioSourceName != null && audioSourceCreated)
        {
            soundSystem.stop(audioSourceName);
            soundSystem.removeSource(audioSourceName);
        }

        audioFile = null;
        audioSourceName = null;
        audioSourceCreated = false;
    }

    private float getAudioVolume()
    {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.gameSettings.getSoundLevel(SoundCategory.MASTER) * mc.gameSettings.getSoundLevel(SoundCategory.RECORDS);
    }

    private SoundSystem getMinecraftSoundSystem()
    {
        try
        {
            SoundHandler soundHandler = Minecraft.getMinecraft().getSoundHandler();
            SoundManager soundManager = ReflectionHelper.getPrivateValue(SoundHandler.class, soundHandler, "sndManager", "field_147694_f");
            return ReflectionHelper.getPrivateValue(SoundManager.class, soundManager, "sndSystem", "field_148620_e");
        }
        catch(Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    private GifData decodeGif(File file) throws Exception
    {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");

        if(!readers.hasNext())
        {
            throw new Exception("No GIF reader found");
        }

        ImageReader reader = readers.next();

        try(ImageInputStream stream = ImageIO.createImageInputStream(file))
        {
            reader.setInput(stream, false);

            int[] size = getGifSize(reader);
            int width = size[0];
            int height = size[1];

            if(width <= 0 || height <= 0)
            {
                BufferedImage first = reader.read(0);
                width = first.getWidth();
                height = first.getHeight();
            }

            if(width > MAX_GIF_WIDTH || height > MAX_GIF_HEIGHT)
            {
                throw new Exception("Source GIF too large: " + width + "x" + height);
            }

            int frames = reader.getNumImages(true);

            if(frames > MAX_GIF_FRAMES)
            {
                throw new Exception("Too many frames: " + frames);
            }

            double outputScale = Math.min((double) GIF_RENDER_WIDTH / (double) width, (double) GIF_RENDER_HEIGHT / (double) height);

            GifData data = new GifData();
            data.width = Math.max(1, (int) Math.round(width * outputScale));
            data.height = Math.max(1, (int) Math.round(height * outputScale));

            BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = canvas.createGraphics();

            String previousDisposal = "none";
            int previousX = 0;
            int previousY = 0;
            int previousW = 0;
            int previousH = 0;

            for(int i = 0; i < frames; i++)
            {
                if("restoreToBackgroundColor".equals(previousDisposal))
                {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(previousX, previousY, previousW, previousH);
                    clear.dispose();
                }

                IIOMetadata metadata = reader.getImageMetadata(i);
                Node root = metadata.getAsTree("javax_imageio_gif_image_1.0");

                Node gce = getNode(root, "GraphicControlExtension");
                Node imageDescriptor = getNode(root, "ImageDescriptor");

                int delay = 80;
                String disposal = "none";

                if(gce != null)
                {
                    NamedNodeMap attrs = gce.getAttributes();

                    Node delayNode = attrs.getNamedItem("delayTime");
                    if(delayNode != null)
                    {
                        delay = parseInt(delayNode.getNodeValue(), 8) * 10;
                    }

                    Node disposalNode = attrs.getNamedItem("disposalMethod");
                    if(disposalNode != null)
                    {
                        disposal = disposalNode.getNodeValue();
                    }
                }

                int frameX = 0;
                int frameY = 0;
                int frameW;
                int frameH;

                BufferedImage frame = reader.read(i);
                frameW = frame.getWidth();
                frameH = frame.getHeight();

                if(imageDescriptor != null)
                {
                    NamedNodeMap attrs = imageDescriptor.getAttributes();
                    frameX = parseInt(getAttr(attrs, "imageLeftPosition"), 0);
                    frameY = parseInt(getAttr(attrs, "imageTopPosition"), 0);
                    frameW = parseInt(getAttr(attrs, "imageWidth"), frameW);
                    frameH = parseInt(getAttr(attrs, "imageHeight"), frameH);
                }

                graphics.drawImage(frame, frameX, frameY, null);

                BufferedImage copy = new BufferedImage(data.width, data.height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D copyGraphics = copy.createGraphics();
                copyGraphics.drawImage(canvas, 0, 0, data.width, data.height, null);
                copyGraphics.dispose();

                data.frames.add(copy);
                data.delays.add(Math.max(20, delay));

                previousDisposal = disposal;
                previousX = frameX;
                previousY = frameY;
                previousW = frameW;
                previousH = frameH;
            }

            graphics.dispose();
            reader.dispose();

            if(data.frames.isEmpty())
            {
                throw new Exception("GIF has no frames");
            }

            return data;
        }
    }

    private int[] getGifSize(ImageReader reader)
    {
        try
        {
            IIOMetadata metadata = reader.getStreamMetadata();

            if(metadata == null)
            {
                return new int[] { 0, 0 };
            }

            Node root = metadata.getAsTree("javax_imageio_gif_stream_1.0");
            Node screen = getNode(root, "LogicalScreenDescriptor");

            if(screen == null)
            {
                return new int[] { 0, 0 };
            }

            NamedNodeMap attrs = screen.getAttributes();

            int width = parseInt(getAttr(attrs, "logicalScreenWidth"), 0);
            int height = parseInt(getAttr(attrs, "logicalScreenHeight"), 0);

            return new int[] { width, height };
        }
        catch(Exception e)
        {
            return new int[] { 0, 0 };
        }
    }

    private void loadCatalog()
    {
        videos.clear();
        selectedVideo = -1;
        watchPage = false;
        pageMode = PAGE_HOME;
        playing = false;
        loadingGif = false;
        currentGif = null;
        gifTexture = null;
        gifTextureLocation = null;
        homeShuffleSeed = (int) (System.currentTimeMillis() ^ System.nanoTime());

        statusText = "Loading GitTube repo...";
        syncButtons();

        loadUrl(DEFAULT_BASE_URL + "/gittube.txt", "gittube.txt", rootRaw ->
        {
            Map<String, String> root = parseKeyValues(rootRaw);

            if(root.containsKey("base") && !root.get("base").trim().isEmpty())
            {
                baseUrl = root.get("base").trim();
            }
            else
            {
                baseUrl = DEFAULT_BASE_URL;
            }

            loadUrl(baseUrl + "/channels.txt", "channels.txt", channelsRaw ->
            {
                ArrayList<String> channels = parseEntries(channelsRaw, "channel");

                if(channels.isEmpty())
                {
                    statusText = "No channels found in channels.txt";
                    syncButtons();
                    return;
                }

                statusText = "Loading channels...";
                loadChannelList(channels, 0);
            });
        });
    }

    private void loadChannelList(ArrayList<String> channels, int index)
    {
        if(index >= channels.size())
        {
            statusText = "Loaded " + videos.size() + " video(s) from repo.";
            syncButtons();
            return;
        }

        String channelId = channels.get(index);
        String channelPath = baseUrl + "/channels/" + channelId;

        loadUrl(channelPath + "/channel.txt", "channel.txt", channelRaw ->
        {
            Map<String, String> channel = parseKeyValues(channelRaw);
            String channelName = get(channel, "name", channelId);
            String channelDescription = get(channel, "description", "GitTube channel");
            String channelSubscribers = get(channel, "subscribers", "0");

            loadUrl(channelPath + "/videos.txt", "videos.txt", videosRaw ->
            {
                ArrayList<String> videoIds = parseEntries(videosRaw, "video");

                loadVideoList(channelId, channelName, channelDescription, channelSubscribers, videoIds, 0, () ->
                {
                    loadChannelList(channels, index + 1);
                });
            });
        });
    }

    private void loadVideoList(String channelId, String channelName, String channelDescription, String channelSubscribers, ArrayList<String> videoIds, int index, DoneCallback done)
    {
        if(index >= videoIds.size())
        {
            done.run();
            return;
        }

        String videoId = videoIds.get(index);
        String videoPath = baseUrl + "/channels/" + channelId + "/videos/" + videoId;

        loadUrl(videoPath + "/video.txt", "video.txt", videoRaw ->
        {
            Map<String, String> videoData = parseKeyValues(videoRaw);

            if(!videoData.isEmpty())
            {
                VideoEntry entry = new VideoEntry();
                entry.channelId = channelId;
                entry.channelName = channelName;
                entry.channelDescription = channelDescription;
                entry.channelSubscribers = channelSubscribers;
                entry.videoId = videoId;
                entry.title = get(videoData, "title", videoId);
                entry.author = get(videoData, "author", channelName);
                entry.duration = get(videoData, "duration", "?:??");
                entry.views = get(videoData, "views", "0");
                entry.likes = get(videoData, "likes", "0");
                entry.created = get(videoData, "created", "");
                entry.description = get(videoData, "description", "");
                entry.thumbnail = resolveVideoFile(channelId, videoId, get(videoData, "thumbnail", ""));
                entry.gif = resolveVideoFile(channelId, videoId, get(videoData, "gif", ""));
                entry.sound = resolveVideoFile(channelId, videoId, get(videoData, "sound", ""));

                videos.add(entry);
                loadThumbnail(entry);

                statusText = "Loaded " + videos.size() + " video(s)...";
                syncButtons();
            }

            loadVideoList(channelId, channelName, channelDescription, channelSubscribers, videoIds, index + 1, done);
        });
    }

    private void loadThumbnail(VideoEntry video)
    {
        if(video.thumbnail == null || video.thumbnail.trim().isEmpty())
        {
            video.thumbnailFailed = true;
            return;
        }

        video.thumbnailLoading = true;

        new Thread(() ->
        {
            try
            {
                URLConnection connection = new URL(video.thumbnail).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(15000);

                BufferedImage image = ImageIO.read(connection.getInputStream());

                if(image != null)
                {
                    video.thumbnailImage = image;
                }
                else
                {
                    video.thumbnailFailed = true;
                }
            }
            catch(Exception e)
            {
                video.thumbnailFailed = true;
            }

            video.thumbnailLoading = false;
        }, "GitTube Thumbnail Loader").start();
    }

    private void syncButtons()
    {
        boolean home = !watchPage;

        btnHome.setVisible(home);
        btnHome.setEnabled(home);
        btnChannels.setVisible(home);
        btnChannels.setEnabled(home);
        btnTrending.setVisible(home);
        btnTrending.setEnabled(home);
        btnReload.setVisible(home);
        btnReload.setEnabled(home);

        btnBack.setVisible(watchPage);
        btnBack.setEnabled(watchPage);
        btnPlayPause.setVisible(watchPage);
        btnPlayPause.setEnabled(watchPage && currentGif != null && currentGif.frames.size() > 0);
        btnRestart.setVisible(watchPage);
        btnRestart.setEnabled(watchPage && currentGif != null && currentGif.frames.size() > 0);

        for(int i = 0; i < videoButtons.length; i++)
        {
            moveVideoButton(i);
            boolean visible = home && pageMode != PAGE_CHANNELS && i < getDisplayedVideos().size() && (pageMode != PAGE_CHANNEL_PROFILE || i < 2);
            videoButtons[i].setVisible(visible);
            videoButtons[i].setEnabled(visible);
        }

        for(int i = 0; i < channelButtons.length; i++)
        {
            boolean visible = home && pageMode == PAGE_CHANNELS && i < getDisplayedChannels().size();
            channelButtons[i].setVisible(visible);
            channelButtons[i].setEnabled(visible);
        }
    }

    private void moveVideoButton(int index)
    {
        int col = index % 2;
        int row = index / 2;

        int cardX = 82 + col * 137;
        int cardY = pageMode == PAGE_CHANNEL_PROFILE ? 111 : 54 + row * 32;

        videoButtons[index].left = cardX + 96;
        videoButtons[index].top = cardY + 14;

        if(layoutMain != null)
        {
            videoButtons[index].xPosition = layoutMain.xPosition + videoButtons[index].left;
            videoButtons[index].yPosition = layoutMain.yPosition + videoButtons[index].top;
        }
    }

    private void drawGitTubeUI(Minecraft mc, int x, int y)
    {
        if(watchPage)
        {
            drawWatchPage(mc, x, y);
        }
        else
        {
            drawHomePage(mc, x, y);
        }

        syncButtons();
    }

    private void drawHomePage(Minecraft mc, int x, int y)
    {
        Gui.drawRect(x, y, x + 362, y + 164, 0xFFEFEFEF);

        Gui.drawRect(x, y, x + 362, y + 25, 0xFFFFFFFF);
        Gui.drawRect(x, y + 24, x + 362, y + 25, 0xFFCCCCCC);

        Gui.drawRect(x + 8, y + 6, x + 28, y + 20, 0xFFE62117);
        drawCentered(mc, ">", x + 18, y + 9, 0xFFFFFFFF);
        mc.fontRenderer.drawString("GitTube", x + 32, y + 9, 0xFF000000);

        Gui.drawRect(x + 86, y + 6, x + 296, y + 20, 0xFFDDDDDD);
        Gui.drawRect(x + 87, y + 7, x + 295, y + 19, 0xFFFFFFFF);
        if(searchFocused)
        {
            Gui.drawRect(x + 87, y + 18, x + 295, y + 20, 0xFFE62117);
        }

        String searchLabel = searchText.isEmpty() ? "Search GitTube..." : searchText + (searchFocused ? "_" : "");
        mc.fontRenderer.drawString(searchLabel, x + 91, y + 10, searchText.isEmpty() ? 0xFF777777 : 0xFF000000);

        Gui.drawRect(x, y + 25, x + 75, y + 164, 0xFFF7F7F7);
        mc.fontRenderer.drawString("GITTUBE", x + 8, y + 106, 0xFFCC0000);
        mc.fontRenderer.drawString("Repo", x + 12, y + 119, 0xFF333333);
        mc.fontRenderer.drawString("Channels", x + 12, y + 132, 0xFF333333);
        mc.fontRenderer.drawString("Videos", x + 12, y + 145, 0xFF333333);

        mc.fontRenderer.drawString("Home", x + 84, y + 31, pageMode == PAGE_HOME ? 0xFF000000 : 0xFF444444);
        mc.fontRenderer.drawString("Trending", x + 124, y + 31, pageMode == PAGE_TRENDING ? 0xFF000000 : 0xFF444444);
        mc.fontRenderer.drawString("Channels", x + 178, y + 31, pageMode == PAGE_CHANNELS || pageMode == PAGE_CHANNEL_PROFILE ? 0xFF000000 : 0xFF444444);
        drawTabUnderline(x, y);

        Gui.drawRect(x + 75, y + 43, x + 362, y + 164, 0xFFE9E9E9);
        mc.fontRenderer.drawString(getPageTitle(), x + 82, y + 47, 0xFF000000);

        if(videos.isEmpty())
        {
            RenderUtil.drawStringClipped(statusText, x + 84, y + 70, 260, 0xFF555555, false);
            RenderUtil.drawStringClipped("Add videos to the GitTube repo, then press Reload.", x + 84, y + 82, 260, 0xFF555555, false);
        }
        else if(pageMode == PAGE_CHANNELS)
        {
            drawChannelList(mc, x, y);
        }
        else if(pageMode == PAGE_CHANNEL_PROFILE)
        {
            drawChannelProfile(mc, x, y);
        }
        else
        {
            ArrayList<VideoEntry> displayedVideos = getDisplayedVideos();

            if(displayedVideos.isEmpty())
            {
                RenderUtil.drawStringClipped("No videos match your search.", x + 84, y + 70, 260, 0xFF555555, false);
            }
            else
            {
                for(int i = 0; i < displayedVideos.size() && i < MAX_VISIBLE_VIDEOS; i++)
                {
                    drawVideoCard(mc, displayedVideos.get(i), i, x, y);
                }
            }
        }

        Gui.drawRect(x + 75, y + 151, x + 362, y + 164, 0xFFFFFFFF);
        RenderUtil.drawStringClipped(statusText, x + 80, y + 154, 274, 0xFF555555, false);
    }

    private void drawTabUnderline(int x, int y)
    {
        if(pageMode == PAGE_TRENDING)
        {
            Gui.drawRect(x + 124, y + 41, x + 170, y + 43, 0xFFE62117);
        }
        else if(pageMode == PAGE_CHANNELS || pageMode == PAGE_CHANNEL_PROFILE)
        {
            Gui.drawRect(x + 178, y + 41, x + 222, y + 43, 0xFFE62117);
        }
        else
        {
            Gui.drawRect(x + 84, y + 41, x + 110, y + 43, 0xFFE62117);
        }
    }

    private String getPageTitle()
    {
        if(pageMode == PAGE_TRENDING)
        {
            return "Trending by views";
        }
        else if(pageMode == PAGE_CHANNELS)
        {
            return "Channels";
        }
        else if(pageMode == PAGE_CHANNEL_PROFILE)
        {
            return selectedChannelName.isEmpty() ? "Channel" : selectedChannelName;
        }

        return searchText.isEmpty() ? "Random from repo" : "Search results";
    }

    private ArrayList<VideoEntry> getDisplayedVideos()
    {
        ArrayList<VideoEntry> displayedVideos = new ArrayList<>();

        for(VideoEntry video : videos)
        {
            if(pageMode == PAGE_CHANNEL_PROFILE && !selectedChannelId.equals(video.channelId))
            {
                continue;
            }

            if(!matchesSearch(video))
            {
                continue;
            }

            displayedVideos.add(video);
        }

        if(pageMode == PAGE_TRENDING)
        {
            displayedVideos.sort((a, b) -> Integer.compare(parseViews(b.views), parseViews(a.views)));
        }
        else if(pageMode == PAGE_HOME)
        {
            displayedVideos.sort((a, b) -> Integer.compare(getHomeRank(a), getHomeRank(b)));
        }

        return displayedVideos;
    }

    private int getHomeRank(VideoEntry video)
    {
        String key = video.channelId + "/" + video.videoId;
        return key.hashCode() ^ homeShuffleSeed;
    }

    private boolean matchesSearch(VideoEntry video)
    {
        if(searchText.trim().isEmpty())
        {
            return true;
        }

        String query = searchText.toLowerCase();
        String haystack = (video.title + " " + video.author + " " + video.channelName + " " + video.description + " " + video.videoId).toLowerCase();
        return haystack.contains(query);
    }

    private String getSearchStatus()
    {
        int matches = getDisplayedVideos().size();
        return searchText.isEmpty() ? "Search cleared." : "Search: " + searchText + " | " + matches + " result(s)";
    }

    private ArrayList<ChannelEntry> getDisplayedChannels()
    {
        LinkedHashMap<String, ChannelEntry> channels = new LinkedHashMap<>();
        String query = searchText.toLowerCase().trim();

        for(VideoEntry video : videos)
        {
            ChannelEntry channel = channels.get(video.channelId);

            if(channel == null)
            {
                channel = new ChannelEntry();
                channel.channelId = video.channelId;
                channel.channelName = video.channelName;
                channel.description = video.channelDescription;
                channel.subscribers = video.channelSubscribers;
                channels.put(video.channelId, channel);
            }

            channel.videoCount++;
        }

        ArrayList<ChannelEntry> displayedChannels = new ArrayList<>();

        for(ChannelEntry channel : channels.values())
        {
            if(query.isEmpty() || channel.channelId.toLowerCase().contains(query) || channel.channelName.toLowerCase().contains(query))
            {
                displayedChannels.add(channel);
            }
        }

        return displayedChannels;
    }

    private int parseViews(String views)
    {
        if(views == null)
        {
            return 0;
        }

        try
        {
            return Integer.parseInt(views.replaceAll("[^0-9]", ""));
        }
        catch(Exception e)
        {
            return 0;
        }
    }

    private void drawChannelList(Minecraft mc, int x, int y)
    {
        ArrayList<ChannelEntry> channels = getDisplayedChannels();

        int row = 0;

        for(ChannelEntry channel : channels)
        {
            int cardX = x + 82;
            int cardY = y + 58 + row * 24;

            Gui.drawRect(cardX, cardY, cardX + 260, cardY + 20, 0xFFFFFFFF);
            Gui.drawRect(cardX, cardY, cardX + 20, cardY + 20, 0xFFE62117);
            drawCentered(mc, ">", cardX + 10, cardY + 6, 0xFFFFFFFF);
            RenderUtil.drawStringClipped(channel.channelName, cardX + 26, cardY + 3, 150, 0xFF0066CC, false);
            RenderUtil.drawStringClipped(channel.videoCount + " video(s) | click to open", cardX + 26, cardY + 13, 180, 0xFF555555, false);

            row++;

            if(row >= 4)
            {
                break;
            }
        }

        if(channels.isEmpty())
        {
            RenderUtil.drawStringClipped("No channels match your search.", x + 84, y + 70, 260, 0xFF555555, false);
        }
    }

    private void drawChannelProfile(Minecraft mc, int x, int y)
    {
        ArrayList<VideoEntry> channelVideos = getDisplayedVideos();
        ChannelEntry channel = getSelectedChannel();

        if(channel == null)
        {
            RenderUtil.drawStringClipped("Channel not found.", x + 84, y + 70, 260, 0xFF555555, false);
            return;
        }

        int headerX = x + 82;
        int headerY = y + 55;
        int headerW = 260;

        Gui.drawRect(headerX, headerY, headerX + headerW, headerY + 39, 0xFFFFFFFF);
        Gui.drawRect(headerX, headerY, headerX + headerW, headerY + 16, 0xFFE62117);
        Gui.drawRect(headerX + 6, headerY + 8, headerX + 40, headerY + 38, 0xFFE62117);
        Gui.drawRect(headerX + 9, headerY + 11, headerX + 37, headerY + 35, 0xFFFFFFFF);
        Gui.drawRect(headerX + 12, headerY + 14, headerX + 34, headerY + 32, 0xFFE62117);
        drawCentered(mc, ">", headerX + 23, headerY + 19, 0xFFFFFFFF);

        RenderUtil.drawStringClipped(channel.channelName, headerX + 46, headerY + 20, 145, 0xFF000000, false);
        String subscribers = channel.subscribers == null || channel.subscribers.isEmpty() ? "0" : channel.subscribers;
        String description = channel.description == null || channel.description.isEmpty() ? "GitTube channel" : channel.description;

        RenderUtil.drawStringClipped("@" + channel.channelId + " | " + subscribers + " subs | " + channel.videoCount + " video(s)", headerX + 46, headerY + 30, 190, 0xFF555555, false);
        RenderUtil.drawStringClipped(description, headerX + 194, headerY + 20, 62, 0xFF333333, false);

        mc.fontRenderer.drawString("Videos", x + 82, y + 99, 0xFF000000);
        Gui.drawRect(x + 82, y + 109, x + 114, y + 111, 0xFFE62117);

        if(channelVideos.isEmpty())
        {
            RenderUtil.drawStringClipped("No videos match your search.", x + 84, y + 122, 260, 0xFF555555, false);
            return;
        }

        for(int i = 0; i < channelVideos.size() && i < 2; i++)
        {
            drawVideoCard(mc, channelVideos.get(i), i, x, y);
        }
    }

    private ChannelEntry getSelectedChannel()
    {
        for(ChannelEntry channel : getDisplayedChannels())
        {
            if(channel.channelId.equals(selectedChannelId))
            {
                return channel;
            }
        }

        for(VideoEntry video : videos)
        {
            if(video.channelId.equals(selectedChannelId))
            {
                ChannelEntry channel = new ChannelEntry();
                channel.channelId = video.channelId;
                channel.channelName = video.channelName;
                channel.description = video.channelDescription;
                channel.subscribers = video.channelSubscribers;
                channel.videoCount = 0;

                for(VideoEntry other : videos)
                {
                    if(other.channelId.equals(selectedChannelId))
                    {
                        channel.videoCount++;
                    }
                }

                return channel;
            }
        }

        return null;
    }

    private void drawWatchPage(Minecraft mc, int x, int y)
    {
        Gui.drawRect(x, y, x + 362, y + 164, 0xFFEFEFEF);

        Gui.drawRect(x, y, x + 362, y + 25, 0xFFFFFFFF);
        Gui.drawRect(x, y + 24, x + 362, y + 25, 0xFFCCCCCC);

        Gui.drawRect(x + 8, y + 6, x + 28, y + 20, 0xFFE62117);
        drawCentered(mc, ">", x + 18, y + 9, 0xFFFFFFFF);
        mc.fontRenderer.drawString("GitTube", x + 32, y + 9, 0xFF000000);

        Gui.drawRect(x, y + 25, x + 75, y + 164, 0xFFF7F7F7);

        if(selectedVideo < 0 || selectedVideo >= videos.size())
        {
            RenderUtil.drawStringClipped("No video selected.", x + 84, y + 50, 260, 0xFF555555, false);
            return;
        }

        VideoEntry video = videos.get(selectedVideo);

        Gui.drawRect(x + 75, y + 25, x + 362, y + 164, 0xFFE9E9E9);

        int videoMaxX = x + 82;
        int videoMaxY = y + 33;
        int videoW = currentGif != null ? currentGif.width : GIF_RENDER_WIDTH;
        int videoH = currentGif != null ? currentGif.height : GIF_RENDER_HEIGHT;
        int videoX = videoMaxX + (GIF_RENDER_WIDTH - videoW) / 2;
        int videoY = videoMaxY + (GIF_RENDER_HEIGHT - videoH) / 2;

        Gui.drawRect(videoX - 1, videoY - 1, videoX + videoW + 1, videoY + videoH + 1, 0xFF111111);
        Gui.drawRect(videoX, videoY, videoX + videoW, videoY + videoH, 0xFF000000);

        updateGifFrame(mc);

        if(currentGif != null && gifTextureLocation != null)
        {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(gifTextureLocation);
            Gui.drawScaledCustomSizeModalRect(videoX, videoY, 0, 0, currentGif.width, currentGif.height, videoW, videoH, currentGif.width, currentGif.height);
        }
        else
        {
            String msg = loadingGif ? "Loading GIF..." : statusText;
            drawCentered(mc, msg, videoX + videoW / 2, videoY + videoH / 2 - 4, 0xFFFFFFFF);
        }

        mc.fontRenderer.drawString(video.title, x + 82, y + 133, 0xFF000000);
        RenderUtil.drawStringClipped(video.author + " | " + video.views + " views | " + video.likes + " likes", x + 82, y + 158, 270, 0xFF555555, false);

        RenderUtil.drawStringClipped(video.description, x + 230, y + 133, 120, 0xFF333333, false);
    }

    private void drawVideoCard(Minecraft mc, VideoEntry video, int index, int appX, int appY)
    {
        int col = index % 2;
        int row = index / 2;

        int x = appX + 82 + col * 137;
        int y = pageMode == PAGE_CHANNEL_PROFILE ? appY + 111 : appY + 54 + row * 32;
        int w = 128;
        int h = 28;

        Gui.drawRect(x, y, x + w, y + h, 0xFFFFFFFF);
        Gui.drawRect(x, y, x + 40, y + 28, 0xFF222222);

        drawThumbnail(mc, video, x, y, 40, 28);

        int durWidth = mc.fontRenderer.getStringWidth(video.duration) + 4;
        Gui.drawRect(x + 40 - durWidth, y + 18, x + 40, y + 28, 0xAA000000);
        mc.fontRenderer.drawString(video.duration, x + 40 - durWidth + 2, y + 20, 0xFFFFFFFF);

        RenderUtil.drawStringClipped(video.title, x + 44, y + 2, 82, 0xFF0066CC, false);
        RenderUtil.drawStringClipped(video.author, x + 44, y + 12, 82, 0xFF555555, false);
        RenderUtil.drawStringClipped(video.views + " views", x + 44, y + 22, 82, 0xFF777777, false);
    }

    private void drawThumbnail(Minecraft mc, VideoEntry video, int x, int y, int w, int h)
    {
        if(video.thumbnailImage != null && video.thumbnailLocation == null)
        {
            video.thumbnailTexture = new DynamicTexture(video.thumbnailImage);
            video.thumbnailLocation = mc.getTextureManager().getDynamicTextureLocation("gittube_thumb_" + video.videoId, video.thumbnailTexture);
        }

        if(video.thumbnailLocation != null)
        {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            mc.getTextureManager().bindTexture(video.thumbnailLocation);
            Gui.drawScaledCustomSizeModalRect(x, y, 0, 0, video.thumbnailImage.getWidth(), video.thumbnailImage.getHeight(), w, h, video.thumbnailImage.getWidth(), video.thumbnailImage.getHeight());
        }
        else
        {
            Gui.drawRect(x, y, x + w, y + h, 0xFF111111);

            if(video.thumbnailLoading)
            {
                drawCentered(mc, "...", x + w / 2, y + 10, 0xFFFFFFFF);
            }
            else if(video.thumbnailFailed)
            {
                drawCentered(mc, "NO IMG", x + w / 2, y + 10, 0xFFFFFFFF);
            }
        }
    }

    private void updateGifFrame(Minecraft mc)
    {
        if(currentGif == null || currentGif.frames.isEmpty())
        {
            return;
        }

        if(gifTexture == null || gifTextureLocation == null)
        {
            uploadGifFrame(mc);
            startPlaybackAfterFirstFrame();
            return;
        }

        startPlaybackAfterFirstFrame();

        if(playing)
        {
            long now = System.currentTimeMillis();
            int delay = currentGif.delays.get(currentFrame);

            if(now - lastFrameTime >= delay)
            {
                currentFrame++;
                if(currentFrame >= currentGif.frames.size())
                {
                    currentFrame = 0;
                    playAudio(true);
                }

                lastFrameTime = now;
                uploadGifFrame(mc);
            }
        }
    }

    private void startPlaybackAfterFirstFrame()
    {
        if(playbackStarted || currentGif == null || currentGif.frames.isEmpty())
        {
            return;
        }

        currentFrame = 0;
        lastFrameTime = System.currentTimeMillis();
        playing = true;
        playbackStarted = true;
        playAudio(true);

        if(selectedVideo >= 0 && selectedVideo < videos.size())
        {
            VideoEntry video = videos.get(selectedVideo);
            statusText = "Playing " + video.title + " | " + currentGif.frames.size() + " frames";
        }
        else
        {
            statusText = "Playing.";
        }
    }

    private void uploadGifFrame(Minecraft mc)
    {
        if(currentGif == null || currentGif.frames.isEmpty())
        {
            return;
        }

        BufferedImage frame = currentGif.frames.get(currentFrame);

        if(gifTexture == null)
        {
            gifTexture = new DynamicTexture(frame);
            gifTextureLocation = mc.getTextureManager().getDynamicTextureLocation("gittube_player_" + System.nanoTime(), gifTexture);
        }
        else
        {
            int[] data = gifTexture.getTextureData();
            frame.getRGB(0, 0, frame.getWidth(), frame.getHeight(), data, 0, frame.getWidth());
            gifTexture.updateDynamicTexture();
        }
    }

    private void loadUrl(String url, String name, TextCallback callback)
    {
        OnlineRequest.getInstance().make(url, (success, response) ->
        {
            if(success && response != null)
            {
                callback.accept(response);
            }
            else
            {
                statusText = "Failed to load " + name;
                callback.accept("");
            }
        });
    }

    private String resolveVideoFile(String channelId, String videoId, String file)
    {
        if(file == null || file.trim().isEmpty())
        {
            return "";
        }

        file = file.trim();

        if(file.startsWith("http://") || file.startsWith("https://"))
        {
            return file;
        }

        return baseUrl + "/channels/" + channelId + "/videos/" + videoId + "/" + file;
    }

    private Map<String, String> parseKeyValues(String raw)
    {
        Map<String, String> map = new LinkedHashMap<>();
        String[] lines = raw.replace("\uFEFF", "").split("\\r?\\n");

        for(String line : lines)
        {
            line = line.trim();

            if(line.isEmpty() || line.startsWith("#"))
            {
                continue;
            }

            int index = line.indexOf('=');

            if(index != -1)
            {
                String key = line.substring(0, index).trim();
                String value = line.substring(index + 1).trim();
                map.put(key, value);
            }
        }

        return map;
    }

    private ArrayList<String> parseEntries(String raw, String keyName)
    {
        ArrayList<String> list = new ArrayList<>();
        String[] lines = raw.replace("\uFEFF", "").split("\\r?\\n");

        for(String line : lines)
        {
            line = line.trim();

            if(line.isEmpty() || line.startsWith("#"))
            {
                continue;
            }

            String prefix = keyName + "=";

            if(line.startsWith(prefix))
            {
                line = line.substring(prefix.length()).trim();
            }

            if(!line.isEmpty())
            {
                list.add(line);
            }
        }

        return list;
    }

    private String get(Map<String, String> map, String key, String fallback)
    {
        if(map.containsKey(key))
        {
            return map.get(key);
        }

        return fallback;
    }

    private Node getNode(Node root, String name)
    {
        if(root == null)
        {
            return null;
        }

        if(name.equals(root.getNodeName()))
        {
            return root;
        }

        Node child = root.getFirstChild();

        while(child != null)
        {
            Node found = getNode(child, name);

            if(found != null)
            {
                return found;
            }

            child = child.getNextSibling();
        }

        return null;
    }

    private String getAttr(NamedNodeMap attrs, String name)
    {
        if(attrs == null)
        {
            return "";
        }

        Node node = attrs.getNamedItem(name);
        return node == null ? "" : node.getNodeValue();
    }

    private int parseInt(String value, int fallback)
    {
        try
        {
            return Integer.parseInt(value);
        }
        catch(Exception e)
        {
            return fallback;
        }
    }

    private void drawCentered(Minecraft mc, String text, int x, int y, int color)
    {
        mc.fontRenderer.drawString(text, x - mc.fontRenderer.getStringWidth(text) / 2, y, color);
    }

    @Override
    public void load(NBTTagCompound tagCompound)
    {
    }

    @Override
    public void save(NBTTagCompound tagCompound)
    {
    }

    @Override
    public void onClose()
    {
        stopAudio();
        super.onClose();
    }

    private static class GitTubeButton extends Button
    {
        private GitTubeButton(int left, int top, String text)
        {
            super(left, top, text);
        }

        @Override
        public void handleMouseClick(int mouseX, int mouseY, int mouseButton)
        {
            if(!this.visible || !this.enabled)
            {
                return;
            }

            if(mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height)
            {
                if(clickListener != null)
                {
                    clickListener.onClick(mouseX, mouseY, mouseButton);
                }

                playClickSound(Minecraft.getMinecraft().getSoundHandler());
            }
        }
    }

    private static class GitTubeHitboxButton extends GitTubeButton
    {
        private GitTubeHitboxButton(int left, int top)
        {
            super(left, top, "");
        }

        @Override
        public void render(com.mrcrayfish.device.core.Laptop laptop, Minecraft mc, int x, int y, int mouseX, int mouseY, boolean windowActive, float partialTicks)
        {
        }
    }

    private interface TextCallback
    {
        void accept(String text);
    }

    private interface DoneCallback
    {
        void run();
    }

    private static class VideoEntry
    {
        private String channelId;
        private String channelName;
        private String channelDescription;
        private String channelSubscribers;
        private String videoId;
        private String title;
        private String author;
        private String duration;
        private String views;
        private String likes;
        private String created;
        private String description;
        private String thumbnail;
        private String gif;
        private String sound;

        private boolean thumbnailLoading;
        private boolean thumbnailFailed;
        private BufferedImage thumbnailImage;
        private DynamicTexture thumbnailTexture;
        private ResourceLocation thumbnailLocation;
    }

    private static class ChannelEntry
    {
        private String channelId;
        private String channelName;
        private String description;
        private String subscribers;
        private int videoCount;
    }

    private static class GifData
    {
        private int width;
        private int height;
        private ArrayList<BufferedImage> frames = new ArrayList<>();
        private ArrayList<Integer> delays = new ArrayList<>();
    }
}
