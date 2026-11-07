package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(entities = [SyncLogEntity::class, ProductEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncLogDao(): SyncLogDao
    abstract fun productDao(): ProductDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_autosync_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Pre-seed default Technography products
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.productDao()?.insertAll(getDefaultProducts())
                            }
                        }
                    })
                    .build()
                INSTANCE = instance

                // Verify default products exist
                CoroutineScope(Dispatchers.IO).launch {
                    val count = instance.productDao().getCount()
                    if (count == 0) {
                        instance.productDao().insertAll(getDefaultProducts())
                    }
                }

                instance
            }
        }

        fun getDefaultProducts(): List<ProductEntity> {
            return listOf(
                ProductEntity(
                    id = "1",
                    name = "Mega Bundle (All in One)",
                    price = 300.0,
                    vipLink = "https://technographybd.xyz/mega-bundle-vip-access.html",
                    description = "আমাদের সমস্ত প্রিমিয়াম রিলস, বান্ডেল, ভিডিও ফাইল ও মেগা ক্লাউড ড্রাইভ অ্যাক্সেস একত্রে।",
                    features = "1.5 Lakh Reels, Viral Shorts, Movie Clips, VIP Support",
                    isActive = true
                ),
                ProductEntity(
                    id = "2",
                    name = "Facebook & YouTube Growth Course",
                    price = 99.0,
                    vipLink = "https://technographybd.xyz/growth-course-access.html",
                    description = "ফেসবুক পেজ ও ইউটিউব চ্যানেল দ্রুত ভাইরাল ও মনিটাইজ করার সিক্রেট স্ট্র্যাটেজি ও কোর্স।",
                    features = "Page Growth, Watch Time, Monetization Guide, Algorithm Hacks",
                    isActive = true
                ),
                ProductEntity(
                    id = "3",
                    name = "1.5 Lakh Mixed Mega Reels Bundle",
                    price = 190.0,
                    vipLink = "https://technographybd.xyz/reels-bundle-access.html",
                    description = "দেড় লক্ষ মিক্সড হাই-কোয়ালিটি রিলস ও শর্টস ভিডিও কালেকশন যা সরাসরি আপলোড উপযোগী।",
                    features = "150,000+ HD Reels, No Watermark, Ready to Post",
                    isActive = true
                ),
                ProductEntity(
                    id = "4",
                    name = "Movie & Drama Clips Collection",
                    price = 200.0,
                    vipLink = "https://technographybd.xyz/movie-clips-access.html",
                    description = "জনপ্রিয় মুভি ও নাটকের সেরা মোমেন্ট ও ক্লিপস কালেকশন কপিরাইট-ফ্রি এডিটিং গাইডসহ।",
                    features = "HD Movie Scenes, Drama Shorts, Viral Cuts",
                    isActive = true
                ),
                ProductEntity(
                    id = "5",
                    name = "5000 AI Viral Reels Shorts",
                    price = 180.0,
                    vipLink = "https://technographybd.xyz/food-fitness-access.html",
                    description = "৫০০০+ এআই জেনারেটেড হাই-অ্যাঙ্গেজিং ভাইরাল রিলস ভিডিও ও স্ক্রিপ্ট বান্ডেল।",
                    features = "AI Avatars, Motivational, Fitness, Viral Voiceover",
                    isActive = true
                ),
                ProductEntity(
                    id = "6",
                    name = "5000 Islamic Viral Reels Collection",
                    price = 50.0,
                    vipLink = "https://technographybd.xyz/islamic-reels-access.html",
                    description = "৫০০০+ সুন্দর ও তথ্যবহুল ইসলামিক শর্টস ও রিলস কালেকশন ইসলামিক পেজের জন্য।",
                    features = "Islamic Reminders, Quran Recitations, Beautiful Animations",
                    isActive = true
                )
            )
        }
    }
}
