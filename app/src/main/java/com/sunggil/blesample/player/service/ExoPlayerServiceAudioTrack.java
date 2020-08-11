package com.sunggil.blesample.player.service;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;
import com.sunggil.blesample.player.PlayerCallback;
import com.sunggil.blesample.player.PlayerController;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.nio.ByteBuffer;


public class ExoPlayerServiceAudioTrack extends Service implements PlayerController {
    private boolean mBound = false;
    private IBinder mBinder = new LocalBinder();
    private PlayerCallback playerCallback;

    private boolean isPrepared = false;

    private final long K_TIME_OUT_US = 10 * 1000;

    public static final String MIMETYPE_AUDIO = "audio/";

    private final int THREAD0 = 0;
    private final int THREAD1 = 1;

    private int mCurrentThread = -1;
    private PrepareThread mPrepareThread0;
    private PrepareThread mPrepareThread1;
    private Handler mChangehandler = null;
    private long syncTime = 0L;

    private long mTotalFileSize = 0L;
    private long mTotalDuration = 0L;

    public class LocalBinder extends Binder {
        public ExoPlayerServiceAudioTrack getService() {
            return ExoPlayerServiceAudioTrack.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mChangehandler = new Handler();
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
    }

    public void setTotalFileSize(long size) {
        mTotalFileSize = size;
    }

    public void setTotalDuration(long duration) {
        mTotalDuration = duration;
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
//                int progress = (int) mAudioTrack.setPlaybackPositionUpdateListener();
//
//                playerCallback.onProgress(progress);
            } catch (Exception e) {

            }

            progressHandler.removeMessages(0);
            progressHandler.sendEmptyMessageDelayed(0, 250);
        }
    };

    private void createPlayer() {
    }

    //Implements
    @Override
    public void addPlayerCallback(PlayerCallback callback) {
        playerCallback = callback;
    }

    @Override
    public boolean isPlaying() {
        if (mCurrentThread == THREAD0) {
            return mPrepareThread0 != null && mPrepareThread0.getStatus() == PrepareThread.AUDIO_STATUS_PLAYING;
        } else if (mCurrentThread == THREAD1) {
            return mPrepareThread1 != null && mPrepareThread1.getStatus() == PrepareThread.AUDIO_STATUS_PLAYING;
        }

        return false;
    }

    @Override
    public void prepare(boolean isLocal, @Nullable String customKey, final String url) {
        if (mCurrentThread == -1 || mCurrentThread == THREAD1) {
            //스레드 0 종료
            //스레드 0 셋업

            if (mPrepareThread0 != null) {
                mPrepareThread0.interrupt();
                mPrepareThread0 = null;
            }

            mPrepareThread0 = new PrepareThread(url);
            mPrepareThread0.start();
        } else {
            //스레드 1 종료
            //스레드 1 셋업

            if (mPrepareThread1 != null) {
                mPrepareThread1.interrupt();
                mPrepareThread1 = null;
            }

            mPrepareThread1 = new PrepareThread(url);
            mPrepareThread1.start();
        }
    }

    public void syncPlay() {
        if (mCurrentThread == -1 || mCurrentThread == THREAD1) {
            Log.e("SG2","Exoplayer 스위칭 : " + syncTime);
            mPrepareThread0.play(THREAD0);
        } else {
            Log.e("SG2","Exoplayer 스위칭 : " + syncTime);
            mPrepareThread1.play(THREAD1);
        }
    }

    @Override
    public void prepare(boolean isLocal, @Nullable String customKey, byte[] bytes) {
    }

    @Override
    public void addByteData() {

    }

    @Override
    public void setPlay() {
        if (mCurrentThread == -1 || mCurrentThread == THREAD0) {
            mPrepareThread0.setPlay();
        } else {
            mPrepareThread1.setPlay();
        }
    }

    @Override
    public void setPause() {
        if (mCurrentThread == -1 || mCurrentThread == THREAD0) {
            mPrepareThread0.setPause();
        } else {
            mPrepareThread1.setPause();
        }
    }

    private final int TYPE_SEEK_TO_NONE = 0;
    private final int TYPE_SEEK_TO_PAUSE = 1;
    private final int TYPE_SEEK_TO_RESUME = 2;

    private int typeSeekTo = TYPE_SEEK_TO_NONE;


    @Override
    public void setSeekTo(int sec, boolean wasPlaying) {
        if (mCurrentThread == -1 || mCurrentThread == THREAD0) {
            mPrepareThread0.setSeek(sec * 1000);
        } else {
            mPrepareThread1.setSeek(sec * 1000);
        }
    }

    @Override
    public void setStop() {
        startProgress(false);

        if (mPrepareThread0 != null) {
            mPrepareThread0.setStopThread();
        }
        if (mPrepareThread1 != null) {
            mPrepareThread1.setStopThread();
        }
        mCurrentThread = -1;
        syncTime = 0;
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
            setStop();

            if (mPrepareThread0 != null) {
                mPrepareThread0.releaseResources(false);
            }
            if (mPrepareThread1 != null) {
                mPrepareThread1.releaseResources(false);
            }
            syncTime = 0;
            mCurrentThread = -1;
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
        if (mCurrentThread == THREAD0) {
            return (mPrepareThread0 != null)? mPrepareThread0.getCurrentPosition() : 0;
        } else if (mCurrentThread == THREAD1) {
            return (mPrepareThread1 != null)? mPrepareThread1.getCurrentPosition() : 0;
        }

        return 0;
    }


    @Override
    public void initializePlayer() {
        createPlayer();
    }


    class PrepareThread extends Thread {
        private MediaExtractor mExtractor;
        private MediaCodec mMediaCodec;
        private AudioTrack mAudioTrack;

        private int mSampleRate;
        private int mInputBufIndex;

        private ByteBuffer[] mCodecInputByteBuffers;
        private ByteBuffer[] mCodecOutputByteBuffers;

        private MediaCodec.BufferInfo mBufferInfo;

        boolean syncWait = true;
        private Thread mPlayThread;

        private String mFilePath = "";
        private long mPreparedSize = 0L;

        private boolean mIsPause = false;
        private boolean mIsSeek = false;
        private long mSeekTime = 0;

        private boolean mSawInputEOS;
        private boolean mIsForceStop = false;
        private int mNoOutputCounter;
        private int mNoOutputCounterLimit;

        private int mStatus;

        private long duration = 0L;

        public static final int AUDIO_STATUS_STOPPED = 0;
        public static final int AUDIO_STATUS_PREPARE = 1;
        public static final int AUDIO_STATUS_PLAYING = 2;
        public static final int AUDIO_STATUS_PAUSE   = 3;

        public PrepareThread(String url) {
            mFilePath = url;
            setPriority(Thread.MAX_PRIORITY);
        }

        public void play(int type) {
            mCurrentThread = type;

            startProgress(true);

            mPlayThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (syncTime > 0) {
                        mIsSeek = true;
                    }

                    changeHandler.removeMessages(0);
                    changeHandler.sendEmptyMessageDelayed(0, 1000);

                    playLoop();
                }
            });
            mPlayThread.setPriority(Thread.MAX_PRIORITY);
            mPlayThread.start();
        }

        @Override
        public void run() {
            super.run();

            prepareInternal();
        }

        public int getStatus() {
            return mStatus;
        }

        private void prepareInternal() {
            mExtractor = new MediaExtractor();
            try {
                File file = new File(mFilePath);
                FileInputStream fis = null;
                FileDescriptor fd = null;
                try {
                    fis = new FileInputStream(file);
                    mPreparedSize = file.length();
                } catch (Exception e) {
                    Log.e("SG2", "", e);
                }

                try {
                    fd = fis.getFD();
                } catch (Exception e) {
                    Log.e("SG2", "", e);
                }

                mExtractor.setDataSource(fd);
            } catch (Exception e) {
                Log.e("SG2","prepareInternal Error : " , e);
                return;
            }

            int trackCount = mExtractor.getTrackCount();
            MediaFormat format = null;
            String mime = null;
            int selectedTrack = 0;
//        for (int index = 0; index < trackCount; index++) {
//            format = mExtractor.getTrackFormat(index);
//            mime = format.getString(MediaFormat.KEY_MIME);
//            if (mime.startsWith(MIMETYPE_AUDIO))  // 오디오 트랙의 경우만 사용
//            {
//                selectedTrack = index;
//                break;
//            }
//        }
            try {
                format = mExtractor.getTrackFormat(0);
            }catch (Exception e) {
                Log.e("SG2","getTrackFormat Error : " , e);
                return;
            }

            mime = format.getString(MediaFormat.KEY_MIME);

            duration = (int)(format.getLong(MediaFormat.KEY_DURATION) / 1000);  // 밀리세컨드 단위
//            Log.e("SG2","prepareInternal duration : " + duration);

            try {
                mMediaCodec = MediaCodec.createDecoderByType(mime);
            } catch (Exception e) {
                Log.e("SG2","prepareInternal mMediaCodec Error : " , e);
                return;
            }

            mMediaCodec.configure(format, null, null, 0);
            mMediaCodec.start();
            mCodecInputByteBuffers = mMediaCodec.getInputBuffers();
            mCodecOutputByteBuffers = mMediaCodec.getOutputBuffers();

            mSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int bufferSize = AudioTrack.getMinBufferSize(mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 4;
            int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mSampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
            mAudioTrack.play();

            mExtractor.selectTrack(selectedTrack);

            mBufferInfo = new MediaCodec.BufferInfo();

            mNoOutputCounter = 0;
            mNoOutputCounterLimit = 50;

//            if (playerCallback != null) {
//                playerCallback.onPrepared(0);
//            }

            syncPlay();
        }

        public void setExtractor(MediaExtractor ext) {
            mExtractor = ext;
        }

        private void playLoop() {
            syncWait = true;
            mInputBufIndex = 0;
            mIsPause = false;
//            mIsSeek = false;

            while (!mSawInputEOS && mNoOutputCounter < mNoOutputCounterLimit && !mIsForceStop) {
                if (syncWait) {
                    mSeekTime = syncTime;
                    continue;
                }

                if (mIsPause) {
                    if (mStatus != AUDIO_STATUS_PAUSE) {
                        mStatus = AUDIO_STATUS_PAUSE;
                    }
                    continue;
                }
                mNoOutputCounter++;
                if (mIsSeek) {
                    synchronized (mExtractor) {
                        Log.e("SG2","playLoop Seek 동작 : " +mSeekTime);
                        mExtractor.seekTo(mSeekTime, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                        mIsSeek = false;
//                        continue;
                    }
                }

                try {
                    mInputBufIndex = mMediaCodec.dequeueInputBuffer(K_TIME_OUT_US);

                    if (mInputBufIndex >= 0) {
                        ByteBuffer dstBuf = mCodecInputByteBuffers[mInputBufIndex];
//                    ByteBuffer dstBuf = mMediaCodec.getInputBuffer(mInputBufIndex);

                        int sampleSize = mExtractor.readSampleData(dstBuf, 0);

                        long presentationTimeUs = 0;

                        if (sampleSize < 0) {
                            mSawInputEOS = true;
                            sampleSize = 0;
                        } else {
                            presentationTimeUs = mExtractor.getSampleTime();
                        }

                        mMediaCodec.queueInputBuffer(mInputBufIndex, 0, sampleSize, presentationTimeUs, mSawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);

                        if (!mSawInputEOS) {
                            mExtractor.advance();
                        }
                    }

                    if (mMediaCodec == null) {
                        return;
                    }

                    int res = mMediaCodec.dequeueOutputBuffer(mBufferInfo, K_TIME_OUT_US);
                    if (res >= 0) {
                        if (mBufferInfo.size > 0) {
                            mNoOutputCounter = 0;
                        }

                        byte[] chunk = new byte[mBufferInfo.size];
                        int chunkLength = chunk.length;
//                    mCodecOutputByteBuffer = mMediaCodec.getOutputBuffer(res);
//                    if (mCodecOutputByteBuffer != null) {
//                        mCodecOutputByteBuffer.get(chunk);
//                        mCodecOutputByteBuffer.clear();
//                    }
                        mCodecOutputByteBuffers[res].get(chunk);
                        mCodecOutputByteBuffers[res].clear();

                        if (chunk.length > 0) {
                            mAudioTrack.write(chunk, 0, chunkLength);

                            syncTime = mExtractor.getSampleTime();

                            mStatus = AUDIO_STATUS_PLAYING;
                        }

                        try {
                            mMediaCodec.releaseOutputBuffer(res, false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        mCodecOutputByteBuffers = mMediaCodec.getOutputBuffers();
                    } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat format = mMediaCodec.getOutputFormat();

                        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        int channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        int channelOut = 0;

                        if (channelCount == 1) {
                            channelOut = AudioFormat.CHANNEL_OUT_MONO;
                        } else {
                            channelOut = AudioFormat.CHANNEL_OUT_STEREO;
                        }

                        mAudioTrack = new AudioTrack(AudioManager.STREAM_MUSIC
                                , sampleRate
                                , channelOut
                                , AudioFormat.ENCODING_PCM_16BIT
                                , AudioTrack.getMinBufferSize(sampleRate, channelOut, AudioFormat.ENCODING_PCM_16BIT)
                                , AudioTrack.MODE_STREAM);

                        mAudioTrack.play();
                    }
                } catch (Exception e) {
                    Log.e("SG2", "while in Exception  : ", e);
                }
            }

            if (!mIsForceStop) {
                long totalSize = mTotalFileSize;
                long preparedSize = mPreparedSize;
                Log.e("SG2", "끝 totalSize    : "+totalSize);
                Log.e("SG2", "끝 preparedSize : "+preparedSize);
                if (totalSize == preparedSize) {
                    //완전 끝남
                    Log.e("SG2", "SONG COMPLETION");
                } else {
                    Log.e("SG2", "SONG BUFFER WAIT");
                    if (playerCallback != null) {
                        playerCallback.onBufferingEnd(duration);
                    }
                }
            }

            releaseResources(true);

            mStatus = AUDIO_STATUS_STOPPED;
            mIsPause = false;

            if (mNoOutputCounter >= mNoOutputCounterLimit) {
                Log.e("SG2", "Error, noOutputCounter : " + mNoOutputCounter + ", noOutputCounterLimit : " + mNoOutputCounterLimit);
            } else {
                if (!mIsForceStop) {

                }
            }
        }

        public long getCurrentPosition() {
            try {
                return mExtractor.getSampleTime();
//                return (mAudioTrack.getPlaybackHeadPosition() / mAudioTrack.getSampleRate()) * 1000;
            } catch (Exception e) {
                Log.e("SG2","getCurrentPosition Error : " , e);
            }

            return 0;
        }

        private void releaseResources(Boolean release) {
            if (mExtractor != null) {
                mExtractor.release();
                mExtractor = null;
            }

            if (mMediaCodec != null) {
                if (release) {
                    try {
                        mMediaCodec.stop();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    try{
                        mMediaCodec.release();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    mMediaCodec = null;
                }
            }

            if (mAudioTrack != null) {
                try {
                    mAudioTrack.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                try {
                    mAudioTrack.release();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                mAudioTrack = null;
            }
        }

        public void setPlay() {
            mIsPause = false;
        }

        public void setPause() {
            mIsPause = true;
        }

        public void setSeek(long seekTime) {
            mSeekTime = seekTime * 1000;
            mIsSeek = true;
        }

        public void setStopThread() {
            mIsForceStop = true;
        }
    }


    private Handler changeHandler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);

            if (mCurrentThread == THREAD0) {
                mPrepareThread0.syncWait = false;
                if (mPrepareThread1 != null) {
                    mPrepareThread1.setStopThread();
                }

                Log.e("SG2","mPrepareThread0.syncWait = false;");
            } else if (mCurrentThread == THREAD1) {
                mPrepareThread1.syncWait = false;
                if (mPrepareThread0 != null) {
                    mPrepareThread0.setStopThread();
                }

                Log.e("SG2","mPrepareThread1.syncWait = false;");
            }
        }
    };
}
