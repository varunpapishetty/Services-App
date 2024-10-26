package com.example.servicesapp

import android.Manifest
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.servicesapp.ui.theme.ServicesAppTheme
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification Permission Granted!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification Permission Denied!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        setContent {
            ServicesAppTheme {
                PdfDownloadScreen(this)
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "DOWNLOAD_CHANNEL",
                "PDF Download Status",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notification for PDF download status"
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    Toast.makeText(this, "Notification Permission Granted!", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PdfDownloadScreen(context: Context) {
    var numOfFields by remember { mutableStateOf("") }
    var inputUrls = remember { mutableStateListOf<TextFieldValue>() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var message by remember { mutableStateOf("") }
    val validationErrors = remember { mutableStateListOf<String?>() }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2A5470))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PDF Download Activity",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Please provide the number of PDFs you want to download",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        TextField(
            value = numOfFields,
            onValueChange = { newValue ->
                if (newValue.all { it.isDigit() }) {
                    numOfFields = newValue
                    val numberOfFields = newValue.toIntOrNull() ?: 0
                    inputUrls.clear()
                    validationErrors.clear()
                    repeat(numberOfFields) {
                        inputUrls.add(TextFieldValue(""))
                        validationErrors.add(null)
                    }
                    message = if (numberOfFields > 0) {
                        "Awesome! Go ahead and enter $numberOfFields URLs below"
                    } else {
                        ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Number of PDFs") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        inputUrls.forEachIndexed { index, textFieldValue ->
            Column {
                TextField(
                    value = textFieldValue,
                    onValueChange = {
                        inputUrls[index] = it
                    },
                    label = { Text("PDF${index + 1} File Location") },
                    modifier = Modifier.fillMaxWidth()
                )

                validationErrors[index]?.let { errorMessage ->
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        Button(
            onClick = {
                var validInput = true

                scope.launch {
                    for (index in inputUrls.indices) {
                        val url = inputUrls[index].text
                        validationErrors[index] = null

                        if (url.isBlank()) {
                            validationErrors[index] = "URL cannot be empty."
                            validInput = false
                        } else {
                            val isValid = validatePdfUrl(url)
                            if (!isValid) {
                                validationErrors[index] = "This doesn't seem to be a valid PDF URL."
                                validInput = false
                            }
                        }
                    }

                    if (validInput) {
                        inputUrls.forEachIndexed { index, url ->
                            downloadPdf(context, url.text, "PDF${index + 1}.pdf")
                        }
                        inputUrls.clear()
                        numOfFields = ""
                        keyboardController?.hide()
                    }
                }

            },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF7D358)),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF7D358)
            ),
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Start Download",
                color = Color.Black,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

// Function to validate if the URL is a valid PDF
suspend fun validatePdfUrl(urlString: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "HEAD"
        connection.connectTimeout = 5000
        connection.connect()

        return@withContext connection.contentType == "application/pdf"
    } catch (e: Exception) {
        return@withContext false
    }
}

fun downloadPdf(context: Context, url: String, fileName: String) {
    if (url.isNotEmpty()) {
        try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Downloading PDF")
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.enqueue(request)
            showNotification(context, "Download started", "$fileName is downloading.")
        } catch (e: Exception) {
            showNotification(context, "Download failed", "Failed to download $fileName.")
        }
    }
}

fun showNotification(context: Context, title: String, content: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            (context as MainActivity).requestNotificationPermission()
            return
        }
    }

    val notification = NotificationCompat.Builder(context, "DOWNLOAD_CHANNEL")
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(title)
        .setContentText(content)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .build()

    NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), notification)
}

@Preview(showBackground = true)
@Composable
fun PdfDownloadScreenPreview() {
    ServicesAppTheme {
        PdfDownloadScreen(context = MainActivity())
    }
}
