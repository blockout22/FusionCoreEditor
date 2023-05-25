package fusion.core.editor;

import com.fusion.core.GlfwWindow;
import imgui.ImGui;
import open.gl.PerspectiveCamera;
import open.gl.gameobject.MeshInstance;
import open.gl.gameobject.PhysicsComponent;
import open.gl.physics.HitResults;
import open.gl.shaders.WorldShader;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;

public class Toolbar {

    private GlfwWindow window;

    private ArrayList<String> objects = new ArrayList<>();
    private String selectedObject = null;
    private PhysicsComponent spawnedInstance = null;

    private HitResults hitResults = new HitResults();

    public Toolbar(GlfwWindow window) {
        this.window = window;
        objects.add("Cube");
//        objects.add("Sphere");
    }

    public void show(Viewport viewport){
        if(ImGui.begin("Toolbar")){
            ImGui.dummy(10, 10);
            ImGui.dummy(10, 10);

            boolean anyItemHovered = false;
            for (int i = 0; i < objects.size(); i++) {
                ImGui.sameLine();
                if(ImGui.button(objects.get(i))){
                }

                if(ImGui.isItemHovered()){
                    anyItemHovered = true;
                    //check if object is null if not then changing the selected object will overwrite during dragging if another item is hovered
                    if(selectedObject == null) {
                        selectedObject = objects.get(i);
                    }
                }
            }

            //if draggin stops and nothing is hovered set selected item to null
            if(!anyItemHovered && !ImGui.isMouseDragging(0)){
                selectedObject = null;
                spawnedInstance = null;
            }

            if(ImGui.isMouseDragging(0)){
                if(selectedObject != null){
                    //spawn in new item in the viewport
                    if(spawnedInstance != null){
                       Vector3f mouseRay = getMouseRay(viewport.camera, viewport.worldShader);
                       mouseRay.floor();
                        viewport.updateComponent(spawnedInstance, mouseRay, spawnedInstance.getInstance().getRotation(), new Vector3f(1, 1, 1));
                    }else {
                        //TODO check what the selectedObject is and spawned it based on that
                        spawnedInstance = viewport.addCube();
                    }
                }
            }
        }
        ImGui.end();
    }

    private Vector3f getMouseRay(PerspectiveCamera camera, WorldShader worldShader) {
        double[] cursorPosition = window.getCursorPosition();
        float mouseX = (float)cursorPosition[0];
        float mouseY = (float)cursorPosition[1];

        float normalizedX = (2.0f * mouseX) / window.getWidth() - 1f;
        float normalizedY = 1f - (2.0f * mouseY) / window.getHeight();
        Vector4f clipCoords = new Vector4f(normalizedX, normalizedY, -1.0f, 1.0f);

        Matrix4f invertedProjection = new Matrix4f(camera.getProjectionMatrix());
        invertedProjection.invert();

        Vector4f eyeCoords = invertedProjection.transform(clipCoords);
        eyeCoords.z = -1.0f;
        eyeCoords.w = 0.0f;

        Matrix4f invertedView = new Matrix4f(worldShader.getViewMatrix());
        invertedView.invert();

        Vector4f worldRay = invertedView.transform(eyeCoords);
        Vector3f mouseRay = new Vector3f(worldRay.x, worldRay.y, worldRay.z);
        mouseRay.normalize();

        Vector3f cameraPosition = new Vector3f(camera.getPosition());
        mouseRay.mul(5f).add(cameraPosition);

        return mouseRay;
    }
}
