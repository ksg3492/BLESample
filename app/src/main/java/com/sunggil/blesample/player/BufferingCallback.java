package com.sunggil.blesample.player;

public interface BufferingCallback {
    void onBuffering(boolean buffering);
    void onPreload(int percent);
}
