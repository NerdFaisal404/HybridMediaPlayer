package hybridmediaplayer;

import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.view.SurfaceView;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ConcatenatingMediaSource;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.BandwidthMeter;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.common.images.WebImage;
import com.socks.library.KLog;


public class ExoMediaPlayer extends HybridMediaPlayer implements CastPlayer.SessionAvailabilityListener {

    private Player currentPlayer;
    private SimpleExoPlayer exoPlayer;
    private CastPlayer castPlayer;


    private Context context;
    private MediaSource mediaSource;
    private MediaQueueItem[] mediaItems;
    private int currentState;
    private boolean isPreparing = false;
    private OnTracksChangedListener onTracksChangedListener;
    private OnPositionDiscontinuityListener onPositionDiscontinuityListener;
    private boolean isSupportingSystemEqualizer;


    public ExoMediaPlayer(Context context) {
        this.context = context;

        BandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory =
                new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector =
                new DefaultTrackSelector(videoTrackSelectionFactory);

        exoPlayer = ExoPlayerFactory.newSimpleInstance(context, trackSelector);
        currentPlayer = exoPlayer;
    }

    @Override
    public void setDataSource(String path) {
        setDataSource(new String[]{path});
    }

    public void setDataSource(String... paths) {
        String userAgent = Util.getUserAgent(context, "yourApplicationName");
        DefaultHttpDataSourceFactory httpDataSourceFactory = new DefaultHttpDataSourceFactory(
                userAgent,
                null /* listener */,
                DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS,
                DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS,
                true /* allowCrossProtocolRedirects */
        );

        // Produces DataSource instances through which media data is loaded.
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(context, null, httpDataSourceFactory);
        // Produces Extractor instances for parsing the media data.
        ExtractorsFactory extractorsFactory = new SeekableExtractorsFactory();


        MediaSource[] sources = new MediaSource[paths.length];
        for (int i = 0; i < paths.length; i++) {
            // This is the MediaSource representing the media to be played.
            sources[i] = new ExtractorMediaSource(Uri.parse(paths[i]),
                    dataSourceFactory, extractorsFactory, null, null);
        }

        mediaSource = new ConcatenatingMediaSource(sources);

        //media sources for CastPlayer
        mediaItems = new MediaQueueItem[paths.length];
        for (int i = 0; i < paths.length; i++) {
            mediaItems[i] = buildMediaQueueItem(paths[i]);
        }
    }

    private  MediaQueueItem buildMediaQueueItem(String url) {
        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK);
        movieMetadata.putString(MediaMetadata.KEY_TITLE, "HybridPlayer");
        movieMetadata.putString(MediaMetadata.KEY_ALBUM_ARTIST, "Artist");
        movieMetadata.addImage(new WebImage(Uri.parse("https://avatars2.githubusercontent.com/u/10148175?s=460&v=4")));
        //movieMetadata.addImage(new WebImage(Uri.parse("http://www.juvepoland.com/images/news/36975.jpg")));
        MediaInfo mediaInfo = new MediaInfo.Builder(url)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(MimeTypes.AUDIO_UNKNOWN)
                .setMetadata(movieMetadata).build();
        return new MediaQueueItem.Builder(mediaInfo).build();
    }

    @Override
    public void prepare() {
        exoPlayer.setAudioDebugListener(new AudioRendererEventListener() {
            @Override
            public void onAudioEnabled(DecoderCounters counters) {

            }

            @Override
            public void onAudioSessionId(int audioSessionId) {
                setEqualizer();
            }

            @Override
            public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {

            }

            @Override
            public void onAudioInputFormatChanged(Format format) {

            }

            @Override
            public void onAudioSinkUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {

            }


            @Override
            public void onAudioDisabled(DecoderCounters counters) {

            }
        });

        isPreparing = true;
        exoPlayer.prepare(mediaSource);
        exoPlayer.addListener(new Player.DefaultEventListener() {
            @Override
            public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
                if (currentState != playbackState)
                    switch (playbackState) {
                        case ExoPlayer.STATE_ENDED:
                            if (onCompletionListener != null)
                                onCompletionListener.onCompletion(ExoMediaPlayer.this);
                            break;

                        case ExoPlayer.STATE_READY:
                            if (isPreparing && onPreparedListener != null) {
                                isPreparing = false;
                                onPreparedListener.onPrepared(ExoMediaPlayer.this);
                            }
                            break;
                    }
                currentState = playbackState;
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
                if (onPositionDiscontinuityListener != null)
                    onPositionDiscontinuityListener.onPositionDiscontinuity(exoPlayer.getCurrentWindowIndex());
            }

            @Override
            public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
                if (onTracksChangedListener != null)
                    onTracksChangedListener.onTracksChanged(trackGroups, trackSelections);
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                if (onErrorListener != null)
                    onErrorListener.onError(error, ExoMediaPlayer.this);
            }
        });

    }

    private void setEqualizer() {
        if (!isSupportingSystemEqualizer)
            return;
        final Intent intent = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, exoPlayer.getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.sendBroadcast(intent);
    }

    private void releaseEqualizer() {
        if (!isSupportingSystemEqualizer)
            return;
        final Intent intent = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, exoPlayer.getAudioSessionId());
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.sendBroadcast(intent);
    }

    public void setSupportingSystemEqualizer(boolean supportingSystemEqualizer) {
        isSupportingSystemEqualizer = supportingSystemEqualizer;
        if (supportingSystemEqualizer)
            setEqualizer();
        else
            releaseEqualizer();
    }

    public boolean isSupportingSystemEqualizer() {
        return isSupportingSystemEqualizer;
    }

    public void setCastPlayer(CastContext castContext) {
        castPlayer = new CastPlayer(castContext);

        castPlayer.setSessionAvailabilityListener(this);
    }

    @Override
    public void release() {
        releaseEqualizer();
        exoPlayer.release();
        if(castPlayer !=null)
            castPlayer.release();
    }

    @Override
    public void play() {
        currentPlayer.setPlayWhenReady(true);
    }

    @Override
    public void pause() {
        currentPlayer.setPlayWhenReady(false);
    }

    @Override
    public void seekTo(int msec) {
        currentPlayer.seekTo(msec);
    }

    public void stop() {
        currentPlayer.stop();
    }

    public void seekTo(int windowIndex, int msec) {
        currentPlayer.seekTo(windowIndex, msec);
    }

    @Override
    public int getDuration() {
        return (int) currentPlayer.getDuration();
    }

    @Override
    public int getCurrentPosition() {
        return (int) currentPlayer.getCurrentPosition();
    }

    @Override
    public float getVolume() {
        return exoPlayer.getVolume();
    }

    @Override
    public void setVolume(float level) {
        exoPlayer.setVolume(level);
    }

    @Override
    public void setPlaybackParams(float speed, float pitch) {
        PlaybackParameters params = new PlaybackParameters(speed, pitch);
        currentPlayer.setPlaybackParameters(params);
    }

    @Override
    public boolean isPlaying() {
        return exoPlayer.getPlayWhenReady();
    }

    @Override
    public void setPlayerView(Context context, final SurfaceView surfaceView) {
        exoPlayer.setVideoSurfaceView(surfaceView);
    }

    @Override
    public boolean hasVideo() {
        return exoPlayer.getVideoFormat() != null;
    }

    public SimpleExoPlayer getExoPlayer() {
        return exoPlayer;
    }

    public void setOnTracksChangedListener(OnTracksChangedListener onTracksChangedListener) {
        this.onTracksChangedListener = onTracksChangedListener;
    }

    public void setOnPositionDiscontinuityListener(OnPositionDiscontinuityListener onPositionDiscontinuityListener) {
        this.onPositionDiscontinuityListener = onPositionDiscontinuityListener;
    }

    @Override
    public void onCastSessionAvailable() {
        setCurrentPlayer(castPlayer);
    }


    @Override
    public void onCastSessionUnavailable() {
        setCurrentPlayer(exoPlayer);
    }

    private void setCurrentPlayer(Player player) {
        pause();
        long time = currentPlayer.getCurrentPosition();
        int window = currentPlayer.getCurrentWindowIndex();

        KLog.d(time/1000);

        currentPlayer = player;

        if (currentPlayer == castPlayer)
            castPlayer.loadItems(mediaItems, window, time, Player.REPEAT_MODE_OFF);

        if (currentPlayer == exoPlayer) {
            castPlayer.seekTo(window, time);
            play();
        }
    }

    public interface OnTracksChangedListener {
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections);
    }

    public interface OnPositionDiscontinuityListener {
        public void onPositionDiscontinuity(int currentWindowIndex);
    }
}