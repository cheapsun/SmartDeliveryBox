package com.example.deliverybox.delivery

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.Button
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deliverybox.databinding.FragmentPackageBinding
import com.example.deliverybox.delivery.adapter.PackageAdapter
import com.example.deliverybox.delivery.adapter.PackageItem
import com.example.deliverybox.delivery.swipe.PackageSwipeCallback
import utils.StateViewHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PackageFragment : Fragment() {

    private var _binding: FragmentPackageBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PackageViewModel
    private lateinit var packageAdapter: PackageAdapter

    private var allItems: List<PackageItem> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPackageBinding.inflate(inflater, container, false)
        setHasOptionsMenu(true)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[PackageViewModel::class.java]

        setupRecyclerView()
        observeViewModel()
        setupSearchView()
        viewModel.loadPackages(getCurrentBoxId())
    }

    private fun setupRecyclerView() {
        packageAdapter = PackageAdapter(
            onItemClick = { packageItem ->
                val intent = Intent(requireContext(), PackageDetailActivity::class.java).apply {
                    putExtra("packageId", packageItem.id)
                    putExtra("boxId", getCurrentBoxId())
                }
                startActivity(intent)
            },
            onStatusChange = { packageItem, newStatus ->
                viewModel.updateStatus(getCurrentBoxId(), packageItem.id, newStatus)
            },
            onDeleteClick = { packageItem ->
                showDeleteConfirmDialog(packageItem)
            }
        )

        binding.recyclerViewPackages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = packageAdapter
            ItemTouchHelper(PackageSwipeCallback(packageAdapter)).attachToRecyclerView(this)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadPackages(getCurrentBoxId())
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PackageViewModel.UiState.Loading -> StateViewHelper.showLoading(binding.includeStateView.root)
                is PackageViewModel.UiState.Empty -> StateViewHelper.showEmpty(binding.includeStateView.root)
                is PackageViewModel.UiState.Error -> StateViewHelper.showError(binding.includeStateView.root, state.message)
                is PackageViewModel.UiState.Success -> {
                    // PackageInfo를 PackageItem으로 변환
                    allItems = state.packages.map { packageInfo ->
                        PackageItem(
                            id = packageInfo.id,
                            data = packageInfo
                        )
                    }
                    packageAdapter.submitList(allItems)
                    StateViewHelper.hideAll(binding.includeStateView.root)
                }
            }

            binding.swipeRefreshLayout.isRefreshing = false

            // 바인딩을 통한 직접 접근으로 변경
            binding.includeStateView.stateErrorRetry?.setOnClickListener {
                viewModel.loadPackages(getCurrentBoxId())
            }
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                val keyword = newText.orEmpty()
                viewModel.search(keyword, getCurrentBoxId())
                return true
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(com.example.deliverybox.R.menu.menu_package_fragment, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.example.deliverybox.R.id.action_filter -> {
                showFilterDialog()  // ✅ 별도 메서드 호출
                true
            }
            com.example.deliverybox.R.id.action_search -> {
                binding.searchView.visibility = View.VISIBLE
                binding.searchView.requestFocus()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showFilterDialog() {
        val statusOptions = arrayOf(
            "전체",
            DeliveryStatus.REGISTERED.toKorean(),
            DeliveryStatus.PICKED_UP.toKorean(),
            DeliveryStatus.IN_TRANSIT.toKorean(),
            DeliveryStatus.OUT_FOR_DELIVERY.toKorean(),
            DeliveryStatus.IN_BOX.toKorean(),
            DeliveryStatus.DELIVERED.toKorean()
        )

        val statusValues = arrayOf(
            null,
            DeliveryStatus.REGISTERED,
            DeliveryStatus.PICKED_UP,
            DeliveryStatus.IN_TRANSIT,
            DeliveryStatus.OUT_FOR_DELIVERY,
            DeliveryStatus.IN_BOX,
            DeliveryStatus.DELIVERED
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("상태별 필터")
            .setItems(statusOptions) { _, which ->
                applyStatusFilter(statusValues[which])
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun applyStatusFilter(status: DeliveryStatus?) {
        viewModel.filter(status, getCurrentBoxId())
    }

    private fun showDeleteConfirmDialog(packageItem: PackageItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("택배 삭제")
            .setMessage("'${packageItem.data.trackingNumber}' 택배를 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deletePackage(getCurrentBoxId(), packageItem.id)
            }
            .setNegativeButton("취소") { _, _ ->
                packageAdapter.notifyItemChanged(packageAdapter.currentList.indexOf(packageItem))
            }
            .setOnCancelListener {
                packageAdapter.notifyItemChanged(packageAdapter.currentList.indexOf(packageItem))
            }
            .show()
    }

    private fun getCurrentBoxId(): String {
        return "current_box_id"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}