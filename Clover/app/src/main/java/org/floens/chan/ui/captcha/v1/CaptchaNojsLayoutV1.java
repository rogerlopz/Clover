/*
 * Clover - 4chan browser https://github.com/Floens/Clover/
 * Copyright (C) 2014  Floens
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.floens.chan.ui.captcha.v1;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.floens.chan.core.site.SiteAuthentication;
import org.floens.chan.core.site.Site;
import org.floens.chan.ui.captcha.AuthenticationLayoutCallback;
import org.floens.chan.ui.captcha.AuthenticationLayoutInterface;
import org.floens.chan.utils.AndroidUtils;
import org.floens.chan.utils.Logger;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * It directly loads the captcha2 fallback url into a webview, and on each requests it executes
 * some javascript that will tell the callback if the token is there.
 */
public class CaptchaNojsLayoutV1 extends WebView implements AuthenticationLayoutInterface {
    private static final String TAG = "CaptchaNojsLayout";

    private AuthenticationLayoutCallback callback;
    private String baseUrl;
    private String siteKey;

    private OkHttpClient okHttpClient = new OkHttpClient();

    private String webviewUserAgent;

    public CaptchaNojsLayoutV1(Context context) {
        super(context);
    }

    public CaptchaNojsLayoutV1(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CaptchaNojsLayoutV1(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    public void initialize(Site site, AuthenticationLayoutCallback callback) {
        this.callback = callback;

        SiteAuthentication authentication = site.actions().postAuthenticate();

        this.siteKey = authentication.siteKey;
        this.baseUrl = authentication.baseUrl;

        requestDisallowInterceptTouchEvent(true);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        webviewUserAgent = settings.getUserAgentString();

        setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(@NonNull ConsoleMessage consoleMessage) {
                Logger.i(TAG, consoleMessage.lineNumber() + ":" + consoleMessage.message()
                        + " " + consoleMessage.sourceId());
                return true;
            }
        });

        setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                // Fails if there is no token yet, which is ok.
                final String setResponseJavascript = "CaptchaCallback.onCaptchaEntered(" +
                        "document.querySelector('.fbc-verification-token textarea').value);";
                view.loadUrl("javascript:" + setResponseJavascript);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                String host = Uri.parse(url).getHost();
                if (host == null) {
                    return false;
                }

                if (host.equals(Uri.parse(CaptchaNojsLayoutV1.this.baseUrl).getHost())) {
                    return false;
                } else {
                    AndroidUtils.openLink(url);
                    return true;
                }
            }
        });
        setBackgroundColor(0x00000000);

        addJavascriptInterface(new CaptchaInterface(this), "CaptchaCallback");
    }

    public void reset() {
        hardReset();
    }

    @Override
    public boolean requireResetAfterComplete() {
        return true;
    }

    @Override
    public void hardReset() {
        loadRecaptchaAndSetWebViewData();
    }

    @Override
    public void onDestroy() {
    }

    private void loadRecaptchaAndSetWebViewData() {
        final String recaptchaUrl = "https://www.google.com/recaptcha/api/fallback?k=" + siteKey;

        Request request = new Request.Builder()
                .url(recaptchaUrl)
                .header("User-Agent", webviewUserAgent)
                .header("Referer", baseUrl)
                .build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                ResponseBody body = response.body();
                if (body == null) throw new IOException();
                String responseHtml = body.string();

                post(() -> {
                    loadDataWithBaseURL(recaptchaUrl,
                            responseHtml, "text/html", "UTF-8", null);
                });
            }
        });
    }

    private void onCaptchaEntered(String response) {
        if (TextUtils.isEmpty(response)) {
            reset();
        } else {
            callback.onAuthenticationComplete(this, null, response);
        }
    }

    public static class CaptchaInterface {
        private final CaptchaNojsLayoutV1 layout;

        public CaptchaInterface(CaptchaNojsLayoutV1 layout) {
            this.layout = layout;
        }

        @JavascriptInterface
        public void onCaptchaEntered(final String response) {
            AndroidUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    layout.onCaptchaEntered(response);
                }
            });
        }
    }
}
