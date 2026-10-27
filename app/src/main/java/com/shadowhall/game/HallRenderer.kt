package com.shadowhall.game

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/** Lightweight OpenGL ES 2.0 third-person prototype. Art and AI are intentionally placeholder-level. */
class HallRenderer : GLSurfaceView.Renderer {
    @Volatile var moveX = 0f
    @Volatile var moveZ = 0f
    @Volatile var cameraYaw = 0f
    @Volatile var hidden = false
    @Volatile var flashlightOn = true
    @Volatile var flashlightBattery = 100f
    @Volatile var stamina = 100f
    @Volatile var keysCount = 0
    @Volatile var statusText = "اجمع 3 مفاتيح"
    @Volatile var monsterState = "يتجول"
    @Volatile var gameWon = false
    @Volatile var exitDoorOpen = false
    @Volatile var drawerOpen = false
    @Volatile var hasFuse = false
    @Volatile var batteryPackCollected = false
    @Volatile var bedroomDoorOpen = false
    @Volatile var kitchenDoorOpen = false
    @Volatile var storageDoorOpen = false
    @Volatile var sideRoomDoorOpen = false
    private var program = 0
    private var posHandle = 0
    private var mvpHandle = 0
    private var colorHandle = 0
    private lateinit var vertices: FloatBuffer
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val vp = FloatArray(16)
    private val mvp = FloatArray(16)
    private var px = 0f; private var pz = 2.8f
    private var mx = 0f; private var mz = -3.2f
    private val keys = arrayOf(floatArrayOf(-6.0f, -2.9f), floatArrayOf(6.0f, -2.9f), floatArrayOf(0.2f, 3.5f))
    private val collected = BooleanArray(3)
    private var lastTime = System.nanoTime()
    private var gameOver = false
    private var alertTimer = 0f
    private var patrolClock = 0f
    private var investigateTimer = 0f
    private var hideDiscoveryTimer = 0f
    private var lastKnownX = 0f
    private var lastKnownZ = 0f
    private var searchPhase = 0f
    private var searchTargetTimer = 0f
    private var searchTargetX = 0f
    private var searchTargetZ = 0f
    private var lastSeenTimer = 0f
    private var patrolTargetX = -5.5f
    private var patrolTargetZ = 0f
    private val hideSpots = arrayOf(floatArrayOf(-2.8f, -0.4f), floatArrayOf(2.5f, 2.0f), floatArrayOf(0f, -3.8f))

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.025f, 0.035f, 0.07f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        val vertexShader = "attribute vec4 aPosition; uniform mat4 uMVP; void main(){ gl_Position=uMVP*aPosition; }"
        val fragmentShader = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor=uColor; }"
        program = GLES20.glCreateProgram().also { p ->
            val vs = compile(GLES20.GL_VERTEX_SHADER, vertexShader); val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            GLES20.glAttachShader(p, vs); GLES20.glAttachShader(p, fs); GLES20.glLinkProgram(p)
        }
        posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        val data = floatArrayOf(
            -0.5f,-0.5f,0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,-0.5f,0.5f, 0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f,
            0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, -0.5f,-0.5f,0.5f, -0.5f,0.5f,0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,0.5f, -0.5f,0.5f,-0.5f,
            0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,-0.5f,0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f,
            -0.5f,0.5f,0.5f, 0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,0.5f, 0.5f,0.5f,-0.5f, -0.5f,0.5f,-0.5f,
            -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,-0.5f, 0.5f,-0.5f,0.5f, -0.5f,-0.5f,0.5f
        )
        vertices = ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
    }
    private fun compile(type: Int, source: String): Int = GLES20.glCreateShader(type).also { s -> GLES20.glShaderSource(s, source); GLES20.glCompileShader(s) }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0,0,width,height)
        Matrix.perspectiveM(projection,0,62f,width.toFloat()/height.coerceAtLeast(1),0.1f,60f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime(); val dt = ((now-lastTime)/1e9f).coerceIn(0f,0.04f); lastTime=now
        if (!gameOver) update(dt)
        // Flashlight battery makes light management part of the survival loop.
        if (flashlightOn) {
            flashlightBattery = (flashlightBattery - 2.2f * dt).coerceAtLeast(0f)
            if (flashlightBattery <= 0f) flashlightOn = false
        } else {
            flashlightBattery = (flashlightBattery + 1.25f * dt).coerceAtMost(100f)
        }
        // Danger feedback: the scene subtly pulses red as the monster closes in.
        // This is a cheap clear-color effect, so it does not add draw calls.
        val dangerDx = px - mx
        val dangerDz = pz - mz
        val dangerDistance = sqrt(dangerDx * dangerDx + dangerDz * dangerDz)
        val dangerPulse = (kotlin.math.sin(System.nanoTime() / 150000000.0).toFloat() + 1f) * 0.5f
        val danger = if (!hidden && !gameWon && !gameOver && dangerDistance < 4.2f)
            ((4.2f - dangerDistance) / 4.2f).coerceIn(0f, 1f) * (0.025f + dangerPulse * 0.035f)
        else 0f
        GLES20.glClearColor(0.025f + danger, 0.035f, 0.07f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val yaw = Math.toRadians(cameraYaw.toDouble())
        // First-person camera, inspired by classic hide-and-escape horror games.
        val lookX = px + kotlin.math.sin(yaw).toFloat()
        val lookZ = pz - kotlin.math.cos(yaw).toFloat()
        Matrix.setLookAtM(view,0,px,1.48f,pz,lookX,1.38f,lookZ,0f,1f,0f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        // Multi-room house: central hall plus two side wings, kept low-poly for A23.
        box(0f,-0.22f,0f,8f,0.35f,12f,0.13f,0.15f,0.21f)
        box(-6f,-0.22f,0f,4.2f,0.35f,12f,0.12f,0.14f,0.19f)
        box(6f,-0.22f,0f,4.2f,0.35f,12f,0.12f,0.14f,0.19f)
        for (z in -5..5 step 2) box(0f,-0.035f,z.toFloat(),7.7f,0.012f,0.025f,0.22f,0.24f,0.30f)
        for (x in listOf(-6f, 6f)) for (z in -5..5 step 2) box(x,-0.035f,z.toFloat(),3.8f,0.012f,0.025f,0.18f,0.20f,0.26f)
        for (z in -4..4 step 4) {
            box(0f,2.18f,z.toFloat(),1.0f,0.04f,0.35f,0.52f,0.38f,0.16f)
            box(-1.8f,2.18f,z.toFloat(),0.16f,0.035f,0.3f,0.25f,0.38f,0.48f)
            box(1.8f,2.18f,z.toFloat(),0.16f,0.035f,0.3f,0.25f,0.38f,0.48f)
        }
        // Outer walls of the expanded house.
        box(-8.1f,1f,0f,0.3f,2.4f,12.5f,0.20f,0.22f,0.30f)
        box(8.1f,1f,0f,0.3f,2.4f,12.5f,0.20f,0.22f,0.30f)
        // Keep the escape doorway clear in the rear wall.
        box(-4.6f,1f,-6.25f,7.0f,2.4f,0.3f,0.16f,0.18f,0.25f)
        box(4.6f,1f,-6.25f,7.0f,2.4f,0.3f,0.16f,0.18f,0.25f)
        box(0f,1f,6.25f,16.2f,2.4f,0.3f,0.16f,0.18f,0.25f)
        // Side-hall partitions leave doorways at z=-2 and z=2.
        for (x in listOf(-4f, 4f)) {
            box(x,1f,-4.35f,0.18f,2.4f,3.8f,0.20f,0.22f,0.30f)
            box(x,1f,0f,0.18f,2.4f,2.0f,0.20f,0.22f,0.30f)
            box(x,1f,4.35f,0.18f,2.4f,3.8f,0.20f,0.22f,0.30f)
        }
        // Three named rooms: bedroom, kitchen and storage. Door panels slide aside when opened.
        box(-6.1f,0.72f,-3.9f,1.25f,1.35f,0.72f,0.22f,0.13f,0.12f)
        box(-6.1f,1.42f,-3.9f,1.28f,0.08f,0.74f,0.40f,0.25f,0.19f)
        box(-6.1f,0.20f,-3.9f,1.30f,0.35f,0.76f,0.15f,0.10f,0.10f)
        box(6.0f,0.48f,-3.9f,1.15f,0.92f,0.65f,0.26f,0.29f,0.31f)
        box(6.0f,0.98f,-3.9f,1.18f,0.08f,0.68f,0.48f,0.43f,0.30f)
        box(6.0f,0.30f,3.8f,1.05f,0.55f,0.72f,0.28f,0.23f,0.15f)
        box(6.0f,0.62f,3.8f,0.82f,0.08f,0.54f,0.42f,0.34f,0.21f)
        // Internal door between the right-side rooms.
        box(if(sideRoomDoorOpen) 7.35f else 6.0f,1f,0f,1.45f,2.0f,0.12f,if(sideRoomDoorOpen) 0.34f else 0.27f,if(sideRoomDoorOpen) 0.42f else 0.19f,0.12f)
        // Four doorway panels. Open doors are visibly slid along the wall.
        box(if(bedroomDoorOpen) -4.0f else -4.0f,1f,if(bedroomDoorOpen) -0.05f else -2f,0.12f,1.95f,1.35f,0.30f,0.18f,0.12f)
        box(if(kitchenDoorOpen) 4.0f else 4.0f,1f,if(kitchenDoorOpen) 0.05f else -2f,0.12f,1.95f,1.35f,0.22f,0.30f,0.24f)
        box(-4.0f,1f,if(storageDoorOpen) 3.95f else 2f,0.12f,1.95f,1.35f,0.28f,0.20f,0.13f)
        box(4.0f,1f,if(sideRoomDoorOpen) 3.95f else 2f,0.12f,1.95f,1.35f,0.25f,0.22f,0.15f)
        // Atmospheric pass: wall panels, ceiling fixtures and unsettling stains.
        // These simple meshes keep rendering inexpensive on mid-range Android phones.
        val flicker = if ((System.nanoTime() / 100000000L) % 19L == 0L) 0.12f else 1f
        for (z in -4..4 step 2) {
            box(-3.82f,1.05f,z.toFloat(),0.025f,1.55f,0.72f,0.10f,0.12f,0.17f)
            box(3.82f,1.05f,z.toFloat(),0.025f,1.55f,0.72f,0.10f,0.12f,0.17f)
            box(-3.79f,1.78f,z.toFloat(),0.035f,0.045f,0.66f,0.32f,0.12f,0.10f)
            box(3.79f,1.78f,z.toFloat(),0.035f,0.045f,0.66f,0.32f,0.12f,0.10f)
        }
        for (z in -4..4 step 4) {
            box(0f,2.27f,z.toFloat(),0.72f,0.035f,0.24f,0.72f*flicker,0.68f*flicker,0.42f*flicker)
            box(0f,2.22f,z.toFloat(),0.92f,0.035f,0.34f,0.12f,0.13f,0.18f)
        }
        box(-1.45f,0.005f,1.35f,0.62f,0.012f,0.34f,0.12f,0.045f,0.055f)
        box(1.75f,0.006f,-2.7f,0.38f,0.013f,0.52f,0.09f,0.035f,0.045f)
        box(-3.78f,1.10f,-2.0f,0.018f,0.52f,0.30f,0.18f,0.035f,0.045f)
        // Exposed ceiling pipes and utility conduits add depth to the abandoned building.
        for (z in -4..4 step 2) {
            box(-2.65f,2.05f,z.toFloat(),0.055f,0.055f,1.05f,0.20f,0.22f,0.24f)
            box(2.65f,2.05f,z.toFloat(),0.055f,0.055f,1.05f,0.20f,0.22f,0.24f)
            box(-2.65f,2.00f,z.toFloat(),0.09f,0.025f,0.08f,0.31f,0.16f,0.10f)
            box(2.65f,2.00f,z.toFloat(),0.09f,0.025f,0.08f,0.31f,0.16f,0.10f)
        }
        // A damaged wall notice and rusty utility panel provide visual landmarks.
        box(3.78f,1.20f,0.25f,0.025f,0.48f,0.38f,0.30f,0.24f,0.16f)
        box(3.75f,1.20f,0.25f,0.025f,0.32f,0.25f,0.10f,0.12f,0.14f)
        box(3.72f,1.28f,0.17f,0.025f,0.035f,0.035f,0.65f,0.08f,0.06f)
        box(-3.78f,0.82f,2.4f,0.025f,0.25f,0.50f,0.11f,0.13f,0.15f)
        box(-3.75f,0.82f,2.4f,0.025f,0.15f,0.38f,0.34f,0.20f,0.10f)
        // cartoon furniture that can provide visual cover
        box(-2.8f,0.45f,-0.4f,1.0f,0.9f,1.2f,0.28f,0.12f,0.10f)
        box(2.5f,0.6f,2.0f,1.2f,1.2f,0.8f,0.18f,0.20f,0.27f)
        box(0f,0.5f,-3.8f,1.5f,1.0f,0.7f,0.25f,0.18f,0.11f)
        // Cabinet details, bed-like obstacle and scattered floor clutter.
        box(-2.8f,0.48f,-1.02f,0.68f,0.05f,0.025f,0.52f,0.30f,0.14f)
        // Searchable cabinet drawer; it slides outward after interaction.
        box(-2.8f,0.27f,if(drawerOpen) -1.30f else -0.94f,0.58f,0.20f,0.10f,0.36f,0.19f,0.09f)
        box(-2.8f,0.27f,if(drawerOpen) -1.355f else -0.995f,0.12f,0.035f,0.025f,0.72f,0.54f,0.28f)
        if(drawerOpen && !hasFuse) box(-2.8f,0.42f,-1.30f,0.16f,0.10f,0.12f,0.18f,0.88f,0.72f)
        box(-3.12f,0.50f,-0.4f,0.06f,0.74f,0.06f,0.42f,0.23f,0.11f)
        box(-2.48f,0.50f,-0.4f,0.06f,0.74f,0.06f,0.42f,0.23f,0.11f)
        box(2.5f,1.22f,2.0f,0.92f,0.08f,0.56f,0.35f,0.36f,0.43f)
        box(2.5f,0.18f,2.0f,1.0f,0.35f,0.62f,0.16f,0.13f,0.12f)
        box(1.0f,0.015f,0.8f,0.45f,0.025f,0.22f,0.32f,0.25f,0.18f)
        box(-1.1f,0.015f,-2.0f,0.28f,0.025f,0.35f,0.26f,0.27f,0.30f)
        // The player is not drawn because this version uses a first-person camera.
        // Monster: low-poly horror pursuer with red eyes.
        val monsterBob = kotlin.math.sin((System.nanoTime()/180000000.0).toFloat())*0.035f
        box(mx,0.62f+monsterBob,mz,0.72f,1.25f,0.62f,0.20f,0.025f,0.065f)
        box(mx,1.36f+monsterBob,mz+0.02f,0.46f,0.42f,0.42f,0.085f,0.018f,0.04f)
        box(mx-0.47f,0.67f+monsterBob,mz,0.18f,0.82f,0.20f,0.13f,0.018f,0.045f)
        box(mx+0.47f,0.67f+monsterBob,mz,0.18f,0.82f,0.20f,0.13f,0.018f,0.045f)
        box(mx-0.13f,1.40f+monsterBob,mz+0.235f,0.085f,0.065f,0.035f,1f,0.015f,0.01f)
        box(mx+0.13f,1.40f+monsterBob,mz+0.235f,0.085f,0.065f,0.035f,1f,0.015f,0.01f)
        // A spare battery pack is a small optional resource placed in the side corridor.
        if (!batteryPackCollected) {
            val batteryBob = 0.24f + kotlin.math.sin((System.nanoTime()/220000000.0).toFloat())*0.035f
            box(1.15f,batteryBob,-1.85f,0.28f,0.34f,0.18f,0.18f,0.72f,0.34f)
            box(1.15f,batteryBob+0.19f,-1.85f,0.12f,0.055f,0.10f,0.72f,0.82f,0.70f)
            box(1.15f,batteryBob,-1.745f,0.055f,0.18f,0.018f,0.78f,0.95f,0.72f)
        }
        for (i in keys.indices) if (!collected[i]) {
            val bob=0.28f+kotlin.math.sin((System.nanoTime()/250000000.0+i).toFloat())*0.04f
            box(keys[i][0],bob,keys[i][1],0.22f,0.22f,0.22f,1f,0.72f,0.08f)
        }
        // Exit gate and its frame; the door only opens after interaction with all keys.
        box(-1.0f,1f,-5.72f,0.18f,2.15f,0.22f,0.30f,0.22f,0.13f)
        box(1.0f,1f,-5.72f,0.18f,2.15f,0.22f,0.30f,0.22f,0.13f)
        box(0f,2.05f,-5.72f,2.15f,0.18f,0.22f,0.30f,0.22f,0.13f)
        // Exit gate: after unlocking, the heavy panel slides aside instead of vanishing.
        val gateX = if (exitDoorOpen) -2.05f else 0f
        if (exitDoorOpen) {
            box(0f,1.05f,-5.57f,1.7f,2.0f,0.06f,0.08f,0.42f,0.18f)
            box(0f,0.08f,-5.48f,1.45f,0.035f,0.035f,0.12f,0.65f,0.25f)
            box(-0.65f,1.25f,-5.48f,0.05f,1.25f,0.035f,0.22f,0.72f,0.32f)
            box(0.65f,1.25f,-5.48f,0.05f,1.25f,0.035f,0.22f,0.72f,0.32f)
        }
        box(gateX,1f,-5.68f,1.8f,1.95f,0.12f,if(keysCount>=3 && hasFuse) 0.12f else 0.24f,if(keysCount>=3 && hasFuse) 0.8f else 0.12f,0.18f)
        // Small status lamps on the gate frame make puzzle progress readable from a distance.
        val gateReady = keysCount >= 3 && hasFuse
        val lampPulse = if ((System.nanoTime()/350000000L)%2L==0L) 1f else 0.45f
        box(-0.78f,1.88f,-5.48f,0.12f,0.10f,0.035f,if(gateReady) 0.08f else lampPulse*0.8f,if(gateReady) 0.9f else 0.06f,0.04f)
        box(0.78f,1.88f,-5.48f,0.12f,0.10f,0.035f,if(gateReady) 0.08f else lampPulse*0.8f,if(gateReady) 0.9f else 0.06f,0.04f)
        if (flashlightOn && !hidden) {
            // The flashlight becomes unstable both on low charge and when the monster is very close.
            val lowBatteryFlicker = flashlightBattery < 22f && (System.nanoTime() / 85000000L) % 5L == 0L
            val monsterFlicker = dangerDistance < 2.7f && (System.nanoTime() / 115000000L) % 4L == 0L
            if (!lowBatteryFlicker && !monsterFlicker) {
                val beamPower = if (flashlightBattery < 12f) 0.28f else if (flashlightBattery < 28f) 0.38f else 0.48f
                box(px,0.15f,pz-1.05f,0.25f,0.025f,1.6f,beamPower,beamPower*0.92f,beamPower*0.48f)
            }
        }
    }

    private fun update(dt: Float) {
        val yaw=Math.toRadians(cameraYaw.toDouble())
        val cy=kotlin.math.cos(yaw).toFloat(); val sy=kotlin.math.sin(yaw).toFloat()
        val moving=kotlin.math.abs(moveX)+kotlin.math.abs(moveZ)>0.08f
        val sprint=moving && stamina>1f && (sprintHeld || kotlin.math.abs(moveX)+kotlin.math.abs(moveZ)>1.25f)
        val speed=if(sprint) 3.25f else 2.3f
        stamina=if(sprint) (stamina-26f*dt).coerceAtLeast(0f) else (stamina+13f*dt).coerceAtMost(100f)
        val noise=if(sprint) 1f else if(moving) 0.45f else 0f
        val dx=(moveX*cy-moveZ*sy)*speed*dt; val dz=(moveZ*cy+moveX*sy)*speed*dt
        if(!hidden && !gameWon) {
            val nx=(px+dx).coerceIn(-7.55f,7.55f); val nz=(pz+dz).coerceIn(-5.72f,5.72f)
            val furnitureBlocked=(nx < -2.0f && nx > -3.55f && nz > -1.25f && nz < 0.45f) || (nx > 1.75f && nx < 3.25f && nz > 1.25f && nz < 2.75f) || (nx > -0.95f && nx < 0.95f && nz > -4.35f && nz < -3.25f)
            // Closed doors stop the player crossing the side-hall wall; open doors connect the rooms.
            val crossesLeftWall = (px > -4f && nx <= -4f) || (px < -4f && nx >= -4f)
            val crossesRightWall = (px < 4f && nx >= 4f) || (px > 4f && nx <= 4f)
            val doorZ = (nz > -2.75f && nz < -1.25f)
            val doorZ2 = (nz > 1.25f && nz < 2.75f)
            val blockedDoor = (crossesLeftWall && ((doorZ && !bedroomDoorOpen) || (doorZ2 && !storageDoorOpen) || (!doorZ && !doorZ2))) ||
                (crossesRightWall && ((doorZ && !kitchenDoorOpen) || (doorZ2 && !sideRoomDoorOpen) || (!doorZ && !doorZ2)))
            val outerFurniture = (nx < -5.25f && nx > -7.2f && nz > -4.8f && nz < -3.0f) || (nx > 5.2f && nx < 6.9f && nz > -4.8f && nz < -3.0f) || (nx > 5.0f && nx < 7.0f && nz > 3.0f && nz < 4.7f)
            if(!furnitureBlocked && !blockedDoor && !outerFurniture) { px=nx; pz=nz }
        }
        if(!hidden && !gameWon) {
            hideDiscoveryTimer = 0f
            val vx=px-mx; val vz=pz-mz; val d=sqrt(vx*vx+vz*vz).coerceAtLeast(0.001f)
            patrolClock+=dt
            // AI senses sprinting farther away than walking. A close player is spotted even when still.
            // Alpha 5: running is loud and gives away the player's position from farther away.
            // Walking is quieter, but can still be heard when the monster is nearby.
            val sprintNoise = moving && sprint && d < 9.5f
            val walkNoise = moving && !sprint && d < 3.2f
            val spotted = d < 4.6f
            if (spotted || sprintNoise || walkNoise) {
                lastKnownX=px; lastKnownZ=pz
                lastSeenTimer=if(spotted) 2.2f else 0.0f
                alertTimer=if(spotted) 2.8f else if(sprintNoise) 5.0f else 2.5f
                investigateTimer=0f
                searchTargetTimer=0f
            } else {
                lastSeenTimer=(lastSeenTimer-dt).coerceAtLeast(0f)
                alertTimer=(alertTimer-dt).coerceAtLeast(0f)
            }
            if(alertTimer>0f && !hidden) {
                monsterState=if(lastSeenTimer>0f) "شافك وبيطاردك!" else "بيجري ناحية آخر صوت سمعه"
                val chaseSpeed=if(lastSeenTimer>0f) 1.48f else 1.22f
                moveMonsterToward(lastKnownX,lastKnownZ,chaseSpeed,dt)
            } else {
                // Search several nearby points around the last known position instead of standing still.
                if(investigateTimer<8.0f) {
                    if(searchTargetTimer<=0f) {
                        val phase=searchPhase
                        searchTargetX=(lastKnownX+kotlin.math.cos(phase)*1.55f).coerceIn(-7.0f,7.0f)
                        searchTargetZ=(lastKnownZ+kotlin.math.sin(phase)*1.55f).coerceIn(-5.0f,5.0f)
                        searchPhase+=1.35f
                        searchTargetTimer=1.9f
                    }
                    val sx=searchTargetX-mx; val sz=searchTargetZ-mz
                    val sd=sqrt(sx*sx+sz*sz)
                    if(sd>0.28f) {
                        monsterState="بيفتش حوالين آخر مكان شافك فيه"
                        moveMonsterToward(searchTargetX,searchTargetZ,0.82f,dt)
                    } else {
                        searchTargetTimer=0f
                        investigateTimer+=dt
                        monsterState="بيفتش في المكان..."
                    }
                    searchTargetTimer-=dt
                } else {
                    // Small patrol loop with stable targets, rather than jittering in place.
                    if(kotlin.math.abs(mx-patrolTargetX)<0.45f && kotlin.math.abs(mz-patrolTargetZ)<0.45f || patrolClock>14f) {
                        patrolTargetX=when((patrolClock.toInt()/4)%4) { 0 -> -5.5f; 1 -> 5.5f; 2 -> 5.5f; else -> -5.5f }
                        patrolTargetZ=when((patrolClock.toInt()/4)%4) { 0 -> -3.7f; 1 -> -3.7f; 2 -> 3.7f; else -> 3.7f }
                    }
                    monsterState="بيتجول ويدور عليك"
                    moveMonsterToward(patrolTargetX,patrolTargetZ,0.48f,dt)
                }
            }
            // The monster can push open an interior door when actively chasing or investigating.
            // It only opens a door from the correct side of the partition, preventing random toggling.
            if (alertTimer > 0.15f || investigateTimer < 8.0f) {
                if (kotlin.math.abs(mx + 4f) < 0.48f) {
                    if (mz > -2.8f && mz < -1.2f) bedroomDoorOpen = true
                    if (mz > 1.2f && mz < 2.8f) storageDoorOpen = true
                }
                if (kotlin.math.abs(mx - 4f) < 0.48f) {
                    if (mz > -2.8f && mz < -1.2f) kitchenDoorOpen = true
                    if (mz > 1.2f && mz < 2.8f) sideRoomDoorOpen = true
                }
            }
            val cx=px-mx; val cz=pz-mz
            if(sqrt(cx*cx+cz*cz)<0.72f && !hidden) { gameOver=true; statusText="مسكتك العجوز! اضغط إعادة المحاولة"; monsterState="أمسكت بك" }
                } else if(hidden) {
            monsterState="بيدور عليك"
            // If the monster reaches the hiding area, it lingers and searches before leaving.
            val hx=px-mx; val hz=pz-mz
            if(hx*hx+hz*hz<2.8f) {
                investigateTimer=(investigateTimer+dt).coerceAtMost(6f)
                hideDiscoveryTimer += dt
                mx+=kotlin.math.sin(patrolClock*1.8f)*0.18f*dt
                mz+=kotlin.math.cos(patrolClock*1.6f)*0.18f*dt
                if(hideDiscoveryTimer >= 4.5f) {
                    gameOver=true
                    statusText="العجوز اكتشفت مكان اختبائك! اضغط إعادة المحاولة"
                    monsterState="اكتشفت مخبأك!"
                }
            } else {
                hideDiscoveryTimer = (hideDiscoveryTimer - dt * 1.5f).coerceAtLeast(0f)
                mx+=(lastKnownX-mx)*dt*0.08f
                mz+=(lastKnownZ-mz)*dt*0.08f
            }
        }
        for(i in keys.indices) if(!collected[i]) { val kx=px-keys[i][0]; val kz=pz-keys[i][1]; if(kx*kx+kz*kz<0.55f) collected[i]=true }
        keysCount=collected.count { it }
        if(exitDoorOpen && pz < -5.15f && kotlin.math.abs(px)<1.15f) { gameWon=true; monsterState="نجوت!" }
        val nearHideSpot = hideSpots.any { val hx=px-it[0]; val hz=pz-it[1]; hx*hx+hz*hz<1.65f }
        val nearCabinet = (px+2.8f)*(px+2.8f)+(pz+0.4f)*(pz+0.4f)<2.2f
        val nearBatteryPack = !batteryPackCollected && (px-1.15f)*(px-1.15f)+(pz+1.85f)*(pz+1.85f)<1.45f
        // Recalculate the monster distance here: dangerDistance is local to the render frame.
        val warningDx = px - mx
        val warningDz = pz - mz
        val warningDistance = sqrt(warningDx * warningDx + warningDz * warningDz)
        val dangerWarning = if (!hidden && !gameWon && !gameOver && warningDistance < 2.8f) "⚠ العجوز قريبة جدًا!" else if (!hidden && !gameWon && !gameOver && warningDistance < 4.2f) "في حركة قريبة..." else ""
        val nearRoomDoor = kotlin.math.abs(kotlin.math.abs(px)-4f)<1.15f && (kotlin.math.abs(pz+2f)<1.25f || kotlin.math.abs(pz-2f)<1.25f)
        val mainStatus = when { gameOver -> "مسكتك العجوز! اضغط إعادة المحاولة"; gameWon -> "نجحت في الهروب!"; hidden -> "مختبئ — العجوز تبحث عنك"; nearCabinet && !drawerOpen -> "درج قريب • اضغط تفاعل للبحث"; nearCabinet && drawerOpen && !hasFuse -> "وجدت قطعة كهربائية! خذها بالتفاعل"; nearBatteryPack -> "بطارية احتياطية قريبة • اضغط تفاعل"; keysCount>=3 && hasFuse && pz < -3.8f -> "اضغط تفاعل لفتح بوابة الهروب"; keysCount>=3 && !hasFuse -> "المفاتيح كاملة! ابحث عن القطعة الكهربائية في الدرج"; keysCount>=3 && hasFuse -> "المفاتيح والقطعة جاهزان — اذهب إلى بوابة الهروب"; nearHideSpot -> "مكان اختباء قريب • اضغط اختباء"; nearRoomDoor -> "باب غرفة قريب • اضغط تفاعل للفتح أو الإغلاق"; px < -4.1f -> "غرفة النوم / المخزن • المفاتيح: $keysCount/3"; px > 4.1f -> "المطبخ / الغرفة الداخلية • المفاتيح: $keysCount/3"; else -> "المفاتيح: $keysCount/3 • $monsterState" }
        val batteryLabel = "البطارية: ${flashlightBattery.toInt()}%"
        statusText = listOf(mainStatus, dangerWarning, batteryLabel).filter { it.isNotBlank() }.joinToString(" • ")
    }

    fun interact() {
        if (gameOver || gameWon) return
        // Door interaction is proximity-based, so it works with the existing single interact button.
        if (kotlin.math.abs(kotlin.math.abs(px) - 4.0f) < 1.15f && kotlin.math.abs(pz + 2.0f) < 1.25f) {
            if (px < 0f) bedroomDoorOpen = !bedroomDoorOpen else kitchenDoorOpen = !kitchenDoorOpen
            statusText = if (px < 0f) (if (bedroomDoorOpen) "باب غرفة النوم اتفتح" else "باب غرفة النوم اتقفل") else (if (kitchenDoorOpen) "باب المطبخ اتفتح" else "باب المطبخ اتقفل")
            return
        }
        if (kotlin.math.abs(kotlin.math.abs(px) - 4.0f) < 1.15f && kotlin.math.abs(pz - 2.0f) < 1.25f) {
            if (px < 0f) storageDoorOpen = !storageDoorOpen else sideRoomDoorOpen = !sideRoomDoorOpen
            statusText = if (px < 0f) (if (storageDoorOpen) "باب المخزن اتفتح" else "باب المخزن اتقفل") else (if (sideRoomDoorOpen) "باب الغرفة الداخلية اتفتح" else "باب الغرفة الداخلية اتقفل")
            return
        }
        if (kotlin.math.abs(px - 6.0f) < 1.3f && kotlin.math.abs(pz) < 1.15f) {
            sideRoomDoorOpen = !sideRoomDoorOpen
            statusText = if (sideRoomDoorOpen) "فتحت الباب الداخلي بين الغرف" else "قفلت الباب الداخلي"
            return
        }
        val batteryDistance=(px-1.15f)*(px-1.15f)+(pz+1.85f)*(pz+1.85f)
        if(!batteryPackCollected && batteryDistance<1.45f) {
            batteryPackCollected=true
            flashlightBattery=(flashlightBattery+42f).coerceAtMost(100f)
            statusText="جمعت بطارية احتياطية! شحن الكشاف ${flashlightBattery.toInt()}%"
            return
        }
        val cabinetDistance=(px+2.8f)*(px+2.8f)+(pz+0.4f)*(pz+0.4f)
        if(cabinetDistance<2.2f) {
            if(!drawerOpen) { drawerOpen=true; statusText="فتحت الدرج — ابحث عن الأداة"; return }
            if(!hasFuse) { hasFuse=true; statusText="جمعت القطعة الكهربائية!"; return }
        }
        val nearExit = pz < -3.8f && kotlin.math.abs(px) < 1.6f
        if (!nearExit) { statusText = "اقترب من الباب أو ابحث عن المفاتيح"; return }
        if (keysCount < 3) { statusText = "الباب مقفول — تحتاج 3 مفاتيح ($keysCount/3)"; return }
        if (!hasFuse) { statusText = "لوحة الباب بلا كهرباء — ابحث عن القطعة الكهربائية في الدرج"; return }
        exitDoorOpen = true
        statusText = "انفتح الباب! اركض للخارج بسرعة"
    }

    fun setSprinting(on: Boolean) { sprintHeld = on }
    @Volatile private var sprintHeld = false

    fun toggleHide() {
        if(hidden) { hidden=false; return }
        if(hideSpots.any { val dx=px-it[0]; val dz=pz-it[1]; dx*dx+dz*dz<1.65f }) hidden=true
    }

    fun restartGame() {
        px=0f; pz=2.8f; mx=0f; mz=-3.2f
        for(i in collected.indices) collected[i]=false
        hidden=false; hideDiscoveryTimer=0f; flashlightOn=true; flashlightBattery=100f; stamina=100f; keysCount=0; gameWon=false; exitDoorOpen=false; drawerOpen=false; hasFuse=false; batteryPackCollected=false; bedroomDoorOpen=false; kitchenDoorOpen=false; storageDoorOpen=false; sideRoomDoorOpen=false; gameOver=false; alertTimer=0f; investigateTimer=0f; lastKnownX=0f; lastKnownZ=0f; searchPhase=0f; searchTargetTimer=0f; searchTargetX=0f; searchTargetZ=0f; lastSeenTimer=0f; patrolTargetX=-5.5f; patrolTargetZ=0f; patrolClock=0f; sprintHeld=false
        statusText="اجمع المفاتيح والقطعة الكهربائية وابحث عن بطارية احتياطية — خلي بالك من صوت خطواتك"
    }

    /** Moves the monster in small axis-separated steps so furniture and closed room doors are less likely to trap it. */
    private fun moveMonsterToward(tx: Float, tz: Float, speed: Float, dt: Float) {
        val dx=tx-mx; val dz=tz-mz
        val d=sqrt(dx*dx+dz*dz).coerceAtLeast(0.001f)
        val step=(speed*dt).coerceAtMost(d)
        val nx=(mx+dx/d*step).coerceIn(-7.25f,7.25f)
        val nz=(mz+dz/d*step).coerceIn(-5.35f,5.35f)
        fun blocked(x:Float,z:Float):Boolean {
            val furniture=(x < -2.0f && x > -3.55f && z > -1.25f && z < 0.45f) ||
                (x > 1.75f && x < 3.25f && z > 1.25f && z < 2.75f) ||
                (x > -0.95f && x < 0.95f && z > -4.35f && z < -3.25f)
            val doorBlock=(kotlin.math.abs(x+4f)<0.22f && ((z>-2.75f && z< -1.25f && !bedroomDoorOpen) || (z>1.25f && z<2.75f && !storageDoorOpen))) ||
                (kotlin.math.abs(x-4f)<0.22f && ((z>-2.75f && z< -1.25f && !kitchenDoorOpen) || (z>1.25f && z<2.75f && !sideRoomDoorOpen)))
            return furniture || doorBlock
        }
        // Try the direct path first, then slide along one axis to escape corners.
        if(!blocked(nx,nz)) { mx=nx; mz=nz }
        else if(!blocked(nx,mz)) mx=nx
        else if(!blocked(mx,nz)) mz=nz
        else {
            val sideX=(mx+(-dz/d)*step).coerceIn(-7.25f,7.25f)
            val sideZ=(mz+(dx/d)*step).coerceIn(-5.35f,5.35f)
            if(!blocked(sideX,mz)) mx=sideX else if(!blocked(mx,sideZ)) mz=sideZ
        }
    }

    private fun box(x:Float,y:Float,z:Float,sx:Float,sy:Float,sz:Float,r:Float,g:Float,b:Float) {
        Matrix.setIdentityM(model,0); Matrix.translateM(model,0,x,y,z); Matrix.scaleM(model,0,sx,sy,sz)
        Matrix.multiplyMM(mvp,0,vp,0,model,0)
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(mvpHandle,1,false,mvp,0)
        GLES20.glUniform4f(colorHandle,r,g,b,1f)
        vertices.position(0); GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle,3,GLES20.GL_FLOAT,false,0,vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36)
        GLES20.glDisableVertexAttribArray(posHandle)
    }
}
