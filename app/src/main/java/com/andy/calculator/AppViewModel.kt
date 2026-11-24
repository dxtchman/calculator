package com.andy.calculator

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { Calculator, Converter, VaultSetup, VaultUnlock, VaultHome }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val repo = VaultRepository(app)

    var screen by mutableStateOf(Screen.Calculator)

    /** Set while a picker, viewer or system dialog we opened is in front of us. */
    var externalActivityOpen = false
        set(value) {
            field = value
            if (value) externalSince = SystemClock.elapsedRealtime()
        }
    private var externalSince = 0L

    /** Text for the "please wait" overlay, or null. */
    var busy by mutableStateOf<String?>(null)
        private set

    private var failedAttempts = 0
    var lockedUntil by mutableLongStateOf(0L)
        private set

    private fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }

    fun openVault() {
        screen = if (repo.isSetUp) Screen.VaultUnlock else Screen.VaultSetup
    }

    fun lock() {
        repo.lock()
        if (screen == Screen.VaultHome || screen == Screen.VaultUnlock || screen == Screen.VaultSetup) {
            screen = Screen.Calculator
        }
    }

    /** Coming back from another app: lock anyway if it's been a long time. */
    fun onReturn() {
        if (externalActivityOpen && SystemClock.elapsedRealtime() - externalSince > 10 * 60_000) lock()
        externalActivityOpen = false
    }

    fun setup(password: String, pin: Boolean) {
        viewModelScope.launch {
            busy = "Setting up…"
            repo.setup(password, pin)
            busy = null
            screen = Screen.VaultHome
        }
    }

    fun unlock(password: String, onWrong: (String) -> Unit) {
        if (SystemClock.elapsedRealtime() < lockedUntil) return
        viewModelScope.launch {
            busy = "Unlocking…"
            val ok = repo.unlock(password)
            busy = null
            if (ok) {
                failedAttempts = 0
                // the user may have left the app while we were checking
                if (screen == Screen.VaultUnlock) screen = Screen.VaultHome else repo.lock()
            } else {
                failedAttempts++
                if (failedAttempts >= 5) {
                    failedAttempts = 0
                    lockedUntil = SystemClock.elapsedRealtime() + 30_000
                    onWrong("Too many attempts.")
                } else {
                    onWrong(if (repo.usesPin) "Wrong PIN." else "Wrong password.")
                }
            }
        }
    }

    fun changePassword(password: String, pin: Boolean, done: () -> Unit) {
        viewModelScope.launch {
            busy = "Saving…"
            val ok = repo.changePassword(password, pin)
            busy = null
            toast(if (ok) "Password changed." else "Couldn't change the password.")
            done()
        }
    }

    fun wipe() {
        repo.wipe()
        screen = Screen.Calculator
        toast("Locked folder deleted.")
    }

    /** Encrypts each item; calls back with the ones that are safely in the folder. */
    fun importItems(uris: List<Uri>, kind: String, onDone: (List<Uri>) -> Unit) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val ok = ArrayList<Uri>()
            for ((i, u) in uris.withIndex()) {
                if (!repo.isUnlocked) break
                busy = "Moving ${i + 1} of ${uris.size}…"
                val item = repo.importUri(u, kind) ?: continue
                repo.items.add(0, item)
                if (repo.saveIndex()) ok.add(u) else {
                    repo.items.remove(item)
                    repo.deleteFiles(item)
                }
            }
            busy = null
            val failed = uris.size - ok.size
            if (failed > 0) toast("Couldn't move $failed item${if (failed == 1) "" else "s"}.")
            if (repo.isUnlocked) onDone(ok)
        }
    }

    /** Deletes picked documents after they were copied in. */
    fun deleteDocuments(uris: List<Uri>) {
        viewModelScope.launch {
            val cr = getApplication<Application>().contentResolver
            val failed = withContext(Dispatchers.IO) {
                uris.count { u ->
                    try {
                        !DocumentsContract.deleteDocument(cr, u)
                    } catch (e: Exception) {
                        true
                    }
                }
            }
            if (failed > 0) toast("Couldn't delete $failed original${if (failed == 1) "" else "s"}. Delete them yourself.")
            else toast("Originals deleted.")
        }
    }

    fun delete(items: List<VaultItem>) {
        viewModelScope.launch {
            for (item in items) {
                repo.items.remove(item)
                repo.deleteFiles(item)
            }
            repo.saveIndex()
        }
    }

    /** Puts items back in the gallery / Downloads and removes them from the folder. */
    fun restore(items: List<VaultItem>, removeAfter: Boolean = true) {
        viewModelScope.launch {
            var done = 0
            for ((i, item) in items.withIndex()) {
                busy = "Restoring ${i + 1} of ${items.size}…"
                if (repo.export(item)) {
                    done++
                    if (removeAfter) {
                        repo.items.remove(item)
                        repo.deleteFiles(item)
                    }
                }
            }
            repo.saveIndex()
            busy = null
            val where = if (items.all { it.isMedia }) "the gallery (Restored album)" else "Downloads"
            toast(if (done == items.size) "Moved to $where." else "Restored $done of ${items.size}.")
        }
    }

    fun openFile(context: Context, item: VaultItem) {
        viewModelScope.launch {
            busy = "Opening…"
            val f = repo.decryptToCache(item)
            busy = null
            if (f == null) {
                toast("Couldn't open this file.")
                return@launch
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, item.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            externalActivityOpen = true
            try {
                context.startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                externalActivityOpen = false
                toast("No app can open this file.")
            }
        }
    }

    fun newNote() = Note(UUID.randomUUID().toString(), "", "", System.currentTimeMillis())

    fun saveNote(note: Note) {
        val i = repo.notes.indexOfFirst { it.id == note.id }
        if (i >= 0) repo.notes.removeAt(i)
        repo.notes.add(0, note)
        viewModelScope.launch { repo.saveIndex() }
    }

    fun deleteNote(note: Note) {
        repo.notes.removeAll { it.id == note.id }
        viewModelScope.launch { repo.saveIndex() }
    }
}
