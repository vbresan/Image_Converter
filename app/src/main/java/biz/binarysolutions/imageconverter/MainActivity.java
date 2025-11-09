package biz.binarysolutions.imageconverter;

import static android.provider.OpenableColumns.DISPLAY_NAME;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import biz.binarysolutions.imageconverter.data.FilenameUriTuple;
import biz.binarysolutions.imageconverter.data.OutputFormat;
import biz.binarysolutions.imageconverter.exceptions.ConvertException;
import biz.binarysolutions.imageconverter.exceptions.DecodeException;
import biz.binarysolutions.imageconverter.exceptions.EncodeException;
import biz.binarysolutions.imageconverter.exceptions.ExportException;
import biz.binarysolutions.imageconverter.image.Converter;
import biz.binarysolutions.imageconverter.image.TiffUtil;
import biz.binarysolutions.imageconverter.listeners.OutputFormatListener;
import biz.binarysolutions.imageconverter.util.FileUtil;
import biz.binarysolutions.imageconverter.util.PermissionActivity;

/**
 * TODO: add resize functionality?
 * TODO: copy metadata (exif, etc.)?
 * TODO: add action on selected file in file browser
 */
public class MainActivity extends PermissionActivity {

    private static final int REQUEST_CODE_PICK_FILE = 1;

    private ArrayAdapter<FilenameUriTuple> adapter;

    private final List<FilenameUriTuple> files         = new ArrayList<>();
    protected List<OutputFormat>         outputFormats = new ArrayList<>();

    private volatile boolean stopConversion = false;

    /**
     *
     * @param uri
     * @return
     */
    private String getFileName(Uri uri) {

        String result = null;

        String scheme = uri.getScheme();
        if (scheme != null && scheme.equals("content")) {

            ContentResolver resolver = getContentResolver();
            Cursor cursor = resolver.query(uri, null, null, null, null);

            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndex(DISPLAY_NAME);
                    if (columnIndex >= 0) {
                        result = cursor.getString(columnIndex);
                    }
                }

                cursor.close();
            }
        }

        if (result == null) {
            result = uri.getLastPathSegment();
        }

        if (result == null) {
            result = getString(R.string.unknown_file_name);
        }

        return result;
    }

    /**
     *
     */
    private void refreshInputFilesNumber() {

        TextView textView = findViewById(R.id.textViewInputFiles);
        if (textView != null) {
            textView.setText(getString(R.string.input_files, files.size()));
        }
    }

    /**
     *
     * @param tuple
     */
    private void addTuple(FilenameUriTuple tuple) {

        if (files.contains(tuple)) {
            return;
        }

        files.add(tuple);
        adapter.add(tuple);

        refreshInputFilesNumber();
    }

    private void removeTuple(final FilenameUriTuple tuple) {

        files.remove(tuple);
        runOnUiThread(() -> {
            adapter.remove(tuple);
            refreshInputFilesNumber();
        });
    }

    private void displayDialogConfirmFileRemove(final int index) {

        final FilenameUriTuple tuple = files.get(index);

        new AlertDialog.Builder(this)
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage(getString(R.string.confirm_remove, tuple.getFilename()))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                android.R.string.yes,
                (dialog, which) -> removeTuple(tuple)
            )
            .create()
            .show();
    }

    /**
     *
     * @param errors
     */
    private void displayDialogReceivedExceptions(List<String> errors) {

        if (errors.size() == 0) {
            return;
        }

        final StringBuilder sb = new StringBuilder();
        for (String error: errors) {
            sb.append(error).append("\n");
        }

        runOnUiThread(() -> {

            LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
            View container = inflater.inflate(R.layout.dialog_errors, null);

            TextView textView = container.findViewById(R.id.textViewErrors);
            if (textView != null) {
                textView.setText(sb);
            }

            new AlertDialog.Builder(MainActivity.this)
                .setTitle(android.R.string.dialog_alert_title)
                .setView(container)
                .setOnCancelListener(dialog -> onErrorDialogDismissed())
                .setNegativeButton(
                    android.R.string.ok,
                    (dialog, which) -> onErrorDialogDismissed()
                )
                .create()
                .show();
        });
    }

    private void displayToastDone() {

        runOnUiThread(() ->
            Toast.makeText(this, getString(R.string.done), Toast.LENGTH_SHORT).show()
        );
    }

    private void setListView() {

        ListView listView = findViewById(R.id.listViewInputFiles);
        if (listView == null) {
            return;
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(
            (p, v, pos, id) -> displayDialogConfirmFileRemove(pos)
        );
    }

    /**
     *
     * @param id
     * @param format
     */
    private void setCheckBoxListener(int id, OutputFormat format) {

        CheckBox checkBox = findViewById(id);
        if (checkBox == null) {
            return;
        }

        checkBox.setOnCheckedChangeListener(
            new OutputFormatListener(outputFormats, format)
        );
    }

    /**
     *
     */
    private void setCheckBoxListeners() {

        setCheckBoxListener(R.id.checkBoxJPEG, OutputFormat.JPG);
        setCheckBoxListener(R.id.checkBoxPNG,  OutputFormat.PNG);
        setCheckBoxListener(R.id.checkBoxWEBP, OutputFormat.WEBP);

        setCheckBoxListener(R.id.checkBoxBMP,  OutputFormat.BMP);
        setCheckBoxListener(R.id.checkBoxTIF,  OutputFormat.TIF);
    }

    private @Nullable String getMimeType(Uri uri) {

        String mimeType = null;

        Cursor cursor = getContentResolver().query(
            uri, new String[] { MediaStore.MediaColumns.MIME_TYPE },
            null, null, null
        );

        if (cursor != null) {
            if (cursor.moveToNext()) {
                try {
                    mimeType = cursor.getString(0);
                } catch (Exception e) {
                    // do nothing
                }
            }
            cursor.close();
        }

        return mimeType;
    }

    private void publishStatus(final String status) {

        runOnUiThread(() -> {

            TextView textView = findViewById(R.id.textViewStatus);
            if (textView != null) {
                textView.setText(status);
                textView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void hideStatus() {

        runOnUiThread(() -> {
            TextView textView = findViewById(R.id.textViewStatus);
            if (textView != null) {
                textView.setText("");
                textView.setVisibility(View.INVISIBLE);
            }
        });
    }

    private String getRelativePath() {
        return
            Environment.DIRECTORY_PICTURES
            + File.separator
            + getString(R.string.directory_name);
    }

    private void exportFile(FilenameUriTuple file, String mimeType)
        throws ExportException {
        // TODO: do not overwrite existing file

        publishStatus(getString(R.string.exporting, file));

        ContentValues values = new ContentValues();
        values.put(Media.DISPLAY_NAME, file.getFilename());
        values.put(Media.MIME_TYPE, mimeType);
        values.put(Media.RELATIVE_PATH, getRelativePath());
        values.put(Media.IS_PENDING, 1);

        ContentResolver resolver = getContentResolver();
        Uri gallery = Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = resolver.insert(gallery, values);
        if (uri == null) {
            throw new ExportException();
        }

        try {
            InputStream  in  = resolver.openInputStream(file.getUri());
            OutputStream out = resolver.openOutputStream(uri);

            if (in != null && out != null) {

                byte[] buffer = new byte[8 * 1024];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
            }

            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            throw new ExportException(e);
        }

        values.clear();
        values.put(Media.IS_PENDING, 0);
        resolver.update(uri, values, null, null);
    }

    /**
     *
     * @return
     * @param filename
     * @param format
     */
    private String getNewFilename(String filename, OutputFormat format) {

        String substring;

        int index = filename.lastIndexOf(".");
        if (index == -1) {
            substring = filename;
        } else {
            substring = filename.substring(0, index);
        }

        return substring + "." + format.getFileExtension();
    }

    protected List<FilenameUriTuple> convertUsingNonNativeAPI
        (
            File         file,
            OutputFormat format
        )
        throws ConvertException {
        // TODO: update progress bar

        if (! TiffUtil.isTiffFile(file)) {
            throw new DecodeException();
        }

        return TiffUtil.convertFromTIF(file, format, getCacheDir());
    }

    /**
     *
     * @return
     * @param uri
     */
    private InputStream getInputStream(Uri uri) throws DecodeException {

        InputStream is;

        try {
            is = getContentResolver().openInputStream(uri);
        } catch (FileNotFoundException e) {
            throw new DecodeException(e);
        }

        return is;
    }

    private File getTempFile(String filename, InputStream is)
        throws DecodeException {

        try {
            if (is == null) {
                throw new IOException("Re-opened input stream is null.");
            }

            File file = File.createTempFile(filename, "", getCacheDir());
            FileUtil.copy(is, file);

            return file;
        } catch (IOException e) {
            throw new DecodeException(e);
        }
    }

    private File getTempFile(String filename)
        throws DecodeException {

        try {
            return File.createTempFile(filename, "", getCacheDir());
        } catch (IOException e) {
            throw new DecodeException(e);
        }
    }

    private List<FilenameUriTuple> doConvertFile
        (
            FilenameUriTuple file,
            OutputFormat     format
        )
        throws ConvertException {
        // TODO: do not overwrite existing file

        publishStatus(getString(R.string.converting_to, file, format));

        String inFilename = file.getFilename();

        InputStream is     = getInputStream(file.getUri());
        Bitmap      bitmap = BitmapFactory.decodeStream(is);

        // TODO: remove this once tif library is fixed
        if (inFilename.endsWith(".gif") && format == OutputFormat.TIF) {
            try {
                bitmap = Converter.recodeGIFBitmap(bitmap, getCacheDir());
            } catch (IOException e) {
                throw new EncodeException(e);
            }
        }

        if (bitmap != null) {
            //TODO: extract method - encodeBitmap?
            String outFilename = getNewFilename(inFilename, format);
            File   outFile     = getTempFile(outFilename);

            try {
                Converter.encodeBitmap(bitmap, format, outFile);
            } catch (IOException e) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
                throw new EncodeException(e);
            } catch (OutOfMemoryError e) {
                //noinspection ResultOfMethodCallIgnored
                outFile.delete();
                throw e;
            }

            return List.of(new FilenameUriTuple(outFilename, Uri.fromFile(outFile)));
        } else {
            is = getInputStream(file.getUri());
            File tempFile = getTempFile(inFilename, is);

            try {
                return convertUsingNonNativeAPI(tempFile, format);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tempFile.delete();
            }
        }
    }

    private void convertFile(FilenameUriTuple inFile, OutputFormat format)
        throws ConvertException {

        String inMimeType  = getMimeType(inFile.getUri());
        String outMimeType = format.getMimeType();
        if (outMimeType.equals(inMimeType)) {
            exportFile(inFile, outMimeType);
        } else {
            List<FilenameUriTuple> files = doConvertFile(inFile, format);
            for (FilenameUriTuple file: files) {
                exportFile(file, outMimeType);
            }
        }
    }

    private void setViewAndChildrenEnabled(View view, boolean enabled) {

        view.setEnabled(enabled);
        if (view instanceof ViewGroup viewGroup) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setViewAndChildrenEnabled(child, enabled);
            }
        }
    }

    private void setAllInteractiveElementsEnabled(final boolean enabled) {

        runOnUiThread(() -> {

            View view;

            view = findViewById(R.id.linearLayoutActiveContainer);
            if (view != null) {
                setViewAndChildrenEnabled(view, enabled);
            }

            view = findViewById(R.id.buttonStartConversion);
            if (view != null) {
                view.setEnabled(enabled);
            }

            view = findViewById(R.id.buttonStopConversion);
            if (view != null) {
                view.setEnabled(!enabled);
            }
        });
    }

    private void setProgressBarVisible(final int max, final int visibility) {

        runOnUiThread(() -> {

            ProgressBar view = findViewById(R.id.progressBarConversion);
            if (view != null) {
                view.setMax(max);
                view.setVisibility(visibility);
            }
        });
    }

    private void incrementProgressBar() {

        runOnUiThread(() -> {

            ProgressBar view = findViewById(R.id.progressBarConversion);
            if (view != null) {
                view.setProgress(view.getProgress() + 1);
            }
        });
    }

    private List<String> getErrorMessage
        (
            Throwable    exception,
            String       filename,
            OutputFormat format
        ) {

        List<String> errors = new ArrayList<>();

        if (exception instanceof OutOfMemoryError) {
            errors.add(getString(R.string.out_of_memory, filename, format));
        } else if (exception instanceof ExportException) {
            errors.add(getString(R.string.can_not_export, filename));
        } else if (exception instanceof DecodeException) {
            errors.add(getString(R.string.can_not_decode, filename));
        } else if (exception instanceof EncodeException) {
            List<Integer> pages = ((EncodeException) exception).getDirectories();
            if (pages == null || pages.size() == 0) {
                errors.add(getString(R.string.can_not_encode, filename, format));
            } else {
                for (int page: pages) {
                    errors.add(getString(R.string.can_not_encode_page, filename, format, page + 1));
                }
            }
        } else {
            errors.add(getString(R.string.unknown_error, filename));
            exception.printStackTrace();
        }

        return errors;
    }

    /**
     *
     */
    protected void onErrorDialogDismissed() {
        // do nothing
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setListView();
        setCheckBoxListeners();

        //TODO: save output formats selection between app runs
    }

    @Override
    protected void onPermissionGranted(boolean isGranted) {
    }

    /**
     *
     * @param view
     */
    public void onButtonClickAddFiles(View view) {

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");

        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.add_files)),
            REQUEST_CODE_PICK_FILE
        );
    }

    public void onButtonClickStartConversion(View view) {

        final int formatsNumber = outputFormats.size();
        if (formatsNumber == 0) {
            return;
        }

        stopConversion = false;

        new Thread(() -> {

            List<String> errors = new ArrayList<>();

            int progressMax = formatsNumber * files.size();
            setProgressBarVisible(progressMax, View.VISIBLE);
            setAllInteractiveElementsEnabled(false);

            int index = 0;
            while (index < files.size() && !stopConversion) {

                boolean isExceptionCaught = false;
                final FilenameUriTuple file = files.get(index);

                for (OutputFormat format: outputFormats) {
                    try {
                        // TODO: extract converting to separate util class
                        convertFile(file, format);
                        incrementProgressBar();
                    } catch (Throwable e) {
                        e.printStackTrace();
                        isExceptionCaught = true;

                        List<String> messages =
                            getErrorMessage(e, file.getFilename(), format);
                        errors.addAll(messages);
                    }

                    if (stopConversion) {
                        break;
                    }
                }

                if (isExceptionCaught) {
                    index++;
                }

                if (!isExceptionCaught && !stopConversion) {
                    removeTuple(file);
                }
            }

            hideStatus();
            setProgressBarVisible(0, View.INVISIBLE);
            setAllInteractiveElementsEnabled(true);

            displayDialogReceivedExceptions(errors);
            displayToastDone();
        }).start();
    }

    /**
     *
     * @param view
     */
    public void onButtonClickStopConversion(View view) {

        publishStatus(getString(R.string.stopping));
        stopConversion = true;
    }

    /**
     *
     * @param request
     * @param result
     * @param data
     */
    public void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);

        if(request == REQUEST_CODE_PICK_FILE && data != null) {

            ClipData clipData = data.getClipData();
            if(clipData != null) {
                for(int i = 0; i < clipData.getItemCount(); i++) {

                    Uri    uri = clipData.getItemAt(i).getUri();
                    String filename = getFileName(uri);

                    addTuple(new FilenameUriTuple(filename, uri));
                }
            } else {

                Uri uri = data.getData();
                if (uri != null) {

                    String fileName = getFileName(uri);
                    addTuple(new FilenameUriTuple(fileName, uri));
                }
            }
        }
    }
}