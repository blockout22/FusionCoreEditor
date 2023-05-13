package fusion.core.editor;

import com.fusion.core.GlfwWindow;
import com.fusion.core.engine.CoreEngine;
import com.fusion.core.engine.Debug;
import com.fusion.core.engine.Global;
import com.fusion.core.engine.plugin.Plugin;
import com.fusion.core.engine.plugin.UnmodifiableString;
import com.fusion.core.engine.renderer.RendererReady;
import imgui.ImFontConfig;
import imgui.ImFontGlyphRangesBuilder;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import open.gl.OpenGlRenderer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static imgui.flag.ImGuiWindowFlags.*;
import static org.lwjgl.opengl.GL30.*;

public class FusionCoreEditor implements Plugin {

    private GlfwWindow window;
    private OpenGlRenderer renderer;

    private final ImGuiImplGlfw imGuiGLFW = new ImGuiImplGlfw();
    private final ImGuiImplGl3 imGuiGl3 = new ImGuiImplGl3();

    @Override
    public void init(CoreEngine coreEngine) {
        coreEngine.addRendererReadyCallback(new RendererReady() {
            @Override
            public void onReady() {
                ImGui.createContext();
                window = (GlfwWindow) coreEngine.getWindow();
                renderer = (OpenGlRenderer) coreEngine.getRenderer();

                final ImGuiIO io = ImGui.getIO();

                io.setIniFilename(null);
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
                io.getFonts().addFontFromMemoryTTF(loadFromResources("Roboto-Bold.ttf"), 14, fontConfig, glyphRanges); // cyrillic glyphsio.getFonts().build();
                io.getFonts().build();

                fontConfig.destroy();

                imGuiGLFW.init(window.getWindowID(), true);
                imGuiGl3.init("#version 330");
            }
        });
    }

    private byte[] loadFromResources(String path){
        try{
            File file = new File(Global.getAssetDir().getAbsolutePath() + File.separator + path);
            if(!file.exists()){
                throw new FileNotFoundException("File Not Found");
            }
            return Files.readAllBytes(Paths.get(file.toURI()));
        }catch (IOException e){
            throw new RuntimeException(e);
        }
    }

    @Override
    public void update() {
        glClearColor(1, 0, 1, 1);
        glClear(GL_COLOR_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);
        imGuiGLFW.newFrame();
        ImGui.newFrame();

        ImGui.setNextWindowPos(0, 0, ImGuiCond.Always);
        ImGui.setNextWindowSize(window.getWidth(), window.getHeight(), ImGuiCond.Always);

        ImGui.getStyle().setWindowPadding(0, 0);
//        if(ImGui.begin("My Window", NoTitleBar | NoMove | NoResize | NoCollapse | NoBackground)){
        if(ImGui.begin("Viewport")){

        }
        ImGui.end();

        ImGui.render();
        imGuiGl3.renderDrawData(ImGui.getDrawData());
    }

    @Override
    public void shutdown() {
        ImGui.destroyContext();
    }

    @Override
    public UnmodifiableString setId() {
        return UnmodifiableString.fromString("FusionCoreEditor");
    }

    @Override
    public List<String> getDependencies() {
        List<String> deps = new ArrayList<>();
        deps.add("OpenGL");
        deps.add("GLFW");
        deps.add("jbullet");
        deps.add("imgui-binding");
        return null;
    }
}
