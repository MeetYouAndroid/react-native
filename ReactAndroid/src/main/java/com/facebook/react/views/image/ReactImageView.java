/**
 * Copyright (c) 2015-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.react.views.image;

import javax.annotation.Nullable;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.widget.Toast;

import com.facebook.common.util.UriUtil;
import com.facebook.react.common.build.ReactBuildConfig;
import com.facebook.yoga.YogaConstants;
import com.facebook.drawee.controller.AbstractDraweeControllerBuilder;
import com.facebook.drawee.controller.BaseControllerListener;
import com.facebook.drawee.controller.ControllerListener;
import com.facebook.drawee.controller.ForwardingControllerListener;
import com.facebook.drawee.drawable.AutoRotateDrawable;
import com.facebook.drawee.drawable.ScalingUtils;
import com.facebook.drawee.generic.GenericDraweeHierarchy;
import com.facebook.drawee.generic.GenericDraweeHierarchyBuilder;
import com.facebook.drawee.generic.RoundingParams;
import com.facebook.drawee.view.GenericDraweeView;
import com.facebook.imagepipeline.common.ResizeOptions;
import com.facebook.imagepipeline.image.ImageInfo;
import com.facebook.imagepipeline.request.BasePostprocessor;
import com.facebook.imagepipeline.request.ImageRequest;
import com.facebook.imagepipeline.request.ImageRequestBuilder;
import com.facebook.imagepipeline.request.Postprocessor;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.uimanager.FloatUtil;
import com.facebook.react.modules.fresco.ReactNetworkImageRequest;
import com.facebook.react.uimanager.PixelUtil;
import com.facebook.react.uimanager.UIManagerModule;
import com.facebook.react.uimanager.events.EventDispatcher;
import com.facebook.react.views.imagehelper.ImageSource;
import com.facebook.react.views.imagehelper.MultiSourceHelper;
import com.facebook.react.views.imagehelper.ResourceDrawableIdHelper;
import com.facebook.react.views.imagehelper.MultiSourceHelper.MultiSourceResult;

/**
 * Wrapper class around Fresco's GenericDraweeView, enabling persisting props across multiple view
 * update and consistent processing of both static and network images.
 */
public class ReactImageView extends GenericDraweeView {

  public static final int REMOTE_IMAGE_FADE_DURATION_MS = 300;



  /*
   * Implementation note re rounded corners:
   *
   * Fresco's built-in rounded corners only work for 'cover' resize mode -
   * this is a limitation in Android itself. Fresco has a workaround for this, but
   * it requires knowing the background color.
   *
   * So for the other modes, we use a postprocessor.
   * Because the postprocessor uses a modified bitmap, that would just get cropped in
   * 'cover' mode, so we fall back to Fresco's normal implementation.
   */

  private ImageResizeMethod mResizeMethod = ImageResizeMethod.AUTO;

  private final List<ImageSource> mSources;

  private @Nullable ImageSource mImageSource;
  private @Nullable ImageSource mCachedImageSource;
  private @Nullable Drawable mLoadingImageDrawable;
  private int mBorderColor;
  private int mOverlayColor;
  private float mBorderWidth;
  private float mBorderRadius = YogaConstants.UNDEFINED;
  private @Nullable float[] mBorderCornerRadii;
  private ScalingUtils.ScaleType mScaleType;
  private boolean mIsDirty;
  private final AbstractDraweeControllerBuilder mDraweeControllerBuilder;
  private final RoundedCornerPostprocessor mRoundedCornerPostprocessor;
  private @Nullable ControllerListener mControllerListener;
  private @Nullable ControllerListener mControllerForTesting;
  private final @Nullable Object mCallerContext;
  private int mFadeDurationMs = -1;
  private boolean mProgressiveRenderingEnabled;
  private ReadableMap mHeaders;

  // We can't specify rounding in XML, so have to do so here
  private static GenericDraweeHierarchy buildHierarchy(Context context) {
    return new GenericDraweeHierarchyBuilder(context.getResources())
        .setRoundingParams(RoundingParams.fromCornersRadius(0))
        .build();
  }

  public ReactImageView(
      Context context,
      AbstractDraweeControllerBuilder draweeControllerBuilder,
      @Nullable Object callerContext) {
    super(context, buildHierarchy(context));
    mScaleType = ImageResizeMode.defaultValue();
    mDraweeControllerBuilder = draweeControllerBuilder;
    mRoundedCornerPostprocessor = new RoundedCornerPostprocessor();
    mCallerContext = callerContext;
    mSources = new LinkedList<>();
  }

  public void setShouldNotifyLoadEvents(boolean shouldNotify) {
    if (!shouldNotify) {
      mControllerListener = null;
    } else {
      final EventDispatcher mEventDispatcher = ((ReactContext) getContext()).
          getNativeModule(UIManagerModule.class).getEventDispatcher();

      mControllerListener = new BaseControllerListener<ImageInfo>() {
        @Override
        public void onSubmit(String id, Object callerContext) {
          mEventDispatcher.dispatchEvent(
              new ImageLoadEvent(getId(), ImageLoadEvent.ON_LOAD_START));
        }

        @Override
        public void onFinalImageSet(
            String id,
            @Nullable final ImageInfo imageInfo,
            @Nullable Animatable animatable) {
          if (imageInfo != null) {
            mEventDispatcher.dispatchEvent(
              new ImageLoadEvent(getId(), ImageLoadEvent.ON_LOAD,
                mImageSource.getSource(), imageInfo.getWidth(), imageInfo.getHeight()));
            mEventDispatcher.dispatchEvent(
              new ImageLoadEvent(getId(), ImageLoadEvent.ON_LOAD_END));
          }
        }

        @Override
        public void onFailure(String id, Throwable throwable) {
          mEventDispatcher.dispatchEvent(
            new ImageLoadEvent(getId(), ImageLoadEvent.ON_ERROR));
          mEventDispatcher.dispatchEvent(
            new ImageLoadEvent(getId(), ImageLoadEvent.ON_LOAD_END));
        }
      };
    }

    mIsDirty = true;
  }

  public void setBorderColor(int borderColor) {
    mBorderColor = borderColor;
    mIsDirty = true;
  }

  public void setOverlayColor(int overlayColor) {
    mOverlayColor = overlayColor;
    mIsDirty = true;
  }

  public void setBorderWidth(float borderWidth) {
    mBorderWidth = PixelUtil.toPixelFromDIP(borderWidth);
    mIsDirty = true;
  }

  public void setBorderRadius(float borderRadius) {
    if (!FloatUtil.floatsEqual(mBorderRadius, borderRadius)) {
      mBorderRadius = borderRadius;
      mIsDirty = true;
      mRoundedCornerPostprocessor.setBorderRadius(borderRadius);
    }
  }

  public void setBorderRadius(float borderRadius, int position) {
    if (mBorderCornerRadii == null) {
      mBorderCornerRadii = new float[4];
      Arrays.fill(mBorderCornerRadii, YogaConstants.UNDEFINED);
    }

    if (!FloatUtil.floatsEqual(mBorderCornerRadii[position], borderRadius)) {
      mBorderCornerRadii[position] = borderRadius;
      mIsDirty = true;
    }

    mRoundedCornerPostprocessor.setBorderCornerRadii(mBorderCornerRadii);
  }

  public void setScaleType(ScalingUtils.ScaleType scaleType) {
    mScaleType = scaleType;
    mIsDirty = true;
    mRoundedCornerPostprocessor.setScaleType(scaleType);
  }

  public void setResizeMethod(ImageResizeMethod resizeMethod) {
    mResizeMethod = resizeMethod;
    mIsDirty = true;
  }

  public void setSource(@Nullable ReadableArray sources) {
    mSources.clear();
    if (sources != null && sources.size() != 0) {
      // Optimize for the case where we have just one uri, case in which we don't need the sizes
      if (sources.size() == 1) {
        ReadableMap source = sources.getMap(0);
        String uri = source.getString("uri");
        ImageSource imageSource = new ImageSource(getContext(), uri);
        mSources.add(imageSource);
        if (Uri.EMPTY.equals(imageSource.getUri())) {
          warnImageSource(uri);
        }
      } else {
        for (int idx = 0; idx < sources.size(); idx++) {
          ReadableMap source = sources.getMap(idx);
          String uri = source.getString("uri");
          ImageSource imageSource = new ImageSource(
              getContext(),
              uri,
              source.getDouble("width"),
              source.getDouble("height"));
          mSources.add(imageSource);
          if (Uri.EMPTY.equals(imageSource.getUri())) {
            warnImageSource(uri);
          }
        }
      }
    }
    mIsDirty = true;
  }

  public void setLoadingIndicatorSource(@Nullable String name) {
    Drawable drawable = ResourceDrawableIdHelper.getInstance().getResourceDrawable(getContext(), name);
    mLoadingImageDrawable =
        drawable != null ? (Drawable) new AutoRotateDrawable(drawable, 1000) : null;
    mIsDirty = true;
  }

  public void setProgressiveRenderingEnabled(boolean enabled) {
    mProgressiveRenderingEnabled = enabled;
    // no worth marking as dirty if it already rendered..
  }

  public void setFadeDuration(int durationMs) {
    mFadeDurationMs = durationMs;
    // no worth marking as dirty if it already rendered..
  }
  
  public void setHeaders(ReadableMap headers) {
    mHeaders = headers;
  }

  public void maybeUpdateView() {
    if (!mIsDirty) {
      return;
    }

    if (hasMultipleSources() && (getWidth() <= 0 || getHeight() <= 0)) {
      // If we need to choose from multiple uris but the size is not yet set, wait for layout pass
      return;
    }

    setSourceImage();
    if (mImageSource == null) {
      return;
    }

    boolean doResize = shouldResize(mImageSource);
    if (doResize && (getWidth() <= 0 || getHeight() <= 0)) {
      // If need a resize and the size is not yet set, wait until the layout pass provides one
      return;
    }

    GenericDraweeHierarchy hierarchy = getHierarchy();
    hierarchy.setActualImageScaleType(mScaleType);

    if (mLoadingImageDrawable != null) {
      hierarchy.setPlaceholderImage(mLoadingImageDrawable, ScalingUtils.ScaleType.CENTER);
    }

    boolean usePostprocessorScaling =
        mScaleType != ScalingUtils.ScaleType.CENTER_CROP &&
        mScaleType != ScalingUtils.ScaleType.FOCUS_CROP;

    RoundingParams roundingParams = hierarchy.getRoundingParams();

    if (usePostprocessorScaling) {
      roundingParams.setCornersRadius(0);
    } else {
      float[] radiis = mRoundedCornerPostprocessor.getComputedCornerRadii();

      roundingParams.setCornersRadii(radiis[0], radiis[1], radiis[2], radiis[3]);
    }

    roundingParams.setBorder(mBorderColor, mBorderWidth);
    if (mOverlayColor != Color.TRANSPARENT) {
        roundingParams.setOverlayColor(mOverlayColor);
    } else {
        // make sure the default rounding method is used.
        roundingParams.setRoundingMethod(RoundingParams.RoundingMethod.BITMAP_ONLY);
    }
    hierarchy.setRoundingParams(roundingParams);
    hierarchy.setFadeDuration(
        mFadeDurationMs >= 0
            ? mFadeDurationMs
            : mImageSource.isResource() ? 0 : REMOTE_IMAGE_FADE_DURATION_MS);

    Postprocessor postprocessor = usePostprocessorScaling ? mRoundedCornerPostprocessor : null;

    ResizeOptions resizeOptions = doResize ? new ResizeOptions(getWidth(), getHeight()) : null;

    ImageRequestBuilder imageRequestBuilder = ImageRequestBuilder.newBuilderWithSource(mImageSource.getUri())
        .setPostprocessor(postprocessor)
        .setResizeOptions(resizeOptions)
        .setAutoRotateEnabled(true)
        .setProgressiveRenderingEnabled(mProgressiveRenderingEnabled);

    ImageRequest imageRequest = ReactNetworkImageRequest.fromBuilderWithHeaders(imageRequestBuilder, mHeaders);

    // This builder is reused
    mDraweeControllerBuilder.reset();

    mDraweeControllerBuilder
        .setAutoPlayAnimations(true)
        .setCallerContext(mCallerContext)
        .setOldController(getController())
        .setImageRequest(imageRequest);

    if (mCachedImageSource != null) {
      ImageRequest cachedImageRequest =
        ImageRequestBuilder.newBuilderWithSource(mCachedImageSource.getUri())
          .setPostprocessor(postprocessor)
          .setResizeOptions(resizeOptions)
          .setAutoRotateEnabled(true)
          .setProgressiveRenderingEnabled(mProgressiveRenderingEnabled)
          .build();
      mDraweeControllerBuilder.setLowResImageRequest(cachedImageRequest);
    }

    if (mControllerListener != null && mControllerForTesting != null) {
      ForwardingControllerListener combinedListener = new ForwardingControllerListener();
      combinedListener.addListener(mControllerListener);
      combinedListener.addListener(mControllerForTesting);
      mDraweeControllerBuilder.setControllerListener(combinedListener);
    } else if (mControllerForTesting != null) {
      mDraweeControllerBuilder.setControllerListener(mControllerForTesting);
    } else if (mControllerListener != null) {
      mDraweeControllerBuilder.setControllerListener(mControllerListener);
    }

    setController(mDraweeControllerBuilder.build());
    mIsDirty = false;
  }

  // VisibleForTesting
  public void setControllerListener(ControllerListener controllerListener) {
    mControllerForTesting = controllerListener;
    mIsDirty = true;
    maybeUpdateView();
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    if (w > 0 && h > 0) {
      mIsDirty = mIsDirty || hasMultipleSources();
      maybeUpdateView();
    }
  }

  /**
   * ReactImageViews only render a single image.
   */
  @Override
  public boolean hasOverlappingRendering() {
    return false;
  }

  private boolean hasMultipleSources() {
    return mSources.size() > 1;
  }

  private void setSourceImage() {
    mImageSource = null;
    if (mSources.isEmpty()) {
      return;
    }
    if (hasMultipleSources()) {
      MultiSourceResult multiSource =
        MultiSourceHelper.getBestSourceForSize(getWidth(), getHeight(), mSources);
      mImageSource = multiSource.getBestResult();
      mCachedImageSource = multiSource.getBestResultInCache();
      return;
    }

    mImageSource = mSources.get(0);
  }

  private boolean shouldResize(ImageSource imageSource) {
    // Resizing is inferior to scaling. See http://frescolib.org/docs/resizing-rotating.html#_
    // We resize here only for images likely to be from the device's camera, where the app developer
    // has no control over the original size
    if (mResizeMethod == ImageResizeMethod.AUTO) {
      return
        UriUtil.isLocalContentUri(imageSource.getUri()) ||
        UriUtil.isLocalFileUri(imageSource.getUri());
    } else if (mResizeMethod == ImageResizeMethod.RESIZE) {
      return true;
    } else {
      return false;
    }
  }

  private void warnImageSource(String uri) {
    if (ReactBuildConfig.DEBUG) {
      Toast.makeText(
        getContext(),
        "Warning: Image source \"" + uri + "\" doesn't exist",
        Toast.LENGTH_SHORT).show();
    }
  }
}
