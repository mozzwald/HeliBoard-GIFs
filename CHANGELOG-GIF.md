# Changelog: GIF Integration Feature

This document summarizes the changes introduced to enable GIF search and insertion within the emoji toolbar.

## AndroidManifest.xml
- Added `INTERNET` permission to allow network access for GIF search:
  ```xml
  <uses-permission android:name="android.permission.INTERNET" />
  ```
- Registered a `FileProvider` to securely share downloaded GIF files:
  ```xml
  <provider
      android:name="androidx.core.content.FileProvider"
      android:authorities="${applicationId}.fileprovider"
      android:exported="false"
      android:grantUriPermissions="true"
      android:resource="@xml/file_paths" />
  ```

## res/xml/file_paths.xml
- Created `file_paths.xml` to expose the cache directory for GIF storage:
  ```xml
  <paths xmlns:android="http://schemas.android.com/apk/res/android">
      <cache-path name="tenor_gifs" path="tenor_gifs/" />
  </paths>
  ```

## Gradle Configuration
- In `app/build.gradle.kts`, introduced a `TENOR_API_KEY` project property and injected it into `BuildConfig`:
  ```kotlin
  val tenorKey: String = project.findProperty("TENOR_API_KEY") as? String ?: ""
  android {
    defaultConfig {
      buildConfigField("String", "TENOR_API_KEY", "\"$tenorKey\"")
    }
  }
  ```

## GifConfig.java
- Added `GifConfig` with a static getter for `BuildConfig.TENOR_API_KEY`:
  ```java
  public class GifConfig {
      public static String getTenorApiKey() {
          return BuildConfig.TENOR_API_KEY;
      }
  }
  ```

## Toolbar Icon Update
- Created placeholder drawable `res/drawable/ic_emoji_gif.xml` for the GIF tab.
- Updated `KeyboardIconsSet.kt` to map `ToolbarKey.EMOJI` to `R.drawable.ic_emoji_gif` in all toolbar styles.

## EmojiPalettesView Modifications
- In `emoji_palettes_view.xml`, replaced the empty GIF container with our custom `<GifSearchView>`.
- In `EmojiPalettesView.java`:
  - Introduced `mGifTab` and `mGifSearchView` fields.
  - During initialization, appended a GIF tab to the category strip.
  - Enhanced `onClick(...)` to toggle between the emoji pager and GIF search view when the GIF tab is selected.

## GifSearchView Component
- New layout `res/layout/gif_search_view.xml`:
  - A horizontal row with an `EditText` (query) and `ImageButton` (search).
  - A `RecyclerView` grid to display GIF previews.
- New class `GifSearchView.java`:
  - Inflates `gif_search_view.xml` and binds its views.
  - Sets up a 3-column `RecyclerView` with `GifAdapter`.
  - Listens for search actions (button click or IME search) and executes `FetchGifTask`.
  - `FetchGifTask`: queries Tenor API, parses JSON, and produces a list of `(id, previewUrl, fullUrl)` items.
  - `GifAdapter`: downloads preview bitmaps via `ImageLoadTask` and displays them.
  - Tapping a thumbnail triggers `DownloadAndSendTask`, which:
    1. Downloads the full GIF to `cache/tenor_gifs/<id>.gif`.
    2. Wraps it via `FileProvider`.
    3. Commits the content to the editor with `InputConnectionCompat.commitContent(...)`.

All changes ensure seamless integration of GIF search and insertion directly within the emoji toolbar.  
Future work: refine UI styling, implement paging/loading indicators, and error handling.