package com.gabai.gabby.di

import com.google.gson.Gson
import com.gabai.gabby.db.AccountManager
import com.gabai.gabby.db.AppDatabase
import com.gabai.gabby.network.MastodonApi
import com.gabai.gabby.repository.TimelineRepository
import com.gabai.gabby.repository.TimelineRepositoryImpl
import com.gabai.gabby.util.HtmlConverter
import dagger.Module
import dagger.Provides

@Module
class RepositoryModule {
    @Provides
    fun providesTimelineRepository(db: AppDatabase, mastodonApi: MastodonApi,
                                   accountManager: AccountManager, gson: Gson,
                                   htmlConverter: HtmlConverter): TimelineRepository {
        return TimelineRepositoryImpl(db.timelineDao(), mastodonApi, accountManager, gson,
                htmlConverter)
    }
}