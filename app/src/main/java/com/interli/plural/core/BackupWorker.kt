package com.interli.plural.core

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.interli.plural.core.BackupHelper

class BackupWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val success = BackupHelper.saveAutoBackup(applicationContext)
        return if (success) Result.success() else Result.retry()
    }
}
