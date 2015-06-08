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

import java.net.URL;
import java.util.List;

import org.apache.http.HttpResponse;
import org.exoplatform.R;
import org.exoplatform.model.ExoAccount;
import org.exoplatform.singleton.AccountSetting;
import org.exoplatform.singleton.ServerSettingHelper;
import org.exoplatform.singleton.SocialServiceHelper;
import org.exoplatform.social.client.api.ClientServiceFactory;
import org.exoplatform.social.client.api.SocialClientContext;
import org.exoplatform.social.client.api.service.VersionService;
import org.exoplatform.social.client.core.ClientServiceFactoryHelper;
import org.exoplatform.ui.social.SpaceSelectorActivity;
import org.exoplatform.utils.ExoConnectionUtils;
import org.exoplatform.utils.ExoConstants;

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

/**
 * Created by The eXo Platform SAS
 * 
 * @author Philippe Aristote paristote@exoplatform.com
 * @since 3 Jun 2015
 */
public class ShareActivity extends FragmentActivity {

  public static final String LOG_TAG                  = "____eXo____Share_Extension____";

  private static final int   SELECT_SHARE_DESTINATION = 11;

  private final String       COMPOSE_FRAGMENT         = "compose";

  private final String       ACCOUNTS_FRAGMENT        = "accounts";

  private final String       SIGNIN_FRAGMENT          = "sign_in";

  private ComposeFragment    composer;

  private ExoAccount         selectedAccount;

  private String             selectedSpace;

  private Uri                contentUri;

  private boolean            online;

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    setContentView(R.layout.share_extension_activity);

    online = false;

    Intent intent = getIntent();
    String action = intent.getAction();
    String type = intent.getType();
    String postMessage = "";
    if (Intent.ACTION_SEND.equals(action) && type != null) {
      if ("text/plain".equals(type)) {
        postMessage = intent.getStringExtra(Intent.EXTRA_TEXT);
      } else {
        contentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
      }
      Log.d(LOG_TAG, String.format("Sharing file at uri %s", contentUri.toString()));

      init();

      composer = new ComposeFragment(postMessage, contentUri);
      getSupportFragmentManager().beginTransaction().add(R.id.fragment_panel, composer, COMPOSE_FRAGMENT).commit();
    } else {
      // TODO show error toast and finish the activity
      finish();
    }
  }

  private void init() {
    ServerSettingHelper.getInstance().getServerInfoList(this);
    selectedAccount = AccountSetting.getInstance().getCurrentAccount();
    if (selectedAccount == null) {
      List<ExoAccount> serverList = ServerSettingHelper.getInstance().getServerInfoList(this);
      int selectedServerIdx = Integer.parseInt(getSharedPreferences(ExoConstants.EXO_PREFERENCE, 0).getString(ExoConstants.EXO_PRF_DOMAIN_INDEX,
                                                                                                              "-1"));
      AccountSetting.getInstance().setDomainIndex(String.valueOf(selectedServerIdx));
      AccountSetting.getInstance()
                    .setCurrentAccount((selectedServerIdx == -1 || selectedServerIdx >= serverList.size()) ? null
                                                                                                          : serverList.get(selectedServerIdx));
      selectedAccount = AccountSetting.getInstance().getCurrentAccount();
    }
    if (selectedAccount != null && selectedAccount.password != null && !"".equals(selectedAccount.password)) {
      loginWithSelectedAccount();
    }
  }

  public void onShareButtonClicked(View view) {
    if (selectedAccount == null || !online)
      return;

    Log.d(LOG_TAG, "Start share service...");
    Intent share = new Intent(getBaseContext(), ShareService.class);
    share.putExtra(ShareService.CONTENT_URI, contentUri.toString());
    share.putExtra(ShareService.EXO_ACCOUNT, selectedAccount);
    share.putExtra(ShareService.POST_MESSAGE, composer.getPostMessage());
    share.putExtra(ShareService.POST_IN_SPACE, selectedSpace);
    startService(share);
    // TODO i18n
    Toast.makeText(getBaseContext(), "The upload has started, check the status in the notification area.", Toast.LENGTH_LONG)
         .show();

    finish();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == SELECT_SHARE_DESTINATION) {
      if (resultCode == RESULT_OK) {
        selectedSpace = data.getStringExtra(SpaceSelectorActivity.SELECTED_DESTINATION);
        if (selectedSpace == null) {
          composer.setSpaceSelectorLabel(getResources().getString(R.string.Public));
        } else {
          String spaceDisplayName = data.getStringExtra(SpaceSelectorActivity.SELECTED_SPACE_DISPLAY_NAME);
          composer.setSpaceSelectorLabel(spaceDisplayName);
        }
      }
    }
  }

  /*
   * GETTERS
   */

  public ExoAccount getSelectedAccount() {
    return selectedAccount;
  }

  /*
   * CLICK LISTENERS
   */

  public void onSelectAccount(View v) {

  }

  public void onSelectSpace(View v) {
    if (online) {
      Intent spaceSelector = new Intent(this, SpaceSelectorActivity.class);
      startActivityForResult(spaceSelector, SELECT_SHARE_DESTINATION);
    }
  }

  /*
   * TASKS
   */

  public void loginWithSelectedAccount() {
    new LoginTask().execute(selectedAccount);
  }

  private class LoginTask extends AsyncTask<ExoAccount, Void, Integer> {

    @SuppressWarnings("unchecked")
    @Override
    protected Integer doInBackground(ExoAccount... accounts) {
      String username = accounts[0].username;
      String password = accounts[0].password;
      String url = accounts[0].serverUrl + "/rest/private/platform/info";
      try {
        Log.d(LOG_TAG, String.format("Started login request to %s ...", url));
        HttpResponse resp = ExoConnectionUtils.getPlatformResponse(username, password, url);
        ExoConnectionUtils.checkPLFVersion(resp, accounts[0].serverUrl, username);
        int result = ExoConnectionUtils.checkPlatformRespose(resp);
        if (ExoConnectionUtils.LOGIN_SUCCESS == result) {
          URL u = new URL(accounts[0].serverUrl);
          SocialClientContext.setProtocol(u.getProtocol());
          SocialClientContext.setHost(u.getHost());
          SocialClientContext.setPort(u.getPort());
          SocialClientContext.setPortalContainerName(ExoConstants.ACTIVITY_PORTAL_CONTAINER);
          SocialClientContext.setRestContextName(ExoConstants.ACTIVITY_REST_CONTEXT);
          SocialClientContext.setUsername(username);
          SocialClientContext.setPassword(password);
          ClientServiceFactory clientServiceFactory = ClientServiceFactoryHelper.getClientServiceFactory();
          VersionService versionService = clientServiceFactory.createVersionService();
          SocialClientContext.setRestVersion(versionService.getLatest());
          SocialServiceHelper.getInstance().activityService = clientServiceFactory.createActivityService();
          SocialServiceHelper.getInstance().spaceService = clientServiceFactory.createSpaceService();
        }
        return Integer.valueOf(result);
      } catch (Exception e) {
        Log.e(LOG_TAG, "Login task failed", e);
      }
      return Integer.valueOf(ExoConnectionUtils.LOGIN_FAILED);
    }

    @Override
    protected void onPostExecute(Integer result) {
      Log.d(LOG_TAG, String.format("Received login response %s", result));
      String status = "Status: ";
      if (ExoConnectionUtils.LOGIN_SUCCESS == result.intValue()) {
        online = true;
        status += "online";
      } else {
        online = false;
        status += "offline";
      }
      Toast.makeText(getBaseContext(), status, Toast.LENGTH_SHORT).show();
    }

  }
}
