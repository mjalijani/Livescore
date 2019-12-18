package com.itsamirrezah.livescore.ui.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.BindView
import butterknife.ButterKnife
import com.itsamirrezah.livescore.R
import com.itsamirrezah.livescore.data.services.FootbalDataApiImp
import com.itsamirrezah.livescore.ui.items.CompetitionItem
import com.itsamirrezah.livescore.ui.items.DateItem
import com.itsamirrezah.livescore.ui.items.MatchItem
import com.itsamirrezah.livescore.ui.model.CompetitionModel
import com.itsamirrezah.livescore.ui.model.DateModel
import com.itsamirrezah.livescore.ui.model.ItemModel
import com.itsamirrezah.livescore.ui.model.MatchModel
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.GenericFastAdapter
import com.mikepenz.fastadapter.adapters.ModelAdapter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    @BindView(R.id.recyclerView)
    lateinit var recyclerView: RecyclerView

    private val itemAdapter = ModelAdapter { item: ItemModel ->
        when (item) {
            is MatchModel -> return@ModelAdapter MatchItem(item)
            is DateModel -> return@ModelAdapter DateItem(item)
            is CompetitionModel -> return@ModelAdapter CompetitionItem((item))
            else -> throw IllegalArgumentException()
        }
    }
    private lateinit var fastAdapter: GenericFastAdapter
    private var compositeDisposable = CompositeDisposable()

    /**
     * LifeCycle
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)

        setupRecyclerView()
        requestMatches()

    }

    override fun onDestroy() {
        super.onDestroy()
        compositeDisposable.clear()
    }


    /**
     * Methods
     */

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        fastAdapter = FastAdapter.with(listOf(itemAdapter))
        recyclerView.adapter = fastAdapter
    }

    private fun requestMatches() {
        val requestMatches = FootbalDataApiImp.getApi()
            .getMatches(getDate(-2), getDate(4))
            //returning matches one by one from matchResponse.matches
            .flatMap { Observable.fromIterable(it.matches) }
            //change api model to ui model
            .map {
                MatchModel(
                    it.homeTeam.name,
                    it.score.fullTime.homeTeam.toString(),
                    it.awayTeam.name,
                    it.score.fullTime.awayTeam.toString(),
                    it.utcDate,
                    it.status,
                    it.competition,
                    it.matchday.toString()
                ) as ItemModel
            } //group emissions base on their match dates
            .groupBy { it.shortDate.date }
            //group emissions base on their competition.id
            .switchMap { it.groupBy { it.competition!!.id } }
            .flatMapSingle { it.toList() }
            .groupBy { it.first().shortDate.date }
            .flatMapSingle { it.toList() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::onResponse, this::onError)

        compositeDisposable.add(requestMatches)
    }

    private fun onResponse(matchItems: MutableList<MutableList<ItemModel>>) {
        updateItemAdapter(matchItems)
    }

    private fun onError(throwable: Throwable) {
        print("test")
    }

    private fun updateItemAdapter(matchItems: MutableList<MutableList<ItemModel>>) {

        itemAdapter.add(DateModel(matchItems.first().first().utcDate))
        val matchesObservable = Observable.fromIterable(matchItems)
            .map {
                val match = it.first() as MatchModel
                it.add(0, CompetitionModel(match.utcDate, match.competition, match.matchday))
                it
            }.subscribe { itemAdapter.add(it) }
        compositeDisposable.add(matchesObservable)
    }

    //step:0 => today, step:1 => tomorrow, ...
    private fun getDate(step: Int): String {
        val today = Calendar.getInstance()
        today.add(Calendar.DAY_OF_MONTH, step)
        return SimpleDateFormat("yyyy-MM-dd").format(today.time)
    }
}
