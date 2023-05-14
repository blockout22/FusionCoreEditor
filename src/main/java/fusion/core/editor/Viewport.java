package fusion.core.editor;

import com.fusion.core.GlfwWindow;
import com.fusion.core.engine.Global;
import imgui.ImGui;
import imgui.ImVec2;
import open.gl.*;
import open.gl.gameobject.Mesh;
import open.gl.gameobject.MeshInstance;
import open.gl.shaders.DepthShader;
import open.gl.shaders.OpenGlShader;
import open.gl.shaders.WorldShader;
import open.gl.shaders.lights.DirLight;
import open.gl.shaders.lights.PointLight;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.*;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT;
import static org.lwjgl.opengl.GL13.GL_TEXTURE4;
import static org.lwjgl.opengl.GL13.glActiveTexture;

public class Viewport {

    private Mesh cube;
    Map<Mesh, List<MeshInstance>> instances = new HashMap<>();

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

    public Viewport(GlfwWindow window) {
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
            int distance = 100;
            int x = r.nextInt(distance) - (distance / 2);
            int y = r.nextInt(distance) - (distance / 2);
            int z = r.nextInt(distance) - (distance / 2);

            MeshInstance instance = new MeshInstance(cube);

            instance.setPosition(x, y, z);
            instanceList.add(instance);
        }
        instances.put(cube, instanceList);
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

    public void show(OpenGlRenderer renderer){
        cameraController.update();
        {
            glClearColor(0.53f, 0.81f, 0.98f, 1.0f);
            glEnable(GL_DEPTH_TEST);
            glEnable(GL_CULL_FACE);
            glCullFace(GL_FRONT);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

            renderer.render(depthShader, instances, whileDepthRendering);
        }
        depthFrameBuffer.bind();

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

        if(ImGui.begin("Viewport")){
            ImVec2 winSize = ImGui.getWindowSize();
            ImGui.image(framebuffer.getTextureId(), winSize.x, winSize.y, 0, 1, 1, 0);
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
