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

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.exoplatform.model.ExoAccount;
import org.exoplatform.singleton.DocumentHelper;
import org.exoplatform.singleton.SocialServiceHelper;
import org.exoplatform.social.client.api.SocialClientLibException;
import org.exoplatform.social.client.api.model.RestActivity;
import org.exoplatform.social.client.api.model.RestIdentity;
import org.exoplatform.utils.ExoConnectionUtils;
import org.exoplatform.utils.ExoConstants;
import org.exoplatform.utils.ExoDocumentUtils;
import org.exoplatform.utils.ExoDocumentUtils.DocumentInfo;
import org.exoplatform.utils.TitleExtractor;

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
    SUCCESS, ERROR_INCORRECT_CONTENT_URI, ERROR_INCORRECT_ACCOUNT, ERROR_CREATE_FOLDER, ERROR_UPLOAD_FAILED, ERROR_POST_FAILED
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

    if (account == null)
      stopService(ShareResult.ERROR_INCORRECT_ACCOUNT);

    Map<String, String> templateParams = null;
    String link = null;
    if (contentUri != null) {

      if (destinationSpace == null) {
      } else {
        // TODO upload file to the documents folder of the space
        // set the activity type as files:spaces
      }

      // Create the directory where the mobile uploads are stored on the server
      String uploadUrl = DocumentHelper.getInstance().getRepositoryHomeUrl() + "/Public/Mobile";
      boolean createFolder = ExoDocumentUtils.createFolder(uploadUrl);
      if (!createFolder) {
        stopService(ShareResult.ERROR_CREATE_FOLDER);
        return;
      }

      DocumentInfo fileToUpload = ExoDocumentUtils.documentInfoFromUri(Uri.parse(contentUri), getBaseContext());
      if (fileToUpload == null) {
        stopService(ShareResult.ERROR_INCORRECT_CONTENT_URI);
        return;
      }

      boolean uploadResult = uploadFile(fileToUpload, uploadUrl);
      templateParams = docParams(fileToUpload.documentName, uploadUrl, account.serverUrl);
      if (uploadResult) {

      } else {
        // stopService(ShareResult.ERROR_UPLOAD_FAILED);
        // TODO fix 500 server error when upload is successful
      }
    } else {
      link = extractLinkFromText(postMessage);
      if (link != null)
        postMessage = postMessage.replace(link, String.format(Locale.US, "<a href=\"%s\">%s</a>", link, link));
      templateParams = linkParams(link, postMessage);
    }

    boolean postResult = false;
    if (contentUri != null)
      postResult = postDocActivity(postMessage, templateParams, destinationSpace);
    else if (link != null)
      postResult = postLinkActivity(postMessage, templateParams, destinationSpace);
    else {
      postResult = postTextActivity(postMessage, destinationSpace);
    }

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
    BufferedHttpEntity buffEntity = null;
    try {
      buffEntity = new BufferedHttpEntity(stream);
    } catch (IOException e) {
      Log.e(LOG_TAG, "Cannot use a BufferedHttpEntity", e);
    }
    if (buffEntity != null)
      upload.setEntity(buffEntity);
    else
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

  private Map<String, String> docParams(String docName, String uploadUrl, String domainUrl) {
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

  private Map<String, String> linkParams(String link, String message) {
    if (link == null)
      return null;
    Map<String, String> templateParams = new HashMap<String, String>();
    templateParams.put("comment", message);
    templateParams.put("link", link);
    templateParams.put("description", "");
    templateParams.put("image", "");
    try {
      templateParams.put("title", TitleExtractor.getPageTitle(link));
    } catch (IOException e) {
      Log.e(LOG_TAG, "Cannot retrieve link title", e);
      templateParams.put("title", link);
    }
    return templateParams;
  }

  private String extractLinkFromText(String text) {
    int posHttp = text.indexOf("http://");
    int posHttps = text.indexOf("https://");
    int startOfLink = -1;
    if (posHttps > -1)
      startOfLink = posHttps;
    else if (posHttp > -1)
      startOfLink = posHttp;
    if (startOfLink > -1) {
      int endOfLink = text.indexOf(' ', startOfLink);
      if (endOfLink == -1)
        return text.substring(startOfLink);
      else
        return text.substring(startOfLink, endOfLink);
    } else {
      return null;
    }
  }

  private String retrieveSpaceId(String spaceName) {
    try {
      RestIdentity spaceIdentity = SocialServiceHelper.getInstance().identityService.getIdentity("space", spaceName);
      return spaceIdentity.getId();
    } catch (SocialClientLibException e) {
      Log.e(LOG_TAG, "Could not retrieve space ID of " + spaceName, e);
    }
    return null;
  }

  private boolean postDocActivity(String message, Map<String, String> templateParams, String inSpace) {
    RestActivity activity = new RestActivity();
    activity.setTitle(message);
    if (inSpace == null || "".equals(inSpace)) {
      activity.setType(RestActivity.DOC_ACTIVITY_TYPE);
    } else {
      activity.setType("files:spaces");
      String spaceId = retrieveSpaceId(inSpace);
      activity.setIdentityId(spaceId);
    }
    templateParams.put("MESSAGE", message);
    activity.setTemplateParams(templateParams);

    return postActivity(activity);
  }

  private boolean postLinkActivity(String message, Map<String, String> templateParams, String inSpace) {
    RestActivity activity = new RestActivity();
    activity.setTitle(message);
    activity.setTemplateParams(templateParams);
    if (inSpace == null || "".equals(inSpace)) {
      activity.setType(RestActivity.LINK_ACTIVITY_TYPE);
    } else {
      // TODO set type link activity in space
      activity.setType(RestActivity.LINK_ACTIVITY_TYPE);
      String spaceId = retrieveSpaceId(inSpace);
      activity.setIdentityId(spaceId);
    }

    return postActivity(activity);
  }

  private boolean postTextActivity(String message, String inSpace) {
    RestActivity activity = new RestActivity();
    activity.setTitle(message);
    if (inSpace == null || "".equals(inSpace))
      activity.setType(RestActivity.DEFAULT_ACTIVITY_TYPE);
    else
      activity.setType(RestActivity.SPACE_ACTIVITY_TYPE);

    return postActivity(activity);
  }

  private boolean postActivity(RestActivity activity) {
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
  }

}
