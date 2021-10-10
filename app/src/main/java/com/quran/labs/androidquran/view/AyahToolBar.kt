package com.quran.labs.androidquran.view

import android.content.Context
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MenuItem.OnMenuItemClickListener
import android.view.View
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.BuildConfig
import com.quran.labs.androidquran.R
import com.quran.labs.androidquran.ui.util.ToastCompat
import com.quran.labs.androidquran.view.AyahToolBar.PipPosition.DOWN
import com.quran.labs.androidquran.view.AyahToolBar.PipPosition.UP

class AyahToolBar @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyle: Int = 0
) : ViewGroup(context, attrs, defStyle), OnClickListener, OnLongClickListener {

  enum class PipPosition { UP, DOWN }

  private var menu: Menu
  private val pipWidth: Int
  private val pipHeight: Int
  private val itemWidth: Int
  private val ayahMenu = R.menu.ayah_menu
  private val menuLayout: LinearLayout
  private val toolBarPip: AyahToolBarPip

  private var pipOffset = 0f
  private var pipPosition: PipPosition
  private var currentMenu: Menu? = null
  private var itemSelectedListener: OnMenuItemClickListener? = null

  var isShowing = false
    private set

  init {
    val resources = context.resources
    itemWidth = resources.getDimensionPixelSize(R.dimen.toolbar_item_width)
    val toolBarHeight = resources.getDimensionPixelSize(R.dimen.toolbar_height)
    pipHeight = resources.getDimensionPixelSize(R.dimen.toolbar_pip_height)
    pipWidth = resources.getDimensionPixelSize(R.dimen.toolbar_pip_width)
    val background = ContextCompat.getColor(context, R.color.toolbar_background)

    menuLayout = LinearLayout(context).apply {
      layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, toolBarHeight)
      setBackgroundColor(background)
    }
    addView(menuLayout)

    pipPosition = DOWN
    toolBarPip = AyahToolBarPip(context)
    toolBarPip.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, pipHeight)
    addView(toolBarPip)

    // used to use MenuBuilder, but now it has @RestrictTo, so using this clever trick from
    // StackOverflow - PopupMenu generates a new MenuBuilder internally, so this just lets us
    // get that menu and do whatever we want with it.
    menu = PopupMenu(this.context, this).menu
    val inflater = MenuInflater(this.context)
    inflater.inflate(ayahMenu, menu)
    showMenu(menu)
  }

  override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    val totalWidth = measuredWidth
    val pipWidth = toolBarPip.measuredWidth
    val pipHeight = toolBarPip.measuredHeight
    val menuWidth = menuLayout.measuredWidth
    val menuHeight = menuLayout.measuredHeight
    var pipLeft = pipOffset.toInt()
    if (pipLeft + pipWidth > totalWidth) {
      pipLeft = totalWidth / 2 - pipWidth / 2
    }

    // overlap the pip and toolbar by 1px to avoid occasional gap
    if (pipPosition == UP) {
      toolBarPip.layout(pipLeft, 0, pipLeft + pipWidth, pipHeight + 1)
      menuLayout.layout(0, pipHeight, menuWidth, pipHeight + menuHeight)
    } else {
      toolBarPip.layout(pipLeft, menuHeight - 1, pipLeft + pipWidth, menuHeight + pipHeight)
      menuLayout.layout(0, 0, menuWidth, menuHeight)
    }
  }

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    measureChild(menuLayout, widthMeasureSpec, heightMeasureSpec)
    val width = menuLayout.measuredWidth
    var height = menuLayout.measuredHeight
    measureChild(
      toolBarPip,
      MeasureSpec.makeMeasureSpec(pipWidth, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(pipHeight, MeasureSpec.EXACTLY)
    )
    height += toolBarPip.measuredHeight
    setMeasuredDimension(
      resolveSize(width, widthMeasureSpec),
      resolveSize(height, heightMeasureSpec)
    )
  }

  private fun showMenu(menu: Menu) {
    if (currentMenu === menu) {
      // no need to re-draw
      return
    }

    // disable sharing for warsh and qaloon
    val menuItem = menu.findItem(R.id.cab_share_ayah)
    if (menuItem != null &&
      (BuildConfig.FLAVOR == "warsh" || BuildConfig.FLAVOR == "qaloon")
    ) {
      menuItem.isVisible = false
    }
    menuLayout.removeAllViews()
    val count = menu.size()
    for (i in 0 until count) {
      val item = menu.getItem(i)
      if (item.isVisible) {
        val view = getMenuItemView(item)
        menuLayout.addView(view)
      }
    }
    currentMenu = menu
  }

  private fun getMenuItemView(item: MenuItem): View {
    return ImageButton(context).apply {
      setImageDrawable(item.icon)
      setBackgroundResource(R.drawable.toolbar_button)
      id = item.itemId
      layoutParams = LayoutParams(itemWidth, LayoutParams.MATCH_PARENT)
      setOnClickListener(this@AyahToolBar)
      setOnLongClickListener(this@AyahToolBar)
    }
  }

  // relying on getWidth() may give us the width of a shorter
  // submenu instead of the actual menu
  val toolBarWidth: Int
    get() = menu.size() * itemWidth

  fun setBookmarked(bookmarked: Boolean) {
    val bookmarkItem = menu.findItem(R.id.cab_bookmark_ayah)
    bookmarkItem.setIcon(if (bookmarked) R.drawable.ic_favorite else R.drawable.ic_not_favorite)
    val bookmarkButton = findViewById<ImageButton>(R.id.cab_bookmark_ayah)
    bookmarkButton?.setImageDrawable(bookmarkItem.icon)
  }

  fun updatePosition(position: AyahToolBarPosition) {
    val needsLayout = position.pipPosition != pipPosition || pipOffset != position.pipOffset
    ensurePipPosition(position.pipPosition)
    pipOffset = position.pipOffset
    val x = position.x + position.xScroll
    val y = position.y + position.yScroll
    setPosition(x, y)
    if (needsLayout) {
      requestLayout()
    }
  }

  private fun setPosition(x: Float, y: Float) {
    translationX = x
    translationY = y
  }

  private fun ensurePipPosition(position: PipPosition) {
    pipPosition = position
    toolBarPip.ensurePosition(position)
  }

  fun resetMenu() {
    showMenu(menu)
  }

  fun showMenu() {
    showMenu(menu)
    visibility = VISIBLE
    isShowing = true
  }

  fun hideMenu() {
    isShowing = false
    visibility = GONE
  }

  fun setOnItemSelectedListener(listener: OnMenuItemClickListener?) {
    itemSelectedListener = listener
  }

  override fun onClick(v: View) {
    val item = menu.findItem(v.id) ?: return
    if (item.hasSubMenu()) {
      showMenu(item.subMenu)
    } else {
      itemSelectedListener?.onMenuItemClick(item)
    }
  }

  override fun onLongClick(v: View): Boolean {
    val item = menu.findItem(v.id)
    if (item != null && item.title != null) {
      ToastCompat.makeText(context, item.title, Toast.LENGTH_SHORT).show()
      return true
    }
    return false
  }

  data class AyahToolBarPosition(
    val x: Float = 0f,
    val y: Float = 0f,
    val xScroll: Float = 0f,
    val yScroll: Float = 0f,
    val pipOffset: Float = 0f,
    val pipPosition: PipPosition = DOWN
  ) {
    fun withX(x: Float) = copy(x = x)
    fun withY(y: Float) = copy(y = y)
    fun withXScroll(xScroll: Float) = copy(xScroll = xScroll)
    fun withYScroll(yScroll: Float) = copy(yScroll = yScroll)
  }
}