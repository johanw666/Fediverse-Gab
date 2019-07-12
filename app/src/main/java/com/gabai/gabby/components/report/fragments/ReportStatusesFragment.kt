/* Copyright 2019 Joel Pyska
 *
 * This file is a part of Tusky.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Tusky is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Tusky; if not,
 * see <http://www.gnu.org/licenses>. */

package com.gabai.gabby.components.report.fragments

import android.os.Bundle
import android.preference.PreferenceManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.app.ActivityOptionsCompat
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.paging.PagedList
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.android.material.snackbar.Snackbar
import com.gabai.gabby.AccountActivity
import com.gabai.gabby.R
import com.gabai.gabby.ViewMediaActivity
import com.gabai.gabby.ViewTagActivity
import com.gabai.gabby.components.report.ReportViewModel
import com.gabai.gabby.components.report.Screen
import com.gabai.gabby.components.report.adapter.AdapterHandler
import com.gabai.gabby.components.report.adapter.StatusesAdapter
import com.gabai.gabby.db.AccountManager
import com.gabai.gabby.di.Injectable
import com.gabai.gabby.di.ViewModelFactory
import com.gabai.gabby.entity.Attachment
import com.gabai.gabby.entity.Status
import com.gabai.gabby.util.ThemeUtils
import com.gabai.gabby.util.hide
import com.gabai.gabby.util.show
import com.gabai.gabby.viewdata.AttachmentViewData
import kotlinx.android.synthetic.main.fragment_report_statuses.*
import javax.inject.Inject

class ReportStatusesFragment : Fragment(), Injectable, AdapterHandler {

    @Inject
    lateinit var viewModelFactory: ViewModelFactory

    @Inject
    lateinit var accountManager: AccountManager

    private lateinit var viewModel: ReportViewModel

    private lateinit var adapter: StatusesAdapter
    private lateinit var layoutManager: LinearLayoutManager

    private var snackbarErrorRetry: Snackbar? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProviders.of(requireActivity(), viewModelFactory)[ReportViewModel::class.java]
    }

    override fun showMedia(v: View?, status: Status?, idx: Int) {
        status?.actionableStatus?.let { actionable ->
            when (actionable.attachments[idx].type) {
                Attachment.Type.GIFV, Attachment.Type.VIDEO, Attachment.Type.IMAGE -> {
                    val attachments = AttachmentViewData.list(actionable)
                    val intent = ViewMediaActivity.newIntent(context, attachments,
                            idx)
                    if (v != null) {
                        val url = actionable.attachments[idx].url
                        ViewCompat.setTransitionName(v, url)
                        val options = ActivityOptionsCompat.makeSceneTransitionAnimation(requireActivity(),
                                v, url)
                        startActivity(intent, options.toBundle())
                    } else {
                        startActivity(intent)
                    }
                }
                Attachment.Type.UNKNOWN -> {
                }
            }

        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_report_statuses, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        handleClicks()
        initStatusesView()
        setupSwipeRefreshLayout()
    }

    private fun setupSwipeRefreshLayout() {
        swipeRefreshLayout.setColorSchemeResources(R.color.tusky_blue)
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(ThemeUtils.getColor(swipeRefreshLayout.context, android.R.attr.colorBackground))

        swipeRefreshLayout.setOnRefreshListener {
            snackbarErrorRetry?.dismiss()
            viewModel.refreshStatuses()
        }
    }

    private fun initStatusesView() {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val useAbsoluteTime = preferences.getBoolean("absoluteTimeView", false)

        val account = accountManager.activeAccount
        val mediaPreviewEnabled = account?.mediaPreviewEnabled ?: true


        adapter = StatusesAdapter(useAbsoluteTime, mediaPreviewEnabled, viewModel.statusViewState, this)

        recyclerView.addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        layoutManager = LinearLayoutManager(requireContext())
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as SimpleItemAnimator).supportsChangeAnimations = false

        viewModel.statuses.observe(viewLifecycleOwner, Observer<PagedList<Status>> {
            adapter.submitList(it)
        })

        viewModel.networkStateAfter.observe(viewLifecycleOwner, Observer {
            if (it?.status == com.gabai.gabby.util.Status.RUNNING)
                progressBarBottom.show()
            else
                progressBarBottom.hide()

            if (it?.status == com.gabai.gabby.util.Status.FAILED)
                showError(it.msg)
        })

        viewModel.networkStateBefore.observe(viewLifecycleOwner, Observer {
            if (it?.status == com.gabai.gabby.util.Status.RUNNING)
                progressBarTop.show()
            else
                progressBarTop.hide()

            if (it?.status == com.gabai.gabby.util.Status.FAILED)
                showError(it.msg)
        })

        viewModel.networkStateRefresh.observe(viewLifecycleOwner, Observer {
            if (it?.status == com.gabai.gabby.util.Status.RUNNING && !swipeRefreshLayout.isRefreshing)
                progressBarLoading.show()
            else
                progressBarLoading.hide()

            if (it?.status != com.gabai.gabby.util.Status.RUNNING)
                swipeRefreshLayout.isRefreshing = false
            if (it?.status == com.gabai.gabby.util.Status.FAILED)
                showError(it.msg)
        })
    }

    private fun showError(@Suppress("UNUSED_PARAMETER") msg: String?) {
        if (snackbarErrorRetry?.isShown != true) {
            snackbarErrorRetry = Snackbar.make(swipeRefreshLayout, R.string.failed_fetch_statuses, Snackbar.LENGTH_INDEFINITE)
            snackbarErrorRetry?.setAction(R.string.action_retry) {
                viewModel.retryStatusLoad()
            }
            snackbarErrorRetry?.show()
        }
    }


    private fun handleClicks() {
        buttonCancel.setOnClickListener {
            viewModel.navigateTo(Screen.Back)
        }

        buttonContinue.setOnClickListener {
            if (viewModel.isStatusesSelected()) {
                viewModel.navigateTo(Screen.Note)
            } else {
                Snackbar.make(swipeRefreshLayout, R.string.error_report_too_few_statuses, Snackbar.LENGTH_LONG).show()
            }
        }
    }

    override fun setStatusChecked(status: Status, isChecked: Boolean) {
        viewModel.setStatusChecked(status, isChecked)
    }

    override fun isStatusChecked(id: String): Boolean {
        return viewModel.isStatusChecked(id)
    }

    override fun onViewAccount(id: String) = startActivity(AccountActivity.getIntent(requireContext(), id))

    override fun onViewTag(tag: String) = startActivity(ViewTagActivity.getIntent(requireContext(), tag))

    override fun onViewUrl(url: String?) = viewModel.checkClickedUrl(url)

    companion object {
        fun newInstance() = ReportStatusesFragment()
    }
}
