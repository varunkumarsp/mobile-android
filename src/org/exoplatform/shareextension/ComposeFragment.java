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
import org.exoplatform.singleton.ServerSettingHelper;
import org.exoplatform.utils.ExoDocumentUtils;
import org.exoplatform.utils.ExoDocumentUtils.DocumentInfo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Created by The eXo Platform SAS
 * 
 * @author Philippe Aristote paristote@exoplatform.com
 * @since Jun 3, 2015
 */
public class ComposeFragment extends Fragment {

  private static ComposeFragment instance;

  public static final String     COMPOSE_FRAGMENT = "compose_fragment";

  private EditText               etPostMessage;

  private String                 postMessage      = "";

  private TextView               tvAccount, tvSpace;

  private ImageView              imgThumb;

  private ScrollView             scroller;

  private Uri                    contentUri;

  /**
   * Enables the post button if the composer text field contains a message
   */
  private TextWatcher            postValidator    = new TextWatcher() {
                                                    @Override
                                                    public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                                                      enableDisableMainButton();
                                                    }

                                                    @Override
                                                    public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
                                                    }

                                                    @Override
                                                    public void afterTextChanged(Editable arg0) {
                                                    }
                                                  };

  private ComposeFragment() {
  }

  public static ComposeFragment getFragment() {
    if (instance == null) {
      instance = new ComposeFragment();
    }
    return instance;
  }

  private void init() {
    // Show a > caret on the account selector if 2 or more accounts exist
    boolean manyAccounts = ServerSettingHelper.getInstance().twoOrMoreAccountsExist(getActivity());
    if (manyAccounts) {
      tvAccount.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.icon_chevron_right_grey, 0);
    } else {
      tvAccount.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
    }
    if (contentUri != null) {
      // Retrieve some information about the attachment
      DocumentInfo info = ExoDocumentUtils.documentInfoFromUri(contentUri, getActivity());
      if (info == null) {
        // TODO i18n
        Toast.makeText(getActivity(), "Cannot access the document to share.", Toast.LENGTH_LONG).show();
        getActivity().finish();
      }

      if (info.documentSizeKb > 10000) {
        // TODO i18n
        Toast.makeText(getActivity(), "File too big (more than 10MB)", Toast.LENGTH_LONG).show();
        getActivity().finish();
      }
      // Create a thumbnail of the attachment, works only for images
      Bitmap thumbnail = getThumbnail(info.documentData);
      if (thumbnail != null) {
        imgThumb.setImageBitmap(thumbnail);
      }
    }
  }

  public void setTouchListener() {
    // Open the soft keyboard and give focus to the edit text field when the
    // scroll view is tapped
    scroller.setOnTouchListener(new View.OnTouchListener() {
      @Override
      public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
          etPostMessage.requestFocus();
          InputMethodManager mgr = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
          mgr.showSoftInput(etPostMessage, InputMethodManager.SHOW_IMPLICIT);
        }
        return false;
      }
    });
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
    etPostMessage.addTextChangedListener(postValidator);
    etPostMessage.setText(postMessage);
    tvAccount = (TextView) layout.findViewById(R.id.share_account);
    tvSpace = (TextView) layout.findViewById(R.id.share_space);
    imgThumb = (ImageView) layout.findViewById(R.id.share_attachment_thumbnail);
    scroller = (ScrollView) layout.findViewById(R.id.share_scroll_wrapper);
    init();
    return layout;
  }

  @Override
  public void onResume() {
    setTouchListener();
    getShareActivity().toggleMainButtonType(R.attr.share_button_type_post);
    ExoAccount selectedAccount = getShareActivity().getSelectedAccount();
    if (selectedAccount != null)
      tvAccount.setText(selectedAccount.accountName + " (" + selectedAccount.username + ")");
    super.onResume();
  }

  /*
   * GETTERS & SETTERS
   */

  public EditText getEditText() {
    return etPostMessage;
  }

  public void setPostMessage(String text) {
    postMessage = text == null ? "" : text;
    if (etPostMessage != null)
      etPostMessage.setText(postMessage);
  }

  public void setContentUri(Uri uri) {
    contentUri = uri;
  }

  public ShareActivity getShareActivity() {
    if (getActivity() instanceof ShareActivity) {
      return (ShareActivity) getActivity();
    } else {
      throw new RuntimeException("This fragment is only valid in the activity org.exoplatform.shareextension.ShareActivity");
    }
  }

  public String getPostMessage() {
    return etPostMessage != null ? etPostMessage.getText().toString() : "";
  }

  public void setSpaceSelectorLabel(String label) {
    tvSpace.setText(label);
  }

  private void enableDisableMainButton() {
    boolean postEmpty = "".equals(etPostMessage.getText().toString());
    getShareActivity().enableDisableMainButton(!postEmpty);
  }

}
