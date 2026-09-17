package com.matheo.flashcardcompanion.ui

/**
 * Strings that exist only in the native app.
 *
 * The web UI had no storage permission, no local API key and no backend to
 * import from, so these have no counterpart in `i18n.js` and are kept out of
 * the generated [Strings] table rather than pushed back into the web app.
 */
object StringsExtra {

    /**
     * Keys whose web wording no longer describes the native app, and which
     * therefore take precedence over the generated [Strings] table. Kept
     * separate so regenerating from `i18n.js` cannot quietly undo them.
     */
    val overrideFr: Map<String, String> = mapOf(
        "settings.notifSub" to "Heure du rappel quotidien s'il reste des cartes dues. Notification locale, sans serveur ni Google.",
        "courses.sub" to "Les PDF de cours synchronisés, tels qu'ils sont rangés sur le téléphone.",
    )

    val overrideEn: Map<String, String> = mapOf(
        "settings.notifSub" to "Time of the daily reminder when cards are still due. A local notification, with no server and no Google.",
        "courses.sub" to "The synced course PDFs, laid out as they are on the phone.",
    )

    val fr: Map<String, String> = mapOf(
        "storage.title" to "Accès aux fichiers",
        "storage.desc" to "L'app lit les .apkg et les PDF synchronisés par Syncthing dans le stockage partagé. Android demande une autorisation explicite pour ça.",
        "storage.grant" to "Autoriser l'accès",
        "storage.note" to "Réglages › Accès à tous les fichiers",

        "import.title" to "Reprendre l'historique",
        "import.body" to "Une base du backend Termux a été trouvée. Ses révisions et son planning peuvent être repris tels quels.",
        "import.confirm" to "Importer",
        "import.skip" to "Ignorer",
        "import.done" to "{n} cartes reprises",

        "ai.noKey" to "Aucune clé Infercom. Renseigne-la dans les réglages pour activer les explications.",
        "ai.error" to "Impossible de générer l'explication. Vérifie la clé Infercom et la connexion.",

        "settings.pathsTitle" to "Dossiers",
        "settings.apkgDir" to "Dossier des .apkg",
        "settings.pdfDir" to "Dossier des cours (PDF)",
        "settings.aiTitle" to "IA",
        "settings.apiKey" to "Clé Infercom",
        "settings.model" to "Modèle",
        "settings.baseUrl" to "URL de l'API",
        "settings.reload" to "Recharger les decks",
        "settings.reloaded" to "{n} cartes chargées",
        "settings.apiKeySet" to "Clé enregistrée",
        "settings.apiKeyEmpty" to "Non renseignée",

        "courses.fileCount" to "{n} fichiers",

        "pdf.page" to "Page {n} / {total}",

        "review.noCards" to "Rien à réviser ici.",
        "exams.delete" to "Supprimer",
        "common.retry" to "Réessayer",
        "common.ok" to "OK",
    )

    val en: Map<String, String> = mapOf(
        "storage.title" to "File access",
        "storage.desc" to "The app reads the .apkg decks and course PDFs that Syncthing mirrors into shared storage. Android requires an explicit permission for that.",
        "storage.grant" to "Grant access",
        "storage.note" to "Settings › All files access",

        "import.title" to "Carry over your history",
        "import.body" to "A database from the Termux backend was found. Its reviews and schedule can be carried over as they are.",
        "import.confirm" to "Import",
        "import.skip" to "Skip",
        "import.done" to "{n} cards carried over",

        "ai.noKey" to "No Infercom key. Add one in settings to enable explanations.",
        "ai.error" to "Could not generate the explanation. Check the Infercom key and your connection.",

        "settings.pathsTitle" to "Folders",
        "settings.apkgDir" to ".apkg folder",
        "settings.pdfDir" to "Course PDF folder",
        "settings.aiTitle" to "AI",
        "settings.apiKey" to "Infercom key",
        "settings.model" to "Model",
        "settings.baseUrl" to "API URL",
        "settings.reload" to "Reload decks",
        "settings.reloaded" to "{n} cards loaded",
        "settings.apiKeySet" to "Key saved",
        "settings.apiKeyEmpty" to "Not set",

        "courses.fileCount" to "{n} files",

        "pdf.page" to "Page {n} / {total}",

        "review.noCards" to "Nothing to review here.",
        "exams.delete" to "Delete",
        "common.retry" to "Retry",
        "common.ok" to "OK",
    )
}
