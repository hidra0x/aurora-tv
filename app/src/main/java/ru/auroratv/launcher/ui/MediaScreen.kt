package ru.auroratv.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ru.auroratv.launcher.MainActivity
import ru.auroratv.launcher.R
import ru.auroratv.launcher.core.AppEntry
import ru.auroratv.launcher.core.Prefs
import ru.auroratv.launcher.media.PlayerActivity
import java.io.File
import java.text.Collator
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors

class MediaScreen(host: MainActivity, prefs: Prefs) : LauncherScreen(host, prefs) {

    private enum class Kind { BACK, ACTION, FOLDER, FILE, LIBRARY }

    private data class Row(
        val kind: Kind,
        val title: String,
        val subtitle: String = "",
        val meta: String = "",
        val uri: Uri? = null,
        val docId: String? = null,
        val mime: String? = null,
        val savedRoot: Boolean = false
    )

    private data class Crumb(val docId: String, val name: String)

    private val adapter = MediaAdapter(
        host = host,
        prefs = prefs,
        onClick = { row -> onRowClick(row) },
        onLongClick = { row -> onRowLongClick(row) }
    )
    private val list = RecyclerView(host)
    private lateinit var pathLabel: TextView
    private lateinit var emptyHint: TextView

    private var rows: List<Row> = emptyList()
    private var currentTree: Uri? = null
    private var currentDocId: String? = null
    private var currentName: String = ""
    private var libraryMode = false
    private val stack = ArrayDeque<Crumb>()
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "aurora-media").apply { isDaemon = true }
    }

    init {
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(dp(40), dp(4), dp(40), dp(8))
        }
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        val header = LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(
            makeText("Медиа", 26f, host.getColor(R.color.text), light = true),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(makeText("Видео и музыка", 11f, host.getColor(R.color.text_faint)))
        root.addView(
            header,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(44))
        )

        pathLabel = makeText("Папки", 11f, host.getColor(R.color.text_faint))
        root.addView(
            pathLabel,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(28))
        )

        emptyHint = makeText(
            "Выберите папку с видео или откройте медиатеку телевизора. " +
                "Для папок доступ выдаётся только к выбранной папке.",
            11f,
            host.getColor(R.color.text_dim)
        ).apply {
            background = host.getDrawable(R.drawable.bg_card)
            setPadding(dp(18), dp(14), dp(18), dp(14))
            visibility = View.GONE
        }
        root.addView(
            emptyHint,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        )

        list.apply {
            layoutManager = LinearLayoutManager(host)
            adapter = this@MediaScreen.adapter
            setupDefaults()
            setPadding(0, dp(4), 0, dp(4))
            addItemDecoration(VerticalSpaceDecoration(dp(4)))
        }
        root.addView(
            list,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        showRoots()
    }

    override fun onAppsLoaded(apps: List<AppEntry>) = Unit

    override fun requestInitialFocus() {
        if (rows.isEmpty()) {
            host.focusNavigation()
        } else {
            list.focusChildAt(if (rows.firstOrNull()?.kind == Kind.BACK) 1 else 0)
        }
    }

    override fun onShown() {
        when {
            libraryMode -> loadLibrary()
            currentTree == null -> showRoots()
            else -> loadChildren()
        }
    }

    override fun onBack(): Boolean {
        if (libraryMode) {
            showRoots()
            return true
        }
        if (currentTree == null) return false
        if (stack.isEmpty()) {
            showRoots()
        } else {
            val previous = stack.removeLast()
            currentDocId = previous.docId
            currentName = previous.name
            loadChildren()
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_PICK_FOLDER) return false
        if (resultCode == Activity.RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                try {
                    host.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {
                    // Some providers hand out a non-persistable grant; playback still works this session.
                }
                prefs.addMediaFolder(uri.toString())
                openTree(uri)
            }
        }
        return true
    }

    override fun hint(): String =
        "OK — открыть    BACK — назад    MENU — настройки"

    private fun showRoots() {
        libraryMode = false
        currentTree = null
        currentDocId = null
        currentName = ""
        stack.clear()
        pathLabel.text = "Папки"

        val items = mutableListOf<Row>()
        if (Build.VERSION.SDK_INT <= 28) {
            items.add(
                Row(
                    kind = Kind.LIBRARY,
                    title = "Медиатека устройства",
                    subtitle = "Видео и музыка из памяти телевизора",
                    meta = "▶"
                )
            )
        }
        for (folder in prefs.mediaFolders) {
            val uri = try {
                Uri.parse(folder)
            } catch (_: Throwable) {
                null
            } ?: continue
            val rootId = try {
                DocumentsContract.getTreeDocumentId(uri)
            } catch (_: Throwable) {
                null
            } ?: continue
            items.add(
                Row(
                    kind = Kind.FOLDER,
                    title = treeName(uri),
                    subtitle = "Сохранённая папка",
                    meta = "›",
                    uri = uri,
                    docId = rootId,
                    savedRoot = true
                )
            )
        }
        items.add(
            Row(
                kind = Kind.ACTION,
                title = "Добавить папку",
                subtitle = "USB-накопитель или внутренняя память",
                meta = "+"
            )
        )
        submit(items)
        emptyHint.visibility = if (prefs.mediaFolders.isEmpty()) View.VISIBLE else View.GONE
        list.focusChildAt(0)
    }

    private fun requestLibrary() {
        if (Build.VERSION.SDK_INT <= 28 &&
            host.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AlertDialog.Builder(host, R.style.Theme_Aurora_Dialog)
                .setTitle("Доступ к медиатеке")
                .setMessage(
                    "Разрешите доступ к файлам в настройках приложения. " +
                        "При установке через install.ps1 разрешение выдаётся автоматически."
                )
                .setPositiveButton("Настройки") { _, _ ->
                    try {
                        host.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${host.packageName}")
                            )
                        )
                    } catch (_: Throwable) {
                        host.toast("Не удалось открыть настройки приложения")
                    }
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }
        loadLibrary()
    }

    private fun loadLibrary() {
        libraryMode = true
        currentTree = null
        currentDocId = null
        currentName = ""
        stack.clear()
        pathLabel.text = "Медиатека"
        emptyHint.visibility = View.GONE

        worker.execute {
            val found = scanLibrary()
            host.runOnUiThread {
                if (!isShown || !libraryMode) return@runOnUiThread
                val items = mutableListOf<Row>()
                items.add(
                    Row(
                        kind = Kind.BACK,
                        title = "Назад",
                        subtitle = "К папкам"
                    )
                )
                items.addAll(found)
                submit(items)
                if (found.isEmpty()) {
                    host.toast("Видео и музыка не найдены")
                    list.focusChildAt(0)
                } else {
                    list.focusChildAt(1)
                }
            }
        }
    }

    private fun scanLibrary(): List<Row> {
        val found = LinkedHashMap<String, File>()
        val internal = Environment.getExternalStorageDirectory()
        val commonDirs = listOf(
            "Movies", "Movie", "Video", "Videos",
            "Music", "Audio",
            "Download", "Downloads",
            "DCIM", "Pictures"
        )
        for (name in commonDirs) {
            scanDirectory(File(internal, name), 5, found)
        }

        // USB drives and SD cards are mounted as /storage/<volume-id>.
        File("/storage").listFiles()?.forEach { volume ->
            if (volume.isDirectory &&
                volume.name !in setOf("emulated", "self", "sdcard", "sdcard0", "sdcard1")
            ) {
                scanDirectory(volume, 5, found)
            }
        }

        return found.values.map { file ->
            Row(
                kind = Kind.FILE,
                title = file.name,
                subtitle = formatSize(file.length()).ifBlank { "Медиа" },
                meta = "▶",
                uri = Uri.fromFile(file),
                mime = mimeFor(file.name)
            )
        }.sortedWith(Comparator { a, b -> a.title.compareTo(b.title, ignoreCase = true) })
    }

    private fun scanDirectory(
        directory: File,
        depth: Int,
        found: MutableMap<String, File>
    ) {
        if (depth < 0 || !directory.isDirectory) return
        val children = try {
            directory.listFiles()
        } catch (_: Throwable) {
            null
        } ?: return
        for (child in children) {
            if (child.isDirectory) {
                if (child.name.equals("Android", ignoreCase = true) ||
                    child.name.equals("LOST.DIR", ignoreCase = true) ||
                    child.name.startsWith(".")
                ) {
                    continue
                }
                scanDirectory(child, depth - 1, found)
            } else {
                val extension = child.name.substringAfterLast('.', "").lowercase(Locale.US)
                if (extension in MEDIA_EXTENSIONS && child.canRead()) {
                    found[child.absolutePath] = child
                }
            }
        }
    }

    private fun mimeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        return if (extension in AUDIO_EXTENSIONS) "audio/*" else "video/*"
    }

    private fun openTree(tree: Uri) {
        val rootId = try {
            DocumentsContract.getTreeDocumentId(tree)
        } catch (_: Throwable) {
            host.toast("Не удалось открыть папку")
            return
        }
        currentTree = tree
        currentDocId = rootId
        currentName = treeName(tree)
        stack.clear()
        loadChildren()
    }

    private fun openFolder(row: Row) {
        if (row.savedRoot) {
            row.uri?.let { openTree(it) }
            return
        }
        val docId = row.docId ?: return
        if (currentDocId != null) {
            stack.addLast(Crumb(currentDocId!!, currentName))
        }
        currentDocId = docId
        currentName = row.title
        loadChildren()
    }

    private fun loadChildren() {
        val tree = currentTree ?: return
        val docId = currentDocId ?: return
        pathLabel.text = if (currentName.isBlank()) "Папки" else "Медиа  /  $currentName"
        emptyHint.visibility = View.GONE

        worker.execute {
            val children = queryChildren(tree, docId)
            host.runOnUiThread {
                if (!isShown) return@runOnUiThread
                if (currentTree != tree || currentDocId != docId) return@runOnUiThread
                val items = mutableListOf<Row>()
                items.add(
                    Row(
                        kind = Kind.BACK,
                        title = "Назад",
                        subtitle = if (stack.isEmpty()) "К списку папок" else "Вверх",
                        meta = ""
                    )
                )
                items.addAll(children)
                submit(items)
                list.focusChildAt(if (children.isEmpty()) 0 else 1)
            }
        }
    }

    private fun queryChildren(tree: Uri, docId: String): List<Row> {
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        } catch (_: Throwable) {
            return emptyList()
        }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE
        )
        val result = mutableListOf<Row>()
        try {
            host.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val id = if (idIndex >= 0) cursor.getString(idIndex) else null ?: continue
                    val name = (if (nameIndex >= 0) cursor.getString(nameIndex) else null)
                        ?.takeIf { it.isNotBlank() } ?: "Без имени"
                    val mime = if (mimeIndex >= 0) cursor.getString(mimeIndex).orEmpty() else ""
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        cursor.getLong(sizeIndex)
                    } else {
                        0L
                    }
                    val uri = try {
                        DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    } catch (_: Throwable) {
                        continue
                    }
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        result.add(
                            Row(
                                kind = Kind.FOLDER,
                                title = name,
                                subtitle = "Папка",
                                meta = "›",
                                uri = uri,
                                docId = id
                            )
                        )
                    } else if (isMedia(name, mime)) {
                        result.add(
                            Row(
                                kind = Kind.FILE,
                                title = name,
                                subtitle = formatSize(size).ifBlank { "Медиа" },
                                meta = "▶",
                                uri = uri,
                                docId = id,
                                mime = mime
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {
            return emptyList()
        }
        val collator = Collator.getInstance(Locale("ru")).apply { strength = Collator.PRIMARY }
        result.sortWith(Comparator { a, b ->
            if (a.kind != b.kind) {
                if (a.kind == Kind.FOLDER) -1 else 1
            } else {
                collator.compare(a.title, b.title)
            }
        })
        return result
    }

    private fun onRowClick(row: Row) {
        when (row.kind) {
            Kind.LIBRARY -> requestLibrary()
            Kind.ACTION -> pickFolder()
            Kind.FOLDER -> openFolder(row)
            Kind.FILE -> play(row)
            Kind.BACK -> {
                if (libraryMode) {
                    showRoots()
                } else if (stack.isEmpty()) {
                    showRoots()
                } else {
                    val previous = stack.removeLast()
                    currentDocId = previous.docId
                    currentName = previous.name
                    loadChildren()
                }
            }
        }
    }

    private fun onRowLongClick(row: Row) {
        if (!row.savedRoot) return
        val uri = row.uri ?: return
        AlertDialog.Builder(host, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Убрать папку?")
            .setMessage(row.title)
            .setPositiveButton("Убрать") { _, _ ->
                try {
                    host.contentResolver.releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Throwable) {
                    // Permission may already be gone.
                }
                prefs.removeMediaFolder(uri.toString())
                showRoots()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        try {
            host.startActivityForResult(intent, REQUEST_PICK_FOLDER)
        } catch (_: Throwable) {
            host.toast("На телевизоре нет системного выбора папок")
        }
    }

    private fun play(row: Row) {
        val files = rows.filter { it.kind == Kind.FILE }
        val index = files.indexOfFirst { it.uri == row.uri }
        if (index < 0) return
        val uris = ArrayList<String>(files.size)
        val titles = ArrayList<String>(files.size)
        for (file in files) {
            uris.add(file.uri?.toString() ?: continue)
            titles.add(file.title)
        }
        if (uris.isEmpty()) return
        host.startActivity(
            Intent(host, PlayerActivity::class.java).apply {
                putStringArrayListExtra(PlayerActivity.EXTRA_URIS, uris)
                putStringArrayListExtra(PlayerActivity.EXTRA_TITLES, titles)
                putExtra(PlayerActivity.EXTRA_START, index)
            }
        )
    }

    private fun submit(items: List<Row>) {
        rows = items
        adapter.submit(items)
    }

    private fun treeName(uri: Uri): String {
        return try {
            DocumentFile.fromTreeUri(host, uri)?.name
                ?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment
                    ?.substringAfterLast(':')
                    ?.takeIf { it.isNotBlank() }
                ?: "Папка"
        } catch (_: Throwable) {
            "Папка"
        }
    }

    private fun isMedia(name: String, mime: String): Boolean {
        if (mime.startsWith("video/") || mime.startsWith("audio/")) return true
        val extension = name.substringAfterLast('.', "").lowercase(Locale.US)
        return extension in MEDIA_EXTENSIONS
    }

    private class MediaAdapter(
        private val host: MainActivity,
        private val prefs: Prefs,
        private val onClick: (Row) -> Unit,
        private val onLongClick: (Row) -> Unit
    ) : RecyclerView.Adapter<MediaAdapter.Holder>() {

        private var items: List<Row> = emptyList()

        fun submit(newItems: List<Row>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media_row, parent, false)
            view.applyFocusScale({ prefs.animations }, 1.0f)
            return Holder(view)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = items[position]
            holder.title.text = row.title
            holder.subtitle.text = row.subtitle
            holder.meta.text = row.meta
            holder.itemView.contentDescription = row.title

            val icon = when (row.kind) {
                Kind.BACK -> R.drawable.ic_back
                Kind.ACTION -> R.drawable.ic_folder
                Kind.LIBRARY -> R.drawable.ic_play
                Kind.FOLDER -> R.drawable.ic_folder
                Kind.FILE -> if (row.mime?.startsWith("audio/") == true) {
                    R.drawable.ic_audio
                } else {
                    R.drawable.ic_play
                }
            }
            holder.icon.setImageResource(icon)
            val tint = when (row.kind) {
                Kind.ACTION -> host.getColor(R.color.accent)
                Kind.LIBRARY -> host.getColor(R.color.accent)
                Kind.FILE -> host.getColor(R.color.text_dim)
                else -> host.getColor(R.color.text_faint)
            }
            holder.icon.setColorFilter(tint)

            holder.itemView.setOnClickListener { onClick(row) }
            holder.itemView.setOnLongClickListener {
                onLongClick(row)
                true
            }
        }

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.icon)
            val title: TextView = view.findViewById(R.id.title)
            val subtitle: TextView = view.findViewById(R.id.subtitle)
            val meta: TextView = view.findViewById(R.id.meta)
        }
    }

    private companion object {
        const val REQUEST_PICK_FOLDER = 4101

        val MEDIA_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "m2ts", "mpg", "mpeg",
            "wmv", "flv", "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "mka"
        )

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "mka"
        )
    }
}
