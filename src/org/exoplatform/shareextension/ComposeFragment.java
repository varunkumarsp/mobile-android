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

import java.io.InputStream;

import org.exoplatform.R;
import org.exoplatform.model.ExoAccount;
import org.exoplatform.singleton.AccountSetting;
import org.exoplatform.singleton.ServerSettingHelper;
import org.exoplatform.utils.ExoDocumentUtils;
import org.exoplatform.utils.ExoDocumentUtils.DocumentInfo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by The eXo Platform SAS Author : Philippe Aristote
 * paristote@exoplatform.com Jun 3, 2015
 */
public class ComposeFragment extends Fragment {

  private EditText   etPostMessage;

  private TextView   tvAccount, tvSpace;

  private ImageView  imgThumb;

  private String     postMessage;

  private ExoAccount selectedAccount;

  private Uri        contentUri;

  private String     contentName;

  // if null, the post is public
  private String     spaceName;

  public ComposeFragment(String text, Uri content) {
    postMessage = text == null ? "" : text;
    contentUri = content;
    selectedAccount = AccountSetting.getInstance().getCurrentAccount();
  }

  private void init() {
    boolean manyAccounts = ServerSettingHelper.getInstance().twoOrMoreAccountsExist(getActivity());
    if (manyAccounts) {
      tvAccount.setCompoundDrawablesWithIntrinsicBounds(0, 0, android.R.drawable.ic_media_play, 0);
    }
    if (contentUri != null) {
      DocumentInfo info = ExoDocumentUtils.documentInfoFromUri(contentUri, getActivity());
      if (info == null) {
        // TODO i18n
        Toast.makeText(getActivity(), "Cannot access the document to share.", Toast.LENGTH_SHORT).show();
        getActivity().finish();
      }

      if (info.documentSizeKb > 10000) {
        // TODO i18n
        Toast.makeText(getActivity(), "File too big (more than 10MB)", Toast.LENGTH_SHORT).show();
        getActivity().finish();
      }
      contentName = info.documentName;
      Bitmap thumbnail = getThumbnail(info.documentData);
      if (thumbnail != null) {
        imgThumb.setImageBitmap(thumbnail);
      }
    }
  }

  private Bitmap getThumbnail(InputStream bitmapStream) {
    BitmapFactory.Options opts = new BitmapFactory.Options();
    opts.inSampleSize = 4;
    opts.inPreferredConfig = Bitmap.Config.RGB_565;
    Bitmap thumbnail = BitmapFactory.decodeStream(bitmapStream, null, opts);
    return thumbnail;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View layout = inflater.inflate(R.layout.share_extension_compose_fragment, container, false);
    etPostMessage = (EditText) layout.findViewById(R.id.share_post_message);
    tvAccount = (TextView) layout.findViewById(R.id.share_account);
    tvSpace = (TextView) layout.findViewById(R.id.share_space);
    imgThumb = (ImageView) layout.findViewById(R.id.share_attachment_thumbnail);
    init();
    return layout;
  }

  @Override
  public void onResume() {
    etPostMessage.setText(postMessage);
    if (selectedAccount != null)
      tvAccount.setText(selectedAccount.accountName + " (" + selectedAccount.username + ")");
    super.onResume();
  }

  /*
   * GETTERS
   */

  public ExoAccount getSelectedAccount() {
    return selectedAccount;
  }

  public String getPostMessage() {
    return etPostMessage.getText().toString();
  }

  public String getContentUri() {
    return contentUri.toString();
  }

  public String getContentName() {
    return contentName;
  }

  public String getSpaceName() {
    return spaceName;
  }
}
