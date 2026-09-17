package com.flickbeam.tv

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The app class Hilt needs to set up dependency injection. */
@HiltAndroidApp
class FlickBeamApplication : Application()
