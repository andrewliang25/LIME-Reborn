package io.github.chipppppppppp.lime.hooks;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.chipppppppppp.lime.LimeOptions;

/**
 * TEMPORARY diagnostic hook used to re-derive obfuscated hook targets when bumping LINE support.
 * It is NOT a shipping feature — register it in {@code Main.hooks}, build a debug APK, install on a
 * device running the TARGET LINE version + LSPosed, then watch the log while exercising the app:
 *
 *   adb logcat | grep LIME-DIAG        (or read the LSPosed module log for jp.naver.line.android)
 *
 * What each section gives you (maps to Constants.HookTarget entries still to re-derive):
 *
 *  - Thrift tracer (org.apache.thrift.o#b / #a — the send/recv base, already re-derived):
 *      * Confirms REQUEST_HOOK / RESPONSE_HOOK are live and prints every request/response name
 *        + payload, so you can see which op carries each feature's data.
 *      * When a payload/name matches a known SIGNAL it also dumps the caller stack trace, which
 *        reveals the obfuscated Runnable / coroutine class that drives that feature:
 *          "sendChatChecked"          -> MARK_AS_READ_HOOK   (a Runnable#run in the chain)
 *          "NOTIFIED_READ_MESSAGE"    -> NOTIFICATION_READ_HOOK (a coroutine invokeSuspend)
 *          "hidden:true/false" / "SetChatHiddenStatusRequest" -> ARCHIVE_HOOK (coroutine invokeSuspend)
 *          "TO_BE_SENT_SILENTLY"      -> MUTE_MESSAGE_HOOK send path
 *
 *  - WebView tracer: prints the concrete class registered via setWebViewClient. Open the in-app
 *    browser (tap a link) and the printed class is WEBVIEW_CLIENT_HOOK.className; its onPageFinished
 *    is the method.
 *
 *  - Mute-enum tracer: hooks the classes that consume the send-mode enum i38.f (NONE /
 *    TO_BE_SENT_SILENTLY) and dumps members + call sites. Send a normal message, then long-press
 *    send -> "Mute message", and compare the logged enum values / call chain to pick MUTE_MESSAGE_HOOK.
 *
 *  USER_AGENT_HOOK is not covered here (it is the niche "android_secondary" spoof: a (Context)->String
 *  method returning the app-identity like "ANDROID\t<ver>"). Find it by searching the decompiled APK
 *  for that identity builder, or hook the X-Line-Application header construction.
 *
 * REMOVE this file and its Main.hooks entry before shipping.
 */
public class DiagnosticLogger implements IHook {
    private static final String TAG = "LIME-DIAG";
    private static final int MAX_PAYLOAD = 800;

    // The obfuscated Thrift TServiceClient base (sendBase=b, receiveBase=a). Re-derived for 26.11.0.
    private static final String THRIFT_BASE = "org.apache.thrift.o";

    // Payload/name substrings that should trigger a caller stack-trace dump.
    private static final String[] SIGNALS = {
            "sendChatChecked",
            "NOTIFIED_READ_MESSAGE",
            "NOTIFIED_DESTROY_MESSAGE",
            "hidden:true",
            "hidden:false",
            "SetChatHiddenStatusRequest",
            "TO_BE_SENT_SILENTLY",
    };

    // Classes observed (statically) to consume the mute-mode enum i38.f in LINE 26.11.0.
    private static final String[] MUTE_ENUM_USERS = { "tw0.c$d", "tw0.c$c", "d98.e1" };
    private static final String MUTE_ENUM = "i38.f";

    @Override
    public void hook(LimeOptions limeOptions, XC_LoadPackage.LoadPackageParam lpparam) {
        log("==================================================");
        log("DiagnosticLogger active — re-derivation build. Remove before release.");
        log("==================================================");
        hookThrift(lpparam);
        hookWebViewClient();
        hookMuteEnumUsers(lpparam);
    }

    // (1) Thrift request/response tracer + signal-triggered stack dumps.
    private void hookThrift(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> base = lpparam.classLoader.loadClass(THRIFT_BASE);
            XposedBridge.hookAllMethods(base, "b", tracer("THRIFT-REQ"));
            XposedBridge.hookAllMethods(base, "a", tracer("THRIFT-RESP"));
            log("thrift tracer installed on " + THRIFT_BASE + " #b/#a");
        } catch (Throwable t) {
            log("thrift tracer FAILED (re-check REQUEST/RESPONSE_HOOK class): " + t);
        }
    }

    private XC_MethodHook tracer(final String kind) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    String name = (param.args != null && param.args.length > 0) ? String.valueOf(param.args[0]) : "?";
                    String payload = (param.args != null && param.args.length > 1) ? String.valueOf(param.args[1]) : "";
                    log("[" + kind + "] name=" + name + " payload=" + truncate(payload));
                    for (String sig : SIGNALS) {
                        if (name.contains(sig) || payload.contains(sig)) {
                            dumpStack("[" + kind + "] SIGNAL '" + sig + "' — caller chain (look for obfuscated Runnable/invokeSuspend):");
                            break;
                        }
                    }
                } catch (Throwable t) {
                    log("tracer error: " + t);
                }
            }
        };
    }

    // (2) WebViewClient finder — the printed class's onPageFinished is WEBVIEW_CLIENT_HOOK.
    private void hookWebViewClient() {
        try {
            XposedHelpers.findAndHookMethod(WebView.class, "setWebViewClient", WebViewClient.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Object client = param.args[0];
                            if (client != null) {
                                log("[WEBVIEW] setWebViewClient -> " + client.getClass().getName()
                                        + "  (open the in-app browser; this class + onPageFinished = WEBVIEW_CLIENT_HOOK)");
                            }
                        }
                    });
            log("webview tracer installed on WebView#setWebViewClient");
        } catch (Throwable t) {
            log("webview tracer FAILED: " + t);
        }
    }

    // (3) Mute-enum tracer — dumps members of the candidate classes and logs the enum value at each call.
    private void hookMuteEnumUsers(XC_LoadPackage.LoadPackageParam lpparam) {
        for (String cn : MUTE_ENUM_USERS) {
            try {
                Class<?> c = lpparam.classLoader.loadClass(cn);
                dumpClassMembers(c);
                XposedBridge.hookAllConstructors(c, muteTracer(cn + ".<init>"));
                for (Method m : c.getDeclaredMethods()) {
                    if (usesMuteEnum(m)) {
                        XposedBridge.hookMethod(m, muteTracer(cn + "#" + m.getName()));
                    }
                }
            } catch (Throwable t) {
                log("mute tracer skip " + cn + ": " + t);
            }
        }
    }

    private boolean usesMuteEnum(Method m) {
        for (Class<?> p : m.getParameterTypes()) {
            if (p.getName().equals(MUTE_ENUM)) return true;
        }
        return m.getReturnType().getName().equals(MUTE_ENUM);
    }

    private XC_MethodHook muteTracer(final String where) {
        return new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    StringBuilder sb = new StringBuilder("[MUTE] ").append(where).append(" args=");
                    if (param.args != null) {
                        for (Object a : param.args) {
                            sb.append(a == null ? "null" : (a.getClass().getName() + "(" + a + ")")).append(", ");
                        }
                    }
                    log(sb.toString());
                    dumpStack("[MUTE] caller chain:");
                } catch (Throwable t) {
                    log("mute tracer error: " + t);
                }
            }
        };
    }

    private void dumpClassMembers(Class<?> c) {
        log("[MEMBERS] " + c.getName());
        for (Method m : c.getDeclaredMethods()) {
            StringBuilder sb = new StringBuilder("    ").append(m.getReturnType().getSimpleName())
                    .append(' ').append(m.getName()).append('(');
            Class<?>[] ps = m.getParameterTypes();
            for (int i = 0; i < ps.length; i++) {
                sb.append(ps[i].getName());
                if (i < ps.length - 1) sb.append(", ");
            }
            log(sb.append(')').toString());
        }
    }

    private void dumpStack(String header) {
        log(header);
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        // skip the getStackTrace/dumpStack frames; print the meaningful LINE frames
        for (int i = 3; i < st.length && i < 28; i++) {
            log("    at " + st[i]);
        }
    }

    private static String truncate(String s) {
        if (s == null) return "null";
        s = s.replace('\n', ' ');
        return s.length() > MAX_PAYLOAD ? s.substring(0, MAX_PAYLOAD) + "…(" + s.length() + ")" : s;
    }

    private static void log(String msg) {
        XposedBridge.log(TAG + " | " + msg);
    }
}
