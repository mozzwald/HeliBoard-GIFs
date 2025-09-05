package helium314.keyboard.keyboard.emoji;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.GifConfig;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.content.ClipDescription;
import android.content.ContextWrapper;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.EditorInfo;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import android.net.Uri;

/**
 * A view for searching and displaying GIFs.
 */
public class GifSearchView extends LinearLayout {
    private EditText queryField;
    private ImageButton searchButton;
    private RecyclerView grid;
    private GifAdapter adapter;

    public GifSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        View.inflate(context, R.layout.gif_search_view, this);
        queryField = findViewById(R.id.gif_query_field);
        searchButton = findViewById(R.id.btn_search_gif);
        grid = findViewById(R.id.gif_results_grid);
        // set up grid and adapter
        grid.setLayoutManager(new GridLayoutManager(context, 3));
        adapter = new GifAdapter();
        grid.setAdapter(adapter);
        // search on button click or IME action
        searchButton.setOnClickListener(v -> performSearch(queryField.getText().toString()));
        queryField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(queryField.getText().toString());
                return true;
            }
            return false;
        });
    }

    public EditText getQueryField() {
        return queryField;
    }

    public ImageButton getSearchButton() {
        return searchButton;
    }

    public RecyclerView getGrid() {
        return grid;
    }

    /** Perform a GIF search using Tenor API. */
    private void performSearch(String query) {
        if (query == null || query.isEmpty()) return;
        new FetchGifTask().execute(query);
    }

    /** AsyncTask to fetch GIF search results. */
    private class FetchGifTask extends AsyncTask<String, Void, List<GifItem>> {
        @Override
        protected List<GifItem> doInBackground(String... params) {
            String q = params[0];
            List<GifItem> list = new ArrayList<>();
            try {
                String key = GifConfig.getTenorApiKey();
                String urlStr = "https://g.tenor.com/v1/search?q=" + URLEncoder.encode(q, StandardCharsets.UTF_8.name())
                        + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8.name())
                        + "&limit=10";
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.connect();
                InputStream in = new BufferedInputStream(conn.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close(); conn.disconnect();
                JSONObject root = new JSONObject(sb.toString());
                JSONArray results = root.optJSONArray("results");
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject item = results.getJSONObject(i);
                        String id = item.optString("id");
                        JSONArray media = item.optJSONArray("media");
                        if (media != null && media.length() > 0) {
                            JSONObject m0 = media.getJSONObject(0);
                            JSONObject tiny = m0.optJSONObject("tinygif");
                            JSONObject full = m0.optJSONObject("gif");
                            if (tiny != null && full != null) {
                                String preview = tiny.optString("url");
                                String fullUrl = full.optString("url");
                                list.add(new GifItem(id, preview, fullUrl));
                            }
                        }
                    }
                }
            } catch (Exception e) { /* ignore */ }
            return list;
        }

        @Override
        protected void onPostExecute(List<GifItem> items) {
            adapter.setItems(items);
        }
    }

    /** Model for GIF item. */
    private static class GifItem {
        final String id, previewUrl, fullUrl;
        GifItem(String id, String previewUrl, String fullUrl) {
            this.id = id; this.previewUrl = previewUrl; this.fullUrl = fullUrl;
        }
    }

    /** Adapter for displaying GIF previews. */
    private class GifAdapter extends RecyclerView.Adapter<GifAdapter.GifViewHolder> {
        private List<GifItem> items = new ArrayList<>();

        void setItems(List<GifItem> list) { this.items = list; notifyDataSetChanged(); }

        @Override
        public GifViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ImageButton iv = new ImageButton(parent.getContext());
            iv.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    parent.getWidth() / 3));
            iv.setScaleType(ImageButton.ScaleType.CENTER_CROP);
            return new GifViewHolder(iv);
        }

        @Override
        public void onBindViewHolder(GifViewHolder holder, int position) {
            GifItem item = items.get(position);
            holder.image.setImageDrawable(null);
            new ImageLoadTask(holder.image).execute(item.previewUrl);
            holder.image.setOnClickListener(v -> new DownloadAndSendTask(item).execute(item));
        }

        @Override public int getItemCount() { return items.size(); }

        class GifViewHolder extends RecyclerView.ViewHolder {
            final ImageButton image;
            GifViewHolder(View v) { super(v); image = (ImageButton) v; }
        }
    }

    /** AsyncTask to load an image. */
    private static class ImageLoadTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageButton iv;
        ImageLoadTask(ImageButton iv) { this.iv = iv; }
        @Override protected Bitmap doInBackground(String... urls) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urls[0]).openConnection();
                conn.connect();
                InputStream is = conn.getInputStream();
                Bitmap bm = BitmapFactory.decodeStream(is);
                is.close(); conn.disconnect();
                return bm;
            } catch (Exception e) { return null; }
        }
        @Override protected void onPostExecute(Bitmap bm) { if (bm != null) iv.setImageBitmap(bm); }
    }

    /** Downloads the full GIF and commits to the editor. */
    private class DownloadAndSendTask extends AsyncTask<GifItem, Void, Uri> {
        private final GifItem item;
        DownloadAndSendTask(GifItem item) { this.item = item; }
        @Override protected Uri doInBackground(GifItem... params) {
            try {
                File dir = new File(getContext().getCacheDir(), "tenor_gifs");
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, item.id + ".gif");
                if (!out.exists()) {
                    HttpURLConnection conn = (HttpURLConnection)
                            new URL(item.fullUrl).openConnection();
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[4096]; int r;
                    while ((r = is.read(buf)) > 0) fos.write(buf, 0, r);
                    fos.close(); is.close(); conn.disconnect();
                }
                return FileProvider.getUriForFile(getContext(),
                        getContext().getPackageName() + ".fileprovider", out);
            } catch (Exception e) { return null; }
        }
        @Override protected void onPostExecute(Uri uri) {
            if (uri == null) return;
            ContextWrapper base = (ContextWrapper) getContext();
            while (!(base instanceof android.inputmethodservice.InputMethodService) && 
                    base.getBaseContext() instanceof ContextWrapper) {
                base = (ContextWrapper) base.getBaseContext();
            }
            if (!(base instanceof android.inputmethodservice.InputMethodService)) return;
            android.inputmethodservice.InputMethodService ims = (android.inputmethodservice.InputMethodService) base;
            InputConnection ic = ims.getCurrentInputConnection();
            EditorInfo ei = ims.getCurrentInputEditorInfo();
            ClipDescription desc = new ClipDescription("GIF", new String[]{"image/gif"});
            InputContentInfoCompat info = new InputContentInfoCompat(uri, desc, null);
            InputConnectionCompat.commitContent(ic, ei, info,
                    InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null);
        }
    }
}