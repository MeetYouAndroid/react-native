package com.facebook.react.views.image;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.imagepipeline.request.BasePostprocessor;
import com.facebook.react.uimanager.FloatUtil;
import com.facebook.react.views.imagehelper.RoundedCornerHelper;
import com.facebook.yoga.YogaConstants;

import javax.annotation.Nullable;

/**
 * Created by Linhh on 17/5/4.
 */

class RoundedCornerPostprocessor extends BasePostprocessor {

    private ScalingUtils.ScaleType mScaleType = ImageResizeMode.defaultValue();
    private static final Matrix sMatrix = new Matrix();
    private static final Matrix sInverse = new Matrix();

    private static float[] sComputedCornerRadii = new float[4];
    private @Nullable
    float[] mBorderCornerRadii;

    private float mBorderRadius;

    public void setBorderRadius(float borderRadius){
        mBorderRadius = borderRadius;
    }

    public void setScaleType(ScalingUtils.ScaleType scaleType){
        mScaleType = scaleType;
    }

    public void setBorderCornerRadii(float[] borderCornerRadii){
        mBorderCornerRadii = borderCornerRadii;
    }

    void getRadii(Bitmap source, float[] computedCornerRadii, float[] mappedRadii) {
        mScaleType.getTransform(
                sMatrix,
                new Rect(0, 0, source.getWidth(), source.getHeight()),
                source.getWidth(),
                source.getHeight(),
                0.0f,
                0.0f);
        sMatrix.invert(sInverse);

        mappedRadii[0] = sInverse.mapRadius(computedCornerRadii[0]);
        mappedRadii[1] = mappedRadii[0];

        mappedRadii[2] = sInverse.mapRadius(computedCornerRadii[1]);
        mappedRadii[3] = mappedRadii[2];

        mappedRadii[4] = sInverse.mapRadius(computedCornerRadii[2]);
        mappedRadii[5] = mappedRadii[4];

        mappedRadii[6] = sInverse.mapRadius(computedCornerRadii[3]);
        mappedRadii[7] = mappedRadii[6];
    }

    public float[] getComputedCornerRadii(){
        RoundedCornerHelper.cornerRadii(sComputedCornerRadii,mBorderCornerRadii,mBorderRadius);
        return sComputedCornerRadii;
    }

    @Override
    public void process(Bitmap output, Bitmap source) {
        sComputedCornerRadii = getComputedCornerRadii();
        output.setHasAlpha(true);
        if (FloatUtil.floatsEqual(sComputedCornerRadii[0], 0f) &&
                FloatUtil.floatsEqual(sComputedCornerRadii[1], 0f) &&
                FloatUtil.floatsEqual(sComputedCornerRadii[2], 0f) &&
                FloatUtil.floatsEqual(sComputedCornerRadii[3], 0f)) {
            super.process(output, source);
            return;
        }
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        Canvas canvas = new Canvas(output);

        float[] radii = new float[8];

        getRadii(source, sComputedCornerRadii, radii);

        Path pathForBorderRadius = new Path();

        pathForBorderRadius.addRoundRect(
                new RectF(0, 0, source.getWidth(), source.getHeight()),
                radii,
                Path.Direction.CW);

        canvas.drawPath(pathForBorderRadius, paint);
    }
}
