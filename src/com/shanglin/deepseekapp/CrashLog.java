package com.shanglin.deepseekapp;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 崩溃黑匣子：把堆栈写到应用外部文件目录，便于用户反馈定位问题 */
public final class CrashLog {
    private CrashLog() { }

    public static void record(Context ctx, Throwable e) {
        try {
            File dir = new File(ctx.getExternalFilesDir(null), "crash");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "crash.log");
            PrintWriter pw = new PrintWriter(new FileWriter(f, true));
            pw.println("===== " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    .format(new Date()) + " =====");
            e.printStackTrace(pw);
            Throwable cause = e.getCause();
            while (cause != null) {
                pw.println("--- Caused by ---");
                cause.printStackTrace(pw);
                cause = cause.getCause();
            }
            pw.close();
        } catch (Throwable ignored) { }
    }
}
