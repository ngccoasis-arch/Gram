package com.gram.client.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationCompat
import com.gram.client.GramApplication
import com.gram.core.tdlib.TransferPhase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class TransferJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observer: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        createChannel()
        setNotification(
            params,
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Gram media transfer")
                .setContentText("Telegram transfers are active")
                .setOngoing(true)
                .setProgress(0, 0, true)
                .build(),
            JOB_END_NOTIFICATION_POLICY_REMOVE,
        )
        val backend = (application as GramApplication).backend
        observer = scope.launch {
            backend.transfers.collectLatest { transfers ->
                if (transfers.none { it.phase == TransferPhase.ACTIVE || it.phase == TransferPhase.QUEUED }) {
                    jobFinished(params, false)
                    cancel()
                }
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        observer?.cancel()
        return true
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Media transfers", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "media_transfers"
        private const val NOTIFICATION_ID = 4102
        private const val JOB_ID = 4102

        fun schedule(context: Context) {
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, TransferJobService::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setUserInitiated(true)
                .build()
            context.getSystemService(JobScheduler::class.java).schedule(info)
        }
    }
}
