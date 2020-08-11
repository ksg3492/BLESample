package com.sunggil.blesample.player;

public interface PlayerCallback {
    void onPrepared(int duration);

    void onPlayed();

    void onPaused();

    void onProgress(int sec);

    void onCompletion(int duration);

    void onError(String errMsg);

    void onBuffering(boolean buffering);

    void onBufferingEnd(long seek);

}
