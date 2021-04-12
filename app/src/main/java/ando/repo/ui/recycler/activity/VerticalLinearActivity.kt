package ando.repo.ui.recycler.activity

import ando.library.widget.recycler.BaseQuickAdapter
import ando.library.widget.recycler.BaseViewHolder
import ando.repo.R
import android.graphics.Color
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ando.repo.ui.recycler.DataServer
import ando.repo.ui.recycler.adapter.DataAdapter
import ando.repo.ui.recycler.dpToPx
import ando.library.widget.recycler.decoration.DecorationProvider

class VerticalLinearActivity : BaseActivity() {

    override fun createLayoutManager(): RecyclerView.LayoutManager {
        return LinearLayoutManager(this)
    }

    override fun createAdapter(): RecyclerView.Adapter<BaseViewHolder> {
        return DataAdapter()
    }

    override fun addHeaderFooter(adapter: RecyclerView.Adapter<BaseViewHolder>) {
        val quickAdapter = adapter as DataAdapter
        quickAdapter.addHeaderView(getView(R.layout.item_widget_ver_header))
        quickAdapter.addFooterView(getView(R.layout.item_widget_ver_footer))
    }

    override fun initItemDecoration(recyclerView: RecyclerView, adapter: RecyclerView.Adapter<BaseViewHolder>) {
        DecorationProvider.linear()
            .color(Color.RED)
            .dividerSize(dpToPx(1f))
            .marginStart(dpToPx(20f))
            .hideDividerForItemType(BaseQuickAdapter.HEADER_VIEW)
            .hideAroundDividerForItemType(BaseQuickAdapter.FOOTER_VIEW)
            .build()
            .addTo(recyclerView)
    }

    override fun initData(adapter: RecyclerView.Adapter<BaseViewHolder>) {
        val quickAdapter = adapter as DataAdapter
        quickAdapter.setNewData(DataServer.createLinearData(20).toMutableList())
    }

}
