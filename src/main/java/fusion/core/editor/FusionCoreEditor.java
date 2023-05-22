package fusion.core.editor;

import com.fusion.core.GlfwWindow;
import com.fusion.core.engine.CoreEngine;
import com.fusion.core.engine.Global;
import com.fusion.core.engine.plugin.Plugin;
import com.fusion.core.engine.plugin.UnmodifiableString;
import com.fusion.core.engine.renderer.RendererReady;
import imgui.*;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import open.gl.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.lwjgl.opengl.GL30.*;

public class FusionCoreEditor extends Plugin {

    private GlfwWindow window;
    private OpenGlRenderer renderer;

    private final ImGuiImplGlfw imGuiGLFW = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    private Viewport viewport;
    private Properties properties;

    @Override
    public void init(CoreEngine coreEngine) {
        String jarFilePath = FusionCoreEditor.class.getProtectionDomain().getCodeSource().getLocation().getPath();

        try(JarFile jarFile = new JarFile(jarFilePath)){
            jarFile.stream().map(JarEntry::getName).forEach(System.out::println);
        }catch (IOException e){
            e.printStackTrace();
        }

        coreEngine.addRendererReadyCallback(new RendererReady() {
            @Override
            public void onReady() {
                ImGui.createContext();
                window = (GlfwWindow) coreEngine.getWindow();
                renderer = (OpenGlRenderer) coreEngine.getRenderer();

                final ImGuiIO io = ImGui.getIO();

                File layoutConfigFile = new File("Config/EditorLayout.ini");

                if(!layoutConfigFile.getParentFile().exists()){
                    layoutConfigFile.getParentFile().mkdirs();
                }

                io.setIniFilename(layoutConfigFile.toString());
                io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
                io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
                io.setConfigViewportsNoTaskBarIcon(true);

                io.getFonts().addFontDefault();
                final ImFontGlyphRangesBuilder rangesBuilder = new ImFontGlyphRangesBuilder(); // Glyphs ranges provide
                rangesBuilder.addRanges(io.getFonts().getGlyphRangesDefault());
                rangesBuilder.addRanges(io.getFonts().getGlyphRangesCyrillic());
                rangesBuilder.addRanges(io.getFonts().getGlyphRangesJapanese());

                final ImFontConfig fontConfig = new ImFontConfig();
                fontConfig.setMergeMode(true);

                final short[] glyphRanges = rangesBuilder.buildRanges();
                io.getFonts().addFontFromMemoryTTF(loadFromResources(getId() + File.separator + "Roboto-Bold.ttf"), 14, fontConfig, glyphRanges); // cyrillic glyphsio.getFonts().build();
                io.getFonts().build();

                fontConfig.destroy();

                imGuiGLFW.init(window.getWindowID(), true);
                imGuiGl3.init("#version 330");

                viewport = new Viewport(window);
                properties = new Properties();
            }
        });
    }

    @Override
    public void update() {
        glClear(GL_COLOR_BUFFER_BIT);
        imGuiGLFW.newFrame();
        ImGui.newFrame();

        viewport.show(renderer);
        properties.show(viewport);

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    @Override
    public void shutdown() {
        viewport.cleanup();
        ImGui.destroyContext();
    }

    private byte[] loadFromResources(String path){
        try{
            File file = new File(Global.getAssetDir().getAbsolutePath() + File.separator + path);
            if(!file.exists()){
                throw new FileNotFoundException("File Not Found: " + file);
            }
            return Files.readAllBytes(Paths.get(file.toURI()));
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setId() {
        id.set("FusionCoreEditor");
//        return UnmodifiableString.fromString("FusionCoreEditor");
    }

    @Override
    public List<String> getDependencies() {
        List<String> deps = new ArrayList<>();
        deps.add("OpenGL");
        deps.add("GLFW");
        deps.add("jbullet");
        deps.add("imgui-app-1.86.10-all");
        return null;
    }
}
