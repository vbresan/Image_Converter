package biz.binarysolutions.imageconverter;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import biz.binarysolutions.imageconverter.data.FilenameUriTuple;
import biz.binarysolutions.imageconverter.data.OutputFormat;
import biz.binarysolutions.imageconverter.exceptions.ConvertException;
import biz.binarysolutions.imageconverter.exceptions.CopyException;
import biz.binarysolutions.imageconverter.exceptions.DecodeException;
import biz.binarysolutions.imageconverter.exceptions.EncodeException;
import biz.binarysolutions.imageconverter.image.Converter;
import biz.binarysolutions.imageconverter.image.TiffUtil;
import biz.binarysolutions.imageconverter.util.FileUtil;
import biz.binarysolutions.imageconverter.listeners.OutputFormatListener;
import biz.binarysolutions.imageconverter.util.PermissionActivity;

import static android.widget.AdapterView.OnItemClickListener;

/**
 * TODO: add resize functionality?
 * TODO: copy metadata (exif, etc.)?
 *
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
                    result = cursor.getString(cursor.getColumnIndex(
                        OpenableColumns.DISPLAY_NAME));
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

    /**
     *
     * @param tuple
     */
    private void removeTuple(final FilenameUriTuple tuple) {

        files.remove(tuple);

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                adapter.remove(tuple);
                refreshInputFilesNumber();
            }
        });
    }

    /**
     *
     * @param index
     */
    private void displayDialogConfirmFileRemove(final int index) {

        final FilenameUriTuple tuple = files.get(index);

        new AlertDialog.Builder(this)
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage(getString(R.string.confirm_remove, tuple.getFilename()))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.yes,
                new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeTuple(tuple);
                    }
                })
            .create()
            .show();
    }

    /**
     *
     */
    private void displayDialogErrorCreatingOutputDirectory() {

        new AlertDialog.Builder(this)
            .setTitle(android.R.string.dialog_alert_title)
            .setMessage(getString(R.string.error_creating_output_directory))
            .setNegativeButton(android.R.string.ok, null)
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

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                LayoutInflater inflater = LayoutInflater.from(MainActivity.this);
                View container = inflater.inflate(R.layout.dialog_errors, null);

                TextView textView = container.findViewById(R.id.textViewErrors);
                if (textView != null) {
                    textView.setText(sb);
                }

                OnClickListener listener = new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onErrorDialogDismissed();
                    }
                };

                new AlertDialog.Builder(MainActivity.this)
                    .setTitle(android.R.string.dialog_alert_title)
                    .setView(container)
                    .setNegativeButton(android.R.string.ok, listener)
                    .create()
                    .show();
            }
        });
    }

    /**
     *
     */
    private void displayToastDone() {

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                Toast.makeText(
                    MainActivity.this,
                    getString(R.string.done),
                    Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    /**
     *
     */
    private void setListView() {

        ListView listView = findViewById(R.id.listViewInputFiles);
        if (listView == null) {
            return;
        }

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> p, View v, int pos, long id) {
                displayDialogConfirmFileRemove(pos);
            }
        });
    }

    /**
     *
     */
    private void setOutputFolder() {

        TextView textView = findViewById(R.id.textViewOutputFolder);
        if (textView == null) {
            return;
        }

        textView.setText(getOutputFolder().getAbsolutePath());
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

    /**
     *
     * @param uri
     * @return
     */
    private String getMimeType(Uri uri) {

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

    /**
     *
     * @param status
     */
    private void publishStatus(final String status) {

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                TextView textView = findViewById(R.id.textViewStatus);
                if (textView != null) {
                    textView.setText(status);
                    textView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     *
     */
    private void hideStatus() {

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                TextView textView = findViewById(R.id.textViewStatus);
                if (textView != null) {
                    textView.setText("");
                    textView.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    /**
     * TODO: do not overwrite existing file
     *
     * @param file
     */
    private void copyFile(FilenameUriTuple file) throws CopyException {

        publishStatus(getString(R.string.copying, file));

        ContentResolver resolver = getContentResolver();
        File copy = new File(getOutputFolder(), file.getFilename());

        try {
            InputStream is = resolver.openInputStream(file.getUri());
            if (is != null) {
                FileUtil.copy(is, copy);
            } else {
                throw new Exception();
            }
        } catch (Exception e) {
            throw new CopyException(e);
        }
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

    /**
     * TODO: update progress bar
     *
     * @param file
     * @param format
     * @return
     */
    protected void convertUsingNonNativeAPI(File file, OutputFormat format)
        throws ConvertException {

        if (! TiffUtil.isTiffFile(file)) {
            throw new DecodeException();
        }

        TiffUtil.convertFromTIF(file, format, getOutputFolder());
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

    /**
     *
     * @param filename
     * @param is
     * @return
     * @throws DecodeException
     */
    private File getCachedFile(String filename, InputStream is)
        throws DecodeException {

        try {
            return FileUtil.getCachedFile(is, getCacheDir(), filename);
        } catch (IOException e) {
            throw new DecodeException(e);
        }
    }

    /**
     * TODO: do not overwrite existing file
     *
     * @param file
     * @param format
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void doConvertFile(FilenameUriTuple file, OutputFormat format)
        throws ConvertException {

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
            File   outFile     = new File(getOutputFolder(), outFilename);

            try {
                Converter.encodeBitmap(bitmap, format, outFile);
            } catch (IOException e) {
                outFile.delete();
                throw new EncodeException(e);
            } catch (OutOfMemoryError e) {
                outFile.delete();
                throw e;
            }
        } else {
            is = getInputStream(file.getUri());
            File cachedFile = getCachedFile(inFilename, is);

            try {
                convertUsingNonNativeAPI(cachedFile, format);
            } finally {
                cachedFile.delete();
            }
        }
    }

    /**
     *
     * @param file
     * @param format
     */
    private void convertFile(FilenameUriTuple file, OutputFormat format)
        throws ConvertException {

        String mimeType = getMimeType(file.getUri());
        if (format.isMimeType(mimeType)) {
            copyFile(file);
        } else {
            doConvertFile(file, format);
        }
    }

    /**
     *
     */
    private boolean createOutputDirectory() throws SecurityException {

        File root = Environment.getExternalStorageDirectory();
        if (root == null) {
            return false;
        }

        File directory = new File(root, getString(R.string.directory_name));
        if (directory.exists() && directory.isDirectory()) {
            return true;
        }

        return directory.mkdir();
    }

    /**
     *
     * @return
     */
    private File getOutputFolder() {

        File root = Environment.getExternalStorageDirectory();
        return new File(root, getString(R.string.directory_name));
    }

    /**
     *
     * @param view
     * @param enabled
     */
    private void setViewAndChildrenEnabled(View view, boolean enabled) {

        view.setEnabled(enabled);
        if (view instanceof ViewGroup) {

            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setViewAndChildrenEnabled(child, enabled);
            }
        }
    }

    /**
     *
     * @param enabled
     */
    private void setAllInteractiveElementsEnabled(final boolean enabled) {

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

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
            }
        });
    }

    /**
     *
     * @param max
     * @param visiblity
     */
    private void setProgressBarVisible(final int max, final int visiblity) {

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                ProgressBar view = findViewById(R.id.progressBarConversion);
                if (view != null) {
                    view.setMax(max);
                    view.setVisibility(visiblity);
                }
            }
        });
    }

    /**
     *
     */
    private void incrementProgressBar() {

        new Handler(getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                ProgressBar view = findViewById(R.id.progressBarConversion);
                if (view != null) {
                    view.setProgress(view.getProgress() + 1);
                }
            }
        });
    }

    /**
     *
     * @param exception
     * @param filename
     * @param format
     * @return
     */
    private List<String> getErrorMessage
        (
            Throwable    exception,
            String       filename,
            OutputFormat format
        ) {

        List<String> errors = new ArrayList<>();

        if (exception instanceof OutOfMemoryError) {
            errors.add(getString(R.string.out_of_memory, filename, format));
        } else if (exception instanceof CopyException) {
            errors.add(getString(R.string.can_not_copy, filename));
        } else if (exception instanceof DecodeException) {
            errors.add(getString(R.string.can_not_decode, filename));
        } else { // exception instanceof EncodeException
            List<Integer> pages = ((EncodeException) exception).getDirectories();
            if (pages == null || pages.size() == 0) {
                errors.add(getString(R.string.can_not_encode, filename, format));
            } else {
                for (int page: pages) {
                    errors.add(getString(R.string.can_not_encode_page, filename, format, page + 1));
                }
            }
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
        setOutputFolder();
        setCheckBoxListeners();

        //TODO: save output formats selection between app runs
    }

    @Override
    protected void onPermissionGranted(boolean isGranted) {

        try {
            if (!createOutputDirectory()) {
                displayDialogErrorCreatingOutputDirectory();
            }
        } catch (SecurityException e) {
            displayDialogErrorCreatingOutputDirectory();
        }
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

    /**
     *
     * @param view
     */
    public void onButtonClickStartConversion(View view) {

        final int formatsNumber = outputFormats.size();
        if (formatsNumber == 0) {
            return;
        }

        stopConversion = false;

        new Thread(new Runnable() {
            @Override
            public void run() {

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
            }
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