package ru.akkordnik.app;

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
}
