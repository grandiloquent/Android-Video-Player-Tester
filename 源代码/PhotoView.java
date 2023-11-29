package psycho.euphoria.myapplication;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import com.android.ex.photo.R;
import com.android.ex.photo.fragments.PhotoViewFragment.HorizontallyScrollable;
public class PhotoView extends View implements OnGestureListener, OnDoubleTapListener, ScaleGestureDetector.OnScaleGestureListener, HorizontallyScrollable {
    private final static long ZOOM_ANIMATION_DURATION = 300L;
    private final static long ROTATE_ANIMATION_DURATION = 500L;
    private static final long SNAP_DURATION = 100L;
    private static final long SNAP_DELAY = 250L;
    private final static float DOUBLE_TAP_SCALE_FACTOR = 1.5f;
    private final static float SNAP_THRESHOLD = 20.0f;
    private final static float CROPPED_SIZE = 256.0f;
    private static boolean sInitialized;
    private static int sCropSize;
    private static Bitmap sVideoImage;
    private static Bitmap sVideoNotReadyImage;
    private static Paint sCropDimPaint;
    private static Paint sCropPaint;
    private BitmapDrawable mDrawable;
    private Matrix mDrawMatrix;
    private Matrix mMatrix = new Matrix();
    private Matrix mOriginalMatrix = new Matrix();
    private int mFixedHeight = -1;
    private boolean mHaveLayout;
    private boolean mFullScreen;
    private byte[] mVideoBlob;
    private boolean mVideoReady;
    private boolean mAllowCrop;
    private Rect mCropRect = new Rect();
    private int mCropSize;
    private float mMaxInitialScaleFactor;
    private GestureDetectorCompat mGestureDetector;
    private ScaleGestureDetector mScaleGetureDetector;
    private OnClickListener mExternalClickListener;
    private boolean mTransformsEnabled;
    private boolean mDoubleTapToZoomEnabled = true;
    private boolean mDoubleTapDebounce;
    private boolean mIsDoubleTouch;
    private ScaleRunnable mScaleRunnable;
    private float mMinScale;
    private float mMaxScale;
    private TranslateRunnable mTranslateRunnable;
    private SnapRunnable mSnapRunnable;
    private RotateRunnable mRotateRunnable;
    private float mRotation;
    private RectF mTempSrc = new RectF();
    private RectF mTempDst = new RectF();
    private RectF mTranslateRect = new RectF();
    private float[] mValues = new float[9];
    public PhotoView(Context context) {
        super(context);
        initialize();
    }
    public PhotoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }
    public PhotoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mScaleGetureDetector == null || mGestureDetector == null) {
            return true;
        }
        mScaleGetureDetector.onTouchEvent(event);
        mGestureDetector.onTouchEvent(event);
        final int action = event.getAction();
        switch (action) {
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!mTranslateRunnable.mRunning) {
                    snap();
                }
                break;
        }
        return true;
    }
    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (mDoubleTapToZoomEnabled && mTransformsEnabled) {
            if (!mDoubleTapDebounce) {
                float currentScale = getScale();
                float targetScale = currentScale * DOUBLE_TAP_SCALE_FACTOR;
                targetScale = Math.max(mMinScale, targetScale);
                targetScale = Math.min(mMaxScale, targetScale);
                mScaleRunnable.start(currentScale, targetScale, e.getX(), e.getY());
            }
            mDoubleTapDebounce = false;
        }
        return true;
    }
    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return true;
    }
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (mExternalClickListener != null && !mIsDoubleTouch) {
            mExternalClickListener.onClick(this);
        }
        mIsDoubleTouch = false;
        return true;
    }
    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }
    @Override
    public void onLongPress(MotionEvent e) {
    }
    @Override
    public void onShowPress(MotionEvent e) {
    }
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (mTransformsEnabled) {
            translate(-distanceX, -distanceY);
        }
        return true;
    }
    @Override
    public boolean onDown(MotionEvent e) {
        if (mTransformsEnabled) {
            mTranslateRunnable.stop();
            mSnapRunnable.stop();
        }
        return true;
    }
    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (mTransformsEnabled) {
            mTranslateRunnable.start(velocityX, velocityY);
        }
        return true;
    }
    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        if (mTransformsEnabled) {
            mIsDoubleTouch = false;
            float currentScale = getScale();
            float newScale = currentScale * detector.getScaleFactor();
            scale(newScale, detector.getFocusX(), detector.getFocusY());
        }
        return true;
    }
    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        if (mTransformsEnabled) {
            mScaleRunnable.stop();
            mIsDoubleTouch = true;
        }
        return true;
    }
    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
        if (mTransformsEnabled && mIsDoubleTouch) {
            mDoubleTapDebounce = true;
            resetTransformations();
        }
    }
    @Override
    public void setOnClickListener(OnClickListener listener) {
        mExternalClickListener = listener;
    }
    @Override
    public boolean interceptMoveLeft(float origX, float origY) {
        if (!mTransformsEnabled) {
            return false;
        } else if (mTranslateRunnable.mRunning) {
            return true;
        } else {
            mMatrix.getValues(mValues);
            mTranslateRect.set(mTempSrc);
            mMatrix.mapRect(mTranslateRect);
            final float viewWidth = getWidth();
            final float transX = mValues[Matrix.MTRANS_X];
            final float drawWidth = mTranslateRect.right - mTranslateRect.left;
            if (!mTransformsEnabled || drawWidth <= viewWidth) {
                return false;
            } else if (transX == 0) {
                return false;
            } else if (viewWidth >= drawWidth + transX) {
                return true;
            } else {
                return true;
            }
        }
    }
    @Override
    public boolean interceptMoveRight(float origX, float origY) {
        if (!mTransformsEnabled) {
            return false;
        } else if (mTranslateRunnable.mRunning) {
            return true;
        } else {
            mMatrix.getValues(mValues);
            mTranslateRect.set(mTempSrc);
            mMatrix.mapRect(mTranslateRect);
            final float viewWidth = getWidth();
            final float transX = mValues[Matrix.MTRANS_X];
            final float drawWidth = mTranslateRect.right - mTranslateRect.left;
            if (!mTransformsEnabled || drawWidth <= viewWidth) {
                return false;
            } else if (transX == 0) {
                return true;
            } else if (viewWidth >= drawWidth + transX) {
                return false;
            } else {
                return true;
            }
        }
    }
    public void clear() {
        mGestureDetector = null;
        mScaleGetureDetector = null;
        mDrawable = null;
        mScaleRunnable.stop();
        mScaleRunnable = null;
        mTranslateRunnable.stop();
        mTranslateRunnable = null;
        mSnapRunnable.stop();
        mSnapRunnable = null;
        mRotateRunnable.stop();
        mRotateRunnable = null;
        setOnClickListener(null);
        mExternalClickListener = null;
    }
    public void bindPhoto(Bitmap photoBitmap) {
        boolean changed = false;
        if (mDrawable != null) {
            final Bitmap drawableBitmap = mDrawable.getBitmap();
            if (photoBitmap == drawableBitmap) {
                return;
            }
            changed = photoBitmap != null && (mDrawable.getIntrinsicWidth() != photoBitmap.getWidth() || mDrawable.getIntrinsicHeight() != photoBitmap.getHeight());
            mMinScale = 0f;
            mDrawable = null;
        }
        if (mDrawable == null && photoBitmap != null) {
            mDrawable = new BitmapDrawable(getResources(), photoBitmap);
        }
        configureBounds(changed);
        invalidate();
    }
    public Bitmap getPhoto() {
        if (mDrawable != null) {
            return mDrawable.getBitmap();
        }
        return null;
    }
    public byte[] getVideoData() {
        return mVideoBlob;
    }
    public boolean isVideo() {
        return mVideoBlob != null;
    }
    public boolean isVideoReady() {
        return mVideoBlob != null && mVideoReady;
    }
    public boolean isPhotoBound() {
        return mDrawable != null;
    }
    public void setFullScreen(boolean fullScreen, boolean animate) {
        if (fullScreen != mFullScreen) {
            mFullScreen = fullScreen;
            requestLayout();
            invalidate();
        }
    }
    public void enableAllowCrop(boolean allowCrop) {
        if (allowCrop && mHaveLayout) {
            throw new IllegalArgumentException("Cannot set crop after view has been laid out");
        }
        if (!allowCrop && mAllowCrop) {
            throw new IllegalArgumentException("Cannot unset crop mode");
        }
        mAllowCrop = allowCrop;
    }
    public Bitmap getCroppedPhoto() {
        if (!mAllowCrop) {
            return null;
        }
        final Bitmap croppedBitmap = Bitmap.createBitmap((int) CROPPED_SIZE, (int) CROPPED_SIZE, Bitmap.Config.ARGB_8888);
        final Canvas croppedCanvas = new Canvas(croppedBitmap);
        final int cropWidth = mCropRect.right - mCropRect.left;
        final float scaleWidth = CROPPED_SIZE / cropWidth;
        final float scaleHeight = CROPPED_SIZE / cropWidth;
        final Matrix matrix = new Matrix(mDrawMatrix);
        matrix.postTranslate(-mCropRect.left, -mCropRect.top);
        matrix.postScale(scaleWidth, scaleHeight);
        if (mDrawable != null) {
            croppedCanvas.concat(matrix);
            mDrawable.draw(croppedCanvas);
        }
        return croppedBitmap;
    }
    public void resetTransformations() {
        mMatrix.set(mOriginalMatrix);
        invalidate();
    }
    public void rotateClockwise() {
        rotate(90, true);
    }
    public void rotateCounterClockwise() {
        rotate(-90, true);
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mDrawable != null) {
            int saveCount = canvas.getSaveCount();
            canvas.save();
            if (mDrawMatrix != null) {
                canvas.concat(mDrawMatrix);
            }
            mDrawable.draw(canvas);
            canvas.restoreToCount(saveCount);
            if (mVideoBlob != null) {
                final Bitmap videoImage = (mVideoReady ? sVideoImage : sVideoNotReadyImage);
                final int drawLeft = (getWidth() - videoImage.getWidth()) / 2;
                final int drawTop = (getHeight() - videoImage.getHeight()) / 2;
                canvas.drawBitmap(videoImage, drawLeft, drawTop, null);
            }
            mTranslateRect.set(mDrawable.getBounds());
            if (mDrawMatrix != null) {
                mDrawMatrix.mapRect(mTranslateRect);
            }
            if (mAllowCrop) {
                int previousSaveCount = canvas.getSaveCount();
                canvas.drawRect(0, 0, getWidth(), getHeight(), sCropDimPaint);
                canvas.save();
                canvas.clipRect(mCropRect);
                if (mDrawMatrix != null) {
                    canvas.concat(mDrawMatrix);
                }
                mDrawable.draw(canvas);
                canvas.restoreToCount(previousSaveCount);
                canvas.drawRect(mCropRect, sCropPaint);
            }
        }
    }
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        mHaveLayout = true;
        final int layoutWidth = getWidth();
        final int layoutHeight = getHeight();
        if (mAllowCrop) {
            mCropSize = Math.min(sCropSize, Math.min(layoutWidth, layoutHeight));
            final int cropLeft = (layoutWidth - mCropSize) / 2;
            final int cropTop = (layoutHeight - mCropSize) / 2;
            final int cropRight = cropLeft + mCropSize;
            final int cropBottom = cropTop + mCropSize;
            mCropRect.set(cropLeft, cropTop, cropRight, cropBottom);
        }
        configureBounds(changed);
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mFixedHeight != -1) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(mFixedHeight, MeasureSpec.AT_MOST));
            setMeasuredDimension(getMeasuredWidth(), mFixedHeight);
        } else {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }
    public void setFixedHeight(int fixedHeight) {
        final boolean adjustBounds = (fixedHeight != mFixedHeight);
        mFixedHeight = fixedHeight;
        setMeasuredDimension(getMeasuredWidth(), mFixedHeight);
        if (adjustBounds) {
            configureBounds(true);
            requestLayout();
        }
    }
    public void enableImageTransforms(boolean enable) {
        mTransformsEnabled = enable;
        if (!mTransformsEnabled) {
            resetTransformations();
        }
    }
    private void configureBounds(boolean changed) {
        if (mDrawable == null || !mHaveLayout) {
            return;
        }
        final int dwidth = mDrawable.getIntrinsicWidth();
        final int dheight = mDrawable.getIntrinsicHeight();
        final int vwidth = getWidth();
        final int vheight = getHeight();
        final boolean fits = (dwidth < 0 || vwidth == dwidth) && (dheight < 0 || vheight == dheight);
        mDrawable.setBounds(0, 0, dwidth, dheight);
        if (changed || (mMinScale == 0 && mDrawable != null && mHaveLayout)) {
            generateMatrix();
            generateScale();
        }
        if (fits || mMatrix.isIdentity()) {
            mDrawMatrix = null;
        } else {
            mDrawMatrix = mMatrix;
        }
    }
    private void generateMatrix() {
        final int dwidth = mDrawable.getIntrinsicWidth();
        final int dheight = mDrawable.getIntrinsicHeight();
        final int vwidth = mAllowCrop ? sCropSize : getWidth();
        final int vheight = mAllowCrop ? sCropSize : getHeight();
        final boolean fits = (dwidth < 0 || vwidth == dwidth) && (dheight < 0 || vheight == dheight);
        if (fits && !mAllowCrop) {
            mMatrix.reset();
        } else {
            mTempSrc.set(0, 0, dwidth, dheight);
            if (mAllowCrop) {
                mTempDst.set(mCropRect);
            } else {
                mTempDst.set(0, 0, vwidth, vheight);
            }
            RectF scaledDestination = new RectF((vwidth / 2) - (dwidth * mMaxInitialScaleFactor / 2), (vheight / 2) - (dheight * mMaxInitialScaleFactor / 2), (vwidth / 2) + (dwidth * mMaxInitialScaleFactor / 2), (vheight / 2) + (dheight * mMaxInitialScaleFactor / 2));
            if (mTempDst.contains(scaledDestination)) {
                mMatrix.setRectToRect(mTempSrc, scaledDestination, Matrix.ScaleToFit.CENTER);
            } else {
                mMatrix.setRectToRect(mTempSrc, mTempDst, Matrix.ScaleToFit.CENTER);
            }
        }
        mOriginalMatrix.set(mMatrix);
    }
    private void generateScale() {
        final int dwidth = mDrawable.getIntrinsicWidth();
        final int dheight = mDrawable.getIntrinsicHeight();
        final int vwidth = mAllowCrop ? getCropSize() : getWidth();
        final int vheight = mAllowCrop ? getCropSize() : getHeight();
        if (dwidth < vwidth && dheight < vheight && !mAllowCrop) {
            mMinScale = 1.0f;
        } else {
            mMinScale = getScale();
        }
        mMaxScale = Math.max(mMinScale * 8, 8);
    }
    private int getCropSize() {
        return mCropSize > 0 ? mCropSize : sCropSize;
    }
    private float getScale() {
        mMatrix.getValues(mValues);
        return mValues[Matrix.MSCALE_X];
    }
    private void scale(float newScale, float centerX, float centerY) {
        mMatrix.postRotate(-mRotation, getWidth() / 2, getHeight() / 2);
        newScale = Math.max(newScale, mMinScale);
        newScale = Math.min(newScale, mMaxScale);
        float currentScale = getScale();
        float factor = newScale / currentScale;
        mMatrix.postScale(factor, factor, centerX, centerY);
        snap();
        mMatrix.postRotate(mRotation, getWidth() / 2, getHeight() / 2);
        invalidate();
    }
    private boolean translate(float tx, float ty) {
        mTranslateRect.set(mTempSrc);
        mMatrix.mapRect(mTranslateRect);
        final float maxLeft = mAllowCrop ? mCropRect.left : 0.0f;
        final float maxRight = mAllowCrop ? mCropRect.right : getWidth();
        float l = mTranslateRect.left;
        float r = mTranslateRect.right;
        final float translateX;
        if (mAllowCrop) {
            translateX = Math.max(maxLeft - mTranslateRect.right, Math.min(maxRight - mTranslateRect.left, tx));
        } else {
            if (r - l < maxRight - maxLeft) {
                translateX = maxLeft + ((maxRight - maxLeft) - (r + l)) / 2;
            } else {
                translateX = Math.max(maxRight - r, Math.min(maxLeft - l, tx));
            }
        }
        float maxTop = mAllowCrop ? mCropRect.top : 0.0f;
        float maxBottom = mAllowCrop ? mCropRect.bottom : getHeight();
        float t = mTranslateRect.top;
        float b = mTranslateRect.bottom;
        final float translateY;
        if (mAllowCrop) {
            translateY = Math.max(maxTop - mTranslateRect.bottom, Math.min(maxBottom - mTranslateRect.top, ty));
        } else {
            if (b - t < maxBottom - maxTop) {
                translateY = maxTop + ((maxBottom - maxTop) - (b + t)) / 2;
            } else {
                translateY = Math.max(maxBottom - b, Math.min(maxTop - t, ty));
            }
        }
        mMatrix.postTranslate(translateX, translateY);
        invalidate();
        return (translateX == tx) && (translateY == ty);
    }
    private void snap() {
        mTranslateRect.set(mTempSrc);
        mMatrix.mapRect(mTranslateRect);
        float maxLeft = mAllowCrop ? mCropRect.left : 0.0f;
        float maxRight = mAllowCrop ? mCropRect.right : getWidth();
        float l = mTranslateRect.left;
        float r = mTranslateRect.right;
        final float translateX;
        if (r - l < maxRight - maxLeft) {
            translateX = maxLeft + ((maxRight - maxLeft) - (r + l)) / 2;
        } else if (l > maxLeft) {
            translateX = maxLeft - l;
        } else if (r < maxRight) {
            translateX = maxRight - r;
        } else {
            translateX = 0.0f;
        }
        float maxTop = mAllowCrop ? mCropRect.top : 0.0f;
        float maxBottom = mAllowCrop ? mCropRect.bottom : getHeight();
        float t = mTranslateRect.top;
        float b = mTranslateRect.bottom;
        final float translateY;
        if (b - t < maxBottom - maxTop) {
            translateY = maxTop + ((maxBottom - maxTop) - (b + t)) / 2;
        } else if (t > maxTop) {
            translateY = maxTop - t;
        } else if (b < maxBottom) {
            translateY = maxBottom - b;
        } else {
            translateY = 0.0f;
        }
        if (Math.abs(translateX) > SNAP_THRESHOLD || Math.abs(translateY) > SNAP_THRESHOLD) {
            mSnapRunnable.start(translateX, translateY);
        } else {
            mMatrix.postTranslate(translateX, translateY);
            invalidate();
        }
    }
    private void rotate(float degrees, boolean animate) {
        if (animate) {
            mRotateRunnable.start(degrees);
        } else {
            mRotation += degrees;
            mMatrix.postRotate(degrees, getWidth() / 2, getHeight() / 2);
            invalidate();
        }
    }
    private void initialize() {
        Context context = getContext();
        if (!sInitialized) {
            sInitialized = true;
            Resources resources = context.getApplicationContext().getResources();
            sCropSize = resources.getDimensionPixelSize(R.dimen.photo_crop_width);
            sCropDimPaint = new Paint();
            sCropDimPaint.setAntiAlias(true);
            sCropDimPaint.setColor(resources.getColor(R.color.photo_crop_dim_color));
            sCropDimPaint.setStyle(Style.FILL);
            sCropPaint = new Paint();
            sCropPaint.setAntiAlias(true);
            sCropPaint.setColor(resources.getColor(R.color.photo_crop_highlight_color));
            sCropPaint.setStyle(Style.STROKE);
            sCropPaint.setStrokeWidth(resources.getDimension(R.dimen.photo_crop_stroke_width));
        }
        mGestureDetector = new GestureDetectorCompat(context, this, null);
        mScaleGetureDetector = new ScaleGestureDetector(context, this);
        mScaleRunnable = new ScaleRunnable(this);
        mTranslateRunnable = new TranslateRunnable(this);
        mSnapRunnable = new SnapRunnable(this);
        mRotateRunnable = new RotateRunnable(this);
    }
    private static class ScaleRunnable implements Runnable {
        private final PhotoView mHeader;
        private float mCenterX;
        private float mCenterY;
        private boolean mZoomingIn;
        private float mTargetScale;
        private float mStartScale;
        private float mVelocity;
        private long mStartTime;
        private boolean mRunning;
        private boolean mStop;
        public ScaleRunnable(PhotoView header) {
            mHeader = header;
        }
        public boolean start(float startScale, float targetScale, float centerX, float centerY) {
            if (mRunning) {
                return false;
            }
            mCenterX = centerX;
            mCenterY = centerY;
            mTargetScale = targetScale;
            mStartTime = System.currentTimeMillis();
            mStartScale = startScale;
            mZoomingIn = mTargetScale > mStartScale;
            mVelocity = (mTargetScale - mStartScale) / ZOOM_ANIMATION_DURATION;
            mRunning = true;
            mStop = false;
            mHeader.post(this);
            return true;
        }
        public void stop() {
            mRunning = false;
            mStop = true;
        }
        @Override
        public void run() {
            if (mStop) {
                return;
            }
            long now = System.currentTimeMillis();
            long ellapsed = now - mStartTime;
            float newScale = (mStartScale + mVelocity * ellapsed);
            mHeader.scale(newScale, mCenterX, mCenterY);
            if (newScale == mTargetScale || (mZoomingIn == (newScale > mTargetScale))) {
                mHeader.scale(mTargetScale, mCenterX, mCenterY);
                stop();
            }
            if (!mStop) {
                mHeader.post(this);
            }
        }
    }
    private static class TranslateRunnable implements Runnable {
        private static final float DECELERATION_RATE = 1000f;
        private static final long NEVER = -1L;
        private final PhotoView mHeader;
        private float mVelocityX;
        private float mVelocityY;
        private long mLastRunTime;
        private boolean mRunning;
        private boolean mStop;
        public TranslateRunnable(PhotoView header) {
            mLastRunTime = NEVER;
            mHeader = header;
        }
        public boolean start(float velocityX, float velocityY) {
            if (mRunning) {
                return false;
            }
            mLastRunTime = NEVER;
            mVelocityX = velocityX;
            mVelocityY = velocityY;
            mStop = false;
            mRunning = true;
            mHeader.post(this);
            return true;
        }
        public void stop() {
            mRunning = false;
            mStop = true;
        }
        @Override
        public void run() {
            if (mStop) {
                return;
            }
            long now = System.currentTimeMillis();
            float delta = (mLastRunTime != NEVER) ? (now - mLastRunTime) / 1000f : 0f;
            final boolean didTranslate = mHeader.translate(mVelocityX * delta, mVelocityY * delta);
            mLastRunTime = now;
            float slowDown = DECELERATION_RATE * delta;
            if (mVelocityX > 0f) {
                mVelocityX -= slowDown;
                if (mVelocityX < 0f) {
                    mVelocityX = 0f;
                }
            } else {
                mVelocityX += slowDown;
                if (mVelocityX > 0f) {
                    mVelocityX = 0f;
                }
            }
            if (mVelocityY > 0f) {
                mVelocityY -= slowDown;
                if (mVelocityY < 0f) {
                    mVelocityY = 0f;
                }
            } else {
                mVelocityY += slowDown;
                if (mVelocityY > 0f) {
                    mVelocityY = 0f;
                }
            }
            if ((mVelocityX == 0f && mVelocityY == 0f) || !didTranslate) {
                stop();
                mHeader.snap();
            }
            if (mStop) {
                return;
            }
            mHeader.post(this);
        }
    }
    private static class SnapRunnable implements Runnable {
        private static final long NEVER = -1L;
        private final PhotoView mHeader;
        private float mTranslateX;
        private float mTranslateY;
        private long mStartRunTime;
        private boolean mRunning;
        private boolean mStop;
        public SnapRunnable(PhotoView header) {
            mStartRunTime = NEVER;
            mHeader = header;
        }
        public boolean start(float translateX, float translateY) {
            if (mRunning) {
                return false;
            }
            mStartRunTime = NEVER;
            mTranslateX = translateX;
            mTranslateY = translateY;
            mStop = false;
            mRunning = true;
            mHeader.postDelayed(this, SNAP_DELAY);
            return true;
        }
        public void stop() {
            mRunning = false;
            mStop = true;
        }
        @Override
        public void run() {
            if (mStop) {
                return;
            }
            long now = System.currentTimeMillis();
            float delta = (mStartRunTime != NEVER) ? (now - mStartRunTime) : 0f;
            if (mStartRunTime == NEVER) {
                mStartRunTime = now;
            }
            float transX;
            float transY;
            if (delta >= SNAP_DURATION) {
                transX = mTranslateX;
                transY = mTranslateY;
            } else {
                transX = (mTranslateX / (SNAP_DURATION - delta)) * 10f;
                transY = (mTranslateY / (SNAP_DURATION - delta)) * 10f;
                if (Math.abs(transX) > Math.abs(mTranslateX) || transX == Float.NaN) {
                    transX = mTranslateX;
                }
                if (Math.abs(transY) > Math.abs(mTranslateY) || transY == Float.NaN) {
                    transY = mTranslateY;
                }
            }
            mHeader.translate(transX, transY);
            mTranslateX -= transX;
            mTranslateY -= transY;
            if (mTranslateX == 0 && mTranslateY == 0) {
                stop();
            }
            if (mStop) {
                return;
            }
            mHeader.post(this);
        }
    }
    private static class RotateRunnable implements Runnable {
        private static final long NEVER = -1L;
        private final PhotoView mHeader;
        private float mTargetRotation;
        private float mAppliedRotation;
        private float mVelocity;
        private long mLastRuntime;
        private boolean mRunning;
        private boolean mStop;
        public RotateRunnable(PhotoView header) {
            mHeader = header;
        }
        public void start(float rotation) {
            if (mRunning) {
                return;
            }
            mTargetRotation = rotation;
            mVelocity = mTargetRotation / ROTATE_ANIMATION_DURATION;
            mAppliedRotation = 0f;
            mLastRuntime = NEVER;
            mStop = false;
            mRunning = true;
            mHeader.post(this);
        }
        public void stop() {
            mRunning = false;
            mStop = true;
        }
        @Override
        public void run() {
            if (mStop) {
                return;
            }
            if (mAppliedRotation != mTargetRotation) {
                long now = System.currentTimeMillis();
                long delta = mLastRuntime != NEVER ? now - mLastRuntime : 0L;
                float rotationAmount = mVelocity * delta;
                if (mAppliedRotation < mTargetRotation && mAppliedRotation + rotationAmount > mTargetRotation || mAppliedRotation > mTargetRotation && mAppliedRotation + rotationAmount < mTargetRotation) {
                    rotationAmount = mTargetRotation - mAppliedRotation;
                }
                mHeader.rotate(rotationAmount, false);
                mAppliedRotation += rotationAmount;
                if (mAppliedRotation == mTargetRotation) {
                    stop();
                }
                mLastRuntime = now;
            }
            if (mStop) {
                return;
            }
            mHeader.post(this);
        }
    }
    public void setMaxInitialScale(float f) {
        mMaxInitialScaleFactor = f;
    }
}