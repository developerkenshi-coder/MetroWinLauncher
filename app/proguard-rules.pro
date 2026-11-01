# MetroWin Launcher: no custom rules needed. Compose and AndroidX ship their own consumer rules.

# Mail (IMAP/SMTP): the library finds its protocols and content handlers by name.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**
-dontwarn javax.naming.**
-dontwarn org.apache.harmony.**
