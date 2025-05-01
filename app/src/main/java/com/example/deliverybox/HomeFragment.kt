package com.example.deliverybox

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.deliverybox.adapter.BoxListAdapter
import com.example.deliverybox.databinding.FragmentHomeBinding
import com.example.deliverybox.model.BoxInfo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.deliverybox.dialog.RegisterBoxMethodDialogFragment



class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BoxListAdapter
    private val boxList = mutableListOf<BoxInfo>()

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val registerBoxLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == android.app.Activity.RESULT_OK) {
                loadBoxList()
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

        adapter = BoxListAdapter(boxList)
        binding.recyclerViewBoxes.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewBoxes.adapter = adapter

        binding.btnEmptyAddBox.setOnClickListener {
            showRegisterBoxDialog()
        }

        binding.toolbarHome.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menu_add_box) {
                showRegisterBoxDialog()
                true
            } else {
                false
            }
        }

        loadBoxList()
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
        val userUid = auth.currentUser?.uid ?: return
        db.collection("users").document(userUid).get()
            .addOnSuccessListener { document ->
                val boxAliases = document.get("boxAliases") as? Map<String, String> ?: emptyMap()
                boxList.clear()

                if (boxAliases.isEmpty()) {
                    binding.layoutEmpty.visibility = View.VISIBLE
                    binding.recyclerViewBoxes.visibility = View.GONE
                } else {
                    boxList.addAll(boxAliases.map { BoxInfo(it.key, it.value) })
                    adapter.notifyDataSetChanged()
                    binding.layoutEmpty.visibility = View.GONE
                    binding.recyclerViewBoxes.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.recyclerViewBoxes.visibility = View.GONE
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
