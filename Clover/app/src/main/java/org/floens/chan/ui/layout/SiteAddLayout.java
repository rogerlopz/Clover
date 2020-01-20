package org.floens.chan.ui.layout;

import android.app.Dialog;
import android.content.Context;
import com.google.android.material.textfield.TextInputLayout;
import android.util.AttributeSet;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.floens.chan.R;
import org.floens.chan.core.presenter.SitesSetupPresenter;

public class SiteAddLayout extends LinearLayout implements SitesSetupPresenter.AddCallback {
    private EditText url;
    private TextInputLayout urlContainer;

    private Dialog dialog;
    private SitesSetupPresenter presenter;

    public SiteAddLayout(Context context) {
        this(context, null);
    }

    public SiteAddLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SiteAddLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        urlContainer = findViewById(R.id.url_container);
        url = findViewById(R.id.url);
        url.setHint(R.string.setup_sites_url_hint);
    }

    public void setDialog(Dialog dialog) {
        this.dialog = dialog;
    }

    public void setPresenter(SitesSetupPresenter presenter) {
        this.presenter = presenter;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        presenter.bindAddDialog(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        presenter.unbindAddDialog();
    }

    public void onPositiveClicked() {
        presenter.onAddClicked(url.getText().toString());
    }

    @Override
    public void showAddError(String error) {
        urlContainer.setError(error);
    }

    @Override
    public void dismissDialog() {
        dialog.dismiss();
    }
}
