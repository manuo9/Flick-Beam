package com.flickbeam.remote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The app class Hilt needs to set up dependency injection. */
@HiltAndroidApp
class FlickBeamRemoteApplication : Application()
