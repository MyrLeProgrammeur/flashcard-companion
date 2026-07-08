/* i18n — FR/EN, no build step. Loaded BEFORE common.js on every page.
   - String source of truth is the I18N dict below (fr is canonical).
   - Static markup: mark elements with data-i18n="key" (textContent) or
     data-i18n-attr="attr:key;attr2:key2" (attributes like aria-label).
   - Dynamic JS strings: call t("key", {var: value}); {var} placeholders are
     substituted. For a plural noun call tn("key", n) where the key value is
     written "singular|plural".
   Switching language persists to localStorage and reloads the page so every
   string (static + dynamic) re-applies cleanly. */

const I18N_KEY = "fc-lang";
const SUPPORTED_LANGS = ["fr", "en"];
const LANG_LABELS = { fr: "Français", en: "English" };

function getLang() {
  const l = localStorage.getItem(I18N_KEY);
  return SUPPORTED_LANGS.includes(l) ? l : "fr";
}
function setLang(l) {
  if (SUPPORTED_LANGS.includes(l)) localStorage.setItem(I18N_KEY, l);
}

const I18N = {
  fr: {
    // ---- shared: nav / header / menu ----
    "nav.home": "Accueil",
    "nav.courses": "Cours",
    "nav.exams": "Examens",
    "nav.stats": "Statistiques",
    "nav.settings": "Réglages",
    "nav.theme": "Thème",
    "nav.menu": "Menu",
    "nav.language": "Langue",
    "health.online": "En ligne",
    "health.offline": "Hors ligne",
    "common.back": "Retour",
    "common.close": "Fermer",
    "common.error": "erreur",
    // ---- shared rating labels (settings + review) ----
    "rate.again": "Encore",
    "rate.hard": "Difficile",
    "rate.good": "Correct",
    "rate.easy": "Facile",
    // ---- home / decks ----
    "home.today": "Aujourd'hui",
    "home.loading": "Chargement…",
    "home.crumbRoot": "Accueil",
    "home.folder": "dossier|dossiers",
    "home.deck": "deck|decks",
    // subtitle built in JS: {due} + due-plural "s" + noun (via tn)
    "home.subtitle": "{due} carte{dueS} due{dueS} · {count} {noun}",
    "home.empty": "Vide ici. Vérifie que Syncthing a copié les .apkg dans le dossier Flashcards.",
    "home.cardCount": "{n} cartes",
    "home.upToDate": " · à jour",
    "home.reviewAll": "Réviser tout",
    "home.reviewScoped": "Réviser · {name}",
    "home.directCards": "Cartes directes",
    // ---- offline screen ----
    "offline.title": "Backend hors ligne",
    "offline.desc": "Le serveur local ne répond pas sur",
    "offline.restartHead": "Pour redémarrer",
    "offline.step1pre": "Ouvre ",
    "offline.step2pre": "Lance ",
    "offline.retry": "Réessayer",
    // ---- courses ----
    "courses.title": "PDF de cours",
    "courses.sub": "Un dossier par matière, retrouvé automatiquement à partir des decks — pas garanti à 100%.",
    "courses.loadError": "Impossible de charger les cours.",
    "courses.noSubjects": "Aucune matière trouvée.",
    "courses.noPdf": "Aucun PDF trouvé.",
    // ---- exams ----
    "exams.newTitle": "Nouvel examen",
    "exams.newSub": "Matière (choisie parmi tes decks) et date approximative des résultats.",
    "exams.subject": "Matière",
    "exams.pickSubject": "Choisir une matière…",
    "exams.expectedResults": "Résultats attendus",
    "exams.add": "Ajouter",
    "exams.corrTitle": "Corrélation par matière",
    "exams.corrSub": "Note obtenue, % de réussite en révision, temps investi — trois chiffres indépendants, pas de score composite : c'est à vous de juger.",
    "exams.thResults": "Résultats",
    "exams.thGrade": "Note",
    "exams.thSuccess": "Réussite",
    "exams.thTime": "Temps investi",
    "exams.empty": "Aucun examen enregistré pour l'instant.",
    "exams.adding": "Ajout…",
    "exams.added": "Ajouté.",
    "exams.addFailed": "Échec de l'ajout.",
    // ---- stats ----
    "stats.overviewTitle": "Vue d'ensemble",
    "stats.overviewSub": "Historique complet des révisions, jamais purgé.",
    "stats.reviews": "révisions",
    "stats.totalTime": "temps total",
    "stats.successRate": "réussite",
    "stats.daysTitle": "Jours de révision",
    "stats.noReviews": "Pas encore de révision enregistrée.",
    "stats.export": "Export",
    "stats.perCard": "Par carte",
    "stats.thCard": "Carte",
    "stats.thReviews": "Révisions",
    "stats.thSuccess": "Réussite",
    "stats.thAvgTime": "Tps moy.",
    "stats.thTotalTime": "Tps total",
    "stats.thLastGrade": "Dern. note",
    "stats.thLastReview": "Dernière révision",
    "stats.noCards": "Aucune carte révisée pour l'instant.",
    "stats.dayCellTitle": "{date} · {n} révision{s}",
    "stats.dayHigh": "Jour le plus haut : ",
    "stats.dayLow": "Jour le plus bas : ",
    // ---- settings ----
    "settings.sm2Sub": "Intervalles gradués (en jours) appliqués dès la 1ʳᵉ révision, et bonus des cartes matures.",
    "settings.easyBonus": "Bonus Facile",
    "settings.unitDays": "j",
    "settings.notifTitle": "Notifications",
    "settings.notifSub": "Heure du rappel quotidien s'il reste des cartes dues (Termux, local, sans Google).",
    "settings.reminderHour": "Heure du rappel",
    "settings.save": "Enregistrer",
    "settings.loadError": "Impossible de charger les réglages.",
    "settings.saving": "Enregistrement…",
    "settings.saved": "Enregistré.",
    "settings.saveFailed": "Échec de l'enregistrement.",
    // ---- review ----
    "review.recto": "Recto",
    "review.tapReveal": "Touchez pour révéler",
    "review.verso": "Verso",
    "review.tapToAnswer": "Touchez la carte pour répondre",
    "review.sessionDone": "Session terminée",
    "review.allReviewed": "Toutes les cartes dues ont été revues.",
    "review.backToDecks": "Retour aux decks",
    "review.deepExplTitle": "Explication approfondie",
    "review.explainBtn": "Explique en profondeur",
    "review.allDecks": "Tous les decks",
    "review.viewSource": "Voir le cours source",
    "review.cardsReviewed": "{n} carte{s} revue{s}.",
    "review.nothingToReview": "Rien à réviser ici pour le moment.",
    "review.groundedIn": "Ancré dans {files}",
    "review.noSourceChip": "Sans source PDF · carte seule",
    "review.noSourceBand": "Aucun PDF source n'a pu être associé à cette carte. L'explication est générée à partir du recto/verso seulement — recoupe avec ton cours.",
    "review.explainError": "Impossible de générer l'explication. Vérifie que le backend et la clé Infercom sont OK.",
    "review.fbQuestion": "Utile ?",
    "review.fbUp": "Utile",
    "review.fbDown": "Pas utile",
    "review.fbThanks": "Merci, c'est noté",
    // ---- pdf viewer ----
    "pdf.helpFab": "Besoin d'aide",
    "pdf.prevPage": "Page précédente",
    "pdf.nextPage": "Page suivante",
    "pdf.askPlaceholder": "Pose une question sur ce cours…",
    "pdf.noCourse": "Aucun cours spécifié.",
    "pdf.loading": "Chargement du PDF…",
    "pdf.loadError": "Impossible de charger ce PDF.",
    "pdf.modelFallback": "modèle",
    "pdf.cached": "en cache",
    "pdf.freshGen": "généré à l'instant",
    "pdf.helpError": "Impossible d'obtenir une réponse. Vérifie que le backend et la clé Infercom sont OK.",
  },
  en: {
    "nav.home": "Home",
    "nav.courses": "Courses",
    "nav.exams": "Exams",
    "nav.stats": "Statistics",
    "nav.settings": "Settings",
    "nav.theme": "Theme",
    "nav.menu": "Menu",
    "nav.language": "Language",
    "health.online": "Online",
    "health.offline": "Offline",
    "common.back": "Back",
    "common.close": "Close",
    "common.error": "error",
    "rate.again": "Again",
    "rate.hard": "Hard",
    "rate.good": "Good",
    "rate.easy": "Easy",
    "home.today": "Today",
    "home.loading": "Loading…",
    "home.crumbRoot": "Home",
    "home.folder": "folder|folders",
    "home.deck": "deck|decks",
    "home.subtitle": "{due} card{dueS} due · {count} {noun}",
    "home.empty": "Nothing here. Check that Syncthing copied the .apkg files into the Flashcards folder.",
    "home.cardCount": "{n} cards",
    "home.upToDate": " · up to date",
    "home.reviewAll": "Review all",
    "home.reviewScoped": "Review · {name}",
    "home.directCards": "Direct cards",
    "offline.title": "Backend offline",
    "offline.desc": "The local server is not responding on",
    "offline.restartHead": "To restart",
    "offline.step1pre": "Open ",
    "offline.step2pre": "Run ",
    "offline.retry": "Retry",
    "courses.title": "Course PDFs",
    "courses.sub": "One folder per subject, matched automatically from the decks — not 100% guaranteed.",
    "courses.loadError": "Could not load the courses.",
    "courses.noSubjects": "No subject found.",
    "courses.noPdf": "No PDF found.",
    "exams.newTitle": "New exam",
    "exams.newSub": "Subject (picked from your decks) and approximate date of the results.",
    "exams.subject": "Subject",
    "exams.pickSubject": "Choose a subject…",
    "exams.expectedResults": "Expected results",
    "exams.add": "Add",
    "exams.corrTitle": "Correlation by subject",
    "exams.corrSub": "Grade obtained, % review success, time invested — three independent figures, no composite score: it's up to you to judge.",
    "exams.thResults": "Results",
    "exams.thGrade": "Grade",
    "exams.thSuccess": "Success",
    "exams.thTime": "Time invested",
    "exams.empty": "No exam recorded yet.",
    "exams.adding": "Adding…",
    "exams.added": "Added.",
    "exams.addFailed": "Failed to add.",
    "stats.overviewTitle": "Overview",
    "stats.overviewSub": "Full review history, never purged.",
    "stats.reviews": "reviews",
    "stats.totalTime": "total time",
    "stats.successRate": "success",
    "stats.daysTitle": "Review days",
    "stats.noReviews": "No review recorded yet.",
    "stats.export": "Export",
    "stats.perCard": "Per card",
    "stats.thCard": "Card",
    "stats.thReviews": "Reviews",
    "stats.thSuccess": "Success",
    "stats.thAvgTime": "Avg time",
    "stats.thTotalTime": "Total time",
    "stats.thLastGrade": "Last grade",
    "stats.thLastReview": "Last review",
    "stats.noCards": "No card reviewed yet.",
    "stats.dayCellTitle": "{date} · {n} review{s}",
    "stats.dayHigh": "Highest day: ",
    "stats.dayLow": "Lowest day: ",
    "settings.sm2Sub": "Graduated intervals (in days) applied from the 1st review, plus a bonus for mature cards.",
    "settings.easyBonus": "Easy bonus",
    "settings.unitDays": "d",
    "settings.notifTitle": "Notifications",
    "settings.notifSub": "Time of the daily reminder if due cards remain (Termux, local, no Google).",
    "settings.reminderHour": "Reminder time",
    "settings.save": "Save",
    "settings.loadError": "Could not load the settings.",
    "settings.saving": "Saving…",
    "settings.saved": "Saved.",
    "settings.saveFailed": "Failed to save.",
    "review.recto": "Front",
    "review.tapReveal": "Tap to reveal",
    "review.verso": "Back",
    "review.tapToAnswer": "Tap the card to answer",
    "review.sessionDone": "Session complete",
    "review.allReviewed": "All due cards have been reviewed.",
    "review.backToDecks": "Back to decks",
    "review.deepExplTitle": "In-depth explanation",
    "review.explainBtn": "Explain in depth",
    "review.allDecks": "All decks",
    "review.viewSource": "View source course",
    "review.cardsReviewed": "{n} card{s} reviewed.",
    "review.nothingToReview": "Nothing to review here right now.",
    "review.groundedIn": "Grounded in {files}",
    "review.noSourceChip": "No PDF source · card only",
    "review.noSourceBand": "No source PDF could be matched to this card. The explanation is generated from the front/back only — cross-check with your course.",
    "review.explainError": "Could not generate the explanation. Check that the backend and the Infercom key are OK.",
    "review.fbQuestion": "Helpful?",
    "review.fbUp": "Helpful",
    "review.fbDown": "Not helpful",
    "review.fbThanks": "Thanks, noted",
    "pdf.helpFab": "Need help",
    "pdf.prevPage": "Previous page",
    "pdf.nextPage": "Next page",
    "pdf.askPlaceholder": "Ask a question about this course…",
    "pdf.noCourse": "No course specified.",
    "pdf.loading": "Loading PDF…",
    "pdf.loadError": "Could not load this PDF.",
    "pdf.modelFallback": "model",
    "pdf.cached": "cached",
    "pdf.freshGen": "just generated",
    "pdf.helpError": "Could not get an answer. Check that the backend and the Infercom key are OK.",
  },
};

/* Look up a key; substitute {var} placeholders. Falls back fr -> key. */
function t(key, vars) {
  const lang = getLang();
  let s = (I18N[lang] && I18N[lang][key]) ?? I18N.fr[key] ?? key;
  if (vars) for (const k in vars) s = s.split(`{${k}}`).join(vars[k]);
  return s;
}

/* Plural-aware noun: value is "singular|plural"; picks by |n|. */
function tn(key, n) {
  const forms = t(key).split("|");
  return Math.abs(n) === 1 ? forms[0] : forms[forms.length - 1];
}

/* Apply translations to static markup within root. */
function applyI18n(root = document) {
  document.documentElement.setAttribute("lang", getLang());
  root.querySelectorAll("[data-i18n]").forEach((el) => {
    el.textContent = t(el.getAttribute("data-i18n"));
  });
  root.querySelectorAll("[data-i18n-attr]").forEach((el) => {
    el.getAttribute("data-i18n-attr").split(";").forEach((pair) => {
      const idx = pair.indexOf(":");
      if (idx < 0) return;
      const attr = pair.slice(0, idx).trim();
      const key = pair.slice(idx + 1).trim();
      if (attr && key) el.setAttribute(attr, t(key));
    });
  });
}
