package ru.jumperbreaker.photoreport
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.icu.text.DecimalFormat
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.os.Build
import android.view.Window
import android.view.WindowManager
import android.graphics.Color

class MainActivity : AppCompatActivity() {

    //Переменные класса
    private var preferences: String = "preferences"
    private var tempPath: String = ""
    private var tempFolder: String = ""
    private var rootPath: String = ""
    private var currentPath: String = ""
    private var sortType  : String = "По имени"
    private var sortOrder  : String = "Возрастание"
    private lateinit var listView: ListView
    private val pathStack: MutableList<String> = mutableListOf()
    private lateinit var currentPathTextView: TextView
    private lateinit var tempImageFile: File
    private lateinit var tempZipFile: File
    private var addPhotoQuality : Int = 50
    private lateinit var moveItem: File
    private lateinit var moveButtons: LinearLayout
    private lateinit var selectedCheckboxesQueryActionButtons: View
    private lateinit var selectedCheckboxesAcceptActionButtons: View
    private lateinit var selectedCheckBoxItems : Set<Int>
    private var checkAbsolutePaths = mutableListOf<String>()
    private var checkMoveAbsolutePaths = mutableListOf<String>()
    private lateinit var adapter: mainArrayAdapter
    private lateinit var mainMenu: View

    @SuppressLint("MissingInflatedId", "QueryPermissionsNeeded")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Проверка разрешений
        if (checkPermission()) {
            startApp()
        } else {
            requestPermission()
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1
        private const val REQUEST_IMAGE_CAPTURE = 2
        private const val REQUEST_SEND_FOLDER = 3
    }

    //Настройка доступа
    private fun checkPermission(): Boolean {

        if (Build.VERSION.SDK_INT >= 33) {
            return (checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }

        if (Build.VERSION.SDK_INT <= 32) {
            return (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
        }
        return false
    }

    private fun requestPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(
                arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.READ_MEDIA_IMAGES), PERMISSION_REQUEST_CODE
            )
        }

        if (Build.VERSION.SDK_INT <= 32) {
            requestPermissions(
                arrayOf(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ), PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startApp()
            } else {
                finishAffinity()
            }
        }
    }

    //Начать работу
    private fun startApp() {

        setContentView(R.layout.activity_main)

        // Изменяем цвет верхней панели (status bar) на синий
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window: Window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.setStatusBarColor(Color.parseColor("#005597"))
        }

        //Настройка системных директорий
        tempPath = application.cacheDir.absolutePath
        rootPath = initSysDir("photoreport")

        //Настройка системных файлов
        tempImageFile = File(tempPath, "tempImageFile.jpg")
        tempZipFile = File(tempPath, "Фотоотчет.zip")

        //Настройка макета
        listView = findViewById(R.id.listView)
        currentPathTextView = findViewById(R.id.currentPathTextView)
        moveButtons = findViewById(R.id.moveButtons)
        selectedCheckboxesQueryActionButtons = findViewById<View>(R.id.selectedCheckboxesQueryActionButtons)
        selectedCheckboxesAcceptActionButtons = findViewById<View>(R.id.selectedCheckboxesAcceptActionButtons)
        mainMenu = findViewById<View>(R.id.mainMenu)

        //Настройка начального пути
        val savedCurrentPath = getCurrentPathFromSharedPreferences(this)
        if (savedCurrentPath.isEmpty()) {
            currentPath = rootPath
        } else {
            currentPath = savedCurrentPath
        }
        pathStack.add(rootPath)

        //Настройка качества фото
        val savedAddPhotoQuality = getAddPhotoQualityFromSharedPreferences(this)
        if (savedAddPhotoQuality.isEmpty()) {
            saveAddPhotoQualityToSharedPreferences(this, addPhotoQuality)
        } else {
            addPhotoQuality  = savedAddPhotoQuality.toInt()
        }

        //Настройка адаптера
        adapter = mainArrayAdapter(this,this)

        //Кнопка - назад
        val backButton: ImageButton = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            onBackPressed()
        }

        //Кнопка - добавить папку
        val addFolderButton: ImageButton = findViewById(R.id.addFolderButton)
        addFolderButton.setOnClickListener {
            showAddFolderDialog()
        }

        //Кнопка - добавить фото
        val addPhotoButton: ImageButton = findViewById(R.id.addPhotoButton)
        addPhotoButton.setOnClickListener {
            startAddPhoto()
        }

        //Кнопка - сортировка
        val sortButton: ImageButton = findViewById(R.id.sortButton)
        sortButton.setOnClickListener {
            val sortOptions = arrayOf("По имени", "По дате")
            val builder = AlertDialog.Builder(this)

            builder.setTitle("Выберите сортировку")
                .setSingleChoiceItems(sortOptions, sortOptions.indexOf(sortType)) { dialog, which ->
                    sortType = sortOptions[which]
                }
                .setNegativeButton("Возрастание") { dialog, _ ->
                    sortOrder = "Возрастание"
                }
                .setPositiveButton("Убывание") { dialog, _ ->
                    sortOrder = "Убывание"
                }
                .setOnDismissListener {
                    displayFiles(sortType, sortOrder)
                }

            val dialog = builder.create()
            dialog.show()
        }

        //кнопка - поиск
        val searchButton: ImageButton = findViewById(R.id.searchButton)
        searchButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Фильтр")
            val inflater = layoutInflater
            val dialogView = inflater.inflate(R.layout.search_dialog, null)
            builder.setView(dialogView)

            val searchEditText: EditText = dialogView.findViewById(R.id.searchEditText)

            builder.setNegativeButton("Отмена") { dialog, which ->
                dialog.dismiss()
            }

            builder.setPositiveButton("OK") { dialog, which ->
                val searchTerm = searchEditText.text.toString().trim()

                if (searchTerm.isNotEmpty()) {
                    displayFiles(sortType, sortOrder, searchTerm)
                } else {
                    displayFiles(sortType, sortOrder)
                }
            }
            val dialog = builder.create()
            dialog.show()
        }

        //Кнопка - настройки
        val settingsButton: ImageButton = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            showSettingsDialog()
        }

        //Переходы по папкам и открытие фото
        listView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val selectedFile = File(currentPath, listView.adapter.getItem(position).toString())
            if (selectedFile.isDirectory) {
                pathStack.add(currentPath)
                currentPath = selectedFile.absolutePath
                saveCurrentPathToSharedPreferences(this, currentPath)
                displayFiles(sortType, sortOrder)
                displayCurrentPath()
            } else {
                openImage(selectedFile.absolutePath)
            }
        }

        //Контекстное меню
        listView.setOnItemLongClickListener { _, _, position, _ ->
            selectedCheckboxesQueryActionButtons.visibility = View.GONE
            selectedCheckboxesAcceptActionButtons.visibility = View.GONE

            adapter.clearSelectedItems()

            val selectedFile = File(currentPath, listView.adapter.getItem(position).toString())
            if (selectedFile.isDirectory) {
                showFolderActionsDialog(selectedFile)
            }
            if (selectedFile.isFile) {
                showFileActionsDialog(selectedFile)
            }
            true
        }

        //Кнопка - отменить перемещение элемента
        val cancelMoveButton: Button = findViewById(R.id.cancelMoveButton)
        cancelMoveButton.setOnClickListener {
            moveButtons = findViewById(R.id.moveButtons)
            moveButtons.visibility = View.GONE
        }

        //Кнопка - применить перемещение элемента
        val applayMoveButton: Button = findViewById(R.id.applayMoveButton)
        applayMoveButton.setOnClickListener {
            applayMove()
        }

        //Кнопка - отменить перемещение выбранного
        val selectedCheckboxesCancelButton: Button = findViewById(R.id.selectedCheckboxesCancelButton)
        selectedCheckboxesCancelButton.setOnClickListener {
            moveButtons = findViewById(R.id.moveButtons)
            selectedCheckboxesAcceptActionButtons.visibility = View.GONE
            displayFiles(sortType, sortOrder)
        }

        //Кнопка - применить перемещение выбранного
        val selectedCheckboxesPasteButton: Button = findViewById(R.id.selectedCheckboxesPasteButton)
        selectedCheckboxesPasteButton.setOnClickListener {
            applayMoveItems()
        }

        //Кнопка - запросить перемещение выбранного
        val selectedCheckboxesMoveButton: Button = findViewById(R.id.selectedCheckboxesMoveButton)
        selectedCheckboxesMoveButton.setOnClickListener {
            selectedCheckboxesQueryActionButtons.visibility = View.GONE
            selectedCheckboxesAcceptActionButtons.visibility = View.VISIBLE

            mainMenu.visibility = View.GONE

            checkMoveAbsolutePaths = checkAbsolutePaths

            val toast = Toast.makeText(this, "Выберите папку назначения", Toast.LENGTH_LONG)
            toast.setGravity(
                Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL,
                0,
                0
            )
            toast.show()
        }

        //Кнопка - удалить выбранное
        val selectedCheckboxesRemoveButton: Button = findViewById(R.id.selectedCheckboxesRemoveButton)
        selectedCheckboxesRemoveButton.setOnClickListener {
            selectedCheckboxesRemoveButtonClicked()
        }

        //Кнопка - закрыть меню выбранных элементов
        val selectedCheckboxesClearButton: Button = findViewById(R.id.selectedCheckboxesClearButton)
        selectedCheckboxesClearButton.setOnClickListener {
            displayFiles(sortType, sortOrder)

        }

        //Показать папки и файлы
        displayCurrentPath()
        displayFiles(sortType, sortOrder)
    }

    fun selectedCheckboxesRemoveButtonClicked() {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Удалить?")
        alertDialog.setMessage("Вы действительно хотите удалить выбранные элементы?")
        alertDialog.setPositiveButton("Удалить") { dialog, _ ->

            for (position in selectedCheckBoxItems) {
                val itemPath = checkAbsolutePaths[position]
                val itemFile = File(itemPath)

                deleteDirectory(itemFile)
            }
            displayFiles(sortType, sortOrder)
        }
        alertDialog.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
            displayFiles(sortType, sortOrder)
        }
        alertDialog.show()
    }

    fun deleteDirectory(directory: File) {
        if (directory.isDirectory) {
            val children = directory.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteDirectory(child)
                }
            }
        }
        directory.delete()
    }

    private fun showFolderActionsDialog(selectedFolder: File) {
        val isFolderEmpty = selectedFolder.listFiles()?.isNotEmpty() != true

        val actions = if (isFolderEmpty) {
            arrayOf("Переименовать", "Переместить", "Удалить")
        } else {
            arrayOf("Переименовать", "Переместить",  "Удалить", "Отправить")
        }

        val icons = if (isFolderEmpty) {
            arrayOf(
                R.drawable.rename_icon, R.drawable.move_icon, R.drawable.delete_icon
            )
        } else {
            arrayOf(
                R.drawable.rename_icon, R.drawable.move_icon, R.drawable.delete_icon, R.drawable.send_icon
            )
        }
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(selectedFolder.name)

        val adapter = object : ArrayAdapter<String>(
            this, R.layout.action_item_layout, R.id.actionTextView, actions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val actionIconImageView = view.findViewById<ImageView>(R.id.actionIconImageView)
                actionIconImageView.setImageResource(icons[position])
                return view
            }
        }

        var dialog: AlertDialog? = null

        alertDialog.setAdapter(adapter) { _, which ->
            when (which) {
                0 -> showRenameFolderDialog(selectedFolder)
                1 -> showMoveDialog(selectedFolder)
                2 -> showDeleteFolderDialog(selectedFolder)
                3 -> showSendFolderDialog(selectedFolder)
            }
            dialog?.dismiss()
        }

        dialog = alertDialog.create()
        dialog.show()

        val listView = dialog.listView
        listView.adapter = adapter
    }

    private fun showRenameFolderDialog(selectedFolder: File) {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Переименовать папку")
        val input = EditText(this)
        alertDialog.setView(input)
        input.setText(selectedFolder.name)
        alertDialog.setPositiveButton("Переименовать") { dialog, _ ->
            val newFolderName = input.text.toString()
            if (newFolderName.isNotEmpty() && newFolderName != selectedFolder.name) {
                val newFolderPath = "${selectedFolder.parent}/$newFolderName"
                val newFolder = File(newFolderPath)
                if (!newFolder.exists()) {
                    selectedFolder.renameTo(newFolder)
                    displayFiles(sortType, sortOrder)
                } else {
                    Toast.makeText(this, "Ошибка! Невозможно переименовать, такое имя папки уже существует.", Toast.LENGTH_LONG).show()
                }
            }
            dialog.dismiss()
        }
        alertDialog.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    private fun showMoveDialog(selectedItem: File) {
        moveButtons.visibility = View.VISIBLE
        moveItem = selectedItem

        val toast = Toast.makeText(this, "Выберите папку назначения", Toast.LENGTH_LONG)
        toast.setGravity(
            Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL,
            0,
            0
        )
        toast.show()
    }

    private fun applayMove() {
        val targetFile = File(currentPath, moveItem.name)

        if (targetFile.exists()) {
            Toast.makeText(this, "Ошибка! Такое имя файла или папки уже существует в папке назначения", Toast.LENGTH_LONG).show()
            return
        }

        moveButtons.visibility = View.GONE

        moveItem.renameTo(targetFile)
        displayFiles(sortType, sortOrder)
    }

    private fun applayMoveItems() {
        val targetFolder = File(currentPath)

        for (position in selectedCheckBoxItems) {
            val itemPath = checkMoveAbsolutePaths[position]
            val itemFile = File(itemPath)
            val targetFile = File(targetFolder, itemFile.name)

            if (!targetFile.exists()) {
                itemFile.renameTo(targetFile)
            } else {
                Toast.makeText(this, "Файл или папка уже существует", Toast.LENGTH_SHORT).show()
            }
        }
        selectedCheckboxesAcceptActionButtons.visibility = View.GONE
        displayFiles(sortType, sortOrder)
    }

    private fun showDeleteFolderDialog(selectedFolder: File) {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Удалить папку?")
        alertDialog.setMessage("Вы действительно хотите удалить эту папку и все ее содержимое?")
        alertDialog.setPositiveButton("Удалить") { dialog, _ ->
            selectedFolder.deleteRecursively()
            displayFiles(sortType, sortOrder)
            dialog.dismiss()
        }
        alertDialog.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    private fun showSendFolderDialog(selectedFolder: File) {
        val qualityOptions = arrayOf("Низкое (20%)", "Среднее (50%)", "Высокое (100%)")
        var selectedQuality = 1    // Изменить значение на 0, 1 или 2 для выбора по умолчанию
        val builder = AlertDialog.Builder(this)

        builder.setTitle("Выберите качество изображений")
            .setSingleChoiceItems(qualityOptions, selectedQuality) { dialog, which ->
                selectedQuality = when (which) {
                    0 -> 20
                    1 -> 50
                    2 -> 100
                    else -> 0
                }
            }
            .setPositiveButton("OK") { dialog, which ->
                val dialogBuilder = AlertDialog.Builder(this)
                dialogBuilder.setMessage("Создание архива. Ждите...")
                dialogBuilder.setCancelable(false)

                val alertDialog = dialogBuilder.create()
                alertDialog.show()
                Thread {
                    val quality = when (selectedQuality) {
                        0 -> 20
                        1 -> 50
                        2 -> 100
                        else -> 0
                    }
                    sendFolder(selectedFolder, quality)
                    runOnUiThread { alertDialog.dismiss() }
                }.start()
            }
            .setNegativeButton("Отмена") { dialog, which -> dialog.dismiss() }

        val dialog = builder.create()
        dialog.show()
    }

    @SuppressLint("WrongConstant")
    private fun sendFolder(selectedFolder: File, quality: Int) {

        copyFolder(selectedFolder, tempPath)
        tempFolder = tempPath+"/"+selectedFolder.name
        createZipFile(File(tempFolder), quality)

        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.type = "application/zip"
        val fileUri = FileProvider.getUriForFile(this, "ru.jumperbreaker.photoreport.provider", tempZipFile)

        sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivityForResult(Intent.createChooser(sendIntent, "Отправить папку"), REQUEST_SEND_FOLDER)
    }

    private fun copyFolder(selectedFolder: File, tempPath: String) {
        val tempFolder = File(tempPath, selectedFolder.name)
        tempFolder.mkdirs()

        selectedFolder.listFiles()?.forEach { file ->
            val tempFile = File(tempFolder, file.name)
            val fis: FileInputStream
            val fos: FileOutputStream

            if (file.isDirectory) {
                copyFolder(file, tempFile.absolutePath)
                return@forEach
            }

            fis = FileInputStream(file)
            fos = FileOutputStream(tempFile)

            val buffer = ByteArray(1024)
            var length: Int
            while (fis.read(buffer).also { length = it } > 0) {
                fos.write(buffer, 0, length)
            }
            fos.close()
            fis.close()
        }
    }

    private fun createZipFile(selectedFolder: File, quality: Int) {

        val cacheDir = this.cacheDir
        val tempFile = File(cacheDir, "Фотоотчет.zip")

        val fos = FileOutputStream(tempFile)
        val zos = ZipOutputStream(BufferedOutputStream(fos))
        zipSubFolder(zos, selectedFolder, selectedFolder.name, quality)
        zos.close()
        fos.close()
    }

    private fun zipSubFolder(zos: ZipOutputStream, folder: File, basePath: String, quality: Int) {
        folder.listFiles()?.forEach { file ->
            val entryPath = basePath + "/" + file.name
            if (file.isDirectory) {
                zipSubFolder(zos, file, entryPath, quality)
            } else {
                if (file.name.toLowerCase().endsWith(".jpg")) {
                    val compressedFile = compressImage(file,quality)

                    val buffer = ByteArray(1024)
                    val fis = FileInputStream(compressedFile)

                    val zipEntry = ZipEntry(entryPath)
                    zos.putNextEntry(zipEntry)

                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }

                    fis.close()
                    zos.closeEntry()

                } else {
                    val buffer = ByteArray(1024)
                    val fis = FileInputStream(file)
                    val zipEntry = ZipEntry(entryPath)
                    zos.putNextEntry(zipEntry)

                    var len: Int
                    while (fis.read(buffer).also { len = it } > 0) {
                        zos.write(buffer, 0, len)
                    }

                    fis.close()
                    zos.closeEntry()
                }
            }
        }
    }

    private fun showFileActionsDialog(selectedFile: File) {
        val actions = arrayOf("Переименовать", "Переместить", "Удалить", "Отправить")
        val icons = arrayOf(
            R.drawable.rename_icon,
            R.drawable.move_icon,
            R.drawable.delete_icon,
            R.drawable.send_icon
        )

        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(selectedFile.name)

        val adapter = object : ArrayAdapter<String>(
            this, R.layout.action_item_layout, R.id.actionTextView, actions
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                val actionIconImageView = view.findViewById<ImageView>(R.id.actionIconImageView)
                actionIconImageView.setImageResource(icons[position])
                return view
            }
        }

        var dialog: AlertDialog? = null

        alertDialog.setAdapter(adapter) { _, which ->
            when (which) {
                0 -> showRenameFileDialog(selectedFile)
                1 -> showMoveDialog(selectedFile)
                2 -> showDeleteFileDialog(selectedFile)
                3 -> showSendFileDialog(selectedFile)
            }
            dialog?.dismiss()
        }

        dialog = alertDialog.create()
        dialog.show()

        val listView = dialog.listView
        listView.adapter = adapter
    }

    private fun showRenameFileDialog(selectedFile: File) {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Переименовать фото")
        val input = EditText(this)
        alertDialog.setView(input)
        val fileNameWithoutExtension = selectedFile.name.substringBeforeLast(".")
        input.setText(fileNameWithoutExtension)
        alertDialog.setPositiveButton("Переименовать") { dialog, _ ->
            val newFileName = input.text.toString()+".jpg"
            if (newFileName.isNotEmpty() && newFileName != selectedFile.name) {
                val newFolderPath = "${selectedFile.parent}/$newFileName"
                val newFile = File(newFolderPath)
                if (!newFile.exists()) {
                    selectedFile.renameTo(newFile)
                    displayFiles(sortType, sortOrder)
                } else {
                    Toast.makeText(this, "Невозможно переименовать, такое имя файла уже существует", Toast.LENGTH_LONG).show()
                }
            }
            dialog.dismiss()
        }
        alertDialog.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    private fun showSendFileDialog(selectedFile: File) {
        val sendIntent = Intent(Intent.ACTION_SEND)
        sendIntent.type = "application/jpg"

        val fileUri = FileProvider.getUriForFile(this, "ru.jumperbreaker.photoreport.provider", selectedFile)

        sendIntent.putExtra(Intent.EXTRA_STREAM, fileUri)
        sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(Intent.createChooser(sendIntent, "Отправить файл"))
    }

    private fun showDeleteFileDialog(selectedFile: File) {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Удалить фото?")
        alertDialog.setMessage("Вы действительно хотите удалить это фото?")
        alertDialog.setPositiveButton("Удалить") { dialog, _ ->
            Thread {
                selectedFile.deleteRecursively()
                runOnUiThread {
                    displayFiles(sortType, sortOrder)
                    dialog.dismiss()
                }
            }.start()
        }
        alertDialog.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    //Функция для записи значения currentPath в SharedPreferences
    private fun saveCurrentPathToSharedPreferences(context: Context, currentPath: String) {
        val sharedPref = context.getSharedPreferences(preferences, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString("currentPath", currentPath)
        editor.apply()
    }

    //Функция для чтения значения currentPath из SharedPreferences
    private fun getCurrentPathFromSharedPreferences(context: Context): String {
        val sharedPref = context.getSharedPreferences(preferences, Context.MODE_PRIVATE)
        return sharedPref.getString("currentPath", "") ?: ""
    }

    //Функция для записи значения addPhotoQuality в SharedPreferences
    private fun saveAddPhotoQualityToSharedPreferences(context: Context, AddPhotoQuality : Int) {
        val sharedPref = context.getSharedPreferences(preferences, Context.MODE_PRIVATE)
        val editor = sharedPref.edit()
        editor.putString("AddPhotoQuality", AddPhotoQuality.toString())
        editor.apply()
    }

    //Функция для чтения значения addPhotoQuality из SharedPreferences
    private fun getAddPhotoQualityFromSharedPreferences(context: Context): String {
        val sharedPref = context.getSharedPreferences(preferences, Context.MODE_PRIVATE)
        return sharedPref.getString("AddPhotoQuality", "") ?: ""
    }

    //Удаление временных файлов
    private fun clearCache() {
        val cacheDir = this.cacheDir
        val files = cacheDir.listFiles()
        if (files != null) {
            for (file in files) {
                file.delete()
            }
        }
    }

    //Системные директории
    private fun initSysDir(folderName: String): String {
        val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), folderName)
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder.absolutePath
    }

    //Добавить папку
    private fun showAddFolderDialog() {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle("Добавить папку")
        val input = EditText(this)
        alertDialog.setView(input)

        alertDialog.setPositiveButton("Добавить") { dialog, _ ->
            val folderName = input.text.toString()

            if (folderName.isNotEmpty()) {
                val newFolderPath = "$currentPath/$folderName"
                val newFolder = File(newFolderPath)

                if (newFolder.exists()) {
                    Toast.makeText(this, "Ошибка! Такое имя папки уже существует.", Toast.LENGTH_LONG).show()
                } else {
                    newFolder.mkdirs()
                        displayFiles(sortType, sortOrder)
                }
            }
            dialog.dismiss()
        }
        alertDialog.setNegativeButton("Отмена") { dialog, _ ->
            dialog.dismiss()
        }
        alertDialog.show()
    }

    //Добавить фото
    private fun startAddPhoto() {
        clearCache()
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val photoUri = FileProvider.getUriForFile(this, "ru.jumperbreaker.photoreport.provider", tempImageFile)
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
        startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
    }

    //Сжатие фото
    private fun compressImage(file: File, quality: Int): File {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, baos)

        val output = File(file.parent, file.name)
        output.createNewFile()

        val fos: FileOutputStream = FileOutputStream(output)
        fos.write(baos.toByteArray())

        fos.flush()
        fos.close()
        return output
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == Activity.RESULT_OK) {
            val dialogBuilder = AlertDialog.Builder(this)
            val input = EditText(this)

            dialogBuilder.setTitle("Введите имя изображения")
            dialogBuilder.setView(input)

            dialogBuilder.setPositiveButton("ОК") { dialog, _ ->
                val newFileName = input.text.toString()
                if (newFileName.isNotEmpty()) {
                    val newFilePath = "$currentPath/$newFileName.jpg"
                    val newFile = File(newFilePath)
                    if (newFile.exists()) {
                        Toast.makeText(this, "Ошибка! Такое имя файла уже существует в этой папке.", Toast.LENGTH_LONG).show()
                    } else {
                        tempImageFile = compressImage(tempImageFile, addPhotoQuality)
                        tempImageFile.copyTo(newFile, overwrite = true)
                        displayFiles(sortType, sortOrder)
                    }
                }
                dialog.dismiss()
            }

            dialogBuilder.setNegativeButton("Отмена") { dialog, _ ->
                dialog.dismiss()
            }
            dialogBuilder.show()
        }

        if (requestCode == REQUEST_SEND_FOLDER && resultCode == Activity.RESULT_OK) {
            clearCache()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)

        val mainLayout = LinearLayout(this)
        mainLayout.orientation = LinearLayout.VERTICAL

        val infoTextView = TextView(this)
        infoTextView.text = "Приложение «Фотоотчет»\nВерсия: 1.2\nРазработчик: Александр Димитриев \nДата сборки: 25.06.2024"
        val layoutParamsInfo = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParamsInfo.setMargins(30, 30, 0, 0)
        infoTextView.layoutParams = layoutParamsInfo
        infoTextView.textSize = 12f // Установите желаемый размер шрифта
        mainLayout.addView(infoTextView)

        val labelTextView = TextView(this)
        labelTextView.text = "Качество новых фото"
        val layoutParamsLabel = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParamsLabel.setMargins(30, 30, 0, 0)
        labelTextView.layoutParams = layoutParamsLabel
        mainLayout.addView(labelTextView)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.HORIZONTAL

        val seekBar = SeekBar(this)
        seekBar.min = 20
        seekBar.max = 100
        seekBar.progress = addPhotoQuality

        val layoutParamsSeekBar = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layoutParamsSeekBar.weight = 1f
        layout.addView(seekBar, layoutParamsSeekBar)

        val textView = TextView(this)
        textView.text = "${seekBar.progress}%"

        val layoutParamsTextView = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        layout.addView(textView, layoutParamsTextView)
        layoutParamsTextView.setMargins(0, 0, 30, 0)

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                textView.text = "$progress%"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        mainLayout.addView(layout)

        builder.setPositiveButton("OK") { dialog, which ->

            val progressAddPhotoQuality = seekBar.progress
            saveAddPhotoQualityToSharedPreferences(this, progressAddPhotoQuality)
            addPhotoQuality = progressAddPhotoQuality
        }

        builder.setNegativeButton("Отмена") { dialog, which ->
            dialog.cancel()
        }

        builder.setTitle("Настройки")
        builder.setView(mainLayout)
        val dialog = builder.create()
        dialog.show()
    }

    //Текущий путь
    private fun displayCurrentPath() {
        currentPathTextView.text = getphotoreportContent(currentPath, rootPath)
    }

    private fun getphotoreportContent(currentPath: String, rootPath: String): String {
        val index = currentPath.indexOf(rootPath)

        return if (index != -1 && index + rootPath.length < currentPath.length) {
            "\u2302/" + currentPath.substring(index + rootPath.length + 1)
        } else {
            "\u2302/"
        }
    }

    //Вывод папок и файлов
    private fun displayFiles(sortType: String, sortOrder: String, searchTerm: String = "") {
        clearCache()

        if (selectedCheckboxesAcceptActionButtons.visibility != View.VISIBLE)
            mainMenu.visibility = View.VISIBLE

        selectedCheckboxesQueryActionButtons.visibility = View.GONE

        val file = File(currentPath)
        val files = file.listFiles()
        val absolutePaths = mutableListOf<String>()
        val directories = mutableListOf<File>()
        val regularFiles = mutableListOf<File>()

        files?.forEach {
            if (it.isDirectory) {
                directories.add(it)
            } else {
                regularFiles.add(it)
            }
        }

        val filteredDirectories: List<File>?
        val filteredFiles: List<File>?

        if (searchTerm.isNotEmpty()) {
            filteredDirectories = directories.filter { it.name.contains(searchTerm, ignoreCase = true) }
            filteredFiles = regularFiles.filter { it.name.contains(searchTerm, ignoreCase = true) }
        } else {
            filteredDirectories = directories
            filteredFiles = regularFiles
        }

        val sortedDirectories: List<File>?
        val sortedFiles: List<File>?

        if (sortType == "По имени") {
            if (sortOrder == "Возрастание") {
                sortedDirectories = filteredDirectories.sortedBy { it.name }
                sortedFiles = filteredFiles.sortedBy { it.name }
            } else {
                sortedDirectories = filteredDirectories.sortedByDescending { it.name }
                sortedFiles = filteredFiles.sortedByDescending { it.name }
            }
        } else {
            if (sortOrder == "Возрастание") {
                sortedDirectories = filteredDirectories.sortedBy { it.lastModified() }
                sortedFiles = filteredFiles.sortedBy { it.lastModified() }
            } else {
                sortedDirectories = filteredDirectories.sortedByDescending { it.lastModified() }
                sortedFiles = filteredFiles.sortedByDescending { it.lastModified() }
            }
        }

        sortedDirectories?.forEach { directory ->
            absolutePaths.add(directory.absolutePath)
            checkAbsolutePaths = absolutePaths
        }

        sortedFiles?.forEach { regularFile ->
            absolutePaths.add(regularFile.absolutePath)
            checkAbsolutePaths = absolutePaths
        }


        val items = mutableListOf<String>()
        val icons = mutableListOf<Int>()
        val fileSizes = mutableListOf<Long>()
        val folderCounts = mutableListOf<Int>()
        val fileCounts = mutableListOf<Int>()
        val creationDates = mutableListOf<String>()

        sortedDirectories.forEach { directory ->
            items.add(directory.name)
            icons.add(R.drawable.ic_folder)
            fileSizes.add(directory.length())

            val listOfFiles = directory.listFiles()
            if (listOfFiles != null) {
                folderCounts.add(listOfFiles.count { it.isDirectory })
            }
            if (listOfFiles != null) {
                fileCounts.add(listOfFiles.count { it.isFile })
            }
            creationDates.add(
                SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault()).format(directory.lastModified())
            )
        }

        sortedFiles.forEach { regularFile ->
            items.add(regularFile.name)
            icons.add(R.drawable.ic_image)
            fileSizes.add(regularFile.length())
            folderCounts.add(0)
            fileCounts.add(0)
            creationDates.add(
                SimpleDateFormat("dd.MM.yyyy - HH:mm:ss", Locale.getDefault()).format(regularFile.lastModified())
            )
        }

        adapter = mainArrayAdapter(
            this,
            this,
            R.layout.list_item,
            items,
            absolutePaths,
            icons,
            fileSizes,
            folderCounts,
            fileCounts,
            creationDates
        )
        listView.adapter = adapter
    }

    fun onCheckboxClicked(context: Context, selectedItems: Set<Int>) {
        if (selectedCheckboxesAcceptActionButtons.visibility == View.VISIBLE) {
            selectedCheckboxesAcceptActionButtons.visibility = View.GONE
        }

        selectedCheckBoxItems = selectedItems
        if (selectedItems.isNotEmpty()) {
            selectedCheckboxesQueryActionButtons.visibility = View.VISIBLE
            mainMenu.visibility = View.GONE
        } else {
            selectedCheckboxesQueryActionButtons.visibility = View.GONE
            mainMenu.visibility = View.VISIBLE
        }
    }

    //Основной адаптер
    class mainArrayAdapter(
        private val activity: MainActivity,
        private val context: Context,
        private val resource: Int = 0,
        private val items: List<String> = emptyList(),
        private val absolutePaths: List<String> = emptyList(),
        private val icons: List<Int> = emptyList(),
        private val fileSizes: List<Long> = emptyList(),
        private val folderCounts: List<Int> = emptyList(),
        private val fileCounts: List<Int> = emptyList(),
        private val creationDates: List<String> = emptyList()
    ) : ArrayAdapter<String>(context, resource, items) {
        private val selectedCheckboxItems = HashSet<Int>()

        @SuppressLint("SetTextI18n")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            var view = convertView
            if (view == null) {
                val inflater = LayoutInflater.from(context)
                view = inflater.inflate(resource, null)
            }

            val folderIcon = view?.findViewById<ImageView>(R.id.folderIcon)
            val imagePreview = view?.findViewById<ImageView>(R.id.imagePreview)
            val itemName = view?.findViewById<TextView>(R.id.itemName)
            val fileInfo = view?.findViewById<TextView>(R.id.fileInfo)
            val folderInfo = view?.findViewById<TextView>(R.id.folderInfo)
            val checkBox = view?.findViewById<CheckBox>(R.id.checkBox)

            itemName?.text = items[position]

            if (icons[position] == R.drawable.ic_folder) {
                folderIcon?.visibility = View.VISIBLE
                imagePreview?.visibility = View.GONE
                folderInfo?.visibility = View.VISIBLE
                fileInfo?.visibility = View.GONE
                folderInfo?.text = "${creationDates[position]} | Папок: ${folderCounts[position]} | Файлов: ${fileCounts[position]}"
            } else if (icons[position] == R.drawable.ic_image) {
                folderIcon?.visibility = View.GONE
                imagePreview?.visibility = View.VISIBLE
                folderInfo?.visibility = View.GONE
                fileInfo?.visibility = View.VISIBLE
                fileInfo?.text = "${creationDates[position]} | ${getFileSizeString(fileSizes[position])}"
                val imagePath = absolutePaths[position]
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(imagePath, options)
                val imageWidth = options.outWidth
                val imageHeight = options.outHeight
                val scaleFactor = (imageWidth.toFloat() / 100).coerceAtMost(imageHeight.toFloat() / 100)
                options.inJustDecodeBounds = false
                options.inSampleSize = scaleFactor.toInt()
                val bitmap = BitmapFactory.decodeFile(imagePath, options)
                if (bitmap != null) {
                    imagePreview?.setImageBitmap(bitmap)
                }
            }

            checkBox?.isChecked = selectedCheckboxItems.contains(position)
            checkBox?.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedCheckboxItems.add(position)
                    activity.onCheckboxClicked(context, selectedCheckboxItems)

                } else {
                    selectedCheckboxItems.remove(position)
                    activity.onCheckboxClicked(context, selectedCheckboxItems)
                }

            }
            return view!!
        }

        override fun getItem(position: Int): String {
            return items[position]
        }

        override fun getCount(): Int {
            return items.size
        }

        override fun getViewTypeCount(): Int {
            return 2
        }

        override fun getItemViewType(position: Int): Int {
            if (icons[position] == R.drawable.ic_folder) {
                return 0
            }
            return 1
        }

        fun clearSelectedItems() {
            selectedCheckboxItems.clear()
            notifyDataSetChanged()
        }

        private fun getFileSizeString(fileSizeInBytes: Long): String {
            val decimalFormat = DecimalFormat("0.00")
            val kilobyte = 1024
            val megabyte = kilobyte * 1024
            val gigabyte = megabyte * 1024
            return when {
                fileSizeInBytes >= gigabyte -> {
                    val fileSizeInGB = fileSizeInBytes.toDouble() / gigabyte
                    "${decimalFormat.format(fileSizeInGB)} GB"
                }
                fileSizeInBytes >= megabyte -> {
                    val fileSizeInMB = fileSizeInBytes.toDouble() / megabyte
                    "${decimalFormat.format(fileSizeInMB)} MB"
                }
                fileSizeInBytes >= kilobyte -> {
                    val fileSizeInKB = fileSizeInBytes.toDouble() / kilobyte
                    "${decimalFormat.format(fileSizeInKB)} KB"
                }
                else -> {
                    "$fileSizeInBytes bytes"
                }
            }
        }
    }

    //Показать изображение
    private fun openImage(imagePath: String) {
        val originalFile = File(imagePath)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
        val timestamp = dateFormat.format(Date(originalFile.lastModified()))

        val tempFileName = "($timestamp)-${originalFile.name}"
        val tempFile = File(cacheDir, tempFileName)

        originalFile.inputStream().use { input ->
            tempFile.outputStream().use { fileOut ->
                input.copyTo(fileOut)
            }
        }

        val uri = FileProvider.getUriForFile(this, "ru.jumperbreaker.photoreport.provider", tempFile)

        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "image/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    //Переход назад
    override fun onBackPressed() {
        if (pathStack.isNotEmpty()) {
            currentPath = pathStack.removeAt(pathStack.size - 1)
            saveCurrentPathToSharedPreferences(this, currentPath)
            displayFiles(sortType, sortOrder)
            displayCurrentPath()

        } else {
            finishAffinity()
        }
    }
}