package com.sunggil.blesample.player;

import android.media.MediaDataSource;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.IOException;

@RequiresApi(api = Build.VERSION_CODES.M)
public class BufferMediaDataSource extends MediaDataSource {

    private volatile byte[] mBuffer;    // byte array for whole media

    private int fileSize;

    private int writeIndex = 0;

    private BufferingCallback callback;
    private boolean isStop = false;

    public BufferMediaDataSource(int fileSize, BufferingCallback callback) {
        this.fileSize = fileSize;
        this.callback = callback;
        mBuffer = new byte[this.fileSize];
    }

    public void inputData(byte[] data, int length) {
        if (mBuffer != null) {
            System.arraycopy(data, 0, mBuffer, writeIndex, length);

            writeIndex += length;

            float per = writeIndex * 1.00f / fileSize * 1.00f;
            int percent = (int)(per * 100);

//                int percent = (writeIndex * 100) / fileSize;

            if (callback != null) {
                callback.onPreload(percent);
            }
        }
    }

    public int getWriteIndex() {
        return writeIndex;
    }

    @Override
    public int readAt(long position, byte[] buffer, int offset, int size) throws IOException {
        if(position == writeIndex) {
            return -1;
        }

        if(position + size > writeIndex) {
            if(fileSize == writeIndex) {
                Log.e("SG2","MediaDataSource loaded data End.");
                int rest = (int) (writeIndex - position);

                System.arraycopy(mBuffer, (int) position, buffer, offset, rest);

                return rest;

            } else {
                Log.e("SG2","미디어에 필요한 Bytes : " + (position + size) + " / " + writeIndex + "  Bytes");
                Log.e("SG2","MediaDataSource loading data is faster than downloading data. Waiting...");
                //loading data is faster than downloading data.

                while(!isStop) {
                    if (position + size <= writeIndex) {
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
            System.arraycopy(mBuffer, (int) position, buffer, offset, size);
            return size;
        }

        return 0;
    }

    @Override
    public long getSize() throws IOException {
        Log.e("SG2","BufferMediaSource getSize()");
            return fileSize;
    }



    @Override

    public void close() throws IOException {
        isStop = true;
        mBuffer = null;
    }

}