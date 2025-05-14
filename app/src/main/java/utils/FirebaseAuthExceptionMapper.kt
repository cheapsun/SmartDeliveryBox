package com.example.deliverybox.utils

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthEmailException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException

object FirebaseAuthExceptionMapper {
    fun mapException(exception: Exception): Exception {
        val friendlyMessage = when (exception) {
            is FirebaseAuthInvalidCredentialsException -> Exception("이메일 또는 비밀번호가 올바르지 않습니다")
            is FirebaseAuthInvalidUserException -> Exception("존재하지 않는 계정입니다")
            is FirebaseAuthUserCollisionException -> Exception("이미 사용 중인 이메일입니다")
            is FirebaseAuthWeakPasswordException -> Exception("비밀번호가 너무 약합니다")
            is FirebaseAuthEmailException -> Exception("이메일 전송에 실패했습니다")
            is FirebaseNetworkException, is IOException -> Exception("인터넷 연결을 확인해주세요")
            is FirebaseFirestoreException -> Exception("데이터 처리 중 오류가 발생했습니다")
            else -> Exception("오류가 발생했습니다: ${exception.message}")
        }

        return friendlyMessage
    }
}