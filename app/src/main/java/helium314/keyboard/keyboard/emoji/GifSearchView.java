package helium314.keyboard.keyboard.emoji;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.util.Log;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.GifConfig;
import helium314.keyboard.latin.GifPrefs;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.content.Context;
import android.widget.Toast;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import androidx.annotation.Nullable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
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
import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import android.net.Uri;
import android.inputmethodservice.InputMethodService;
import android.graphics.Movie;
import android.graphics.Canvas;
import android.widget.Toast;
import java.io.FileInputStream;
import android.content.Intent;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/**
 * A view for searching and displaying GIFs.
 */
public class GifSearchView extends LinearLayout {
    private static final String TAG = "GifSearchView";
    private EditText queryField;
    private ImageButton searchButton;
    private RecyclerView grid;
    private GifAdapter adapter;
    private GifActionsListener actionsListener;
    private GridLayoutManager layoutManager;
    private RequestManager glide;
    // Pagination state for endless scrolling
    private volatile boolean isLoading = false;
    private volatile boolean hasMore = true;
    @Nullable private String nextPos = null;
    @Nullable private String currentQuery = null;
    private ImageButton clearButton;
    // Helpers for editing state
    public boolean hasResults() {
        return adapter != null && adapter.getItemCount() > 0;
    }
    public int getQueryLength() {
        if (queryField == null || queryField.getText() == null) return 0;
        return queryField.getText().length();
    }
    private int spanCount = 2; // will be recalculated at runtime

    public GifSearchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Inflate layout and ensure focus/touch configuration
        View.inflate(context, R.layout.gif_search_view, this);
        setClickable(false);
        setFocusable(true);
        setFocusableInTouchMode(false);
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        queryField = findViewById(R.id.gif_query_field);
        // Notify editing state when query changes
        queryField.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                boolean wantsKeys = (s == null || s.length() == 0) || !hasResults();
                if (actionsListener != null) actionsListener.onGifEditingStateChanged(wantsKeys);
                // Show or hide clear button
                if (clearButton != null) {
                    clearButton.setVisibility(s != null && s.length() > 0 ? View.VISIBLE : View.GONE);
                }
            }
        });
        searchButton = findViewById(R.id.btn_search_gif);
        // Clear button
        clearButton = findViewById(R.id.btn_clear_gif);
        clearButton.setVisibility(View.GONE);
        clearButton.setOnClickListener(v -> resetGifUi());
        // Ensure search button is clickable and has a background for hit-testing
        searchButton.setClickable(true);
        searchButton.setFocusable(false);
        searchButton.setFocusableInTouchMode(false);
        searchButton.setBackgroundResource(android.R.drawable.btn_default);
        grid = findViewById(R.id.gif_results_grid);
        // Setup Glide for animated previews
        glide = Glide.with(this);
        grid.setHasFixedSize(true);
        grid.setItemViewCacheSize(20);
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrollStateChanged(RecyclerView rv, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    glide.pauseRequests();
                } else {
                    glide.resumeRequests();
                }
            }
        });
        // Endless scroll for loading more GIFs
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy <= 0) return; // only down
                if (isLoading || !hasMore) return;
                RecyclerView.LayoutManager lm = rv.getLayoutManager();
                if (!(lm instanceof GridLayoutManager)) return;
                GridLayoutManager glm = (GridLayoutManager) lm;
                int total = adapter.getItemCount();
                int lastVisible = glm.findLastVisibleItemPosition();
                int threshold = 6;
                if (total > 0 && lastVisible >= total - threshold) {
                    GifSearchView.this.loadNextPage();
                }
            }
        });
        // Recompute span count once grid knows its width
        grid.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            int w = grid.getWidth();
            if (w <= 0) return;

            // desired minimum cell size in dp (tweak 120–140dp to taste)
            int minCellPx = (int) (getResources().getDisplayMetrics().density * 128);

            int cols = Math.max(2, Math.min(3, w / Math.max(1, minCellPx)));
            if (cols != spanCount) {
                spanCount = cols;
                layoutManager.setSpanCount(spanCount);
                adapter.notifyDataSetChanged();
            }
        });
        // Ensure grid is clickable to intercept taps
        grid.setClickable(true);
        grid.setFocusable(true);
        grid.setFocusableInTouchMode(true);
        // set up grid and adapter
        layoutManager = new GridLayoutManager(context, spanCount);
        grid.setLayoutManager(layoutManager);
        adapter = new GifAdapter();
        grid.setAdapter(adapter);
        // Add gesture-based single-tap listener for grid items
        final GestureDetector gestureDetector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override public boolean onSingleTapUp(MotionEvent e) { return true; }
                });
        grid.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            @Override
            public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
                if (!gestureDetector.onTouchEvent(e)) return false;
                View child = rv.findChildViewUnder(e.getX(), e.getY());
                if (child != null) {
                    int pos = rv.getChildAdapterPosition(child);
                    Log.d(TAG, "gif thumbnail tap id=" + pos);
                    child.performClick();
                    return true;
                }
                return false;
            }
            @Override public void onTouchEvent(RecyclerView rv, MotionEvent e) { }
            @Override public void onRequestDisallowInterceptTouchEvent(boolean disallow) { }
        });
        // Ensure interactive children receive clicks
        grid.setClickable(true);
        grid.setFocusable(true);
        grid.setFocusableInTouchMode(true);
        searchButton.setClickable(true);
        searchButton.setFocusable(true);
        // search on button click or IME action
        searchButton.setOnClickListener(v -> {
            Log.d(TAG, "searchButton onClick");
            performSearch(queryField.getText().toString());
        });
        queryField.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch(queryField.getText().toString());
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Constrain the panel to a compact height so it sits above the main keyboard
        int strip = getResources().getDimensionPixelSize(R.dimen.config_suggestions_strip_height);
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp != null) {
            lp.height = strip * 4; // search row + a few rows of results
            setLayoutParams(lp);
        }
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
    public void performSearch(String query) {
        if (query == null || query.isEmpty()) return;
        // Check Tenor enabled and API key
        Context ctx = getContext();
        if (!helium314.keyboard.latin.GifPrefs.isTenorEnabled(ctx)) {
            Toast.makeText(ctx, "Enable Tenor in Settings to search GIFs", Toast.LENGTH_SHORT).show();
            return;
        }
        String key = GifConfig.getTenorApiKey(ctx);
        if (key == null || key.isEmpty()) {
            Toast.makeText(ctx, "Add your Tenor API key in Settings", Toast.LENGTH_SHORT).show();
            return;
        }
        Log.d(TAG, "Tenor enabled; using API key length=" + key.length());
        // Reset pagination state for new search
        currentQuery = query;
        isLoading = false;
        hasMore = true;
        nextPos = null;
        adapter.setItems(Collections.emptyList());
        adapter.notifyDataSetChanged();
        if (actionsListener != null) actionsListener.onGifEditingStateChanged(true);
        new FetchGifTask().execute(query);
    }
    /**
     * Load next page of GIF results (pagination).
     */
    public void loadNextPage() {
        if (isLoading || !hasMore) return;
        if (currentQuery == null || currentQuery.isEmpty()) return;
        if (nextPos == null || nextPos.isEmpty()) { hasMore = false; return; }
        isLoading = true;
        new FetchGifTask().execute(currentQuery, nextPos);
    }
    /**
     * Listener to notify when GIF actions occur.
     */
    public interface GifActionsListener {
        /** Called when a GIF has been inserted into the editor. */
        void onGifInsertCompleted();
        /** Called when GIF search results are visible (non-empty). */
        void onGifResultsVisible();
        /**
         * Called when the editing state changes: wantsKeysVisible=true if the user should see
         * letter keys (editing or no results), false when browsing results.
         */
        void onGifEditingStateChanged(boolean wantsKeysVisible);
    }
    /**
     * Set listener for GIF insertion completion.
     */
    public void setActionsListener(GifActionsListener l) {
        this.actionsListener = l;
        // Initial editing state: no results, so keys visible
        if (actionsListener != null) {
            actionsListener.onGifEditingStateChanged(true);
        }
    }
    /**
     * Reset the GIF UI: clear query and results.
     */
    private void resetGifUi() {
        try {
            EditText q = findViewById(R.id.gif_query_field);
            if (q != null) q.setText("");
            if (adapter != null) {
                adapter.setItems(Collections.emptyList());
                adapter.notifyDataSetChanged();
            }
            if (grid != null) grid.scrollToPosition(0);
            // After reset, editing state: show keys
            if (actionsListener != null) actionsListener.onGifEditingStateChanged(true);
        } catch (Throwable t) {
            Log.w(TAG, "resetGifUi: " + t);
        }
    }

    /** AsyncTask to fetch GIF search results. */
    private class FetchGifTask extends AsyncTask<String, Void, List<GifItem>> {
        private boolean isLoadMore;
        @Override
        protected List<GifItem> doInBackground(String... params) {
            String q = params[0];
            String pos = (params.length > 1) ? params[1] : null;
            isLoadMore = (pos != null && !pos.isEmpty());
            currentQuery = q;
            List<GifItem> list = new ArrayList<>();
            HttpURLConnection conn = null;
            try {
                // Check Tenor enabled and fetch API key
                Context ctx = getContext();
                if (!helium314.keyboard.latin.GifPrefs.isTenorEnabled(ctx)) {
                    Log.d(TAG, "Tenor disabled in settings");
                    return list;
                }
                String key = GifConfig.getTenorApiKey(ctx);
                if (key == null || key.isEmpty()) {
                    Log.d(TAG, "No Tenor API key provided");
                    return list;
                }
                String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8.name());
                String encodedQ = URLEncoder.encode(q, StandardCharsets.UTF_8.name());
                StringBuilder sbUrl = new StringBuilder(
                        "https://tenor.googleapis.com/v2/search?key=" + encodedKey
                        + "&q=" + encodedQ
                        + "&limit=25&media_filter=gif,tinygif&client_key=HeliBoard");
                if (pos != null && !pos.isEmpty()) {
                    sbUrl.append("&pos=").append(URLEncoder.encode(pos, StandardCharsets.UTF_8.name()));
                }
                String urlStr = sbUrl.toString();
                // Log request with masked key
                //String logUrl = urlStr.replaceAll("key=[^&]*", "key=****");
                Log.d(TAG, "Tenor API Request: " + urlStr);
                conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.connect();
                int status = conn.getResponseCode();
                Log.d(TAG, "Tenor API HTTP status: " + status);
                InputStream in = new BufferedInputStream(conn.getInputStream());
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                JSONObject root = new JSONObject(sb.toString());
                // Pagination cursor
                String next = root.optString("next", null);
                nextPos = (next != null && !next.isEmpty()) ? next : null;
                hasMore = (nextPos != null);
                JSONArray results = root.optJSONArray("results");
                if (results != null) {
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject item = results.getJSONObject(i);
                        String id = item.optString("id");
                        JSONObject media = item.optJSONObject("media_formats");
                        if (media != null) {
                            JSONObject tiny = media.optJSONObject("tinygif");
                            if (tiny == null) {
                                tiny = media.optJSONObject("nanogif");
                            }
                            JSONObject full = media.optJSONObject("gif");
                            if (tiny != null && full != null) {
                                String preview = tiny.optString("url");
                                String fullUrl = full.optString("url");
                                list.add(new GifItem(id, preview, fullUrl));
                            }
                        }
                    }
                }
                Log.d(TAG, "Parsed GIF results: " + list.size() + " next=" + nextPos);
            } catch (Exception e) {
                // ignore
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
            return list;
        }

        @Override
        protected void onPostExecute(List<GifItem> items) {
            isLoading = false;
            if (items == null || items.isEmpty()) {
                hasMore = false;
                return;
            }
            if (isLoadMore) {
                // Append more items
                int start = adapter.getItemCount();
                adapter.appendItems(items);
                adapter.notifyItemRangeInserted(start, items.size());
            } else {
                // First page
                adapter.setItems(items);
                adapter.notifyDataSetChanged();
                if (actionsListener != null) actionsListener.onGifResultsVisible();
            }
            // After load, editing state depends on having results => browsing mode hides keys
            if (actionsListener != null) actionsListener.onGifEditingStateChanged(false);
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
        /** Append items for pagination. */
        void appendItems(List<GifItem> more) {
            if (more == null || more.isEmpty()) return;
            this.items.addAll(more);
        }

        @Override
        public GifViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            ImageButton iv = new ImageButton(parent.getContext());

            int parentW = ((RecyclerView) parent).getMeasuredWidth();
            if (parentW <= 0) {
                parentW = parent.getContext().getResources().getDisplayMetrics().widthPixels;
            }

            GridLayoutManager lm = (GridLayoutManager) ((RecyclerView) parent).getLayoutManager();
            int cols = (lm != null ? lm.getSpanCount() : 2);
            int size = Math.max(1, parentW / Math.max(1, cols));

            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(size, size);
            iv.setLayoutParams(lp);

            iv.setBackgroundResource(android.R.drawable.btn_default);
            iv.setScaleType(ImageButton.ScaleType.CENTER_CROP);
            iv.setClickable(true);
            iv.setFocusable(true);
            iv.setFocusableInTouchMode(true);
            return new GifViewHolder(iv);
        }

        @Override
        public void onBindViewHolder(GifViewHolder holder, int position) {
            // keep square cells even after span changes
            holder.image.post(() -> {
                ViewGroup.LayoutParams lp = holder.image.getLayoutParams();
                int w = holder.image.getWidth();
                if (w > 0 && lp.height != w) {
                    lp.height = w;
                    holder.image.setLayoutParams(lp);
                }
            });
            GifItem item = items.get(position);
            // Clear any previous request
            Glide.with(holder.image).clear(holder.image);
            // Load animated tiny GIF preview
            glide.asGif()
                 .load(item.previewUrl)
                 .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                 .transition(DrawableTransitionOptions.withCrossFade())
                 .centerCrop()
                 .into(holder.image);
            holder.image.setOnClickListener(v -> {
                Log.d(TAG, "thumbnail onClick id=" + item.id + " fullUrl=" + item.fullUrl);
                new DownloadAndSendTask(item).execute(item);
            });
        }

        @Override public int getItemCount() { return items.size(); }
        @Override
        public void onViewRecycled(GifViewHolder holder) {
            Glide.with(holder.image).clear(holder.image);
            super.onViewRecycled(holder);
        }

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
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(15000);
                conn.connect();
                InputStream is = conn.getInputStream();
                Bitmap bm = BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();
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
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.e(TAG, "Failed to create cache directory: " + dir.getAbsolutePath());
                }
                File out = new File(dir, item.id + ".gif");
                if (!out.exists()) {
                    HttpURLConnection conn = (HttpURLConnection)
                            new URL(item.fullUrl).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.connect();
                    InputStream is = conn.getInputStream();
                    FileOutputStream fos = new FileOutputStream(out);
                    byte[] buf = new byte[4096]; int r;
                    while ((r = is.read(buf)) > 0) fos.write(buf, 0, r);
                    fos.close(); is.close(); conn.disconnect();
                }
                long size = out.length();
                Log.d(TAG, "Saved GIF file " + out.getAbsolutePath() + " size=" + size + " bytes");
                if (size <= 0) {
                    Log.e(TAG, "Downloaded GIF is empty, aborting insert");
                    return null;
                }
                String authority = getContext().getPackageName() + ".fileprovider";
                Log.d(TAG, "Using FileProvider authority=" + authority);
                return FileProvider.getUriForFile(getContext(), authority, out);
            } catch (Exception e) {
                Log.e(TAG, "doInBackground error downloading GIF: " + e);
                return null;
            }
        }
        @Override protected void onPostExecute(Uri uri) {
            if (uri == null) {
                Log.e(TAG, "Insert aborted: uri is null");
                return;
            }
            InputMethodService ims = getImeService();
            if (ims == null) {
                Log.e(TAG, "Insert aborted: could not resolve InputMethodService from context=" + getContext());
                return;
            }
            InputConnection ic = ims.getCurrentInputConnection();
            EditorInfo ei = ims.getCurrentInputEditorInfo();
            if (ic == null || ei == null) {
                Log.e(TAG, "Insert aborted: ic=" + ic + " ei=" + ei);
                return;
            }
            try {
                ic.finishComposingText();
            } catch (Throwable t) {
                Log.w(TAG, "finishComposingText failed: " + t);
            }
            String[] mimes = androidx.core.view.inputmethod.EditorInfoCompat.getContentMimeTypes(ei);
            boolean supportsGif = false;
            if (mimes != null) {
                StringBuilder sb = new StringBuilder();
                for (String m : mimes) {
                    sb.append(m).append(" ");
                    if (ClipDescription.compareMimeTypes(m, "image/gif")) supportsGif = true;
                }
                Log.d(TAG, "Editor supports MIME(s): " + sb.toString().trim());
            } else {
                Log.d(TAG, "Editor has no declared content MIME types");
            }
            String authority = getContext().getPackageName() + ".fileprovider";
            // Try GIF first
            if (tryCommit(ic, ei, uri, "image/gif")) {
                resetGifUi();
                GifSearchView.this.setVisibility(View.GONE);
                InputMethodService ims2 = getImeService();
                if (ims2 != null) {
                    try {
                        helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().setAlphabetKeyboard();
                    } catch (Throwable t) {
                        Log.w(TAG, "Error switching to alphabet keyboard: " + t);
                    }
                }
                if (actionsListener != null) {
                    actionsListener.onGifInsertCompleted();
                }
                return;
            }
            // Try static PNG first frame
            File gifFile = new File(getContext().getCacheDir(), "tenor_gifs/" + item.id + ".gif");
            File pngFile = convertGifToPngFirstFrame(gifFile);
            if (pngFile != null) {
                Uri pngUri = FileProvider.getUriForFile(getContext(), authority, pngFile);
                if (tryCommit(ic, ei, pngUri, "image/png")) {
                    resetGifUi();
                    GifSearchView.this.setVisibility(View.GONE);
                    InputMethodService ims3 = getImeService();
                    if (ims3 != null) {
                        try {
                            helium314.keyboard.keyboard.KeyboardSwitcher.getInstance().setAlphabetKeyboard();
                        } catch (Throwable t) {
                            Log.w(TAG, "Error switching to alphabet keyboard: " + t);
                        }
                    }
                    if (actionsListener != null) {
                        actionsListener.onGifInsertCompleted();
                    }
                    return;
                }
            }
            // All commitContent attempts failed; fallback to Share sheet
            // Prefer sharing the original GIF
            File gifFileShare = new File(getContext().getCacheDir(), "tenor_gifs/" + item.id + ".gif");
            if (gifFileShare.exists()) {
                Uri shareGif = FileProvider.getUriForFile(getContext(), authority, gifFileShare);
                Log.d(TAG, "Falling back to Share sheet with GIF");
                launchShareSheet(shareGif, "image/gif");
                return;
            } else if (pngFile != null && pngFile.exists()) {
                Uri sharePng = FileProvider.getUriForFile(getContext(), authority, pngFile);
                Log.d(TAG, "Falling back to Share sheet with PNG");
                launchShareSheet(sharePng, "image/png");
                return;
            } else {
                Log.e(TAG, "All commit and share fallbacks failed; no file to share");
                Toast.makeText(getContext(), "This app doesn't accept images from the keyboard", Toast.LENGTH_SHORT).show();
            }
    }

    /**
     * Resolve the hosting InputMethodService by traversing the context chain.
     */
    private InputMethodService getImeService() {
        Context c = getContext();
        int guard = 0;
        while (c instanceof ContextWrapper && guard < 10) {
            if (c instanceof InputMethodService) {
                return (InputMethodService) c;
            }
            c = ((ContextWrapper) c).getBaseContext();
            guard++;
        }
        return null;
    }
    // Helper to try committing content with a given MIME type
    private boolean tryCommit(InputConnection ic, EditorInfo ei, Uri uri, String mime) {
        ClipDescription desc = new ClipDescription("IMAGE", new String[]{mime});
        InputContentInfoCompat info = new InputContentInfoCompat(uri, desc, null);
        int flags = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
        boolean ok = false;
        try {
            ok = InputConnectionCompat.commitContent(ic, ei, info, flags, null);
        } catch (Throwable t) {
            Log.e(TAG, "commitContent(" + mime + ") threw: " + t);
        }
        Log.d(TAG, "commitContent(" + mime + ") returned=" + ok + " uri=" + uri);
        return ok;
    }

    // Helper to convert the first frame of a GIF to a static PNG
    private File convertGifToPngFirstFrame(File gifFile) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(gifFile);
            Movie mv = Movie.decodeStream(fis);
            if (mv == null) {
                Log.e(TAG, "Movie decode failed");
                return null;
            }
            int w = Math.max(1, mv.width());
            int h = Math.max(1, mv.height());
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            mv.setTime(0);
            mv.draw(c, 0, 0);
            File outDir = new File(getContext().getCacheDir(), "tenor_gifs_png");
            if (!outDir.exists() && !outDir.mkdirs()) {
                Log.e(TAG, "Failed to create cache directory for PNG: " + outDir.getAbsolutePath());
                return null;
            }
            String name = gifFile.getName();
            if (name.toLowerCase().endsWith(".gif")) {
                name = name.substring(0, name.length() - 4);
            }
            File outPng = new File(outDir, name + ".png");
            FileOutputStream fos = new FileOutputStream(outPng);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            Log.d(TAG, "Wrote PNG first-frame: " + outPng.getAbsolutePath() + " size=" + outPng.length());
            return outPng;
        } catch (Throwable t) {
            Log.e(TAG, "convertGifToPngFirstFrame failed", t);
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (Throwable ignore) {}
            }
        }
    }


    // Fallback: launch system share sheet with the image URI
    private void launchShareSheet(Uri uri, String mime) {
        if (uri == null || mime == null) {
            Log.e(TAG, "launchShareSheet: missing uri or mime");
            Toast.makeText(getContext(), "Unable to share image", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(mime);
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            send.setClipData(ClipData.newUri(getContext().getContentResolver(), "image", uri));
            PackageManager pm = getContext().getPackageManager();
            List<ResolveInfo> targets = pm.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY);
            for (ResolveInfo ri : targets) {
                String pkg = ri.activityInfo.packageName;
                try {
                    getContext().grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Throwable t) {
                    Log.w(TAG, "grantUriPermission failed for " + pkg + ": " + t);
                }
            }
            Intent chooser = Intent.createChooser(send, "Send GIF");
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(chooser);
            resetGifUi();
            if (actionsListener != null) actionsListener.onGifInsertCompleted();
        } catch (Throwable t) {
            Log.e(TAG, "launchShareSheet failed", t);
            Toast.makeText(getContext(), "No app available to share image", Toast.LENGTH_SHORT).show();
        }
    }
} // end DownloadAndSendTask

    // Programmatic query editing for GIF search
    public void appendQueryChar(char c) {
        queryField.append(Character.toString(c));
    }

    public void deleteLastChar() {
        String s = queryField.getText().toString();
        if (!s.isEmpty()) {
            String newText = s.substring(0, s.length() - 1);
            queryField.setText(newText);
            queryField.setSelection(newText.length());
        }
    }
    /** Never intercept; let children handle touches first, but log intercept events */
    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_UP:
                Log.d(TAG, "GifSearchView onInterceptTouchEvent action=" + ev.getActionMasked());
                break;
        }
        return false;
    }

    /** Consume unhandled touch events to prevent click-through, logging them */
    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_UP:
                Log.d(TAG, "GifSearchView onTouchEvent action=" + ev.getActionMasked());
                break;
        }
        return false;
    }

    /** Dispatch to children, then pass to onTouchEvent if unhandled */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean handled = super.dispatchTouchEvent(ev);
        /*if (!handled) {
            handled = onTouchEvent(ev);
        }*/
        Log.d(TAG, "GifSearchView dispatchTouchEvent handled=" + handled + " action=" + ev.getActionMasked());
        return handled;
    }
}