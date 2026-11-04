package ru.akkordnik.app;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/** Передаёт в веб-часть текст/ссылку, которыми поделились с приложением. */
@CapacitorPlugin(name = "ShareIn")
public class ShareInPlugin extends Plugin {
    private static String pending = null;
    private static ShareInPlugin instance = null;

    @Override
    public void load() { instance = this; }

    static void deliver(String text) {
        if (instance != null && instance.hasListeners("share")) {
            JSObject data = new JSObject();
            data.put("text", text);
            instance.notifyListeners("share", data);
        } else {
            pending = text;
        }
    }

    @PluginMethod
    public void getPending(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("text", pending);
        pending = null;
        call.resolve(ret);
    }

    /** Высота системных панелей (в CSS-пикселях) для отступов под строкой состояния. */
    @PluginMethod
    public void insets(PluginCall call) {
        getActivity().runOnUiThread(() -> {
            JSObject r = new JSObject();
            WindowInsetsCompat w = ViewCompat.getRootWindowInsets(getActivity().getWindow().getDecorView());
            float d = getContext().getResources().getDisplayMetrics().density;
            int top = 0, bottom = 0;
            if (w != null) {
                Insets i = w.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                top = i.top; bottom = i.bottom;
            }
            int res = getContext().getResources().getIdentifier("status_bar_height", "dimen", "android");
            if (res > 0) top = Math.max(top, getContext().getResources().getDimensionPixelSize(res));
            r.put("top", top / d);
            r.put("bottom", bottom / d);
            call.resolve(r);
        });
    }
}
