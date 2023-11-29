package android.widget;
import android.annotation.NonNull;
import android.app.AlertDialog;
import android.compat.annotation.UnsupportedAppUsage;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Cea708CaptionRenderer;
import android.media.ClosedCaptionRenderer;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.Metadata;
import android.media.SubtitleController;
import android.media.SubtitleTrack.RenderingWidget;
import android.media.TtmlRenderer;
import android.media.WebVttRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.MediaController.MediaPlayerControl;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Vector;
public class VideoView extends SurfaceView
        implements MediaPlayerControl, SubtitleController.Anchor {
    private static final String TAG = "VideoView";
        private static final int STATE_ERROR = -1;
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P, trackingBug = 115609023)
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;
    private final Vector<Pair<InputStream, MediaFormat>> mPendingSubtitleTracks = new Vector<>();
        @UnsupportedAppUsage
    private Uri mUri;
    @UnsupportedAppUsage
    private Map<String, String> mHeaders;
                        @UnsupportedAppUsage
    private int mCurrentState = STATE_IDLE;
    @UnsupportedAppUsage
    private int mTargetState = STATE_IDLE;
        @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P, trackingBug = 115609023)
    private SurfaceHolder mSurfaceHolder = null;
    @UnsupportedAppUsage
    private MediaPlayer mMediaPlayer = null;
    private int mAudioSession;
    @UnsupportedAppUsage
    private int mVideoWidth;
    @UnsupportedAppUsage
    private int mVideoHeight;
    private int mSurfaceWidth;
    private int mSurfaceHeight;
    @UnsupportedAppUsage
    private MediaController mMediaController;
    private OnCompletionListener mOnCompletionListener;
    private MediaPlayer.OnPreparedListener mOnPreparedListener;
    @UnsupportedAppUsage
    private int mCurrentBufferPercentage;
    private OnErrorListener mOnErrorListener;
    private OnInfoListener mOnInfoListener;
    private int mSeekWhenPrepared;      private boolean mCanPause;
    private boolean mCanSeekBack;
    private boolean mCanSeekForward;
    private AudioManager mAudioManager;
    private int mAudioFocusType = AudioManager.AUDIOFOCUS_GAIN;     private AudioAttributes mAudioAttributes;
    private RenderingWidget mSubtitleWidget;
    private RenderingWidget.OnChangedListener mSubtitlesChangedListener;
    public VideoView(Context context) {
        this(context, null);
    }
    public VideoView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    public VideoView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }
    public VideoView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mVideoWidth = 0;
        mVideoHeight = 0;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mAudioAttributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build();
        getHolder().addCallback(mSHCallback);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        setFocusable(true);
        setFocusableInTouchMode(true);
        requestFocus();
        mCurrentState = STATE_IDLE;
        mTargetState = STATE_IDLE;
    }
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(mVideoWidth, widthMeasureSpec);
        int height = getDefaultSize(mVideoHeight, heightMeasureSpec);
        if (mVideoWidth > 0 && mVideoHeight > 0) {
            int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSpecSize = MeasureSpec.getSize(widthMeasureSpec);
            int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);
            int heightSpecSize = MeasureSpec.getSize(heightMeasureSpec);
            if (widthSpecMode == MeasureSpec.EXACTLY && heightSpecMode == MeasureSpec.EXACTLY) {
                                width = widthSpecSize;
                height = heightSpecSize;
                                if ( mVideoWidth * height  < width * mVideoHeight ) {
                                        width = height * mVideoWidth / mVideoHeight;
                } else if ( mVideoWidth * height  > width * mVideoHeight ) {
                                        height = width * mVideoHeight / mVideoWidth;
                }
            } else if (widthSpecMode == MeasureSpec.EXACTLY) {
                                width = widthSpecSize;
                height = width * mVideoHeight / mVideoWidth;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                                        height = heightSpecSize;
                }
            } else if (heightSpecMode == MeasureSpec.EXACTLY) {
                                height = heightSpecSize;
                width = height * mVideoWidth / mVideoHeight;
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                                        width = widthSpecSize;
                }
            } else {
                                width = mVideoWidth;
                height = mVideoHeight;
                if (heightSpecMode == MeasureSpec.AT_MOST && height > heightSpecSize) {
                                        height = heightSpecSize;
                    width = height * mVideoWidth / mVideoHeight;
                }
                if (widthSpecMode == MeasureSpec.AT_MOST && width > widthSpecSize) {
                                        width = widthSpecSize;
                    height = width * mVideoHeight / mVideoWidth;
                }
            }
        } else {
                    }
        setMeasuredDimension(width, height);
    }
    @Override
    public CharSequence getAccessibilityClassName() {
        return VideoView.class.getName();
    }
    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        return getDefaultSize(desiredSize, measureSpec);
    }
    public void setVideoPath(String path) {
        setVideoURI(Uri.parse(path));
    }
    public void setVideoURI(Uri uri) {
        setVideoURI(uri, null);
    }
    public void setVideoURI(Uri uri, Map<String, String> headers) {
        mUri = uri;
        mHeaders = headers;
        mSeekWhenPrepared = 0;
        openVideo();
        requestLayout();
        invalidate();
    }
    public void setAudioFocusRequest(int focusGain) {
        if (focusGain != AudioManager.AUDIOFOCUS_NONE
                && focusGain != AudioManager.AUDIOFOCUS_GAIN
                && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                && focusGain != AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) {
            throw new IllegalArgumentException("Illegal audio focus type " + focusGain);
        }
        mAudioFocusType = focusGain;
    }
    public void setAudioAttributes(@NonNull AudioAttributes attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("Illegal null AudioAttributes");
        }
        mAudioAttributes = attributes;
    }
    public void addSubtitleSource(InputStream is, MediaFormat format) {
        if (mMediaPlayer == null) {
            mPendingSubtitleTracks.add(Pair.create(is, format));
        } else {
            try {
                mMediaPlayer.addSubtitleSource(is, format);
            } catch (IllegalStateException e) {
                mInfoListener.onInfo(
                        mMediaPlayer, MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE, 0);
            }
        }
    }
    public void stopPlayback() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mCurrentState = STATE_IDLE;
            mTargetState  = STATE_IDLE;
            mAudioManager.abandonAudioFocus(null);
        }
    }
    private void openVideo() {
        if (mUri == null || mSurfaceHolder == null) {
                        return;
        }
                        release(false);
        if (mAudioFocusType != AudioManager.AUDIOFOCUS_NONE) {
                        mAudioManager.requestAudioFocus(null, mAudioAttributes, mAudioFocusType, 0 );
        }
        try {
            mMediaPlayer = new MediaPlayer();
                                    final Context context = getContext();
            final SubtitleController controller = new SubtitleController(
                    context, mMediaPlayer.getMediaTimeProvider(), mMediaPlayer);
            controller.registerRenderer(new WebVttRenderer(context));
            controller.registerRenderer(new TtmlRenderer(context));
            controller.registerRenderer(new Cea708CaptionRenderer(context));
            controller.registerRenderer(new ClosedCaptionRenderer(context));
            mMediaPlayer.setSubtitleAnchor(controller, this);
            if (mAudioSession != 0) {
                mMediaPlayer.setAudioSessionId(mAudioSession);
            } else {
                mAudioSession = mMediaPlayer.getAudioSessionId();
            }
            mMediaPlayer.setOnPreparedListener(mPreparedListener);
            mMediaPlayer.setOnVideoSizeChangedListener(mSizeChangedListener);
            mMediaPlayer.setOnCompletionListener(mCompletionListener);
            mMediaPlayer.setOnErrorListener(mErrorListener);
            mMediaPlayer.setOnInfoListener(mInfoListener);
            mMediaPlayer.setOnBufferingUpdateListener(mBufferingUpdateListener);
            mCurrentBufferPercentage = 0;
            mMediaPlayer.setDataSource(mContext, mUri, mHeaders);
            mMediaPlayer.setDisplay(mSurfaceHolder);
            mMediaPlayer.setAudioAttributes(mAudioAttributes);
            mMediaPlayer.setScreenOnWhilePlaying(true);
            mMediaPlayer.prepareAsync();
            for (Pair<InputStream, MediaFormat> pending: mPendingSubtitleTracks) {
                try {
                    mMediaPlayer.addSubtitleSource(pending.first, pending.second);
                } catch (IllegalStateException e) {
                    mInfoListener.onInfo(
                            mMediaPlayer, MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE, 0);
                }
            }
                                    mCurrentState = STATE_PREPARING;
            attachMediaController();
        } catch (IOException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mUri, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            mErrorListener.onError(mMediaPlayer, MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            return;
        } finally {
            mPendingSubtitleTracks.clear();
        }
    }
    public void setMediaController(MediaController controller) {
        if (mMediaController != null) {
            mMediaController.hide();
        }
        mMediaController = controller;
        attachMediaController();
    }
    private void attachMediaController() {
        if (mMediaPlayer != null && mMediaController != null) {
            mMediaController.setMediaPlayer(this);
            View anchorView = this.getParent() instanceof View ?
                    (View)this.getParent() : this;
            mMediaController.setAnchorView(anchorView);
            mMediaController.setEnabled(isInPlaybackState());
        }
    }
    MediaPlayer.OnVideoSizeChangedListener mSizeChangedListener =
        new MediaPlayer.OnVideoSizeChangedListener() {
            public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
                mVideoWidth = mp.getVideoWidth();
                mVideoHeight = mp.getVideoHeight();
                if (mVideoWidth != 0 && mVideoHeight != 0) {
                    getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                    requestLayout();
                }
            }
    };
    @UnsupportedAppUsage
    MediaPlayer.OnPreparedListener mPreparedListener = new MediaPlayer.OnPreparedListener() {
        public void onPrepared(MediaPlayer mp) {
            mCurrentState = STATE_PREPARED;
                        Metadata data = mp.getMetadata(MediaPlayer.METADATA_ALL,
                                      MediaPlayer.BYPASS_METADATA_FILTER);
            if (data != null) {
                mCanPause = !data.has(Metadata.PAUSE_AVAILABLE)
                        || data.getBoolean(Metadata.PAUSE_AVAILABLE);
                mCanSeekBack = !data.has(Metadata.SEEK_BACKWARD_AVAILABLE)
                        || data.getBoolean(Metadata.SEEK_BACKWARD_AVAILABLE);
                mCanSeekForward = !data.has(Metadata.SEEK_FORWARD_AVAILABLE)
                        || data.getBoolean(Metadata.SEEK_FORWARD_AVAILABLE);
            } else {
                mCanPause = mCanSeekBack = mCanSeekForward = true;
            }
            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared(mMediaPlayer);
            }
            if (mMediaController != null) {
                mMediaController.setEnabled(true);
            }
            mVideoWidth = mp.getVideoWidth();
            mVideoHeight = mp.getVideoHeight();
            int seekToPosition = mSeekWhenPrepared;              if (seekToPosition != 0) {
                seekTo(seekToPosition);
            }
            if (mVideoWidth != 0 && mVideoHeight != 0) {
                                getHolder().setFixedSize(mVideoWidth, mVideoHeight);
                if (mSurfaceWidth == mVideoWidth && mSurfaceHeight == mVideoHeight) {
                                                                                if (mTargetState == STATE_PLAYING) {
                        start();
                        if (mMediaController != null) {
                            mMediaController.show();
                        }
                    } else if (!isPlaying() &&
                               (seekToPosition != 0 || getCurrentPosition() > 0)) {
                       if (mMediaController != null) {
                                                      mMediaController.show(0);
                       }
                   }
                }
            } else {
                                                if (mTargetState == STATE_PLAYING) {
                    start();
                }
            }
        }
    };
    private MediaPlayer.OnCompletionListener mCompletionListener =
        new MediaPlayer.OnCompletionListener() {
        public void onCompletion(MediaPlayer mp) {
            mCurrentState = STATE_PLAYBACK_COMPLETED;
            mTargetState = STATE_PLAYBACK_COMPLETED;
            if (mMediaController != null) {
                mMediaController.hide();
            }
            if (mOnCompletionListener != null) {
                mOnCompletionListener.onCompletion(mMediaPlayer);
            }
            if (mAudioFocusType != AudioManager.AUDIOFOCUS_NONE) {
                mAudioManager.abandonAudioFocus(null);
            }
        }
    };
    private MediaPlayer.OnInfoListener mInfoListener =
        new MediaPlayer.OnInfoListener() {
        public  boolean onInfo(MediaPlayer mp, int arg1, int arg2) {
            if (mOnInfoListener != null) {
                mOnInfoListener.onInfo(mp, arg1, arg2);
            }
            return true;
        }
    };
    @UnsupportedAppUsage(maxTargetSdk = Build.VERSION_CODES.P, trackingBug = 115609023)
    private MediaPlayer.OnErrorListener mErrorListener =
        new MediaPlayer.OnErrorListener() {
        public boolean onError(MediaPlayer mp, int framework_err, int impl_err) {
            Log.d(TAG, "Error: " + framework_err + "," + impl_err);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
            if (mMediaController != null) {
                mMediaController.hide();
            }
            if (mOnErrorListener != null) {
                if (mOnErrorListener.onError(mMediaPlayer, framework_err, impl_err)) {
                    return true;
                }
            }
            if (getWindowToken() != null) {
                Resources r = mContext.getResources();
                int messageId;
                if (framework_err == MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK) {
                    messageId = com.android.internal.R.string.VideoView_error_text_invalid_progressive_playback;
                } else {
                    messageId = com.android.internal.R.string.VideoView_error_text_unknown;
                }
                new AlertDialog.Builder(mContext)
                        .setMessage(messageId)
                        .setPositiveButton(com.android.internal.R.string.VideoView_error_button,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        if (mOnCompletionListener != null) {
                                            mOnCompletionListener.onCompletion(mMediaPlayer);
                                        }
                                    }
                                })
                        .setCancelable(false)
                        .show();
            }
            return true;
        }
    };
    private MediaPlayer.OnBufferingUpdateListener mBufferingUpdateListener =
        new MediaPlayer.OnBufferingUpdateListener() {
        public void onBufferingUpdate(MediaPlayer mp, int percent) {
            mCurrentBufferPercentage = percent;
        }
    };
    public void setOnPreparedListener(MediaPlayer.OnPreparedListener l)
    {
        mOnPreparedListener = l;
    }
    public void setOnCompletionListener(OnCompletionListener l)
    {
        mOnCompletionListener = l;
    }
    public void setOnErrorListener(OnErrorListener l)
    {
        mOnErrorListener = l;
    }
    public void setOnInfoListener(OnInfoListener l) {
        mOnInfoListener = l;
    }
    @UnsupportedAppUsage
    SurfaceHolder.Callback mSHCallback = new SurfaceHolder.Callback()
    {
        public void surfaceChanged(SurfaceHolder holder, int format,
                                    int w, int h)
        {
            mSurfaceWidth = w;
            mSurfaceHeight = h;
            boolean isValidState =  (mTargetState == STATE_PLAYING);
            boolean hasValidSize = (mVideoWidth == w && mVideoHeight == h);
            if (mMediaPlayer != null && isValidState && hasValidSize) {
                if (mSeekWhenPrepared != 0) {
                    seekTo(mSeekWhenPrepared);
                }
                start();
            }
        }
        public void surfaceCreated(SurfaceHolder holder)
        {
            mSurfaceHolder = holder;
            openVideo();
        }
        public void surfaceDestroyed(SurfaceHolder holder)
        {
                        mSurfaceHolder = null;
            if (mMediaController != null) mMediaController.hide();
            release(true);
        }
    };
    @UnsupportedAppUsage
    private void release(boolean cleartargetstate) {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
            mPendingSubtitleTracks.clear();
            mCurrentState = STATE_IDLE;
            if (cleartargetstate) {
                mTargetState  = STATE_IDLE;
            }
            if (mAudioFocusType != AudioManager.AUDIOFOCUS_NONE) {
                mAudioManager.abandonAudioFocus(null);
            }
        }
    }
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN
                && isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return super.onTouchEvent(ev);
    }
    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN
                && isInPlaybackState() && mMediaController != null) {
            toggleMediaControlsVisiblity();
        }
        return super.onTrackballEvent(ev);
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK &&
                                     keyCode != KeyEvent.KEYCODE_VOLUME_UP &&
                                     keyCode != KeyEvent.KEYCODE_VOLUME_DOWN &&
                                     keyCode != KeyEvent.KEYCODE_VOLUME_MUTE &&
                                     keyCode != KeyEvent.KEYCODE_MENU &&
                                     keyCode != KeyEvent.KEYCODE_CALL &&
                                     keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isInPlaybackState() && isKeyCodeSupported && mMediaController != null) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                    keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                } else {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!mMediaPlayer.isPlaying()) {
                    start();
                    mMediaController.hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (mMediaPlayer.isPlaying()) {
                    pause();
                    mMediaController.show();
                }
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        }
        return super.onKeyDown(keyCode, event);
    }
    private void toggleMediaControlsVisiblity() {
        if (mMediaController.isShowing()) {
            mMediaController.hide();
        } else {
            mMediaController.show();
        }
    }
    @Override
    public void start() {
        if (isInPlaybackState()) {
            mMediaPlayer.start();
            mCurrentState = STATE_PLAYING;
        }
        mTargetState = STATE_PLAYING;
    }
    @Override
    public void pause() {
        if (isInPlaybackState()) {
            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentState = STATE_PAUSED;
            }
        }
        mTargetState = STATE_PAUSED;
    }
    public void suspend() {
        release(false);
    }
    public void resume() {
        openVideo();
    }
    @Override
    public int getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }
        return -1;
    }
    @Override
    public int getCurrentPosition() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getCurrentPosition();
        }
        return 0;
    }
    @Override
    public void seekTo(int msec) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(msec);
            mSeekWhenPrepared = 0;
        } else {
            mSeekWhenPrepared = msec;
        }
    }
    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }
    @Override
    public int getBufferPercentage() {
        if (mMediaPlayer != null) {
            return mCurrentBufferPercentage;
        }
        return 0;
    }
    private boolean isInPlaybackState() {
        return (mMediaPlayer != null &&
                mCurrentState != STATE_ERROR &&
                mCurrentState != STATE_IDLE &&
                mCurrentState != STATE_PREPARING);
    }
    @Override
    public boolean canPause() {
        return mCanPause;
    }
    @Override
    public boolean canSeekBackward() {
        return mCanSeekBack;
    }
    @Override
    public boolean canSeekForward() {
        return mCanSeekForward;
    }
    @Override
    public int getAudioSessionId() {
        if (mAudioSession == 0) {
            MediaPlayer foo = new MediaPlayer();
            mAudioSession = foo.getAudioSessionId();
            foo.release();
        }
        return mAudioSession;
    }
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (mSubtitleWidget != null) {
            mSubtitleWidget.onAttachedToWindow();
        }
    }
    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mSubtitleWidget != null) {
            mSubtitleWidget.onDetachedFromWindow();
        }
    }
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (mSubtitleWidget != null) {
            measureAndLayoutSubtitleWidget();
        }
    }
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (mSubtitleWidget != null) {
            final int saveCount = canvas.save();
            canvas.translate(getPaddingLeft(), getPaddingTop());
            mSubtitleWidget.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }
    private void measureAndLayoutSubtitleWidget() {
        final int width = getWidth() - getPaddingLeft() - getPaddingRight();
        final int height = getHeight() - getPaddingTop() - getPaddingBottom();
        mSubtitleWidget.setSize(width, height);
    }
    @Override
    public void setSubtitleWidget(RenderingWidget subtitleWidget) {
        if (mSubtitleWidget == subtitleWidget) {
            return;
        }
        final boolean attachedToWindow = isAttachedToWindow();
        if (mSubtitleWidget != null) {
            if (attachedToWindow) {
                mSubtitleWidget.onDetachedFromWindow();
            }
            mSubtitleWidget.setOnChangedListener(null);
        }
        mSubtitleWidget = subtitleWidget;
        if (subtitleWidget != null) {
            if (mSubtitlesChangedListener == null) {
                mSubtitlesChangedListener = new RenderingWidget.OnChangedListener() {
                    @Override
                    public void onChanged(RenderingWidget renderingWidget) {
                        invalidate();
                    }
                };
            }
            setWillNotDraw(false);
            subtitleWidget.setOnChangedListener(mSubtitlesChangedListener);
            if (attachedToWindow) {
                subtitleWidget.onAttachedToWindow();
                requestLayout();
            }
        } else {
            setWillNotDraw(true);
        }
        invalidate();
    }
    @Override
    public Looper getSubtitleLooper() {
        return Looper.getMainLooper();
    }
}