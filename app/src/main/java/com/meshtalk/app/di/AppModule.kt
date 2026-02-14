package com.meshtalk.app.di

import android.content.Context
import androidx.room.Room
import com.meshtalk.app.data.db.ConversationDao
import com.meshtalk.app.data.db.MeshTalkDatabase
import com.meshtalk.app.data.db.MessageDao
import com.meshtalk.app.data.db.PeerDao
import com.meshtalk.app.data.preferences.UserPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeshTalkDatabase {
        return Room.databaseBuilder(
            context,
            MeshTalkDatabase::class.java,
            "meshtalk.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideMessageDao(database: MeshTalkDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun providePeerDao(database: MeshTalkDatabase): PeerDao = database.peerDao()

    @Provides
    @Singleton
    fun provideConversationDao(database: MeshTalkDatabase): ConversationDao = database.conversationDao()

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context
}

