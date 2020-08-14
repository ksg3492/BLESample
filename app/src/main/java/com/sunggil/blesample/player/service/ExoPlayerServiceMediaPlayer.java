package com.sunggil.blesample.player.service;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.sunggil.blesample.Util;
import com.sunggil.blesample.player.BufferMediaDataSource;
import com.sunggil.blesample.player.BufferingCallback;
import com.sunggil.blesample.player.PlayerCallback;
import com.sunggil.blesample.player.PlayerController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;


public class ExoPlayerServiceMediaPlayer extends Service implements PlayerController {
    private boolean mBound = false;
    private IBinder mBinder = new LocalBinder();
    private PlayerCallback playerCallback;

    private PrepareThread mPrepareThread;
    private Thread mAddbyteThread;
    private boolean isVideo = false;

    private BufferMediaDataSource mediaDataSource = null;

    public class LocalBinder extends Binder {
        public ExoPlayerServiceMediaPlayer getService() {
            return ExoPlayerServiceMediaPlayer.this;
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

        if (mPrepareThread != null) {
            try {
                mPrepareThread.interrupt();
            } catch (Exception e) { }
            mPrepareThread = null;
        }
    }

    public void setIsVideo(boolean b) {
        isVideo = b;
    }

    public void setSurfaceHolder(SurfaceHolder sh) {
        if (mPrepareThread != null) {
            mPrepareThread.setSurfaceHolder(sh);
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
                int progress = (int)(getCurrentPosition() / 1000);

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
        return mPrepareThread != null && mPrepareThread.isPlaying();
    }


    @Override
    public void prepare(boolean isLocal, @Nullable String fileLength, final String url) {
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

    @Override
    public void setSeekTo(int sec, boolean wasPlaying) {
        mPrepareThread.setSeek(sec * 1000);
    }

    @Override
    public void setStop() {
        startProgress(false);

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
        return (mPrepareThread != null)? mPrepareThread.getCurrentPosition() : 0;
    }


    @Override
    public void initializePlayer() {

    }


    class PrepareThread extends Thread {
        private MediaPlayer mediaPlayer = null;

        private String mFilePath = "";
        private long mPreparedSize = 0L;

        private boolean mIsPause = false;
        private boolean mIsSeek = false;
        private long mSeekTime = 0;

        private long duration = 0L;

        private int fileLength = 0;
        private boolean isPrepared = false;
        private boolean isAddbyteDone = true;
        private SurfaceHolder surfaceHolder;

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
            if (mediaPlayer == null) {
                mediaPlayer = new MediaPlayer();
            }

            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    int duration = 0;
                    try {
                        duration = mp.getDuration() / 1000;

                        Log.e("SG2","Mediaplayer onPrepared");
                        if (playerCallback != null) {
                            playerCallback.onPrepared(duration);
                        }
                    }catch (Exception e) {
                        Log.e("SG2","Mediaplayer onPrepared Error : ", e);
                    }
                }
            });

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    Log.e("SG2","Mediaplayer onCompletion");
                    if (playerCallback != null) {
                        playerCallback.onBuffering(false);
                    }
                }
            });
        }

        public boolean isPrepared() {
            return isPrepared;
        }

        public boolean isAddbyteDone() {
            return isAddbyteDone;
        }

        public void addByte() {
            if (mediaDataSource == null) {
                isAddbyteDone = true;
                return;
            }

            try {
                isAddbyteDone = false;
                Log.e("SG2",Util.Companion.isMainLooper() + " ] addByte()");
                byte[] data = readFileToByteArray(new File(mFilePath));

                int index = mediaDataSource.getWriteIndex();
                byte[] newByte = new byte[data.length - index];
                System.arraycopy(data, index, newByte, 0, data.length - index);

                mediaDataSource.inputData(newByte, newByte.length);
                isAddbyteDone = true;
            }catch (Exception e) {
                Log.e("SG2","addByte() Error : " , e);
            }
        }

        private byte[] readFileToByteArray(File file){
            FileInputStream fis = null;
            // Creating a byte array using the length of the file
            // file.length returns long which is cast to int
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

        public void setSurfaceHolder(SurfaceHolder sh) {
            surfaceHolder = sh;
        }

        private void prepareMedia() {
            try {
                isPrepared = false;

                createMedia();
                mediaPlayer.reset();
                mediaDataSource = new BufferMediaDataSource(fileLength, new BufferingCallback() {
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
                addByte();
                if (isVideo) {
                    mediaPlayer.setDisplay(surfaceHolder);
                } else {
                    mediaPlayer.setDisplay(null);
                }
                Log.e("SG2", Util.Companion.isMainLooper() + "] Mediaplayer onPrepare 요청");
                mediaPlayer.setDataSource(mediaDataSource);
//                mediaPlayer.setDataSource(mFilePath);
                mediaPlayer.prepareAsync();
                isPrepared = true;
            } catch (Exception e) {
                Log.e("SG2","prepareInternal Error : " , e);
                return;
            }

            startProgress(true);
//            mediaPlayer.start();
        }

        public long getCurrentPosition() {
            try {
                if (mediaPlayer != null) {
                    return mediaPlayer.getCurrentPosition();
                }
            } catch (Exception e) {
                Log.e("SG2","getCurrentPosition Error : " , e);
            }

            return 0;
        }

        public void setPlay() {
            try {
                mediaPlayer.start();
                if (playerCallback != null) {
                    playerCallback.onPlayed();
                }
            }catch (Exception e) {

            }
        }

        public void setPause() {
            try {
                mediaPlayer.pause();
                if (playerCallback != null) {
                    playerCallback.onPaused();
                }
            }catch (Exception e) {

            }
        }

        public void setSeek(long seekTime) {
            mSeekTime = seekTime * 1000;
            mIsSeek = true;
        }

        public boolean isPlaying() {
            if (mediaPlayer != null) {
                return mediaPlayer.isPlaying();
            }

            return false;
        }

        public void setStop() {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
            }
            if (playerCallback != null) {
                playerCallback.onPaused();
            }
        }

        private void setRelease() {
            if (mediaPlayer == null) {
                return;
            }

            try {
                mediaDataSource.close();
            } catch (Exception e) { }
            try {
                mediaPlayer.release();
            } catch (Exception e) { }
            mediaPlayer = null;

            if (playerCallback != null) {
                playerCallback.onPaused();
            }
        }
    }
}
