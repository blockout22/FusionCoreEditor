package fusion.core.editor;

import imgui.ImGui;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import open.gl.gameobject.MeshInstance;
import open.gl.gameobject.PhysicsComponent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Properties {

    public Properties() {
    }

    public void show(Viewport viewport){
        ImGui.setNextWindowPos(0, 0, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(250, 250, ImGuiCond.FirstUseEver);

        if(ImGui.begin("Properties")){
            if(viewport.getSelectedComponent() != null) {
                MeshInstance meshInstance = viewport.getSelectedComponent().getInstance();
                Matrix4f instanceMatrix = meshInstance.getModelMatrix();

                float[] modelMatrix = new float[16];
                instanceMatrix.get(modelMatrix);
                float[] translation = new float[3];
                float[] rotation = new float[3];
                float[] scale = new float[3];
                ImGuizmo.decomposeMatrixToComponents(modelMatrix, translation, rotation, scale);

                boolean usingTrn = ImGui.inputFloat3("Translation", translation, "%.3f", ImGuiInputTextFlags.EnterReturnsTrue);
                boolean usingRot = ImGui.inputFloat3("Rotation", rotation, "%.3f", ImGuiInputTextFlags.EnterReturnsTrue);
                boolean usingScl = ImGui.inputFloat3("Scale", scale, "%.3f", ImGuiInputTextFlags.EnterReturnsTrue);

                if(usingRot){
                    for (int i = 0; i < 3; i++) {
                        rotation[i] = normalizeAngle(rotation[i]);
                    }
                }

                if(usingTrn || usingRot || usingScl) {
                    ImGuizmo.recomposeMatrixFromComponents(modelMatrix, translation, rotation, scale);

                    Matrix4f newModelMatrix = new Matrix4f();
                    newModelMatrix.set(modelMatrix);

                    Vector3f newPos = new Vector3f();
                    newModelMatrix.getTranslation(newPos);

                    Quaternionf newRot = new Quaternionf();
                    newModelMatrix.getNormalizedRotation(newRot);

                    viewport.updateComponent(newPos, newRot);
                }
            }
        }
        ImGui.end();
    }

    private float normalizeAngle(float angle) {
        float newAngle = angle % 360;
        if (newAngle > 180) {
            newAngle -= 360;
        } else if (newAngle < -180) {
            newAngle += 360;
        }
        return newAngle;
    }
}

