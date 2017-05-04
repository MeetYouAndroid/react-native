package com.facebook.react.views.imagehelper;

import com.facebook.yoga.YogaConstants;

/**
 * Created by Linhh on 17/5/4.
 */

public class RoundedCornerHelper {

    public static void cornerRadii(float[] computedCorners, float[] mBorderCornerRadii, float mBorderRadius) {
        float defaultBorderRadius = !YogaConstants.isUndefined(mBorderRadius) ? mBorderRadius : 0;

        computedCorners[0] = mBorderCornerRadii != null && !YogaConstants.isUndefined(mBorderCornerRadii[0]) ? mBorderCornerRadii[0] : defaultBorderRadius;
        computedCorners[1] = mBorderCornerRadii != null && !YogaConstants.isUndefined(mBorderCornerRadii[1]) ? mBorderCornerRadii[1] : defaultBorderRadius;
        computedCorners[2] = mBorderCornerRadii != null && !YogaConstants.isUndefined(mBorderCornerRadii[2]) ? mBorderCornerRadii[2] : defaultBorderRadius;
        computedCorners[3] = mBorderCornerRadii != null && !YogaConstants.isUndefined(mBorderCornerRadii[3]) ? mBorderCornerRadii[3] : defaultBorderRadius;
    }
}
