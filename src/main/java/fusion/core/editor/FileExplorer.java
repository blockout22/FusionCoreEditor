package fusion.core.editor;

import com.fusion.core.engine.Global;
import imgui.ImGui;
import open.gl.texture.Texture;
import open.gl.texture.TextureLoader;

import java.io.File;

public class FileExplorer {

    private File rootDir;
    private File currentDir;

    private Texture fileIcon;
    private Texture folderIcon;

    private int[] iconSize = {75};

    public FileExplorer() {
        this.rootDir = Global.getAssetDir();
        this.currentDir = new File(rootDir.getAbsolutePath());

        fileIcon = TextureLoader.loadTexture(Global.getAssetDir().getAbsolutePath() + File.separator + "FusionCoreEditor" + File.separator + "fileIcon.png");
        folderIcon = TextureLoader.loadTexture(Global.getAssetDir().getAbsolutePath() + File.separator + "FusionCoreEditor" + File.separator + "folderIcon.png");
    }

    public void show() {
        if (ImGui.begin("File Explorer")) {
            String displayPath = currentDir.getAbsolutePath().replace(rootDir.getAbsolutePath(), rootDir.getName());
            ImGui.text("Current Directory: " + (displayPath.isEmpty() ? "\\" : displayPath));

            float windowWidth = ImGui.getContentRegionAvailX();
            int numColumns = (int) (windowWidth / (iconSize[0] + 20));

            ImGui.setNextItemWidth(100);
            ImGui.sameLine(ImGui.getWindowWidth() - 120);
            if(ImGui.sliderInt("##Size", iconSize, 30, 125)){

            }

            if (!currentDir.getAbsolutePath().equals(rootDir.getAbsolutePath())) {
                if (ImGui.button("..")) {
                    currentDir = currentDir.getParentFile();
                }
            }


            File[] files = currentDir.listFiles();
            if (files != null) {
                ImGui.columns(numColumns, "My Columns", false);
                for (File file : files) {
                    Texture icon = file.isDirectory() ? folderIcon : fileIcon;
                    ImGui.pushID(file.getName());
                    {
                        if (ImGui.imageButton(icon.getID(), iconSize[0], iconSize[0])) {
                            if (file.isDirectory()) {
                                currentDir = file;
                            } else {
                            }
                        }
                        ImGui.pushTextWrapPos(ImGui.getCursorPosX() + iconSize[0]);
                        ImGui.text(file.getName());
                        ImGui.popTextWrapPos();
                    }
                    ImGui.popID();
                    ImGui.nextColumn();
                }
                ImGui.columns(1);
            }
        }
        ImGui.end();
    }
}
