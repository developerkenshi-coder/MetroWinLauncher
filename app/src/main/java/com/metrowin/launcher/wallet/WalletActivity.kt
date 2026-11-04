package com.metrowin.launcher.wallet

import androidx.compose.runtime.Composable
import com.metrowin.launcher.ui.mini.MiniActivity

/** The wallet of the launcher: loyalty cards and tickets as barcodes and QR codes, kept encrypted, with a backup to keep and to restore. */
class WalletActivity : MiniActivity() {
    @Composable
    override fun Content() = WalletScreen()
}
