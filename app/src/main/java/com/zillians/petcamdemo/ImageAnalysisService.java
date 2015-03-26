package com.zillians.petcamdemo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;

import org.apache.http.Header;
import org.apache.http.HttpException;
import org.apache.http.entity.InputStreamEntity;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by giggle on 5/6/14.
 */
public class ImageAnalysisService {
    private static final String TAG = "ImageAnalysisService";
    private static final String IMAGE_ANALYSIS_URL = "http://jarvis.ai";

    public class Tag {
        private String tag;
        private double confidence; // confidence of the tag, 0~1

        public Tag(final String tag, final double confidence) {
            this.tag = tag;
            this.confidence = confidence;
        }

        public String getTag() {
            return tag;
        }

        public double getConfidence() {
            return confidence;
        }
    }

    private Bitmap getScaledBitmap(final File image) throws IOException {
        final double expectDimension = 250;

        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(image.getPath(), options);

        // If it's already too small, just return
        Bitmap bitmap = null;
        if (options.outWidth < expectDimension || options.outHeight < expectDimension) {
            bitmap = BitmapFactory.decodeFile(image.getPath());
        }
        else {
            int xRatio = (int) Math.ceil(options.outWidth / expectDimension);
            int yRatio = (int) Math.ceil(options.outHeight / expectDimension);

            options.inSampleSize = (xRatio > yRatio) ? xRatio : yRatio;
            options.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeFile(image.getPath(), options);
        }

        if (bitmap == null) {
            throw new IOException("Failed to decode bitmap");
        }

        return bitmap;
    }

    private InputStream convertBitmapToInputStream(final Bitmap bitmap) {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 0, bos);

        return new ByteArrayInputStream(bos.toByteArray());
    }

    public Tag[] getTag(final File image) throws IOException, HttpException {

        if (!image.exists()) {
            throw new FileNotFoundException("File not found: " + image.getPath());
        }

        class Result {
            boolean value = true;
        }

        final List<Tag> myTags = new ArrayList<Tag>();
        final Bitmap scaledBitmap = getScaledBitmap(image);
        final InputStream stream = convertBitmapToInputStream(scaledBitmap);

        final Result result = new Result();
        final AsyncHttpClient client = new SyncHttpClient();

        Log.d(TAG, "Send image to server, size = " + stream.available());
        client.post(null, IMAGE_ANALYSIS_URL, new InputStreamEntity(stream, stream.available()), "image/jpeg", new JsonHttpResponseHandler() {

            @Override
            public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
                Log.d(TAG, "Success to get tag: " + response.toString());

                final int length = response.length();
                for (int i = 0; i < length; ++i) {
                    try {
                        final JSONArray entry = response.getJSONArray(i);
                        final double confidence = entry.getDouble(0);
                        final String tag = entry.getString(1);

                        final Tag result = new Tag(tag, confidence);
                        myTags.add(result);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONObject errorResponse) {
                result.value = false;
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, Throwable throwable, JSONArray errorResponse) {
                result.value = false;
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
                result.value = false;
            }
        });

        if (result.value == false) {
            throw new HttpException("Fail to connect to server");
        }

        Log.d(TAG, "We retrieve tags count: " + myTags.size());
        return myTags.toArray(new Tag[0]);
    }
}
