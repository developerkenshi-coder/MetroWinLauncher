# MetroWin tile SDK

Puts a title, a line of text and a number on the tile of your app in MetroWin Launcher. Apache-2.0.

```kotlin
// Off the main thread (it is a call to another process).
when (val result = MetroTile.publish(context, "Inbox", "3 new messages", count = 3)) {
    MetroTileResult.Accepted -> Unit
    MetroTileResult.NotInstalled -> Unit            // the launcher is not on the phone
    is MetroTileResult.Refused -> Log.i("tile", result.reason)   // "turned off by the user", "too fast"
}
MetroTile.clear(context)
```

The library's manifest adds `PUBLISH_TILE` (a normal permission: granted at install, no prompt) and the `<queries>` entry that Android 11 and later need to
see the launcher's provider. The tile is always the one of the calling app, and shows only if the user pinned your app on Start.
The contract (names, limits, answers, version) is in `docs/progetti/tile-sdk.md`; the example app is `sample-tile/`.
