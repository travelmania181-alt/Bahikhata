package com.bahikhata

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

internal class DriveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(applicationContext)?.account
                ?: return@withContext Result.failure(
                    workDataOf(ERROR_KEY to "No Google account is connected."),
                )
            val entries = LedgerStore.readEntries(applicationContext)

            val token = GoogleAuthUtil.getToken(
                applicationContext,
                account,
                "oauth2:${LedgerStore.driveScope()}",
            )
            val fileId = LedgerStore.driveFileId(applicationContext)
            if (fileId == null) {
                createWorkbook(token, entries)
            } else {
                updateWorkbook(token, fileId, entries)
            }
            Result.success()
        } catch (error: UserRecoverableAuthException) {
            Result.failure(workDataOf(ERROR_KEY to "Google needs additional Drive permission: ${error.message}"))
        } catch (error: DriveApiException) {
            val message = "Drive API error ${error.statusCode}: ${error.message}"
            if (error.statusCode >= 500) Result.retry() else Result.failure(workDataOf(ERROR_KEY to message))
        } catch (error: IOException) {
            Result.retry()
        } catch (error: Exception) {
            Result.failure(workDataOf(ERROR_KEY to (error.message ?: error.javaClass.simpleName)))
        }
    }

    private fun createWorkbook(token: String, entries: List<LedgerEntry>) {
        val metadata = JSONObject()
            .put("name", "Bahikhata Ledger.xlsx")
            .put("mimeType", XLSX_MIME)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val workbook = XlsxWorkbook.create(entries).toRequestBody(XLSX_MEDIA_TYPE)
        val body = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata)
            .addPart(workbook)
            .build()
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DriveApiException(response.code, response.body?.string().orEmpty().take(300))
            }
            val id = JSONObject(response.body?.string().orEmpty()).getString("id")
            LedgerStore.saveDriveFileId(applicationContext, id)
        }
    }

    private fun updateWorkbook(token: String, fileId: String, entries: List<LedgerEntry>) {
        val request = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
            .header("Authorization", "Bearer $token")
            .patch(XlsxWorkbook.create(entries).toRequestBody(XLSX_MEDIA_TYPE))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw DriveApiException(response.code, response.body?.string().orEmpty().take(300))
            }
        }
    }

    private class DriveApiException(val statusCode: Int, detail: String) : IOException(detail)

    companion object {
        const val ERROR_KEY = "sync_error"
        const val UNIQUE_WORK_NAME = "bahikhata-drive-sync"
        private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        private val XLSX_MEDIA_TYPE = XLSX_MIME.toMediaType()
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val client = OkHttpClient()

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
        }
    }
}
