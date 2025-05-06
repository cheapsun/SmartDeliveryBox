import { onCall } from "firebase-functions/v2/https"; // ✅ v6 기준
import * as admin from "firebase-admin";

admin.initializeApp();
const db = admin.firestore();

interface ClaimBoxData {
  code: string;
}

export const claimBox = onCall<ClaimBoxData>(async (request) => {
  const uid = request.auth?.uid;
  const { code } = request.data;

  if (!uid) {
    throw new Error("로그인이 필요합니다.");
  }

  return db.runTransaction(async (tx) => {
    const codeRef = db.doc(`boxCodes/${code}`);
    const codeSnap = await tx.get(codeRef);

    if (!codeSnap.exists) {
      throw new Error("존재하지 않는 코드입니다.");
    }

    const codeData = codeSnap.data() as { active: boolean; boxId: string };
    if (!codeData.active) {
      throw new Error("이미 사용된 코드입니다.");
    }

    tx.update(codeRef, { active: false });

    const boxRef = db.doc(`boxes/${codeData.boxId}`);
    tx.set(
      boxRef,
      {
        ownerUid: uid,
        members: { [uid]: "owner" },
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    return { success: true };
  });
});
