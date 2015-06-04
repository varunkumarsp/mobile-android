/*
 * Copyright (C) 2003-2015 eXo Platform SAS.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.exoplatform.shareextension;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.InputStreamEntity;
import org.exoplatform.model.ExoAccount;
import org.exoplatform.utils.ExoConnectionUtils;
import org.exoplatform.utils.ExoDocumentUtils;
import org.exoplatform.utils.ExoDocumentUtils.DocumentInfo;

import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

/**
 * Created by The eXo Platform SAS.<br/>
 * <br/>
 * A service that uploads a document on the given Platform account, then
 * publishes an activity on the account's activity stream or a space.<br/>
 * <br/>
 * Usage:
 * 
 * <pre>
 * Intent share = new Intent(context, ShareService.class);
 * 
 * share.putExtra(ShareService.CONTENT_URI, "content://" or "file://");
 * share.putExtra(ShareService.EXO_ACCOUNT, eXoAccount);
 * 
 * <pre>
 * 
 * @author Philippe Aristote paristote@exoplatform.com
 * @since Jun 4, 2015
 */
public class ShareService extends IntentService {

  public static final String LOG_TAG          = "____eXo____UploadService____";

  public static final String CONTENT_URI      = "contentUri";

  public static final String CONTENT_NAME     = "contentName";

  public static final String UPLOAD_URL       = "uploadUrl";

  public static final String EXO_ACCOUNT      = "eXoAccount";

  public static final String POST_MESSAGE     = "postMessage";

  public static final String POST_IN_SPACE    = "postInSpace";

  private String             destinationSpace = null;

  public ShareService() {
    super(LOG_TAG);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    String contentUri = intent.getStringExtra(CONTENT_URI);
    String contentName = intent.getStringExtra(CONTENT_NAME);
    ExoAccount account = intent.getParcelableExtra(EXO_ACCOUNT);
    String uploadUrl = intent.getStringExtra(UPLOAD_URL);
    String postMessage = intent.getStringExtra(POST_MESSAGE);
    destinationSpace = intent.getStringExtra(POST_IN_SPACE);

    if (!isOK(contentUri, account, uploadUrl))
      stopService();

    if (contentName == null || "".equals(contentName))
      contentName = "default";

    if (postMessage == null || "".equals(postMessage))
      postMessage = account.userFullName + " has shared the document " + contentName;

    // Create the directory where the mobile uploads are stored on the server
    if (ExoDocumentUtils.createFolder(uploadUrl)) {
      DocumentInfo fileToUpload = ExoDocumentUtils.documentInfoFromUri(Uri.parse(contentUri), getBaseContext());

      if (fileToUpload == null)
        stopService();

      String destinationUrl = (uploadUrl + "/" + fileToUpload.documentName).replaceAll(" ", "%20");
      Log.d(LOG_TAG, String.format("Uploading %s to %s", fileToUpload, destinationUrl));
      HttpPut upload = new HttpPut(destinationUrl);
      InputStreamEntity stream = new InputStreamEntity(fileToUpload.documentData, fileToUpload.documentSizeKb * 1024);
      stream.setContentType(fileToUpload.documentMimeType);
      upload.setEntity(stream);
      HttpResponse response = null;
      try {
        response = ExoConnectionUtils.httpClient.execute(upload);
      } catch (Exception e) {
        stopService();
      }
      Log.d(LOG_TAG, String.format("Response %s : %s", response.getStatusLine().getStatusCode(), response.getStatusLine()
                                                                                                         .getReasonPhrase()));

      int status = response.getStatusLine().getStatusCode();
      if (status >= HttpStatus.SC_OK && status < HttpStatus.SC_MULTIPLE_CHOICES) {

      }
    }
  }

  private boolean isOK(String contentUri, ExoAccount account, String uploadUrl) {
    boolean notOk = false;
    notOk = (contentUri == null || "".equals(contentUri) || account == null || uploadUrl == null || "".equals(uploadUrl));
    return !notOk;
  }

  private void stopService() {
    // TODO notif to inform that upload has failed
    stopSelf();
  }

}
