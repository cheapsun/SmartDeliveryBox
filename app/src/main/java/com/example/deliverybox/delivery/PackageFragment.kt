package com.example.deliverybox.delivery

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.SearchView
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deliverybox.databinding.FragmentPackageBinding
import com.example.deliverybox.delivery.adapter.PackageAdapter
import com.example.deliverybox.delivery.adapter.PackageItem
import com.example.deliverybox.delivery.dialog.FilterDialogFragment
import com.example.deliverybox.delivery.swipe.PackageSwipeCallback
import com.example.deliverybox.ui.StateViewHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class PackageFragment : Fragment() {

    private var _binding: FragmentPackageBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: PackageViewModel
    private lateinit var adapter: PackageAdapter

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
        viewModel.load(getCurrentBoxId()) // ✅ boxId 로드
    }

    private fun setupRecyclerView() {
        adapter = PackageAdapter(
            onItemClick = { packageItem ->
                val intent = Intent(requireContext(), PackageDetailActivity::class.java).apply {
                    putExtra("packageId", packageItem.id)
                    putExtra("boxId", getCurrentBoxId())
                }
                startActivity(intent)
            },
            onStatusChange = { packageItem, newStatus ->
                viewModel.updatePackageStatus(packageItem.id, newStatus)
            },
            onDeleteClick = { packageItem ->
                showDeleteConfirmDialog(packageItem)
            }
        )

        binding.recyclerViewPackages.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@PackageFragment.adapter
            ItemTouchHelper(PackageSwipeCallback(adapter)).attachToRecyclerView(this)
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.refreshPackages()
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is PackageViewModel.PackageUiState.Loading -> StateViewHelper.showLoading(binding.includeStateView)
                is PackageViewModel.PackageUiState.Empty -> StateViewHelper.showEmpty(binding.includeStateView)
                is PackageViewModel.PackageUiState.Error -> StateViewHelper.showError(binding.includeStateView, state.message)
                is PackageViewModel.PackageUiState.Success -> {
                    allItems = state.list
                    adapter.submitList(state.list)
                    StateViewHelper.hideAll(binding.includeStateView)
                }
            }

            binding.swipeRefreshLayout.isRefreshing = false

            binding.includeStateView.findViewById<Button>(com.example.deliverybox.R.id.state_error_retry)?.setOnClickListener {
                viewModel.refreshPackages()
            }
        }
    }

    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                val keyword = newText.orEmpty()
                val filtered = allItems.filter {
                    it.data.trackingNumber.contains(keyword, ignoreCase = true) ||
                            it.data.itemName?.contains(keyword, ignoreCase = true) == true
                }
                adapter.submitList(filtered)
                return true
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            com.example.deliverybox.R.id.action_filter -> {
                FilterDialogFragment { selectedStatus ->
                    applyStatusFilter(selectedStatus)
                }.show(childFragmentManager, "FilterDialog")
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

    private fun applyStatusFilter(status: DeliveryStatus?) {
        val filtered = if (status == null) {
            allItems
        } else {
            allItems.filter { it.data.status == status }
        }
        adapter.submitList(filtered)
    }

    private fun showDeleteConfirmDialog(packageItem: PackageItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("택배 삭제")
            .setMessage("'${packageItem.data.trackingNumber}' 택배를 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.deletePackage(packageItem.id)
            }
            .setNegativeButton("취소") { _, _ ->
                adapter.notifyItemChanged(adapter.currentList.indexOf(packageItem))
            }
            .setOnCancelListener {
                adapter.notifyItemChanged(adapter.currentList.indexOf(packageItem))
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
