package com.aliemressman.movieapp.ViewModel

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.aliemressman.movieapp.models.DashboardResult
import com.aliemressman.movieapp.models.GenresVeriler
import com.aliemressman.movieapp.models.FilmDashboardVeriler
import com.aliemressman.movieapp.models.Genres
import com.aliemressman.movieapp.models.UpcomResult
import com.aliemressman.movieapp.models.UpcomVeriler
import com.aliemressman.movieapp.servis.FilmAPIServis
import com.aliemressman.movieapp.servis.FilmDataBase
import com.aliemressman.movieapp.util.OzelSheredPreferences
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.observers.DisposableSingleObserver
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) :BaseViewModel(application)
{

   private val context = getApplication<Application>().applicationContext


    val detayYukleniyor = MutableLiveData<Boolean>()
    val hataMesaji = MutableLiveData<Boolean>()

    val dashboardResultVeriler = MutableLiveData<List<DashboardResult>>()
    val dashBoardTurVeriler = MutableLiveData<List<Genres>>()
    val dashboardUpcomVeriler = MutableLiveData<List<UpcomResult>>()

    private val filmAPIServis = FilmAPIServis()
    private val disposable = CompositeDisposable()

    private val ozelSheredPreferences = OzelSheredPreferences(getApplication())
    private var guncellemeZamani = 10 * 60 * 1000 * 1000 * 1000L

    fun refreshData(){

        val kaydedilmeZamani = ozelSheredPreferences.zamaniAl()
        if (kaydedilmeZamani != null && kaydedilmeZamani != 0L && System.nanoTime() - kaydedilmeZamani < guncellemeZamani){
            // sqLite'tan çek.
            verileriSQlitetanAl()
        }
        else{
            getFilm()
            getGenres()
            getUpcom()
        }
    }
    fun refreshFromInternet(){
        getFilm()
        getGenres()
        getUpcom()
    }
   private fun verileriSQlitetanAl(){
        detayYukleniyor.value = true
        launch {
            val filmDashboard = FilmDataBase(getApplication()).filmDao().getAllFilm()
            filmDashboardVerileriGoster(filmDashboard)

            val filmGenres = FilmDataBase(getApplication()).filmDao().getAllGenres()
            filmGenresVerileriGoster(filmGenres)

            val filmUpcom = FilmDataBase(getApplication()).filmDao().getAllUpcom()
            filmUpcomVerileriGoster(filmUpcom)

            Toast.makeText(getApplication(),"filmleri Room'dan aldık",Toast.LENGTH_LONG).show()
        }
    }
    private fun getFilm(){
        detayYukleniyor.value = true
        Log.d("api", "yükleniyor")
        disposable.add(
            filmAPIServis.getFilm()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(object : DisposableSingleObserver<FilmDashboardVeriler>(){
                    override fun onSuccess(t:FilmDashboardVeriler) {
                        t.results?.let {
                            dashboardResultVeriler.value = it
                            sqliteSakla(it)
                        }
                    }

                    override fun onError(e: Throwable) {
                        hataMesaji.value = true
                        detayYukleniyor.value = false
                        e.printStackTrace()
                        e.localizedMessage?.let { Log.d("api", it) }
                    }
                })
        )
    }

    private fun getGenres(){
        detayYukleniyor.value = true
        disposable.add(
            filmAPIServis.getGenres()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(object : DisposableSingleObserver<GenresVeriler>(){
                    override fun onSuccess(t: GenresVeriler) {

                        t.genres.let {
                            dashBoardTurVeriler.value = it
                            sqliteGenresSakla(it)
                        }
                    }

                    override fun onError(e: Throwable) {
                        hataMesaji.value = true
                        detayYukleniyor.value = false
                        e.printStackTrace()
                    }
                }
                )
        )
    }

    private fun getUpcom(){
        detayYukleniyor.value = true
        disposable.add(
            filmAPIServis.getUpcom()
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(object : DisposableSingleObserver<UpcomVeriler>(){
                    override fun onSuccess(t: UpcomVeriler) {

                        t.results?.let {
                            dashboardUpcomVeriler.value = it
                            sqliteUpcomSakla(it)
                        }
                        detayYukleniyor.value = false
                        hataMesaji.value = false
                    }

                    override fun onError(e: Throwable) {
                        hataMesaji.value = true
                        detayYukleniyor.value = false
                        e.printStackTrace()
                    }

                })
        )
    }

    private fun filmDashboardVerileriGoster(filmlerListesi : List<DashboardResult>){
        dashboardResultVeriler.value = filmlerListesi
        detayYukleniyor.value = false
        hataMesaji.value = false
    }
    private fun filmGenresVerileriGoster(filmGenresListesi : List<Genres>){
        dashBoardTurVeriler.value = filmGenresListesi
        detayYukleniyor.value = false
        hataMesaji.value = false
    }
    private fun filmUpcomVerileriGoster(filmUpcomListesi : List<UpcomResult>) {
        dashboardUpcomVeriler.value = filmUpcomListesi
        detayYukleniyor.value = false
        hataMesaji.value = false
    }

   private fun sqliteSakla(filmListesi : List<DashboardResult>){
        viewModelScope.launch {
            val dao = FilmDataBase(getApplication()).filmDao()
            dao.deleteAllFilm()
            val uuidListesi =  dao.insertAll(*filmListesi.toTypedArray())
            var i = 0
            while (i < filmListesi.size){
                filmListesi[i].uuid = uuidListesi[i].toInt()
                i += 1
            }
            hataMesaji.value = false
            detayYukleniyor.value = false
            dashboardResultVeriler.value = filmListesi
            filmDashboardVerileriGoster(filmListesi)
        }
        ozelSheredPreferences.zamaniKaydet(System.nanoTime())
    }

    private fun sqliteGenresSakla(genresListesi : List<Genres>){
        viewModelScope.launch {
            val dao = FilmDataBase(getApplication()).filmDao()
            dao.deleteGenresAllFilm()
            val uuidListesi =  dao.insertGenresAll(*genresListesi.toTypedArray())
            var i = 0
            while (i < genresListesi.size){
                genresListesi[i].uuid = uuidListesi[i].toInt()
                i += 1
            }
            hataMesaji.value = false
            detayYukleniyor.value = false
            dashBoardTurVeriler.value = genresListesi
            filmGenresVerileriGoster(genresListesi)
        }
        ozelSheredPreferences.zamaniKaydet(System.nanoTime())
    }

    private fun sqliteUpcomSakla(upcomListesi : List<UpcomResult>){
        viewModelScope.launch {
            val dao = FilmDataBase(getApplication()).filmDao()
            dao.deleteUpcomAllFilm()
            val uuidListesi =  dao.insertUpcomAll(*upcomListesi.toTypedArray())
            var i = 0
            while (i < upcomListesi.size){
                upcomListesi[i].uuid = uuidListesi[i].toInt()
                i += 1
            }
            hataMesaji.value = false
            detayYukleniyor.value = false
            dashboardUpcomVeriler.value = upcomListesi
            filmUpcomVerileriGoster(upcomListesi)
        }
        ozelSheredPreferences.zamaniKaydet(System.nanoTime())
    }
}