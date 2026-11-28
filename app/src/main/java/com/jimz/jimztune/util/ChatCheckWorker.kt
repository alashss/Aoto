package com.jimz.jimztune.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jimz.jimztune.AppGraph

/** Pemeriksa pesan baru berkala (±15 menit) agar notifikasi tetap datang walau aplikasi ditutup. */
class ChatCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            AppGraph.init(applicationContext)
            ChatNotifier.check(applicationContext)
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
