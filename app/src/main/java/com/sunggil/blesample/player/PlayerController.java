package com.sunggil.blesample.player;

import androidx.annotation.Nullable;

import com.google.android.exoplayer2.Player;

public interface PlayerController {
    void addPlayerCallback(PlayerCallback callback);

    boolean isPlaying();

    void prepare(boolean isLocal, @Nullable String customKey, String url);

    void prepare(boolean isLocal, @Nullable String customKey, byte[] bytes);

    void addByteData();

    void setPlay();

    void setPause();

    void setSeekTo(int sec, boolean wasPlaying);

    void setStop();

    void setPlayPauseUI(boolean isPlay);

    void release();

    void setVolume(float volume);

    long getCurrentPosition();

    Player getPlayer();

    void initializePlayer();
}
