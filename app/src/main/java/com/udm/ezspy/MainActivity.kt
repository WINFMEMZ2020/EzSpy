package com.udm.ezspy

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import com.udm.ezspy.data.SpyRepository
import com.udm.ezspy.databinding.ActivityMainBinding
import com.udm.ezspy.service.EzSpyAccessibilityService
import com.udm.ezspy.ui.SpyViewModel
import com.udm.ezspy.ui.grabber.GrabberFragment
import com.udm.ezspy.ui.properties.PropertiesFragment
import com.udm.ezspy.ui.tree.NodeTreeFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: SpyViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()
        setupPager()
        observeCaptureResults()
    }

    override fun onResume() {
        super.onResume()
        // The floating overlay is dismissed here (and, when the user opened the
        // app manually while grabbing, the current selection is kept).
        EzSpyAccessibilityService.instance?.onMainUiResumed()
        // Enable the "task removed from recents" watchdog.
        EzSpyAccessibilityService.instance?.onMainUiShown()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        EzSpyAccessibilityService.instance?.onMainUiResumed()
    }

    private fun setupPager() {
        binding.pager.adapter = MainPagerAdapter(this)
        binding.pager.offscreenPageLimit = 2
        TabLayoutMediator(binding.tabs, binding.pager) { tab, position ->
            tab.text = getString(
                when (position) {
                    0 -> R.string.tab_grabber
                    1 -> R.string.tab_tree
                    else -> R.string.tab_properties
                }
            )
        }.attach()

        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // On the tree page horizontal gestures pan the (wider) tree
                // instead of changing tabs. Tabs remain reachable by tapping.
                binding.pager.isUserInputEnabled = position != PAGE_TREE
            }
        })
        binding.pager.isUserInputEnabled = binding.pager.currentItem != PAGE_TREE
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            // The app bar paints behind the status bar, its content sits below it.
            binding.header.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            // Content never overlaps the navigation bar / cutout.
            binding.pagerContainer.updatePadding(
                left = bars.left,
                right = bars.right,
                bottom = bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun observeCaptureResults() {
        SpyRepository.result.observe(this) { result ->
            if (result == null) return@observe
            if (!viewModel.applyResult(result)) return@observe

            val hasCapture = result.capture?.nodes?.isNotEmpty() == true
            // With data land on the node tree, otherwise go back to the grabber.
            binding.pager.setCurrentItem(if (hasCapture) 1 else 0, false)
        }
    }

    /** Called right before the task is sent to the background. */
    fun onGrabStarted() {
        SpyRepository.clear()
        viewModel.beginNewGrab()
        binding.pager.setCurrentItem(0, false)
    }

    fun openPropertiesTab() {
        binding.pager.setCurrentItem(2, true)
    }

    override fun onDestroy() {
        val shouldShutdown = isFinishing && !isChangingConfigurations
        super.onDestroy()
        if (shouldShutdown) {
            // A true privacy exit: recents swipe / back finishes the task.
            // Disable the accessibility service and end the process so the
            // system cannot keep the app alive in the background.
            EzSpyAccessibilityService.instance?.shutdownFromTaskRemoval()
        }
    }

    private companion object {
        const val PAGE_TREE = 1
    }

    private class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment = when (position) {
            0 -> GrabberFragment()
            1 -> NodeTreeFragment()
            else -> PropertiesFragment()
        }
    }
}
