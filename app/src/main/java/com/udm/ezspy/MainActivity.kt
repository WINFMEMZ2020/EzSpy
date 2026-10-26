package com.udm.ezspy

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
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
import com.udm.ezspy.data.PrivacySettings
import com.udm.ezspy.data.SessionEndReason
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
        if (savedInstanceState == null) {
            // The privacy switches are deliberately not persisted.
            PrivacySettings.reset()
        }
        // Sensitive node text must not leak into recents thumbnails or
        // screenshots by default.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        PrivacySettings.disableSecureScreen.observe(this) { disabled ->
            if (disabled) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()
        setupPager()
        observeCaptureResults()
    }

    override fun onResume() {
        super.onResume()
        // The preview's back button dismisses the overlay; opening the app
        // manually keeps the temporary session and its floating panel alive.
        EzSpyAccessibilityService.instance?.onMainUiResumed()
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
            if (result == null) {
                // Stop / timeout / task removal also clears the UI state, so a
                // stale tree can never be shown again.
                viewModel.beginNewGrab()
                return@observe
            }
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
        val shouldFinish = isFinishing && !isChangingConfigurations
        super.onDestroy()
        if (shouldFinish) {
            // A temporary tool should not leave a capture behind as a back
            // press / task removal destroys the UI.
            val service = EzSpyAccessibilityService.instance
            if (service != null) {
                service.onMainUiFinished()
            } else {
                SpyRepository.clear()
                SpyRepository.endSession(SessionEndReason.ACTIVITY_FINISHED)
            }
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
