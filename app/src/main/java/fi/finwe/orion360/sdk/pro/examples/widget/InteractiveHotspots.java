/*
 * Copyright (c) 2016, Finwe Ltd. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation and/or
 *    other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package fi.finwe.orion360.sdk.pro.examples.widget;

import android.os.Bundle;
import android.util.Log;

import fi.finwe.orion360.sdk.pro.OrionActivity;
import fi.finwe.orion360.sdk.pro.OrionScene;
import fi.finwe.orion360.sdk.pro.OrionViewport;
import fi.finwe.orion360.sdk.pro.controller.RotationAligner;
import fi.finwe.orion360.sdk.pro.controller.TouchPincher;
import fi.finwe.orion360.sdk.pro.controller.TouchRotater;
import fi.finwe.orion360.sdk.pro.examples.MainMenu;
import fi.finwe.orion360.sdk.pro.examples.R;
import fi.finwe.orion360.sdk.pro.item.OrionCamera;
import fi.finwe.orion360.sdk.pro.item.OrionPanorama;
import fi.finwe.orion360.sdk.pro.source.OrionTexture;
import fi.finwe.orion360.sdk.pro.view.OrionView;
import fi.finwe.orion360.sdk.pro.widget.OrionWidget;
import fi.finwe.orion360.sdk.pro.widget.SelectablePointerIcon;
import fi.finwe.util.ContextUtil;

/**
 * An example of a hotspot, an icon that can be selected simply by pointing / looking at it.
 * <p/>
 * Features:
 * <ul>
 * <li>Plays one hard-coded full spherical (360x180) equirectangular video
 * <li>Creates a fullscreen view locked to landscape orientation
 * <li>Auto-starts playback on load and stops when playback is completed
 * <li>Renders the video using standard rectilinear projection
 * <li>Allows navigation with touch & movement sensors (if supported by HW) as follows:
 * <ul>
 * <li>Panning (gyro or swipe)
 * <li>Zooming (pinch)
 * <li>Tilting (pinch rotate)
 * </ul>
 * <li>Auto Horizon Aligner (AHL) feature straightens the horizon</li>
 * </ul>
 */
public class InteractiveHotspots extends OrionActivity {

    /** The distance (from the scene origin) where hotspots will be drawn (sphere = 1.0f). */
    protected final static float HOTSPOT_LAYER_RADIUS = 0.9f;

    /** The size of the hotspots, as a scaling factor relative to image asset size (100% = 1.0f). */
    protected final static float HOTSPOT_SCALE_FACTOR = 0.21f;

    /** When focused the hotspot will grow larger up to this scale factor (no effect = 1.0f). */
    protected final static float HOTSPOT_SCALE_FOCUSED_MAX = 1.5f * HOTSPOT_SCALE_FACTOR;

    /** When focused the hotspot selection will trigger after this many frames rendered (~60FPS). */
    protected final static int HOTSPOT_TRIGGER_DELAY_IN_FRAMES = 120;

    /** The Android view where our 3D scene will be rendered to. */
    protected OrionView mView;

    /** The 3D scene where our panorama sphere and hotspots will be added to. */
    protected OrionScene mScene;

    /** The panorama sphere where our video texture will be mapped to. */
    protected OrionPanorama mPanorama;

    /** The video texture where our decoded video frames will be updated to. */
    protected OrionTexture mPanoramaTexture;

    /** The camera which will project our 3D scene to a 2D (view) surface. */
    protected OrionCamera mCamera;

    /** The widget that will handle our touch gestures. */
    protected TouchControllerWidget mTouchController;

    /** The widget that will act as the 'front' hotspot. */
    protected SelectablePointerIcon mHotspotFront;

    /** The widget that will act as the 'left' hotspot. */
    protected SelectablePointerIcon mHotspotLeft;

    /** The widget that will act as the 'right' hotspot. */
    protected SelectablePointerIcon mHotspotRight;

    /** The widget that will act as the 'back' hotspot. */
    protected SelectablePointerIcon mHotspotBack;


    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        // Create a new scene. This represents a 3D world where various objects can be placed.
        mScene = new OrionScene();

        // Bind sensor fusion as a controller. This will make it available for scene objects.
        mScene.bindController(mOrionContext.getSensorFusion());

        // Create a new panorama. This is a 3D object that will represent a spherical video/image.
        mPanorama = new OrionPanorama();

        // Create a new video (or image) texture from a video (or image) source URI.
        mPanoramaTexture = OrionTexture.createTextureFromURI(this,
                MainMenu.PRIVATE_EXPANSION_FILES_PATH + MainMenu.TEST_VIDEO_FILE_MQ);

        // Pause video playback until user triggers one of the 'Start' hotspots.
        mPanoramaTexture.pause();

        // Dim video until playback starts, helps to make end-user focus to 'Start' hotspots.
        mPanorama.setAmpAlpha(0.2f);

        // Bind the panorama texture to the panorama object. Here we assume full spherical
        // equirectangular monoscopic source, and wrap the complete texture around the sphere.
        // If you have stereoscopic content or doughnut shape video, use other method variants.
        mPanorama.bindTextureFull(0, mPanoramaTexture);

        // Bind the panorama to the scene. This will make it part of our 3D world.
        mScene.bindSceneItem(mPanorama);

        // Create a new camera. This will become the end-user's eyes into the 3D world.
        mCamera = new OrionCamera();

        // Bind camera as a controllable to sensor fusion. This will let sensors rotate the camera.
        mOrionContext.getSensorFusion().bindControllable(mCamera);

        // Create a new touch controller widget (convenience class), and let it control our camera.
        mTouchController = new TouchControllerWidget(mCamera);

        // Bind the touch controller widget to the scene. This will make it functional in the scene.
        mScene.bindWidget(mTouchController);

        // Create 'front' hotspot (yaw = 0) and bind it to the scene.
        mHotspotFront = createStartHotSpot(0.0f, 0.0f, 0.0f);
        mScene.bindWidget(mHotspotFront);

        // Create 'left' hotspot (yaw = -90) and bind it to the scene.
        mHotspotLeft = createStartHotSpot(-90.0f, 0.0f, 0.0f);
        mScene.bindWidget(mHotspotLeft);

        // Create 'right' hotspot (yaw = 90) and bind it to the scene.
        mHotspotRight = createStartHotSpot(90.0f, 0.0f, 0.0f);
        mScene.bindWidget(mHotspotRight);

        // Create 'back' hotspot (yaw = 180) and bind it to the scene.
        mHotspotBack = createStartHotSpot(180.0f, 0.0f, 0.0f);
        mScene.bindWidget(mHotspotBack);

        // Find Orion360 view from the XML layout. This is an Android view where we render content.
        mView = (OrionView)findViewById(R.id.orion_view);

        // Bind the scene to the view. This is the 3D world that we will be rendering to this view.
        mView.bindDefaultScene(mScene);

        // Bind the camera to the view. We will look into the 3D world through this camera.
        mView.bindDefaultCamera(mCamera);

        // The view can be divided into one or more viewports. For example, in VR mode we have one
        // viewport per eye. Here we fill the complete view with one (landscape) viewport.
        mView.bindViewports(OrionViewport.VIEWPORT_CONFIG_FULL,
                OrionViewport.CoordinateType.FIXED_LANDSCAPE);
	}

    /**
     * Creates a 'Start' hotspot at the direction given as Euler angles.
     *
     * @param yawDeg The yaw angle in degrees.
     * @param pitchDeg The pitch angle in degrees.
     * @param rollDeg The roll angle in degrees.
     */
    protected SelectablePointerIcon createStartHotSpot(float yawDeg, float pitchDeg, float rollDeg) {

        // Create a new hotspot as a selectable pointer icon widget.
        SelectablePointerIcon hotspot = new SelectablePointerIcon();

        // Set the location of the hotspot using Euler angles.
        hotspot.setLocationPolarZXYDeg(yawDeg, pitchDeg, rollDeg, HOTSPOT_LAYER_RADIUS);

        // Set the size of the hotspot as a scale factor relative to image asset size.
        hotspot.setScale(HOTSPOT_SCALE_FACTOR, HOTSPOT_SCALE_FOCUSED_MAX);

        // Set the icon for the hotspot as a PNG image. */
        hotspot.getIcon().bindTexture(OrionTexture.createTextureFromURI(this,
                getString(R.string.asset_hotspot_start)));

        // Adjust hotspot icon's alpha value to make it a little bit transparent.
        hotspot.getIcon().setAmpAlpha(0.90f);

        // Set the pie (selection) animation for the hotspot as a PNG image.
        // A pie animation image is drawn as a pie chart whose angle grows steadily, resulting
        // to a clock hand type movement that is suitable for illustrating timed triggering.
        hotspot.getPieSprite().bindTexture(OrionTexture.createTextureFromURI(this,
                getString(R.string.asset_hotspot_pie)));

        // Set the pointer i.e. scene item whose nearness to watch, typically the camera.
        // This way we know if user is looking at the hotspot.
        hotspot.setPointer(mCamera);

        // When hotspot is focused, we keep counting frames until a given frame count is reached
        // and it is time to trigger the hotspot action. Notice that if end-user looks away and
        // hotspot focus is lost, we do not immediately reset the count but start decreasing it
        // slowly so end-user can focus on the hotspot again if she accidentally moved away.
        // The rendering runs at 60 framer per second (FPS), if not limited by weak hardware.
        hotspot.setSelectionTriggerFrameCount(HOTSPOT_TRIGGER_DELAY_IN_FRAMES);

        // Create a listener so that we can respond to selection events.
        // Notice that these callbacks will be run on the UI thread, not on the GL thread whose
        // job is to keep rendering at constant pace (time critical).
        hotspot.setUiThreadListener(new SelectablePointerIcon.Listener() {

            @Override
            public void onSelectionTrigger() {
                Log.d(TAG, "Hotspot triggered!");
                start();
            }

            @Override
            public void onSelectionFocusLost() {
                Log.d(TAG, "Hotspot focus lost");
            }

            @Override
            public void onSelectionFocusGained() {
                Log.d(TAG, "Hotspot focus gained");
            }

        });

        return hotspot;
    }

    /**
     * To be called when a hotspot triggers and it is time to start video playback.
     */
    protected void start() {

        // Hide (and disable) hotspots, there are not needed anymore.
        mHotspotFront.setEnabled(false);
        mHotspotLeft.setEnabled(false);
        mHotspotRight.setEnabled(false);
        mHotspotBack.setEnabled(false);

        // Make the panorama fully opaque (remove dimming).
        mPanorama.setAmpAlpha(1.0f);

        // Start video playback.
        mPanoramaTexture.play();

    }


    /**
     * Convenience class for configuring typical touch control logic.
     */
    public class TouchControllerWidget implements OrionWidget {

        /** The camera that will be controlled by this widget. */
        private OrionCamera mCamera;

        /** Touch pinch-to-zoom/pinch-to-rotate gesture handler. */
        private TouchPincher mTouchPincher;

        /** Touch drag-to-pan gesture handler. */
        private TouchRotater mTouchRotater;

        /** Rotation aligner keeps the horizon straight at all times. */
        private RotationAligner mRotationAligner;


        /**
         * Constructs the widget.
         *
         * @param camera The camera to be controlled by this widget.
         */
        TouchControllerWidget(OrionCamera camera) {

            // Keep a reference to the camera that we control.
            mCamera = camera;

            // Create pinch-to-zoom/pinch-to-rotate handler.
            mTouchPincher = new TouchPincher();
            mTouchPincher.setMinimumDistanceDp(mOrionContext.getActivity(), 20);
            mTouchPincher.bindControllable(mCamera, OrionCamera.VAR_FLOAT1_ZOOM);

            // Create drag-to-pan handler.
            mTouchRotater = new TouchRotater();
            mTouchRotater.bindControllable(mCamera);

            // Create the rotation aligner, responsible for rotating the view so that the horizon
            // aligns with the user's real-life horizon when the user is not looking up or down.
            mRotationAligner = new RotationAligner();
            mRotationAligner.setDeviceAlignZ(-ContextUtil.getDisplayRotationDegreesFromNatural(
                    mOrionContext.getActivity()));
            mRotationAligner.bindControllable(mCamera);

            // Rotation aligner needs sensor fusion data in order to do its job.
            mOrionContext.getSensorFusion().bindControllable(mRotationAligner);
        }

        @Override
        public void onBindWidget(OrionScene scene) {
            // When widget is bound to scene, bind the controllers to it to make them functional.
            scene.bindController(mTouchPincher);
            scene.bindController(mTouchRotater);
            scene.bindController(mRotationAligner);
        }

        @Override
        public void onReleaseWidget(OrionScene scene) {
            // When widget is released from scene, release the controllers as well.
            scene.releaseController(mTouchPincher);
            scene.releaseController(mTouchRotater);
            scene.releaseController(mRotationAligner);
        }
    }
}
