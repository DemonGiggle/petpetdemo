package com.zillians.petcamdemo;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.easycamera.DefaultEasyCamera;
import com.easycamera.EasyCamera;

import org.apache.http.HttpException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class ActionWrapper {
    TextView textView;
    Runnable action;

    ActionWrapper(TextView textView, Runnable action) {
        this.textView = textView;
        this.action = action;
    }
}

public class MainActivity extends Activity {
    private static final String TAG = "PetcamDemo";

    EasyCamera camera;
    SurfaceView surface;
    Map<String, ActionWrapper> actionMap = new HashMap<String, ActionWrapper>();

    BlockingQueue<Runnable> queue;
    ThreadPoolExecutor executor;

    File appFolder;
    ImageAnalysisService imageAnalysisService = new ImageAnalysisService();

    Handler handler;
    File outputImage ;
    TextView alert;

    SoundPool soundPool;
    int negativeSound;
    int positiveSound;

    Runnable currentAction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initSoundPool();
        initFolder();

        handler = new Handler();
        queue = new LinkedBlockingQueue<Runnable>();
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, queue);

        alert = (TextView) findViewById(R.id.alert);
        surface = (SurfaceView) findViewById(R.id.preview);
        actionMap.put("meow", new ActionWrapper((TextView) findViewById(R.id.meow), meowAction));
        actionMap.put("chacha", new ActionWrapper((TextView) findViewById(R.id.chacha), chachaAction));
        actionMap.put("background", new ActionWrapper((TextView) findViewById(R.id.background), backgroundAction));

        camera = DefaultEasyCamera.open(1);

        surface.getHolder().addCallback(previewCallback);
    }

    private void initSoundPool() {
        soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 3);
        negativeSound = soundPool.load(this, R.raw.negative, 1);
        positiveSound = soundPool.load(this, R.raw.positive, 2);
    }

    private Animation.AnimationListener alertAnimationListener = new Animation.AnimationListener() {
        @Override
        public void onAnimationStart(Animation animation) {
            alert.setVisibility(View.VISIBLE);
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            alert.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {

        }
    };

    private Runnable meowAction = new Runnable() {
        @Override
        public void run() {
            if (currentAction != meowAction) {
                Log.d(TAG, "[tag] meow action");
                soundPool.play(positiveSound, 1.0f, 1.0f, 0, 0, 1.0f);
                currentAction = meowAction;

                alert.setText("PASS");
                alert.setTextColor(getResources().getColor(android.R.color.holo_blue_bright));

                final Animation animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.blink);
                animation.setAnimationListener(alertAnimationListener);
                alert.startAnimation(animation);
            }
        }
    };

    private Runnable chachaAction = new Runnable() {
        @Override
        public void run() {
            if (currentAction != chachaAction) {
                Log.d(TAG, "[tag] chacha action");
                soundPool.play(negativeSound, 1.0f, 1.0f, 0, 0, 1.5f);
                currentAction = chachaAction;

                alert.setText("WARNING");
                alert.setTextColor(getResources().getColor(android.R.color.holo_red_light));

                final Animation animation = AnimationUtils.loadAnimation(MainActivity.this, R.anim.blink);
                animation.setAnimationListener(alertAnimationListener);
                alert.startAnimation(animation);
            }
        }
    };

    private Runnable backgroundAction = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "[tag] background action");
            currentAction = backgroundAction;
        }
    };

    private void initFolder() {
        appFolder = new File(Environment.getExternalStorageDirectory(), "petcamdemo");
        if (!appFolder.exists()) {
            appFolder.mkdir();
        }
        outputImage = new File(appFolder, "tmp.jpg");
        if (outputImage.exists()) {
            outputImage.delete();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        queue.clear();
        executor.shutdown();
    }

    private SurfaceHolder.Callback previewCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                Camera.Parameters parameters = camera.getParameters();
                if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                    parameters.set("orientation", "portrait");
                    camera.setDisplayOrientation(90);
                    parameters.setRotation(90);
                } else {
                    parameters.set("orientation", "landscape");
                    camera.setDisplayOrientation(0);
                    parameters.setRotation(0);
                }

                camera.startPreview(holder);
                camera.setPreviewCallback(cameraPreviewCallback);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            camera.setPreviewCallback(null);
            camera.close();
        }
    };

    private void writeToJpeg(File outputImage, byte[] data) {
        try {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            YuvImage image = new YuvImage(data, ImageFormat.NV21,
                    size.width, size.height, null);
            Rect rectangle = new Rect();
            rectangle.bottom = size.height;
            rectangle.top = 0;
            rectangle.left = 0;
            rectangle.right = size.width;
            ByteArrayOutputStream out2 = new ByteArrayOutputStream();
            image.compressToJpeg(rectangle, 90, out2);

            FileOutputStream fos = new FileOutputStream(outputImage);
            fos.write(out2.toByteArray());
            fos.close();
            Log.d(TAG, "Write image complete: " + outputImage.toString());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Camera.PreviewCallback cameraPreviewCallback = new Camera.PreviewCallback() {

        private long lastTimeCapture = 0;

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            long current = System.currentTimeMillis();

            if (!outputImage.exists()) {
                lastTimeCapture = current;
                Log.d(TAG, "Capture data size = " + data.length);

                writeToJpeg(outputImage, data);
                executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            final ImageAnalysisService.Tag[] tags = imageAnalysisService.getTag(outputImage);
                            Log.d(TAG, "[tag] Analysis complete: ");
                            for (ImageAnalysisService.Tag tag : tags) {
                                Log.d(TAG, "\t[tag] " + tag.toString());
                            }

                            handler.post(new Runnable() {

                                @Override
                                public void run() {
                                    updateNameTextView(tags);
                                }
                            });

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (HttpException e) {
                            e.printStackTrace();
                        } finally {
                            final boolean result = outputImage.delete();
                            Log.d(TAG, "delete tmp image result = " + result);
                        }
                    }
                });
            }
        }
    };

    private void updateNameTextView(ImageAnalysisService.Tag[] tags) {
        final int maxTextSize = 42;
        final int minTextSize = 8;

        double maxConfidence = 0;
        Runnable actionToTake = null;
        for (ImageAnalysisService.Tag tag : tags) {
            final ActionWrapper actionWrapper = actionMap.get(tag.getTag().toLowerCase());
            if (actionWrapper == null) {
                Toast.makeText(this, "Wrong tag from server: " + tag.getTag(), Toast.LENGTH_SHORT).show();
                break;
            }

            final TextView name = actionWrapper.textView;
            float textSize = (float)tag.getConfidence() * maxTextSize;

            textSize = textSize < minTextSize ? minTextSize : textSize;
            name.setTextSize(textSize);

            if (tag.getConfidence() > maxConfidence) {
                actionToTake = actionWrapper.action;
                maxConfidence = tag.getConfidence();
            }
        }

        if (actionToTake != null) {
            actionToTake.run();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
