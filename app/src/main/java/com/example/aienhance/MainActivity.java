package com.example.aienhance;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private static final int PICK = 100;
    private Bitmap original, enhanced;
    private ImageView image;
    private TextView status;
    private Button compare, save, enhance;
    private boolean showOriginal = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 30, 24, 24);
        root.setBackgroundColor(Color.rgb(15, 19, 32));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title = label("AI Enhance", 26);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        status = label("یک عکس انتخاب کنید", 15);
        status.setGravity(Gravity.CENTER);
        root.addView(status);
        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
        Button pick = button("انتخاب عکس");
        root.addView(pick);
        pick.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, PICK);
        });
        enhance = button("افزایش وضوح");
        root.addView(enhance);
        enhance.setEnabled(false);
        enhance.setOnClickListener(v -> process());
        compare = button("نمایش قبل / بعد");
        root.addView(compare);
        compare.setEnabled(false);
        compare.setOnClickListener(v -> {
            showOriginal = !showOriginal;
            image.setImageBitmap(showOriginal ? original : enhanced);
            status.setText(showOriginal ? "تصویر اصلی" : "تصویر پردازش‌شده");
        });
        save = button("ذخیره در گالری");
        root.addView(save);
        save.setEnabled(false);
        save.setOnClickListener(v -> saveImage());
        setContentView(root);
    }

    private TextView label(String value, int size) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(Color.WHITE);
        t.setPadding(10, 14, 10, 14);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        return b;
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK || result != RESULT_OK || data == null) return;
        try {
            Uri uri = data.getData();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                original = BitmapFactory.decodeStream(in, null, opts);
            }
            if (original == null) throw new IllegalStateException("Cannot decode image");
            enhanced = null;
            image.setImageBitmap(original);
            enhance.setEnabled(true);
            compare.setEnabled(false);
            save.setEnabled(false);
            status.setText("آماده افزایش وضوح");
        } catch (Exception e) {
            status.setText("خطا در باز کردن تصویر");
        }
    }

    private void process() {
        if (original == null) return;
        enhance.setEnabled(false);
        status.setText("در حال پردازش...");
        new Thread(() -> {
            try {
                Bitmap result = sharpen(original);
                runOnUiThread(() -> {
                    enhanced = result;
                    image.setImageBitmap(result);
                    compare.setEnabled(true);
                    save.setEnabled(true);
                    enhance.setEnabled(true);
                    showOriginal = false;
                    status.setText("شارپ‌سازی پایه انجام شد؛ این مدل هوش مصنوعی نیست");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    enhance.setEnabled(true);
                    status.setText("خطا در پردازش");
                });
            }
        }).start();
    }

    private Bitmap sharpen(Bitmap source) {
        int w = source.getWidth(), h = source.getHeight();
        int[] pixels = new int[w * h], output = new int[w * h];
        source.getPixels(pixels, 0, w, 0, 0, w, h);
        System.arraycopy(pixels, 0, output, 0, pixels.length);
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                int c = pixels[i];
                int l = pixels[i - 1], r = pixels[i + 1], u = pixels[i - w], d = pixels[i + w];
                output[i] = Color.argb(Color.alpha(c),
                    channel(Color.red(c), Color.red(l), Color.red(r), Color.red(u), Color.red(d)),
                    channel(Color.green(c), Color.green(l), Color.green(r), Color.green(u), Color.green(d)),
                    channel(Color.blue(c), Color.blue(l), Color.blue(r), Color.blue(u), Color.blue(d)));
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(output, 0, w, 0, 0, w, h);
        return bitmap;
    }

    private int channel(int c, int l, int r, int u, int d) {
        return Math.max(0, Math.min(255, (int)(1.6 * c - 0.15 * (l + r + u + d))));
    }

    private void saveImage() {
        if (enhanced == null) return;
        Uri uri = null;
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "enhanced_" + System.currentTimeMillis() + ".png");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            if (Build.VERSION.SDK_INT >= 29) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AIEnhance");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
            }
            uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("Cannot create output");
            try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
                if (stream == null || !enhanced.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    throw new IllegalStateException("Cannot save image");
            }
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                getContentResolver().update(uri, done, null, null);
            }
            status.setText("تصویر ذخیره شد");
        } catch (Exception e) {
            if (uri != null) getContentResolver().delete(uri, null, null);
            status.setText("ذخیره‌سازی ناموفق بود");
        }
    }
}
