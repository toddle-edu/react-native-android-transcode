package com.reactlibrary;

import android.content.ContentResolver;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.events.RCTEventEmitter;
import com.otaliastudios.transcoder.Transcoder;
import com.otaliastudios.transcoder.TranscoderListener;
import com.otaliastudios.transcoder.TranscoderOptions;
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy;
import com.otaliastudios.transcoder.strategy.size.FractionResizer;

import java.io.File;
import java.util.Objects;

public class AndroidTranscodeModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;

    public AndroidTranscodeModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "AndroidTranscode";
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @ReactMethod
    public void compressVideo(String inputFilePath, ReadableMap options, final Promise promise){
        try {

            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(inputFilePath);

            int videoWidth = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int videoHeight = Integer.parseInt(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            float fractionSize = 1F;

            if(videoWidth < videoHeight && videoWidth > 480){
                float tempFraction = (float) 480/videoWidth;
                if(tempFraction > 0 && tempFraction <= 1) fractionSize = tempFraction;
            }
            else if(videoHeight < videoWidth && videoHeight > 480){
                float tempFraction  = (float) 480/videoHeight;
                if(tempFraction > 0 && tempFraction <= 1) fractionSize = tempFraction;
            }

            //System.out.println("fraction_size "+fractionSize);

            //int height = options.hasKey("height") ? options.getInt("height") : 480;
            //int width = options.hasKey("width") ? options.getInt("width") : 640;
            long bitRate = options.hasKey("bitRate") ? (long) options.getDouble("bitRate") : 1200;
            int frameRate = options.hasKey("frameRate") ? options.getInt("frameRate") : 24;
            float fractionResizer = options.hasKey("fractionResizer") ? (float) options.getDouble("fractionResizer") : fractionSize;
            float keyFrameInterval = options.hasKey("keyFrameInterval") ? (float) options.getDouble("keyFrameInterval") : 1F;

            File outputDir = Objects.requireNonNull(reactContext).getCacheDir();
            final File outputFile = File.createTempFile("video_to_upload_", ".mp4", outputDir);

            /* Define Output path for Transcoder*/
            TranscoderOptions.Builder TranscoderObject = Transcoder.into(outputFile.getAbsolutePath());

            /* Add data source, check if uri is valid */
            Uri inputFileUri = Uri.parse(inputFilePath);
            ContentResolver cr = reactContext.getContentResolver();
            Cursor cursor = cr.query(inputFileUri, null, null, null, null);
            if (cursor != null) {
                cursor.close();
                TranscoderObject.addDataSource(reactContext, inputFileUri);
            }
            else{
                TranscoderObject.addDataSource(inputFilePath);
            }

            /* Set Video Track Strategy */
            TranscoderObject.setVideoTrackStrategy(
                    DefaultVideoStrategy
                            .fraction(fractionResizer)
                            //.exact(height, width)
                            //.addResizer(new FractionResizer(fractionResizer))
                            .bitRate(bitRate * 1000)
                            .frameRate(frameRate)
                            .keyFrameInterval(keyFrameInterval)
                            .build()
            );

            /* Set Listener */
            TranscoderObject.setListener(new TranscoderListener() {
                public void onTranscodeProgress(double progress) {
                    WritableMap map = Arguments.createMap();
                    map.putDouble("progress", progress);
                    reactContext
                            .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                            .emit("transcodeProgressUpdate", map);
                }
                public void onTranscodeCompleted(int successCode) {
                    WritableMap map = Arguments.createMap();
                    map.putInt("successCode", successCode);
                    map.putString("compressedVideoAbsolutePath", outputFile.getAbsolutePath());
                    map.putString("compressedVideoUri", outputFile.toString());
                    promise.resolve(map);
                }
                public void onTranscodeCanceled() {
                    promise.resolve("transcode_canceled");
                }
                public void onTranscodeFailed(@NonNull Throwable exception) {
                    promise.reject("transcode_failed", exception.toString());
                }
            }).transcode();

            /*
            Transcoder.into(outputFile.getAbsolutePath())
                    .addDataSource(reactContext, inputFilePath)
                    .setVideoTrackStrategy(DefaultVideoStrategy.exact(height, width)
                            .bitRate(bitRate * 1000)
                            .frameRate(frameRate)
                            .keyFrameInterval(1F)
                            .build())
                    .setListener(new TranscoderListener() {
                        public void onTranscodeProgress(double progress) {
                            WritableMap map = Arguments.createMap();
                            map.putDouble("progress", progress);
                            reactContext.getJSModule(RCTEventEmitter.class).receiveEvent(Objects.requireNonNull(getCurrentActivity()).getTaskId(), "transcodeProgressUpdate", map);
                        }
                        public void onTranscodeCompleted(int successCode) {
                            WritableMap map = Arguments.createMap();
                            map.putInt("successCode", successCode);
                            map.putString("compressedVideoAbsolutePath", outputFile.getAbsolutePath());
                            map.putString("compressedVideoUri", outputFile.toString());
                            promise.resolve(map);
                        }
                        public void onTranscodeCanceled() {
                            promise.resolve("transcode_canceled");
                        }
                        public void onTranscodeFailed(@NonNull Throwable exception) {
                            promise.reject("transcode_failed", exception.toString());
                        }
                    }).transcode();*/

        } catch (Exception e) {
            promise.reject("transcode_failed", e.toString());
        }
    }

    @ReactMethod
    public void mediaMetadata(String inputVideoPath, final Promise promise){
        try{
            String bitRate, videoWidth, videoHeight, mimeType, videoTitle, videoRotation, videoFrameRate = null;

            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(inputVideoPath);

            videoTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            mimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            bitRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            videoWidth = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            videoHeight = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            videoRotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                videoFrameRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            }

            WritableMap map = Arguments.createMap();
            map.putString("title", videoTitle);
            map.putString("mimeType", mimeType );
            map.putString("width", videoWidth);
            map.putString("height", videoHeight);
            map.putString("bitRate", bitRate);
            map.putString("frameRate", videoFrameRate);
            map.putString("rotation", videoRotation);

            promise.resolve(map);
        }
        catch (Exception error){
            promise.reject("mediaMetadata_failed",error);
        }
    }
}
