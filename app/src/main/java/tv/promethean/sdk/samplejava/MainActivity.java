package tv.promethean.sdk.samplejava;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.MainThread;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.jvm.internal.Intrinsics;
import tv.promethean.ptvsdk.OverlayManager;
import tv.promethean.ptvsdk.interfaces.DefaultOverlayEventListener;
import tv.promethean.ptvsdk.interfaces.PlayerChangeListener;
import tv.promethean.ptvsdk.models.ConfigData;
import tv.promethean.ptvsdk.models.OverlayData;
import tv.promethean.ptvsdk.models.StreamSource;

public class MainActivity extends AppCompatActivity {

    static private final String SAMPLE_CHANNEL_ID = "5c701be7dc3d20080e4092f4";
    static private final String SAMPLE_STREAM_ID = "5de7e7c2a6adde5211684519";

    private PlayerView exoplayerView;
    private SimpleExoPlayer exoplayer;
    private ConcatenatingMediaSource concatenatingMediaSource;
    private OverlayManager overlayManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        exoplayerView = findViewById(R.id.simple_exo_player_view);
        init(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        releaseOverlays();
    }

    @Override
    protected void onPause() {
        super.onPause();
        exoplayer.setPlayWhenReady(false);
    }

    private void init(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            initializePlayer();
            initializeOverlays();
        }
    }

    private void initializePlayer() {
        Context baseContext = getBaseContext();
        DefaultTrackSelector trackSelector = new DefaultTrackSelector();
        ComponentName componentName = new ComponentName(baseContext, "Exo");

        exoplayer = ExoPlayerFactory.newSimpleInstance(baseContext, trackSelector);
        exoplayerView.setPlayer(exoplayer);
        exoplayerView.setUseController(true);

        PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
        playbackStateBuilder.setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_FAST_FORWARD);

        MediaSessionCompat mediaSession = new MediaSessionCompat(baseContext, "ExoPlayer", componentName, null);
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setActive(true);

        concatenatingMediaSource = new ConcatenatingMediaSource();
    }

    private void initializeOverlays() {
        OverlayData overlayData = new OverlayData.Builder()
                .setChannelId(SAMPLE_CHANNEL_ID)
                .setStreamId(SAMPLE_STREAM_ID)
                .setDebug(true)
                .build();

        // Instantiate overlay manager.
        overlayManager = new OverlayManager(this, R.id.ptv_overlay_view, overlayData);

        overlayManager.addOverlayListener(new DefaultOverlayEventListener() {
            @Override
            public void onConfigReady(@NotNull ConfigData configData) {
                // Run on UI thread so player controls function correctly.
                runOnUiThread(new Thread() {
                    public void run() {
                        // Create a collection of media sources from the array of config
                        // sources returned from the API.
                        for (StreamSource source : configData.getStreamSources()) {
                            MediaSource mediaSource = buildMediaSource(source.getUrl());
                            concatenatingMediaSource.addMediaSource(mediaSource);
                        }
                        exoplayer.setPlayWhenReady(true);
                        exoplayer.prepare(concatenatingMediaSource);
                    }
                });
            }

            @Override
            public boolean dispatchKeyEvent(int i, @NotNull KeyEvent keyEvent) {
                return false;
            }

            @Override
            public void onUserGesture(boolean b, @Nullable MotionEvent motionEvent) { }

            @Override
            public void onVisibilityChange(boolean b, int i) { }

            @Override
            public void onWebViewBrowserClose() { }

            @Override
            public void onWebViewBrowserOpen() { }

            @Override
            public void onWebViewBrowserContentVisible(boolean b) { }
        });

        // Set the player position for VOD playback.
        overlayManager.addPlayerListener(new PlayerChangeListener() {
            @Override
            public Long getCurrentPosition() {
                return exoplayer.getCurrentPosition();
            }
        });

        // Add player change listener to determine overlay visibility.
        exoplayer.addListener(new Player.EventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                overlayManager.setVisible(playWhenReady && playbackState == Player.STATE_READY);
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                if (error.type == ExoPlaybackException.TYPE_SOURCE) {
                    // Example fallback strategy for cycling through media sources if different
                    // ones are specified in the Broadcast Center.
                    playNextMediaSource();
                }
            }
        });
    }

    private MediaSource buildMediaSource(String url) {
        String userAgent = Util.getUserAgent(this.getBaseContext(), "Exo");
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(this.getBaseContext(), userAgent);
        Uri uri = Uri.parse(url);

        int type = Util.inferContentType(uri);
        MediaSource mediaSource;

        switch (type) {
            case C.TYPE_DASH:
                mediaSource = new DashMediaSource
                        .Factory(dataSourceFactory)
                        .createMediaSource(uri);
                break;
            case C.TYPE_HLS:
                mediaSource = new HlsMediaSource
                        .Factory(dataSourceFactory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(uri);
                break;
            case C.TYPE_SS:
                mediaSource = new SsMediaSource
                        .Factory(dataSourceFactory)
                        .createMediaSource(uri);
                break;
            case C.TYPE_OTHER:
                mediaSource = new ExtractorMediaSource
                        .Factory(dataSourceFactory)
                        .setExtractorsFactory(new DefaultExtractorsFactory())
                        .createMediaSource(uri);
                break;
            default:
                throw new IllegalStateException("Unsupported type: " + type);
        }

        return mediaSource;
    }

    private void playNextMediaSource() {
        if (concatenatingMediaSource != null) {
            concatenatingMediaSource.removeMediaSource(exoplayer.getCurrentWindowIndex());
            if (exoplayer != null) {
                exoplayer.setPlayWhenReady(true);
                exoplayer.prepare(concatenatingMediaSource, true, true);
            }
        }

    }

    private void releasePlayer() {
        if (exoplayer != null) {
            exoplayer.stop();
            exoplayer.release();
            exoplayer = null;
        }
    }

    private void releaseOverlays() {
        if (overlayManager != null) {
            overlayManager.release();
            overlayManager = null;
        }
    }
}
