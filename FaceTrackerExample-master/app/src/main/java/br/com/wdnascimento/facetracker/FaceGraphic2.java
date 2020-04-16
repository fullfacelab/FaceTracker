/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package br.com.wdnascimento.facetracker;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.LinearLayout;

import com.google.android.gms.vision.face.Face;

import br.com.wdnascimento.facetracker.camera.GraphicOverlay;

/**
 * Graphic instance for rendering face position, orientation, and landmarks within an associated
 * graphic overlay view.
 */
public class FaceGraphic2 extends GraphicOverlay.Graphic {
    private LinearLayout faceMask;
    private float BOX_ROUND;
    private float BOX_FOTO0;
    private float BOX_FOTO1;

    private volatile Face mFace;
    private int mFaceId;
    private float mFaceHappiness;
    private boolean detect;

    /**
     * Constructor
     *
     * @param context
     * @param overlay
     */
    public FaceGraphic2(Context context, GraphicOverlay overlay) {
        super(overlay);

        // get Dimension
        BOX_ROUND = (float) context.getResources().getInteger(R.integer.box_round);
        BOX_FOTO0 = (float) context.getResources().getInteger(R.integer.box_foto0);
        BOX_FOTO1 = (float) context.getResources().getInteger(R.integer.box_foto1);
    }


    public void setId(int id) {
        mFaceId = id;
    }
    //faceid

    /**
     * Updates the face instance from the detection of the most recent frame.  Invalidates the
     * relevant portions of the overlay to trigger a redraw.
     */
    public void updateFace(Face face) {
        mFace = face;
        postInvalidate();
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        // inicia variaveis
        int resource = R.mipmap.ic_fullface_background;

        // inicia o Detect
        detect = false;

        // center window
        float xCenter    = ((float) canvas.getWidth()  / 2.0f);
        float yCenter    = ((float) canvas.getHeight() / 2.0f);
        float leftMask   = xCenter - BOX_FOTO0;
        float topMask    = yCenter - BOX_FOTO0;
        float rightMask  = xCenter + BOX_FOTO0;
        float bottomMask = yCenter + BOX_FOTO1;

        // Face face = mFace;
        if (mFace != null) {
            // Draws a circle at the position of the detected face, with the face's track id below.
            float x = translateX(mFace.getPosition().x + mFace.getWidth()  / 2);
            float y = translateY(mFace.getPosition().y + mFace.getHeight() / 2);

            // Draws a bounding box around the face.
            float left    = x - BOX_ROUND;
            float top     = y - BOX_ROUND;
            float right   = x + BOX_ROUND;
            float bottom  = y + BOX_ROUND;

            // verifica a posicao do rosto
            if ((left >= leftMask) && (top >= topMask) && (right <= rightMask) && (bottom <= bottomMask)) {
                resource = R.mipmap.ic_fullface_background_on;
                detect   = true;
            }
            else {
                resource = R.mipmap.ic_fullface_background;
                detect   = false;
            }
        }

        // set Background
        faceMask.setBackgroundResource(resource);
    }

    /**
     * Metodo Setter´s
     *
     * @param faceMask
     */
    public void setFaceMask(LinearLayout faceMask) { this.faceMask = faceMask; }

    /**
     * Metodo Getter´s
     *
     * @return
     */
    public boolean isDetect() { return detect; }
}