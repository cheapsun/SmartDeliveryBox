package com.example.deliverybox.ui.home

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.example.deliverybox.adapter.BoxAdapter
import com.example.deliverybox.model.Box
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeFragment : Fragment() {

    private lateinit var recyclerViewBoxes: RecyclerView
    private lateinit var cardEmptyBox: View
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        db = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        recyclerViewBoxes = view.findViewById(R.id.recycler_view_boxes)
        cardEmptyBox = view.findViewById(R.id.card_empty_box)

        recyclerViewBoxes.layoutManager = LinearLayoutManager(requireContext())

        val btnAddBoxCenter = view.findViewById<Button>(R.id.btn_add_box_center)
        btnAddBoxCenter.setOnClickListener {
            showRegisterBoxMethodDialog()
        }

        val btnAddBox = view.findViewById<ImageButton>(R.id.btn_add_box)
        btnAddBox.setOnClickListener {
            showRegisterBoxMethodDialog()  // 🔥 ➕ 버튼 클릭 시 바로 BottomSheetDialog 띄우기
        }

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar_home)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)

        loadBoxes()

        return view
    }

    private fun showRegisterBoxMethodDialog() {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_register_box_method, null)

        val cardManual = view.findViewById<View>(R.id.card_manual_register)
        val cardQr = view.findViewById<View>(R.id.card_qr_register)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_confirm)

        var selectedMethod: String? = null

        cardManual.setOnClickListener {
            selectedMethod = "manual"
            cardManual.setBackgroundResource(R.drawable.rounded_background)
            cardQr.setBackgroundResource(android.R.color.transparent)
        }

        cardQr.setOnClickListener {
            selectedMethod = "qr"
            cardQr.setBackgroundResource(R.drawable.rounded_background)
            cardManual.setBackgroundResource(android.R.color.transparent)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            when (selectedMethod) {
                "manual" -> {
                    // TODO: RegisterBoxActivity 이동 구현 예정
                }
                "qr" -> {
                    Toast.makeText(requireContext(), "QR 등록 기능 준비 중입니다.", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(requireContext(), "등록 방법을 선택해주세요.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun loadBoxes() {
        db.collection("boxes")
            .whereEqualTo("ownerUid", auth.currentUser?.uid)
            .get()
            .addOnSuccessListener { result ->
                val boxList = mutableListOf<Box>()
                for (document in result) {
                    val boxId = document.id
                    val boxName = document.getString("boxName") ?: "이름 없음"
                    boxList.add(Box(boxId, boxName))
                }

                if (boxList.isEmpty()) {
                    cardEmptyBox.visibility = View.VISIBLE
                    recyclerViewBoxes.visibility = View.GONE
                } else {
                    cardEmptyBox.visibility = View.GONE
                    recyclerViewBoxes.visibility = View.VISIBLE
                    recyclerViewBoxes.adapter = BoxAdapter(boxList)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "박스 불러오기 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
