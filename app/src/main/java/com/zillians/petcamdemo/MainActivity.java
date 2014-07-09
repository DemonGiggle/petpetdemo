package com.zillians.petcamdemo;

import android.app.Activity;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class MainActivity extends Activity {
    private static final String TAG = "PetcamDemo";

    EasyCamera camera;
    SurfaceView surface;
    TextView name;

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
        queue = new ArrayBlockingQueue<Runnable>(10);
        executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, queue);

        surface = (SurfaceView) findViewById(R.id.preview);
        name = (TextView) findViewById(R.id.name);

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
                try {
                    FileOutputStream fos = new FileOutputStream(outputImage);
                    fos.write(data);
                    fos.close();
                    Log.d(TAG, "Write image complete: " + outputImage.toString());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                boolean result = queue.offer(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            final ImageAnalysisService.Tag[] tags = imageAnalysisService.getTag(outputImage);
                            outputImage.delete();
                            Log.d(TAG, "Analysis complete: " + tags);

                            handler.post(new Runnable() {

                                @Override
                                public void run() {
                                    if (tags.length >= 1) {
                                        name.setText(tags[0].getTag());
                                    }
                                }
                            });

                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (HttpException e) {
                            e.printStackTrace();
                        }
                    }
                });

                Log.d(TAG, "Put task to upload thread result: " + result);
            }
        }
    };

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
