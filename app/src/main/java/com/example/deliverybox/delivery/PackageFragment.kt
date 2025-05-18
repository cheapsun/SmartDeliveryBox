package com.example.deliverybox.delivery

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.deliverybox.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class PackageFragment : Fragment() {

    private var _binding: FragmentPackageBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PackageViewModel
    private lateinit var adapter: PackageAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPackageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupViewModel()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        // 어댑터 초기화
        adapter = PackageAdapter(
            onItemClick = { packageItem ->
                // PackageDetailActivity로 이동
                val intent = Intent(requireContext(), PackageDetailActivity::class.java).apply {
                    putExtra("packageId", packageItem.id)
                    putExtra("boxId", getCurrentBoxId())
                }
                startActivity(intent)
            },
            onStatusChange = { packageItem, newStatus ->
                // 상태 변경
                viewModel.updatePackageStatus(packageItem.id, newStatus)
            },
            onDeleteClick = { packageItem ->
                // 삭제 확인 다이얼로그
                showDeleteConfirmDialog(packageItem)
            }
        )

        // RecyclerView 설정
        binding.recyclerViewPackages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PackageFragment.adapter

            // 🔥 스와이프 제스처 연결
            val swipeCallback = PackageSwipeCallback(this@PackageFragment.adapter)
            val itemTouchHelper = ItemTouchHelper(swipeCallback)
            itemTouchHelper.attachToRecyclerView(this)
        }

        // 풀투리프레시 설정 (선택사항)
        binding.swipeRefreshLayout?.setOnRefreshListener {
            viewModel.refreshPackages()
        }
    }

    private fun setupViewModel() {
        // ViewModel 초기화
        viewModel = ViewModelProvider(this)[PackageViewModel::class.java]
    }

    private fun observeViewModel() {
        viewModel.filteredPackages.observe(viewLifecycleOwner) { packages ->
            val packageItems = packages.map { PackageItem(it.id, it) }
            adapter.submitList(packageItems)

            // 빈 상태 처리
            binding.tvEmptyMessage.visibility = if (packages.isEmpty()) View.VISIBLE else View.GONE
        }

        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PackageUiState.Loading -> {
                    // 로딩 표시
                }
                is PackageUiState.Success -> {
                    binding.swipeRefreshLayout?.isRefreshing = false
                }
                is PackageUiState.Error -> {
                    binding.swipeRefreshLayout?.isRefreshing = false
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                }
                is PackageUiState.Empty -> {
                    binding.swipeRefreshLayout?.isRefreshing = false
                }
            }
        }
    }

    private fun showDeleteConfirmDialog(packageItem: PackageItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("택배 삭제")
            .setMessage("'${packageItem.data.trackingNumber}' 택배를 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deletePackage(packageItem.id)
            }
            .setNegativeButton("취소") { _, _ ->
                // 스와이프 되돌리기
                adapter.notifyItemChanged(adapter.currentList.indexOf(packageItem))
            }
            .setOnCancelListener {
                // 다이얼로그 취소시 스와이프 되돌리기
                adapter.notifyItemChanged(adapter.currentList.indexOf(packageItem))
            }
            .show()
    }

    private fun getCurrentBoxId(): String {
        // 현재 선택된 박스 ID 가져오기 로직
        return "current_box_id"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}