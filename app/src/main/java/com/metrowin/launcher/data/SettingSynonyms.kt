package com.metrowin.launcher.data

/**
 * Words that mean the same in the six languages of the launcher, so the search of the settings finds a setting by a word of another language or by
 * another name ("dunkel" finds the dark theme on a phone in Italian; "luminosita" finds the brightness of the screen on a phone in English).
 * Each group is words of one idea, folded (no accents, lower case, one word each); a typed word of a group is searched as every other word of it too.
 */
object SettingSynonyms {
    private val groups: List<Set<String>> = listOf(
        "dark black night scuro nero notte dunkel schwarz nacht oscuro negro noche sombre noir nuit escuro preto noite",
        "theme style look tema stile aspetto thema design aussehen estilo apariencia theme apparence aparencia",
        "color colour accent colore colori accento farbe akzent farben acento couleur cor",
        "brightness luminosita helligkeit brillo luminosite brilho",
        "sound volume audio suono suoni ton klang sonido son som",
        "vibration haptic vibrazione vibracion vibracao",
        "notification alert notifica notifiche benachrichtigung notificacion notificaciones notificacao",
        "battery power batteria energia akku batterie bateria pila energie",
        "search find cerca ricerca suche suchen buscar busqueda recherche chercher pesquisa procurar",
        "language lingua sprache idioma langue",
        "lock blocco sperre bloqueo verrouillage bloqueio",
        "security sicurezza sicherheit seguridad securite seguranca",
        "privacy riservatezza datenschutz privacidad confidentialite",
        "font text carattere testo schrift texto police fonte",
        "size dimensione taille tamano tamanho",
        "animation motion animazione movimento animacion movimiento mouvement animacao",
        "backup restore copia ripristino sicherung wiederherstellen respaldo restaurar sauvegarde restaurer",
        "gesture swipe gesto gesti gesten geste gestos",
        "tile tiles riquadro riquadri kachel kacheln mosaico ficha tuile tuiles bloco blocos",
        "reset default factory ripristina predefinito fabbrica zurucksetzen standard werkseinstellung restablecer predeterminado reinitialiser defaut redefinir padrao",
        "wallpaper background sfondo hintergrund fondo fond fundo",
        "clock time orologio ora uhr zeit reloj hora horloge heure relogio",
        "weather meteo wetter clima tiempo tempo",
        "contact contacts contatto contatti kontakt kontakte contacto contactos",
        "message messages sms messaggio messaggi nachricht nachrichten mensaje mensajes mensagem mensagens",
        "icon icons icona icone symbol symbole icono iconos icone icones",
        "accessibility accessibilita barrierefreiheit accesibilidad accessibilite acessibilidade",
        "contrast contrasto kontrast contraste",
        "mail email posta correo courrier",
        "calendar calendario kalender calendrier",
        "photo photos foto fotos bild bilder imagen imagenes",
        "update updates aggiornamento aggiornamenti aktualisierung actualizacion atualizacao",
    ).map { it.split(' ').toSet() }

    private val byWord: Map<String, List<Set<String>>> = groups.flatten().toSet().associateWith { word -> groups.filter { word in it } }

    /** [query] itself first, then the same query with one of its words put as each of the words that mean the same; [max] queries at most. */
    fun expand(query: String, max: Int = 24): List<String> {
        val words = SearchText.words(query)
        val out = linkedSetOf(query)
        for ((i, word) in words.withIndex()) {
            for (other in byWord[word].orEmpty().flatMap { group -> group.filter { it != word } }) {
                if (out.size >= max) return out.toList()
                out += words.toMutableList().also { it[i] = other }.joinToString(" ")
            }
        }
        return out.toList()
    }
}
