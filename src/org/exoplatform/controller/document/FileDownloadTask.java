/*
 * Copyright (C) 2003-2012 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.controller.document;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.exoplatform.R;
import org.exoplatform.utils.ExoConnectionUtils;
import org.exoplatform.utils.ExoConstants;
import org.exoplatform.utils.ExoDocumentUtils;
import org.exoplatform.utils.image.FileCache;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;

/**
 * Created by The eXo Platform SAS
 * Author : eXoPlatform
 *          exo@exoplatform.com
 * Nov 16, 2012  
 */
public class FileDownloadTask extends AsyncTask<String, String, Integer> {



  private static final int       RESULT_OK                = 0;
  private static final int       RESULT_ERROR             = 1;
  private static final int       RESULT_CANCEL            = 2;
  


  private int view_type = TYPE_MAIL_FILE;
  private File file;
  private FileCache fileCache;
  private Context                mContext;
  private String                 fileType;
  //private String                 filePath;
  private String                 fileName;
  private String subject;
  private String message;
  private String intentTitle;
  private String downLoadingFile;
  private DownloadProgressDialog mProgressDialog;

  public static final int        DIALOG_DOWNLOAD_PROGRESS = 0;
  public static final int TYPE_VIEW_FILE = 0;
  public static final int TYPE_MAIL_FILE = 1;
  public static final int       MAX_FILE_SIZE            = 10*1048576;// 10 MB

  private void changeLanguage() {
    Resources resource = mContext.getResources();
    downLoadingFile = resource.getString(R.string.DownloadingFile);
    subject = resource.getString(R.string.EmailSubject) ;
    message = resource.getString(R.string.EmailMessageAtach) ;
    intentTitle = resource.getString(R.string.IntentTitle);
  }

  public FileDownloadTask(Context context, String fType, String fPath, String fName, int view_type) {
    mContext = context;
    changeLanguage();
    this.view_type = view_type;
    fileCache = new FileCache(context, ExoConstants.DOCUMENT_FILE_CACHE);
    fileType = fType;
    //filePath = fPath;
    fileName = fName;
  }

  private Dialog onCreateDialog(int id) {
    switch (id) {
    case DIALOG_DOWNLOAD_PROGRESS: // we set this to 0
      mProgressDialog = new DownloadProgressDialog(mContext);
      mProgressDialog.setMessage(downLoadingFile);
      mProgressDialog.setIndeterminate(false);
      mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
      mProgressDialog.setCancelable(true);
      mProgressDialog.show();
      return mProgressDialog;
    default:
      return null;
    }
  }

  @Override
  protected void onPreExecute() {
    mProgressDialog = (DownloadProgressDialog) onCreateDialog(DIALOG_DOWNLOAD_PROGRESS);
    mProgressDialog.show();

  }

  @Override
  protected Integer doInBackground(String... params) {

    /*
     * If file exists return file else download from url
     */
    file = fileCache.getFileFromName(fileName);
    if (file.exists()) {
      return RESULT_OK;
    }

    String url = params[0].replaceAll(" ", "%20");
    HttpParams httpParameters = new BasicHttpParams();
    HttpConnectionParams.setConnectionTimeout(httpParameters, 10000);
    HttpConnectionParams.setSoTimeout(httpParameters, 10000);
    HttpConnectionParams.setTcpNoDelay(httpParameters, true);
    DefaultHttpClient httpClient = new DefaultHttpClient(httpParameters);
    httpClient.setCookieStore(ExoConnectionUtils.cookiesStore);

    try {

      HttpGet getRequest = new HttpGet(url);
      HttpResponse response = httpClient.execute(getRequest);
      HttpEntity entity = response.getEntity();
      if (entity != null) {

        // lenghtOfFile is used for calculating download progress
        long lenghtOfFile = entity.getContentLength();
        /*
         * Compare the file size and free sdcard memory
         */
        if (!ExoDocumentUtils.isEnoughMemory((int) lenghtOfFile)) {
          return RESULT_CANCEL;
        }
        mProgressDialog.setMax((int) lenghtOfFile);
        InputStream is = entity.getContent();
        OutputStream os = new FileOutputStream(file);

        // here's the downloading progress
        byte[] buffer = new byte[1024];
        int len = 0;
        long total = 0;

        while ((len = is.read(buffer)) > 0) {
          total += len; // total = total + len
          publishProgress("" + total);
          os.write(buffer, 0, len);
        }

        os.close();
      }
      return RESULT_OK;
    } catch (IOException e) {
      if (file != null) {
        file.delete();
      }
      return RESULT_ERROR;
    } finally {
      httpClient.getConnectionManager().shutdown();
    }

  }

  /*
   * If cancelled, delete the downloading file
   */
  @Override
  protected void onCancelled() {
    if (file.exists()) {
      file.delete();
    }
    mProgressDialog.dismiss();
    super.onCancelled();
  }

  @Override
  protected void onProgressUpdate(String... values) {
    mProgressDialog.setProgress(Integer.parseInt(values[0]));
  }

  @Override
  protected void onPostExecute(Integer result) {
    mProgressDialog.dismiss();
    if(view_type == TYPE_MAIL_FILE) {
    Uri uri = Uri.fromFile(file);
    Intent i = new Intent(Intent.ACTION_SEND);
    i.putExtra(Intent.EXTRA_SUBJECT, subject + fileName);
    i.putExtra(Intent.EXTRA_TEXT, message);
    i.putExtra(Intent.EXTRA_STREAM, uri);
    i.setType(fileType);
    mContext.startActivity(Intent.createChooser(i,intentTitle));
    } else if(view_type == TYPE_VIEW_FILE) {
      //TODO implement view file there to optimize code !
    }
  }
  
  private static class DownloadProgressDialog extends ProgressDialog {

    public DownloadProgressDialog(Context context) {
      super(context);
    }

    /*
     * Override onBackPressed() method to call onCancelLoad() to cancel
     * downloading file task and delete the downloading file
     */

    @Override
    public void onBackPressed() {
      ExoDocumentUtils.onCancelLoad();
      super.onBackPressed();
    }
  }
  
  

}