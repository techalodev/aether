package dev.aether.vpn.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.aether.vpn.data.Repository
import dev.aether.vpn.data.SubscriptionManager
import java.util.concurrent.TimeUnit

/** Hourly check that refreshes every subscription whose own interval has elapsed. */
class AutoUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Repository.init(applicationContext)
        SubscriptionManager.refreshDue(applicationContext)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoUpdateWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("subscription-auto-update", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
