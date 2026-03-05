package com.openpad.core.di

import com.openpad.core.OpenPad
import com.openpad.core.PadPipelineContract
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object PadModule {

    @Provides
    fun providePipeline(): PadPipelineContract {
        return requireNotNull(OpenPad.pipeline) {
            "OpenPad.initialize() must complete before launching PadActivity"
        }
    }
}
