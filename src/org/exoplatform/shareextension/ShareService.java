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

import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.InputStreamEntity;
import org.exoplatform.model.ExoAccount;
import org.exoplatform.singleton.DocumentHelper;
import org.exoplatform.singleton.SocialServiceHelper;
import org.exoplatform.social.client.api.model.RestActivity;
import org.exoplatform.utils.ExoConnectionUtils;
import org.exoplatform.utils.ExoConstants;
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

  public static final String LOG_TAG       = "____eXo____UploadService____";

  public static final String CONTENT_URI   = "contentUri";

  public static final String EXO_ACCOUNT   = "eXoAccount";

  public static final String POST_MESSAGE  = "postMessage";

  public static final String POST_IN_SPACE = "postInSpace";

  private enum ShareResult {
    SUCCESS, ERROR_INCORRECT_FILE, ERROR_INCORRECT_CONTENT_URI, ERROR_INCORRECT_ACCOUNT, ERROR_CREATE_FOLDER, ERROR_UPLOAD_FAILED, ERROR_POST_FAILED
  }

  public ShareService() {
    super(LOG_TAG);
  }

  @Override
  protected void onHandleIntent(Intent intent) {
    String contentUri = intent.getStringExtra(CONTENT_URI);
    ExoAccount account = intent.getParcelableExtra(EXO_ACCOUNT);
    String postMessage = intent.getStringExtra(POST_MESSAGE);
    String destinationSpace = intent.getStringExtra(POST_IN_SPACE);

    if (contentUri == null || "".equals(contentUri))
      stopService(ShareResult.ERROR_INCORRECT_CONTENT_URI);

    if (account == null)
      stopService(ShareResult.ERROR_INCORRECT_ACCOUNT);

    if (destinationSpace == null) {
      // TODO upload file to the documents folder of the space
      // set the activity type as files:spaces
    } else {

    }

    // Create the directory where the mobile uploads are stored on the server
    String uploadUrl = DocumentHelper.getInstance().getRepositoryHomeUrl() + "/Public/Mobile";
    boolean createFolder = ExoDocumentUtils.createFolder(uploadUrl);
    if (!createFolder)
      stopService(ShareResult.ERROR_CREATE_FOLDER);

    DocumentInfo fileToUpload = ExoDocumentUtils.documentInfoFromUri(Uri.parse(contentUri), getBaseContext());
    if (fileToUpload == null)
      stopService(ShareResult.ERROR_INCORRECT_FILE);

    boolean uploadResult = uploadFile(fileToUpload, uploadUrl);
    if (uploadResult) {

    } else {
      stopService(ShareResult.ERROR_UPLOAD_FAILED);
    }

    if (postMessage == null || "".equals(postMessage))
      postMessage = account.userFullName + " has shared the document " + fileToUpload.documentName;

    Map<String, String> templateParams = templateParams(fileToUpload.documentName, uploadUrl, account.serverUrl);
    boolean postResult = postMessage(postMessage, templateParams);
    if (postResult) {
      stopService(ShareResult.SUCCESS);
    } else {
      stopService(ShareResult.ERROR_POST_FAILED);
    }

  }

  private boolean uploadFile(DocumentInfo fileToUpload, String uploadUrl) {
    String destinationUrl = (uploadUrl + "/" + fileToUpload.documentName).replaceAll(" ", "%20");
    Log.d(LOG_TAG, String.format("Uploading %s to %s", fileToUpload, destinationUrl));
    HttpPut upload = new HttpPut(destinationUrl);
    InputStreamEntity stream = new InputStreamEntity(fileToUpload.documentData, fileToUpload.documentSizeKb * 1024);
    stream.setContentType(fileToUpload.documentMimeType);
    upload.setEntity(stream);
    HttpResponse response = null;
    try {
      response = ExoConnectionUtils.httpClient.execute(upload);
      Log.d(LOG_TAG, String.format("Response %s : %s", response.getStatusLine().getStatusCode(), response.getStatusLine()
                                                                                                         .getReasonPhrase()));
      int status = response.getStatusLine().getStatusCode();
      return (status >= HttpStatus.SC_OK && status < HttpStatus.SC_MULTIPLE_CHOICES);
    } catch (Exception e) {
      Log.e(LOG_TAG, "Upload failed", e);
    }
    return false;
  }

  private Map<String, String> templateParams(String docName, String uploadUrl, String domainUrl) {
    String docUrl = uploadUrl + "/" + docName;
    Map<String, String> templateParams = new HashMap<String, String>();
    templateParams.put("WORKSPACE", ExoConstants.DOCUMENT_COLLABORATION);
    templateParams.put("REPOSITORY", DocumentHelper.getInstance().repository);
    String docLink = docUrl.substring(domainUrl.length());
    templateParams.put("DOCLINK", docLink);
    StringBuffer beginPath = new StringBuffer("/portal/rest/jcr/").append(DocumentHelper.getInstance().repository)
                                                                  .append("/")
                                                                  .append(ExoConstants.DOCUMENT_COLLABORATION)
                                                                  .append("/");
    String docPath = docLink.substring(beginPath.length());
    templateParams.put("DOCPATH", docPath);
    templateParams.put("DOCNAME", docName);
    return templateParams;
  }

  private boolean postMessage(String message, Map<String, String> templateParams) {
    RestActivity activity = new RestActivity();
    activity.setTitle(message);
    activity.setType(RestActivity.DOC_ACTIVITY_TYPE);
    templateParams.put("MESSAGE", message);
    activity.setTemplateParams(templateParams);
    try {
      return (SocialServiceHelper.getInstance().activityService.create(activity) != null);
    } catch (Exception e) {
      Log.e(LOG_TAG, "Post message failed", e);
    }
    return false;
  }

  private void stopService(ShareResult result) {
    // TODO notif to inform that upload has finished
    Log.d(LOG_TAG, String.format("Share result: %s", result));
    stopSelf();
  }

}
