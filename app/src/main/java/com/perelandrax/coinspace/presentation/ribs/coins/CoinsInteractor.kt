package com.perelandrax.coinspace.presentation.ribs.coins

import com.perelandrax.coinspace.data.CoinRepository
import com.perelandrax.coinspace.domain.Coin
import com.perelandrax.coinspace.domain.CoinMaster
import com.perelandrax.coinspace.interactors.GetCoins
import com.perelandrax.coinspace.presentation.coroutine.CoroutineScopeProvider
import com.perelandrax.coinspace.presentation.ribs.splash.masterstream.CoinMasterStreamSource
import com.uber.rib.core.Bundle
import com.uber.rib.core.Interactor
import com.uber.rib.core.RibInteractor
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import java8.util.stream.StreamSupport
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Coordinates Business Logic for [CoinsScope].
 *
 * TODO describe the logic of this scope.
 */
@RibInteractor
class CoinsInteractor : Interactor<CoinsInteractor.CoinsPresenter, CoinsRouter>(),
  CoroutineScopeProvider {

  @Inject lateinit var presenter: CoinsPresenter
  @Inject lateinit var coinRepository: CoinRepository
  @Inject lateinit var coinMasterStreamSource: CoinMasterStreamSource
  @Inject lateinit var getCoins: GetCoins

  private val disposables = CompositeDisposable()

  override fun didBecomeActive(savedInstanceState: Bundle?) {
    super.didBecomeActive(savedInstanceState)

    presenter.showLoading()

    disposables.add(presenter.onRefreshCoinList().subscribeBy {
      updateCoinList()
    })

    disposables.add(presenter.onNavigateCoinDetail().subscribeBy { coin ->
      coin?.detailId?.let { routeCoinDetail(it) }
    })

    updateCoinList()
  }

  private fun updateCoinList() = launch {
    runCatching { getCoins.invoke() }.apply {
      onSuccess {
        val mergedCoinList = mergedCoinListByDetailId(it)
        dispatchUi { presenter.showCoinList(mergedCoinList) }
      }

      dispatchUi {
        onFailure(presenter::showError)
        presenter.hideLoading()
      }
    }
  }

  private fun mergedCoinListByDetailId(coinList: List<Coin>): List<Coin> {
    val coinMasterList = coinMasterStreamSource.source.value

    coinList.forEach { coin ->
      val detailId = StreamSupport.stream(coinMasterList)
        .filter { coinMaster -> coin.name == coinMaster.name }
        .findFirst()
        .orElse(CoinMaster()).id

      coin.detailId = detailId
    }

    return coinList
  }

  override fun willResignActive() {
    super.willResignActive()

    parentJob.cancelChildren()
    disposables.clear()
  }

  private fun routeCoinDetail(coinId: String) {
    router.attachCoinDetail(coinId)
  }

  /**
   * Presenter interface implemented by this RIB's view.
   */
  interface CoinsPresenter {

    fun onRefreshCoinList(): Observable<Unit>
    fun onNavigateCoinDetail(): Observable<Coin>

    fun showLoading()
    fun hideLoading()

    fun showError(throwable: Throwable)
    fun showCoinList(coinList: List<Coin>)
  }

  /**
   * Listener interface implemented by a parent RIB's interactor's inner class.
   */
  interface Listener
}

