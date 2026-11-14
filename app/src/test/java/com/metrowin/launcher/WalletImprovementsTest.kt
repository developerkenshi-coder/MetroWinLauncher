package com.metrowin.launcher

import com.metrowin.launcher.notes.NoteCrypt
import com.metrowin.launcher.wallet.Card
import com.metrowin.launcher.wallet.CardKind
import com.metrowin.launcher.wallet.CodeFormat
import com.metrowin.launcher.wallet.ExpiryState
import com.metrowin.launcher.wallet.Imported
import com.metrowin.launcher.wallet.SortMode
import com.metrowin.launcher.wallet.Wallet
import com.metrowin.launcher.wallet.WalletBackup
import com.metrowin.launcher.wallet.WalletVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WalletImprovementsTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** A reversible "encryption" that can be turned into a lost key. */
    private class FakeCrypt(var working: Boolean = true) : NoteCrypt {
        override fun encrypt(plain: String) = "x" + plain.reversed()
        override fun decrypt(text: String): String? = if (working && text.startsWith("x")) text.drop(1).reversed() else null
    }

    // --- the formats

    @Test
    fun `the newer formats can be drawn`() {
        assertTrue(Wallet.valid("96385074", CodeFormat.Ean8))
        assertTrue(Wallet.valid("036000291452", CodeFormat.UpcA))
        assertTrue(Wallet.valid("01234565", CodeFormat.UpcE))
        assertTrue(Wallet.valid("1234567890", CodeFormat.Itf))
        assertFalse("odd ITF", Wallet.valid("123", CodeFormat.Itf))
        assertTrue(Wallet.valid("A12345B", CodeFormat.Codabar))
        assertTrue(Wallet.valid("some text", CodeFormat.DataMatrix))
    }

    @Test
    fun `a short EAN or UPC gets its check digit and a wrong one is refused`() {
        assertEquals("5901234123457", Wallet.completed("590123412345", CodeFormat.Ean13))
        assertEquals("96385074", Wallet.completed("9638507", CodeFormat.Ean8))
        assertEquals("036000291452", Wallet.completed("03600029145", CodeFormat.UpcA))
        assertEquals("5901234123457", Wallet.completed("5901234123457", CodeFormat.Ean13))
        assertEquals("abc", Wallet.completed("abc", CodeFormat.Ean13))
        assertEquals("12345", Wallet.completed("12345", CodeFormat.Qr))
        assertTrue(Wallet.valid("5901234123457", CodeFormat.Ean13))
        assertFalse("wrong check digit", Wallet.valid("5901234123458", CodeFormat.Ean13))
    }

    // --- the list

    @Test
    fun `search finds the note too`() {
        val card = Card("a", "Coop", "1", note = "member since 2019")
        assertTrue(Wallet.matches(card, "coop"))
        assertTrue(Wallet.matches(card, "2019"))
        assertFalse(Wallet.matches(card, "2019", inNote = false))
        assertTrue(Wallet.matches(card, "  "))
    }

    @Test
    fun `cards sort by name, expiry or last use and favourites lead`() {
        val a = Card("a", "Alpha", "1", expiry = 300, lastUsed = 5)
        val b = Card("b", "Beta", "2", expiry = 100, lastUsed = 9)
        val c = Card("c", "Gamma", "3", lastUsed = 1)
        val star = Card("d", "Zed", "4", favorite = true)
        assertEquals(listOf("d", "a", "b", "c"), Wallet.sorted(listOf(c, b, a, star), SortMode.Name).map { it.id })
        assertEquals(listOf("d", "b", "a", "c"), Wallet.sorted(listOf(c, a, b, star), SortMode.Expiry).map { it.id })
        assertEquals(listOf("d", "b", "a", "c"), Wallet.sorted(listOf(c, a, b, star), SortMode.LastUsed).map { it.id })
    }

    @Test
    fun `the expiry state says valid, soon or expired`() {
        assertEquals(ExpiryState.None, Wallet.expiryState(Card("a", "n", "c"), 100))
        assertEquals(ExpiryState.Valid, Wallet.expiryState(Card("a", "n", "c", expiry = 200), 100))
        assertEquals(ExpiryState.Soon, Wallet.expiryState(Card("a", "n", "c", expiry = 120), 100))
        assertEquals(ExpiryState.Soon, Wallet.expiryState(Card("a", "n", "c", expiry = 100), 100))
        assertEquals(ExpiryState.Expired, Wallet.expiryState(Card("a", "n", "c", expiry = 99), 100))
    }

    @Test
    fun `the number restored counts the cards that fit`() {
        val existing = (1..Wallet.MAX_CARDS - 1).map { Card("e$it", "n", "c") }
        val incoming = listOf(Card("x", "n", "c"), Card("y", "n", "c"), Card("z", "n", "c"))
        val merged = Wallet.merge(existing, incoming)
        assertEquals(Wallet.MAX_CARDS, merged.size)
        assertEquals(1, Wallet.accepted(merged, incoming))
    }

    @Test
    fun `the new fields come back from the JSON and an old card has them empty`() {
        val card = Card("a", "n", "c", favorite = true, lastUsed = 77, uses = 3)
        assertEquals(listOf(card), Wallet.decode(Wallet.encode(listOf(card))))
        val old = Wallet.decode("""[{"id":"a","name":"n","code":"c"}]""").single()
        assertFalse(old.favorite)
        assertEquals(0, old.uses)
    }

    // --- the files

    @Test
    fun `a backup in the clear and a sealed one both come back`() {
        val cards = listOf(Card("a", "Coop", "123", CodeFormat.Qr, CardKind.Ticket, 5, "n", 2, 9))
        assertEquals(Imported.Cards(cards), WalletBackup.read(WalletBackup.write(cards, null)))
        val sealed = WalletBackup.write(cards, "secret")
        assertFalse(String(sealed, Charsets.ISO_8859_1).contains("Coop"))
        assertEquals(Imported.NeedsPassphrase, WalletBackup.read(sealed))
        assertEquals(Imported.WrongPassphrase, WalletBackup.read(sealed, "other"))
        assertEquals(Imported.Cards(cards), WalletBackup.read(sealed, "secret"))
    }

    @Test
    fun `a file that is not a backup, or is too big, is told apart`() {
        assertEquals(Imported.Unreadable, WalletBackup.read("nope".toByteArray()))
        assertEquals(Imported.Unreadable, WalletBackup.read(null))
        assertEquals(Imported.Unreadable, WalletBackup.read("[]".toByteArray()))
        assertEquals(Imported.TooBig, WalletBackup.read(ByteArray(Wallet.MAX_IMPORT_BYTES + 1)))
    }

    private fun zipOf(passJson: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { it.putNextEntry(ZipEntry("pass.json")); it.write(passJson.toByteArray()); it.closeEntry() }
        return out.toByteArray()
    }

    @Test
    fun `a pkpass becomes one card`() {
        val json = """{"organizationName":"Rail","eventTicket":{},"expirationDate":"2030-01-02T10:00:00+01:00","backgroundColor":"rgb(255, 0, 16)",
            "barcodes":[{"format":"PKBarcodeFormatQR","message":"TICKET-42"}]}"""
        val read = WalletBackup.read(zipOf(json)) as Imported.Cards
        val card = read.cards.single()
        assertEquals("Rail", card.name)
        assertEquals("TICKET-42", card.code)
        assertEquals(CodeFormat.Qr, card.format)
        assertEquals(CardKind.Ticket, card.kind)
        assertEquals(java.time.LocalDate.of(2030, 1, 2).toEpochDay(), card.expiry)
        assertEquals(0xFF0010, read.rgb)
        assertEquals(Imported.Unreadable, WalletBackup.read(zipOf("""{"organizationName":"x"}""")))
    }

    // --- the vault

    @Test
    fun `the vault saves and loads and the file is not plain`() {
        val file = File(folder.root, "wallet.dat")
        val vault = WalletVault(file, FakeCrypt())
        val cards = listOf(Card("a", "Coop", "123"))
        assertTrue(vault.save(cards))
        assertFalse(file.readText().contains("Coop"))
        assertEquals(cards, vault.load())
        assertFalse(vault.unreadable)
    }

    @Test
    fun `an unreadable file is put aside under a new name each time and the vault says so`() {
        val file = File(folder.root, "wallet.dat")
        val crypt = FakeCrypt()
        var now = 1L
        val vault = WalletVault(file, crypt, clock = { now })
        vault.save(listOf(Card("a", "Coop", "123")))
        crypt.working = false
        assertTrue(vault.load().isEmpty())
        assertTrue(vault.unreadable)
        assertFalse(file.exists())
        crypt.working = true
        now = 2
        vault.save(listOf(Card("b", "Shop", "456")))
        crypt.working = false
        assertTrue(vault.load().isEmpty())
        assertNotNull(File(folder.root, "wallet.unreadable.1").takeIf { it.exists() })
        assertNotNull(File(folder.root, "wallet.unreadable.2").takeIf { it.exists() })
    }

    @Test
    fun `the cards of the older wallet are brought in once and dropped`() {
        var dropped = false
        val file = File(folder.root, "wallet.dat")
        val vault = WalletVault(file, FakeCrypt(), legacy = { listOf(Card("o", "Old", "1")) }, dropLegacy = { dropped = true })
        assertEquals(listOf("o"), vault.load().map { it.id })
        assertTrue(dropped)
        assertTrue(file.exists())
    }
}
