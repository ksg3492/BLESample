package com.sunggil.blesample.player.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor;
import com.google.android.exoplayer2.source.BehindLiveWindowException;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.upstream.ByteArrayDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;
import com.sunggil.blesample.network.CustomSSLOkHttpClient;
import com.sunggil.blesample.player.PlayerCallback;
import com.sunggil.blesample.player.PlayerController;

import java.io.File;
import java.util.Objects;

import okhttp3.OkHttpClient;


public class ExoPlayerService extends Service implements PlayerController {
    private boolean mBound = false;
    private IBinder mBinder = new LocalBinder();
    private PlayerCallback playerCallback;

    //ExoPlayer
    private final int MAX_CACHE_SIZE = 100 * 1024 * 1024;    //100Mb, cache folder maximum size
    private final int MAX_FILE_SIZE = 2 * 1024 * 1024;      //2Mb, cache file maximum size

    private SimpleExoPlayer exoPlayer = null;
    private ExoDataSourceFactory dataSourceFactory = null;
    private boolean isPrepared = false;

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
        exoPlayer = null;
        dataSourceFactory = null;
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
                int progress = (int) exoPlayer.getCurrentPosition() / 1000;

                playerCallback.onProgress(progress);
            } catch (Exception e) {

            }

            progressHandler.removeMessages(0);
            progressHandler.sendEmptyMessageDelayed(0, 250);
        }
    };

    private void createPlayer() {
        if (exoPlayer == null) {
            dataSourceFactory = new ExoDataSourceFactory(getApplicationContext(), MAX_CACHE_SIZE, MAX_FILE_SIZE);
            exoPlayer = ExoPlayerFactory.newSimpleInstance(getApplicationContext());
            exoPlayer.addListener(eventListener);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build();
            exoPlayer.setAudioAttributes(audioAttributes, false);
        }
        setVolume(1.0f);
    }

    private Player.EventListener eventListener = new Player.EventListener() {
        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playbackState == Player.STATE_IDLE) {
                Log.e("SG2","onPlayerStateChanged playbackState : Idle");
            } else if (playbackState == Player.STATE_BUFFERING) {
                Log.e("SG2","onPlayerStateChanged playbackState : Buffering");

                playerCallback.onBuffering(true);
            } else if (playbackState == Player.STATE_READY) {
                if (isPrepared) {
                    isPrepared = false;

                    startProgress(playWhenReady);
                    Log.e("SG2","onPlayerStateChanged playbackState : onPrepared");

                    playerCallback.onPrepared((int) exoPlayer.getDuration() / 1000);
                } else {
//                    if (getViewId() == AppConst.View.VIEW_TYPE_YOUTUBE || getViewId() == AppConst.View.VIEW_TYPE_TWITCH) {
//                        if (typeSeekTo != TYPE_SEEK_TO_NONE) {
//                            //buffering finished!
//                            for (HashMap<Integer, PlayerCallback> map : playerCallbackHashMap) {
//                                if (map.containsKey(GlobalStatus.getCurrentViewId())) {
//                                    map.get(GlobalStatus.getCurrentViewId()).onBufferingEnd();
//                                }
//                            }
//
//                            if (typeSeekTo == TYPE_SEEK_TO_RESUME) {
//                                setPlay();
//                            }
//
//                            typeSeekTo = TYPE_SEEK_TO_NONE;
//                            return;
//                        }
//                    }

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

    private static SimpleCache simpleCache = null;
    private static SimpleCache getInstanceSimpleCache(long maxSize) {
        if (simpleCache == null)
            simpleCache = new SimpleCache(new File(Environment.getExternalStorageDirectory() + "/BLESample/", "cache"), new LeastRecentlyUsedCacheEvictor(maxSize));
        return simpleCache;
    }

    class ExoDataSourceFactory implements DataSource.Factory {
        private final Context context;
        private final DefaultDataSourceFactory defaultDatasourceFactory;
        private final long maxFileSize, maxCacheSize;
//        private SimpleCache simpleCache = null;

        ExoDataSourceFactory(Context context, long maxCacheSize, long maxFileSize) {
            super();
            this.context = context;
            this.maxCacheSize = maxCacheSize;
            this.maxFileSize = maxFileSize;
            String userAgent = Util.getUserAgent(context, "BLESample");
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
//            defaultDatasourceFactory = new DefaultDataSourceFactory(this.context, bandwidthMeter,
//                    new DefaultHttpDataSourceFactory(userAgent, bandwidthMeter,
//                            DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS, DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS, true));

            OkHttpClient client = new CustomSSLOkHttpClient().getSSLOkHttpClient();

            defaultDatasourceFactory = new DefaultDataSourceFactory(this.context, bandwidthMeter,
                    new OkHttpDataSourceFactory(client, Util.getUserAgent(context, context.getApplicationInfo().name)));
        }

        @Override
        public DataSource createDataSource() {
//            LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(maxCacheSize);
//            if (simpleCache == null) {
//                String path = Environment.getExternalStorageDirectory() + "/Motrex/cache/";
//                simpleCache = new SimpleCache(new File(path, "exo"), evictor);
//            }
            return new CacheDataSource(getInstanceSimpleCache(maxCacheSize), defaultDatasourceFactory.createDataSource(),
                    new FileDataSource(), new CacheDataSink(simpleCache, maxFileSize),
                    CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, null);
        }

        public void release() {
            if (simpleCache != null) {
                try {
                    simpleCache.release();
                    simpleCache = null;
                } catch (Exception e) {

                }
            }
        }
    }

    //Implements
    @Override
    public void addPlayerCallback(PlayerCallback callback) {
        playerCallback = callback;
    }

    @Override
    public boolean isPlaying() {
        if (exoPlayer == null) {
            return false;
        }

        if (exoPlayer.getPlaybackState() == Player.STATE_READY && exoPlayer.getPlayWhenReady()) {
            return true;
        }

        return false;
    }

    @Override
    public void prepare(boolean isLocal, @Nullable String customKey, String url) {
        createPlayer();
        typeSeekTo = TYPE_SEEK_TO_NONE;
        try {
            MediaSource mediaSource;

            if (isLocal) {
                Context context = getApplicationContext();
                DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, Util.getUserAgent(context, context.getApplicationInfo().name));
//                DataSource.Factory dataSourceFactory = new DefaultHttpDataSourceFactory(Util.getUserAgent(context, context.getApplicationInfo().name));

                mediaSource = new ExtractorMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(url));

                //안되는것들 .mp3 확장자로했을때
//                mediaSource = new DashMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(url));
//                mediaSource = new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(url));
//                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(url));
//                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(url));

            } else {
                return;
//                if (url.toUpperCase().contains("M3U8")) {
//                    Context context = getApplicationContext();
//                    mediaSource = new HlsMediaSource.Factory(new DefaultHttpDataSourceFactory(Util.getUserAgent(context, context.getApplicationInfo().name)))
//                            .createMediaSource(Uri.parse(url));
//                } else {
//                    ExtractorMediaSource.Factory factory = new ExtractorMediaSource.Factory(dataSourceFactory);
//                    factory.setCustomCacheKey(customKey);
//                    mediaSource = factory.createMediaSource(Uri.parse(url));
//                }
            }
            if (exoPlayer.getPlaybackState() != Player.STATE_IDLE) {
                setStop();
            }
            isPrepared = true;

            exoPlayer.setPlayWhenReady(true);
            exoPlayer.prepare(mediaSource);
        } catch (Exception e) {
            isPrepared = false;

            playerCallback.onError("");
        }
    }

    @Override
    public void prepare(boolean isLocal, @Nullable String customKey, byte[] bytes) {
//        createPlayer();
//        typeSeekTo = TYPE_SEEK_TO_NONE;
//        try {
//            MediaSource mediaSource;
//
//            if (isLocal) {
//                Context context = getApplicationContext();
//                DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, Util.getUserAgent(context, context.getApplicationInfo().name));
//                mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(Uri.parse(url));
//            } else {
//                return;
//            }
//
//            if (exoPlayer.getPlaybackState() != Player.STATE_IDLE) {
//                setStop();
//            }
//            isPrepared = true;
//
//            exoPlayer.setPlayWhenReady(true);
//            exoPlayer.prepare(mediaSource);
//        } catch (Exception e) {
//            isPrepared = false;
//
//            playerCallback.onError("");
//        }
    }

    @Override
    public void addByteData() {

    }

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

    @Override
    public void setPause() {
        if (exoPlayer == null) {
            return;
        }

        try {
            if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
                if (exoPlayer.getPlayWhenReady()) {
                    exoPlayer.setPlayWhenReady(false);
                }
            }
        } catch (Exception e) {
        }
    }

    private final int TYPE_SEEK_TO_NONE = 0;
    private final int TYPE_SEEK_TO_PAUSE = 1;
    private final int TYPE_SEEK_TO_RESUME = 2;

    private int typeSeekTo = TYPE_SEEK_TO_NONE;
    @Override
    public void setSeekTo(int sec, boolean wasPlaying) {
        if (exoPlayer == null) {
            return;
        }

        if (exoPlayer.getPlaybackState() == Player.STATE_READY) {
            int max = (int) exoPlayer.getDuration() / 1000;
            if (sec >= max) {
                setStop();
                playerCallback.onCompletion(max);
            } else {
//                if (getViewId() == AppConst.View.VIEW_TYPE_YOUTUBE || getViewId() == AppConst.View.VIEW_TYPE_TWITCH) {
//                    if (!exoPlayer.getPlayWhenReady() && wasPlaying) {
//                        typeSeekTo = TYPE_SEEK_TO_RESUME;
//                    } else {
//                        typeSeekTo = TYPE_SEEK_TO_PAUSE;
//                    }
//
//                    exoPlayer.seekTo(sec * 1000);
//                } else {
                    exoPlayer.seekTo(sec * 1000);
                	if (!exoPlayer.getPlayWhenReady() && wasPlaying) {
                        exoPlayer.setPlayWhenReady(true);
                    }
//                }
            }
        }
    }

    @Override
    public void setStop() {
        startProgress(false);
        try {
            if (exoPlayer != null) {
                exoPlayer.stop(true);
            }
        } catch (Exception e) {
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
    public void release() {
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

        try {
            if (dataSourceFactory != null) {
                dataSourceFactory.release();
                dataSourceFactory = null;
            }
        } catch (Exception e) {
        }
    }

    @Override
    public void setVolume(float volume) {
        if (exoPlayer == null) {
            return;
        }

        try {
            exoPlayer.setVolume(volume);
        } catch (Exception e) {}
    }

    @Override
    public Player getPlayer() {
        if (exoPlayer != null) {
            return exoPlayer;
        }
        return null;
    }

//    public SimpleExoPlayer getExoPlayer() {
//        if (exoPlayer != null) {
//            return exoPlayer;
//        }
//        return null;
//    }


    @Override
    public long getCurrentPosition() {
        if (exoPlayer != null) {
            return exoPlayer.getCurrentPosition();
        }

        return 0;
    }


    @Override
    public void initializePlayer() {
        createPlayer();
    }
}
