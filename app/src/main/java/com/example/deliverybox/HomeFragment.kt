package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import adapter.BoxListAdapter
import com.example.deliverybox.databinding.FragmentHomeBinding
import com.example.deliverybox.model.BoxInfo
import com.example.deliverybox.dialog.RegisterBoxMethodDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BoxListAdapter
    private val boxList = mutableListOf<BoxInfo>()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private var boxesListener: ListenerRegistration? = null

    private val registerBoxLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == android.app.Activity.RESULT_OK) {
                updateEmptyState()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        loadBoxList()
    }

    private fun setupRecyclerView() {
        adapter = BoxListAdapter(boxList) { boxInfo ->
            val intent = Intent(requireContext(), BoxDetailActivity::class.java).apply {
                putExtra("boxId", boxInfo.boxId)
                putExtra("boxName", boxInfo.boxName)
                putExtra("boxAlias", boxInfo.alias)
            }
            startActivity(intent)
        }

        binding.recyclerViewBoxes.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@HomeFragment.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        binding.btnAddBox.setOnClickListener { showRegisterBoxDialog() }
        binding.btnEmptyAddBox.setOnClickListener { showRegisterBoxDialog() }
    }

    private fun showRegisterBoxDialog() {
        val dialog = RegisterBoxMethodDialogFragment()
        dialog.setOnRegisterBoxSelectedListener {
            val intent = Intent(requireContext(), RegisterBoxActivity::class.java)
            registerBoxLauncher.launch(intent)
        }
        dialog.show(parentFragmentManager, "RegisterBoxMethodDialog")
    }

    private fun loadBoxList() {
        // Fragment가 이미 분리되었는지 확인
        if (!isAdded || _binding == null) return

        val userUid = auth.currentUser?.uid ?: return
        Log.d("HomeFragment", "사용자 UID: $userUid 데이터 로딩 시작")

        boxesListener?.remove()

        boxesListener = db.collection("users").document(userUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("HomeFragment", "데이터 로드 실패: ${error.message}")
                    Toast.makeText(requireContext(), "택배함 정보 로드 실패: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                if (snapshot == null || !snapshot.exists()) {
                    Log.w("HomeFragment", "사용자 문서 없음")
                    updateEmptyState(true)
                    return@addSnapshotListener
                }

                Log.d("HomeFragment", "사용자 문서 데이터: ${snapshot.data}")
                boxList.clear()

                val boxAliases = snapshot.get("boxAliases") as? Map<String, String>
                val mainBoxId = snapshot.getString("mainBoxId")

                if (boxAliases.isNullOrEmpty() && mainBoxId.isNullOrEmpty()) {
                    Log.w("HomeFragment", "boxAliases와 mainBoxId 모두 없음")
                    updateEmptyState(true)
                    return@addSnapshotListener
                }

                val boxesToProcess = mutableMapOf<String, String>()
                boxAliases?.let { boxesToProcess.putAll(it) }
                if (!mainBoxId.isNullOrEmpty() && !boxesToProcess.containsKey(mainBoxId)) {
                    boxesToProcess[mainBoxId] = "내 택배함"
                }

                if (boxesToProcess.isEmpty()) {
                    Log.w("HomeFragment", "처리할 박스 없음")
                    updateEmptyState(true)
                    return@addSnapshotListener
                }

                var processedCount = 0

                for ((boxId, alias) in boxesToProcess) {
                    val currentBoxId = boxId
                    val currentAlias = alias

                    db.collection("boxes").document(currentBoxId)
                        .get()
                        .addOnSuccessListener { boxDoc ->
                            if (!boxDoc.exists()) {
                                processedCount++
                                if (processedCount == boxesToProcess.size) {
                                    updateEmptyState(boxList.isEmpty())
                                }
                                return@addOnSuccessListener
                            }

                            val boxName = boxDoc.getString("boxName") ?: "택배함"
                            val boxInfo = BoxInfo(
                                boxId = currentBoxId,
                                alias = currentAlias,
                                boxName = boxName,
                                packageCount = 0,
                                doorLocked = true
                            )

                            boxList.add(boxInfo)
                            adapter.notifyDataSetChanged()
                            updateEmptyState(boxList.isEmpty())

                            db.collection("boxes").document(currentBoxId)
                                .collection("packages")
                                .whereEqualTo("isDelivered", false)
                                .get()
                                .addOnSuccessListener { packagesSnapshot ->
                                    val packageCount = packagesSnapshot.size()
                                    val index = boxList.indexOfFirst { it.boxId == currentBoxId }
                                    if (index >= 0) {
                                        boxList[index] = boxList[index].copy(packageCount = packageCount)
                                        adapter.notifyItemChanged(index)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e("HomeFragment", "택배 수 로드 실패: ${e.message}")
                                }

                            processedCount++
                        }
                        .addOnFailureListener { e ->
                            processedCount++
                            if (processedCount == boxesToProcess.size) {
                                updateEmptyState(boxList.isEmpty())
                            }
                        }
                }
            }
    }

    private fun updateEmptyState(isEmpty: Boolean = boxList.isEmpty()) {
        _binding?.let { binding ->
            binding.layoutEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
            binding.recyclerViewBoxes.visibility = if (isEmpty) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        // 리스너 제거 및 바인딩 해제
        boxesListener?.remove()
        _binding = null
        super.onDestroyView()
    }
}
