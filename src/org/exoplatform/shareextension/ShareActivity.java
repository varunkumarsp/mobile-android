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

import org.apache.http.HttpResponse;
import org.exoplatform.R;
import org.exoplatform.controller.social.ComposeMessageController;
import org.exoplatform.model.ExoAccount;
import org.exoplatform.singleton.DocumentHelper;
import org.exoplatform.utils.ExoConnectionUtils;

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

  public static final String       LOG_TAG           = "____eXo____Share_Extension____";

  private final String             COMPOSE_FRAGMENT  = "compose";

  private final String             ACCOUNTS_FRAGMENT = "accounts";

  private final String             SIGNIN_FRAGMENT   = "sign_in";

  private final String             SPACES_FRAGMENT   = "spaces";

  private ComposeFragment          composer;

  private ComposeMessageController controller;

  @Override
  protected void onCreate(Bundle bundle) {
    super.onCreate(bundle);

    setContentView(R.layout.share_extension_activity);

    Intent intent = getIntent();
    String action = intent.getAction();
    String type = intent.getType();
    String postMessage = "";
    Uri contentUri = null;
    if (Intent.ACTION_SEND.equals(action) && type != null) {
      if ("text/plain".equals(type)) {
        postMessage = intent.getStringExtra(Intent.EXTRA_TEXT);
      } else {
        contentUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
      }

      Log.d(LOG_TAG, String.format("Sharing file at uri %s", contentUri.toString()));

      composer = new ComposeFragment(postMessage, contentUri);
      getSupportFragmentManager().beginTransaction().add(R.id.fragment_panel, composer, COMPOSE_FRAGMENT).commit();
    } else {
      // TODO show error toast and finish the activity
      finish();
    }
  }

  public void onShareButtonClicked(View view) {
    ExoAccount acc = composer.getSelectedAccount();
    if (acc == null)
      return;

    new AsyncTask<String, Void, Integer>() {
      @Override
      protected Integer doInBackground(String... params) {
        String username = params[0];
        String password = params[1];
        String url = params[2] + "/rest/private/platform/info";
        try {
          Log.d(LOG_TAG, String.format("Started login request to %s ...", url));
          HttpResponse resp = ExoConnectionUtils.getPlatformResponse(username, password, url);
          ExoConnectionUtils.checkPLFVersion(resp, params[2], username);
          int result = ExoConnectionUtils.checkPlatformRespose(resp);
          return Integer.valueOf(result);
        } catch (IOException e) {
          e.printStackTrace();
        }
        return Integer.valueOf(ExoConnectionUtils.LOGIN_FAILED);
      }

      protected void onPostExecute(Integer result) {
        Log.d(LOG_TAG, String.format("Received login response %s", result));
        if (ExoConnectionUtils.LOGIN_SUCCESS == result.intValue()) {
          Log.d(LOG_TAG, "Start share service...");
          Intent share = new Intent(getBaseContext(), ShareService.class);
          share.putExtra(ShareService.CONTENT_URI, composer.getContentUri());
          share.putExtra(ShareService.CONTENT_NAME, composer.getContentName());
          share.putExtra(ShareService.EXO_ACCOUNT, composer.getSelectedAccount());
          share.putExtra(ShareService.UPLOAD_URL, DocumentHelper.getInstance().getRepositoryHomeUrl() + "/Public/Mobile");
          share.putExtra(ShareService.POST_MESSAGE, composer.getPostMessage());
          share.putExtra(ShareService.POST_IN_SPACE, composer.getSpaceName());
          startService(share);
          // TODO i18n
          Toast.makeText(getBaseContext(),
                         "The upload has started, check the status in the notification area.",
                         Toast.LENGTH_LONG).show();

          finish();
        } else {

        }
      };
    }.execute(acc.username, acc.password, acc.serverUrl);

  }
}
