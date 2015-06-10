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

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.net.URI;
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

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

/**
 * Created by The eXo Platform SAS
 * 
 * @author Philippe Aristote paristote@exoplatform.com
 * @since 3 Jun 2015
 */
public class ShareActivity extends FragmentActivity {

  /**
   * Direction of the animation to switch from one fragment to another
   */
  public static enum Anim {
    NO_ANIM, FROM_LEFT, FROM_RIGHT
  }

  public static final String LOG_TAG                  = "____eXo____Share_Extension____";

  public static final String DEFAULT_CONTENT_NAME     = "TEMP_FILE_TO_SHARE";

  private static final int   SELECT_SHARE_DESTINATION = 11;

  private ComposeFragment    composer;

  private ExoAccount         selectedAccount;

  private String             selectedSpace;

  private Uri                contentUri;

  private boolean            online;

  private ProgressBar        loadingIndicator;

  private Button             mainButton;

  private ImageView          onlineIndicator;

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    setContentView(R.layout.share_extension_activity);

    loadingIndicator = (ProgressBar) findViewById(R.id.share_progress_indicator);
    mainButton = (Button) findViewById(R.id.share_button);
    mainButton.setTag(R.attr.share_button_type_post);
    onlineIndicator = (ImageView) findViewById(R.id.share_online_status_icon);
    online = false;

    Intent intent = getIntent();
    String action = intent.getAction();
    String type = intent.getType();
    String postMessage = "";
    if (Intent.ACTION_SEND.equals(action) && type != null) {
      if ("text/plain".equals(type)) {
        // The share does not contain an attachment
        postMessage = intent.getStringExtra(Intent.EXTRA_TEXT);
        // TODO new DownloadTask().execute(postMessage);
      } else {
        // The share contains an attachement
        contentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
        Log.d(LOG_TAG, String.format("Sharing file at uri %s", contentUri.toString()));
      }

      init();

      // Create and display the composer, aka ComposeFragment
      composer = ComposeFragment.getFragment();
      composer.setPostMessage(postMessage);
      composer.setContentUri(contentUri);
      openFragment(composer, ComposeFragment.COMPOSE_FRAGMENT, Anim.NO_ANIM);
    } else {
      // We're not supposed to reach this activity by anything else than an
      // ACTION_SEND intent
      finish();
    }
  }

  private void init() {
    // Load the list of accounts
    ServerSettingHelper.getInstance().getServerInfoList(this);
    // Init the activity with the selected account
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
      // Try to login to setup the social services
      loginWithSelectedAccount();
    } else {

    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    // When we come back from the space selector activity
    // TODO make it another fragment
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

  /**
   * Util method to switch from one fragment to another, with an animation
   * 
   * @param toOpen The Fragment to open in this transaction
   * @param key the string key of the fragment
   * @param anim the type of animation
   */
  public void openFragment(Fragment toOpen, String key, Anim anim) {
    FragmentTransaction tr = getSupportFragmentManager().beginTransaction();
    switch (anim) {
    case FROM_LEFT:
      tr.setCustomAnimations(R.anim.fragment_enter_ltr, R.anim.fragment_exit_ltr);
      break;
    case FROM_RIGHT:
      tr.setCustomAnimations(R.anim.fragment_enter_rtl, R.anim.fragment_exit_rtl);
      break;
    default:
    case NO_ANIM:
      break;
    }
    tr.replace(R.id.share_extension_fragment, toOpen, key);
    tr.commit();
  }

  @Override
  public void onBackPressed() {
    // Intercept the back button taps to display previous state with animation
    // If we're on the composer, call super to finish the activity
    if (composer.isAdded()) {
      super.onBackPressed();
    } else if (AccountsFragment.getFragment().isAdded()) {
      // close the accounts fragment and reopen the composer fragment
      openFragment(composer, ComposeFragment.COMPOSE_FRAGMENT, Anim.FROM_LEFT);
    } else if (SignInFragment.getFragment().isAdded()) {
      // close the sign in fragment and reopen the accounts fragment
      openFragment(AccountsFragment.getFragment(), AccountsFragment.ACCOUNTS_FRAGMENT, Anim.FROM_LEFT);
    }
  }

  /*
   * GETTERS & SETTERS
   */

  public void setSelectedAccount(ExoAccount account) {
    selectedAccount = account;
  }

  public ExoAccount getSelectedAccount() {
    return selectedAccount;
  }

  public ProgressBar getLoadingIndicator() {
    return loadingIndicator;
  }

  public Button getMainButton() {
    return mainButton;
  }

  public void toggleMainButtonType(int type) {
    if (type == R.attr.share_button_type_signin) {
      // switch from post => signin
      mainButton.setText(R.string.SignInInformation);
      mainButton.setTag(R.attr.share_button_type_signin);
      mainButton.setVisibility(View.VISIBLE);
    } else if (type == R.attr.share_button_type_post) {
      // switch from signin => post
      mainButton.setText(R.string.StatusUpdate);
      mainButton.setTag(R.attr.share_button_type_post);
      mainButton.setVisibility(View.VISIBLE);
    }
  }

  /**
   * Switch the main button to the given enabled state. If the main button is
   * the post button, we also check that the account is online.
   * 
   * @param enabled
   */
  public void enableDisableMainButton(boolean enabled) {
    boolean currentState = mainButton.isEnabled();
    if (currentState != enabled) {
      int color;
      if (enabled) {
        color = getResources().getColor(android.R.color.white);
      } else {
        color = getResources().getColor(android.R.color.darker_gray);
      }
      mainButton.setTextColor(color);
      int buttonType = ((Integer) mainButton.getTag()).intValue();
      if (buttonType == R.attr.share_button_type_post) {
        mainButton.setEnabled(enabled && online);
      } else if (buttonType == R.attr.share_button_type_signin) {
        mainButton.setEnabled(enabled);
      }
    }
  }

  /*
   * CLICK LISTENERS
   */

  public void onMainButtonClicked(View view) {
    int buttonType = ((Integer) mainButton.getTag()).intValue();
    // Tap on the Post button
    if (buttonType == R.attr.share_button_type_post) {
      if (selectedAccount == null || !online)
        return;

      String postMessage = composer.getPostMessage();
      if (postMessage == null || "".equals(postMessage))
        return;

      Log.d(LOG_TAG, "Start share service...");
      Intent share = new Intent(getBaseContext(), ShareService.class);
      if (contentUri != null)
        share.putExtra(ShareService.CONTENT_URI, contentUri.toString());
      share.putExtra(ShareService.EXO_ACCOUNT, selectedAccount);
      share.putExtra(ShareService.POST_MESSAGE, postMessage);
      share.putExtra(ShareService.POST_IN_SPACE, selectedSpace);
      startService(share);
      // TODO i18n
      Toast.makeText(getBaseContext(), "The upload has started, check the status in the notification area.", Toast.LENGTH_LONG)
           .show();

      // Post is in progress, our work is done here
      finish();
    } else if (buttonType == R.attr.share_button_type_signin) {
      // Tap on the Sign In button
      selectedAccount.password = SignInFragment.getFragment().getPassword();
      openFragment(composer, ComposeFragment.COMPOSE_FRAGMENT, Anim.FROM_LEFT);
      loginWithSelectedAccount();
    }
  }

  private void hideSoftKeyboard() {
    InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    mgr.hideSoftInputFromWindow(composer.getEditText().getWindowToken(), 0);
  }

  public void onSelectAccount(View v) {
    // Called when the select account field is tapped
    hideSoftKeyboard();
    openFragment(AccountsFragment.getFragment(), AccountsFragment.ACCOUNTS_FRAGMENT, Anim.FROM_RIGHT);
  }

  public void onAccountSelected(ExoAccount account) {
    // Called when an account with password was selected.
    // If the selected account has no password, we open the SignInFragment first
    if (!account.equals(selectedAccount)) {
      selectedAccount = account;
      loginWithSelectedAccount();
    }
  }

  public void onSelectSpace(View v) {
    // Called when the select space field is tapped
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

    @Override
    protected void onPreExecute() {
      mainButton.setVisibility(View.INVISIBLE);
      loadingIndicator.setVisibility(View.VISIBLE);
      super.onPreExecute();
    }

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
          SocialServiceHelper.getInstance().identityService = clientServiceFactory.createIdentityService();
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
      if (ExoConnectionUtils.LOGIN_SUCCESS == result.intValue()) {
        online = true;
        onlineIndicator.setColorFilter(Color.GREEN);
      } else {
        selectedAccount.password = "";
        online = false;
        onlineIndicator.setColorFilter(Color.RED);
      }
      loadingIndicator.setVisibility(View.INVISIBLE);
      mainButton.setVisibility(View.VISIBLE);
      enableDisableMainButton(online && !"".equals(composer.getPostMessage()));
    }
  }

  // TODO support for text content that contains an URL to download a file
  // e.g. share from dropbox
  @SuppressWarnings("unused")
  private class DownloadTask extends AsyncTask<String, Void, String> {

    private String extractUriFromText(String text) {
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
          endOfLink = text.length() - startOfLink;
        return text.substring(startOfLink, endOfLink);
      } else {
        return null;
      }
    }

    private String getUriWithoutQueryString(URI uri) {
      StringBuffer buf = new StringBuffer(uri.getScheme()).append("://")
                                                          .append(uri.getHost())
                                                          .append(uri.getPort() != -1 ? ":" + uri.getPort() : "")
                                                          .append(uri.getRawPath() != null ? uri.getRawPath() : "");
      return buf.toString();
    }

    private String getFileName(String decodedPath) {
      if (decodedPath == null)
        return DEFAULT_CONTENT_NAME;
      if (decodedPath.endsWith("/"))
        decodedPath = decodedPath.substring(0, decodedPath.length() - 1);
      int beginNamePos = decodedPath.lastIndexOf('/');
      if (beginNamePos < 0)
        return DEFAULT_CONTENT_NAME;
      String name = decodedPath.substring(beginNamePos + 1);
      if (name == null || "".equals(name))
        return DEFAULT_CONTENT_NAME;
      else
        return name;
    }

    @Override
    protected String doInBackground(String... params) {
      String str = params[0];
      if (str != null) {
        try {
          URI uri = URI.create(extractUriFromText(str));
          String strUri = getUriWithoutQueryString(uri);
          Log.d(LOG_TAG, "Started download of " + strUri);
          BufferedInputStream in = new BufferedInputStream(new URL(strUri).openStream());
          String fileName = getFileName(uri.getPath());
          FileOutputStream out = openFileOutput(fileName, 0);
          byte[] buf = new byte[1024];
          int len;
          while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
          }
          out.close();
          in.close();
          contentUri = Uri.parse(new URI("file://" + getFileStreamPath(fileName).getAbsolutePath()).toString());
          // isLocalFile = true;
          Log.d(LOG_TAG, "Download successful: " + contentUri.toString());
        } catch (Exception e) {
          Log.e(LOG_TAG, "Error ", e);
        }
      }
      return null;
    }
  }
}
