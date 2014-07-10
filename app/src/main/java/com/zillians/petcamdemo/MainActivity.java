package com.zillians.petcamdemo;

import android.app.Activity;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

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


public class MainActivity extends Activity {
    private static final String TAG = "PetcamDemo";

    EasyCamera camera;
    SurfaceView surface;
    Map<String, TextView> nameMap = new HashMap<String, TextView>();

    BlockingQueue<Runnable> queue;
    ThreadPoolExecutor executor;

    File appFolder;
    ImageAnalysisService imageAnalysisService = new ImageAnalysisService();

    Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initFolder();

        handler = new Handler();
        queue = new LinkedBlockingQueue<Runnable>();
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, queue);

        surface = (SurfaceView) findViewById(R.id.preview);
        nameMap.put("meow", (TextView) findViewById(R.id.meow));
        nameMap.put("chacha", (TextView) findViewById(R.id.chacha));
        nameMap.put("background", (TextView) findViewById(R.id.background));

        camera = DefaultEasyCamera.open(1);

        surface.getHolder().addCallback(previewCallback);
    }

    private void initFolder() {
        appFolder = new File(Environment.getExternalStorageDirectory(), "petcamdemo");
        if (!appFolder.exists()) {
            appFolder.mkdir();
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
        int captureCount = 0;

        @Override
        public void onPreviewFrame(byte[] data, Camera camera) {
            long current = System.currentTimeMillis();

            if (current - lastTimeCapture > 2 * 1000) {
                lastTimeCapture = current;
                Log.d(TAG, "Capture data size = " + data.length);

                captureCount++;
                final File outputImage = new File(appFolder, captureCount + ".jpg");

                writeToJpeg(outputImage, data);
                executor.submit(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            final ImageAnalysisService.Tag[] tags = imageAnalysisService.getTag(outputImage);
                            outputImage.delete();
                            Log.d(TAG, "Analysis complete: " + tags);

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
                        }
                    }
                });
            }
        }
    };

    private void updateNameTextView(ImageAnalysisService.Tag[] tags) {
        final int maxTextSize = 42;
        final int minTextSize = 8;

        for (ImageAnalysisService.Tag tag : tags) {
            final TextView name = nameMap.get(tag.getTag().toLowerCase());
            float textSize = (float)tag.getConfidence() * maxTextSize;

            textSize = textSize < minTextSize ? minTextSize : textSize;
            name.setTextSize(textSize);
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
