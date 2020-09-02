package com.sunggil.blesample.player;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

public class InputStreamDataSource implements DataSource {
    private InputStream inputStream;
    private long bytesRemaining;

    private int readed = 0;

    private String mFilePath;


    private volatile byte[] mBuffer;    // byte array for whole media
    private int fileSize;

    private int writeIndex = 0;

    private BufferingCallback callback;
    private boolean isStop = false;

    public InputStreamDataSource(String path, int fileSize, BufferingCallback callback) {
        mFilePath = path;
        this.fileSize = fileSize;
        this.callback = callback;
        mBuffer = new byte[this.fileSize];
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {

    }

    @Override
    public long open(DataSpec dataSpec) throws IOException {
        bytesRemaining = fileSize;
        File file = new File(mFilePath);
        if (file.exists()) {
            inputStream = new FileInputStream(file);
        }

        readed = 0;

        return bytesRemaining;
    }

    public int getWriteIndex() {
        return writeIndex;
    }

    public void inputData(byte[] data, int length) {
        if (mBuffer != null) {
            System.arraycopy(data, 0, mBuffer, writeIndex, length);

            try {
                inputStream = new FileInputStream(new File(mFilePath));
                inputStream.skip(readed);
            } catch (Exception e) {
                Log.e("SG2","inputData Error : " , e);
            }

            writeIndex += length;

            float per = writeIndex * 1.00f / fileSize * 1.00f;
            int percent = (int)(per * 100);

//                int percent = (writeIndex * 100) / fileSize;

            if (callback != null) {
                callback.onPreload(percent);
            }
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int readLength) throws IOException {
        if (readed == writeIndex) {
            return -1;
        }

//        Log.e("SG2","InputStreamDataSource buffer size : " + buffer.length);
//        Log.e("SG2","InputStreamDataSource offset : " + offset + ", readLength : "+ readLength);

        if (readed + readLength > writeIndex) {
            if (fileSize == writeIndex) {
                Log.e("SG2","MediaDataSource loaded data End.");
                int rest = (int) (writeIndex - readed);

                System.arraycopy(mBuffer, (int) readed, buffer, offset, rest);

                return rest;
            } else {
                while(!isStop) {
                    if (readed + readLength <= writeIndex) {
                        if (callback != null) {
                            callback.onBuffering(false);
                        }
                        break;
                    }

                    if (callback != null) {
                        callback.onBuffering(true);
                    }

                    try {
                        Thread.sleep(100);    // wait a second for downloading.
                    } catch (InterruptedException e) { }
                }
            }
        }

        if (mBuffer != null) {
//            System.arraycopy(mBuffer, readed, buffer, offset, readLength);
            int r = inputStream.read(buffer, offset, readLength);
            readed += readLength;
//            readLength = inputStream.read(mBuffer, readed, readLength);
            return readLength;
        }

        return 0;

//        if (readLength == 0) {
//            return 0;
//        } else if (bytesRemaining == 0) {
//            return C.RESULT_END_OF_INPUT;
//        }
//
//        int bytesToRead = bytesRemaining == C.LENGTH_UNSET ? readLength : (int) Math.min(bytesRemaining, readLength);
//
//        while (!isStop) {
//            if ((readed + bytesToRead) > writeIndex) {
//                try {
//                    Thread.sleep(100);    // wait a second for downloading.
//                } catch (InterruptedException e) { }
//            } else {
//                break;
//            }
//        }
//
//        System.arraycopy(mBuffer, readed, buffer, offset, bytesToRead);
//        readed += bytesToRead;
////            bytesRead = inputStream.read(buffer, offset, bytesToRead);
////            readed += bytesRead;
//
//        if (bytesRemaining != C.LENGTH_UNSET) {
//            bytesRemaining -= bytesToRead;
//        }
//        return bytesToRead;
    }

    @Override
    public Uri getUri() {
        // I can return any uri != null here and player will work! For example
        return Uri.fromFile(Environment.getExternalStorageDirectory());
    }

    @Override
    public void close() throws IOException {
        isStop = true;
        try {
            if (inputStream != null) {
                inputStream.close();
            }
        } catch (IOException e) {
            throw new IOException(e);
        } finally {
            inputStream = null;
        }
    }
}