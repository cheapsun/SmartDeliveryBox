package com.example.deliverybox.box

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class QrCodeValidationService {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * QR 스캔된 코드를 Firebase에서 검증
     */
    suspend fun validateScannedQrCode(scannedCode: String): Result<BoxValidationResult> {
        return try {
            // boxes 컬렉션에서 해당 코드 확인
            val document = firestore.collection("boxes")
                .document(scannedCode)
                .get()
                .await()

            if (!document.exists()) {
                return Result.failure(Exception("등록되지 않은 QR 코드입니다"))
            }

            val data = document.data!!
            val status = data["status"] as String
            val batchName = data["batchName"] as String
            val qrCodeBase64 = data["qrCodeBase64"] as? String
            val ownerId = data["ownerId"] as? String

            when (status) {
                "AVAILABLE" -> {
                    // 등록 가능한 택배함
                    Result.success(BoxValidationResult(
                        boxCode = scannedCode,
                        isValid = true,
                        canRegister = true,
                        status = status,
                        message = "등록 가능한 택배함입니다",
                        batchName = batchName,
                        qrCodeBase64 = qrCodeBase64
                    ))
                }
                "REGISTERED" -> {
                    // 이미 등록된 택배함
                    val currentUserId = auth.currentUser?.uid
                    if (ownerId == currentUserId) {
                        Result.success(BoxValidationResult(
                            boxCode = scannedCode,
                            isValid = true,
                            canRegister = false,
                            status = status,
                            message = "이미 등록된 본인의 택배함입니다",
                            batchName = batchName,
                            qrCodeBase64 = qrCodeBase64
                        ))
                    } else {
                        Result.success(BoxValidationResult(
                            boxCode = scannedCode,
                            isValid = true,
                            canRegister = false,
                            status = status,
                            message = "다른 사용자가 등록한 택배함입니다",
                            batchName = batchName,
                            qrCodeBase64 = qrCodeBase64
                        ))
                    }
                }
                "INACTIVE" -> {
                    Result.failure(Exception("사용할 수 없는 택배함입니다"))
                }
                else -> {
                    Result.failure(Exception("알 수 없는 택배함 상태입니다"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 검증된 택배함을 사용자 계정에 등록
     */
    suspend fun registerValidatedBox(
        boxCode: String,
        boxAlias: String
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("로그인이 필요합니다"))

            val batch = firestore.batch()

            // 1. boxes 컬렉션의 상태 업데이트
            val boxRef = firestore.collection("boxes").document(boxCode)
            batch.update(boxRef, mapOf(
                "status" to "REGISTERED",
                "ownerId" to currentUser.uid,
                "registeredAt" to Date(),
                "alias" to boxAlias
            ))

            // 2. 사용자 컬렉션에 택배함 정보 추가
            val userRef = firestore.collection("users").document(currentUser.uid)

            // 기존 boxAliases 가져오기
            val userDoc = userRef.get().await()
            val existingAliases = userDoc.get("boxAliases") as? Map<String, String>
                ?: mapOf()
            val updatedAliases = existingAliases.toMutableMap()
            updatedAliases[boxCode] = boxAlias

            // boxAliases와 mainBoxId 업데이트
            val updateData = mutableMapOf<String, Any>(
                "boxAliases" to updatedAliases
            )

            // 첫 번째 택배함인 경우 mainBoxId로 설정
            if (existingAliases.isEmpty()) {
                updateData["mainBoxId"] = boxCode
            }

            batch.update(userRef, updateData)

            // 배치 커밋
            batch.commit().await()

            Result.success("택배함이 성공적으로 등록되었습니다")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 사용자의 등록된 택배함 목록 조회
     */
    suspend fun getUserBoxes(): Result<List<UserBoxInfo>> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("로그인이 필요합니다"))

            val userDoc = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            if (!userDoc.exists()) {
                return Result.success(emptyList())
            }

            val boxAliases = userDoc.get("boxAliases") as? Map<String, String>
                ?: return Result.success(emptyList())

            val userBoxes = mutableListOf<UserBoxInfo>()

            // 각 택배함의 상세 정보 조회
            for ((boxCode, alias) in boxAliases) {
                try {
                    val boxDoc = firestore.collection("boxes")
                        .document(boxCode)
                        .get()
                        .await()

                    if (boxDoc.exists()) {
                        val boxData = boxDoc.data!!
                        userBoxes.add(UserBoxInfo(
                            boxCode = boxCode,
                            alias = alias,
                            status = boxData["status"] as String,
                            batchName = boxData["batchName"] as String,
                            registeredAt = boxData["registeredAt"] as? Date,
                            qrCodeBase64 = boxData["qrCodeBase64"] as? String
                        ))
                    }
                } catch (e: Exception) {
                    // 개별 택배함 조회 실패 시 무시하고 계속 진행
                    continue
                }
            }

            Result.success(userBoxes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// 데이터 클래스들
data class BoxValidationResult(
    val boxCode: String,
    val isValid: Boolean,
    val canRegister: Boolean,
    val status: String,
    val message: String,
    val batchName: String? = null,
    val qrCodeBase64: String? = null
)

data class UserBoxInfo(
    val boxCode: String,
    val alias: String,
    val status: String,
    val batchName: String,
    val registeredAt: Date?,
    val qrCodeBase64: String?
)