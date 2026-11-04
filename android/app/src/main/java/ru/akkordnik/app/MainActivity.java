package ru.akkordnik.app;

import android.content.Intent;
import android.os.Bundle;
import androidx.core.view.WindowCompat;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.WebViewListener;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(ShareInPlugin.class);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        // Отступ под строку состояния передаём в CSS, когда страница загрузилась
        getBridge().addWebViewListener(new WebViewListener() {
            @Override
            public void onPageLoaded(WebView webView) {
                float d = getResources().getDisplayMetrics().density;
                int res = getResources().getIdentifier("status_bar_height", "dimen", "android");
                int top = res > 0 ? getResources().getDimensionPixelSize(res) : 0;
                if (top > 0) {
                    webView.evaluateJavascript("document.documentElement.style.setProperty('--sat','" + Math.round(top / d) + "px')", null);
                }
            }
        });
        handleShare(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleShare(intent);
    }

    private void handleShare(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (text == null || text.trim().isEmpty()) return;
        ShareInPlugin.deliver(text);
    }
}
