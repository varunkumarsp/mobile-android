/*
 * Copyright (C) 2003-2014 eXo Platform SAS.
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
package org.exoplatform.controller.social;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.exoplatform.R;
import org.exoplatform.singleton.DocumentHelper;
import org.exoplatform.singleton.SocialServiceHelper;
import org.exoplatform.social.client.api.SocialClientLibException;
import org.exoplatform.social.client.api.model.RestActivity;
import org.exoplatform.social.client.api.model.RestIdentity;
import org.exoplatform.ui.social.AllUpdatesFragment;
import org.exoplatform.ui.social.MyStatusFragment;
import org.exoplatform.utils.ExoConnectionUtils;
import org.exoplatform.utils.ExoConstants;
import org.exoplatform.utils.ExoDocumentUtils;
import org.exoplatform.utils.PhotoUtils;
import org.exoplatform.utils.WebdavMethod;
import org.exoplatform.widget.PostWaitingDialog;
import org.exoplatform.widget.WarningDialog;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.AsyncTask;

public class PostStatusTask extends AsyncTask<Void, Void, Integer> {
    private PostWaitingDialog        _progressDialog;

    private Context                  mContext;

    private String                   sdcard_temp_dir;

    private String                   composeMessage;

    private String                   sendingData;

    private String                   okString;

    private String                   errorString;

    private String                   warningTitle;

    private String                   uploadUrl;

    private ComposeMessageController messageController;

    public PostStatusTask(Context context,
                          String dir,
                          String content,
                          ComposeMessageController controller,
                          PostWaitingDialog dialog) {
        mContext = context;
        messageController = controller;
        sdcard_temp_dir = dir;
        composeMessage = content;
        _progressDialog = dialog;
        changeLanguage();
    }

    @Override
    public void onPreExecute() {
        _progressDialog = new PostWaitingDialog(mContext, messageController, null, sendingData);
        _progressDialog.show();
    }

    @Override
    public Integer doInBackground(Void... params) {

        try {
            RestActivity activityImpl = new RestActivity();
            if (sdcard_temp_dir != null) {
                if (createFolder()) {

                    File file = new File(sdcard_temp_dir);
                    String imageDir = uploadUrl + "/" + file.getName();
                    if (file != null) {

                        File tempFile = PhotoUtils.reziseFileImage(file);
                        if (tempFile != null) {
                            ExoDocumentUtils.putFileToServerFromLocal(imageDir,
                                                                      tempFile,
                                                                      ExoConstants.IMAGE_TYPE);
                        }
                        Map<String, String> templateParams = new HashMap<String, String>();
                        activityImpl.setType(RestActivity.DOC_ACTIVITY_TYPE);
                        StringBuilder pathExtension = new StringBuilder("jcr");
                        pathExtension.append("/");
                        pathExtension.append(DocumentHelper.getInstance().repository);
                        pathExtension.append("/");
                        pathExtension.append(ExoConstants.DOCUMENT_COLLABORATION);
                        // String pathExtension =
                        // "jcr/repository/collaboration";
                        int indexOfDocLink = imageDir.indexOf(pathExtension.toString());
                        StringBuffer docBuffer = new StringBuffer("/portal/rest/");
                        docBuffer.append(imageDir.substring(indexOfDocLink));
                        String docPath = imageDir.substring(indexOfDocLink
                                + pathExtension.toString().length());
                        templateParams.put("DOCPATH", docPath);
                        templateParams.put("MESSAGE", composeMessage);
                        templateParams.put("DOCLINK", docBuffer.toString());
                        templateParams.put("WORKSPACE", ExoConstants.DOCUMENT_COLLABORATION);
                        templateParams.put("REPOSITORY", DocumentHelper.getInstance().repository);
                        templateParams.put("DOCNAME", file.getName());
                        activityImpl.setTemplateParams(templateParams);

                    }
                }
            } else if (messageController.getPostDestination() != null) {
                String spaceName = messageController.getPostDestination();
                RestIdentity spaceIdentity = SocialServiceHelper.getInstance().identityService.getIdentity("space",
                                                                                                           spaceName);
                if (spaceIdentity != null) {
                    activityImpl.setType(RestActivity.SPACE_ACTIVITY_TYPE);
                    activityImpl.setIdentityId(spaceIdentity.getId());
                }
            }
            activityImpl.setTitle(composeMessage);
            SocialServiceHelper.getInstance().activityService.create(activityImpl);
            return 1;
        } catch (SocialClientLibException e) {
            return 0;
        } catch (RuntimeException e) {
            return -2;
        }

    }

    @Override
    public void onPostExecute(Integer result) {
        if (result == 1) {
            ((Activity) mContext).finish();
            if (AllUpdatesFragment.instance != null)
                AllUpdatesFragment.instance.onPrepareLoad(ExoConstants.NUMBER_OF_ACTIVITY, true, 0);
            if (MyStatusFragment.instance != null)
                MyStatusFragment.instance.onPrepareLoad(ExoConstants.NUMBER_OF_ACTIVITY, true, 0);

        } else {
            new WarningDialog(mContext, warningTitle, errorString, okString).show();
        }
        _progressDialog.dismiss();

    }

    private boolean createFolder() {

        uploadUrl = DocumentHelper.getInstance().getRepositoryHomeUrl() + "/Public/Mobile";

        HttpResponse response;
        try {
            WebdavMethod copy = new WebdavMethod("HEAD", uploadUrl);

            response = ExoConnectionUtils.httpClient.execute(copy);
            int status = response.getStatusLine().getStatusCode();
            if (status >= HttpStatus.SC_OK && status < HttpStatus.SC_MULTIPLE_CHOICES) {
                return true;

            } else {
                copy = new WebdavMethod("MKCOL", uploadUrl);
                response = ExoConnectionUtils.httpClient.execute(copy);
                status = response.getStatusLine().getStatusCode();

                if (status >= HttpStatus.SC_OK && status < HttpStatus.SC_MULTIPLE_CHOICES) {
                    return true;
                } else
                    return false;
            }

        } catch (IOException e) {
            return false;
        }
    }

    private void changeLanguage() {
        Resources resource = mContext.getResources();
        sendingData = resource.getString(R.string.SendingData);
        okString = resource.getString(R.string.OK);
        errorString = resource.getString(R.string.PostError);
        warningTitle = resource.getString(R.string.Warning);
    }

}
