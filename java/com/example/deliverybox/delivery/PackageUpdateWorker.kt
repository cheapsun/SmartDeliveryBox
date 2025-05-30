package com.example.deliverybox.delivery

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.example.deliverybox.delivery.api.DeliveryTrackerService
import com.example.deliverybox.domain.repositories.PackageRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class PackageUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val packageRepository: PackageRepository,
    private val trackingService: DeliveryTrackerService,
    private val notificationManager: PackageNotificationManager
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("PackageUpdateWorker", "배송 상태 업데이트 시작")

            val boxId = inputData.getString("boxId") ?: run {
                Log.e("PackageUpdateWorker", "boxId가 제공되지 않았습니다")
                return Result.failure()
            }

            // 1. 진행 중인 모든 패키지 조회
            val activePackages = packageRepository.getActivePackages(boxId)
            Log.d("PackageUpdateWorker", "업데이트 대상 패키지: ${activePackages.size}개")

            // 2. 각 패키지 상태 확인 및 업데이트
            activePackages.forEach { packageInfo ->
                try {
                    updatePackageStatus(packageInfo)
                } catch (e: Exception) {
                    Log.e("PackageUpdateWorker", "패키지 업데이트 실패: ${packageInfo.trackingNumber}", e)
                }
            }

            Log.d("PackageUpdateWorker", "배송 상태 업데이트 완료")
            Result.success()
        } catch (e: Exception) {
            Log.e("PackageUpdateWorker", "전체 업데이트 실패", e)
            Result.retry()
        }
    }

    private suspend fun updatePackageStatus(packageInfo: PackageInfo) {
        // 배송 완료된 패키지는 제외
        if (packageInfo.status == DeliveryStatus.DELIVERED) return

        // 마지막 업데이트로부터 1시간 이상 경과한 경우만 업데이트
        val hoursSinceUpdate = (System.currentTimeMillis() - packageInfo.lastUpdated) / (60 * 60 * 1000)
        if (hoursSinceUpdate < 1) return

        trackingService.trackPackage(
            packageInfo.courierCompany,
            packageInfo.trackingNumber
        ).fold(
            onSuccess = { trackingInfo ->
                // 상태가 변경된 경우에만 업데이트
                if (trackingInfo.currentStatus != packageInfo.status) {
                    val updatedPackage = packageInfo.copy(
                        status = trackingInfo.currentStatus,
                        deliverySteps = trackingInfo.deliverySteps,
                        lastUpdated = System.currentTimeMillis(),
                        deliveredAt = if (trackingInfo.currentStatus == DeliveryStatus.DELIVERED)
                            System.currentTimeMillis() else packageInfo.deliveredAt
                    )

                    // DB 업데이트
                    packageRepository.updatePackage(updatedPackage)

                    // 알림 전송
                    notificationManager.sendStatusUpdateNotification(updatedPackage)

                    Log.d("PackageUpdateWorker",
                        "패키지 상태 업데이트: ${packageInfo.trackingNumber} " +
                                "${packageInfo.status} -> ${trackingInfo.currentStatus}")
                }
            },
            onFailure = { error ->
                Log.w("PackageUpdateWorker",
                    "추적 실패: ${packageInfo.trackingNumber} - ${error.message}")
            }
        )
    }
}

// 워커 스케줄링
object PackageUpdateScheduler {

    fun schedulePeriodicUpdates(context: Context, boxId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = workDataOf("boxId" to boxId)

        val updateRequest = PeriodicWorkRequestBuilder<PackageUpdateWorker>(
            repeatInterval = 2, // 2시간마다
            repeatIntervalTimeUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .setInputData(inputData)
            .addTag("package_update")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "package_status_update",
                ExistingPeriodicWorkPolicy.KEEP,
                updateRequest
            )
    }

    fun scheduleImmediateUpdate(context: Context, boxId: String) {
        val inputData = workDataOf("boxId" to boxId)

        val updateRequest = OneTimeWorkRequestBuilder<PackageUpdateWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(inputData)
            .addTag("immediate_update")
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                "immediate_package_update",
                ExistingWorkPolicy.REPLACE,
                updateRequest
            )
    }

    fun cancelAllUpdates(context: Context) {
        WorkManager.getInstance(context).cancelAllWorkByTag("package_update")
    }
}