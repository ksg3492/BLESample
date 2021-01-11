package com.sunggil.blesample.player.service;

import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp4.Mp4Extractor;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.sunggil.blesample.player.BufferingCallback;
import com.sunggil.blesample.player.InputStreamDataSource;
import com.sunggil.blesample.player.PlayerCallback;
import com.sunggil.blesample.player.PlayerController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;


public class ExoPlayerService extends Service implements PlayerController, SurfaceHolder.Callback {
    private boolean mBound = false;
    private IBinder mBinder = new LocalBinder();
    private PlayerCallback playerCallback;

    private PrepareThread mPrepareThread;
    private Thread mAddbyteThread;
    private boolean isVideo = false;
    private SurfaceHolder mSurfaceHolder = null;

    public class LocalBinder extends Binder {
        public ExoPlayerService getService() {
            return ExoPlayerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        mBound = true;

        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mBound = false;
        startProgress(false);
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mSurfaceHolder != null) {
            try {
                mSurfaceHolder.removeCallback(this);
            }catch (Exception e) { }
        }

        if (mPrepareThread != null) {
            try {
                mPrepareThread.interrupt();
            } catch (Exception e) { }
            mPrepareThread = null;
        }
    }

    private void startProgress(boolean start) {
        if (start) {
            progressHandler.removeMessages(0);
            progressHandler.sendEmptyMessage(0);
        } else {
            progressHandler.removeMessages(0);
        }
    }

    private Handler progressHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            try {
                int progress = (int) getCurrentPosition() / 1000;

                playerCallback.onProgress(progress);
            } catch (Exception e) {

            }

            progressHandler.removeMessages(0);
            progressHandler.sendEmptyMessageDelayed(0, 250);
        }
    };

    //Implements
    @Override
    public void addPlayerCallback(PlayerCallback callback) {
        playerCallback = callback;
    }

    @Override
    public boolean isPlaying() {
        if (mPrepareThread != null) {
            return mPrepareThread.isPlaying();
        }
        return false;
    }

    @Override
    public void prepare(boolean isLocal, @Nullable String fileLength, String url) {

        int length = 0;
        try {
            if (fileLength != null) {
                length = Integer.parseInt(fileLength);
            }
        }catch (Exception e ) {

        }

        addByteHandler.removeMessages(0);
        if (mAddbyteThread != null) {
            mAddbyteThread.interrupt();
            mAddbyteThread = null;
        }

        if (mPrepareThread != null) {
            release();
            mPrepareThread.interrupt();
            mPrepareThread = null;
        }

        mPrepareThread = new PrepareThread(url, length);
        mPrepareThread.start();
    }

    @Override
    public void prepare(boolean isLocal, @Nullable String customKey, byte[] bytes) {
    }

    class PrepareThread extends Thread {
        private SimpleExoPlayer exoPlayer = null;
        private InputStreamDataSource inputStreamDataSource = null;

        private int MIN_BUFFER_MILLISECOND = 1000;
        private int MAX_BUFFER_MILLISECOND = 2000;

        private String mFilePath = "";

        private int fileLength = 0;
        private boolean isPrepared = false;
        private boolean isDataSourcePrepared = false;
        private boolean isAddbyteDone = true;

        public PrepareThread(String url, int length) {
            mFilePath = url;
            fileLength = length;
            setPriority(Thread.MAX_PRIORITY);
        }

        @Override
        public void run() {
            super.run();

            prepareMedia();
        }

        private void createMedia() {
            if (exoPlayer == null) {
//            DefaultLoadControl loadControl = new DefaultLoadControl.Builder().setBufferDurationsMs(100*1024, 200*1024, 1024, 1024).createDefaultLoadControl();
                DefaultLoadControl loadControl = new DefaultLoadControl.Builder().setBufferDurationsMs(MIN_BUFFER_MILLISECOND, MAX_BUFFER_MILLISECOND, MIN_BUFFER_MILLISECOND, MIN_BUFFER_MILLISECOND).createDefaultLoadControl();

                exoPlayer = ExoPlayerFactory.newSimpleInstance(getApplicationContext(), new DefaultTrackSelector(), loadControl);
//            exoPlayer = ExoPlayerFactory.newSimpleInstance(getApplicationContext());

                exoPlayer.addListener(eventListener);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.CONTENT_TYPE_MUSIC)
                        .build();
                exoPlayer.setAudioAttributes(audioAttributes, false);
            }
            setVolume(1.0f);
        }

        public boolean isPrepared() {
            return isDataSourcePrepared;
        }

        public boolean isAddbyteDone() {
            return isAddbyteDone;
        }

        public void addByte() {
            if (inputStreamDataSource == null) {
                return;
            }

            try {
                isAddbyteDone = false;
                byte[] data = readFileToByteArray(new File(mFilePath));

                int index = inputStreamDataSource.getWriteIndex();
                byte[] newByte = new byte[data.length - index];
                System.arraycopy(data, index, newByte, 0, data.length - index);

                inputStreamDataSource.inputData(newByte, newByte.length);
                isAddbyteDone = true;
            }catch (Exception e) {
                Log.e("SG2","addByte() Error : " , e);
            }
        }


        private byte[] readFileToByteArray(File file){
            FileInputStream fis = null;
            byte[] bArray = new byte[(int) file.length()];
            try{
                fis = new FileInputStream(file);
                fis.read(bArray);
                fis.close();
            }catch(IOException e){
                Log.e("SG2","readFileToByteArray Error : " , e);
            }
            return bArray;
        }

        private void prepareMedia() {
            isPrepared = false;
            isDataSourcePrepared = false;

            createMedia();
            typeSeekTo = TYPE_SEEK_TO_NONE;
            try {
                inputStreamDataSource = new InputStreamDataSource(mFilePath, fileLength, new BufferingCallback() {
                    @Override
                    public void onBuffering(boolean buffering) {
                        if (playerCallback != null) {
                            playerCallback.onBuffering(buffering);
                        }
                    }

                    @Override
                    public void onPreload(int percent) {
                        if (playerCallback != null) {
                            playerCallback.onPreload(percent);
                        }
                    }
                });

                try {
                    inputStreamDataSource.open(null);
                } catch (IOException e) {
                    Log.e("SG2","mediaSource.open Error : " , e);
                }

                DataSource.Factory factory = new DataSource.Factory() {

                    @Override
                    public DataSource createDataSource() {
                        return inputStreamDataSource;
                    }
                };
//            MediaSource audioSource = new ExtractorMediaSource(inputStreamDataSource.getUri(), factory, new DefaultExtractorsFactory(), null, null);
                MediaSource audioSource = new ProgressiveMediaSource.Factory(factory, Mp4Extractor.FACTORY).createMediaSource(Uri.parse(mFilePath));

                addByte();

                if (exoPlayer.getPlaybackState() != Player.STATE_IDLE) {
                    setStop();
                }

                if (isVideo) {
                    exoPlayer.setVideoSurfaceHolder(mSurfaceHolder);
                } else {
                    exoPlayer.setVideoSurfaceHolder(null);
                }

                exoPlayer.setPlayWhenReady(false);
                exoPlayer.prepare(audioSource);
                isDataSourcePrepared = true;
                isPrepared = true;
            } catch (Exception e) {
                isPrepared = false;

                playerCallback.onError("");
            }
        }

        public void setSurfaceHolder(SurfaceHolder holder) {
            if (isVideo) {
                if (exoPlayer != null) {
                    exoPlayer.setVideoSurfaceHolder(holder);
                }
            }
        }

        public long getCurrentPosition() {
            try {
                if (exoPlayer != null) {
                    return exoPlayer.getCurrentPosition();
                }
            } catch (Exception e) {
                Log.e("SG2","getCurrentPosition Error : " , e);
            }

            return 0;
        }

        public void setPlay() {
            if (exoPlayer == null) {
                return;
            }

            try {
                setVolume(1.0f);
                if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                    if (!exoPlayer.getPlayWhenReady()) {
                        exoPlayer.setPlayWhenReady(true);
                    }
                }
            } catch (Exception e) {
            }
        }

        public void setPause() {
            if (exoPlayer == null) {
                return;
            }

            try {
                setVolume(0.0f);
                if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                    if (exoPlayer.getPlayWhenReady()) {
                        exoPlayer.setPlayWhenReady(false);
                    }
                }
            } catch (Exception e) {
            }
        }

        public void setSeek(long sec, boolean wasPlaying) {
            if (exoPlayer == null) {
                return;
            }

            if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                int max = (int) exoPlayer.getDuration() / 1000;
                if (sec >= max) {
                    setStop();
                    playerCallback.onCompletion(max);
                } else {
                    exoPlayer.seekTo(sec * 1000);
                    if (!exoPlayer.getPlayWhenReady() && wasPlaying) {
                        exoPlayer.setPlayWhenReady(true);
                    }
                }
            }
        }

        public boolean isPlaying() {
            if (exoPlayer == null) {
                return false;
            }

            if (exoPlayer.getPlaybackState() == Player.STATE_READY && exoPlayer.getPlayWhenReady()) {
                return true;
            }

            return false;
        }

        public void setStop() {
            startProgress(false);
            try {
                if (exoPlayer != null) {
                    exoPlayer.stop(true);
                }
            } catch (Exception e) {
            }
        }

        private void setRelease() {
            try {
                typeSeekTo = TYPE_SEEK_TO_NONE;
                startProgress(false);
                if (exoPlayer != null) {
                    exoPlayer.removeListener(eventListener);
                    if (exoPlayer.getPlayWhenReady()) {
                        exoPlayer.setVolume(0.0f);
                        exoPlayer.setPlayWhenReady(false);
                    }
                    if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                        exoPlayer.release();
                    }
                    exoPlayer = null;
                }
            } catch (Exception e) {
            }
        }

        private Player.EventListener eventListener = new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (playbackState == Player.STATE_IDLE) {
                    Log.e("SG2","onPlayerStateChanged playbackState : Idle");
                } else if (playbackState == Player.STATE_BUFFERING) {
                    Log.e("SG2","onPlayerStateChanged playbackState : Buffering");
                } else if (playbackState == Player.STATE_READY) {
                    if (isPrepared) {
                        isPrepared = false;

                        startProgress(playWhenReady);
                        Log.e("SG2","onPlayerStateChanged playbackState : onPrepared");

                        playerCallback.onPrepared((int) exoPlayer.getDuration() / 1000);
                    } else {
                        if (playWhenReady) {
                            startProgress(true);
                            Log.e("SG2","onPlayerStateChanged playbackState : onPlayed");
                            playerCallback.onPlayed();
                        } else {
                            startProgress(false);
                            Log.e("SG2","onPlayerStateChanged playbackState : onPaused");
                            playerCallback.onPaused();
                        }
                    }

                } else if (playbackState == Player.STATE_ENDED) {
                    Log.e("SG2","onPlayerStateChanged playbackState : onCompletion");
                    if (playerCallback != null) {
                        playerCallback.onBuffering(false);
                    }
                    startProgress(false);
                    playerCallback.onCompletion((int) exoPlayer.getDuration() / 1000);
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                Log.e("SG2","onPlayerStateChanged playbackState : onPlayerError : ", error);
                if (error != null && error instanceof ExoPlaybackException && error.getCause() instanceof BehindLiveWindowException) {
                    setPlay();
                }
                startProgress(false);
                String errorMessage = "";
                try {
                    switch (error.type) {
                        case ExoPlaybackException.TYPE_SOURCE:
                            errorMessage = error.getSourceException().getMessage();
                            break;

                        case ExoPlaybackException.TYPE_RENDERER:
                            errorMessage = error.getSourceException().getMessage();
                            break;

                        case ExoPlaybackException.TYPE_UNEXPECTED:
                            errorMessage = error.getSourceException().getMessage();
                            break;
                    }
                } catch (Exception e) {}

                if (errorMessage == null) {
                    errorMessage = "";
                }

                playerCallback.onError(errorMessage);
            }
        };
    }

    @Override
    public void addByteData() {
        if (mPrepareThread != null && mPrepareThread.isPrepared() && mPrepareThread.isAddbyteDone()) {
            addByteHandler.removeMessages(0);
            if (mAddbyteThread != null) {
                mAddbyteThread.interrupt();
                mAddbyteThread = null;
            }

            mAddbyteThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (mPrepareThread != null && mPrepareThread.isPrepared()) {
                        mPrepareThread.addByte();
                    }
                }
            });
            mAddbyteThread.start();
        } else {
            addByteHandler.removeMessages(0);
            addByteHandler.sendEmptyMessageDelayed(0, 500);
        }
    }

    private Handler addByteHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            addByteData();
        }
    };

    private MediaSource createMediaSourceFromByteArray(byte[] data) {
        final ByteArrayDataSource byteArrayDataSource = new ByteArrayDataSource(data);
        DataSource.Factory factory = new DataSource.Factory() {
            @Override
            public DataSource createDataSource() {
                return byteArrayDataSource;
            }
        };
        MediaSource mediaSource = new ExtractorMediaSource.Factory(factory)
                .setExtractorsFactory(new DefaultExtractorsFactory())
                .createMediaSource(Uri.EMPTY);

        return Objects.requireNonNull(mediaSource, "MediaSource cannot be null");
    }

    @Override
    public void setPlay() {
        if (mPrepareThread != null) {
            mPrepareThread.setPlay();
        }
    }

    @Override
    public void setPause() {
        if (mPrepareThread != null) {
            mPrepareThread.setPause();
        }
    }

    private final int TYPE_SEEK_TO_NONE = 0;
    private final int TYPE_SEEK_TO_PAUSE = 1;
    private final int TYPE_SEEK_TO_RESUME = 2;

    private int typeSeekTo = TYPE_SEEK_TO_NONE;
    @Override
    public void setSeekTo(int sec, boolean wasPlaying) {
        if (mPrepareThread != null) {
            mPrepareThread.setSeek(sec * 1000, wasPlaying);
        }
    }

    @Override
    public void setStop() {
        if (mPrepareThread != null) {
            mPrepareThread.setStop();
        }
    }

    @Override
    public void setPlayPauseUI(boolean isPlay) {
        try {
            if (isPlay) {
                playerCallback.onPlayed();
            } else {
                playerCallback.onPaused();
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void setIsVideo(boolean b) {
        isVideo = b;
    }

    public void setSurfaceHolder(SurfaceHolder sh) {
        mSurfaceHolder = sh;
        mSurfaceHolder.addCallback(this);
    }

    @Override
    public void release() {
        try {
            startProgress(false);

            if (mPrepareThread != null) {
                mPrepareThread.setStop();
                mPrepareThread.setRelease();
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void setVolume(float volume) {
    }

    @Override
    public Player getPlayer() {
        return null;
    }

    @Override
    public long getCurrentPosition() {
        if (mPrepareThread != null) {
            return mPrepareThread.getCurrentPosition();
        }

        return 0;
    }


    @Override
    public void initializePlayer() {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (mPrepareThread != null) {
            mPrepareThread.setSurfaceHolder(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (mPrepareThread != null) {
            mPrepareThread.setSurfaceHolder(holder);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {

    }
}
