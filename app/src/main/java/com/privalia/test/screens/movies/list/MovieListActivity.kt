package com.privalia.test.screens.movies.list

import android.app.ActivityOptions
import android.app.SearchManager
import android.arch.lifecycle.ViewModelProviders
import android.content.Context
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.util.Log
import android.util.Pair
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.andrognito.flashbar.Flashbar
import com.jakewharton.rxbinding2.support.v7.widget.RxRecyclerView
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView
import com.privalia.test.FIRED
import com.privalia.test.R
import com.privalia.test.hasLollipop
import com.privalia.test.mvi.BaseEvent
import com.privalia.test.mvi.view.BaseActivity
import com.privalia.test.mvi.view.ErrorMessageFactory
import com.privalia.test.screens.ViewModelFactory
import com.privalia.test.screens.movies.detail.MovieDetailActivity
import com.privalia.test.screens.movies.detail.MovieDetailFragment
import com.privalia.test.screens.movies.entities.Movie
import com.privalia.test.screens.movies.list.events.GetMoviesEvent
import com.privalia.test.screens.movies.list.events.SearchMoviesEvent
import com.privalia.test.showErrorFlashBar
import com.zeyad.gadapter.GenericRecyclerViewAdapter
import com.zeyad.usecases.api.DataServiceFactory
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.internal.schedulers.IoScheduler
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.activity_movie_list.*
import kotlinx.android.synthetic.main.movie_list.*
import kotlinx.android.synthetic.main.view_loader_layout.*
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * An activity representing a list of Pings. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a [MovieDetailActivity] representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 */
class MovieListActivity : BaseActivity<MovieListState, MovieListVM>() {

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private var twoPane: Boolean = false
    private var currentFragTag: String = ""
    private lateinit var moviesAdapter: GenericRecyclerViewAdapter
    private val postOnResumeEvents = PublishSubject.create<BaseEvent<*>>()
    private lateinit var eventObservable: Observable<BaseEvent<*>>


    override fun errorMessageFactory(): ErrorMessageFactory {
        return object : ErrorMessageFactory {
            override fun getErrorMessage(throwable: Throwable) = throwable.localizedMessage
        }
    }

    override fun initialize() {
        viewState = EmptyState()
        eventObservable = Observable.empty()
        viewModel = ViewModelProviders.of(this,
                ViewModelFactory(DataServiceFactory.getInstance()!!)).get(MovieListVM::class.java)
        eventObservable = Single.just<BaseEvent<*>>(GetMoviesEvent(0))
                .doOnSuccess { Log.d("GetPaginatedMoviesEvent", FIRED) }.toObservable()
    }

    override fun setupUI(isNew: Boolean) {
        setContentView(R.layout.activity_movie_list)
        setSupportActionBar(toolbar)
        toolbar.title = title
        setupRecyclerView()
        twoPane = findViewById<View>(R.id.movie_detail_container) != null
    }

    override fun events(): Observable<BaseEvent<*>> = eventObservable.mergeWith(postOnResumeEvents())

    private fun postOnResumeEvents() = postOnResumeEvents

    override fun renderSuccessState(successState: MovieListState, event: String) {
        moviesAdapter.setDataList(successState.movies, successState.callback)
    }

    override fun toggleViews(isLoading: Boolean, event: String) {
        linear_layout_loader.bringToFront()
        linear_layout_loader.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun showError(errorMessage: String, event: String) {
        showErrorFlashBar(errorMessage)
                .backgroundColorRes(android.R.color.holo_red_dark)
                .primaryActionText("Retry")
                .primaryActionTextColor(R.color.white)
                .primaryActionTapListener(object : Flashbar.OnActionTapListener {
                    override fun onActionTapped(bar: Flashbar) {
                        bar.dismiss()
//                        postOnResumeEvents.onNext()
                    }
                })
                .show()
    }

    private fun setupRecyclerView() {
        moviesAdapter = object : GenericRecyclerViewAdapter(
                getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater, ArrayList()) {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
                    when (viewType) {
                        R.layout.movie_list_content -> MovieViewHolder(layoutInflater
                                .inflate(R.layout.movie_list_content, parent, false))
                        else -> throw IllegalArgumentException("Could not find view of type $viewType")
                    }
        }
        moviesAdapter.setAreItemsClickable(true)
        moviesAdapter.setOnItemClickListener { _, itemInfo, holder ->
            if (itemInfo.getData<Any>() is Movie) {
                val movieModel = itemInfo.getData<Movie>()
                var pair: Pair<View, String>? = null
                var secondPair: Pair<View, String>? = null
                if (hasLollipop()) {
                    val movieViewHolder = holder as MovieViewHolder
                    val avatar = movieViewHolder.getAvatar()
                    pair = Pair.create(avatar, avatar.transitionName)
                    val textViewTitle = movieViewHolder.getTextViewTitle()
                    secondPair = Pair.create(textViewTitle, textViewTitle.transitionName)
                }
                if (twoPane) {
                    if (currentFragTag.isNotBlank()) {
                        removeFragment(currentFragTag)
                    }
                    val orderDetailFragment = MovieDetailFragment.newInstance(movieModel)
                    currentFragTag = orderDetailFragment.javaClass.simpleName + movieModel.id
                    addFragment(R.id.movie_detail_container, orderDetailFragment, currentFragTag,
                            pair!!, secondPair!!)
                } else {
                    if (hasLollipop()) {
                        val options = ActivityOptions.makeSceneTransitionAnimation(this, pair,
                                secondPair)
                        startActivity(MovieDetailActivity.getCallingIntent(this, movieModel),
                                options.toBundle())
                    } else {
                        startActivity(MovieDetailActivity.getCallingIntent(this, movieModel))
                    }
                }
            }
        }
        movie_list.layoutManager = LinearLayoutManager(this)
        movie_list.adapter = moviesAdapter
        moviesAdapter.setAllowSelection(true)
        eventObservable = eventObservable.mergeWith(RxRecyclerView.scrollEvents(movie_list)
                .map { recyclerViewScrollEvent ->
                    GetMoviesEvent(
                            if (ScrollEventCalculator.isAtScrollEnd(recyclerViewScrollEvent)
                                    && viewState !is MovieSearchState)
                                viewState.page
                            else -1)
                }
                .filter { it.getPayLoad() != -1 }
                .throttleLast(200, TimeUnit.MILLISECONDS, IoScheduler())
                .debounce(300, TimeUnit.MILLISECONDS, IoScheduler())
                .doOnNext { Log.d("NextPageEvent", FIRED) })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.movies_menu, menu)
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        val searchView = menu.findItem(R.id.menu_search).actionView as SearchView
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName))
        searchView.setOnCloseListener {
            postOnResumeEvents.onNext(GetMoviesEvent(viewState.page))
            false
        }
        eventObservable = eventObservable.mergeWith(RxSearchView.queryTextChanges(searchView)
                .filter { charSequence -> charSequence.toString().isNotBlank() }
                .throttleLast(300, TimeUnit.MILLISECONDS, Schedulers.computation())
                .debounce(400, TimeUnit.MILLISECONDS, Schedulers.computation())
                .distinctUntilChanged()
                .switchMap { Observable.just(SearchMoviesEvent(it.toString(), viewState.movies)) } // Todo Check!!
                .doOnEach { Log.d("SearchEvent", FIRED) })
        return super.onCreateOptionsMenu(menu)
    }

    /**
     * Adds a [Fragment] to this activity's layout.
     *
     * @param containerViewId The container view to where add the fragment.
     * @param fragment        The fragment to be added.
     */
    @SafeVarargs
    private fun addFragment(containerViewId: Int, fragment: Fragment, currentFragTag: String?,
                            vararg sharedElements: Pair<View, String>) {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        for (pair in sharedElements) {
            fragmentTransaction.addSharedElement(pair.first, pair.second)
        }
        if (currentFragTag == null || currentFragTag.isEmpty()) {
            fragmentTransaction.addToBackStack(fragment.tag)
        } else {
            fragmentTransaction.addToBackStack(currentFragTag)
        }
        fragmentTransaction.add(containerViewId, fragment, fragment.tag).commit()
    }

    private fun removeFragment(tag: String) {
        supportFragmentManager.beginTransaction().remove(supportFragmentManager.findFragmentByTag(tag))
                .commit()
    }
}