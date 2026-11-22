# MetroWin Launcher: no custom rules needed. Compose and AndroidX ship their own consumer rules.

# Shizuku: the shell runner (data/ShizukuShell.kt) calls newProcess by name, through reflection, because the library keeps it private. Without this R8
# renames it and every command of the release build fails without a word.
-keepclassmembers class rikka.shizuku.Shizuku {
    *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}

# Mail (IMAP/SMTP): the only classes the library loads by name are the providers of META-INF/javamail.default.providers (Session) and the
# handlers of META-INF/mailcap (javax.activation); everything else is reached by calls and R8 follows them. (The SASL helpers that
# IMAPProtocol and SMTPTransport look for by name are not in this build of the library: they are tried and the failure is caught.) The POP3 providers are listed in the same file but the app never opens one.
-keep class com.sun.mail.imap.IMAPStore { <init>(...); }
-keep class com.sun.mail.imap.IMAPSSLStore { <init>(...); }
-keep class com.sun.mail.smtp.SMTPTransport { <init>(...); }
-keep class com.sun.mail.smtp.SMTPSSLTransport { <init>(...); }
-keep class com.sun.mail.handlers.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
-dontwarn org.apache.harmony.**
-dontwarn com.github.luben.zstd.**

# The user service of Shizuku (shizukuservice/ShellUserService.kt): Shizuku starts it in its own process by the name of the class, and the AIDL
# interface is called by its generated names, so none of them may be renamed or taken out.
-keep class com.metrowin.launcher.shizukuservice.ShellUserService { <init>(...); }
-keep class com.metrowin.launcher.shizukuservice.IShellService { *; }
-keep class com.metrowin.launcher.shizukuservice.IShellService$Stub { *; }
-keep class com.metrowin.launcher.shizukuservice.IShellService$Stub$Proxy { *; }
