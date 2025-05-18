package utils

import android.view.View
import android.widget.TextView
import com.example.deliverybox.R

object StateViewHelper {
    fun showLoading(root: View) {
        root.findViewById<View>(R.id.state_loading).visibility = View.VISIBLE
        hideOthers(root, R.id.state_loading)
    }

    fun showEmpty(root: View) {
        root.findViewById<View>(R.id.state_empty).visibility = View.VISIBLE
        hideOthers(root, R.id.state_empty)
    }

    fun showError(root: View, message: String) {
        root.findViewById<View>(R.id.state_error).visibility = View.VISIBLE
        root.findViewById<TextView>(R.id.state_error_message).text = message
        hideOthers(root, R.id.state_error)
    }

    fun hideAll(root: View) {
        hideOthers(root, -1)
    }

    private fun hideOthers(root: View, exceptId: Int) {
        listOf(R.id.state_loading, R.id.state_empty, R.id.state_error).forEach {
            if (it != exceptId) root.findViewById<View>(it).visibility = View.GONE
        }
    }
}
