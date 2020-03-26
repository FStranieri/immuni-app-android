package org.ascolto.onlus.geocrowd19.android.ui.uploadData

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bendingspoons.base.livedata.Event
import kotlinx.coroutines.*
import org.ascolto.onlus.geocrowd19.android.api.oracle.ApiManager
import org.ascolto.onlus.geocrowd19.android.db.AscoltoDatabase
import org.ascolto.onlus.geocrowd19.android.managers.SurveyManager
import org.ascolto.onlus.geocrowd19.android.models.ExportData
import org.ascolto.onlus.geocrowd19.android.models.ExportDevice
import org.ascolto.onlus.geocrowd19.android.models.ExportHealthProfile
import org.ascolto.onlus.geocrowd19.android.toast
import org.koin.core.KoinComponent
import org.koin.core.inject

class UploadDataViewModel(val userId:String, val database: AscoltoDatabase) : ViewModel(), KoinComponent {

    private val viewModelJob = SupervisorJob()
    private val uiScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private val surveyManager: SurveyManager by inject()
    private val apiManager: ApiManager by inject()

    val error = MutableLiveData<Event<Boolean>>()
    val loading = MutableLiveData<Event<Boolean>>()

    fun exportData(code: String) {
        uiScope.launch {
            loading.value = Event(true)
            delay(500) // min loader time to avoid flickering
            val devices = database.bleContactDao().getAll().map {
                ExportDevice(
                    timestamp = it.timestamp,
                    btId = it.btId,
                    signalStrength = it.signalStrength
                )
            }
            val surveys = surveyManager.allHealthProfiles(userId).map {
                ExportHealthProfile.fromHealthProfile(it)
            }

            val exportData = ExportData(
                profileId = userId,
                surveys = surveys,
                devices = devices
            )

            val result = apiManager.exportData(code, exportData)
            loading.value = Event(false)
            if (result.isSuccessful) {
                //error.value = Event(false)
            } else {
                error.value = Event(true)
            }
        }
    }
}