package de.rwth_aachen.phyphox

import androidx.multidex.MultiDexApplication
import dagger.hilt.android.HiltAndroidApp

//This extension to application is only used to store measured data in memory as this may easily exceed the amount of data allowed on the transaction stack
@HiltAndroidApp
class App : MultiDexApplication() {

    //Need to get rid off of this ASAP
    @JvmField
    var experiment: PhyphoxExperiment? = null
}
