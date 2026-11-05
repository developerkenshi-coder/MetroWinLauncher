# MetroWin Launcher: no custom rules needed. Compose and AndroidX ship their own consumer rules.

# Mail (IMAP/SMTP): the library finds its protocols and content handlers by name.
# IMAP and SMTP only: the app speaks nothing else, so the POP3 classes (19) can go.
-keep class com.sun.mail.imap.** { *; }
-keep class com.sun.mail.smtp.** { *; }
-keep class com.sun.mail.iap.** { *; }
-keep class com.sun.mail.auth.** { *; }
-keep class com.sun.mail.handlers.** { *; }
-keep class com.sun.mail.util.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
-dontwarn org.apache.harmony.**
