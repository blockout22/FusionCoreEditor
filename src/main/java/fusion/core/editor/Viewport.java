package fusion.core.editor;

import com.bulletphysics.collision.shapes.BoxShape;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.linearmath.Transform;
import com.fusion.core.EventMouseButton;
import com.fusion.core.GlfwInput;
import com.fusion.core.GlfwWindow;
import com.fusion.core.engine.Global;
import imgui.ImColor;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.extension.imguizmo.ImGuizmo;
import imgui.extension.imguizmo.flag.Mode;
import imgui.extension.imguizmo.flag.Operation;
import imgui.flag.ImGuiCond;
import open.gl.*;
import open.gl.gameobject.Component;
import open.gl.gameobject.Mesh;
import open.gl.gameobject.MeshInstance;
import open.gl.gameobject.PhysicsComponent;
import open.gl.physics.HitResults;
import open.gl.physics.PhysicsWorld;
import open.gl.shaders.DepthShader;
import open.gl.shaders.OpenGlShader;
import open.gl.shaders.WorldShader;
import open.gl.shaders.lights.DirLight;
import open.gl.shaders.lights.PointLight;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.vecmath.Quat4f;
import java.io.File;
import java.util.*;

import static imgui.flag.ImGuiWindowFlags.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL13.GL_TEXTURE4;
import static org.lwjgl.opengl.GL13.glActiveTexture;

public class Viewport {

    private GlfwWindow window;

    private PhysicsWorld physicsWorld;
    private HitResults hitResults;
    private PhysicsComponent lastHitComponent = null;

    private Mesh cube;

    Map<Mesh, List<MeshInstance>> instances = new HashMap<>();

    List<Component> components = new ArrayList<>();

    private PerspectiveCamera camera;
    private CameraController cameraController;
    private DirLight dirLight;
    public PointLight pointLight = new PointLight();

    private DepthShader depthShader;
    private WorldShader worldShader;
    private FrameBuffer depthFrameBuffer, framebuffer;

    private WhileRendering whileDepthRendering, whileRendering;

    private Vector3f center = new Vector3f(0.0f, 0.0f, 0.0f);

    private Matrix4f lightSpaceMatrix = new Matrix4f();
    private Matrix4f lightProjectionMatrix = new Matrix4f();

    private ImVec2 viewportPosition = new ImVec2();
    private ImVec2 viewportSize = new ImVec2();

    private int operation = Operation.TRANSLATE;

    private boolean performRaycast = false;
    private boolean mouseConsumed = false;

    private float angle = 0;

    public Viewport(GlfwWindow window) {
        this.window = window;
        physicsWorld = new PhysicsWorld();
        hitResults = new HitResults();

        camera = new PerspectiveCamera(window.getWidth(), window.getHeight(), 70, 0.1f, 1000f);
        cameraController = new CameraController(camera);
        dirLight = new DirLight();

        pointLight.position.set(10, 15, -5);
        pointLight.ambient.set(0.2f, 0.2f, 0.2f);
        pointLight.diffuse.set(0.5f, 0.5f, 0.5f);
        pointLight.specular.set(1f, 1f, 1f);
        pointLight.constant = 1.0f;
        pointLight.linear = 0.09f;
        pointLight.quadratic = 0.032f;

        Model cubeModel = ModelLoader.loadModels(Global.getAssetDir() + File.separator + "FusionCoreEditor/cube.fbx").get(0);
        cube = new Mesh(cubeModel);

        depthFrameBuffer = new FrameBuffer(4096, 4096, false);
        framebuffer = new FrameBuffer(1920, 1080, false);

        depthShader = new DepthShader();
        worldShader = new WorldShader();

        depthShader.bind();
        OpenGlShader.loadMatrix4f(depthShader.getProjection(), camera.getProjectionMatrix());

        worldShader.bind();
        OpenGlShader.loadMatrix4f(worldShader.getProjection(), camera.getProjectionMatrix());

        GlfwInput.setOnMouseButton(new EventMouseButton() {
            @Override
            public void handle(int button, int action, int mods) {
                if(button == GLFW_MOUSE_BUTTON_LEFT && action == GLFW_PRESS && !ImGuizmo.isOver()){
                    performRaycast = true;
                }
            }
        });

        whileDepthRendering = new WhileRendering(depthShader) {
            @Override
            public void ShaderAfterBind() {
                calculateDirLightPosition();
                OpenGlShader.loadMatrix4f(depthShader.getLightSpaceMatrix(), lightSpaceMatrix);
            }

            @Override
            public void MeshInstanceUpdate(Mesh currentMesh, MeshInstance currentInstance) {
                OpenGlShader.loadMatrix4f(depthShader.getUniformLocation("model"), currentMesh.createTransformationMatrix(currentInstance));
            }
        };

        whileRendering = new WhileRendering(worldShader){
            @Override
            public void ShaderAfterBind() {
                OpenGlShader.loadMatrix4f(worldShader.getProjection(), camera.getProjectionMatrix());
                worldShader.loadViewMatrix(camera);
                OpenGlShader.loadVector3f(worldShader.getUniformLocation("viewPos"), camera.getPosition());
                worldShader.updateDepthMap(depthFrameBuffer.getTextureId());

                calculateDirLightPosition();
                OpenGlShader.loadMatrix4f(worldShader.getUniformLocation("lightSpaceMatrix"), lightSpaceMatrix);
                OpenGlShader.loadInt(worldShader.getUniformLocation("shadowMap"), 4);
                glActiveTexture(GL_TEXTURE4);
                glBindTexture(GL_TEXTURE_2D, depthFrameBuffer.getTextureId());

                OpenGlShader.loadVector3f(worldShader.getUniformLocation("light.position"), pointLight.position);
                OpenGlShader.loadVector3f(worldShader.getUniformLocation("light.ambient"), pointLight.ambient);
                OpenGlShader.loadVector3f(worldShader.getUniformLocation("light.diffuse"), pointLight.diffuse);
                OpenGlShader.loadVector3f(worldShader.getUniformLocation("light.specular"), pointLight.specular);
                OpenGlShader.loadFloat(worldShader.getUniformLocation("light.constant"), pointLight.constant);
                OpenGlShader.loadFloat(worldShader.getUniformLocation("light.linear"), pointLight.linear);
                OpenGlShader.loadFloat(worldShader.getUniformLocation("light.quadratic"), pointLight.quadratic);

                angle += 0.001;
                float x = (float) Math.sin(angle);
                float y = (float) Math.max(-Math.PI, Math.cos(angle)); // This will make the sun rise and set, as it won't go below the horizon
                float z = (float) Math.cos(angle);

                dirLight.direction.set(x, y, z).normalize();
                if(angle >= 2 * Math.PI){
                    angle = (float) -Math.PI;
                }

                worldShader.updateDirLight(dirLight);

                OpenGlShader.loadFloat(worldShader.getUniformLocation("gamma"), 2.2f);
            }

            @Override
            public void MeshInstanceUpdate(Mesh currentMesh, MeshInstance currentInstance) {
                OpenGlShader.loadMatrix4f(worldShader.getUniformLocation("model"), currentMesh.createTransformationMatrix(currentInstance));
            }
        };

        Random r = new Random();
        List<MeshInstance> instanceList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            int distance = 100 / 2;
            int x = r.nextInt(distance) - (distance / 2);
            int y = r.nextInt(distance) - (distance / 2);
            int z = r.nextInt(distance) - (distance / 2);

            MeshInstance instance = new MeshInstance(cube);

            instance.setPosition(x, y, z);
            instanceList.add(instance);

            javax.vecmath.Vector3f halfExtents = physicsWorld.toPhysicsVector(new Vector3f(instance.getScale().x / 2, instance.getScale().y / 2, instance.getScale().z / 2));
            BoxShape box = new BoxShape(halfExtents);
            RigidBody rigidBody = physicsWorld.addShapeToWorld(box, 0.0f, instance.getRotation(), instance.getPosition(), 1.0f);
            components.add(new PhysicsComponent(rigidBody, instance));
        }
        instances.put(cube, instanceList);
    }

    private void raycast(){
        double[] cursorPosition = window.getCursorPosition();

        // Convert cursor position to be relative to the viewport window
        double relativeCursorPositionX = cursorPosition[0] - viewportPosition.x;
        double relativeCursorPositionY = cursorPosition[1] - viewportPosition.y;
        physicsWorld.cursorRaycast(relativeCursorPositionX, relativeCursorPositionY, (int) viewportSize.x, (int) viewportSize.y, camera.getProjectionMatrix(), worldShader.getViewMatrix(), hitResults);

        if(hitResults.hitComponent != null && hitResults.hitComponent != lastHitComponent){
            lastHitComponent = hitResults.hitComponent;
            lastHitComponent.setManipulate(true);
        }else{
            if(lastHitComponent != null) {
                lastHitComponent.getRigidBody().setLinearVelocity(new javax.vecmath.Vector3f(0, 0, 0));
                lastHitComponent.getRigidBody().setAngularVelocity(new javax.vecmath.Vector3f(0, 0, 0));
                lastHitComponent.setManipulate(false);
            }
            lastHitComponent = null;
        }

        performRaycast = false;
    }

    private void calculateDirLightPosition() {
        float distanceFromCenter = 200f;
        Matrix4f lightView = dirLight.getLightViewMatrix(center, distanceFromCenter);
        float near = 1.0f;
        float far = 500f;
        float size = 50f;
        lightProjectionMatrix = dirLight.getLightProjectionMatrix(-size, size, -size, size, near, far);
        lightSpaceMatrix = lightProjectionMatrix.mul(lightView, lightSpaceMatrix);
    }

    int flags = NoCollapse | NoMove;

    public void show(OpenGlRenderer renderer){
        physicsWorld.update(1.0f);
        cameraController.update();

        depthFrameBuffer.bind();
        {
            glClearColor(0.53f, 0.81f, 0.98f, 1.0f);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_FRONT);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            renderer.render(depthShader, instances, whileDepthRendering);
        }
        depthFrameBuffer.unbind();
        framebuffer.bind();
        {
            glClearColor(0.53f, 0.81f, 0.98f, 1.0f);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_BACK);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            renderer.render(worldShader, instances, whileRendering);
        }
        framebuffer.unbind();

        for (int i = 0; i < components.size(); i++) {
            components.get(i).update();
        }

        ImGui.setNextWindowPos(0, 0, ImGuiCond.Once);
        ImGui.setNextWindowSize(window.getWidth(), window.getHeight(), ImGuiCond.Once);

        ImGui.getStyle().setWindowPadding(0, 0);


        if(ImGui.begin("Viewport", flags)){
            ImGui.getWindowPos(viewportPosition);
            ImGui.getWindowSize(viewportSize);
            ImVec2 winSize = ImGui.getWindowSize();
            ImVec2 regionAvail = ImGui.getContentRegionAvail();

            float titleBarHeight = ImGui.getFont().getFontSize() + ImGui.getStyle().getFramePadding().y * 2;

            ImGui.setCursorPos(0, titleBarHeight);


            float aspectRatio = 1920 / 1080;

            ImVec2 imageSize = new ImVec2();
            if(regionAvail.x / regionAvail.y > aspectRatio){
                imageSize.y = regionAvail.y;
                imageSize.x = regionAvail.y * aspectRatio;
            }else{
                imageSize.x = regionAvail.x;
                imageSize.y = regionAvail.x / aspectRatio;
            }


            ImGui.image(framebuffer.getTextureId(), regionAvail.x, regionAvail.y, 0, 1, 1, 0);

            ImGui.setCursorPos(regionAvail.x, regionAvail.y);
            ImGuizmo.beginFrame();
            ImGuizmo.setOrthographic(false);
            ImGuizmo.setEnabled(true);
            ImGuizmo.setDrawList();
            ImGuizmo.setRect(ImGui.getWindowPosX(), ImGui.getWindowPosY(), winSize.x, winSize.y);

            if(lastHitComponent != null){
                lastHitComponent.getRigidBody().activate();
                MeshInstance instance = lastHitComponent.getInstance();
                Matrix4f matrix = new Matrix4f();
                matrix.translate(instance.getPosition());
                matrix.rotate(instance.getRotation());
                matrix.scale(instance.getScale());

                float[] viewMatrix = new float[16];
                viewMatrix = worldShader.getViewMatrix().get(viewMatrix);

                float[] projectionMatrix = new float[16];
                projectionMatrix = camera.getProjectionMatrix().get(projectionMatrix);


                float[] transform = new float[16];
                transform = matrix.get(transform);

                ImGuizmo.manipulate(viewMatrix, projectionMatrix, transform, operation, Mode.WORLD);

                Matrix4f newTransform = new Matrix4f();
                newTransform = newTransform.set(transform);

                Vector3f newPosition = new Vector3f();
                newPosition = newTransform.getTranslation(newPosition);

                Quaternionf newRotation = new Quaternionf();
                newRotation = newTransform.getNormalizedRotation(newRotation);

                instance.setPosition(newPosition);
                instance.setRotation(newRotation);


                javax.vecmath.Vector3f physicsPosition = physicsWorld.toPhysicsVector(newPosition);
                javax.vecmath.Quat4f physicsRotation = new Quat4f(newRotation.x, newRotation.y, newRotation.z, newRotation.w);

                com.bulletphysics.linearmath.Transform physicsTransform = new Transform();
                physicsTransform.set(new javax.vecmath.Matrix4f(physicsRotation, physicsPosition, 1.0f));
                lastHitComponent.getRigidBody().setWorldTransform(physicsTransform);
                lastHitComponent.getRigidBody().getMotionState().setWorldTransform(physicsTransform);
            }

            ImVec2 translateSize = new ImVec2();
            ImVec2 rotateSize = new ImVec2();
            ImVec2 scaleSize = new ImVec2();

            ImGui.calcTextSize(translateSize, "Translate");
            ImGui.calcTextSize(rotateSize, "Rotate");
            ImGui.calcTextSize(scaleSize, "Scale");

            ImGui.setCursorPos(regionAvail.x - 250, titleBarHeight + 5);
            ImVec2 cursorPos = ImGui.getCursorPos();
            //starX, startY, endX, endY, color
            ImGui.getWindowDrawList().addRectFilled(cursorPos.x - 5, cursorPos.y - 1, regionAvail.x, cursorPos.y + 25, ImColor.floatToColor(0, 0, 0, .8f));
            // Create the radio buttons
            if (ImGui.radioButton("Translate", operation == Operation.TRANSLATE)) {
                operation = Operation.TRANSLATE;
                performRaycast = false;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Rotate", operation == Operation.ROTATE)) {
                operation = Operation.ROTATE;
                performRaycast = false;
            }
            ImGui.sameLine();
            if (ImGui.radioButton("Scale", operation == Operation.SCALE)) {
                operation = Operation.SCALE;
                performRaycast = false;
            }

            if(performRaycast && ImGui.isWindowHovered()){
                raycast();
            }
        }
        ImGui.end();
    }

    public void cleanup()
    {
        cube.cleanup();
        depthShader.cleanup();
        worldShader.cleanup();
        depthFrameBuffer.cleanup();
        framebuffer.cleanup();
    }
}
