package com.example.deliverybox

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.deliverybox.utils.AccountUtils
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 미인증 계정 정리 Worker
 * WorkManager를 통해 주기적으로 실행
 */
class AccountCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AccountCleanupWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "미인증 계정 정리 작업 시작")

            // 작업 실행 코드가 단일 콜백에서 완료되도록 CompletableDeferred 사용
            val result = CompletableDeferred<Result>()

            AccountUtils.cleanupOldUnverifiedAccounts { count ->
                Log.d(TAG, "${count}개의 미인증 계정 정리 완료")
                result.complete(Result.success())
            }

            // 완료 될 때까지 대기
            return@withContext result.await()
        } catch (e: Exception) {
            Log.e(TAG, "계정 정리 작업 실패: ${e.message}")
            return@withContext Result.retry()
        }
    }
}