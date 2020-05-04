/*
 * Licensed to the Light Team Software (Light Team) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The Light Team licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightteam.modpeide.internal.di.settings

import androidx.lifecycle.ViewModelProvider
import com.lightteam.modpeide.database.AppDatabase
import com.lightteam.modpeide.data.storage.keyvalue.PreferenceHandler
import com.lightteam.modpeide.domain.providers.rx.SchedulersProvider
import com.lightteam.modpeide.ui.settings.activities.SettingsActivity
import com.lightteam.modpeide.ui.settings.viewmodel.SettingsViewModel
import dagger.Module
import dagger.Provides

@Module
class SettingsActivityModule {

    @Provides
    @SettingsScope
    fun provideSettingsViewModelFactory(
        schedulersProvider: SchedulersProvider,
        preferenceHandler: PreferenceHandler,
        appDatabase: AppDatabase
    ): SettingsViewModel.Factory {
        return SettingsViewModel.Factory(
            schedulersProvider,
            preferenceHandler,
            appDatabase
        )
    }

    @Provides
    @SettingsScope
    fun provideSettingsViewModel(
        activity: SettingsActivity,
        factory: SettingsViewModel.Factory
    ): SettingsViewModel {
        return ViewModelProvider(activity, factory).get(SettingsViewModel::class.java)
    }
}