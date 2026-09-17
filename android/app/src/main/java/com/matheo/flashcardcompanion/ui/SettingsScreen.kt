package com.matheo.flashcardcompanion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.matheo.flashcardcompanion.AppViewModel
import com.matheo.flashcardcompanion.notify.DueReminder
import kotlinx.coroutines.launch

/**
 * SM-2 knobs, folder paths, the AI credentials and the daily reminder.
 *
 * The graduated intervals are what stop a fresh card collapsing to the same
 * one-day interval whatever you rate it, so they are the first thing shown.
 */
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val t = LocalT.current
    val c = LocalFcColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var status by remember { mutableStateOf<String?>(null) }

    var apkgDir by remember { mutableStateOf(vm.repo.prefs.apkgDir) }
    var pdfDir by remember { mutableStateOf(vm.repo.prefs.pdfDir) }
    var apiKey by remember { mutableStateOf(vm.repo.prefs.apiKey) }
    var model by remember { mutableStateOf(vm.repo.prefs.model) }
    var baseUrl by remember { mutableStateOf(vm.repo.prefs.baseUrl) }

    LaunchedEffect(Unit) {
        values = vm.repo.store.getSettingsMap().mapValues { trimNumber(it.value) }
    }

    fun set(key: String, v: String) { values = values + (key to v) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = PagePadding).verticalScroll(rememberScrollState())
    ) {
        AppHeader(title = t.t("nav.settings"), onBack = onBack)
        ScreenTitle(t.t("nav.settings"))
        ScreenSub(t.t("settings.sm2Sub"))

        NumberField(t.t("rate.again"), values["again_days"].orEmpty(), t.t("settings.unitDays")) {
            set("again_days", it)
        }
        NumberField(t.t("rate.hard"), values["hard_days"].orEmpty(), t.t("settings.unitDays")) {
            set("hard_days", it)
        }
        NumberField(t.t("rate.good"), values["good_days"].orEmpty(), t.t("settings.unitDays")) {
            set("good_days", it)
        }
        NumberField(t.t("rate.easy"), values["easy_days"].orEmpty(), t.t("settings.unitDays")) {
            set("easy_days", it)
        }
        NumberField(t.t("settings.easyBonus"), values["easy_bonus"].orEmpty(), "×") {
            set("easy_bonus", it)
        }

        Spacer(Modifier.height(18.dp))
        SectionTitle(t.t("settings.notifTitle"))
        Text(t.t("settings.notifSub"), color = c.muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        NumberField(t.t("settings.reminderHour"), values["notify_hour"].orEmpty(), "h") {
            set("notify_hour", it)
        }

        Spacer(Modifier.height(18.dp))
        Cta(t.t("settings.save")) {
            val parsed = values.mapNotNull { (k, v) -> v.toDoubleOrNull()?.let { k to it } }.toMap()
            val clean = parsed.mapValues { (k, v) ->
                when (k) {
                    "easy_bonus" -> v.coerceAtLeast(1.0)
                    "notify_hour" -> v.coerceIn(0.0, 23.0)
                    else -> v.coerceAtLeast(0.0)
                }
            }
            vm.repo.store.saveSettings(clean)
            DueReminder.schedule(context, clean["notify_hour"]?.toInt() ?: 9)
            status = t.t("settings.saved")
        }

        Spacer(Modifier.height(26.dp))
        SectionTitle(t.t("settings.pathsTitle"))
        TextField(t.t("settings.apkgDir"), apkgDir) { apkgDir = it }
        TextField(t.t("settings.pdfDir"), pdfDir) { pdfDir = it }
        Spacer(Modifier.height(10.dp))
        Cta(t.t("settings.reload")) {
            vm.repo.prefs.apkgDir = apkgDir.trim()
            vm.repo.prefs.pdfDir = pdfDir.trim()
            scope.launch {
                val cards = vm.repo.loadCards(force = true)
                vm.repo.coursePdfs(force = true)
                vm.refresh(force = true)
                status = t.t("settings.reloaded", mapOf("n" to cards.size))
            }
        }

        Spacer(Modifier.height(26.dp))
        SectionTitle(t.t("settings.aiTitle"))
        Text(
            if (apiKey.isNotBlank()) t.t("settings.apiKeySet") else t.t("settings.apiKeyEmpty"),
            color = c.muted,
            fontFamily = FcType.mono,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        TextField(t.t("settings.apiKey"), apiKey, secret = true) { apiKey = it }
        TextField(t.t("settings.model"), model) { model = it }
        TextField(t.t("settings.baseUrl"), baseUrl) { baseUrl = it }
        Spacer(Modifier.height(10.dp))
        Cta(t.t("settings.save")) {
            vm.repo.prefs.apiKey = apiKey.trim()
            vm.repo.prefs.model = model.trim()
            vm.repo.prefs.baseUrl = baseUrl.trim()
            vm.pingAi()
            status = t.t("settings.saved")
        }

        // Carry over the review history from the Termux backend, if it is still
        // on the device — the schema is identical, so nothing is lost.
        val legacy = remember { vm.legacyDbCandidates() }
        if (legacy.isNotEmpty()) {
            Spacer(Modifier.height(26.dp))
            SectionTitle(t.t("import.title"))
            Text(t.t("import.body"), color = c.muted, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))
            Cta(t.t("import.confirm")) {
                vm.importLegacy(legacy.first()) { n ->
                    status = t.t("import.done", mapOf("n" to n))
                }
            }
        }

        status?.let {
            Spacer(Modifier.height(14.dp))
            Text(it, color = c.accent, fontSize = 13.sp)
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Trailing ".0" is noise on a field the user types days into. */
private fun trimNumber(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

@Composable
private fun NumberField(label: String, value: String, unit: String, onChange: (String) -> Unit) {
    val c = LocalFcColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = c.ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            suffix = { Text(unit, color = c.muted, fontFamily = FcType.mono, fontSize = 12.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.width(130.dp),
        )
    }
}

@Composable
private fun TextField(
    label: String,
    value: String,
    secret: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
