package tv.promethean.sdk.samplejava;

import android.content.ComponentName;
import android.content.Context;
import android.net.Uri;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import tv.promethean.ptvsdk.OverlayManager;
import tv.promethean.ptvsdk.interfaces.PlayerChangeListener;
import tv.promethean.ptvsdk.models.OverlayData;

public class MainActivity extends AppCompatActivity {

    static private final String SAMPLE_VIDEO_URL = "http://184.72.239.149/vod/smil:BigBuckBunny.smil/playlist.m3u8";
    static private final String SAMPLE_CHANNEL_ID = "5c50eefce6f94249a2e104b3";
    static private final String SAMPLE_STREAM_ID = "5c5273cd5b88da1e6943200b";

    private SimpleExoPlayerView exoplayerView;
    private SimpleExoPlayer exoplayer;
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
        String userAgent = Util.getUserAgent(baseContext, "Exo");
        Uri mediaUri = Uri.parse(SAMPLE_VIDEO_URL);
        HlsMediaSource mediaSource = new HlsMediaSource(mediaUri, new DefaultDataSourceFactory(baseContext, userAgent), null, null);
        ComponentName componentName = new ComponentName(baseContext, "Exo");

        exoplayer = ExoPlayerFactory.newSimpleInstance(baseContext, trackSelector);
        exoplayerView.setPlayer(exoplayer);
        exoplayerView.setUseController(true);
        exoplayer.prepare(mediaSource);

        PlaybackStateCompat.Builder playbackStateBuilder = new PlaybackStateCompat.Builder();
        playbackStateBuilder.setActions(
                PlaybackStateCompat.ACTION_PLAY |
                PlaybackStateCompat.ACTION_PAUSE |
                PlaybackStateCompat.ACTION_FAST_FORWARD);

        MediaSessionCompat mediaSession = new MediaSessionCompat(baseContext, "ExoPlayer", componentName, null);
        mediaSession.setPlaybackState(playbackStateBuilder.build());
        mediaSession.setActive(true);
    }

    private void initializeOverlays() {
        OverlayData overlayData = new OverlayData.Builder()
                .setChannelId(SAMPLE_CHANNEL_ID)
                .setStreamId(SAMPLE_STREAM_ID)
                .setStreamType(OverlayData.StreamType.VOD)
                .setDebug(true)
                .build();

        // Instantiate overlay manager.
        overlayManager = new OverlayManager(getSupportFragmentManager(), R.id.ptv_overlay_view, overlayData);

        // Set the player position for VOD playback.
        overlayManager.addPlayerListener(new PlayerChangeListener() {
            @Override
            public Long getCurrentPosition() {
                return exoplayer.getCurrentPosition();
            }
        });

        // Add player change listener to determine overlay visibility.
        exoplayer.addListener(new Player.DefaultEventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                super.onPlayerStateChanged(playWhenReady, playbackState);
                overlayManager.setVisible(playWhenReady && playbackState == Player.STATE_READY);
            }
        });
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
